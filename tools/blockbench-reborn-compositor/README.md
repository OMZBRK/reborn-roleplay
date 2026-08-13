# Reborn — Compositeur de perso (plugin Blockbench)

Plugin **Blockbench (bureau)** pour la fabrication de skins RP Reborn. Il assiste le
workflow réel des skinmakers : composer un personnage à partir d'une **bibliothèque
modulaire** de pièces 64×64 (peau, sous‑vêtement, tenue, yeux, cheveux) et garder une
**référence épinglée** dans la vue 3D pendant qu'on peint à la main.

> V1 — **composer / prévisualiser / exporter** la bibliothèque existante. La fabrication
> de nouvelles pièces (extraction depuis un skin, palettes, symétrie) viendra ensuite.

## Ce que ça fait

**Onglet Perso**
- Scanne le dossier bibliothèque et range les pièces par catégorie.
- Tu choisis **une pièce par catégorie** (la tenue accepte plusieurs pièces, ex. haut + bas).
- Empilement automatique dans le bon ordre : `peau → sous‑vêtement → tenue → yeux → cheveux`.
- Aperçu **Face / Dos** instantané dans le panneau.
- **Appliquer au 3D** : met à jour en direct la texture du modèle Blockbench → tu vois le
  perso composé en 3D immédiatement.
- **Exporter PNG** : sort le skin 64×64 combiné.

**Onglet Références**
- Grille des images de `ref/` et `skin_naruto_reference/` (avec filtre).
- Clic → **épingle** l'image dans la vue 3D via le système de référence natif de Blockbench
  (déplaçable, redimensionnable), façon carte *RESULT / REFERENCE*.
- Bouton pour retirer les références ajoutées.

## Structure de bibliothèque attendue

Le plugin cherche ces sous‑dossiers (insensible à la casse, récursif) sous le chemin
configuré :

| Catégorie      | Noms de dossier acceptés                         |
|----------------|--------------------------------------------------|
| Peau           | `peau`, `skin`, `peaux` (sous‑dossiers `female`/`male` OK) |
| Sous‑vêtement  | `sousvetement`, `sous-vetement`, `underwear`     |
| Tenue          | `tenue`, `tenues`, `outfit`, `clothes`           |
| Yeux           | `yeux`, `eyes`                                    |
| Cheveux        | `hair`, `cheveux`, `coiffure`                     |
| Références     | `ref`, `references`, `skin_naruto_reference`      |

Chaque pièce est un **PNG 64×64** ne peignant que sa zone (le reste transparent) — exactement
le format déjà utilisé dans `D:\REBORN - PJ\Modélisation\Skin`.

## Installation

1. Blockbench → **Fichier → Plugins → l'onglet « Chargés »** → bouton **« Charger un plugin
   depuis un fichier »** (icône dossier).
2. Sélectionne `reborn-compositor.js`.
3. Le panneau **Reborn Compositor** apparaît dans la barre de droite (icône vêtement).

> Bureau uniquement : le plugin lit tes PNG sur le disque, ce que la version web ne permet pas.

## Utilisation rapide

1. Ouvre (ou crée) un projet **format Skin** avec le modèle joueur et une texture.
2. Dans le panneau, vérifie le **chemin bibliothèque** (par défaut
   `D:/REBORN - PJ/Modélisation/Skin`) puis **Scanner**.
3. Clique une pièce par catégorie → l'aperçu Face/Dos se met à jour.
4. **Appliquer au 3D** pour voir le résultat sur le modèle, **Exporter PNG** pour sauver.

## Notes / limites connues (V1)

- La mise à jour de la texture 3D cible la **texture sélectionnée** du projet (sinon en crée
  une nommée `Reborn Composite`). Garde la bonne texture sélectionnée avant « Appliquer au 3D ».
- L'aperçu **Dos** est miroité horizontalement pour rester lisible ; la gauche/droite exacte
  dépend de tes pièces.
- Le journal en bas du panneau affiche les erreurs éventuelles — utile pour signaler un
  souci d'API selon ta version de Blockbench.
- Modèle **Classic (bras 4px)** géré ; le support Slim (3px) est prévu si besoin.

## Roadmap (post‑V1)

- Extraction d'une pièce depuis un skin complet vers la bibliothèque.
- Extraction de palette (avec teintes d'ombrage) depuis une image de référence.
- Helpers peinture : symétrie bras/jambes, copie face→dos, teinte de calque.
- Export direct des combinaisons vers le pipeline de création de perso in‑game.
