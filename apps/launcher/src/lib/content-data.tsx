// Mock data des sections Règlement & Lore.
// À remplacer par des appels API quand les endpoints `/v1/rules/categories` et
// `/v1/lore/categories` existeront — le format API actuel (un seul markdown par
// doc) n'est plus compatible avec la structure 3-niveaux du nouveau design.

import type { ReactNode } from "react";

export type Category = {
  id: string;
  num: number;
  name: string;
  color: string;
  glow: string;
  silhouette: ReactNode;
};

export type RulesSection = {
  title: string;
  body: string;
};

export type LoreSection = {
  title: string;
  body: string;
  era?: string;
};

export type RulesCategory = Category & {
  sections: RulesSection[];
};

export type LoreCategory = Category;

export type LoreDetail = {
  intro: string;
  sections: LoreSection[];
};

// ──────────────────────────────────────────────────────────────────────────────
// RÈGLEMENT
// ──────────────────────────────────────────────────────────────────────────────

export const RULES_CATEGORIES: RulesCategory[] = [
  {
    id: "roleplay",
    num: 1,
    name: "Roleplay",
    color: "#3b5bdb",
    glow: "rgba(59,91,219,0.4)",
    silhouette: (
      <path d="M50 18a14 14 0 0 1 14 14 14 14 0 0 1-14 14 14 14 0 0 1-14-14A14 14 0 0 1 50 18zm-26 64c0-12 12-20 26-20s26 8 26 20v8H24z" />
    ),
    sections: [
      {
        title: "Armes uniques / Item unique",
        body: "<p>Une <b>arme unique</b> est un objet narratif lié à un personnage. Elle ne se vole pas en HRP, ne se loot pas par opportunisme, et nécessite une <em>capture RP construite</em> pour changer de main.</p><ul><li>Maximum <b>une arme unique par personnage</b>, validée par le staff.</li><li>La perte ne peut survenir qu'à l'issue d'une <em>scène RP perdue</em> avec témoins.</li><li>La duplication via crash, alt ou exploit entraîne un <b>retrait définitif</b> et un avertissement.</li></ul>",
      },
      {
        title: "Arnaque / Racket",
        body: "<p>L'arnaque et le racket sont <em>autorisés</em> dans un cadre RP cohérent. Ils doivent rester <b>réalistes</b>, justifiés par le contexte (clan, dette, territoire) et ne jamais cibler un joueur incapable de répondre.</p><ul><li>Pas plus de <b>50 000 ryos</b> par scène pour un PNJ joueur isolé.</li><li>Le racket d'un commerce nécessite <em>3 joueurs minimum</em> et un préavis de 24h.</li></ul>",
      },
      {
        title: "Repli",
        body: "<p>Tout personnage a le droit de <b>se replier</b> si la scène devient injouable, déséquilibrée, ou si un crash technique survient. Le repli doit être <em>annoncé</em> en RP (fuite, embuscade contournée, fumigène) et non par déconnexion.</p>",
      },
      {
        title: "Batailles entre ninja (préméditée)",
        body: "<p>Une bataille préméditée se planifie <b>au moins 48h à l'avance</b> sur Discord avec un <em>arbitre staff</em>. Les conditions de victoire, le nombre de combattants et les enjeux sont actés par écrit avant le début de la scène.</p>",
      },
      {
        title: "Captures de Ninjas (Hors Batailles / Guerres)",
        body: "<p>Une capture hors guerre nécessite <b>3 captureurs</b> contre 1 cible isolée, ou <em>égalité numérique +1</em>. La cible peut être détenue <b>maximum 6h IRL</b>, après quoi un échange ou une libération RP doit avoir lieu.</p>",
      },
      {
        title: "Familles",
        body: "<p>Les <b>familles RP</b> sont validées par le staff lore. Elles donnent accès à des <em>ressources narratives</em> (manoir, jutsus, héritage) mais imposent des devoirs : présence, transmission, intrigue politique active.</p>",
      },
      {
        title: "Guerres",
        body: "<p>Une <b>guerre de clans</b> ou de villages se déclare via un <em>casus belli</em> documenté. Durée minimale : 2 semaines RP. Tout PK pendant guerre est <b>définitif</b> sauf clause contraire signée.</p>",
      },
      {
        title: "Haut-Gradé",
        body: "<p>L'accès aux grades <b>Jonin et au-delà</b> passe par une scène d'évaluation RP devant le Kage du village. Les hauts-gradés ont des <em>obligations de présence</em> en réunion (2/mois) et de mentorat (1 élève actif).</p>",
      },
      {
        title: "Masques",
        body: "<p>Le port d'un <b>masque ANBU ou clandestin</b> permet de cacher l'identité du personnage. Toutefois, des indices RP (cicatrices, voix, jutsu signature) peuvent être <em>légitimement reconnus</em> par les autres joueurs.</p>",
      },
      {
        title: "Naissance Clan",
        body: "<p>Un <b>nouveau clan</b> peut naître via 5 membres actifs, un dojutsu/kekkei genkai validé par le staff lore, et un patriarche/matriarche élu en RP. Délai minimum d'instruction : <em>3 semaines</em>.</p>",
      },
    ],
  },
  {
    id: "hrp",
    num: 2,
    name: "HRP",
    color: "#8b5cf6",
    glow: "rgba(139,92,246,0.4)",
    silhouette: (
      <path d="M50 18a14 14 0 1 1 0 28 14 14 0 0 1 0-28zm-22 70c0-12 10-20 22-20s22 8 22 20zM30 60a6 6 0 1 1 0-12 6 6 0 0 1 0 12zm40 0a6 6 0 1 1 0-12 6 6 0 0 1 0 12z" />
    ),
    sections: [
      {
        title: "Discord & vocaux",
        body: "<p>Le serveur Discord est une <b>extension du jeu</b>. Toute insulte, doxxing, ou comportement toxique est sanctionné de la même manière qu'en jeu. Les <em>vocaux RP</em> sont distincts des vocaux HRP — ne pas mélanger.</p>",
      },
      {
        title: "Méta-game & Power-game",
        body: "<p>Le <b>méta-game</b> (utiliser des informations HRP en RP) et le <b>power-game</b> (imposer une action sans laisser de chance) sont strictement interdits. Toute action doit pouvoir être <em>justifiée IC</em>.</p>",
      },
      {
        title: "Multi-comptes",
        body: "<p>Un seul compte par joueur, sauf <b>dérogation staff</b> pour personnage secondaire. L'utilisation d'un alt pour <em>contourner un PK</em> ou influencer une scène est sanctionnée par bannissement.</p>",
      },
    ],
  },
  {
    id: "lexique",
    num: 3,
    name: "Lexique RP",
    color: "#06b6d4",
    glow: "rgba(6,182,212,0.4)",
    silhouette: <path d="M30 20h40v8H30zm0 16h40v8H30zm0 16h40v8H30zm0 16h28v8H30z" />,
    sections: [
      {
        title: "IC / OOC / IG / HRP",
        body: "<p><b>IC</b> (In Character) = en jeu, dans la peau du personnage. <b>OOC</b> (Out Of Character) = hors-jeu. <b>IG</b> (In Game) = sur le serveur. <b>HRP</b> (Hors Roleplay) = synonyme français de OOC. Toujours préciser <em>// HRP :</em> avant une remarque OOC en jeu.</p>",
      },
      {
        title: "PK / CK / KS",
        body: "<p><b>PK</b> (Player Kill) = mort RP du personnage, qui se traduit par une suppression du perso. <b>CK</b> (Character Kill) = mort narrative validée par le staff. <b>KS</b> (Kill Steal) = voler la victoire d'un combat — interdit.</p>",
      },
      {
        title: "Kekkei Genkai & Dojutsu",
        body: "<p>Un <b>Kekkei Genkai</b> est une aptitude héréditaire propre à un clan. Les <em>dojutsu</em> (Sharingan, Byakugan…) sont des KKG oculaires soumis à validation et quota par village.</p>",
      },
    ],
  },
  {
    id: "rpk",
    num: 4,
    name: "RPK",
    color: "#4ade80",
    glow: "rgba(74,222,128,0.4)",
    silhouette: <path d="M50 14l-22 12v22c0 16 9 28 22 36 13-8 22-20 22-36V26z" />,
    sections: [
      {
        title: "Conditions du RPK",
        body: "<p>Un <b>RPK</b> (Roleplay Kill) doit suivre une scène <em>équilibrée</em>, avec témoins, et un motif RP fort (vengeance, mission, ordre supérieur). Le staff peut annuler un RPK jugé <b>abusif ou farmé</b>.</p>",
      },
      {
        title: "Conséquences",
        body: "<p>Suite à un RPK, le joueur perd son personnage. Il peut <em>recréer</em> un personnage après <b>72h</b>, sans lien direct avec l'ancien (pas de famille immédiate, pas de héritage automatique).</p>",
      },
      {
        title: "Refus de RPK",
        body: "<p>Un joueur peut <b>refuser un RPK</b> uniquement si les conditions ne sont pas réunies (déséquilibre, méta, contexte technique). Il doit alors <em>ouvrir un ticket</em> staff dans les 24h.</p>",
      },
    ],
  },
  {
    id: "diplo",
    num: 5,
    name: "Diplomatie",
    color: "#f59e0b",
    glow: "rgba(245,158,11,0.4)",
    silhouette: <path d="M50 18l30 18v28L50 82 20 64V36z" />,
    sections: [
      {
        title: "Traités & Alliances",
        body: "<p>Un <b>traité officiel</b> entre villages doit être signé en cérémonie RP, archivé sur le canal #archives-diplo, et respecté <em>sous peine de scandale narratif</em>. Toute trahison écrite est exploitable politiquement.</p>",
      },
      {
        title: "Ambassadeurs",
        body: "<p>Chaque village peut nommer <b>1 ambassadeur</b> par village allié. L'ambassadeur a accès au territoire allié sans escorte, mais reste <em>responsable de ses actes</em>.</p>",
      },
    ],
  },
  {
    id: "clans",
    num: 6,
    name: "Clans",
    color: "#f97316",
    glow: "rgba(249,115,22,0.4)",
    silhouette: <path d="M50 16l34 22v18c0 16-14 28-34 30-20-2-34-14-34-30V38z" />,
    sections: [
      {
        title: "Hiérarchie de clan",
        body: "<p>Tout clan possède un <b>chef</b>, un <b>conseil</b> de 3 anciens, et des <em>membres actifs</em>. La succession se fait par tournoi RP, désignation du chef, ou vote du conseil en cas de disparition.</p>",
      },
      {
        title: "Techniques de clan",
        body: "<p>Les <b>techniques héréditaires</b> ne se transmettent qu'aux membres confirmés. Un membre exilé perd l'accès à la transmission, mais conserve les jutsus déjà appris (sauf <em>sceau de répudiation</em> apposé par le chef).</p>",
      },
    ],
  },
];

