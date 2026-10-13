# NydoMod 5.5 — étude du combat (pour Reborn)

> Source : code décompilé `net/nydomod/` (hors dépôt) (chemins ci-dessous relatifs à ce dossier) et
> `res/assets/nydomod/lang/en_us.json`. Mod « All Rights Reserved » : on retient des **concepts et des chiffres**,
> pas de code ni d'asset. Une partie des animations vient d'un pack tiers (`player_animation/combat/licensed_boundless/`),
> donc de toute façon non réutilisable.
> 1 tick = 50 ms. Côté Reborn, comparaison faite avec `ShinobiCombat/…/combat/CombatListener.java`,
> `mod-hud/…/combat/`, `docs/CHANTIERS.md`, `docs/gameplay/KO_ET_MORT_RP.md`, `docs/technique/MOD_HUD.md`,
> `docs/technique/PLUGINS_REFERENCE.md`, `docs/gameplay/ANIMATIONS_BASE.md`.

## Résumé en 10 lignes

1. Le combat Nydo (« NYCombat », ~42 000 lignes dans `combat/`) est **autoritaire côté serveur** : le client envoie des *intentions* numérotées, prédit l'animation, et le serveur accepte, met en tampon ou refuse (avec un motif d'acquittement).
2. Les combos sont des **chaînes de L/R confirmées au contact** : un clic ne compte dans la route que s'il touche ; la route est le suffixe (max 6) des boutons.
3. Le clic droit **défend** (garde / parade) tant que le joueur n'a pas placé **deux coups gauches qui touchent** ; ensuite il devient offensif : c'est l'astuce centrale.
4. Les finishers sont des routes nommées par style (`LLRR`, `LLRLRL`…), avec coûts endurance + chakra, et un **guide HUD** qui affiche la route en cours et les alternatives.
5. Défense en 4 couches : garde (jauge 100), **parade timée de 5 ticks** (anti-spam 12 ticks), garde cassée, et **pression** qui pénalise l'attaquant qui martèle une garde.
6. Esquive = dash directionnel à charges (2), avec **« jeu de jambes parfait »** si l'on esquive pendant la fenêtre active du coup adverse, ce qui ouvre un contre.
7. Combat aérien : lanceur → poursuite (double-tap direction) → juggle plafonné (9 points), **dégâts et hitstun dégressifs**, et l'**« air tech »** qui permet à la victime de s'échapper.
8. « Shinobi Preparation » / « MAX JUTSU » = **loadout restreint** : 3 presets, 2 à 6 disciplines selon le niveau, **4 techniques préparées max** par discipline ; R maintenu ouvre la roue radiale.
9. Les jutsu se lancent en tenant le clic pendant une **suite de signes** (4 à 8 selon le rang, 2 à 12 ticks par signe, accélérée par la maîtrise) ; le clash Rasengan/Chidori est un **QTE à 4 manches + phase de martelage**, cinématique.
10. Pour Reborn, les idées à fort rendement RP : confirmation au contact + R défensif, parade timée anti-spam, pression de garde, loadout limité, contrôle du HUD de cible par la perception (dōjutsu) ; le combat aérien et les clashes cinématiques sont lourds et plus « anime » que RP.

---

## 1. Architecture réseau : qui décide

