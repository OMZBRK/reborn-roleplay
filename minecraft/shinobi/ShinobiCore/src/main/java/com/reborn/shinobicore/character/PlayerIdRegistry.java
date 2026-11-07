package com.reborn.shinobicore.character;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <b>Player ID</b> — le N° court de chaque personnage (GAMEPLAN §1.4, décision du 13/10 : « on commence par
 * 00000 »). Affiché sur 5 chiffres avec zéros ({@code 00000}, {@code 00001}…), unique, <b>jamais réutilisé</b> : un
 * personnage supprimé reste dans l'index, marqué {@code deleted}.
 *
 * <p>Fichier {@code plugins/ShinobiCore/player-ids.yml} :
 * <pre>
 * next: 3
 * index:
 *   '00000': {character: &lt;uuid perso&gt;, owner: &lt;uuid MC&gt;, name: Kaito Uzumaki, deleted: false}
 * </pre>
 * {@code suppression: true} (correctif du 07/11) distingue une vraie suppression ({@link #markDeleted}, Poste de garde)
 * d'un personnage simplement absent du disque au démarrage (marqué supprimé par la migration, réactivable si son fichier
 * revient). Un personnage supprimé pour de bon n'est jamais réactivé : s'il réapparaît dans un fichier, il en est purgé.
 * Pour restaurer volontairement une sauvegarde, serveur éteint : retirer {@code suppression} et passer
 * {@code deleted: false} dans la ligne de l'index.
 *
 * <p>Écriture atomique (fichier temporaire puis renommage). Le compteur est écrit <b>avant</b> que le numéro soit rendu :
 * si l'écriture échoue, rien n'est attribué ({@code -1}) et l'appelant réessaiera. Toutes les mutations sont
 * synchronisées.
 *
 * <p>Le N° n'est montré qu'au joueur lui-même (sa fiche) et au staff : jamais aux autres joueurs (le système
 * « rencontrer » garde les noms secrets).
 */
public final class PlayerIdRegistry {

    /** Une ligne de l'index. */
    public record Entry(int number, UUID characterId, UUID ownerId, String name, boolean deleted) {
        public String formatted() { return format(number); }
    }

    private final Logger log;
    private final File file;
    private int next = 0;
    private final TreeMap<Integer, Entry> byNumber = new TreeMap<>();
    private final Map<UUID, Integer> byCharacter = new HashMap<>();
    /** N° supprimés par {@link #markDeleted} (vraie suppression) — jamais réactivés par la migration. */
    private final Set<Integer> suppressions = new HashSet<>();

    public PlayerIdRegistry(Plugin plugin) {
        this(new File(plugin.getDataFolder(), "player-ids.yml"), plugin.getLogger());
    }

    /** Constructeur sans serveur (tests). */
    PlayerIdRegistry(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    /* ================================================================ format */

    /** {@code 42} → {@code "00042"} (5 chiffres minimum). */
    public static String format(int n) {
        return n < 0 ? "" : String.format("%05d", n);
    }

    /**
     * {@code "#00042"} / {@code "#42"} → 42 ; tout le reste (pseudo, nom RP, nombre sans dièse) → -1. Le dièse est
     * obligatoire pour ne pas confondre un N° avec un pseudo numérique.
     */
    public static int parse(String arg) {
        if (arg == null) return -1;
        String s = arg.trim();
        if (s.length() < 2 || s.length() > 10 || s.charAt(0) != '#') return -1;
        for (int i = 1; i < s.length(); i++) if (!Character.isDigit(s.charAt(i))) return -1;
        try { return Integer.parseInt(s.substring(1)); } catch (NumberFormatException e) { return -1; }
    }

    /* ================================================================ lecture */

    public synchronized Entry get(int number) { return byNumber.get(number); }

    /** N° du personnage, ou -1. */
    public synchronized int numberOf(UUID characterId) {
        Integer n = characterId == null ? null : byCharacter.get(characterId);
        return n == null ? -1 : n;
    }

    public synchronized int next() { return next; }

    /**
     * Vrai si le personnage a été supprimé pour de bon ({@link #markDeleted}) : il ne doit jamais être rechargé ni
     * réactivé (un personnage seulement absent du disque au démarrage ne compte pas).
     */
    public synchronized boolean estSupprimeDefinitif(UUID characterId) {
        Integer n = characterId == null ? null : byCharacter.get(characterId);
        if (n == null || !suppressions.contains(n)) return false;
        Entry e = byNumber.get(n);
        return e != null && e.deleted();
    }

    public synchronized int size() { return byNumber.size(); }

    /** Copie de l'index (ordre croissant des N°). */
    public synchronized List<Entry> entries() { return new ArrayList<>(byNumber.values()); }

    /* ================================================================ écriture */

    /**
     * Attribue un N° au personnage (ou rend celui qu'il a déjà). Le compteur est persisté avant le retour ; en cas
     * d'échec d'écriture, l'attribution est annulée et la méthode rend -1.
     */
    public synchronized int assign(UUID characterId, UUID ownerId, String name) {
        Integer have = byCharacter.get(characterId);
        if (have != null) return have;
        int n = next;
        while (byNumber.containsKey(n)) n++;          // garde-fou : index édité à la main
        Entry e = new Entry(n, characterId, ownerId, name == null ? "" : name, false);
        int prevNext = next;
        byNumber.put(n, e);
        byCharacter.put(characterId, n);
        next = n + 1;
        if (!write()) {
            byNumber.remove(n);
            byCharacter.remove(characterId);
            next = prevNext;
            return -1;
        }
        return n;
    }

    /** Marque le personnage supprimé (son N° reste pris pour toujours). */
    public synchronized void markDeleted(UUID characterId) {
        Integer n = byCharacter.get(characterId);
        if (n == null) return;
        Entry e = byNumber.get(n);
        if (e == null || (e.deleted() && suppressions.contains(n))) return;
        byNumber.put(n, new Entry(n, e.characterId(), e.ownerId(), e.name(), true));
        suppressions.add(n);
        // La session garde la suppression en mémoire (aucun retour possible d'ici l'arrêt) ; chaque écriture suivante de
        // l'index la persistera. Mais tant qu'elle n'est pas sur le disque, le filet du redémarrage (migrate) ne la voit
        // pas : on le dit clairement plutôt qu'un simple « écriture impossible ».
        if (!write()) {
            log.severe("[player-id] suppression du N° " + format(n) + " (personnage " + characterId + ") NON enregistrée "
                    + "dans player-ids.yml : elle sera réécrite à la prochaine écriture de l'index. Si le serveur s'arrête "
                    + "avant, vérifier au redémarrage que ce personnage n'est pas revenu (et le resupprimer).");
        }
    }

    /** Met à jour le nom RP de l'index (renommage staff) ; n'écrit que si le nom a changé. */
    public synchronized void rename(UUID characterId, String name) {
        Integer n = byCharacter.get(characterId);
        if (n == null || name == null) return;
        Entry e = byNumber.get(n);
        if (e == null || name.equals(e.name())) return;
        byNumber.put(n, new Entry(n, e.characterId(), e.ownerId(), name, e.deleted()));
        write();
    }

    /* ================================================================ disque */

    /** Lit {@code player-ids.yml} (absent = index vide, compteur à 0). */
    public synchronized void load() {
        byNumber.clear();
        byCharacter.clear();
        suppressions.clear();
        next = 0;
        if (!file.isFile()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection idx = y.getConfigurationSection("index");
        int max = -1;
        if (idx != null) {
            for (String key : idx.getKeys(false)) {
                ConfigurationSection s = idx.getConfigurationSection(key);
                if (s == null) continue;
                try {
                    // Le N° est aussi écrit dans la ligne : la clé n'est qu'un repère lisible.
                    int n = s.isInt("n") ? s.getInt("n") : Integer.parseInt(key.trim());
                    UUID ch = UUID.fromString(s.getString("character", ""));
                    String ow = s.getString("owner", "");
                    Entry e = new Entry(n, ch, ow.isBlank() ? null : UUID.fromString(ow),
                            s.getString("name", ""), s.getBoolean("deleted", false));
                    if (byNumber.containsKey(n) || byCharacter.containsKey(ch)) {
                        log.warning("[player-id] doublon ignoré dans player-ids.yml : " + key);
                        continue;
                    }
                    byNumber.put(n, e);
                    byCharacter.put(ch, n);
                    if (e.deleted() && s.getBoolean("suppression", false)) suppressions.add(n);
                    max = Math.max(max, n);
                } catch (IllegalArgumentException ex) {
                    log.warning("[player-id] ligne illisible dans player-ids.yml : " + key);
                }
            }
        }
        // Jamais en dessous du plus grand N° connu + 1 (un compteur reculé réattribuerait un numéro).
        next = Math.max(y.getInt("next", 0), max + 1);
    }

    /** Écriture atomique : {@code player-ids.yml.tmp} puis renommage. */
    private boolean write() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of(
                "Player ID — N° de personnage (ShinobiCore). Ne pas éditer à la main serveur allumé.",
                "next = prochain N° à attribuer ; un N° n'est jamais réutilisé (deleted: true = personnage supprimé)."));
        y.set("next", next);
        for (Entry e : byNumber.values()) {
            String k = "index." + format(e.number());
            y.set(k + ".n", e.number());
            y.set(k + ".character", e.characterId().toString());
            y.set(k + ".owner", e.ownerId() == null ? "" : e.ownerId().toString());
            y.set(k + ".name", e.name());
            y.set(k + ".deleted", e.deleted());
            if (e.deleted() && suppressions.contains(e.number())) y.set(k + ".suppression", true);
        }
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) throw new IOException("dossier " + dir);
            atomicWrite(file.toPath(), y.saveToString());
            return true;
        } catch (IOException | RuntimeException ex) {
            log.log(Level.SEVERE, "[player-id] écriture de player-ids.yml impossible", ex);
            return false;
        }
    }

    static void atomicWrite(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /* ================================================================ migration */

    /**
     * Migration au démarrage (idempotente) : chaque personnage de {@code characters/*.yml} reçoit un N° s'il n'en a
     * pas, dans un ordre déterministe (UUID du propriétaire, puis UUID du personnage). Un N° déjà présent dans un
     * fichier personnage est réinscrit dans l'index s'il y manque. Les entrées de l'index dont le personnage n'existe
     * plus sur le disque sont marquées supprimées. À appeler avant tout chargement de roster.
     *
     * <p>Filet du 07/11 : un personnage supprimé pour de bon ({@code suppression: true}) retrouvé dans un fichier n'est
     * <b>pas</b> réactivé ; il est purgé du fichier (fichier supprimé s'il devient vide) et signalé une fois au log.
     *
     * @return nombre de N° attribués pendant ce passage
     */
    public synchronized int migrate(File charactersDir) {
        File[] files = charactersDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return 0;
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        Set<UUID> seen = new HashSet<>();
        int assigned = 0, relinked = 0, purged = 0;
        boolean indexDirty = false;
        for (File f : files) {
            UUID owner;
            try { owner = UUID.fromString(f.getName().substring(0, f.getName().length() - 4)); }
            catch (IllegalArgumentException ex) { continue; }
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            List<String> keys = new ArrayList<>(cfg.getKeys(false));
            Collections.sort(keys);
            boolean fileDirty = false;
            for (String key : keys) {
                if (!cfg.isConfigurationSection(key)) continue;
                UUID charId;
                try { charId = UUID.fromString(key); } catch (IllegalArgumentException ex) { continue; }
                String name = cfg.getString(key + ".name", "");
                Integer known = byCharacter.get(charId);
                Entry ke = known == null ? null : byNumber.get(known);
                if (ke != null && ke.deleted() && suppressions.contains(known)) {
                    // Supprimé pour de bon puis remis par une écriture périmée : purgé, jamais ressuscité.
                    log.warning("[player-id] personnage supprimé " + format(known) + " " + ke.name() + " (" + charId
                            + ") retrouvé dans " + f.getName() + " : purgé du fichier, N° gardé supprimé.");
                    cfg.set(key, null);
                    fileDirty = true;
                    purged++;
                    continue;
                }
                seen.add(charId);
                if (ke != null && ke.deleted()) {                     // présent sur le disque = pas supprimé
                    byNumber.put(known, new Entry(known, charId, ke.ownerId(), ke.name(), false));
                    indexDirty = true;
                }
                if (cfg.isInt(key + ".playerId")) {
                    int n = cfg.getInt(key + ".playerId");
                    Entry e = byNumber.get(n);
                    if (known != null && known == n) continue;                       // déjà cohérent (idempotent)
                    if (known == null && e == null && n >= 0) {                       // index perdu : on réinscrit
                        byNumber.put(n, new Entry(n, charId, owner, name, false));
                        byCharacter.put(charId, n);
                        next = Math.max(next, n + 1);
                        indexDirty = true;
                        relinked++;
                        continue;
                    }
                    log.warning("[player-id] N° " + format(n) + " de " + name + " (" + charId
                            + ") en conflit avec l'index — l'index fait foi.");
                }
                int n = known != null ? known : -1;
                if (n < 0) {
                    n = next;
                    while (byNumber.containsKey(n)) n++;
                    byNumber.put(n, new Entry(n, charId, owner, name, false));
                    byCharacter.put(charId, n);
                    next = n + 1;
                    indexDirty = true;
                    assigned++;
                }
                cfg.set(key + ".playerId", n);
                fileDirty = true;
            }
            if (fileDirty) {
                // L'index d'abord : un N° écrit dans un personnage doit toujours être connu de l'index.
                if (indexDirty && !write()) {
                    log.severe("[player-id] migration interrompue (index non écrit).");
                    return assigned;
                }
                indexDirty = false;
                try {
                    if (cfg.getKeys(false).isEmpty()) Files.deleteIfExists(f.toPath());   // ne contenait qu'un fantôme
                    else atomicWrite(f.toPath(), cfg.saveToString());
                } catch (IOException | RuntimeException ex) {
                    log.log(Level.SEVERE, "[player-id] écriture de " + f.getName() + " impossible", ex);
                }
            }
        }
        int orphans = 0;
        for (Entry e : new ArrayList<>(byNumber.values())) {
            if (!e.deleted() && !seen.contains(e.characterId())) {
                byNumber.put(e.number(), new Entry(e.number(), e.characterId(), e.ownerId(), e.name(), true));
                orphans++;
                indexDirty = true;
            }
        }
        if (indexDirty) write();
        if (assigned + relinked + orphans + purged > 0) {
            log.info("[player-id] migration : " + assigned + " N° attribué(s), " + relinked
                    + " réinscrit(s), " + orphans + " marqué(s) supprimé(s), " + purged
                    + " personnage(s) supprimé(s) purgé(s) ; prochain N° " + format(next) + ".");
        }
        return assigned;
    }
}
