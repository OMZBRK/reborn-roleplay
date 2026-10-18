# Référence des plugins serveur

> **Ce document est un annuaire**, généré à partir du code le 2026-10-12 : `plugin.yml`, `config.yml`,
> canaux réseau et fichiers de données de chaque plugin. Il répond à « où est la commande X », « quel canal
> transporte Y », « quel fichier stocke Z ».
>
> Les règles de jeu de chaque système sont dans [`gameplay/`](../gameplay/). Les commandes du staff, rangées
> par usage, sont dans [`staff/GUIDE_STAFF_EN_JEU.md`](../staff/GUIDE_STAFF_EN_JEU.md).
>
> **À tenir à jour** quand on ajoute une commande, un canal ou une section de config. Les commandes
> utilisées pour régénérer les tableaux sont en annexe (§7).

---

## 1. Les plugins et leurs dépendances

| Plugin | Dossier | Build | Dépendances (`softdepend`) | Rôle |
|---|---|---|---|---|
| **ShinobiCore** | `minecraft/shinobi/ShinobiCore` | Maven | PlaceholderAPI | Moteur : personnages, chakra, stats, XP, grades, KO, médical, mort RP, inventaire RP, emotes, Poste de garde, carte, météo, boutique |
| **ShinobiAbilities** | `minecraft/shinobi/ShinobiAbilities` | Maven | ShinobiCore, MagicSpells, ShinobiTail | Contenu Naruto : catalogue de techniques (`abilities.yml`), lancer, mobilité (Naruto run, dash, sauts), parchemins, bibliothèques, apprentissage |
| **ShinobiCombat** | `minecraft/shinobi/ShinobiCombat` | Maven | ShinobiCore | Combat M1 kenjutsu / taïjutsu, parade, endurance, HUD combat |
| **ShinobiLearning** | `minecraft/shinobi/ShinobiLearning` | Maven | ShinobiCore, ShinobiAbilities | Académie, équipes Genin, transmission de maîtrise par un sensei |
| **ShinobiSense** | `minecraft/shinobi/ShinobiSense` | Maven | ShinobiCore, ShinobiAbilities, ShinobiTail | Perception : radar de chakra, genjutsu, dōjutsu (Byakugan) |
| **ShinobiTail** | `minecraft/shinobi/ShinobiTail` | Maven | ShinobiCore | Démons à queues : rage, corruption, monde intérieur, transformation |
| **ShinobiRadio** | `minecraft/shinobi/ShinobiRadio` | Maven | **`depend`** : ShinobiCore, **PlasmoVoice** (plugin serveur `spigot-2.1.17`) | Radio en escouades (14/10) : oreillette, escouades persistantes, addon serveur Plasmo Voice (activation et ligne « radio »), écoute staff — [`../gameplay/RADIO.md`](../gameplay/RADIO.md) |
| **RebornGuardian** | `minecraft/plugin-guardian` | Gradle | — | Vérifie le play-token au JOIN, kick sinon |
| **RebornOst** | `minecraft/plugin-ost` | Gradle | — | Diffusion des musiques (OST), zones, late-join |

> ⚠️ **Si ShinobiCore ne se charge pas, tous les autres plugins Shinobi tombent** en
> `NoClassDefFoundError: com/reborn/shinobicore/...`. Première chose à vérifier au démarrage : la ligne
> `Enabling ShinobiCore`. Cause déjà rencontrée : un `plugin.yml` invalide (cf.
> [`operations/PUBLICATION.md`](../operations/PUBLICATION.md) §4).

Les addons ne parlent pas à la classe `ShinobiCore` : ils passent par les **services** publiés dans le
`ServicesManager` de Bukkit (`com.reborn.shinobicore.api.*`, regroupés côté Abilities dans
`CoreServices`). Services publiés : `CharacterService`, `ResourceService`, `KoService`, `SkillService`,
`HudService`, `ItemGiveService`, `MobilityService`, `ProgressionLadder`, `StatsService`, `CastAttribution`,
`TechniqueRegistry`, `XpService`, `JournalService` (journal universel, 14/10 : `entree(cat, action).acteur(…).cible(…).detail(…).lieu(…).ecrire()`), `InventoryService` (sacoche : palier de surcharge, `weapon(p)`, `give`, verrou —
13/10 ; `cosmetique(p, slot)` / `poserCosmetique(p, slot, objet)`, 14/10, pour la radio) ; `KoService#isDowned` (à terre,
14/10 : `isKo && !isDowned` = inconscient) ; ShinobiCombat publie `EnduranceService` et `StunService` (étourdi). Annotation `@Stable` = point d'extension sûr, `@Internal` = réservé au
Core.

---

## 2. Commandes

