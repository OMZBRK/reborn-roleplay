# Radio — l'oreillette et l'escouade

> **Statut : codée le 2026-10-14 (non publiée, non testée sur serveur)** — plugin `ShinobiRadio` + mod `reborn-hud`
> (`radio/`, `voice/RadioVoix`), détail au **§12**. Conception v3 du même jour, décidée. Demande du 14/10 : « le proximity chat c'est déjà Plasmo
> Voice, mais ce serait bien de faire un truc RADIO ». Une première proposition à canaux (équipe, section, fréquences
> libres, appel de service) a été **remplacée le jour même** par les décisions de l'utilisateur
> ([`../pilotage/DECISIONS.md`](../pilotage/DECISIONS.md), 14/10) : **la radio = une escouade**. Ce qui reste marqué
> *(proposition)* est à valider ; les questions sont au §11. **v3 (retours du même jour)** : écran N épuré et centré,
> élément HUD « Escouade » (tête → nom) placé par l'éditeur de HUD, écoute staff minimale, **aucun kanji décoratif**,
> et les recommandations par défaut adoptées (§4, §11).
>
> **Maquettes** (hors dépôt, 1920 × 1080, sur de vraies captures du client de dev), `Maquettes/gameplan/18_radio/`
> à côté de `RBLAUNCHER/` :
> - **v3** (référence) : `v3/01_ecran_escouade.png`, `v3/02_hud_escouade.png`, `v3/02b_editeur_hud.png`,
>   `v3/03_staff_ecoute.png` (`_maquettes_v3.py`) ;
> - **v2**, encore valable pour l'ajout par le menu R et le vol : `v2/01_escouade.png` (menu R seulement : l'écran N
>   et le cordon sont remplacés par la v3), `v2/03_a_terre_oreillette.png` (**validé tel quel**) ;
> - v1 (à canaux) et le reste de la v2 : pour mémoire.
>
> **Sources** : [`../GAMEPLAN.md`](../GAMEPLAN.md) §17-18, la référence RedLine (*Proximity Chat*, *Den Den Mushi*),
> [`../technique/MOD_HUD.md`](../technique/MOD_HUD.md) (`voice/RebornVoiceAddon`, `VoiceState`),
> [`INVENTAIRE_ET_COSMETIQUES.md`](./INVENTAIRE_ET_COSMETIQUES.md) §10, [`KO_ET_MORT_RP.md`](./KO_ET_MORT_RP.md),
> [`../design/DIRECTION_ARTISTIQUE.md`](../design/DIRECTION_ARTISTIQUE.md) §6 règle 9, la doc officielle de Plasmo
> Voice (§2).

---

## 0. En dix lignes

1. La **voix de proximité reste Plasmo Voice** (installé sur le serveur). La radio s'y **ajoute**.
2. La radio = **une escouade** : un petit groupe de joueurs qui s'entendent où qu'ils soient. **Pas** de canaux de
   section, d'équipe ni de fréquences.
3. Il faut **porter une oreillette** (objet Nexo, emplacement cosmétique **Boucle d'oreille**, déjà existant).
4. **Créer / ajouter** : par le **menu R** sur un joueur **à côté de soi** (« Radio › Ajouter à « Kagerō » »). Le
   créateur ajoute ; **ceux qu'il a ajoutés peuvent ajouter aussi**. Toujours en face à face.
5. **Persistante** : se déconnecter ne fait pas quitter l'escouade.
6. **Touches configurables** : **B** (maintenir) pour parler, **N** pour le petit écran de l'escouade.
7. En parlant à la radio on est aussi entendu **à 4 blocs, en chuchoté**. À l'écoute : son radio (bande étroite,
   souffle, clic). HUD : un **élément « Escouade »** de l'éditeur de HUD (déplaçable, redimensionnable) qui montre
   seulement **[tête du skin] Nom** de ceux qui parlent.
8. **À terre** : la radio marche mais **très dégradée** (hachée, coupures), dans les deux sens. Inconscient : rien.
9. L'oreillette peut être **prise** à un joueur à terre : la **couper** (il est isolé) ou la **garder** et parler à sa
   place sur son escouade.
