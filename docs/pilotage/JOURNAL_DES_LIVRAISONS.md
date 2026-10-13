# Journal des livraisons

> **Ce qui a été publié, quand, avec quoi.** Reconstitué le 2026-10-12 à partir de l'historique Git et des
> sessions de travail. La version **actuellement** en ligne se lit dans [`../ETAT_DES_LIEUX.md`](../ETAT_DES_LIEUX.md) ;
> en cas de doute, le **manifest live** fait foi (les tags `mods-v*` ne sont pas fiables).
>
> À compléter à chaque publication (une ligne).

---

## `reborn-hud` (mod client)

| Version | Manifest | Date | Contenu | Commit(s) |
|---|---|---|---|---|
| 0.4.172 | 3.1.128 | 13/10 | Sélection/création de personnage au niveau de la fiche (décor nocturne par village, blasons de clan, voix off, musique), chirurgie des yeux complète (prélèvement, greffe, cécité, bandage), scène de mort, menu d'interaction, concentration (ensō), bibliothèque refaite. 0.4.171 jamais publiée | `6a19627`, `af4bca8` |
| 0.4.170 | 3.1.127 | 13/10 | Fiche shinobi « portrait en scène » (Identité/Attributs/Techniques/État), écrans de chirurgie des yeux (banc de test, pas branchés), scène de mort 10 s, corps portables/fouillables, dépôt au sol, parchemins servis par Nexo | `9e00326` |
| 0.4.169 | 3.1.126 | 11/10 | KO-5 Volonté facultative, KO-7 mort RP (RPK validée puis exécutée), menu d'interaction sur un corps, scène de mort, niveau retiré des affichages, fiche sans niveau | `3c18cdc`, `ca732a5` |
| 0.4.168 | 3.1.125 | 10/10 | Scène des persos : sons, braises de chakra par village, apparence à vignettes façon Ketto, emblème d'Ame, sans Déserteur | `341eac5`, `ead45b3` |
| 0.4.167 | 3.1.124 | 10/10 | Sélection et création de personnage en scène ; menus à la taille de l'échelle 2 (correctif de la 0.4.166) | `f2acc89`, `d095a64`, `5045870` |
| 0.4.166 | 3.1.123 | 09/10 | Parchemins lot 3 (séances, « S'entraîner ») ; `MenuScale` ; HUD KO qui évite le chat et les jauges | `3b182d8`, `2d7d1fa` |
| 0.4.165 | 3.1.122 | 08/10 | Parchemins lots 1-2 (objet par rang, bibliothèques à tirage) | `8e60674`, `184c11a` |
| 0.4.164 | 3.1.121 | 08/10 | Grades shinobi dans la tab list ; previews parchemins (inactives) | `4e2c5c5`, `278bb9c` |
| 0.4.163 | 3.1.120 | 08/10 | HUD combat (ensō d'endurance, dégâts, combo) | — |
| 0.4.162 | 3.1.119 | 08/10 | Chat en panneau DA (onglets, recherche, /me, icônes Lucide) ; sable et brume en volume 3D | `ceb1a6e`, `98b1334` |
| 0.4.161 | 3.1.118 | 08/10 | Refonte de la météo client (pluie, sable 3 degrés, brume) | `34adbf5` |
| 0.4.160 | 3.1.117 | 08/10 | Poste de garde unifié, Infirmerie en onglet | `ff94e06` |
| 0.4.159 | 3.1.116 | 07/10 | Infirmerie (gestion visuelle du KO, lits d'hôpital) | `1f8ad29` |
| 0.4.158 | 3.1.115 | 07/10 | Poste de garde v2 (bois, ferrures, plaquettes, sceau) | `db57d4e` |
| 0.4.157 | 3.1.114 | 06/10 | Poste de garde, panel staff en jeu (F7, `/garde`) | `4f42345` |
| 0.4.156 | 3.1.113 | 06/10 | Habillage KO / ATA (KO-6), météo client, merge du mode photo | `1db2d59`, merge `e9e3a10` |
| 0.4.153-0.4.155 | 3.1.110-112 | 05/10 | Mode photo façon viseur (filtres, cadres) ; 4 styles de noms au-dessus des têtes, nom sur une ligne | branche `feat/photo-mode-v2`, mergée le 06/10 |
| 0.4.152 | 3.1.109 | 04/10 | Byakugan « comme dans l'anime » (vision négatif, réseau de chakra) | `8cbd0d0` |
| 0.4.151 | 3.1.108 | 04/10 | Caméra, interaction, galerie, mode photo : DA + correctifs | — |
| 0.4.150 | 3.1.107 | 03/10 | Byakugan v1 | `4eb2e6d` |
| 0.4.148-0.4.149 | 3.1.105-106 | 03/10 | Sélection des persos « Horizon » ; correctif du gel au join (entités factices) | — |
| 0.4.144-0.4.147 | 3.1.100-103 | 02-03/10 | Échap « Tokonoma » ; sacoche « inrō » ; éditeur HUD, viseur, animations, création par village dans la DA | — |
| 0.4.139-0.4.143 | 3.1.95-99 | 01-02/10 | Carte du monde (M) + cartes en objets ; fiche de stats « lanternes célestes » v1-v2 ; échoppe ; sons | `2c0a61a`… `8aaa60a` |
| 0.4.136-0.4.138 | 3.1.92-94 | 28-29/09 | Fiche shinobi (K) du prototype `StatsService` ; infobulles sans chiffres ; jetons | `e3cfeb7`, `b21d109` |
| 0.4.135 | 3.1.91 | 09/2026 | Emotes, Technique Creator côté jeu | réunifié sur `main` le 17/09 |

## `reborn-ost`

| Version | Manifest | Date | Contenu |
|---|---|---|---|
| 0.2.4 | 3.1.104 | 03/10 | Menu OST refait sur la maquette (disque laqué, cordes de shamisen) |
| 0.2.3 | 3.1.103 | 03/10 | Couleurs DA |

## Launcher

| Version | Date | Contenu | Commit |
|---|---|---|---|
| 0.3.45 | 06/10 | Fenêtre de mise à jour qui revenait en boucle : « Plus tard » 24 h, « Ignorer cette version », erreur de vérification avec « Réessayer » | `7d1cddc`, `f7e7f2a` |
| 0.3.44 | 28/09 | Boîte de téléchargement à taille fixe ; sélecteur Build / Dev revenu (variables `REBORN_SERVER_DEV_*`) | `aee8fb6` |
| 0.3.43 | 28/09 | Galerie et feed corrigés ; keepalive du JWT (mods optionnels qui disparaissaient) ; version MC dynamique ; barre de téléchargement « Slosh » | `c104b41` |

## Plugins sur le serveur dev (déploiement SFTP)

| Date | Plugins | Contenu |
|---|---|---|
| 13/10 | ShinobiCore, ShinobiAbilities (inchangé), pack Nexo (fiole_oeil) | Yeux : œil G/D par personnage, fiole d'œil, `/yeux prelever\|greffer\|consentir\|etat\|definir\|donner`, `/bandage`, alerte Poste de garde — `af4bca8` |
| 13/10 | ShinobiCore, ShinobiAbilities, pack Nexo (parchemin_*) | Données de fiche + états de service, `/stats caractere\|citation\|edit\|service\|json`, scène de mort 10 s, corps du mort portable, dépôt au sol ; parchemins = rouleaux SNR dans `plugins/Nexo/pack` — `9e00326` |
| 11/10 | ShinobiCore | **Correctif `plugin.yml`** (un « : » non quoté empêchait le chargement) — `55297e3` |
| 11/10 | ShinobiCore, ShinobiAbilities | KO-5 facultatif, KO-7, scène de mort, corps, exécutions, XP / Mérite, lot 4 ; embarque la concentration du chakra |
| 10/10 | ShinobiCore | Âge, taille, sexe dans la liste des persos — `d453c5a` |
| 09/10 | ShinobiAbilities | Parchemins lot 3 (séances) |
| 08/10 | ShinobiCore, ShinobiAbilities, ShinobiLearning | Grades ; parchemins lots 1-2 ; API sacoche ; `/meteo` niveaux (`60b50ea`) |
| 07/10 | ShinobiCore | Infirmerie |
| 06/10 | ShinobiCore, ShinobiAbilities | KO-1 à KO-4 (`1a0b5b0`), KO-6 (`KoHudSync`), Poste de garde |
| 03-04/10 | ShinobiSense, pack Nexo, datapack `reborn_effets` | Byakugan, effets de sang |
| 28-29/09 | ShinobiCore, ShinobiAbilities, ShinobiCombat | Prototype `StatsService` |

> ⚠️ Ces déploiements visent le **serveur dev**. L'état des plugins sur le serveur **prod** n'est pas tracé
> précisément ; on sait seulement qu'au 10/10 le ShinobiCore de prod n'envoyait pas encore âge / taille / sexe.
> À vérifier avant toute mise en prod (cf. [`../ETAT_DES_LIEUX.md`](../ETAT_DES_LIEUX.md)).
