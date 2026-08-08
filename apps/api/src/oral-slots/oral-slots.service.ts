import {
  BadRequestException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';
import { OpenSlotItemDto } from './dto/oral-slots.dto';

// Sélection publique du "booker" affichée au staff (pas de données sensibles).
const BOOKER_SELECT = {
  id: true,
  minecraftUsername: true,
  displayName: true,
} as const;

@Injectable()
export class OralSlotsService {
  private readonly logger = new Logger(OralSlotsService.name);

  constructor(private readonly prisma: PrismaService) {}

  // ─────────────────────────── Staff ───────────────────────────

  /** Ouvre un lot de créneaux (pool global). Retourne le nombre créé. */
  async openSlots(staffUserId: string, slots: OpenSlotItemDto[]) {
    const data = slots.map((s) => ({
      startAt: new Date(s.startAt),
      durationMin: s.durationMin ?? 30,
      openedByUserId: staffUserId,
    }));
    await this.prisma.whitelistOralSlot.createMany({ data });
    this.logger.log(`${data.length} creneau(x) oral ouvert(s) par ${staffUserId}`);
    return { created: data.length };
  }

  /** Liste staff : tous les créneaux non annulés, à venir en premier. */
  async listForStaff() {
    return this.prisma.whitelistOralSlot.findMany({
      where: { status: { not: 'CANCELLED' } },
      orderBy: { startAt: 'asc' },
      include: { bookedBy: { select: BOOKER_SELECT } },
    });
  }

  /** Annule un créneau (libre ou réservé) — le candidat est libéré. */
  async cancelSlot(slotId: string) {
    const slot = await this.prisma.whitelistOralSlot.findUnique({
      where: { id: slotId },
    });
    if (!slot) throw new NotFoundException('Créneau introuvable.');
    return this.prisma.whitelistOralSlot.update({
      where: { id: slotId },
      data: { status: 'CANCELLED', bookedByUserId: null, bookedAt: null },
    });
  }

  /** Marque un oral comme passé (le staff tranche l'HRP ailleurs). */
  async markDone(slotId: string, notes?: string) {
    const slot = await this.prisma.whitelistOralSlot.findUnique({
      where: { id: slotId },
    });
    if (!slot) throw new NotFoundException('Créneau introuvable.');
    if (!slot.bookedByUserId) {
      throw new BadRequestException('Ce créneau n\'a pas été réservé.');
    }
    return this.prisma.whitelistOralSlot.update({
      where: { id: slotId },
      data: { status: 'DONE', notes: notes ?? undefined },
    });
  }

  // ────────────────────────── Candidat ──────────────────────────

  /**
   * Vue candidat : les créneaux OUVERTS à venir + sa propre réservation
   * (BOOKED/DONE) le cas échéant.
   */
  async listForPlayer(userId: string) {
    const now = new Date();
    const [open, mine] = await Promise.all([
      this.prisma.whitelistOralSlot.findMany({
        where: { status: 'OPEN', startAt: { gte: now } },
        orderBy: { startAt: 'asc' },
        select: { id: true, startAt: true, durationMin: true },
      }),
      this.prisma.whitelistOralSlot.findFirst({
        where: { bookedByUserId: userId, status: { in: ['BOOKED', 'DONE'] } },
        orderBy: { startAt: 'desc' },
        select: {
          id: true,
          startAt: true,
          durationMin: true,
          status: true,
          notes: true,
        },
      }),
    ]);
    return { open, mine };
  }

  /**
   * Réserve un créneau. Gardes : candidature soumise, pas de réservation
   * active, créneau OPEN. La prise se fait via updateMany conditionnel pour
   * éviter la double-réservation concurrente (deux candidats, même créneau).
   */
  async book(userId: string, slotId: string) {
    const app = await this.prisma.whitelistApplication.findUnique({
      where: { userId },
      select: { id: true },
    });
    if (!app) {
      throw new BadRequestException(
        'Aucune candidature — soumets ta whitelist avant de réserver un oral.',
      );
    }

    const active = await this.prisma.whitelistOralSlot.findFirst({
      where: { bookedByUserId: userId, status: 'BOOKED' },
      select: { id: true },
    });
    if (active) {
      throw new BadRequestException('Tu as déjà un créneau réservé.');
    }

    // Prise atomique : n'aboutit que si le créneau est encore OPEN.
    const res = await this.prisma.whitelistOralSlot.updateMany({
      where: { id: slotId, status: 'OPEN' },
      data: { status: 'BOOKED', bookedByUserId: userId, bookedAt: new Date() },
    });
    if (res.count === 0) {
      throw new BadRequestException('Créneau indisponible (déjà pris ou fermé).');
    }
    this.logger.log(`creneau ${slotId} reserve par ${userId}`);
    return this.prisma.whitelistOralSlot.findUnique({
      where: { id: slotId },
      select: { id: true, startAt: true, durationMin: true, status: true },
    });
  }

  /** Annule sa propre réservation → le créneau repasse OPEN. */
  async cancelOwnBooking(userId: string, slotId: string) {
    const res = await this.prisma.whitelistOralSlot.updateMany({
      where: { id: slotId, bookedByUserId: userId, status: 'BOOKED' },
      data: { status: 'OPEN', bookedByUserId: null, bookedAt: null },
    });
    if (res.count === 0) {
      throw new BadRequestException(
        'Réservation introuvable (déjà annulée ou oral passé).',
      );
    }
    return { ok: true };
  }
}
