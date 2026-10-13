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
                           Map<CosmeticSlot, SlotItem> equipped,
                           SlotItem[] hotbar, String lecture, int palier, double poidsMax,
                           boolean armeDegainee, boolean ouvrir) {

        /** Forme courte (bancs, démo solo) : barre lue chez le joueur, pas de verrou, palier 0, plafond 130 %. */
        public Snapshot(String bagTier, int baseSlots, int extraSlots, double maxWeight, double curWeight,
                        SlotItem bagItem, SlotItem[] bag, Map<CosmeticSlot, SlotItem> equipped) {
            this(bagTier, baseSlots, extraSlots, maxWeight, curWeight, bagItem, bag, equipped, null, null, 0, 1.3,
                false, true);
        }

        /** Copie avec un autre poids / verrou (bancs d'essai). */
        public Snapshot avec(double max, double cur, String lecture, int palier) {
            return new Snapshot(bagTier, baseSlots, extraSlots, max, cur, bagItem, bag, equipped, hotbar, lecture, palier,
                poidsMax, armeDegainee, ouvrir);
        }

        /** Métadonnées d'une case de la barre (rareté…), si le serveur les a envoyées. */
        public SlotItem hotbarMeta(int i) {
            return hotbar != null && i >= 0 && i < hotbar.length ? hotbar[i] : null;
        }
    }

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

            SlotItem[] hotbar = null;
            if (root.has("hotbar") && root.get("hotbar").isJsonArray()) {
                JsonArray h = root.getAsJsonArray("hotbar");
                hotbar = new SlotItem[9];
                for (int i = 0; i < h.size() && i < 9; i++) {
                    JsonElement el = h.get(i);
                    hotbar[i] = el.isJsonObject() ? parseItem(el.getAsJsonObject()) : null;
                }
            }
            // « ouvrir » absent = ancien serveur, qui n'envoyait la sacoche que pour l'ouvrir.
            boolean ouvrir = !root.has("ouvrir") || root.get("ouvrir").getAsBoolean();
            snapshot = new Snapshot(bagTier, baseSlots, extraSlots, maxWeight, curWeight, bagItem, bag, equipped,
                hotbar, str(root, "lecture", null), intv(root, "palier", 0), dbl(root, "poidsMax", 1.3),
                root.has("armeDegainee") && root.get("armeDegainee").getAsBoolean(), ouvrir);
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
            SlotItem si = new SlotItem(mat, model, count, name, weight, desc, rarity, actionId, actionLabel);
            si.arme = o.has("arme") && o.get("arme").getAsBoolean();
            si.unique = o.has("unique") && o.get("unique").getAsBoolean();
            return si;
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