**Joueur** : réactivité immédiate (l'animation part tout de suite), mais un coup refusé « se rembobine » sans désynchroniser.

**Fonctionnement**
- Le client envoie `combat/network/NYCombatIntentMessage` (type d'intention, numéro de séquence, tick client, direction, cible prédite, action prédite). Le serveur répond par un acquittement typé (`CombatIntentAckReason` : ACCEPTED, BUFFERED, COOLDOWN, STATE_LOCKED, INSUFFICIENT_STAMINA, INVALID_ACTION, BYPASS, RELEASED).
- Prédiction client : `combat/client/NYCombatPredictionClientState` (32 intentions en attente max).
- Contrôles anti-triche, `combat/input/CombatInputSecurity` : séquence strictement croissante (pas de rejeu), intention périmée au-delà de **700 ms** (`CombatInputFreshness.MAX_PENDING_NANOS`), plafonds par seconde (**14** coups/s, **5** dashs/s espacés de 2 ticks, **8** gardes/s, **4** saisies/s). `CombatIntentIngress` : 48 intentions en file, 96/s au total.
- Compensation de latence, `combat/target/CombatLagCompensation` : historique des positions, rembobinage de `ping/2/50 + 1` ticks, **8 ticks max**, rien sous 25 ms.
- État renvoyé au client : `NYCombatStateSyncMessage` (endurance, garde, fenêtres, cadence) et `CombatComboSyncMessage`.

**Intérêt RP** : indispensable pour du PvP arbitré (aucun résultat de combat ne doit venir du client). ★★★
**Reborn** : déjà autoritaire (`reborn:combatin` C→S envoie les entrées, `reborn:combat` S→C renvoie le HUD, `CombatChannel`). Il manque la **séquence anti-rejeu, la péremption et le rate-limit par type d'entrée**, ainsi que la compensation de latence. Ajout côté plugin : **facile** (rate-limit, séquence) à **moyen** (rembobinage des positions).

## 2. Taijutsu : combos clic gauche / clic droit et routes

**Joueur** : on enchaîne librement des coups gauches ; après deux coups qui touchent, le clic droit devient un coup (sinon c'est la garde). Certaines suites précises déclenchent un **finisher** nommé. Le HUD affiche en haut « Free combat », la route reconnue, la touche suivante et ce qu'il manque (« Requires learned Kage Bunshin », « More chakra needed », « Finisher ready in 4 s »).

**Fonctionnement**
- `combat/combo/CombatComboManager.route/start`, `CombatComboState`.
- Chaque appui ajoute `L`, `R`, `H` (lourd) ou `D` (coup en dash) à `pending` ; il ne passe dans `sequence` que via `confirm()` **au contact**. Historique tronqué à 6 (`append`).
- `rightIsOffense()` : R est offensif si `leftRun >= 2` (deux L confirmés) ou si la combo est « commitée ». Sinon R = garde/parade.
- Fenêtres : combo expirée après **28 ticks** sans contact (`start`, `confirm`), prolongée jusqu'à fin d'action + 8 ; file d'entrée de **2 appuis**, chacun valable **14 ticks** (`CombatComboRules.INPUT_QUEUE_CAPACITY/TICKS`, `BUFFER_TICKS = 8`).
- Routes (`CombatComboRoutes`) : motif obligatoire `LL[LR]{2,4}`, longueur ≤ 6, 8 recettes max par style. De base : `cross_break LLRR`, `driving_palm LLRL`, `turning_kick LLLR`. Uchiha : `phoenix_vault LLRLRL`, `fireball_vault LLRLLR`, `lion_barrage LLRLRR`. Naruto : `spiral_palm LLRRLL` (Rasengan), `shadow_cascade LLRRRL` (clones). Certaines routes exigent une compétence apprise (`skill`).
- Coûts (`CombatComboRules.staminaCost/chakraCost`) : finisher frappe 16 endurance / 0 chakra ; Fireball 20 / 28 ; clones 22 / 30 ; Lion 22 / 0 ; défaut spécial 16 / 20. Recharge de finisher **120 ticks (6 s)**, récupération après finisher 14 ticks, ratée 9 ticks.
- Si le finisher n'est pas payable (chakra, endurance, recharge, compétence), le serveur **retombe silencieusement sur le coup normal** (`terminal = null`).
- Coups de base (`combat/style/BasicTaijutsuStyle`) : 7 légers en boucle, **4,0 à 5,8 dégâts**, startup **2-3 ticks**, durée 10-11 ticks, portée 3,0-3,4 blocs, arc 62°-92°, dégâts de garde 12-23. 4 lourds 7,0-8,4 dégâts, startup 3-5 ticks, dégâts de garde 34-45. 2 coups en dash 6,0-6,6.
- Anti-spam du même coup : `NYCombatState.commitRepeatPressure` (jusqu'à 5 niveaux sur 22 ticks), qui allonge la recharge via `CombatAttackTuner`.
- Coût d'endurance par style (`NYCombatStaminaProfiles`) : léger 4-6, lourd 11-18, coup en dash 16-22, dash 13-19 ; multiplicateur d'arme (épée lourde ×1,6, kunai ×0,92).
- 9 styles (`basic_taijutsu`, `uchiha`, `naruto`, `rock_lee`, `hyuga`, `kiba`, `toad_sage`, `kurama`, `kurama_mantle`) + `weapon`, chacun avec son catalogue.

**Intérêt RP** : la **confirmation au contact** (on ne « déroule » pas un combo dans le vide) et le **R défensif par défaut** donnent un combat lisible, moins spammable, où l'on peut annoncer en RP « il enchaîne » / « il se protège ». Les finishers par clan sont une bonne vitrine de l'identité de clan. ★★★
**Reborn** : a un combo M1 **linéaire de 4 coups** (`M1_COMBO_DAMAGE = {3,4,6,8}`, fenêtre `COMBO_WINDOW_MS = 1200`, finition « pyramid »), parade sur **C**, pas de branche L/R ni de route nommée.
**Faisabilité** : routes L/R confirmées au contact = **moyen** (état côté plugin, nouvelle entrée R dans `CombatInputPayload`, HUD de route dans `mod-hud`). Les finishers spectaculaires (clones, Rasengan en ruée) = **lourd** (animations + VFX).

## 3. Garde, parade, garde cassée, pression

**Joueur** : maintenir R = garde (déplacement ralenti). Appuyer juste avant l'impact = **parade** : l'attaquant est sonné 2 s. Garder trop longtemps vide la jauge : garde cassée, on est sonné. Taper en boucle dans une garde **repousse et fatigue l'attaquant**.

**Fonctionnement**
- `combat/guard/GuardManager.handleLivingHurt` : garde seulement **de face** (`frontDot`, arc de l'arme + bonus de style), jauge `MAX_GUARD = 100`, régénération +1 tous les 3 ticks hors garde (`NYCombatState.tick`).
- Parade timée, `NYCombatState.setGuarding` : armée si la garde commence **≤ 5 ticks** après l'appui, active **5 ticks** (250 ms), puis **12 ticks** avant de pouvoir réarmer (anti-spam). `CombatComboRules.PARRY_WINDOW_TICKS = 5`, `PARRY_REARM_TICKS = 12`.
- Parade réussie (`CombatParryManager.tryParry`) : seulement si l'attaque adverse est **dans sa phase active** et à ≤ 6,5 blocs ; attaquant en `PARRY_STAGGER` **40 ticks**, défenseur regagne `max(6, dégâts de garde/2)` de jauge et 8 ticks de recharge.
- Garde timée qui rate la parade : dégâts ×0,48, recul ×0,42, dégâts de jauge ×0,52, +3 jauge.
- Garde normale : réduction = arme + armure (≤ 18 %) + style + maîtrise, plafonnée à 92 % (`GuardProfileResolver`) ; poing par défaut 28 % (`GuardProfile.DEFAULT`). Garde cassée : `42 / stabilité` ticks (bornés 14-60), dégâts du coup ×0,9. Charge « guard breaker » = vide la jauge d'un coup.
- Pression, `CombatGuardPressureManager` : chaque coup bloqué par le même attaquant en 28 ticks monte la pression (max 5) ; dégâts de jauge ×(1 + 0,12 × pression), **l'attaquant est repoussé** (0,06 + 0,045 × pression) et **perd 1 + pression d'endurance**. À pression ≥ 3, les transitions de combo sur coup bloqué sont interdites.
- Armure de posture (`CombatPoiseManager`) : certains coups ont une fenêtre d'armure qui absorbe le hitstun tant que la posture n'est pas épuisée.

**Intérêt RP** : la pression de garde est excellente pour du PvP encadré : elle tue le « je martèle la garde » et récompense le changement de rythme. La parade avec réarmement empêche le spam de touche. ★★★
**Reborn** : a garde + parade timée **320 ms** + drain 25 + garde cassée 20 ticks + étourdissement de l'attaquant 16 ticks (`PARRY_WINDOW_MS`, `GUARD_DRAIN`, `GUARD_BREAK_STUN_TICKS`, `PARRY_ATTACKER_STUN_TICKS`). Manquent : **délai de réarmement de la parade, garde frontale uniquement, pression sur l'attaquant, parade valable seulement pendant la phase active du coup**.
**Faisabilité** : **facile**, tout se fait dans `CombatListener` (quelques maps par joueur).

## 4. Esquive, dash, « jeu de jambes parfait », substitution

**Joueur** : double-tap d'une direction = dash (handspring, roulade). Esquiver latéralement ou en arrière **pendant qu'un coup allait toucher** ouvre une fenêtre où le prochain clic gauche est un **contre**.

**Fonctionnement**
- `core/NYCombatManager.executeDash` : **2 charges** (`maxDashCharges`), recharge d'une charge toutes les **45 ticks** au sol ; vitesse 1,3 en avant, 1,12 en arrière, 1,16 latéral, ×0,78 en l'air. Coûte l'endurance de dash du style (13-19). Interdit en garde (sauf esquive en combo), en charge, sonné, en saisie.
- Annulation de combo en esquive : seulement après confirmation d'un coup et après `cancelAt` (`CombatComboManager.executeDodge`).
- « Perfect footwork » : si l'esquive n'est pas vers l'avant, que la cible verrouillée est à ≤ 5 blocs et que **sa hitbox active vous contenait** (fenêtre −2/+1 tick, `isPreciseDodgeOpportunity`), fenêtre de **12 ticks** où un léger devient `basic_taijutsu_native_dodge_counter`.
- **Pas de substitution (Kawarimi)** dans le combat : aucune occurrence dans le code ni le fichier de langue.

**Intérêt RP** : esquive « méritée » plutôt que gratuite, lisible. ★★
**Reborn** : esquive (`DODGE_COOLDOWN_MS = 2500`, sans coût), dash (`DASH_COOLDOWN_MS = 7000`, 35 endurance), et la technique **kawarimi** existe (`ShinobiAbilities/…/JutsuEffectRegistry`, « hop back + decoy »). Pas de charges ni de contre sur esquive parfaite.
**Faisabilité** : charges de dash = **facile** ; esquive parfaite = **moyen** (le serveur doit connaître la phase active et la hitbox du coup adverse, ce qui suppose des actions découpées en phases, cf. §8).

## 5. Endurance

**Joueur** : une barre d'endurance sous le viseur ; plus rien ne part quand elle est vide.

**Fonctionnement** : `core/NYCombatStaminaProfiles` (base **150**, max 295 + bonus de stat), `NYCombatState.tick` : +1 tous les 1-2 ticks selon le style, **après un délai de 10-19 ticks** suivant la dépense, et **pas de régénération en garde, en attaque ou en charge**.

**Intérêt RP** : bon frein à la durée des échanges. ★★
**Reborn** : déjà en place (endurance en ensō autour du viseur, `StaminaManager`, max issu des stats Vigueur + Taïjutsu). Idée à reprendre : **régénération bloquée pendant la garde** et délai par style. **Facile**.

## 6. Verrouillage de cible (lock-on)

**Joueur** : H verrouille la cible la plus proche du centre de l'écran ; la caméra et le corps pivotent progressivement vers elle ; un réticule s'affiche sur la cible avec sa barre de vie.

**Fonctionnement**
- Serveur, `combat/lockon/LockOnManager` : acquisition à **28 blocs**, cône de 156°, ±9 blocs en hauteur, perdu au-delà de **30 blocs** ou après **40 ticks** hors validité ; changement de cible par direction.
- Client, `combat/client/NYCombatLockOnClient` : rotation limitée par tick (24° à l'arrêt, 30° en garde, 32° en mouvement, 50° en mode « boost ») ; perte de vue tolérée 18 ticks.
- Rendu : `combat/client/NYCombatLockOnRendererClient` (icône 3D jusqu'à 26 blocs), `gui/overlay/OverlayNYCombatLockOn` (crochets + barre de vie).
- Le mixin concerné n'est pas un mixin dédié au lock-on : `mixin/MixinCamera` laisse `NydoCameraDirector` prendre la main sur `Camera.setup` (caméras de combo, de finisher et de clash) et ajoute la secousse d'impact (`NydoCameraShakePunch`).
- Le lock-on sert aussi au gameplay : le jeu de jambes parfait et les cibles de combo l'utilisent.

**Intérêt RP** : aide réelle au PvP à la souris en 3ᵉ personne et pour les joueurs moins mécaniques. Risque : la rotation automatique est une aide de visée, à réserver aux duels déclarés. ★★
**Reborn** : **absent** (seulement évoqué dans `ANIMATIONS_BASE.md` pour le strafe). Caméras d'épaule existantes (`RebornCamera`).
**Faisabilité** : **moyen**. Côté mod (sélection, rotation, réticule) + validation serveur de la cible (distance, ligne de vue) pour que le serveur ignore un verrouillage invalide.

## 7. Combat aérien

**Joueur** : un lanceur projette la cible en l'air ; un double-tap de direction lance une **poursuite** ; on enchaîne des coups aériens puis un écrasement au sol. La victime peut « tech » (se rétablir) si le juggle dure trop.

**Fonctionnement** (`combat/air/CombatAirManager`, `CombatAirComboContext`, `CombatAirRouteRules`)
- Fenêtre de poursuite **28 ticks** après le lanceur, une seule poursuite par lanceur, suspension 16 ticks avant / 10 après.
- Points de juggle : +2 par lanceur (+3 en relance), plafond de relance à **9** ; dégâts aériens ×max(0,42 ; 1 − 0,065 × points), hitstun ×max(0,45 ; 1 − 0,06 × points).
- **Air tech** : fenêtre de **18 ticks** pour la victime dès 6 points ou 4 coups aériens.
- Protection contre les dégâts de chute 80-100 ticks.
- Débloqué par une compétence (`FOOTWORK`, `LEE_LEAF_HURRICANE`, `UCHIHA_LION_COMBO`).

**Intérêt RP** : spectaculaire mais « jeu de baston », peu compatible avec un combat RP lent et arbitré, et pénible sur des serveurs à latence variable. Le **principe de dégressivité + échappatoire** est en revanche à retenir pour tout enchaînement. ★
**Reborn** : absent (double saut et saut de chakra existent).
**Faisabilité** : **lourd** (physique serveur de suspension, animations, synchronisation fine).

## 8. Hitbox, actions découpées en phases, animation ↔ hitbox

**Joueur** : le coup touche quand l'animation « arrive », pas au clic.

**Fonctionnement**
- `combat/action/CombatActionDefinition` : chaque action a `startupTicks`, `activeTicks`, `recoveryTicks`, `whiffRecoveryTicks` (récupération plus longue si raté), fenêtre de contre, fenêtre d'armure, mode de déplacement et de rotation, et **groupes de coups** (`CombatHitGroupDefinition`) avec `startTick/endTick`, forme de hitbox, nombre de cibles, dégâts, posture, hitstun, type de réaction, points de juggle, rebonds, dégâts de garde, `blockable`, `parryable`, **`pvpDamageMultiplier` / `pveDamageMultiplier`**, ligne de vue, `allowsAirTech`. Un groupe hors de la durée de l'action est refusé à la construction.
- Formes : `hitbox/ArcHitbox`, `ConeHitbox`, `LineHitbox`, `AabbHitbox` ; `HitboxResolver` échantillonne jusqu'à 14 points du corps, avec rembobinage (§1).
- Animation : le serveur choisit le clip et l'envoie à tous (`combat/animation/PlayerAnimationCombatBridge` → `NYCombatAnimationMessage` : id, durée, vitesse bornée 0,05-3, miroir, `TRACKING_ENTITY_AND_SELF`). La synchro repose sur le **même découpage en ticks** des deux côtés, pas sur des événements extraits de l'animation.
- 22 réactions typées (`CombatReactionType` : hitstun léger/lourd, garde, parade, projeté, écrasé, mur, rebond, tech…).

**Intérêt RP** : startup visible = on peut **réagir** (parer, esquiver), donc combat lisible. Le multiplicateur PvP/PvE par coup est très utile pour régler le PvP sans casser le PvE. ★★★
**Reborn** : coups instantanés au clic (dégâts appliqués sur l'événement de frappe, `CombatListener`), anims diffusées à 32 blocs ; ⚠️ « dégâts pas à l'échelle des PV » noté dans `CHANTIERS.md`.
**Faisabilité** : **moyen à lourd**. Un modèle « action = startup/actif/récupération + hitbox » dans le plugin est le socle de parade timée stricte, d'esquive parfaite et de cadence ; c'est le plus gros chantier mais aussi le plus structurant.

## 9. Charge, saisie, posture

- **Coup chargé** (`combat/charge/CombatChargeProfiles`, `combat/input/CombatHeldAttackState`) : maintenir plus de **20 ticks** ; charge max 29-42 ticks selon le style, bonus de dégâts jusqu'à +72 % à +116 %, coût d'endurance supplémentaire 16-24, déplacement freiné ; à 80 % de charge il devient **brise-garde**.
- **Saisie** (`combat/grapple/CombatGrappleProfiles`, ex. Rock Lee) : startup 7 ticks, capture 4, **fenêtre d'échappement de 3 ticks** pour la victime, 5,2 dégâts (×0,82 en PvP), immunité 24 ticks après une saisie.
- **Intérêt RP** : la charge = réponse claire à la « tortue » en garde ★★ ; la saisie est utile en RP (immobiliser, capturer) mais demande des animations à deux ★★.
- **Reborn** : ni l'un ni l'autre. Charge = **facile/moyen** ; saisie = **lourd** (anims couplées).

## 10. Mode combat et Jutsu Row (activation, changement de jutsu, touche R)

**Joueur** : G active le **mode combat** : le taijutsu et la rangée de jutsu s'activent ensemble, la hotbar vanilla disparaît (`mixin/MixinGuiCombatHotbar`). 1-6 / molette choisissent la discipline (rouleau), flèches haut/bas changent la technique dans le rouleau, **maintenir R** ouvre une roue radiale (`client/JutsuWheelState`, rayon 30-60 px).

**Fonctionnement** : `client/KeyBindings` (R = `SPECIAL_JUTSU_1`, code 82 ; R maintenu avec un rouleau en main → `JutsuWheelState.open`, sinon R lance le pouvoir oculaire n° 1 via `procedure/ProcedureSpecialJutsu1OnKeyPressed`) ; F, X = pouvoirs 2 et 3 ; H = lock-on ; B = arbre de compétences taijutsu ; N = carte ninja. Sélection côté serveur : `JutsuBarSelectMessage`, `JutsuScrollCycleMessage`, `JutsuWheelMessage`. `mixin/MixinMinecraftJutsuInput` lit les fronts de touche avant les raccourcis vanilla.

**Intérêt RP** : le **mode combat explicite** est précieux en RP (on « dégaine » ; hors mode, clic = interaction). ★★
**Reborn** : sélecteur de techniques par **échange de hotbar** (`ShinobiAbilities/…/JutsuHotbarManager` : slot 0 = icône, 1-5 = techniques liées). R est déjà pris par le mode interaction (`MOD_HUD.md`). Roue radiale côté mod = **moyen**.

## 11. Shinobi Preparation et MAX JUTSU (loadout)

**Joueur** : dans la carte ninja, onglet « Preparation » : on place des disciplines sur des positions numérotées, puis dans chaque discipline on prépare jusqu'à **4 techniques** apprises (« MAX JUTSU • 2/4 PREPARED »). On peut changer de preset. Le tutoriel insiste : moins de techniques préparées = build plus rapide à faire tourner.

**Fonctionnement** (`item/JutsuLoadoutData`, `item/JutsuScrollInventory`, `client/gui/JutsuPackScreen`)
- `PRESET_COUNT = 3`, `MAX_PREPARED_JUTSUS = 4`, positions `< 6`.
- Positions débloquées par le niveau ninja (`JutsuScrollInventory.quickCapacity`) : 2 de base, **3 au niv. 10, 4 au 25, 5 au 45, 6 au 70**. Stockage de l'arsenal : 90 rouleaux.
- Une technique ne peut être préparée que si elle est connue (`isJutsuKnownForLoadout`) ; tout passe par le serveur (`JutsuLoadoutActionMessage`, `JutsuPackSyncMessage`).

**Intérêt RP** : **fort**. Un shinobi « part en mission avec ses techniques », ce qui limite le « couteau suisse » en PvP, rend les rencontres lisibles (le staff sait ce qu'un joueur peut sortir) et colle au discours de préparation de mission. Les positions débloquées par le grade sont une récompense de progression. ★★★
**Reborn** : 5 techniques liées dans la hotbar de techniques, sans notion de preset ni de plafond lié au grade.
**Faisabilité** : **facile à moyen** (données par personnage dans ShinobiAbilities, écran dans le mod). S'articule bien avec l'apprentissage par parchemins et les slots S existants.

## 12. Signes des mains

**En combat** : maintenir le clic : les signes défilent (HUD `client/JutsuChargeHudClient`, 7 signes visibles), puis la technique est prête et part au relâchement. Relâcher trop tôt annule ou affaiblit.
- `item/JutsuHandSigns.getHandSignPlan` : nombre de signes défini par technique, sinon par rang (**C 5, B 6, A 7, S 8**) ; intervalle **2 à 12 ticks** par signe, modulé par `getHandSignTimeModifier` (maîtrise). Mode QUICK (intervalle dédié) et mode SINGLE (1 signe).

**À l'apprentissage** : mini-jeu de mémorisation (`system/HandSealRegistry`, `system/HandSealTrainingManager`, `gui/overlay/HandSealScreen`) parmi les 12 signes : temps de mémorisation `max(par rang, 6 + 2 × signes)` s (D 9, C 12, B 14, A 16, S 18), puis saisie avec des **vies** (A/S 3, B 4, C/D 5) ; échec = technique bloquée (« only obtain it from loot ») ou parchemin détruit. Sans affinité de nature : signes en plus et chakra en moins.

**Intérêt RP** : le temps de signes qui raccourcit avec la maîtrise est très RP (le génin est lent, le jōnin rapide) et donne une fenêtre d'interruption à l'adversaire. ★★★
**Reborn** : **déjà l'équivalent** : phase d'incantation avec mudras (`ShinobiAbilities/…/IncantationManager` : 2 / 4 / 6 étapes selon le rang, 5 ticks par étape, barre de boss) et mini-jeu d'apprentissage (`LearningMinigame`, séances de parchemin). À reprendre : **cadence modulée par la maîtrise** et **interruption** si l'on est frappé pendant les signes. **Facile**.

## 13. Clash de jutsu (struggle / jutsu_clash) et cinématiques

**Joueur** : deux techniques compatibles qui se rencontrent (Rasengan contre Chidori au contact ; boule de feu contre boule de feu ou Grande Annihilation contre balle d'eau à distance) déclenchent une **cinématique** : 4 manches de QTE directionnels puis une phase de **martelage** ; le perdant est projeté.

**Fonctionnement** (`jutsu/clash/JutsuClashRules`, `JutsuClashContext`, `JutsuClashService`, `struggle/StruggleManager`)
- Admission stricte : distance 0,65-2,65 blocs, écart de hauteur ≤ 0,18, se faire face (produit scalaire ≥ 0,82), PvP autorisé, au sol, rien en main… (`JutsuClashService.Admission`). À distance : 4-48 blocs. **3 matchups seulement** (`JutsuClashMatchup`).
- Préparation 18 ticks ; manches de 28, 26, 24, 22 ticks + 4 de récupération ; phase de martelage **100 ticks**, **34 impulsions max** espacées d'au moins **3 ticks** (anti-macro, `PulseGate`) ; durée max 280 ticks.
- Notes par timing (`StruggleGrade`) : PERFECT 1,0 / GREAT 0,78 / GOOD 0,55 / LATE 0,22 / MISS 0.
- Score = (QTE 60 % + impulsions 40 %) × (1 + charge × 0,2 + stats × 0,15 + état × 0,05). Égalité départagée par la charge, puis les stats, puis une graine.
- Le vainqueur inflige **65 %** des dégâts de sa technique ; le perdant est projeté selon la marge. Les deux joueurs sont **invulnérables aux tiers** pendant le clash (`JutsuClashService.damage`).
- Le même moteur `struggle` sert à l'extraction d'œil, à l'extraction de bijū et au concours de sceau (`StruggleType`), avec choix de tactique (`StruggleTacticMessage`).

**Intérêt RP** : moment fort et « anime », mais c'est l'adresse du joueur (QTE) qui tranche, pas le RP ; à réserver aux duels arbitrés. Le **moteur générique de « lutte »** (QTE + stats + état) est plus intéressant pour Reborn : il pourrait trancher des actions RP contestées (immobilisation, extraction, scellement, interrogatoire) à la place d'un jet de dés. ★★
**Reborn** : rien d'équivalent (le QTE Volonté du KO est le plus proche, `KO_ET_MORT_RP.md`).
**Faisabilité** : moteur de lutte générique (serveur + écran QTE dans le mod) = **moyen** ; clash avec cinématique de caméra = **lourd**.

## 14. HUD de combat minimaliste, personnalisable, et HUD de cible

**Joueur** : HUD discret centré : barre d'endurance 74×4 px, jauge de garde, recharges, **barre de cadence** (startup, actif, récupération, fenêtre d'enchaînement qui clignote, glyphes de l'entrée en tampon), guide de route de combo en haut, rangée de jutsu 24×28 px en bas. Éditeur de HUD : position, activation, échelle **50-200 %** par élément.

**Fonctionnement** : `gui/overlay/OverlayNYCombatStatus` (`renderCadence`, `renderGuardMeter`, `renderStamina`, `renderCooldowns`), `combat/client/CombatComboHud`, `gui/overlay/OverlayCombatBar`, `client/NinjaHudLayout` + `client/gui/NinjaHudLayoutScreen`, `client/MinimalHudSettings` + `client/HudElement` (CHAKRA, STAMINA, TEXT, MISSIONS, INSPECT, SUSANOO, HINTS).
- **HUD de cible** : réticule de lock-on + barre de vie ; panneau **Inspect** (`client/inspect/OverlayTargetInspect`, `inspect/*Provider`) **construit côté serveur** : distance pour tous, PV pour les créatures, **chakra d'un ninja**, et lignes supplémentaires seulement avec un dōjutsu (nom et type de la cible ; figure des tenketsu au Byakugan, `client/inspect/TenketsuFigure`, `ByakuganInspectProvider`, `SharinganInspectProvider`).

**Intérêt RP**
- La barre de cadence = formidable outil d'apprentissage du timing (parade, enchaînement). ★★
- Le **HUD de cible filtré par la perception** est la meilleure idée RP du lot : on ne voit pas les PV ni le chakra d'un joueur par défaut, seulement ce que son personnage peut percevoir (Byakugan, Sharingan, sensoriel). Le serveur garde la main sur l'information. ★★★
**Reborn** : HUD combat (endurance en ensō, chiffres de dégâts, combo, icônes de recharge), éditeur de HUD (touche H), viseur. **Pas de HUD de cible, pas de cadence.** Le Byakugan (ShinobiSense) existe et pourrait alimenter un panneau d'inspection.
**Faisabilité** : HUD de cible filtré = **moyen** (fournisseurs côté plugin, nouveau canal S→C, panneau dans le mod) ; cadence = **facile** une fois les actions découpées en phases (§8).

## 15. Hyūga : rework du taijutsu, tenketsu, 64 Paumes

**Joueur** : le style Hyūga frappe des **points de chakra** précis ; fermer des points ralentit la régénération du chakra de l'adversaire et renchérit ses jutsu. Les 64 Paumes sont une technique de zone cinématique ; Kaiten une rotation défensive.

**Fonctionnement**
- `combat/tenketsu/TenketsuPoint` : **7 points** (tête, poitrine, abdomen, bras, jambes). `TenketsuSystem.resolve` : il faut viser le point (rayon 0,22 bloc mis à l'échelle de la taille), portée de visée 6 blocs ; scellement 160 ticks par défaut, 8 ticks entre deux scellements.
- `TenketsuRules` : chaque point fermé réduit la régénération de chakra de **1/7** et augmente le coût des jutsu de **12 %** ; scellement complet 100 ticks, blocage physique 20 ticks, **immunité de 220 ticks** ensuite ; les 64 Paumes ferment `paumes × 7 / 64` régions.
- `combat/hyuga/HyugaAbilityService` : 64 Paumes = préparation 18 ticks, rayon 6 / hauteur 3, **8 cibles max**, **100 chakra**, recharge **1200 ticks (60 s)**, **18 dégâts** répartis entre les cibles, scène de 64 frappes ; Kaiten 40 chakra, 240 ticks.
- Routes Hyūga dans `HyugaComboCatalog` (`RETURN`, `MERIDIAN`, `TRIAD`).

**Intérêt RP** : les tenketsu sont **très RP** (effet durable, non létal, explicable, visible au Byakugan, soignable par un médic). Les 64 Paumes en zone multi-cibles le sont moins. ★★★ (tenketsu) / ★ (64 Paumes en AoE)
**Reborn** : Byakugan « comme dans l'anime » ✅ ; sorts claniques Hyūga ⏳ (Sprints 2-3).
**Faisabilité** : tenketsu comme **statut serveur** (malus de régénération / coût de chakra, durée, soin médical) = **facile à moyen** ; visée précise d'un point du corps = moyen ; 64 Paumes cinématique = lourd.

## 16. Progression du taijutsu (contexte)

`combat/progression/NYCombatProgression`, `TaijutsuSkill`, `combat/client/TaijutsuSkillTreeScreen` (touche B) : XP de combat, niveau de combat (mise à l'échelle jusqu'à 160), points de compétence, styles débloqués ; l'entraînement sur bloc est **plafonné** (72 XP par fenêtre de 1200 ticks, rendement dégressif si répétition, plancher 42 %). Les compétences débloquent routes, combat aérien, `IRON_BODY` (garde).
**Reborn** : XP invisible + Mérite + parchemins ; l'arbre de compétences de combat ferait doublon. À retenir seulement : **le plafond anti-farm sur l'entraînement**. ★

---

## Tableau de synthèse

| Idée | Ce que fait Nydo | Reborn a déjà ? | Valeur RP | Effort |
|---|---|---|---|---|
| Combos L/R confirmés au contact | Route = suffixe L/R des coups qui ont touché, fenêtre 28 ticks, file de 2 appuis (`CombatComboState`) | Non : combo M1 linéaire de 4 coups, fenêtre 1200 ms | ★★★ | Moyen |
| R défensif tant que 2 L n'ont pas touché | `rightIsOffense()` (`leftRun >= 2`) | Non (parade sur C) | ★★★ | Moyen |
| Finishers de clan par route | `CombatComboRoutes` + coûts endurance/chakra, recharge 6 s, repli sur le coup normal | Finition « pyramid » unique | ★★ | Moyen (logique) / lourd (VFX) |
| Guide de route au HUD | `CombatComboHud` : route, prochaine touche, ce qui manque | Compteur de combo seulement | ★★ | Facile |
| Parade timée avec réarmement | 5 ticks actifs, 12 ticks de réarmement, valable seulement en phase active adverse, 40 ticks d'étourdissement | Parade 320 ms, sans réarmement | ★★★ | Facile |
| Garde frontale uniquement | `frontDot` selon l'arc de l'arme et du style | Non | ★★ | Facile |
| Pression de garde sur l'attaquant | Jusqu'à 5 niveaux : recul + perte d'endurance pour qui martèle | Non | ★★★ | Facile |
| Garde cassée / jauge | Jauge 100, rupture 14-60 ticks selon stabilité | Oui (drain 25, rupture 20 ticks) | — | — |
| Anti-spam du même coup | `commitRepeatPressure` (5 niveaux / 22 ticks) | Non | ★★ | Facile |
| Charges de dash | 2 charges, 1 par 45 ticks au sol | Recharges fixes (esquive 2,5 s, dash 7 s) | ★ | Facile |
| Esquive parfaite → contre | Fenêtre 12 ticks si esquive dans la hitbox active | Non | ★★ | Moyen |
| Substitution | Absente du combat Nydo | Technique kawarimi existante | — | — |
| Endurance sans régén en garde | Délai 10-19 ticks + pas de régén en garde/attaque | Endurance oui, règle non | ★★ | Facile |
| Lock-on | 28/30 blocs, rotation bornée, réticule + barre de vie | Non | ★★ | Moyen |
| Combat aérien + air tech | Juggle 9 points, dégressif, échappatoire 18 ticks | Non | ★ | Lourd |
| Actions en phases + hitbox | startup/actif/récup, groupes de hitbox, forme arc/cône/ligne | Non (dégâts au clic) | ★★★ | Moyen à lourd |
| Multiplicateur PvP/PvE par coup | `pvpDamageMultiplier` / `pveDamageMultiplier` | Non (dégâts pas à l'échelle des PV) | ★★★ | Facile |
| Coup chargé brise-garde | > 20 ticks, brise la garde à 80 % de charge | Non | ★★ | Facile/moyen |
| Saisie avec échappement | Fenêtre de 3 ticks, immunité 24 ticks | Non | ★★ | Lourd |
| Mode combat explicite | Touche G, hotbar remplacée par la rangée de jutsu | Non (combat toujours actif) | ★★ | Facile/moyen |
| Roue radiale de jutsu | R maintenu, max 4 techniques | Hotbar de techniques (5) | ★ | Moyen |
| Loadout limité (Preparation / MAX JUTSU) | 3 presets, 2→6 disciplines selon le niveau, 4 techniques max chacune | Partiel (5 techniques liées) | ★★★ | Facile/moyen |
| Signes accélérés par la maîtrise | 4-8 signes, 2-12 ticks chacun, modulés par la maîtrise | Incantation à mudras, cadence fixe 5 ticks | ★★★ | Facile |
| Mini-jeu de mémorisation des signes avec vies | Échec = technique bloquée ou parchemin détruit | Mini-jeu d'apprentissage existant | ★ | — |
| Moteur générique de lutte (QTE + stats + état) | `StruggleManager` : extraction, scellement, clash | Seul le QTE Volonté | ★★ | Moyen |
| Clash de jutsu cinématique | 3 matchups, 4 manches + martelage, 65 % des dégâts au vainqueur | Non | ★★ | Lourd |
| Cadence au HUD | Barre startup/actif/récup + fenêtre d'enchaînement | Non | ★★ | Facile (si §8) |
| HUD de cible filtré par la perception | Panneau construit par le serveur ; infos selon le dōjutsu | Non | ★★★ | Moyen |
| Éditeur de HUD | Position + échelle 50-200 % par élément | Oui (touche H) | — | — |
| Tenketsu (statut chakra) | 7 points, régén −1/7 et coût +12 % par point, immunité 220 ticks | Non (Byakugan visuel oui) | ★★★ | Facile/moyen |
| 64 Paumes en zone | 8 cibles, 100 chakra, 60 s, 18 dégâts répartis, cinématique | Non | ★ | Lourd |
| Rate-limit et anti-rejeu des entrées | Séquence, péremption 700 ms, plafonds par type | Non | ★★★ | Facile |
| Compensation de latence | Rembobinage ≤ 8 ticks | Non | ★★ | Moyen |
| Entraînement anti-farm | XP de bloc plafonnée et dégressive | XP invisible (à vérifier) | ★ | Facile |