Le descriptif complet (usage, alias) est dans le `plugin.yml` de chaque plugin. Colonne « Perm. » =
permission déclarée ; « — » = aucune (la commande vérifie elle-même ce qu'il faut).

### 2.1 ShinobiCore

| Commande | Alias | Pour | Perm. | Ce qu'elle fait |
|---|---|---|---|---|
| `/shinobi` | `sc`, `shinobicore` | tous / staff | `shinobicore.use` | Commande racine (`/sc help`, `/sc reload`) |
| `/character` | `char`, `characters` | tous | `shinobicore.use` | `select` (ses personnages, repli de la sélection du mod), `list [joueur]` (texte ; `[joueur]` et `cinematic <nom\|none>` : `shinobicore.character.admin`). `create` / `edit` retirés le 15/10 : gestion au Poste de garde (§4 bis.2, `persos`) |
| `/stats` | `fiche`, `statistiques` | tous / staff | `shinobicore.use` | Fiche shinobi : répartir ses points, réinitialiser, jeton ; staff `give`, `set`, `reset` |
| `/xp` | — | staff | `shinobicore.staff` | `voir`, `donner`, `retirer`, `niveau <joueur> <n>` — l'XP est invisible pour le joueur |
| `/grade` | — | tous / gradés / staff | `shinobicore.use` | Voir son grade ; promouvoir ou rétrograder selon son propre grade |
| `/meditation` | `meditate`, `med` | tous | `shinobicore.use` | S'asseoir, régénérer du chakra, avancer le repos ATA |
| `/testfeuille` | `feuille`, `leaftest` | tous | `shinobicore.use` | Test de la feuille : révèle la nature de chakra |
| `/me` | `action`, `act` | tous | `shinobicore.use` | Action RP avec le nom du personnage |
| `/rencontrer` | `meet`, `introduce` | tous | `shinobicore.use` | Se présenter à la personne regardée |
| `/amitier` | `friend`, `amis` | tous | `shinobicore.use` | Demande d'amitié |
| `/etat` | `état` | tous | `shinobicore.use` | État ressenti de son propre personnage |
| `/etats` | `états` | staff | `shinobicore.staff` | **États du joueur** (17/10, [`ETATS_JOUEUR.md`](./ETATS_JOUEUR.md) §8) : `<joueur>` (état principal, couches, lien, modificateurs, lignes de la matrice, refus, vitesse) · `poser <joueur> <id> [s] [n]` (type du registre — `menottes`, `hydratation`… — ou `etourdi` / `entrave` / `controle` / `corps_vide`) · `lever <joueur> <id\|controle\|prison\|occupe>` · `prison <joueur> <minutes> [zone] [motif]` · `recharger` (relit `etats.yml`) ; journal `STAFF` |
| `/osculter` | `ausculter`, `auscult`, `examine` | tous | `shinobicore.osculter` | Ausculter la personne en face |
| `/soigner` | `heal`, `treat` | tous | `shinobicore.use` | Traiter les blessures (silhouette + recette) |
| `/paume` | `shosen`, `soin` | médics / patient | `shinobicore.use` | Paume de soin maintenue ; `accepter`, `stop` |
| `/aide` | `help-rp` | à terre / KO | `shinobicore.use` | Appeler à l'aide (alerte médics + staff) |
| `/hopital` | `hospital` | KO / staff | `shinobicore.use` | KO : se laisser emmener (proposé après 5 min) ; staff : `set`, `suppr`, `liste` |
| `/ata` | — | tous / staff | `shinobicore.use` | Son ATA ; staff `voir`, `lever`, `appliquer <joueur> [pleine\|allegee]` |
| `/corps` | — | tous | — | Actions du menu d'interaction du mod sur un corps (KO, mort) ou une personne **en douleur** : `porter` (sans accord en douleur), `gorge`, `fouiller`, `rpk`, `executer <joueur> [id\|sobre]` (douleur → K.O. ; KO + RPK → mort RP ; canalisation), `achever` (= exécuter sans animation), `annuler` (touche F relâchée : renoncer à l'exécution en cours), et `quitter` (fin de la scène de mort) — [`KO_ET_MORT_RP.md`](../gameplay/KO_ET_MORT_RP.md) §6, §8 |
| `/execution` | — | tous / staff | — | Ses exécutions ; staff `donner`, `retirer`, `liste`, `recharger`, `forcer <joueur>` (K.O. inconscient), `douleur <joueur>` (PV au seuil de douleur), `annuler <joueur>` (arrête une exécution en cours) |
| `/sckillvote` | — | staff | `shinobicore.staff` | Boutons du tchat RPK : `approve`, `refuse`, `goto <id>` |
| `/zonerp` | `entrainement`, `zonesrp` | staff | `shinobicore.zonerp` | Zones d'entraînement, de repos et de **prison** (17/10) : `pos1`, `pos2`, `creer <entrainement\|repos\|prison> <id>`, `suppr`, `liste`, `ici` |
| `/duel` | `defi` | tous | `shinobicore.use` | Duel consenti n'importe où, sans séquelles : `proposer <joueur>`, `accepter [joueur]`, `refuser [joueur]`, `abandonner` (menu d'interaction et boutons du mod) — [`KO_ET_MORT_RP.md`](../gameplay/KO_ET_MORT_RP.md) §4.1 |
| `/pid` | `playerid`, `numero` | staff | `reborn.staff.helper` | **Player ID** : `/pid <joueur\|nom RP\|#N°>` affiche le N°, le nom RP, le compte propriétaire, l'état (vivant, mort RP, supprimé ; KO / ATA si en ligne), en ligne ou non et les autres N° du même compte. Hors ligne avec `#N°` (lu dans l'index). Sans argument : son propre personnage |
| `/garde` | `postedegarde`, `panelstaff` | staff | `shinobicore.use` | Ouvre le Poste de garde (mod requis, touche F7) |
| `/infirmerie` | `ko`, `kogestion` | staff | `shinobicore.use` | Ouvre le Poste de garde sur l'onglet Infirmerie |
| `/staffchat` | `schat` | staff | `shinobicore.use` | Écrire dans le canal staff |
| `/signaler` | `report` | tous | `shinobicore.use` | Signaler un joueur au staff |
| `/staff` | `panel`, `staffpanel`, `adminpanel` | staff | `shinobicore.staff` | Ancien panneau staff en inventaire (`build` = mode construction) |
| `/vanish` | `v`, `invisible` | staff | `shinobicore.vanish` | Invisibilité staff |
| `/cinematic` | `cine`, `cinematique` | staff | `shinobicore.cinematic` | Cinématiques |
| `/dummy` | `trainingdummy`, `sandbag` | staff | `shinobicore.dummy` | Mannequins d'entraînement |
| `/assoir`, `/allonger`, `/ramper` | `sit`, `lay`, `crawl` | tous | `shinobicore.use` | Postures RP |
| `/sacoche` | `sac`, `inv` | tous / staff | `shinobicore.use` | Sacoche RP ; staff `give`, `reset` |
| `/playemote`, `/stopemote` | `emote`, `pe`, `se` | tous | `shinobicore.emote` | Jouer / arrêter une emote |
| `/creator` | — | staff | `shinobicore.creator.admin` | `reload` des assets du créateur de personnage |
| `/ryo` | — | tous / Admin (ou `shinobicore.ryo.admin`) | — | **Monnaie (15/10)** : joueur « Sur toi : 1 240 ryo » (la bourse) ; staff `donner\|retirer\|fixer <cible> <montant> [bourse\|compte] "<motif>"` (compte par défaut, même hors ligne ; **motif obligatoire** ; `give\|take\|set` en alias) — [`../gameplay/MONNAIE_ET_BANQUE.md`](../gameplay/MONNAIE_ET_BANQUE.md) §15.5 |
| `/banque` | — | guichetier : `sceller`, `refuser`, `client <joueur>` ; Modérateur : `voir` ; Admin (ou `shinobicore.banque.admin`) : le reste | `shinobicore.use` | **Banque (15/10)** : `voir <cible>`, `coffre <cible>`, `palier <cible> <1-4> "<motif>"`, `geler\|degeler <cible> "<motif>"`, `guichet ajouter <village> ["libellé"] [entreprise]` / `retirer` / `liste` (en visant le bloc, le meuble ou le PNJ), `contrepasser <n° d'op> "<raison>"`, `masse [village]`, `solde [bareme\|payer [village]]`, `tresor <village> [ajouter\|retirer\|fixer <n> "<motif>"]` ; tout journalisé (`ECONOMIE`) — idem §15.5 |
| `/testament` | — | joueur : `deposer` (au guichet), `voir` ; Admin : `enregistrer`, `valider`, `revoquer` ; Modérateur : `voir <cible>` | `shinobicore.use` | **Testament (15/10)** : `deposer <héritier>[:part] … ["texte"]` (héritier = `#N°` ou nom d'un contact), `enregistrer <cible> <héritier>[:part] … ["texte"]` (validé d'office) ; à la mort RP, après 7 jours de gel, compte et coffre vont aux héritiers d'un testament validé — idem §15.6 |
| `/carte` | `map`, `worldmap` | tous / staff | `shinobicore.use` | Carte (touche M) ; staff `liste`, `tp`, `set`, `del`, `reload` |
| `/meteo` | `weatherfx`, `blizzard`, `tempetedeneige`, `tempetedesable` | staff | `shinobicore.meteo` | Météo visuelle (15/10) : `/meteo <type> [intensité] [durée] [cible]` — types `pluie`, `sable` (vent de sable), `brume`, **`blizzard`**, **`tempetesable`**, `clair` ; intensité 0-100, `leger` / `moyen` / `fort` / `max`, ou **visibilité** `v8` (blizzard et tempête : 100 % = 6 blocs, 90 % ≈ 8, 75 % = 12) ; durée `30s` / `10m` / `2h` (la météo posée s'efface ensuite) ; cible `tous` (défaut, efface le reste), `monde`, `ici [rayon]` (zone ronde autour de soi, fondue sur le dernier quart du rayon), ou un joueur ; priorité joueur > zone > monde > globale ; `clair ici` retire les zones où l'on est ; `/meteo liste`. Les alias `/blizzard …` et `/tempetedesable …` posent directement le type. Journalisé (`STAFF` / `METEO`) ; rien n'est gardé au redémarrage |
| `/journal` | `logs` | Helper | `reborn.staff.helper` | **Journal universel** (14/10) : `/journal [#N°] [catégorie…] [j:<pseudo\|nom RP>] [h:<heures>\|d:<jours>] [p:<page>] [texte]`, 12 lignes par page, lieu au survol ; au-delà de l'index mémoire, relit les fichiers |
| `/note` | `notes`, `tag` | Helper | `reborn.staff.helper` | Notes staff par N° : `/note <cible> [ajouter [etiquette:<mot>] <texte> \| suppr <id>]` ; sans sous-commande = liste (✕ cliquable si on peut supprimer : auteur ou Admin) |
| `/annonce` | `broadcast`, `bc` | Modérateur | `reborn.staff.helper` | Annonce à tous (bandeau du mod, sinon titre) ; `/annonce joueur <cible> <message>` = une seule personne |
| `/annoncejoueur` | `broadcastplayer`, `bcp` | Helper | `reborn.staff.helper` | Message à un seul joueur (scène, convocation) |
| `/aller` | `goto`, `stp` | Helper | `reborn.staff.helper` | Se téléporter à la cible (position de départ mémorisée) |
| `/ramener` | `bring` | Modérateur | `reborn.staff.helper` | Ramener la cible à soi (sa position est mémorisée) |
| `/retour` | `back` | Helper / Modérateur | `reborn.staff.helper` | Sans argument : revenir où l'on était ; `/retour <cible>` (Modérateur) : la renvoyer où elle était |
| `/geler`, `/degeler` | `freeze`, `unfreeze` | Modérateur | `reborn.staff.helper` | Gel **persistant** (`gels.yml`) : ni déplacement (la chute reste), ni coup, ni interaction, ni lâcher, ni bloc ; tchat et commandes ouverts ; titre rappelé à la reconnexion. `/degeler` marche hors ligne (`#N°`) |
| `/voirsac` | `invsee`, `voirinv` | Modérateur | `reborn.staff.helper` | Coffre en lecture : barre, sac porté, équipement, fiche (bourse et compte, poids). **Admin** : clic = prendre l'objet (il arrive dans sa sacoche, journalisé) |
| `/noir` | `blind`, `ecrannoir` | Modérateur | `reborn.staff.helper` | Écran noir : `/noir <cible> [secondes] [message]`, `/noir <cible> off` (ou retaper sans argument) ; `tous` = Admin (joueurs non staff). Persiste à la reconnexion |
| `/seisme` | `earthquake` | Admin | `reborn.staff.helper` | `/seisme [rayon\|tous] [force 1-10] [secondes] [message]` : secousse + grondement ; `tous` = tout le serveur + annonce « La terre tremble… » |
| `/tenue` | `forceclothes` | Modérateur | `reborn.staff.helper` | `/tenue <cible> <id Nexo\|main\|retirer>` : impose l'emplacement cosmétique `TENUE` (verrouillé), rend l'ancienne pièce à la levée. **TODO** : l'apparence de skin de l'échoppe ne peut pas être imposée |
| `/emoteforcee` | `forceemote` | Modérateur | `reborn.staff.helper` | Fait jouer une emote à la cible (`stop` pour arrêter) ; même catalogue que `/playemote` |
| `/modestaff` | `staffmode`, `sm` | Helper | `reborn.staff.helper` | Mode staff : invisible aux joueurs et au staff hors mode staff, vol, invulnérable, **aucune mécanique RP** et personne ne peut le cibler (§4 bis.7), tchat redirigé vers le chat staff, plaques au-dessus des têtes (`reborn:plaques`) ; tombe à la déconnexion. `/modestaff anim` = `/modeanim` |
| `/entreprise` | `entreprises` | Modérateur (ou `shinobicore.entreprise`) ; joueur : les siennes | `shinobicore.use` | **Entreprises** (14/10) : `creer <restaurant\|forge\|echoppe> "<nom>" [responsable]`, `supprimer <e> confirmer`, `renommer <e> "<nom>"`, `membre ajouter\|retirer\|role <e> <cible> [gerant\|employe]`, `poste ajouter <e> [libellé]` / `poste retirer` (en visant le bloc ou le meuble), `info`, `liste` ; journal Staff `ENTREPRISE_*` — [`../gameplay/ENTREPRISES.md`](../gameplay/ENTREPRISES.md) |
| `/boutique` | `boutiques` | Modérateur (ou `shinobicore.boutique`) | `shinobicore.use` | **Boutiques-lieux** (15/10) : `creer <tailleur\|bazar> "<nom>" [village] [entreprise]`, `supprimer <b> confirmer`, `comptoir ajouter <b> [catégories…]` (en visant un bloc, un meuble Nexo ou un PNJ) / `comptoir retirer` / `comptoir categories <cat…>` / `comptoir articles <id…\|tous>` / `comptoir nom "<libellé>"` (en visant le comptoir), `estrade ajouter\|retirer <b>` (là où se tient le staff, regard vers la caméra), `camera <b> <distance> <hauteur>`, `article prix <b> <id> <prix\|catalogue>` / `article retirer\|remettre <b> <id>`, `ouvrir\|fermer <b>`, `info <b>`, `liste [village]`, `voir <b> [n°]` (écran à distance, test), `donner\|reprendre <cible> <tenue> "<motif>"` (garde-robe), `recharger` (catalogue) ; catégories `tenues, hauts, bas, manteaux, coiffes, masques, accessoires, vivres, cartes` ; journal Staff `BOUTIQUE_*` — [`../gameplay/BOUTIQUES.md`](../gameplay/BOUTIQUES.md) §13 |
| `/tendre` | `donner` | Joueur | `shinobicore.use` | **Tendre** (14/10) : `/tendre <joueur>` (objet en main), `/tendre <joueur> ryo <montant>`, `/tendre prendre\|refuser` (touches F / Retour du mod) ; journal `DON` — [`../gameplay/NOURRITURE.md`](../gameplay/NOURRITURE.md) §5 |
| `/nourriture` | `satiete` | Joueur : son état ; Modérateur (ou `shinobicore.nourriture.staff`) | `shinobicore.use` | `etat [cible]`, `satiete <cible> <0-100>`, `donner <cible> <plat:id\|panier:id\|ration\|bol> [n] [froid]`, `effet <cible> <type> <intensité> <secondes>`, `retirer <cible> <type>` ; journal Staff |
| `/modeanim` | `modeanimation` | Animateur / MJ | `reborn.staff.animation` | **Mode animation** (14/10) : incarner un personnage pour un event — visible, mécaniques RP permises, **tout journalisé** (entrée / sortie, commandes, tchat, coups) ; exclusif avec le mode staff ; tombe à la déconnexion (§4 bis.7) |
| `/faction` | `factions` | Joueur ; staff : `admin` | `shinobicore.use` | **Faction et sections** (16/10) : sans argument = le volet J ; `info`, `inviter <joueur>` (face à face, 3 blocs — ce qu'envoie le menu R), `accepter`, `refuser`, `quitter`, `annoncer [section] <texte>`, au guichet (6 blocs) `verser <montant> [motif]`, `prime <#N°\|nom> <montant> <motif>`, `doter <section> <montant> [motif]` ; staff `admin liste`, `info <tag>`, `creer <tag> <village\|organisation> <motif…> nom=<nom>` (Admin), `chef <tag> <#N°\|aucun> <motif…>` (Modo), `section <tag> <modèle\|libre> <#N° chef> <motif…> [nom=<nom>]` (Modo), `cacher <section> <oui\|non> <motif…>` (Modo), `dissoudre <section> <motif…>` (Admin), `caisse <section> <ajouter\|retirer\|fixer> <n> <motif…>` (Admin) ; journal `FACTION` — [`../gameplay/FACTION.md`](../gameplay/FACTION.md) §15 |
| `/cloche` · `/voliere` (`volieres`) · `/missive` | — | Staff | `shinobicore.use` (grade staff vérifié dans le code) | **Cloche du village et volières** (16/10, staff, journal `STAFF`) : `/cloche poser <village> [rayon] ["lieu"]` (Modo, vise le meuble à 6 blocs), `liste` (Helper), `retirer <id>`, `taire <village> [minutes]` (Modo, 0 = rendre la parole, retire la ligne en cours) ; `/voliere poser "<nom>" <petite\|grande\|tour\|palais> <village>` (Modo, vise le perchoir), `liste` (Helper), `vider <id> "<motif>"`, `retirer <id> "<motif>"` (Admin) ; `/missive lire <id> "<motif>"` (Admin, motif obligatoire). Les joueurs n'ont pas de commande : menu R ou clic droit sur le meuble — [`../gameplay/CLOCHE_ET_MISSIVES.md`](../gameplay/CLOCHE_ET_MISSIVES.md) §10 |

### 2.1 bis Syntaxe `#N°` (Player ID) dans les commandes staff

Partout où une commande staff attend un joueur, on peut écrire le **N° du personnage** précédé d'un dièse :
`#00042` ou `#42` (zéros facultatifs, dièse obligatoire pour ne pas confondre avec un pseudo numérique).
Résolution commune : `character/PlayerIds` (`online(sender, arg)` = le joueur qui joue **ce** personnage ;
`character(sender, arg)` = le personnage, même hors ligne ; `ownerByRef` = le compte).

- **ShinobiCore** : `/pid`, `/grade` (`voir` et promotion), `/stats` (`give|set|reset|jeton` — hors ligne pour
  le personnage), `/fiche edit|service|json`, `/xp`, `/ata voir|lever|appliquer`, `/execution liste|donner|retirer`,
  `/character list <joueur>`, `/ryo give|take|set`, `/carte donner`, `/meteo … <joueur>`,
  `/cinematic … <joueur>`, `/playemote <emote> <joueur>`, `/testfeuille give`, `/vanish <cible>`.
- **ShinobiAbilities** : `/sa` (cibles joueur), `/sa mobility path <#N°> …`, `/parchemin donner|seance`.
- **Commandes vanilla** `/tp`, `/teleport`, `/spectate`, `/kick`, `/gamemode`, `/clear`, `/give`, `/effect`, `/kill` :
  le `#N°` est remplacé par le pseudo avant exécution (`character/PlayerIdCommandAlias`, joueur en ligne seulement).
- **Réservé au staff** : la console, `reborn.staff.*`, `shinobicore.staff` ou op. Pour un joueur, `#N°` ne
  résout rien (sinon il pourrait sonder les N° pour retrouver des noms — le système « rencontrer » garde les
  noms secrets). Le N° n'est montré qu'au joueur lui-même (fiche) et au staff.

### 2.2 ShinobiAbilities

| Commande | Alias | Pour | Perm. | Ce qu'elle fait |
|---|---|---|---|---|
| `/menu` | `hub`, `shinobimenu` | tous | `shinobiabilities.use` | Menu Shinobi (techniques, liaisons, mobilité) |
| `/techniques` | `technique`, `jutsus` | tous | `shinobiabilities.use` | Techniques connues |
| `/shinobiabilities` | `sa`, `sja` | staff | `shinobiabilities.use` | Racine : `reload`, `preview`… |
| `/bibliotheque` | `biblio` | staff | `shinobiabilities.staff` | `creer <nom>`, `regler`, `tirage`, `suppr`, `liste` |
| `/parchemin` | `parchemins` | staff | `shinobiabilities.staff` | `donner`, `slots`, `seance`, `attente`, `valider`, `refuser`, `concentration` |
| `/trainingground` | `parkour`, `entrainement` | tous / staff | `shinobiabilities.use` | Parcours qui débloquent la mobilité |
| `/mobility` | `mob`, `voie` | staff | `shinobiabilities.staff` | `path <perso> <none\|one\|two>` |
| `/trail` | `piste` | staff | `shinobiabilities.staff` | Pistes de déplacement |

### 2.3 Autres plugins

| Plugin | Commande | Alias | Perm. | Ce qu'elle fait |
|---|---|---|---|---|
| ShinobiCombat | `/resetcd [joueur]` | — | `shinobicombat.resetcd` | Remet les recharges à zéro (test) |
| ShinobiCombat | `/scendurance [joueur] <n>` | — | `shinobicombat.endurance` | Débite l'endurance (pont MagicSpells) |
| ShinobiCombat | `/lookdown` | — | `shinobicombat.lookdown` | Force la caméra vers le sol |
| ShinobiLearning | `/academy` | `academie`, `ecole` | `shinobilearning.use` | Cursus de l'académie, outils d'instructeur |
| ShinobiLearning | `/squad` | `equipe`, `team` | `shinobilearning.use` | Équipe Genin, transmission sensei |
| ShinobiSense | `/sense [suppress]` | `sentir`, `perception` | `shinobisense.use` | Percevoir le chakra alentour |
| ShinobiTail | `/tail` | `biju`, `bijuu` | `shinobitail.use` | Démons à queues : statut + outils MJ |
| RebornOst | `/ost play\|playat\|playglobal\|stop` | — | `reborn.ost.broadcast` | Diffusion musicale |

### 2.4 ShinobiRadio (`/radio`, 14/10)

Permission de la commande : `shinobiradio.use` (tous). `<joueur>` : pseudo, nom RP du personnage actif, ou `#N°` pour
le staff. Tout est revérifié côté serveur (distance 3 blocs, oreillettes, états, grade).

| Sous-commande | Pour | Ce qu'elle fait |
|---|---|---|
| `creer <joueur>` | joueur (menu R) | Invite un joueur **à côté** (3 blocs) à créer une escouade avec soi (10 s pour accepter). Les deux portent une oreillette en état, sans escouade ; nom par défaut : le prénom du créateur |
| `ajouter <joueur>` | membre (menu R) | Invite un joueur à côté dans son escouade (8 au plus ; tout membre peut ajouter ; re-lie l'oreillette d'un membre qui en a une neuve) |
| `accepter` / `refuser` | invité (touches Entrée / Retour du mod) | Répond à l'invitation |
| `quitter` | membre (écran N) | Quitte l'escouade (l'oreillette est déliée ; le plus ancien devient créateur ; vide = dissoute) |
| `renommer <nom>` | créateur | Renomme (2 à 24 caractères) |
| `exclure <membre>` | créateur | Exclut un membre (UUID du compte ou du personnage, ou nom) ; son oreillette ne capte plus rien |
| `exclure-oreillette <membre>` | créateur | Exclut l'oreillette liée à ce membre (parade au vol), le membre reste |
| `dissoudre confirmer` | créateur | Dissout l'escouade |
| `oreillette couper\|prendre <joueur>` | joueur (menu R sur un joueur à terre / inconscient, à côté) | Coupe son oreillette (fil arraché) ou la prend (elle va dans la sacoche) |
| `info <joueur>` | Helper | Oreillette (n°, état, propriétaire), escouade, membres, oreillettes exclues |
| `ecouter <joueur> [minutes] <motif>` | Helper (Poste de garde : fiche › Plus › « Écouter la radio… ») | Écoute l'escouade du joueur (défaut 10 min, max 60), **sans pouvoir parler**, motif obligatoire, journalisé ; le joueur n'est pas prévenu |
| `stop` | Helper | Arrête l'écoute |
| `couper <joueur> [minutes] [motif]` | Modérateur | Interdit d'émettre (défaut 30 min ; en mémoire, perdu au redémarrage) |
| `retirer <joueur> [motif]` | Modérateur | Retire le joueur de son escouade |
| `donner <joueur>` | Admin | Donne une oreillette neuve (objet Nexo `oreillette`) |
| `reparer [joueur]` | Modérateur ou `shinobiradio.artisan` | Répare l'oreillette coupée portée (ou en main) |

En mode staff, `/radio` n'accepte que `ecouter`, `stop`, `couper`, `retirer`, `info`, `donner`, `reparer`
(`ModeStaff`, §4 bis.7).

---

## 3. Permissions

### 3.1 Grades du staff (Poste de garde)

`StaffGrades` lit des permissions, pas des groupes :

| Niveau | Permission | Nom affiché |
|---|---|---|
| 4 | OP ou `reborn.staff.owner` | Owner |
| 3 | `reborn.staff.admin` | Admin |
| 2 | `reborn.staff.modo` | Modérateur |
| 1 | `reborn.staff.helper` | Helper |
| 0 | — | (pas staff) |

Chaque action du Poste de garde exige un niveau minimum (ex. téléportation = Helper, valider un RPK =
Modérateur).
**Factions (16/10)** : voir l'outil F7 › Outils › Factions = Helper ; créer / corriger une section, nommer un chef de
faction = Modérateur ; créer, renommer ou dissoudre une faction, dissoudre une section, corriger une caisse, régler la
taxe d'office = Admin. Côté joueurs, les droits viennent du jeu (chef de faction, charges, grades de section, seuils du
village), jamais d'une permission.
**Cloche et volières (16/10)** : `/cloche liste`, `/voliere liste` = Helper ; poser une cloche ou une volière, retirer une
cloche, faire taire les cloches = Modérateur ; vider ou retirer une volière, lire une missive (`/missive lire`, motif) =
Admin. Qui sonne, qui écrit au palais, qui prend à une volière : seuils et droits de la Faction, jamais une permission.

### 3.2 Permissions de gameplay

