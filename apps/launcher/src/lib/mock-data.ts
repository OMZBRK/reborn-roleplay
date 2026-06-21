import { Calendar, ShieldCheck, Sparkles, type LucideIcon } from "lucide-react";

// Configuration visuelle + contenu placeholder des slots de la Home.
// Quand un slot sera branché à un endpoint API, son entrée sera remplacée par
// les vrais hooks (cf. routes/Home.tsx pour les slots déjà câblés).

export type HomeCardSlotConfig = {
  icon: LucideIcon;
  gradient: string;
  link: string;
  href: string;
};

export const HOME_CARD_GRADIENTS = {
  blue: "linear-gradient(135deg, #1e2a6b 0%, #a0182b 55%, #6f8aff 100%)",
  green: "linear-gradient(135deg, #0f3a22 0%, #16a34a 55%, #5be6a0 100%)",
  purple: "linear-gradient(135deg, #2b1b5c 0%, #6d3fd1 55%, #b89bff 100%)",
} as const;

// Slot Patch — branché sur fetchPatchnotes
export const PATCH_CARD: HomeCardSlotConfig = {
  icon: Sparkles,
  gradient: HOME_CARD_GRADIENTS.blue,
  link: "Voir le patch",
  href: "/patchnotes",
};

export const PATCH_CARD_FALLBACK = {
  kicker: "Patch notes",
  title: "En attente du premier patch",
  excerpt: "Aucune note publiée pour le moment. Reviens bientôt.",
  link: "Voir l'historique",
};

export const PATCH_CARD_ERROR = {
  kicker: "Patch notes",
  title: "Indisponible",
  excerpt: "Impossible de charger le dernier patch — vérifie ta connexion.",
  link: "Réessayer",
};

// Slot Whitelist — branché sur fetchWhitelistMe
export const WHITELIST_CARD: HomeCardSlotConfig = {
  icon: ShieldCheck,
  gradient: HOME_CARD_GRADIENTS.green,
  link: "Voir les détails",
  href: "/whitelist",
};

export const WHITELIST_CARD_STATES = {
  loading: {
    kicker: "Whitelist",
    title: "Chargement...",
    excerpt: "Récupération de ton statut.",
    link: "Voir les détails",
  },
  error: {
    kicker: "Whitelist",
    title: "Indisponible",
    excerpt: "Impossible de récupérer ton statut de whitelist.",
    link: "Réessayer",
  },
  none: {
    kicker: "Whitelist",
    title: "Statut : non postulé",
    excerpt: "Tu n'as pas encore déposé ta candidature RP. Postule pour rejoindre le serveur.",
    link: "Postuler",
  },
  PENDING: {
    kicker: "Whitelist",
    title: "Statut : en attente",
    excerpt: "Le staff examine ta candidature. Réponse sous 48h.",
    link: "Voir ma candidature",
  },
  APPROVED: (characterName: string) => ({
    kicker: "Whitelist",
    title: `Bienvenue, ${characterName}`,
    excerpt: "Ta whitelist est validée. Tu peux te connecter au serveur.",
    link: "Voir les détails",
  }),
  REJECTED: {
    kicker: "Whitelist",
    title: "Statut : refusée",
    excerpt: "Le staff a refusé ta candidature. Tu peux la modifier et la resoumettre.",
    link: "Modifier & resoumettre",
  },
  NEEDS_REVISION: {
    kicker: "Whitelist",
    title: "Statut : révision demandée",
    excerpt: "Le staff demande des modifications avant validation.",
    link: "Modifier & resoumettre",
  },
} as const;

// Slot Actu RP — TODO: brancher sur API quand calendrier RP dispo (mock pour l'instant)
export const NEWS_RP_CARD = {
  kicker: "Actu RP",
  title: "Calendrier RP",
  excerpt:
    "Les événements RP en jeu seront annoncés ici — tournoi des chûnin, examen jônin, raids de clan.",
  link: "Voir le règlement",
  href: "/rules",
  icon: Calendar,
  gradient: HOME_CARD_GRADIENTS.purple,
};
