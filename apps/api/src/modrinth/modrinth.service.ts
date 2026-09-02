import { Injectable, Logger } from '@nestjs/common';
import { Cron, CronExpression } from '@nestjs/schedule';
import { PrismaService } from '../prisma/prisma.service';
import { WebhooksService } from '../webhooks/webhooks.service';

/**
 * Détection planifiée des mises à jour de mods tiers sur Modrinth.
 *
 * Tourne côté API (accès DB au manifeste courant + pont bot déjà en place). Ne
 * fait QUE notifier : le staff lance ensuite `prepare` + `publish` en local
 * (packages/modrinth-sync), la clé de signature restant hors-ligne.
 *
 * Gardé par MODRINTH_SYNC_ENABLED=true. Le mapping mods→slug/policy doit rester
 * cohérent avec packages/modrinth-sync/mods.config.json (les `pinned` — Axiom,
 * emotecraft, PlayerAnimationLib, BendableCuboids — ne sont pas surveillés).
 */
interface ModMap {
  prefix: string;
  slug: string;
}

const GAME_VERSION = process.env.REBORN_MC_VERSION ?? '26.2';
const LOADER = 'fabric';

// Uniquement les mods `auto` (les `pinned` sont volontairement absents).
const AUTO_MODS: ModMap[] = [
  { prefix: 'sodium-fabric-', slug: 'sodium' },
  { prefix: 'sodium-extra-fabric-', slug: 'sodium-extra' },
  { prefix: 'lithium-fabric-', slug: 'lithium' },
  { prefix: 'fabric-api-', slug: 'fabric-api' },
  { prefix: 'fabric-language-kotlin-', slug: 'fabric-language-kotlin' },
  { prefix: 'iris-fabric-', slug: 'iris' },
  { prefix: 'DistantHorizons-', slug: 'distanthorizons' },
  { prefix: 'NoChatReports-', slug: 'no-chat-reports' },
  { prefix: 'entityculling-fabric-', slug: 'entityculling' },
  { prefix: 'entity_texture_features-', slug: 'entitytexturefeatures' },
  { prefix: 'entity_model_features-', slug: 'entity-model-features' },
  { prefix: 'plasmovoice-fabric-', slug: 'plasmo-voice' },
  { prefix: 'firstperson-fabric-', slug: 'first-person-model' },
  { prefix: 'yet_another_config_lib_v3-', slug: 'yacl' },
  { prefix: 'zoomify-', slug: 'zoomify' },
  { prefix: 'skinlayers3d-fabric-', slug: '3dskinlayers' },
];

interface ModrinthVersion {
  date_published: string;
  version_type: string;
  files: Array<{ filename: string; primary: boolean }>;
}

@Injectable()
export class ModrinthService {
  private readonly logger = new Logger(ModrinthService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly webhooks: WebhooksService,
  ) {}

  @Cron(CronExpression.EVERY_DAY_AT_10AM)
  async scheduledCheck(): Promise<void> {
    if (process.env.MODRINTH_SYNC_ENABLED !== 'true') return;
    try {
      await this.checkAndNotify();
    } catch (e) {
      this.logger.warn(`Modrinth check échoué : ${(e as Error).message}`);
    }
  }

  /** Compare chaque mod `auto` du manifeste courant à sa dernière version Modrinth
   *  compatible ; notifie le bot s'il y a des updates. Retourne le nombre trouvé. */
  async checkAndNotify(): Promise<number> {
    const manifest = await this.prisma.manifest.findFirst({
      where: { isCurrent: true },
      orderBy: { publishedAt: 'desc' },
    });
    if (!manifest) {
      this.logger.warn('Aucun manifeste courant — check Modrinth ignoré.');
      return 0;
    }
    const files = manifest.files as unknown as Array<{ path: string }>;
    const lines: string[] = [];

    for (const mod of AUTO_MODS) {
      const cur = files.find(
        (f) => f.path.startsWith('mods/') && f.path.slice(5).startsWith(mod.prefix),
      );
      if (!cur) continue;
      const curName = cur.path.slice(5);
      const latest = await this.latestModrinthFile(mod.slug);
      if (latest && latest.filename !== curName) {
        lines.push(
          `**${mod.slug}** : ${curName.replace(/\.jar$/, '')} → ${latest.filename.replace(/\.jar$/, '')} (${latest.type})`,
        );
      }
    }

    if (lines.length === 0) {
      this.logger.log('Modrinth : aucun mod à jour.');
      return 0;
    }
    const version = this.bumpPatch(manifest.version);
    this.logger.log(`Modrinth : ${lines.length} update(s) détecté(s) → notif bot.`);
    await this.webhooks.modsUpdate({ count: lines.length, version, mods: lines });
    return lines.length;
  }

  private async latestModrinthFile(
    slug: string,
  ): Promise<{ filename: string; type: string } | null> {
    const gv = encodeURIComponent(JSON.stringify([GAME_VERSION]));
    const ld = encodeURIComponent(JSON.stringify([LOADER]));
    const url = `https://api.modrinth.com/v2/project/${encodeURIComponent(slug)}/version?game_versions=${gv}&loaders=${ld}`;
    const res = await fetch(url, {
      headers: { 'User-Agent': 'reborn-roleplay/api (modrinth-sync)' },
      signal: AbortSignal.timeout(10000),
    });
    if (!res.ok) return null;
    const versions = (await res.json()) as ModrinthVersion[];
    if (!Array.isArray(versions) || versions.length === 0) return null;
    versions.sort(
      (a, b) => new Date(b.date_published).getTime() - new Date(a.date_published).getTime(),
    );
    const v = versions[0];
    const pf = v.files.find((f) => f.primary) ?? v.files[0];
    return pf ? { filename: pf.filename, type: v.version_type } : null;
  }

  private bumpPatch(v: string): string {
    const p = v.split('.').map((n) => parseInt(n, 10) || 0);
    while (p.length < 3) p.push(0);
    p[2] += 1;
    return p.join('.');
  }
}
