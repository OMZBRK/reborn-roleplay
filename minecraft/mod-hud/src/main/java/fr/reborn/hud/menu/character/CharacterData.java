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

    // Candidature whitelist (poussée par le serveur avec le roster, lot J §4.1-4.2). {@code premier} = création du
    // premier personnage issu de la candidature : village et âge VERROUILLÉS, prénom et nom de famille PRÉ-REMPLIS
    // mais modifiables, sexe libre (simple suggestion). Tout est nullable → si le serveur ne l'envoie pas encore, rien
    // n'est pré-rempli (dégradation propre). {@code staffExempt} = le joueur est staff/admin → aucun verrou, clans de
    // la liste ouverts (hors staff, les clans se rejoignent en session : « Sans clan » seulement).
    private static volatile String candVillage = null;
    private static volatile String candClan = null;
    private static volatile String candName = null;
    private static volatile String candFamille = null;
    private static volatile Integer candAge = null;
    private static volatile String candSexe = null;
    private static volatile String candType = null;
    private static volatile boolean candPremier = false;
    private static volatile boolean staffExempt = false;
    /** Change à chaque bloc candidature différent : l'écran de création relit alors ses verrous. */
    private static volatile int generation = 0;

    public static List<CharacterCard> characters() {
        List<CharacterCard> c = characters;
        return c != null ? c : MockCharacters.build();
    }

    public static int slotLimit() { return slotLimit; }

    public static boolean hasData() { return characters != null; }

    public static String candidatureVillage() { return candVillage; }
    public static String candidatureClan() { return candClan; }
    public static String candidatureName() { return candName; }
    /** Nom de famille libre de la candidature (clan « Autre », « Sans clan »), ou null. */
    public static String candidatureFamille() { return candFamille; }
    /** Âge du personnage fixé par la candidature, ou null. */
    public static Integer candidatureAge() { return candAge; }
    /** Sexe déclaré dans la candidature (« Homme » / « Femme »), simple suggestion, ou null. */
    public static String candidatureSexe() { return candSexe; }
    /** {@code NINJA} / {@code CIVIL}, ou null. */
    public static String candidatureType() { return candType; }
    /** Vrai pendant la création du premier personnage issu de la candidature (verrous du §4.2). */
    public static boolean candidaturePremier() { return candPremier && !staffExempt; }
    public static boolean staffExempt() { return staffExempt; }
    public static int generation() { return generation; }

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
                        str(o, "appearance", ""),
                        intv(o, "age", 18),
                        dbl(o, "size", 1.0),
                        str(o, "sexe", "Homme")));
                }
            }
            // Candidature (optionnelle) : { staff, premier, village, age, clan, name, familyName, sexe, type }.
            String avant = signature();
            candVillage = candClan = candName = candFamille = candSexe = candType = null;
            candAge = null;
            candPremier = staffExempt = false;
            if (root.has("candidature") && root.get("candidature").isJsonObject()) {
                JsonObject c = root.getAsJsonObject("candidature");
                candVillage = blankToNull(str(c, "village", ""));
                candClan = blankToNull(str(c, "clan", ""));
                candName = blankToNull(str(c, "name", ""));
                candFamille = blankToNull(str(c, "familyName", ""));
                candSexe = blankToNull(str(c, "sexe", ""));
                candType = blankToNull(str(c, "type", ""));
                int age = intv(c, "age", -1);
                candAge = age >= 0 ? age : null;
                // Serveur d'avant le lot J (pas de « premier ») : un village envoyé valait verrou.
                candPremier = c.has("premier") ? bool(c, "premier") : candVillage != null;
                staffExempt = bool(c, "staff");
            }
            if (!signature().equals(avant)) generation++;
            update(list, limit);
        } catch (Exception ignored) {
            // JSON malformé : on garde le snapshot précédent.
        }
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
    }

    /** Empreinte du bloc candidature, pour {@link #generation()}. */
    private static String signature() {
        return candVillage + "|" + candClan + "|" + candName + "|" + candFamille + "|" + candAge + "|" + candSexe
            + "|" + candType + "|" + candPremier + "|" + staffExempt;
    }

    private static boolean bool(JsonObject o, String k) {
        try { return o.has(k) && !o.get(k).isJsonNull() && o.get(k).getAsBoolean(); } catch (Exception e) { return false; }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static double dbl(JsonObject o, String k, double def) {
        try {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : def;
        } catch (Exception e) { return def; }
    }

    private static int intv(JsonObject o, String k, int def) {
        try {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : def;
        } catch (Exception e) { return def; }
    }

    public static void clear() {
        characters = null;
        slotLimit = 2;
        candVillage = candClan = candName = candFamille = candSexe = candType = null;
        candAge = null;
        candPremier = staffExempt = false;
        generation++;
    }
}
