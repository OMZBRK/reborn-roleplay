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
import { PrismaService } from '../prisma/prisma.service';

/**
 * Cibles de rechargement autorisées : clé logique → commande console résolue +
 * racine de fichiers requise (le grade doit avoir accès à cette racine pour
 * pouvoir recharger le plugin). Le pont côté jeu ne reçoit JAMAIS de texte
 * libre — seulement une de ces commandes fixes.
 */
const RELOAD_TARGETS: Record<
  string,
  { command: string; root: string; label: string }
> = {
  nexo: { command: 'nexo reload', root: 'plugins/Nexo', label: 'Nexo' },
  magicspells: { command: 'ms reload', root: 'plugins/MagicSpells', label: 'MagicSpells' },
  mythicmobs: { command: 'mm reload', root: 'plugins/MythicMobs', label: 'MythicMobs' },
  modelengine: { command: 'meg reload', root: 'plugins/ModelEngine', label: 'ModelEngine' },
};

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
      { path: 'plugins/Nexo', label: 'Nexo — modèles & items' },
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
    private readonly prisma: PrismaService,
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
  ): Promise<{ path: string; size: number }> {
    const rel = this.assertAllowed(role, rawPath, true);
    const remote = this.remote(rel);
    const buf = Buffer.from(content, 'utf8');
    await this.withSftp(async (sftp) => {
      // Refuse d'écraser un dossier ; écriture directe (pas de `.bak`).
      if ((await sftp.exists(remote)) === 'd') {
        throw new BadRequestException('La cible est un dossier.');
      }
      await sftp.put(buf, remote);
    });
    void this.audit.log({
      actorId,
      action: 'files.write',
      targetEntity: `file:${rel}`,
      metadata: { server: 'dev', size: buf.length },
      source: 'panel',
    });
    return { path: rel, size: buf.length };
  }

  async upload(
    role: Role,
    actorId: string,
    rawPath: string,
    contentBase64: string,
  ): Promise<{ path: string; size: number }> {
    const rel = this.assertAllowed(role, rawPath, true);
    const remote = this.remote(rel);
    let buf: Buffer;
    try {
      buf = Buffer.from(contentBase64, 'base64');
    } catch {
      throw new BadRequestException('Contenu base64 invalide.');
    }
    await this.withSftp(async (sftp) => {
      if ((await sftp.exists(remote)) === 'd') {
        throw new BadRequestException('La cible est un dossier.');
      }
      await sftp.put(buf, remote);
    });
    void this.audit.log({
      actorId,
      action: 'files.upload',
      targetEntity: `file:${rel}`,
      metadata: { server: 'dev', size: buf.length },
      source: 'panel',
    });
    return { path: rel, size: buf.length };
  }

  async remove(
    role: Role,
    actorId: string,
    rawPath: string,
  ): Promise<{ deleted: boolean }> {
    const rel = this.assertAllowed(role, rawPath, true);
    const remote = this.remote(rel);
    await this.withSftp(async (sftp) => {
      const exists = await sftp.exists(remote);
      if (exists === false) throw new NotFoundException('Fichier introuvable.');
      if (exists === 'd') throw new BadRequestException('Suppression de dossier non autorisée.');
      // Suppression sèche — plus de `.bak` (les staff le trouvaient parasite).
      await sftp.delete(remote);
    });
    void this.audit.log({
      actorId,
      action: 'files.delete',
      targetEntity: `file:${rel}`,
      metadata: { server: 'dev' },
      source: 'panel',
    });
    return { deleted: true };
  }

  /** Crée un dossier (récursif) dans le périmètre du grade. */
  async mkdir(
    role: Role,
    actorId: string,
    rawPath: string,
  ): Promise<{ created: boolean; path: string }> {
    const rel = this.assertAllowed(role, rawPath, true);
    if (!rel) throw new BadRequestException('Chemin de dossier vide.');
    const remote = this.remote(rel);
    await this.withSftp(async (sftp) => {
      if ((await sftp.exists(remote)) !== false) {
        throw new BadRequestException('Un fichier/dossier existe déjà à ce chemin.');
      }
      await sftp.mkdir(remote, true);
    });
    void this.audit.log({
      actorId,
      action: 'files.mkdir',
      targetEntity: `dir:${rel}`,
      metadata: { server: 'dev' },
      source: 'panel',
    });
    return { created: true, path: rel };
  }

  /**
   * Générateur « item animé Nexo » — en un appel, à partir d'une spritesheet
   * PNG, écrit dans le pack Nexo :
   *  - `pack/assets/reborn/textures/item/<id>.png` (la sheet),
   *  - `pack/assets/reborn/textures/item/<id>.png.mcmeta` (animation, si animé),
   *  - `pack/assets/reborn/models/item/<id>.json` (modèle plat, réf CORRECTE),
   *  - `items/<id>.yml` (entrée Nexo, `Pack.model: reborn:item/<id>`),
   * puis file un `nexo reload`. Tout est sous `plugins/Nexo` → dans le périmètre
   * Modélisateur/Développeur. But : « ça marche du premier coup », plus de réf
   * Blockbench cassée ni de `.mcmeta` mal nommé.
   */
  async createAnimatedItem(
    role: Role,
    actorId: string,
    dto: {
      id: string;
      spriteBase64: string;
      name?: string;
      frames?: number;
      frametime?: number;
      animated?: boolean;
    },
  ): Promise<{
    itemId: string;
    animated: boolean;
    frames: number;
    frametime: number;
    files: string[];
    reloadQueued: boolean;
    snippet: string;
  }> {
    const id = dto.id;
    if (!/^[a-z0-9_]+$/.test(id)) {
      throw new BadRequestException('Identifiant invalide.');
    }
    let sprite: Buffer;
    try {
      sprite = Buffer.from(dto.spriteBase64, 'base64');
    } catch {
      throw new BadRequestException('Spritesheet base64 invalide.');
    }
    if (sprite.length === 0) throw new BadRequestException('Spritesheet vide.');

    // Auto-détection frames/anim depuis les dimensions PNG (feuille verticale).
    const dims = this.pngDimensions(sprite);
    let frames = dto.frames;
    let animated = dto.animated;
    if (dims && dims.width > 0) {
      const auto = dims.height % dims.width === 0 ? dims.height / dims.width : 1;
      if (frames == null) frames = auto;
      if (animated == null) animated = auto > 1;
    }
    if (frames == null) frames = 1;
    if (animated == null) animated = frames > 1;
    if (frames < 1) frames = 1;
    const frametime = dto.frametime ?? 2;
    const name = (dto.name ?? id).replace(/"/g, "'");

    // Chemins (tous sous plugins/Nexo → périmètre Modélisateur/Dev).
    const PACK = 'plugins/Nexo/pack/assets/reborn';
    const texRel = `${PACK}/textures/item/${id}.png`;
    const mcmetaRel = `${PACK}/textures/item/${id}.png.mcmeta`;
    const modelRel = `${PACK}/models/item/${id}.json`;
    const ymlRel = `plugins/Nexo/items/${id}.yml`;

    // Valide chaque chemin (écriture requise) avant d'écrire quoi que ce soit.
    for (const rel of [texRel, mcmetaRel, modelRel, ymlRel]) {
      this.assertAllowed(role, rel, true);
    }

    const modelJson =
      JSON.stringify(
        {
          parent: 'minecraft:item/generated',
          textures: { layer0: `reborn:item/${id}` },
        },
        null,
        2,
      ) + '\n';
    const mcmetaJson =
      JSON.stringify(
        {
          animation: {
            frametime,
            frames: Array.from({ length: frames }, (_, i) => i),
          },
        },
        null,
        2,
      ) + '\n';
    const ymlText =
      `# Généré par le panel (item animé). Édite librement.\n` +
      `${id}:\n` +
      `  itemname: "${name}"\n` +
      `  material: PAPER\n` +
      `  Pack:\n` +
      `    model: reborn:item/${id}\n`;

    const toWrite: { rel: string; buf: Buffer }[] = [
      { rel: texRel, buf: sprite },
      { rel: modelRel, buf: Buffer.from(modelJson, 'utf8') },
      { rel: ymlRel, buf: Buffer.from(ymlText, 'utf8') },
    ];
    if (animated) {
      toWrite.push({ rel: mcmetaRel, buf: Buffer.from(mcmetaJson, 'utf8') });
    }

    await this.withSftp(async (sftp) => {
      // Nettoie un `.png.mcmeta` résiduel si l'item n'est plus animé.
      if (!animated) {
        const mc = this.remote(mcmetaRel);
        if ((await sftp.exists(mc)) !== false) await sftp.delete(mc);
      }
      for (const f of toWrite) {
        const abs = this.remote(f.rel);
        const dir = posix.dirname(abs);
        if ((await sftp.exists(dir)) !== 'd') await sftp.mkdir(dir, true);
        await sftp.put(f.buf, abs);
      }
    });

    void this.audit.log({
      actorId,
      action: 'files.nexo.animated-item',
      targetEntity: `nexo:${id}`,
      metadata: { server: 'dev', animated, frames, frametime },
      source: 'panel',
    });

    // Recharge Nexo pour régénérer le pack (best-effort : ne casse pas la créa).
    let reloadQueued = false;
    try {
      await this.reload(role, actorId, 'nexo');
      reloadQueued = true;
    } catch {
      /* pas d'accès reload / bridge indispo → l'item est écrit quand même */
    }

    const snippet =
      `effect: itemdisplay\n` +
      `item: nexo:${id}\n` +
      `duration: ${animated ? frames * frametime : 20}\n` +
      `scale: 1.5`;

    return {
      itemId: `nexo:${id}`,
      animated,
      frames,
      frametime,
      files: toWrite.map((f) => f.rel),
      reloadQueued,
      snippet,
    };
  }

  /** Lit largeur/hauteur d'un PNG depuis son chunk IHDR (0 si non-PNG). */
  private pngDimensions(buf: Buffer): { width: number; height: number } | null {
    // Signature PNG (8) + longueur(4) + "IHDR"(4) + width(4) + height(4).
    const sig = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
    if (buf.length < 24) return null;
    for (let i = 0; i < 8; i++) if (buf[i] !== sig[i]) return null;
    return { width: buf.readUInt32BE(16), height: buf.readUInt32BE(20) };
  }

  /**
   * Déplace / renomme un fichier ou dossier. Source ET destination doivent être
   * dans le périmètre du grade (écriture requise des deux côtés). Refuse
   * d'écraser une destination existante ou de déplacer un dossier dans lui-même.
   */
  async move(
    role: Role,
    actorId: string,
    rawFrom: string,
    rawTo: string,
  ): Promise<{ moved: boolean; from: string; to: string }> {
    const from = this.assertAllowed(role, rawFrom, true);
    const to = this.assertAllowed(role, rawTo, true);
    if (!from || !to) throw new BadRequestException('Chemin vide.');
    if (from === to) throw new BadRequestException('Source et destination identiques.');
    // Empêche de déplacer un dossier à l'intérieur de lui-même.
    if (to === from || to.startsWith(from + '/')) {
      throw new BadRequestException('Impossible de déplacer un dossier dans lui-même.');
    }
    const rFrom = this.remote(from);
    const rTo = this.remote(to);
    await this.withSftp(async (sftp) => {
      if ((await sftp.exists(rFrom)) === false) {
        throw new NotFoundException('Source introuvable.');
      }
      if ((await sftp.exists(rTo)) !== false) {
        throw new BadRequestException('La destination existe déjà.');
      }
      await sftp.rename(rFrom, rTo);
    });
    void this.audit.log({
      actorId,
      action: 'files.move',
      targetEntity: `file:${from}`,
      metadata: { server: 'dev', to },
      source: 'panel',
    });
    return { moved: true, from, to };
  }

  /** Cibles de reload accessibles au grade (pour l'UI). */
  reloadTargetsFor(role: Role): { key: string; label: string }[] {
    const scope = SCOPES[role];
    if (!scope) return [];
    return Object.entries(RELOAD_TARGETS)
      .filter(([, t]) =>
        scope.roots.some(
          (r) => r.path === '' || t.root === r.path || t.root.startsWith(r.path + '/'),
        ),
      )
      .map(([key, t]) => ({ key, label: t.label }));
  }

  /**
   * Enfile une commande de rechargement WHITELISTÉE dans la file d'attente. Le
   * pont côté jeu (ShinobiCore) la draine, l'exécute en console et renvoie le
   * résultat. Le grade doit avoir accès à la racine du plugin ciblé.
   */
  async reload(
    role: Role,
    actorId: string,
    target: string,
  ): Promise<{ queued: boolean; message: string; id?: string }> {
    const t = RELOAD_TARGETS[target];
    if (!t) {
      throw new BadRequestException('Cible de rechargement inconnue.');
    }
    const scope = SCOPES[role];
    const allowed =
      scope?.roots.some(
        (r) => r.path === '' || t.root === r.path || t.root.startsWith(r.path + '/'),
      ) ?? false;
    if (!allowed) {
      throw new ForbiddenException('Rechargement hors périmètre autorisé.');
    }
    const cmd = await this.prisma.serverCommand.create({
      data: {
        target,
        command: t.command,
        requestedById: actorId,
      },
      select: { id: true },
    });
    void this.audit.log({
      actorId,
      action: 'files.reload',
      targetEntity: `reload:${target}`,
      metadata: { server: 'dev', command: t.command, commandId: cmd.id },
      source: 'panel',
    });
    return {
      queued: true,
      message: `Rechargement de ${t.label} envoyé au serveur.`,
      id: cmd.id,
    };
  }

  // ─────────────────── Pont file d'attente (côté jeu) ───────────────────

  /**
   * Draine les commandes en attente et les passe DISPATCHED (elles ne seront
   * pas ré-envoyées). Appelé par le pont ShinobiCore (HMAC), pas par le panel.
   */
  async drainPending(): Promise<{ commands: { id: string; command: string }[] }> {
    const pending = await this.prisma.serverCommand.findMany({
      where: { status: 'PENDING' },
      orderBy: { createdAt: 'asc' },
      take: 20,
      select: { id: true, command: true },
    });
    if (pending.length > 0) {
      await this.prisma.serverCommand.updateMany({
        where: { id: { in: pending.map((p) => p.id) } },
        data: { status: 'DISPATCHED', dispatchedAt: new Date() },
      });
    }
    return { commands: pending };
  }

  /** Enregistre le résultat d'exécution des commandes (renvoyé par le pont). */
  async ack(results: { id: string; ok: boolean; output?: string }[]): Promise<{ ok: true }> {
    for (const r of results) {
      await this.prisma.serverCommand
        .update({
          where: { id: r.id },
          data: {
            status: r.ok ? 'DONE' : 'FAILED',
            output: r.output ? r.output.slice(0, 2000) : null,
            completedAt: new Date(),
          },
        })
        .catch(() => {
          /* commande inconnue / déjà nettoyée — on ignore */
        });
    }
    return { ok: true };
  }

  // ─────────────────────────── helpers ───────────────────────────

  private ext(rel: string): string {
    const dot = rel.lastIndexOf('.');
    const slash = rel.lastIndexOf('/');
    if (dot <= slash) return '';
    return rel.slice(dot + 1).toLowerCase();
  }
}
