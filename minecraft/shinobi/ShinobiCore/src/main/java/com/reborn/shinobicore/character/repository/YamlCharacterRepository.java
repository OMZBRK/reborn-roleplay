package com.reborn.shinobicore.character.repository;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.Affinity;
import com.reborn.shinobicore.character.ChakraAffinity;
import com.reborn.shinobicore.character.NinjaArt;
import com.reborn.shinobicore.character.Rank;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.skill.Skill;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One YAML file per owner UUID at {@code plugins/ShinobiCore/characters/<uuid>.yml}.
 * Each character lives under a section keyed by its own UUID:
 *
 * <pre>
 * 4f56...:
 *   name: Kakashi
 *   clan: Hatake
 *   level: 14
 *   chakra-affinities: [KATON, RAITON]
 *   last-position:
 *     world: world
 *     x: 12.5
 *     ...
 * </pre>
 *
 * <p>The loader reads both the new list form {@code chakra-affinities} and the
 * legacy single-value form {@code chakra-affinity} so old save files continue
 * to work.
 */
public class YamlCharacterRepository implements CharacterRepository {

    private final Logger log;
    private final File dir;
    /** Écritures ordonnées (générations), contenu en attente et pierres tombales (correctif du 07/11). */
    private final FichiersPersonnages fichiers;
    /** Personnage supprimé pour de bon (index des N°, {@code suppression: true}) : jamais rechargé, purgé du fichier. */
    private final Predicate<UUID> supprimeDefinitif;

