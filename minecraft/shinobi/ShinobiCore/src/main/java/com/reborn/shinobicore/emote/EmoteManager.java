package com.reborn.shinobicore.emote;

import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Emotes RP — pont serveur→client vers EmoteCraft, + DISTRIBUTION des emotes déposées
 * par les devs.
 *
 * <p>Deux rôles :
 * <ul>
 *   <li><b>Lecture</b> ({@link #CHANNEL} {@code reborn:emote}) : diffuse le nom d'emote
 *       résolu aux joueurs proches → chaque client la joue (visible par tous).</li>
 *   <li><b>Distribution</b> ({@link #CHANNEL_PACK} {@code reborn:emotepack}) : les
 *       fichiers {@code .emotecraft}/{@code .json} déposés dans
 *       {@code plugins/ShinobiCore/emotes/} (via le panel dev) sont POUSSÉS à chaque
 *       client à la connexion → jouables par {@code /playemote <nom>} pour TOUS, sans
 *       mise à jour du mod ni serveur Fabric. C'est ce qui permet aux devs de « drop +
 *       link serveur » (ex. animations de swing kenjutsu M1).</li>
 * </ul>
 *
 * <p>Catalogue {@code emotes/emotes.yml} : alias/permission/open-mode (éditable panel).
 */
public final class EmoteManager implements Listener, PluginMessageListener {

    public static final String CHANNEL = "reborn:emote";
    public static final String CHANNEL_PACK = "reborn:emotepack";

    /** Octets max d'un fichier emote poussé (garde-fou plugin-message). */
    private static final int MAX_EMOTE_BYTES = 512 * 1024;
    private static final long PUSH_DELAY_TICKS = 40L; // ~2s après le join

    public record Entry(String key, String display, String emote, String permission) {}

    private final ShinobiCore plugin;

    private final Map<String, Entry> byKey = new LinkedHashMap<>();
    private final List<Entry> declared = new ArrayList<>();
    /** Emotes serveur déposées par les devs : nom → octets du fichier. */
    private final Map<String, byte[]> customEmotes = new LinkedHashMap<>();
    private boolean openMode = true;
    private double range = 48.0;

    public EmoteManager(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    /** Enregistre les canaux (in/out) + événements, puis charge catalogue + emotes serveur. */
    public void start() {
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL)) m.registerOutgoingPluginChannel(plugin, CHANNEL);
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL_PACK)) m.registerOutgoingPluginChannel(plugin, CHANNEL_PACK);
        if (!m.isIncomingChannelRegistered(plugin, CHANNEL_PACK)) m.registerIncomingPluginChannel(plugin, CHANNEL_PACK, this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        reload();
    }

    /** (Re)charge {@code emotes.yml} + scanne le dossier {@code emotes/} + re-pousse aux joueurs. */
    public void reload() {
        byKey.clear();
        declared.clear();
        customEmotes.clear();

        File dir = new File(plugin.getDataFolder(), "emotes");
        File file = new File(dir, "emotes.yml");
        if (!file.exists()) {
            try { plugin.saveResource("emotes/emotes.yml", false); }
            catch (IllegalArgumentException ignored) {}
        }
        YamlConfiguration cfg = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        openMode = cfg.getBoolean("open-mode", true);
        range = cfg.getDouble("range", 48.0);

        ConfigurationSection emotes = cfg.getConfigurationSection("emotes");
        if (emotes != null) {
            for (String key : emotes.getKeys(false)) {
                ConfigurationSection e = emotes.getConfigurationSection(key);
                if (e == null) continue;
                String display = e.getString("display", key);
                String emote = e.getString("emote", key);
                String perm = e.getString("permission", "");
                Entry entry = new Entry(key.toLowerCase(Locale.ROOT), display,
                        emote == null || emote.isBlank() ? key : emote, perm == null ? "" : perm);
                declared.add(entry);
                byKey.put(entry.key(), entry);
                for (String alias : e.getStringList("aliases")) {
                    if (alias != null && !alias.isBlank()) byKey.put(alias.toLowerCase(Locale.ROOT), entry);
                }
            }
        }

        // Emotes serveur déposées par les devs : tout .emotecraft / .json du dossier.
        File[] files = dir.listFiles((d, n) -> {
            String l = n.toLowerCase(Locale.ROOT);
            return l.endsWith(".emotecraft") || l.endsWith(".json");
        });
        if (files != null) {
            for (File f : files) {
                try {
                    if (f.length() > MAX_EMOTE_BYTES) {
                        plugin.getLogger().warning("Emote " + f.getName() + " ignorée (> "
                                + (MAX_EMOTE_BYTES / 1024) + " Ko).");
                        continue;
                    }
                    String name = f.getName().replaceFirst("\\.(emotecraft|json)$", "");
                    customEmotes.put(name, Files.readAllBytes(f.toPath()));
                } catch (IOException ex) {
                    plugin.getLogger().warning("Lecture emote " + f.getName() + " échouée : " + ex.getMessage());
                }
            }
        }

        plugin.getLogger().info("Emotes : " + declared.size() + " déclarée(s), "
                + customEmotes.size() + " serveur, open-mode=" + openMode + ", portée=" + range);

        // Re-pousse aux joueurs déjà connectés (utile après /playemote reload).
        for (Player p : Bukkit.getOnlinePlayers()) sendPack(p);
    }

    public Entry resolve(String token) {
        if (token == null || token.isBlank()) return null;
        Entry e = byKey.get(token.toLowerCase(Locale.ROOT));
        if (e != null) return e;
        // Une emote serveur non déclarée reste jouable par son nom de fichier.
        if (customEmotes.containsKey(token) || openMode) {
            return new Entry(token.toLowerCase(Locale.ROOT), token, token, "");
        }
        return null;
    }

    public List<String> declaredKeys() {
        List<String> out = new ArrayList<>();
        for (Entry e : declared) out.add(e.key());
        out.addAll(customEmotes.keySet());
        return out;
    }

    public void play(Player actor, Entry entry) {
        if (actor == null || entry == null) return;
        broadcast(actor, entry.emote());
    }

    public void stop(Player actor) {
        if (actor != null) broadcast(actor, "");
    }

    // ─── Distribution (reborn:emotepack) ───

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) sendPack(p); }, PUSH_DELAY_TICKS);
    }

    /** Requête client (« pousse-moi le pack ») → renvoie toutes les emotes serveur. */
    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (CHANNEL_PACK.equals(channel)) sendPack(player);
    }

    /** Pousse chaque emote serveur au client : {@code int nameLen, name, octets}. */
    public void sendPack(Player p) {
        if (customEmotes.isEmpty()) return;
        for (Map.Entry<String, byte[]> e : customEmotes.entrySet()) {
            byte[] nameBytes = e.getKey().getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(b)) {
                out.writeInt(nameBytes.length);
                out.write(nameBytes);
                out.write(e.getValue());
            } catch (IOException ignored) { continue; }
            try {
                p.sendPluginMessage(plugin, CHANNEL_PACK, b.toByteArray());
            } catch (Exception ignore) {
                // canal non enregistré côté client (pas de mod) → cosmétique.
            }
        }
    }

    // ─── Diffusion lecture (reborn:emote) ───

    private void broadcast(Player actor, String emoteName) {
        byte[] payload;
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeInt(actor.getEntityId());
            out.write(emoteName.getBytes(StandardCharsets.UTF_8));
            out.flush();
            payload = b.toByteArray();
        } catch (IOException ignored) {
            return;
        }
        double r2 = range * range;
        for (Player p : actor.getWorld().getPlayers()) {
            if (!p.equals(actor) && p.getLocation().distanceSquared(actor.getLocation()) > r2) continue;
            try {
                p.sendPluginMessage(plugin, CHANNEL, payload);
            } catch (Exception ignore) {
                // canal non enregistré côté récepteur → cosmétique.
            }
        }
    }
}
