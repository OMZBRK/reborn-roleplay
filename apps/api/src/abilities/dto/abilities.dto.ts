import {
  IsIn,
  IsObject,
  IsOptional,
  IsString,
  Matches,
  MaxLength,
} from 'class-validator';

/** Statuts publiables — miroir de l'enum Prisma TechniqueGraphStatus. */
export const GRAPH_STATUSES = ['DRAFT', 'PUBLISHED', 'ARCHIVED'] as const;
export type GraphStatus = (typeof GRAPH_STATUSES)[number];

/**
 * Le champ `graph` est le graph.json (nodes + edges). Ici on ne valide que
 * grossièrement (objet non vide) — la validation profonde (schéma Zod + DAG)
 * est faite dans le service via `@reborn/ability-compiler`, qui renvoie des
 * erreurs précises. On évite ainsi de dupliquer le schéma en class-validator.
 */
export class CreateGraphDto {
  @IsString()
  @Matches(/^[a-z0-9_]+$/, {
    message: 'slug: lower_snake_case uniquement (a-z, 0-9, _)',
  })
  @MaxLength(64)
  slug!: string;

  @IsString()
  @MaxLength(128)
  name!: string;

  @IsOptional()
  @IsString()
  @MaxLength(128)
  category?: string;

  @IsObject()
  graph!: Record<string, unknown>;

  @IsOptional()
  @IsIn(GRAPH_STATUSES)
  status?: GraphStatus;
}

/** Analyse d'un fichier MagicSpells à importer (coller YAML OU chemin serveur). */
export class ImportParseDto {
  @IsOptional()
  @IsString()
  yaml?: string;

  @IsOptional()
  @IsString()
  @MaxLength(512)
  serverPath?: string;
}

/** Construction du graphe depuis un YAML + un sort racine choisi. */
export class ImportGraphDto {
  @IsString()
  yaml!: string;

  @IsString()
  rootSpell!: string;

  @IsOptional()
  @IsString()
  @MaxLength(512)
  serverPath?: string;
}

export class UpdateGraphDto {
  @IsOptional()
  @IsString()
  @MaxLength(128)
  name?: string;

  @IsOptional()
  @IsString()
  @MaxLength(128)
  category?: string;

  @IsOptional()
  @IsObject()
  graph?: Record<string, unknown>;

  @IsOptional()
  @IsIn(GRAPH_STATUSES)
  status?: GraphStatus;
}
