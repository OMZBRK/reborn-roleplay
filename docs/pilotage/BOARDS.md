# Trello et Miro — les tableaux du staff

> **Ce document décrit comment les boards sont organisés, ce qu'on y met, et l'état de leur synchronisation avec
> le dépôt.** Les boards sont la vitrine de l'avancement pour le staff (et pour la communauté, côté Trello). Ils
> **suivent** les documents du dépôt : en cas d'écart, c'est le dépôt qui fait foi et les boards qu'on corrige.
> Mis à jour le 2026-10-16.

---

## 1. Les deux boards

| Board | Adresse | Public | Rôle |
|---|---|---|---|
| **Trello** *[WIP] FR - Naruto* | <https://trello.com/b/OqmICLMD/wip-fr-naruto> | **Oui** (communauté) | Plan de vol de la beta, chantiers, ce qui est livré |
| **Miro** *[WIP] FR - Shinobi Reborn* | <https://miro.com/app/board/uXjVGubPsdk=/> | Non (staff) | Roadmap visuelle, sprints, matrices (combat, progression), API ShinobiCore, état réel |

**Règle absolue pour le Trello public** : ni IP, ni mot de passe, ni chemin du VPS, ni lien d'artefact privé.
Parler de fonctionnalités, pas d'infrastructure.

## 2. Organisation du Trello

| Liste | Contenu |
|---|---|
| `📅 Plan de vol Beta 2026` | Une carte par sprint (Sprint 1 → 4), avec dates et checklist des livrables |
| `🎯 Beta Fin 2026` | Les **16 chantiers** de la roadmap, une carte chacun (axe, priorité, sprint) |
| `In Production` | Chantiers en cours de réalisation (ex. Technique Creator, Poste de garde) |
| `Done` | Ce qui est livré, une carte par livraison notable |
| `👑 Post-beta (v1.0.5 → v1.3)` | La cut list et la v1.1 |
| `Suggestions` | Idées de la communauté et du staff |

**Format d'une carte de chantier** : 1-2 phrases sur le but · « Fait » · « Reste » · sprint visé · lien vers la
fiche publique du dépôt si elle existe (le dépôt est public). Commentaire daté à chaque avancée notable.

Cartes connues : Sprint 1 `L72RSjdK` · KO / mort RP `gs3UC9fB` · Médical `x04oWuq0` · Graphisme HUD `QSfjPeqc` ·
Poste de garde `KffW79ed` · Technique Creator `xylVgQ58` · modrinth-sync `A1wrwmZ4` · sorts claniques `ScwJ0pMp`.
Ajoutées le 14/10 (In Production) : Game Plan, suivi point par point `HWy6kfc7` · États du joueur `nZb7nKaq` ·
Radio en escouades `kjFadbcE` · Monnaie `8FlE0akL`. Autres chantiers souvent commentés : Sprint 2 `UoFMizpN` ·
Combat `hzVHMsfU` · Déplacement `8AnAy11X` · Communication RP `arA0gQ51` · Faction `u9ifx7lu` ·
Bibliothèque `y6Ra6Xxr` · Assets `b6tB4PnR` · Modélisation 3D `aRUKM8FW` · v1.1 Boutique launcher `ilp5zCyN`.
Ajoutées le 16/10 (`Done`) : Boutiques en jeu `WUIQQQFz` · Météo `uzS7qoNx` · Marche sur l'eau `mPF1Jv9H` ·
Scène de chargement `ChXm1lsf` · Touches `7OWy5DlO` · Personnages au F7 `bYdqXoP3` · Factions `KtR21DYw` ·
Cloche et volières `Uc3C51RF` ; passées en `Done` : Radio `kjFadbcE`, Monnaie `8FlE0akL`. Ajoutées le 16/10
(In Production) : En attente de l'équipe `3tjRoBGa` · Portes et coffres de section `5pObxJ0O` · Mécaniques par
section `9Mhhyc9R` ; Game Plan `HWy6kfc7` réécrite, États du joueur `nZb7nKaq` « validé, à coder ».

## 3. Organisation du Miro

