import { Module } from '@nestjs/common';
import { OralSlotsService } from './oral-slots.service';

/**
 * Créneaux de test oral (L5). Le service est exporté et consommé par les
 * controllers existants : whitelist.controller (candidat) et admin.controller
 * (staff), plutôt que d'ajouter un nouveau controller.
 */
@Module({
  providers: [OralSlotsService],
  exports: [OralSlotsService],
})
export class OralSlotsModule {}
