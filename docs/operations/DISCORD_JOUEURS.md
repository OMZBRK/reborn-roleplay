# Discord joueurs — v3 « façon Ketto »

> **PROPOSITION du 2026-10-19, rien n'est appliqué ni posté.** Le serveur visé est le **nouveau serveur joueurs créé
> par l'utilisateur le 19/10** (nom et ID pas encore communiqués, bot pas encore invité) — **plus** le serveur perso
> « OMZ - DISCORD PERSO » de `DISCORD_GUILD_ID`, auquel on ne touche plus. Le code de l'accueil et du salon piège est
> écrit (désactivé tant que ses variables sont vides) ; `plan:guild` / `apply:guild` attendent l'ID et la validation.
> Remplace la v2 du même jour, écartée par les décisions de l'utilisateur du 19/10
> ([`../pilotage/DECISIONS.md`](../pilotage/DECISIONS.md)) : « structure de Ketto, simple, efficace, pas besoin de
> 10 000 salons » ; « le bienvenue comme celui de Ketto, simple et efficace, ou comme celui de SAO qui est un network ».
>
> Sources : captures du 19/10 (hors dépôt) — Ketto salon `⛩` (un message par arrivée), `informations` (« Nos Réseaux »),
> `tuto-whitelist` (statut + procédure) ; « Sword Art Online - Minecraft » (network) salon `⚠️-dont-write-here` (piège à
> comptes piratés) ; <https://ketto-rp.fr/> ; [`../etudes/benchmark-2026-10/serveurs_rp.md`](../etudes/benchmark-2026-10/serveurs_rp.md) §3.
>
> **Maquettes** (hors dépôt, 1920 × 1080, pseudos fictifs) : `Reborn - Gestion\Maquettes\communication\discord_joueurs_v3\`
> — v3 du matin : `01_barres_salons.png` … `06_bannieres.png` ; **accueil façon Ketto** (`bienvenue_ketto\`) :
> `01_bienvenue_arrivees.png` (salon ⛩, 3 arrivées), `02_informations.png`, `03_nous_rejoindre.png`, `04_salon_piege.png`,
> `05_ketto_sao_vs_reborn.png` (**privée** : captures Ketto et SAO), `06_bannieres.png` ; bannières dans
> `bienvenue_ketto\bannieres\`, générateurs dans `_sources\` (`banners_ketto.py`, `gen_ketto.py`, `build_plan_joueurs.py`).
>
> Règle 7 : aucun ID de serveur, salon, rôle ou membre, aucun lien d'invitation ni secret ici. Le plan JSON vit hors
> dépôt (`Reborn - Gestion\Discord\plan-joueurs.json`), les messages à poster aussi (`Discord\posts\joueurs\`).

## En bref

1. **Structure de Ketto** : `🪵` **tout en haut, hors catégorie** (+ le salon piège optionnel), puis
   `🍥 | Reborn` (7 salons d'information en lecture seule, postés par le bot) et `💬 | Communauté` (5 salons + le vocal
   `⏳・Attente oral`). `🛡️ | Staff` et `🗄️ | Archives` sont masquées. 20 salons en tout.
2. **Accueil façon Ketto** : à chaque arrivée, le bot poste dans `🪵` la **mention** du nouveau membre et un
   **cadre** avec la **bannière BIENVENUE** et le pied « Reborn Roleplay • N membres ». Rien d'autre (§5.1).
3. **Option « network » à la SAO** : salon piège `⚠️・ne-pas-écrire` — tout message = **bannissement automatique de
   7 jours** (comptes piratés), codé, **désactivé par défaut** ; reco : l'activer à l'ouverture publique (§5.2).
4. **Pas de Douane, pas de salon tickets, pas de rôle Candidat** : candidature, réservation d'oral et tickets se font
   **dans le launcher**, le staff les traite dans le **panel web** (et les fils du bot, salon privé). Le `tickets` de
   Ketto est remplacé par `📜・règlement` (exigé par la Communauté Discord).
5. **Rôle de base : `Membre`** (tout arrivant). Le rôle `| Whitelisté` se verra **depuis le launcher** (mécanique à définir plus tard, décision du 24/10) ; les messages postés n'en parlent plus.
6. **Chaque salon d'information = bannière au-dessus + message en cadre** (format Ketto `informations` /
   `tuto-whitelist`). Nouvelle série de bannières « façon Ketto » : titre au pinceau blanc, trait laque, nuages pleins
   et fleurs de prunier sur les côtés, sans personnage ni kanji (`bienvenue_ketto\06_bannieres.png`).

## 1. Structure (maquettes `bienvenue_ketto\01` à `04`)

Style Ketto : catégories `emoji | Nom`, salons `emoji・nom` en minuscules (le `・` rend comme le `·` de Ketto ; un
salon texte Discord ne peut pas contenir d'espace). Ketto nomme son salon d'accueil `⛩` tout court ; on garde
`🪵`, plus lisible pour un nouveau venu (et retrouvable par nom par les scripts).

| Catégorie | Salon | Type | Écrit | Contenu | Équivalent |
|---|---|---|---|---|---|
| *(hors catégorie, en tête)* | `🪵` | texte | bot | Un message par arrivée (§5.1) | Ketto `⛩` |
| | `⚠️・ne-pas-écrire` | texte | **tout le monde** (piège) | Salon piège, **optionnel** (§5.2) | SAO `⚠️-dont-write-here` |
| **🍥 \| Reborn** | `📣・annonces` | annonces | Direction, Lead Communication, bot | Annonces, ouvertures, mises à jour, **sessions de candidatures de clan** | `annonce` |
| | `ℹ️・informations` | texte | bot | « Nos réseaux » (§4) | `informations` |
| | `📜・règlement` | règles | bot | Règlement Discord (§4), accepté par « Terminer » | (`tickets` chez Ketto) |
| | `🌐・réseaux` | texte | bot | Créateurs de contenu et partenaires (plus tard) | `réseaux` |
| | `🚪・nous-rejoindre` | texte | bot | Statut whitelist + procédure (§4) | `tuto-whitelist` |
| | `🧭・recrutement` | texte | bot | Postes staff ouverts, candidature dans le launcher | `recrutement` |
| | `👀・spoils` | texte | Direction | Avant-goûts, feuille de route | `spoil` |
| **💬 \| Communauté** | `💬・général` · `🎨・créations` | texte | membres | Discussion, fan-arts | `général`, `créations` |
| | `📷・médias` · `🎬・clips` | texte | **Membre** (+ whitelistés, staff) — 24/10 | Captures et clips en jeu | `médias`, `clips` |
| | `❔・questions` | forum | membres | Entraide | `questions` |
| | `⏳・Attente oral` | vocal | tous (muets) | Le candidat attend à l'heure de son oral | `attente-m…` |
| **🛡️ \| Staff** (masquée, rôle `Staff`) | `📥・candidatures` | texte | bot, staff | Fils du bot (candidatures, tickets) | — |
| | `🧾・journal-bot` | texte | bot | Journal : arrivées massives, piège | — |
| | `🛡️・modération` | texte | Discord | Mises à jour de la Communauté Discord | — |
| | `🎙️・Oral 1` · `🎙️・Oral 2` | vocal | staff | Un recruteur y déplace le candidat | — |
| **🗄️ \| Archives** (masquée, Direction) | *(vide au départ)* | — | — | Ce qu'on retire plus tard, sans le supprimer | — |

Pas de vocal libre au départ (Ketto n'en a pas) : à ajouter si la communauté le demande.

## 2. Rôles et permissions

Du plus haut au plus bas ; « séparé » = groupé dans la liste des membres, comme chez Ketto.

| Rôle | Couleur | Séparé | Permissions serveur |
|---|---|---|---|
| `🔒 \| Bot(s)` | — | oui | Rôle du bot, réglé à la main (tout en haut, **garde Administrateur** — décision du 22/10) |
| `\| Fondateur` | or `#D9A95E` | oui | **Administrateur** (le seul) |
| `\| Direction` | rouge `#F06A78` | oui | Gérer salons, rôles, pseudos, événements ; logs ; expulser, bannir, exclure ; messages, fils, épingles ; vocal ; @everyone ; sondages |
| `\| Head of Reborn` | vermillon `#F28A5C` | oui | Gérer rôles et pseudos ; événements ; logs ; expulser, exclure ; messages, fils, épingles ; déplacer, muet |
| `\| Lead Staff` · `\| Lead Roleplay` · `\| Lead Communication` | ambre `#EBA352` | oui | Événements, logs, exclure, messages, fils, épingles, déplacer, muet (+ rôles et pseudos pour Lead Staff ; sondages pour Lead Communication) |
| `\| Modération` | bleu `#8FB8E8` | oui | Pseudos, logs, expulser, exclure, messages, fils, déplacer, muet |
| `\| Recrutement` | rose `#E8A0C0` | oui | Déplacer, muet — examinateurs des oraux (le « Resp. Douane » de Ketto) |
| `\| Maître du jeu` · `\| Développement` | terre `#D9A27E` · jade `#8FC7AE` | oui | aucune |
| `Staff` | — | non | aucune — socle porté par tout le staff, ouvre `🛡️ \| Staff` |
| `Server Booster` | natif | oui | géré par Discord (apparaît au premier boost) |
| `\| Whitelisté` | violet `#8B5CF6` | non | aucune — écrire dans `médias` et `clips` ; se verra depuis le launcher (à définir) |

