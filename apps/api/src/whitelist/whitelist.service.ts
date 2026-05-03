import {
  ConflictException,
  ForbiddenException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { AppStatus, WhitelistApplication } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { WebhooksService } from '../webhooks/webhooks.service';
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
  private readonly logger = new Logger(WhitelistService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly webhooks: WebhooksService,
  ) {}

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
    await this.notifyStaff(userId, created);
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
    await this.notifyStaff(userId, updated);
    return this.toDto(updated);
  }

  /**
   * Retire la candidature courante. Autorise tant que le statut n'est
   * pas APPROVED — on ne laisse pas un joueur valide s'auto-supprimer ;
   * pour ca il doit ouvrir un ticket et passer par le staff.
   */
  async withdraw(userId: string): Promise<void> {
    const app = await this.prisma.whitelistApplication.findUnique({
      where: { userId },
    });
    if (!app) {
      throw new NotFoundException('Aucune candidature a retirer.');
    }
    if (app.status === AppStatus.APPROVED) {
      throw new ForbiddenException(
        "Tu as ete accepte. Pour quitter le serveur, contacte le staff via un ticket.",
      );
    }
    await this.prisma.whitelistApplication.delete({ where: { userId } });
  }

  /**
   * Notifie le bot Discord qu'une candidature vient d'etre creee /
   * resoumise. Le bot ouvre un thread prive dans le salon staff. Echec
   * non-bloquant : le user a deja recu sa reponse 200.
   */
  private async notifyStaff(userId: string, app: WhitelistApplication) {
    const user = await this.prisma.user.findUnique({ where: { id: userId } });
    if (!user) return;
    try {
      await this.webhooks.whitelistSubmitted({
        applicationId: app.id,
        userPseudo: user.minecraftUsername,
        userId: user.id,
        characterName: app.characterName,
        characterAge: app.characterAge,
        background: app.background,
        motivation: app.motivation,
        discordUserId: user.discordUserId,
      });
    } catch (err) {
      this.logger.warn(
        `Webhook whitelist non transmis : ${(err as Error).message}`,
      );
    }
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
