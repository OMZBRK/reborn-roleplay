# Correctifs préparés — nuit 2026-09-17 (sujet C)

> **Non déployé.** Analyse + patchs prêts pour validation. Rien n'a été écrit sur
> le serveur (garde-fous nocturnes).

## 1. Crash MagicSpells récurrent — `missing required data interface BlockData`

**Signature** (dernier `plugins/MagicSpells/errors/*.txt`, 2026-09-05) :
```
java.lang.IllegalArgumentException: missing required data interface org.bukkit.block.data.BlockData
  at CraftParticle.createParticleParam(...)
  at ParticlesEffect.playEffectLocation(ParticlesEffect.java:162)
  at ParticleProjectileSpell.playEffects(...)
```
→ un effet `effect: particles` dont la particule exige un `BlockData`
(`block`, `block_marker`, `falling_dust`, `block_crumble`, `dust_pillar`) est
joué **sans `material:`** → crash à chaque tick du projectile (d'où les centaines
de fichiers dans `errors/`).

**Résultat du scan** des fichiers récupérés (tous les `spells-*.yml`) : **aucune**
particule de la famille BlockData/ItemStack en `particle-name`. Les 9 particules
utilisées : `cloud` (×5), `flame`, `redstone`, `nautilus`, `villager_happy` —
aucune n'exige de BlockData.

**Conclusion** : le sort fautif n'est **pas** dans les fichiers analysés — il est
soit dans un fichier non capté, soit ajouté après le snapshot. **À localiser sur
le serveur live** (5 s) :
```bash
grep -rEni "particle-name:\s*(block|falling_dust|block_marker|block_crumble|dust_pillar)" plugins/MagicSpells/
```
Puis ajouter `material: <un_bloc>` (ex. `material: stone`) à l'effet, ou changer
la particule.

**✅ Prévention déjà en place** : l'éditeur (Technique Creator) **refuse à la
compilation** toute particule block/item/falling_dust sans `material`
(`validateGraph` → `validateParticles`). Les sorts créés via l'éditeur ne peuvent
plus provoquer ce crash. Reste à corriger le sort hérité côté serveur (ci-dessus).

## 2. Construct YAML fragile — `spells-command.yml`

Lignes 52-53 : une chaîne entre guillemets s'étend sur deux lignes, la
continuation revenant en colonne 0 :
```yaml
    str-cast-self: "You have successfully bound the %s spell to the
item you are holding."
```
SnakeYAML (serveur) le tolère probablement (repliage de scalaire), mais c'est
fragile et **casse les parseurs stricts** (rencontré à l'analyse). **Patch prêt**
— mettre la valeur sur une seule ligne :
```yaml
    str-cast-self: "You have successfully bound the %s spell to the item you are holding."
```
Non urgent (le fichier charge sûrement), mais à assainir tant qu'on y touche.

## 3. Inventaire particules serveur (pour référence)

`cloud` 5 · `flame` 1 · `redstone` 1 · `nautilus` 1 · `villager_happy` 1.
Très peu de particules — l'essentiel des VFX passe par `effect: entity`
(item_display Nexo, 52×) et `effect: effectlib` (29×). Piste d'enrichissement :
la palette 26.2 offre `dust_color_transition` (dégradé par affinité) et la famille
block/item (avec `material`) — que l'éditeur sait déjà générer proprement.
