import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { WhitelistModule } from '../whitelist/whitelist.module';
import { DouaneAdminController } from './douane-admin.controller';
import { DouaneController } from './douane.controller';
import { DouaneOralService } from './douane-oral.service';
import { DouaneTachesService } from './douane-taches.service';
import { DouaneService } from './douane.service';

/**
 * Douane (oral d'entrée) : séances avec capacité, réservations, décision
 * d'oral (CANDIDATURE_ET_DOUANE.md §3). Les créneaux d'un candidat
 * (`OralSlotsModule`) restent en service en parallèle pendant la transition.
 *
 * `DouaneOralService` est exporté pour la route signée du serveur de jeu
 * (lot B) ; `DouaneService` pour le calcul de la phase et des places.
 * Audit, webhooks et Prisma sont des modules globaux.
 */
@Module({
  imports: [AuthModule, WhitelistModule],
  controllers: [DouaneController, DouaneAdminController],
  providers: [DouaneService, DouaneOralService, DouaneTachesService],
  exports: [DouaneService, DouaneOralService],
})
export class DouaneModule {}
