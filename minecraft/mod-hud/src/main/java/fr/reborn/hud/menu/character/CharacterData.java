package fr.reborn.hud.menu.character;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot "mes personnages" alimenté par le serveur (ShinobiCore) sur le canal
 * {@code reborn:character}. Convention identique à {@code TablistData} :
 * l'écran appelle toujours {@link #characters()} et récupère des données mock en
 * solo/sans serveur automatiquement.
 *
 * <p>{@link #slotLimit()} = nombre de personnages autorisés selon le grade
 * (lambda 2 / premium 4 / staff 7), poussé par le serveur avec la liste.
 * Défaut prudent = 2 (joueur lambda).
 */
public final class CharacterData {

    private CharacterData() {}

    private static volatile List<CharacterCard> characters = null;
    private static volatile int slotLimit = 2;

    // Candidature whitelist (poussée par le serveur avec le roster) : sert à
    // VERROUILLER village/clan dans le wizard de création et à pré-remplir le
    // prénom. Tout est nullable → si le serveur ne l'envoie pas encore, rien
    // n'est verrouillé (dégradation propre). {@code staffExempt} = le joueur
    // est staff/admin → aucun verrou (choix libre).
    private static volatile String candVillage = null;
    private static volatile String candClan = null;
    private static volatile String candName = null;
    private static volatile boolean staffExempt = false;

    public static List<CharacterCard> characters() {
        List<CharacterCard> c = characters;
        return c != null ? c : MockCharacters.build();
    }

    public static int slotLimit() { return slotLimit; }

    public static boolean hasData() { return characters != null; }

    public static String candidatureVillage() { return candVillage; }
    public static String candidatureClan() { return candClan; }
    public static String candidatureName() { return candName; }
    public static boolean staffExempt() { return staffExempt; }

    public static void update(List<CharacterCard> list, int limit) {
        characters = list;
        slotLimit = Math.max(1, limit);
    }

    /** Parse le JSON reçu du serveur sur le canal {@code reborn:character}. */
    public static void update(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            int limit = root.has("slotLimit") ? root.get("slotLimit").getAsInt() : 2;
            List<CharacterCard> list = new ArrayList<>();
            JsonArray arr = root.getAsJsonArray("characters");
            if (arr != null) {
                for (JsonElement el : arr) {
                    if (!el.isJsonObject()) continue;
                    JsonObject o = el.getAsJsonObject();
                    list.add(new CharacterCard(
                        str(o, "id", ""),
                        str(o, "name", "?"),
                        str(o, "clan", ""),
                        intv(o, "clanColor", 0),
                        str(o, "village", ""),
                        str(o, "rank", ""),
                        intv(o, "level", 0),
                        o.has("dead") && !o.get("dead").isJsonNull() && o.get("dead").getAsBoolean(),
                        str(o, "appearance", "")));
                }
            }
            // Candidature (optionnelle) : { village, clan, name, staff }.
            candVillage = candClan = candName = null;
            staffExempt = false;
            if (root.has("candidature") && root.get("candidature").isJsonObject()) {
                JsonObject c = root.getAsJsonObject("candidature");
                candVillage = blankToNull(str(c, "village", ""));
                candClan = blankToNull(str(c, "clan", ""));
                candName = blankToNull(str(c, "name", ""));
                staffExempt = c.has("staff") && !c.get("staff").isJsonNull() && c.get("staff").getAsBoolean();
            }
            update(list, limit);
        } catch (Exception ignored) {
            // JSON malformé : on garde le snapshot précédent.
        }
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static int intv(JsonObject o, String k, int def) {
        try {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : def;
        } catch (Exception e) { return def; }
    }

    public static void clear() {
        characters = null;
        slotLimit = 2;
        candVillage = candClan = candName = null;
        staffExempt = false;
    }
}
