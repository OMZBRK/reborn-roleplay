# Candidature, douane et « Mes personnages »

> **Statut : proposition du 24/10, rien n'est codé.** Aucune maquette n'est faite. Relue et corrigée le 24/10
> (34 points : erreurs, contradictions, sécurité, manques). Treize questions attendent une réponse (§7).
>
> **Dépôt public** : les faiblesses de sécurité sont résumées ici (§3.12), sans mode d'emploi.
>
> **Demande du 24/10** ([`../pilotage/DECISIONS.md`](../pilotage/DECISIONS.md), dernière ligne) :
> - refaire la candidature du launcher « comme Ketto visuellement, ou mieux », avec ses onglets ;
> - corriger les bugs : le calendrier de l'âge, les textes longs ;
> - après la validation de l'écrit, passer l'oral **en jeu**, dans une douane façon Elderwood ;
> - relier la candidature à la création de personnage et à un écran « Mes personnages » ;
> - mettre le Discord dans le launcher et dans le client.
>
> **Références**
> - **Ketto** : `Maquettes/gameplan/candidature/ref_ketto/ketto_candidature_1.png` à `_7.png`. Images du launcher
>   Ketto récupérées par l'utilisateur sur Discord, depuis son autre PC. **Privées : ne pas les publier.**
> - **Elderwood** : la vidéo, découpée en `ref_elderwood/planche_01.jpg` à `planche_04.jpg` (images `img_01` à
>   `img_46`).
>
> **Ce que cette fiche remplace**
> - L'oral dans le vocal Discord : salons `⏳・Attente oral` et `🎙️・Oral 1/2`, étape 4 de
>   [`../operations/DISCORD_JOUEURS.md`](../operations/DISCORD_JOUEURS.md) §3.
> - L'ordre « HRP validé à l'oral, puis RP » (`apps/api/src/staff/staff.service.ts:26-47`).
> - Le remplacement côté Discord (salons, rôle `| Recrutement`, message `nous-rejoindre`) attend que la douane soit
>   en service : lot L (§6.1).
>
> **Hors périmètre**
> - **Rôles Discord** (décision du 24/10) : le rôle de base est `Membre`. Aucun rôle Discord n'est posé à
>   l'acceptation. Le rôle whitelist se verra depuis le launcher ; ce badge est à définir plus tard, pas ici.
>   [`DISCORD_JOUEURS.md`](../operations/DISCORD_JOUEURS.md) §3 étape 5, §5.3 et §7 C parlaient encore d'un
>   `| Whitelisté` posé par le bot : une note y renvoie ici.
> - **Candidatures de clan** et leur oral (décision du 19/10, DISCORD_JOUEURS §7 B) : ni modèle ni parcours ici.
>
> **Ce qu'elle complète** : [`../GAMEPLAN.md`](../GAMEPLAN.md) §1.8 (onboarding), §43 (création), §45 (sélection),
> §47 (launcher).
>
> **Vocabulaire**
> - La **Douane**, ici, est un **lieu en jeu**.
> - La décision du 19/10 « pas de Douane » visait une catégorie ou un serveur Discord. Elle reste valable.
>
> **Hors lecture** : l'écran actuel de la candidature (`apps/launcher/src/components/whitelist/`, `routes/`) n'a pas
> été relu. Une autre tâche le réécrit. Cette fiche lui sert de cahier des charges.
>
> **Lignes du launcher** : la copie de travail de `apps/launcher/` (24/10) diffère de `main` et elle est en cours de
> réécriture. Ses numéros de ligne bougent : on cite le nom de la fonction ou du symbole quand c'est possible.

---

## 0. En dix lignes

1. La candidature garde **deux parties**, comme chez Elderwood :
   - l'**HRP** : l'onglet Vous ;
   - le **RP** : les onglets Personnage, Faction, Histoire et Voix (Q2).

   Elle s'affiche en **six onglets** façon Ketto : **1 Vous · 2 Personnage · 3 Faction · 4 Histoire · 5 Voix ·
   6 Envoi**.
2. L'**âge** se saisit comme un **nombre**, plus avec un calendrier. Chaque texte a un **compteur** min / max et une
   zone qui s'agrandit. Un texte trop long **n'est jamais coupé** : il est signalé avant l'envoi.
3. **Enregistrement automatique** : sur le PC (existe) et **sur le serveur** (nouveau). La mention « Enregistré »
   s'affiche en haut à droite, comme chez Ketto. L'onglet Envoi sert de **relecture complète**.
4. Le staff tranche l'**écrit**, HRP et RP, au panel. Ensuite, le candidat **réserve une place dans une séance de
   douane** depuis le launcher. Les créneaux d'oral actuels (un candidat chacun) deviennent des **séances avec
   capacité** (§3.3).
5. Le jour J, le bouton « Jouer » devient **« Passer la douane »**. **Règle unique** : il est actif de 15 min avant
   à 30 min après l'heure de convocation (§3.3). Le jeton de jeu porte alors une **portée « douane »**.
6. Le candidat arrive dans le **hall** d'un monde dédié, sans personnage. Le hall **n'ouvre qu'avec un examinateur ou
   un membre du staff** présent. Le candidat **se déplace** et **parle** aux autres candidats, à la voix et au tchat,
   limités au hall. Une ligne discrète affiche « En attente · 3ᵉ ».
7. Un **examinateur**, en mode examinateur, au Poste de garde, onglet Douane, **appelle** le suivant. Le candidat est
   **téléporté dans un bureau**, isolé à la voix. L'examinateur a une **fiche résumée** du dossier et pose des
   **questions tirées au hasard**.
8. **Décision** : Accepter, À revoir ou Refuser, avec un motif obligatoire hors acceptation. Le serveur l'envoie à
   l'API, signée avec un secret propre au jeu. L'API passe le compte en `WHITELISTED` et prévient le launcher et le
   bot. Aucun rôle Discord n'est posé.
9. Si la décision est **Accepter** :
   - l'écran **« Bienvenue sur Reborn »** s'affiche ;
   - le joueur passe à la sélection ;
   - la **création de personnage est pré-remplie** : prénom, clan, village, sexe et âge. Les verrous sont contrôlés
     **côté serveur** (le prénom : Q13).
10. Les personnages sont **poussés vers l'API** à chaque changement. Le launcher les affiche dans **« Mes
    personnages »**. Le Discord officiel apparaît en **carte sur l'accueil** du launcher (membres, en ligne,
    Rejoindre) et dans le client : menu principal, Échap, Options.

---

## 1. Le parcours du joueur, de l'installation à « Bienvenue »

### 1.1 Les étapes

| # | Où | Ce que voit le joueur | Ce qui se passe | Aujourd'hui |
|---|---|---|---|---|
| 0 | Discord, site | `🚪・nous-rejoindre` : télécharger le launcher | — | ✅ message posté (version fermée) |
| 1 | Launcher | Carte « Bienvenue sur Reborn » : ① Microsoft, ② Discord | Compte Reborn créé, Discord lié | ✅ launcher 0.3.46 (GAMEPLAN §1.8) |
| 2 | Launcher › Candidature | Six onglets, « Enregistré » en haut à droite | Brouillon sur le PC et sur le serveur | ❌ trois étapes, brouillon sur le PC seulement |
| 3 | Launcher › Envoi | Relecture, règlement coché, « Envoyer ma candidature » | `POST /v1/whitelist`, fil staff, annonce du bot | ✅ envoi ; ❌ relecture complète |
| 4 | Launcher › Suivi | Frise « Écrit · Douane · Bienvenue », fil avec le staff | Le staff tranche HRP et RP au panel | ✅ fil et décision par partie ; ❌ frise |
| 5 | Launcher › Suivi | « Écrit accepté — réserve ta séance de douane » | Séances ouvertes par le staff, avec capacité | ✅ créneaux d'un candidat ; ❌ séances, réservation liée à l'écrit validé |
| 6 | Launcher › Accueil | Le jour J, « Passer la douane » à la place de « Jouer » | Jeton de jeu `scope:"douane"` | ❌ un compte `PLAYER` est bloqué |
| 7 | Jeu › Hall | Hall, autres candidats, ligne « Douane · en attente · 3ᵉ » | File d'attente en mémoire (ShinobiCore) ; hall ouvert seulement avec un examinateur ou un staff | ❌ |
| 8 | Jeu › Bureau | Fondu « Un examinateur t'appelle », arrivée au bureau | Téléportation, voix isolée | ❌ (`/ramener` existe) |
| 9 | Jeu › Bureau | Questions de l'examinateur, une ligne « Question » à l'écran | — | ❌ |
| 10 | Jeu | « Bienvenue sur Reborn », ou résultat puis retour au launcher | `POST /v1/game/douane/decision`, rôle `WHITELISTED` | ❌ |
| 11 | Jeu › Création | Village, clan, sexe et âge pré-remplis et verrouillés ; prénom pré-rempli (verrou : Q13) | Contrôle côté serveur dans `handleCreate` | ✍🏻 village et clan pré-remplis, verrouillés côté client seulement |
| 12 | Launcher › Mes personnages | N°, nom, village, grade, temps de jeu | `POST /v1/game/characters/sync` puis `GET /v1/me/characters` | ❌ |

### 1.2 Les phases, calculées par l'API

L'API calcule une `phase` et l'expose dans trois réponses :
- `GET /v1/whitelist/me` ;
- `GET /v1/admin/whitelist/:id` ;
- `GET /v1/game/acces/:mcUuid` (nouveau).

Le launcher et le serveur lisent cette phase **au lieu du rôle**.

| Phase | Condition | Le launcher affiche | Peut lancer le jeu | Le serveur fait |
|---|---|---|---|---|
| `BROUILLON` | Aucune candidature envoyée | Formulaire à six onglets | Non | — |
| `ECRIT_EN_ATTENTE` | HRP ou RP en attente, rien à revoir ni refusé | Frise, fil, délai de relecture annoncé (Q11) | Non | — |
| `ECRIT_A_REVOIR` | HRP ou RP à revoir | Commentaire du staff, **seuls les onglets visés sont rouverts** | Non | — |
| `DOUANE_A_RESERVER` | HRP et RP validés, oral en attente, aucune réservation à venir | « Écrit accepté — réserve ta séance de douane » | Non | — |
| `DOUANE_RESERVEE` | Même chose, avec une réservation dans une séance | Date, heure de convocation et compte à rebours ; « Passer la douane » dans la fenêtre (§3.3) | Oui, dans la fenêtre (portée `douane`) | Hall, confinement |
| `DOUANE_A_REVOIR` | Oral « à revoir » | Commentaire, nouvelle réservation possible | Non | — |
| `REFUSE` | HRP, RP ou oral refusé | Commentaire, « Nouvelle candidature possible le … » (délai 1 jour) | Non | — |
| `ACCEPTE` | Rôle `WHITELISTED` ou plus | Accueil normal | Oui (portée `play`) | Sélection de personnage |

**L'oral à la première whitelist seulement** (décision du 19/10) : si `User.oralPassedAt` est renseigné quand l'écrit
est validé, on passe directement à `ACCEPTE`.

### 1.3 Ce qui change par rapport à aujourd'hui

- **Ordre inversé**. Aujourd'hui, l'HRP « via test oral » est validé, puis le RP est examiné
  (`apps/api/prisma/schema.prisma:147-150`, `staff.service.ts:34`). Demain, `hrpStatus` et `rpStatus` sont les deux
  parties de l'**écrit**, et un nouveau `oralStatus` porte l'oral.
- **Promotion déplacée**. Le passage `PLAYER` → `WHITELISTED` (`staff.service.ts:271-277`) attend l'oral : HRP, RP
  et oral validés, ou `oralPassedAt` déjà renseigné.
- **Statut global : nouvel état « écrit accepté »**. Aujourd'hui, la dérivation (`staff.service.ts:232-246`) passe
  `status` à APPROVED dès que HRP et RP sont validés. Trois effets en dépendent :
  - le message « Félicitations… Bienvenue sur Reborn » (`staff.service.ts:36-37`) ;
  - le fil fermé aux messages (`whitelist-messages.service.ts:84` côté joueur, `:204` côté staff) ;
  - le retrait refusé (`whitelist.service.ts:157`).

  Demain, `status` ne passe à APPROVED **qu'après l'oral** (ou avec `oralPassedAt`). Quand l'écrit est validé,
  `status` reste PENDING, `writtenApprovedAt` est renseigné, et le message système devient « Écrit accepté. Réserve
  ta séance de douane dans le launcher. ». Le fil reste ouvert et le retrait reste possible jusqu'à l'oral.
