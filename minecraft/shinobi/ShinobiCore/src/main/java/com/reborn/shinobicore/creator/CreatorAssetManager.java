package com.reborn.shinobicore.creator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.Bukkit;
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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Distribution serveur→client des <b>assets du character creator</b> (tenues,
 * cheveux, yeux, pilosité, accessoires…). Calqué sur {@code EmoteManager} : les
 * fichiers déposés par les devs dans {@code plugins/ShinobiCore/creator-assets/}
 * sont poussés à chaque client à la connexion (canal {@code reborn:creatorpack}),
 * qui les injecte dans son catalogue au runtime → nouveaux cosmétiques visibles
 * dans le creator <b>sans republier le mod</b>.
 *
 * <p>Arborescence attendue dans {@code creator-assets/} :
 * <pre>
 *   catalog.json                 (mêmes clés que le catalogue bundlé : hair/outfit/…)
 *   &lt;catégorie&gt;/&lt;id&gt;.png          (ex. outfit/Complet_Foo.png)
 *   &lt;catégorie&gt;/&lt;id&gt;_Mask.png     (masque RGBA optionnel)
 * </pre>
 *
 * <p>Format d'un asset (réassemblé côté client) : {@code UTF folder, UTF id,
 * UTF metaJson, int pngLen, png, int maskLen, mask}.
 */
public final class CreatorAssetManager implements Listener, PluginMessageListener {

    public static final String CHANNEL_PACK = "reborn:creatorpack";

    /** Catégories reconnues (mêmes que le catalogue bundlé côté mod). */
    private static final String[] CATEGORIES =
            { "hair", "outfit", "eyes", "facial", "tattoo", "accessory", "underwear" };

    /** Octets max d'un PNG poussé (garde-fou). */
    private static final int MAX_PNG_BYTES = 512 * 1024;
    /** Taille d'un chunk : sous la limite des plugin-messages Bukkit. */
    private static final int CHUNK = 24000;
    private static final long PUSH_DELAY_TICKS = 45L; // ~2,25 s après le join

    /** Un asset prêt à pousser : corps binaire complet (folder+id+meta+png+mask). */
    private record Packed(String key, byte[] body) {}

    private final ShinobiCore plugin;
    private final List<Packed> assets = new ArrayList<>();

    public CreatorAssetManager(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    /** Enregistre le canal (in/out) + events, puis charge les assets. */
    public void start() {
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL_PACK)) m.registerOutgoingPluginChannel(plugin, CHANNEL_PACK);
        if (!m.isIncomingChannelRegistered(plugin, CHANNEL_PACK)) m.registerIncomingPluginChannel(plugin, CHANNEL_PACK, this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        reload();
    }

    /** (Re)charge {@code creator-assets/catalog.json} + PNG puis re-pousse aux joueurs. */
    public void reload() {
        assets.clear();
        File dir = new File(plugin.getDataFolder(), "creator-assets");
        if (!dir.exists()) dir.mkdirs();
        File catalog = new File(dir, "catalog.json");
        if (!catalog.exists()) {
            plugin.getLogger().info("Creator assets : aucun catalog.json (dossier " + dir.getPath() + ").");
            return;
        }
        JsonObject root;
        try (InputStreamReader r = new InputStreamReader(Files.newInputStream(catalog.toPath()), StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(r).getAsJsonObject();
        } catch (Exception e) {
            plugin.getLogger().warning("Creator assets : catalog.json illisible (" + e.getMessage() + ").");
            return;
        }

        int loaded = 0, skipped = 0;
        for (String cat : CATEGORIES) {
            if (!root.has(cat) || !root.get(cat).isJsonArray()) continue;
            JsonArray arr = root.getAsJsonArray(cat);
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String id = o.has("id") && !o.get("id").isJsonNull() ? o.get("id").getAsString() : null;
                if (id == null || id.isBlank()) { skipped++; continue; }
                File png = new File(dir, cat + "/" + id + ".png");
                if (!png.isFile()) {
                    plugin.getLogger().warning("Creator assets : PNG manquant pour " + cat + "/" + id
                            + " (attendu " + png.getPath() + ").");
                    skipped++;
                    continue;
                }
                if (png.length() > MAX_PNG_BYTES) {
                    plugin.getLogger().warning("Creator assets : " + cat + "/" + id + " ignoré (> "
                            + (MAX_PNG_BYTES / 1024) + " Ko).");
                    skipped++;
                    continue;
                }
                File maskFile = new File(dir, cat + "/" + id + "_Mask.png");
                try {
                    byte[] pngBytes = Files.readAllBytes(png.toPath());
                    byte[] maskBytes = maskFile.isFile() ? Files.readAllBytes(maskFile.toPath()) : new byte[0];
                    byte[] body = pack(cat, id, o.toString(), pngBytes, maskBytes);
                    assets.add(new Packed(cat + "/" + id, body));
                    loaded++;
                } catch (IOException ex) {
                    plugin.getLogger().warning("Creator assets : lecture " + cat + "/" + id
                            + " échouée (" + ex.getMessage() + ").");
                    skipped++;
                }
            }
        }
        plugin.getLogger().info("Creator assets : " + loaded + " chargé(s), " + skipped + " ignoré(s).");
        for (Player p : Bukkit.getOnlinePlayers()) sendPack(p);
    }

    /** Corps binaire d'un asset : {@code UTF folder, UTF id, UTF metaJson, int pngLen, png, int maskLen, mask}. */
    private static byte[] pack(String folder, String id, String metaJson, byte[] png, byte[] mask) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(b)) {
            out.writeUTF(folder);
            out.writeUTF(id);
            out.writeUTF(metaJson);
            out.writeInt(png.length);
            out.write(png);
            out.writeInt(mask.length);
            if (mask.length > 0) out.write(mask);
        }
        return b.toByteArray();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) sendPack(p); }, PUSH_DELAY_TICKS);
    }

    /** Requête client (« pousse-moi le pack ») → renvoie tous les assets. */
    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (CHANNEL_PACK.equals(channel)) sendPack(player);
    }

    /**
     * Pousse chaque asset au client EN CHUNKS (les PNG dépassent la taille max d'un
     * plugin-message). Enveloppe par chunk : {@code int nameLen, name, int idx, int total,
     * octets du morceau}.
     */
    public void sendPack(Player p) {
        if (assets.isEmpty()) return;
        int sent = 0, chunks = 0;
        for (Packed a : assets) {
            byte[] nameBytes = a.key().getBytes(StandardCharsets.UTF_8);
            byte[] data = a.body();
            int total = Math.max(1, (data.length + CHUNK - 1) / CHUNK);
            boolean ok = true;
            for (int idx = 0; idx < total && ok; idx++) {
                int start = idx * CHUNK;
                int len = Math.min(CHUNK, data.length - start);
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                try (DataOutputStream out = new DataOutputStream(b)) {
                    out.writeInt(nameBytes.length);
                    out.write(nameBytes);
                    out.writeInt(idx);
                    out.writeInt(total);
                    out.write(data, start, len);
                } catch (IOException ignored) { ok = false; break; }
                try {
                    p.sendPluginMessage(plugin, CHANNEL_PACK, b.toByteArray());
                    chunks++;
                } catch (Exception ex) {
                    plugin.getLogger().warning("Creator pack: '" + a.key() + "' chunk " + idx
                            + " → " + p.getName() + " échoué : " + ex.getMessage());
                    ok = false;
                }
            }
            if (ok) sent++;
        }
        plugin.getLogger().info("Creator assets: pack envoyé à " + p.getName()
                + " (" + sent + " asset(s), " + chunks + " chunk(s)).");
    }
}
