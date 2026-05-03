import { Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';

export interface LoreDocument {
  version: string;
  content: string;
  publishedAt: string;
}

@Injectable()
export class LoreService {
  constructor(private readonly prisma: PrismaService) {}

  async getCurrent(): Promise<LoreDocument> {
    const lore = await this.prisma.lore.findFirst({
      where: { isCurrent: true },
      orderBy: { publishedAt: 'desc' },
    });
    if (!lore) {
      throw new NotFoundException('Aucun lore publie pour le moment.');
    }
    return {
      version: lore.version,
      content: lore.content,
      publishedAt: lore.publishedAt.toISOString(),
    };
  }
}
