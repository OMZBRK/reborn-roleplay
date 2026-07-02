package fr.reborn.hud.menu.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * État global des préférences UI Reborn — persisté en JSON dans
 * {@code <config_dir>/reborn_prefs.json}.
 *
 * <p>Pas de connection au gameplay (options.txt) pour la PR #3 : on
 * persiste seulement entre sessions du launcher. Quand le câblage
 * options.txt sera nécessaire (PR ultérieure), on ajoute un sync
 * dans {@link #save()}.
 *
 * <p>Le lien "Options avancées Minecraft" du VideoTab ouvre l'écran
 * options vanilla pour les vraies prefs gameplay (FPS, VSync, etc.).
 *
 * <p>Singleton chargé une fois au boot du title screen.
 */
public final class RebornPrefs {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/prefs");

    public static final RebornPrefs INSTANCE = new RebornPrefs();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILENAME = "reborn_prefs.json";

    // ────────────────── Video ──────────────────
    public String resolution = "fhd";     // hd | fhd | qhd | 4k
    public String windowMode = "fullscreen"; // fullscreen | borderless | windowed
    public int fpsMax = 144;              // 30..240
    public int renderDistance = 16;       // 4..32 chunks
    public boolean vsync = true;

    // ────────────────── Audio ──────────────────
    public int volumeMaster = 80;
    public int volumeMusic = 62;
    public int volumeSfx = 75;
    public int volumeVoice = 90;
    public boolean muteOnUnfocus = true;

    // ────────────────── Discord ────────────────
    public boolean discordEnabled = true;
    public boolean discordShowCharacter = true;
    public boolean discordShowMap = false;

    // ────────────────── Viseur (crosshair) ──────
    public boolean crosshairEnabled = false; // false = crosshair vanilla
    public int crosshairPreset = 0;          // index 0..(N-1)
    public int crosshairScale = 100;         // % (50..200), 100 = 1.0
    public int crosshairColor = 0xFFFFFFFF;  // ARGB, blanc par défaut
    public boolean crosshairRainbow = false;
    public boolean crosshairDynamic = false;   // réagit au cooldown d'attaque
    public boolean crosshairHitMarker = true;  // croix au moment du hit
    // Style : "preset" (PNG) | "cross" | "dot" | "circle" (procéduraux).
    public String crosshairStyle = "preset";
    public int crosshairGap = 4;        // px depuis le centre (procédural)
    public int crosshairLength = 6;     // longueur des branches / rayon (procédural)
    public int crosshairThickness = 2;  // épaisseur (procédural)

    // ────────────────── Caméra épaule (3e pers. RP) ──────
    public double camDistance = 3.4;    // blocs (2.0..6.5)
    public double camRight = 0.50;      // décalage latéral magnitude (0..1.2)
    public double camUp = 0.15;         // décalage vertical (-0.6..1.2)
    public int camSide = 1;             // +1 épaule droite, -1 gauche
    public double camTurnSpeed = 0.5;   // vitesse de rotation du corps (0.1..1.0)
    public int camPreset = 0;           // index CameraPreset

    // ────────────────── Animations de mouvement (GTA-RP) ──────
    public int walkStyle = 0;           // index du style de marche choisi

    private boolean loaded = false;

    private RebornPrefs() {}

    /** Lazy load — appelé automatiquement au premier accès depuis l'UI. */
    public void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path path = configPath();
        if (path == null) return;
        if (!Files.exists(path)) {
            LOG.info("prefs file not found ({}), using defaults", path);
            return;
        }
        try {
            String json = Files.readString(path);
            RebornPrefs loaded = GSON.fromJson(json, RebornPrefs.class);
            if (loaded != null) {
                copyFrom(loaded);
                LOG.info("loaded prefs from {}", path);
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("failed to load prefs: {}", e.getMessage());
        }
    }

    /** Sauvegarde immédiate sur disque. */
    public void save() {
        Path path = configPath();
        if (path == null) return;
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this));
            LOG.debug("saved prefs to {}", path);
        } catch (IOException e) {
            LOG.warn("failed to save prefs: {}", e.getMessage());
        }
    }

    private Path configPath() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve(FILENAME);
        } catch (Exception e) {
            return null;
        }
    }

    private void copyFrom(RebornPrefs other) {
        this.resolution = other.resolution != null ? other.resolution : this.resolution;
        this.windowMode = other.windowMode != null ? other.windowMode : this.windowMode;
        this.fpsMax = other.fpsMax;
        this.renderDistance = other.renderDistance;
        this.vsync = other.vsync;
        this.volumeMaster = other.volumeMaster;
        this.volumeMusic = other.volumeMusic;
        this.volumeSfx = other.volumeSfx;
        this.volumeVoice = other.volumeVoice;
        this.muteOnUnfocus = other.muteOnUnfocus;
        this.discordEnabled = other.discordEnabled;
        this.discordShowCharacter = other.discordShowCharacter;
        this.discordShowMap = other.discordShowMap;
        this.crosshairEnabled = other.crosshairEnabled;
        this.crosshairPreset = other.crosshairPreset;
        this.crosshairScale = other.crosshairScale;
        this.crosshairColor = other.crosshairColor;
        this.crosshairRainbow = other.crosshairRainbow;
        this.crosshairDynamic = other.crosshairDynamic;
        this.crosshairHitMarker = other.crosshairHitMarker;
        this.crosshairStyle = other.crosshairStyle != null ? other.crosshairStyle : this.crosshairStyle;
        this.crosshairGap = other.crosshairGap;
        this.crosshairLength = other.crosshairLength;
        this.crosshairThickness = other.crosshairThickness;
        this.camDistance = other.camDistance;
        this.camRight = other.camRight;
        this.camUp = other.camUp;
        this.camSide = other.camSide;
        this.camTurnSpeed = other.camTurnSpeed;
        this.camPreset = other.camPreset;
        this.walkStyle = other.walkStyle;
    }
}
