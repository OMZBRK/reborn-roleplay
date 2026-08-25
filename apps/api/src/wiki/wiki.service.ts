import { Injectable, NotFoundException } from '@nestjs/common';
import { Prisma, WikiEntryStatus, WikiIdeaStatus } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import {
  CreateEntryDto,
  CreateIdeaDto,
  CreateTagDto,
  ListEntriesQueryDto,
  UpdateEntryDto,
  UpdateIdeaDto,
} from './dto/wiki.dto';

@Injectable()
export class WikiService {
  constructor(private readonly prisma: PrismaService) {}

  // ── Slugify ────────────────────────────────────────────
  /** lowercase, strip accents, non-alnum → '-', collapse doublons, trim. */
  private baseSlug(input: string): string {
    const s = input
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '') // retire les accents
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/-{2,}/g, '-')
      .replace(/^-+|-+$/g, '');
    return s.length > 0 ? s : 'entree';
  }

  private async uniqueEntrySlug(input: string): Promise<string> {
    const base = this.baseSlug(input);
    let slug = base;
    let n = 2;
    while (await this.prisma.wikiEntry.findUnique({ where: { slug } })) {
      slug = `${base}-${n++}`;
    }
    return slug;
  }

  private async uniqueTagSlug(input: string): Promise<string> {
    const base = this.baseSlug(input);
    let slug = base;
    let n = 2;
    while (await this.prisma.wikiTag.findUnique({ where: { slug } })) {
      slug = `${base}-${n++}`;
    }
    return slug;
  }

  // ── Entries ────────────────────────────────────────────
  async listEntries(query: ListEntriesQueryDto) {
    const { q, tag, status, kind } = query;
    const where: Prisma.WikiEntryWhereInput = {};
    const and: Prisma.WikiEntryWhereInput[] = [];

    if (status) where.status = status;

    if (q && q.trim().length > 0) {
      const term = q.trim();
      where.OR = [
        { title: { contains: term, mode: 'insensitive' } },
        { summary: { contains: term, mode: 'insensitive' } },
        { body: { contains: term, mode: 'insensitive' } },
      ];
    }

    const tagSlugs = (tag ?? '')
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
    if (tagSlugs.length > 0) {
      and.push({ tags: { some: { slug: { in: tagSlugs } } } });
    }
    if (kind) {
      and.push({ tags: { some: { kind } } });
    }
    if (and.length > 0) where.AND = and;

    return this.prisma.wikiEntry.findMany({
      where,
      orderBy: { updatedAt: 'desc' },
      include: { tags: true },
    });
  }

  async getEntry(id: string) {
    const entry = await this.prisma.wikiEntry.findUnique({
      where: { id },
      include: { tags: true, _count: { select: { revisions: true } } },
    });
    if (!entry) throw new NotFoundException('Entrée introuvable.');
    return entry;
  }

  async createEntry(dto: CreateEntryDto, userId?: string) {
    const slug = await this.uniqueEntrySlug(dto.title);
    return this.prisma.wikiEntry.create({
      data: {
        title: dto.title,
        slug,
        summary: dto.summary,
        body: dto.body,
        status: dto.status ?? WikiEntryStatus.DRAFT,
        sources: dto.sources,
        createdById: userId,
        tags:
          dto.tagSlugs && dto.tagSlugs.length > 0
            ? { connect: dto.tagSlugs.map((s) => ({ slug: s })) }
            : undefined,
      },
      include: { tags: true },
    });
  }

  /**
   * Snapshot l'état courant dans WikiRevision AVANT la mise à jour, puis
   * update + réconcilie les tags (set complet). Le slug reste stable (c'est
   * un identifiant unique) même si le titre change.
   */
  async updateEntry(id: string, dto: UpdateEntryDto, userId?: string) {
    const current = await this.prisma.wikiEntry.findUnique({ where: { id } });
    if (!current) throw new NotFoundException('Entrée introuvable.');

    await this.prisma.wikiRevision.create({
      data: {
        entryId: current.id,
        title: current.title,
        summary: current.summary,
        body: current.body,
        editedById: userId,
      },
    });

    const data: Prisma.WikiEntryUpdateInput = {};
    if (dto.title !== undefined) data.title = dto.title;
    if (dto.summary !== undefined) data.summary = dto.summary;
    if (dto.body !== undefined) data.body = dto.body;
    if (dto.status !== undefined) data.status = dto.status;
    if (dto.sources !== undefined) data.sources = dto.sources;
    if (dto.tagSlugs !== undefined) {
      data.tags = { set: dto.tagSlugs.map((s) => ({ slug: s })) };
    }

    return this.prisma.wikiEntry.update({
      where: { id },
      data,
      include: { tags: true },
    });
  }

  async deleteEntry(id: string) {
    const existing = await this.prisma.wikiEntry.findUnique({ where: { id } });
    if (!existing) throw new NotFoundException('Entrée introuvable.');
    await this.prisma.wikiEntry.delete({ where: { id } });
    return { deleted: true };
  }

  async listRevisions(entryId: string) {
    const existing = await this.prisma.wikiEntry.findUnique({
      where: { id: entryId },
    });
    if (!existing) throw new NotFoundException('Entrée introuvable.');
    return this.prisma.wikiRevision.findMany({
      where: { entryId },
      orderBy: { createdAt: 'desc' },
    });
  }

  // ── Tags ───────────────────────────────────────────────
  async listTags() {
    return this.prisma.wikiTag.findMany({
      orderBy: [{ kind: 'asc' }, { label: 'asc' }],
    });
  }

  async createTag(dto: CreateTagDto) {
    const slug = await this.uniqueTagSlug(dto.label);
    return this.prisma.wikiTag.create({
      data: {
        kind: dto.kind,
        label: dto.label,
        slug,
        color: dto.color,
      },
    });
  }

  // ── Ideas ──────────────────────────────────────────────
  async listIdeas(status?: WikiIdeaStatus) {
    return this.prisma.wikiIdea.findMany({
      where: status ? { status } : {},
      orderBy: { updatedAt: 'desc' },
      include: {
        linkedEntry: { select: { id: true, title: true, slug: true } },
      },
    });
  }

  async createIdea(dto: CreateIdeaDto, userId?: string) {
    return this.prisma.wikiIdea.create({
      data: {
        title: dto.title,
        body: dto.body,
        category: dto.category,
        linkedEntryId: dto.linkedEntryId,
        createdById: userId,
      },
      include: {
        linkedEntry: { select: { id: true, title: true, slug: true } },
      },
    });
  }

  async updateIdea(id: string, dto: UpdateIdeaDto) {
    const current = await this.prisma.wikiIdea.findUnique({ where: { id } });
    if (!current) throw new NotFoundException('Idée introuvable.');

    const data: Prisma.WikiIdeaUpdateInput = {};
    if (dto.title !== undefined) data.title = dto.title;
    if (dto.body !== undefined) data.body = dto.body;
    if (dto.status !== undefined) data.status = dto.status;
    if (dto.category !== undefined) data.category = dto.category;

    return this.prisma.wikiIdea.update({
      where: { id },
      data,
      include: {
        linkedEntry: { select: { id: true, title: true, slug: true } },
      },
    });
  }
}