    public YamlCharacterRepository(ShinobiCore plugin) {
        this(new File(plugin.getDataFolder(), "characters"), plugin.getLogger(),
                new FichiersPersonnages.Planificateur() {
                    @Override public boolean asyncDisponible() { return plugin.isEnabled(); }
                    @Override public void async(Runnable tache) {
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, tache);
                    }
                },
                id -> plugin.playerIds() != null && plugin.playerIds().estSupprimeDefinitif(id));
    }

    /** Constructeur sans serveur (tests) : planificateur et index des N° fournis par l'appelant. */
    YamlCharacterRepository(File dir, Logger log, FichiersPersonnages.Planificateur planif,
                            Predicate<UUID> supprimeDefinitif) {
        this.dir = dir;
        this.log = log;
        this.fichiers = new FichiersPersonnages(log, planif);
        this.supprimeDefinitif = supprimeDefinitif == null ? id -> false : supprimeDefinitif;
    }

    @Override
    public void init() {
        if (!dir.exists() && !dir.mkdirs()) {
            log.warning("Could not create character data folder: " + dir);
        }
    }

    /** Arrêt : réécrit tout de suite les fichiers dont la dernière écriture a échoué (contenu gardé en mémoire). */
    @Override
    public void close() { fichiers.reessayerEchecs(); }

    /**
     * Contenu le plus récent de {@code characters/<compte>.yml} (en attente d'écriture ou disque) ; {@code null} si le
     * fichier est absent. Sert aux sauvegardes du staff (GestionPerso) : une copie du disque pourrait être en retard.
     */
    public String contenuCompte(UUID owner) throws IOException {
        return fichiers.lire(fileFor(owner));
    }

    /* ---------------------------------------------------------------- load */

    @Override
    public List<ShinobiCharacter> loadAll(UUID owner) {
        File f = fileFor(owner);
        // Contenu le plus récent : une écriture encore en file (déconnexion puis reconnexion rapide) fait foi.
        YamlConfiguration cfg = lireConfig(f);
        if (cfg == null || cfg.getKeys(false).isEmpty()) return new ArrayList<>();

        if (f.isFile()) {
            backupIfLegacyLearnedShape(f, cfg);
            backupIfLegacyStats(f, cfg);
        }
        List<ShinobiCharacter> out = new ArrayList<>();
        List<UUID> fantomes = new ArrayList<>();
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection s = cfg.getConfigurationSection(key);
            if (s == null) continue;
            try {
                UUID id = UUID.fromString(key);
                // Filet (07/11) : un personnage supprimé ne ressuscite jamais, même si une vieille écriture l'a remis.
                if (estSupprime(id)) { fantomes.add(id); continue; }
                out.add(fromSection(owner, id, s));
            } catch (IllegalArgumentException e) {
                log.warning("Skipping malformed character '" + key + "' for " + owner + ": " + e.getMessage());
            }
        }
        // Purge d'abord, journal ensuite, et sans ciblePersonnage : loadAll tourne dans byOwner.computeIfAbsent
        // (CharacterManager.getAll) ; résoudre le personnage relancerait getAll sur le même compte pendant le calcul
        // (« Recursive update », voire récursion sans fin tant que le fichier contient encore le fantôme).
        for (UUID id : fantomes) {
            delete(owner, id);
            log.warning("Personnage supprimé " + id + " retrouvé dans " + f.getName() + " : non chargé, purgé du fichier.");
            com.reborn.shinobicore.journal.Journal.entree(com.reborn.shinobicore.api.JournalService.Categorie.SYSTEME,
                    "PERSO_FANTOME_PURGE").cible(owner)
                    .detail("personnage supprimé " + id + " retrouvé au chargement, purgé").ecrire();
        }
        return out;
    }

    /**
     * One-time safety net for the learned-state unification: if the file
     * still carries the legacy {@code known-abilities}/{@code ability-mastery}
     * shape (pre-unification), copy it to {@code <name>.bak} before the next
     * save rewrites it into the {@code learned:} shape. Idempotent — skipped
     * once the backup exists.
     */
    private void backupIfLegacyLearnedShape(File f, YamlConfiguration cfg) {
        File bak = new File(f.getParentFile(), f.getName() + ".bak");
        if (bak.exists()) return;
        boolean legacy = false;
        for (String key : cfg.getKeys(false)) {
            if (cfg.contains(key + ".known-abilities")
                    || cfg.contains(key + ".ability-mastery")) {
                legacy = true;
                break;
            }
        }
        if (!legacy) return;
        try {
            java.nio.file.Files.copy(f.toPath(), bak.toPath());
            log.info("Learned-state migration: backed up "
                    + f.getName() + " to " + bak.getName() + ".");
        } catch (java.io.IOException ex) {
            log.warning("Could not back up " + f.getName()
                    + " before learned-state migration: " + ex.getMessage());
        }
    }

    /**
     * Same safety net for the stats migration (SPEC_STATS_SERVICE §4.2): a
     * character without a {@code stats:} block is converted from its legacy
     * {@code level} / {@code affinity} on load, and the next save rewrites the
     * file — so copy it to {@code <name>.stats.bak} first. Idempotent.
     */
    private void backupIfLegacyStats(File f, YamlConfiguration cfg) {
        File bak = new File(f.getParentFile(), f.getName() + ".stats.bak");
        if (bak.exists()) return;
        boolean legacy = false;
        for (String key : cfg.getKeys(false)) {
            if (cfg.isConfigurationSection(key) && !cfg.isConfigurationSection(key + ".stats")) {
                legacy = true;
                break;
            }
        }
        if (!legacy) return;
        try {
            java.nio.file.Files.copy(f.toPath(), bak.toPath());
            log.info("Stats migration: backed up " + f.getName()
                    + " to " + bak.getName() + ".");
        } catch (java.io.IOException ex) {
            log.warning("Could not back up " + f.getName()
                    + " before stats migration: " + ex.getMessage());
        }
    }

    /**
     * Read the {@code stats:} block, or convert a legacy character (§4.2):
     * {@code points = round(min(15, (level − 1) × 15 / 16))}, split 60/40
     * following the old body affinity, capped at MAX, the rest left unspent.
     * Returns true when a conversion happened (the character must be saved).
     */
    private boolean loadStats(ShinobiCharacter c, ConfigurationSection s) {
        com.reborn.shinobicore.stats.CharacterStats st = c.stats();
        ConfigurationSection sec = s.getConfigurationSection("stats");
        if (sec != null) {
            for (com.reborn.shinobicore.api.StatsService.Stat stat
                    : com.reborn.shinobicore.api.StatsService.Stat.values()) {
                st.set(stat, sec.getInt(stat.key(), com.reborn.shinobicore.api.StatsService.MIN));
            }
            st.setBonusPoints(sec.getInt("bonus", 0));
            // Points de grade séparés des stats (2026-10) : un build qui dépasse les points d'XP est remboursé.
            if (!sec.getBoolean("split", false)) {
                if (com.reborn.shinobicore.stats.StatFormulas.unspent(c.rank(), st) < 0) {
                    log.info("Stats : " + c.name() + " remboursé (points de grade devenus Mérite) "
                            + st.snapshot() + ".");
                    st.reset();
                }
                st.touch();
                return true;
            }
            return false;
        }
        int level = Math.max(1, Math.min(17, s.getInt("level", 1)));
        int points = (int) Math.round(Math.min(15.0, (level - 1) * 15.0 / 16.0));
        if (points > 0) {
            int major = (int) Math.round(points * 0.6);
            int minor = points - major;
            com.reborn.shinobicore.api.StatsService.Stat a, b;
            switch (c.affinity()) {
                case INTELLIGENCE -> { a = com.reborn.shinobicore.api.StatsService.Stat.NINJUTSU;
                                       b = com.reborn.shinobicore.api.StatsService.Stat.CONTROLE; }
                case AGILITY -> { a = com.reborn.shinobicore.api.StatsService.Stat.KENJUTSU;
                                  b = com.reborn.shinobicore.api.StatsService.Stat.VIGUEUR; }
                default -> { a = com.reborn.shinobicore.api.StatsService.Stat.TAIJUTSU;
                             b = com.reborn.shinobicore.api.StatsService.Stat.VIGUEUR; }
            }
            // set() clamps at MAX: the overflow simply stays unspent.
            st.set(a, com.reborn.shinobicore.api.StatsService.MIN + major);
            st.set(b, com.reborn.shinobicore.api.StatsService.MIN + minor);
            // Bonus so the converted build is affordable at the current rank.
            int fromLevel = com.reborn.shinobicore.stats.Progression.pointsFor(c.level());
            st.setBonusPoints(Math.max(0, points - fromLevel));
        }
        st.touch();
        log.info("Stats migration: " + c.name() + " (niveau " + level + ", "
                + c.affinity() + ") → " + points + " point(s) " + st.snapshot() + ".");
        return true;
    }

    private ShinobiCharacter fromSection(UUID owner, UUID id, ConfigurationSection s) {
        // Multi-affinity — read the new list; fall back to the legacy single key.
        Set<ChakraAffinity> affinities = new LinkedHashSet<>();
        List<String> list = s.getStringList("chakra-affinities");
        if (list != null && !list.isEmpty()) {
            for (String a : list) {
                ChakraAffinity parsed = ChakraAffinity.from(a);
                if (parsed != ChakraAffinity.NONE) affinities.add(parsed);
            }
        } else if (s.isString("chakra-affinity")) {
            ChakraAffinity parsed = ChakraAffinity.from(s.getString("chakra-affinity"));
            if (parsed != ChakraAffinity.NONE) affinities.add(parsed);
        }

        String lastWorld = s.getString("last-position.world", null);
        double lx = s.getDouble("last-position.x", 0.0);
        double ly = s.getDouble("last-position.y", 0.0);
        double lz = s.getDouble("last-position.z", 0.0);
        float  lyaw   = (float) s.getDouble("last-position.yaw", 0.0);
        float  lpitch = (float) s.getDouble("last-position.pitch", 0.0);

        ShinobiCharacter c = new ShinobiCharacter(
                id,
                owner,
                s.getString("name", "Shinobi"),
                s.getString("clan", "None"),
                s.getInt("age", 12),
                s.getDouble("size", 1.0),
                NinjaArt.from(s.getString("ninja-art")),
                Affinity.from(s.getString("affinity")),
                affinities,
                Rank.from(s.getString("rank")),
                s.getInt("leaf-test-rolls-used", 0),
                s.getInt("level", 1),
                s.getDouble("experience", 0.0),
                s.getDouble("current-hp", -1.0),
                s.getDouble("current-chakra", -1.0),
                lastWorld, lx, ly, lz, lyaw, lpitch
        );

        // Stats (or legacy conversion), then the saved vitals against the real maxima.
        boolean statsMigrated = loadStats(c, s);
        c.restoreVitals(s.getDouble("current-hp", -1.0), s.getDouble("current-chakra", -1.0));

        // Champs ajoutés après coup (hors constructeur pour ne pas casser sa signature).
        c.setVillage(s.getString("village", ""));
        c.setSexe(s.getString("sexe", "Homme"));
        c.setAppearance(s.getString("appearance", ""));
        c.restoreRyo(s.getLong("ryo", 0L));
        // Player ID (N°) : absent avant la migration du 13/10 — PlayerIdRegistry le pose au démarrage.
        if (s.isInt("playerId")) c.setPlayerId(s.getInt("playerId"));
        for (String oid : s.getStringList("owned-outfits")) c.addOwnedOutfit(oid);
        // Candidature et suivi (lot J, CANDIDATURE_ET_DOUANE.md §4.4-4.5) : absents avant la mise à jour.
        c.setType(s.getString("type", ShinobiCharacter.TYPE_NINJA));
        c.setApplicationId(s.getString("application", null));
        c.setCreatedAt(s.getLong("created-at", 0L));
        c.setLastSeenAt(s.getLong("last-seen-at", 0L));
        c.setPlayMinutes(s.getInt("play-minutes", 0));

        // disabled-mobility-slots block is no longer read — the mobility
        // toggle system was deleted. Legacy keys in saved YAML files are
        // silently ignored.

        // Contact book — who this character knows, and how. Stored as a map
        // of "<subjectCharUuid>": { "display": "...", "nickname": bool }.
        ConfigurationSection contacts = s.getConfigurationSection("contacts");
        if (contacts != null) {
            for (String key : contacts.getKeys(false)) {
                ConfigurationSection row = contacts.getConfigurationSection(key);
                if (row == null) continue;
                String display = row.getString("display");
                boolean isNick = row.getBoolean("nickname", false);
                if (display == null || display.isBlank()) continue;
                try {
                    c.remember(UUID.fromString(key), display, isNick);
                } catch (IllegalArgumentException ignored) { /* bad uuid key */ }
            }
        }

        // Saved nicknames pool — plain list of strings.
        for (String nick : s.getStringList("saved-nicknames")) {
            c.saveNickname(nick);
        }
        // Learned abilities — unified shape: one map, ability id →
        // mastery 0..100, where a key present == known. Falls back to
        // the legacy pair (known-abilities list + ability-mastery
        // section) for pre-migration saves; legacy mastery entries for
        // never-learned abilities become learned (the unified model
        // has no "mastery without knowing" — see LearnedState).
        ConfigurationSection learnedSec = s.getConfigurationSection("learned");
        if (learnedSec != null) {
            for (String aid : learnedSec.getKeys(false)) {
                c.setAbilityMastery(aid, learnedSec.getInt(aid, 0));
            }
        } else {
            for (String aid : s.getStringList("known-abilities")) {
                c.learnAbility(aid);
            }
        }

        // Fiche shinobi v2 : origine / date d'apprentissage des techniques,
        // caractère (3 traits + citation), stade du dōjutsu. Absent = défauts.
        ConfigurationSection metaSec = s.getConfigurationSection("learned-meta");
        if (metaSec != null) {
            for (String aid : metaSec.getKeys(false)) {
                c.setLearnMeta(aid,
                        com.reborn.shinobicore.character.LearnOrigin.from(metaSec.getString(aid + ".origin")),
                        metaSec.getLong(aid + ".at", 0L));
            }
        }
        if (s.isList("caractere.traits")) c.setTraits(s.getStringList("caractere.traits"));
        c.setCitation(s.getString("caractere.citation"));
        c.setDojutsuStage(s.getInt("dojutsu-stade", 0));
        // Yeux (chirurgie) : absent = deux yeux du clan, intacts, sans bandage.
        c.setOeilGauche(com.reborn.shinobicore.medic.yeux.Oeil.fromYaml(s.getConfigurationSection("yeux.gauche")));
        c.setOeilDroit(com.reborn.shinobicore.medic.yeux.Oeil.fromYaml(s.getConfigurationSection("yeux.droit")));
        c.setYeuxBandes(s.getBoolean("yeux.bandage", false));
        // Couleur d'aura de méditation (#RRGGBB) : absente = celle du serveur.
        String aura = s.getString("aura-meditation");
        if (aura != null) {
            try { c.setAuraCouleur(Integer.parseInt(aura.trim().replace("#", ""), 16)); } catch (NumberFormatException ignored) { }
        }

        // Skills & unspent points (the /sc roll system).
        c.setSkillPoints(s.getInt("skill-points", 0));
        ConfigurationSection skillsSec = s.getConfigurationSection("skills");
        if (skillsSec != null) {
            for (String sk : skillsSec.getKeys(false)) {
                Skill parsed = Skill.from(sk);
                if (parsed != null) c.setSkill(parsed, skillsSec.getInt(sk, 0));
            }
        }

        // Legacy per-ability mastery (0..100) — only when the unified
        // learned: section wasn't present above.
        if (learnedSec == null) {
            ConfigurationSection masterySec = s.getConfigurationSection("ability-mastery");
            if (masterySec != null) {
                for (String aid : masterySec.getKeys(false)) {
                    c.setAbilityMastery(aid, masterySec.getInt(aid, 0));
                }
            }
        }

        // Friends — list of character-UUID strings. Mutual graph, maintained
        // by FriendshipManager; we just load the edges we stored for this side.
        for (String fid : s.getStringList("friends")) {
            try { c.addFriend(UUID.fromString(fid)); }
            catch (IllegalArgumentException ignored) { /* bad uuid */ }
        }

        // Welcome / reminder toggle. Stored under "welcome-disabled".
        // Defaults to false (welcome enabled) so fresh characters see
        // the message on first selection. Old saves with the legacy
        // "welcomed" key are ignored — those characters will simply
        // see the message again on next select, no harm done.
        c.setWelcomeDisabled(s.getBoolean("welcome-disabled", false));

        // First-play cinematic intro — default false so brand-new characters
        // run the designated intro once; flipped true on completion.
        c.setIntroSeen(s.getBoolean("intro-seen", false));

        // Death flag (set by validated /tuer requests; set or cleared by
        // the staff from the Poste de garde, F7 › fiche › Personnages). Default false so fresh characters are
        // selectable; older saves without the key behave the same way.
        c.setDead(s.getBoolean("dead", false));

        // jutsu-bindings block is no longer read — the jutsu system was
        // deleted. The new ShinobiAbilities plugin owns its own per-
        // character binding storage. Legacy keys in saved YAML files are
        // silently ignored.

        // Injuries — list of YAML maps, each carrying body part /
        // type / origin / severity / timestamps. Malformed entries are
        // silently skipped so a corrupted single row doesn't lose the
        // rest of the wound history.
        ConfigurationSection injSec = s.getConfigurationSection("injuries");
        if (injSec != null) {
            for (String iKey : injSec.getKeys(false)) {
                ConfigurationSection inj = injSec.getConfigurationSection(iKey);
                if (inj == null) continue;
                com.reborn.shinobicore.ko.injury.Injury parsed =
                        com.reborn.shinobicore.ko.injury.Injury.readFrom(inj);
                if (parsed != null) c.addInjury(parsed);
            }
        }

        // Per-character inventory snapshot. Stored under `inventory.<slot>`
        // as a Bukkit-serialised ItemStack. Absent section = character
        // predates the feature; hasSavedInventory stays false so the
        // first activation seeds from whatever the player currently
        // holds instead of wiping them.
        ConfigurationSection invSec = s.getConfigurationSection("inventory");
        if (invSec != null) {
            ItemStack[] snap = new ItemStack[ShinobiCharacter.INVENTORY_SIZE];
            for (int i = 0; i < snap.length; i++) {
                // ItemStack instances round-trip through YAML via the
                // ConfigurationSerializable interface — YamlConfiguration
                // gives us the rebuilt ItemStack directly.
                Object raw = invSec.get(String.valueOf(i));
                if (raw instanceof ItemStack stack) snap[i] = stack;
            }
            c.setInventorySnapshot(snap);
        }
        // Legacy ability-bindings + disabled-mobility-slots blocks are
        // no longer read — the mobility toggle system was deleted along
        // with the abilities themselves.

        // Loading is not a mutation — start clean so the autosave only
        // writes this character once something actually changes.
        c.markClean();
        // A converted character must reach disk even if nothing else changes.
        if (statsMigrated) c.stats().touch();
        return c;
    }

    /* ---------------------------------------------------------------- save */

    @Override
    public void save(ShinobiCharacter c) {
        enregistrer(c);
    }

    /**
     * Sauvegarde ; faux si rien n'a pu être produit (fichier illisible, sérialisation impossible) : l'appelant garde
     * alors le personnage « à sauvegarder » pour que l'autosave réessaie, au lieu de perdre la modification en silence.
     */
    @Override
    public boolean enregistrer(ShinobiCharacter c) {
        // Personnage supprimé (cette session ou pour de bon) : on ne le réécrit jamais (correctif du 07/11).
        if (estSupprime(c.id())) return true;
        File f = fileFor(c.ownerId());
        // Base = contenu le plus récent (en attente d'écriture ou disque), jamais un fichier périmé.
        YamlConfiguration cfg = lireConfig(f);
        if (cfg == null) {                               // fichier illisible : ne rien écraser, mais le signaler
            log.severe("Personnage " + c.id() + " non sauvegardé : " + f.getName()
                    + " illisible (gardé en mémoire, nouvel essai à la prochaine sauvegarde).");
            return false;
        }
        retirerSupprimes(cfg);

        String key = c.id().toString();
        cfg.set(key + ".name", c.name());
        // Player ID : jamais effacé ici (un perso chargé sans N° ne doit pas écraser celui posé par la migration).
        if (c.hasPlayerId()) cfg.set(key + ".playerId", c.playerId());
        cfg.set(key + ".clan", c.clan());
        cfg.set(key + ".village", c.village().isBlank() ? null : c.village());
        cfg.set(key + ".sexe", "Homme".equals(c.sexe()) ? null : c.sexe());
        cfg.set(key + ".appearance", c.appearance().isBlank() ? null : c.appearance());
        cfg.set(key + ".ryo", c.ryo() == 0L ? null : c.ryo());
        cfg.set(key + ".owned-outfits", c.ownedOutfits().isEmpty()
                ? null : new ArrayList<>(c.ownedOutfits()));
        cfg.set(key + ".age", c.age());
        cfg.set(key + ".size", c.size());
        cfg.set(key + ".ninja-art", c.ninjaArt().name());
        cfg.set(key + ".affinity", c.affinity().name());
        cfg.set(key + ".rank", c.rank().name());
        // Candidature et suivi (lot J) : n'écrire que les valeurs présentes (type par défaut NINJA omis).
        cfg.set(key + ".type", ShinobiCharacter.TYPE_NINJA.equals(c.type()) ? null : c.type());
        cfg.set(key + ".application", c.applicationId());
        cfg.set(key + ".created-at", c.createdAt() > 0 ? c.createdAt() : null);
        cfg.set(key + ".last-seen-at", c.lastSeenAt() > 0 ? c.lastSeenAt() : null);
        cfg.set(key + ".play-minutes", c.playMinutes() > 0 ? c.playMinutes() : null);

        // Multi-affinity: write list form; drop the legacy single-value key so
        // we don't ship stale data from old saves.
        List<String> affNames = new ArrayList<>();
        for (ChakraAffinity a : c.chakraAffinities()) affNames.add(a.name());
        cfg.set(key + ".chakra-affinities", affNames);
        cfg.set(key + ".chakra-affinity", null);

        cfg.set(key + ".leaf-test-rolls-used", c.leafTestRollsUsed());
        cfg.set(key + ".level", c.level());
        cfg.set(key + ".experience", c.experience());
        cfg.set(key + ".current-hp", c.currentHp());
        cfg.set(key + ".current-chakra", c.chakra().current());

        // The six stats + bonus points (unspent is derived from rank, never stored).
        for (com.reborn.shinobicore.api.StatsService.Stat stat
                : com.reborn.shinobicore.api.StatsService.Stat.values()) {
            cfg.set(key + ".stats." + stat.key(), c.stats().get(stat));
        }
        cfg.set(key + ".stats.bonus", c.stats().bonusPoints() > 0 ? c.stats().bonusPoints() : null);
        cfg.set(key + ".stats.split", true);

        if (c.hasLastPosition()) {
            cfg.set(key + ".last-position.world", c.lastWorldName());
            cfg.set(key + ".last-position.x", c.lastX());
            cfg.set(key + ".last-position.y", c.lastY());
            cfg.set(key + ".last-position.z", c.lastZ());
            cfg.set(key + ".last-position.yaw", c.lastYaw());
            cfg.set(key + ".last-position.pitch", c.lastPitch());
        } else {
            cfg.set(key + ".last-position", null);
        }

        // Mobility toggles: persist only the slots the player has *disabled*.
        // Absent-means-enabled keeps the YAML file small and self-explanatory.
        // We also null out the legacy `ability-bindings` map so old saves are
        // migrated cleanly and we never ship stale binding data.
        // Legacy mobility-slot blocks: clear them out of saved files so
        // upgrades don't carry stale data, but write nothing new.
        cfg.set(key + ".ability-bindings", null);
        cfg.set(key + ".disabled-mobility-slots", null);

        // Contact book — persist as a map so readability stays OK in YAML.
        // Writes each entry under `contacts.<subjectUuid>` with display +
        // nickname flag. Null the whole section first so deletions propagate.
        cfg.set(key + ".contacts", null);
        for (var entry : c.contacts().entrySet()) {
            String sub = key + ".contacts." + entry.getKey();
            cfg.set(sub + ".display", entry.getValue().display());
            cfg.set(sub + ".nickname", entry.getValue().isNickname());
        }

        // Saved nickname pool — just a list of strings.
        List<String> nicks = new ArrayList<>(c.savedNicknames());
        cfg.set(key + ".saved-nicknames", nicks.isEmpty() ? null : nicks);

        // Learned abilities — unified map (id → mastery, 0 included so a
        // known-but-untrained ability survives the round-trip). The legacy
        // known-abilities / ability-mastery keys are purged below.
        cfg.set(key + ".learned", null);
        for (var e : c.learned().masteryView().entrySet()) {
            cfg.set(key + ".learned." + e.getKey(), e.getValue());
        }
        cfg.set(key + ".known-abilities", null);

        // Fiche shinobi v2 — n'écrire que les valeurs présentes.
        cfg.set(key + ".learned-meta", null);
        for (var e : c.learnMetaView().entrySet()) {
            if (e.getValue() == null || e.getValue().origin() == null) continue;
            String mk = key + ".learned-meta." + e.getKey();
            cfg.set(mk + ".origin", e.getValue().origin().name());
            cfg.set(mk + ".at", e.getValue().at() > 0 ? e.getValue().at() : null);
        }
        cfg.set(key + ".caractere", null);
        if (!c.traits().isEmpty()) cfg.set(key + ".caractere.traits", new ArrayList<>(c.traits()));
        if (c.citation() != null) cfg.set(key + ".caractere.citation", c.citation());
        cfg.set(key + ".dojutsu-stade", c.dojutsuStage() > 0 ? c.dojutsuStage() : null);
        // Yeux (chirurgie) — n'écrire que ce qui a été touché.
        cfg.set(key + ".yeux", null);
        com.reborn.shinobicore.medic.yeux.Oeil.toYaml(cfg, key + ".yeux.gauche", c.oeilGauche());
        com.reborn.shinobicore.medic.yeux.Oeil.toYaml(cfg, key + ".yeux.droit", c.oeilDroit());
        if (c.yeuxBandes()) cfg.set(key + ".yeux.bandage", true);
        cfg.set(key + ".aura-meditation", c.auraCouleur() >= 0
                ? String.format(java.util.Locale.ROOT, "#%06X", c.auraCouleur()) : null);

        // Skills & unspent points (the /sc roll system).
        cfg.set(key + ".skill-points", c.skillPoints() > 0 ? c.skillPoints() : null);
        cfg.set(key + ".skills", null);
        for (var e : c.skills().entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                cfg.set(key + ".skills." + e.getKey().name(), e.getValue());
            }
        }

        // Legacy mastery section — purged; lives in learned: now.
        cfg.set(key + ".ability-mastery", null);

        // Friends — character-UUID strings. The graph is symmetric; the
        // other side's YAML carries the mirroring edge.
        List<String> friendIds = new ArrayList<>();
        for (UUID friendId : c.friends()) friendIds.add(friendId.toString());
        cfg.set(key + ".friends", friendIds.isEmpty() ? null : friendIds);

        // Welcome-disabled toggle (only persisted when true so fresh
        // characters' YAML stays clean of false defaults). Also wipe
        // the legacy "welcomed" key from old saves on next write.
        cfg.set(key + ".welcomed", null);
        cfg.set(key + ".welcome-disabled", c.welcomeDisabled() ? true : null);
        cfg.set(key + ".intro-seen", c.introSeen() ? true : null);

        // Death flag — same "only persist when true" pattern.
        cfg.set(key + ".dead", c.dead() ? true : null);

        // Legacy jutsu-bindings block: clear out of saved files. The new
        // ShinobiAbilities plugin owns its own per-character storage.
        cfg.set(key + ".jutsu-bindings", null);

        // Injuries — wipe the section first so removed wounds disappear
        // from disk, then re-write each one keyed by the injury's UUID
        // so editing the YAML by hand stays unambiguous.
        cfg.set(key + ".injuries", null);
        if (!c.injuries().isEmpty()) {
            for (com.reborn.shinobicore.ko.injury.Injury inj : c.injuries()) {
                String iKey = key + ".injuries." + inj.id();
                cfg.createSection(iKey);
                inj.writeTo(cfg.getConfigurationSection(iKey));
            }
        }

        // Inventory snapshot. Blank section out first so removed items
        // don't linger across saves. Only write when the character has
        // ever had their inventory captured — otherwise we'd clobber a
        // valid "inherit on first use" state with an empty section.
        cfg.set(key + ".inventory", null);
        if (c.hasSavedInventory()) {
            ItemStack[] snap = c.inventorySnapshot();
            for (int i = 0; i < snap.length; i++) {
                if (snap[i] != null) cfg.set(key + ".inventory." + i, snap[i]);
            }
        }

        // Écriture différée — sérialisée ici, sur le fil principal (données cohérentes) ; seule la chaîne produite
        // part en arrière-plan. YamlConfiguration n'est pas thread-safe, l'objet cfg ne quitte jamais ce fil.
        //
        // Ordre (correctif du 07/11) : FichiersPersonnages numérote chaque contenu à sa production ; une écriture plus
        // ancienne que la dernière arrivée sur le disque est sautée, et l'écriture est atomique (.tmp puis renommage).
        String serialized;
        try {
            serialized = cfg.saveToString();
        } catch (Throwable ex) {
            log.log(Level.SEVERE,
                    "Failed to serialize character " + c.id(), ex);
            return false;
        }
        fichiers.ecrire(f, serialized);
        return true;
    }

    /* -------------------------------------------------------------- delete */

    /**
     * Suppression : pierre tombale d'abord (plus aucune sauvegarde ne réécrit ce personnage), puis le fichier est
     * réécrit tout de suite à partir du contenu le plus récent, sous le verrou du fichier, avec une génération plus
     * récente que toute écriture encore en file (qui sera donc ignorée). Fichier devenu vide : supprimé.
     */
    @Override
    public void delete(UUID owner, UUID characterId) {
        fichiers.enterrer(characterId);
        File f = fileFor(owner);
        YamlConfiguration cfg = lireConfig(f);
        if (cfg == null) return;
        cfg.set(characterId.toString(), null);
        retirerSupprimes(cfg);
        try {
            if (cfg.getKeys(false).isEmpty()) {
                fichiers.ecrireMaintenant(f, null);
            } else {
                fichiers.ecrireMaintenant(f, cfg.saveToString());
            }
        } catch (RuntimeException ex) {
            log.log(Level.SEVERE, "Failed to delete character " + characterId, ex);
        }
    }

    /** Supprimé pendant la session (pierre tombale) ou pour de bon (index des N°, {@code suppression: true}). */
    @Override
    public boolean estSupprime(UUID characterId) {
        return fichiers.estEnterre(characterId) || supprimeDefinitif.test(characterId);
    }

    /** Retire toute clé d'un personnage supprimé (un reste périmé ne doit pas survivre à une réécriture). */
    private void retirerSupprimes(YamlConfiguration cfg) {
        for (String key : new ArrayList<>(cfg.getKeys(false))) {
            UUID id;
            try { id = UUID.fromString(key); } catch (IllegalArgumentException e) { continue; }
            if (estSupprime(id)) cfg.set(key, null);
        }
    }

    /**
     * Contenu le plus récent du fichier en YAML : vide si absent ; {@code null} si illisible (on n'écrase rien). Un
     * YAML invalide donne une configuration vide, comme {@code YamlConfiguration.loadConfiguration} avant.
     */
    private YamlConfiguration lireConfig(File f) {
        YamlConfiguration cfg = new YamlConfiguration();
        String texte;
        try {
            texte = fichiers.lire(f);
        } catch (IOException ex) {
            log.log(Level.SEVERE, "Cannot read " + f, ex);
            return null;
        }
        if (texte == null) return cfg;
        try {
            cfg.loadFromString(texte);
        } catch (InvalidConfigurationException ex) {
            log.log(Level.SEVERE, "Cannot load " + f, ex);
        }
        return cfg;
    }

    /* -------------------------------------------------------------- helpers */

    private File fileFor(UUID owner) {
        return new File(dir, owner + ".yml");
    }

    // Silence unused-import lint for EnumSet; it's here if you want to extend.
    @SuppressWarnings("unused")
    private static final Set<ChakraAffinity> KEEP_IMPORT = EnumSet.noneOf(ChakraAffinity.class);
}
