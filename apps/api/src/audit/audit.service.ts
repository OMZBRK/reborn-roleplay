import { Injectable, Logger } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { createHash } from 'node:crypto';
import { PrismaService } from '../prisma/prisma.service';

/**
 * AuditService — log append-only avec hash chain (cf PLAN §14.5).
 *
 * Chaque entree calcule son hash comme :
 *   sha256(previousHash || actorId || action || targetUserId || targetEntity
 *          || JSON.stringify(metadata) || createdAt.toISOString())
 *
 * Le previousHash est celui de l'entree precedente la plus recente
 * (toutes actors confondues), ce qui permet de detecter toute alteration
 * historique : casser un seul row casse toute la chaine apres.
 *
 * En cas de race condition (deux logs concurrents), la transaction Prisma
 * serialise via la contrainte UNIQUE sur hash : le second .create() peut
 * conflicter, on retry une fois en relisant le previousHash.
 */

export type AuditSource = 'panel' | 'discord' | 'launcher' | 'system';

export interface LogInput {
  /** UUID du User Reborn auteur de l'action. Pour les actions systeme
   *  (ex: anomalie auto-detectee), on utilise un user "system" virtuel ;
   *  pour l'instant on rejette si actorId vide. */
  actorId: string;
  /** Verbe canonique en snake_case, ex: "whitelist.approve". */
  action: string;
  /** UUID du User cible quand l'action concerne un joueur. */
  targetUserId?: string;
  /** Reference textuelle (ex: "whitelist:abc123", "ticket:xyz789"). */
  targetEntity?: string;
  /** JSON libre pour le contexte (notes, reason, before/after diff). */
  metadata?: Record<string, unknown>;
  /** D'ou vient l'action — utile pour filtrer/distinguer panel vs bot. */
  source: AuditSource;
  ip?: string;
  userAgent?: string;
}

@Injectable()
export class AuditService {
  private readonly logger = new Logger(AuditService.name);

  constructor(private readonly prisma: PrismaService) {}

  /**
   * Append une entree au log. Fire-and-forget — utilise via `void this.audit.log(...)`
   * pour ne pas bloquer la transaction metier. Best-effort : en cas d'echec
   * on log un warning mais on ne throw pas (sinon une action staff
   * peut etre rollback a cause d'un side-effect d'audit).
   */
  async log(input: LogInput): Promise<void> {
    try {
      await this.logInner(input);
    } catch (err) {
      this.logger.warn(
        `audit log echec ${input.action} actor=${input.actorId} : ${(err as Error).message}`,
      );
    }
  }

  private async logInner(input: LogInput, attempt = 0): Promise<void> {
    const previous = await this.prisma.auditLog.findFirst({
      orderBy: { createdAt: 'desc' },
      select: { hash: true },
    });
    const previousHash = previous?.hash ?? null;
    const createdAt = new Date();
    const metadata = input.metadata ?? {};
    const hash = this.computeHash({
      previousHash,
      actorId: input.actorId,
      action: input.action,
      targetUserId: input.targetUserId ?? null,
      targetEntity: input.targetEntity ?? null,
      metadata,
      createdAtIso: createdAt.toISOString(),
    });

    try {
      await this.prisma.auditLog.create({
        data: {
          actorId: input.actorId,
          action: input.action,
          targetUserId: input.targetUserId ?? null,
          targetEntity: input.targetEntity ?? null,
          metadata: metadata as Prisma.InputJsonValue,
          ipAddress: input.ip,
          userAgent: input.userAgent,
          source: input.source,
          createdAt,
          previousHash,
          hash,
        },
      });
    } catch (err) {
      // P2002 = unique constraint sur hash → race condition, retry une fois.
      if (
        attempt < 1 &&
        typeof err === 'object' &&
        err !== null &&
        'code' in err &&
        (err as { code: string }).code === 'P2002'
      ) {
        this.logger.warn('audit hash collision, retry');
        return this.logInner(input, attempt + 1);
      }
      throw err;
    }
  }

  private computeHash(parts: {
    previousHash: string | null;
    actorId: string;
    action: string;
    targetUserId: string | null;
    targetEntity: string | null;
    metadata: Record<string, unknown>;
    createdAtIso: string;
  }): string {
    const canonical = [
      parts.previousHash ?? '',
      parts.actorId,
      parts.action,
      parts.targetUserId ?? '',
      parts.targetEntity ?? '',
      // JSON.stringify avec tri des cles pour reproductibilite — on prend
      // le meme algo que packages/manifest-signer.
      stableStringify(parts.metadata),
      parts.createdAtIso,
    ].join('|');
    return createHash('sha256').update(canonical).digest('hex');
  }

  /**
   * Re-verifie l'integrite de toute la chaine. Renvoie l'id de la
   * premiere entree corrompue ou null si OK. Utilise par un endpoint
   * admin de health check (sera ajoute plus tard).
   */
  async verifyChain(): Promise<string | null> {
    const rows = await this.prisma.auditLog.findMany({
      orderBy: { createdAt: 'asc' },
    });
    let prev: string | null = null;
    for (const row of rows) {
      const expected = this.computeHash({
        previousHash: row.previousHash,
        actorId: row.actorId,
        action: row.action,
        targetUserId: row.targetUserId,
        targetEntity: row.targetEntity,
        metadata: (row.metadata as Record<string, unknown>) ?? {},
        createdAtIso: row.createdAt.toISOString(),
      });
      if (row.hash !== expected) return row.id;
      if (row.previousHash !== prev) return row.id;
      prev = row.hash;
    }
    return null;
  }
}

function stableStringify(value: unknown): string {
  if (value === null || typeof value !== 'object') {
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) {
    return '[' + value.map((v) => stableStringify(v)).join(',') + ']';
  }
  const obj = value as Record<string, unknown>;
  const keys = Object.keys(obj).sort();
  return (
    '{' +
    keys.map((k) => JSON.stringify(k) + ':' + stableStringify(obj[k])).join(',') +
    '}'
  );
}