10. Le **staff peut écouter** une escouade (un bouton de la fiche joueur ou `/radio ecouter`, motif, durée,
    **journalisé**). **Le contenu vocal n'est jamais enregistré.**
    L'appel de service (hôpital, police) **n'est pas** la radio : c'est un système à part (GAMEPLAN §18).

---

## 1. Ce qui existe aujourd'hui

| Élément | État au 14/10 | Où |
|---|---|---|
| Plasmo Voice **serveur** | **Installé** (confirmé par l'utilisateur le 14/10) ; version à relever sur le panel | serveur dev / prod |
| Plasmo Voice **client** | `plasmovoice-fabric-26.2-2.1.16` dans le modpack (dernier manifest signé, 3.1.132) ; **`reborn-hud` compile contre 2.1.17** depuis le 14/10 (`libs/`, `compileOnly`) ; client de dev en 2.1.17 (`run/mods/`) | [`../ETAT_DES_LIEUX.md`](../ETAT_DES_LIEUX.md) §4, `minecraft/mod-hud/build.gradle` |
| `RebornVoiceAddon` | Addon **client** PV : détection de présence + branche la radio (`voice/RadioVoix`, 14/10) | `mod-hud/.../voice/RebornVoiceAddon.java` |
| `VoiceState` | « Je parle », micro coupé / mute serveur, assourdi ; entités qui parlent (bulles) | `mod-hud/.../voice/VoiceState.java` |
| Emplacement **Boucle d'oreille** | Un des 10 `CosmeticSlot`, rendu sur le corps, diffusé à tous | [`INVENTAIRE_ET_COSMETIQUES.md`](./INVENTAIRE_ET_COSMETIQUES.md) §10 |
| Menu R (interaction) | Menu contextuel sur un joueur / un corps (Ausculter, Porter, Fouiller, Exécution…) | `mod-hud/.../interaction/` |
| Escouades Genin `/squad` | Autre chose (sensei + élèves, ShinobiLearning) ; **ne sert pas** à la radio | `ShinobiLearning/.../squad/` |

Versions publiées (Modrinth, 14/10) : **2.1.17** (15/09, jar serveur `spigot-2.1.17` pour Paper / Purpur 26.1.2 →
26.3) et 2.2.0-beta.1 (30/09). Recommandation : **2.1.17 partout** (modpack, `libs/` du mod, serveur), pas la bêta.

---

## 2. Faisabilité Plasmo Voice 2.x

Sources : [plasmovoice.com/docs/api](https://plasmovoice.com/docs/api/) (*Activations*, *Source Lines*, *Sources*,
*Encoding & Encryption*, *Events*), [dokka.plasmovoice.com](https://dokka.plasmovoice.com),
[github.com/plasmoapp/plasmo-voice](https://github.com/plasmoapp/plasmo-voice), le jar 2.1.14 du dépôt (`javap`).

**Serveur (plugin Paper qui dépend de PlasmoVoice)**

| Besoin | API | |
|---|---|---|
| Touche « parler à la radio » | `ServerActivation` « radio » (`getActivationManager().createBuilder(…)`) : le client lui donne **sa propre touche** | ✅ |
| Ne pas déclencher la proximité en même temps | `setProximity(false)`, `setTransitive(false)` | ✅ |
| Interdits (inconscient, sans oreillette, oreillette coupée) | `setRequirements(…)`, vérifié **à chaque paquet** | ✅ |
| Qui entend : les membres de l'escouade | `onPlayerActivation` reçoit chaque trame (20 ms) → renvoyée à des **sources directes** (`createDirectSource`, `setSender` = nom affiché) ou une **source de diffusion** `setPlayers(membres connectés)` | ✅ |
| Entendu à 4 blocs autour de soi | même trame vers une **source joueur** à distance 4 | ✅ |
| Volume à part | `ServerSourceLine` « radio » (curseur dans le menu PV) | ✅ |
| Son haché à terre | Le serveur **saute des trames** (coupures) et marque la source « dégradée » ; le client ajoute l'effet (§2, client) | ✅ |
| Écoute staff | le staff est simplement ajouté aux destinataires, **sans être membre** et sans pouvoir émettre | ✅ |
| Journal sans contenu | `PlayerServerActivationStartEvent` / `EndEvent` : qui parle, quand, sur quelle escouade | ✅ |

**Client (addon déjà présent dans `reborn-hud`)**

| Besoin | API | |
|---|---|---|
| Effet radio, effet « haché » | `AudioSourceWriteEvent(source, séquence, short[])` : on filtre les échantillons de la ligne « radio » (passe-bande, souffle ; à terre : bitcrush, trous, hachures) | ✅ |
| Indicateur « j'émets », qui parle | `getActivationByName("radio").isActive()` ; sources actives de la ligne « radio » | ✅ |
| Masquer l'icône PV de la radio | `HudActivationRenderEvent.setRender(false)` | ✅ |
| Touches configurables | `ClientActivation.getPttKey()` → `Hotkey.setKeys()` (B par défaut, puis le réglage du joueur) ; reprise dans les Réglages Reborn | ✅ |

Pas d'addon existant qui fasse ça (`pv-addon-groups`, MIT, est le plus proche : groupes par commandes, sans objet ni
proximité ; bon **modèle de code**). Où vit l'addon serveur : §11.

---

## 3. L'oreillette

- Objet **Nexo** `reborn:oreillette` (écouteur laqué cerclé de laiton, fil vers un micro à pince au col), portée dans
  l'emplacement **Boucle d'oreille**. Dans la sacoche, elle ne sert à rien.
- **Chaque oreillette est unique** (numéro) et garde : **son escouade**, **le nom de son propriétaire d'origine**, son
  état (**en marche / coupée**).
- **C'est l'oreillette qui donne l'accès** à l'escouade (comme le Den Den Mushi garde ses données) : qui la porte entend
  et parle sur **son** escouade.
- Une oreillette = **une** escouade. Pour en rejoindre une autre, on quitte la sienne.
- Obtention *(proposition)* : échoppe / artisan, prix modeste. Une oreillette coupée se **répare** chez un artisan
  (ou on en rachète une, puis un membre vous ré-ajoute).

## 4. L'escouade (maquettes v2/01 pour le menu R, v3/01 pour l'écran N)

- **Créer** : menu R sur un joueur à côté → « Radio › Créer une escouade avec lui ». L'escouade naît avec les deux
  joueurs. Nom par défaut : celui du créateur, **renommable** (« Kagerō »).
- **Ajouter** : menu R sur un joueur **à 3 blocs au plus** → « Radio › Ajouter à « Kagerō » ». **Tout membre** peut
  ajouter (le créateur, et ceux qu'il a ajoutés, et ainsi de suite). L'autre reçoit une **invitation discrète** :
  « Aki U. t'ajoute à l'escouade Kagerō — Entrée accepter · Retour refuser » ; il a **10 s pour accepter** (adopté).
  Il doit porter une oreillette.
- **Persistante** : déconnecté = toujours membre (« hors ligne » dans la liste). Survit aux redémarrages.
- **Petit écran N** (maquette v3/01) : **centré, épuré**, sans ornement ni kanji. Titre « Escouade Kagerō », « 4 membres
  sur 8 », puis une ligne par membre : **tête du skin, nom, état** (point de couleur + « en ligne », « à terre »,
  « hors ligne », « toi · créateur »). **Survoler** un membre montre « Exclure » (créateur seul). En bas, trois mots
  soulignés au survol : **Renommer, Quitter, Dissoudre…** (Renommer et Dissoudre : créateur seul). Pas de bouton
  « Ajouter » : on ajoute en jeu, face à face.

| Règle (**adoptée le 14/10**) | Valeur |
|---|---|
| Distance pour ajouter | 3 blocs, face à face |
| Taille maximale | **8** membres |
| Quitter | À tout moment, depuis l'écran N |
| Exclure | Le **créateur seul**, à distance (exclure un membre **ou une oreillette** précise : parade au vol) |
| Créateur qui quitte | Le **plus ancien** membre devient créateur |
| Dissolution | Par le créateur ; automatique si l'escouade est vide, ou si **personne ne s'est connecté depuis 30 jours** |

## 5. Parler (maquettes v3/02 et v3/02b)

- **Maintenir B** (configurable) : on parle à l'escouade. Les membres **connectés qui portent leur oreillette** entendent,
  où qu'ils soient dans le monde.
- **Autour de soi** : les joueurs à **4 blocs** entendent la voix normale (spatialisée, sans effet).
- **Le son** à l'écoute : non spatialisé, bande étroite, léger souffle, clic au début et à la fin ; volume à part.
- **HUD : l'élément « Escouade »** (maquettes v3/02 et 02b), règle 9 de la DA :
  - une ligne par personne qui parle : **tête du skin** (8 px GUI) puis **le nom** (tel que je le connais), rien
    d'autre — ni fond, ni cadre, ni nom d'escouade ;
  - celui qui parle est net avec un filet d'or d'1 px ; la ligne s'efface 1,5 s après la fin ;
  - **j'émets** : ma propre ligne avec un petit point rouge ;
  - **à terre** : « haché » après le nom ;
  - en silence : **rien à l'écran** ; option de l'éditeur « Afficher toute l'escouade » (ceux qui se taisent en
    retrait) ;
  - **déplaçable et redimensionnable** dans l'éditeur de HUD (touche H) : une entrée `ESCOUADE` de l'énumération
    `element/HudElement` (comme `COMBAT_ENDURANCE` et `COOLDOWNS`), ses limites dans `HudElementBounds`, des têtes
    d'exemple affichées dans l'éditeur. Défaut **validé le 14/10** : sous les vitals, à gauche.
- **Plus de ligne « radio · canal »** à côté des vitals (v1/v2), et l'icône d'activation de Plasmo Voice est masquée
  pour la radio.

## 6. À terre (maquette v2/03, validée)

- **À terre (agonie)** : la radio marche **dans les deux sens** mais **très dégradée** : coupures, voix hachée, souffle.
  La ligne dit « à terre · haché », le nom de celui qui parle est « troué ». C'est la façon RP d'appeler à l'aide.
- **Inconscient** : plus rien (ni entendre, ni émettre).
- **Porté** *(proposition)* : comme à terre.

## 7. Prendre l'oreillette (maquette v2/03, validée)

Sur un joueur **à terre** (ou inconscient), le menu R propose **« Oreillette › »** :

| Choix | Effet |
|---|---|
| **Couper son oreillette** | Fil arraché : l'oreillette devient **muette** (sur lui). Il reste membre, mais n'entend ni n'émet tant qu'il n'a pas une oreillette réparée ou neuve. |
| **Prendre son oreillette** | Elle passe dans la **sacoche** du preneur. S'il la **porte**, il entend l'escouade et parle **à la place** du propriétaire : chez les autres, l'élément Escouade affiche **la tête et le nom du propriétaire** (« Ren H. ») avec une autre voix (adopté). Chez lui, un rappel discret « oreillette de Ren Hyūga ». |

- **Parade** : le créateur **exclut l'oreillette** (par son numéro) depuis l'écran N ; elle ne capte plus rien.
- Le vol passe par la **fouille** existante (KO_ET_MORT_RP) : mêmes conditions, même journal des objets.

## 8. Staff (maquette v3/03)

> **Côté mod, prêt (14/10)** : Poste de garde v3 › fiche › Plus › « Écouter la radio… » (raccourci E) ou section Radio
> ouvre le formulaire dans le volet (motif obligatoire, durée 5 / 10 / 15 / 30 min) qui envoie
> `/radio ecouter <#N°> <minutes> <motif>`. Tant que le serveur ne déclare pas `/radio`, le bouton reste grisé
> « Bientôt ». En **mode staff**, `/radio` est refusé hors des sous-commandes staff (`ecouter`, `stop`, `couper`,
> `retirer`) : le staff n'émet jamais (PLUGINS_REFERENCE §4 bis.7).

Minimal (le Poste de garde est en cours de refonte compacte par ailleurs ; on n'y ajoute qu'**un bouton**) :

- Fiche joueur du Poste de garde, boîte Surveillance : **« Écouter sa radio… »** (grisé si le joueur n'a pas
  d'escouade). Un seul champ : le **motif** (obligatoire), et la durée (**10 min** par défaut).
- Commandes : `/radio ecouter <joueur> [minutes] <motif>`, `/radio stop` ; *(proposition)* `/radio couper <joueur>
  [durée]` (interdit d'émettre) et `/radio retirer <joueur>`.
- Pendant l'écoute, le staff entend l'escouade comme un membre, **sans pouvoir parler** ; une **petite ligne**
  au-dessus de l'élément Escouade : « écoute · Kagerō · 9:41 ».
- Le joueur **n'est pas prévenu** (adopté). **Tout est journalisé** et visible des autres staffs : « Omar (staff) écoute
  Escouade Kagerō — plainte n° 212 — 10 min ».

## 9. Journalisation

**Jamais de contenu vocal**, ni enregistré ni transcrit. Au journal : création, ajout (par qui), invitation refusée,
départ, exclusion, renommage, dissolution ; oreillette **coupée** / **prise** (par qui, sur qui) ; oreillette exclue ;
actions staff (écoute avec motif et durée, coupure, retrait).

## 10. Ce qui sort de la radio

- **Canaux de section, d'équipe, fréquences libres, balayage, interception** : abandonnés (décision du 14/10).
- **Appel de service** (hôpital, police) : **système à part** (GAMEPLAN §18), plus lié à la radio.
- **Portée** : **illimitée dans le monde** (adopté) ; pas de brouillage.
- **Kanji et sceaux décoratifs** : aucun, dans aucun écran ni élément de la radio (règle générale du 14/10).

## 11. Décisions adoptées et questions restantes

Adopté le 14/10 (recommandations par défaut) : accepter pour être ajouté (**10 s**), **8** membres au plus,
dissolution après **30 jours** sans connexion, **portée illimitée** dans le monde, oreillette volée = **nom du
propriétaire** affiché, oreillette coupée **réparable chez un artisan**, écoute staff **non signalée**, addon serveur
dans un nouveau module Maven **`ShinobiRadio`**, Plasmo Voice **2.1.17** partout.

Questions restantes :
1. ~~Élément Escouade : par défaut seulement ceux qui parlent ou toute l'escouade ?~~ **Tranché le 14/10 : seulement ceux qui parlent** (toute l'escouade en option).
2. ~~Position par défaut : sous les vitals, à gauche ?~~ **Validé le 14/10.**
3. Commandes staff en plus de l'écoute (`/radio couper`, `/radio retirer`) : **codées** (Modérateur), à confirmer ou
   retirer.
4. Touches : **B** est aussi la touche par défaut d'Emotecraft et **N** celle du menu OST (`mod-ost`) — à rebinder
   chez l'un ou l'autre dans le modpack (les deux touches radio sont réglables dans les contrôles Reborn).
5. Réparer une oreillette : en attendant les artisans, `/radio reparer` (Modérateur ou permission
   `shinobiradio.artisan`). Obtention (échoppe, prix) : à décider ; aujourd'hui `/radio donner` (Admin) ou `/nexo give`.

---

## 12. Ce qui est codé (14/10, non publié)

**Serveur — plugin `ShinobiRadio`** (`minecraft/shinobi/ShinobiRadio`, module de l'agrégateur Maven, `depend:
[ShinobiCore, PlasmoVoice]`). Référence : [`../technique/PLUGINS_REFERENCE.md`](../technique/PLUGINS_REFERENCE.md) §2.4
(commandes), §4 quater (protocole `reborn:radio`, Plasmo Voice, journal), §5.3 (config), §6 (`escouades.yml`).

| Pièce | Classe | Ce qu'elle fait |
|---|---|---|
| Plugin | `ShinobiRadio` | Charge l'addon Plasmo Voice dans `onLoad`, services ShinobiCore, boucle toutes les 5 ticks |
| Addon PV | `RadioAddon` (`@Addon reborn-radio`, `@InjectPlasmoVoice`) | Enregistre la voix à l'initialisation de Plasmo Voice (et à chaque rechargement) |
| Voix | `VoixRadio` | Activation `radio` (sans proximité, non transitive, conditions à chaque paquet), ligne `radio` (volume à part), une source de diffusion par émetteur, chuchoté à 4 blocs sur la ligne de proximité, trames sautées à terre |
| Règles | `Radio` | Instantané immuable (qui émet, qui entend) lu par la voix ; états (inconscient, à terre, porté, mort RP, mode staff) ; créer / ajouter / accepter (10 s) / quitter / renommer / exclure / exclure l'oreillette / dissoudre ; couper / prendre l'oreillette d'un joueur à terre ; staff (info, écoute, coupure, retrait, don, réparation) ; dissolution après 30 jours ; journal |
| Données | `Escouade`, `Escouades` | Escouades persistantes (`escouades.yml`, écriture atomique), numéros d'oreillettes jamais réutilisés |
| Oreillette | `Oreillettes` | Reconnaît l'objet (`oreillette.ids`), lit / écrit ses données (numéro, escouade, propriétaire, coupée), lore discrète |
| Canal | `Canal` | `reborn:radio` (S → C) |
| Commande | `RadioCommande` | `/radio …` (joueurs et staff, grades du Poste de garde) |

Changements dans **ShinobiCore** (à redéployer avec) : `InventoryService#cosmetique` / `#poserCosmetique` (lire et poser
l'objet d'un emplacement cosmétique), `KoService#isDowned` (à terre vs inconscient), catégorie de journal **`RADIO`**,
et `ModeStaff` accepte aussi `/radio info|donner|reparer` en mode staff.

**Client — mod `reborn-hud`** (paquet `radio/`) :

| Pièce | Classe | Maquette |
|---|---|---|
| Canal, état, invitation, paroles | `RadioClient` | — |
| Écran N (centré, épuré, sans kanji ; survol → Exclure, « Couper l'oreillette » si elle a été prise ; Renommer, Quitter et Dissoudre avec second clic) | `RadioScreen`, textures `textures/gui/radio/panneau_<h>.png` (`tools/gen_radio_textures.py`) | v3/01 |
| Élément HUD « Escouade » (`HudElement.ESCOUADE`, défaut sous les vitals à gauche, têtes d'exemple dans l'éditeur, case « Afficher toute l'escouade » dans l'inspecteur) ; invitation ; ligne staff « écoute · Kagerō · 9:41 » ; rappel bref si B ne peut pas émettre | `RadioHud` | v3/02, v3/02b, v3/03 |
| Menu R : Radio › Créer / Ajouter à « … » / Voir l'escouade (N) ; sur un joueur à terre : Oreillette › Couper / Prendre | `RadioMenus` (+ `interaction/InteractionMenus`) | v2/01, v2/03 |
| Son radio (passe-bande, souffle ; haché à terre), icône PV masquée, touche B recopiée dans l'activation PV (forcée en push-to-talk) | `voice/RadioVoix` (chargé par `RebornVoiceAddon`) | — |
| Touches **B** (parler, maintenir) et **N** (écran), réglables | `keybind/HudKeybinds` | — |
| Banc de captures `REBORN_RADIO_DEBUG=1` (`REBORN_RADIO_POS` pour le point de vue) | `RadioDebug` | captures `Maquettes/gameplan/18_radio/code/` |

L'écoute du Poste de garde (fiche › Plus › « Écouter la radio… ») s'active dès que le serveur a la radio (commande
`/radio` déclarée ou premier message `reborn:radio`). Plasmo Voice du mod passé en **2.1.17** (`libs/`).

**Oreillette (pack Nexo)** : `oreillette` dans `minecraft/server-config/nexo/items/reborn_cosmetiques.yml`, modèle et
texture `pack/assets/reborn/{models/item,textures/item,items}/oreillette.*` générés par
`minecraft/mod-hud/tools/gen_oreillette_nexo.py` (jamais dans les assets du mod).

**Pas vérifiable sans serveur** : la voix elle-même (routage des trames, chuchoté, coupures, effet radio sur de vraies
voix, masquage de l'icône, touche B dans Plasmo Voice, réflexion sur le type d'activation), le rendu de l'oreillette
portée sur le corps, et la persistance après redémarrage. Les écrans et le HUD sont capturés sur le client de dev avec
des messages simulés.
