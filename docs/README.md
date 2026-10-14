# Documentation Reborn Roleplay

> **Point d'entrée de toute la documentation.** Restructurée le 2026-10-12 : une fiche par sujet, des noms
> explicites, et des documents de pilotage tenus à jour à chaque chantier.
>
> **Vous reprenez le projet (ou vous êtes Claude en début de session) ?** Commencez par
> [`REPRISE_DE_SESSION.md`](./REPRISE_DE_SESSION.md).

---

## Pilotage — où on en est, où on va

| Document | Répond à |
|---|---|
| [`GAMEPLAN.md`](./GAMEPLAN.md) | **Le Game Plan de Reborn** (modèle RedLine) : spécification, feuille de route en 4 phases (système, priorité, dépendance, état), une section par système avec la cible ; on avance point par point dans l'ordre RedLine |
| [`REPRISE_DE_SESSION.md`](./REPRISE_DE_SESSION.md) | Comment reprendre : quoi lire, règles de travail, outils, prompts |
| [`CHANTIERS.md`](./CHANTIERS.md) | **Où on en est**, chantier par chantier ; décisions en attente ; tests à faire |
| [`ETAT_DES_LIEUX.md`](./ETAT_DES_LIEUX.md) | Ce qui tourne **maintenant** : versions en ligne, serveurs, modpack, périmètre |
| [`ROADMAP_BETA_2026.md`](./ROADMAP_BETA_2026.md) | Ce qu'on livre d'ici le 31 décembre : 4 sprints, 16 chantiers, chiffres, cut list |
| [`pilotage/DECISIONS.md`](./pilotage/DECISIONS.md) | Toutes les décisions de l'utilisateur, datées, et ce qu'elles remplacent |
| [`pilotage/JOURNAL_DES_LIVRAISONS.md`](./pilotage/JOURNAL_DES_LIVRAISONS.md) | Chaque version publiée (mod, launcher, plugins) et son contenu |
| [`pilotage/BOARDS.md`](./pilotage/BOARDS.md) | Trello public et Miro : organisation, synchronisation, mise à jour en attente |
| [`adr/`](./adr/) | Les décisions techniques structurantes, une par fichier |

## Gameplay — les règles du jeu et leur implémentation

| Fiche | Système |
|---|---|
| [`gameplay/KO_ET_MORT_RP.md`](./gameplay/KO_ET_MORT_RP.md) | KO, ATA, soins, zones d'entraînement, menu des corps, mise à mort RP, exécutions, scène de mort |
| [`gameplay/APPRENTISSAGE.md`](./gameplay/APPRENTISSAGE.md) | Parchemins, bibliothèques à tirage, séances, concentration du chakra, slots du rang S |
| [`gameplay/STATS_ET_PROGRESSION.md`](./gameplay/STATS_ET_PROGRESSION.md) | 6 stats, XP invisible, grades, points de Mérite |
| [`gameplay/PERSONNAGES.md`](./gameplay/PERSONNAGES.md) | Sélection et création en scène, apparence |
| [`gameplay/FACTION_PROPOSITION.md`](./gameplay/FACTION_PROPOSITION.md) | **Proposition** de design Faction (sections, fonctions, quotas, trésor, coffres, diplomatie), 18 questions ouvertes |
| [`gameplay/RADIO.md`](./gameplay/RADIO.md) | Radio = **escouades** (décidé 14/10, non codé) : oreillette, ajout à côté, persistance, à terre, vol, écoute staff, faisabilité PlasmoVoice 2.x, 8 questions |
| [`gameplay/MISSIONS.md`](./gameplay/MISSIONS.md) | Idées de missions et d'entraînements, formats de rapports (pas encore de système en jeu) |
| [`gameplay/INVENTAIRE_ET_COSMETIQUES.md`](./gameplay/INVENTAIRE_ET_COSMETIQUES.md) | Sacoche RP, poids, cosmétiques |
| [`gameplay/NOURRITURE.md`](./gameplay/NOURRITURE.md) | Nourriture v3 (codée 14/10, non publiée) : satiété, régénération, FAIM, cuisinier joueur, repas, tendre, ration, pilule du soldat, modificateurs |
| [`gameplay/ENTREPRISES.md`](./gameplay/ENTREPRISES.md) | Entreprises (restaurant, forge, échoppe) créées par le staff : membres, rôles, postes ; futur onglet du menu Faction |
| [`gameplay/MONNAIE_ET_BANQUE.md`](./gameplay/MONNAIE_ET_BANQUE.md) | **Proposition** (14/10, maquettée) : bourse-objet, compte et guichet, échange garanti, coffre à paliers, sources / puits chiffrés, vol, staff, RBCoins ; 12 questions |
| [`gameplay/DEPLACEMENTS.md`](./gameplay/DEPLACEMENTS.md) | Allures marche / trot / sprint, endurance, emboîtement avec la surcharge et la course ninja |
| [`gameplay/EMOTES.md`](./gameplay/EMOTES.md) | Emotes RP (EmoteCraft) et leur distribution |
| [`gameplay/ANIMATIONS_BASE.md`](./gameplay/ANIMATIONS_BASE.md) | Catalogue d'animations à produire |

