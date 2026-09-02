package fr.reborn.hud.menu.tablist;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dernier snapshot du tablist reçu du serveur (canal {@code reborn:tablist}).
 * Alimenté par {@link TablistPayload} ; lu par {@link TablistScreen}. Tant que
 * rien n'est reçu (hors serveur Shinobi, ou en solo), on retombe sur
 * {@link MockTablist} pour garder un rendu correct.
 *
 * <p>Le serveur envoie les rangs/affinités en clé enum (ex. {@code GENIN}) ;
 * on les mappe en libellés FR côté client (seul endroit qui connaît la langue
 * du menu). Le ping n'est pas transmis — on le lit en direct depuis la latence
 * du {@code PlayerInfo} vanilla par UUID.
 */
public final class TablistData {

    private TablistData() {}

    private static volatile List<TabEntry> entries = null;
    private static volatile String rpDate = null;

    /** Vitals RP du viewer poussés par le serveur (bloc {@code "self"} du feed). */
    public record SelfVitals(int hp, int maxHp, int chakra, int maxChakra) {}
    private static volatile SelfVitals selfVitals = null;

    /** Identité RP d'un joueur du roster, adressable par UUID (pour les plaques de
     *  nom au-dessus des têtes). {@code name} est déjà résolu serveur selon la
     *  relation ({@code relation} sert à afficher « Inconnu » côté client). */
    public record RpName(String name, TabEntry.Relation relation, int clanColor) {}
    /** UUID → identité RP, reconstruit à chaque feed. Vide tant que rien reçu. */
    private static volatile Map<UUID, RpName> byUuid = Map.of();

    /** Identité RP d'un joueur par UUID (ou {@code null} si absent du dernier roster). */
    public static RpName rpNameFor(UUID id) { return byUuid.get(id); }

    public static boolean hasData() { return entries != null; }

    /** Vie/chakra RP du joueur local (serveur-authoritative), ou {@code null}
     *  hors serveur / sans perso actif → l'appelant retombe sur les valeurs vanilla. */
    public static SelfVitals selfVitals() { return selfVitals; }

    /** Entrées serveur si dispo, sinon mock (pour ne jamais afficher un tab vide). */
    public static List<TabEntry> entries(String selfName) {
        List<TabEntry> e = entries;
        return e != null ? e : MockTablist.build(selfName);
    }

    public static String rpDate() {
        String d = rpDate;
        return d != null ? d : MockTablist.rpDate();
    }

    public static void clear() { entries = null; rpDate = null; selfVitals = null; byUuid = Map.of(); }

    /** Parse le JSON du canal et publie le nouveau snapshot (thread client). */
    public static void update(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String date = root.has("date") && !root.get("date").isJsonNull()
                ? root.get("date").getAsString() : null;

            List<TabEntry> list = new ArrayList<>();
            Map<UUID, RpName> ids = new HashMap<>();
            JsonArray players = root.getAsJsonArray("players");
            if (players != null) {
                for (JsonElement el : players) {
                    if (!el.isJsonObject()) continue;
                    JsonObject o = el.getAsJsonObject();

                    TabEntry.Relation relation = switch (opt(o, "r", "INCONNU")) {
                        case "SOI" -> TabEntry.Relation.SOI;
                        case "AMI" -> TabEntry.Relation.AMI;
                        case "CONNAISSANCE" -> TabEntry.Relation.CONNAISSANCE;
                        default -> TabEntry.Relation.INCONNU;
                    };
                    boolean staff = o.has("s") && !o.get("s").isJsonNull() && o.get("s").getAsBoolean();
                    String name = opt(o, "n", "Inconnu (Pas RP)");
                    String grade = frRank(opt(o, "g", "???"));
                    String clan = o.has("c") && !o.get("c").isJsonNull() ? o.get("c").getAsString() : null;
                    int clanColor = optInt(o, "cc", 0);
                    int level = optInt(o, "lv", 0);
                    int age = optInt(o, "a", 0);
                    String affinity = frAffinity(opt(o, "af", ""));
                    String uuid = opt(o, "u", null);
                    int ping = pingOf(uuid);

                    list.add(new TabEntry(name, relation, staff, grade, clan, clanColor,
                        ping, level, age, affinity));
                    // Index par UUID pour les plaques de nom (relation → « Inconnu »).
                    if (uuid != null) {
                        try { ids.put(UUID.fromString(uuid), new RpName(name, relation, clanColor)); }
                        catch (IllegalArgumentException ignoredId) { /* uuid invalide */ }
                    }
                }
            }
            byUuid = ids;
            // Vitals RP du viewer (présent seulement si perso actif côté serveur).
            if (root.has("self") && root.get("self").isJsonObject()) {
                JsonObject sv = root.getAsJsonObject("self");
                selfVitals = new SelfVitals(
                    optInt(sv, "hp", 0), Math.max(1, optInt(sv, "mhp", 1)),
                    optInt(sv, "ck", 0), Math.max(1, optInt(sv, "mck", 1)));
            } else {
                selfVitals = null;
            }

            entries = list;
            rpDate = date;
        } catch (Exception ignored) {
            // JSON malformé : on garde le snapshot précédent.
        }
    }

    private static int pingOf(String uuidStr) {
        if (uuidStr == null) return 0;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == null) return 0;
            var entry = mc.getConnection().getPlayerInfo(UUID.fromString(uuidStr));
            return entry != null ? Math.max(0, entry.getLatency()) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String opt(JsonObject o, String k, String def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
    }

    private static int optInt(JsonObject o, String k, int def) {
        try {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : def;
        } catch (Exception e) { return def; }
    }

    /** Rang ShinobiCore (enum) → libellé FR. */
    private static String frRank(String key) {
        return switch (key) {
            case "ACADEMY" -> "Étudiant Académie";
            case "GENIN" -> "Genin";
            case "CHUNIN" -> "Chunin";
            case "SPECIAL_JONIN" -> "Jonin Spécial";
            case "JONIN" -> "Jonin";
            case "ANBU" -> "ANBU";
            case "SANNIN" -> "Sannin";
            case "KAGE" -> "Kage";
            default -> "???";
        };
    }

    /** Affinité ShinobiCore (enum) → libellé FR. */
    private static String frAffinity(String key) {
        return switch (key) {
            case "STRENGTH" -> "Force";
            case "INTELLIGENCE" -> "Intelligence";
            case "AGILITY" -> "Agilité";
            default -> "—";
        };
    }
}
