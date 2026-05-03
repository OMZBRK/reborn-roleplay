import { Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';

export interface PatchNoteSummary {
  id: string;
  version: string;
  title: string;
  thumbnail: string | null;
  pinned: boolean;
  publishedAt: string;
  excerpt: string;
}

export interface PatchNoteDetail extends PatchNoteSummary {
  content: string;
}

@Injectable()
export class PatchnotesService {
  constructor(private readonly prisma: PrismaService) {}

  async list(page: number, size: number): Promise<{ items: PatchNoteSummary[]; total: number }> {
    const safePage = Math.max(1, page);
    const safeSize = Math.min(50, Math.max(1, size));
    const [rows, total] = await Promise.all([
      this.prisma.patchNote.findMany({
        orderBy: [{ pinned: 'desc' }, { publishedAt: 'desc' }],
        skip: (safePage - 1) * safeSize,
        take: safeSize,
      }),
      this.prisma.patchNote.count(),
    ]);
    return {
      items: rows.map((row) => ({
        id: row.id,
        version: row.version,
        title: row.title,
        thumbnail: row.thumbnail,
        pinned: row.pinned,
        publishedAt: row.publishedAt.toISOString(),
        excerpt: extractExcerpt(row.content, 220),
      })),
      total,
    };
  }

  async getById(id: string): Promise<PatchNoteDetail> {
    const row = await this.prisma.patchNote.findUnique({ where: { id } });
    if (!row) throw new NotFoundException('Patch note introuvable.');
    return {
      id: row.id,
      version: row.version,
      title: row.title,
      thumbnail: row.thumbnail,
      pinned: row.pinned,
      publishedAt: row.publishedAt.toISOString(),
      excerpt: extractExcerpt(row.content, 220),
      content: row.content,
    };
  }
}

function extractExcerpt(markdown: string, maxLen: number): string {
  // Strip very basic markdown : headings, emphasis, links, then truncate.
  const stripped = markdown
    .replace(/^#+\s+/gm, '')
    .replace(/[*_`]/g, '')
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    .replace(/\s+/g, ' ')
    .trim();
  if (stripped.length <= maxLen) return stripped;
  return stripped.slice(0, maxLen).replace(/\s\S*$/, '') + '…';
}
