# Discord Reborn (staff et joueurs) — accès du bot, lecture, style visé, plan cible

> Créé le 2026-10-14, révisé le même jour après les 18 captures de référence envoyées par l'utilisateur
> (FSC, Ketto, Seizen, Zenkai, REDLINE · RL). Concerne **deux Discord** : le **staff** et les **joueurs**
> (communautaire, celui de `DISCORD_GUILD_ID`, où vivent aujourd'hui les fils whitelist / tickets du bot).
>
> **Règle 7** ([`../REPRISE_DE_SESSION.md`](../REPRISE_DE_SESSION.md) §5) : aucun ID de serveur, de salon, de rôle
> ou de membre, aucun token dans ce fichier ni ailleurs dans le dépôt. Les exports vivent **hors dépôt**.
>
> État (14/10, nuit) : **plan des rôles et des salons de « RBN - STAFF » validé** (§4 bis, §5.1). Maquettes
> faites (`Maquettes/discord/`), simulation lancée en lecture seule (`plan:guild`, diff + empreinte hors dépôt ;
> dernière : 14/10 21 h 39, 197 opérations, Invité(e) ajouté(e) au vocal « Vocal libre », rôle du bot déjà
> tout en haut),
> script d'application **écrit mais jamais exécuté** (§3 bis). Rien n'a été modifié sur Discord. Le Discord
> joueurs (§5.2) reste une proposition.
>
> **16/10** : salon `🏗️・build-updates` créé dans Création, juste après `🛰️・dev-updates` et sur son modèle
> (plan → `plan:guild` → lots `salons`, `overwrites`, `salons-ordre` ; vérifié par audit), bannière BUILD UPDATES
> posée, postable depuis le panel (§5.1, §11).
>
> **17/10** : **déploiement web fait** (train web) : page « Discord » du panel et salon `🏗️・build-updates` en ligne
> (§11.4).

---

## 1. Les serveurs en jeu

| Serveur | À qui | Ce qu'on en fait |
|---|---|---|
| **REDLINE · RL** (« serveur modèle ») | L'utilisateur en est **Fondateur** | On y invite le bot pour lire la **structure** (catégories, 76 rôles, permissions) **et le contenu** des salons staff : `hiérarchie`, `règlement-staff`, `transmission`, `décision`, `demote`, `absences`… |
| **Discord staff Reborn** (cible) | Reborn | Audit, puis application du plan §5.1 |
| **Discord joueurs Reborn** (cible, `DISCORD_GUILD_ID`) | Reborn | Audit, puis application du plan §5.2 ; le bot y tourne déjà (whitelist, tickets) |
| Zenkai, Ketto, FSC, Seizen | **Pas à nous** | **Références visuelles seulement** (captures). Jamais d'invitation, jamais de lecture automatisée |

**À savoir sur REDLINE · RL** (vu sur les captures) : le serveur est né le 12/08/2026, tous les rôles sauf
Fondateur ont 0 membre, on y trouve une catégorie « Zenkai » et des rôles « ZK-Network:Ban/Unban ». Il a très
probablement été créé **depuis un modèle de serveur Zenkai**. Un modèle Discord copie salons, rôles et
permissions mais **aucun message** : `hiérarchie` et `règlement-staff` peuvent donc être **vides**. La lecture le
dira (`INDEX.md`, colonne Messages). S'ils sont vides, le principe des grades viendra de l'utilisateur (texte
collé ou captures), pas du bot.

---

## 2. Inviter le bot

### 2.1 Avant d'inviter (une fois)

1. **Developer Portal** (<https://discord.com/developers/applications>) → l'application Reborn → **Bot** :
   - **Public Bot : désactivé** (sinon n'importe qui ayant le client ID peut l'inviter chez lui) ;
   - **Requires OAuth2 Code Grant : désactivé** ;
   - **Message Content Intent : activé** (déjà le cas pour le bot communautaire) — nécessaire à `--lire`.
2. Il faut **Gérer le serveur** sur le serveur visé pour y ajouter un bot (le Fondateur l'a).
3. Le **client ID** est dans le `.env` racine (`DISCORD_CLIENT_ID`) ou dans le portail (General Information →
   Application ID). Construire l'URL à la main, **ne pas la coller dans le dépôt**.

### 2.2 Les deux URL

**A. Lecture complète, sans pouvoir** (recommandée pour les serveurs Reborn) :

```text
https://discord.com/oauth2/authorize?client_id=<CLIENT_ID>&scope=bot&permissions=66560
```

`66560` = **Voir les salons** (1024) + **Voir l'historique des messages** (65536), rien d'autre. Limite : les
salons privés (où `@everyone` est refusé et le rôle du bot pas ajouté) restent masqués.

**B. Administrateur temporaire** (l'utilisateur est d'accord ; le plus simple pour REDLINE · RL, serveur modèle
sans enjeu) :

```text
https://discord.com/oauth2/authorize?client_id=<CLIENT_ID>&scope=bot&permissions=8
```

`8` = **Administrateur** : le bot voit et lit tout, salons privés compris. **À retirer après la lecture**
(Paramètres du serveur → Rôles → rôle du bot → décocher Administrateur), ou expulser le bot du serveur modèle.

Sur un serveur Reborn, variante sans jamais donner Administrateur par l'URL : inviter avec A, puis donner
**temporairement** Administrateur au rôle du bot le temps d'un `--tout`, puis le retirer. Relancer `audit:guild`
sans argument confirme le retrait (la mention « bot ADMIN » disparaît).

- `scope=bot` seul : pas de `applications.commands` (inutile pour l'audit, et le bot ne dépose ses commandes
  que sur le serveur communautaire).
- Dans la page Discord, **vérifier deux fois le serveur choisi**.

### 2.3 Obtenir l'ID d'un serveur

Discord → Paramètres utilisateur → **Avancés** → **Mode développeur** → clic droit sur l'icône du serveur →
**Copier l'identifiant du serveur**. Ne l'écrire dans aucun fichier du dépôt (s'il faut le stocker :
`DISCORD_STAFF_GUILD_ID` dans le `.env` racine, non versionné). La commande sans argument (§3) liste aussi les
serveurs avec leur ID.

### 2.4 Effet sur le bot de production

Le bot de production (`pnpm bot:dev` / VPS) partage le token. Sur un serveur où il vient d'arriver il **ne fait
rien** : commandes slash déposées sur le serveur communautaire seulement, relais de fils limité au salon tickets
communautaire, webhooks qui postent dans des salons fixés par le `.env`, aucun gestionnaire d'arrivée de membre.
Il reçoit en mémoire les messages des salons qu'il voit mais les ignore et ne les stocke pas — raison de plus pour
lui retirer Administrateur après la lecture.

---

## 3. L'audit et la lecture (`apps/bot/src/scripts/audit-guild.ts`)

Depuis la racine du monorepo (le script lit `DISCORD_BOT_TOKEN` dans le `.env` racine) :

```pwsh
# 1. Où est le bot ? (nom + ID de chaque serveur ; le communautaire est signalé)
pnpm --filter @reborn/bot audit:guild

# 2. Structure seule (catégories, salons, rôles, permissions)
pnpm --filter @reborn/bot audit:guild --guild <ID>

# 3. Structure + messages de salons choisis (noms sans emoji ni accent acceptés, IDs, ou nom de catégorie)
pnpm --filter @reborn/bot audit:guild --guild <ID> --lire "hiérarchie,règlement-staff,transmission,décision"
pnpm --filter @reborn/bot audit:guild --guild <ID> --lire "Staff Général" --limite 300

# 4. Structure + tous les salons texte, annonces et forums lisibles (100 derniers messages par salon)
pnpm --filter @reborn/bot audit:guild --guild <ID> --tout
pnpm --filter @reborn/bot audit:guild --guild <ID> --tout --limite 500

# Variante : autre dossier de sortie (doit être hors dépôt, sinon refus)
pnpm --filter @reborn/bot audit:guild --guild <ID> --tout --out "D:\Sauvegardes\Discord"
```

Sortie : `%USERPROFILE%\Desktop\Reborn - Gestion\Discord\audit-<date>_<heure>\` (à côté du dépôt, pas dedans ;
dossier parent réglable par `--out` ou `REBORN_DISCORD_AUDIT_DIR`). Jamais écrasé.

| Fichier | Contenu |
|---|---|
| `audit.json` | Export brut : serveur, bot, rôles (permissions en noms + bitfield, couleur, séparé, mentionnable), catégories et salons (type, position, parent, sujet, mode lent, synchro, overwrites), fils actifs, constats. **Sert de sauvegarde** pour un retour arrière. |
| `RAPPORT.md` | Le même en lisible : résumé, salons masqués au bot, rôles, arborescence « qui voit / qui écrit », points d'attention. |
| `messages\INDEX.md` | (avec `--lire` / `--tout`) Un salon par ligne : catégorie, nombre de messages, fils, lu en entier ou tronqué, fichier ou raison de l'abandon. |
| `messages\<salon>.md` | Messages du plus ancien au plus récent : date, auteur affiché (bot / webhook signalé), épinglé, texte (mentions de salons et de rôles résolues), **embeds** (couleur, auteur, titre, description, champs, pied, image, vignette), **composants** (boutons avec libellé, style, emoji, lien ; menus avec options ; composants v2 : conteneurs, textes, galeries), messages transférés, sondages (question, réponses, votes), réactions, **pièces jointes : nom + URL, rien n'est téléchargé**. Forums : un chapitre par post (étiquettes comprises). |
| `messages\<salon>.json` | Le même en brut. |

Garanties :

- **Lecture seule** : uniquement le cache gateway et des GET. Le garde-fou intercepte toutes les requêtes REST
  et **refuse toute méthode autre que GET** avant l'envoi, lecture de messages comprise.
- **Intents** : `Guilds` seul pour la structure ; `GuildMessages` + `MessageContent` **seulement** avec
  `--lire` / `--tout`. Jamais `DirectMessages` : **aucun message privé n'est lu** (le script ne parcourt que les
  salons du serveur passé en `--guild`).
- `--tout` couvre texte, annonces et forums (posts actifs et archivés publics). Le chat textuel des vocaux
  n'est lu que s'il est nommé dans `--lire`. Les fils privés ne sont pas lus.
- Un salon sans **Voir les salons** ou **Voir l'historique** pour le bot est listé « ignoré » avec la raison.
- Refuse d'écrire dans le dépôt ; vérifie le dossier **avant** de se connecter.
- Ouvre une seconde session du même bot le temps de l'audit : sans effet sur le bot de production.
- Les exports contiennent des pseudos et des IDs : dossier privé, jamais dans le dépôt ni sur le Trello public.

---

## 3 bis. Simulation et application d'un plan (`plan-guild.ts`, `apply-guild.ts`)

Le plan (rôles, catégories, salons, permissions, avec les **IDs d'origine** pour renommer / déplacer au lieu de
recréer) est un JSON **hors dépôt** : `Desktop\Reborn - Gestion\Discord\plan-rbn-staff.json`. Code commun :
`apps/bot/src/scripts/guild-plan-lib.ts`.

```pwsh
# Simulation, lecture seule (GET uniquement, même garde-fou que l'audit) : diff + empreinte SHA-256
pnpm --filter @reborn/bot plan:guild
pnpm --filter @reborn/bot plan:guild --sans-admin     # diff du lot « permissions » avec retrait d'Administrateur

# Application (MODIFIE le serveur) : un lot à la fois, empreinte du diff affiché juste avant
pnpm --filter @reborn/bot apply:guild --confirm <empreinte> --lot roles
pnpm --filter @reborn/bot apply:guild --confirm <empreinte> --attribuer-fondateurs
```

`plan:guild` écrit `Discord\diff-<date>\` : `DIFF.md` (résumé chiffré, actions manuelles, renommages avant →
après, créations, couleurs, permissions ajoutées / retirées, ordre des rôles, salons créés / renommés /
déplacés, overwrites avant → après, arborescence cible, éléments hors plan « à archiver / supprimer —
décision », **accès simulés par membre** avant → après, commandes), `diff.json`, `etat.json`, `EMPREINTE.txt`.

Lots, dans l'ordre : `roles` (création des rôles et séparateurs, sans permission) · `roles-maj` (noms, couleurs,
séparé, mentionnable) · `roles-ordre` · `permissions` (rôles puis @everyone en dernier) · `salons` (création,
renommage, déplacement ; un salon créé naît invisible pour @everyone) · `overwrites` (catégories puis salons
synchronisés) · `salons-ordre`.

Garde-fous d'`apply:guild` :

- **refuse sans `--confirm <empreinte>`** égale à l'empreinte du diff **recalculé au moment de l'exécution**
  (le serveur ou le plan a bougé → refus, relancer `plan:guild`) ;
- toute requête autre que GET est rejetée hors de la fenêtre d'écriture d'un lot ; chaque écriture est
  journalisée (`Discord\apply-<date>\JOURNAL.md`, `etat-avant.json`) ; **arrêt au premier échec** ;
- **sauvegarde complète** (`audit-guild.ts`) juste avant d'écrire, sinon refus ;
- **jamais de suppression** (rôle ou salon), **aucune attribution de rôle** sauf `--attribuer-fondateurs`
  (donne `| Fondateur` aux membres qui ont aujourd'hui Direction, listés avant) ;
- Administrateur retiré à Direction / Head of Reborn **seulement** par `--lot permissions` explicite (empreinte
  de `plan:guild --sans-admin`), si `| Fondateur` existe, a Administrateur et **au moins un membre** ;
- vérifie que le bot a **Gérer les rôles** / **Gérer les salons**, que son rôle est **au-dessus** des rôles qu'il
  modifie (`roles-ordre` exige son rôle tout en haut) et qu'il **possède chaque permission qu'il accorde**
  (règle Discord : donner Administrateur à `| Fondateur` exige un bot Administrateur) ; sinon refus expliqué ;
- sans `--lot`, les lots s'enchaînent ; chaque lot après le premier est recalculé sur l'état frais et doit avoir
  la même intention (signature) que dans le diff validé, sinon arrêt.

Ce que le bot ne peut pas faire et reste **manuel** (listé en tête du diff) : monter son propre rôle tout en
haut, le renommer `🔒 | Bot(s)`, lui retirer Administrateur après application (garder Voir / Envoyer / Intégrer
des liens), exiger la 2FA de modération.

## 4. Style visé

Relevé sur les captures, puis adapté à Reborn. Palette : **nuit** `#0A0608` (fonds), **laque** `#A0182B`
(`#C01E35` pour du texte sur fond sombre), **or** `#D9A95E`, **crème** `#F5E9D0` — `menu/Colors.java`. Ton
Naruto (shinobi, villages, ninja), **sans kanji ni écriture japonaise décorative** (règle DA du 14/10,
[`../design/DIRECTION_ARTISTIQUE.md`](../design/DIRECTION_ARTISTIQUE.md)) : pas de catégorie « ようこそ » comme
Seizen, pas de sceaux sur les bannières.

### 4.1 Nommage

| Élément | Observé | Reborn |
|---|---|---|
| Salon | `emoji · nom` en minuscules (Zenkai, Ketto, RedLine) ; `emoji ┆ nom` (Seizen) ; `emoji · NOM` en capitales (FSC, RedLine haut) | **`emoji·nom`**, minuscules, un seul emoji par salon, choisi pour être lisible à 16 px. Séparateur `·` (point médian), le même partout |
| Catégorie | `· Nom` précédé d'un emoji rond (Zenkai, RedLine), `✕ \| Ketto`, `【 🏮 】 Important` (FSC) | **`· Nom`** court, majuscule initiale (`· Accueil`, `· Reborn`, `· Staff Général`) ; une catégorie repliée par défaut pour le staff (`Logs`) |
| Rôle | `\| Fondateur`, `\| Resp. RP` (barre devant), `🔒\| Bot(s)` pour les rôles verrouillés | **`\| Nom`** pour les grades et pôles ; rôles techniques sans barre |
| Séparateur de rôles | rôle vide `______________________` (0 membre, aucune permission) entre deux niveaux | Idem : un séparateur entre chaque **niveau** (Direction / Développement / Management / Responsables / Équipe / Pôles / Joueurs) |
| Pseudo staff | préfixe de grade `AD · Pseudo`, `DEV · Pseudo`, `CM · Pseudo`, `RD · Pseudo` (Ketto, Seizen, Zenkai) | **Option** : `ADM · Pseudo`, `DEV · Pseudo`… à trancher (§8) |

### 4.2 Rôles : hiérarchie et couleurs

Observé (REDLINE · RL, 76 rôles, liste latérale Zenkai / Ketto / FSC) : rôles **séparés** dans la liste des
membres (« Créateur — 2 », « Direction — 5 »), une **couleur par niveau** (rouge Direction, orange Dev/Managers,
jaune Responsables, vert CM, bleu Administrateur, rouge Genius Team), séparateurs, bot en tête (« 🔒\| Bot(s) »).
Grille REDLINE (haut → bas) : Bot(s) · RedLine · séparateur · Fondateur · Co-Fondateur · Direction · Lead
Développement · séparateurs · Développeur · `*` · RedLine Launcher · Manager · Staff / Roleplay / Animation / MJ /
Streamer / Douane Manager · séparateur · ZK-Network:Unban/Ban · séparateur · Ambassadeur · séparateur ·
Responsable(s) · Resp. RP / Iles / MJ / Streamer / Douanier / Lore · séparateur · Lead CM · Administrateur ·
Community Manager · séparateur · Genius Team · nanos team…

Reborn, **dégradé dans la palette** (première proposition ; pour le staff, **remplacée par le plan validé du §4 bis**) :

| Niveau | Couleur proposée | Séparé |
|---|---|---|
| Fondateur | or `#D9A95E` | oui |
| Co-Fondateur, Direction | laque claire `#C01E35` | oui |
| Lead Développement, Développeur | crème `#F5E9D0` | oui |
| Managers (Staff, Roleplay, Animation, MJ) | ambre `#E0A040` | oui |
| Responsables de pôle (RP, Lore, MJ, Build, Whitelist) | or pâle `#E8CC94` | oui |
| Administrateur, Modérateur, Helper | `#C2B59A` → `#9C8F78` (du plus clair au plus éteint) | oui |
| Pôles (Animateur, Builder, Lore…) | aucune couleur | non |
| Joueurs : Whitelisté / Citoyen | violet Reborn `#8B5CF6` (rôle whitelisté du jeu) | non |

`#A0182B` reste réservé aux **barres d'embed et aux bannières** : en couleur de pseudo il est trop sombre sur le
fond Discord.

### 4.3 Messages du bot : embeds à bannière

Tous les serveurs de référence ont le même procédé : **un bot (« APP ») poste un message d'en-tête par salon**,
salon en lecture seule, avec une **bannière illustrée** (titre en grande typographie pinceau/manuscrite sur un
décor de l'univers) puis un embed sobre. Ce qu'on reprend, salon par salon :

| Salon | Référence observée | Reborn |
|---|---|---|
| **Bienvenue / arrivées** | Zenkai : embed par arrivée « Bienvenue sur Zenkai, @X ! », « tu es notre 36064e membre », renvoi vers `#règles` et `#infos`, avatar en vignette, bannière « Bienvenue » (pagode, nuages). Seizen : « 👋 Bienvenue Ninja ! » + logo en bannière | Embed nuit, barre laque, « Bienvenue au village, @X », **numéro de membre**, renvoi `#règlement` + `#nous-rejoindre`, avatar en vignette, bannière « Bienvenue » (village de nuit, or). **Nécessite du code** : événement d'arrivée (le bot a déjà l'intent GuildMembers) |
| **Règlement** | En-tête à bannière, salon `règles` épinglé dans Accueil | Bannière « Règlement » + un embed par chapitre (champs numérotés), pied « Reborn · mis à jour le … » |
| **Informations / réseaux** | Ketto : bannière « INFORMATIONS » + embed « Nos Réseaux » (icône par réseau, lien) | Bannière « Informations » + embed liens (site, launcher, YouTube, TikTok, X, Twitch) avec les **vrais logos** en emoji serveur |
| **Tickets** | Ketto : bannière « TICKETS » + embed « Support » : phrase d'accroche, **motifs** en liste (Boutique, Technique, Signalement, Contestation, Autre) avec icône, **bouton « Ouvrir un ticket »** → salon privé | Bannière « Support » + embed motifs (Technique / launcher, Signalement, Contestation de sanction, Whitelist, Boutique RBCoins, Autre) + bouton « Ouvrir un ticket » → **menu de motifs puis formulaire**, ticket créé **par l'API** (même table que le launcher) et fil privé. **Code à écrire** (§7) |
| **Whitelists** | Zenkai : annonces d'ouverture « Ouverture à 18h30 ! » / « Fermeture à 3h, Bon jeu ! » avec emoji interrupteur, illustration, réactions | « Session de whitelist » : ouverture des candidatures, créneaux d'oraux, résultats — posté par le bot depuis le panel (oral-slots) |
| **Nous rejoindre** | Zenkai : conteneur « ✦ TÉLÉCHARGER LE LAUNCHER & REJOINDRE ZENKAI », étapes avec icône (site → télécharger → créer le compte → page principale → onglet Whitelist → candidature), ligne de transition en police mono, GIF de mascotte | Même structure en **composants v2** (conteneur, textes, séparateurs, galerie) : site → launcher → connexion Microsoft → lier Discord → candidature whitelist → oral → jouer ; bouton lien « Télécharger le launcher » ; image animée de fin (pas de kanji) |
| **Annonces** | Texte libre + `@everyone`, illustration, nombreuses réactions | Embed annonce : titre, texte, illustration, pied « La Direction » ; mention `@Annonces` (rôle à notification) plutôt que `@everyone` |
| **Spoils / roadmap** | Zenkai : légende (✅ fait, 🟡 en cours, ❌ pas commencé), visuel « ROADMAP » par colonnes | Visuel roadmap aux couleurs Reborn + légende, tiré de `docs/ROADMAP_BETA_2026.md` / `GAMEPLAN.md` (sans rien d'inédit non validé) |
| **Dailylog / patch-notes** | Zenkai « Zenkai Launcher » : en-tête « PATCH NOTE · DailyLog 13/10/2026 · Ajouts, ajustements et fix » + logo, puis sections « Ajouts » → « Roleplay » → paragraphes « **Classeur :** … », pied « Équipe Zenkai » ; une publication par jour | Bot « Reborn » : en-tête « PATCH NOTE · Journal du jj/mm/aaaa · Ajouts, ajustements et correctifs », sections Ajouts / Ajustements / Correctifs puis par domaine (Roleplay, Combat, Interface, Launcher), entrées « **Nom :** phrase », pied « Équipe Reborn ». Alimenté par la publication du manifest (route `mods-update` existante, à enrichir) |
| **Consoles (staff)** | FSC « FSC UPDATE BOT » : un embed par push « [Dépôt] (branche) - 1 nouvelle mise à jour : », hash court + auteur, message de commit, **bandeau « UPDATE » coloré par branche** (rouge = branche de fonctionnalité, bleu = master), pied « 1 commit sur master · heure », barre latérale de couleur | Salon staff `consoles` : un embed par push sur `origin` (« [reborn-roleplay] (main) — 3 commits »), liste hash + titre Conventional Commits, bandeau « MISE À JOUR » **or** pour `main`, **laque** pour les autres branches |
| **Événements / sondages** | Zenkai : **sondage natif Discord** (« Qu'aimeriez-vous voir cette semaine ? », résultats en barres, « Vote visible · Terminé »), quiz en fil | Sondages natifs postés par le staff ; annonce d'event en embed (affiche, date, lieu en jeu, consignes) |
| **Logs (staff)** | REDLINE : une catégorie Logs, un salon par type (`cmd`, `generic-logs`, `ban-logs`, `mod-service`, `join-leave`, `messages`, `modérations`, `vocal`, `rôles`, `invitations`, `join-limit`, `serveurs`, `réactions`, `sondage`, `membre`, `tokengrab`, `wiki-log`, `archives`), pastille de couleur par gravité (bleu info, rouge sanctions, orange activité, jaune technique) | Même découpage, pastille emoji par gravité (🔵 info, 🔴 sanction, 🟠 activité, 🟡 technique), embeds courts sans bannière |

Règles communes des embeds Reborn :

- **Bannière** : PNG 1200 × 340 (≈ 3,5 : 1, lisible dans la largeur d'un embed), **titre seul** en blanc cassé
  / or (pas de sur-titre ni de sous-titre, décision du 14/10), un **personnage de Naruto** (render détouré, ou
  un duo) sur un côté, coupé par le bord comme sur les références, décor de nuit laque et or, **marge de
  sécurité** de 80 px sur les côtés pour le titre, pas de texte fin. Les flux
  (consoles, patch-notes) utilisent un **bandeau** plus bas, 1200 × 200.
- Couleur de barre : laque `#A0182B` (information), or `#D9A95E` (Direction, mises à jour `main`), vert / rouge
  du kit seulement pour un état (succès, sanction).
- Un message d'en-tête par salon d'information, **salon en lecture seule** ; mise à jour en **éditant** le
  message du bot (pas de nouveau message), pour garder le salon propre.
- Pied d'embed uniforme : « Reborn · <rubrique> ».
- Emojis : ceux du serveur (logos réels, icônes plates), pas d'emoji kanji.

---

## 4 bis. Rôles — RBN - STAFF (plan validé le 14/10)

> Validé par l'utilisateur le 14/10 ([`../pilotage/DECISIONS.md`](../pilotage/DECISIONS.md)). Base : audit en
> lecture seule (20 rôles, 29 membres). On **renomme** les rôles existants (membres gardés), on ne supprime
> rien. Pôle **Roleplay** = staff d'**un** projet (porte le tag du projet) ; pôle **Création** = équipe commune
> au réseau. Grades de base en français, titres **Head of / Lead** en anglais.

| # | Rôle | Couleur | Séparé | Mentionnable | Permissions serveur | Rôle actuel |
|---|---|---|---|---|---|---|
| 1 | `🔒 \| Bot(s)` | — | oui | — | Gérées à la main (le bot ne touche pas son rôle) ; à terme sans Administrateur | `Reborn` (rôle du bot) |
| 2 | `━━━━━━ DIRECTION ━━━━━━` | — | — | — | aucune | *nouveau* |
| 3 | `\| Fondateur` | or `#D9A95E` | oui | — | **Administrateur** (le seul) — porté par les 3 membres qui ont Direction aujourd'hui | *nouveau* |
| 4 | `\| Co-Fondateur` | or pâle `#EBD3A0` | oui | — | Celles de Direction + Gérer le serveur, les webhooks, les expressions | *nouveau* |
| 5 | `\| Direction` | rouge `#F06A78` | oui | — | Gérer salons, rôles, pseudos, événements ; logs ; expulser, bannir, exclure ; gérer messages, fils, épingles ; vocal (déplacer, muet, sourdine) ; invitations ; @everyone. **Sans** Administrateur ni Gérer le serveur / webhooks | `Direction` (perd Administrateur) |
| 6 | `Haut Staff` | — | non | oui | aucune (accès par les salons) | `Haut staff` |
| 7 | `━━━━━━ GÉRANCE ━━━━━━` | — | — | — | aucune | séparateur « Gérance » |
| 8 | `\| Head of Reborn` | vermillon `#F28A5C` | oui | oui | Gérer rôles et pseudos ; événements ; logs ; expulser, exclure ; messages, fils, épingles ; déplacer, muet ; invitations | `Gérant SR` (perd Administrateur) |
| 9 | `\| Head of Projet X` | vermillon `#F28A5C` | oui | oui | idem | `Gérant PX` |
| 10 | `\| Lead Roleplay` | ambre `#EBA352` | oui | oui | Événements ; logs ; expulser, exclure ; messages, fils, épingles ; déplacer, muet ; invitations | `Lead Rôleplay` |
| 11 | `\| Lead Creation` | ambre `#EBA352` | oui | oui | idem | `Lead Création` |
| 12 | `\| Lead Dev` | ambre `#EBA352` | oui | oui | idem | `Gérant Dev` |
| 13 | `\| Lead Communication` | ambre `#EBA352` | oui | oui | idem (partenariats, langues, réseaux) | *nouveau* |
| 14 | `\| Lead Staff` | ambre `#EBA352` | oui | oui | idem + Gérer rôles et pseudos (recrutement, formation, absences, demote) | *nouveau* |
| 15 | `━━━━━━ RESPONSABLES ━━━━━━` | — | — | — | aucune | *nouveau* |
| 16 | `\| Responsable Roleplay` | crème `#F5E9D0` | oui | oui | Gérer rôles et pseudos ; exclure ; messages, fils, épingles ; déplacer | `Responsable` |
| 17 | `\| Responsable Création` | crème `#F5E9D0` | oui | oui | idem | *nouveau* |
| 18 | `━━━━━━ STAFF ━━━━━━` | — | — | — | aucune | séparateur « Membre du Staff » |
| 19 | `\| Roleplay` | terre `#D9A27E` | oui | oui | aucune — tag de pôle | `Rôleplay` |
| 20 | `\| Création` | jade `#8FC7AE` | oui | oui | aucune — tag de pôle | `Création` |
| 21 | `Shinobi Reborn` | — | non | oui | aucune — tag de projet | `Shinobi Reborn` (perd sa couleur, n'est plus séparé, perd ses permissions) |
| 22 | `Projet X` | — | non | oui | aucune — tag de projet | *nouveau* |
| 23 | `Staff` | — | non | — | aucune — socle (ouvre Informations, Staff Global, Vocaux) | `Staff` (n'est plus séparé) |
| 24 | `En formation` · `Absent` | — | non | — | aucune | *nouveaux* |
| 25 | `━━━━━━ SPÉCIALITÉS ━━━━━━` | — | — | — | aucune | *nouveau* |
| 26 | `MJ` · `Animateur` · `Recruteur` · `Modérateur` · `Scénariste` · `Développeur` · `Builder` · `Modélisateur` · `Animateur 3D` · `Graphiste` · `Game Designer` · `Community Manager` · `Traducteur` · `Testeur` | — | non | oui | aucune | *nouveaux* |
| 27 | `━━━━━━ COMMUNAUTÉ ━━━━━━` | — | — | — | aucune | séparateur « Communautaire » |
| 28 | `\| Invité(e)` | bleu-gris `#8FA6BF` | oui | oui | aucune — voit Informations (lecture), `retours-de-test` et le vocal `🔊・Vocal libre` (voir, se connecter, parler, vidéo) | `Invité(e)` |
| 29 | `\| Partenaire` | lavande `#A99BCB` | oui | — | aucune | *nouveau* |
| 30 | `Server Booster` | inchangé | — | — | inchangé (géré par Discord) | `Server Booster` |
| — | `nouveau rôle` | — | — | — | — | vide : **suppression proposée**, décision au moment d'appliquer |

`@everyone` : discussion de base, **sans** « Voir les salons » ni « Créer une invitation » (un nouvel arrivant ne
voit que le règlement). Couleurs : contraste ≥ 4:1 sur le fond sombre (`#F06A78` plutôt que `#F0606E`, trop
juste sur `#313338`). `Haut Staff`, tags de projet, `Staff`, `En formation`, `Absent`, spécialités et
séparateurs n'ont **pas de couleur** : ils ne masquent pas celle du grade.

## 5. Plans des salons

### 5.1 Discord STAFF « RBN - STAFF » (plan validé le 14/10)

Tout l'existant est **renommé et déplacé** (aucune suppression, historique gardé) ; style `emoji・nom`,
catégories décorées `▬《emoji》Nom •`. Les permissions sont posées sur la catégorie, salons synchronisés sauf
exceptions (*propres*).

| Catégorie | Salons (ancien nom entre parenthèses) | Qui voit |
|---|---|---|
| **▬《🔔》Informations •** | `📜・règlement` (⭐・règlement, salon règlement de la communauté) · `📣・annonces` (annonce) · `❓・questions` · `📊・sondages` · `💡・idées` (forums) | Staff, Haut Staff ; Invité(e) en lecture ; le règlement est le seul salon visible par @everyone ; annonces et règlement : seul le Haut Staff écrit |
| **▬《🔥》Nos serveurs •** | `🍥・shinobi-reborn` (x, nom à confirmer) · `🎓・projet-x` · `☄️・trello-roadmap` | Staff en lecture, Haut Staff écrit |
| **▬《🏮》Staff Global •** | `📜・règlement-staff`* · `🏯・hiérarchie`* · `💬・général-staff` · `🎲・bordel` · `🌙・absences`* · `✏️・décisions`* · `👑・chat-direction` (chat-direction) · `🚫・demote`* · `🚀・transmission`* · `📬・suggestions`* · `🐞・bug-interne`* (forums) · `📣・communication` (forum comm du Pôle Création) · vocal `🎙️・Réunion` | Staff ; règlement-staff, hiérarchie et décisions en lecture (Haut Staff écrit) ; chat-direction, demote, transmission : **Haut Staff seulement** |
| **▬《🍥》Roleplay — Shinobi Reborn •** (Pôle Roleplay) | `📥・recrutement` · `🎭・mj` · `🎉・animation` · `🛡️・modération` · `📖・lore` (forums ; lore lisible aussi par Création) | Tag `Shinobi Reborn`, Haut Staff, Responsable Roleplay |
| **▬《🛠️》Création •** (Pôle Création) | `🛰️・dev-updates`* (bot) · `🏗️・build-updates`* (bot, 16/10) · `🖥️・consoles`* (bot) · `🧪・retours-de-test` (feedback-dev, 13 fils) · `🐞・bugs`* · `💻・dev` · `🧱・build` · `🗺️・map` · `🧊・modélisation` · `🎞️・animation` · `🎨・graphisme` (graphisme-texturepack) · `📐・game-design`* | Tag `\| Création`, Haut Staff, Responsable Création ; dev-updates et build-updates lisibles par tout le staff (seul le bot écrit) ; consoles : Création seulement ; retours-de-test et bugs : tout le staff, retours-de-test aussi les **Invité(e)s** |
| **▬《🔊》Vocaux •** (catégorie vide « Communications ») | `🔊・Vocal staff` · `🔊・Taff` (Taff voice) · `🔊・Vocal libre` (Voc) | Staff ; **`🔊・Vocal libre` aussi aux Invité(e)s** (voir, se connecter, parler, vidéo — décision du 14/10 au soir), les autres vocaux restent staff |
| **▬《📜》Logs •*** | `🔵・logs-serveur` (💻」logs, salon système, fermé à @everyone) · `🔵・commandes`* · `🔴・sanctions`* · `🟠・arrivées-départs`* · `🟠・messages`* · `🟠・vocal`* · `🟠・rôles`* · `🟠・invitations`* · `🟡・journal-jeu`* · `🟡・alertes`* | **Direction** (et Co-Fondateur) en lecture, le bot écrit |
| **▬《🗄️》Archives •** (Archives) | `🗄️・archives` · `🗄️・archive-roleplay` · `🗄️・archive-création` (forums archive des deux pôles) | Direction |

\* = créé par le plan (22 créations : 1 catégorie, 21 salons ; + `🏗️・build-updates` le 16/10). Les salons de
logs restent vides tant que le bot n'a pas le code des logs (§7.2).

**`🏗️・build-updates`** (ajouté le 16/10) — sujet : « Avancées du build publiées : zone, avant/après,
avancement ». Mêmes permissions que dev-updates : Staff, `| Création` et Haut Staff voient, personne n'écrit
sauf le bot (la Direction garde ses droits d'administratrice). Usage : **un post par avancée marquante** (pas un
journal au fil de l'eau) avec la **zone**, des **captures avant / après**, l'**avancement** (en % et prochaine
étape) et les **auteurs** ; posté depuis le panel (§11, modèle « Build update »), sans mention. Les échanges de
travail restent dans `🧱・build` et `🗺️・map`.

**Ce que la simulation a montré** (accès par membre, avant → après, détail dans le `DIFF.md` hors dépôt) :

- les membres qui n'ont **que** `Invité(e)` passent de 16 à 7 salons (Informations + retours-de-test + Vocal
  libre) : voulu ;
- **3 membres `Création` n'ont pas `Staff`** : ils perdraient Staff Global, Informations et les vocaux → leur
  donner `Staff` (à la main) avant ou juste après le lot `overwrites` ;
- `Shinobi Reborn` est porté aujourd'hui par 12 membres, presque tous du pôle Création : ils **verront** la
  catégorie Roleplay — Shinobi Reborn (invisible aujourd'hui hors admins). Retirer le tag à ceux qui ne font
  pas de roleplay si ce n'est pas voulu ;
- le pôle Création perd ses droits de gestion (gérer messages / fils, mentionner @everyone) donnés aujourd'hui à
  **tout** le tag dans sa catégorie : ils restent aux Leads et Responsables ;
- les Leads (Haut Staff) **voient désormais `chat-direction`** et son historique (51 messages) ;
- les archives des pôles ne sont plus lisibles que par la Direction.

### 5.2 Discord JOUEURS

| Catégorie | Salons |
|---|---|
| **· Accueil** | `📜·règlement` (règles du serveur, écran d'adhésion Discord), `👋·bienvenue` (arrivées, bot), `💎·booster` |
| **· Reborn** | `📢·annonces`, `🔒·whitelists` (sessions, oraux), `📰·news`, `📖·trame` (lore), `🔮·spoils` (roadmap), `🗓️·patch-notes` (journal quotidien, bot), `🚪·nous-rejoindre` (launcher), `📋·infos`, `❓·faq`, `⭐·partenariat`, `🌐·réseaux`, `🎉·events`, `📊·sondages` |
| **· Communauté** | `💬·général`, `🎭·personnages` (fiches RP), `📷·médias`, `🎬·clips`, `🎨·créations`, `❔·questions` (forum), `💡·suggestions` (forum), `🐞·bug-report` (forum), `🤖·commandes` |
| **· Support** | `🎫·tickets` (bouton « Ouvrir un ticket »), vocal `Attente support` |
| **· Vocaux** | `Créer ton vocal` (salon temporaire, si on code la fonction), `Général`, `Scène RP`, `AFK` |

Le flux actuel des fils whitelist / tickets (salon `DISCORD_TICKETS_CHANNEL_ID`) devient privé (staff seulement)
ou passe sur le Discord staff — décision à part (§8), changement de `.env` et de code.

---

## 6. Méthode prudente et principes (inchangés)

Chaque étape attend un **« oui » explicite** de l'utilisateur. Rien n'est automatique.

| # | Étape | Qui | Écrit sur Discord ? |
|---|---|---|---|
| 1 | Inviter le bot (URL A ou B, §2) sur le modèle et les cibles | Utilisateur | Non (ajout du bot) |
| 2 | `audit:guild` puis `--guild <ID> --tout` sur chaque serveur ; retirer Administrateur ensuite | Utilisateur lance, Claude lit | Non |
| 3 | **Sauvegarde** : copier chaque dossier `audit-…` dans un second endroit privé | Utilisateur | Non |
| 4 | **Plan détaillé + maquettes** (bannières, liste des rôles, en-têtes de salon) ; validation **ligne par ligne** | Claude propose, utilisateur valide | Non |
| 5 | **Script d'application en simulation** (pas codé) : relit l'état réel, affiche le diff exact — créé / modifié (avant → après) / déplacé / à archiver — sans rien envoyer | Claude | Non |
| 6 | **Application par lots**, chacun après « oui » sur le diff : 1) rôles (sans attribution), 2) catégories et salons, 3) permissions, 4) ordre, 5) messages d'en-tête et bannières, 6) archivage de l'ancien | Utilisateur lance | Oui, lot validé seulement |
| 7 | Audit après chaque lot, comparé au précédent | Utilisateur lance, Claude compare | Non |
| 8 | Retirer au bot les permissions élevées ; lui laisser le minimum | Utilisateur | Oui |
| 9 | Attribuer les rôles aux membres | **Utilisateur, à la main** | Oui |

Garde-fous du futur script d'application : simulation par défaut, application avec l'**empreinte du diff**
affiché, refus si le serveur a changé ; **jamais de suppression** de salon ni de rôle sans validation ligne par
ligne (archivage par défaut : supprimer un salon détruit ses messages) ; pas d'attribution de rôles par le bot ;
pendant l'application, **Gérer les salons** et **Gérer les rôles** seulement, jamais Administrateur pour écrire ;
retour arrière depuis `audit.json`.

Principes : **moindre privilège** ; **@everyone ne voit rien** sur le staff ; **Administrateur pour 1 ou 2
personnes**, les Admin ont des permissions explicites sans Gérer le serveur ni Gérer les webhooks ; **2FA exigée**
pour la modération ; **invitations** par Admin+ seulement, à usage unique ; **départ** d'un staff = retrait des
rôles, expulsion, révocation de ses invitations ; permissions posées **sur la catégorie**, salons synchronisés,
exceptions rares ; jamais de permission à un membre nommément ; rien d'identifiant dans le dépôt.

Bot sur le serveur staff, à terme : **Voir les salons + Envoyer des messages + Intégrer des liens** dans `Logs`,
`avancées-dev` et `consoles` seulement. Sur le serveur joueurs : en plus, envoyer dans `bienvenue`,
`patch-notes`, `whitelists` et créer des fils privés pour les tickets.

---

## 7. À produire

### 7.1 Bannières (PNG 1200 × 340, bandeaux 1200 × 200)

| # | Bannière | Serveur | Décor proposé |
|---|---|---|---|
| 1 | Bienvenue | Joueurs | Village de nuit vu des toits, lanternes, or |
| 2 | Règlement | Joueurs | Parchemin déroulé sur laque |
| 3 | Informations | Joueurs | Portes du village, ciel de nuit |
| 4 | Nous rejoindre | Joueurs | Chemin vers le village + silhouette du launcher |
| 5 | Whitelists | Joueurs | Bureau de l'Hokage, dossiers |
| 6 | Support (tickets) | Joueurs | Poste de garde de nuit |
| 7 | Annonces | Joueurs | Toits et bannière de village |
| 8 | Patch note (bandeau) | Joueurs | Bandeau nuit, filet d'or, logo Reborn à droite |
| 9 | Spoils / roadmap | Joueurs | Carte du monde voilée |
| 10 | Events | Joueurs | Place du village en fête |
| 11 | Partenariat | Joueurs | Poignée de main shinobi, sobre |
| 12 | Règlement staff | Staff | Laque et or, sobre |
| 13 | Hiérarchie | Staff | Escalier / pyramide des grades, sobre |
| 14 | Mise à jour (bandeau consoles) | Staff | Deux variantes : or (`main`), laque (autres branches) |
| 15 | Avancées dev (bandeau) | Staff | Nuit, filet d'or |

Typographie : un titre pinceau latin (comme les références) ou la police du menu, **sans kanji**. Maquettes
dans `Maquettes/discord/` à montrer avant toute publication (règle 2).

**Faites le 14/10 pour le staff** (hors dépôt, `Maquettes\discord\`) : `01_sidebar_salons.png` (barre des salons
par profil, calculée depuis le plan), `02_roles.png` (écran Rôles), `03_membres.png` (liste des membres groupée,
pseudos fictifs), `04_bannieres.png` + `bannieres\*.png` (WELCOME, RULES, HIERARCHY, ANNOUNCEMENTS, DEV UPDATES,
TEST FEEDBACK, TICKETS, ABSENCES, MEETING ; BUILD UPDATES ajoutée le 16/10 avec Hashirama, Mokuton, village en
chantier — 1200 × 340, **titre seul** en capitales pinceau, sans sur-titre
ni sous-titre, et **un vrai personnage de Naruto (ou un duo) par bannière** : Naruto, Tsunade, les Hokage
(Hashirama, Tobirama, Minato), Shikamaru, Orochimaru, Rock Lee + Gai, Iruka, Kakashi plongé dans son livre,
Hiruzen ; renders détourés, étalonnés nuit / laque, contre-jour, ombre portée, coupés par le bord ; sources et
détourage dans `_sources\persos\SOURCES.md`, usage privé), `05_embed_dev_update.png` (message du bot : bannière, version, Ajouts / Ajustements / Fix, À tester,
captures). Générateurs Python dans `Maquettes\discord\_sources\`.

### 7.2 Messages du bot

| Message | Type | Code |
|---|---|---|
| En-têtes fixes (règlement, informations, nous-rejoindre, tickets, faq, partenariat, réseaux, règlement-staff, hiérarchie) | Un message par salon, **édité** à chaque mise à jour, contenu versionné dans le dépôt (sans ID) | Script « poser / mettre à jour les en-têtes » (simulation puis application), à écrire |
| Bienvenue | Un embed par arrivée, numéro de membre | Événement d'arrivée dans le bot, à écrire |
| Ouvrir un ticket | Bouton → menu de motifs → formulaire → ticket API + fil privé | Interaction + route API, à écrire |
| Patch note / journal | Un embed par publication ou par jour | Enrichir `mods-update` (contenu du changelog), à écrire |
| Consoles | Un embed par push | Route signée (webhook GitHub) ou étape du prompt de push ; à décider |
| Whitelists | Ouverture / fermeture des sessions, oraux | Depuis le panel (oral-slots), à écrire |
| Logs | Arrivées / départs, messages supprimés, rôles, sanctions | Événements du bot, à écrire (intents déjà présents) |

---

## 8. Points à trancher

- **Préfixe de grade dans les pseudos** (`ADM · Pseudo`) : oui / non.
- **Tickets** : bouton Discord en plus du launcher (même table, via l'API) ou launcher seul ?
- **Fils whitelist / tickets du bot** : restent sur le serveur joueurs (salon privé) ou passent sur le staff ?
- **Consoles** : flux de commits automatique (webhook GitHub sur le dépôt public) ou posté par le prompt de push ?
- **Patch-notes** : une publication par jour (Zenkai) ou par mise à jour publiée ?
- Grades : **Builder** et **Modélisateur** un seul rôle ou deux ; **Lore** pôle à part ou rattaché à l'Animation ;
  les **Helpers** voient-ils `Logs` ; les **testeurs** sont-ils des joueurs ou du staff ; qui est **Fondateur /
  Co-Fondateur** (Administrateur), 1 ou 2 personnes.
- Grades Discord **synchronisés** un jour depuis le panel (`/role set`) ou à la main.

---

## 9. La suite

1. L'utilisateur active *Message Content Intent* (déjà fait a priori) et vérifie *Public Bot* désactivé.
2. Il invite le bot sur **REDLINE · RL** (URL B, Administrateur) et sur le(s) Discord **Reborn** (URL A, puis
   Administrateur temporaire).
3. Il lance `pnpm --filter @reborn/bot audit:guild` (liste et IDs), puis
   `pnpm --filter @reborn/bot audit:guild --guild <ID> --tout` sur le modèle et sur chaque cible.
4. Il retire Administrateur au bot (ou expulse le bot du serveur modèle) et relance `audit:guild` sans argument
   pour le vérifier.
5. Il transmet les dossiers `audit-…` (chemins) à Claude, qui lit `RAPPORT.md`, `messages\INDEX.md` puis les
   salons `hiérarchie`, `règlement-staff`, `transmission`, `décision`, `demote`, `absences`.
6. Claude propose le **plan détaillé** (arborescence, rôles, couleurs, matrice) et les **maquettes des
   bannières** → validation → application **en simulation** puis **par lots** (§6).

## 10. À tester (quand l'utilisateur est prêt)

1. Portail développeur : *Public Bot* désactivé, *Message Content Intent* activé.
2. Inviter le bot sur REDLINE · RL avec l'URL B : un rôle au nom du bot apparaît avec Administrateur.
3. `pnpm --filter @reborn/bot audit:guild` : REDLINE · RL apparaît avec « bot ADMIN » ; le serveur communautaire
   est marqué `DISCORD_GUILD_ID`.
4. `pnpm --filter @reborn/bot audit:guild --guild <ID REDLINE> --tout` : la console affiche une ligne
   `[lecture] <salon> : N message(s)` par salon ; un dossier `audit-…` contient `audit.json`, `RAPPORT.md` et
   `messages\` (avec `INDEX.md`).
5. Ouvrir `messages\INDEX.md` : `hiérarchie` et `règlement-staff` ont-ils des messages ? (0 = serveur créé
   depuis un modèle, voir §1.)
6. Retirer Administrateur au bot, relancer l'étape 3 : plus de « bot ADMIN ».
7. Refaire 2 à 6 sur le(s) Discord Reborn avec l'URL A, puis Administrateur temporaire.
8. Copier les dossiers d'audit dans un second endroit privé, puis donner leurs chemins à Claude.
9. **Application RBN - STAFF** (quand l'utilisateur décide) : monter le rôle du bot tout en haut, puis pour chaque
   lot `plan:guild` → relire `DIFF.md` → `apply:guild --confirm <empreinte> --lot <lot>` (ordre et commandes en
   fin de `DIFF.md`). Après chaque lot : `plan:guild` ne doit plus montrer ce lot ; un staff Création, un
   Invité(e) et un compte sans rôle vérifient ce qu'ils voient (comparer à `01_sidebar_salons.png`).
10. Avant le lot `permissions` : `| Fondateur` attribué (`--attribuer-fondateurs` ou à la main), sinon le script
    refuse ; après : Direction n'a plus Administrateur, les Fondateurs gardent tout.

---

## 11. Publier depuis le panel (annonces, dev updates, build updates)

> Ajouté le 14/10 (demande : « permettre de faire les messages dans annonces ou bien dev-updates depuis le panel
> staff »). Code écrit, **pas encore déployé** (train web) : rien n'a été posté sur Discord pendant le
> développement. Même rendu que `post-message.ts` (un cadre composants v2), partagé dans
> `apps/bot/src/messages/cadre.ts`. **16/10** : `build-updates` ajouté comme troisième salon (même cadre, aucune
> mention possible) ; déployé le 17/10.

### 11.1 Utilisation

Panel → **Discord** (menu de gauche), réservé aux comptes **ADMIN** et **OWNER** (le rang Reborn le plus proche de
Direction / Lead ; HELPER, MODERATOR, etc. reçoivent un 403).

1. Choisir le **salon** (Annonces / Dev updates / Build updates) et, si besoin, un **modèle** : « Dev update »
   (Ajouts / Ajustements / Fix / À tester), « Build update » (zone, galeries Avant / Après, avancement,
   auteurs), « Annonce », « Installation du launcher ».
2. Composer les **blocs** : texte (markdown Discord : `#`, `##`, `-#` petit texte, `**gras**`, listes,
   `{#salon}` et `{@rôle}` résolus par le bot, jamais notifiés), galerie (glisser-déposer, 10 images max par
   message, 8 Mo par image), séparateur, boutons-liens (5 max). L'aperçu de droite reproduit le client Discord
   (thème sombre, barre de couleur du cadre).
3. **Aperçu Discord** : poste le message dans le salon de test `DISCORD_STAFF_APERCU_CHANNEL_ID` (en-tête
   « Aperçu · non publié », le précédent aperçu du même message est remplacé). Sans ce salon, le bot vérifie
   seulement le rendu et ne poste rien.
4. **Publier** (confirmation) : le bot poste dans le salon. Option **Mentionner** désactivée par défaut :
   `@Testeur` en dev-updates, `@everyone` en annonces, aucune en build-updates ; c'est la seule façon de notifier.
5. **Historique** (bas de page) : auteur, date, salon, statut (Brouillon / Publié / Retiré), lien Discord.
   **Modifier** recharge le message ; sur un message publié, « Mettre à jour sur Discord » **réécrit le message
   sur place** (même lien, aucune notification). **Retirer** supprime le message de Discord et le garde dans
   l'historique ; **Republier** en poste un nouveau.

Le bot ne modifie et ne supprime **que ses propres messages** ; le salon d'un message publié ne change pas
(retirer puis republier).

### 11.2 Routes

API (`/v1`, JWT + `@MinRole(ADMIN)`, module `apps/api/src/staff-messages/`) :

| Méthode | Route | Rôle |
|---|---|---|
| GET | `/admin/discord/messages[?canal=annonces\|dev-updates\|build-updates]` | Historique (100 derniers) |
| GET | `/admin/discord/messages/:id` | Un message |
| POST | `/admin/discord/messages` | Crée un brouillon ; `action` = `brouillon` (défaut) · `apercu` · `publier` |
| PATCH | `/admin/discord/messages/:id` | Contenu complet ; publié + `publier` = réécriture du message Discord ; publié + `apercu` = aperçu sans enregistrer |
| DELETE | `/admin/discord/messages/:id` | Publié → retiré de Discord (gardé, statut Retiré) ; brouillon / retiré → effacé |
| POST | `/admin/discord/media` | Image (multipart `file`, 8 Mo, PNG / JPEG / WebP / GIF vérifiés aux octets) → `{ id, url }` |
| GET | `/admin/discord/media/:id?exp=&sig=` | Lecture par **URL signée** (HMAC, 1 h), sans JWT (balise `<img>`) |

Corps : `{ canal, couleur?: "#RRGGBB", blocs: [{ texte } | { images: [id] } | { separateur: true } | { liens:
[{ libelle, url, emoji? }] }], ping?: "aucun" | "testeurs" | "everyone", action? }`. Journal d'audit :
`discord.message.create`, `.update`, `.preview`, `.publish`, `.edit`, `.remove`, `.delete`.

Bot (`apps/bot/src/webhook-server.ts`, signé `X-Reborn-Signature` = HMAC-SHA256 du corps avec
`REBORN_WEBHOOK_SECRET`, comme les autres routes ; corps plafonné à 40 Mo) :

| Route | Corps | Réponse |
|---|---|---|
| `POST /webhooks/staff-message` | `{ canal, mode: "apercu" \| "publier", spec: { couleur, blocs }, images: [{ nom, base64 \| url }], ping?, cleRemplacement? }` | `{ messageId, channelId, url }` ; aperçu sans salon de test : `{ messageId: null, url: null, rendu }` |
| `POST /webhooks/staff-message/edit` | `{ canal, messageId, spec, images, ping? }` | `{ messageId, channelId, url }` |
| `POST /webhooks/staff-message/delete` | `{ canal: "annonces" \| "dev-updates" \| "build-updates" \| "apercu", messageId }` | `{ deleted, absent }` |

`cleRemplacement` = ID d'un message du bot du même salon, supprimé **après** l'envoi du nouveau (sert aux aperçus).
Erreurs renvoyées avec leur statut et affichées dans le panel : 400 contenu (limites Discord : 4000 caractères
de texte, 40 composants, 10 images, 5 boutons par rangée, libellé ≤ 80), 403 message d'un autre auteur, 404
message disparu, 503 serveur / salon non configuré.

### 11.3 Variables d'environnement (sans valeur dans le dépôt)

| Variable | Où | Rôle |
|---|---|---|
| `DISCORD_STAFF_GUILD_ID` | bot | Serveur « RBN - STAFF » ; vide = routes en 503 |
| `DISCORD_STAFF_ANNONCES_CHANNEL_ID` | bot | Salon `📣・annonces` |
| `DISCORD_STAFF_DEVUPDATES_CHANNEL_ID` | bot | Salon `🛰️・dev-updates` |
| `DISCORD_STAFF_BUILDUPDATES_CHANNEL_ID` | bot | Salon `🏗️・build-updates` (vide = ce salon en 503, les autres marchent) |
| `DISCORD_STAFF_APERCU_CHANNEL_ID` | bot, optionnel | Salon de test des aperçus (ex. privé Direction) |
| `DISCORD_STAFF_TESTEUR_ROLE_ID` | bot, optionnel | Rôle pingé ; vide = rôle nommé « Testeur » |
| `REBORN_STAFF_MEDIA_DIR` | API, optionnel | Dossier des images ; défaut `<REBORN_UPLOAD_DIR>/.staff-media` (dossier à point jamais servi par `/uploads`, persisté par le volume des uploads) |
| `REBORN_MEDIA_SIGNING_SECRET` | API, optionnel | Clé des URL signées ; défaut dérivée de `JWT_SECRET` |

Chaque salon doit appartenir au serveur staff (sinon 503). Le bot a besoin, dans ces salons, de **Voir**,
**Envoyer des messages**, **Joindre des fichiers**, **Intégrer des liens** et, pour `@everyone`, **Mentionner
@everyone** (le rôle `Testeur` doit être mentionnable ou le bot avoir cette permission).

### 11.4 Mise en prod (train web) — ✅ faite (17/10)

> Déploiement confirmé par l'utilisateur le 17/10 : panel « Discord » et salon `build-updates` en ligne. Étapes
> gardées pour mémoire (nouveau déploiement, autre VPS).

1. Base : `pnpm exec prisma migrate deploy` (migration `20261014200000_staff_discord_messages`, une table et deux
   enums ; jamais appliquée, aucune base locale ne tournait) puis `20261016100000_staff_discord_build_updates`
   (valeur `BUILD_UPDATES` ajoutée à l'enum).
2. `.env.prod` du VPS : les variables du §11.3 (IDs copiés depuis Discord, mode développeur) ;
   `infra/docker-compose.prod.yml` les passe au conteneur du bot.
3. Rebuild `api`, `bot`, `admin` depuis `origin/main`.
4. Recette : §11.5.

### 11.5 À tester

1. Panel en ADMIN → **Discord** : la page s'ouvre ; en MODERATOR, l'historique affiche « Rôle insuffisant ».
2. Modèle « Dev update », glisser 3 captures dans la galerie : vignettes, aperçu de droite en 3 images (une
   grande à gauche).
3. **Aperçu Discord** : message « Aperçu · non publié » dans le salon de test ; relancer → l'ancien disparaît.
4. **Publier** sans mention dans dev-updates : message identique à l'aperçu, personne notifié, lien dans
   l'historique.
5. **Modifier** ce message, changer une phrase, **Mettre à jour sur Discord** : même lien, texte changé.
6. **Retirer** : le message disparaît du salon, la ligne passe « Retiré » ; **Republier** en poste un nouveau.
7. Annonce avec « Mentionner @everyone » : la notification part une seule fois (pas à la modification).
8. Erreurs : bouton avec une URL sans `https://`, 11 images, texte > 4000 caractères → refus lisible.
9. Journal d'audit du panel : lignes `discord.message.*` avec l'auteur.
10. `pnpm --filter @reborn/bot post:message --guild <id> --fichier <…>.json` (sans `--envoyer`) : la simulation
    marche toujours (rendu partagé).
11. Salon **Build updates**, modèle « Build update » : deux galeries (Avant / Après), pas de case « Mentionner » ;
    publier → le cadre arrive dans `🏗️・build-updates` sous la bannière, filtre « Build updates » de l'historique.
