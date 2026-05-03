import {
  ConflictException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { AppStatus, WhitelistApplication } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { SubmitWhitelistDto } from './dto/whitelist.dto';

export interface WhitelistApplicationDto {
  id: string;
  status: AppStatus;
  characterName: string;
  characterAge: number;
  background: string;
  motivation: string;
  submittedAt: string;
  reviewedAt: string | null;
  reviewNotes: string | null;
}

@Injectable()
export class WhitelistService {
  constructor(private readonly prisma: PrismaService) {}

  async getMine(userId: string): Promise<{ application: WhitelistApplicationDto | null }> {
    const app = await this.prisma.whitelistApplication.findUnique({ where: { userId } });
    return { application: app ? this.toDto(app) : null };
  }

  async submit(
    userId: string,
    dto: SubmitWhitelistDto,
  ): Promise<WhitelistApplicationDto> {
    const existing = await this.prisma.whitelistApplication.findUnique({ where: { userId } });
    if (existing) {
      throw new ConflictException(
        'Tu as deja une candidature en cours. Pour modifier, utilise PATCH /whitelist/me.',
      );
    }
    const created = await this.prisma.whitelistApplication.create({
      data: {
        userId,
        characterName: dto.characterName.trim(),
        characterAge: dto.characterAge,
        background: dto.background.trim(),
        motivation: dto.motivation.trim(),
        status: AppStatus.PENDING,
      },
    });
    return this.toDto(created);
  }

  async resubmit(
    userId: string,
    dto: SubmitWhitelistDto,
  ): Promise<WhitelistApplicationDto> {
    const existing = await this.prisma.whitelistApplication.findUnique({ where: { userId } });
    if (!existing) {
      throw new NotFoundException(
        'Aucune candidature a modifier. Utilise POST /whitelist pour la creer.',
      );
    }
    if (existing.status !== AppStatus.NEEDS_REVISION && existing.status !== AppStatus.REJECTED) {
      throw new ForbiddenException(
        `Statut ${existing.status} : modification non autorisee. Le staff doit demander une revision.`,
      );
    }
    const updated = await this.prisma.whitelistApplication.update({
      where: { userId },
      data: {
        characterName: dto.characterName.trim(),
        characterAge: dto.characterAge,
        background: dto.background.trim(),
        motivation: dto.motivation.trim(),
        status: AppStatus.PENDING,
        submittedAt: new Date(),
        reviewedAt: null,
        reviewNotes: null,
      },
    });
    return this.toDto(updated);
  }

  private toDto(app: WhitelistApplication): WhitelistApplicationDto {
    return {
      id: app.id,
      status: app.status,
      characterName: app.characterName,
      characterAge: app.characterAge,
      background: app.background,
      motivation: app.motivation,
      submittedAt: app.submittedAt.toISOString(),
      reviewedAt: app.reviewedAt?.toISOString() ?? null,
      reviewNotes: app.reviewNotes,
    };
  }
}
