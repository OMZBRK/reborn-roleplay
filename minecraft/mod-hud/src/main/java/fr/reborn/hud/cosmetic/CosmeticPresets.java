package fr.reborn.hud.cosmetic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Presets nommés de {@link CosmeticTransform}, persistés en JSON dans
 * {@code <config_dir>/reborn-cosmetics.json}. Sert l'éditeur visuel
 * ({@code CosmeticEditorScreen}) : sauvegarder / charger / supprimer une position.
 *
 * <p>Structuré <b>par identifiant de cosmétique</b> pour généraliser au-delà du
 * katana : le fichier est un objet {@code { "<cosmeticId>": { "<nom>": {transform} } }}.
 * L'API publique cible pour l'instant le seul id {@link #KATANA}. Les transforms
 * sont stockés en copie (l'éditeur muter l'instance live sans altérer les presets).
 *
 * <p>Chargement paresseux via {@link #ensureLoaded()} (appelé au boot par
 * {@code RebornHudClient} et défensivement par chaque accès). Aucune dépendance
 * ajoutée : Gson est déjà sur le classpath (utilisé par {@code RebornPrefs} & co).
 */
public final class CosmeticPresets {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/cosmetic-presets");

    /**
     * Nom réservé stockant le placement ACTIF (bouton « Appliquer » du menu de
     * repositionnement) — masqué de {@link #list(String)}. Persisté comme un preset
     * normal pour réutiliser toute l'I/O, mais rechargé à la demande dans le
     * transform live via {@link #loadApplied(String)}.
     */
    private static final String APPLIED = "@applied";

    private static final String FILENAME = "reborn-cosmetics.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE =
        new TypeToken<Map<String, Map<String, CosmeticTransform>>>() {}.getType();

    /** cosmeticId → (nom du preset → transform). Ordre d'insertion préservé. */
    private static final Map<String, Map<String, CosmeticTransform>> DATA = new LinkedHashMap<>();
    private static boolean loaded = false;

    private CosmeticPresets() {}

    // ─────────────────── API (par cosmeticId) ───────────────────

    private static String key(String id) {
        return (id == null || id.isBlank()) ? "cosmetic" : id.trim();
    }

    /** Noms des presets enregistrés pour {@code id}, dans l'ordre d'insertion (hors placement actif). */
    public static List<String> list(String id) {
        ensureLoaded();
        Map<String, CosmeticTransform> m = DATA.get(key(id));
        if (m == null) return new ArrayList<>();
        List<String> out = new ArrayList<>(m.keySet());
        out.remove(APPLIED);
        return out;
    }

    /** Persiste le placement ACTIF de {@code id} (bouton « Appliquer »). */
    public static void saveApplied(String id, CosmeticTransform transform) {
        if (transform == null) return;
        ensureLoaded();
        DATA.computeIfAbsent(key(id), k -> new LinkedHashMap<>()).put(APPLIED, transform.copy());
        persist();
    }

    /** Charge le placement ACTIF persisté de {@code id}, ou {@code null} s'il n'y en a pas. */
    public static CosmeticTransform loadApplied(String id) {
        return load(id, APPLIED);
    }

    /** Enregistre (ou écrase) un preset nommé pour {@code id}, puis persiste sur disque. */
    public static void save(String id, String name, CosmeticTransform transform) {
        if (name == null || name.isBlank() || transform == null) return;
        ensureLoaded();
        DATA.computeIfAbsent(key(id), k -> new LinkedHashMap<>()).put(name.trim(), transform.copy());
        persist();
    }

    /** Charge une copie du preset nommé de {@code id}, ou {@code null} s'il n'existe pas. */
    public static CosmeticTransform load(String id, String name) {
        if (name == null) return null;
        ensureLoaded();
        Map<String, CosmeticTransform> m = DATA.get(key(id));
        if (m == null) return null;
        CosmeticTransform t = m.get(name);
        return t != null ? t.copy() : null;
    }

    /** Supprime un preset nommé de {@code id} (no-op s'il est absent), puis persiste. */
    public static void delete(String id, String name) {
        if (name == null) return;
        ensureLoaded();
        Map<String, CosmeticTransform> m = DATA.get(key(id));
        if (m != null && m.remove(name) != null) persist();
    }

    // ─────────────────── I/O ───────────────────

    /** Charge le fichier une seule fois (idempotent). Appelé au boot + à chaque accès. */
    public static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path path = configPath();
        if (path == null || !Files.exists(path)) return;
        try {
            String json = Files.readString(path);
            Map<String, Map<String, CosmeticTransform>> parsed = GSON.fromJson(json, TYPE);
            if (parsed != null) {
                DATA.clear();
                DATA.putAll(parsed);
                LOG.info("loaded cosmetic presets from {}", path);
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("failed to load cosmetic presets: {}", e.getMessage());
        }
    }

    private static void persist() {
        Path path = configPath();
        if (path == null) return;
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(DATA, TYPE));
            LOG.debug("saved cosmetic presets to {}", path);
        } catch (IOException e) {
            LOG.warn("failed to save cosmetic presets: {}", e.getMessage());
        }
    }

    private static Path configPath() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve(FILENAME);
        } catch (Exception e) {
            return null;
        }
    }
}
