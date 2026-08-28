import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  Logger,
  NotFoundException,
  ServiceUnavailableException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Role } from '@prisma/client';
import * as posix from 'node:path/posix';
import SftpClient from 'ssh2-sftp-client';
import { AuditService } from '../audit/audit.service';

/** Une racine autorisée pour un grade (chemin relatif à la base SFTP + libellé UI). */
export interface ScopeRoot {
  path: string;
  label: string;
}
interface Scope {
  roots: ScopeRoot[];
  write: boolean;
}
/** Réponse de `GET /v1/files/scopes`. */
export interface FileScopes {
  server: string;
  canWrite: boolean;
  roots: ScopeRoot[];
}

/**
 * Carte de scopes fichiers PAR GRADE — source de vérité de « qui peut toucher
 * quoi ». Volontairement explicite et NON hiérarchique (un modélisateur n'est
 * pas « au-dessus » d'un dev, ils ont des périmètres différents). Les grades
 * absents de cette carte n'ont AUCUN accès fichier, même s'ils passent la garde
 * de rôle du contrôleur.
 */
const SCOPES: Partial<Record<Role, Scope>> = {
  [Role.MODELISATEUR]: {
    write: true,
    roots: [{ path: 'plugins/Nexo', label: 'Nexo — modèles & items' }],
  },
  [Role.DEVELOPPEUR]: {
    write: true,
    roots: [
      { path: 'plugins/MagicSpells', label: 'MagicSpells' },
      { path: 'plugins/MythicMobs', label: 'MythicMobs' },
      { path: 'plugins/ModelEngine', label: 'ModelEngine' },
    ],
  },
  [Role.ADMIN]: {
    write: true,
    roots: [{ path: 'plugins', label: 'plugins/' }],
  },
  [Role.OWNER]: {
    write: true,
    roots: [{ path: '', label: 'Serveur (racine)' }],
  },
};

/** Extensions éditables comme texte. */
const TEXT_EXT = new Set([
  'yml', 'yaml', 'json', 'json5', 'txt', 'md', 'properties', 'conf', 'config',
  'mcfunction', 'js', 'ts', 'csv', 'lang', 'toml', 'ini', 'sh', 'xml', 'html',
  'css', 'log',
]);
/** Extensions image → aperçu base64 (non éditable en texte). */
const IMAGE_EXT = new Set(['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp']);

/** Taille max qu'on rapatrie pour lecture (configs/textures sont petits). */
const MAX_READ_BYTES = 4_000_000;
/** Au-delà, un texte est tronqué pour l'affichage. */
const TEXT_TRUNCATE_BYTES = 400_000;

export interface FileEntry {
  name: string;
  path: string;
  type: 'dir' | 'file';
  size: number;
  modified: string;
}
export interface DirListing {
  path: string;
  parent: string | null;
  entries: FileEntry[];
}
export interface FileContent {
  path: string;
  size: number;
  encoding: 'utf8' | 'base64';
  content: string;
  editable: boolean;
  kind: 'text' | 'image' | 'binary';
}

@Injectable()
export class FilesService {
  private readonly logger = new Logger(FilesService.name);

  constructor(
    private readonly config: ConfigService,
    private readonly audit: AuditService,
  ) {}

  // ─────────────────────────── Scopes / jail ───────────────────────────

  /** Scopes visibles par un grade (vide si aucun accès). */
  scopesFor(role: Role): FileScopes {
    const s = SCOPES[role];
    return {
      server: 'dev',
      canWrite: s?.write ?? false,
      roots: s?.roots ?? [],
    };
  }

