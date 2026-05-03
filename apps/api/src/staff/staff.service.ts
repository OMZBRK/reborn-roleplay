import {
  BadRequestException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { AppStatus, TicketStatus } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { TicketStatusDto, WhitelistDecisionDto } from './dto/staff.dto';

@Injectable()
export class StaffService {
  private readonly logger = new Logger(StaffService.name);

  constructor(private readonly prisma: PrismaService) {}

  async decideWhitelist(applicationId: string, dto: WhitelistDecisionDto) {
    if (dto.status === AppStatus.PENDING) {
      throw new BadRequestException(
        'PENDING n\'est pas une decision staff (cest letat initial).',
      );
    }
    const app = await this.prisma.whitelistApplication.findUnique({
      where: { id: applicationId },
    });
    if (!app) throw new NotFoundException('Candidature introuvable.');

    const updated = await this.prisma.whitelistApplication.update({
      where: { id: applicationId },
      data: {
        status: dto.status,
        reviewedAt: new Date(),
        reviewNotes: dto.reviewNotes ?? null,
      },
    });

    this.logger.log(
      `staff decision ${applicationId} → ${dto.status}${dto.reviewNotes ? ` ("${dto.reviewNotes.slice(0, 60)}")` : ''}`,
    );

    // Si la candidature est acceptee, on remonte le role du joueur a
    // WHITELISTED. Le role par defaut est PLAYER (cf schema.prisma).
    if (dto.status === AppStatus.APPROVED) {
      await this.prisma.user.update({
        where: { id: app.userId },
        data: { role: 'WHITELISTED' },
      });
      this.logger.log(`user ${app.userId} role → WHITELISTED`);
    }

    return {
      id: updated.id,
      status: updated.status,
      reviewedAt: updated.reviewedAt?.toISOString() ?? null,
      reviewNotes: updated.reviewNotes,
      userId: updated.userId,
    };
  }

  async setTicketStatus(ticketId: string, dto: TicketStatusDto) {
    if (dto.status === TicketStatus.OPEN) {
      throw new BadRequestException(
        'OPEN nest pas une transition staff (cest letat initial).',
      );
    }
    const ticket = await this.prisma.ticket.findUnique({
      where: { id: ticketId },
    });
    if (!ticket) throw new NotFoundException('Ticket introuvable.');

    const updated = await this.prisma.ticket.update({
      where: { id: ticketId },
      data: { status: dto.status },
    });

    this.logger.log(`staff ticket ${ticketId} → ${dto.status}`);

    return {
      id: updated.id,
      status: updated.status,
      userId: updated.userId,
      subject: updated.subject,
    };
  }
}