- **Un candidat entre sur le serveur**. Aujourd'hui, c'est impossible à trois endroits :
  - le launcher (`apps/launcher/src-tauri/src/launcher/game.rs`, `launcher_launch_game`, test
    `user.role == "PLAYER"`) ;
  - l'API (`apps/api/src/play/play.service.ts:66-68`) ;
  - Guardian : sans jeton, le joueur est expulsé au bout de 8 s.

---

## 2. La candidature v2 (launcher)

### 2.1 Aujourd'hui

**Données** : `WhitelistApplication` (`schema.prisma:139-191`), une ligne par compte (`userId @unique`).
- Partie HRP : `dob`, `motivation`, `experience`, `availability`.
- Partie RP : `firstName`, `lastName` (en pratique le **clan**), `village`, `support`, `history`, `appearance`,
  `objectives`.
- Champs absents : faction, voix, sexe et âge du personnage, brouillon côté serveur.

**Âge** : la date de naissance est saisie avec un calendrier.
- L'API la vérifie seulement avec `@IsDateString` (`whitelist.dto.ts:18-21`). Aucune règle d'âge côté serveur : une
  date future passe.
- La règle des 16 ans n'existe que dans le launcher (`apps/launcher/src/lib/whitelist-validation.ts:36-43`).
  `calcAge` (`:7-16`) lit en heure locale une date analysée en UTC.

**Textes longs** : le launcher ne vérifie que les **minimums** (`whitelist-validation.ts:44-64`). Les maximums
n'existent que dans l'API (`whitelist.dto.ts:24-75`). Un texte trop long passe dans le formulaire, puis **tout
l'envoi** est refusé en 400.

**Ajouter un champ casse l'envoi**. Il faut toucher trois couches à la fois :
- le DTO, rejeté par `forbidNonWhitelisted` (`apps/api/src/main.ts:56-59`) ;
- la struct Rust `SubmitWhitelistRequest` (`src-tauri/src/content/mod.rs:259-273`), qui perd un champ inconnu sans
  rien dire ;
- `WhitelistApplicationDto` (`:214-246`).

**Brouillon** : il vit seulement dans le localStorage (`apps/launcher/src/stores/whitelist-store.ts:106-155`). Il est
perdu si l'on change de PC.

**« À revoir »** est affiché comme un refus (`whitelist-store.ts:10-12`).

### 2.2 Habillage : Ketto, ou mieux

La demande dit « comme Ketto **visuellement** ». On reprend la mise en page, pas les textes : accroche, aides et
segments sont écrits par nous. Seuls les libellés d'interface courants (« Continuer », « Étape précédente », « Il
reste à compléter », « Envoyer ma candidature », « Tester le micro ») restent communs.

Ce qu'on reprend des captures Ketto :
- titre de page « Ma candidature » et **onglets numérotés** dans la barre du haut, l'onglet actif souligné ;
- un grand titre, puis une ligne d'accroche courte : durée estimée, reprise possible plus tard (texte Reborn) ;
- « Enregistré » en petit, en haut à droite du contenu ;
- une colonne de formulaire de largeur fixe, environ 760 px, alignée à gauche ;
- l'étiquette au-dessus du champ, le compteur à droite de l'étiquette, l'aide en petit sous le champ ;
- des **choix en segments** (libellés Reborn, §2.3) ;
- pour l'histoire, une **barre de mise en forme** (Gras · Italique · Barré · Titre · Liste · Citation) et un minimum
  compté en **mots** (« 300 mots minimum », capture 5) ;
- les boutons « Étape précédente » (en mot) et **« Continuer »** (plaque rouge) ;
- dans l'onglet Envoi, « Il reste à compléter : » suivi de puces cliquables avec le nombre de champs manquants ;
- en pied de page, les aides clavier « A / E Changer d'onglet » et « Échap Retour ».

**Couleurs** : celles du launcher (`apps/launcher/src/styles/globals.css:13-49`), déjà très proches de Ketto :
- fond `#0a0608` ;
- CTA crimson `#b01c32` ;
- or `#e0b06a` pour les compteurs ;
- crème `#f7ecd6` pour le texte.

Ce qu'on fait **mieux que Ketto** :
- un onglet complet porte une **coche** (Ketto ne la montre que sur Faction) ; un onglet incomplet affiche son numéro
  et une pastille avec le nombre de manques ;
- le compteur montre **le minimum et le maximum**, passe à l'or près du maximum, puis au rouge au-delà, avec
  « 120 caractères de trop » ;
- la zone de texte **s'agrandit** avec le contenu, jusqu'à 60 % de la hauteur, puis défile ;
- l'onglet Faction montre les **vrais emblèmes** de village et de clan, ceux du mod (DA §1, « vrais logos »). Son
  contenu est une **proposition Reborn** : aucune capture Ketto ne montre l'intérieur de cet onglet ;
- la **relecture complète** est dans l'onglet Envoi (Elderwood : « une dernière relecture ») ;
- le brouillon est **enregistré sur le serveur** ;
- l'état « hors ligne » est explicite.

Les touches A et E ne changent d'onglet que **hors d'un champ de saisie**. Ctrl+PgUp et Ctrl+PgDn marchent partout.

### 2.3 Champs par onglet

*Colonne « Champ »* : nom en base. ✅ = existe déjà ; 🆕 = à créer.

**Onglet 1 — Vous (partie HRP)**

| Libellé | Champ | Saisie | Règle |
|---|---|---|---|
| Quel âge as-tu ? | 🆕 `playerAge` (entier) | Petit champ numérique (2 chiffres, flèches), **aucun calendrier** ; à droite « Entre 15 et 99 » (Q1) ; dessous « Reborn accueille les joueurs à partir de 15 ans. » | Obligatoire ; de `WHITELIST_AGE_MIN` (Q1) à 99 ; borné **par l'API**. L'âge est **déclaré** : rien ne le prouve. À l'oral, l'examinateur signale un doute |
| Depuis combien de temps fais-tu du roleplay ? | 🆕 `rpYears` | Segments : Jamais · Moins d'un an · 1 à 2 ans · 3 à 5 ans · 6 ans et plus (Elderwood : « tes années d'expérience RP ») | Obligatoire |
| Ton expérience du roleplay | ✅ `experience` | Zone extensible ; aide : « Où tu as joué, combien de temps, quels personnages. » | 150 à 4 000 caractères (inchangé) |
| Ta connaissance de l'univers Naruto | 🆕 `narutoKnowledge` | Quatre segments, libellés Reborn : Débutant · Quelques arcs · Bonne connaissance · Incollable (Boruto compris) | Obligatoire |
| Pourquoi Reborn ? | ✅ `motivation` | Zone extensible (« Pourquoi Ketto ? » chez Ketto) | 150 à 4 000 (inchangé, reste obligatoire) |
| Tes disponibilités | ✅ `availability` | Zone courte ; aide : « Soirs, week-ends… sert à placer ta douane. » | 20 à 2 000 (inchangé) |
| Comment as-tu découvert le serveur ? | 🆕 `discoverySource` | Liste : Pas de réponse · Discord · TikTok · YouTube · Un ami · Un autre serveur · Une recherche · Autre | Facultatif ; mesure les canaux de COMMUNICATION |
| Ton Discord | — (`User.discordUserId`) | Ligne en lecture seule : pseudo lié, coche | Lié à l'onboarding, rien à saisir |

**Onglet 2 — Personnage (partie RP)**

| Libellé | Champ | Saisie | Règle |
|---|---|---|---|
| Prénom | ✅ `firstName` | Champ simple ; aide : « Le nom de famille est ton clan (onglet Faction). » | **3 à 20** caractères : lettres, espace, apostrophe, tiret. C'est la règle du jeu (`PersoCreationScreen.java:176-181`), aujourd'hui de 1 à 64 |
| Sexe | 🆕 `characterSex` | Segments Homme · Femme (valeurs du mod) | Obligatoire |
| Âge du personnage | 🆕 `characterAge` | Champ numérique, avec la tranche affichée à côté : « Académie » (10-12), « Jeune genin » (13-16), « Adulte » (17-60) | **10 à 60**, bornes du jeu (`PersoCreationScreen.java:503`) |
| Caractère et apparence | ✅ `appearance` (renommé à l'écran) | Zone extensible ; aide : « Personnalité, manières, signes distinctifs. L'apparence exacte se choisit en jeu. » | 150 à 4 000 (inchangé) |
| Type de personnage (Ninja / Civil, Ketto) | — | **Pas de champ** pour la beta (Q3) | — |

**Onglet 3 — Faction (partie RP)**

| Libellé | Champ | Saisie | Règle |
|---|---|---|---|
| Ton village | ✅ `village` | Six cartes avec l'emblème : Konoha, Suna, Kiri, Kumo, Iwa, Ame. **Déserteur exclu**, comme en jeu | Une des six valeurs exactes de `CharacterCreateScreen.java:41-44` (« Konohagakure »…). Aujourd'hui, texte libre de 64 caractères |
| Ton clan | ✅ `lastName` | La grille dépend du village (Konoha : 11 clans ; Suna 2 ; Kiri 5 ; Kumo 1 ; Iwa 2 ; Ame aucun) et se termine par **« Sans clan »**. Les clans sur session portent un cadenas : « Sur candidature de clan, session annoncée sur Discord » (Q4) | Valeur **canonique ASCII**, la même que le mod (`CharacterCreateScreen.java:120-129` : « Hyuga », « Hozuki », « Onoki ») |
| Nom de famille (si « Sans clan ») | ✅ `lastName` | Champ libre | 3 à 20 lettres ; un nom de clan existant est refusé (règle du mod, `CharacterRules.isPredefinedClan`). En jeu, « Sans clan » correspond au clan **« Autre »** du mod (§4.2) |
| — | — | Ligne d'information : « Les organisations (ANBU, Akatsuki…) se rejoignent en jeu, jamais à la candidature. » ([`FACTION.md`](./FACTION.md) §3) | — |

La liste des villages et des clans, avec leur état libre ou sur session, vient d'un `GET /v1/whitelist/options`. Elle
n'est pas codée en dur dans le launcher.

**Contrainte** : deux listes doivent rester alignées, celle du mod (`CharacterCreateScreen.java:120-129`) et celle de
l'API. Un test devra le vérifier.

**Onglet 4 — Histoire (partie RP)**

| Libellé | Champ | Saisie | Règle |
|---|---|---|---|
| Histoire du personnage | ✅ `history` | Barre Markdown légère, comme Ketto (Gras · Italique · Barré · Titre · Liste · Citation), qui insère la syntaxe. Le compteur affiche les mots et les caractères, par exemple « 312 mots · 1 840 / 12 000 » (maximum proposé en Q10). Aide : « D'où vient ton personnage, qui l'a formé, ce qu'il cherche. » | Bornes à trancher (**Q10**) ; aujourd'hui 150 à 4 000 caractères. La correction des « textes longs », c'est le compteur et le texte jamais coupé (§2.4), pas un minimum plus haut |
| Objectifs roleplay | ✅ `objectives` | Zone extensible (Elderwood : « ses objectifs ») | 400 à 8 000 (inchangé) |
| Support visuel | ✅ `support` | Lien YouTube, Canva ou image | Facultatif, 500 caractères, `https://` exigé |

**Onglet 5 — Voix (partie RP, proposé : Q2)**

L'enregistrement présente le personnage : il relève de la **décision RP**. Une partie RP « à revoir » rouvre les
onglets Personnage, Faction, Histoire et Voix. Le test du micro n'est pas jugé : c'est un prérequis technique.

| Libellé | Champ | Saisie | Règle |
|---|---|---|---|
| Tester le micro | — | Bouton « Tester le micro » : vu-mètre, puis « Ton micro fonctionne » | Obligatoire avant d'enregistrer (la douane se passe à la voix) |
| Enregistrement vocal | 🆕 `voiceFile`, `voiceDurationSec` | « Fais entendre ton personnage : qui il est, d'où il vient. Une minute au plus. » Boutons Enregistrer · Arrêter · Réécouter · Refaire, et la durée 0:42 / 1:00 | **15 à 60 s** ; obligatoire ou non (Q2) ; un seul fichier, remplacé si l'on refait |

**Onglet 6 — Envoi** : voir §2.6.

### 2.4 Règles de validation et messages

| Cas | Où | Message |
|---|---|---|
| Âge vide | Sous le champ | « Indique ton âge. » |
| Âge hors bornes | Sous le champ, et refus de l'API (400) | « Reborn accueille les joueurs à partir de 15 ans. » / « Entre 15 et 99. » |
| Texte trop court | Compteur en or, puis message à la sortie du champ | « Encore 38 caractères (150 minimum). » |
| Texte trop long | Compteur rouge, onglet marqué incomplet ; le texte **n'est pas coupé** | « 120 caractères de trop (4 000 maximum). » |
| Prénom invalide | Sous le champ | « Lettres, espaces, apostrophes et tirets, de 3 à 20 caractères. » |
| Clan sur session choisi | Clic sur une carte à cadenas | « Ce clan s'obtient par une candidature de clan. Choisis-en un autre ou « Sans clan ». » |
| Micro refusé par le système | Onglet Voix | « Le launcher n'a pas accès au micro. Autorise-le dans Windows › Confidentialité › Microphone. » |
| Enregistrement trop court | Onglet Voix | « 15 secondes au moins. » |
| Envoi refusé par l'API | Bandeau dans l'onglet Envoi, avec renvoi au champ | Le message de l'API traduit, champ par champ ; **jamais** « Erreur 400 » seul |
| Hors ligne | Indicateur en haut à droite | « Hors ligne — gardé sur ce PC » |

**Mesure commune** : le launcher compte les caractères comme l'API (`String.length`, en UTF-16 ; un émoji compte
pour 2). Les deux affichages restent donc identiques. Le décompte des mots reste une indication : il ne bloque rien.

