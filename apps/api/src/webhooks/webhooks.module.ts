import { Global, Module } from '@nestjs/common';
import { WebhooksService } from './webhooks.service';

/**
 * Global pour eviter d'importer WebhooksModule dans chaque feature
 * qui veut emettre un event vers le bot. WebhooksService est injecte
 * directement par les services (WhitelistService, TicketsService).
 */
@Global()
@Module({
  providers: [WebhooksService],
  exports: [WebhooksService],
})
export class WebhooksModule {}
