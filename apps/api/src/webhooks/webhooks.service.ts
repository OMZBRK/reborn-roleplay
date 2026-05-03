import { Injectable, Logger } from '@nestjs/common';
import { createHmac } from 'node:crypto';

/**
 * Dispatcher de webhooks signes vers le bot Discord (`apps/bot`).
 *
 * Le bot expose un serveur HTTP local qui ecoute /webhooks/whitelist et
 * /webhooks/tickets ; chaque requete est signee HMAC-SHA256(body) avec
 * `REBORN_WEBHOOK_SECRET`. La cle est partagee via le .env du monorepo.
 *
 * Best-effort : un echec d'appel ne bloque pas la creation cote API
 * (l'utilisateur a soumis sa whitelist / son ticket avec succes ; la
 * notification staff est secondaire). Les echecs sont logges en warn
 * et un futur job de retry (BullMQ via Redis) les reprendra.
 */

export interface WhitelistWebhookPayload {
  applicationId: string;
  userPseudo: string;
  userId: string;
  characterName: string;
  characterAge: number;
  background: string;
  motivation: string;
  discordUserId: string | null;
}

export interface TicketWebhookPayload {
  ticketId: string;
  userPseudo: string;
  userId: string;
  category: string;
  subject: string;
  message: string;
  discordUserId: string | null;
}

@Injectable()
export class WebhooksService {
  private readonly logger = new Logger(WebhooksService.name);

  async whitelistSubmitted(payload: WhitelistWebhookPayload): Promise<void> {
    await this.dispatch('/webhooks/whitelist', payload);
  }

  async ticketCreated(payload: TicketWebhookPayload): Promise<void> {
    await this.dispatch('/webhooks/tickets', payload);
  }

  private async dispatch(path: string, payload: object): Promise<void> {
    const baseUrl = process.env.REBORN_BOT_WEBHOOK_URL;
    const secret = process.env.REBORN_WEBHOOK_SECRET;
    if (!baseUrl || !secret) {
      this.logger.warn(
        `Webhook ${path} ignore : REBORN_BOT_WEBHOOK_URL ou REBORN_WEBHOOK_SECRET non configure.`,
      );
      return;
    }
    const body = JSON.stringify(payload);
    const signature = createHmac('sha256', secret).update(body).digest('hex');
    const url = `${baseUrl.replace(/\/$/, '')}${path}`;
    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: {
          'content-type': 'application/json',
          'x-reborn-signature': signature,
        },
        body,
        signal: AbortSignal.timeout(5000),
      });
      if (!res.ok) {
        const text = await res.text().catch(() => '');
        this.logger.warn(
          `Webhook ${path} → ${res.status} : ${text.slice(0, 200)}`,
        );
      }
    } catch (err) {
      this.logger.warn(
        `Webhook ${path} echec reseau : ${(err as Error).message}`,
      );
    }
  }
}