Mêmes noms et couleurs que le Discord staff ([`DISCORD_STAFF.md`](./DISCORD_STAFF.md) §4 bis), en plus court.

| | Visiteur (règlement non accepté) | Membre (après « Terminer ») | Whitelisté | Staff |
|---|---|---|---|---|
| `🍥 \| Reborn` | lire, réagir | = | = | Direction et Lead Communication écrivent |
| `🪵` | lire | = | = | = (le bot écrit) |
| `⚠️・ne-pas-écrire` (option) | lire, **écrire = ban 7 j** | = | = | exempté |
| `général`, `créations`, `questions` | lire | écrire | = | + modérer |
| `médias`, `clips` | lire | **écrire, images** (24/10) | = | écrire |
| `⏳・Attente oral` | rejoindre, muet | = | = | parler, déplacer |
| `🛡️ \| Staff` · `🗄️ \| Archives` | — | — | — | voir (Archives : Direction) |

`@everyone` sans « Créer une invitation » (lien officiel unique), sans mentions de masse ni fils privés. L'écriture
est bloquée tant que le règlement n'est pas accepté par l'**écran de règles de la Communauté** (à activer sur le
nouveau serveur). 2FA exigée pour la modération, AutoMod (invitations, mentions de masse), invitations en pause en cas de raid.

