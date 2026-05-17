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

// Schéma riche v2 (cf launcher WhitelistDraft + Prisma model WhitelistApplication).
// On expose tout au launcher pour qu'il puisse réafficher la candidature en
// récap (ApplicationPanel sur StatusChatPage), pas juste les champs de base.
export interface WhitelistApplicationDto {
  id: string;
  status: AppStatus;
  // Étape 1
  dob: string; // ISO YYYY-MM-DD
  motivation: string;
  experience: string;
  availability: string;
  // Étape 2
  firstName: string;
  lastName: string;
  village: string;
  support: string | null;
  history: string;
  appearance: string;
  objectives: string;
  // Méta
  submittedAt: string;
  reviewedAt: string | null;
  reviewNotes: string | null;
  // Assignation staff (cf C3 — flow DM bot). Permet au launcher
  // d'afficher "Pris en charge par @X depuis Yh" et, apres 4h sans
  // suite, un bouton "Demander une reprise" qui appelle POST
  // /v1/whitelist/me/reclaim.
  assignee: { username: string | null } | null;
  assignedAt: string | null;
}

@Injectable()
export class WhitelistService {
  private readonly logger = new Logger(WhitelistService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly webhooks: WebhooksService,
  ) {}

  async getMine(userId: string): Promise<{ application: WhitelistApplicationDto | null }> {
    const app = await this.prisma.whitelistApplication.findUnique({
      where: { userId },
      include: {
        assignedTo: { select: { minecraftUsername: true, discordUsername: true } },
      },
    });
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
        ...this.normalize(dto),
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
        ...this.normalize(dto),
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
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      select: { minecraftUsername: true },
    });
    const threadId = app.discordThreadId;
    const messageId = app.discordMessageId;
    await this.prisma.whitelistApplication.delete({ where: { userId } });
    this.logger.log(`whitelist withdrawn by user ${userId} (app ${app.id})`);

    if (threadId || messageId) {
      void this.webhooks
        .statusUpdate({
          kind: 'whitelist',
          threadId,
          messageId,
          status: 'DELETED',
          actorName: user?.minecraftUsername ?? 'le joueur',
        })
        .catch((err) =>
          this.logger.warn(
            `statusUpdate whitelist withdraw echec : ${(err as Error).message}`,
          ),
        );
    }
  }

  // Normalise : trim les chaines + parse la date. Sépare la logique de
  // serialisation pour pouvoir la réutiliser entre submit et resubmit.
  private normalize(dto: SubmitWhitelistDto) {
    const support = dto.support?.trim() ?? '';
    return {
      dob: new Date(dto.dob),
      motivation: dto.motivation.trim(),
      experience: dto.experience.trim(),
      availability: dto.availability.trim(),
      firstName: dto.firstName.trim(),
      lastName: dto.lastName.trim(),
      village: dto.village.trim(),
      support: support === '' ? null : support,
      history: dto.history.trim(),
      appearance: dto.appearance.trim(),
      objectives: dto.objectives.trim(),
    };
  }

  /**
   * Notifie le bot Discord qu'une candidature vient d'etre creee /
   * resoumise. Le bot ouvre un thread public dans le salon staff. Echec
   * non-bloquant : le user a deja recu sa reponse 200.
   *
   * Le bot retourne l'id du thread cree → on le persiste sur l'application
   * pour pouvoir relayer les messages user→discord (cf phase 2 chat).
   */
  private async notifyStaff(userId: string, app: WhitelistApplication) {
    const user = await this.prisma.user.findUnique({ where: { id: userId } });
    if (!user) return;
    try {
      const result = await this.webhooks.whitelistSubmitted({
        applicationId: app.id,
        userPseudo: user.minecraftUsername,
        userId: user.id,
        discordUserId: user.discordUserId,
        firstName: app.firstName,
        lastName: app.lastName,
        village: app.village,
        support: app.support,
        dob: app.dob.toISOString(),
        motivation: app.motivation,
        experience: app.experience,
        availability: app.availability,
        history: app.history,
        appearance: app.appearance,
        objectives: app.objectives,
      });
      // Nouveau flow C3 : le bot retourne {messageId} (message public
      // dans le salon staff avec bouton Prendre en charge). Legacy
      // {threadId} reste supporte pour les anciens deploys mais ne
      // devrait plus etre renvoye.
      const data: { discordMessageId?: string; discordThreadId?: string } = {};
      if (result?.messageId) data.discordMessageId = result.messageId;
      if (result?.threadId) data.discordThreadId = result.threadId;
      if (Object.keys(data).length > 0) {
        await this.prisma.whitelistApplication.update({
          where: { id: app.id },
          data,
        });
        this.logger.log(
          `whitelist app ${app.id} → ${result?.messageId ? `message ${result.messageId}` : `thread ${result?.threadId}`}`,
        );
      }
    } catch (err) {
      this.logger.warn(
        `Webhook whitelist non transmis : ${(err as Error).message}`,
      );
    }
  }

  private toDto(
    app: WhitelistApplication & {
      assignedTo?: { minecraftUsername: string; discordUsername: string | null } | null;
    },
  ): WhitelistApplicationDto {
    // dob est stocké en DateTime mais on l'expose en YYYY-MM-DD côté client
    // (cohérent avec l'input <input type="date"> qui produit ce format).
    const dobIso = app.dob.toISOString().slice(0, 10);
    return {
      id: app.id,
      status: app.status,
      dob: dobIso,
      motivation: app.motivation,
      experience: app.experience,
      availability: app.availability,
      firstName: app.firstName,
      lastName: app.lastName,
      village: app.village,
      support: app.support,
      history: app.history,
      appearance: app.appearance,
      objectives: app.objectives,
      submittedAt: app.submittedAt.toISOString(),
      reviewedAt: app.reviewedAt?.toISOString() ?? null,
      reviewNotes: app.reviewNotes,
      assignee: app.assignedTo
        ? {
            username:
              app.assignedTo.discordUsername ??
              app.assignedTo.minecraftUsername,
          }
        : null,
      assignedAt: app.assignedAt?.toISOString() ?? null,
    };
  }
}
