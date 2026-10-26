import { Injectable, Logger } from '@nestjs/common';
import { Cron, CronExpression } from '@nestjs/schedule';
import { DouaneService } from './douane.service';

/**
 * Tâches planifiées de la douane, à part du service pour qu'il reste
 * testable seul (`@nestjs/schedule` n'est livré qu'en module ESM).
 *
 * Toutes les 5 min :
 *  - les places encore réservées 15 min après la fin de leur séance sont
 *    soldées (manquée, ou annulée sans pénalité si l'arrivée est notée) ;
 *  - le rappel d'1 h part une seule fois par place (message dans le fil).
 */
@Injectable()
export class DouaneTachesService {
  private readonly logger = new Logger(DouaneTachesService.name);

  constructor(private readonly douane: DouaneService) {}

  @Cron(CronExpression.EVERY_5_MINUTES)
  async toutesLesCinqMinutes(): Promise<void> {
    try {
      const maintenant = new Date();
      const soldees = await this.douane.solderSeancesPassees(maintenant);
      const rappels = await this.douane.envoyerRappels(maintenant);
      if (soldees > 0 || rappels > 0) {
        this.logger.log(
          `douane : ${soldees} place(s) soldée(s), ${rappels} rappel(s) envoyé(s)`,
        );
      }
    } catch (err) {
      this.logger.warn(`tâche douane échouée : ${(err as Error).message}`);
    }
  }
}
