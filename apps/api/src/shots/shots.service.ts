import { unlink, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';
import {
  buildPublicUrl,
  generateStoredFilename,
  isAllowedImageMime,
  resolveUploadDir,
} from '../upload/upload.config';
import type { UploadedImageFile } from '../upload/upload.service';

export interface ShotView {
  id: string;
  url: string;
  caption: string | null;
  width: number | null;
  height: number | null;
  likeCount: number;
  likedByMe: boolean;
  createdAt: string;
  author: { id: string; name: string; avatarUrl: string | null };
}

/**
 * Galerie sociale : partage de screenshots + feed + likes. Réutilise le
 * stockage disque de l'`UploadModule` (dossier servi via `/uploads`).
 */
@Injectable()
export class ShotsService {
  private readonly logger = new Logger(ShotsService.name);
  private readonly uploadDir = resolveUploadDir();

  constructor(private readonly prisma: PrismaService) {}

  async create(
    userId: string,
    file: UploadedImageFile,
    caption?: string,
  ): Promise<ShotView> {
    if (!file?.buffer) throw new BadRequestException('Aucun fichier reçu.');
    if (!isAllowedImageMime(file.mimetype)) {
      throw new BadRequestException(`Type de fichier non supporté: ${file.mimetype}`);
    }
    const filename = generateStoredFilename(file.mimetype);
    await writeFile(join(this.uploadDir, filename), file.buffer);
    const dims = pngDimensions(file.buffer);

    const shot = await this.prisma.shot.create({
      data: {
        authorId: userId,
        filename,
        caption: caption?.trim() || null,
        width: dims?.w ?? null,
        height: dims?.h ?? null,
      },
      include: { author: true },
    });
    this.logger.log(`shot ${shot.id} par ${userId}`);
    return toView(shot, false);
  }

  async feed(
    userId: string,
    limit = 30,
    cursor?: string,
  ): Promise<{ items: ShotView[]; nextCursor: string | null }> {
    const shots = await this.prisma.shot.findMany({
      take: limit + 1,
      ...(cursor ? { cursor: { id: cursor }, skip: 1 } : {}),
      orderBy: { createdAt: 'desc' },
      include: {
        author: true,
        likes: { where: { userId }, select: { userId: true } },
      },
    });
    const hasMore = shots.length > limit;
    const page = hasMore ? shots.slice(0, limit) : shots;
    return {
      items: page.map((s) => toView(s, s.likes.length > 0)),
      nextCursor: hasMore ? page[page.length - 1].id : null,
    };
  }

  async mine(userId: string): Promise<ShotView[]> {
    const shots = await this.prisma.shot.findMany({
      where: { authorId: userId },
      orderBy: { createdAt: 'desc' },
      include: {
        author: true,
        likes: { where: { userId }, select: { userId: true } },
      },
    });
    return shots.map((s) => toView(s, s.likes.length > 0));
  }

  async toggleLike(
    userId: string,
    shotId: string,
  ): Promise<{ liked: boolean; likeCount: number }> {
    const shot = await this.prisma.shot.findUnique({ where: { id: shotId } });
    if (!shot) throw new NotFoundException('Screenshot introuvable.');
    const existing = await this.prisma.shotLike.findUnique({
      where: { shotId_userId: { shotId, userId } },
    });
    if (existing) {
      await this.prisma.$transaction([
        this.prisma.shotLike.delete({ where: { shotId_userId: { shotId, userId } } }),
        this.prisma.shot.update({
          where: { id: shotId },
          data: { likeCount: { decrement: 1 } },
        }),
      ]);
      return { liked: false, likeCount: Math.max(0, shot.likeCount - 1) };
    }
    await this.prisma.$transaction([
      this.prisma.shotLike.create({ data: { shotId, userId } }),
      this.prisma.shot.update({
        where: { id: shotId },
        data: { likeCount: { increment: 1 } },
      }),
    ]);
    return { liked: true, likeCount: shot.likeCount + 1 };
  }

  async remove(userId: string, shotId: string): Promise<void> {
    const shot = await this.prisma.shot.findUnique({ where: { id: shotId } });
    if (!shot) throw new NotFoundException('Screenshot introuvable.');
    if (shot.authorId !== userId) {
      throw new ForbiddenException("Ce screenshot n'est pas le tien.");
    }
    await this.prisma.shotLike.deleteMany({ where: { shotId } });
    await this.prisma.shot.delete({ where: { id: shotId } });
    try {
      await unlink(join(this.uploadDir, shot.filename));
    } catch {
      // fichier déjà absent — sans conséquence
    }
  }
}

type ShotWithAuthor = {
  id: string;
  filename: string;
  caption: string | null;
  width: number | null;
  height: number | null;
  likeCount: number;
  createdAt: Date;
  author: {
    id: string;
    displayName: string | null;
    minecraftUsername: string;
    avatarUrl: string | null;
  };
};

function toView(shot: ShotWithAuthor, likedByMe: boolean): ShotView {
  return {
    id: shot.id,
    url: buildPublicUrl(shot.filename),
    caption: shot.caption,
    width: shot.width,
    height: shot.height,
    likeCount: shot.likeCount,
    likedByMe,
    createdAt: shot.createdAt.toISOString(),
    author: {
      id: shot.author.id,
      name: shot.author.displayName || shot.author.minecraftUsername,
      avatarUrl: shot.author.avatarUrl ?? null,
    },
  };
}

/** Dimensions d'un PNG (IHDR : largeur @16, hauteur @20, big-endian). */
function pngDimensions(buf: Buffer): { w: number; h: number } | null {
  if (buf.length >= 24 && buf[0] === 0x89 && buf[1] === 0x50) {
    return { w: buf.readUInt32BE(16), h: buf.readUInt32BE(20) };
  }
  return null;
}
