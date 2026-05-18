import {
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Logger,
  Query,
  Req,
  Res,
  UseGuards,
} from '@nestjs/common';
import type { Request, Response } from 'express';
import { CurrentUser } from '../auth/current-user.decorator';
import type { RequestUser } from '../auth/current-user.decorator';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { DiscordService } from './discord.service';

@Controller('auth/discord')
export class DiscordController {
  private readonly logger = new Logger(DiscordController.name);

  constructor(private readonly service: DiscordService) {}

  /**
   * Staff panel — entry point. Redirects directly to Discord. The
   * Next.js admin app just sets `window.location` here; no JWT yet.
   */
  @Get('staff/start')
  staffStart(@Res() res: Response) {
    const { url } = this.service.startStaffLogin();
    return res.redirect(HttpStatus.FOUND, url);
  }

  /**
   * Staff panel — callback. Exchanges the code, looks up the user by
   * Discord ID, issues a token pair (gated on role ≥ HELPER), then
   * redirects to the admin app with tokens in the URL fragment.
   */
  @Get('staff/callback')
  async staffCallback(
    @Query('code') code: string | undefined,
    @Query('state') state: string | undefined,
    @Query('error') error: string | undefined,
    @Query('error_description') errorDescription: string | undefined,
    @Req() req: Request,
    @Res() res: Response,
  ) {
    const adminBase = process.env.ADMIN_BASE_URL ?? 'http://localhost:3002';
    const callbackPath = process.env.ADMIN_AUTH_CALLBACK_PATH ?? '/auth/callback';
    const target = new URL(callbackPath, adminBase);

    if (error) {
      target.hash = new URLSearchParams({
        error: errorDescription ?? error,
      }).toString();
      return res.redirect(HttpStatus.FOUND, target.toString());
    }
    if (!code || !state) {
      target.hash = new URLSearchParams({ error: 'missing_params' }).toString();
      return res.redirect(HttpStatus.FOUND, target.toString());
    }

    try {
      const result = await this.service.completeStaffLogin(code, state, {
        userAgent: req.headers['user-agent'],
        ip: req.ip,
      });
      // Fragment > query so secrets never hit referer/proxy logs.
      if (result.kind === 'challenge') {
        // 2FA actif → redirige avec un challenge id ; le panel
        // demandera le code TOTP avant d'obtenir les tokens.
        target.hash = new URLSearchParams({
          challenge: result.challenge,
        }).toString();
      } else {
        target.hash = new URLSearchParams({
          access: result.tokens.accessToken,
          refresh: result.tokens.refreshToken,
        }).toString();
      }
      return res.redirect(HttpStatus.FOUND, target.toString());
    } catch (err) {
      const message =
        err instanceof Error ? err.message : 'Erreur inattendue.';
      this.logger.warn(`staff login failed: ${message}`);
      target.hash = new URLSearchParams({ error: message }).toString();
      return res.redirect(HttpStatus.FOUND, target.toString());
    }
  }

  /**
   * Le launcher (authentifie) appelle cet endpoint pour obtenir l'URL
   * d'autorisation Discord. Il l'ouvre ensuite dans le navigateur systeme.
   */
  @Get('start')
  @UseGuards(JwtAuthGuard)
  start(@CurrentUser() user: RequestUser) {
    return this.service.startLinkFlow(user.sub);
  }

  /**
   * Discord redirige ici apres l'autorisation. Pas de JWT — l'utilisateur
   * est identifie via le `state` qu'on a memorise au moment du `start`.
   * Renvoie une page HTML simple ; le launcher detecte la liaison via
   * polling de /v1/auth/me.
   */
  @Get('callback')
  async callback(
    @Query('code') code: string | undefined,
    @Query('state') state: string | undefined,
    @Query('error') error: string | undefined,
    @Query('error_description') errorDescription: string | undefined,
    @Res() res: Response,
  ) {
    if (error) {
      return res
        .status(HttpStatus.BAD_REQUEST)
        .type('html')
        .send(this.renderHtml(false, errorDescription ?? error));
    }
    if (!code || !state) {
      return res
        .status(HttpStatus.BAD_REQUEST)
        .type('html')
        .send(this.renderHtml(false, 'Parametres manquants.'));
    }
    try {
      await this.service.completeLinkFlow(code, state);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : 'Erreur inattendue.';
      return res
        .status(HttpStatus.BAD_REQUEST)
        .type('html')
        .send(this.renderHtml(false, message));
    }
    return res.type('html').send(this.renderHtml(true, null));
  }

  @Delete('unlink')
  @HttpCode(HttpStatus.NO_CONTENT)
  @UseGuards(JwtAuthGuard)
  async unlink(@CurrentUser() user: RequestUser) {
    await this.service.unlink(user.sub);
  }

  private renderHtml(success: boolean, message: string | null): string {
    const title = success ? 'Compte Discord lie' : 'Echec de la liaison';
    const body = success
      ? 'Tu peux fermer cet onglet et retourner dans le launcher Reborn.'
      : `Une erreur est survenue : ${message ?? 'inconnue'}.`;
    const accent = success ? '#3b5bdb' : '#ef4444';
    return `<!doctype html>
<html lang="fr">
<head>
<meta charset="utf-8" />
<title>${title} — Reborn Roleplay</title>
<style>
  :root { color-scheme: dark; }
  body {
    margin: 0;
    min-height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
    background: radial-gradient(ellipse at top, #1c2a55 0%, #07080b 70%);
    color: #e5e7eb;
    font-family: "Inter", system-ui, sans-serif;
  }
  .card {
    max-width: 420px;
    padding: 32px 28px;
    border-radius: 12px;
    background: #0e1014;
    border: 1px solid #1d212a;
    text-align: center;
  }
  .badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 48px;
    height: 48px;
    border-radius: 50%;
    background: ${accent}1A;
    color: ${accent};
    font-size: 24px;
    margin-bottom: 16px;
  }
  h1 { margin: 0 0 8px; font-size: 20px; }
  p { margin: 0; color: #9ca3af; line-height: 1.55; font-size: 14px; }
</style>
</head>
<body>
  <div class="card">
    <div class="badge">${success ? '✓' : '!'}</div>
    <h1>${title}</h1>
    <p>${body}</p>
  </div>
</body>
</html>`;
  }
}
