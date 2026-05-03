import { Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';

export interface RulesDocument {
  version: string;
  content: string;
  publishedAt: string;
}

@Injectable()
export class RulesService {
  constructor(private readonly prisma: PrismaService) {}

  async getCurrent(): Promise<RulesDocument> {
    const rules = await this.prisma.rules.findFirst({
      where: { isCurrent: true },
      orderBy: { publishedAt: 'desc' },
    });
    if (!rules) {
      throw new NotFoundException('Aucun reglement publie pour le moment.');
    }
    return {
      version: rules.version,
      content: rules.content,
      publishedAt: rules.publishedAt.toISOString(),
    };
  }
}
