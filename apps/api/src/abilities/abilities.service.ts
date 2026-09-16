import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { Role } from '@prisma/client';
import yaml from 'js-yaml';
import { PrismaService } from '../prisma/prisma.service';
import { FilesService } from '../files/files.service';
import {
  CreateGraphDto,
  GraphStatus,
  UpdateGraphDto,
} from './dto/abilities.dto';

/**
 * Technique Creator — CRUD des graphes + compilation/déploiement.
 *
 * Le graphe (JSON) est la source de vérité. À la publication, on compile via
 * `@reborn/ability-compiler` (import dynamique : le package est ESM, l'API CJS),
 * on sérialise les 3 artefacts YAML, on les pousse par SFTP (FilesService) puis
 * on enfile les reloads (sa/ms/mm). ShinobiCore décide, le moteur dessine.
 */

// Cibles SFTP des artefacts générés.
const OUT_ABILITIES = 'plugins/ShinobiAbilities/abilities.generated.yml';
const OUT_MS = 'plugins/MagicSpells/spells-reborn-generated.yml';
const OUT_MYTHIC = 'plugins/MythicMobs/Skills/Reborn_generated.yml';

const YAML_HEADER =
  '# ⚠️ GÉNÉRÉ par le Technique Creator — NE PAS ÉDITER À LA MAIN.\n' +
  '# Source de vérité : les graphes du panel. Régénéré à chaque déploiement.\n';

type Compiler = {
  compile: typeof import('@reborn/ability-compiler/compile').compile;
  validateGraph: typeof import('@reborn/ability-compiler/dag').validateGraph;
  validateReferences: typeof import('@reborn/ability-compiler/dag').validateReferences;
  CompileError: typeof import('@reborn/ability-compiler/dag').CompileError;
  TechniqueGraph: typeof import('@reborn/ability-compiler/schema').TechniqueGraph;
};

@Injectable()
export class AbilitiesService {
  private compilerPromise?: Promise<Compiler>;

  constructor(
    private readonly prisma: PrismaService,
    private readonly files: FilesService,
  ) {}

  /** Charge le compilateur ESM une seule fois (import dynamique depuis CJS). */
  private compiler(): Promise<Compiler> {
    if (!this.compilerPromise) {
      this.compilerPromise = (async () => {
        const [compileMod, dagMod, schemaMod] = await Promise.all([
          import('@reborn/ability-compiler/compile'),
          import('@reborn/ability-compiler/dag'),
          import('@reborn/ability-compiler/schema'),
        ]);
        return {
          compile: compileMod.compile,
          validateGraph: dagMod.validateGraph,
          validateReferences: dagMod.validateReferences,
          CompileError: dagMod.CompileError,
          TechniqueGraph: schemaMod.TechniqueGraph,
        };
      })();
    }
    return this.compilerPromise;
  }

  /** Parse + valide un graphe brut ; renvoie le graphe normalisé ou lève 400. */
  private async parseGraph(raw: unknown, slug: string) {
    const c = await this.compiler();
    const parsed = c.TechniqueGraph.safeParse(raw);
    if (!parsed.success) {
      const msg = parsed.error.issues
        .map((i) => `${i.path.join('.') || '(racine)'}: ${i.message}`)
        .join(' ; ');
      throw new BadRequestException(`Graphe invalide — ${msg}`);
    }
    if (parsed.data.id !== slug) {
      throw new BadRequestException(
        `L'id du graphe (${parsed.data.id}) doit être égal au slug (${slug}).`,
      );
    }
    try {
      c.validateGraph(parsed.data);
    } catch (e) {
      throw new BadRequestException((e as Error).message);
    }
    return parsed.data;
  }

  // ── CRUD ───────────────────────────────────────────────

  list(status?: GraphStatus) {
    return this.prisma.techniqueGraph.findMany({
      where: status ? { status } : undefined,
      orderBy: { updatedAt: 'desc' },
      select: {
        id: true,
        slug: true,
        name: true,
        category: true,
        status: true,
        updatedAt: true,
      },
    });
  }

  async get(id: string) {
    const g = await this.prisma.techniqueGraph.findUnique({ where: { id } });
    if (!g) throw new NotFoundException('Technique introuvable.');
    return g;
  }

