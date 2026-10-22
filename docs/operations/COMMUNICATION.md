# Communication et ouverture — plan proposé

> **PROPOSITION du 2026-10-19**, rien n'est appliqué : aucun compte créé, aucun post, aucun contact, Discord non
> modifié. À valider point par point (questions §7), puis chaque décision va dans
> [`../pilotage/DECISIONS.md`](../pilotage/DECISIONS.md).
> Sources internes : [`../CHANTIERS.md`](../CHANTIERS.md), [`../ROADMAP_BETA_2026.md`](../ROADMAP_BETA_2026.md),
> [`../GAMEPLAN.md`](../GAMEPLAN.md) (introduction, trois valeurs), [`DISCORD_STAFF.md`](./DISCORD_STAFF.md),
> [`../design/DIRECTION_ARTISTIQUE.md`](../design/DIRECTION_ARTISTIQUE.md),
> [`../etudes/benchmark-2026-10/serveurs_rp.md`](../etudes/benchmark-2026-10/serveurs_rp.md),
> [`../etudes/CONCURRENCE_SHINODA.md`](../etudes/CONCURRENCE_SHINODA.md), les dev updates postés
> (`Reborn - Gestion\Discord\posts\`) et les captures (`Reborn - Gestion\Maquettes\gameplan\`).
> Maquettes (hors dépôt) : `Reborn - Gestion\Maquettes\communication\` — ~~`01_discord_joueurs.png`~~ (v1,
> remplacée par `discord_joueurs_v3\`, voir [`DISCORD_JOUEURS.md`](./DISCORD_JOUEURS.md)), `02_calendrier.png`,
> `03_formats_courts.png`, générateur `_sources\gen_maquettes.py`.
> Sources web en §8 ; ce qui n'a pas pu être vérifié est marqué **à vérifier**.

## En dix lignes

1. **Message** : « Que des joueurs. » Un RP Naruto sur Minecraft où chaque shinobi est un vrai joueur, avec un launcher
   et une interface faits maison. On montre des **mécaniques RP qu'on voit**, pas des promesses.
2. **Public** : francophone d'abord (FR, BE, CH, QC) ; joueurs de Naruto RP (GMod, nanos world) et de RP Minecraft.
3. **Phases** : préparation (20/10) → teasing (2/11) → annonce + ouverture du Discord (sam. 14/11) → candidatures
   (sam. 28/11) → beta fermée par vagues (sam. 2/01) → ouverture (date fixée fin janvier).
4. **Canaux** : TikTok + YouTube Shorts (mêmes clips 9:16), X, Discord joueurs ; devlog YouTube ponctuel ; Twitch
   via des créateurs à la beta ; Reddit et Instagram en opportuniste.
5. **Cadence tenable** : 3 clips par semaine, 1 « Journal de Konoha » hebdo sur Discord, 1 devlog par phase.
6. **Bloquant n° 1** : on n'a que des clips de bancs de test (monde plat, 1 à 9 s). Il faut **une session de
   tournage dans le vrai monde** (Konoha, plusieurs comptes, OBS) avant le premier post public.
7. **Prérequis** : Discord joueurs construit ([`DISCORD_JOUEURS.md`](./DISCORD_JOUEURS.md), plan → `plan:guild` → `apply:guild`), page de téléchargement,
   réponse au blocage antivirus du launcher non signé, règlement, FAQ, politique de confidentialité.
8. **Droits** : aucun visuel ni son officiel Naruto en public (les bannières staff à renders officiels restent
   privées) ; mention « non officiel Minecraft » ; pas de musique du commerce.
9. **Organisation** : un Lead Communication, un monteur, un modérateur Discord ; l'utilisateur valide chaque
   visuel ; Claude prépare montages, textes, bannières et posts Discord ; l'utilisateur crée les comptes et publie.
10. **8 questions** à trancher (§7) : dates, nom public, Discord existant ou neuf, âge minimum, visuels Naruto,
    signature du launcher, créateurs, qui porte la com.

---

## 1. Positionnement, cible, ton

### 1.1 Ce qui distingue Reborn (du plus visible au plus profond)

| Différence | Ce qu'on montre | Preuve existante |
|---|---|---|
| **Que des joueurs** : pas de PNJ de quête ni de scénario écrit, le village est tenu par les joueurs | Factions et sections, cloche du village, missives par corbeau | GAMEPLAN Introduction ; FACTION, CLOCHE_ET_MISSIVES (publiés 16/10) |
| **Tout fait maison** : launcher, mod client, six plugins ; zéro écran Mojang du clic « Jouer » à Konoha | Launcher → scène de chargement « nuit de pleine lune » → monde | launcher 0.3.46, `reborn-hud` 0.4.191 |
| **Interface pixel art dessinée** dans une seule DA (nuit, laque, or), aucun menu vanilla | Avant / après vanilla ↔ Reborn | DA §4, tous les écrans refaits (13/10) |
| **Conséquence** : KO, douleur, portage, agonie, mort RP validée par le staff puis jouée | Scène à deux ou trois joueurs | KO_ET_MORT_RP, KO v2 (14/10) |
| **Moments d'anime jouables** | Test de la Feuille, marcher sur l'eau, méditation, Byakugan, météo | 0.4.182 → 0.4.190 |
| **Rien n'est gratuit** : parchemins uniques et volables, bourse qu'on peut fouiller, cuisinier joueur | Vol d'un parchemin, fouille de bourse | APPRENTISSAGE, MONNAIE_ET_BANQUE, NOURRITURE |
| **Futur network** : Reborn est le premier serveur d'un réseau | Pas en communication avant l'ouverture | Mémoire « Reborn = futur network », DISCORD_STAFF §4 bis |

Face aux concurrents (benchmark du 12/10 et Shinoda du 15/10) : les serveurs GMod (Solve, FSC, Ketto) et Zenkai
(nanos world) recrutent par **TikTok et les streamers** ; Shinoda est sur la même version de Minecraft avec un HUD et
un chat vanilla. Notre angle : **l'immersion de bout en bout** (launcher → interface → RP joueur) sur un jeu que
tout le monde possède déjà.

### 1.2 Message clé et accroches

- Signature : **« Que des joueurs. »** Variante longue : « Ton histoire, personne ne l'a écrite avant toi. »
- Trois piliers, repris des trois valeurs du Game Plan : **Liberté** (apprendre, trahir, déserter),
  **Conséquence** (ce que tu perds, tu le perds vraiment), **Immersion** (aucun écran ne te sort de Konoha).
- À éviter : comparer nommément aux concurrents, promettre le **combat** (mis de côté le 14/10, cf. CHANTIERS),
  montrer ce qui est en cut list (autres villages, rangs A/S, Susanoo…), annoncer une date non tenue.

### 1.3 Cible

| Segment | Où il est | Ce qui le fait venir |
|---|---|---|
| Joueurs de Naruto RP GMod / nanos world (FR) | TikTok (#narutorp, #gmodrp), Discord des serveurs, Twitch | La profondeur RP, la mort RP, le lore, l'accès sans acheter un autre jeu |
| Joueurs de RP Minecraft (FR) | TikTok, YouTube, annuaires de serveurs | Un RP « sérieux » avec une interface jamais vue sur Minecraft |
| Fans de Naruto curieux | TikTok, Shorts | Les moments d'anime (feuille, eau, méditation) |
| Créateurs de contenu RP | Twitch, TikTok | Des scènes fortes et une communauté encadrée |

Francophone d'abord ; pas d'anglais avant l'ouverture (le serveur, le règlement et l'interface sont en français).
Âge : voir Q4.

### 1.4 Ton

Le « tu », phrases courtes, sobre et immersif ; vocabulaire de l'univers (shinobi, village, chakra) mais **aucun
kanji ni écriture japonaise décorative** (règle DA du 14/10) ; pas de majuscules criées ni de « INCROYABLE » ; on
montre, on ne survend pas. Sous-titres français **incrustés** sur toutes les vidéos (la plupart sont regardées sans
le son).

---

## 2. Calendrier en phases

Vue d'ensemble : `Maquettes\communication\02_calendrier.png`. Dates indicatives (Q1). Jalons publics le **samedi à
18 h**. Règle : **une date n'est annoncée en public que quand le livrable est en recette** (J-14 au plus tôt) ; la
roadmap n'a pas été recalée et le combat est en pause, donc aucune date de beta publique avant décembre.

| Phase | Dates | Objectif | Contenus | Canaux et fréquence | Indicateurs | Prérequis |
|---|---|---|---|---|---|---|
| **0 · Préparation** | 20/10 → 1/11 | Tout prêt avant le premier post | Réserver les noms (§5.1), kit visuel, **session de tournage** dans Konoha, banque de 10 clips montés, règlement, FAQ, textes des en-têtes Discord | Interne (forum `📣・communication` du Discord staff) | 10 clips validés, comptes réservés | Konoha tournable ; 3-5 comptes staff pour les scènes ; Lead Communication désigné |
| **1 · Teasing** | 2/11 → 13/11 | Créer une attente, premiers abonnés | Clips « moments d'anime » sans tout dévoiler, logo en fin ; staff et créateurs proches relaient ; « Discord le 14/11 » en fin de vidéo | TikTok + Shorts 3/sem, X 3/sem | Vues moyennes par clip, taux de visionnage complet, abonnés | Comptes créés et habillés |
| **2 · Annonce + Discord** | sam. 14/11 → 27/11 | Ouvrir le Discord joueurs, expliquer le projet | **Trailer 30-45 s**, devlog YouTube n° 1, posts « qu'est-ce que Reborn », FAQ live, sondages | TikTok + Shorts 4-5/sem, X quotidien, Discord quotidien, devlog 1 | Membres à J+2 et J+14, % qui acceptent le règlement, rétention à J+7 | Discord joueurs construit et modéré ([`DISCORD_JOUEURS.md`](./DISCORD_JOUEURS.md)) ; trailer validé ; règlement et FAQ publiés |
| **3 · Candidatures + oraux** | sam. 28/11 → 1/01 | Recruter les premiers joueurs | « Comment candidater », coulisses du build, Journal de Konoha hebdo, annonces de sessions d'oraux | TikTok + Shorts 3/sem, X 4-5/sem, Discord quotidien | Candidatures déposées, % complètes, présence aux oraux, acceptés | **Page de téléchargement** ; réponse au blocage antivirus (Q6) ; politique de confidentialité ; staff d'oraux disponible ; whitelist dans le launcher (existe) |
| **4 · Beta fermée** | sam. 2/01 → ~12/02 | Faire vivre le serveur, produire du contenu joueur | Clips de vraies scènes (avec accord), créateurs en live, patch notes, « joueur de la semaine » | TikTok + Shorts 3/sem, X 4-5/sem, Twitch créateurs 4-5/sem | Joueurs simultanés, rétention à J+7, tickets, bugs | Playtest staff fait (18-24/12) ; **inventaire de la prod** (CHANTIERS §3) ; vagues de whitelist |
| **5 · Ouverture** | date fixée fin janvier | Ouvrir à tous les candidats acceptés | Trailer d'ouverture, live de lancement, créateurs simultanés | Tout au maximum pendant 1 semaine | Pic de joueurs, candidatures par semaine | Capacité serveur testée ; modération renforcée |

Pourquoi ce séquencement : la roadmap place « annonce + ouverture des inscriptions » au ship (25-31/12) ; en
ouvrant les candidatures dès le 28/11, la beta démarre avec des joueurs déjà passés à l'oral au lieu d'un serveur
vide. Ordres de grandeur pour se recaler (pas des objectifs fermes) : quelques centaines de membres Discord deux
semaines après l'ouverture, une centaine de candidatures avant la beta.

---

## 3. Par plateforme

| Plateforme | Rôle | Format | Cadence | Points d'attention |
|---|---|---|---|---|
| **TikTok** | Recrutement principal (canal n° 1 des serveurs RP du benchmark) | 9:16, 1080 × 1920, **15-45 s**, accroche visuelle dans la 1re seconde, texte en haut, sous-titres incrustés, logo en fin | 3/sem (4-5 autour des jalons) | Un compte **entreprise** n'accède qu'à la bibliothèque musicale commerciale (CML) : musique Reborn ou CML seulement. Lien Discord dans la bio, pas dans les légendes. Hashtags à tester : #narutorp #minecraftrp #rpfr #minecraft |
| **YouTube Shorts** | Mêmes clips, audience qui reste (recherche) | 9:16, ≤ 60 s conseillé (la limite des Shorts est 3 min depuis fin 2024 d'après des sources secondaires) | Comme TikTok, mêmes fichiers | Pas d'audio d'anime : Content ID |
| **YouTube long** | Crédibilité, profondeur | **Devlog 8-12 min** (chapitres, sous-titres .srt), trailer 30-45 s en 16:9 | 1 par phase | Aucune fenêtre d'IDE, de `.env`, de console ni d'IP à l'écran |
| **X** | Annonces, dialogue avec la communauté RP | Clip court ou GIF + une phrase ; fils pour les devlogs ; sondages | 3/sem → quotidien à l'annonce | Repostage des clips TikTok sans filigrane |
| **Reddit** | Vitrine technique, opportuniste | Post « regardez ce qu'on a fait » (ex. interface, marche sur l'eau), jamais « venez sur notre serveur » | Ponctuel, après lecture des règles du sub | r/Minecraft : promotion de serveur retirée, IP et liens d'invitation interdits, un nom de serveur est toléré dans un post qui montre une construction (règles 2022, **à vérifier**). r/admincraft : publicité de serveur interdite, un post d'autopromotion par mois (miroir 2020, **à vérifier**). r/Naruto, subs FR (r/jeuxvideo, r/france) : **à vérifier** |
| **Instagram** | Facultatif | Reels = clips TikTok | Repost seulement | Réserver le nom quand même |
| **Twitch** | Beta et ouverture, via des créateurs | Lives de créateurs invités ; éventuellement lives « on construit Konoha » (catégorie dev / build) | 0 avant la beta, puis par les créateurs | Accord des joueurs filmés (vocal de proximité) ; mention de partenariat si contrepartie (Q7) |

### 3.1 Les 20 premiers contenus

Matière déjà produite : `Maquettes\gameplan\` (Test de la Feuille v2/v3 : 7 films et 5 réactions, marche sur l'eau
v3 → v8, météo blizzard / sable, méditation v2 → v4, parade, chargement), `Discord\posts\img\` (30 captures et clips
des dev updates), bancs de capture du mod (`technique/MOD_HUD.md` §5). **Tout ce qui se passe dans le monde est à
retourner dans Konoha** : les bancs montrent un monde plat de test (voir `03_formats_courts.png`). Les écrans
d'interface (chargement, création, fiche) peuvent servir tels quels.

| # | Accroche (texte à l'écran) | Format | Matière | À faire |
|---|---|---|---|---|
| 1 | « Quelle est ta nature de chakra ? » — série de 5 (Katon, Suiton, Doton, Raiton, Fūton) | 5 × 20-30 s | `test_feuille_v3\film_*_skin_joueur.mp4` | Retourner dans un dojo de Konoha |
| 2 | « Oui, on marche sur l'eau. » | 10-15 s en boucle | `eau_v8\*.mp4` | Retourner au lac, deux joueurs |
| 3 | « Zéro écran Mojang : du clic au village. » | 20 s | launcher + `chargement\v2\chargement_scene_9s.gif` | Capture continue launcher → monde |
| 4 | « Minecraft de base vs Reborn » (sacoche, chat, Échap, tablist) | 30 s en écran partagé | captures DA + vanilla | Capturer le vanilla équivalent |
| 5 | « Crée ton shinobi en 30 secondes » | 30 s | création en scène, coupes, sourcils, bandeau teintable (0.4.190) | Capture écran |
| 6 | « Quand la cloche sonne, tout le village l'entend. » | 15 s | cloche (16/10), banc `REBORN_CLOCHE_DEBUG` | Scène avec plusieurs joueurs |
| 7 | « Ici, pas de MP. On envoie un corbeau. » | 20 s | volière et missives | À tourner |
| 8 | « Perdre un combat a des conséquences. » (douleur, portage, hôpital) | 30 s | KO v2 | À tourner à trois |
| 9 | « Une mort RP se décide. Puis elle se joue. » | 30 s, sobre | scène de mort (10 s) | À tourner, sans surenchère |
| 10 | « Ton chakra revient en méditant. » | 15 s, ambiance | `meditation\v4\meditation_pose_v4_8s.mp4` | Retourner de nuit |
| 11 | « Parade. » (ralenti) — aperçu, sans promesse sur le combat | 10 s | `parade\parade_05_*_ralenti_x4.mp4` | Retourner |
| 12 | « Le village est dirigé par des joueurs. » | 30 s | volet Faction (J), sections, grades | Capture + voix off texte |
| 13 | « Ton parchemin est unique. Et on peut te le voler. » | 25 s | bibliothèques v2 (vol) | À tourner |
| 14 | « Ton argent est dans ta bourse. Fais attention à qui tu croises. » | 20 s | bourse-objet, fouille | À tourner |
| 15 | « Les escouades se parlent à l'oreille. » | 20 s | radio en escouades | 3 comptes, voix enregistrées avec accord |
| 16 | « Entrer dans une tempête de sable. » | 10 s | `eau_meteo\meteo_entree_*.gif` | Retourner en zone désert |
| 17 | « Le restaurant de ramen est tenu par un joueur. » | 25 s | nourriture v3, entreprises | À tourner |
| 18 | « Chaque menu est dessiné au pixel. » | 20 s, défilé de menus | bancs de capture | Prêt (choisir les écrans) |
| 19 | **Devlog n° 1** : « On construit un RP Naruto sur Minecraft : launcher, mod, six plugins » | 8-12 min | captures, dev updates, maquettes | Script + voix (utilisateur ou staff) |
| 20 | « De la carte au village : Konoha en accéléré » | 30-45 s timelapse | build (World Machine → World Painter → Konoha) | Captures des builders (`build-updates`) |

**Trailer d'annonce** (14/11) : 30-45 s, plans des n° 1, 2, 3, 6, 8, 10, 20, fin « Discord le 14/11 » puis logo ;
versions 16:9 (YouTube, X) et 9:16 (TikTok, Shorts).

---

## 4. Discord joueurs — **voir la v3 : [`DISCORD_JOUEURS.md`](./DISCORD_JOUEURS.md)**

> 19/10 : l'utilisateur décide de **commencer la communication par le Discord joueurs** et rejette la v1 de cette
> section (« le format manque et ne ressemble pas aux autres serveurs existants »). La v2 du même jour (Accueil +
> grande catégorie « Reborn », Douane, Konoha, tickets à bouton) est à son tour remplacée par la **v3 « façon
> Ketto »** après ses décisions du soir ([`../pilotage/DECISIONS.md`](../pilotage/DECISIONS.md)). Maquettes v3 :
> `Maquettes\communication\discord_joueurs_v3\` ; v1 (`01_discord_joueurs.png`) et v2 (`discord_joueurs_v2\`)
> abandonnées.

Résumé de la v3 : **deux catégories publiques, 15 salons** — `🍥 | Reborn` (bienvenue, règlement, annonces,
informations, nous-rejoindre, réseaux, recrutement, spoils : bannière + cadre postés par le bot) et `💬 | Communauté`
(général, médias, clips, créations, questions, vocal, attente oral) ; Staff et Archives masquées. Pas de Douane, pas
de bouton ticket : candidature, oral et tickets **dans le launcher** et le panel. Un seul rôle joueur, `| Whitelisté`,
posé par le bot (à coder). Oral à la première whitelist et pour les clans ; recandidature après 1 jour (joueur) ou à
la session de clan suivante. Restent valables : Communauté Discord activée, 2FA, AutoMod, plan anti-raid, découverte
impossible avant 1 000 membres et 8 semaines.

---

## 5. Identité et kit

### 5.1 Noms de comptes (à réserver par l'utilisateur)

Un seul identifiant partout, sans point (X n'accepte pas les points) : **`rebornroleplay`** recommandé (le logo dit
« REBORN ROLEPLAY »), repli `reborn_rp` ou `rebornrp_fr`. Nom affiché : « Reborn Roleplay ». Disponibilité **à
vérifier** sur TikTok, YouTube (@handle), X, Instagram, Twitch, Reddit (u/ et éventuellement r/RebornRoleplay).
« Naruto » et « Minecraft » jamais dans le nom (Q2, Q5). Domaine : `reborn-rp.com` existe déjà (API, panel).

### 5.2 Visuels par plateforme (valeurs courantes, **à revérifier** au moment de l'envoi)

| Plateforme | Avatar | Bannière |
|---|---|---|
| Discord | icône 512 × 512 | bannière 960 × 540 (palier de boost 2), fond d'invitation 1920 × 1080 (palier 1) |
| YouTube | 800 × 800 | 2560 × 1440, zone sûre centrale 1546 × 423 ; miniatures 1280 × 720 |
| X | 400 × 400 | 1500 × 500 |
| TikTok, Instagram | 200 × 200 minimum, affiché en rond | — |
| Twitch | 256 × 256 | 1200 × 480 ; écran hors ligne 1920 × 1080 |

Avatar : le logo carré (à dériver du logo `textures/gui/title/logo.png`, lisible en rond à 40 px). Bannières : scène
de nuit DA (`Maquettes\discord\_sources\scenes.py`, sans personnage) ou capture de Konoha étalonnée.

### 5.3 Charte

Palette `menu/Colors.java` : nuit `#0A0608`, laque `#A0182B` (texte sur sombre `#C01E35`), or `#D9A95E`, crème
`#F5E9D0`. Polices du mod : Inter (textes, sous-titres), ArcadePix (titres pixel), Bebas Neue (titres de
bannière). Logo « REBORN ROLEPLAY » (version « BETA » jusqu'à l'ouverture). Aucun kanji décoratif.

### 5.4 Bonnes pratiques et risques

- **Rien d'identifiant à l'écran** : pas d'IP, pas de `.env`, pas de console ni d'IDE avec des secrets, pas de
  coordonnées de debug, pas de commandes staff. Le serveur s'atteint par le launcher ; ne jamais publier l'adresse de
  jeu. ⚠️ `operations/MAINTENANCE.md` §1 contient encore une IP en clair dans un dépôt public : à retirer
  (signalé, pas corrigé ici).
- **Droits Naruto** : aucune politique officielle de fan-content trouvée (Shueisha / Pierrot / Viz) ; un serveur
  de fans tolère l'univers, mais **les images, renders, musiques et sons officiels sont le risque le plus net**
  (retraits, Content ID, signalements). Les bannières du Discord staff utilisent des renders officiels « usage
  privé » (`_sources\persos\SOURCES.md`) : **ne pas les réutiliser en public**. « Naruto » seulement comme
  description (« RP Naruto », #narutorp). Pas de monétisation sur le nom.
- **Mojang / Minecraft** : mention « NON OFFICIEL. NON APPROUVÉ PAR MOJANG OU MICROSOFT » (formule anglaise dans les
  règles, à reprendre telle quelle sur le site, la bio YouTube et `réseaux`) ; « Minecraft » jamais comme nom
  principal ; boutique RBCoins = cosmétiques sans avantage, contenu tout public. Le logo imite le style de blocs de
  Minecraft : courant chez les serveurs, mais ne pas reprendre le logo Minecraft lui-même. Texte officiel **à
  relire** sur minecraft.net (non joignable pendant cette recherche).
- **Musique** : OST Reborn seulement si les droits de chaque piste sont clairs (**à vérifier** : provenance des
  pistes du manifest), sinon bibliothèques libres (YouTube Audio Library, CML TikTok). Jamais d'OST de l'anime.
- **Joueurs filmés** : le règlement doit dire que le serveur peut être filmé et diffusé, avec un droit de retrait ;
  les voix du vocal de proximité ne se publient qu'avec accord ; flouter les pseudos si besoin.
- **Données personnelles** : candidatures, comptes Microsoft et Discord → **politique de confidentialité** et mentions
  légales sur le site avant d'ouvrir les candidatures au public (RGPD, **à faire relire**).
- **Modération** : règlement Discord + règlement RP séparés ; sanctions tracées (`mod-log`) ; commentaires TikTok /
  YouTube filtrés par mots-clés ; on ne répond jamais à une polémique en public, on renvoie aux tickets.

---

## 6. Organisation

| Rôle | Qui (proposition) | Fait |
|---|---|---|
| **Lead Communication** (rôle existant du Discord staff : partenariats, langues, réseaux) | à désigner (Q8) | Calendrier, validation des textes, partenaires et créateurs, réponses publiques |
| **Monteur / graphiste** (pôle Création) | 1 personne | Montages, miniatures ; peut partir des montages préparés par Claude |
| **Capture** | 3-5 staff, 1 soir par semaine | Joue les scènes dans Konoha, enregistre en OBS 1080p60 (Iris si possible) |
| **Community manager / modération Discord** | 2 personnes, soirs de jalons | Accueil, AutoMod, tickets, sondages |
| **Validation finale** | l'utilisateur | Chaque visuel avant publication (règle 2) ; les dates ; les partenariats |

**Outils** : forum `📣・communication` du Discord staff (existe) pour le planning et les validations, une carte par
contenu (brouillon → monté → validé → publié). **Pas de planning de contenus sur le Trello public** (il dévoilerait
les teasers) : le Miro ou le forum staff à la place. Banque de médias : `Reborn - Gestion\Communication\` (hors
dépôt) avec `rushes\`, `montes\`, `publies\`.

| Claude peut produire | L'utilisateur (ou le staff) doit faire |
|---|---|
| Montages ffmpeg depuis les rushes et les bancs : recadrage 9:16, accroches et sous-titres incrustés, carte de fin avec logo, exports par plateforme, GIF pour Discord | Créer et sécuriser les comptes (2FA), accepter les CGU, choisir compte personnel ou entreprise |
| Bannières, avatars, miniatures dans la DA (générateurs Python existants) | Tourner les scènes en jeu à plusieurs comptes |
| Textes : accroches, légendes, fils X, script du devlog, règlement, FAQ, en-têtes de salons | Publier sur TikTok, YouTube, X, Reddit, Instagram ; répondre aux commentaires |
| Posts Discord en JSON pour le panel (`Discord\posts\*.json`, comme les dev updates) | Envoyer depuis le panel |
| Plan JSON du Discord joueurs, simulation `plan:guild`, code du bot (bienvenue, rôles) | Lancer `apply:guild` lot par lot, attribuer les rôles staff |
| Calendrier et suivi des indicateurs à partir des chiffres fournis | Contacter les créateurs et partenaires |

---

## 7. Questions à trancher (8)

| # | Question | Recommandation |
|---|---|---|
| Q1 | **Dates** : teasing 2/11, annonce + Discord sam. 14/11, candidatures sam. 28/11, beta sam. 2/01 ? | Valider la préparation et le teasing tout de suite ; n'annoncer publiquement que le 14/11 ; la date des candidatures à J-14, si la page de téléchargement et Q6 sont réglées |
| Q2 | **Nom public** : « Reborn Roleplay » (logo), « Shinobi Reborn » (tag staff) ou une marque réseau ? | Comptes au nom de **Reborn Roleplay** (`rebornroleplay`), la marque du futur network ; le serveur Naruto en est le premier contenu ; un 2e serveur passera sur les mêmes comptes |
| Q3 | **Discord joueurs** : réutiliser le serveur communautaire actuel (celui du bot, `DISCORD_GUILD_ID`) ou en créer un neuf ? | **Réutiliser** (**à confirmer**) : le bot, l'OAuth et les fils y sont déjà ; audit du 19/10 : c'est aujourd'hui le serveur perso de l'utilisateur (6 membres), tout l'ancien contenu irait dans une catégorie Archives masquée ([`DISCORD_JOUEURS.md`](./DISCORD_JOUEURS.md) §6) |
| Q4 | **Public et âge minimum** | Francophone seulement ; **15 ans minimum** pour candidater (âge de consentement numérique en France pour le RGPD), vérifié à l'oral ; contenu tout public (règle Mojang) |
| Q5 | **Visuels Naruto officiels** en public (bannières, miniatures) ? | **Non** : uniquement nos captures et nos scènes DA ; les bannières staff restent privées ; refaire les bannières joueurs sans renders |
| Q6 | **Launcher non signé** (Norton le bloque, CHANTIERS §4 n° 9) avant les candidatures ? | Prendre un certificat de signature de code avant le 28/11 si le budget le permet (offres et éligibilité **à vérifier**) ; sinon, au minimum : page d'aide SmartScreen et antivirus, empreinte SHA-256 publiée, lien de téléchargement officiel unique |
| Q7 | **Créateurs / streamers** : programme à la beta ? payé ? | Oui à la beta fermée, **sans paiement** : accès anticipé, rôle `\| Créateur`, mise en avant dans `réseaux` ; charte écrite (règles RP identiques, accord des joueurs filmés) ; mention de partenariat dès qu'il y a une contrepartie (loi française sur l'influence, **à vérifier** pour un simple accès anticipé) |
| Q8 | **Qui porte la com** et combien de temps par semaine ? | Désigner le Lead Communication et un monteur avant le 26/10 ; prévoir ~6 h/semaine (tournage 2 h, montage 3 h, publication 1 h) ; sans eux, réduire à 2 clips par semaine |

---

## 8. Sources web (consultées le 19/10/2026)

- Règles d'usage Minecraft (monétisation, nom, mention « non officiel ») : page officielle
  <https://www.minecraft.net/en-us/usage-guidelines> (non joignable, délai dépassé) ; résumé secondaire
  <https://www.gameserverkings.com/knowledge-base/minecraft/monetising-your-server/>, historique des modifications
  (2026) <https://conductatlas.com/platform/minecraft/minecraft-usage-guidelines/history/>. **À relire sur la source.**
- Découverte de serveurs Discord (1 000 membres, 8 semaines, 2FA, sécurité) :
  <https://support.discord.com/hc/en-us/articles/360030843331-Enabling-Server-Discovery>.
- Shorts jusqu'à 3 min, formats 9:16 1080 × 1920 (sources secondaires, pas de page officielle lue) :
  <https://blitzcutai.com/blog/youtube-shorts-length-2026>, <https://blitzcutai.com/blog/social-video-specs-2026>.
  Durée maximale TikTok : sources contradictoires, **à vérifier** dans l'application.
- Comptes entreprise TikTok limités à la bibliothèque musicale commerciale (source d'un vendeur de licences) :
  <https://www.soundstripe.com/blogs/why-can-i-only-use-commercial-sounds-on-tiktok>.
- r/Minecraft, refonte des règles de 2022 (promotion de serveur, IP, liens) — miroir :
  <https://reddit.birdcat.cafe/r/Minecraft/comments/xm2vsp/rules_rework_part_2>. r/admincraft, wiki — miroir :
  <https://lr.eu.psf.lt/r/admincraft/wiki>. Pages Reddit non lisibles directement : **à vérifier** dans la barre
  latérale de chaque sub avant de poster.
- Aucune politique officielle de fan-content Naruto trouvée.
- Benchmark des serveurs RP (TikTok et streamers comme canal de recrutement, page « Lives » de Ketto) :
  [`../etudes/benchmark-2026-10/serveurs_rp.md`](../etudes/benchmark-2026-10/serveurs_rp.md).

## 9. À faire dès la validation

1. L'utilisateur réserve les noms (§5.1) et répond aux questions §7.
2. Claude : plan JSON du Discord joueurs v3 écrit et simulé le 19/10 ([`DISCORD_JOUEURS.md`](./DISCORD_JOUEURS.md) §6),
   à appliquer après validation ; textes du règlement, de la FAQ et des en-têtes ; montage des clips n° 3, 5 et 18
   (matière déjà prête).
3. Staff : soirée de tournage dans Konoha (clips n° 1, 2, 6, 8, 10, 12).
4. Vérifications à faire par l'utilisateur, une fois les comptes créés : avatar lisible en rond sur mobile ;
   un clip test publié en privé sur TikTok, sous-titres hors des zones des boutons ; Discord joueurs parcouru avec un
   compte sans rôle (voit le public en lecture seule, barre « Terminer »), puis whitelisté, puis staff (comparer à `discord_joueurs_v3\01_barres_salons.png`).
