import {
  ConflictException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { AuditService } from '../audit/audit.service';
import { PrismaService } from '../prisma/prisma.service';
import { CreateReleaseDto, GetUpdateQueryDto } from './releases.dto';

/**
 * Compare deux versions semver basiques "X.Y.Z" → > 0 si a > b, 0 egal,
 * < 0 si a < b. Tolere "-beta.1" et autres suffixes : on compare seulement
 * les 3 premiers chiffres et on traite tout suffixe comme < release stable.
 */
export function compareVersions(a: string, b: string): number {
  const parse = (v: string) => {
    const [core, pre] = v.split('-');
    const parts = core.split('.').map((n) => Number.parseInt(n, 10) || 0);
    while (parts.length < 3) parts.push(0);
    return { parts, pre: pre ?? '' };
  };
  const A = parse(a);
  const B = parse(b);
  for (let i = 0; i < 3; i++) {
    if (A.parts[i] !== B.parts[i]) return A.parts[i] - B.parts[i];
  }
  // Egalite numerique : sans suffixe = stable > avec suffixe (pre-release).
  if (A.pre === B.pre) return 0;
  if (A.pre === '') return 1;
  if (B.pre === '') return -1;
  return A.pre.localeCompare(B.pre);
}

/**
 * Format de reponse attendu par tauri-plugin-updater (cf docs Tauri 2).
 * 204 No Content => pas d'update dispo.
 */
export interface TauriUpdateResponse {
  version: string;
  pub_date: string;
  url: string;
  signature: string;
  notes: string;
}

@Injectable()
export class ReleasesService {
  private readonly logger = new Logger(ReleasesService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly audit: AuditService,
  ) {}

  /**
   * Cherche la release la plus recente pour target+channel ET version
   * strictement superieure a `current`. Retourne null si pas d'update.
   */
  async findUpdate(
    query: GetUpdateQueryDto,
  ): Promise<TauriUpdateResponse | null> {
    const channel = query.channel ?? 'stable';
    const candidates = await this.prisma.launcherRelease.findMany({
      where: {
        target: query.target,
        channel,
      },
      orderBy: { publishedAt: 'desc' },
      take: 20, // tri final cote app, on borne pour eviter scan complet
    });
    const newer = candidates.filter(
      (r) => compareVersions(r.version, query.current) > 0,
    );
    if (newer.length === 0) return null;
    // Le plus haut version-wise (pas forcement le plus recent par date —
    // au cas ou un older release serait republie posterieurement).
    newer.sort((a, b) => compareVersions(b.version, a.version));
    const latest = newer[0]!;
    return {
      version: latest.version,
      pub_date: latest.publishedAt.toISOString(),
      url: latest.url,
      signature: latest.signature,
      notes: latest.notes ?? '',
    };
  }

  async list() {
    return this.prisma.launcherRelease.findMany({
      orderBy: { publishedAt: 'desc' },
    });
  }

  async create(dto: CreateReleaseDto, actorUserId: string) {
    try {
      const created = await this.prisma.launcherRelease.create({
        data: {
          version: dto.version,
          channel: dto.channel ?? 'stable',
          target: dto.target,
          url: dto.url,
          signature: dto.signature,
          notes: dto.notes,
        },
      });
      this.logger.log(
        `release publiee : ${created.target} v${created.version} (${created.channel})`,
      );
      void this.audit.log({
        actorId: actorUserId,
        action: 'release.publish',
        targetEntity: `release:${created.id}`,
        metadata: {
          version: created.version,
          target: created.target,
          channel: created.channel,
          url: created.url,
        },
        source: 'panel',
      });
      return created;
    } catch (err) {
      // Prisma P2002 = unique constraint violation (version+target deja
      // pris).
      if (
        typeof err === 'object' &&
        err !== null &&
        'code' in err &&
        (err as { code: string }).code === 'P2002'
      ) {
        throw new ConflictException(
          `Release ${dto.target} v${dto.version} existe deja.`,
        );
      }
      throw err;
    }
  }

  async remove(id: string, actorUserId: string) {
    const found = await this.prisma.launcherRelease.findUnique({
      where: { id },
    });
    if (!found) throw new NotFoundException('Release introuvable.');
    await this.prisma.launcherRelease.delete({ where: { id } });
    this.logger.log(`release supprimee : ${found.target} v${found.version}`);
    void this.audit.log({
      actorId: actorUserId,
      action: 'release.delete',
      targetEntity: `release:${id}`,
      metadata: {
        version: found.version,
        target: found.target,
        channel: found.channel,
      },
      source: 'panel',
    });
  }
}
