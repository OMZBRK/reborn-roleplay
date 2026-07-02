package fr.reborn.hud.animation;

import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import fr.reborn.hud.menu.settings.RebornPrefs;
import io.github.kosmx.emotes.server.serializer.UniversalEmoteSerializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Animations de mouvement Reborn (styles de marche GTA-RP) via PlayerAnimator.
 *
 * <p>Remplace l'anim vanilla de déplacement selon l'état : <b>marche</b> (avec
 * un STYLE sélectionnable par le joueur — défaut / tremblante / timide /
 * arrogante / désespérée), <b>course</b> (sprint) et <b>naruto-run</b>. Les
 * fichiers {@code .emotecraft} sont lus par {@link UniversalEmoteSerializer} →
 * {@link KeyframeAnimation}, joués via un {@link ModifierLayer} de
 * PlayerAnimator (persiste pendant le déplacement), avec fade entre états.
 *
 * <p>V1 : joueur local uniquement. « Voir les autres » (les autres joueurs
 * voient ton anim) = étape suivante, nécessite de synchroniser le style choisi
 * + l'état naruto (via plugin-message / réseau Emotecraft).
 */
public final class MovementAnimations {

    public static final MovementAnimations INSTANCE = new MovementAnimations();
    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/anim");

    private static final Identifier LAYER = Identifier.of("reborn-hud", "movement");
    private static final int PRIORITY = 1000;
    private static final double MOVE_THRESHOLD_SQ = 0.02 * 0.02;
    private static final int FADE_TICKS = 4;

    /** Styles de marche : {label, fichier}. Le joueur en choisit un. */
    private static final String[][] WALK_STYLES = {
        {"Défaut", "walk_default.emotecraft"},
        {"Tremblante", "walk_trembling.emotecraft"},
        {"Timide", "walk_timid.emotecraft"},
        {"Arrogante", "walk_arrogant.emotecraft"},
        {"Désespérée", "walk_hopeless.emotecraft"},
    };

    private enum MoveState { NONE, WALK, RUN, NARUTO }

    private final List<KeyframeAnimation> walkAnims = new ArrayList<>();
    private KeyframeAnimation run, narutoRun;
    private int selectedWalk = 0;
    private boolean narutoTest = false;

    /** false si PlayerAnimator/Emotecraft absent au runtime → feature inerte
     *  (au lieu de faire crasher tout l'init du mod). */
    private boolean available = false;

    private MoveState currentState = MoveState.NONE;
    private int currentWalkIndex = -1; // pour re-fade si on change de style en marchant

    private MovementAnimations() {}

    public boolean isAvailable() { return available; }

    // ── Sélection de style ──
    public int walkStyleCount() { return WALK_STYLES.length; }
    public String walkStyleName(int i) { return WALK_STYLES[i][0]; }
    public int selectedWalk() { return selectedWalk; }

    public void setWalkStyle(int i) {
        selectedWalk = Math.max(0, Math.min(WALK_STYLES.length - 1, i));
        RebornPrefs.INSTANCE.walkStyle = selectedWalk;
        RebornPrefs.INSTANCE.save();
        currentState = MoveState.NONE; // force le ré-application au prochain tick
    }

    public void cycleWalkStyle() { setWalkStyle((selectedWalk + 1) % WALK_STYLES.length); }
    public void toggleNarutoTest() { narutoTest = !narutoTest; }

    /**
     * À appeler une fois au client init. Enregistre le layer + charge les anims.
     *
     * <p>PlayerAnimator/Emotecraft sont fournis par le modpack au runtime
     * (compileOnly ici). S'ils sont absents, on capte le {@link Throwable}
     * ({@code NoClassDefFoundError} est une {@link Error}, pas une exception)
     * et on laisse la feature inerte plutôt que d'aborter tout l'init du mod
     * (ce qui tuerait HUD + menu Reborn).
     */
    public void register() {
        try {
            PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                LAYER, PRIORITY, player -> new ModifierLayer<>());

            for (String[] style : WALK_STYLES) {
                walkAnims.add(load(style[1]));
            }
            run = load("run.emotecraft");
            narutoRun = load("naruto_run.emotecraft");

            RebornPrefs.INSTANCE.ensureLoaded();
            selectedWalk = Math.max(0, Math.min(WALK_STYLES.length - 1, RebornPrefs.INSTANCE.walkStyle));
            available = true;
            LOG.info("anims : {} styles marche, run={}, naruto={}",
                walkAnims.size(), run != null, narutoRun != null);
        } catch (Throwable t) {
            available = false;
            LOG.warn("PlayerAnimator/Emotecraft absent — démarches animées désactivées ({})",
                t.toString());
        }
    }

    private KeyframeAnimation load(String file) {
        try (InputStream in = MovementAnimations.class
                .getResourceAsStream("/assets/reborn-hud/animations/" + file)) {
            if (in == null) { LOG.warn("anim introuvable : {}", file); return null; }
            List<KeyframeAnimation> list = UniversalEmoteSerializer.readData(in, file);
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            LOG.error("échec lecture anim {} : {}", file, e.toString());
            return null;
        }
    }

    /** À appeler chaque client tick. Pilote l'anim du joueur local. */
    public void tick(MinecraftClient mc) {
        if (!available) return; // PlayerAnimator absent → ne pas toucher ses classes.
        AbstractClientPlayerEntity player = mc.player;
        if (player == null) return;
        ModifierLayer<IAnimation> layer = layerOf(player);
        if (layer == null) return;

        MoveState desired = computeState(player);
        // Re-fade si l'état change OU si on change de style de marche en marchant.
        boolean walkStyleChanged = desired == MoveState.WALK && currentWalkIndex != selectedWalk;
        if (desired == currentState && !walkStyleChanged) return;
        currentState = desired;
        currentWalkIndex = selectedWalk;

        KeyframeAnimation anim = switch (desired) {
            case WALK -> walkAnims.get(selectedWalk);
            case RUN -> run;
            case NARUTO -> narutoRun;
            case NONE -> null;
        };
        layer.replaceAnimationWithFade(
            AbstractFadeModifier.standardFadeIn(FADE_TICKS, Ease.INOUTSINE),
            anim == null ? null : new KeyframeAnimationPlayer(anim));
    }

    private MoveState computeState(AbstractClientPlayerEntity player) {
        double vx = player.getVelocity().x, vz = player.getVelocity().z;
        boolean moving = (vx * vx + vz * vz) > MOVE_THRESHOLD_SQ;
        if (!moving) return MoveState.NONE;
        if (narutoTest && narutoRun != null) return MoveState.NARUTO;
        if (player.isSprinting() && run != null) return MoveState.RUN;
        return walkAnims.get(selectedWalk) != null ? MoveState.WALK : MoveState.NONE;
    }

    @SuppressWarnings("unchecked")
    private ModifierLayer<IAnimation> layerOf(AbstractClientPlayerEntity player) {
        IAnimation a = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER);
        return a instanceof ModifierLayer ? (ModifierLayer<IAnimation>) a : null;
    }
}