**À confirmer** (recommandations gardées de la v2, sans réponse) : **préfixes** de pseudo staff (`DIR ·`, `HEAD ·`,
`LEAD ·`, `MOD ·`, `REC ·`, `MJ ·`, `DEV ·`, comme `AD ·` / `DEV ·` / `RD ·` chez Ketto) et **tag de serveur `RBN`**
(comme `KETO`) — reco : oui aux deux.

## 3. Parcours d'entrée et règles de candidature

1. **Rejoindre** le Discord (lien officiel unique) → écran de règles → **Terminer**.
2. **`🚪・nous-rejoindre`** : site → **télécharger le launcher** → compte Microsoft → **lier Discord** (OAuth existante).
3. **Candidature dans le launcher** (onglet Whitelist, existe) → fil staff dans `📥・candidatures` et page Whitelist du
   panel (existent).
4. **Oral** (première whitelist seulement) : créneau réservé dans le launcher (`oral-slots`, existe) ; à l'heure, le
   candidat rejoint `⏳・Attente oral`, un `| Recrutement` le déplace en `🎙️・Oral 1`.
5. **Acceptation** (bouton du fil, `/whitelist accept` ou panel, existent) → rôle Reborn `WHITELISTED` (existe) → le bot
   pose **`| Whitelisté`** (à coder) + MP (existe) → « Jouer » s'active.
   **Remplacé le 24/10** : rôle whitelist visible depuis le launcher, à définir ; voir [`CANDIDATURE_ET_DOUANE.md`](../gameplay/CANDIDATURE_ET_DOUANE.md).

