/**
 * ============================================================================
 *  CADRE — rendu « spec JSON → message composants v2 » partagé
 * ============================================================================
 *
 *  Un message Reborn mis en forme = UN cadre (conteneur, barre de couleur à gauche) qui enchaîne des blocs :
 *    { "texte": "## Titre\n…" }                       → bloc de texte markdown Discord
 *    { "images": ["a.jpg", "b.jpg"] }                  → galerie (1 à 10 images)
 *    { "separateur": true }                            → filet de séparation
 *    { "liens": [ { "libelle", "url", "emoji"? } ] }   → rangée de boutons-liens (5 max)
 *  Dans les textes, `{#salon}` devient une mention de salon et `{@rôle}` une mention de rôle (affichées,
 *  jamais notifiées : `allowedMentions` est vide sauf ping explicite).
 *
 *  Utilisé par :
 *    - scripts/post-message.ts (fichier JSON hors dépôt, images = chemins relatifs au fichier) ;
 *    - messages/staff-message.ts (route signée HMAC /webhooks/staff-message, images envoyées par l'API).
 *  La façon de trouver une image est donc injectée (`ResolveurImage`) ; le reste est commun.
 * ============================================================================
 */
import {
  ActionRowBuilder,
  AttachmentBuilder,
  ButtonBuilder,
  ButtonStyle,
  ContainerBuilder,
  MediaGalleryBuilder,
  MediaGalleryItemBuilder,
  MessageFlags,
  SeparatorBuilder,
  SeparatorSpacingSize,
  TextDisplayBuilder,
  type Guild,
  type GuildBasedChannel,
  type MessageMentionOptions,
} from "discord.js";

export interface LienSpec {
  libelle: string;
  url: string;
  emoji?: string;
}

export interface BlocSpec {
  texte?: string;
  images?: string[];
  separateur?: boolean;
  liens?: LienSpec[];
}

export interface CadreSpec {
  /** Couleur de la barre du cadre, `#RRGGBB`. Défaut : laque Reborn. */
  couleur?: string;
  blocs: BlocSpec[];
}

/** Laque Reborn (`menu/Colors.java`), couleur de barre par défaut. */
export const COULEUR_DEFAUT = "#A0182B";

/**
 * Limites Discord des messages composants v2 (doc « Component Reference ») :
 * 4000 caractères de texte au total, 40 composants (imbriqués compris), 10 éléments par galerie,
 * 10 pièces jointes par message, 5 boutons par rangée, libellé de bouton ≤ 80 caractères.
 */
export const LIMITES = {
  texteTotal: 4000,
  composants: 40,
  imagesParGalerie: 10,
  fichiers: 10,
  boutonsParRangee: 5,
  libelleBouton: 80,
} as const;

/** Comment obtenir le contenu d'une image citée dans un bloc (`images: [ref]`). */
export type ResolveurImage = (ref: string) => { nom: string; donnees: Buffer | string };

/** Transformation appliquée à chaque texte avant rendu (mentions `{#salon}` / `{@rôle}`). */
export type ResolveurTexte = (md: string) => string;

/** Erreur de contenu (spec invalide) : la route HTTP la renvoie en 400. */
export class ErreurCadre extends Error {
  constructor(public readonly details: string[]) {
    super(details.join(" ; "));
  }
}

/** Minuscules sans accent ni emoji ni ponctuation : « 🛰️・dev-updates » → « dev-updates ». */
export function normaliser(s: string): string {
  return s
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[^\p{L}\p{N}-]+/gu, " ")
    .trim()
    .toLowerCase();
}

export function trouverSalon(guild: Guild, nom: string): GuildBasedChannel | undefined {
  const n = normaliser(nom);
  return guild.channels.cache.find((c) => normaliser(c.name) === n);
}

export function trouverRole(guild: Guild, nom: string) {
  const n = normaliser(nom);
  return guild.roles.cache.find((r) => normaliser(r.name) === n);
}

/** `{#salon}` → `<#id>`, `{@rôle}` → `<@&id>` ; un nom introuvable reste en texte (`#salon`, `@rôle`). */
export function resoudreMentions(guild: Guild | null, md: string): string {
  return md
    .replace(/\{#([^}]+)\}/g, (_, n: string) => {
      const c = guild ? trouverSalon(guild, n) : undefined;
      return c ? `<#${c.id}>` : `#${n}`;
    })
    .replace(/\{@([^}]+)\}/g, (_, n: string) => {
      const r = guild ? trouverRole(guild, n) : undefined;
      return r ? `<@&${r.id}>` : `@${n}`;
    });
}

const RE_COULEUR = /^#?[0-9a-fA-F]{6}$/;
const RE_URL = /^(https?|discord):\/\/\S+$/;

/**
 * Vérifie une spec AVANT tout appel à Discord. Retourne la liste des problèmes (vide = valide).
 * `texte` sert à mesurer la longueur après résolution des mentions (c'est elle que Discord compte).
 */
