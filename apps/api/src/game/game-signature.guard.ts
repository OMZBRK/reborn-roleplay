import {
  CanActivate,
  ExecutionContext,
  HttpException,
  HttpStatus,
  Injectable,
  SetMetadata,
  ServiceUnavailableException,
  UnauthorizedException,
  createParamDecorator,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import type { Request, Response } from 'express';
import { GameSignatureService, type LimiteJeu } from './game-signature.service';

/**
 * Garde des routes appelées par le serveur de jeu (CANDIDATURE_ET_DOUANE.md
 * §3.12) : signature liée à la requête, horodatage, nonce à usage unique,
 * limite de débit. Contrat : game-signature.ts. La route historique
 * (`GET /v1/game/candidature/:mcUuid`) garde `GameHmacGuard` jusqu'à son
 * retrait (`REBORN_GAME_CANDIDATURE_HISTORIQUE=0`, voir game-hmac.guard.ts).
 *
 * Réponses : 401 (signature, horodatage, nonce, serveur inconnu), 429 avec
 * `Retry-After`, 503 (aucun secret configuré, base indisponible).
 */

const CLE_LIMITE_JEU = 'reborn:limite-jeu';

/** Limite par défaut d'une route signée, par serveur. */
const LIMITE_PAR_DEFAUT: LimiteJeu = { nom: 'defaut', parMinute: 120 };

/** Limite de débit d'une route signée : `nom` sert de clé, `parMinute` par serveur. */
export const LimiteJeuRoute = (nom: string, parMinute: number) =>
  SetMetadata(CLE_LIMITE_JEU, { nom, parMinute } satisfies LimiteJeu);

type RequeteSignee = Request & { rawBody?: Buffer; serveurJeu?: string };

/** Identifiant du serveur de jeu authentifié par le garde (pour l'audit). */
export const ServeurJeu = createParamDecorator(
  (_data: unknown, ctx: ExecutionContext): string =>
    ctx.switchToHttp().getRequest<RequeteSignee>().serveurJeu ?? '',
);

@Injectable()
export class GameSignatureGuard implements CanActivate {
  constructor(
    private readonly signature: GameSignatureService,
    private readonly reflector: Reflector,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const http = context.switchToHttp();
    const req = http.getRequest<RequeteSignee>();
    const limite =
      this.reflector.get<LimiteJeu | undefined>(
        CLE_LIMITE_JEU,
        context.getHandler(),
      ) ?? LIMITE_PAR_DEFAUT;

    const res = await this.signature.verifier(
      {
        methode: req.method,
        chemin: req.originalUrl,
        enTetes: req.headers,
        corps: req.rawBody ?? Buffer.alloc(0),
      },
      limite,
    );
    if (res.ok) {
      req.serveurJeu = res.serveur;
      return true;
    }
    if (res.statut === 429) {
      http
        .getResponse<Response>()
        .setHeader('Retry-After', String(res.reessayerDansS ?? 60));
      throw new HttpException(res.message, HttpStatus.TOO_MANY_REQUESTS);
    }
    if (res.statut === 503) throw new ServiceUnavailableException(res.message);
    throw new UnauthorizedException(res.message);
  }
}