## Staff

| Document | Pour |
|---|---|
| [`staff/GUIDE_STAFF_EN_JEU.md`](./staff/GUIDE_STAFF_EN_JEU.md) | Poste de garde, grades staff, commandes par situation |
| [`staff/NEXO_STAFF_GUIDE.md`](./staff/NEXO_STAFF_GUIDE.md) | Ajouter un modèle 3D ou une texture animée depuis le panel |

## Technique

| Document | Contenu |
|---|---|
| [`technique/PLUGINS_REFERENCE.md`](./technique/PLUGINS_REFERENCE.md) | **Annuaire** : commandes, permissions, canaux réseau, config, fichiers de données |
| [`technique/MOD_HUD.md`](./technique/MOD_HUD.md) | Le mod client : packages, touches, taille des menus, **banc de captures**, pièges 26.2 |
| [`technique/MIGRATION_26.2.md`](./technique/MIGRATION_26.2.md) | Plateforme actuelle : versions, API 26.2, recette de build |
| [`technique/SPEC_STATS_SERVICE.md`](./technique/SPEC_STATS_SERVICE.md) | Spécification du `StatsService` |
| [`technique/ETATS_JOUEUR.md`](./technique/ETATS_JOUEUR.md) | **Conception (non codée)** des états du joueur : inventaire du code et des clans, couches, modificateurs, matrice, « contourne X », migration |
| [`technique/AUDIT_TECHNIQUES_ET_CREATOR.md`](./technique/AUDIT_TECHNIQUES_ET_CREATOR.md) | Système de techniques et Technique Creator |
| [`technique/CREATEUR_PERSONNAGE_ASSETS.md`](./technique/CREATEUR_PERSONNAGE_ASSETS.md) | Pipeline des skins (catalogue, masques de couleur) |
| [`technique/MCP_OUTILLAGE.md`](./technique/MCP_OUTILLAGE.md) | Faisabilité MCP : Blockbench, Discord, Blender |
| [`../CLAUDE.md`](../CLAUDE.md) | Architecture du launcher, de l'API et du bot (anglais) |
| [`../PLAN_CONCEPTION_LAUNCHER.md`](../PLAN_CONCEPTION_LAUNCHER.md) | Conception d'origine (le *pourquoi*) ; §11 et §18 historiques |

## Opérations

