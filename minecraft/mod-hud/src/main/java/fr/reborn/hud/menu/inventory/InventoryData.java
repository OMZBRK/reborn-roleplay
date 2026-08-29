package fr.reborn.hud.menu.inventory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.EnumMap;
import java.util.Map;

/**
 * Snapshot volatile du sac du personnage <b>actif</b>, poussé par ShinobiCore
 * sur {@code reborn:inventory}. Modèle « base = hotbar vanilla » : les 9 cases de
 * base ne sont PAS ici (le client lit le vrai hotbar du joueur). Ce snapshot ne
 * porte que la capacité + le contenu du <b>sac</b> (espace débloqué), le sac
 * porté et les cosmétiques — en items reconstructibles ({@link SlotItem}).
 *
 * <p>Aucun serveur (test solo) → {@link MockInventory}.
 */
public final class InventoryData {

    private InventoryData() {}

    /** État immuable du sac. {@code extraSlots} = cases débloquées par le sac
     *  (0 sans sac) ; {@code bag[i]} = contenu du sac (null = vide) ;
     *  {@code bagItem} = l'objet-sac porté (ou null) ; {@code curWeight}/
     *  {@code maxWeight} = jauge de poids (hotbar + sac, calculée serveur). */
    public record Snapshot(String bagTier, int baseSlots, int extraSlots,
                           double maxWeight, double curWeight,
                           SlotItem bagItem, SlotItem[] bag,
                           Map<CosmeticSlot, SlotItem> equipped) {}

    private static volatile Snapshot snapshot = null;

    public static Snapshot get() {
        Snapshot s = snapshot;
        return s != null ? s : MockInventory.build();
    }

    public static boolean fromServer() {
        return snapshot != null;
    }

    public static void clear() {
        snapshot = null;
    }

    /** MAJ optimiste locale (test solo / changement de sac). */
    public static void setLocal(Snapshot s) {
        if (s != null) snapshot = s;
    }

    /** Parse le JSON serveur → snapshot. Silencieux en cas d'erreur (garde l'ancien). */
    public static void update(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String bagTier = str(root, "bagTier", "NONE");
            int baseSlots = intv(root, "baseSlots", 9);
            int extraSlots = intv(root, "extraSlots", 0);
            double maxWeight = dbl(root, "maxWeight", 12.0);
            double curWeight = dbl(root, "curWeight", 0.0);

            SlotItem bagItem = parseItem(getObj(root, "bagItem"));

            SlotItem[] bag = new SlotItem[Math.max(0, extraSlots)];
            JsonArray arr = root.has("bag") && root.get("bag").isJsonArray() ? root.getAsJsonArray("bag") : null;
            if (arr != null) {
                for (int i = 0; i < arr.size() && i < bag.length; i++) {
                    JsonElement el = arr.get(i);
                    bag[i] = el.isJsonObject() ? parseItem(el.getAsJsonObject()) : null;
                }
            }

            Map<CosmeticSlot, SlotItem> equipped = new EnumMap<>(CosmeticSlot.class);
            if (root.has("equipped") && root.get("equipped").isJsonObject()) {
                JsonObject eq = root.getAsJsonObject("equipped");
                for (CosmeticSlot cs : CosmeticSlot.values()) {
                    SlotItem it = parseItem(getObj(eq, cs.name()));
                    if (it != null) equipped.put(cs, it);
                }
            }

            snapshot = new Snapshot(bagTier, baseSlots, extraSlots, maxWeight, curWeight, bagItem, bag, equipped);
        } catch (Exception ignored) {
            // garde le snapshot précédent
        }
    }

    private static SlotItem parseItem(JsonObject o) {
        if (o == null) return null;
        try {
            String mat = str(o, "mat", "minecraft:paper");
            String model = str(o, "model", null);
            int count = Math.max(1, intv(o, "count", 1));
            String name = str(o, "name", null);
            double weight = dbl(o, "weight", 0.0);
            String desc = str(o, "desc", null);
            String rarity = str(o, "rarity", null);
            String actionId = null, actionLabel = null;
            if (o.has("action") && o.get("action").isJsonObject()) {
                JsonObject a = o.getAsJsonObject("action");
                actionId = str(a, "id", null);
                actionLabel = str(a, "label", null);
            }
            return new SlotItem(mat, model, count, name, weight, desc, rarity, actionId, actionLabel);
        } catch (Exception e) {
            return null;
        }
    }

    private static JsonObject getObj(JsonObject o, String k) {
        return (o != null && o.has(k) && o.get(k).isJsonObject()) ? o.getAsJsonObject(k) : null;
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
    }

    private static int intv(JsonObject o, String k, int def) {
        try {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : def;
        } catch (Exception e) {
            return def;
        }
    }

    private static double dbl(JsonObject o, String k, double def) {
        try {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : def;
        } catch (Exception e) {
            return def;
        }
    }
}