export function validerCadre(spec: unknown, texte: ResolveurTexte = (s) => s): string[] {
  const err: string[] = [];
  if (!spec || typeof spec !== "object") return ["spec absente"];
  const s = spec as Partial<CadreSpec>;
  if (s.couleur !== undefined && (typeof s.couleur !== "string" || !RE_COULEUR.test(s.couleur))) {
    err.push(`couleur invalide (${String(s.couleur)}), attendu #RRGGBB`);
  }
  if (!Array.isArray(s.blocs) || s.blocs.length === 0) return [...err, "aucun bloc"];

  let texteTotal = 0;
  let composants = 1; // le conteneur
  let fichiers = 0;
  s.blocs.forEach((b, i) => {
    const ou = `bloc ${i + 1}`;
    if (!b || typeof b !== "object") {
      err.push(`${ou} : invalide`);
      return;
    }
    let contenu = false;
    if (b.texte !== undefined) {
      if (typeof b.texte !== "string") err.push(`${ou} : texte invalide`);
      else if (b.texte.trim()) {
        texteTotal += texte(b.texte).length;
        composants += 1;
        contenu = true;
      }
    }
    if (b.images !== undefined) {
      if (!Array.isArray(b.images) || b.images.some((x) => typeof x !== "string" || !x)) {
        err.push(`${ou} : images invalides`);
      } else if (b.images.length > 0) {
        if (b.images.length > LIMITES.imagesParGalerie) {
          err.push(`${ou} : ${b.images.length} images (max ${LIMITES.imagesParGalerie} par galerie)`);
        }
        fichiers += b.images.length;
        composants += 1;
        contenu = true;
      }
    }
    if (b.separateur) {
      composants += 1;
      contenu = true;
    }
    if (b.liens !== undefined) {
      if (!Array.isArray(b.liens)) err.push(`${ou} : liens invalides`);
      else if (b.liens.length > 0) {
        if (b.liens.length > LIMITES.boutonsParRangee) {
          err.push(`${ou} : ${b.liens.length} boutons (max ${LIMITES.boutonsParRangee} par rangée)`);
        }
        b.liens.forEach((l, j) => {
          const lo = `${ou}, bouton ${j + 1}`;
          if (!l || typeof l.libelle !== "string" || !l.libelle.trim()) err.push(`${lo} : libellé vide`);
          else if (l.libelle.length > LIMITES.libelleBouton) err.push(`${lo} : libellé > ${LIMITES.libelleBouton} caractères`);
          if (!l || typeof l.url !== "string" || !RE_URL.test(l.url)) err.push(`${lo} : URL invalide (http(s)://…)`);
        });
        composants += 1 + b.liens.length;
        contenu = true;
      }
    }
    if (!contenu) err.push(`${ou} : vide`);
  });
  if (texteTotal > LIMITES.texteTotal) {
    err.push(`texte total ${texteTotal} caractères (max ${LIMITES.texteTotal} par message)`);
  }
  if (fichiers > LIMITES.fichiers) err.push(`${fichiers} images (max ${LIMITES.fichiers} par message)`);
  if (composants > LIMITES.composants) err.push(`${composants} composants (max ${LIMITES.composants})`);
  return err;
}

/**
 * Construit le conteneur et les pièces jointes. Lève `ErreurCadre` si la spec est invalide.
 * Les noms de pièces jointes doivent être uniques dans le message (`attachment://<nom>`).
 */
export function construireCadre(
  spec: CadreSpec,
  opts: { image: ResolveurImage; texte?: ResolveurTexte },
): { container: ContainerBuilder; files: AttachmentBuilder[] } {
  const texte = opts.texte ?? ((s: string) => s);
  const erreurs = validerCadre(spec, texte);
  if (erreurs.length) throw new ErreurCadre(erreurs);

  const couleur = parseInt((spec.couleur ?? COULEUR_DEFAUT).replace("#", ""), 16);
  const c = new ContainerBuilder().setAccentColor(couleur);
  const files: AttachmentBuilder[] = [];
  const noms = new Set<string>();
  for (const b of spec.blocs) {
    if (b.texte?.trim()) c.addTextDisplayComponents(new TextDisplayBuilder().setContent(texte(b.texte)));
    if (b.images?.length) {
      const gal = new MediaGalleryBuilder();
      for (const ref of b.images) {
        const { nom, donnees } = opts.image(ref);
        if (!noms.has(nom)) {
          noms.add(nom);
          files.push(new AttachmentBuilder(donnees, { name: nom }));
        }
        gal.addItems(new MediaGalleryItemBuilder().setURL(`attachment://${nom}`));
      }
      c.addMediaGalleryComponents(gal);
    }
    if (b.separateur) c.addSeparatorComponents(new SeparatorBuilder().setDivider(true).setSpacing(SeparatorSpacingSize.Small));
    if (b.liens?.length) {
      const row = new ActionRowBuilder<ButtonBuilder>();
      for (const l of b.liens) {
        const btn = new ButtonBuilder().setStyle(ButtonStyle.Link).setLabel(l.libelle).setURL(l.url);
        if (l.emoji) btn.setEmoji(l.emoji);
        row.addComponents(btn);
      }
      c.addActionRowComponents(row);
    }
  }
  return { container: c, files };
}

/** Ligne de ping posée AU-DESSUS du cadre (un composant v2 ne peut pas avoir de `content`). */
export interface Ping {
  texte: string;
  mentions: MessageMentionOptions;
}

/**
 * Charge utile prête pour `channel.send()` / `message.edit()`. Sans `ping`, aucune mention ne notifie.
 */
export function payloadCadre(
  container: ContainerBuilder,
  files: AttachmentBuilder[],
  opts: { ping?: Ping; entete?: string } = {},
) {
  const components: (ContainerBuilder | TextDisplayBuilder)[] = [];
  if (opts.entete) components.push(new TextDisplayBuilder().setContent(opts.entete));
  if (opts.ping) components.push(new TextDisplayBuilder().setContent(opts.ping.texte));
  components.push(container);
  return {
    components,
    files,
    flags: MessageFlags.IsComponentsV2 as const,
    allowedMentions: opts.ping?.mentions ?? { parse: [] as never[] },
  };
}
