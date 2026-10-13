# NydoMod 5.5 — moteur client et assets (étude pour Reborn)

> Étude **en lecture seule** du code décompilé et des ressources de NydoMod 5.5 (Forge 1.20.1, « All Rights
> Reserved »). On reprend des **concepts et des structures** (schémas JSON, architecture), jamais du code ni
> des assets. Les modèles Susanoo sont crédités à un prestataire tiers (HytMO) et une partie des animations
> de combat est sous licence (`combat/licensed_boundless/`) : rien de tout ça n'est réutilisable, même en
> partie. Date : 2026-10-12.

## Résumé (10 lignes)

1. **Cinématiques** : 36 fichiers JSON décrits par un schéma strict (v1, champs inconnus refusés). Chaque fichier contient des pistes à images clés : caméra, acteurs mis en scène, animations jouées, vignettes « manga », décor procédural, marqueurs. Le serveur n'envoie qu'un paquet `{id, casterId, targetId, startTick, seed}`.
2. Il y a trois publics possibles : privé (lanceur + cible), observateurs dans un rayon (fichiers `*_observer` qui laissent HUD et contrôles), et « world scenes » où les tiers voient les acteurs mis en scène sans que leur caméra soit prise.
3. Les **marqueurs** déclenchent sons, VFX et effets d'écran : ce sont des événements client nommés (`tsukuyomi/torture_impact`). La synchro se fait au tick, sans réseau.
4. **VFX** : 426 effets JSON (jusqu'à 64 émetteurs chacun) et 86 presets héritables (`extends`/`preset`). On y trouve 36 géométries procédurales (éclair, flamme, hélice d'eau, onde de choc…) et un budget strict : particules, sommets et passes de matériaux selon le réglage « Particules », LOD par distance et par priorité.
5. **Animations joueur** : 298 clips Bedrock 1.8.0 joués par playerAnimator (KosmX) avec bendylib (coudes et genoux pliés). Une seule couche « modifiers » gère fondu, vitesse, miroir et correctifs procéduraux par technique. La synchro réseau passe par `{entityId, anim, durée, vitesse, miroir, tick de départ}`.
6. **Garde-robe** : 73 tenues décrites en cubes JSON par emplacement (tête, torse, jambes, pieds). Chaque tenue a une variante bras fins (slim), une variante `half_off` (robe ouverte) et des plis synchronisés sur les coudes et genoux du joueur.
7. **Post-effets** : étalonnage de scène (négatif, teinte, désaturation, vignette, pulsation), « lentille » qui respire, vide noir des Hyūga par shader de profondeur, Shinra Tensei en post-chain, cécité oculaire qui redessine le monde en contours. Il y a aussi un HUD minimal déplaçable et un menu principal en parallaxe.
8. **Modèles** : 55 géométries GeckoLib (Susanoo × 5 utilisateurs × 5 phases, invocations, argile de Deidara, Gedo Mazo), 131 modèles d'objets, et des meshes « cuits » depuis Blockbench (Kurama, Kirin, Quatre-Queues) pour contourner la limite aux cubes.
9. **Ce qui apporte le plus à Reborn** : un **directeur de cinématiques piloté par JSON**. Il sert directement les exécutions (`executions.yml` existe déjà, sans animation), les éveils de dōjutsu, les morts et les rapports de mission. Reborn a déjà PlayerAnimationLib/Emotecraft sur 26.2, la scène de mort en bandes de cinéma et le compositeur de skins.
10. **À ne pas viser tout de suite** : le moteur VFX complet (énorme, et le rendu 26.2 est très différent), les Susanoo, et le menu principal (la DA Reborn a déjà ses règles).

---

## A. Système de cinématiques

### A.1 Ce que ça apporte au joueur
Le joueur voit des scènes réalisées. La caméra quitte le joueur et suit un plan à images clés, des bandes de cinéma apparaissent, le HUD est caché et les contrôles bloqués. Les deux protagonistes sont repositionnés face à face et jouent des animations synchronisées. Des vignettes « manga » (gros plan du visage, déchirures de papier) s'incrustent à l'écran, et les sons tombent aux bons moments. Exemples : extraction d'œil (900 ticks, attaquant + victime), Tsukuyomi (200 ticks, monde remplacé + étalonnage négatif), éveil du Mangekyō éternel, choc de jutsu, invocation de Jiraiya, Kirin vu de loin par les témoins.

### A.2 Fichiers
`cinematics/` : `genjutsu/` (sharingan_left, shisui, tsukuyomi), `hyuga/sixty_four_palms`, `jutsu/` (kirin_charge, kirin_launch et leurs `_observer`), `story/` (jiraiya_reverse_summon, jiraiya_snowy_arrival), `struggle/` (24 variantes d'extraction, d'implant, de libération et d'évasion oculaire gauche/droite/les deux/cadavre/soi, jutsu_clash, table d'opération), `nydo.json` (démo).

### A.3 Schéma JSON complet (v1) — `NydoCinematicDefinition`
Toute clé inconnue fait échouer le chargement, et chaque nombre est borné. Plafonds : durée ≤ 6000 ticks, 128 images clés caméra, 64 animations, 16 pistes d'acteurs, 8 vignettes, 128 marqueurs.

**Racine**

