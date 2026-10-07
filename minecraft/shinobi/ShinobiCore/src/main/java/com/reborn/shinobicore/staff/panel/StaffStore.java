package com.reborn.shinobicore.staff.panel;

import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Données du Poste de garde, sur disque dans {@code plugins/ShinobiCore/} :
 * <ul>
 *   <li>{@code sanctions.yml} — le casier (avertissements, mutes, bans, avec preuves et note) ;</li>
 *   <li>{@code staff-journal.yml} — les 400 dernières actions staff ;</li>
 *   <li>{@code staff-ips.yml} — dernière IP par compte, pour repérer les comptes liés (lecture staff uniquement).</li>
 * </ul>
 * Première version locale au serveur ; le casier passera dans l'API (Postgres) pour être partagé avec le panel web.
 */
public final class StaffStore {

    public static final class Sanction {
        public String id, offense, kind, staff, note;
        public long at, minutes, until;          // until = 0 : sans fin (avertissement) ; -1 : définitif
        public boolean revoked;
        public List<String> evidence = new ArrayList<>();

        public boolean active(long now) {
            if (revoked) return false;
            if ("WARN".equals(kind) || "KICK".equals(kind)) return false;
            return until < 0 || until > now;
        }
    }

    public record JournalEntry(long at, String staff, int grade, String kind, String type, String detail) { }

    private static final int JOURNAL_MAX = 400;

    private final ShinobiCore plugin;
    private final File sanctionsFile, journalFile, ipsFile;
    private final Map<UUID, List<Sanction>> sanctions = new HashMap<>();
    private final List<JournalEntry> journal = new ArrayList<>();
    private final Map<UUID, String> lastIp = new HashMap<>();
    private final Map<UUID, Long> lastSeen = new HashMap<>();
    private final Map<UUID, String> lastName = new HashMap<>();

    public StaffStore(ShinobiCore plugin) {
        this.plugin = plugin;
        this.sanctionsFile = new File(plugin.getDataFolder(), "sanctions.yml");
        this.journalFile = new File(plugin.getDataFolder(), "staff-journal.yml");
        this.ipsFile = new File(plugin.getDataFolder(), "staff-ips.yml");
        load();
    }

    /* ============================================================ casier */

    public synchronized List<Sanction> of(UUID player) {
        return sanctions.getOrDefault(player, List.of());
    }

    public synchronized void add(UUID player, Sanction s) {
        sanctions.computeIfAbsent(player, k -> new ArrayList<>()).add(s);
        saveSanctions();
    }

    /** Strikes pour cette infraction sur les 90 derniers jours. */
    public synchronized int strikes(UUID player, String offense) {
        long since = System.currentTimeMillis() - 90L * 24 * 3600 * 1000;
        int n = 0;
        for (Sanction s : of(player)) if (!s.revoked && offense.equals(s.offense) && s.at >= since) n++;
        return n;
    }

    /** Fin du mute actif, 0 si aucun. */
    public synchronized long mutedUntil(UUID player) {
        long now = System.currentTimeMillis();
        long best = 0;
        for (Sanction s : of(player)) {
            if ("MUTE".equals(s.kind) && s.active(now)) best = Math.max(best, s.until);
        }
        return best;
    }

    /* ============================================================ journal */

    public synchronized void log(String staff, int grade, String kind, String type, String detail) {
        journal.add(0, new JournalEntry(System.currentTimeMillis(), staff, grade, kind, type, detail));
        while (journal.size() > JOURNAL_MAX) journal.remove(journal.size() - 1);
        saveJournal();
    }

    public synchronized List<JournalEntry> journal() {
        return Collections.unmodifiableList(new ArrayList<>(journal));
    }

    /* ============================================================ IP / comptes liés */

    public synchronized void seen(UUID player, String name, String ip) {
        if (ip != null) lastIp.put(player, ip);
        lastSeen.put(player, System.currentTimeMillis());
        lastName.put(player, name);
        saveIps();
    }

    /** Autres comptes vus avec la même dernière IP. */
    public synchronized Map<UUID, Long> alts(UUID player) {
        String ip = lastIp.get(player);
        Map<UUID, Long> out = new LinkedHashMap<>();
        if (ip == null) return out;
        lastIp.forEach((id, other) -> {
            if (!id.equals(player) && ip.equals(other)) out.put(id, lastSeen.getOrDefault(id, 0L));
        });
        return out;
    }

