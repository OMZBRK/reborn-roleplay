# NydoMod 5.5 — Dōjutsu, yeux, chirurgie, Susanoo, genjutsu : analyse pour Reborn

> Source : code décompilé `net/nydomod/` (Forge 1.20.1) et ressources `assets/nydomod/`. Mod « All Rights Reserved » :
> on retient des **concepts et des chiffres d'équilibrage**, aucun code ni asset. Chemins relatifs à `src/net/nydomod/`.

## Résumé (10 lignes)

1. Chaque œil est une **entité de données à part entière** (UUID d'œil, UUID de paire, côté d'origine immuable, intégrité 0-100, aveugle, détaché, hôte, compteurs d'extractions/greffes), stockée en NBT sur l'objet dōjutsu (`OcularIdentity`).
2. Le corps du joueur a deux **orbites** : `NORMAL`, `ESCROW` (œil gardé par un cadavre), `MISSING` (`OcularBodyState`).
3. **Trois extractions** : sur soi (parchemin + fiole), sur un cadavre (2 min après une vraie mort) et sur un vivant, **seulement s'il est entravé** (ombre Nara, chaînes) ou **immobile depuis 1,8 s**.
4. L'extraction est un **duel de timing** (« Struggle ») : flèches à frapper au bon tick, 3 tactiques par camp, une barre de progression et une barre de prise, des stats plafonnées à ±18 %, une tolérance de ping et une fenêtre finale que **des tiers peuvent interrompre**.
5. La réussite produit un **œil physique** stockable (fiole scellée à usage unique) ; la victime perd l'œil, et son orbite passe à `MISSING`.
6. La **greffe** respecte le côté d'origine (un œil gauche va à gauche) ; un médic qualifié peut greffer un autre joueur à moins de 3 blocs.
7. Le **Mangekyō Éternel** passe par une **chirurgie** sur une table, avec un PNJ (Konoha 12 000 ryo, Akatsuki 4 000) ou seul (manuscrit). Mini-jeu en 3 phases (incision, connexion chakra, sutures, 24 gestes). Au bout de 5 erreurs, l'opération avorte. Issues : succès / échec / **cécité définitive**.
8. **Chaque œil porte ses propres pouvoirs** : le slot 0 exige l'œil gauche, le slot 1 le droit, le slot 2 l'un ou l'autre, ou les deux. Un œil volé coûte **×2 à ×3 en chakra** à son nouveau porteur.
9. Le **Susanoo** a **6 phases** débloquées par XP de maîtrise (100/300/750/1500). La phase ailée est réservée à l'Éternel et à quelques profils. La taille va de 2,4 à 53 blocs, plus un bonus de chakra jusqu'à +50 %. Il coûte 500 chakra par activation ou changement de phase et dure **41 s** au plus.
10. Les **genjutsu** (Sharingan, Tsukuyomi, Kotoamatsukami) restent simples côté serveur (paralysie, cécité, dégâts, interdiction d'attaquer). Côté client, ils prennent la forme de **cinématiques plein écran** qui verrouillent les contrôles de la victime.

---

## 1. L'œil comme objet physique

**Ce que voit le joueur** : un inventaire dédié aux dōjutsu (18 emplacements, `DojutsuSlotInventory`). Les infobulles indiquent « Œil d'origine : gauche · Intégrité : 87 % », « Tissu extrait — chirurgie du même côté » ou « Implanté dans l'orbite droite ». Un œil extrait **ne s'équipe pas** : c'est « du vrai tissu ».

**Comment ça marche**
- `dojutsu/ocular/OcularIdentity` : clé NBT `nydomod:ocular_identity` sur l'ItemStack, avec deux sous-blocs `left`/`right` (`eye_id`, `present`, `origin_side`, `integrity`, `blinded`, `detached`, `host`, `socket`, `extractions`, `transplants`). Une paire partage un `pair_id`. Les opérations s'appellent `splitOut`, `markExtracted(penalty)`, `markInstalled(host, socket)`, `mergeEye`, `samePair`.
- `OcularBodyState` : données persistantes du joueur `nydomod:ocular_body`, `left_socket`/`right_socket` ∈ {NORMAL, ESCROW, MISSING}. Une mort réinitialise à NORMAL (le personnage renaît). Un clone sans mort conserve l'état.
- `DojutsuProfile` / `NativeDojutsuProfiles` : par type d'œil, `liveExtractable`, `corpseExtractable`, `transplantable` et une **difficulté d'extraction** (Byakugan 0,95 ; Sharingan 1,0 ; MS 1,2 ; Rinnegan 1,25 ; Éternel 1,35 ; Tenseigan 1,4).
- **Garde anti-duplication** : `OcularEscrowSavedData` est un registre mondial des yeux en transit (cadavre, fiole). Les opérations sont transactionnelles, avec snapshot et rollback (`commitEternalMangekyo`, `escrowDeathEyes`), et vérifient qu'un `eye_id` n'existe qu'une fois (`containsEyeId`).
- **Fiole de conservation** (`item/ItemOcularVial`) : se fabrique avec 3 verres et 1 lingot de fer. Une fois remplie, elle porte un **sceau** UUID à usage unique. On l'ouvre et on se l'implante en 1,2 s, immobile ; un dégât interrompt (`OcularVialImplantation`). L'infobulle sert de dossier médical : intégrité, vision, tomoe, maîtrise Susanoo, fatigue restante.

**Intérêt RP** ★★★ : l'œil devient un **bien rare, traçable et unique**. On peut le voler, le revendre, le cacher dans un coffre, le rendre à un clan, le mettre sous scellés par l'ANBU. L'UUID et les compteurs d'extractions/greffes donnent un historique que le staff peut consulter (« cet œil a changé 3 fois de main »).
**Risques** : marché noir hors RP, duplication par bug (Nydo y consacre beaucoup de code), et joueurs qui stockent des yeux « pour la puissance » sans RP.

**Faisabilité Reborn** : **moyen**. Côté serveur autoritaire, un registre `yeux` (UUID, propriétaire d'origine, clan, côté, intégrité, état, porteur actuel) en base ou en YAML ShinobiCore, plus un objet custom « fiole » (comme `reborn:parchemin_*`, en PDC). Le personnage porte deux orbites dans ses données. La difficulté réelle tient à l'**anti-duplication** (transactions, journal).
**Équivalent Reborn** : aucun. `ShinobiSense/DojutsuManager` traite le dōjutsu comme un **mode** lié au clan, pas comme un organe. Les **parchemins** (`APPRENTISSAGE.md` §2, « un vrai objet : il se vole, s'échange, se brûle ») sont le précédent de design le plus proche.

---

## 2. Extraction et mini-jeu « Struggle »

**Ce que voit le joueur** : le médic tient un **scalpel**, vise le visage et choisit l'œil gauche, le droit ou les deux (`OcularExtractionSelectorScreen`, offre valable 240 ticks). Une cinématique verrouillée démarre (`cinematics/struggle/eye_extraction_left.json`, 900 ticks max, HUD masqué). Les deux joueurs voient des **flèches** à frapper au bon moment, avec un retour PERFECT / GREAT / GOOD / LATE / MISS. Le chirurgien choisit une tactique (Précision / Force / Contrôle), la victime aussi (Protéger / Briser l'entrave / S'arc-bouter). Si la victime s'échappe, une cinématique `eye_escape_*` se joue. En cas de réussite, la cinématique `eye_release_*` montre l'œil retiré.

**Comment ça marche**
- Le moteur générique se trouve dans `struggle/StruggleManager` et `StruggleDefinition` : préparation, rounds de durée variable, rayons de timing en ticks, « breakpoint » final. Notation (`grade`) : écart au tick idéal moins une **tolérance de ping** (au plus 3 ticks, `ping_grace_max_ticks`). Grades : PERFECT 1,0 ; GREAT 0,78 ; GOOD 0,55 ; LATE 0,22 ; MISS 0. Score effectif = grade × (1 + biais stats **plafonné à ±18 %** + condition ±7 % + chance ±3 %), borné entre 0,65 et 1,35. Le client n'envoie que la direction ; le serveur horodate.
- **Vivant** (`OcularStruggleRules.liveDefinition`) : préparation 14 ticks, rounds de 12 à 20 ticks, rayons perfect/great/good/late = 1/2/3/4 ticks (**très serré**), breakpoint de 12 ticks. Progression initiale = force de l'ouverture (bornée entre 0,12 et 0,64).
- **Cadavre** : rounds de 30 à 38 ticks, rayons 2/3/5/8 ticks, pas de défenseur, progression initiale entre 0,10 et 0,30 selon la difficulté de l'œil.
- **Calcul** (`OcularContestMath.pulse`) : la Force avance vite (0,17) mais fait perdre la prise et crée du **trauma** (+3 si l'exécution n'est pas propre, +3 si la prise est sous 0,45). Le Contrôle restaure la prise et réduit le trauma. La Précision est l'option équilibrée. Côté victime : S'arc-bouter freine fortement la Force, Briser fait baisser la prise (et **brise l'entrave** si la prise tombe à 0). L'avance est divisée par la difficulté de l'œil. L'extraction est prête à `progress ≥ 1` et `grip ≥ 0,55`. La victime gagne si la progression ou la prise tombe à 0.
- **Profils de stats** du chirurgien : MEDICAL (Médecine 55 %, Contrôle 20 %…), FIELD, BRUTAL (Taïjutsu 35 %, Vitalité 25 %…). Le défenseur s'appuie sur Contrôle 30 % et Vitalité 25 %.
- **Méthodes** (`ExtractionMethod`) : Médicale −0 % d'intégrité, Terrain −7 %, Brutale −18 %. S'y ajoute le trauma accumulé (plafonné à 40), moins la **protection** du médic (jusqu'à 18, selon la Médecine et la maîtrise chirurgicale /17). La perte d'intégrité totale est plafonnée à 95 %.
- **Accès à un vivant** (`OcularExtractionOfferManager.resolveLiveAccess`) : soit une **entrave enregistrée** (`OcularRestraintRegistry` : Imitation d'ombre, force 0,56, cassable, le lanceur ne peut pas extraire lui-même ; Chaînes de scellement, 0,54 à 0,62, le lanceur peut extraire), soit une **interception** (`OcularInterceptionTracker`) : la victime est immobile depuis **36 ticks** (1,8 s), dōjutsu visible, hors attaque. L'ouverture est alors faible (0,16 à 0,28) et la prise de départ vaut 0,42. Message : « Pas d'ouverture : entravez la cible ou attendez qu'elle reste immobile 2 s ».
- **Qualification** (`OcularSurgerySkill`) : il faut le succès « Medical Genin », Médecine 12, Contrôle 12, Ninjutsu 8, et connaître les soins Iryō et le mode médical. La maîtrise chirurgicale (0-100) progresse de +1 par cadavre, +2 par vivant ou greffe. Le scalpel s'use.
- **Garde-fous** : pas pendant une attaque ou une charge (`combatSafe`). Tout dégât reçu interrompt (`interruptOnDamage = true`). Distance validée par le serveur (1,75 bloc par défaut, `extraction_range`) et ligne de vue. Des interrupteurs de config séparent vivant, soi et cadavre (`ModServerConfig` §ocular_system).
- **Sur soi** (`SelfOcularStruggleContext`) : « Parchemin d'extraction volontaire » réutilisable (butin de donjon), une fiole vide par œil, au sol, dōjutsu actif. Chaque pulsation **draine du chakra et blesse** (environ 1,5 % des PV max par impulsion, entre 1 et 8, × un facteur de coût ; arrêt si les PV deviennent trop bas). Une réussite entraîne une cécité brève et de la faiblesse. Une annulation coûte quand même.
- **Sur cadavre** (`OcularLifecycleHooks.onDeath`) : à la mort réelle d'un joueur, une entité `EntityShinobiCorpse` apparaît et ses yeux passent en **ESCROW** pendant 2400 ticks (2 min, réglable jusqu'à 1 h). S'ils sont extraits, l'orbite du propriétaire passe à MISSING. Sinon, ils lui reviennent à expiration (si la gamerule le permet).

**Intérêt RP** ★★★ : c'est le cœur de la vidéo, et le meilleur concept. Un vol d'yeux dramatique, interactif des deux côtés : la victime n'est jamais passive, elle se débat. Les tiers peuvent sauver la victime pendant la fenêtre finale. Les stats et la formation médicale ont un sens.
**Risques** : (1) **mutilation permanente sans consentement**. Sur un serveur RP, c'est l'équivalent d'une RPK partielle : le gank, la vengeance hors RP et les « collectionneurs » d'yeux sont les dangers évidents. (2) L'interception « immobile 1,8 s » est **anti-RP** pour Reborn : un joueur AFK ou en train d'écrire un `/me` peut se faire arracher un œil. (3) Les fenêtres de 1 à 4 ticks pénalisent les pings élevés malgré la tolérance. (4) L'extraction sur soi est facile à abuser si les yeux s'échangent hors RP.

**Faisabilité Reborn** : **moyen à lourd**.
- Serveur (ShinobiCore) : session de duel, rounds, notation par tick serveur. Le modèle « le client envoie une direction, le serveur horodate » est directement transposable. Les **mini-jeux de parchemin et la concentration** d'`APPRENTISSAGE.md` montrent que la plomberie existe déjà (écran mod + validation serveur).
- Client (reborn-hud) : écran de duel (flèches, deux barres, tactiques), caméra de cinématique. L'extraction peut d'abord se jouer **sans cinématique**, avec une emote EmoteCraft.
- **Adaptation recommandée** : supprimer l'interception par immobilité. Pour un vivant, n'autoriser l'extraction que sur un **corps KO** (état existant de `KO_ET_MORT_RP.md`), et la soumettre à une **validation staff calquée sur la RPK** (`KillRequestManager` : demande, validation, droit accordé, « dernière action »). Exemple : « Demander un prélèvement oculaire ». Sur un KO, le défenseur ne peut pas se débattre ; on peut lui laisser le mini-jeu en « dernier sursaut » si le staff le veut, ou faire d'un tiers présent le défenseur.
**Équivalent Reborn** : les actions sur corps (`/corps`, menu R : ausculter, porter, attraper à la gorge, fouiller, exécution) en sont la porte d'entrée naturelle. Il suffit d'ajouter une action « Prélever un œil ▸ ».

---

## 3. Cécité et état du porteur

**Ce que voit le joueur**
- Un œil aveugle (définitivement, ou par fatigue du Mangekyō) **noircit la moitié correspondante de l'écran**, avec un dégradé de 34 px au centre. Le monde reste perceptible en **fil de fer** : les arêtes des blocs dans un rayon de 24 blocs sont projetées en traits blancs (`client/vision/NydoOcularBlindness`). Un minuteur s'affiche par œil (« 4:32 » ou « permanent »). Une cécité totale (genjutsu) assombrit tout l'écran avec un minuteur central.
- Le visage reçoit des **traînées de sang** animées pendant et après l'opération, et l'orbite vide est rendue (`dojutsu/ocular/client/OcularTraumaRenderer`, `OcularTraumaVisuals`, clé `nydomod:ocular_wound_visual`).
- **Aucun bandage** : la recherche « bandage » ne renvoie que du décor de village. Le seul habillage du porteur est le sang et l'orbite vide.

**Comment ça marche**
- **Fatigue du Mangekyō** (`system/DojutsuFatigue`, `item/ItemSharingan.applyDojutsuWear`) : chaque technique use l'œil, via sa durabilité (« strain » : Tsukuyomi 60, Kotoamatsukami 70, Kamui 55…). Au-delà de **72 %** d'usure, chaque utilisation a une chance entre 0,005 % et 0,2 % de déclencher une cécité temporaire. Les paliers durent **45 s, 1 min 30, 3 min, 5 min, 7 min 30, 10 min**, et le palier redescend après 10 min sans incident. Le multiplicateur est configurable ; à 0, la cécité temporaire est désactivée. Une utilisation continue bénéficie d'une grâce de 6 s.
- **Cécité définitive** : le flag `sharingan_blinded` sur l'œil, posé en cas d'issue BLINDNESS de la chirurgie (§5). Un œil aveugle **désactive ses pouvoirs** (`DojutsuPowerCatalog`, `isVisionBlocked`) et empêche le Susanoo.
- **Orbite vide** (`MISSING`) : le joueur perd les pouvoirs de ce côté. Une fiole ou une greffe peut remplir l'orbite ; « l'œil normal » ne régénère pas une orbite vide.

**Intérêt RP** ★★★ : la cécité est un **handicap jouable**, pas une mort. Un œil bandé, une moitié d'écran noire, un ninja qui doit se faire guider : on obtient des arcs narratifs (quête de greffe, rival qui porte votre œil). Le prix du Mangekyō (« plus je l'utilise, plus je m'aveugle ») est fidèle au canon et freine le spam.
**Risques** : une moitié d'écran noire est pénible en PvP. Il faut un moyen RP de compenser (sens du chakra, Byakugan…). Une cécité définitive sans retour peut faire quitter un joueur : il faut garantir une voie de guérison (greffe, quête staff).

**Faisabilité Reborn** : **facile à moyen**. Le mod reborn-hud sait déjà superposer des filtres plein écran par état serveur (`ko.fsh` : désaturation, vignette, paupières ; priorité KO > Byakugan > Météo, `KO_ET_MORT_RP.md` §10). Un masque demi-écran plus un minuteur est un ajout direct. Le fil de fer des blocs (projection d'arêtes) représente un vrai travail de rendu (moyen). **Le bandage est à inventer chez Reborn** : un cosmétique de visage dans le compositeur de skin (`skin/`), posé par le médic (`/soigner`) à la place du sang. C'est une bonne idée originale.
**Équivalent Reborn** : les blessures par partie du corps et `/soigner` (`KO_ET_MORT_RP.md` §5) ; une blessure « Œil (gauche/droit) » de gravité Urgente avec une séquelle permanente s'y intègre naturellement.

---

## 4. Greffe (implantation)

**Ce que voit le joueur** : un médic qualifié, scalpel en main, opère un patient conscient à moins de 3 blocs, en ligne de vue (`OcularTransferService.tryAutoTransplant` / `commitStoredTransplant`). Le patient doit avoir **un seul** œil détaché en stock ; s'il en a plusieurs, le message est « ambigu ». Ou bien le joueur ouvre lui-même une fiole scellée (1,2 s). Les cinématiques sont `eye_implant_left/right(_offhand).json`.

**Comment ça marche** : greffe du **même côté uniquement** (« un œil droit volé ne s'implante qu'à droite »). Un dōjutsu déjà en place est extrait et **conservé**, pas détruit. Le patient ne doit être ni en duel, ni en grapple, ni en chirurgie. La maîtrise chirurgicale progresse de +2. Un œil greffé qui n'appartient pas au porteur (`isOwner`) **coûte plus cher** : genjutsu Sharingan 300 chakra au lieu de 100 et usure ×3 ; techniques MS ×3 en chakra (`DynamicMangekyoAbilities.begin`) ; Susanoo ×2 en entretien (`EntitySusanooBase.chakraUsageModifier`).

**Intérêt RP** ★★★ : un « Kakashi » est possible, avec un vrai coût pour l'œil d'emprunt. Les médics deviennent incontournables, et c'est une profession à part entière.
**Risques** : les greffes en chaîne entre amis pour optimiser le PvP. Il faut un **verrou staff** ou au moins une journalisation.
**Faisabilité Reborn** : **moyen**. Réutiliser `/paume` et `/soigner` (immobilité, ciblage, rang médical `shinobicore.medic.expert`) pour une action « Greffer ». **Équivalent** : les rangs médicaux existent, la greffe non.

---

## 5. Chirurgie du Mangekyō Éternel (table d'opération)

**Ce que voit le joueur** : une **salle de microchirurgie** avec une table de bloc dédiée (`OcularOperatingTableBlock`, « Ocular microsurgery table »). Il y a deux PNJ chirurgiens : **Nao** (chambre de Konoha, 12 000 ryo) et **Kuro** (chirurgien renégat Akatsuki, 4 000 ryo) (`OcularSurgeryStations`, `OcularClinicBlueprint`). Sans PNJ, il existe un **manuscrit d'auto-chirurgie** (gratuit, exige « Medical Genin »). Le patient choisit **son MS** puis une **paire donneuse compatible** dans deux colonnes. L'écran affiche ensuite les **chances** (succès / échec / cécité) et le profil (Contrôle /60, Vitalité /60, santé à l'admission, intégrité du donneur), puis « J'accepte · commencer ». Le joueur est allongé ; le PNJ se place et s'anime. ESPACE montre la salle (cinématique `ocular_operating_table.json`, 2400 ticks), Échap annule.

**Mini-jeu (3 phases, 24 gestes)** (`OcularSurgeryChallenge`, `GuiMedicalScroll.Menu`) :
1. **Incision** : 12 tracés, 6 nœuds × 2 yeux. Il faut suivre un arc à la souris, bouton maintenu. Erreurs : hors du chemin (distance > 5 px), trop rapide (saut de plus de 40 % du tracé), tracé trop court (au moins 6 ticks).
2. **Connexion du chakra** : 4 gestes. Il faut suivre une cible mobile (orbite de 84 ticks), charger à 14/20 en restant aligné, puis relâcher **dans la pulsation** (cycle de 28 ticks, ±3). Un décrochage de 6 ticks est une erreur.
3. **Sutures** : 8 gestes. Après le tracé, il faut **tenir l'aiguille immobile** et relâcher entre 10 et 22 ticks après la mise en tension (« relâchez dans la bande turquoise »). Trop tôt ou trop tard est une erreur, et la tension cède seule au-delà de 28 ticks.
- Chaque erreur **blesse** le patient (2 PV, `ERROR_DAMAGE`) et retire 4 points de chance de succès. Au bout de **5 erreurs, l'opération avorte**, avec 30 à 40 % de chances de **perdre la paire donneuse**. Le message de mort existe : « %1$s est mort pendant une chirurgie oculaire ».
- **Opération parfaite** : 0 erreur donne un score de 100 (`finalScore = 100 − 5 × erreurs`). Le score est gravé sur l'œil (`nydomod:eternal_ritual` : stabilité, erreurs, résonance, date). La « résonance » vaut 60 + intégrité du donneur / 4, +15 pour le couple canonique (Sasuke + Itachi, Madara + Izuna). Le score s'affiche, sans autre bonus de jeu trouvé.

**Probabilités** (`OcularSurgeryProfile.odds`) :

| Prestataire | Succès de base | Plafond | Cécité de base | Perte du donneur après 5 erreurs |
|---|---|---|---|---|
| Soi (manuscrit) | 58 % | 85 % | 8 % | 40 % (min 30) |
| Akatsuki (Kuro) | 74 % | 92 % | 4 % | 36 % |
| Konoha (Nao) | 88 % | 98 % | 1 % | 32 % |

Bonus : Contrôle / 5 + Vitalité / 10, lignée Uchiha +8, Senju ou Uzumaki +4. Malus : tissu abîmé et santé basse. −4 % de succès et +1 % de cécité par erreur. Le jet se fait **après** le mini-jeu. **Conditions d'admission** (`OcularSurgeryAdmission`) : argent (portefeuille shinobi) ; Konoha refuse si la réputation est sous −40 ou si le patient est **recherché** ; Akatsuki refuse s'il lui est hostile.
**Résultat** : réussite, les deux paires sont consommées et une paire Éternelle naît, qui hérite de l'identité des yeux du **donneur**. Échec : rien ne change. Cécité : les yeux primaires deviennent **aveugles définitivement**.
**Compatibilité** : seulement MS de Sasuke + MS d'Itachi, et Madara + Izuna. Le donneur doit être une paire **détachée, complète, d'un autre joueur**, intègre, non aveugle.

**Deux joueurs ?** Non : la chirurgie EMS se fait toujours entre **un joueur et un PNJ**, ou seul. Le chirurgien joueur n'existe que pour la greffe simple (§4) et l'extraction (§2).

**Intérêt RP** ★★★ (★★ pour Reborn tel quel) : le design « choix du prestataire = choix moral et social » est excellent. L'hôpital du village est sûr et cher, mais il refuse les criminels ; le chirurgien renégat est moins cher et plus risqué. Le score gravé sur l'œil sert de trophée. Une **salle d'opération** devient un lieu de RP.
**Risques** : un Mangekyō Éternel accessible par PNJ et argent contourne le staff ; chez Reborn, une évolution de cette ampleur doit rester **une décision du staff**. La mort sur la table doit être cadrée : vraie mort RP, ou simple KO ?

**Faisabilité Reborn** : **moyen**. Le mini-jeu de tracé à la souris est un écran reborn-hud (formes simples, validation des échantillons côté serveur comme chez Nydo, qui rejoue les positions envoyées). La table est un bloc ou meuble Nexo, et le chirurgien PNJ un Citizens ou un **médic joueur** (meilleur pour le RP).
**Adaptation proposée** : le chirurgien est un **joueur médic de rang expert**, et c'est lui qui joue le mini-jeu, pas le patient. Le passage à l'Éternel exige un **ticket staff validé** en amont, comme la RPK. Les prix et refus (réputation, recherché) se branchent sur le futur système de village et de réputation.

---

## 6. Pouvoirs indépendants par œil

**Ce que voit le joueur** : une roue ou un HUD des pouvoirs oculaires (`dojutsu/hud/OcularPowerHudRenderer`). Chaque pouvoir est marqué G, D, G|D ou G+D, et disparaît si l'œil requis manque ou est aveugle.

**Comment ça marche** (`OcularPowerRequirement` {LEFT, RIGHT, EITHER, BOTH, NONE} ; `DojutsuProfile.requirementForSlot` ; `OcularPowerResolver.canUse`)
- Par défaut : slot 0 = **œil gauche**, slot 1 = **œil droit**, slot 2 = l'un ou l'autre. Les variantes « split third » mettent le slot 2 à droite. Le MS d'Indra a le slot 1 à gauche et le slot 2 à droite. Les 7 arts Enton de Sasuke : 0 à gauche, 1 à 3 à droite, 4 à 6 avec les deux yeux.
- Exemples (`ItemDynamicMangekyo.Profile`) : Itachi = Tsukuyomi (G) / Amaterasu (D) / Susanoo ; Shisui = Kotoamatsukami / Susanoo ; Kakashi = Kamui / Raikiri / Susanoo ; Madara = Limbo / Susanoo ; Izuna = retour dans le temps / Susanoo. Il y a aussi des MS originaux (Naka, Naori, Rai, Baru, Fugaku, Nanashi).
- Le pouvoir suit l'**origine** de l'œil, pas l'orbite. Un œil gauche d'Itachi greffé apporte Tsukuyomi ; son porteur ne gagne pas Amaterasu.
- Izanagi **sacrifie** un œil stocké (`sacrificeStoredEye`) : consommer un œil comme ressource.

**Intérêt RP** ★★★ : voler **un seul** œil a du sens, avec des personnages à moitié armés et des échanges négociés (« je te rends ton œil droit contre… »). C'est cohérent avec l'œil-objet du §1.
**Risques** : la combinatoire (un œil gauche de X, un œil droit de Y) complique l'équilibrage. Il faut limiter les techniques par œil.
**Faisabilité Reborn** : **facile** une fois le §1 en place. Le Technique Creator et ShinobiAbilities n'ont qu'à exiger « œil G/D actif et voyant » comme prérequis de technique. **Équivalent** : aucun. Le Byakugan Reborn est un mode global.

---

## 7. Éveil (Sharingan, tomoe, Mangekyō)

**Comment ça marche**
- **Sharingan** (`procedure/ProcedureKGDistribution`) : un tirage pondéré très rare (environ 0,1 % par tentative). Il faut être ninja, avoir 300 d'XP de combat et une nature de chakra, sans autre éveil avancé. Le Byakugan sort du même tirage.
- **Tomoe** (`system/SharinganProgression`) : l'XP vient des **coups reçus** (au plus 1 fois par seconde, dégâts × 2), de la copie de techniques (+3 / +12) et de l'usage (+8). 2 tomoe demandent 200 XP et **1 « porte émotionnelle »** ; 3 tomoe demandent 700 XP et 2 portes. Les portes sont : **danger critique** (PV ≤ 25 % après un coup), **mort d'un familier nommé**, **mort d'un Uchiha du clan**. Le genjutsu de base exige 2 tomoe.
- **Mangekyō** (`system/clan/MiniClanMangekyoTrigger`) : être **témoin** de la mort d'un membre Uchiha « de sang pur » de son mini-clan, membre depuis **au moins 30 min** (36 000 ticks), à 32 blocs ou moins, **en ligne de vue**, avec au moins 1 500 d'XP de combat, sans en être le tueur ni appartenir au même clan que le tueur. Délai de recharge par défunt : 2 h de jeu (144 000 ticks). Autre voie : la mort d'un **familier nommé**. Le motif du MS est tiré au hasard (`chooseBaseMangekyo`).
- La cinématique `eternal_mangekyo_awakening.json` (72 ticks, plus un effet visuel) est réservée à l'Éternel.

**Intérêt RP** ★★ : les « portes émotionnelles » sont un bon concept, fidèle au canon : le pouvoir naît du trauma, pas du farm. **Risques** : le MS automatique sur la mort d'un allié pousse à **tuer des proches pour l'éveil** ; le délai de 30 min de membre est trivial. Chez Reborn, l'éveil du MS doit être **narratif et validé par le staff**.
**Faisabilité Reborn** : **facile** (côté serveur uniquement). Hook sur `CharacterDeathEvent` : le serveur détecte les témoins Uchiha et **notifie le staff** (« X a assisté à la mort de Y : candidat à l'éveil »), au lieu d'éveiller automatiquement. Les tomoe par XP et paliers d'événements se branchent sur `STATS_ET_PROGRESSION`. **Équivalent** : aucun Sharingan pour l'instant ; les iris Sharingan/Rinnegan sont prévus en vague 2 (`ROADMAP_BETA_2026.md` S4).

---

## 8. Susanoo (phases, taille, puissance)

**Ce que voit le joueur** : une touche invoque une cage de côtes autour de lui ; une seconde action le **fait monter d'une phase**, avec une cinématique de formation de 24 ticks (courbe smootherstep) et un son propre à chaque phase. Il y a 6 phases et des modèles **spécifiques par personnage** (Sasuke, Itachi, Madara, Shisui, Kakashi) ou génériques, avec une palette de flammes personnalisable (`SusanooPaletteScreen`). Aux phases avancées, le joueur est porté à l'intérieur (cavalier physique). En phase ailée, il vole.

**Comment ça marche**
- **Phases** (`SusanooPhase`, `SusanooScale.baseHeight`) : 1 Cage 2,4 blocs → 2 Squelette 8 → 3 Vêtu 13 → 4 Armuré 25 → 5 Humanoïde complet 40 → 6 Ailé 53,3. La largeur suit des ratios de torse (0,32 à 0,46). La portée des coups monte jusqu'à ×12.
- **Mise à l'échelle par chakra** (`combat/ColossalChakraPower`) : potentiel = √(min(chakra max, 100 000) / 100 000). La hauteur va jusqu'à **+50 %**, la largeur jusqu'à +15 % en plus, et les **dégâts jusqu'à ×4**. Il existe un plafond affiché de 60 blocs (humanoïde) et 80 (ailé).
- **Passage de phase** (`SusanooProgression`) : XP de maîtrise **stockée sur l'œil**. Paliers standard : 0 / 100 / 300 / 750 / 1500 / 1500 (Sasuke : 1000 pour l'humanoïde). La phase ailée n'est permise que pour Sasuke, Madara, Shisui, Kakashi et tout Éternel. Gain d'XP : +1 par seconde actif, +2 à +16 par coup ou capacité (`SusanooMastery.award`). Le serveur peut plafonner la phase (`SUSANOO_MAX_STAGE`).
- **Coût** (`ProcedureSusanoo`) : **500 chakra** à l'activation et **500 par montée de phase** ; entretien de 30 × modificateur chakra par seconde (**×2 si l'œil n'est pas le sien**). Durée maximale **820 ticks (41 s)**, puis arrêt forcé. Au contrecoup, Lenteur IV et Faiblesse III pendant 25 % de la durée d'utilisation. Recharge de 2 min. Œil aveugle : pas de Susanoo, ou dissipation immédiate.
- **Points de vie** (config) : cage 80, squelette 140, vêtu 220, armuré 340, humanoïde 340, ailé 500. Bonus de maîtrise jusqu'à **+65 %** (courbe en racine carrée) ; multiplicateurs par profil (Indra ×2).
- **Capacités** : 3 par Susanoo (coût en chakra et recharge affichés). Prise et étreinte d'une cible (`SusanooBindingGrip`, `SusanooBindingPose`). Lame de Totsuka qui **scelle** des entités, **jamais un joueur** (`TotsukaSealSavedData.seal` exclut `ServerPlayer` ; 16 sceaux par œil, 512 par monde).

**Intérêt RP** ★★ : le Susanoo est un moment de spectacle. La progression par maîtrise **portée par l'œil** est un beau concept : voler l'œil, c'est voler la maîtrise ; l'infobulle de la fiole l'affiche. La limite de 41 s et le contrecoup en font une carte ultime, pas un état permanent.
**Risques** : un colosse de 40 à 80 blocs **casse l'échelle d'un serveur RP** (villages, intérieurs, lisibilité), rend les combats illisibles et coûte cher au client. En multijoueur, un Susanoo n'est acceptable qu'en **événement encadré**.
**Faisabilité Reborn** : **lourd**. Il faut une entité serveur à géométrie animée : Model Engine est absent côté serveur (`CHANTIERS.md`, Modélisation 3D), bbforge produit les modèles mais rien ne les intègre. Il faudrait aussi des hitbox géantes, un siège de cavalier et le rendu côté mod. **Recommandation** : se limiter aux phases 1 à 3 (cage, squelette, torse ; 2 à 13 blocs), sous forme de **bouclier et armure de chakra** autour du joueur, plutôt qu'un colosse monté. Les phases 4 à 6 restent des scènes staff ou cinématiques. **Équivalent** : aucun.

---

## 9. Genjutsu (Sharingan, Tsukuyomi, Kotoamatsukami)

**Ce que voit la victime**
- **Genjutsu du Sharingan** : une courte cinématique « révélation du Sharingan » (`genjutsu/sharingan_left.json`, ou variante au hasard), puis paralysie et cécité.
- **Tsukuyomi** : une **cinématique plein écran de 10 s** (`genjutsu/tsukuyomi.json`, 200 ticks, HUD masqué, **contrôles verrouillés, non passable**) : plan infini, ondulations d'eau, lune, nuages, étalonnage rouge (`client/cinematic/TsukuyomiSceneRenderer`, `NydoCinematicShaderMaterials`, compatible avec les shaderpacks). Le lanceur voit la scène aussi (`playForCasterAndTarget`).
- **Kotoamatsukami (Shisui)** : une cinématique de 18 s (`genjutsu/shisui.json`, 360 ticks, avec un acteur) et le message « Les cieux plient ta volonté… ».

**Comment ça marche**
- Sharingan (`procedure/ProcedureSharinganGenjutsu`) : portée 24 blocs, **contact visuel** (rayon et ligne de vue). 2 tomoe requis. Coût 100 chakra (300 si l'œil n'est pas le sien). Durée de base 120 ticks, puis paralysie, cécité et nausée. Recharge 400 ticks × modificateur de niveau (minimum 80). Usure de l'œil : 1 (ou 3 pour un œil emprunté).
- Mangekyō (`system/DynamicMangekyoAbilities.genjutsu`) : **Tsukuyomi** = portée 20, 800 chakra, 600 ticks, **10 dégâts mentaux**, Lenteur IX, Faiblesse IV, cécité, nausée, usure 60. **Kuroyume** = portée 15, 750 chakra, 400 ticks, 4 dégâts. **Kotoamatsukami** = portée 20, **2000 chakra, recharge de 3 min**, la cible **ne peut plus attaquer personne pendant 30 s** (`LivingAttackEvent` annulé tant que `NYDOKotoUntil` court), plus nausée et faiblesse, usure 70.
- Échelle (`NinjaStats.scaleGenjutsuDuration` / `scaleIncomingGenjutsu*`) : la stat Genjutsu du lanceur augmente la durée et les dégâts ; celle de la victime **les réduit** (résistance).
- **Contres** : (1) le **mode ermite actif** transforme le genjutsu oculaire en simple cécité de 2 à 4 s (`system/sage/SageGenjutsuDefense`) ; (2) la nuée d'insectes Aburame active rend insensible au genjutsu de base (`ItemInton.Genjutsu.canTargetBeAffected`) ; (3) la stat Genjutsu en défense. **Pas de « Kai »** manuel côté joueur dans ce code.

**Intérêt RP** ★★ : le Tsukuyomi en cinématique est un **moment fort** pour la victime comme pour les spectateurs. La règle du **contact visuel** pousse à jouer « ne regarde pas ses yeux ». Kotoamatsukami (« tu ne peux plus attaquer ») est un vrai outil narratif : manipulation, retournement.
**Risques** : **verrouiller les contrôles** et forcer une scène non passable est très intrusif en multijoueur ; c'est un vecteur d'abus (stun-lock, harcèlement). Kotoamatsukami sur un joueur touche au **consentement RP** : contrôler la volonté d'un personnage. Chez Reborn, l'effet doit se borner à « ne peut pas attaquer le lanceur » et rester encadré.

**Faisabilité Reborn** : **moyen**. Le serveur sait déjà faire (`ShinobiSense/GenjutsuManager` : effets, chuchotements, **Kai** payant en chakra). Le travail porte sur la scène côté reborn-hud : le mod a déjà des scènes et filtres plein écran (KO, scène de mort grisée avec bandes de cinéma, Byakugan). Une version « Tsukuyomi » = ciel rouge, lune et filtre pendant N s, **sans verrouiller les contrôles**, et **Kai** toujours disponible.
**⚠️ Décision existante** : **« genjutsu hors beta »** (`docs/pilotage/DECISIONS.md`, 2026-09-23, option B à 6 stats). Ce chantier est donc post-beta, et la stat Genjutsu n'existe pas chez Reborn : la résistance devra s'appuyer sur Contrôle.
**Équivalent Reborn** : `GenjutsuManager` (lancer, Kai, durée) et `DojutsuManager` (dōjutsu actif = drain de chakra par seconde, **visible** des voisins, lié au clan).

---

## 10. Architecture transverse à retenir

- **Le serveur est la seule autorité** : « les cinématiques client ne possèdent jamais l'état des yeux » (commentaire de config). Toutes les mutations sont transactionnelles, avec rollback et vérification après coup. Ce principe correspond à celui de Reborn.
- **Cinématiques data-driven** : du JSON (`schema`, `duration`, `hide_hud`, `lock_input`, `skippable`, `letterbox`, fondus, caméra, animations, acteurs, marqueurs) lu par un directeur client (`client/cinematic/NydoCinematicDirector`, `NydoCinematicDefinition`). Pour Reborn, un format équivalent dans reborn-hud servirait à la fois les exécutions (`KO_ET_MORT_RP.md` §8), le genjutsu et l'extraction.
- **Interrupteurs par fonctionnalité** en config (live, soi, cadavre, phase max du Susanoo, multiplicateur de cécité) : indispensables pour régler finement en production.

---

## Tableau final

| Idée | Ce que fait Nydo | Reborn a déjà ? | Valeur RP | Effort |
|---|---|---|---|---|
| Œil = organe unique traçable | UUID d'œil et de paire, côté, intégrité, historique, registre anti-duplication | Non (dōjutsu = mode de clan, `DojutsuManager`) | ★★★ | Moyen |
| Orbites NORMAL / ESCROW / MISSING | État par côté sur le personnage | Non | ★★★ | Facile |
| Extraction sur cadavre | Le cadavre garde les yeux 2 min ; duel solo de timing | Corps au sol 15 min + menu R (`/corps`) | ★★★ | Moyen |
| Extraction sur vivant | Entrave (ombre, chaînes) ou cible immobile 1,8 s ; duel à deux | KO + RPK validée par le staff (modèle à copier) | ★★★ (si validée staff) | Lourd |
| Duel « Struggle » serveur | Timing en ticks, tolérance de ping, stats ±18 %, tactiques, tiers peuvent interrompre | Mini-jeux de parchemin / concentration | ★★★ | Moyen |
| Extraction volontaire | Parchemin + fiole, coût en PV et chakra | Non | ★★ | Facile |
| Fiole scellée à usage unique | Objet commerçable, dossier médical en infobulle | Précédent : parchemin-objet | ★★★ | Facile |
| Cécité demi-écran + fil de fer | Masque par œil, minuteur, arêtes des blocs à 24 blocs | Filtres plein écran (KO, Byakugan) | ★★★ | Moyen |
| Fatigue du Mangekyō | Usure > 72 %, cécité de 45 s à 10 min par paliers | Non | ★★ | Facile |
| Bandage / œil masqué | **Absent** (sang + orbite vide seulement) | Compositeur de skin, `/soigner` | ★★★ | Facile |
| Greffe même côté par un médic | Médic qualifié à ≤ 3 blocs ; l'œil remplacé est conservé | Rangs médicaux, `/paume`, `/soigner` | ★★★ | Moyen |
| Surcoût de l'œil emprunté | Chakra ×2 à ×3, usure ×3 | Non | ★★ | Facile |
| Chirurgie Éternelle sur table | PNJ Konoha / Akatsuki / soi, chances, admission par réputation | Non | ★★ (★★★ avec un médic joueur + staff) | Moyen |
| Mini-jeu chirurgical 3 phases | Incision, connexion chakra, sutures ; 5 erreurs = échec ; « parfait » gravé | Écrans mod de mini-jeux | ★★ | Moyen |
| Pouvoirs par œil G/D | Prérequis LEFT / RIGHT / EITHER / BOTH par slot | Non (Technique Creator pourrait l'exiger) | ★★★ | Facile |
| Éveil par « portes émotionnelles » | Danger critique, mort d'un familier, mort d'un Uchiha en ligne de vue | `CharacterDeathEvent` (hook possible, avec alerte staff) | ★★ | Facile |
| Susanoo 6 phases | 2,4 → 53 blocs, XP sur l'œil, 41 s, 500 chakra par phase | Non ; Model Engine non intégré | ★★ | Lourd |
| Susanoo réduit (cage, squelette) | Phases 1-2 = 2,4-8 blocs | Non | ★★ | Moyen |
| Genjutsu cinématique (Tsukuyomi) | Scène plein écran de 10 s, contrôles verrouillés | `GenjutsuManager` (effets + Kai) ; scènes mod | ★★ | Moyen (post-beta) |
| Kotoamatsukami | La cible ne peut plus attaquer pendant 30 s | Non | ★★ (consentement à cadrer) | Facile |
| Contres de genjutsu | Mode ermite, insectes, stat de résistance | **Kai** payant en chakra (plus RP) | ★★ | Facile |
| Cinématiques data-driven | JSON durée / HUD / verrouillage / caméra / acteurs | Scènes ad hoc (mort, KO) | ★★ | Moyen |

**Ordre conseillé pour Reborn** : (1) modèle de données œil + orbites (§1) ; (2) action « Prélever un œil » sur **cadavre ou corps KO avec validation staff** (§2, sur le circuit RPK) ; (3) cécité demi-écran et bandage (§3) ; (4) greffe par un médic joueur (§4) ; (5) pouvoirs G/D (§6). Le Susanoo complet et le genjutsu cinématique viennent après la beta (décision du 2026-09-23 pour le genjutsu).
