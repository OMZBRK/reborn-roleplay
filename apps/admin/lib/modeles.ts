/**
 * Modèles de texte du staff (PANEL_V2 §4.3, §4.4, §4.7) : motifs de
 * candidature (« À revoir », « Refuser ») et réponses types de tickets
 * (« / » en début de réponse). Un modèle est un point de départ, toujours
 * modifiable avant envoi.
 *
 * Jeu commun livré avec le panel : l'API n'a pas encore de route pour les
 * enregistrer, donc ils ne se modifient pas depuis Réglages › Modèles (lot
 * suivant, capacité `reponses-types.gerer`). Ils sont identiques pour tout le
 * staff.
 */

export interface ModeleMotif {
  id: string;
  nom: string;
  texte: string;
}

export interface ReponseType {
  /** Raccourci tapé après « / ». */
  code: string;
  nom: string;
  texte: string;
}

export const MOTIFS_CANDIDATURE: readonly ModeleMotif[] = [
  {
    id: 'histoire-courte',
    nom: 'Histoire trop courte',
    texte:
      'Ton histoire manque de détails sur l’enfance et la formation de ton personnage. Développe son parcours, ce qui l’a construit et ce qu’il vise.',
  },
  {
    id: 'incoherence-lore',
    nom: 'Incohérence avec le lore',
    texte:
      'Un élément de ton histoire contredit le lore de Reborn : précise lequel tu veux garder et ajuste le reste pour qu’il reste cohérent avec l’univers.',
  },
  {
    id: 'trop-proche-canon',
    nom: 'Personnage trop proche d’un canon',
    texte:
      'Ton personnage ressemble trop à un personnage de l’œuvre. Donne-lui une histoire, un caractère et des objectifs qui lui sont propres.',
  },
  {
    id: 'voix-inaudible',
    nom: 'Voix inaudible',
    texte:
      'Ton enregistrement est trop faible ou coupé. Refais-le dans un endroit calme, en parlant près du micro.',
  },
  {
    id: 'hrp-incomplet',
    nom: 'Partie HRP incomplète',
    texte:
      'Certaines réponses de la partie HRP sont trop courtes pour qu’on se fasse une idée. Complète-les avec un peu plus de détails.',
  },
];

export const REPONSES_TYPES: readonly ReponseType[] = [
  {
    code: 'preuves',
    nom: 'Demander des preuves complémentaires',
    texte:
      'Merci pour ton signalement. Peux-tu nous envoyer une capture ou une vidéo de la scène, avec l’heure approximative ? Ça nous permettra de vérifier rapidement.',
  },
  {
    code: 'temoin',
    nom: 'Contacter un témoin de la scène',
    texte:
      'Nous allons contacter les joueurs présents sur la scène. Reviens vers nous si un autre élément te revient.',
  },
  {
    code: 'classe',
    nom: 'Sans suite, éléments insuffisants',
    texte:
      'Après vérification, les éléments ne permettent pas de sanctionner. Le ticket est classé, mais n’hésite pas à en rouvrir un si la situation se reproduit.',
  },
  {
    code: 'bug-recu',
    nom: 'Bug transmis à l’équipe technique',
    texte:
      'Merci, le bug est transmis à l’équipe technique. Tu seras prévenu ici dès qu’un correctif sera publié.',
  },
  {
    code: 'merci',
    nom: 'Remercier et clore',
    texte: 'Merci pour ton message, c’est réglé de notre côté. Bon jeu sur Reborn !',
  },
];