**L'API reste l'autorité**. Elle applique les mêmes bornes, y compris l'âge et les listes fermées de villages et de
clans.

### 2.5 Enregistrement automatique et brouillon sûr

**Sur le PC** : le store zustand persisté existe (`whitelist-store.ts:106-155`). On le garde.

**Sur le serveur** : nouveau modèle `CandidatureBrouillon { userId @unique, data Json, updatedAt }`, à part de
`WhitelistApplication`. Il ne touche donc ni aux statuts ni au `@unique`. Le nom `WhitelistDraft` est déjà pris par
le type TypeScript du launcher (`whitelist-store.ts`, `export type WhitelistDraft`) : on ne le réutilise pas côté API.
- Lecture : `GET /v1/whitelist/me/draft`. Écriture : `PUT /v1/whitelist/me/draft`, contrôle souple (aucun minimum),
  maximums appliqués, corps de 256 Ko au plus.
- L'écriture part 1,5 s après la dernière frappe, ainsi qu'au changement d'onglet et quand la fenêtre perd le focus.

**Indicateur** :
- « Enregistrement… » ;
- « Enregistré » (gris) ;
- « Hors ligne — gardé sur ce PC » (ambre) ;
- « Échec, nouvel essai dans 10 s ».

**À l'ouverture**, on garde la version la plus récente des deux (`updatedAt`). Si l'écart dépasse 1 heure, on demande
« Reprendre la version de ce PC ou celle du serveur ? ».

**Après l'envoi**, le brouillon serveur est effacé. Pour une candidature **à revoir**, il sert de zone d'édition des
seuls onglets rouverts.

### 2.6 Relecture et envoi (onglet 6)

1. **« Il reste à compléter »** : une puce par onglet incomplet, avec le nombre de manques (Ketto). Un clic mène au
   premier champ fautif.
2. **Relecture** : toutes les réponses en lecture seule, groupées en « Partie HRP » et « Partie RP », avec « Modifier »
   par section. L'enregistrement vocal se réécoute.
3. Case **« J'ai lu le règlement et ces réponses sont de moi »**, avec un lien vers le règlement → 🆕
   `rulesAcceptedAt`.
4. **Voix (RGPD)** : un court texte dit à quoi sert l'enregistrement, qui l'écoute (le staff de la whitelist
   seulement), combien de temps il est gardé (Q2), et qu'un retrait de la candidature l'efface. Case **« J'accepte
   que ma voix soit enregistrée pour ma candidature »** → 🆕 `voiceConsentAt`. Sans elle, pas d'envoi si la voix est
   obligatoire.
5. **« Envoyer ma candidature »**, grisé tant qu'il manque quelque chose (Ketto) → `POST /v1/whitelist`.
6. Ligne finale : « Les annonces de la whitelist passent par le Discord », suivie du bouton Discord (§5).

### 2.7 Après l'envoi : suivi, « à revoir », refus

**Frise en haut du suivi** : ① Écrit envoyé → ② Écrit relu → ③ Douane → ④ Bienvenue, avec la date de chaque étape.
En dessous, le **fil** existant (polling toutes les 5 s, `WhitelistMessage`).

**À revoir, écrit** : le commentaire du staff (`reviewNotes`) s'affiche en tête, et seuls les onglets visés sont
rouverts. Si seule la partie RP est à revoir, l'onglet Vous reste verrouillé ; la partie RP rouvre Personnage,
Faction, Histoire et Voix. C'est déjà la logique de `PATCH /me` (renvoi des seuls champs RP,
`whitelist.service.ts:112-127`).

**À revoir, oral** : le motif de l'examinateur (`oralReason`) s'affiche en tête ; aucun onglet ne se rouvre.

**Refusé** : le commentaire (de l'écrit ou de l'oral), puis « Nouvelle candidature possible le 25/10 à 21 h ». Le
bouton est grisé, avec un compte à rebours.

Le délai de 1 jour n'existe pas encore (DISCORD_JOUEURS §7 A). Il faut :
- `User.whitelistRejectedAt` ;
- `canReapplyAt` dans `GET /whitelist/me` ;
- un refus 409 dans `submit` et `resubmit` avant la fin du délai.

**Renvoi après l'oral**. Aujourd'hui, `PATCH /me` garde l'HRP s'il est approuvé et ne renvoie que le RP
(`whitelist.service.ts:112-127`). Après un oral :
- **refusé** : Q7 prévoit une nouvelle candidature écrite complète. `resubmit` remet alors HRP, RP et oral à
  PENDING, même si l'HRP était approuvé (la décision d'oral `REJECTED` le signale) ;
- **à revoir** : l'écrit est gardé tel quel, seul l'oral est à repasser (nouvelle réservation).

**Écrit accepté** : la carte « Réserve ta séance de douane » s'affiche, avec les séances et leurs places libres
(§3.3).

### 2.8 Ce qui disparaît, ce qui reste au staff

**Ce qui disparaît**
- La date de naissance et son calendrier, remplacés par l'âge en nombre. `dob` devient facultatif et reste lu pour
  les anciennes candidatures.
- L'assistant actuel en étapes, remplacé par les six onglets.
- « Pourquoi Ketto », remplacé par « Pourquoi Reborn ».
- « À revoir » affiché comme un refus (`whitelist-store.ts:10-12`).
- Le nom de famille en texte libre, remplacé par le clan choisi dans la liste, ou « Sans clan » avec un nom libre.

**Ce qui reste au staff** :
- les décisions **Écrit HRP**, **Écrit RP** et **Oral**, avec un commentaire visible du joueur ;
- le délai ou le blocage ;
- le clan sur session ;
- l'entrée dans une organisation ;
- le grade ;
- la remise à zéro (ADMIN, `staff.service.ts:170-211`).

**Rien n'est tranché automatiquement.**

### 2.9 Données : ce qui change dans l'API

**`WhitelistApplication`**
- 🆕 `playerAge Int?`, `rpYears`, `narutoKnowledge`, `discoverySource?`, `characterSex`, `characterAge Int?`,
  `voiceFile?`, `voiceDurationSec?`, `rulesAcceptedAt?`, `voiceConsentAt?`, `formVersion Int @default(1)`.
- 🆕 `oralStatus AppStatus @default(PENDING)`, `writtenApprovedAt?`, `oralDecidedAt?`, `oralExaminerUserId?` (relation
  `User`).
- 🆕 Trois textes séparés, pour ne plus rien écraser :
  - `reviewNotes` (existe) : commentaire de l'**écrit**, **visible du joueur** (`whitelist.service.ts:285`, renvoyé
    par `getMine`) ;
  - `writtenStaffNotes?` : note **privée** du staff sur l'écrit, transmise à l'examinateur (§3.7) ;
  - `oralReason?` : motif de l'oral, visible du joueur ; `oralNotes?` : notes privées de l'examinateur.
- 🆕 `characterId?` et `characterPlayerId?` : le personnage issu de la candidature (§4.4).
- `dob` devient facultatif.