| Document | Pour |
|---|---|
| [`operations/PUBLICATION.md`](./operations/PUBLICATION.md) | **Publier le mod et déployer les plugins** (checklist, script, SFTP, prompt) |
| [`operations/RECETTE_GAMEPLAY.md`](./operations/RECETTE_GAMEPLAY.md) | **Tout tester de A à Z en jeu** (checklist numérotée, à jour de la 0.4.178) |
| [`operations/DISCORD_STAFF.md`](./operations/DISCORD_STAFF.md) | Discord staff et joueurs : invitation du bot, audit et lecture des salons (lecture seule), style visé, plan cible, bannières à produire |
| [`operations/MAINTENANCE.md`](./operations/MAINTENANCE.md) | Opérations courantes du VPS : mods du manifest, rôles, env, logs, backups |
| [`operations/RELEASING.md`](./operations/RELEASING.md) | Publier le launcher (auto-update Ed25519) |
| [`operations/AUTO_UPDATE_TEST_PROTOCOL.md`](./operations/AUTO_UPDATE_TEST_PROTOCOL.md) | Tester l'auto-update |
| [`operations/DEPLOY.md`](./operations/DEPLOY.md) | Infra VPS (Docker + Caddy), premier déploiement |
| [`operations/INSTALLATION_FROM_SCRATCH.md`](./operations/INSTALLATION_FROM_SCRATCH.md) | Monter une instance neuve (exemples génériques) |
| [`operations/MULTI_POSTES.md`](./operations/MULTI_POSTES.md), [`operations/BRANCHING.md`](./operations/BRANCHING.md) | Travailler depuis plusieurs machines, branches |

## Design

| Document | Contenu |
|---|---|
| [`design/DIRECTION_ARTISTIQUE.md`](./design/DIRECTION_ARTISTIQUE.md) | **Les règles de DA** et tout ce qui a été rejeté |
| [`design/MODELES_3D.md`](./design/MODELES_3D.md) | bbforge, modèles produits, règles de texture, plugins Blockbench |
| [`design/REBORN_ASEPRITE_PALETTE.md`](./design/REBORN_ASEPRITE_PALETTE.md), [`design/reborn-akatsuki.gpl`](./design/reborn-akatsuki.gpl) | Palette Akatsuki |
| [`design/refs/`](./design/refs/) | Images de référence |

## Études

| Document | Contenu |
|---|---|
| [`etudes/BENCHMARK_MODS_ET_SERVEURS.md`](./etudes/BENCHMARK_MODS_ET_SERVEURS.md) | **NydoMod 5.5, Ninshu Origins 2.1.8, Solve / FSC / Ketto / Zenkai comparés à Reborn** : ce qui est pratique en RP, ce qu'on écarte, ce qu'on récupère par lots (12/10) |
| [`etudes/REDLINE_REFERENCES.md`](./etudes/REDLINE_REFERENCES.md) | Cahier des charges RedLine (RP One Piece) lu en entier : 51 systèmes comparés à Reborn, reprises priorisées, éclairage des questions Faction |
| [`etudes/KO_MEDICAL_STATS_COMPARATIF.md`](./etudes/KO_MEDICAL_STATS_COMPARATIF.md) | KO, RP médical et stats (gameplay et visuel) chez Nydo et Ninshu, ce qu'on intègre et dans quel ordre |
| [`etudes/benchmark-2026-10/`](./etudes/benchmark-2026-10/) | Les annexes détaillées : combat, progression et monde, yeux, bijū, moteur client de Nydo ; Ninshu 2.1.8 ; serveurs RP ; inventaire Reborn |

## Archives

Études, audits, propositions de design d'origine, passations et migrations terminées :
[`archives/README.md`](./archives/README.md). **Ne décrivent plus l'état actuel.**

---

## Conventions de cette documentation

- **En français**, phrases courtes, tableaux pour tout ce qui se compare. Les termes de code restent tels quels.
- Chaque fiche commence par un bandeau : ce qu'elle couvre, **sa date de mise à jour**, où est le code.
- **Ce qui est codé** (fiches `gameplay/`, `technique/`) est séparé de **ce qui a été réfléchi** (`archives/`).
- Aucun secret (mot de passe, token, IP) : le dépôt est **public**.
- Les fiches se mettent à jour **à chaque fin de chantier** (cf. [`REPRISE_DE_SESSION.md`](./REPRISE_DE_SESSION.md) §7).