  /**
   * Normalise un chemin relatif reçu du client et le valide contre la carte de
   * scopes du grade. Rejette tout `..`/échappement et tout chemin hors des
   * racines autorisées. Retourne le chemin relatif nettoyé (POSIX, sans slash
   * de tête). `requireWrite` exige en plus le droit d'écriture du grade.
   */
  private assertAllowed(role: Role, rawPath: string, requireWrite: boolean): string {
    const scope = SCOPES[role];
    if (!scope || scope.roots.length === 0) {
      throw new ForbiddenException('Aucun accès fichier pour ce grade.');
    }
    if (requireWrite && !scope.write) {
      throw new ForbiddenException('Écriture non autorisée pour ce grade.');
    }
    if (rawPath.includes('\0')) {
      throw new BadRequestException('Chemin invalide.');
    }
    // Normalisation POSIX + retrait du slash de tête → chemin relatif propre.
    let rel = posix.normalize(rawPath.replace(/\\/g, '/')).replace(/^\/+/, '');
    if (rel === '.' || rel === './') rel = '';
    // Après normalisation, un `..` résiduel = tentative d'échappement.
    if (rel === '..' || rel.startsWith('../') || rel.includes('/../')) {
      throw new ForbiddenException('Chemin hors périmètre.');
    }
    // Doit être une racine autorisée OU un descendant d'une racine autorisée.
    const ok = scope.roots.some((r) => {
      if (r.path === '') return true; // OWNER : racine serveur
      return rel === r.path || rel.startsWith(r.path + '/');
    });
    if (!ok) {
      throw new ForbiddenException('Chemin hors périmètre autorisé.');
    }
    return rel;
  }

  /** Base SFTP (jail racine) — préfixe appliqué à tout chemin relatif. */
  private base(): string {
    return this.config.get<string>('REBORN_SFTP_BASE_PATH')?.trim() || '.';
  }

  /** Chemin distant absolu = base + chemin relatif validé. */
  private remote(rel: string): string {
    const b = this.base();
    return rel ? posix.join(b, rel) : b;
  }

  // ─────────────────────────── SFTP plumbing ───────────────────────────

  private connectConfig() {
    const host = this.config.get<string>('REBORN_SFTP_HOST');
    const user = this.config.get<string>('REBORN_SFTP_USER');
    const password = this.config.get<string>('REBORN_SFTP_PASSWORD');
    if (!host || !user || !password) {
      throw new ServiceUnavailableException(
        'Accès fichiers serveur non configuré (REBORN_SFTP_*).',
      );
    }
    return {
      host,
      port: Number(this.config.get<string>('REBORN_SFTP_PORT') ?? '22'),
      username: user,
      password,
      readyTimeout: 15_000,
    };
  }

  /** Ouvre une connexion SFTP le temps d'une opération, puis la ferme. */
  private async withSftp<T>(fn: (sftp: SftpClient) => Promise<T>): Promise<T> {
    const sftp = new SftpClient();
    try {
      await sftp.connect(this.connectConfig());
      return await fn(sftp);
    } catch (err) {
      const msg = (err as Error).message ?? String(err);
      // Erreurs métier déjà typées → on les relaie telles quelles.
      if (
        err instanceof ForbiddenException ||
        err instanceof NotFoundException ||
        err instanceof BadRequestException
      ) {
        throw err;
      }
      this.logger.warn(`SFTP échec : ${msg}`);
      throw new ServiceUnavailableException(`Serveur de fichiers injoignable : ${msg}`);
    } finally {
      try {
        await sftp.end();
      } catch {
        /* ignore */
      }
    }
  }

  // ─────────────────────────── Opérations ───────────────────────────

  async list(role: Role, rawPath?: string): Promise<DirListing> {
    const scope = SCOPES[role];
    if (!scope || scope.roots.length === 0) {
      throw new ForbiddenException('Aucun accès fichier pour ce grade.');
    }
    const raw = rawPath ?? '';
    // Racine virtuelle : liste les racines autorisées comme des dossiers.
    if (raw === '' || raw === '.' || raw === '/') {
      // OWNER (racine réelle) descend directement dans le vrai dossier base.
      if (scope.roots.length === 1 && scope.roots[0].path === '') {
        return this.listReal(role, '');
      }
      const now = new Date().toISOString();
      return {
        path: '',
        parent: null,
        entries: scope.roots.map((r) => ({
          name: r.label,
          path: r.path,
          type: 'dir' as const,
          size: 0,
          modified: now,
        })),
      };
    }
    return this.listReal(role, raw);
  }

