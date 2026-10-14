import { api } from './api';

/**
 * Wrappers typés sur `/v1/admin/discord/*` : annonces et dev updates du
 * Discord staff, postées par le bot (cadre composants v2). Format des blocs
 * identique à apps/bot/src/messages/cadre.ts.
 */

export type Canal = 'annonces' | 'dev-updates';
export type PingOption = 'aucun' | 'testeurs' | 'everyone';
export type ActionMessage = 'brouillon' | 'apercu' | 'publier';
export type StatutMessage = 'brouillon' | 'publie' | 'retire';

export interface Lien {
  libelle: string;
  url: string;
  emoji?: string;
}

/** Un bloc côté API : une seule des clés est renseignée par bloc dans le panel. */
export interface Bloc {
  texte?: string;
  images?: string[];
  separateur?: boolean;
  liens?: Lien[];
}

export interface MessageInput {
  canal: Canal;
  couleur: string;
  blocs: Bloc[];
  ping: PingOption;
  action: ActionMessage;
}

export interface StaffDiscordMessage {
  id: string;
  canal: Canal;
  statut: StatutMessage;
  titre: string;
  couleur: string;
  blocs: Bloc[];
  ping: PingOption;
  discordUrl: string | null;
  discordMessageId: string | null;
  auteur: string;
  modifiePar: string | null;
  creeLe: string;
  modifieLe: string;
  publieLe: string | null;
  retireLe: string | null;
  medias: Record<string, string>;
  apercu?: { url: string | null; rendu?: unknown };
}

export function listMessages(canal?: Canal): Promise<StaffDiscordMessage[]> {
  return api<StaffDiscordMessage[]>(`/admin/discord/messages${canal ? `?canal=${canal}` : ''}`);
}

export function createMessage(input: MessageInput): Promise<StaffDiscordMessage> {
  return api<StaffDiscordMessage>('/admin/discord/messages', { method: 'POST', body: input });
}

export function updateMessage(id: string, input: MessageInput): Promise<StaffDiscordMessage> {
  return api<StaffDiscordMessage>(`/admin/discord/messages/${id}`, { method: 'PATCH', body: input });
}

export function removeMessage(id: string): Promise<{ removed: true; deleted: boolean }> {
  return api(`/admin/discord/messages/${id}`, { method: 'DELETE' });
}

export function uploadMedia(file: File): Promise<{ id: string; url: string }> {
  const form = new FormData();
  form.append('file', file);
  return api('/admin/discord/media', { method: 'POST', body: form });
}

// ── Couleurs et limites ─────────────────────────────────

export const COULEURS = [
  { nom: 'Laque', valeur: '#A0182B' },
  { nom: 'Or', valeur: '#D9A95E' },
  { nom: 'Crème', valeur: '#F5E9D0' },
  { nom: 'Succès', valeur: '#16A34A' },
] as const;

export const LIMITE_TEXTE = 4000;
export const LIMITE_IMAGES = 10;
export const LIMITE_LIENS = 5;
export const MAX_IMAGE_OCTETS = 8 * 1024 * 1024;

export const CANAL_LIBELLE: Record<Canal, string> = {
  annonces: 'Annonces',
  'dev-updates': 'Dev updates',
};

/** Nom affiché du salon dans l'aperçu (plan validé de RBN - STAFF). */
export const CANAL_SALON: Record<Canal, string> = {
  annonces: '📣・annonces',
  'dev-updates': '🛰️・dev-updates',
};

// ── Modèles prêts ───────────────────────────────────────

export interface Modele {
  id: string;
  nom: string;
  description: string;
  canal: Canal;
  couleur: string;
  blocs: Bloc[];
}

function aujourdhui(): string {
  const d = new Date();
  return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`;
}

export function modeles(): Modele[] {
  return [
    {
      id: 'dev-update',
      nom: 'Dev update',
      description: 'Ajouts, ajustements, fix et à tester',
      canal: 'dev-updates',
      couleur: '#A0182B',
      blocs: [
        {
          texte: `# Dev Update · ${aujourdhui()}\n-# reborn-hud 0.4.x · launcher 0.3.x · plugins Shinobi\nCe qui est arrivé **sur le serveur de dev** depuis la dernière mise à jour.`,
        },
        { images: [] },
        { separateur: true },
        { texte: '## Ajouts\n**Système :** ce qui change pour le joueur, en une phrase.\n**Autre système :** …' },
        { texte: '## Ajustements\n…' },
        { texte: '## Fix\n… · … · …' },
        { separateur: true },
        {
          texte:
            '## À tester\n1. Première vérification, avec la touche ou la commande.\n2. Deuxième vérification.\nUn post par retour dans {#retours-de-test}. Pour {@Testeur}.',
        },
      ],
    },
    {
      id: 'annonce',
      nom: 'Annonce',
      description: 'Titre, visuel, texte, signature',
      canal: 'annonces',
      couleur: '#D9A95E',
      blocs: [
        { texte: '# Titre de l’annonce\n-# Sous-titre court · date' },
        { images: [] },
        { texte: 'Le texte de l’annonce. **Ce qui change**, pour qui, à partir de quand.' },
        { separateur: true },
        { texte: '-# La Direction' },
      ],
    },
    {
      id: 'launcher',
      nom: 'Installation du launcher',
      description: 'Étapes + bouton de téléchargement',
      canal: 'annonces',
      couleur: '#D9A95E',
      blocs: [
        {
          texte:
            '# Installer le launcher Reborn\n-# Version 0.3.x · Windows 10 / 11 · les mises à jour suivantes se font toutes seules',
        },
        { images: [] },
        { separateur: true },
        {
          texte:
            "**1 ·** Télécharge l'installateur avec le bouton ci-dessous.\n**2 ·** Lance-le. Si Windows affiche « Windows a protégé votre ordinateur » : *Informations complémentaires* → *Exécuter quand même*.\n**3 ·** Ouvre le launcher et connecte ton **compte Microsoft** (Minecraft Java doit être sur ce compte).\n**4 ·** Lie ton **compte Discord** (obligatoire).\n**5 ·** Pour accéder au serveur de dev, demande ton **accès staff** à la Direction.\n**6 ·** Clique sur **Jouer** : Minecraft, les mods et les ressources s'installent tout seuls.",
        },
        {
          liens: [
            {
              libelle: 'Télécharger le launcher',
              url: 'https://github.com/OMZBRK/reborn-roleplay/releases/latest',
              emoji: '⬇️',
            },
            {
              libelle: 'Notes de version',
              url: 'https://github.com/OMZBRK/reborn-roleplay/releases',
              emoji: '📄',
            },
          ],
        },
        { texte: "-# Un souci à l'installation ? Écris dans {#retours-de-test} avec une capture." },
      ],
    },
  ];
}
