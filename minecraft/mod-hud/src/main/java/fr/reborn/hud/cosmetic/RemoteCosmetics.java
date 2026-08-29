package fr.reborn.hud.cosmetic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.reborn.hud.menu.inventory.CosmeticSlot;
import fr.reborn.hud.menu.inventory.SlotItem;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cosmétiques équipés des <b>autres joueurs</b>, reçus sur le canal
 * {@code reborn:cosmetics} et indexés par UUID. Le renderer
 * ({@link CosmeticFeatureRenderer}) y résout les cosmétiques d'un avatar distant
 * (le joueur local, lui, garde sa source autoritaire locale
 * {@code InventoryData} pour l'aperçu live de l'éditeur — avec repli sur ce store
 * tant que le sac n'a pas encore été poussé).
 *
 * <p>Analogue de {@code RebornSkins.overrides} (skins RP) — même cycle de vie :
 * peuplé par les broadcasts serveur, vidé à la déconnexion. Chaque entrée porte
 * l'item ET, si le porteur l'a appliqué, son {@link CosmeticTransform} (placement
 * 3D) pour que tous voient le MÊME positionnement.
 */
public final class RemoteCosmetics {

    /** Item équipé + placement appliqué (transform {@code null} = placement par défaut). */
    public record Remote(SlotItem item, CosmeticTransform transform) {}

    private static final Map<UUID, Map<CosmeticSlot, Remote>> REMOTE = new ConcurrentHashMap<>();

    private RemoteCosmetics() {}

    /** Cosmétiques équipés du joueur distant {@code id}, ou {@code null} si aucun. */
    public static Map<CosmeticSlot, Remote> get(UUID id) {
        return REMOTE.get(id);
    }

    public static void clearAll() {
        REMOTE.clear();
    }

    /**
     * Applique un broadcast {@code <uuid>\n<json>}. Corps vide ou map vide =
     * retrait des cosmétiques de ce joueur. Silencieux en cas de JSON invalide.
     */
    public static void apply(String content) {
        if (content == null) return;
        int nl = content.indexOf('\n');
        if (nl < 0) return;
        UUID id;
        try {
            id = UUID.fromString(content.substring(0, nl).trim());
        } catch (Exception e) {
            return;
        }
        String body = content.substring(nl + 1).trim();
        if (body.isEmpty()) {
            REMOTE.remove(id);
            return;
        }
        try {
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            Map<CosmeticSlot, Remote> map = new EnumMap<>(CosmeticSlot.class);
            for (CosmeticSlot cs : CosmeticSlot.values()) {
                if (!o.has(cs.name()) || !o.get(cs.name()).isJsonObject()) continue;
                JsonObject io = o.getAsJsonObject(cs.name());
                String mat = strOr(io, "mat", "minecraft:paper");
                String model = strOr(io, "model", null);
                String tf = strOr(io, "t", null);
                CosmeticTransform transform = tf != null ? CosmeticTransform.deserialize(tf) : null;
                map.put(cs, new Remote(new SlotItem(mat, model, 1, null, 0.0), transform));
            }
            if (map.isEmpty()) REMOTE.remove(id);
            else REMOTE.put(id, map);
        } catch (Exception ignored) {
            // garde l'état précédent
        }
    }

    private static String strOr(JsonObject o, String k, String def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
    }
}
