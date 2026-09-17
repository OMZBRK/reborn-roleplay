import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { Role } from '@prisma/client';
import yaml from 'js-yaml';
import { PrismaService } from '../prisma/prisma.service';
import { FilesService } from '../files/files.service';
import { compile } from './compiler/compile';
import { CompileError, validateGraph, validateReferences } from './compiler/dag';
import {
  guessRoot,
  importTechnique,
  listSpellNames,
  parseSpellFile,
} from './compiler/import';
import { TechniqueGraph } from './compiler/schema';
import {
  CreateGraphDto,
  GraphStatus,
  ImportGraphDto,
  ImportParseDto,
  UpdateGraphDto,
} from './dto/abilities.dto';

/**
 * Technique Creator — CRUD des graphes + compilation/déploiement.
 *
 * Le graphe (JSON) est la source de vérité. À la publication, on compile via le
 * compilateur vendorisé (apps/api/src/abilities/compiler, miroir du package
 * @reborn/ability-compiler — compilé directement par nest build, pas d'import
 * ESM cross-package en conteneur), on sérialise les 3 artefacts YAML, on les
 * pousse par SFTP (FilesService) puis on enfile les reloads (sa/ms/mm).
 */

const OUT_ABILITIES = 'plugins/ShinobiAbilities/abilities.generated.yml';
const OUT_MS = 'plugins/MagicSpells/spells-reborn-generated.yml';

const YAML_HEADER =
  '# ⚠️ GÉNÉRÉ par le Technique Creator — NE PAS ÉDITER À LA MAIN.\n' +
  '# Source de vérité : les graphes du panel. Régénéré à chaque déploiement.\n';

@Injectable()
export class AbilitiesService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly files: FilesService,
  ) {}

  /** Parse + valide un graphe brut ; renvoie le graphe normalisé ou lève 400. */
  private parseGraph(raw: unknown, slug: string) {
    const parsed = TechniqueGraph.safeParse(raw);
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
      validateGraph(parsed.data);
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
    this.parseGraph(dto.graph, dto.slug);
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
    if (dto.graph !== undefined) this.parseGraph(dto.graph, current.slug);
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

  // ── Import (MagicSpells YAML → graphe éditable) ────────

  /** Résout le YAML depuis un chemin serveur (SFTP) ou le corps brut. */
  private async resolveYaml(
    role: Role,
    actorId: string,
    yaml?: string,
    serverPath?: string,
  ): Promise<string> {
    if (serverPath) {
      const f = await this.files.read(role, actorId, serverPath);
      if (f.encoding !== 'utf8')
        throw new BadRequestException('Le fichier serveur n’est pas du texte.');
      return f.content;
    }
    if (yaml && yaml.trim()) return yaml;
    throw new BadRequestException('Fournis un YAML ou un chemin serveur.');
  }

  /** Analyse : liste les sorts + devine une racine, pour que l'UI propose un choix. */
  async importParse(role: Role, actorId: string, dto: ImportParseDto) {
    const text = await this.resolveYaml(role, actorId, dto.yaml, dto.serverPath);
    let parsed;
    try {
      parsed = parseSpellFile(text);
    } catch (e) {
      throw new BadRequestException(`YAML illisible : ${(e as Error).message}`);
    }
    const spellNames = listSpellNames(parsed);
    if (spellNames.length === 0)
      throw new BadRequestException('Aucun sort MagicSpells trouvé dans ce fichier.');
    return { spellNames, guessedRoot: guessRoot(parsed) ?? spellNames[0], yaml: text };
  }

  /** Construit le graphe éditable depuis le sort racine choisi. */
  async importGraph(role: Role, actorId: string, dto: ImportGraphDto) {
    const text = await this.resolveYaml(role, actorId, dto.yaml, dto.serverPath);
    let parsed;
    try {
      parsed = parseSpellFile(text);
    } catch (e) {
      throw new BadRequestException(`YAML illisible : ${(e as Error).message}`);
    }
    let graph;
    try {
      graph = importTechnique(parsed, dto.rootSpell);
    } catch (e) {
      throw new BadRequestException((e as Error).message);
    }
    return { graph };
  }

  // ── Compile (dry-run), Preview & Deploy ────────────────

  /** Valide un graphe sans rien écrire — renvoie les avertissements de compil. */
  async validateOne(id: string) {
    const g = await this.get(id);
    const graph = this.parseGraph(g.graph, g.slug);
    const { warnings } = compile(graph);
    return { ok: true, warnings };
  }

  /**
   * « Tester sur moi » — enfile `sa preview <slug> <pseudo>` pour le staff
   * authentifié. Commande construite côté serveur (slug validé + pseudo du
   * compte lié), jamais du texte libre ; le pont plugin re-valide via le
   * préfixe whitelisté `sa preview `.
   */
  async preview(userId: string, id: string) {
    const tech = await this.get(id);
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      select: { minecraftUsername: true },
    });
    if (!user?.minecraftUsername)
      throw new BadRequestException(
        'Aucun compte Minecraft lié — impossible de cibler ton personnage.',
      );
    const command = `sa preview ${tech.slug} ${user.minecraftUsername}`;
    await this.prisma.serverCommand.create({
      data: { target: 'abilities-preview', command, requestedById: userId },
    });
    return { ok: true, queued: command };
  }

  /**
   * Compile toutes les techniques PUBLISHED, écrit les 3 artefacts par SFTP,
   * puis enfile les reloads. Fail-fast : une seule technique invalide annule
   * tout le déploiement (aucune écriture partielle).
   */
  async deploy(role: Role, actorId: string) {
    const rows = await this.prisma.techniqueGraph.findMany({
      where: { status: 'PUBLISHED' },
    });
    if (rows.length === 0)
      throw new BadRequestException('Aucune technique PUBLISHED à déployer.');

    const graphs = [];
    for (const row of rows) {
      const parsed = TechniqueGraph.safeParse(row.graph);
      if (!parsed.success)
        throw new BadRequestException(
          `Technique '${row.slug}' : graphe corrompu en base.`,
        );
      graphs.push(parsed.data);
    }

    try {
      for (const g of graphs) validateGraph(g);
      validateReferences(graphs);
    } catch (e) {
      if (e instanceof CompileError)
        throw new BadRequestException(e.message);
      throw e;
    }

    const abilities: unknown[] = [];
    const spells: Record<string, unknown> = {};
    const magicItems: Record<string, unknown> = {};
    const warnings: string[] = [];
    for (const g of graphs) {
      const r = compile(g);
      abilities.push({ id: g.id, ...r.ability });
      Object.assign(spells, r.spells);
      Object.assign(magicItems, r.magicItems);
      warnings.push(...r.warnings);
    }

    const dump = (data: unknown) =>
      YAML_HEADER + yaml.dump(data, { lineWidth: 100, noRefs: true });

    const written: string[] = [];
    await this.files.write(role, actorId, OUT_ABILITIES, dump({ abilities }));
    written.push(OUT_ABILITIES);
    // MagicSpells file : magic-items + sorts en clés top-level (style serveur).
    if (Object.keys(spells).length || Object.keys(magicItems).length) {
      const msFile: Record<string, unknown> = {};
      if (Object.keys(magicItems).length) msFile['magic-items'] = magicItems;
      Object.assign(msFile, spells);
      await this.files.write(role, actorId, OUT_MS, dump(msFile));
      written.push(OUT_MS);
    }

    const reloaded: string[] = [];
    for (const target of ['abilities', 'magicspells']) {
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
      spells: Object.keys(spells).length,
      magicItems: Object.keys(magicItems).length,
      warnings,
    };
  }
}
