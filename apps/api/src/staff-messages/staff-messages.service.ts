import {
  BadGatewayException,
  BadRequestException,
  ForbiddenException,
  HttpException,
  Injectable,
  Logger,
  NotFoundException,
  ServiceUnavailableException,
} from '@nestjs/common';
import {
  Prisma,
  StaffDiscordChannel,
  StaffDiscordMessageStatus,
  type StaffDiscordMessage,
} from '@prisma/client';
import { AuditService } from '../audit/audit.service';
import type { RequestUser } from '../auth/current-user.decorator';
import { PrismaService } from '../prisma/prisma.service';
import {
  BotWebhookError,
  WebhooksService,
  type StaffCadreSpec,
  type StaffMessageResult,
} from '../webhooks/webhooks.service';
import type {
  ActionMessage,
  BlocDto,
  Canal,
  PingOption,
  StaffMessageBodyDto,
} from './dto/staff-messages.dto';
import { StaffMediaService } from './staff-media.service';

const COULEUR_DEFAUT = '#A0182B';
const TEXTE_MAX = 4000;
const IMAGES_MAX = 10;

const CANAL_ENUM: Record<Canal, StaffDiscordChannel> = {
  annonces: StaffDiscordChannel.ANNONCES,
  'dev-updates': StaffDiscordChannel.DEV_UPDATES,
  'build-updates': StaffDiscordChannel.BUILD_UPDATES,
};
const ENUM_CANAL: Record<StaffDiscordChannel, Canal> = {
  ANNONCES: 'annonces',
  DEV_UPDATES: 'dev-updates',
  BUILD_UPDATES: 'build-updates',
};
const STATUT: Record<
  StaffDiscordMessageStatus,
  'brouillon' | 'publie' | 'retire'
> = {
  DRAFT: 'brouillon',
  PUBLISHED: 'publie',
  REMOVED: 'retire',
};

/** Vue renvoyée au panel. */
export interface StaffMessageView {
  id: string;
  canal: Canal;
  statut: 'brouillon' | 'publie' | 'retire';
  titre: string;
  couleur: string;
  blocs: BlocDto[];
  ping: PingOption;
  discordUrl: string | null;
  discordMessageId: string | null;
  auteur: string;
  modifiePar: string | null;
  creeLe: string;
  modifieLe: string;
  publieLe: string | null;
  retireLe: string | null;
  /** id de média → URL signée (1 h) pour afficher les images dans le panel. */
  medias: Record<string, string>;
  /** Résultat du dernier aperçu (lien Discord du salon de test, ou rendu JSON). */
  apercu?: { url: string | null; rendu?: unknown };
}

