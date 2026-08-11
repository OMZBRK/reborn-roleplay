package fr.reborn.hud.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Catalogue des assets d'apparence (cheveux, tenues, yeux, pilosité, tatouages),
 * chargé une fois depuis {@code /assets/reborn/textures/character/catalog.json}
 * (bundlé dans le jar). C'est la <b>source de vérité data-driven</b> : ajouter un
 * cosmétique = déposer son PNG + une entrée JSON, sans toucher au code.
 *
 * <p>Chaque {@link Asset} déclare son <b>nom FR</b>, sa <b>restriction</b> (genre /
 * clan, appliquée uniquement au choix à la création — le staff est exempté), son
 * <b>mode de teinte</b> ({@code none/all/red}) et d'éventuelles <b>zones de masque
 * RGBA</b> (recoloration par canal). Voir {@code catalog.json#_doc}.
 *
 * <p>Convention de nommage : préfixe = catégorie ({@code Cheveux_}, {@code Complet_},
 * {@code Yeux_}…), suffixe {@code _Mask} pour le masque compagnon. L'{@code id} d'un
 * asset EST le nom de fichier sans extension.
 */
public final class CharacterCatalog {

    private CharacterCatalog() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-catalog");
    private static final String PATH = "/assets/reborn/textures/character/catalog.json";

    /** Mode de teinte d'un asset. */
    public enum Tint { NONE, ALL, RED }

    /** Zone recolorable d'un masque RGBA : un canal → une couleur (défaut) réglable. */
    public record Zone(char channel, String name, int defaultColor) {}

    /** Un cosmétique. {@code folder} + {@code id} → chemin PNG ; {@code id}+{@code _Mask} → masque. */
    public static final class Asset {
        public final String folder;      // hair / outfit / eyes / facial / tattoo
        public final String id;          // = nom de fichier (sans .png)
        public final String name;        // libellé FR
        public final String gender;      // "male" / "female" / null (tous)
        public final String clan;        // nom de clan exact / null (tous)
        public final String slot;        // outfit : "complet" / "haut" / "bas" ; sinon null
        public final Tint tint;
        public final int split;          // tint RED : x de séparation gauche/droite
        public final List<Zone> zones;   // recoloration par masque (peut être vide)

        Asset(String folder, String id, String name, String gender, String clan,
              String slot, Tint tint, int split, List<Zone> zones) {
            this.folder = folder; this.id = id; this.name = name;
            this.gender = gender; this.clan = clan; this.slot = slot;
            this.tint = tint; this.split = split; this.zones = zones;
        }

        /** Chemin classpath du PNG de base. */
        public String texturePath() {
            return "/assets/reborn/textures/character/" + folder + "/" + id + ".png";
        }

        /** Chemin classpath du masque RGBA compagnon ({@code <id>_Mask.png}). */
        public String maskPath() {
            return "/assets/reborn/textures/character/" + folder + "/" + id + "_Mask.png";
        }

        /** Cet asset propose-t-il une couleur réglable (picker) ? */
        public boolean tintable() {
            return tint == Tint.ALL || tint == Tint.RED || !zones.isEmpty();
        }

        /** Éligible pour ce perso ? Le staff ({@code exempt}) ignore toute restriction. */
        public boolean allowedFor(boolean female, String clanName, boolean exempt) {
            if (exempt) return true;
            if (gender != null && !gender.equals(female ? "female" : "male")) return false;
            if (clan != null && (clanName == null || !clan.equalsIgnoreCase(clanName.trim()))) return false;
            return true;
        }
    }

    // Catégorie → assets (ordre du JSON préservé). Chargé paresseusement.
    private static volatile Map<String, List<Asset>> byCategory;
    // "folder:id" → asset (résolution O(1), toutes catégories confondues).
    private static volatile Map<String, Asset> index;

    private static void ensureLoaded() {
        if (byCategory != null) return;
        synchronized (CharacterCatalog.class) {
            if (byCategory != null) return;
            Map<String, List<Asset>> cats = new LinkedHashMap<>();
            Map<String, Asset> idx = new LinkedHashMap<>();
            try (InputStream in = CharacterCatalog.class.getResourceAsStream(PATH)) {
                if (in == null) {
                    LOGGER.warn("catalog.json introuvable ({})", PATH);
                } else {
                    JsonObject root = JsonParser.parseReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    for (String cat : new String[] { "hair", "outfit", "eyes", "facial", "tattoo" }) {
                        List<Asset> list = new ArrayList<>();
                        if (root.has(cat) && root.get(cat).isJsonArray()) {
                            for (JsonElement el : root.getAsJsonArray(cat)) {
                                if (!el.isJsonObject()) continue;
                                Asset a = parse(cat, el.getAsJsonObject());
                                if (a == null) continue;
                                list.add(a);
                                idx.put(cat + ":" + a.id, a);
                            }
                        }
                        cats.put(cat, list);
                        LOGGER.info("catalogue {} : {} asset(s)", cat, list.size());
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("lecture catalog.json échec : {}", e.getMessage());
            }
            index = idx;
            byCategory = cats;
        }
    }

    private static Asset parse(String cat, JsonObject o) {
        String id = str(o, "id", null);
        if (id == null || id.isBlank()) return null;
        Tint tint = switch (str(o, "tint", "none").toLowerCase(Locale.ROOT)) {
            case "all" -> Tint.ALL;
            case "red" -> Tint.RED;
            default -> Tint.NONE;
        };
        List<Zone> zones = new ArrayList<>();
        if (o.has("zones") && o.get("zones").isJsonArray()) {
            for (JsonElement z : o.getAsJsonArray("zones")) {
                if (!z.isJsonObject()) continue;
                JsonObject zo = z.getAsJsonObject();
                String ch = str(zo, "ch", "R").toUpperCase(Locale.ROOT);
                if (ch.isEmpty()) continue;
                zones.add(new Zone(ch.charAt(0), str(zo, "name", "Zone"),
                    hexToArgb(str(zo, "default", "FFFFFF"))));
            }
        }
        return new Asset(cat, id, str(o, "name", id),
            blankNull(str(o, "gender", null)), blankNull(str(o, "clan", null)),
            blankNull(str(o, "slot", null)), tint, intv(o, "split", 11), zones);
    }

    // ── API ────────────────────────────────────────────────────────────

    /** Tous les assets d'une catégorie (ordre du JSON). Jamais {@code null}. */
    public static List<Asset> all(String category) {
        ensureLoaded();
        return byCategory.getOrDefault(category, Collections.emptyList());
    }

    /** Assets d'une catégorie éligibles pour ce perso (genre/clan ; staff exempté). */
    public static List<Asset> available(String category, boolean female, String clan, boolean exempt) {
        List<Asset> out = new ArrayList<>();
        for (Asset a : all(category)) {
            if (a.allowedFor(female, clan, exempt)) out.add(a);
        }
        return out;
    }

    /** Résout {@code (catégorie, id)} → asset, ou {@code null} (id vide = « aucun »). */
    public static Asset byId(String category, String id) {
        ensureLoaded();
        if (id == null || id.isBlank()) return null;
        return index.get(category + ":" + id);
    }

    // ── Helpers JSON ────────────────────────────────────────────────────
    private static String str(JsonObject o, String k, String def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
    }

    private static int intv(JsonObject o, String k, int def) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : def; }
        catch (Exception e) { return def; }
    }

    private static String blankNull(String s) { return (s == null || s.isBlank()) ? null : s; }

    private static int hexToArgb(String hex) {
        try { return 0xFF000000 | (Integer.parseInt(hex.trim(), 16) & 0xFFFFFF); }
        catch (Exception e) { return 0xFFFFFFFF; }
    }
}