// ──────────────────────────────────────────────────────────────────────────────
// LORE
// ──────────────────────────────────────────────────────────────────────────────

export const LORE_CATEGORIES: LoreCategory[] = [
  {
    id: "monde",
    num: 1,
    name: "Monde Shinobi",
    color: "#8b5cf6",
    glow: "rgba(139,92,246,0.4)",
    silhouette: <circle cx="50" cy="50" r="32" />,
  },
  {
    id: "konoha",
    num: 2,
    name: "Konoha",
    color: "#dc2626",
    glow: "rgba(220,38,38,0.4)",
    silhouette: <path d="M50 18l8 16 18 2-13 13 4 18-17-9-17 9 4-18-13-13 18-2z" />,
  },
  {
    id: "suna",
    num: 3,
    name: "Suna",
    color: "#f59e0b",
    glow: "rgba(245,158,11,0.4)",
    silhouette: <path d="M50 16l30 30-15 30H35L20 46z" />,
  },
  {
    id: "uchiha",
    num: 4,
    name: "Uchiha",
    color: "#dc2626",
    glow: "rgba(220,38,38,0.4)",
    silhouette: (
      <path d="M50 22c14 0 22 12 22 22-8 0-14-8-22-8s-14 8-22 8c0-10 8-22 22-22z" />
    ),
  },
  {
    id: "senju",
    num: 5,
    name: "Senju",
    color: "#16a34a",
    glow: "rgba(22,163,74,0.4)",
    silhouette: <path d="M50 18v64M30 30l40 40M70 30L30 70M22 50h56" />,
  },
  {
    id: "nara",
    num: 6,
    name: "Nara",
    color: "#475569",
    glow: "rgba(71,85,105,0.5)",
    silhouette: (
      <path d="M50 18c-12 0-20 12-20 24 0 14 8 24 20 36 12-12 20-22 20-36 0-12-8-24-20-24z" />
    ),
  },
  {
    id: "akimichi",
    num: 7,
    name: "Akimichi",
    color: "#ea580c",
    glow: "rgba(234,88,12,0.4)",
    silhouette: <circle cx="50" cy="50" r="28" />,
  },
  {
    id: "hyuga",
    num: 8,
    name: "Hyuga",
    color: "#94a3b8",
    glow: "rgba(148,163,184,0.4)",
    silhouette: (
      <path d="M50 22a18 12 0 1 1 0 24 18 12 0 0 1 0-24zm-12 18a4 4 0 1 1 8 0 4 4 0 0 1-8 0zm16 0a4 4 0 1 1 8 0 4 4 0 0 1-8 0z" />
    ),
  },
];