/** Titre d'historique : premier titre markdown, sinon début du premier texte. */
export function deriveTitre(blocs: BlocDto[]): string {
  for (const b of blocs) {
    const lignes = (b.texte ?? '').split('\n').map((l) => l.trim());
    const titre = lignes.find((l) => /^#{1,3}\s+\S/.test(l));
    if (titre) return titre.replace(/^#{1,3}\s+/, '').slice(0, 120);
  }
  const premier = blocs.find((b) => b.texte?.trim())?.texte?.trim() ?? '';
  return premier
    ? premier
        .split('\n')[0]
        .replace(/^-#\s*/, '')
        .slice(0, 120)
    : '(sans titre)';
}

function imagesDe(blocs: BlocDto[]): string[] {
  return blocs.flatMap((b) => b.images ?? []);
}

/**
 * Contrôles de forme avant tout appel au bot (le bot revalide après résolution
 * des mentions — c'est lui qui fait foi sur la limite exacte de 4000).
 */
function verifierBlocs(blocs: BlocDto[]): void {
  const erreurs: string[] = [];
  let texte = 0;
  blocs.forEach((b, i) => {
    const vide =
      !b.texte?.trim() &&
      !(b.images?.length ?? 0) &&
      !b.separateur &&
      !(b.liens?.length ?? 0);
    if (vide) erreurs.push(`bloc ${i + 1} vide`);
    texte += b.texte?.length ?? 0;
  });
  if (texte > TEXTE_MAX)
    erreurs.push(`texte total ${texte} caractères (max ${TEXTE_MAX})`);
  const n = imagesDe(blocs).length;
  if (n > IMAGES_MAX)
    erreurs.push(`${n} images (max ${IMAGES_MAX} par message)`);
  if (erreurs.length)
    throw new BadRequestException({
      message: 'Message invalide',
      details: erreurs,
    });
}

function verifierPing(canal: Canal, ping: PingOption): void {
  if (ping === 'everyone' && canal !== 'annonces') {
    throw new BadRequestException('@everyone est réservé aux annonces.');
  }
  if (ping === 'testeurs' && canal !== 'dev-updates') {
    throw new BadRequestException('@Testeur est réservé aux dev updates.');
  }
}

/** Traduit une erreur du bot en exception HTTP lisible côté panel. */
function erreurBot(err: unknown): never {
  if (!(err instanceof BotWebhookError)) throw err;
  const body = { message: `Discord : ${err.message}`, details: err.details };
  if (err.status === 0) throw new BadGatewayException(body);
  if (err.status === 400 || err.status === 413)
    throw new BadRequestException(body);
  if (err.status === 403) throw new ForbiddenException(body);
  if (err.status === 404) throw new NotFoundException(body);
  if (err.status === 503) throw new ServiceUnavailableException(body);
  throw new HttpException(body, 502);
}

@Injectable()
export class StaffMessagesService {
  private readonly logger = new Logger(StaffMessagesService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly webhooks: WebhooksService,
    private readonly audit: AuditService,
    private readonly media: StaffMediaService,
  ) {}

  async list(canal?: Canal): Promise<StaffMessageView[]> {
    const rows = await this.prisma.staffDiscordMessage.findMany({
      where: canal ? { channel: CANAL_ENUM[canal] } : undefined,
      orderBy: { updatedAt: 'desc' },
      take: 100,
    });
    return rows.map((r) => this.view(r));
  }

  async get(id: string): Promise<StaffMessageView> {
    return this.view(await this.find(id));
  }

  async create(
    user: RequestUser,
    dto: StaffMessageBodyDto,
  ): Promise<StaffMessageView> {
    const ping = dto.ping ?? 'aucun';
    verifierBlocs(dto.blocs);
    verifierPing(dto.canal, ping);
    await this.media.assertAll(imagesDe(dto.blocs));
    const auteur = await this.nom(user.sub);
    const row = await this.prisma.staffDiscordMessage.create({
      data: {
        channel: CANAL_ENUM[dto.canal],
        title: deriveTitre(dto.blocs),
        color: dto.couleur ?? COULEUR_DEFAUT,
        blocks: dto.blocs as unknown as Prisma.InputJsonValue,
        ping,
        createdById: user.sub,
        createdByName: auteur,
      },
    });
    void this.audit.log({
      actorId: user.sub,
      action: 'discord.message.create',
      targetEntity: `staff-discord-message:${row.id}`,
      metadata: { canal: dto.canal, titre: row.title },
      source: 'panel',
    });
    return this.appliquer(row, dto.action ?? 'brouillon', user, auteur);
  }

  /**
   * Modification. Brouillon / retiré : le contenu est enregistré puis l'action
   * appliquée (aperçu, publication = nouveau message). Publié : « publier »
   * RÉÉCRIT le message Discord (même lien) avant d'enregistrer ; « apercu »
   * montre le nouveau contenu sans rien enregistrer ; « brouillon » est refusé
   * (la base divergerait de Discord).
   */
  async update(
    id: string,
    user: RequestUser,
    dto: StaffMessageBodyDto,
  ): Promise<StaffMessageView> {
    const row = await this.find(id);
    const ping = dto.ping ?? 'aucun';
    verifierBlocs(dto.blocs);
    verifierPing(dto.canal, ping);
    await this.media.assertAll(imagesDe(dto.blocs));
    const auteur = await this.nom(user.sub);
    const action: ActionMessage =
      dto.action ?? (row.status === 'PUBLISHED' ? 'publier' : 'brouillon');
    const contenu = {
      channel: CANAL_ENUM[dto.canal],
      title: deriveTitre(dto.blocs),
      color: dto.couleur ?? COULEUR_DEFAUT,
      blocks: dto.blocs as unknown as Prisma.InputJsonValue,
      ping,
      updatedById: user.sub,
      updatedByName: auteur,
    };

    if (row.status === 'PUBLISHED') {
      if (CANAL_ENUM[dto.canal] !== row.channel) {
        throw new BadRequestException(
          'Salon non modifiable après publication : retire le message puis republie-le.',
        );
      }
      if (action === 'brouillon') {
        throw new BadRequestException(
          'Message publié : « Publier » met Discord à jour, « Aperçu » montre le rendu.',
        );
      }
      if (action === 'apercu') {
        const apercu = await this.envoyerApercu({
          ...row,
          ...contenu,
        } as StaffDiscordMessage);
        const saved = await this.prisma.staffDiscordMessage.update({
          where: { id },
          data: { previewMessageId: apercu.messageId ?? row.previewMessageId },
        });
        return {
          ...this.view(saved),
          apercu: { url: apercu.url, rendu: apercu.rendu },
        };
      }
      // publier → édition du message existant.
      const res = await this.webhooks
        .staffMessageEdit({
          canal: dto.canal,
          messageId: row.discordMessageId!,
          spec: this.spec({ ...row, ...contenu } as StaffDiscordMessage),
          images: await this.media.forBot(imagesDe(dto.blocs)),
          ping,
        })
        .catch(erreurBot);
      const saved = await this.prisma.staffDiscordMessage.update({
        where: { id },
        data: { ...contenu, discordUrl: res.url ?? row.discordUrl },
      });
      void this.audit.log({
        actorId: user.sub,
        action: 'discord.message.edit',
        targetEntity: `staff-discord-message:${id}`,
        metadata: {
          canal: dto.canal,
          discordMessageId: row.discordMessageId,
          titre: saved.title,
        },
        source: 'panel',
      });
      return this.view(saved);
    }

    const saved = await this.prisma.staffDiscordMessage.update({
      where: { id },
      data: contenu,
    });
    void this.audit.log({
      actorId: user.sub,
      action: 'discord.message.update',
      targetEntity: `staff-discord-message:${id}`,
      metadata: { canal: dto.canal, titre: saved.title },
      source: 'panel',
    });
    return this.appliquer(saved, action, user, auteur);
  }

  /** Publié → supprimé de Discord, gardé en historique (« retiré »). Brouillon / retiré → effacé. */
  async remove(
    id: string,
    user: RequestUser,
  ): Promise<{ removed: true; deleted: boolean }> {
    const row = await this.find(id);
    if (row.previewMessageId) {
      await this.webhooks
        .staffMessageDelete({
          canal: 'apercu',
          messageId: row.previewMessageId,
        })
        .catch((e: Error) =>
          this.logger.warn(
            `aperçu ${row.previewMessageId} non supprimé : ${e.message}`,
          ),
        );
    }
    if (row.status === 'PUBLISHED' && row.discordMessageId) {
      const res = await this.webhooks
        .staffMessageDelete({
          canal: ENUM_CANAL[row.channel],
          messageId: row.discordMessageId,
        })
        .catch(erreurBot);
      await this.prisma.staffDiscordMessage.update({
        where: { id },
        data: {
          status: 'REMOVED',
          removedAt: new Date(),
          previewMessageId: null,
          updatedById: user.sub,
          updatedByName: await this.nom(user.sub),
        },
      });
      void this.audit.log({
        actorId: user.sub,
        action: 'discord.message.remove',
        targetEntity: `staff-discord-message:${id}`,
        metadata: {
          canal: ENUM_CANAL[row.channel],
          discordMessageId: row.discordMessageId,
          dejaAbsent: res.absent ?? false,
          titre: row.title,
        },
        source: 'panel',
      });
      return { removed: true, deleted: false };
    }
    await this.prisma.staffDiscordMessage.delete({ where: { id } });
    void this.audit.log({
      actorId: user.sub,
      action: 'discord.message.delete',
      targetEntity: `staff-discord-message:${id}`,
      metadata: {
        canal: ENUM_CANAL[row.channel],
        statut: STATUT[row.status],
        titre: row.title,
      },
      source: 'panel',
    });
    return { removed: true, deleted: true };
  }

  // ── interne ─────────────────────────────────────────────

  private async appliquer(
    row: StaffDiscordMessage,
    action: ActionMessage,
    user: RequestUser,
    auteur: string,
  ): Promise<StaffMessageView> {
    if (action === 'apercu') {
      const apercu = await this.envoyerApercu(row);
      const saved = await this.prisma.staffDiscordMessage.update({
        where: { id: row.id },
        data: { previewMessageId: apercu.messageId ?? row.previewMessageId },
      });
      void this.audit.log({
        actorId: user.sub,
        action: 'discord.message.preview',
        targetEntity: `staff-discord-message:${row.id}`,
        metadata: {
          canal: ENUM_CANAL[row.channel],
          posteEnTest: !!apercu.messageId,
        },
        source: 'panel',
      });
      return {
        ...this.view(saved),
        apercu: { url: apercu.url, rendu: apercu.rendu },
      };
    }
    if (action === 'publier') {
      const blocs = row.blocks as unknown as BlocDto[];
      const res = await this.webhooks
        .staffMessage({
          canal: ENUM_CANAL[row.channel],
          mode: 'publier',
          spec: this.spec(row),
          images: await this.media.forBot(imagesDe(blocs)),
          ping: row.ping as PingOption,
        })
        .catch(erreurBot);
      if (!res?.messageId)
        throw new BadGatewayException(
          'Discord : réponse du bot sans identifiant de message.',
        );
      // L'aperçu a servi : on le retire du salon de test (sans bloquer).
      if (row.previewMessageId) {
        await this.webhooks
          .staffMessageDelete({
            canal: 'apercu',
            messageId: row.previewMessageId,
          })
          .catch((e: Error) =>
            this.logger.warn(`aperçu non supprimé : ${e.message}`),
          );
      }
      const saved = await this.prisma.staffDiscordMessage.update({
        where: { id: row.id },
        data: {
          status: 'PUBLISHED',
          discordMessageId: res.messageId,
          discordChannelId: res.channelId ?? null,
          discordUrl: res.url,
          previewMessageId: null,
          publishedAt: new Date(),
          removedAt: null,
          updatedById: user.sub,
          updatedByName: auteur,
        },
      });
      void this.audit.log({
        actorId: user.sub,
        action: 'discord.message.publish',
        targetEntity: `staff-discord-message:${row.id}`,
        metadata: {
          canal: ENUM_CANAL[row.channel],
          discordMessageId: res.messageId,
          ping: row.ping,
          titre: row.title,
        },
        source: 'panel',
      });
      return this.view(saved);
    }
    return this.view(row);
  }

  private async envoyerApercu(
    row: StaffDiscordMessage,
  ): Promise<StaffMessageResult> {
    const blocs = row.blocks as unknown as BlocDto[];
    return this.webhooks
      .staffMessage({
        canal: ENUM_CANAL[row.channel],
        mode: 'apercu',
        spec: this.spec(row),
        images: await this.media.forBot(imagesDe(blocs)),
        ping: row.ping as PingOption,
        cleRemplacement: row.previewMessageId ?? undefined,
      })
      .catch(erreurBot);
  }

  private spec(row: StaffDiscordMessage): StaffCadreSpec {
    return {
      couleur: row.color,
      blocs: row.blocks as unknown as StaffCadreSpec['blocs'],
    };
  }

  private async find(id: string): Promise<StaffDiscordMessage> {
    const row = await this.prisma.staffDiscordMessage.findUnique({
      where: { id },
    });
    if (!row) throw new NotFoundException('Message introuvable.');
    return row;
  }

  private async nom(userId: string): Promise<string> {
    const u = await this.prisma.user.findUnique({
      where: { id: userId },
      select: {
        displayName: true,
        discordUsername: true,
        minecraftUsername: true,
      },
    });
    return (
      u?.displayName ?? u?.discordUsername ?? u?.minecraftUsername ?? 'staff'
    );
  }

  private view(r: StaffDiscordMessage): StaffMessageView {
    const blocs = (r.blocks as unknown as BlocDto[]) ?? [];
    return {
      id: r.id,
      canal: ENUM_CANAL[r.channel],
      statut: STATUT[r.status],
      titre: r.title,
      couleur: r.color,
      blocs,
      ping: r.ping as PingOption,
      discordUrl: r.discordUrl,
      discordMessageId: r.discordMessageId,
      auteur: r.createdByName,
      modifiePar: r.updatedByName,
      creeLe: r.createdAt.toISOString(),
      modifieLe: r.updatedAt.toISOString(),
      publieLe: r.publishedAt?.toISOString() ?? null,
      retireLe: r.removedAt?.toISOString() ?? null,
      medias: this.media.signedUrls(imagesDe(blocs)),
    };
  }
}