| Règle | Valeur | État du code |
|---|---|---|
| Oral | **première whitelist** et **chaque candidature de clan** ; pas d'oral pour une nouvelle candidature joueur ensuite | partiel : l'HRP validé à l'oral reste verrouillé au sein d'une candidature, mais la suppression (retrait) efface cette trace |
| Délai après refus — joueur | **1 jour** | à coder |
| Délai après refus — clan | **prochaine session de candidatures de clan** | à coder (les candidatures de clan n'existent pas) |
| Sessions de clan | ouvertes et fermées par le staff, annoncées dans `📣・annonces` | à coder |

## 4. Messages fixes (bannière au-dessus + cadre, postés par le bot)

Un message par salon, **remplacé** à chaque mise à jour (`post:message --refaire`), jamais en double. **Postés le 24/10** :
`ne-pas-écrire`, `règlement`, `informations`, `nous-rejoindre` (version **fermée**), `recrutement`, `réseaux`, `spoils`. Fichiers dans `Reborn - Gestion\Discord\posts\joueurs\` (format de `post-message.ts`, nouvelle option
`"banniere"` = image posée **au-dessus** du cadre, hors barre de couleur, comme chez Ketto ; images dans `img\`). Les
liens réels remplacent les `A-REMPLIR` au moment de poster, jamais dans le dépôt. Vérifiés hors ligne par `validerCadre`.

| Fichier | Salon | Contenu |
|---|---|---|
| `informations.json` | `ℹ️・informations` | Bannière INFORMATIONS · `## Reborn Roleplay` + « Que des joueurs. » · trois piliers (Liberté, Conséquence, Immersion) · accès (whitelist + launcher) · « Nos réseaux : bientôt » · mention « non officiel ». Le champ `salon` porte le nom exact `ℹ️・informations` (ℹ compte comme une lettre pour `normaliser`). Les liens réseaux remplaceront « bientôt » (`--refaire`) |
| `recrutement.json` · `reseaux.json` · `spoils.json` | `🧭・recrutement` · `🌐・réseaux` · `👀・spoils` | RECRUTEMENT STAFF : FERMÉ 🔴 + pôles + attentes · créateurs et partenaires (programme à l'ouverture) · présentation des spoils |
| `nous-rejoindre.json` / `nous-rejoindre-fermee.json` | `🚪・nous-rejoindre` | Bannière WHITELIST · `## » STATUT WHITELIST : OUVERTE 🟢` (ou FERMÉE 🔴, réouverture annoncée dans annonces) · `### 📜 Procédure à suivre` : 1️⃣ télécharge le launcher (bouton **Télécharger**) · 2️⃣ connecte-toi Microsoft + lie Discord · 3️⃣ candidature (onglet Whitelist) · 4️⃣ oral (première whitelist : créneau puis `⏳・Attente oral`) · 5️⃣ à toi de jouer (Jouer s'active ; rôle et délai de re-candidature retirés le 24/10, pas encore codés) · ligne Clans. Même clé : `--refaire` passe de l'un à l'autre |
| `reglement.json` | `📜・règlement` | Bannière RÈGLEMENT · 7 règles (respect, tout public, pas de pub, RP en jeu, spoils, un compte, le staff tranche par ticket launcher) · sanctions. Les 7 règles vont aussi dans l'écran de règles de la Communauté |
| `piege.json` | `⚠️・ne-pas-écrire` | Cadre **rouge** bilingue FR / EN (façon SAO) : piège de sécurité, ban automatique de 7 jours, changer de mot de passe + 2FA |

```pwsh
# Simulation (rien n'est posté), puis envoi — une fois le bot sur le serveur et l'ID connu :
pnpm --filter @reborn/bot post:message --guild <ID> --fichier "…\Discord\posts\joueurs\informations.json"
pnpm --filter @reborn/bot post:message --guild <ID> --fichier "…\Discord\posts\joueurs\informations.json" --envoyer
```

`📣・annonces` : pas de message fixe, chaque annonce en cadre. `🪵` : aucun message fixe, seulement les messages
d'arrivée. Communauté : pas de message fixe.

## 5. Le bot : accueil, piège, existant

### 5.1 Accueil par arrivée (`apps/bot/src/joueurs/bienvenue.ts`) — codé

À chaque `GuildMemberAdd` sur `DISCORD_JOUEURS_GUILD_ID` (bots ignorés, Discord staff jamais concerné), message en
composants v2 dans `DISCORD_BIENVENUE_CHANNEL_ID` : ligne `@pseudo` au-dessus, puis un cadre laque contenant la
bannière `apps/bot/assets/bienvenue.png` et le pied `-# <émoji> **Reborn Roleplay** • 1 234 membres`. **Seul le nouveau
membre est notifié** (jamais @everyone ni rôle). Options : `DISCORD_BIENVENUE_EMOJI` (le logo en émoji du serveur,
`<:reborn:ID>`, comme le logo Ketto du pied), `DISCORD_BIENVENUE_COULEUR` (barre du cadre, défaut #6A2177 = violet de l avatar du bot ; plus de ligne « Pour commencer », 22/10).

**Anti-flood** (fenêtre de 60 s) : jusqu'à `DISCORD_BIENVENUE_SEUIL_GROUPE` arrivées (8) → un message chacune ; au-delà
→ **un message par minute** qui cite les arrivants (20 au plus, « et N autres »), **sans ping** ; au-delà de
`DISCORD_BIENVENUE_SEUIL_RAID` (25) → **accueil suspendu 10 min** + alerte au journal (« mettre les invitations en
pause »). Envois en file (un à la fois). Journal : console + `DISCORD_JOUEURS_LOG_CHANNEL_ID` (`🧾・journal-bot`).
Désactiver les messages d'arrivée natifs de Discord (Paramètres → Vue d'ensemble → salon système) pour éviter le doublon.

### 5.2 Salon piège « network » (`apps/bot/src/joueurs/piege.ts`) — codé, désactivé par défaut

Un compte piraté poste ses liens d'arnaque dans **tous** les salons où il peut écrire ; un salon où personne ne doit
écrire le démasque. Tout message dans `DISCORD_PIEGE_CHANNEL_ID` → MP bilingue à l'auteur (au mieux) → **bannissement de
`DISCORD_PIEGE_JOURS` jours** (7 ; 0 = définitif) qui **efface ses messages des `DISCORD_PIEGE_PURGE_SECONDES` dernières
secondes partout** (3600 = 1 h : nettoie le spam) → ligne au journal. **Débannissement automatique** : la date de fin est
écrite dans le motif du bannissement (`[piège Reborn] fin=<date>`), relue au démarrage puis toutes les 30 min — aucun
état local, un redémarrage ne perd rien. **Jamais sanctionnés** : bots, webhooks, propriétaire, rôle
`DISCORD_JOUEURS_STAFF_ROLE_ID`, toute personne ayant une permission de modération ; un membre au-dessus du bot est
seulement journalisé. `DISCORD_PIEGE_SIMULATION=1` : message effacé + journal, **personne n'est banni**.

**Reco : oui, à l'ouverture publique** (Reborn = futur network, les comptes piratés qui spamment de faux giveaways sont
la nuisance n° 1 des Discords publics ; coût nul, MP explicatif, levée automatique). Activer d'abord **une semaine en
simulation** pour vérifier que personne d'honnête n'y écrit (nom, sujet et message sont explicites), puis en vrai. Pas
utile tant que le serveur est fermé ou presque vide.

### 5.3 Existant / à coder

| Fonction | État |
|---|---|
| Candidature launcher → API → fil staff + boutons, `/whitelist`, MP | ✅ existe (au basculement : `DISCORD_GUILD_ID` + `DISCORD_TICKETS_CHANNEL_ID` → nouveau serveur, `📥・candidatures`, puis `register`) |
| Tickets launcher → fil staff, relais des messages | ✅ existe |
| Cadres (`post:message`, option `banniere` ajoutée le 19/10), `audit:guild`, `plan:guild`, `apply:guild` (salons **hors catégorie** `topChannels` ajoutés le 19/10) | ✅ existe |
| Accueil par arrivée (§5.1), salon piège (§5.2) | ✅ codé le 19/10, **à activer** (variables d'env) |
| **Pose de `\| Whitelisté` à l'acceptation**, retrait si le rôle Reborn redescend | ⏳ à coder (§7 C) — **Remplacé le 24/10** : rôle whitelist visible depuis le launcher, à définir ; voir [`CANDIDATURE_ET_DOUANE.md`](../gameplay/CANDIDATURE_ET_DOUANE.md). |

**Variables** (`.env` racine et `infra/docker-compose.prod.yml`, vides = désactivé ; détails dans `.env.example`) :
`DISCORD_JOUEURS_GUILD_ID`, `DISCORD_BIENVENUE_CHANNEL_ID`, `DISCORD_BIENVENUE_EMOJI`, `DISCORD_BIENVENUE_COULEUR`,
`DISCORD_BIENVENUE_SEUIL_GROUPE`, `DISCORD_BIENVENUE_SEUIL_RAID`, `DISCORD_JOUEURS_LOG_CHANNEL_ID`,
`DISCORD_JOUEURS_STAFF_ROLE_ID`, `DISCORD_PIEGE_CHANNEL_ID`, `DISCORD_PIEGE_JOURS`, `DISCORD_PIEGE_PURGE_SECONDES`,
`DISCORD_PIEGE_SIMULATION`. L'intent **Server Members** (privilégié) est déjà demandé par le code (`index.ts`) : il doit
être coché dans le portail développeur (Bot → Privileged Gateway Intents) ; il l'est forcément si le bot se connecte
aujourd'hui. Le piège n'a pas besoin de « Message Content ».

## 6. Le nouveau serveur et le plan

Le plan `Discord\plan-joueurs.json` (hors dépôt) est réécrit pour le **nouveau serveur** par
`_sources\build_plan_joueurs.py` : 13 rôles, 2 salons **hors catégorie** en tête (`topChannels`, nouveauté de
`guild-plan-lib.ts`), 4 catégories, 20 salons ; tout est créé (aucune origine), sauf ce que l'audit du nouveau serveur
trouvera (rôle du bot, salons `rules` / `moderator-only` si la Communauté est activée, `général`). Tant que l'ID n'est pas
rempli, `guild.id = "A_REMPLIR"` et `plan:guild` **refuse** le plan. L'ancien plan (serveur perso) est gardé :
`Discord\plan-joueurs.v3-serveur-perso-2026-10-19.json` et `build_plan_joueurs.v3-serveur-perso.py`. **Aucun audit,
plan ni apply n'a été lancé sur le nouveau serveur.**

**Ce que l'utilisateur fait** (dans l'ordre) :
1. **Inviter le bot** sur le nouveau serveur : portail développeur → OAuth2 → URL Generator, scopes `bot` +
   `applications.commands`. Pour appliquer le plan (créer rôles et salons, poser un rôle Administrateur), cocher
   **Administrateur** (permissions `8`), comme sur le Discord staff. **Décision du 22/10 : le bot garde Administrateur**
   (pas de réduction ; si un jour on la fait, la liste minimale est : Voir, Écrire, Intégrer, Joindre, Historique,
   Réactions, Émojis externes, Gérer les messages, Gérer les fils, Fils publics et privés, Écrire dans les fils, Gérer
   les rôles, Gérer les salons, **Bannir** (piège), Déplacer, Se connecter — entier `395423640660`).
2. Vérifier l'intent **Server Members** (§5.3). Activer la **Communauté** du serveur (salon des règles et des mises à
   jour : ceux par défaut, le plan les renomme).
3. Donner l'**ID du serveur** (mode développeur → clic droit sur le serveur → Copier l'identifiant).
4. Ensuite seulement (session suivante, avec validation) : `audit:guild --guild <ID>` → `build_plan_joueurs.py
   …\plan-joueurs.json --audit <audit.json>` → `plan:guild` (simulation, diff à relire) → `apply:guild` lot par lot.
