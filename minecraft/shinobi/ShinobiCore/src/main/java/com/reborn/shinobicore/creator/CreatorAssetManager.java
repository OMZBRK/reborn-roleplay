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

    /** Catalog d'exemple semé au premier démarrage : {@code _doc}/{@code _exemple}
     *  sont ignorés par le parseur (pas des catégories) ; les vraies catégories
     *  commencent vides → aucun « PNG manquant » parasite. */
    private static final String TEMPLATE_CATALOG = """
            {
              "_doc": [
                "Assets du Character Creator diffuses en LIVE aux joueurs.",
                "Ajouter un asset = 1) deposer le PNG dans <categorie>/<id>.png",
                "                    2) ajouter une entree {id,...} dans la categorie ci-dessous",
                "                    3) recharger (bouton panel 'Character creator' ou /creator reload).",
                "L'id EST le nom du fichier PNG sans .png. Texture 64x64 (layout skin standard),",
                "transparent hors de la zone, <= 512 Ko. Masque RGBA optionnel : <id>_Mask.png.",
                "Categories : hair, outfit, eyes, facial, tattoo, accessory, underwear.",
                "Champs : id (obligatoire), name (libelle FR), gender (male|female|null),",
                "  clan (nom exact|null), tint (none|all|red), split (x separation pour tint red),",
                "  slot (outfit: complet|haut|bas), zones [{ch:R|G|B|A, name, default:'RRGGBB'}].",
                "Voir README.txt pour le detail. Aucun restart requis : le reload suffit."
              ],
              "_exemple": {
                "hair":   [ { "id": "Cheveux_Exemple", "name": "Cheveux exemple", "tint": "all" } ],
                "outfit": [ { "id": "Complet_Exemple", "name": "Tenue exemple", "slot": "complet",
                              "zones": [ { "ch": "R", "name": "Principale", "default": "3A5FCD" } ] } ],
                "eyes":   [ { "id": "Yeux_Exemple", "name": "Yeux exemple", "tint": "red", "split": 11 } ]
              },
              "hair": [],
              "outfit": [],
              "eyes": [],
              "facial": [],
              "tattoo": [],
              "accessory": [],
              "underwear": []
            }
            """;

    private static final String TEMPLATE_README = """
            CHARACTER CREATOR — ASSETS EN DIFFUSION LIVE
            ============================================

            Ce dossier (plugins/ShinobiCore/creator-assets/) contient les cosmetiques du
            createur de personnage. Ils sont pousses aux clients EN DIRECT : aucun
            redemarrage du serveur n'est necessaire, un simple RELOAD suffit.

            AJOUTER UN ASSET (3 etapes)
            ---------------------------
            1) Depose le PNG dans le dossier de sa categorie, nomme <id>.png
               (ex. outfit/Complet_Akatsuki.png). L'id = le nom du fichier SANS .png.
               - Texture 64x64, layout skin Minecraft standard, transparent hors zone.
               - Taille <= 512 Ko.
               - Masque de teinte optionnel : <id>_Mask.png (RGBA, meme dossier).
            2) Ajoute une entree dans catalog.json, dans le tableau de la bonne categorie :
                 "outfit": [
                   { "id": "Complet_Akatsuki", "name": "Manteau Akatsuki", "slot": "complet" }
                 ]
            3) Recharge : bouton "Character creator (assets live)" dans le panel Fichiers,
               ou commande /creator reload en jeu. Les assets apparaissent aussitot dans
               le createur pour TOUS les joueurs connectes.

            CATEGORIES : hair, outfit, eyes, facial, tattoo, accessory, underwear.

            CHAMPS DU CATALOG
            -----------------
              id        (obligatoire) nom du fichier PNG sans extension.
              name      libelle FR affiche dans le createur.
              gender    "male" | "female" | absent (= tous).
              clan      nom exact du clan pour reserver l'asset, ou absent (= tous).
              tint      "none" (aucune teinte) | "all" (teinte globale) | "red" (zones).
              split     (tint red) colonne x de separation gauche/droite.
              slot      (outfit) "complet" | "haut" | "bas".
              zones     liste de zones teintables : [{ "ch":"R|G|B|A", "name":"...",
                        "default":"RRGGBB" }].

            NOTE : les blocs "_doc" et "_exemple" de catalog.json sont ignores par le
            serveur (ce ne sont pas des categories) — ils servent d'aide-memoire. Copie
            une entree de "_exemple" vers la vraie categorie pour demarrer.
            """;

    public CreatorAssetManager(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    /** Bilan d'un {@link #reload()} — remonté au staff (commande + panel) pour lever
     *  l'ambiguïté « 0 chargé = ça marche pas → je restart ». */
    public record ReloadResult(int loaded, int skipped, List<String> errors) {}

    /** Enregistre le canal (in/out) + events, sème le template si absent, puis charge. */
    public void start() {
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL_PACK)) m.registerOutgoingPluginChannel(plugin, CHANNEL_PACK);
        if (!m.isIncomingChannelRegistered(plugin, CHANNEL_PACK)) m.registerIncomingPluginChannel(plugin, CHANNEL_PACK, this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        seedTemplateIfAbsent();
        reload();
    }

    /**
     * Écrit un {@code catalog.json} d'exemple + un {@code README.txt} dans
     * {@code creator-assets/} si aucun catalog n'existe encore — pour que le format
     * soit ÉVIDENT côté staff (le dossier était créé vide = source de confusion).
     * N'écrase jamais un catalog existant.
     */
    private void seedTemplateIfAbsent() {
        File dir = new File(plugin.getDataFolder(), "creator-assets");
        if (!dir.exists()) dir.mkdirs();
        File catalog = new File(dir, "catalog.json");
        if (catalog.exists()) return;
        try {
            Files.writeString(catalog.toPath(), TEMPLATE_CATALOG, StandardCharsets.UTF_8);
            Files.writeString(new File(dir, "README.txt").toPath(), TEMPLATE_README, StandardCharsets.UTF_8);
            for (String cat : CATEGORIES) new File(dir, cat).mkdirs(); // dossiers par catégorie
            plugin.getLogger().info("Creator assets : template catalog.json + README.txt semés dans "
                    + dir.getPath() + ".");
        } catch (IOException e) {
            plugin.getLogger().warning("Creator assets : échec écriture du template (" + e.getMessage() + ").");
        }
    }

    /** (Re)charge {@code creator-assets/catalog.json} + PNG puis re-pousse aux joueurs.
     *  Retourne le bilan (chargés / ignorés / erreurs) pour retour staff. */
    public ReloadResult reload() {
        assets.clear();
        List<String> errors = new ArrayList<>();
        File dir = new File(plugin.getDataFolder(), "creator-assets");
        if (!dir.exists()) dir.mkdirs();
        File catalog = new File(dir, "catalog.json");
        if (!catalog.exists()) {
            plugin.getLogger().info("Creator assets : aucun catalog.json (dossier " + dir.getPath() + ").");
            return new ReloadResult(0, 0, List.of("Aucun catalog.json dans " + dir.getPath()));
        }
        JsonObject root;
        try (InputStreamReader r = new InputStreamReader(Files.newInputStream(catalog.toPath()), StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(r).getAsJsonObject();
        } catch (Exception e) {
            plugin.getLogger().warning("Creator assets : catalog.json illisible (" + e.getMessage() + ").");
            return new ReloadResult(0, 0, List.of("catalog.json illisible : " + e.getMessage()));
        }

        int loaded = 0, skipped = 0;
        for (String cat : CATEGORIES) {
            if (!root.has(cat) || !root.get(cat).isJsonArray()) continue;
            JsonArray arr = root.getAsJsonArray(cat);
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String id = o.has("id") && !o.get("id").isJsonNull() ? o.get("id").getAsString() : null;
                if (id == null || id.isBlank()) {
                    errors.add(cat + " : entrée sans « id »");
                    skipped++;
                    continue;
                }
                File png = new File(dir, cat + "/" + id + ".png");
                if (!png.isFile()) {
                    String msg = cat + "/" + id + " : PNG manquant (attendu " + cat + "/" + id + ".png)";
                    plugin.getLogger().warning("Creator assets : " + msg);
                    errors.add(msg);
                    skipped++;
                    continue;
                }
                if (png.length() > MAX_PNG_BYTES) {
                    String msg = cat + "/" + id + " : PNG > " + (MAX_PNG_BYTES / 1024) + " Ko (ignoré)";
                    plugin.getLogger().warning("Creator assets : " + msg);
                    errors.add(msg);
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
                    String msg = cat + "/" + id + " : lecture échouée (" + ex.getMessage() + ")";
                    plugin.getLogger().warning("Creator assets : " + msg);
                    errors.add(msg);
                    skipped++;
                }
            }
        }
        plugin.getLogger().info("Creator assets : " + loaded + " chargé(s), " + skipped + " ignoré(s).");
        int pushed = 0;
        for (Player p : Bukkit.getOnlinePlayers()) { sendPack(p); pushed++; }
        plugin.getLogger().info("Creator assets : re-poussé à " + pushed + " joueur(s) connecté(s).");
        return new ReloadResult(loaded, skipped, errors);
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
