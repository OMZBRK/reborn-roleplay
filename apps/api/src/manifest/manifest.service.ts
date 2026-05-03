import { Injectable, NotFoundException } from '@nestjs/common';
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

@Injectable()
export class ManifestService {
  constructor(private readonly prisma: PrismaService) {}

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
}
