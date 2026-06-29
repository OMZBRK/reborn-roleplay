package fr.reborn.hud.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import fr.reborn.hud.chat.ChatSettings;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementState;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Préférences utilisateur du mod HUD — persiste dans
 * {@code config/reborn-hud.json}.
 *
 * <p>Stocke un {@link HudElementState} par {@link HudElement} (position +
 * scale + visible). Les clés du JSON sont les {@link HudElement#id()}
 * stables (string keys) pour résister à un renommage interne de l'enum.
 */
public final class HudConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-hud/config");
    private static final String FILE_NAME = "reborn-hud.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Map<String, HudElementState> states = new HashMap<>();

    /**
     * Presets sauvegardés : preset-id → map element-id → state.
     * Initialisé via {@link HudPresets#defaults()} au premier boot.
     */
    private Map<String, Map<String, HudElementState>> presets = new LinkedHashMap<>();

    /** ID du preset actuellement actif (purement informatif côté UI). */
    private String activePreset = HudPresets.DEFAULT;

    /**
     * Toggle du rendu chat Reborn. false (défaut) = chat VANILLA normal :
     * position en bas, saisie attachée, scroll natif — ce que veut le RP.
     * Les features RP (blocage) sont greffées de façon additive (mixins) sans
     * casser ce layout. true = ancien rendu custom léger (positionnement
     * expérimental, peut décoller la saisie) — déconseillé.
     */
    private boolean enableCustomChat = false;

    /** Settings du chat (timestamps, mention highlights, etc.). Init lazy. */
    private ChatSettings chatSettings = null;

    /**
     * Curseur du menu d'interaction : 0 = flèche procédurale par défaut,
     * N≥1 = texture {@code reborn:textures/gui/cursorN.png}. Permet d'en
     * proposer plusieurs au goût des joueurs.
     */
    private int interactionCursor = 1;

    public int getInteractionCursor() { return interactionCursor; }

    public void setInteractionCursor(int n) {
        this.interactionCursor = Math.max(0, n);
    }

    public ChatSettings getChatSettings() {
        if (chatSettings == null) chatSettings = ChatSettings.defaults();
        return chatSettings;
    }

    public void setChatSettings(ChatSettings settings) {
        this.chatSettings = settings;
    }

    public boolean isEnableCustomChat() {
        return enableCustomChat;
    }

    public void setEnableCustomChat(boolean enable) {
        this.enableCustomChat = enable;
    }

    // ─── Presets ───

    public Map<String, Map<String, HudElementState>> getPresets() {
        if (presets == null) presets = new LinkedHashMap<>();
        return presets;
    }

    public String getActivePreset() { return activePreset; }
    public void setActivePreset(String id) { this.activePreset = id; }

    /**
     * Applique un preset : copie ses states sur les éléments courants
     * et marque le preset comme actif. Save inclus.
     */
    public void applyPreset(String id) {
        Map<String, HudElementState> p = getPresets().get(id);
        if (p == null) return;
        for (HudElement e : HudElement.values()) {
            HudElementState s = p.getOrDefault(e.id(), HudElementState.DEFAULT);
            setState(e, s);
        }
        this.activePreset = id;
        save();
    }

    /** Crée un nouveau preset à partir des states actuels. */
    public void saveAsNewPreset(String id) {
        Map<String, HudElementState> snapshot = new LinkedHashMap<>();
        for (HudElement e : HudElement.values()) {
            snapshot.put(e.id(), stateOf(e));
        }
        getPresets().put(id, snapshot);
        this.activePreset = id;
        save();
    }

    public void deletePreset(String id) {
        if (HudPresets.DEFAULT.equals(id)) return; // Default est protégé
        getPresets().remove(id);
        if (id.equals(activePreset)) activePreset = HudPresets.DEFAULT;
        save();
    }

    public HudElementState stateOf(HudElement element) {
        if (states == null) return HudElementState.DEFAULT;
        return states.getOrDefault(element.id(), HudElementState.DEFAULT);
    }

    public void setState(HudElement element, HudElementState state) {
        if (states == null) states = new HashMap<>();
        states.put(element.id(), state);
    }

    public void resetAll() {
        if (states != null) states.clear();
    }

    public static Path defaultPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static HudConfig load() {
        Path path = defaultPath();
        HudConfig parsed;
        if (!Files.exists(path)) {
            parsed = new HudConfig();
        } else {
            try {
                String json = Files.readString(path);
                parsed = GSON.fromJson(json, HudConfig.class);
                if (parsed == null) parsed = new HudConfig();
                if (parsed.states == null) parsed.states = new HashMap<>();
                if (parsed.activePreset == null) parsed.activePreset = HudPresets.DEFAULT;
            } catch (IOException | JsonSyntaxException e) {
                LOGGER.warn("config invalide ({}), reset to defaults : {}", path, e.getMessage());
                parsed = new HudConfig();
            }
        }
        // Initialise les presets par défaut s'ils manquent (premier boot ou migration)
        if (parsed.presets == null || parsed.presets.isEmpty()) {
            parsed.presets = HudPresets.defaults();
        } else {
            // Garantit la présence de Default au minimum (final ref pour lambda)
            final HudConfig fParsed = parsed;
            HudPresets.defaults().forEach((id, snap) ->
                fParsed.presets.putIfAbsent(id, snap));
        }
        parsed.save();
        return parsed;
    }

    public synchronized void save() {
        Path path = defaultPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this));
        } catch (IOException e) {
            LOGGER.warn("config save echec ({}) : {}", path, e.getMessage());
        }
    }
}
