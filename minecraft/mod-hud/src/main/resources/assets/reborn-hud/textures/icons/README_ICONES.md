# Icônes de la Sacoche — cahier des charges

Dépose les PNG **dans ce dossier** en gardant **exactement** les noms ci-dessous
(ils écrasent mes placeholders). Fond **transparent** (RGBA). Une fois posés,
dis-moi « c'est fait » → je rebuild + publie.

## Format
- **32×32 px** (le rendu est calé sur 32 natif). PNG transparent (RGBA).
- Art centré, ~2 px de marge sur les bords, pixel net (pas de flou).
- Pour les icônes de **filtres** et d'**emplacements** : plutôt **clair/monochrome**
  (elles s'affichent sur fond sombre translucide). Les **actions** peuvent être colorées.

## PRIORITÉ 1 — Icônes (14 fichiers)

### Filtres de tri (en haut du panneau droit)
| Fichier | Rôle | Idée visuelle |
|---|---|---|
| `f_all.png`     | « Tout »    | grille de 4 carrés |
| `f_weapons.png` | « Armes »   | shuriken / kunai |
| `f_blocks.png`  | « Blocs »   | cube |
| `f_misc.png`    | « Divers »  | 3 points / petite caisse |

### Emplacements (icône-repère quand le slot est vide)
| Fichier | Emplacement |
|---|---|
| `slot_bandeau.png` | Bandeau frontal |
| `slot_masque.png`  | Masque |
| `slot_manteau.png` | Manteau |
| `slot_dos.png`     | Dos (sac à dos) |
| `slot_bag.png`     | Sac (emplacement + en-tête + écran « aucun sac ») |

### Actions (boutons du panneau clic droit)
| Fichier | Action |
|---|---|
| `act_leaf.png`  | Action contextuelle (ex. « Faire le test ») — feuille |
| `act_equip.png` | Équiper — coche / main |
| `act_drop.png`  | Déposer — flèche bas |
| `act_trash.png` | Supprimer — poubelle |

### Divers
| Fichier | Rôle |
|---|---|
| `ic_weight.png` | Barre de poids — haltère / balance |

## PRIORITÉ 2 (optionnel) — Skins de cadre

Si tu veux un rendu 100 % custom (au lieu de mes cases/panneaux dessinés) :

| Fichier | Dimensions | Rôle |
|---|---|---|
| `slot.png`          | 18×18 | fond d'une case d'inventaire (dessiné derrière chaque item) |
| `slot_hover.png`    | 18×18 | variante survol |
| `slot_selected.png` | 18×18 | variante sélection |
| `panel.png`         | 48×48 | fond des 2 panneaux, en **9-slice** (coins de 8 px) |

> Pour le 9-slice : coins 8×8 fixes, bords étirables, centre étirable. Si tu me
> donnes `panel.png` je code le nine-slice. Sinon je garde le panneau translucide actuel.

## Hors de ce dossier — modèles 3D (Nexo, séparé)

Les **modèles 3D** des sacs / cosmétiques (ce qu'on voit posé sur le perso ou en main)
ne sont PAS des icônes : ce sont des modèles Blockbench + textures pour **Nexo**
(`minecraft/server-config/nexo/pack/...`, cf. le README de ce dossier). Pipeline différent.
