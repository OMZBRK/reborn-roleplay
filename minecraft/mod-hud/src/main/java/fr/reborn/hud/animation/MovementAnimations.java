package fr.reborn.hud.animation;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.enums.PlayState;
import fr.reborn.hud.menu.settings.RebornPrefs;
import io.github.kosmx.emotes.server.serializer.UniversalEmoteSerializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Animations de mouvement Reborn (démarches GTA-RP) via PlayerAnimationLib.
 *
 * <p>Remplace l'anim vanilla selon l'état du joueur : <b>marche</b> (STYLE
 * sélectionnable), <b>course</b> (sprint) et <b>course chakraïque</b> (Naruto run,
 * touche dédiée → {@link NarutoRun}). Fichiers {@code .emotecraft} lus par
 * {@link UniversalEmoteSerializer} (→ {@code Animation} zigythebird) et joués via
 * un {@link ModifierLayer} par avatar, en crossfade.
 *
 * <p><b>Port 26.1</b> : migré de l'API {@code dev.kosmx.playerAnim} (1.21.4) vers
 * le fork mergé {@code com.zigythebird.playeranim} + Emotecraft 3.3.0. L'ancien
 * {@code KeyframeAnimationPlayer(anim)} n'existe plus : une {@code Animation}
 * (record) se joue via un {@link PlayerAnimationController} (l'{@code IAnimation}
 * concret) qu'on {@code triggerAnimation(...)} puis qu'on pose dans le layer.
 *
 * <p>Le rendu est <b>local</b> (le joueur voit ses propres démarches). La synchro
 * cross-joueurs (voir les démarches des autres) dépend d'un canal serveur
 * {@code reborn:anim} relayé par ShinobiCore — non re-câblé côté client ici.
 */
public final class MovementAnimations {

    public static final MovementAnimations INSTANCE = new MovementAnimations();
    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/anim");

    /** Priorité du layer dans la pile PAL (au-dessus des anims de base). */
    private static final int PRIORITY = 1000;
    private static final double MOVE_THRESHOLD_SQ = 0.02 * 0.02;
    /** Durée de transition (crossfade) entre deux démarches, en ticks. */
    private static final float TRANSITION = 5.0f;

    /** Styles de marche : {label, fichier}. */
    private static final String[][] WALK_STYLES = {
        {"Défaut", "walk_default.emotecraft"},
        {"Tremblante", "walk_trembling.emotecraft"},
        {"Timide", "walk_timid.emotecraft"},
        {"Arrogante", "walk_arrogant.emotecraft"},
        {"Désespérée", "walk_hopeless.emotecraft"},
    };

    private enum MoveState { NONE, WALK, RUN, NARUTO }

    private final List<Animation> walkAnims = new ArrayList<>();
    private Animation run, narutoRun;
    private int selectedWalk = 0;
    private boolean available = false;

    /** Controller PAL par avatar (weak : suit le cycle de vie de l'entité). */
    private final Map<Avatar, PlayerAnimationController> controllers = new WeakHashMap<>();

    // État local courant (pour ne ré-appliquer qu'aux changements).
    private MoveState currentState = MoveState.NONE;
    private int currentWalkIndex = -1;

    private MovementAnimations() {}

    public boolean isAvailable() { return available; }

    public int walkStyleCount() { return WALK_STYLES.length; }
    public String walkStyleName(int i) { return WALK_STYLES[i][0]; }
    public int selectedWalk() { return selectedWalk; }

    public void setWalkStyle(int i) {
        selectedWalk = clamp(i);
        RebornPrefs.INSTANCE.walkStyle = selectedWalk;
        RebornPrefs.INSTANCE.save();
        currentState = MoveState.NONE; // force ré-application au prochain tick
    }

    public void cycleWalkStyle() { setWalkStyle((selectedWalk + 1) % WALK_STYLES.length); }