  private async listReal(role: Role, rawPath: string): Promise<DirListing> {
    const rel = this.assertAllowed(role, rawPath, false);
    const remote = this.remote(rel);
    const entries = await this.withSftp(async (sftp) => {
      const exists = await sftp.exists(remote);
      if (exists !== 'd') {
        throw new NotFoundException('Dossier introuvable.');
      }
      const list = await sftp.list(remote);
      return list
        .map((f) => ({
          name: f.name,
          path: rel ? `${rel}/${f.name}` : f.name,
          type: (f.type === 'd' ? 'dir' : 'file') as 'dir' | 'file',
          size: f.size ?? 0,
          modified: f.modifyTime ? new Date(f.modifyTime).toISOString() : '',
        }))
        .sort((a, b) =>
          a.type !== b.type
            ? a.type === 'dir'
              ? -1
              : 1
            : a.name.localeCompare(b.name),
        );
    });
    return { path: rel, parent: this.parentOf(role, rel), entries };
  }

  /** Parent navigable : null si `rel` est une racine autorisée (remonte au top virtuel). */
  private parentOf(role: Role, rel: string): string | null {
    const scope = SCOPES[role];
    const isRoot = scope?.roots.some((r) => r.path === rel);
    if (isRoot) return ''; // remonte au sélecteur de racines
    const idx = rel.lastIndexOf('/');
    return idx === -1 ? '' : rel.slice(0, idx);
  }

  async read(role: Role, actorId: string, rawPath: string): Promise<FileContent> {
    const rel = this.assertAllowed(role, rawPath, false);
    const remote = this.remote(rel);
    const ext = this.ext(rel);
    const isText = TEXT_EXT.has(ext);
    const isImage = IMAGE_EXT.has(ext);

    const result = await this.withSftp(async (sftp): Promise<FileContent> => {
      const exists = await sftp.exists(remote);
      if (exists === false) throw new NotFoundException('Fichier introuvable.');
      if (exists === 'd') throw new BadRequestException('C\'est un dossier.');
      const stat = await sftp.stat(remote);
      if (stat.size > MAX_READ_BYTES && !isText) {
        // Binaire trop gros → on ne le rapatrie pas.
        return {
          path: rel,
          size: stat.size,
          encoding: 'base64',
          content: '',
          editable: false,
          kind: 'binary',
        };
      }
      const buf = (await sftp.get(remote)) as Buffer;
      if (isImage) {
        return {
          path: rel,
          size: buf.length,
          encoding: 'base64',
          content: buf.toString('base64'),
          editable: false,
          kind: 'image',
        };
      }
      if (isText) {
        const truncated = buf.length > TEXT_TRUNCATE_BYTES;
        const slice = truncated ? buf.subarray(0, TEXT_TRUNCATE_BYTES) : buf;
        return {
          path: rel,
          size: buf.length,
          encoding: 'utf8',
          content: slice.toString('utf8'),
          editable: !truncated,
          kind: 'text',
        };
      }
      // Binaire inconnu (petit) → base64 non éditable.
      return {
        path: rel,
        size: buf.length,
        encoding: 'base64',
        content: buf.toString('base64'),
        editable: false,
        kind: 'binary',
      };
    });

    void this.audit.log({
      actorId,
      action: 'files.read',
      targetEntity: `file:${rel}`,
      metadata: { server: 'dev', size: result.size, kind: result.kind },
      source: 'panel',
    });
    return result;
  }

