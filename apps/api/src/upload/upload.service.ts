import { mkdirSync } from 'node:fs';
import { writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { BadRequestException, Injectable, Logger } from '@nestjs/common';
import {
  buildPublicUrl,
  generateStoredFilename,
  isAllowedImageMime,
  resolveUploadDir,
} from './upload.config';

/**
 * Forme minimale d'un fichier multer (memory storage). Définie localement car
 * multer 2.x n'embarque pas ses types et `@types/multer` n'est pas installé —
 * on évite ainsi d'ajouter une dépendance juste pour `Express.Multer.File`.
 */
export interface UploadedImageFile {
  fieldname: string;
  originalname: string;
  mimetype: string;
  size: number;
  buffer: Buffer;
}

@Injectable()
export class UploadService {
  private readonly logger = new Logger(UploadService.name);
  private readonly uploadDir = resolveUploadDir();

  constructor() {
    // Crée le dossier au boot si absent (requirement §4).
    mkdirSync(this.uploadDir, { recursive: true });
    this.logger.log(`Dossier d'upload prêt: ${this.uploadDir}`);
  }

  /**
   * Persiste l'image sur disque sous un nom aléatoire (UUID + extension
   * whitelistée dérivée du MIME, jamais du nom client) et retourne l'URL
   * publique absolue.
   */
  async store(file: UploadedImageFile): Promise<{ url: string }> {
    if (!file?.buffer) {
      throw new BadRequestException('Aucun fichier reçu.');
    }
    if (!isAllowedImageMime(file.mimetype)) {
      throw new BadRequestException(
        `Type de fichier non supporté: ${file.mimetype}`,
      );
    }

    const filename = generateStoredFilename(file.mimetype);
    await writeFile(join(this.uploadDir, filename), file.buffer);

    return { url: buildPublicUrl(filename) };
  }
}