| Champ | Type, défaut, bornes | Rôle |
|---|---|---|
| `schema` | int = 1 | Version du format |
| `duration` | int, 80, 1..6000 | Durée en ticks |
| `priority` | int, 100, 0..1000 | Une cinématique de priorité supérieure remplace la courante. Une priorité inférieure est ignorée |
| `hide_hud` | bool, true | Masque le HUD |
| `lock_input` | bool, true | Bloque les déplacements et les actions |
| `skippable` | bool, false | Autorise le passage par une touche |
| `snapshot_anchors` | bool, true | Fige la position et le lacet des acteurs au départ. La scène ne « glisse » plus si un acteur bouge |
| `letterbox` | float, 0, 0..0.35 | Hauteur des bandes noires (fraction de l'écran) |
| `fade_in` / `fade_out` | int, 6 / 8 | Fondus d'enveloppe (smoothstep). Leur somme doit rester ≤ `duration` |
| `camera` | objet | Piste caméra |
| `scene` | objet | Décor procédural plein écran (renderer nommé) |
| `animations` | tableau | Animations jouées sur les acteurs |
| `actors` | tableau | Mise en scène des acteurs (position, rotation, échelle) |
| `panels` | tableau | Vignettes portrait en surimpression |
| `markers` | tableau | Événements nommés |

Au moins une piste doit exister. Les **rôles** requis sont déduits automatiquement de toutes les pistes : si un acteur manque à l'exécution, la cinématique s'arrête avec la raison `MISSING_ACTOR`.

**Rôles (`ActorRole`)** : `self` (le joueur local), `caster`, `target`.
**Espaces (`CoordinateSpace`)** : `world` (absolu), `actor` (décalage tourné par le lacet de l'acteur, c'est le plus utilisé), `scene` (repère du décor procédural).
**Easing** : `linear`, `in_quad`, `out_quad`, `in_out_cubic`, `out_back`. **Courbe caméra** : `linear`, `catmull_rom`.

**`camera`** : `{collision: bool=true, keyframes: [1..128]}`. Les ticks doivent être strictement croissants.

| Champ d'image clé | Défaut, bornes | Rôle |
|---|---|---|
| `tick` | 0..duration | Instant |
| `position_actor` | `caster` | Acteur d'ancrage de la position |
| `position_offset` | [0, 1.7, 3.5], ±256 | Décalage dans `space` |
| `space` | `actor` | Repère |
| `look_actor` / `look_offset` | `caster` / [0, 1.45, 0] | Point visé |
| `fov` | 55, 10..120 | Champ de vision |
| `roll` | 0, −30..30 | Roulis |
| `ease` / `curve` | `in_out_cubic` / `catmull_rom` | Interpolation dans le temps et dans l'espace |

Un **saut de plan** se fait avec deux images clés à un tick d'écart en `linear` (cf. `kirin_charge_observer` : 29 → 30). La collision ramène la caméra contre les blocs, sur le rayon qui part du point visé.

**`animations[]`** : `{tick, actor="caster", animation (chemin playerAnimator ≤160 car.), duration (défaut : jusqu'à la fin), speed 0.05..8, mirror}`. Le directeur appelle le même point d'entrée que le combat (`applySyncedCombatAnimation`), avec le tick de départ dans le clip pour rattraper un retard. Un même acteur enchaîne souvent un clip d'engagement et un clip long (`eye_extract_attacker_engage` 14 ticks à ×1,57, puis `eye_extract_attacker` 886 ticks).

**`actors[]`** (mise en scène) : `{name (unique), actor="target", hide_original=true, start, end, keyframes[1..128]}`.
Image clé : `{tick, anchor="caster", offset[3], space="actor", yaw ±720, pitch ±180, roll ±180, scale 0.05..16, ease}`.
→ Pendant la scène, une **copie de rendu** de l'acteur est dessinée à la position voulue (par exemple l'attaquant à 0,735 bloc devant la victime, retourné de 180°). L'entité réelle est masquée.

**`panels[]`** (vignettes) : `{name, actor="caster", style, framing, start, end, seed, entity_offset_x/y ±0.75, keyframes[1..64]}`.
- `style` : `manga`, `clean`, `genjutsu`, `sharingan`, `sharingan_reveal`. `framing` : `face`, `bust`, `full_body`.
- Image clé : `{tick, x 0.6, y 0.05, width 0.38, height 0.3, alpha, scale, yaw 180, pitch, ease}` en fractions de l'écran. On anime donc l'entrée de la vignette (x de 1.04 à 0.035 en `out_back`), puis le zoom et la rotation de l'entité rendue.
- Rendu : un portrait **en direct** de l'entité dans un cadre en papier déchiré (bords pseudo-aléatoires tirés du `seed`), avec un fond d'anneaux de Sharingan pour le style « reveal ».

**`scene`** (décor procédural) : `{renderer (id, ex. nydomod:tsukuyomi, nydomod:hyuga_sixty_four), start, end, fade_in, fade_out, seed, intensity 0..4, altitude 32..512, grade{}, lens{}, roles[]}`.
- `grade` : `{mode: normal|negative, tint[r,g,b] 0..1, tint_strength, desaturation, vignette, pulse 0..0.25}`.
- `lens` : `{compression 0.55..1.45, breathing 0..12, period 8..400}`. Le FOV « respire » de façon cyclique.
- Le renderer remplace le monde par un décor dessiné en code : ciel rouge et croix du Tsukuyomi, lune et mer noire de Shisui, vide noir et trigramme des 64 paumes.

