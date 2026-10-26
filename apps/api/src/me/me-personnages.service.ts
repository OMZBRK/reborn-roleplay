import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { normaliserMcUuid } from '../game/game-personnages.regles';
import { PrismaService } from '../prisma/prisma.service';
import {
  comparerPersonnages,
  lireServeursPersonnages,
  vuePersonnage,
  type MesPersonnagesVue,
  type PersonnagesStaffVue,
} from './me-personnages';

/**
 * GET /v1/me/characters (CANDIDATURE_ET_DOUANE.md §4.6) : les personnages du
 * compte, sur les serveurs de prod seulement (me-personnages.ts). Lecture
 * seule : on joue et on crée en jeu. Le N° n'apparaît que dans cette vue du
 * propriétaire (et au panel staff), jamais dans un endpoint social ou public.
 */
@Injectable()
export class MePersonnagesService {
  private readonly logger = new Logger(MePersonnagesService.name);
  private readonly serveurs: string[];

  constructor(
    config: ConfigService,
    private readonly prisma: PrismaService,
  ) {
    const lu = lireServeursPersonnages(
      config.get<string>('REBORN_CHARACTERS_SERVER_IDS'),
    );
    for (const a of lu.avertissements) this.logger.warn(a);
    this.serveurs = lu.serveurs;
    this.logger.log(
      `« Mes personnages » : serveur(s) lu(s) ${this.serveurs.join(', ')} (REBORN_CHARACTERS_SERVER_IDS).`,
    );
  }

  /**
   * Personnages rattachés au compte à la synchronisation, plus ceux de son
   * compte Minecraft que la synchronisation n'a pas encore pu rattacher (compte
   * Reborn créé après coup). Jamais ceux rattachés à un autre compte.
   */
  async mesPersonnages(userId: string): Promise<MesPersonnagesVue> {
    const rows = await this.lire(userId, this.serveurs);
    return { characters: rows.map((r) => vuePersonnage(r, userId)) };
  }

  /**
   * Onglet Personnages de la fiche joueur du panel (PANEL_V2 §4.2) : mêmes
   * cartes, sur TOUS les serveurs (le staff voit aussi le dev et le build),
   * chacune marquée de son serveur et de sa visibilité dans le launcher.
   */
  async personnagesPourStaff(userId: string): Promise<PersonnagesStaffVue> {
    const rows = await this.lire(userId, null);
    return {
      serveursLauncher: [...this.serveurs],
      characters: rows.map((r) => ({
        ...vuePersonnage(r, userId),
        serverId: r.serverId,
      })),
    };
  }

  /** Lignes du compte (rattachées, ou de son compte Minecraft pas encore rattachées), triées. */
  private async lire(userId: string, serveurs: string[] | null) {
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      select: { minecraftUuid: true },
    });
    if (!user) return [];

    const rows = await this.prisma.gameCharacter.findMany({
      where: {
        ...(serveurs ? { serverId: { in: serveurs } } : {}),
        OR: [
          { userId },
          { userId: null, ownerMcUuid: normaliserMcUuid(user.minecraftUuid) },
        ],
      },
      select: {
        serverId: true,
        characterId: true,
        playerId: true,
        firstName: true,
        lastName: true,
        clan: true,
        village: true,
        type: true,
        rank: true,
        sex: true,
        age: true,
        dead: true,
        deletedAt: true,
        playMinutes: true,
        createdAt: true,
        lastSeenAt: true,
        firstSyncedAt: true,
        applicationId: true,
        application: {
          select: {
            userId: true,
            history: true,
            objectives: true,
            oralDecidedAt: true,
          },
        },
      },
    });
    rows.sort(comparerPersonnages);
    return rows;
  }
}
