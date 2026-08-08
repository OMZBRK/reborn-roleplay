import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';

export type BadgesDto = {
  unreadTickets: number;
  unreadPatchnotes: number;
  coins: number;
};

export type ReadScope = 'tickets' | 'patchnotes';

// Date de référence quand l'utilisateur n'a jamais consulté une section :
// tout ce qui existe est alors considéré comme non-lu.
const EPOCH = new Date(0);

@Injectable()
export class MeService {
  constructor(private readonly prisma: PrismaService) {}

  /**
   * Compteurs non-lus pour la cloche + la sidebar, et solde de monnaie.
   * - tickets non-lus = tickets de l'utilisateur ayant au moins un message
   *   STAFF postérieur à son dernier `ticketsReadAt`.
   * - patchnotes non-lus = patch notes publiés après `patchnotesReadAt`.
   */
  async getBadges(userId: string): Promise<BadgesDto> {
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      select: {
        ticketsReadAt: true,
        patchnotesReadAt: true,
        zkCoinBalance: true,
      },
    });
    if (!user) {
      return { unreadTickets: 0, unreadPatchnotes: 0, coins: 0 };
    }

    const [unreadTickets, unreadPatchnotes] = await Promise.all([
      this.prisma.ticket.count({
        where: {
          userId,
          messages: {
            some: {
              authorType: 'STAFF',
              createdAt: { gt: user.ticketsReadAt ?? EPOCH },
            },
          },
        },
      }),
      this.prisma.patchNote.count({
        where: { publishedAt: { gt: user.patchnotesReadAt ?? EPOCH } },
      }),
    ]);

    return {
      unreadTickets,
      unreadPatchnotes,
      coins: user.zkCoinBalance,
    };
  }

  /** Marque une section comme lue (timestamp = maintenant) et renvoie les badges à jour. */
  async markRead(userId: string, scope: ReadScope): Promise<BadgesDto> {
    const field = scope === 'tickets' ? 'ticketsReadAt' : 'patchnotesReadAt';
    await this.prisma.user.update({
      where: { id: userId },
      data: { [field]: new Date() },
    });
    return this.getBadges(userId);
  }

  /** Met à jour le nom d'affichage RP. Retourne le displayName appliqué. */
  async updateProfile(
    userId: string,
    displayName: string,
  ): Promise<{ displayName: string }> {
    const user = await this.prisma.user.update({
      where: { id: userId },
      data: { displayName: displayName.trim() },
      select: { displayName: true },
    });
    return { displayName: user.displayName ?? '' };
  }
}