**`markers[]`** : `{tick, name ([a-z0-9_./-]{1,96}), actor="self"}`. Le directeur publie un événement `MarkerReached(cinematic, marker, acteur résolu, seed)` sur le bus client. D'autres événements existent : `Started`, et `Stopped(reason)` avec les raisons COMPLETED, MANUAL, SKIPPED, REPLACED, LEVEL_CHANGE, MISSING_ACTOR, INVALID_TIME.

### A.4 Synchro sons / VFX / animations
- **Sons** : un abonné client fait la correspondance `(cinématique, marqueur) → son(s), pitch, volume`. Exemple : `tsukuyomi/torture_impact` toutes les 18 ticks, avec un pitch qui varie selon le tick. C'est du code aujourd'hui, mais ça se convertit facilement en table JSON.
- **VFX et effets d'écran** : mêmes marqueurs (ex. `kirin/observer_warning`, `jiraiya/snow`).
- **Animations** : les cues sont rejouées à chaque tick avec le temps écoulé depuis `startTick`. Un client qui reçoit le paquet en retard démarre donc au bon endroit du clip.
- **Déterminisme** : `seed` partagé (déchirures, variantes) + `startTick` = temps monde serveur.

### A.5 Déclenchement serveur (`NydoCinematic`)
- `play(viewer, id, caster, target)` : un seul spectateur.
- `playForCasterAndTarget(caster, target, id)` : scène privée aux deux protagonistes.
- `playForNearbyObservers(caster, target, id, rayon)` : tous les joueurs vivants dans le rayon (48 blocs pour le Kirin), sauf le lanceur. On leur envoie un **fichier différent**, `*_observer`, avec `hide_hud:false`, `lock_input:false` et une bande fine (0.045). Les témoins gardent le contrôle mais voient un plan large de 80 ticks.
- `stop` / `stopIfMatching`.
- Paquet : `{id, casterEntityId, targetEntityId, startTick, seed}`. Les définitions sont des ressources client rechargeables (`SimpleJsonResourceReloadListener`).
- **World scenes** (`NydoWorldScenes`) : pour les scènes longues (extraction 900 ticks, opération 2400), le serveur garde l'état et l'envoie aux joueurs qui **commencent à suivre** l'acteur (`StartTracking`). Un passant voit ainsi les deux personnages en position et animés, sans cinématique imposée.
- **Area cinematics** (64 paumes) : un `AreaCinematicPlan` répartit jusqu'à 128 « coups » sur 1 à 64 cibles, en mode `SIMULTANEOUS` ou `DISTRIBUTED`, entre `firstTick` et `endTick` (≤ 600 ticks). On obtient ainsi une chorégraphie de masse déterministe.
- Outils de dev : `/nydocinematic play|skip|stop|list`, `/test cinematic …` (client).

### A.6 Intérêt RP
- **Exécutions** : le cas d'usage parfait. `executions.yml` de ShinobiCore porte déjà l'animation du tueur, celle de la victime, la durée et l'instant de la mort. Il suffit d'ajouter une **cinématique** : caméra, mise en scène face à face, marqueurs `mort`, `sang`, `son`. Les exécutions deviennent un vrai cosmétique vendable. Le plan « témoins » sert aux passants dans un rayon.
- **Scène de mort** : la vue grisée en bandes de cinéma existe déjà. On peut y ajouter un plan d'ouverture en JSON (travelling qui s'éloigne du corps, vignette du visage du personnage en style `clean`).
- **Éveils** (Sharingan, Mangekyō, Byakugan) : vignette visage `sharingan_reveal` + étalonnage + battement de cœur. Fort impact pour peu de contenu.
- **Rapports de mission et arrivées scénarisées** (cf. `jiraiya_snowy_arrival`, `self` seul) : plans d'arrivée au village, remises de grade, cérémonies.
- **Staff** : déclencher une cinématique sur un groupe de joueurs pour un event (avec `skippable`).

### A.7 Faisabilité Reborn (26.2 Fabric)
- **Moyenne, et rentable.** Briques déjà en place : `ko/DeathScreen` (bandes, vue grisée, HUD masqué via `HudAccessor`), `camera/RebornCamera` (presets 3ᵉ personne), PlayerAnimationLib + Emotecraft (zigy, 26.2) pour les cues, `AnimSyncPayload`, et le rendu du personnage en scène pour les vignettes (`character/scene`).
- À écrire :
  1. `CinematicDefinition` (on peut garder un schéma proche, champs en français ou non) ;
  2. directeur client : override de la caméra par mixin sur `Camera`. ⚠️ Les mixins 26.2 cassent au lancement, il faut tester en `runClient` ;
  3. pistes d'acteurs. ⚠️ Règle Reborn : **pas d'entité factice hors monde**. Il faut donc décaler et tourner le **rendu de l'entité réelle** (render state), pas créer de `RemotePlayer` ;
  4. payload `reborn:cinematic` côté ShinobiCore (Paper) avec audience privée, rayon ou les deux ;
  5. table `marqueur → son / filtre` en JSON.
- Commencer **sans** `scene` procédurale ni `panels`. Ces deux pistes viennent dans un second temps.

---

## B. Moteur VFX

### B.1 Ce que ça apporte
Les jutsu ont un rendu propre au mod : éclairs ramifiés qui changent de topologie toutes les N frames, nuages de feu volumiques, hélices et dragons d'eau, ondes de choc, aura de chakra, traînées sur la lame. Le tout est **purement client**, piloté par le serveur par un simple déclencheur.