  async write(
    role: Role,
    actorId: string,
    rawPath: string,
    content: string,
  ): Promise<{ path: string; size: number; backedUp: boolean }> {
    const rel = this.assertAllowed(role, rawPath, true);
    const remote = this.remote(rel);
    const buf = Buffer.from(content, 'utf8');
    const backedUp = await this.withSftp(async (sftp) => {
      const bk = await this.backup(sftp, remote);
      await sftp.put(buf, remote);
      return bk;
    });
    void this.audit.log({
      actorId,
      action: 'files.write',
      targetEntity: `file:${rel}`,
      metadata: { server: 'dev', size: buf.length, backedUp },
      source: 'panel',
    });
    return { path: rel, size: buf.length, backedUp };
  }

  async upload(
    role: Role,
    actorId: string,
    rawPath: string,
    contentBase64: string,
  ): Promise<{ path: string; size: number; backedUp: boolean }> {
    const rel = this.assertAllowed(role, rawPath, true);
    const remote = this.remote(rel);
    let buf: Buffer;
    try {
      buf = Buffer.from(contentBase64, 'base64');
    } catch {
      throw new BadRequestException('Contenu base64 invalide.');
    }
    const backedUp = await this.withSftp(async (sftp) => {
      const bk = await this.backup(sftp, remote);
      await sftp.put(buf, remote);
      return bk;
    });
    void this.audit.log({
      actorId,
      action: 'files.upload',
      targetEntity: `file:${rel}`,
      metadata: { server: 'dev', size: buf.length, backedUp },
      source: 'panel',
    });
    return { path: rel, size: buf.length, backedUp };
  }

  async remove(
    role: Role,
    actorId: string,
    rawPath: string,
  ): Promise<{ deleted: boolean; backedUp: boolean }> {
    const rel = this.assertAllowed(role, rawPath, true);
    const remote = this.remote(rel);
    const backedUp = await this.withSftp(async (sftp) => {
      const exists = await sftp.exists(remote);
      if (exists === false) throw new NotFoundException('Fichier introuvable.');
      if (exists === 'd') throw new BadRequestException('Suppression de dossier non autorisée.');
      // Backup = on renomme en .bak (en écrasant un .bak préexistant) plutôt
      // que de supprimer sèchement → récupérable.
      const bak = remote + '.bak';
      if ((await sftp.exists(bak)) !== false) await sftp.delete(bak);
      await sftp.rename(remote, bak);
      return true;
    });
    void this.audit.log({
      actorId,
      action: 'files.delete',
      targetEntity: `file:${rel}`,
      metadata: { server: 'dev', backedUp },
      source: 'panel',
    });
    return { deleted: true, backedUp };
  }

  reload(
    role: Role,
    actorId: string,
    target: string,
  ): { queued: boolean; message: string } {
    // Phase 1b : le pont plugin (file d'attente de commandes console) n'est pas
    // encore déployé → on journalise l'intention et on répond proprement.
    void this.audit.log({
      actorId,
      action: 'files.reload',
      targetEntity: `reload:${target}`,
      metadata: { server: 'dev', role },
      source: 'panel',
    });
    return {
      queued: false,
      message: 'Rechargement à venir (pont plugin — Phase 1b).',
    };
  }

  // ─────────────────────────── helpers ───────────────────────────

  /** Sauvegarde `.bak` du fichier existant avant écrasement. Retourne true si backup fait. */
  private async backup(sftp: SftpClient, remote: string): Promise<boolean> {
    const exists = await sftp.exists(remote);
    if (exists === 'd') {
      throw new BadRequestException('La cible est un dossier.');
    }
    if (exists === false) return false; // création → rien à sauvegarder
    const bak = remote + '.bak';
    const old = (await sftp.get(remote)) as Buffer;
    await sftp.put(old, bak);
    return true;
  }

  private ext(rel: string): string {
    const dot = rel.lastIndexOf('.');
    const slash = rel.lastIndexOf('/');
    if (dot <= slash) return '';
    return rel.slice(dot + 1).toLowerCase();
  }
}