    /** Enregistre le layer PAL par avatar + charge les animations. */
    public void register() {
        try {
            // Un PlayerAnimationController par avatar, ajouté DIRECTEMENT à sa pile
            // d'anims via l'event PAL (pattern EmotePlayer d'Emotecraft — pas de
            // ModifierLayer intermédiaire, sinon l'anim déclenchée ne joue pas).
            PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((avatar, animManager) -> {
                PlayerAnimationController controller =
                    new PlayerAnimationController(avatar, (ctrl, data, setter) -> PlayState.STOP);
                animManager.addAnimLayer(PRIORITY, controller);
                controllers.put(avatar, controller);
            });

            for (String[] style : WALK_STYLES) walkAnims.add(load(style[1]));
            run = load("run.emotecraft");
            narutoRun = load("naruto_run.emotecraft");

            RebornPrefs.INSTANCE.ensureLoaded();
            selectedWalk = clamp(RebornPrefs.INSTANCE.walkStyle);
            available = true;
            LOG.info("démarches : {} styles marche, run={}, naruto={}",
                walkAnims.size(), run != null, narutoRun != null);
        } catch (Throwable t) {
            available = false;
            LOG.warn("PlayerAnimationLib/Emotecraft absent — démarches désactivées ({})", t.toString());
        }
    }

    /** Charge une {@code .emotecraft} (format binaire Emotecraft) → {@code Animation}. */
    private Animation load(String file) {
        try (InputStream in = MovementAnimations.class
                .getResourceAsStream("/assets/reborn-hud/animations/" + file)) {
            if (in == null) { LOG.warn("anim introuvable : {}", file); return null; }
            Map<String, Animation> data = UniversalEmoteSerializer.readData(in, file);
            return data.isEmpty() ? null : data.values().iterator().next();
        } catch (Exception e) {
            LOG.error("échec lecture anim {} : {}", file, e.toString());
            return null;
        }
    }

    /** À appeler chaque client tick (piloté par {@code HudKeybinds}). */
    public void tick(Minecraft mc) {
        if (!available) return;
        AbstractClientPlayer player = mc.player;
        if (player == null) return;

        MoveState desired = computeState(player);
        boolean walkStyleChanged = desired == MoveState.WALK && currentWalkIndex != selectedWalk;
        if (desired != currentState || walkStyleChanged) {
            currentState = desired;
            currentWalkIndex = selectedWalk;
            applyState(player, desired, selectedWalk);
        }
    }

    /** Déclenche l'anim voulue sur le controller du joueur (crossfade), ou stop si idle. */
    private void applyState(AbstractClientPlayer player, MoveState desired, int walkStyle) {
        PlayerAnimationController controller = controllers.get(player);
        if (controller == null) return;
        Animation anim = switch (desired) {
            case WALK   -> walkAnims.isEmpty() ? null : walkAnims.get(clamp(walkStyle));
            case RUN    -> run;
            case NARUTO -> narutoRun;
            case NONE   -> null;
        };
        if (anim == null) {
            controller.stop();
        } else {
            // RawAnimation en boucle (démarche continue) + transition douce.
            controller.triggerAnimation(RawAnimation.begin().thenLoop(anim), TRANSITION);
        }
    }

    private MoveState computeState(AbstractClientPlayer player) {
        Vec3 v = player.getDeltaMovement();
        boolean moving = (v.x * v.x + v.z * v.z) > MOVE_THRESHOLD_SQ;
        if (!moving) return MoveState.NONE;
        if (NarutoRun.INSTANCE.isActive() && narutoRun != null) return MoveState.NARUTO;
        if (player.isSprinting() && run != null) return MoveState.RUN;
        return (!walkAnims.isEmpty() && walkAnims.get(selectedWalk) != null)
            ? MoveState.WALK : MoveState.NONE;
    }

    private static int clamp(int i) {
        return Math.max(0, Math.min(WALK_STYLES.length - 1, i));
    }
}