### B.2 Organisation
`vfx/` : 426 JSON répartis par élément (`nydoraiton` 29, `nydowater` 47, `nydosusanoovfx` 75, `nydoenton` 27, `nydobakuton` 29, `nydofutton` 25, `nydobiju` 20, `hyuga` 8, `jutsu_clash`, `nydocinematicvfx`…) + `_presets/` 86 (core, chakra/rasengan, fire, ninja_smoke, raiton, trails, water, waves, wind).

### B.3 Schéma d'un effet (`NydoVfxDefinition`, schema 1)

| Champ racine | Défaut, bornes | Rôle |
|---|---|---|
| `duration` | 20, 1..1200 | Vie de l'effet (ticks) |
| `priority` | 1, 0..3 | Admission, éviction, plancher de LOD |
| `max_distance` | 96, 4..256 | Au-delà, l'effet n'est pas instancié |
| `bounds` | 8, 0.1..128 | Boîte englobante (culling) |
| `follow_source` | false | L'effet suit l'entité source |
| `stop_when_source_missing` | false | S'arrête si la source disparaît |
| `source_anchor` | `origin` | `origin`, `center`, `eyes`, `main_hand`, `owner_center` |
| `preserve_trigger_offset` | true | Garde le décalage initial par rapport à la source |
| `follow_source_direction` / `source_direction` | false / `motion` | `motion`, `look`, `blade`, `projectile_disc` |
| `hide_in_first_person` | false | Masqué en 1ʳᵉ personne |
| `source_scale_mode` (+ `_base`, `_power`, `_floor`, `_limit`) | `fixed` | `entity_width`, `entity_bounds` : l'effet s'adapte à la taille de la source (bijū, Susanoo) |
| `required_source_effect` | — | Ne joue que si un autre effet tourne sur la source |
| `replace_on_trigger` | false | Un nouveau déclenchement remplace l'instance |
| `snapshot_interpolation_ticks` | 1..4 | Lissage du suivi |
| `emitters` | 1..64, noms uniques | Liste d'émetteurs |

**Émetteur**

| Champ | Défaut, bornes | Rôle |
|---|---|---|
| `preset` | id sous `_presets/` | Fusion profonde preset ← surcharges |
| `name`, `start`, `duration` | — | Fenêtre d'émission dans l'effet |
| `burst` / `rate` / `max_particles` | 0..4096 / 0..1024 par tick / 2048, 1..8192 | Débit |
| `geometry` | `billboard` | Voir la liste ci-dessous |
| `shape` | `{type, offset, radius, inner_radius, length, angle, scale, direction}` | Formes : `point`, `sphere`, `ring`, `cone`, `box`, `box_perimeter`, `line`. Directions : `forward`, `backward`, `radial`, `random`, `shape`, `target`, `body_tangent`, `surface_tangent` |
| `speed`, `lifetime`, `rotation`, `spin` | intervalles `[min, max]` | Tirage par particule |
| `gravity` ±10, `drag` 0.98, `turbulence` 0..4, `vortex`, `vortex_pull` | — | Dynamique |
| `size`, `alpha` | courbes `[[t, v], …]` | Évolution sur la vie de la particule |
| `size_variation`, `color{start, end}` (#AARRGGBB), `color_variation` | — | Variations |
| `near_fade` | 0..16 | Fondu près de la caméra |
| `material` | `{texture, blend: alpha/additive, shader: standard/lit/procedural, depth_write, fullbright}` | Rendu |
| `atlas` | `{columns, rows, frames}` | Flipbook |
| `collision` | `{mode: none/world, response: clip/impact/bounce/surface_flow, padding, deformation, probe_radius, restitution, spread, lift, fluids: ignore/surface, impact_interval}` | Interaction avec les blocs (le feu qui glisse au sol, l'eau qui s'étale) |
| `follow_origin`, `align_to_spawn_direction` | bool | Repère |
| `geometry_settings` | selon la géométrie | `segments`, `thickness`, `length`, `jitter`, `branches`, `branch_length`, `core`, `leader`, `topology_ticks` (éclair) ; `arc_degrees`, `edge`, `roll`, `lay_flat` (anneau, onde) ; `turns`, `flare`, `wave`, `crest` (hélice) ; `lobes`, `rim`, `curl`, `swirl`, `taper`, `flow`, `bloom`, `glow`, `mouth`, `horns` (eau, dragon) ; `trail_points` ≤16, `trail_taper`, `trail_min_distance`, `trail_break_distance` (traînée) ; `front_scale`, `sag` (feu) |

**Géométries (36)** : billboard, directional_billboard, cross, cube, ring, impact_dome, streak, shock_wave, ninja_smoke, chakra_sphere, chakra_orbits, chakra_aura, spiral_vortex, futon_shuriken, wind_gust, four_tail_claw, tapered_streak, lightning_bolt, trail, flame, fire_wisp, fire_volume, fire_cloud, black_flame, enton_mantle/crescent/focus/ground, water_helix, water_lance, water_ribbon, water_dragon_head, biju_beam, lava_surface, basalt_pillar, obsidian_shard.

**Presets** : fichier `{schema, extends?, emitter{…}}`, héritage jusqu'à 16 niveaux, cycles détectés. Chaîne type : `core/fullbright_alpha` (texture blanche, alpha, standard, fullbright) → `core/fullbright_additive` (blend additif) → `fire/jet_core` (géométrie fire_cloud, courbes de taille et d'alpha). Les effets n'écrivent ensuite que leurs différences, avec un nom d'émetteur parlant (« violet_predator_crown »).