  async create(dto: CreateGraphDto, actorId: string) {
    await this.parseGraph(dto.graph, dto.slug);
    const exists = await this.prisma.techniqueGraph.findUnique({
      where: { slug: dto.slug },
    });
    if (exists)
      throw new BadRequestException(`Le slug '${dto.slug}' existe déjà.`);
    return this.prisma.techniqueGraph.create({
      data: {
        slug: dto.slug,
        name: dto.name,
        category: dto.category ?? '',
        status: dto.status ?? 'DRAFT',
        graph: dto.graph as object,
        createdById: actorId,
        updatedById: actorId,
      },
    });
  }

  async update(id: string, dto: UpdateGraphDto, actorId: string) {
    const current = await this.get(id);
    if (dto.graph !== undefined) await this.parseGraph(dto.graph, current.slug);
    return this.prisma.techniqueGraph.update({
      where: { id },
      data: {
        name: dto.name ?? undefined,
        category: dto.category ?? undefined,
        status: dto.status ?? undefined,
        graph: dto.graph !== undefined ? (dto.graph as object) : undefined,
        updatedById: actorId,
      },
    });
  }

  async remove(id: string) {
    await this.get(id);
    await this.prisma.techniqueGraph.delete({ where: { id } });
    return { ok: true };
  }

  // ── Compile (dry-run) & Deploy ─────────────────────────

  /** Valide un graphe sans rien écrire — renvoie les avertissements de compil. */
  async validateOne(id: string) {
    const g = await this.get(id);
    const c = await this.compiler();
    const graph = await this.parseGraph(g.graph, g.slug);
    const { warnings } = c.compile(graph);
    return { ok: true, warnings };
  }

  /**
   * Compile toutes les techniques PUBLISHED, écrit les 3 artefacts par SFTP,
   * puis enfile les reloads. Fail-fast : une seule technique invalide annule
   * tout le déploiement (aucune écriture partielle).
   */
  async deploy(role: Role, actorId: string) {
    const c = await this.compiler();
    const rows = await this.prisma.techniqueGraph.findMany({
      where: { status: 'PUBLISHED' },
    });
    if (rows.length === 0)
      throw new BadRequestException('Aucune technique PUBLISHED à déployer.');

    const graphs = [];
    for (const row of rows) {
      const parsed = c.TechniqueGraph.safeParse(row.graph);
      if (!parsed.success)
        throw new BadRequestException(
          `Technique '${row.slug}' : graphe corrompu en base.`,
        );
      graphs.push(parsed.data);
    }

    try {
      for (const g of graphs) c.validateGraph(g);
      c.validateReferences(graphs);
    } catch (e) {
      throw new BadRequestException((e as Error).message);
    }

    const abilities: unknown[] = [];
    const msSpells: Record<string, unknown> = {};
    const mythicSkills: Record<string, unknown> = {};
    const warnings: string[] = [];
    for (const g of graphs) {
      const r = c.compile(g);
      abilities.push({ id: g.id, ...r.ability });
      Object.assign(msSpells, r.msSpells);
      Object.assign(mythicSkills, r.mythicSkills);
      warnings.push(...r.warnings);
    }

    const dump = (data: unknown) =>
      YAML_HEADER + yaml.dump(data, { lineWidth: 100, noRefs: true });

    // Écritures SFTP (le scope DEVELOPPEUR couvre les 3 racines).
    const written: string[] = [];
    await this.files.write(role, actorId, OUT_ABILITIES, dump({ abilities }));
    written.push(OUT_ABILITIES);
    if (Object.keys(msSpells).length) {
      await this.files.write(role, actorId, OUT_MS, dump({ spells: msSpells }));
      written.push(OUT_MS);
    }
    if (Object.keys(mythicSkills).length) {
      await this.files.write(role, actorId, OUT_MYTHIC, dump(mythicSkills));
      written.push(OUT_MYTHIC);
    }

    // Reloads (enfilés dans ServerCommand, drainés par le pont plugin).
    const reloaded: string[] = [];
    for (const target of ['abilities', 'magicspells', 'mythicmobs']) {
      try {
        await this.files.reload(role, actorId, target);
        reloaded.push(target);
      } catch {
        // reload best-effort : l'écriture est faite, le reload peut être relancé.
      }
    }

    return {
      deployed: graphs.length,
      written,
      reloaded,
      msSpells: Object.keys(msSpells).length,
      mythicSkills: Object.keys(mythicSkills).length,
      warnings,
    };
  }
}