5. Après application : monter et renommer le rôle du bot, s'attribuer `| Fondateur`, désactiver les messages d'arrivée
   natifs, écran de règles (7 règles du §4), 2FA de modération, supprimer à la main les salons créés par Discord à la
   création (listés « hors plan »), remplir les variables du §5.3, poster les messages du §4, puis activer l'accueil.

## 7. Reste à coder

**A. Délai de 1 jour avant une nouvelle candidature joueur** (API whitelist)
- Champ durable hors de la ligne de candidature (le retrait `DELETE /whitelist/me` la supprime et effacerait le
  délai) : p. ex. `User.whitelistRejectedAt` (+ `User.oralPassedAt` pour « oral seulement à la première whitelist »).
- `StaffService` (décision `REJECTED`, globale ou par partie) : renseigner la date.
- `WhitelistService.submit` et `resubmit` (statut `REJECTED`) : refuser avant 24 h (409 + date de fin) ; pas de délai
  après `NEEDS_REVISION`.
- DTO `GET /whitelist/me` : `canReapplyAt` ; launcher (onglet Whitelist) : bouton grisé + compte à rebours ; panel :
  date affichée ; migration Prisma ; tests.

**B. Candidatures de clan par sessions** (n'existe pas)
- Modèles `ClanApplicationSession` (clan(s), ouverture, fermeture, statut, créée par) et `ClanApplication` (joueur,
  personnage, clan, session, texte, statuts, oral, décision, `discordMessageId`).
- API : sessions côté staff (ouvrir / fermer / lister), candidature joueur (déposer si session ouverte, joueur
  whitelisté, une seule par session et par clan ; refus = bloqué jusqu'à la **session suivante** du clan).
- Oral obligatoire : réutiliser `oral-slots` avec un type (whitelist / clan).
- Launcher : écran « Clans » (sessions ouvertes, dépôt, suivi) ; panel : page Clans (sessions, revue, oral, décision) ;
  bot : fil staff dans `📥・candidatures` + annonce d'ouverture dans `📣・annonces` ; jeu : appliquer le clan accepté
  au personnage (ShinobiCore — commande ou synchro API, à définir).

**C. Pose du rôle `| Whitelisté` par le bot**
> **Remplacé le 24/10** : rôle whitelist visible depuis le launcher, à définir ; voir [`CANDIDATURE_ET_DOUANE.md`](../gameplay/CANDIDATURE_ET_DOUANE.md).

- API : à l'acceptation (`StaffService`, là où le rôle passe à `WHITELISTED`) et à toute redescente vers `PLAYER`
  (`/role set`), webhook HMAC `POST /webhooks/discord-role` `{discordUserId, role, action}` (ignoré si Discord non lié).
- Bot : route du serveur de webhooks → `guild.members.fetch(id)` → `roles.add/remove` ; variable
  `DISCORD_WHITELIST_ROLE_ID` (dans `.env`, jamais dans le dépôt) ; journal en cas d'échec (membre absent du Discord).
- Rattrapage : à l'arrivée d'un membre (`GuildMemberAdd`) ou à la liaison Discord, poser le rôle si l'utilisateur est
  déjà `WHITELISTED` (route API de lecture par `discordUserId`).
- Discord : rôle du bot au-dessus de `| Whitelisté`, permission « Gérer les rôles ».

## 8. Vérifications (après application, si validée)

Avec 3 comptes (sans rôle, whitelisté, staff), en comparant aux maquettes `bienvenue_ketto` à `04` :
- arrivée d'un compte de test : dans `🪵`, un message « @pseudo » + bannière + « Reborn Roleplay • N membres »,
  le compte reçoit la notification, personne d'autre ; aucun message d'arrivée natif de Discord en doublon ;
- `DISCORD_BIENVENUE_SEUIL_GROUPE=1` le temps du test, 3 arrivées en moins d'une minute : un seul message groupé, sans
  ping, et la ligne « Arrivées rapides » dans `🧾・journal-bot` ;
- piège en `DISCORD_PIEGE_SIMULATION=1` : un compte de test écrit dans `⚠️・ne-pas-écrire` → message effacé, ligne
  « Piège (simulation) » au journal, pas de ban ; un compte staff y écrit → rien ; sans simulation : ban + MP, le
  bannissement apparaît avec le motif `[piège Reborn] fin=…` (le lever à la main pour le test) ;
- sans rôle : `🍥 | Reborn` et `💬 | Communauté` visibles, rien à écrire, barre « Terminer » ; après « Terminer » :
  écrit dans `général`, `questions`, `médias` et `clips` (rôle `Membre`) ;
- whitelisté : écrit et poste une image dans `médias` et `clips` ; ne voit ni Staff ni Archives ;
- staff : voit `📥・candidatures` avec les fils du bot ; déplace un compte de `⏳・Attente oral` vers `🎙️・Oral 1` (le
  candidat ne peut pas y entrer seul) ;
- une candidature déposée dans le launcher crée toujours son fil dans `📥・candidatures` ;
- liste des membres : groupes du staff dans l'ordre du §2, couleurs lisibles sur fond sombre.