### B.4 Déclenchement et réseau
`NydoVfx.play(level, effet, origine, direction)` envoie `NydoVfxTriggerMessage {effect, origin, direction, sourceEntityId, targetEntityId, seed, scale, colorOverride}` en `NEAR` (rayon de 96 par défaut), en `TRACKING_ENTITY_AND_SELF` (effets attachés) ou à un joueur. Le client résout et instancie l'effet localement. Le `seed` partagé donne la même forme chez tous les joueurs.

### B.5 Budget de performances (`NydoVfxLimits`, `NydoVfxEngine`)
- Par effet : 4096 particules, 65 536 sommets, 12 matériaux. Global : 256 effets actifs, 128 déclenchements en attente.
- Budget global selon le réglage vanilla « Particules » : **Tout** 16 384 particules / 524 288 sommets / 64 passes · **Réduit** 8 192 / 262 144 / 32 · **Minimal** 3 072 / 98 304 / 16.
- Saturation : on remplace l'effet de priorité plus basse, ou à priorité égale le plus éloigné.
- LOD = réglage (1 / 0,68 / 0,34) × max(plancher de priorité (0,75 / 0,45 / 0,15), facteur de distance), multiplié par un facteur « hors champ » (0,55 / 0,3 / 0,15 si invisible et priorité < 3).
- Shaders propres : `nydo_vfx_procedural`, `nydo_vfx_lit`. Compatibilités shaderpack (Oculus/Iris) et Distant Horizons gérées à part.

### B.6 Intérêt RP et faisabilité
- Intérêt : les techniques Reborn (39 techniques + 18 claniques à venir) gagneraient beaucoup en lisibilité. Le format JSON + presets permet aussi au **Technique Creator** de référencer un effet par id.
- Faisabilité : **effort élevé**. Le rendu 26.2 (RenderPipelines, extraction / état retenu, pas de `RenderSystem.setShader`) impose de tout réécrire. Recommandation :
  1. reprendre **le format** (effet → émetteurs → presets) ;
  2. implémenter 5 géométries (billboard, ring/shock_wave, streak, trail, lightning_bolt) avec le budget par réglage ;
  3. déclencheur `reborn:vfx` depuis ShinobiAbilities.
- En attendant : particules vanilla et datapack côté serveur, ou modèles Model Engine pour les gros effets.

---

## C. Pipeline d'animation joueur

### C.1 Fichiers
`player_animation/` : 298 clips au format **Bedrock 1.8.0** (`animations.<nom>.{animation_length, loop, bones.{body, head, rightArm, leftArm, rightLeg, leftLeg}.{position, rotation, bend}}`, images clés `vector` + `lerp_mode`/`easing`).
Répartition : racine 32 (chakra_charge, signes, rasengan, chidori, shinra, rasenshuriken), `cinematic/nydo` 41 (clash ×14, eye_* ×21, sharingan_reveal, shisui ×3, tsukuyomi_executioner/victim), `combat/nydo/*` (core 13, basic 12, combo 10, hyuga 22, kurama 35, kiba 17, naruto 17, uchiha 13, jutsu 17, rock_lee 9, frog_mode 9, toad_sage 5, training 9 : pompes, abdos, équilibre), `combat/weapon` 16 (kunai, shuriken, fūma), `movement/nydo` 8 (sprint armé/désarmé en 1ʳᵉ et 3ᵉ personne, double saut, réception), `licensed_boundless` 13 (**sous licence tierce**).

