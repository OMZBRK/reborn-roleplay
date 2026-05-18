import { ConflictException, Injectable, NotFoundException } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { AuditService } from '../audit/audit.service';
import { PrismaService } from '../prisma/prisma.service';

export interface ManifestFile {
  path: string;
  sha256: string;
  size: number;
  url: string;
  required: boolean;
}

export interface SignedManifestResponse {
  version: string;
  minecraftVersion: string;
  issuedAt: string;
  expiresAt: string;
  minLauncherVersion: string;
  files: ManifestFile[];
  signature: string;
}

export interface PublishManifestInput {
  version: string;
  minecraftVersion: string;
  minLauncherVersion: string;
  issuedAt: string;
  expiresAt: string;
  files: ManifestFile[];
  signature: string;
}

@Injectable()
export class ManifestService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly audit: AuditService,
  ) {}

  /**
   * Retourne le manifest courant (le seul ou le plus recent
   * `isCurrent=true`). Le contenu `files` est stocke en JSONB —
   * on le passe tel quel sans le re-signer cote API : la signature
   * est calculee une fois pour toutes au moment de la publication
   * via le CLI manifest-signer.
   */
  async getCurrent(): Promise<SignedManifestResponse> {
    const manifest = await this.prisma.manifest.findFirst({
      where: { isCurrent: true },
      orderBy: { publishedAt: 'desc' },
    });

    if (!manifest) {
      throw new NotFoundException('Aucun manifest publie pour le moment.');
    }

    const files = (manifest.files as unknown) as ManifestFile[];

    // Retour BIT-POUR-BIT identique a ce qui a ete signe : toute modification
    // (timestamps reconstruits, ordre des champs change, etc.) invaliderait
    // la signature lors de la verification cote launcher.
    return {
      version: manifest.version,
      minecraftVersion: manifest.minecraftVersion,
      issuedAt: manifest.issuedAt.toISOString(),
      expiresAt: manifest.expiresAt.toISOString(),
      minLauncherVersion: manifest.minLauncherVersion,
      files,
      signature: manifest.signature,
    };
  }

  async list() {
    return this.prisma.manifest.findMany({
      orderBy: { publishedAt: 'desc' },
      take: 50,
    });
  }

  /**
   * Publie un nouveau manifest signe. Le payload est cense venir du CLI
   * packages/manifest-signer/src/cli.ts (`sign` command) — la signature
   * est calculee LOCALEMENT par le maintainer, jamais cote API. L'API
   * stocke tel quel et bascule isCurrent.
   *
   * Les timestamps issuedAt/expiresAt doivent etre les valeurs EXACTES
   * signees (cf comments dans CLAUDE.md sur le round-trip Postgres).
   */
  async publish(input: PublishManifestInput, actorUserId: string) {
    const issuedAt = new Date(input.issuedAt);
    const expiresAt = new Date(input.expiresAt);
    if (Number.isNaN(issuedAt.getTime()) || Number.isNaN(expiresAt.getTime())) {
      throw new ConflictException('issuedAt / expiresAt invalides.');
    }

    // Transactionnel : bascule isCurrent=false sur tous les anciens
    // ET cree le nouveau en `isCurrent=true`. Si la creation throw
    // (version dupliquee p.ex.) on rollback les flips.
    const created = await this.prisma.$transaction(async (tx) => {
      await tx.manifest.updateMany({
        where: { isCurrent: true },
        data: { isCurrent: false },
      });
      return tx.manifest.create({
        data: {
          version: input.version,
          minecraftVersion: input.minecraftVersion,
          minLauncherVersion: input.minLauncherVersion,
          issuedAt,
          expiresAt,
          files: input.files as unknown as Prisma.InputJsonValue,
          signature: input.signature,
          isCurrent: true,
        },
      });
    });

    void this.audit.log({
      actorId: actorUserId,
      action: 'manifest.publish',
      targetEntity: `manifest:${created.id}`,
      metadata: {
        version: created.version,
        minecraftVersion: created.minecraftVersion,
        files: input.files.length,
      },
      source: 'panel',
    });

    return created;
  }
}