export const LORE_DETAIL_BY_ID: Record<string, LoreDetail> = {
  monde: {
    intro:
      "<b>Plusieurs décennies</b> se sont écoulées depuis la Quatrième Grande Guerre. Les villages cachés ont reconstruit leurs murs, leurs écoles et leurs hiérarchies — mais <em>l'équilibre reste fragile</em>. Une nouvelle génération grandit dans l'ombre des anciens héros, ignorante des véritables coûts de la paix.",
    sections: [
      {
        era: "Ère actuelle · An 64 du Calendrier Shinobi",
        title: "L'ère du silence",
        body: "<blockquote>Le monde shinobi se reconstruit, mais les blessures du passé saignent encore.</blockquote><p>Les <b>cinq grandes nations</b> coexistent dans une paix armée. Les traités signés au lendemain de la Grande Guerre tiennent — sur le papier. En coulisses, les services de renseignement n'ont jamais été aussi actifs, et les <em>cellules clandestines</em> recrutent parmi les jeunes générations désabusées.</p><p>L'Académie de Konoha forme à nouveau des cohortes complètes, et les <b>premiers Genin nés après la guerre</b> ont aujourd'hui l'âge des décisions.</p>",
      },
      {
        title: "Les cinq grandes nations",
        body: "<h3>Pays du Feu — Konohagakure</h3><p>Le <em>village de la Feuille</em>, autrefois fer de lance de l'alliance shinobi, traverse une période de <b>renouveau politique</b>. Le Hokage actuel cherche à moderniser les structures du village tout en préservant la <em>Volonté du Feu</em>.</p><h3>Pays du Vent — Sunagakure</h3><p>Suna souffre toujours d'une <b>économie fragile</b> liée au climat aride. Le Kazekage pousse une politique d'<em>ouverture commerciale</em> vers les autres nations, au prix de tensions internes avec les vieilles familles.</p><h3>Pays de l'Eau — Kirigakure</h3><p>Après des décennies sous la <b>Brume Sanglante</b>, Kiri a rejoint la communauté internationale. Mais des <em>factions extrémistes</em> persistent dans les îles du sud, refusant la réforme.</p>",
      },
      {
        title: "Les forces clandestines",
        body: "<p>L'<b>Akatsuki Réformée</b>, les <em>Racines</em> redevenues actives, et plusieurs groupuscules indépendants opèrent dans les zones grises entre nations. Le staff lore introduit régulièrement de nouvelles factions jouables.</p>",
      },
    ],
  },
  konoha: {
    intro:
      "<b>Konohagakure no Sato</b>, le village caché de la feuille. Bâti par <em>Hashirama Senju</em> et <em>Madara Uchiha</em>, devenu symbole de la coexistence shinobi, aujourd'hui à la croisée de son héritage et de sa modernité.",
    sections: [
      {
        era: "Fondation · An -82",
        title: "Les origines",
        body: "<p>Hashirama et Madara, ennemis jurés devenus alliés, posent la première pierre du village au cœur du <b>Pays du Feu</b>. La <em>Volonté du Feu</em> — l'idée que chaque génération protège la suivante — devient la doctrine fondatrice.</p>",
      },
      {
        era: "Ère contemporaine",
        title: "Le Hokage actuel",
        body: "<p>Le <b>Septième Hokage</b> a passé le flambeau il y a peu. Son successeur, plus jeune, fait face à des défis nouveaux : <em>réintégration des Uchiha exilés</em>, montée des cellules indépendantes, rivalité commerciale avec Kumo.</p>",
      },
      {
        title: "Quartiers & lieux emblématiques",
        body: "<p>Le <b>quartier Uchiha</b> a été partiellement reconstruit. L'<em>Académie</em> a doublé en taille. La <b>Tour du Hokage</b> domine toujours le mont des visages, où trône désormais un septième portrait.</p>",
      },
    ],
  },
  uchiha: {
    intro:
      "<b>Le clan du brasier intérieur.</b> Descendants d'Indra, porteurs du <em>Sharingan</em>, marqués par une histoire de gloire et de tragédie. Leur retour à Konoha reste un sujet de tension politique majeur.",
    sections: [
      {
        era: "Mythe fondateur",
        title: "Le sang d'Indra",
        body: "<p>Les Uchiha descendent du <b>fils aîné de l'Ermite</b>, héritier de l'œil et du chakra du combat. Cette lignée explique la <em>puissance oculaire</em> du clan, mais aussi sa <b>malédiction de la haine</b>.</p>",
      },
      {
        title: "Le Sharingan",
        body: "<p>Le <em>Sharingan</em> s'éveille dans la douleur émotionnelle. Trois tomoes maximum à la naissance, le <b>Mangekyō</b> requiert la perte d'un proche, et l'<em>Eternel Mangekyō</em> nécessite la fusion oculaire entre frères/sœurs de sang.</p><p><b>Restrictions Reborn :</b> 2 porteurs du Mangekyō max simultanés. Aucun Eternel Mangekyō sans validation staff lore.</p>",
      },
      {
        title: "Statut actuel à Konoha",
        body: "<p>Après l'amnistie post-guerre, les Uchiha sont <b>réintégrés progressivement</b>. Le quartier est reconstruit, mais la <em>méfiance institutionnelle</em> persiste. Aucun Uchiha n'a encore accédé au poste de Hokage depuis Sasuke.</p>",
      },
    ],
  },
  senju: {
    intro:
      "<b>Le clan de la forêt vivante.</b> Maîtres du <em>Mokuton</em>, descendants d'Asura, fondateurs historiques de Konoha aux côtés des Uchiha.",
    sections: [
      {
        era: "Mythe fondateur",
        title: "Le sang d'Asura",
        body: "<p>Les Senju descendent du <b>fils cadet de l'Ermite</b>, héritier du corps et du chakra de la vie. Leur affinité avec les <em>cinq éléments</em> fait d'eux les seuls capables de produire le Mokuton.</p>",
      },
      {
        title: "Le Mokuton aujourd'hui",
        body: "<p>Plus aucun Senju de naissance ne possède le Mokuton à l'état naturel. Les <b>porteurs actuels</b> sont des sujets d'expérimentations issues des cellules d'Hashirama, dont la stabilité reste <em>fragile</em>.</p>",
      },
    ],
  },
  suna: {
    intro:
      "<b>Sunagakure</b>, village du sable, joyau aride du Pays du Vent. Une nation de <em>survivants</em> aux ressources limitées et aux marionnettistes redoutables.",
    sections: [
      {
        era: "Histoire récente",
        title: "Après le Kazekage Gaara",
        body: "<p>L'ère de Gaara a transformé Suna en <b>nation respectée</b>. Son successeur poursuit l'œuvre, mais doit gérer la <em>raréfaction des ressources</em> et les tensions avec les tribus du désert profond.</p>",
      },
      {
        title: "L'art des marionnettes",
        body: "<p>L'<b>école de marionnettes</b> est unique à Suna. Cinq grands maîtres actifs, dont deux acceptent des élèves étrangers sous <em>parrainage diplomatique</em>.</p>",
      },
    ],
  },
  nara: {
    intro:
      "<b>Le clan des stratèges.</b> Maîtres du <em>Kage Mane</em>, gardiens d'une forêt sacrée et d'un savoir médical millénaire.",
    sections: [
      {
        title: "Manipulation des ombres",
        body: "<p>Le <b>Kage Mane no Jutsu</b> et ses dérivés (Kage Nui, Kage Kubishibari) constituent le cœur du clan. L'apprentissage débute à <em>9 ans</em>, l'éveil complet vers 18.</p>",
      },
      {
        title: "Forêt et cerfs",
        body: "<p>La <b>forêt Nara</b> est sanctuarisée. Les cerfs sacrés fournissent les bois utilisés en pharmacopée. Toute chasse non autorisée est punie d'<em>exil</em>.</p>",
      },
    ],
  },
  akimichi: {
    intro:
      "<b>Le clan de la table et du tonnerre.</b> Géants au cœur tendre, redoutés sur le champ de bataille, gardiens d'une cuisine rituelle.",
    sections: [
      {
        title: "Multiplication corporelle",
        body: "<p>Les techniques d'<em>expansion partielle</em> (Baika no Jutsu) puis totale culminent dans le <b>Chō Baika</b>, transformation en titan. Coût en calories massif — d'où l'importance des <em>pilules militaires</em>.</p>",
      },
    ],
  },
  hyuga: {
    intro:
      "<b>Le clan du regard pur.</b> Porteurs du <em>Byakugan</em>, héritiers d'une discipline martiale millénaire, le Jūken.",
    sections: [
      {
        title: "Byakugan",
        body: "<p>Le <em>Byakugan</em> offre une <b>vision à 359°</b>, perception du chakra et des points tenketsu. Réforme post-guerre : <b>abolition du sceau</b> de la branche secondaire, encore en cours d'application dans les vieilles familles.</p>",
      },
      {
        title: "Jūken — Le poing souple",
        body: "<p>L'art martial Hyuga vise à <em>fermer les tenketsu</em>, court-circuitant le réseau de chakra adverse. <b>361 points</b> au total — les maîtres en visent une quinzaine en combat réel.</p>",
      },
    ],
  },
};

export function findRulesCategory(slug: string): RulesCategory | undefined {
  return RULES_CATEGORIES.find((c) => c.id === slug);
}

export function findLoreCategory(slug: string): LoreCategory | undefined {
  return LORE_CATEGORIES.find((c) => c.id === slug);
}