    public synchronized String nameOf(UUID id) { return lastName.getOrDefault(id, id.toString().substring(0, 8)); }

    /* ============================================================ disque */

    private void load() {
        if (sanctionsFile.isFile()) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(sanctionsFile);
            ConfigurationSection root = cfg.getConfigurationSection("joueurs");
            if (root != null) for (String key : root.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    List<Sanction> list = new ArrayList<>();
                    ConfigurationSection ps = root.getConfigurationSection(key);
                    if (ps == null) continue;
                    for (String sid : ps.getKeys(false)) {
                        ConfigurationSection s = ps.getConfigurationSection(sid);
                        if (s == null) continue;
                        Sanction x = new Sanction();
                        x.id = sid;
                        x.offense = s.getString("infraction", "");
                        x.kind = s.getString("type", "WARN");
                        x.staff = s.getString("staff", "?");
                        x.note = s.getString("note", "");
                        x.at = s.getLong("le");
                        x.minutes = s.getLong("minutes");
                        x.until = s.getLong("jusqua");
                        x.revoked = s.getBoolean("levee", false);
                        x.evidence = new ArrayList<>(s.getStringList("preuves"));
                        list.add(x);
                    }
                    sanctions.put(id, list);
                } catch (IllegalArgumentException ignored) { }
            }
        }
        if (journalFile.isFile()) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(journalFile);
            for (Map<?, ?> m : cfg.getMapList("journal")) {
                try {
                    journal.add(new JournalEntry(((Number) m.get("le")).longValue(), String.valueOf(m.get("staff")),
                            ((Number) m.get("grade")).intValue(), String.valueOf(m.get("kind")),
                            String.valueOf(m.get("type")), String.valueOf(m.get("detail"))));
                } catch (RuntimeException ignored) { }
            }
        }
        if (ipsFile.isFile()) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(ipsFile);
            ConfigurationSection root = cfg.getConfigurationSection("comptes");
            if (root != null) for (String key : root.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    lastIp.put(id, root.getString(key + ".ip"));
                    lastSeen.put(id, root.getLong(key + ".vu"));
                    lastName.put(id, root.getString(key + ".nom", key));
                } catch (IllegalArgumentException ignored) { }
            }
        }
    }

    private void saveSanctions() {
        YamlConfiguration cfg = new YamlConfiguration();
        sanctions.forEach((id, list) -> {
            for (Sanction s : list) {
                String k = "joueurs." + id + "." + s.id;
                cfg.set(k + ".infraction", s.offense);
                cfg.set(k + ".type", s.kind);
                cfg.set(k + ".staff", s.staff);
                cfg.set(k + ".note", s.note);
                cfg.set(k + ".le", s.at);
                cfg.set(k + ".minutes", s.minutes);
                cfg.set(k + ".jusqua", s.until);
                cfg.set(k + ".levee", s.revoked);
                cfg.set(k + ".preuves", s.evidence);
            }
        });
        write(cfg, sanctionsFile);
    }

    private void saveJournal() {
        YamlConfiguration cfg = new YamlConfiguration();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JournalEntry e : journal) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("le", e.at()); m.put("staff", e.staff()); m.put("grade", e.grade());
            m.put("kind", e.kind()); m.put("type", e.type()); m.put("detail", e.detail());
            rows.add(m);
        }
        cfg.set("journal", rows);
        write(cfg, journalFile);
    }

    private void saveIps() {
        YamlConfiguration cfg = new YamlConfiguration();
        Set<UUID> ids = new LinkedHashSet<>(lastIp.keySet());
        ids.addAll(lastName.keySet());
        for (UUID id : ids) {
            cfg.set("comptes." + id + ".ip", lastIp.get(id));
            cfg.set("comptes." + id + ".vu", lastSeen.getOrDefault(id, 0L));
            cfg.set("comptes." + id + ".nom", lastName.get(id));
        }
        write(cfg, ipsFile);
    }

    private void write(YamlConfiguration cfg, File f) {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            cfg.save(f);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Échec d'écriture de " + f.getName(), ex);
        }
    }
}