Frames : *Roadmap*, *Sprint Timeline*, *Matrice Combat* (39 techniques beta / 49 cible), *Progression Timeline*,
*ShinobiCore — API existante*, *📌 État réel* (au 17/09), *Livré depuis le 17/09*, *Livraisons 5-8 oct*,
*Bilan Sprint 1*. Palette Akatsuki (`#A0182B`, or `#D9A95E`, fond `#0A0608`).

Frames ajoutées le 12/10 : *Livré du 08 au 11/10*, *Sprint 1, bilan au 12/10*, *💀 Mort RP — le cycle*. Ajoutées le
14/10, à droite de la même rangée : *✅ Livré du 12 au 14/10* et *🗺️ Game Plan RedLine — point par point* (fait /
validé, code à venir / à venir). Les frames du 12/10 avaient pris le style clair « Bright Paper » ; celles du 14/10
reviennent à la palette Akatsuki, comme *État réel*. Ajoutée le 16/10 : *✅ Livré du 15 au 16/10* ; *Game Plan* et
*État réel* passés au 16/10. ⚠️ Encore
périmées : *Sprint Timeline* (dates des Sprints 2-4 non recalées), frame HUD v1 (bandeau « obsolète ») ; *Progression
Timeline* est marquée « remplacée ».

## 4. État de la synchronisation

