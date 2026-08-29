package com.reborn.shinobicore.emote;

import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Emotes RP — pont serveur→client vers EmoteCraft.
 *
 * <p>Le mod-hud embarque la couche cliente : on ne transmet ici que le <b>nom
 * d'emote résolu</b> sur le canal {@link #CHANNEL} ({@code reborn:emote}) à chaque
 * joueur proche, exactement comme {@code reborn:combat}/{@code TYPE_ANIM} pour les
 * coups. Chaque client joue alors l'emote (celle qu'EmoteCraft a chargée) sur
 * l'avatar visé — donc <b>tout le monde la voit</b>.
 *
 * <p>Format S2C (miroir de {@code EmotePayload} côté mod) : {@code int entityId}
 * puis les octets UTF-8 du nom d'emote. Nom vide = arrêt de l'emote en cours.
 *
 * <p>Catalogue dans {@code plugins/ShinobiCore/emotes.yml} (éditable par les devs
 * via le panel) : alias, nom d'affichage, permission, et un {@code open-mode} qui
 * autorise par défaut toute emote chargée côté client.
 */
public final class EmoteManager {

    public static final String CHANNEL = "reborn:emote";

    /** Une emote déclarée : clé de commande, nom affiché, nom EmoteCraft, permission. */
    public record Entry(String key, String display, String emote, String permission) {}

    private final ShinobiCore plugin;

    private final Map<String, Entry> byKey = new LinkedHashMap<>();   // clé + alias → entrée
    private final List<Entry> declared = new ArrayList<>();           // ordre du fichier (menu/tab)
    private boolean openMode = true;
    private double range = 48.0;

    public EmoteManager(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    /** Enregistre le canal sortant + charge le catalogue. */
    public void start() {
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL)) {
            m.registerOutgoingPluginChannel(plugin, CHANNEL);
        }
        reload();
    }

    /** (Re)charge {@code emotes.yml} (créé depuis les ressources au premier lancement). */
    public void reload() {
        byKey.clear();
        declared.clear();

        // Sous-dossier plugins/ShinobiCore/emotes/ — scope propre côté panel staff
        // (et futur emplacement pour déposer des .emotecraft distribués par le serveur).
        File file = new File(plugin.getDataFolder(), "emotes/emotes.yml");
        if (!file.exists()) {
            try {
                plugin.saveResource("emotes/emotes.yml", false);
            } catch (IllegalArgumentException ignored) {
                // ressource absente du jar (build partiel) — on garde les défauts.
            }
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
                        emote == null || emote.isBlank() ? key : emote,
                        perm == null ? "" : perm);
                declared.add(entry);
                byKey.put(entry.key(), entry);
                for (String alias : e.getStringList("aliases")) {
                    if (alias != null && !alias.isBlank()) {
                        byKey.put(alias.toLowerCase(Locale.ROOT), entry);
                    }
                }
            }
        }
        plugin.getLogger().info("Emotes : " + declared.size() + " déclarée(s), open-mode="
                + openMode + ", portée=" + range + " blocs.");
    }

    /**
     * Résout un jeton (clé/alias) en entrée jouable. En {@code open-mode}, un jeton
     * inconnu devient une entrée synthétique (le nom est passé tel quel au client).
     * {@code null} si inconnu et open-mode désactivé.
     */
    public Entry resolve(String token) {
        if (token == null || token.isBlank()) return null;
        Entry e = byKey.get(token.toLowerCase(Locale.ROOT));
        if (e != null) return e;
        if (openMode) return new Entry(token.toLowerCase(Locale.ROOT), token, token, "");
        return null;
    }

    /** Noms de commande déclarés (pour la tab-complétion). */
    public List<String> declaredKeys() {
        List<String> out = new ArrayList<>(declared.size());
        for (Entry e : declared) out.add(e.key());
        return out;
    }

    /**
     * Joue l'emote {@code entry} sur {@code actor} : diffuse à tous les joueurs
     * proches (≤ portée, même monde), acteur inclus, pour que chacun la voie.
     */
    public void play(Player actor, Entry entry) {
        if (actor == null || entry == null) return;
        broadcast(actor, entry.emote());
    }

    /** Arrête l'emote en cours de {@code actor} chez tous les observateurs proches. */
    public void stop(Player actor) {
        if (actor == null) return;
        broadcast(actor, "");
    }

    /** Diffuse {@code {actorEntityId, emoteName}} aux joueurs proches (acteur inclus). */
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
            return; // écriture mémoire — ne peut échouer en pratique
        }

        double r2 = range * range;
        Collection<? extends Player> world = actor.getWorld().getPlayers();
        for (Player p : world) {
            if (!p.equals(actor) && p.getLocation().distanceSquared(actor.getLocation()) > r2) continue;
            try {
                p.sendPluginMessage(plugin, CHANNEL, payload);
            } catch (Exception ignore) {
                // canal non enregistré côté récepteur (pas de mod) → cosmétique.
            }
        }
    }
}
