import { mkdirSync } from 'node:fs';
import { readFile, stat, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import {
  BadRequestException,
  Injectable,
  Logger,
  NotFoundException,
  PayloadTooLargeException,
} from '@nestjs/common';
import type { UploadedImageFile } from '../upload/upload.service';
import {
  generateStaffMediaId,
  isStaffMediaId,
  resolveStaffMediaDir,
  signedStaffMediaUrl,
  sniffImageMime,
  STAFF_MEDIA_MAX_BYTES,
  STAFF_MEDIA_MAX_TOTAL_BYTES,
  staffMediaExtension,
} from './staff-media.config';

/** Stockage privé des images des messages Discord du panel (cf. staff-media.config.ts). */
@Injectable()
export class StaffMediaService {
  private readonly logger = new Logger(StaffMediaService.name);
  readonly dir = resolveStaffMediaDir();

  constructor() {
    mkdirSync(this.dir, { recursive: true });
    this.logger.log(`Médias des messages Discord : ${this.dir}`);
  }

  async store(file: UploadedImageFile): Promise<{ id: string; url: string }> {
    if (!file?.buffer?.length)
      throw new BadRequestException('Aucun fichier reçu.');
    if (file.buffer.length > STAFF_MEDIA_MAX_BYTES) {
      throw new PayloadTooLargeException('Image > 8 Mo.');
    }
    // Type déterminé par les octets, jamais par le nom ni le MIME annoncé.
    const mime = sniffImageMime(file.buffer);
    if (!mime || !staffMediaExtension(mime)) {
      throw new BadRequestException(
        'Images PNG, JPEG, WebP ou GIF uniquement.',
      );
    }
    const id = generateStaffMediaId(mime);
    await writeFile(join(this.dir, id), file.buffer);
    return { id, url: signedStaffMediaUrl(id) };
  }

  path(id: string): string {
    if (!isStaffMediaId(id)) throw new NotFoundException();
    return join(this.dir, id);
  }

  async exists(id: string): Promise<boolean> {
    if (!isStaffMediaId(id)) return false;
    return stat(join(this.dir, id)).then(
      (s) => s.isFile(),
      () => false,
    );
  }

  /** Vérifie que toutes les images citées existent ; lève 400 sinon. */
  async assertAll(ids: string[]): Promise<void> {
    for (const id of new Set(ids)) {
      if (!(await this.exists(id))) {
        throw new BadRequestException(
          `Image ${id} introuvable (téléverse-la à nouveau).`,
        );
      }
    }
  }

  /** Contenu base64 des images pour le webhook du bot (plafond cumulé 25 Mo). */
  async forBot(ids: string[]): Promise<{ nom: string; base64: string }[]> {
    const out: { nom: string; base64: string }[] = [];
    let total = 0;
    for (const id of new Set(ids)) {
      const buf = await readFile(this.path(id)).catch(() => {
        throw new BadRequestException(`Image ${id} introuvable.`);
      });
      total += buf.length;
      if (total > STAFF_MEDIA_MAX_TOTAL_BYTES) {
        throw new PayloadTooLargeException(
          'Images > 25 Mo au total pour un message.',
        );
      }
      out.push({ nom: id, base64: buf.toString('base64') });
    }
    return out;
  }

  signedUrls(ids: string[]): Record<string, string> {
    const out: Record<string, string> = {};
    for (const id of new Set(ids))
      if (isStaffMediaId(id)) out[id] = signedStaffMediaUrl(id);
    return out;
  }
}