| Date | Board | Ce qui a été fait |
|---|---|---|
| 09-08 | Trello + Miro | Grand ménage : 16 chantiers réécrits, chiffres alignés (39 techniques), cut list, frame *État réel* |
| 09-17 | Trello + Miro | Dates et checklists des 4 sprints ; carte Technique Creator ; « dette soldée » |
| 10-06 | Trello + Miro | 6 cartes `Done` (stats, carte + échoppe, DA des menus, Byakugan, Technique Creator, launchers) ; carte Poste de garde ; frame *Livré depuis le 17/09* |
| 10-08 | Trello + Miro | 7 cartes `Done` (mode photo + noms, KO-1→4 + KO-6, launcher 0.3.45, Poste de garde + Infirmerie, météo, chat, HUD combat) ; bilan daté sur la carte Sprint 1 ; frames livraisons et bilan |
| 10-12 | Trello | 5 cartes `Done` (grades, parchemins, persos en scène, progression XP, mort RP) ; descriptions réécrites d'après le code (Stats, Progression, Parchemins, KO, Graphisme HUD, Modélisation 3D, Animations) ; commentaires datés (Sprint 1 bilan, Sprint 2, Faction, Médical, Assets, Poste de garde) |
| 10-12 | Miro | Frames *Livré du 08 au 11/10*, *Sprint 1, bilan au 12/10*, *Mort RP — le cycle* ; *État réel* passé au 12/10 ; *Progression Timeline* marquée « remplacée » |
| 10-14 | Trello | 14 cartes `Done` (menus au niveau de la fiche, yeux + bibliothèques v2, Game Plan + études, Player ID, launcher 0.3.46, inventaire / ramassage, duel, journal + commandes staff + modes, allures, KO v2, Poste de garde v3, correctifs des états, nourriture v3 + entreprises, natures + Test de la Feuille) ; 4 cartes `In Production` (Game Plan suivi `HWy6kfc7`, États du joueur `nZb7nKaq`, Radio en escouades `kjFadbcE`, Monnaie `8FlE0akL`) ; commentaires datés 14/10 (KO, Poste de garde, Graphisme HUD, Sprint 1 bilan, Sprint 2, Déplacement, Communication RP, Faction, Combat, Médical, Bibliothèque, Assets) |
| 10-14 | Miro | Frames *Livré du 12 au 14/10* et *Game Plan RedLine — point par point* ; *État réel* passé au 14/10 (`reborn-hud` 0.4.178, launcher 0.3.46) |
| **10-16** | Trello | 8 cartes `Done` (boutiques en jeu `WUIQQQFz`, météo `uzS7qoNx`, marche sur l'eau `mPF1Jv9H`, scène de chargement `ChXm1lsf`, touches `7OWy5DlO`, personnages au F7 `bYdqXoP3`, factions `KtR21DYw`, cloche et volières `Uc3C51RF`) ; Radio `kjFadbcE` et Monnaie `8FlE0akL` passées en `Done` ; `In Production` : Game Plan `HWy6kfc7` réécrite, nouvelle « En attente de l'équipe » `3tjRoBGa`, États du joueur `nZb7nKaq` « validé, à coder », nouvelles « Portes et coffres de section » `5pObxJ0O` et « Mécaniques par section » `9Mhhyc9R` ; commentaires datés 16/10 (Communication RP, Faction, Déplacement, Poste de garde, Sprint 1, Modélisation 3D, Combat, v1.1 Boutique launcher) |
| **10-16** | Miro | Frame *Livré du 15 au 16/10* ; *Game Plan* et *État réel* passés au 16/10 (`reborn-hud` 0.4.184, launcher 0.3.46) |

## 5. Mise à jour du 12/10 — ✅ appliquée (gardée comme modèle de contenu)

### Trello (textes publics, sans infrastructure)

1. **`Done`** — nouvelles cartes :
   - « Grades shinobi » : 9 grades d'Académicien à Kage, passage décidé en RP, bonus de grade.
   - « Parchemins & bibliothèques » : rouleaux par rang, bibliothèques à tirage partagé, séances
     d'apprentissage, slots limités du rang S.
   - « Sélection et création de personnage en scène » : scène sombre, apparence façon Ketto, stature selon
     l'âge et la taille, braises de chakra par village.
   - « Mort RP » : demande au staff, validation, exécution par le demandeur, scène de mort (« Assister à votre
     réelle fin ? »), corps qui reste au sol.
   - « Progression à l'expérience » : niveaux invisibles, 1 point de stats par niveau ; points de Mérite des grades.
2. **Carte KO / mort RP** (`gs3UC9fB`) — commentaire : KO-5 (Volonté, désormais facultative) et KO-7 livrés sur le
   serveur de test ; restent les animations d'exécution et le coma.
3. **Carte Sprint 1** (`L72RSjdK`) — commentaire « Bilan au 12/10 » : designs Stats, Progression, Parchemins,
   KO faits ; restent **Faction**, icônes (3/8), assets wave 1 (Uchiha seulement), build en cours. Sprint
   prolongé : on le termine avant d'ouvrir le Sprint 2.
4. **Carte Faction** — passer en priorité du moment ; commentaire « design à faire avec références visuelles ».
5. **Carte Statistiques / Progression** — corriger : les grades ne montent plus à l'XP ; l'XP donne les points de
   stats.

### Miro

1. Frame *État réel* : passer à `reborn-hud` 0.4.169 / launcher 0.3.45 (ou la marquer obsolète et pointer vers la
   frame *Bilan Sprint 1*).
2. Frame *Bilan Sprint 1* : cocher Progression, Parchemins, KO ; ajouter « Sprint 1 prolongé ».
3. *Progression Timeline* : remplacer l'échelle XP → grades par « grades RP + XP → points de stats + Mérite ».
4. Nouvelle frame **« Mort RP »** : le schéma du cycle KO → RPK → exécution → scène de mort (cf.
   [`../gameplay/KO_ET_MORT_RP.md`](../gameplay/KO_ET_MORT_RP.md) §2 et §7).

## 6. Pièges des connecteurs

- Trello MCP : le paramètre `items` est ignoré à la création d'une checklist (ajouter les éléments un par un) ;
  `&amp;` est stocké littéralement ; déconnexions (`ENOTFOUND`). La liste `Done` (40+ cartes) dépasse la limite de
  sortie de `list_by_list` : préférer `trelloReadList` `get` (noms + IDs) puis lire les cartes une à une.
- Miro : `canvas_read_as_svg` renvoie le gras des `textArea` échappé (`&lt;b&gt;`) ; c'est la sérialisation, pas le
  contenu. On écrit `<b>` en clair dans le corps d'un `textArea`.
- Les connecteurs se réautorisent depuis les **réglages des connecteurs de claude.ai** (ou `/mcp` dans un terminal
  `claude`).