**Transition des envois** (l'ancien launcher tourne encore quand A sort)
- Les nouveaux champs obligatoires, les listes fermées et le prénom de 3 à 20 caractères rejetteraient en 400 tous
  les envois de l'ancien launcher.
- Deux voies, au choix :
  - **A tolérant** : le launcher v2 envoie `formVersion: 2`. Sans elle, le DTO applique les anciennes règles (`dob`
    accepté, nouveaux champs facultatifs, texte libre) ;
  - **A et F1 livrés ensemble**, avec une version minimale du launcher imposée par l'auto-update.
- Reco : A tolérant. Il découple les trains web et launcher.

**Migration des dossiers en cours** (sinon ces joueurs repassent l'oral)
- Comptes déjà `WHITELISTED` ou plus : `oralPassedAt` = date de migration.
- Candidatures avec `hrpStatus` = APPROVED : selon l'ancien sens, l'oral est passé. Elles reçoivent `oralStatus` =
  APPROVED.
- Candidatures déjà APPROVED : `writtenApprovedAt` = `reviewedAt`.

**`User`** : 🆕 `oralPassedAt?` et `whitelistRejectedAt?`. Ils survivent au retrait (`DELETE /whitelist/me`), qui
supprime la ligne de candidature.

**Séances de douane** : un créneau d'aujourd'hui = un seul candidat (`WhitelistOralSlot.bookedByUserId`,
`schema.prisma:245-263`). Le panel ouvre des créneaux **consécutifs** (`oral-slots/page.tsx:23-28`, `:74-77`). Le
hall n'aurait donc qu'un ou deux candidats, loin du « grand hall » d'Elderwood (planche 03).
- 🆕 `DouaneSeance { startAt, endAt, capacite, kind (WHITELIST), statut, examinateurs (relation User) }`.
- 🆕 `DouaneReservation { seanceId, applicationId, userId, convocationAt, statut, presentAt?, calledAt? }`. Statuts :
  BOOKED, DONE, MISSED, CANCELLED.
- `kind` laisse la place aux candidatures de clan, **hors périmètre** (DISCORD_JOUEURS §7 B). Un joueur whitelisté
  arrive avec la portée `play` et son personnage : le faire venir au bureau demandera une convocation par
  l'examinateur, à concevoir avec ces candidatures.
- `WhitelistOralSlot` sert encore pendant la transition (§3.11), puis il est retiré.
- Les annulations par le staff **préviennent** le candidat : MP du bot, notification du launcher, message système.
- Dimensionnement : §3.3.

**Voix**
- `POST /v1/whitelist/me/voice` : JWT, multipart, `audio/webm` ou `audio/ogg`, 1 Mo au plus.
- La signature d'octets est vérifiée, comme dans `staff-messages/staff-media.config.ts:62`.
- Le fichier est rangé dans `<uploads>/.voix/`. Ce dossier n'est **jamais** servi par `express.static`
  (`main.ts:46-52`, précédent `.staff-media`).
- Écoute staff : `GET /v1/admin/whitelist/:id/voice` (WHITELIST_REVIEWER).
- Le fichier est **supprimé** avec la candidature (`DELETE /whitelist/me`) et à la fin de la durée de garde (Q2).
- Aujourd'hui, l'upload n'accepte que les images (`apps/api/src/upload/upload.config.ts:14-19`).

**Trois couches à mettre à jour ensemble** : le DTO, `SubmitWhitelistRequest` et `WhitelistApplicationDto`
(`content/mod.rs`). Il faut aussi les nouvelles commandes Tauri (brouillon, voix, options), déclarées avec leur
**chemin complet** dans `generate_handler!` (CLAUDE.md).

**Micro dans le launcher**
- La CSP est à `null` (`src-tauri/tauri.conf.json:31`) : rien ne bloque `getUserMedia`.
- **À vérifier** : le comportement de WebView2 dans Tauri 2 sur la demande de permission (invite ou refus d'office).
  Un gestionnaire Rust de la demande sera peut-être nécessaire.
- MediaRecorder en Opus : environ 250 à 500 Ko par minute. Les octets passent par une commande Tauri, jamais par un
  appel HTTP depuis la WebView.
- Ils partent en **corps brut** (Tauri 2 : `invoke` avec un `Uint8Array`, lu côté Rust par `tauri::ipc::Request`,
  `InvokeBody::Raw`). Pas en tableau JSON de nombres, qui multiplierait la taille par trois ou quatre.

**Ancienne décision globale**, celle des boutons du bot et de `/whitelist accept` (`staff.service.ts:79-160`)
- Elle n'écrit que `status`.
- Elle donne `WHITELISTED` sans condition (`:109-115`) : elle **rétrograde un staff** et laisse `hrpStatus` à
  PENDING.
- **Correction** : la faire passer par la décision par partie, avec l'acteur transmis.

---

## 3. La Douane en jeu

### 3.1 Aujourd'hui

**Accès** : un compte `PLAYER` n'obtient pas de jeton de jeu (`play.service.ts:66-68`), et le launcher le bloque
avant même de le demander (`game.rs`, `launcher_launch_game`).
- Le jeton ne contient que `{sub, mcUuid, mcUsername, iat, exp}` (`play.service.ts:20-26`), sans portée.
- `LAUNCHER_BETA_GATE` (`apps/api/src/auth/auth.service.ts:124-142`) refuse même la **connexion** au launcher d'un
  compte `PLAYER`. Tant que cette porte est active, elle bloque **la candidature elle-même**, pas seulement la douane
  (§3.10, porte beta).
- Il n'y a **qu'une seule API** : elle sert la prod, le dev et le build (ETAT §3). Le jeton ne désigne aucun serveur.

**À la connexion**, tout joueur part en sélection de personnage (`CharacterLifecycleListener.java:40-66`, appel en
`:52`). Il est téléporté à l'ancre (`world`, 0.5 / 100 / 0.5, `config.yml:836-847`) et **caché de tous**
(`CharacterSelectManager.java:144-145`).

**Ce qui existe et se réutilise**
- Le cadre `HORS_JEU` (`EtatServiceImpl.java:213-225`, `etats.yml:44`) : il laisse `PARLER` et `VOCAL`. Une variante
  s'ajoute par une ligne (`cadre.HORS_JEU.CINEMATIQUE`, `etats.yml:39`).
- `/ramener` et `/retour` (`OutilsStaff.java:123-138`, grade Modérateur).
- Le filtre de tchat par distance du KO (`KoListener.java:425-434`).
- L'addon voix `ShinobiRadio` (`VoixRadio.java`).
- Le verdict de mise à mort du Poste de garde (`StaffPanel.java:214-222`, mod `StaffScreen.java:565-572`), modèle
  d'une décision avec motif.

### 3.2 Le lieu

**Un monde dédié `douane`, vide** (Q6), chargé par ShinobiCore au démarrage (`WorldCreator`, clé `douane.monde`).
Multiverse n'est attesté que sur le serveur de build.
- **Générateur vide** : un `ChunkGenerator` qui ne génère rien, pour que le monde ne se remplisse pas de terrain.
- **Copie** : le monde est construit sur le dev, puis copié vers la prod (dossier du monde, par SFTP ou le panel
  Minestrator), avec les positions de la config (lot I).
- **Accès staff** : 🆕 `/douane aller` (examinateurs et staff) mène au hall sans passer par la file.

**Pourquoi un monde à part**
- **Pas dans `world`** : la sélection y vit. Si Plasmo Voice ignore `hideEntity`, les voix fuiraient vers les joueurs
  en sélection.
- **Pas dans `PaysDuFeu06`** (`places.yml:20`) : le monde RP.

**Contenu** : un **hall** et **deux à quatre bureaux**. Chaque bureau est écarté du hall et des autres d'au moins
**deux fois la portée vocale maximale** de Plasmo. Si la portée est de 32 blocs, on prend 100 blocs.
- Décor proposé : un **poste-frontière neutre**, ni d'un village ni d'un autre (bois, lanternes, registres, un
  panneau du règlement comme dans le hall d'Elderwood), **sans kanji** (DA §3).
- Construit par l'équipe. Les meubles et objets 3D sont modélisés par l'utilisateur ; nous ne livrons que des
  identifiants stables et la liste à modéliser.

**Configuration** (`config.yml`, section 🆕 `douane:`) :
- `enabled` ;
- `monde` ;
- `hall` : point d'arrivée, et zone (deux coins) ;
- `bureaux` : identifiant, point du candidat, point de l'examinateur, zone ;
- `questions` (§3.7) ;
- les délais.

### 3.3 Entrer dans la douane

**Séances avec capacité**
- Le staff ouvre une **séance** au panel : début, fin, capacité, examinateurs prévus. Le candidat y réserve une
  **place**, pas un créneau à lui seul.
- **Dimensionnement** : un oral dure 10 à 15 min, soit **4 à 6 candidats par heure et par examinateur**. Capacité
  proposée : examinateurs × heures × 4, avec de la marge pour les retards. Exemple : 2 examinateurs pendant 2 h, 16
  places. Le hall se remplit alors comme chez Elderwood.
- **Convocation** : chaque réservation porte une heure de convocation, `convocationAt`. Par défaut, c'est le début
  de la séance. Pour une séance de plus d'une heure, le panel échelonne les convocations par vagues de 30 min, au
  rythme des examinateurs. L'attente au hall reste ainsi autour de 30 min au plus.

**Fenêtre d'entrée : la règle unique**
- Soit H l'heure de convocation. La connexion « douane » est permise **de H − 15 min à H + 30 min**. Le retard
  maximal de 30 min est réglable côté API.
- Une fois admis, le candidat reste jusqu'à son passage, même après H + 30.
- Après une coupure, il peut revenir dans les 10 min, même hors fenêtre, si son arrivée a été enregistrée (lot
  présence, §3.6). Sans le lot présence, seule la fenêtre compte.
- Cette règle vaut partout : bouton du launcher, jeton de l'API, cas limites (§3.10), Q5.

**Réserver**
- `book` (`oral-slots.service.ts:107-139`) n'accepte plus que les phases `DOUANE_A_RESERVER` et `DOUANE_A_REVOIR`.
  Aujourd'hui, toute candidature peut réserver.
- La réservation est liée à la candidature (`applicationId`), dans la limite de la capacité.
- **Rappel 1 h avant** (`@nestjs/schedule` est déjà une dépendance de l'API) :
  - MP du bot (webhook `directMessage` existant). Il n'arrive que si le candidat est sur le Discord du bot ;
  - **notification du launcher**, s'il est ouvert. Les permissions `notification:*` sont déjà accordées
    (`src-tauri/capabilities/default.json:16-19`).

**Jeton**. Dans `issuePlayToken`, la règle devient :
- compte banni : 403 ;
- rôle `WHITELISTED` ou plus : `scope:"play"` ;
- compte `PLAYER` en phase `DOUANE_RESERVEE`, dans la fenêtre ci-dessus, et `REBORN_DOUANE_ENABLED` actif :
  `scope:"douane"` ;
- sinon : 403, avec un message selon la phase (« Ta séance commence à 21 h »).

Le jeton porte aussi 🆕 `aud`, l'identifiant du serveur visé. Guardian refuse un jeton qui n'est pas pour lui
(§3.12).

**Launcher**
- `game.rs` (`launcher_launch_game`) et `PlayButton.tsx` (`isWhitelistGated`) lisent la **phase** au lieu du rôle.
  `isWhitelistGated` est en ligne 36 sur `main`, en ligne 60 dans la copie de travail du 24/10.
- Le bouton affiche « Passer la douane » avec l'heure. En dehors de la fenêtre, il est grisé avec « Ta séance :
  samedi 21 h ».
- La seconde instance (`game.rs`, `launcher_launch_second_instance`) reste réservée aux comptes non `PLAYER`.

**Guardian**
- Lire `scope` et `aud` dans le record `TokenPayload` (`PlayTokenVerifier.java:113-119`). Comparer `aud` à une 🆕 clé
  `server-id` de sa config.
- Après `markAuthenticated` (`AuthChannelListener.java:73`), sur le thread principal, poser une **métadonnée
  transitoire** : `Player#setMetadata("reborn.scope", "play" | "douane")`.
- **Pas une permission** : une permission non déclarée est accordée aux op, et le joker `*` de LuckPerms l'accorde
  aussi. Des membres du staff se retrouveraient enfermés au hall.
- Guardian est un plugin Paper isolé : ShinobiCore ne peut pas importer ses classes. Une métadonnée Bukkit se lit
  d'un plugin à l'autre.

**ShinobiCore : l'aiguillage**
- Au pré-login (`AsyncPlayerPreLoginEvent`), il interroge `GET /v1/game/acces/:mcUuid`. Cet événement est
  asynchrone, un appel bloquant y est permis, et aucun plugin ne l'utilise encore. **Délai HTTP court : 2 à 3 s.**
- Réponse : `{acces: 'joueur'|'douane'|'refuse', phase, staff, candidature: {...}, seance: {reservationId,
  convocationAt}}`. Garde : la nouvelle signature (§3.12), pas `GameHmacGuard`.
- **Contrainte de temps** : l'aiguillage a lieu au tick qui suit la connexion (`CharacterLifecycleListener.java:51`).
  L'attestation de Guardian peut arriver jusqu'à 8 s plus tard (`PendingAuthListener.java:32`). Sa métadonnée
  n'existe donc pas encore à ce moment-là.
- Dans `CharacterLifecycleListener`, **avant** `beginSelection` (`:52`) :
  1. réponse du pré-login reçue : `joueur` → sélection ; `douane` → `douane().admettre(p)` ; `refuse` → expulsion ;
  2. pas de réponse (API muette) : **état d'attente neutre**. Écran opaque « Vérification de l'accès… », joueur
     immobile, rien ne s'ouvre. L'aiguillage se fait dès que la métadonnée de Guardian arrive ;
  3. aucune preuve au bout de 10 s : expulsion, « Accès non vérifié. Réessaie dans un instant. ».
- **Refus par défaut** : la sélection n'ouvre que sur une **preuve positive** de `play` (réponse `joueur` de l'API, ou
  métadonnée `play`). Une preuve « douane » ne mène qu'au hall. Si les deux sources divergent, la plus restrictive
  gagne.
- Le serveur n'envoie pas de liste de personnages au mod. Aucun écran de sélection ne s'ouvre donc
  (`RebornHudClient.java:215-223`).

### 3.4 Dans le hall

À l'arrivée, `admettre` fait ceci :
- téléportation au hall, avec un fondu ;
- mode Aventure ;
- aucun dégât ;
- faim figée ;
- inventaire vanilla vide ;
- le **skin Minecraft**, et le **pseudo** sur la plaque : sans personnage, la plaque retombe sur le pseudo Minecraft
  (`CharacterDisplay.reset`). Elderwood montre les pseudos.

**Le tchat, lui, n'affiche pas le pseudo**. `CharacterNicknameListener.prefixFor` (`:83-90`) écrit « Inconnu » pour
tout joueur sans personnage. Dans le hall, tous les candidats seraient donc « Inconnu ». Il faut une exception
« douane » dans `prefixFor` : le pseudo Minecraft (reco, comme la plaque), ou « Candidat ».

**Le hall n'ouvre qu'avec un encadrant**. Des joueurs dès 15 ans y parlent à la voix. Le hall n'est donc
ouvert que si un examinateur ou un membre du staff est connecté dans le monde `douane`. Sinon :
- les candidats restent dans l'**état d'attente neutre** (§3.3) : écran opaque, immobiles, tchat coupé, voix coupée
  (mute Plasmo côté serveur, à vérifier, §3.5) ;
- la ligne HUD dit « DOUANE · Ouverture dès l'arrivée d'un examinateur » ;
- le hall s'ouvre pour tous dès qu'un examinateur ou un staff entre dans le monde.

| Le candidat peut | Le candidat ne peut pas |
|---|---|
| Se déplacer, sauter, dans la zone du hall (renvoi au point d'arrivée s'il en sort) | Poser ou casser des blocs, ramasser, frapper |
| Parler à la voix (proximité Plasmo) | Ouvrir la sélection : `open` est refusé (`CharacterSelectManager.java:343`) ; `create` aussi |
| Écrire au tchat, **lu seulement des occupants de la douane et du staff** | Lire le tchat du monde RP |
| Lire le panneau du règlement | Utiliser les commandes, sauf `/douane` (son statut) ; filtre `PlayerCommandPreprocessEvent` |
| Voir les autres candidats (pas de `hideEntity`) | Radio, techniques, course ninja, emotes de personnage, sacoche (tout exige un personnage) |

**État**
- 🆕 `cadre.HORS_JEU.DOUANE` dans `etats.yml`, placé **avant** la ligne 44.
- Il reprend les refus de `HORS_JEU` mais rend `DEPLACEMENT` et `VISIBLE`.
- `EtatServiceImpl.cadre()` renvoie `{HORS_JEU, "DOUANE"}` avant le test de la ligne 218.

**Tchat** : un écouteur en priorité HIGH, sur le modèle du KO (`KoListener.java:425-434`), filtre `viewers()` par
monde. `CharacterNicknameListener` (`:52-53`, HIGHEST) rediffuse ensuite à ces seuls destinataires.

**HUD du candidat** (DA §6.9 : subtil, petit), envoyé par le serveur sur 🆕 `reborn:douane` :
- une seule ligne fine en haut, Inter 7 sur un voile léger, filet d'or d'1 px ;
- le texte : « DOUANE · En attente · 3ᵉ » ;
- hall fermé, faute d'examinateur ou de staff : « DOUANE · Ouverture dès l'arrivée d'un examinateur ».

### 3.5 La voix

**Hall** : la proximité Plasmo Voice normale. C'est voulu : les candidats « discutent avec les autres » (Elderwood).

**Bureau** : isolé par la **distance** (§3.2), sans code. C'est la solution de base.

**Isolement strict, en option** : un addon dans `ShinobiRadio`, sur le modèle de `VoixRadio` (`:70-91`, `:209-219`).
Une source n'est diffusée qu'à l'examinateur et au candidat du bureau.

**À vérifier sur le serveur** (la configuration Plasmo n'est pas dans le dépôt) :
- la portée maximale ;
- que la proximité s'arrête au monde du joueur ;
- que Plasmo ignore ou non `hideEntity` et le vanish ;
- le mute staff de Plasmo, pour un candidat qui trouble le hall ;
- qu'un plugin peut couper la voix d'un joueur côté serveur (hall fermé, §3.4).

**L'examinateur doit être vu et entendu : le « mode examinateur »**
- En mode staff, il est invisible (`VanishManager.java:68-82`) et son tchat part au staff (`ModeStaff.java:329-345`).
- Hors du mode staff, avec un personnage actif, il passe dans le cadre MONDE (`EtatServiceImpl.cadre()`) : mécaniques
  RP actives et nom RP au tchat, en plein monde de la douane. Ce n'est pas acceptable non plus.
- 🆕 **Mode examinateur**, sur le modèle du `/modeanim` du 14/10 (`ModeAnimation.java`) :
  - visible des candidats, plaque « Examinateur » (Elderwood : « Douanier ») ;
  - aucune mécanique RP (cadre `HORS_JEU.DOUANE`, comme les candidats) ;
  - **tout est journalisé** : entrée et sortie du mode, appels, questions montrées, décisions ;
  - exclusif avec le mode staff et le mode animation ; tombe à la déconnexion.
- Il exige le grade **Helper au minimum** (§3.8).

### 3.6 File d'attente et appel

**File en mémoire** dans ShinobiCore, triée par heure de convocation puis par heure d'arrivée. Seule la décision
est persistée.

**Présence** (lot facultatif) : `POST /v1/game/douane/presence {mcUuid, evenement: arrivee|appel|depart, ts}`. Il
remplit `presentAt` et `calledAt` de la réservation, et sert à deux choses :
- le panel voit la file en direct ;
- le bot alerte `📥・candidatures` quand des candidats attendent sans examinateur en jeu.

L'API ne peut pas joindre le serveur de jeu (aucun port entrant, `files/commands.controller.ts:6-11`). Tout passe donc
par des appels sortants du serveur.

**Appeler**
- L'examinateur clique « Appeler » sur une ligne, ou « Appeler le suivant ».
- Le bureau choisi est celui où il se trouve, sinon le premier bureau libre. Il y est téléporté aussi.
- Chez le candidat :
  - la ligne « Un examinateur t'appelle » s'affiche ;
  - un fondu de 0,5 s ;
  - il est téléporté au point candidat du bureau (repris de `OutilsStaff.memoriser` et `ramener`) ;
  - la zone autorisée devient le bureau.
- **Renvoyer** remet le candidat au hall, en tête de file.

**Un bureau = un examinateur + un candidat.** Plusieurs bureaux tournent en même temps.

### 3.7 Au bureau : les questions

**Une fiche résumée, pas le dossier entier**. Lire environ 40 000 caractères en jeu pendant un oral de 10 à 15 min
n'est pas réaliste. L'examinateur voit, dans le **volet élargi** du Poste de garde (`GardeScreen`, `large()`, `:93`,
640 GUI au plus) :
- l'identité : pseudo, âge déclaré, années de RP, connaissance de l'univers ;
- le personnage : prénom, sexe, âge, village, clan ;
- les trois premières lignes de chaque texte long, repliables ;
- la **note privée du staff sur l'écrit** (`writtenStaffNotes`, §2.9) : les points à creuser ;
- « Voix : à réécouter dans le panel » ;
- **« Ouvrir dans le panel »** : le mod ouvre la fiche web de la candidature dans le navigateur, pour tout lire.

**D'où vient la fiche** : le serveur la demande à l'API par 🆕 `GET /v1/game/douane/dossier/:mcUuid`. Ce sont des
données personnelles : la route a son **propre garde signé** (méthode, chemin, heure et corps, §3.12). Ni
`GameHmacGuard` ni `HmacSignatureGuard` ne conviennent.
- Taille : quelques Ko. Le serveur vers le client tient dans un seul paquet.
- Du client vers le serveur, on n'envoie que la décision, le motif et les notes (environ 32 Kio de limite).

**Banque de questions**
- Liste configurable : `douane.questions` dans la config, ou plus tard dans le panel.
- **Grande banque, tirage au hasard**, surtout des **mises en situation** (« Ton personnage voit… que fait-il ? »)
  plutôt que des questions de règlement à réciter. Les candidats déjà passés racontent les questions au hall : une
  petite liste fixe serait vite connue.
- Le tirage évite les questions déjà montrées à ce candidat. Un candidat **renvoyé** au hall, qui a pu entendre les
  autres, reçoit d'autres questions à son retour.
- L'examinateur peut **« Montrer la question »** : elle s'affiche en une ligne en haut de l'écran du candidat
  (« Question · Que fais-tu si… »).
- C'est mieux qu'Elderwood, où la question n'est que parlée ; à la planche 04, le candidat lève une ardoise pour
  répondre.

**Réponse** : à la voix.

**Notes privées** : un champ libre, envoyé avec la décision → `oralNotes`, jamais montré au joueur. Le motif visible
va dans `oralReason`, séparé du commentaire de l'écrit (§2.9).

### 3.8 Outils de l'examinateur (Poste de garde)

**Un 5ᵉ onglet « Douane »** dans le volet v3, à côté d'Alertes · Joueurs · Journal · Outils.
- Il porte le compteur des candidats en attente.
- Il n'est visible qu'avec un grade staff **et** la permission 🆕 `reborn.douane.examinateur`, et seulement quand
  `douane.enabled` est actif (droits : voir plus bas).
- Habillage : celui du volet (DA §4, Poste de garde v3) : mots plutôt que boutons, icônes au trait, aucun kanji.

**La file** : une ligne de 23 GUI par candidat.
- Pseudo, minutes d'attente, heure de convocation.
- Village et clan demandés.
- Une marque « 2ᵉ passage » pour un candidat à revoir.
- Au survol : Appeler · Dossier · Renvoyer · Retirer de la douane.

**L'alerte** : 🆕 type `douane`, « un candidat est arrivé », dans la liste des alertes existante. Un type inconnu
s'y affiche déjà en générique (`StaffScreen.java:440-449`).

**La décision** : dans le volet (`GardeFormulaire`), trois mots : **Accepter** · À revoir · Refuser.
- Le motif est obligatoire pour À revoir et Refuser. **Il est visible du joueur.**
- Les notes privées sont facultatives.
- Il faut monter la limite actuelle de **200 caractères** (`GardeFormulaire.java:52`) à 2 000, en zone de plusieurs
  lignes.

**Droits : deux mondes, à régler à la main, ensemble**
- **En jeu** : les droits viennent des **groupes LuckPerms** (`StaffGrades.java:6-8`, `:16-23`). Rien ne les
  synchronise avec le panel web ni avec Discord. `| Recrutement` est un **rôle Discord** : il ne donne rien en jeu.
  - Le Poste de garde ignore tout joueur sans grade `reborn.staff.*` (`StaffPanel.java:148-149`). La seule
    permission `reborn.douane.examinateur` ne suffit donc pas.
  - Il faut un groupe LuckPerms « examinateur » : `reborn.staff.helper` au minimum, plus
    `reborn.douane.examinateur`.
- **Sur le web** : le rôle `WHITELIST_REVIEWER` ou plus, avec un `User.minecraftUuid` égal au compte utilisé en
  jeu. Un staff qui passe la douane avec un second compte Minecraft est refusé par l'API.
- Côté API : la décision est **revérifiée** (§3.9).

### 3.9 Décision et retour à l'API

**`POST /v1/game/douane/decision`**
- Signée avec un 🆕 **secret propre au jeu**, `REBORN_GAME_SECRET`, **différent pour chaque serveur**. Pas
  `REBORN_WEBHOOK_SECRET` : ce secret sert aussi au bot et aux routes staff. ShinobiCore le lit déjà
  (`CandidatureClient.java:59-63`, `secret()`), depuis l'environnement ou en clair dans `config.yml`, clé
  `reborn.webhook-secret`. Il vit donc chez l'hébergeur du jeu.
- Nouveau garde (§3.12) : signature sur méthode, chemin, heure et corps brut ; heure refusée au-delà de 60 s ; nonce à
  usage unique ; limite de débit ; chaque appel à l'audit.
- Corps : `{candidatMcUuid, examinateurMcUuid, decision: APPROVED|NEEDS_REVISION|REJECTED, motif, notes, ts, nonce}`.
- Le contrôle de l'examinateur par son uuid ne protège pas d'une fuite du secret : les uuid Minecraft sont publics.
  Toute la sécurité de cette route repose sur le secret du serveur.

**Ce que fait l'API, dans l'ordre**
1. Elle retrouve l'examinateur par `User.minecraftUuid` (`schema.prisma:20`) et exige un rôle web
   **WHITELIST_REVIEWER ou plus** (§3.8).
2. Elle vérifie que la phase vaut `DOUANE_RESERVEE`. L'appel devient ainsi idempotent.
3. Elle écrit `oralStatus`, `oralDecidedAt`, `oralExaminerUserId`, `oralReason` (le motif, visible) et `oralNotes`
   (privées). Elle **ne touche pas** `reviewNotes`, le commentaire de l'écrit (§2.9). La réservation passe à `DONE`.
4. Si l'oral est accepté :
   - `User.oralPassedAt` ;
   - le statut global passe à APPROVED (§1.3) ;
   - promotion par `updateMany where role PLAYER`, jamais un staff.
5. Si l'oral est refusé : `User.whitelistRejectedAt`, et le prochain renvoi repart de zéro (§2.7).
6. Elle poste un message système dans le fil (`whitelistMessages.postSystemMessage`).
7. Elle envoie `webhooks.statusUpdate` au bot (MP). **Aucun rôle Discord** n'est posé (décision du 24/10).
8. Elle écrit l'AuditLog avec `actorId` = l'examinateur et `source: "game"`.
9. Elle répond `{ok, phase}`.

**Côté jeu, après la réponse**
- **Accepté**
  - L'écran 🆕 **« Bienvenue sur Reborn »** s'affiche : écran opaque, nuit, titre or et filet, 3 s. C'est la fin de la
    vidéo Elderwood. La règle DA de la création s'applique : jamais le monde du jeu derrière.
  - Le confinement est levé.
  - La marque « douane » posée par Guardian (`reborn.scope`) est **effacée** et remplacée par `play`. Sinon, elle
    reste jusqu'à la déconnexion, et tout nouveau contrôle remettrait le joueur au hall.
  - La candidature est **relue** : le cache `candidatures` (`CharacterSelectManager.java:50`) contient la réponse du
    pré-login, d'avant l'oral. Il faut la recharger avant `beginSelection`.
  - Le joueur est téléporté à l'ancre de sélection, puis `beginSelection` est appelé, avec le bloc candidature.
  - **Pas besoin de se reconnecter** : le jeton n'est vérifié qu'à la connexion.
- **À revoir ou refusé** : un écran court s'affiche, « La douane n'est pas validée aujourd'hui. Le commentaire de
  l'examinateur est dans ton launcher. ». Puis le joueur est expulsé au bout de 8 s, avec le même texte.
- **API injoignable** : la décision part dans une file locale et est renvoyée toutes les 30 s.
  - L'examinateur voit « Décision en attente d'envoi ».
  - Le candidat reste au bureau, ou est expulsé avec « Résultat sous peu dans le launcher ».
  - Il n'est **jamais** promu sans réponse de l'API.

### 3.10 Cas limites

| Cas | Règle |
|---|---|
| Déconnexion dans le hall | Sortie de la file. Reconnexion dans les 10 min : il reprend sa place (fenêtre d'entrée, §3.3) |
| Déconnexion au bureau | L'examinateur est prévenu (« … s'est déconnecté »). Reconnexion dans les 10 min : retour au bureau ; sinon, fin de file |
| Examinateur déconnecté | Le candidat revient au hall, **en tête** de file. S'il ne reste aucun examinateur ni staff, le hall se ferme (§3.4) |
| Retard | Entrée possible jusqu'à H + 30 min, H = heure de convocation (règle unique, §3.3). Au-delà, refus « Séance manquée » et réservation `MISSED`, visible au panel. Le candidat peut réserver de nouveau. Limite de séances manquées : **Q12** |
| Aucun examinateur ni staff | Hall fermé : attente neutre, voix et tchat coupés (§3.4). À 20 min de l'heure de convocation sans examinateur : « Séance reportée », réservation libérée sans pénalité, alerte au bot (lot présence) |
| Plusieurs bureaux | Chaque bureau a un état libre ou occupé. « Appeler » prend le bureau de l'examinateur, sinon le premier libre. Deux examinateurs ne peuvent pas appeler le même candidat (verrou en mémoire) |
| Candidat qui trouble le hall | Mute Plasmo (staff), puis « Retirer de la douane » : expulsion, réservation `CANCELLED` avec motif, réservation suivante possible |
| Redémarrage du serveur | La file en mémoire est perdue. À la reconnexion, les candidats sont replacés selon leur heure de convocation |
| Ancien Guardian ou ancien ShinobiCore | **Refus par défaut** (§3.12) : sans preuve positive de `play`, pas de sélection. L'ordre de déploiement (§6.2) évite aussi qu'un jeton « douane » atteigne un serveur pas à jour |
| API injoignable au pré-login | Délai HTTP court (2 à 3 s), puis **état d'attente neutre**. Seule l'attestation Guardian peut alors aiguiller. Sans aucune preuve au bout de 10 s : expulsion (§3.3) |
| Compte staff | Rôle HELPER ou plus : `scope:"play"`, jamais la douane |
| Recandidature après un oral réussi | `oralPassedAt` renseigné : pas de nouvel oral (décision du 19/10) |
| Porte beta | `LAUNCHER_BETA_GATE` (`auth.service.ts:124-142`) refuse la **connexion au launcher** des comptes `PLAYER` : en prod, elle bloque la candidature elle-même, pas seulement la douane. Il n'y a pas d'API de dev (ETAT §3) : « couper la porte sur le dev » est impossible. Il faut une liste de comptes de test autorisés malgré la porte (🆕 `LAUNCHER_BETA_ALLOW`) |
| Candidature de clan | Hors périmètre (DISCORD_JOUEURS §7 B) ; voir §2.9 |
| Crash, micro du jeu en panne | Oral hors jeu de secours ? **Q9** |

### 3.11 Transition, avant la mise en ligne de la douane

Tant que les lots jeu ne sont pas publiés (§6) :
- l'oral reste possible **hors jeu** ;
- le panel reçoit une carte de décision **« Oral »** (nouvelle partie de `PATCH /admin/whitelist/:id/decision`) ;
- l'examinateur la saisit après un échange vocal ;
- les données restent les mêmes.

Elderwood propose deux formules : « Douane classique » et « Douane interactive » (planche 03, images 1 et 3). Garder
cette carte « Oral » **après** la transition, comme secours, est la **Q9**.

### 3.12 Sécurité : les principes

Le dépôt est public : cette section résume, sans détailler comment exploiter les faiblesses actuelles.

| Principe | Pourquoi | Où |
|---|---|---|
| **Refus par défaut** | Une panne (API muette, plugin absent ou ancien) ne doit jamais ouvrir la sélection à un candidat | Aiguillage §3.3, cas limites §3.10 |
| **Preuve positive de `play`** | La sélection n'ouvre que sur une réponse `joueur` de l'API ou une marque `play` de Guardian | §3.3 |
| **Marque transitoire, pas permission** | Une permission non déclarée est accordée aux op et au joker `*` de LuckPerms | Guardian §3.3 |
| **Jeton ciblé (`aud`)** | Une seule API sert tous les serveurs ; un jeton ne doit valoir que pour le serveur visé | Jeton §3.3, Guardian (lot C) |
| **Secret propre au jeu, par serveur** | `REBORN_WEBHOOK_SECRET` est partagé avec le bot et stocké chez l'hébergeur du jeu | Décision §3.9 |
| **Signature liée à la requête** | Les gardes actuels (`GameHmacGuard`, `HmacSignatureGuard`) ne lient la signature ni à la route ni à l'heure. Nouveau garde pour toutes les nouvelles routes `game/` : méthode, chemin, heure, corps ; nonce ; limite de débit ; audit | `acces`, `dossier`, `decision`, `presence`, `characters/sync` ; à terme, `candidature` |
| **Revérification côté API** | L'examinateur doit avoir le rôle web, avec son compte Minecraft lié ; la phase doit être la bonne | §3.9 |
| **Barrière vérifiée avant tout** | Si Guardian n'est pas actif, plus rien n'expulse un client sans jeton | Vérification n° 0, §6.2 |

---

## 4. Création de personnage et « Mes personnages »

### 4.1 Données transmises au serveur

C'est le chemin existant, à étendre. Les champs déjà présents sont `name`, `clan`, `village` et `staff`.

1. `CandidatureView` (`apps/api/src/game/game.service.ts:13-27`, `:50-74`) : ajouter `phase`, `sexe`, `age`,
   `applicationId` et `consumed`. Ne plus renvoyer `found:true` pour une candidature non acceptée (`:67`).
2. Le record `Candidature` (`CandidatureClient.java:41`) et sa lecture (`:69-102`), qui jettent aujourd'hui
   `hrpApproved` et `rpApproved`.
3. Le bloc `candidature` du roster (`CharacterSelectManager.java:235-258`).
4. `CharacterData.java:33-36` et `:83-92`, côté mod.
5. Le constructeur de `PersoCreationScreen` (`:111-123`) et ses setters.
6. Le contrôle **côté serveur** dans `handleCreate` (`CharacterSelectManager.java:425`), avec la candidature déjà en
   cache (`:50`).

### 4.2 Ce qui est verrouillé, ce qui reste libre

Pour le **premier personnage** du compte, celui issu de la candidature (Q8) :

| Donnée | En jeu | Contrôle serveur |
|---|---|---|
| Village | Pré-rempli, **verrouillé** | Égalité exigée dans `handleCreate` |
| Clan de la liste | Pré-rempli, **verrouillé** ; « Autre » n'est plus ouvert si la candidature porte un clan. Aujourd'hui, « Autre » n'est jamais verrouillé et contourne le verrou (`CharacterRules.clanLocked`, `:73-78`) | Égalité exigée |
| Nom de famille libre (« Sans clan ») | Correspondance : « Sans clan » + nom libre côté launcher = clan **« Autre »** + nom saisi côté mod (`PersoCreationScreen.java:150`, `customClan`). Le mod présélectionne « Autre », pré-remplit le nom et **verrouille les deux**. `clanLocked` ne doit plus exempter « Autre » quand la candidature porte un nom libre | Égalité exigée sur le nom ; un nom de clan existant reste refusé (`isPredefinedClan`) |
| Nom (prénom) | Pré-rempli ; verrouillé ou non : **Q13** | Si verrouillé : égalité exigée ; 3 à 20 caractères comme le mod |
| Sexe | Pré-rempli, **verrouillé** | Égalité exigée |
| Âge | Pré-rempli, **verrouillé** | Égalité exigée ; bornes 10 à 60 (aujourd'hui `max(0, valeur)`) |
| Taille, apparence (peau, cheveux, yeux, tenue) | **Libres** | Taille ramenée entre 153 et 207 cm pour un adulte |
| Caractère, histoire, objectifs | Non repris dans le personnage, qui n'a pas de champ histoire | Lus par « Mes personnages » (§4.6) |

**Changer un verrou** : une demande par ticket, puis le staff passe par le Poste de garde, fiche › Personnages ›
Identité (`PersonnagesPanel.java:298-330`).

### 4.3 Corrections nécessaires

- **Course au chargement**. La candidature arrive en asynchrone, après le premier roster. Le constructeur de
  `PersoCreationScreen` la lit une seule fois, et `validate()` ne revérifie pas les verrous (`:188-210`).
  - Correctif : relire les verrous à chaque nouveau roster.
  - Côté serveur, ne pas envoyer la tuile « Nouveau shinobi » tant que la candidature n'est pas chargée.
- **Comparaison stricte** (`equalsIgnoreCase`). Une valeur hors liste retombe sur Konoha (`PersoCreationScreen.java:114-115`).
  Les listes fermées du §2.3 suppriment ce cas.
- **Création avant l'oral** : refusée (§3.4). Le serveur ne propose pas la création à un compte qui n'est pas en
  phase `ACCEPTE`.

### 4.4 Lien entre la candidature et le personnage

À la création du premier personnage :
- ShinobiCore écrit `application: <id>` dans le YAML du personnage ;
- la synchronisation (§4.5) renseigne `WhitelistApplication.characterId` et `characterPlayerId`.

La candidature est alors **consommée** (`consumed: true` dans `CandidatureView`). Les personnages suivants suivent la
règle de Q8.

### 4.5 Synchronisation vers l'API

Le serveur de jeu n'a pas de port entrant : c'est **le plugin qui pousse**, comme `PanelBridge`.

**Modèle Prisma 🆕 `GameCharacter`**
- Champs : `server`, `characterId`, `ownerMcUuid`, `userId?`, `playerId`, `name`, `clan`, `village`, `rank`, `age`,
  `sexe`, `dead`, `deleted`, `createdAt`, `lastPlayedAt`, `playMinutes`, `applicationId?`, `updatedAt`.
- Clés : `@@unique([server, characterId])` et `@@unique([server, playerId])`.
- Le champ `server` est **obligatoire**. Chaque serveur numérote ses N° (`player-ids.yml`), et le network viendra.
- ShinobiCore n'a **aucun identifiant de serveur** aujourd'hui : il faut une 🆕 clé `reborn.server-id` dans sa
  config (la même valeur que le `server-id` de Guardian, §3.3).
- Le dev parle à l'**API de prod** (une seule API, ETAT §3). Ses personnages de test arriveraient donc en base.
  « Mes personnages » ne montre que le serveur de prod (filtre sur `server`).

**Route** : `POST /v1/game/characters/sync`, avec le nouveau garde signé (§3.12) et le secret du serveur.

**Déclencheurs dans ShinobiCore**
- la création (`CharacterManager.create`, `:138`) ;
- `setActive` et la déconnexion ;
- la sauvegarde automatique (60 s) ;
- la mort RP (`KillRequestManager.java:260`) ;
- la suppression ;
- les actions du staff (`PersonnagesPanel.act`) ;
- un **rattrapage complet** au démarrage.

Une file de reprise couvre les moments où l'API est injoignable.

**Temps de jeu par personnage** : 🆕 `playMinutes` et `lastPlayed` dans `ShinobiCharacter`, incrémentés pour le
personnage actif. **Le passé ne se reconstitue pas.** Le launcher affiche « compté depuis le JJ/MM ».

### 4.6 L'écran « Mes personnages » du launcher

**Accès** : une nouvelle entrée dans la barre latérale (`Sidebar.tsx:31-38`, type `SidebarItem`), avec sa route. La
route est à coordonner avec la tâche qui réécrit `routes/`.

**Données** : `GET /v1/me/characters` (JWT) renvoie les seuls personnages du compte, sur le serveur de prod. Côté
launcher : méthode `ApiClient`, commande Tauri, `generate_handler!` (chemin complet), puis le wrapper dans
`src/lib/`.

**Une carte par personnage**
- l'emblème du village et le blason du clan (pas de portrait en v1 : le skin est composé côté client, le serveur n'a
  que la `SkinSpec`) ;
- le nom et le clan ;
- **N° 00042**, avec « Copier » pour un ticket ;
- village, grade, âge, sexe ;
- un état : En vie · Mort RP · Supprimé ;
- « Créé le », « Dernière partie », temps de jeu ;
- un emplacement vide : « Emplacement libre — crée ton personnage en jeu ».

**Détail du premier personnage** : le **dossier d'entrée** en lecture seule (histoire, objectifs, caractère, date de
la douane).

**Règles** (GAMEPLAN §1.4, §1.9)
- Le N° n'apparaît **que** dans cette vue du propriétaire et dans le panel staff. Jamais dans un endpoint social ou
  public.
- Le nom RP peut changer, le N° jamais.
- L'écran est en lecture seule : on joue et on crée en jeu.

### 4.7 En jeu

La sélection `PersoSelectionScreen` sert déjà de « Mes personnages ». En option, on y ajoute le N° :
- `pid` dans le roster (`CharacterSelectManager.java:206-234`) ;
- `CharacterData` et `CharacterCard` côté mod.

---

## 5. Discord

### 5.1 Aujourd'hui

**Lien faux dans le mod** : `DISCORD_URL = "https://discord.gg/reborn"` (`minecraft/mod-hud/.../menu/RebornBranding.java:83`).
Le lien officiel est `https://discord.gg/Nw7jpjJ6dg`. Le site, lui, pointe vers `reborn-rp.com` (`:79`), alors que
l'API utilise `reborn-rp.fr` (`REBORN_WEB_URL`).

**Dans le client**, le Discord existe déjà à deux endroits :
- l'**Échap** : la ligne « Discord : N membres · N en ligne » et quatre icônes (`EscTokonoma.java:89-95`,
  `:521-541`) ;
- **Options › Discord** (`DiscordTab.java:24-33`).

Les deux lisent `GET /v1/menu/panel` (`EscData.java:74-89`, `:117-124`). Les champs inconnus sont ignorés : on peut
étendre la réponse sans casser le mod.

**Mauvais serveur compté** : le compteur appelle `guilds/${DISCORD_GUILD_ID}?with_counts=true`
(`apps/api/src/menu/menu.service.ts:82-107`).
- `DISCORD_GUILD_ID`, c'est l'ancien serveur perso (DISCORD_JOUEURS, en-tête).
- Le conteneur `api` ne reçoit pas `DISCORD_JOUEURS_GUILD_ID` (`infra/docker-compose.prod.yml:62` contre `:147`).
- Conclusion : on compte le mauvais serveur. Non confirmé, `.env` n'a pas été lu.

**Menu principal** : aucun Discord. Cinq entrées sont codées en dur (`TitleScreenMixin.java:126`), dont NEWS et
BOUTIQUE vides (`:146-147`).

**Launcher** : quatre liens sociaux morts, `href="#"` (`apps/launcher/src/components/home/FooterMega.tsx:57-72`,
`:98-116`). Aucun compteur, aucune carte Discord.

### 5.2 Lien et compteur, côté API

- 🆕 `DISCORD_INVITE_CODE` dans `.env` et dans le service `api` du compose.
- `fetchDiscord` appelle `GET https://discord.com/api/v10/invites/${code}?with_counts=true`, **sans jeton**.
  - Il récupère `approximate_member_count`, `approximate_presence_count`, `guild.name` et `guild.icon`.
  - Le cache de 60 s existe déjà (`menu.service.ts:34`).
- Réponse étendue : `discord: {members, online, inviteUrl, name}`.
- Si l'invitation a expiré (404, code 10006), `inviteUrl` vaut `null`, le bouton se masque et un avertissement part
  au journal.
- **À vérifier sur Discord** : l'invitation `Nw7jpjJ6dg` doit être réglée sur « n'expire jamais ». Les invitations
  expirent à 7 jours par défaut.
- Autre solution : lire `DISCORD_JOUEURS_GUILD_ID` avec le jeton du bot. Il faudrait alors ajouter la variable au
  service `api` du compose.
- **Repli** : la constante `https://discord.gg/Nw7jpjJ6dg` dans le mod et dans le launcher, si l'API ne répond pas.
  Le lien est public ; il figure déjà dans DECISIONS.md.

### 5.3 Dans le launcher

**Carte « Rejoindre le Discord »** sur l'accueil, sous les actualités :
- l'icône du serveur, embarquée : c'est l'avatar violet de la DA §7, pas le CDN de Discord ;
- « Discord Reborn Roleplay » ;
- « 1 234 membres · ● 210 en ligne » ;
- le bouton **Rejoindre**.

**Ouverture du lien** : par `tauri-plugin-opener`. La permission est déjà accordée (`capabilities/default.json:11`).

**Données** : une commande Tauri sur `/menu/panel`, via `get_json_public` (`src-tauri/src/api/mod.rs:586-592`), puis
un wrapper dans `src/lib/content.ts`.

**Mêmes liens ailleurs**
- Le lien Discord du pied de page (`FooterMega.tsx:98-116`). Les autres réseaux restent en attente.
- La fin de l'onglet Envoi et la page de suivi de la candidature.

### 5.4 Dans le client

- **Corriger** `RebornBranding.DISCORD_URL` (`:83`). `EscData` lit `discord.inviteUrl`, avec la constante en repli.
- **Menu principal** : une 6ᵉ entrée **DISCORD**, entre OPTIONS et QUITTER.
  - Au survol, « 1 234 MEMBRES », avec `MenuEntryButton.withHoverInfo` (`:67`). C'est la même mécanique que « N EN
    LIGNE » sur JOUER.
  - `menuStartY(this.height, 5)` passe à 6 (`TitleScreenMixin.java:126`), et `EscData.refreshIfStale()` est appelé
    dans `init`.
  - À valider sur maquette (J10) : l'utilisateur a refusé les refontes de ce menu (DA §3). Ici, on ajoute une ligne
    de même style, sans rien refaire.
- **Échap et Options** : rien ne change visuellement, seul le lien change.
- **Écran de connexion** : rien. La décision « ni logo, ni bouton » est validée (MOD_HUD.md §8).

---

## 6. Lots, ordre et maquettes

### 6.1 Les lots

Deux trains de publication ne se mélangent jamais dans un commit (CLAUDE.md) :
- **Web** : API, panel, bot ;
- **Jeu** : mod et plugins.

La release du launcher est à part.

| Lot | Train | Contenu | Dépend de |
|---|---|---|---|
| **0 — Vérifications** | Serveur (utilisateur) | Vérification n° 0 (§6.2) : Guardian chargé et actif sur chaque serveur, `white-list` vanilla | — |
| **K — Discord** | Web, Jeu, launcher (trois commits) | Invitation dans `/menu/panel`, variable du compose ; lien du mod et entrée du menu principal ; carte de l'accueil et pied de page du launcher | — |
| **A — API candidature v2** | Web | Migration des dossiers en cours (§2.9) ; DTO tolérant par `formVersion` ; bornes (âge, listes fermées, maximums) ; brouillon serveur `CandidatureBrouillon` ; voix (stockage privé, consentement, effacement) ; `GET /whitelist/options` ; phases ; écrit = HRP + RP, état « écrit accepté » (§1.3) ; `writtenStaffNotes`, `oralReason`, `oralNotes` ; promotion après l'oral ; délai de 1 jour ; renvoi après l'oral (§2.7) ; séances avec capacité et réservations (§3.3) ; rappels ; décision « Oral » au panel (transition) ; ancienne décision globale corrigée ; `LAUNCHER_BETA_ALLOW` | — |
| **F1 — Launcher, candidature** | Launcher | Six onglets, habillage Ketto (§2.2), validations, enregistrement automatique, voix (corps brut), relecture, consentement, suivi avec frise, « à revoir » partiel, refus avec compte à rebours, réservation dans une séance, notifications | A |
| **G — Panel** | Web | Liste : colonnes Écrit HRP / Écrit RP / Oral et filtre « Douane à faire » (aujourd'hui seul le statut global, `admin.service.ts:30-48`) ; fiche : nouveaux champs, lecteur de la voix, note privée de l'écrit, bloc Douane (séance, résultat, examinateur, motif, notes privées) ; **séances** : ouverture avec capacité et examinateurs, convocations par vagues, réservations, notes à la clôture (aujourd'hui `body: {}`, `oral-slots/page.tsx:56`) ; file en direct (si lot présence) | A |
| **H — Bot** | Web | Décision par partie au lieu de la décision globale ; acteur transmis à l'audit ; droits sur `/whitelist` ; MP de rappel et d'annulation de séance ; découpage des textes longs dans les embeds (4 096 caractères au plus) ; aucun rôle posé | A |
| **I — Construction** | Serveur (utilisateur) | Monde `douane` (générateur vide) : hall, deux à quatre bureaux, panneau du règlement ; positions relevées pour la config ; **copie du dev vers la prod** | Q6 |
| **C — Guardian** | Jeu | `scope` et `aud` dans `TokenPayload`, clé `server-id`, métadonnée transitoire `reborn.scope` (pas de permission), tests | — |
| **D — ShinobiCore, douane** | Jeu | Paquet `douane/` (`DouaneManager`, `DouaneClient` pour le pré-login, le dossier, la décision et la présence, `DouaneCommand` dont `/douane aller`) ; aiguillage avec attente neutre et refus par défaut ; hall fermé sans examinateur ; confinement ; état `HORS_JEU.DOUANE` ; exception « douane » dans `prefixFor` ; tchat du hall ; file ; bureaux ; mode examinateur ; banque de questions tirée au hasard ; refus de `open` et `create` ; nouveau garde signé et secret du serveur ; clé `reborn.server-id` ; config `douane:` et `plugin.yml` (validés par un vrai parseur YAML avant publication) | B pour les routes, I pour les positions |
| **E — Mod, douane** | Jeu | Canal `reborn:douane` (ligne d'attente, appel, question, Bienvenue, résultat) ; onglet Douane du Poste de garde, fiche résumée en volet élargi, « Ouvrir dans le panel », formulaire de décision (limite 200 → 2 000) | D (protocole) |
| **B — API jeu** | Web | `GET /game/acces/:mcUuid` ; `GET /game/douane/dossier/:mcUuid` ; `POST /game/douane/decision` ; `POST /game/douane/presence` (facultatif) ; nouveau garde signé et `REBORN_GAME_SECRET` par serveur (§3.12) ; `scope` et `aud` dans le jeton ; drapeau `REBORN_DOUANE_ENABLED` | A |
| **F2 — Launcher, entrée** | Launcher | Barrière par phase (`launcher_launch_game`, `PlayButton.tsx` › `isWhitelistGated`), « Passer la douane », compte à rebours de la séance | B |
| **J — Personnages** | Jeu, Web, launcher (trois commits) | ShinobiCore : `playMinutes`, synchronisation, verrous côté serveur dans `handleCreate`, marque `application`, roster relu (§4.3) ; mod : verrous sexe et âge, « Autre » verrouillé pour un nom libre, relecture des verrous ; API : `GameCharacter`, `/game/characters/sync`, `/me/characters` filtré sur la prod ; launcher : écran « Mes personnages » | A ; verrous après D |
| **L — Discord après la douane** | Discord (bot, `post:message`) | **Seulement une fois la douane en service.** Retirer `⏳・Attente oral` et `🎙️・Oral 1/2` du plan (DISCORD_JOUEURS §1, §3 étape 4) ; réécrire le rôle de `\| Recrutement` (§2) : examinateurs de la douane en jeu ; **reposter `nous-rejoindre` avec `--refaire`** : le message posté renvoie encore à « Attente oral » | Douane en service |

### 6.2 Ordre proposé

0. **Vérification n° 0 : la barrière**. Rien ne sert de bâtir la douane si l'entrée n'est pas gardée.
   - Guardian se désactive tout seul si son secret manque (`RebornGuardian.java:53-60`). Alors plus personne n'est
     expulsé. Le constater dans le journal de démarrage de chaque serveur.
   - Son déploiement n'est pas attesté (ETAT §2, ligne `reborn-guardian`), et l'état des plugins de la prod n'est pas
     tracé (ETAT §2).
   - L'état du `white-list` vanilla (`server.properties`) n'est pas dans le dépôt. S'il est actif, un candidat est
     refusé avant même les plugins.
1. **K** : rapide et sans risque.
2. **A** (tolérant, `formVersion`), puis **F1, G et H** en parallèle. La candidature v2 sort avant la douane, et
   l'oral passe par la carte « Oral » du panel (§3.11).
3. **I**, en parallèle : construction par l'équipe, puis copie vers la prod.
4. **C, D et E** publiés sur le dev, drapeau API **coupé**.
5. **B**. Attention : **une seule API** sert la prod, le dev et le build (ETAT §3), et le jeton d'aujourd'hui ne
   désigne aucun serveur (`play.service.ts:20-26`). Activer `REBORN_DOUANE_ENABLED` vaut donc pour tous les serveurs.
   - **Avant** l'activation : `aud` contrôlé par Guardian (lot C) sur **tous** les serveurs, prod comprise.
   - Pour tester sur le dev : jetons « douane » émis pour le dev, et seulement pour les comptes de test
     (`LAUNCHER_BETA_ALLOW`).
   - Ouvrir la douane sur la prod seulement après y avoir publié C et D.
   - Puis **F2**.
6. **J**, dès que A est en place. Les verrous côté serveur suivent D.
7. **L**, une fois la douane en service sur la prod.

### 6.3 Maquettes à produire pour validation

Définitions :
- **Capture réelle** : l'écran existe déjà, ou peut se capturer dans le vrai client de dev (`pnpm launcher:dev`,
  bancs `REBORN_*` du mod).
- **Montage** : l'image est composée, avec le kit de style ou en HTML aux couleurs du launcher, parce que l'écran
  n'existe pas.

Toute maquette validée est refaite en capture réelle **avant** toute publication (DA §5).

| N° | Écran | Contenu | Type |
|---|---|---|---|
| L1 | Candidature › 1 Vous | Âge en nombre, années de RP, expérience, connaissance de Naruto en segments, « Pourquoi Reborn ? », découverte, Discord lié, « Enregistré » | Montage (HTML, 1440 × 900) |
| L2 | Candidature › 2 Personnage | Prénom, sexe, âge et sa tranche, caractère et apparence | Montage |
| L3 | Candidature › 3 Faction | Six emblèmes, grille des clans de Konoha, un clan sous cadenas, « Sans clan » avec le nom libre | Montage (emblèmes réels du mod) |
| L4 | Candidature › 4 Histoire | Barre Markdown, « 312 mots · 1 840 / 12 000 » (maximum de la reco Q10), zone agrandie, objectifs | Montage |
| L5 | Candidature › 5 Voix | Test du micro (vu-mètre), enregistrement 0:42 / 1:00, Réécouter, Refaire | Montage |
| L6 | Candidature › 6 Envoi | « Il reste à compléter » avec ses puces, puis l'état complet : relecture, règlement coché, bouton actif | Montage |
| L7 | États d'erreur | Texte trop long (compteur rouge), âge hors bornes, micro refusé, « Hors ligne — gardé sur ce PC » | Montage |
| L8 | Suivi | Frise ; « À revoir » avec commentaire et onglets rouverts ; « Refusé » avec compte à rebours | Montage (le fil existant se capture en réel) |
| L9 | Réservation de la séance de douane | Jours, séances avec places libres (« 5 places sur 16 »), heure de convocation, Annuler | Montage |
| L10 | Accueil | Bouton « Passer la douane · 21 h » et carte « Rejoindre le Discord » | Montage sur **capture réelle** de l'accueil actuel |
| L11 | Mes personnages | Deux cartes (en vie, mort RP), un emplacement libre, le détail avec dossier d'entrée | Montage |
| J1 | Hall de la douane | Vue du candidat, autres candidats, ligne « DOUANE · En attente · 3ᵉ » ; état fermé « Ouverture dès l'arrivée d'un examinateur » | Montage (monde non construit) ; capture réelle après le lot I |
| J2 | Appel | Fondu « Un examinateur t'appelle », arrivée au bureau | Montage |
| J3 | Bureau | L'examinateur face au candidat, ligne « Question · … » en haut | Montage |
| J4 | Bienvenue sur Reborn | Écran opaque, titre or, filet | Montage, puis capture réelle par un banc |
| J5 | Résultat négatif | « La douane n'est pas validée aujourd'hui… » avant l'expulsion | Montage |
| J6 | Poste de garde › Douane | File de trois candidats, survol avec Appeler · Dossier · Renvoyer | Montage avec le kit du volet v3 ; capture réelle possible ensuite par `REBORN_STAFF_DEBUG` |
| J7 | Fiche résumée du candidat | Volet élargi, identité, personnage, extraits repliables, note privée de l'écrit, « Ouvrir dans le panel », question tirée au hasard, « Montrer la question » | Montage |
| J8 | Décision | Accepter · À revoir · Refuser, motif sur plusieurs lignes, notes privées | Montage |
| J9 | Création pré-remplie | Village, clan, sexe et âge verrouillés (cadenas), prénom pré-rempli (verrou selon Q13), taille et apparence libres | **Capture réelle** de l'état actuel (banc de scène), plus un montage des nouveaux verrous |
| J10 | Menu principal | Entrée DISCORD, avec « 1 234 MEMBRES » au survol | **Capture réelle** du menu actuel, plus un montage de l'entrée |
| P1 | Panel › Whitelist (liste) | Colonnes Écrit HRP / Écrit RP / Oral, filtre « Douane à faire » | Montage ; capture réelle de la liste actuelle pour comparer |
| P2 | Panel › Fiche candidature | Nouveaux champs, lecteur de la voix, bloc Douane | Montage |
| P3 | Panel › Séances de douane | Séance avec capacité et examinateurs, convocations par vagues, réservations, présence en direct, notes à la clôture | Montage |

Pas de maquette pour Échap et Options › Discord : seul le lien change.

### 6.4 Vérifications prévues (après code)

**Candidature**
- Âge « 14 » : le message s'affiche et l'API refuse aussi, par un appel direct.
- Histoire au-delà du maximum (13 000 caractères si Q10 retient 12 000) : le compteur passe au rouge, l'envoi est
  grisé, **rien n'est coupé**.
- Brouillon : fermer le launcher en plein texte, rouvrir sur un autre PC avec le même compte : le texte est là.
- Ancien launcher (sans `formVersion`) : son envoi passe encore, avec les anciennes règles.
- Retrait de la candidature : le fichier de voix disparaît du stockage.
- Migration : un compte déjà `WHITELISTED` qui recandidate ne repasse pas l'oral.

**Douane**
- Réservation impossible tant que l'écrit n'est pas validé.
- Le jour J, « Passer la douane » est grisé à −20 min, actif à −10 min, encore actif à +25 min, grisé à +35 min.
- Avant l'arrivée d'un examinateur : écran d'attente, aucune voix, aucun tchat. L'examinateur entre : le hall s'ouvre.
- Deux comptes `PLAYER` de test arrivent au hall : ils se voient, s'entendent et se lisent au tchat, **sous leur
  pseudo** (pas « Inconnu »). Un joueur du monde RP ne lit pas leur tchat.
- API coupée au pré-login : écran « Vérification de l'accès… », puis aiguillage par Guardian ; Guardian absent aussi :
  expulsion, jamais la sélection.
- Un jeton émis pour le dev est refusé par la prod (`aud`).
- Un op et un staff avec le joker `*` arrivent en sélection, jamais au hall.
- Un examinateur sans grade `reborn.staff.*` ne voit pas l'onglet Douane. Un staff sur un second compte Minecraft :
  l'API refuse sa décision.
- En mode examinateur : visible, plaque « Examinateur », aucune mécanique RP, entrée et sortie au journal.
- Un candidat qui sort de la zone revient au point d'arrivée. `/tp` et `/spawn` sont refusés ; la sacoche et la fiche
  n'ouvrent rien.
- Un examinateur appelle : le candidat est au bureau. Les voix du hall ne s'y entendent pas, et inversement.
- Accepter : écran Bienvenue, sélection, création pré-remplie verrouillée. Tenter un autre clan par un client modifié :
  le serveur refuse. Rouvrir la sélection (touche) : on reste en sélection, pas de retour au hall.
- « Sans clan » avec un nom libre : en jeu, « Autre » est présélectionné, le nom pré-rempli, les deux verrouillés.
- Refuser : expulsion avec le message. Le launcher affiche le motif de l'oral **et** garde le commentaire de l'écrit,
  avec le compte à rebours de 24 h. Le renvoi repart de zéro (HRP compris).
- Renvoyer un candidat au hall puis le rappeler : la question tirée n'est pas celle du premier passage.

**Personnages**
- Créer un personnage : il apparaît dans « Mes personnages » en moins d'une minute, avec son N°.
- Un personnage créé sur le dev n'apparaît pas dans « Mes personnages ».

**Discord**
- La carte de l'accueil affiche des nombres proches de ceux de Discord. Le bouton ouvre `discord.gg/Nw7jpjJ6dg`.
- L'entrée du menu principal et le lien de l'Échap ouvrent le même lien.

---

## 7. Questions ouvertes

Q1 à Q8 : questions de conception. Q9 : le secours. Q10 à Q13 : des règles que la première version de cette fiche
avait posées sans que personne ne les demande ; elles attendent une décision.

| # | Question | Recommandation |
|---|---|---|
| Q1 | **Âge minimum du joueur** : 15 ans (COMMUNICATION Q4), 16 (le launcher aujourd'hui) ou 18 (Ketto) ? | **15 ans**, l'âge du consentement numérique en France. Réglable (`WHITELIST_AGE_MIN`), borné par l'API. L'âge est **déclaré** : rien ne le prouve ; à l'oral, l'examinateur signale un doute |
| Q2 | **Onglet Voix** : enregistrement obligatoire ou facultatif ? Rattaché à quelle partie ? Combien de temps le garder ? | **Obligatoire**, de 15 à 60 s, avec test du micro : la douane se passe à la voix, et ça écarte tôt les candidats sans micro. Rattaché à la **partie RP** (il présente le personnage) : « RP à revoir » rouvre aussi la Voix. Consentement explicite à l'envoi. Fichier privé, **effacé 30 jours après la décision finale** et au retrait |
| Q3 | **Type Ninja / Civil**, comme Ketto ? | **Pas de champ pour la beta** : aucune mécanique civile en jeu, tout personnage est créé Genin. À rouvrir avec les professions (GAMEPLAN §21) |
| Q4 | **Clans sur session** : à la première candidature, lesquels sont libres, lesquels passent par une session de clan ? Liste du mod (`CharacterCreateScreen.java:120-129`) : **Konoha** Uchiha, Senju, Hyuga, Nara, Sarutobi, Aburame, Akimichi, Yamanaka, Hatake, Uzumaki, Kurama ; **Suna** Sabaku, Hoki ; **Kiri** Hozuki, Hoshigaki, Kaguya, Yuki, Terumi ; **Kumo** Yotsuki ; **Iwa** Kamizuru, Onoki ; **Ame** aucun | Sur session : les clans à **kekkei genkai** de cette liste, **Uchiha, Hyuga, Senju, Kurama, Kaguya, Yuki, Terumi**. Les autres, libres. À trancher à part : Uzumaki (pas de kekkei genkai, mais lignée très demandée), Sabaku et Onoki (lignées de Kage). « Sans clan » toujours possible. La liste est réglée dans l'API (`GET /whitelist/options`), pas en dur |
| Q5 | **Forme de la douane** : séance avec capacité, créneau individuel, ou file libre aux heures d'ouverture ? | **Séance avec capacité** (§3.3) : le staff ouvre une séance (début, fin, capacité, examinateurs), les candidats y réservent une place. Dimensionnement : 4 à 6 candidats par heure et par examinateur, capacité = examinateurs × heures × 4. Convocations par vagues de 30 min pour une séance longue. **Fenêtre d'entrée unique** : de H − 15 min à H + 30 min, H = heure de convocation |
| Q6 | **Lieu et décor** : monde dédié ou zone dans `world` ? Qui construit ? | **Monde dédié vide** (générateur vide), hall et deux bureaux, décor de poste-frontière neutre construit par l'équipe sur le dev, puis copié vers la prod. Un hall provisoire en blocs simples suffit pour les tests |
| Q7 | **Échec à l'oral** : que peut faire le candidat ensuite ? | « **À revoir** » : nouvelle séance dès le lendemain, écrit conservé. « **Refusé** » : délai de 1 jour (règle joueur du 19/10), puis **nouvelle candidature écrite complète**, HRP compris (§2.7) |
| Q8 | **Personnages suivants** (2ᵉ emplacement) : verrouillés eux aussi par la candidature ? | Seul le **premier personnage** suit la candidature, verrouillé côté serveur. Le 2ᵉ est libre (village, clans communs), sans nouvel oral ; les clans sur session restent exclus |
| Q9 | **Oral hors jeu de secours** : une fois la douane en service, garde-t-on un oral hors jeu (vocal Discord ou appel), pour un crash du jeu ou un micro qui ne marche qu'en dehors du jeu ? Si oui, qui le décide ? Elderwood a deux formules, « classique » et « interactive » (planche 03) | **Oui, en secours seulement.** La carte « Oral » du panel (§3.11) reste. C'est l'**examinateur** qui la décide, motif obligatoire (« crash », « micro du jeu »), tracé à l'audit. Pas au choix du candidat |
| Q10 | **Bornes de l'histoire** : la première version portait le minimum de 150 à 1 000 caractères, sans demande. Garder 150, monter, ou compter en mots comme Ketto (« 300 mots minimum ») ? Et le maximum ? | **Garder le minimum à 150 caractères** pour la beta ; la plainte portait sur le bug des textes longs, pas sur un minimum trop bas. **Monter le maximum à 12 000** pour accueillir les longues histoires. Le compteur affiche les mots à titre indicatif |
| Q11 | **Délai de relecture annoncé** au candidat (la première version écrivait « relu sous 48 h ») ? | **Ne pas promettre de délai** tant que l'équipe de recrutement n'est pas constituée. Afficher la date d'envoi et « en cours de relecture » |
| Q12 | **Séances manquées** : à partir de combien faut-il passer par un ticket (la première version disait 2) ? | **Pas de limite pour la beta** : chaque séance manquée est visible au panel, le staff juge. Reco à revoir si les absences encombrent les séances |
| Q13 | **Prénom verrouillé en jeu** pour le premier personnage, comme le village et le clan ? | **Pré-rempli mais modifiable** à la création, dans les règles du mod (3 à 20 caractères) : le staff a validé un personnage, pas une orthographe. Village, clan, sexe et âge restent verrouillés |