### C.2 Lecture
- Bibliothèque **playerAnimator (KosmX) + bendylib**. Une couche `ModifierLayer` est enregistrée en priorité 1000 (`nydomod:player_animation`), plus une couche dédiée au manteau de bijū (1100).
- `setAnimation` : charge le `KeyframeAnimation` et crée un `KeyframeAnimationPlayer` à partir d'un **tick de départ** (rattrapage réseau). Il règle aussi la 1ʳᵉ personne : bras et objets visibles ou non, selon le clip (arme, lancer, Kiba).
- **Modificateurs empilés** : `SpeedModifier` (signes à la vitesse du plan de signes, `handsign_hold` à 0,35), `MirrorModifier` (main gauche), puis des modificateurs procéduraux par famille. On trouve `NYBendyAnimationModifier` (pliure automatique des coudes et genoux si le clip n'en a pas), `NYKuramaFacingModifier`, `NYJutsuClashBendyModifier`, l'étalonnage des mains et objets (`NYItemHandCalibration`), Katon, Uchiha, Rasengan…
- **Transitions** : `replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(n, INOUTSINE))`. On compte 1 tick pour sortir d'un clip de combat, 5 sinon, et des fondus propres pour Kiba et les clips oculaires.
- **Lectures spécialisées** (Hyūga, Kurama) quand la chorégraphie doit être exacte au tick.

### C.3 Synchro réseau
- `PlayerAnimationMessage {entityId, animation, durationTicks}` pour les one-shots, et une variante combat `{…, speed, mirror, initialClipTick}`. `"none"` ou une requête « stop » ciblée arrête le clip.
- Prédiction locale côté client (`confirmPredictedCombatAnimation`) : le lanceur n'attend pas le serveur.
- Les cinématiques passent par le même point d'entrée (cf. A.4).

### C.4 Intérêt et faisabilité Reborn
- Reborn utilise déjà **PlayerAnimationLib (fork zigythebird, 26.2) + Emotecraft**, héritiers directs de playerAnimator : le format et le modèle de couches sont les mêmes. L'**effort est faible** pour reprendre les concepts suivants :
  1. clip + tick de départ dans le payload (rattrapage) ;
  2. vitesse et miroir en paramètres ;
  3. pliure automatique quand le clip n'a pas de `bend` ;
  4. fondus courts en sortie de combat ;
  5. prédiction locale.
- Le catalogue Nydo sert de **liste de besoins** pour `gameplay/ANIMATIONS_BASE.md` (garde, parade, réactions gauche/droite, entraînement, signes, clips d'engagement de 14 ticks avant une scène). Les clips eux-mêmes ne se copient pas.
- **Exécutions** : chaque exécution = 2 clips (`_engage` court + clip long) par rôle, comme `eye_extract_*`.

---

## D. Garde-robe et tenues

### D.1 Format (`wardrobe/models/<item>.json`)
```
texture            "nydomod:textures/wardrobe/<x>.png" (doit être sous textures/ et en .png)
texture_size       [128, 128]
source_item        id de l'objet porté
outfit             identifiant de tenue (41 tenues pour 73 pièces)
slot               head (28) | chest (26) | legs (17) | feet (2)
half_off_texture   texture alternative (tag NBT halfOff : robe Akatsuki ouverte)
parts[]            os du joueur : body, head, right_arm, left_arm, right_leg, left_leg
  name, pivot[3], (slim_pivot), cubes[], children[] (arborescence libre)
  cube: from[3], size[3], uv[2], uv_size[3], inflate,
        slim_from / slim_size / slim_uv (variante bras fins)
```

### D.2 Rendu
- Au premier usage, deux modèles sont **cuits** par tenue (classique et slim), puis mis en cache par objet. Le modèle de bras est choisi selon le skin du porteur (joueur, PNJ, profil par défaut de l'UUID).
- Le modèle remplace celui de l'armure (`IClientItemExtensions#getHumanoidArmorModel`). Il est donc rendu **par-dessus le joueur**, avec `inflate` pour éviter le z-fighting.
- **Pliure synchronisée** : si l'os du joueur est plié par bendylib (coude, genou), le cube de vêtement active son propre `BendableCuboid` et recopie axe et angle. Les manches suivent ainsi les animations.

### D.3 Intérêt et faisabilité Reborn
- Reborn a le **compositeur de skins** (couches 2D), une échoppe de tenues et 5 tenues Uchiha. Le skin plat ne donne pas de **volume** : col haut de la robe Akatsuki, gilet tactique, cape du Hokage, bandeau, masques ANBU.
- Idée : une **couche 3D légère** par emplacement, définie en JSON (cubes + UV + variante slim + variante « ouverte »), rendue en couche joueur et pliée avec PlayerAnimationLib.
  - Effort **moyen**.
  - Valeur RP élevée : distinction des grades, ANBU masqué, Kage. On peut aussi l'utiliser sur les corps (mannequin de la scène de mort).
  - Bbforge peut produire ces cubes, mais les règles de texture Reborn s'appliquent (aplats, 32x).
- Risque : l'effet de seuil « armure vanilla ». Il faut que la DA valide sur captures.

---

## E. Shaders, post-effets, HUD, menu, sons

### E.1 Post-effets et visions
| Effet | Technique | Ce que voit le joueur |
|---|---|---|
| Étalonnage de scène `CameraColorProfile` | mode normal/négatif, teinte, désaturation, vignette, pulsation | Tsukuyomi en négatif rouge, éveils |
| Lentille `CameraLensProfile` | compression du FOV + respiration périodique | Malaise, transe |
| `NydoScreenVfx` | profils plein écran à durée, angles et FOV de caméra | Flash, secousse, zoom d'impact |
| `cinematic_backdrop` (core shader) | fond plein écran derrière les acteurs | Monde remplacé par un décor |
| `hyuga_final_void` | lit la profondeur capturée, efface tout sauf le sol et les acteurs, puis noir | Les 64 paumes dans le vide noir |
| `shinra_charge_*` / `shinra_impact` (post-chain) | passe plein écran + blit, variantes « safe/hazard/annihilation » et version shaderpack | Distorsion de Shinra Tensei |
| `biju_consciousness_blur` | flou | Monde intérieur du bijū (`InnerVisitOverlay`) |
| `NydoOcularBlindness` | aveugle après extraction : le monde redevient noir et les blocs proches sont redessinés en **contours** (scan 2048 positions par tick, ≤ 4800 blocs, bords adoucis) | Cécité jouable |
| `jutsu_charge_hud`, `jutsu_seal_hud` | shaders de HUD (191 et 95 lignes) | Jauge de charge et sceau animés |
| Caméra combat : `NydoCameraTrauma` (bruit ∝ trauma²), `Hitstop`, `ShakePunch`, `SecondOrderDynamics` | — | Impacts lourds |

**Reborn** : `effets/FiltresEcran`, la vue grisée de la mort, le Byakugan (réseau de chakra) et la météo existent déjà. Faisable en 26.2 :
- étalonnage et vignette par superposition ou post-effet ;
- caméra « trauma » et hitstop (faible effort, gros gain sur le combat) ;
- **cécité RP** pour les personnages aveugles ou aux yeux pris. C'est une idée originale et forte : effort moyen, attention aux performances.

Le vide Hyūga par profondeur est complexe en 26.2.

### E.2 HUD
- `MinimalHudStyle` : palette fixe (texte, or, vie, chakra, endurance, garde, danger), échelles de texte réduites (0,72 à 0,85), glyphes vectoriels. `NinjaHudLayoutScreen` : glisser-déposer des blocs (vitals, endurance, mission, inspection) et sauvegarde.
- Autres : boussole de mission en mesh, marqueurs de PNJ, HUD des pouvoirs oculaires sur timeline, inspection de cible (`TenketsuFigure` : points de chakra).
- **Reborn est déjà plus avancé** : son éditeur déplace, redimensionne, masque et gère des presets. Une seule idée à prendre : l'**inspection Tenketsu** (silhouette des points de chakra sous Byakugan).

### E.3 Menu principal
`NydoTitleScreen` + `NydoMenuScene` : illustration en couches (ciel, herbe…) avec **parallaxe** à la souris, lumières qui dérivent, feuilles animées, musique de menu, crédits, boutons de réseaux sociaux. Reborn a son menu et les propositions de menus ont été refusées (DA §3). Seule idée à garder : la parallaxe légère, cohérente avec la règle « décors fixes avec de petits détails animés ».

### E.4 Sons
`sounds.json` : 190 événements (~166 fichiers) : bijū 18, Susanoo 11, techniques nommées (Shinra Tensei, Chibaku Tensei, Kamui, Amaterasu, Hakke…), sons de cinématique (révélation du Sharingan, genjutsu, battement de cœur, aigu, écho de vent), musique de menu. Le principe à garder : des **sons de cinématique génériques** (battement, aigu, écho) réutilisés par marqueur avec pitch et volume variés, ce qui évite un son par scène.

---

## F. Inventaire des modèles 3D et animations

### F.1 Formats
- `geo/*.geo.json` + `animations/*.animation.json` : **GeckoLib = géométrie Bedrock**, le **même format que l'export Model Engine / bbforge**. Les nombres d'os et de cubes ci-dessous sont donc directement comparables aux modèles Reborn.
- `models/entity/{biju,kirin}/*_mesh.json` : **meshes cuits depuis Blockbench** (`format_version 2`, `bake: Blockbench 5.1.4 / 48 fps`, sommets, triangles et os). Kurama = 3408 sommets, Quatre-Queues 9,3 Mo, Kirin 0,5 Mo. C'est ainsi que Nydo affiche des modèles **non-cubes** côté client.
- `models/custom/*.json` (131) : modèles d'objets Java (armes, parchemins, icônes de nature).
- `rigs/gamabunta.json` : points d'assise et bornes visuelles pour **chevaucher** une invocation.
- `sculptures/madara_root_throne.json` : décor décrit en éléments texturés avec des **blocs vanilla** (bornes 384×392×320).
- `wardrobe/` : cf. D. `particles/` : 21 particules (impacts de combat, papier, scarabée, fumée).

### F.2 Inventaire par catégorie

| Catégorie | Modèles (os / cubes / texture) | Animations |
|---|---|---|
| **Susanoo** | 5 utilisateurs (Sasuke, Itachi, Madara, Shisui, Kakashi) × phases nf2→nf6. nf6 : Itachi 343 / 1193, Sasuke 290 / 1225 (+ œil 9 / 22), Madara 293 / 1058, Shisui 257 / 1181, Kakashi 53 / 162, en 512². Cage phase 1 (34 / 139), anciennes phases 2-5 | idle(_fly), walk(_fly), sprint, attack1-5, skills, base_particle. ⚠️ Prestataire tiers |
| **Invocations** | Gamabunta 21 / 24 (512²), Manda 54 / 89, requin 9 / 13, dragon d'eau 30 / 101 | Gamabunta : idle, jump_rise/fall, land, strike, swim. Manda : idle, slither, swim, air, bite |
| **Bêtes de foudre** | Loup Raiton 16 / 99, Panthère noire 14 / 32, Bête de foudre 2 / 31 | Loup : idle, entercombat, walk, running, attack ×2, death |
| **Argile (Deidara)** | C1 araignée 30 / 80, C1 hirondelle 26 / 72, C2 wyverne 33 / 256, C3 hibou 17 / 99, C4 effigie 53 / 367 | Cycle commun : form, idle, fly, arm, detonate, disarm |
| **Bijū / grands** | Gedo Mazo 68 / 1782 (1254²), Kurama et Quatre-Queues en mesh, Kirin en mesh, bloc prototype | Gedo Mazo : dormant, wake, seal, roar, recede |
| **PNJ et décor** | Jiraiya (7 / 12 et v10 8 / 35), mannequin d'entraînement, trône de racines de Madara | idle, walk, attack |
| **Objets** | Miroir de Yata 7 / 13, fiole oculaire 8 / 33 (open), bûche de substitution 1 / 3 (drop), 131 modèles d'objets (kunai, shuriken, fūma, Samehada, Hiramekarei, Kabutowari, Nuibari, Kusanagi, gunbai, faux, épée de Totsuka, parchemins A-D, hiden, invocation, scellement, carte…) | — |

### F.3 Ce qui sert de référence à Reborn
- **Grammaire d'animation des invocations** : le cycle `form → idle → fly → arm → detonate → disarm` (argile) et `dormant → wake → seal → roar → recede` (Gedo Mazo) donnent une **liste d'états type** pour les créatures Model Engine (Kirin, Son Gokū, corbeau). Bbforge peut les générer d'office.
- **Budgets** : un colosse détaillé tourne autour de 250-350 os et 1000-1200 cubes en 512². Une invocation lisible tient en 20-50 os et moins de 100 cubes. Le Kirin bbforge (53 os, 147 cubes) et Son Gokū (29 os, 218 cubes) sont dans une plage saine pour Model Engine.
- **Meshes cuits** : Nydo garde des meshes en les « cuisant » pour un rendu client. Pour Reborn, c'est une **piste** si un jour un modèle de l'équipe ne doit pas être converti en cubes (cas de Son Gokū). Il faudrait alors un rendu dans `reborn-hud`, pas Model Engine.
- **Rig d'assise** (Gamabunta) : chevaucher une invocation en RP (crapaud, chien ninja) nécessite un point d'assise par modèle.
- **Sculptures en blocs vanilla** : décors de cinématiques (trône, autel) construits par donnée. La DA a jugé les décors de blocs « dégueulasses » en sélection de personnage : à réserver aux cinématiques en monde.

---

## Tableau final

| Idée | Ce que fait Nydo | Reborn a déjà ? | Valeur RP | Effort |
|---|---|---|---|---|
| Directeur de cinématiques JSON (caméra, acteurs, animations, marqueurs) | Schéma v1 strict, paquet `{id, caster, target, startTick, seed}` | Partiel : scène de mort (bandes, gris, HUD masqué), caméra 3ᵉ personne | ★★★ | Moyen |
| Exécutions en cinématique | Extraction d'œil : engage 14 ticks + clip long, mise en scène face à face | `executions.yml` sans animation ni caméra | ★★★ | Moyen (après le directeur) |
| Plan « témoins » `*_observer` dans un rayon | Plan large sans prise de contrôle | Non | ★★ | Faible (après le directeur) |
| World scenes (passants qui voient la scène) | État serveur renvoyé au `StartTracking` | Non | ★★ | Moyen |
| Vignettes portrait « manga » | Entité rendue en direct dans un cadre déchiré | Rendu de perso en scène (menus) | ★★★ (éveils, morts) | Moyen |
| Décors procéduraux de genjutsu | Renderers nommés + étalonnage + lentille | Filtres plein écran, Byakugan | ★★ | Élevé |
| Étalonnage et lentille de scène | negative / tint / desaturation / vignette / pulse ; FOV respirant | Partiel (FiltresEcran, vue grisée) | ★★ | Faible |
| Table marqueur → sons | Sons génériques, pitch et volume variés | Sons dédiés par écran | ★★ | Faible |
| Moteur VFX data-driven + presets | 426 effets, 36 géométries, budgets et LOD | Non (particules serveur) | ★★ | Élevé |
| Budget VFX selon le réglage Particules | 16k / 8k / 3k particules, priorités, éviction | Non | ★ (prérequis) | Faible si moteur |
| Animation : tick de départ + vitesse + miroir dans le payload | `applySyncedCombatAnimation` | PlayerAnimationLib, `AnimSyncPayload` | ★★ | Faible |
| Pliure automatique et fondus de sortie | `NYBendyAnimationModifier`, fade 1 / 5 ticks | Non vérifié | ★ | Faible |
| Prédiction locale des animations | `confirmPredictedCombatAnimation` | Non | ★★ (ressenti combat) | Moyen |
| Tenues 3D par emplacement (slim, ouverte, pliure) | 73 pièces JSON en cubes | Skins 2D composés, 5 tenues Uchiha | ★★★ (grades, ANBU, Kage) | Moyen |
| Cécité oculaire (monde en contours) | `NydoOcularBlindness` | Non | ★★★ (perso aveugle, yeux pris) | Moyen |
| Caméra trauma, hitstop | Bruit ∝ trauma², arrêt sur impact | Caméra à presets | ★★ | Faible |
| Inspection Tenketsu | Silhouette des points de chakra | Byakugan (réseau de chakra) | ★★ | Moyen |
| Area cinematic (coups répartis sur N cibles) | Plan déterministe ≤ 64 cibles | Non | ★ | Moyen |
| HUD minimal déplaçable | Drag + sauvegarde | **Oui, plus complet** (éditeur H) | — | — |
| Menu principal en parallaxe | Couches + feuilles + musique | Menu Reborn (refus DA des propositions) | ★ | Faible |
| Liste d'états des invocations (form / idle / arm / detonate…) | Argile, Gedo Mazo, Gamabunta | bbforge (Kirin, Son Gokū) | ★★ | Faible |
| Rig d'assise sur invocation | `rigs/gamabunta.json` | Non | ★★ | Moyen (Model Engine) |
| Meshes cuits depuis Blockbench | Kurama, Kirin, Quatre-Queues | Non (Model Engine = cubes) | ★ | Élevé |

**Ordre conseillé** :
1. directeur de cinématiques minimal (caméra + animations + marqueurs + bandes), branché sur les **exécutions** ;
2. plan témoins ;
3. vignette portrait pour les éveils ;
4. tenues 3D ;
5. cécité RP ;
6. VFX, en dernier.
