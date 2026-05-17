import {
  ForbiddenException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { MessageAuthor, WhitelistMessage } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { WebhooksService } from '../webhooks/webhooks.service';

export interface WhitelistMessageDto {
  id: string;
  applicationId: string;
  authorType: MessageAuthor;
  authorId: string | null;
  authorName: string | null;
  content: string;
  attachments: { name?: string; url: string }[];
  createdAt: string;
}

export interface PostUserMessageInput {
  content: string;
  attachmentUrls?: string[];
}

export interface PostStaffMessageInput {
  discordMessageId: string;
  authorDiscordId: string;
  authorName: string;
  content: string;
  attachmentUrls?: string[];
}

/**
 * Conversation staff↔candidat liee a une candidature whitelist. Les
 * messages utilisateurs sont POST par le launcher (JWT user) et relayes
 * en best-effort vers le thread Discord. Les reponses staff sont POST
 * par le bot (HMAC) lorsque celui-ci detecte un messageCreate dans le
 * thread.
 */
@Injectable()
export class WhitelistMessagesService {
  private readonly logger = new Logger(WhitelistMessagesService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly webhooks: WebhooksService,
  ) {}

  /**
   * Liste les messages de la candidature courante de l'utilisateur. Retourne
   * une liste vide s'il n'a pas de candidature.
   */
  async listMine(userId: string): Promise<WhitelistMessageDto[]> {
    const app = await this.prisma.whitelistApplication.findUnique({
      where: { userId },
      select: { id: true },
    });
    if (!app) return [];
    const messages = await this.prisma.whitelistMessage.findMany({
      where: { applicationId: app.id },
      orderBy: { createdAt: 'asc' },
    });
    return messages.map((m) => this.toDto(m));
  }

  /**
   * Cree un message utilisateur (USER), persiste cote DB, puis relaie
   * vers le thread Discord (best-effort). On stocke le discordMessageId
   * retourne par le bot pour pouvoir dedup quand le listener messageCreate
   * fire.
   */
  async postMine(
    userId: string,
    input: PostUserMessageInput,
  ): Promise<WhitelistMessageDto> {
    const app = await this.prisma.whitelistApplication.findUnique({
      where: { userId },
    });
    if (!app) {
      throw new NotFoundException('Aucune candidature en cours.');
    }
    if (app.status === 'APPROVED') {
      // Une fois acceptes, le canal staff↔candidat n'a plus de raison
      // d'etre — le user passe par les tickets pour toute question
      // ulterieure (cf staff service).
      throw new ForbiddenException(
        'Ta candidature est acceptee. Pour toute question, ouvre un ticket.',
      );
    }
    const user = await this.prisma.user.findUnique({ where: { id: userId } });
    const attachmentUrls = input.attachmentUrls ?? [];
    const created = await this.prisma.whitelistMessage.create({
      data: {
        applicationId: app.id,
        authorType: 'USER',
        authorId: userId,
        authorName: user?.minecraftUsername ?? null,
        content: input.content,
        attachments: attachmentUrls.map((url) => ({ url })),
      },
    });

    // Relais best-effort vers Discord. Si le thread n'existe pas encore
    // (webhook initial n'a pas encore repondu) on log et on continue.
    if (app.discordThreadId) {
      try {
        const relay = await this.webhooks.whitelistMessageRelay({
          threadId: app.discordThreadId,
          authorPseudo: user?.minecraftUsername ?? 'Joueur',
          content: input.content,
          attachmentUrls,
        });
        if (relay?.messageId) {
          await this.prisma.whitelistMessage.update({
            where: { id: created.id },
            data: { discordMessageId: relay.messageId },
          });
        }
      } catch (err) {
        this.logger.warn(
          `Relay whitelist message échec : ${(err as Error).message}`,
        );
      }
    } else {
      this.logger.warn(
        `whitelist app ${app.id} sans discordThreadId — message ${created.id} non relaye`,
      );
    }
    return this.toDto(created);
  }

  /**
   * Reception d'un message staff depuis le bot Discord (listener
   * messageCreate). Idempotent : si discordMessageId est deja stocke,
   * on retourne le message existant au lieu d'en creer un doublon.
   */
  async postStaffMessage(
    applicationId: string,
    input: PostStaffMessageInput,
  ): Promise<WhitelistMessageDto> {
    const app = await this.prisma.whitelistApplication.findUnique({
      where: { id: applicationId },
    });
    if (!app) {
      throw new NotFoundException('Candidature introuvable.');
    }
    // Une candidature decidee n'accepte plus de messages staff : la
    // conversation est cloturee cote API meme si le thread Discord
    // existe encore. Le bot devrait avoir lock+archive le thread via
    // status-update, mais un staff avec manage_threads peut quand meme
    // poster — on rejette ici pour eviter l'incoherence cote launcher.
    if (app.status === 'APPROVED' || app.status === 'REJECTED') {
      throw new ForbiddenException(
        `Candidature ${app.status} : pas de nouveau message accepte.`,
      );
    }

    // Dedup par discordMessageId. Le bot peut re-emettre dans certains
    // cas (rate limit, retry) — on ne veut pas creer de doublons.
    const existing = await this.prisma.whitelistMessage.findFirst({
      where: { applicationId, discordMessageId: input.discordMessageId },
    });
    if (existing) {
      return this.toDto(existing);
    }

    const created = await this.prisma.whitelistMessage.create({
      data: {
        applicationId,
        authorType: 'STAFF',
        authorId: input.authorDiscordId,
        authorName: input.authorName,
        content: input.content,
        attachments: (input.attachmentUrls ?? []).map((url) => ({ url })),
        discordMessageId: input.discordMessageId,
      },
    });
    this.logger.log(
      `whitelist app ${applicationId} ← staff message ${created.id} from ${input.authorName}`,
    );
    return this.toDto(created);
  }

  /**
   * Message staff envoye depuis le panel Next (apps/admin). Pas de
   * discordMessageId source (tape dans le panel, pas dans Discord) :
   * on persiste d'abord puis on relaie vers le thread Discord pour
   * que la conversation reste synchronisee. Le `client.user.id`-filter
   * cote bot evite la boucle (relay = bot post → listener skip).
   */
  async postPanelStaffMessage(
    applicationId: string,
    input: { staffUserId: string; content: string },
  ): Promise<WhitelistMessageDto> {
    const app = await this.prisma.whitelistApplication.findUnique({
      where: { id: applicationId },
    });
    if (!app) {
      throw new NotFoundException('Candidature introuvable.');
    }
    const staff = await this.prisma.user.findUnique({
      where: { id: input.staffUserId },
      select: { id: true, minecraftUsername: true, discordUsername: true },
    });
    const authorName =
      staff?.discordUsername ?? staff?.minecraftUsername ?? 'Staff';

    const created = await this.prisma.whitelistMessage.create({
      data: {
        applicationId,
        authorType: 'STAFF',
        authorId: input.staffUserId,
        authorName,
        content: input.content.trim(),
      },
    });

    if (app.discordThreadId) {
      try {
        const relay = await this.webhooks.whitelistMessageRelay({
          threadId: app.discordThreadId,
          authorPseudo: `[Staff] ${authorName}`,
          content: created.content,
        });
        if (relay?.messageId) {
          await this.prisma.whitelistMessage.update({
            where: { id: created.id },
            data: { discordMessageId: relay.messageId },
          });
        }
      } catch (err) {
        this.logger.warn(
          `Relay panel staff message échec : ${(err as Error).message}`,
        );
      }
    }

    return this.toDto(created);
  }

  private toDto(m: WhitelistMessage): WhitelistMessageDto {
    // Le champ `attachments` est un Json côté Prisma — on le caste vers la
    // forme attendue côté client. Si la donnée est mal formée (ex: legacy
    // ou écriture manuelle), on retombe sur un tableau vide.
    let attachments: WhitelistMessageDto['attachments'] = [];
    if (Array.isArray(m.attachments)) {
      attachments = (m.attachments as unknown[])
        .filter((a): a is { url: string } => {
          return typeof a === 'object' && a !== null && 'url' in a;
        })
        .map((a) => ({ url: a.url }));
    }
    return {
      id: m.id,
      applicationId: m.applicationId,
      authorType: m.authorType,
      authorId: m.authorId,
      authorName: m.authorName,
      content: m.content,
      attachments,
      createdAt: m.createdAt.toISOString(),
    };
  }
}