| Permission | Effet |
|---|---|
| `shinobicore.staff` | Statut staff général (commandes staff, messages RPK) |
| `reborn.staff.animation` | Mode animation `/modeanim` (animateur, MJ ; OP par défaut ; pas besoin d'un grade staff) |
| `shinobicore.admin`, `shinobiabilities.admin` | Contourne les vérifications (techniques non apprises, prérequis) |
| `shinobicore.medic` | Médic rang 1 : paume jusqu'à 60 % des PV, reçoit les `/aide` |
| `shinobicore.medic.expert` | Médic rang 2 : paume jusqu'à 100 %, lève toute ATA |
| `shinobicore.grade.staff` | Donne n'importe quel grade |
| `shinobicore.execution.<id>` | Possède l'exécution `<id>` (alternative à `/execution donner`) |
| `shinobicore.hopital`, `shinobicore.ata`, `shinobicore.zonerp` | Outils staff du KO |
| `shinobicore.stats.admin`, `shinobicore.character.admin`, `shinobicore.ryo.admin` | Outils staff des persos (`character.admin` : `/character list <joueur>`, `/character cinematic` ; la gestion des personnages se fait au Poste de garde, selon le grade staff, §4 bis.2) |
| `shinobicore.boutique` (op) | Outils staff des boutiques-lieux (`/boutique`) ; aussi accordé au grade Modérateur du Poste de garde |
| `reborn.fonction.<fonction>` | Remplit la condition `fonction=<fonction>` d'un article de boutique (ex. `reborn.fonction.anbu` pour le masque ANBU) — en attendant le menu Faction |
| `shinobicore.banque.admin` (op) | Outils staff de la banque (`/banque` sauf `voir`, `/testament enregistrer\|valider\|revoquer`) ; aussi accordé au grade Admin du Poste de garde |
| `shinobicore.map.tp`, `shinobicore.map.admin` | Carte : téléportation, édition |
| `shinobicore.emote.others`, `shinobicore.emote.admin` | Emotes sur autrui, catalogue |
| `shinobicore.mobility.zipline`, `shinobicore.mobility.grapple` | Accès à la tyrolienne et au grappin |
| `shinobiabilities.staff`, `shinobiabilities.fuinjutsu` | Outils staff Abilities, sceaux |
| `shinobilearning.instructor` | Instructeur de l'académie |
| `shinobisense.dojutsu.any` | Tous les dōjutsu, quel que soit le clan |
| `shinobitail.gm`, `shinobitail.admin` | Maître du jeu des démons à queues |
| `shinobiradio.use` | Radio (escouades, oreillette) — tous par défaut |
| `shinobiradio.artisan` | Réparer une oreillette coupée (`/radio reparer`) — OP par défaut, à donner aux artisans |
| `pv.activation.radio` | Activation « radio » de Plasmo Voice (déclarée par ShinobiRadio, **tous** par défaut ; les conditions RP sont vérifiées à chaque paquet) |

> ⚠️ **Les OP ont toutes les permissions non déclarées** (défaut Bukkit). C'est pratique en test mais
> trompeur : un OP « a » toujours toutes les exécutions et le rang médic expert.

---

## 4. Canaux réseau mod ⇄ plugins

Tous les échanges entre le mod `reborn-hud` et les plugins passent par des **custom payloads** Minecraft.
Convention : charge utile = **JSON UTF-8 brut** (sans préfixe de longueur), sauf mention contraire.

| Canal | Sens | Côté serveur | Côté mod | Transporte |
|---|---|---|---|---|
| `reborn:auth` | C → S | `guardian…AuthChannelListener` | `integrity/AuthPayload` | Play-token au JOIN (octets bruts) |
| `reborn:character` | ⇄ | `character.select.CharacterSelectManager` | `menu/character/CharacterPayload` | Liste des persos (âge, taille, sexe, apparence), sélection, création |
| `reborn:skins` | S → C | `CharacterSelectManager` | `skin/SkinPayload` | Apparence RP de chaque joueur (`<uuid>\n<SkinSpec>`) |
| `reborn:creatorpack` | S → C | `creator.CreatorAssetManager` | `skin/CreatorPackPayload` | Assets du créateur de perso |
| `reborn:vitals` | S → C | `character.VitalsPush` | `runtime/VitalsPayload` | 4 entiers bruts (PV, PV max, chakra, chakra max) ; **+ 4 entiers** pour un mod qui déclare `reborn:nourriture` (14/10) : satiété ‰, satiété à venir ‰ (repas en cours), pause de la régénération (s), drapeaux (1 régénère, 2 affamé, 4 FAIM, 8 ATA, 16 satiété figée) — un ancien mod lit 4 entiers exactement, il ne reçoit donc jamais les champs ajoutés |
| `reborn:nourriture` | S → C | `nourriture.NourritureCanal` | `nourriture/NourritureClient` | Préparer, manger, tendre, plat en main (§4 ter) |
| `reborn:modifs` | S → C | `modificateur.ModificateurRegistry` | `nourriture/NourritureClient` | Effets actifs visibles (modificateurs) : `{"mods":[{"id","nom","ic","b","val","reste","duree","pause"}]}` (§4 ter) |
| `reborn:monnaie` | S ⇄ C | `monnaie.MonnaieCanal` | `monnaie/MonnaieClient` | Bourse, « Combien ? », fouille d'un corps (§4 quinquies) |
| `reborn:faction` | S ⇄ C | `faction.FactionCanal` | `faction/FactionClient` | Volet J (vue filtrée par profil et par contacts), invitations, état du menu R, outil staff Factions (§4 septies) |
| `reborn:communication` | S ⇄ C | `communication.CommCanal` | `communication/CommClient` | Cloche du village et volières : points proches pour le menu R, écrans (cloche, volière, lettre), bande du haut (annonce, missive, palais) (§4 octies) |
| `reborn:banque` | S ⇄ C | `banque.GuichetCanal` | `monnaie/MonnaieClient` | Écran du guichet, échange garanti, guichetier (§4 quinquies) |
| `reborn:stats` | ⇄ | `stats.StatsChannel` | `menu/stats/StatsPayload` | Fiche shinobi (points, stats, Mérite, techniques ; `pid` = N° du personnage, envoyé au seul joueur) |
| `reborn:tablist` | S → C | `lore.TabListManager` | `menu/tablist/TablistPayload` | Tab list RP |
| `reborn:tirage` | ⇄ | `character.LeafTestManager` | `menu/tirage/TiragePayload` | Test de la feuille : S2C `confirm\|<clan>`, `<NATURE>\|<clan>\|<AUTRES>` (AUTRES = natures déjà éveillées, virgules ; 14/10) ou `deny` ; C2S `go` |
| `reborn:ko` | S → C | `ko.KoHudSync` | `ko/KoPayload` | État KO / ATA (binaire) |
| `reborn:ko_event` | S → C | `ko.KoHudSync` | `ko/KoEventPayload` | Cartes d'événement (À terre, K.O., Second souffle…) |
| `reborn:volonte` | ⇄ | `ko.VolonteQte` | `ko/VolontePayload` | QTE Volonté : `avail`, `start`, `result`, `ok`, `fail`, `deny` |
| `reborn:duel` | S → C | `duel.DuelService` | `duel/DuelClient` | Duel consenti : `invit` / `invit_fin`, `attente` / `attente_fin`, `debut` (`adv`, `advId`, `cr`, `duree`, `tech`), `etat` (`d`, `max`, `reste`, chaque seconde), `fin` (`issue` victoire / defaite / nul / annule, `adv`, `raison`) ; réponses par commandes `/duel` ; sans mod : tchat cliquable et titres |
| `reborn:corps` | S → C | `ko.action.CorpsService`, `DeathScene` | `ko/CorpsClient` | Corps proches + statut RPK (`b`), personnes en douleur (`dl`, 14/10), exécutions possédées (`ex`), corps au sol (`cp`), son propre état (`me` : douleur, porté, jauge de lutte, exécution en cours), scène de mort (`mort`, `fin`) |
| `reborn:staff` | ⇄ | `staff.panel.StaffPanel` | `staff/StaffPayload` | Poste de garde (alertes, joueurs, chat, journal, Infirmerie ; `pid` dans chaque joueur / alerte / fiche) |
| `reborn:inventory` | ⇄ | `inventory.InventoryManager` | `menu/inventory/InventoryPayload` | Sacoche RP. C2S : `open`, `swap:<A>:<B>`, `bag:equip:<ref>` / `bag:unequip`, `cos:equip:<ref>:<SLOT>` / `cos:unequip:<SLOT>`, `arme:equip:<ref>` / `arme:unequip` / `arme:basculer` (dégainer / rengainer), `drop:<ref>`, `del:<ref>`, `use:<ref>`, `pick:<uuid de l'objet au sol>` (ramassage). S2C : JSON (`ouvrir`, capacité, poids, `palier`, `poidsMax`, `lecture`, `armeDegainee`, `hotbar`, `bag`, `equipped` dont `ARME` ; chaque objet porte `rarity`, `arme`, `unique`) — `ouvrir:false` = rafraîchir sans ouvrir |
| `reborn:cosmetics` | S → C | `InventoryManager` | `cosmetic/CosmeticsPayload` | Cosmétiques portés, dont l'arme à l'étui (`ARME`, ancrée au bassin) |
| `reborn:notif` | S → C | `inventory.Notifs` | `notif/NotifClient` | Canal générique (13/10) : `gain` / `perte` (`q`, `nom`, `rar`, `mat`, `model`, `suite`), `msg` (`ton` refus / info / palier / etat, `kj`, `gras`, `texte`, `ic` = picto d'effet au lieu d'un kanji, 14/10) → pile en bas à droite ; `poids` (`cur`, `max`, `palier` 0-3, `libres`, `verrou` ; poussé aussi en créatif avec `palier` 0 — 15/10) → indicateur de surcharge, sprint coupé, invite de ramassage ; `arme` (`degainee`) |
| `reborn:boutique` | ⇄ | `boutique.BoutiqueService` | `boutique/BoutiqueClient` | Boutiques-lieux (15/10) : tailleur (caméra d'essayage), bazar (§4 sexies) |
| `reborn:shop` | ⇄ | `shop.ShopManager` | `menu/shop/ShopPayload` | **Ancienne échoppe** (plus ouverte par l'Échap depuis le 15/10) : `open` et `equip:` restent ; les achats `buy*` sont refusés quand les boutiques-lieux tournent (« L'échoppe a déménagé ») |
| `reborn:map` | ⇄ | `map.MapChannel` | `map/MapPayload` | Carte du monde |
| `reborn:meteo` | S → C | `meteo.MeteoCommand` | `meteo/MeteoPayload` | Météo visuelle : `byte type` (0 clair, 1 pluie, 2 sable, 3 brume, **4 blizzard, 5 tempête de sable** — 15/10) + `float intensité` 0..1 ; renvoyé à chaque changement de la météo **effective** du joueur (zones). Un mod antérieur ignore 4 et 5 (clair) ; le mod récent ignore d'éventuels octets en plus |
| `reborn:typing` | ⇄ | `chat.TypingManager` | `chat/TypingPayload` | Indicateur « en train d'écrire » |
| `reborn:emote` | ⇄ | `emote.EmoteManager` | `emote/EmotePayload` | Jouer une emote (visible par les proches) |
| `reborn:emotepack` | S → C | `EmoteManager` | `emote/EmotePackPayload` | Distribution des fichiers d'emotes au JOIN |
| `reborn:parchemin` | ⇄ | `shinobiabilities.parchemin.LibraryService`, `techniques.ConcentrationGame` | `parchemin/ParcheminPayload` | Bibliothèques, lecture, séances, concentration (`conc`) |
| `reborn:run` | ⇄ | `shinobiabilities.mobility.RunChannelListener` | `animation/NarutoRunPayload` | Course de chakra (1 octet : 1 démarrer, 0 arrêter) |
| `reborn:naruto` | C → S | `RunChannelListener` | (ancien nom) | Alias accepté pour les vieux mods |
| `reborn:allure` | ⇄ | `shinobiabilities.mobility.Allure` | `allure/AllurePayload` | Trois allures (14/10, octets bruts). C2S : `2` sprint / `0` arrêt + n° de demande. S2C `0` réglages (actif, marche, trot, sprint) ; S2C `1` état (allure accordée, endurance 0-1, drapeaux neutre / essoufflé, n° de demande) — [`../gameplay/DEPLACEMENTS.md`](../gameplay/DEPLACEMENTS.md) |
| `reborn:anim` | ⇄ | `shinobiabilities.mobility.AnimRelayListener` | `animation/AnimSyncPayload` | Synchronisation d'animations |
| `reborn:eau` | S → C | `shinobiabilities.mobility.MarcheEau` | `eau/EauPayload` | **Marche sur l'eau** (15/10, octets bruts) : `0` réglages (actif, chakra/s immobile, marche, trot, sprint) ; `1` permis (0/1 + raison : 0 aucune, 1 pas appris, 2 chakra, 3 état, 4 hors jeu, 5 surcharge, 6 désactivé). Envoyé seulement aux clients qui écoutent le canal ; les ondes vues des autres ne passent pas par le réseau — [`../gameplay/DEPLACEMENTS.md`](../gameplay/DEPLACEMENTS.md) §10 |
| `reborn:meditation` | S → C | `character.MeditationManager` | `meditation/MeditationPayload` | **Halo de méditation** (18/10, octets bruts) : `0`, nombre (int), puis pour chaque méditant à moins de `meditation.halo.portee` (48) blocs, destinataire compris : identifiant réseau de l'entité (int) et couleur RGB (int). Envoyé au début / à la fin d'une méditation et toutes les 10 ticks **seulement si la liste change** ; liste vide une fois quand il n'y a plus personne |
| `reborn:combat`, `reborn:combatin` | ⇄ | `shinobicombat.net.CombatChannel` | `combat/CombatPayload`, `CombatInputPayload` | HUD combat, entrées M1 / parade |
| `reborn:byakugan` | S → C | `shinobisense.ByakuganVision` | `byakugan/ByakuganPayload` | Vision Byakugan |
| `reborn:ost`, `reborn:ost_request` | ⇄ | `ost.plugin.network.OstChannel` | `ost/network/*` (mod-ost) | Musiques |
| `reborn:controls` | client | — | `keybind/*` | Interne au mod |
| `reborn:annonce` | S → C | `staff.outils.EffetsStaff` | `staff/EffetsStaff` | Annonce globale ou personnelle (§4 bis.3) |
| `reborn:ecran` | S → C | `staff.outils.EffetsStaff` | `staff/EffetsStaff` (+ `mixin/CameraSeismeMixin`) | Écran noir forcé, séisme (§4 bis.4) |
| `reborn:plaques` | S → C | `staff.outils.ModeStaff` | `staff/PlaquesStaff` | Plaques staff au-dessus des têtes, en mode staff (§4 bis.5) |
| `reborn:radio` | S → C | `shinobiradio.Canal` (ShinobiRadio) | `radio/RadioClient` | Radio en escouades : état, qui parle, invitation, écoute staff (§4 quater) |

---

## 4 quater. Radio en escouades — protocole (14/10)

> ShinobiRadio (`com.reborn.shinobiradio`) ⇄ mod `fr.reborn.hud.radio` + `fr.reborn.hud.voice.RadioVoix`. JSON UTF-8
> brut, champs optionnels, inconnus ignorés. Fiche : [`../gameplay/RADIO.md`](../gameplay/RADIO.md). **La voix ne
> passe pas par ce canal** : elle passe par Plasmo Voice (UDP) ; ce canal ne porte que de quoi afficher.

**`reborn:radio`** (S → C) :

| `t` | Champs | Quand / affichage |
|---|---|---|
| `etat` | `ore` (`aucune` · `neuve` = pas liée · `ok` · `coupee` · `exclue`), `num` (n° de l'oreillette), `volee` + `proprio` + `proprioId` (je porte l'oreillette d'un autre), `canal` (escouade entendue si ce n'est pas la mienne), `h` (je suis à terre ou porté : haché), `muet` (inconscient / mort), `staff` (mode staff), `coupe` (coupé par le staff), `esc` : `id`, `nom`, `n`, `max`, `createur` (suis-je le créateur), `membres` : `id` (compte), `perso`, `nom`, `court` (« Aki U. »), `etat` (`en_ligne`, `a_terre`, `inconscient`, `mort`, `hors_ligne`), `ore` (`ok`, `coupee`, `sans`, `prise`, vide hors ligne), `createur`, `moi` | À la connexion et à chaque changement (recalculé toutes les 5 ticks, envoyé seulement s'il change) → écran N, menu R, élément « Escouade » |
| `parle` / `tait` | `src` (UUID de la source Plasmo Voice), `id` (UUID du compte dont on montre la tête : le **propriétaire** de l'oreillette), `nom` (court), `h` (haché : l'émetteur ou moi à terre) / `src`, `compte` | Quelqu'un parle sur mon escouade / se tait → une ligne [tête] Nom, effacée 1,5 s après ; clic de squelch |
| `invit` / `invit_fin` | `de` (court), `esc`, `creation` (bool), `s` (10) | « Aki U. t'ajoute à l'escouade Kagerō — Entrée accepter · Retour refuser · 10 s » |
| `ecoute` / `ecoute_fin` | `esc`, `reste` (s) | Staff seulement : « écoute · Kagerō · 9:41 » au-dessus de l'élément Escouade |

Réponses : commandes `/radio …` (§2.4). Sans le mod : invitation au tchat (`/radio accepter|refuser`), messages au tchat.

**Plasmo Voice, serveur (`VoixRadio`)** : activation `radio` (« Radio (escouade) », icône
`plasmovoice:textures/icons/microphone_radio.png`, permission `pv.activation.radio` pour tous, poids
`voix.activation-poids`), `setProximity(false)`, `setTransitive(false)`, **conditions à chaque paquet** (personnage,
oreillette portée en état et liée à une escouade existante, numéro non exclu, ni inconscient, ni mort RP, ni mode staff,
ni coupé). Une **source de diffusion** par émetteur sur la **ligne `radio`** (volume à part dans le menu de Plasmo Voice,
icône masquée), destinataires = porteurs connectés d'une oreillette de l'escouade, non inconscients, + staff à l'écoute ;
à terre ou porté, des trames de 20 ms sautent en rafales de 80 ms (`voix.hache-trames-perdues`). En plus, **chuchoté** :
la trame part sur une source joueur de la ligne `proximity`, distance `voix.chuchote-blocs` (4), sauf vers ceux qui
l'entendent déjà à la radio. Début / fin de chaque prise de parole → `parle` / `tait` et journal.

**Plasmo Voice, client (`voice/RadioVoix`)** : l'activation `radio` reçue du serveur est forcée en push-to-talk et prend
la touche Reborn « Radio : parler » (B) ; son icône d'activation est masquée (`HudActivationRenderEvent`) ; les
échantillons de la ligne `radio` passent par un passe-bande 300-3000 Hz, une saturation douce et un léger souffle
(`AudioSourceWriteEvent`) ; haché : trous de 60 ms en rafales, bitcrush 6 bits, échantillonnage tenu, souffle plus fort.

**Journal** (catégorie `RADIO`, **jamais de contenu vocal**) : `INVITATION`, `INVITATION_REFUSEE`, `INVITATION_EXPIREE`,
`CREATION`, `AJOUT`, `DEPART`, `EXCLUSION`, `OREILLETTE_EXCLUE`, `RENOMMAGE`, `DISSOLUTION` (créateur, vide, 30 jours),
`OREILLETTE_COUPEE`, `OREILLETTE_PRISE`, `OREILLETTE_REPAREE`, `EMISSION` (qui, escouade, durée, « haché »,
« oreillette d'autrui » — `journal.emissions`). Staff (catégorie `STAFF`) : `RADIO_ECOUTE` (escouade, motif, durée),
`RADIO_ECOUTE_FIN`, `RADIO_COUPURE`, `RADIO_RETRAIT`, `RADIO_DONNER`.

**L'oreillette** : objet dont l'id Nexo ou la clé de modèle est dans `oreillette.ids` (`oreillette`), portée dans
l'emplacement cosmétique `BOUCLE`. Données (PDC `shinobiradio:*`) : `numero` (jamais réutilisé), `escouade`, `proprio`
(personnage), `proprio_compte`, `proprio_nom`, `coupee`. Lore : « Oreillette radio · N° 12 · Coupée — à réparer chez un
artisan » (jamais le nom de l'escouade). Visuel : pack Nexo `reborn:item/oreillette` (`minecraft/mod-hud/tools/gen_oreillette_nexo.py`).

---

## 4 quinquies. Monnaie et banque — protocole (15/10)

> ShinobiCore `monnaie/`, `banque/` ⇄ mod `fr.reborn.hud.monnaie`. JSON UTF-8 brut S → C, texte C → S, sans préfixe.
> Fiche : [`../gameplay/MONNAIE_ET_BANQUE.md`](../gameplay/MONNAIE_ET_BANQUE.md) §15-§17.

**La bourse** est un objet (`PAPER`, item_model `reborn:bourse_plate|bourse_garnie|bourse_lourde`, PDC `reborn:ryo`
LONG, `reborn:bourse_id`, `reborn:rp_weight` 0, une pile) : le mod lit `custom_data.PublicBukkitValues."reborn:ryo"` et
peint le montant abrégé en or (`mixin/ItemCountBourseMixin`). Le JSON d'objet de `reborn:inventory` porte `"ryo":N`
pour une bourse.

**`reborn:monnaie` S → C** :

| `t` | Champs | Effet |
|---|---|---|
| `bourse` | `n` | Ce que le joueur porte (ligne « Sur toi » de la sacoche ; la carte de l'Échap, devenue BOUTIQUE le 15/10, ne l'affiche plus) |
| `combien` | `mode` (`ranger`), `titre`, `question`, `max`, `action` | Ouvre « Combien ? » par-dessus l'écran courant ; la réponse part en `<mode>:<n>` |
| `fouille_prog` / `fouille_prog_fin` | `nom`, `s` | Filet « Fouille de Kenta · 2 s · ne bouge pas » au-dessus de la barre |
| `fouille` | `cible`, `etat`, `uuid`, `bourse`, `reste`, `plafond`, `pourcent`, `extraSlots`, `hotbar[9]`, `bag[]` (objets au format sacoche) | Ouvre (ou rafraîchit) la sacoche du corps en mode fouille |
| `fouille_ferme` | — | Ferme la fouille (cible relevée, partie, trop loin) |

**`reborn:monnaie` C → S** : `poser:<n>` (bourse au sol), `ranger:<n>` (coffre posé ouvert), `vol:<n>` (bourse du
corps fouillé), `fouille:prendre:<H0..H8|B0..>`, `fouille:fermer`.

**`reborn:banque` S → C** : `{"t":"guichet","ouvrir","onglet","banque","titulaire","numero","guichetier"?,"allie",
"fraisAllie","compte","bourse","plafond","palier","paliers":[{n,nom,cout,cases,plafond}],"coffre":[objet|null],
"sacoche":[{"ref","item","refus"?}],"historique":[{"le","d","t"}],"solde"?:{"montant","semaine"},
"contacts":[{"ref","nom"}],"virement":{"pourcent","min","autre","plafondJour","deja"},"proches":[{"uuid","nom"}],
"echange"?:{"id","autre","moi":{"ryo","source","confirme","objets"},"lui":{…},"attenteSceau","guichetier"?,"frais",
"pourcent","recevoir"},"gele"?,"toast"?,"refus"?:{"ref","texte"}}` ; `{"t":"ferme"}` (guichet trop loin) ;
`{"t":"guichetier","actif"}` (menu R « Ouvrir son compte ») ; `{"t":"sceau","a","b","id"}` (au guichetier).

**`reborn:banque` C → S** : `ouvrir`, `fermer`, `onglet:<n>`, `deposer:<n>`, `retirer:<n>`,
`virer:<#N°|c:uuid>\n<n>\n<motif>`, `palier:<k>`, `coffre:depot:<ref>`, `coffre:retrait:<i>`, `echange:ouvrir:<uuid>`,
`echange:ryo:<n>:<bourse|compte>`, `echange:objet:<ref>`, `echange:retirer:<i>`, `echange:recevoir:<bourse|compte>`,
`echange:confirmer`, `echange:annuler`, `echange:sceller`, `echange:refuser`.

Autres entrées : clic droit sur un guichet (`PlayerInteractEvent` / `PlayerInteractEntityEvent`) ; `/corps fouiller
<joueur>` et `/corps fouiller corps:<uuid du mannequin>` (menu R) ; coffres posés (`InventoryClickEvent` : clic sur la
bourse du coffre = la reprendre, clic sur la sienne = « Combien veux-tu ranger ? ») ; `Ramassage` (touche F) ; `/tendre`.
Canal de la fiche staff : `reborn:staff` `profile` porte `eco` (`bourse`, `compte`, `palier`, `palierNom`, `plafond`,
`cases`, `occupees`, `coffre`, `solde`, `grade`, `soldeRecue`, `dernier`, `dernierTexte`, `gele`?, `testament`?) à la
place de `ryo` ; alertes de type `economie`.

**RBCoins** (Échap) : propriété système `-Dreborn.rbcoins=<n>` ou `-Dreborn.rbcoinsPath=<fichier>` posée par le launcher
au lancement (**pas encore envoyée** par le launcher : « — »).

## 4 sexies. Boutiques-lieux — protocole (15/10)

> ShinobiCore `boutique/` ⇄ mod `fr.reborn.hud.boutique`. JSON UTF-8 brut S → C, texte C → S, sans préfixe.
> Fiche : [`../gameplay/BOUTIQUES.md`](../gameplay/BOUTIQUES.md) §13.

**Entrée** : clic droit sur un **comptoir** (bloc exact, meuble Nexo à 1 bloc près, ou PNJ précis) → refus si la
boutique est fermée (« Fermé — revenez plus tard »), en KO / porté / étourdi / gelé / tenu, en mode staff ou moins de
5 s après un coup (donné ou reçu) ; tailleur : téléportation sur l'**estrade** libre la plus proche du comptoir
(≤ 12 blocs ; « Quelqu'un essaie déjà » si toutes sont prises) ; sans mod : message dans le tchat.

**`reborn:boutique` S → C** :

| `t` | Champs | Effet |
|---|---|---|
| `ouvrir` | `boutique` (`id`, `nom`, `genre` tailleur / bazar, `sous` « Konoha · Au comptoir : Hōsen »), `comptoir` (libellé), `bourse`, `appearance` (SkinSpec du personnage), `cam`? (`x`, `y`, `z` pieds sur l'estrade, `yaw` regard du client vers la caméra, `dist`, `haut`), `categories` [`{id, nom}`] (celles du comptoir, dans l'ordre), `articles` [`{id, nom, cat, genre` tenue / objet / vivre / carte`, prix, possede?, verrou?, raison?, item?` (format sacoche + `slot` = emplacement cosmétique)`}`], `toast`? | Ouvre l'écran (sans `cam` : point de vue calculé là où se tient le joueur, sinon repli sur voile) |
| `etat` | mêmes champs sans `cam` | Rafraîchit (après un achat : `toast` « §a… » / « §c… ») |
| `ferme` | — | Ferme l'écran (coup reçu, plus de 8 blocs de l'estrade ou du comptoir, boutique fermée / supprimée) |

Articles **cachés** au client (clan, village, sexe, pièce unique d'un autre) ; **grisés** avec `verrou` (« Réservé aux
Chūnin et au-dessus », « Réservé à la fonction ANBU », « Réservé à « X » ») et `raison` (« Ton grade : Genin »…).

**`reborn:boutique` C → S** : `fermer`, `etat`, `acheter:<id>`, `acheter_porter:<id>[\n<apparence>]` (tenue :
l'apparence complète avec la tenue ; objet : posé dans son emplacement cosmétique s'il est libre), `porter:<id>\n<apparence>`
(tenue de la garde-robe), `ranger\n<apparence>` (retirer la tenue portée). Ids : catalogue (`tenueh12`, `masque_hannya`…),
`vivre:<id>`, `carte:<id>`. Le serveur revérifie tout ; paiement **bourse** (`BourseService.debiter`, action `ACHAT`) ;
tenue → `ownedOutfits` (garde-robe) ; objet → sacoche (`refusReception` : « Libère une case ») ; vivres → `NourritureModule.acheterVivre` ;
cartes → `MapItem.give`.

## 4 septies. Faction et sections — protocole (16/10)

> ShinobiCore `faction/` ⇄ mod `fr.reborn.hud.faction` (et `staff/GardeFactions`). Canal `reborn:faction`, JSON UTF-8
> brut dans les deux sens, sans préfixe. Le client demande `{"a": …}`, le serveur répond `{"t": …}`. **Tout est
> revérifié et filtré côté serveur** : un inconnu n'est jamais nommé (ni nom, ni UUID, ni N°), une section cachée vue
> d'un non-membre est `{"q":true}` et rien d'autre. Fiche : [`../gameplay/FACTION.md`](../gameplay/FACTION.md).

**S → C**

| `t` | Contenu |
|---|---|
| `vue` | `ok` (sinon `raison`), `ouvrir`? (touche J), `faction{tag,nom,type,embleme,n,kage}`, `moi{nom,rang,rangNom,profil,chefFaction,chefSection,charge?,titre}`, `onglets[section?,village,sections?,annonces,gestion?]`, `section?{id,nom,insigne,cachee,places,n,chef,monGrade,monRang,chefNom,chefFem?,droits[],membres[personne+grade,gradeId,rang,chef?,rangVillage?],tenue{id,nom,accessoire},lieux[{nom,type,acces}],caisse?{solde,taux,lignes[{t,le,n}]},grades?[{id,nom,droits,n}],journal?[{t,le}]}`, `village{total,groupes[{rang,nom,total,connus[personne],inconnus}]}`, `sections?[{q:true} \| {id,nom,insigne,cachee,n,chef:personne,connus[personne+grade],caisse?}]`, `annonces[{le,texte,titre,auteur?,section?}]`, `gestion?{droits[],chef,taux,plafond,tresor?,taxeSemaine?,seuils[{id,nom,rang}],rangs[],sections[{id,nom,insigne,cachee,n,places,chefNom,caisse?}],charges[{id,nom,publique,droits,titulaires[{id,nom}]}],journal?,modeles[],candidats?[{id,nom,rang,section?}]}`, `peut{annoncerVillage,annoncerSection}`. Une **personne** = `{nom,connu}` ; si connue : `+ id` (personnage), `online`, `u` (UUID Minecraft, en ligne seulement), `moi?`, `publique?` |
| `proches` | `{pres[{id,u,nom,rang,dist,refus?}],loin[…],distance,n,places}` : personnes **connues** à 30 blocs (et en ligne plus loin si « inviter à distance ») |
| `etat` | `{section?,insigne?,inviter,refus?,organisation?}` : pour « Inviter dans … » du menu R (à la connexion et à chaque changement) |
| `invit` / `invit_fin` | `{de,section,insigne,faction,s,panneau}` : invitation chez l'invité (Entrée / Retour), 10 s face à face, 30 s panneau |
| `toast` | `{m,refus?}` |
| `staff` | `{grade,factions[{tag,nom,type,embleme,serveur,n,taux,chef?{id,nom,pid},sections[{id,nom,insigne,cachee,n,places,caisse,chefNom,grades[]}],charges[],journal[]}]}` |

**C → S** (`a`) : `open`, `close`, `refresh`, `etat`, `proches`, `inviter{p}`, `repondre{oui}`, `quitter`, `places{n}`,
`tenue{id,nom,acc}`, `grade_creer{nom}`, `grade_renommer{id,nom}`, `grade_suppr{id}`, `grade_ordre{id,sens}`,
`grade_droits{id,droits[]}`, `monter{p}`, `descendre{p}`, `exclure{p,motif}`, `section_creer{modele,nom,insigne,chef,places,cachee}`,
`section_cacher{id,v}`, `section_chef{id,p}`, `section_renommer{id,nom,insigne}`, `section_dissoudre{id,motif}`,
`taux{n}`, `seuil{id,rang}`, `charge_creer{nom}`, `charge_droits{id,droits[],publique}`, `charge_titulaire{id,p,oui}`,
`charge_suppr{id}`, `annoncer{section,texte}` ; staff : `staff`, `staff_close`, `staff_faction{tag,nom,type,embleme,chef,motif}`,
`staff_chef{tag,chef,motif}`, `staff_renommer{tag,nom,motif}`, `staff_dissoudre_faction{tag,motif}`, `staff_taux{tag,n,motif}`,
`staff_section{tag,modele,nom,chef,cachee,motif}`, `staff_section_maj{id,nom?,cachee?,places?,chef?,motif}`,
`staff_section_dissoudre{id,motif}`, `staff_caisse{id,op,n,motif}`. Refus → `toast` avec `refus`.

**Droits** — seuils du village : `voir_sections` (Chūnin), `cloche_village` (Jōnin), `cloche_sans_delai` (Commandant
Jōnin, défaut de la config `communication.cloche.sans-delai-des` tant que le village ne l'a pas réglé), `voliere_palais` (Chūnin),
`alertes_palais` (Jōnin), `prendre_voliere` (Genin) ; grade de section : `inviter`, `inviter_distance`, `exclure`,
`monter`, `voir_caisse`, `depenser`, `coffre_voir`, `coffre_retirer`, `cloche_section`, `journal` ; charge : `sections`,
`taxe`, `caisses`, `doter`, `journal`, `annonces`, `alertes_palais`, `inviter_org`. Le chef de section a tous les droits
de sa section ; le chef de faction tous ceux de sa faction.

## 4 octies. Cloche du village et volières — protocole (16/10)

> ShinobiCore `communication/` ⇄ mod `fr.reborn.hud.communication`. Canal `reborn:communication`, JSON UTF-8 brut dans
> les deux sens, sans préfixe. Le client demande `{"a": …}`, le serveur répond `{"t": …}`. **Tout est revérifié côté
> serveur** (droits de la Faction, distance 6,5 blocs, coût, anti-spam). Texte riche = segments `[["texte","" | "g" |
> "i" | "gi"], …]` (gras, italique seulement). Fiche : [`../gameplay/CLOCHE_ET_MISSIVES.md`](../gameplay/CLOCHE_ET_MISSIVES.md).

**S → C**

| `t` | Contenu |
|---|---|
| `points` | `l[{k:"cloche"\|"voliere",id,x,y,z,nom}]` : cloches que ce joueur peut sonner et volières à 48 blocs (envoyé toutes les 2 s s'il a changé) — menu R |
| `cloche` | `ouvrir`, `id`, `lieu`, `nom`, `titre` (signature imposée), `village` (sinon : chef de section), `max`, `sections[{id,nom,insigne,nouvelle?}]` (jamais une section cachée), `fixe?` (sa section), `grades[9]`, `libreDepuis`, `attenteCloche`, `attenteMoi`, `delaiMoi`, `sansDelai?` (`"grade"` \| `"animation"` : attentes à 0), `tue?`, `dangers`, `zone`, `concernes` |
| `compte` | `{zone,concernes}` (réponse à `compter`) |
| `annonce` | `{type,titre,texte,signe,s,village}` : la bande du haut chez les concernés présents |
| `annonce_fin` | le staff a tu la cloche : la ligne en cours se retire |
| `voliere` | `ouvrir?`, `onglet` (0 Écrire, 1 Missives ici), `id`, `nom`, `type`, `moi`, `now`, `bourse`, `max`, `adresseMax`, `destMax`, `garde`, `capacite`, `contacts[{ref:"c:<perso>",nom,sous,mc?}]`, `destinations[{id,nom,lieu,delai,cout,cond?,refus?}]`, `missives[{id,pour,autres[],toi?,signe?,de,le,scellee}]`, `peutPrendre`, `seuilPrendre`, `enMain?{id,pour,scellee}` |
| `envoye` | `{transfert}` : le brouillon s'efface |
| `lettre` | `{id,a[],lieu,heure,texte,signe?,dejaBrise,vientDeBriser}` : la missive ouverte |
| `alerte` | `{k:"missive",lieu,s}` (« un corbeau a déposé une missive pour toi à … ») ou `{k:"palais",s}` (sans auteur, sans contenu, sans destinataire) |
| `toast` · `ferme` | `{m,refus?}` · ferme l'écran ouvert |

**C → S** (`a`) : `ouvrir{id,missives?}`, `compter{id,tout,sections[],grades[]}`, `sonner{id,type,texte,tout,sections[],grades[]}`,
`envoyer{voliere,vers,dests[{ref}|{adresse}],texte,signee}`, `transferer{voliere,vers,dests[1]}` (la missive en main),
`prendre{id,lire?}`, `bruler{id}`. Clic droit sur le meuble (bloc de barrière ou entité) : ouvre l'écran ; clic droit avec
une missive en main : la lit (la première lecture brise le cachet).

**Règles** — sonner : seuil `cloche_village` du village (Jōnin) ou charge `annonces` → tout le village (visiteurs
compris) ou des sections ; droit de grade `cloche_section` → sa section seulement ; anti-spam 2 min par cloche, 10 min
par personne, 3 « danger » par heure et par village, levé en entier (seule `/cloche taire` refuse encore) pour le seuil
`cloche_sans_delai`, le chef de faction et le staff en mode animation (`sans-delai-animation`) — `cloche.sansDelai` =
`"grade"` ou `"animation"`, journal « sans délai (…) ». Volières : dans le pays toute volière ; hors du pays de tour à tour ;
le palais exige `voliere_palais` (Chūnin) ; prendre exige `prendre_voliere` (Genin) ; délai 1 min (même volière, palais),
1 min + 1 min par 250 blocs (pays, 8 min au plus), 1 min par 250 blocs (autre village, 20 min au plus) ; coût par copie
10 / 40 ryo, pris dans la bourse. Pleine, une volière brûle la plus ancienne ; non prise en 7 jours, brûlée.

## 4 nonies. États du joueur — API, matrice, protocole (17/10)

> ShinobiCore `etat/` + `api/EtatService` + `api/etat/*` ⇄ mod `fr.reborn.hud.etat`. Conception et statut par étape :
> [`ETATS_JOUEUR.md`](./ETATS_JOUEUR.md) (§8 API, §9 migration). JSON UTF-8 brut, champs inconnus ignorés.

**Service** `api/EtatService` (ServicesManager, `@Stable`, `EtatService.get()` = null si absent → repli de l'add-on) :

| Méthode | Rôle |
|---|---|
| `etat(compte)` | Instantané `Etat` : `principal()` (14 valeurs, §4.3), `vital()` / `controle()` / `cadre()`, `lien()`, `modificateurs()` (posés + dérivés : `ata`, `surcharge`, `en_combat`, `gel_staff`, `invisible`, `porteur`, `occupe`), `capacites()` |
| `peut(compte, Capacite)`, `verifier(compte, Capacite[, param])`, `refus(compte, Capacite)` | « Peut-il X ? » — `Verdict(niveau 0/1/2, raison, ligne)` ; `verifier(…, TECHNIQUE, Boolean mudras)` : une technique sans mudra passe une entrave |
| `peutCibler(source, cible, étiquettes)` | Effets de zone, soins, onde au sol : faux pour mode staff, invisible, mort en scène, cinématique, sélection, monde intérieur, support d'armure, créatif / spectateur |
| `transition(joueur, Couche, valeur, Source, Lien[, duréeMs])` | Contrôle **tenu par le service** : `ETOURDI`, `GARDE`, `ENTRAVE`, `CONTROLE`, `CORPS_VIDE[.MONDE_INTERIEUR]` (lien + durée ; `LIBRE` lève tout, `"-GARDE"` lève seulement la garde) ; Vital et Cadre : **annonce** (veto) — renvoie `APPLIQUEE`, `INCHANGEE`, `REFUSEE` (une valeur plus faible n'écrase pas une plus forte ; un seul détenteur, §5.2), `VETO` |
| `poser` / `retirer` / `dissiper(famille, puissance)` | Registre des modificateurs (`ModificateurSpec(id, intensité, duréeMs)`) |
| `facteur(joueur, clé, vitesse, saut)` | Multiplicateurs physiques d'un add-on (manteau du bijū : clé `biju`) ; `1, 1` retire |
| `appliqueEffetsPhysiques()` | Vrai = l'applicateur unique pose vitesse, saut, faim, invulnérabilité : l'add-on ne fige plus lui-même |
| `occuper(joueur, action, cible, tolérance, Set<FinSi>, surInterruption)` / `occupation(compte)` | Action longue `OCCUPE(action)` : interrompue (`BOUGE`, `FRAPPE`, `KO`, `DOULEUR`, `CONTROLE`, `CADRE`, `CIBLE`, + déconnexion, changement de personnage) ; `terminer()` = fin normale |
| `emprisonner(joueur, duréeMs, zone, motif, Source)` / `liberer` / `peine` | Cadre `PRISON` (peine par personnage, temps réel) |

**Événements** (`api/event`) : `EtatChangeEvent` (avant, **annulable** — la chaîne KO l'annonce à l'entrée en KO, source
`coup mortel` / `exécution` / `chakra à 0` ; ShinobiTail l'annule pour le sauvetage du bijū), `EtatChangedEvent` (après,
au plus un par tick et par joueur), `ModificateurPoseEvent` (avant, annulable, intensité / durée modifiables),
`ModificateurRetireEvent` (`fin`, `retire`, `dissipe`, `ko`). `CharacterDamageEvent#contexte()` : `DegatContexte`
(étiquettes, technique, attaquant, défense, `contourneePar`, multiplicateur).

**`etats.yml`** (ShinobiCore, relu par `/sc reload` et `/etats recharger`) : `vitesse-plancher` (0,25), `sync`
(`reborn:etat`), `journal` (catégorie `ETAT`), `applicateur` (`false` = repli sur les anciens écrivains de vitesse, saut,
faim, `Invulnerable`) ; `lignes` = la matrice (§4.6) — une ligne par valeur de couche (`vital.*`, `controle.*`,
`cadre.*`, variantes `controle.PORTE.GORGE`, `controle.ENTRAVE.EXECUTION`, `controle.CORPS_VIDE.MONDE_INTERIEUR`,
`cadre.HORS_JEU.CINEMATIQUE`), par modificateur dérivé (`mod.*`) et par contexte (`ctx.volonte`, `ctx.hopital`,
`ctx.se_debattre`, `ctx.duel_technique`) ; clés `raison`, `refuse` / `sauf`, `limite`, `raisons`, `accorde`, `force`,
`si-vital`, `vitesse`, `saut`, `faim-tenue` ; groupes `TOUT`, `FRAPPER`, `DEPLACEMENT`, `SACOCHE`. L'**ordre du fichier**
est la priorité (la raison affichée est celle de la première ligne qui refuse). `modificateurs` = types du registre
propres aux états (`menottes`, `hydratation`, `deshydrate`, `jutsu_*`, `epuisement_1..4`, `defaite`, `defaite_cecite`),
clés en plus de §4 ter : `etat-ligne` (ligne de la matrice activée), `potions` (`"BLINDNESS:0"` — effets courts
rafraîchis par l'applicateur, joueur seulement), `defense` (`type` `INTANGIBLE` / `REDUCTION` / `ABSORPTION` / `RENVOI` /
`ESQUIVE` / `IMMUNITE`, `contre`, `sauf`, `faiblesse`, `conditions` `CHAKRA_POSITIF` / `PAS_MENOTTE` / `PAS_DESHYDRATE`,
`valeur`), `immunise` (familles ou types refusés) ; `config.yml` garde la priorité sur un même id.

**Capacités** (`api/etat/Capacite`) : `BOUGER` `TROTTER` `SPRINTER` `SAUTER` `MOBILITE` · `FRAPPER_MAINS` `FRAPPER_ARME`
`FRAPPER_PROJECTILE` `GARDER` `TECHNIQUE` `CANALISER` · `UTILISER_OBJET` `OUVRIR_SACOCHE` `MODIFIER_SACOCHE` `LACHER`
`RAMASSER` `DEGAINER` `CONSOMMER` · `INTERAGIR` `PORTER` `EXECUTER` `SOIGNER` `FOUILLER` · `ETRE_PORTE` `ETRE_FOUILLE`
`ETRE_EXECUTE` · `PARLER` `VOCAL` `RADIO` `EMOTE` `COMMANDES` · `VISIBLE` `CIBLABLE` `NOM_VISIBLE` · `TOMBE_KO`
`BLESSURES` `XP_COMBAT` · `CHANGER_PERSO` `SE_DECONNECTER_SANS_SUITE` `TELEPORTE_PAR_COMMANDE` `MODE_STAFF` `DUEL` ·
spéciales `SE_DEBATTRE` `VOLONTE` `HOPITAL` `DISSIPER` `ROMPRE_LIEN` `VOLER`.

**`reborn:etat`** (S → C) : à chaque changement (regroupé par tick), au JOIN (+2 s) et au changement de personnage —
`{"v":1,"seq":12,"p":"A_TERRE","c":{"vital":"A_TERRE","ctrl":"LIBRE","cadre":"MONDE"},"cap":{"bou":1,"tro":0,"spr":0,
"sau":0,"mob":0,"fra":0,"gar":0,"tec":0,"ram":0,"int":0,"exe":0,"par":1,"voc":1,"debattre":0,"sac":"L"},"vit":0.7,
"saut":0,"lien":{"type":"PORTE","nom":"…"},"mods":[{"id":"ata","n":2,"fin":0}],"raison":{"int":"Tu es à terre."}}` —
capacités `2` permis, `1` limité, `0` refusé ; `sac` `M` / `L` / `0` ; `mods` = modificateurs **dérivés** seulement (le
registre garde `reborn:modifs` et ses icônes) ; `fin` en ms. Le mod (`EtatClient`) bloque en avance : déplacement
(`bou`), saut (`sau`), coup sur une entité visée (`fra`), sprint / trot (`spr` / `tro`), garde (`gar`), menu R (`int`),
ramassage (`ram`), invite d'exécution (`exe`), mode photo et F1 sous un voile imposé (KO, mort, écran noir) — seulement
dans un cadre de jeu (monde, entraînement, duel, prison). Remis à zéro à la déconnexion. Les anciens canaux
(`reborn:ko`, `reborn:corps` `me`, `reborn:notif` `poids` / `verrou`, drapeaux de `reborn:allure`) restent émis.

**Journal** : catégorie `ETAT` (« États », `logs/etat-AAAA-MM-JJ.log`) — `ETAT_CONTROLE` (entrave, contrôle, corps vide ;
pas la garde ni l'étourdi), `ETAT_CADRE` (sauf connexion), `VETO`, `LIEN_ROMPU`, `OCCUPE_INTERROMPU`, `DEFENSE` (une par
combat et par défense), `PRISON`, `PRISON_FIN`, `EVASION`, et les simples passages de la chaîne KO (`DOULEUR`,
`FIN_DE_DOULEUR`, `AGONIE_…`, `INCONSCIENT…`, `REVEIL`, `SECOND_SOUFFLE`, `PORTE`, `DEPOSE`, `SE_DEGAGE`) ; `STAFF`
`ETATS_POSER`, `ETATS_LEVER`, `ETATS_PRISON`. Poste de garde : alerte « Évasion ».

---

## 4 ter. Nourriture et modificateurs — protocole (14/10)

> ShinobiCore `nourriture/` et `modificateur/` ⇄ mod `fr.reborn.hud.nourriture`. JSON UTF-8 brut, champs optionnels,
> inconnus ignorés des deux côtés. Fiches : [`../gameplay/NOURRITURE.md`](../gameplay/NOURRITURE.md) §13,
> [`ETATS_JOUEUR.md`](./ETATS_JOUEUR.md) §4.4.

**`reborn:nourriture`** (S → C) :

| `t` | Champs | Affichage |
|---|---|---|
| `prep` / `prep_fin` | `nom`, `ic`, `dur` (s), `fait` (s) / `ok` | Ligne + filet au-dessus de la barre d'action (« Ramen shōyu — au fourneau · 9 s ») |
| `repas` / `repas_fin` | `nom`, `ic`, `dur`, `fait` (s mangées), `vit` (0 = trot : pas de progrès, 1, 2 = assis), `pause` (s figées), `froid` / `ok` | Idem ; gris pendant une pause (« Repas en pause — reprend dans 28 s ») ; le mod extrapole entre deux paquets |
| `offre` / `offre_fin` | `de` (nom tel que je le connais), `quoi`, `etat` (chaud / froid), `mat`, `model` (icône de l'objet) ou `ic` (`ryo`), `s` | « Teuchi te tend un Ramen shōyu — chaud » / « F prendre · Retour refuser · 10 s » |
| `attente` / `attente_fin` | `a`, `quoi`, `s` | « Tu tends … à … — 8 s » (gris) |
| `main` | `nom`, `ic`, `chaud` (s restantes, 0 = froid) ; sans `nom` = plus de plat en main | « chaud · 7 min » ou « Froid — effet réduit de moitié » sous les vitals |

Déclarer `reborn:nourriture` (le mod enregistre un récepteur) vaut « je lis les vitals étendus ». Réponses : commandes
`/tendre prendre|refuser` ; préparer et manger passent par le clic droit (`PlayerInteractEvent`, `PlayerInteractEntityEvent`
pour les meubles Nexo).

**`reborn:modifs`** (S → C) : à chaque changement et toutes les `modificateurs.sync-secondes` (10) :
`{"mods":[{"id":"regeneration","nom":"Régénération","ic":"regeneration","b":1,"val":"×2","reste":287,"duree":300,"pause":0}]}`
— types `visible: SOI` seulement ; `ic` = fichier `textures/gui/effets/<ic>.png` du mod ; `pause` = régénération
figée par un coup (icône grise). Le mod décompte seul.

**Service** `api/ModificateurService` (ServicesManager, `@Stable`) : `multiplicateur(compte, effet)`,
`raison(compte, capacité)` / `refuse`, `poser(joueur, type, intensité, duréeMs, source)`, `retirer`, `actifs` ;
raccourci statique `ModificateurService.refusDe(joueur, "SPRINTER")`. Effets lus aujourd'hui : `regen` (satiété),
`satiete_perte` (satiété), `endurance_regen` (ShinobiCombat), `meditation` (méditation) ; capacités : `SPRINTER`
(allure), `COURSE_NINJA` (course de chakra). Contributeurs dérivés : FAIM (satiété 0 %), repas en cours (pas de sprint).

**Journal** : `OBJET` `PREPARATION`, `REPAS`, `ACHAT`, `PILULE`, `DON` (objet) ; `ECONOMIE` `DON` (ryo, plus les lignes
`GAIN` / `DEPENSE` du journal des ryo avec le motif « don à … ») ; `STAFF` `ENTREPRISE_*`, `SATIETE`, `DONNER`,
`MODIF_POSE`, `MODIF_RETRAIT` ; `JOUEUR` `MODIF_POSE` / `MODIF_RETRAIT` / `MODIF_FIN` pour les types `journal: true`.

---

## 4 bis. Journal universel, outils staff et effets — protocole (14/10)

> Contrat entre ShinobiCore (`journal/`, `staff/outils/`, `staff/panel/StaffPanel`) et le Poste de garde du mod
> (`staff/*Screen`). Décision du 14/10 : **tout est journalisé, quel que soit le grade** (du joueur à l'Owner).
> Toutes les charges utiles sont du **JSON UTF-8 brut**. Les champs inconnus sont ignorés des deux côtés : on peut
> ajouter un champ sans casser l'autre. **Tout est revérifié côté serveur** (grade, cible) ; le client ne fait
> qu'afficher et demander.

### 4 bis.1 Notions communes

**Personne** (objet `acteur` / `cible` d'une entrée de journal, et des réponses) :

```json
{"uuid":"<UUID du compte>","pseudo":"Omar","pid":"00042","perso":"Itachi Uchiha","grade":3,"gradeName":"Admin"}
```

`pid` = N° du personnage **au moment de l'action** (vide si aucun personnage), `grade` = grade staff 0-4 (0 joueur,
1 Helper, 2 Modérateur, 3 Admin, 4 Owner). La console est `{"pseudo":"CONSOLE","grade":4,"gradeName":"Console"}`.

**Cible** dans les requêtes : `"t":"<UUID du compte>"` (comme avant) **ou** `"pid":"00042"` (avec ou sans `#`, zéros
facultatifs). Si les deux sont présents, `t` gagne.

**Catégories** du journal (`cat`), avec leur libellé (renvoyé dans `cats` à chaque réponse `journal`) :

| `cat` | Libellé | Ce qu'on y trouve |
|---|---|---|
| `JOUEUR` | Joueur | connexion, déconnexion (durée), choix / création / suppression / changement de personnage, changement de nom RP |
| `INVENTAIRE` | Inventaire | ramassé, lâché, rangé, équipé / retiré (sac, cosmétique, arme), utilisé, détruit, disparu au sol |
| `OBJET` | Objet | obtenu (toute source), donné ou retiré par le staff (`/sacoche give`, `/give`, prise via `/voirsac`) |
| `COMBAT` | Combat | dégâts donnés / reçus entre joueurs, agression hors zone, kill (mort vanilla), garde brisée |
| `KO` | KO / mort | à terre, inconscient, réveil, relevé, hôpital, ATA, demande / validation / refus de RPK, exécution, mort RP |
| `DUEL` | Duel | proposition, début, fin (issue, raison) |
| `ECONOMIE` | Économie | ryo gagnés, dépensés, donnés / retirés / fixés par le staff, achats à l'échoppe |
| `INTERACTION` | Interaction | menu d'interaction (porter, gorge, fouiller, achever…), soins, rencontrer, échange, ouverture de l'inventaire d'autrui |
| `RADIO` | Radio | escouades (création, ajout, départ, exclusion, renommage, dissolution), oreillettes (coupée, prise, exclue, réparée), émissions (qui, escouade, durée) — jamais la voix (§4 quater) |
| `BIBLIOTHEQUE` | Bibliothèque | dépôts, prises, vols de parchemins, séances |
| `STAFF` | Staff | toute action du Poste de garde et des commandes staff (tp, gel, noir, séisme, annonce, notes, reset…) |
| `COMMANDE` | Commande | **chaque commande tapée par n'importe qui** (joueur ou staff, vanilla comprise) et par la console |
| `CHAT` | Tchat | messages du tchat (`journal.chat`), chat staff |
| `SYSTEME` | Système | démarrage / arrêt, rechargements, suppression / remise à zéro de personnage, sauvegardes, rotation des journaux |

**Actions émises** (champ `action`, majuscules ; liste indicative, de nouvelles actions peuvent apparaître — l'UI
affiche l'action telle quelle si elle ne la connaît pas) :

- `JOUEUR` : `CONNEXION`, `DECONNEXION` (durée, « déconnecté en KO / en duel »), `EXPULSE`, `CHOIX_PERSO`,
  `CREATION_PERSO`, `SUPPRESSION_PERSO`, `MODE_DE_JEU`, `TELEPORTATION` (par commande), `NIVEAU` (et `XP` si
  `journal.xp`).
- `INVENTAIRE` : `RAMASSE`, `LACHE`, `DETRUIT`, `RANGE`, `ARME_ETUI`, `DISPARU`, `EQUIPEMENT` (détail = message de la
  sacoche, ex. `cos:equip:B3:TENUE`), `UTILISE`. `OBJET` : `OBTENU` (toute source), `PRISE_STAFF` (`/voirsac`).
- `COMBAT` : `DEGATS` (détail : PV, PV restants, arme ou projectile, cause, « duel » / « entraînement »),
  `AGRESSION` (premier coup hors terrain d'entraînement), `MORT` (mort vanilla).
- `KO` : une action par transition de `KoJournal` (`DOULEUR`, `AGONIE_…`, `INCONSCIENT`, `REVEIL`, `SECOND_SOUFFLE`,
  `PORTE`, `DEPOSE`, `FOUILLE`, `PRISE_A_LA_GORGE`, `EXECUTION…` — le libellé du KO en majuscules), `RPK_DEMANDE`,
  `RPK_VALIDE`, `RPK_REFUSE`, `RPK_EXPIRE`, `RPK_MORT`. Les actions staff sur le KO (réveil forcé, ATA levée) vont en
  `STAFF`.
- `DUEL` : `DEBUT`, `FIN` (et les autres étapes de `DuelService`). `ECONOMIE` : `GAIN`, `DEPENSE` (détail : montant,
  solde avant → après, motif : achat tenue / carte, `staff /ryo give|take|set`, remise à zéro, « autre »).
- `BIBLIOTHEQUE` : `PRISE`, `DEPOT`, `VOL` (cible = nom de la bibliothèque), `PARCHEMINS_…` (alertes staff).
- `STAFF` : `COMMANDE` (toute commande d'un staff, de la console `CONSOLE`, du RCON), `PANEL_WEB`, `TP_VERS`,
  `RAMENE`, `RETOUR`, `RENVOYE`, `GEL`, `DEGEL`, `GEL_RECONNEXION`, `VOIR_SAC`, `ECRAN_NOIR`, `ECRAN_NOIR_FIN`,
  `SEISME`, `ANNONCE_GLOBALE`, `ANNONCE_PERSO`, `TENUE_IMPOSEE`, `TENUE_LEVEE`, `EMOTE_FORCEE`, `NOTE_AJOUT`,
  `NOTE_SUPPRESSION`, `PERSO_RESET(_DEMANDE)`, `PERSO_SUPPRIME` / `PERSO_SUPPR_DEMANDE`, `MODE_STAFF`,
  `JOURNAL_CONSULTE`, `INVENTAIRE_AUTRUI`, et les entrées du Poste de garde (`<KIND>_<TYPE>`, ex. `SANCTION_BAN_1J`,
  `KO_REVEIL_FORCE`, `GRADE_PROMOTION`, `MONDE_ZONE_CREEE`).
- `COMMANDE` / `INTERACTION` / `CHAT` : `COMMANDE` (commande d'un joueur ; les commandes RP — `/corps`, `/soigner`,
  `/duel`, `/rencontrer`… — vont en `INTERACTION`, `/me`, `/msg`… en `CHAT`), `MESSAGE` (tchat), `CHAT_STAFF`,
  `COFFRE`, `INVENTAIRE_AUTRUI`. `SYSTEME` : `DEMARRAGE`, `ARRET`, `ALERTE_STAFF`, `PERSO_RESET`, `PERSO_SUPPRIME`.

### 4 bis.2 Canal `reborn:staff` — ajouts (C → S, champ `a`)

| `a` | Grade min | Champs | Réponse |
|---|---|---|---|
| `journal` | Helper | `req` (entier, renvoyé tel quel), `cat` (tableau de catégories, vide = toutes), `pid`, `joueur` (pseudo ou nom RP, sous-chaîne, acteur **ou** cible), `du` / `au` (epoch ms), `q` (texte libre dans action + détail), `page` (0…), `par` (taille de page, défaut 50, max 200) | `journal` |
| `notes` | Helper | `pid` (ou `t`) | `notes` |
| `note_add` | Helper | `pid` (ou `t`), `texte` (≤ 500 car.), `etiquette` (facultatif, ≤ 24 car. : « surveillé », « event », « avertissement »…) | `notes` + `toast` |
| `note_del` | auteur, ou Admin | `pid` (ou `t`), `id` | `notes` + `toast` |
| `act` | selon `op` | `t` **ou** `pid`, `op`, et selon `op` : `m` (message), `s` (secondes), `v` (valeur), `r` (rayon) | `toast` + `profile` |
| `annonce` | Modérateur | `m` (message), `titre` (facultatif), `s` (durée d'affichage, défaut 8) | `toast` |
| `seisme` | Admin | `r` (rayon en blocs autour du staff ; 0 = tout le serveur), `f` (force 1-10, défaut 5), `s` (durée, défaut 4), `m` (message facultatif) | `toast` |
| `modestaff` | Helper | — (bascule) | `toast` + `snap` (`modeStaff`) + `mode` |
| `modeanim` | `reborn.staff.animation` | — (bascule, 14/10) | `toast` + `snap` (`modeAnim`) + `mode` |
| `cherche` | Helper | `q` (≥ 2 caractères : N° avec ou sans `#`, nom RP, pseudo ; mots séparés = tous requis), `req` | `cherche` (recherche universelle du Poste de garde v3, **hors ligne compris**) |
| `persos` | Helper | `t` (ou `pid`) | `persos` — personnages du compte, **hors ligne compris** (15/10, `staff/panel/PersonnagesPanel`) |
| `perso_act` | selon `op` | `t`, `c` (UUID du personnage) **ou** `pid`, `op`, champs, `motif` (≤ 160 car.) | `toast` + `persos` + `profile` |
| `perso_slots` | Admin | `t`, `delta` (+1 / −1), `motif` **obligatoire** | idem |
| `perso_creer` | Admin | `t`, `nom` (2-32 car., unique sur le compte), `motif` **obligatoire** | idem |

Champ facultatif de `act` (14/10) : `alerte` = id de l'alerte d'où vient l'action (« Y aller », « Observer », « Répondre ») ;
le serveur la range dans les **alertes traitées** (et la retire des alertes en attente). `kill` accepte `note` (raison de la
validation, déjà lue par le serveur).

**`op` de `act`** (les anciens restent : `tp`, `bring`, `freeze`, `watch`, `revive`, `lift_ata`) :

| `op` | Grade min | Effet |
|---|---|---|
| `tp` | Helper | aller au joueur (la position de départ du staff est mémorisée pour `back`) |
| `back` | Helper | le staff revient où il était avant son dernier tp / ramener / observer |
| `bring` | Modérateur | ramener la cible au staff (sa position de départ est mémorisée pour `return`) |
| `return` | Modérateur | renvoyer la cible où elle était avant d'être ramenée |
| `freeze` / `unfreeze` | Modérateur | `freeze` **bascule** (comme avant) : gèle, ou dégèle un joueur déjà gelé ; `unfreeze` dégèle (hors ligne compris). Gel **persistant** (survit à la déconnexion et au redémarrage) ; `m` = motif |
| `invsee` | Modérateur (lecture) | ouvre côté serveur un coffre en lecture : barre, sacoche, sac porté, équipement. Prendre un objet = Admin |
| `blind` | Modérateur | écran noir : bascule ; `s` = durée (0 = jusqu'à `blind` de nouveau), `m` = texte affiché sur le noir |
| `shake` | Admin | séisme sur la cible seule ; `s`, `v` = force 1-10 |
| `announce` | Helper | annonce personnelle à la cible ; `m`, `s` |
| `emote` | Modérateur | forcer une emote ; `v` = id de l'emote, `stop` pour arrêter |
| `outfit` | Modérateur | forcer une tenue (emplacement cosmétique `TENUE`) ; `v` = id Nexo de la tenue, `main` = l'objet que le staff a en main, `retirer` pour rendre la sienne |

**`op` de `perso_act`** (15/10 ; le serveur revérifie le grade et les valeurs ; champ vide = inchangé ; tout est journalisé
`STAFF` avec « avant → après » et le motif) :

| `op` | Grade min | Champs | Effet |
|---|---|---|---|
| `identite` | Modérateur | `nom`, `age` (0-1000), `taille` (cm, 45-720 → `size = cm / 180`), `sexe` (`Homme` / `Femme`) | affichage et skin rafraîchis si joué |
| `origine` | Modérateur | `village` (`Konohagakure`… `Amegakure`, `Déserteur`, `""` = aucun), `clan` (libre, ≤ 24 ; clan connu normalisé ; `aucun` → `None`) | |
| `art` | Modérateur | `art` (`TAIJUTSU` / `KENJUTSU`), `affinite` (`STRENGTH` / `INTELLIGENCE` / `AGILITY`) | |
| `actif` | Modérateur | `motif` **obligatoire** | `setActive` sur le joueur **en ligne** (refusé : KO, combat, gel…, sélection en cours, mort RP) |
| `progression` | Admin | `rang` (nom de `Rank`), `bonus` (points de stats bonus, total 0-500) | états de service + `onRankChanged` |
| `mort` / `ressusciter` | Admin | `motif` **obligatoire** | `dead` ; s'il est joué : KO effacé et retour à la sélection |
| `reset` / `suppr` | Admin | `motif` **obligatoire** (le client exige aussi le N° retapé) | `GestionPerso.executer` : sauvegarde `sauvegardes/perso/` puis remise à zéro / suppression |

`persos` (S → C) :

```json
{"t":"persos","uuid":"<compte>","pseudo":"Raiden_FR","online":true,"selection":false,"playtime":2472,"grade":3,
 "slots":{"used":3,"bonus":2,"base":2,"total":4},
 "droits":{"voir":1,"modifier":2,"actif":2,"progression":3,"mort":3,"emplacements":3,"creer":3,"reset":3,"suppr":3},
 "rangs":[{"id":"GENIN","label":"Genin"}],"villages":[{"id":"Konohagakure","label":"Konoha"}],
 "persos":[{"id":"<uuid perso>","pid":"00042","nom":"Kazuki Uchiha","clan":"Uchiha","village":"Konohagakure",
   "villageCourt":"Konoha","rang":"Genin","rangId":"GENIN","age":14,"taille":168,"sexe":"Homme","art":"TAIJUTSU",
   "affinite":"STRENGTH","mort":false,"actif":true,"cree":"02/10/2026","niveau":4,"pvMax":1380,"chakraMax":16000,
   "points":3,"bonus":2,"techniques":6,"blessures":1,"ryo":12450,"natures":["Katon"],
   "stats":[{"n":"Taïjutsu","v":14}]}]}
```

`base` / `total` = −1 et pas de `playtime` quand le compte est hors ligne (la base dépend de ses permissions). `cree` =
date de l'enregistrement aux états de service, vide pour un personnage antérieur. Un roster hors ligne est relu du
disque puis relâché après chaque réponse.

**Réponses S → C** (champ `t`) :

`journal` :

```json
{"t":"journal","req":7,"total":312,"page":0,"pages":7,"source":"memoire",
 "cats":[{"id":"COMBAT","label":"Combat"}],
 "entries":[{"id":"lq3k9a-12","at":1760468592000,"date":"14/10 21:03:12","cat":"COMBAT","action":"DEGATS",
   "acteur":{"uuid":"…","pseudo":"Omar","pid":"00042","perso":"Itachi Uchiha","grade":0,"gradeName":"Joueur"},
   "cible":{"uuid":"…","pseudo":"Bob","pid":"00017","perso":"Kakashi","grade":0,"gradeName":"Joueur"},
   "detail":"6,5 PV · kunai","lieu":"world 12 64 -30"}]}
```

Ordre : **du plus récent au plus ancien**. `source` = `memoire` (index des N dernières entrées, réponse
immédiate) ou `disque` (la période demandée remonte avant l'index : le serveur relit les fichiers journaliers en
tâche de fond, puis répond ; dans ce cas `acteur` / `cible` peuvent n'avoir que `pseudo`, `pid` et `perso`).
`cible` peut être `null`. `lieu` peut être vide.

`notes` :

```json
{"t":"notes","pid":"00042","perso":"Itachi Uchiha","pseudo":"Omar",
 "entries":[{"id":"n1a2b","at":1760468592000,"date":"14/10 21:03","auteur":"Patoche","auteurPid":"","auteurGrade":2,
   "etiquette":"avertissement","texte":"Averti pour HRP le 14/10.","suppr":true}]}
```

`suppr` = le spectateur a le droit de supprimer cette note (auteur ou Admin+). Les notes sont rattachées au **N°**
(elles survivent à un changement de nom RP ou de pseudo).

**Champs ajoutés aux réponses existantes** :
- `snap` : `modeStaff` (bool, le spectateur est en mode staff), `journalCats` (liste `{id,label}`) ; 14/10 : `modeAnim`
  (bool), `animPossible` (bool, permission `reborn.staff.animation`), `traitees` (20 dernières alertes traitées :
  `{kind, pid, perso, par, quoi, time, moi}` — `par` = « Toi » pour le spectateur, `quoi` = « y est allé », « l'a
  ignorée », « a validé / refusé la mise à mort », « l'a observé », « a répondu »).
- `person` (chaque joueur de `players`, `alerts`) : `notes` (nombre), `etiquettes` (tableau des étiquettes des notes),
  `gele` (bool), `noir` (bool), `modeStaff` (bool), `modeAnim` (bool, 14/10).

`cherche` (S → C, 14/10) :

```json
{"t":"cherche","req":3,"q":"uchi","joueurs":[{"uuid":"<compte>","pid":"00009","perso":"Sora Uchiha","name":"sorasan",
  "online":false,"vu":"il y a 2 j"}]}
```

Lu dans l'index des N° (`PlayerIdRegistry`, personnages supprimés exclus), 20 résultats au plus ; `online` = ce
personnage est joué en ce moment ; `vu` = dernière connexion du compte (« en ligne (autre perso) » si le compte joue un
autre personnage). Le mod fusionne avec les joueurs en ligne de `snap`.

`mode` (S → C, 14/10) : `{"t":"mode","staff":false,"anim":true}` — envoyé au staff concerné à chaque entrée / sortie du
mode staff ou du mode animation ; le mod affiche un indicateur discret en haut à droite.
- `profile` : idem, plus `geleMotif`, `gelePar`, `retour` (bool : `return` possible), `back` (bool : `back` possible
  pour le spectateur).

### 4 bis.3 Canal `reborn:annonce` (S → C)

```json
{"t":"annonce","portee":"global","titre":"Annonce","texte":"La terre tremble sous vos pieds.","s":8,"son":true}
```

`portee` = `global` (tous les joueurs) ou `perso` (un seul joueur : scène, convocation). Affichage attendu : **bandeau
discret en haut au centre**, DA Reborn, fondu, `s` secondes. L'auteur n'est **jamais** envoyé aux joueurs (il est au
journal). **Sans le mod** : titre + sous-titre vanilla (global) ; sous-titre + ligne de tchat (perso).

### 4 bis.4 Canal `reborn:ecran` (S → C)

```json
{"t":"noir","on":true,"texte":"Vous perdez connaissance…","fondu":20,"s":0}
{"t":"noir","on":false,"fondu":20}
{"t":"seisme","f":5,"s":4,"son":true}
```

- `noir` : écran noir plein (au-dessus du HUD, sous le tchat), `texte` centré facultatif, `fondu` en ticks, `s` =
  durée prévue (0 = jusqu'à `on:false` ; le serveur envoie toujours `on:false` à la fin). Renvoyé à la reconnexion si
  toujours actif. **Sans le mod** : Cécité + Obscurité infinies et un titre.
- `seisme` : secousse de la caméra (amplitude ∝ `f`/10, décroissante sur `s` secondes) ; le **son** est joué par le
  serveur (grondement), le client n'en joue pas. **Sans le mod** : son + message de barre d'action seulement.

### 4 bis.5 Canal `reborn:plaques` (S → C, staff en mode staff seulement)

```json
{"t":"plaques","on":true,"joueurs":[{"uuid":"…","pid":"00042","perso":"Itachi Uchiha","pseudo":"Omar",
  "etat":"À terre · ATA","pv":7,"pvMax":20,"staff":false,"gele":false,"modeStaff":false}]}
{"t":"plaques","on":false}
```

Envoyé **chaque seconde** au staff en mode staff, pour les joueurs du même monde à ≤ 64 blocs. Affichage attendu :
petite plaque au-dessus de la tête (N°, nom RP, pseudo, état, PV), visible du seul staff ; `on:false` = tout
effacer. Les staffs en mode staff se voient entre eux (ils sont invisibles aux joueurs).

### 4 bis.6 Commandes en jeu (équivalents tapés, mêmes droits)

Détail dans §2.1 : `/journal`, `/note`, `/annonce`, `/annoncejoueur`, `/aller`, `/ramener`, `/retour`, `/geler`,
`/degeler`, `/voirsac`, `/noir`, `/seisme`, `/tenue`, `/emoteforcee`, `/modestaff`,
`/modeanim`.

### 4 bis.7 Mode staff et mode animation (14/10)

**Mode staff = aucune mécanique RP** (`staff/outils/ModeStaff`, décision du 14/10). Point d'entrée commun pour les
addons : `KoService#isOutOfPlay(UUID)` (méthode par défaut ajoutée à l'API `@Stable`, vraie en mode staff) ;
`KoService#techniqueDenial` renvoie « Mode staff : aucune technique. ». Ce qui est bloqué, et où :

| Mécanique | Garde |
|---|---|
| Frapper / être frappé (mêlée, projectiles, techniques) | `ModeStaff` `EntityDamageByEntityEvent` / `EntityDamageEvent` (LOWEST) ; ShinobiCombat `onMelee` (`isOutOfPlay` de l'attaquant ou de la victime) |
| Techniques | ShinobiAbilities `JutsuExecutionManager.gateOrWarn` via `techniqueDenial` |
| Garde, dash, bond de chakra, esquive | ShinobiCombat `CombatListener` (canal `reborn:combatin`) |
| Menu R et commandes RP : `/corps` (porter, fouiller, exécuter, gorge, RPK), `/yeux`, `/bandage`, `/rencontrer`, `/amitier`, `/osculter`, `/soigner`, `/paume`, `/aide`, `/me`, `/duel`, `/meditation`, `/assoir`, `/allonger`, `/ramper`, `/playemote` (sur soi), `/testfeuille` (hors `give`), `/hopital` (hors `set` / `suppr` / `liste`), `/radio` (hors outils staff), `/trade`, `/lookdown` | `ModeStaff` `PlayerCommandPreprocessEvent` (LOWEST) |
| Ramassage (touche F, `pick:` ; ramassage auto annulé en survie, aventure **et créatif** depuis le 15/10, sans limite de poids en créatif), lâcher (Q journalisé aussi en créatif), utiliser (manger, pilule), détruire | `InventoryManager` (canal `reborn:inventory`) ; `EntityPickupItemEvent`, `PlayerDropItemEvent`, `PlayerItemConsumeEvent` |
| Blocs et meubles RP (coffres, portes, bibliothèques, chaises), objet en main | `PlayerInteractEvent` (clic droit refusé, clic gauche libre), `PlayerInteractEntityEvent` ; ShinobiAbilities `LibraryService` |
| Monture | `EntityMountEvent` ; on descend en entrant dans le mode |
| Tchat RP | `AsyncChatEvent` (LOWEST) : le message part au **chat staff** (le `#` du chat staff marche toujours) |
| Radio | `/radio` refusé hors sous-commandes staff (`ecouter`, `stop`, `couper`, `retirer`, `info`, `donner`, `reparer`) ; ShinobiRadio : ni émission ni écoute par l'oreillette en mode staff (seule l'écoute staff journalisée) |

**Réciproque — personne ne cible un staff en mode staff** : coups (annulés), regard des commandes RP (les rayons de
`/rencontrer`, `/osculter`, `/soigner`, `/amitier`, `/paume` l'ignorent), commandes RP qui le **nomment** (pseudo ou nom
RP : « Personne de ce nom ici. »), clic droit sur lui, monter sur lui, cible des mobs. Restent permis : Poste de garde,
commandes staff, `/voirsac`, `/emoteforcee`, `/playemote <emote> <joueur>`, choix des lits de l'Infirmerie.
*Non couvert* : la perception de ShinobiSense (Byakugan, détection du chakra), hors du périmètre de ce chantier.

**Mode animation** (`staff/outils/ModeAnimation`, `/modeanim`, permission `reborn.staff.animation`) : l'animateur ou le
MJ incarne un personnage pour un event — visible, mécaniques RP permises. **Journal** : `STAFF / MODE_ANIMATION`
(entrée, sortie et durée), et `STAFF / ANIMATION` pour **chaque commande, chaque message et chaque coup porté** pendant
le mode (en plus des catégories habituelles). Indicateurs : `modeAnim` dans `person` et `snap`, « Animation » dans
l'état des plaques (`reborn:plaques`) et du Poste de garde, message `mode` → ligne fine en haut à droite de l'écran de
l'animateur, ligne « X passe en mode animation » au chat staff. Exclusif avec le mode staff ; tombe à la déconnexion.

---

## 5. Configuration

### 5.1 `ShinobiCore/config.yml` — sections

| Section | Contient | Doc |
|---|---|---|
| `mode` | `dev` / `prod` | — |
| `chakra` | régénération, affichage en barre d'XP, dépassement | [`gameplay/STATS_ET_PROGRESSION.md`](../gameplay/STATS_ET_PROGRESSION.md) |
| `stats` | `points-per-rank` (= points de **Mérite**), respec, plafond doux, PV, chakra, endurance, régén, coûts, critique, roue des natures | idem + [`SPEC_STATS_SERVICE.md`](./SPEC_STATS_SERVICE.md) |
| `progression` | `niveau-max`, `xp-base`, `xp-par-niveau`, `points-par-niveau`, `gains.*` | [`gameplay/STATS_ET_PROGRESSION.md`](../gameplay/STATS_ET_PROGRESSION.md) |
| `grades` | bonus passifs par grade, règles de promotion / rétrogradation | idem |
| `faction` | `taxe.defaut` (10), `taxe.plafond` (30, le Kage règle son taux dessous), `sections.places-defaut` (8) | [`gameplay/FACTION.md`](../gameplay/FACTION.md) |
| `communication` | `cloche.rayon-defaut` (250), `cloche.delai-cloche-secondes` (120), `cloche.delai-personne-secondes` (600), `cloche.danger-par-heure` (3), `cloche.sans-delai-des` (`COMMANDANT_JONIN` : défaut du seuil du village « Sonner sans délai », nom de `/grade`), `cloche.sans-delai-animation` (true : le staff en mode animation sonne sans délai), `cloche.texte-max` (200), `cloche.duree.{normale,importante,danger}` (10 / 12 / 15 s) ; `voliere.cout-village` (10 ryo), `voliere.cout-autre-village` (40), `voliere.delai-base-secondes` (60), `voliere.blocs-par-minute` (250), `voliere.delai-max-pays-secondes` (480), `voliere.delai-max-autre-village-secondes` (1200), `voliere.garde-jours` (7), `voliere.texte-max` (600), `voliere.adresse-max` (40), `voliere.destinataires-max` (10 par missive, aucune limite d'envois), `voliere.duree-alerte` (8 s) | [`gameplay/CLOCHE_ET_MISSIVES.md`](../gameplay/CLOCHE_ET_MISSIVES.md) |
| `ko` | PV persistants, défaite à l'entraînement, à terre, inconscient, `volonte`, plancher, lits, ATA, `douleur` (seuil, vitesse, lutte), `execution.canalisation-secondes` | [`gameplay/KO_ET_MORT_RP.md`](../gameplay/KO_ET_MORT_RP.md) |
| `mort-rp` | `scene-min-secondes`, `corps-minutes` | idem |
| `inventaire` | `ramassage.distance` (3), `ramassage.cadence-ms` (250), `ramassage.poids-max-pourcent` (130) ; `surcharge.charge` (`de` 100, `vitesse-de` 0,90, `vitesse-a` 0,70, `saut`, `sprint`), `surcharge.surcharge` (`de` 115, 0,70 → 0,35, `saut` 0), `surcharge.immobile` (`de` 130, `vitesse` 0, `saut` 0) ; `sol.disparition-minutes` (10), `sol.disparition-ko-minutes` (15), `sol.balayage-secondes`, `sol.jamais-detruits.parchemins` / `.ids` ; `journal.fichier`, `journal.poste-de-garde-des` (RARE) ; `arme.ids`, `arme.vanilla` ; `cases-cachees.balayage` | [`gameplay/INVENTAIRE_ET_COSMETIQUES.md`](../gameplay/INVENTAIRE_ET_COSMETIQUES.md) |
| `journal` | `actif`, `dossier` (`logs`), `retention-jours` (90, 0 = jamais), `memoire` (20 000 entrées), `disque-max-jours` (31), `chat`, `coffres`, `xp` (faux), `commandes.tous` (vrai), `commandes.ignorer` (préfixes), `combat.degats`, `combat.seuil-pv` | §4 bis |
| `rarete` | `par-id` : id Nexo / de modèle → COMMUN, PEU_COMMUN, RARE, EPIQUE, LEGENDAIRE | idem §7 |
| `duel` | `actif`, `distance-proposition`, `distance-max`, `duree-max-secondes`, `expiration-secondes`, `cooldown-secondes`, `cooldown-couple-secondes`, `compte-a-rebours`, `techniques`, `teleport-max-blocs`, `grace-secondes`, `annonce.actif`, `annonce.rayon` | idem §4.1 |
| `medic` | `paume.*`, `bandage.*` | idem |
| `meditation` | régénération, rayon d'annulation (× modificateur `meditation`) ; `halo` (18/10) : `enabled`, `portee`, `couleur`, `par-nature` + `natures.<nature>`, `particules-vanilla` (anciennes particules, défaut `false`) | — |
| `modificateurs` | `sync-secondes` ; `types.<id>` : `nom`, `famille`, `bonus`, `icone`, `empilement`, `plafond`, `persistance`, `au-ko`, `visible`, `journal`, `duree-secondes`, `valeur`, `effets`, `refuse`, `ensuite` (types livrés : `regeneration`, `satiete_prolongee`, `endurance`, `meditation`, `pilule_soldat`, `fatigue`, `poison`, `pilule_recharge`) | [`ETATS_JOUEUR.md`](./ETATS_JOUEUR.md) §4.4 |
| `nourriture` | `actif` ; `satiete.*` (départ 80, perte 0,0125 %/s, `activite.*`, technique, vitesse du sprint, combat, AFK, affamé 20) ; `regeneration.*` (seuil 90, 0,25 %/s, pause 30 s, plafond ATA 30) ; `faim.endurance-recuperation` ; `cuisine.*` (15 s, 2 plats, 4 blocs, chaud 10 min, poste de son entreprise) ; `repas.*` (assis ×2, pause 30 s, effets froids ×0,5) ; `tendre.*` (3 blocs, 10 s) ; `pilule.*` ; `ration` ; `plats.<id>` (`nom`, `satiete`, `duree-secondes`, `icone`, `reste`, `effets`) ; `vivres.<id>` (`prix`, `quantite`, `recette`, `cuisinier-seulement`) | [`gameplay/NOURRITURE.md`](../gameplay/NOURRITURE.md) §11, §13 |
| `monnaie` | `etats.garnie` (100), `etats.lourde` (5 000), `sol-minutes` (30), `alerte-ramasse` (5 000), `vol.pourcent` (30), `vol.sans-plafond-mort-rp` (vrai), `vol.fouille-secondes` (3), `vol.alerte` (1 000), `vol.corps-minutes` (15) — **facultatif, défauts dans le code** | [`gameplay/MONNAIE_ET_BANQUE.md`](../gameplay/MONNAIE_ET_BANQUE.md) §17 |
| `banque` | `guichet.*` (portée 5, guichetier 6, frais allié 5 %), `allies`, `villages-refuses`, `virement.*` (1 %, min 5, 5 %, 20 000 / jour, alerte 10 000), `echange.*` (2 %, min 10, distance 4, alerte 10 000), `paliers` (nom, coût, cases, plafond), `refuses.*` (parchemins, uniques, volés, `ids`, `armes-ids`, `armes-rarete-min`, `tag`), `solde.*` (jour, heure, fuseau, présence 3 h, fonction banque +300, `bareme.<GRADE>`), `tresor-initial`, `mort-rp.gel-jours` (7) — **facultatif** | idem |
| `display` | échelle des cœurs, saturation auto, HUD de recharge | — |
| `mobility` | Naruto run, sauts, dash, grappin, tyrolienne, escalade | — |
| `character-select` | point de sélection des persos | [`gameplay/PERSONNAGES.md`](../gameplay/PERSONNAGES.md) |
| `tablist`, `lore`, `cinematic`, `optimization`, `no-character-lockdown`, `character`, `shop` | divers | — |
| `reborn`, `panel-bridge` | URL de l'API, secrets HMAC du pont panel | **ne jamais commiter de vraie valeur** |

> **Une config existante sur le serveur n'est jamais réécrite** par une nouvelle version du plugin. Une clé
> ajoutée dans le code prend sa **valeur par défaut du code** tant qu'elle n'est pas dans le fichier du
> serveur. Pour changer une valeur déjà présente, il faut éditer `plugins/ShinobiCore/config.yml` sur le
> serveur, **serveur arrêté** (sinon il est réécrit à l'arrêt).

### 5.2 `ShinobiAbilities/config.yml` — sections

| Section | Contient |
|---|---|
| `jutsu` | apprentissage requis, `kenjutsu-exige-arme` (vrai : une arme à l'étui, dégainée ou en main), dépassement, coûts par stats, maîtrise, lancer rapide, incantation, MagicSpells |
| `mobility` | jetons aériens, ressenti, Naruto run, **`allure`** (marche, trot, sprint, coût d'endurance du sprint, relance ; 14/10), **`marche-eau`** (`enabled`, `chakra-par-seconde.immobile` 0.3 / `marche` 0.5 / `trot` 0.8 / `sprint` 1.5 ; 15/10), contrôle aérien, double saut, saut mural, onde au sol, dash, escalade, pistes, entraînement |
| `techniques` | répétitions des pompes, `concentration` (épreuve d'équilibre) |
| `fuinjutsu` | sceaux |
| `parchemins` | catégories, exclusions, `slots-s`, `slots-par-technique`, `seance-heures`, `perte-rare`, `maitre` |

### 5.3 Autres

- **ShinobiLearning** : `academy`, `squad`, `storage`.
- **ShinobiSense** : `sense`, `genjutsu`, `dojutsu` (dont `byakugan`).
- **ShinobiTail** : `triggers`, `rage`, `corruption`, `control-window`, `inner-world`, `fake-body`,
  `final-stage`, `aura`, `union`, `stage-power`, `messages`.
- **ShinobiRadio** : `escouade` (`taille-max` 8, `distance` 3, `invitation-secondes` 10, `dissolution-jours` 30,
  `nom-max` 24), `oreillette` (`ids` [oreillette], `emplacement` BOUCLE, `id-nexo` oreillette), `voix`
  (`chuchote-blocs` 4, `activation-poids` 20, `ligne-poids` 20, `volume-defaut` 1,0, `hache-trames-perdues` 0,35,
  `porte-hache` vrai), `staff` (`ecoute-minutes-defaut` 10, `ecoute-minutes-max` 60, `coupure-minutes-defaut` 30),
  `journal` (`emissions` vrai, `emission-min-secondes` 1).

---

## 6. Fichiers de données (dossier du plugin sur le serveur)

| Fichier | Plugin | Contenu |
|---|---|---|
| `characters/` | Core | Un YAML par joueur : ses personnages (stats, niveau, XP, apparence, techniques, blessures) |
| `rpbags/` | Core | Sacoches RP (barre exclue : sac, sac porté, cosmétiques, étui `ARME`) |
| `logs/inventaire-AAAA-MM-JJ.log` | Core | (Catégorie `INVENTAIRE` du journal universel, format commun depuis le 14/10 ; les objets obtenus vont dans `logs/objet-…`.) Journal des objets, un fichier par jour : `RAMASSE`, `LACHE`, `DONNE`, `DETRUIT`, `RANGE` (case cachée), `ARME_ETUI`, `DISPARU` (Rare et plus) — heure, `#N° pseudo (nom RP)`, quantité, objet, rareté, lieu ; Rare et plus + uniques aussi au Poste de garde (type `objet`) |
| `creator-assets/` | Core | Catalogue du créateur de personnage |
| `emotes/` (+ `emotes.yml`) | Core | Fichiers EmoteCraft distribués + catalogue |
| `ko-state.yml`, `ko-reglages.yml` | Core | États KO en cours, réglages de l'Infirmerie |
| `ata-state.yml` | Core | ATA en cours |
| `etats.yml` | Core | Matrice des états et types de modificateurs des états (§4 nonies) — complétée depuis le jar à chaque démarrage, jamais écrasée |
| `prison.yml` | Core | Peines de prison par personnage (17/10) : `fin` (epoch, temps réel), `zone`, `motif`, `par` |
| `volonte.yml` | Core | Volonté (QTE de relève) par personnage : `releves`, `second-souffle` — survivent au redémarrage depuis le 17/10 |
| `training-zones.yml` | Core | Zones d'entraînement et de repos |
| `hospitals.yml`, `hospital-beds.yml` | Core | Points de réveil par village, lits |
| `factions/factions.yml`, `factions/membres.yml` | Core | Factions (seuils, taxe, charges, annonces, journal court) et sections (grades, places, tenue, caisse et mouvements) ; registre des personnages (nom, grade du village, village, faction, section, grade de section). Journal complet : `logs/faction-…` (catégorie `FACTION`) |
| `communication/points.yml`, `communication/missives.yml` | Core | Cloches (village, coordonnées ou entité du meuble, rayon, dernière annonce, cloche tue) et volières (nom, type, village) ; chaque copie de missive (auteur, signée, destinataires, texte à balises `<g>` `<i>`, départ, arrivée, état `VOL` / `ATTENTE` / `PRISE` / `BRULEE`, cachet, preneur). L'objet missive ne porte que son identifiant (`reborn:missive`). Journal : catégories `FACTION` (action `CLOCHE`), `MISSIVE`, `STAFF` |
| `player-ids.yml` | Core | **Player ID** : `next` (prochain N°) + `index` `'00042'` → `n`, `character` (UUID perso), `owner` (UUID compte), `name`, `deleted`. Un N° n'est **jamais réutilisé** (supprimé = `deleted: true`). Écriture atomique (`.tmp` + renommage), compteur écrit avant d'attribuer. Chaque personnage porte aussi `playerId: 42` dans `characters/<compte>.yml`. Migration idempotente au démarrage (ordre : UUID du compte puis UUID du perso) |
| `logs/<categorie>-AAAA-MM-JJ.log` | Core | **Journal universel** (14/10) : un fichier par catégorie (`joueur`, `inventaire`, `objet`, `combat`, `ko`, `duel`, `economie`, `interaction`, `bibliotheque`, `staff`, `commande`, `chat`, `systeme`) et par jour, une ligne par entrée : `AAAA-MM-JJ HH:mm:ss \| CAT \| ACTION \| #00042 Pseudo (Nom RP) [Grade] \| cible \| détail \| monde x y z` (`—` = vide). Rétention `journal.retention-jours`. Remplace `aggression-log.txt` (→ `combat`), `kill-log.txt` et `ko-log.txt` (→ `ko`), `duel-log.txt` (→ `duel`) ; les anciens fichiers restent sur le disque, plus alimentés |
| `gels.yml` | Core | Gels staff persistants, par compte : `par`, `motif`, `le`, `nom` |
| `boutiques.yml` | Core | Boutiques-lieux (15/10) : `nom`, `genre` (tailleur / bazar), `village`, `entreprise`, `ouverte`, `camera.distance` / `camera.hauteur`, `cree-le`, `cree-par`, `comptoirs` (`type` bloc / entite, `monde`, `x`, `y`, `z`, `entite`? UUID du PNJ, `libelle`, `categories`, `articles`), `estrades` (`monde`, `x`, `y`, `z`, `yaw`), `prix` (id → ryo), `exclus` |
| `boutique-catalogue.yml` | Core | Catalogue des boutiques (copié du jar au premier démarrage, relu par `/boutique recharger`) : `articles.<id>` → `nom`, `categorie`, `genre` (tenue / objet), `nexo`, `emplacement`, `prix`, `conditions` |
| `entreprises.yml` | Core | Entreprises (14/10) : `nom`, `type`, `responsable` (UUID perso), `cree-le`, `cree-par`, `membres` (`perso`, `compte`, `nom`, `numero`, `role`), `postes` (`monde`, `x`, `y`, `z`, `libelle`) |
| `satiete.yml` | Core | Satiété par personnage (UUID → %) |
| `banque/comptes.yml` | Core | **Comptes** (15/10), par UUID de personnage : `owner`, `nom`, `numero`, `village`, `solde`, `palier`, `coffre` (objets base64), `gel.*`, `mort-le`, `regle`, `migre`, `bourse` (dernier montant connu), `virements.*`, `presence.*`, `solde-semaine.*`, `historique` (10) |
| `banque/banque.yml` | Core | `tresors.<village>`, `saisies.<village>` (objets), `guichets` (id, type bloc / entité, monde, x, y, z, entité, village, libellé, entreprise), `testaments.<perso>` (texte, héritiers `perso\|compte\|nom\|part`, statut DEPOSE / VALIDE, le, par, validé par), `operations.<n>` (500 dernières), `prochaine-operation`, `semaine-payee` |
| `modificateurs.yml` | Core | Modificateurs actifs par personnage : `type`, `n` (intensité), `duree`, `reste` (gelé), `fin` (continu, epoch), `source` ; les types `PERDU` ne sont pas écrits |
| `notes-staff.yml` | Core | Notes staff par N° : `id`, `le`, `auteur`, `auteur-uuid`, `auteur-pid`, `auteur-grade`, `etiquette`, `texte` |
| `tenues-forcees.yml` | Core | Tenues imposées, par personnage : id, staff, date, pièce d'origine (sérialisée) |
| `sauvegardes/perso/<date>-<N°>-<reset\|suppr>/` | Core | Copie de `characters/<compte>.yml` et de la sacoche avant une remise à zéro ou une suppression (Poste de garde › Personnages) |
| `emplacements.yml` | Core | Emplacements de personnage débloqués (+) ou retirés (−) par le staff, par compte : `<uuid du compte>: <bonus>` ; s'ajoute à la base 2 / premium 4 / staff 7 (15/10) |
| `logs/ko-AAAA-MM-JJ.log` (catégorie KO, via `ko/KoJournal`) | Core | Transitions du KO (douleur, agonie, inconscient, réveil, hôpital), portages, prises à la gorge, fouilles, exécutions (début, interruption, coup), commandes staff `/execution forcer\|douleur\|annuler` — `#00042 Nom RP (pseudo)` ; aussi au journal du Poste de garde (`ko`) |
| `executions.yml` | Core | Catalogue des exécutions (créé avec 3 exemples au premier démarrage) |
| `executions-joueurs.yml` | Core | Exécutions possédées, par compte |
| `respec-tokens.yml` | Core | Jetons de réinitialisation des stats (par compte) |
| `staff-journal.yml`, `sanctions.yml`, `staff-ips.yml` | Core | Journal, sanctions, IP du staff |
| `medic-armoirs.yml` | Core | Armoires à médicaments |
| `places.yml`, `cinematics.yml`, `dummies.yml` | Core | Lieux de la carte, cinématiques, mannequins |
| `abilities.yml` | Abilities | Catalogue des techniques (données Naruto) |
| `bibliotheques.yml` | Abilities | Bibliothèques à tirage et tirage en cours |
| `parchemins-s.yml` | Abilities | Slots du rang S (`rouleau:<uuid>`, `perso:<uuid>`) |
| `parchemins-progression.yml` | Abilities | Séances faites, dates d'apprentissage, demandes S en attente |
| `shelves.yml`, `bindings.yml` | Abilities | Étagères d'apprentissage, liaisons de touches |
| `mobility-paths.yml`, `mobility-unlocks.yml`, `parkours.yml`, `trails.yml` | Abilities | Voies et déblocages de mobilité, parcours, pistes |
| `beasts.yml` | Tail | Démons à queues |
| `escouades.yml` | Radio | `prochain-numero` (oreillettes, jamais réutilisé) ; `escouades.<uuid>` : `nom`, `createur` (perso), `cree-le`, `derniere-connexion`, `oreillettes-exclues`, `membres.<perso>` : `compte`, `nom`, `ajoute-par`, `depuis`, `oreillette` (n°), `prise`. Écriture atomique, toutes les ~20 s et à l'arrêt |

---

## 7. Annexe — régénérer ces tableaux

```bash
# Commandes et permissions déclarées
python -c "import yaml,io,sys;d=yaml.safe_load(io.open(sys.argv[1],encoding='utf-8'));[print(k,v) for k,v in d['commands'].items()]" \
  minecraft/shinobi/ShinobiCore/src/main/resources/plugin.yml
# Canaux côté serveur
grep -rhoE "\"reborn:[a-z_]+\"" --include=*.java minecraft/shinobi/*/src minecraft/plugin-*/src | sort -u
# Fichiers de données
grep -rhoE "getDataFolder\(\), \"[a-z0-9_.-]+\"" --include=*.java minecraft/shinobi | sort -u
```
