package fr.reborn.hud.animation;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.PlayState;
import fr.reborn.hud.menu.settings.RebornPrefs;
import io.github.kosmx.emotes.server.serializer.UniversalEmoteSerializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    /** ID du layer d'animation Reborn (registerFactory + getPlayerAnimationLayer). */
    private static final Identifier LAYER = Identifier.fromNamespaceAndPath("reborn-hud", "movement");
    private static final double MOVE_THRESHOLD_SQ = 0.015 * 0.015;
    /** Durée du fondu (fade-in) à l'entrée d'une démarche, en ticks. Easing
     *  EASE_IN_OUT_SINE (comme la version 1.21.4 qui rendait très bien). */
    private static final int FADE_TICKS = 6;
    /** Ticks immobiles requis avant de repasser NONE (anti flip-flop). */
    private static final int IDLE_GRACE_TICKS = 4;

    /** Styles de marche : {label, fichier}. Index 0 = démarche par défaut
     *  (« Walking » — walk.emotecraft), puis les 5 styles sélectionnables. */
    private static final String[][] WALK_STYLES = {
        {"Marche", "walk.emotecraft"},
        {"Défaut", "walk_default.emotecraft"},
        {"Tremblante", "walk_trembling.emotecraft"},
        {"Timide", "walk_timid.emotecraft"},
        {"Arrogante", "walk_arrogant.emotecraft"},
        {"Désespérée", "walk_hopeless.emotecraft"},
    };

    private enum MoveState { NONE, WALK, RUN, NARUTO, POSE }

    private final List<Animation> walkAnims = new ArrayList<>();
    private Animation run, narutoRun;
    private Animation idlePose;              // pose idle des écrans perso (asset optionnel)
    private boolean poseActive = false;      // override : joue la pose idle en boucle
    private int selectedWalk = 0;
    private boolean available = false;

    // État local courant (pour ne ré-appliquer qu'aux changements).
    private MoveState currentState = MoveState.NONE;
    private int currentWalkIndex = -1;

    // Détection de mouvement STABLE par delta de position : getDeltaMovement()
    // du joueur local oscille (la vélocité horizontale est remise ~0 par la
    // friction/collision certains ticks) → flip-flop WALK↔NONE chaque tick →
    // l'anim était stoppée+re-déclenchée en boucle et ne jouait jamais.
    private double lastX, lastZ;
    private boolean hasLastPos = false;
    private int idleGrace = 0;

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

    /**
     * Pose idle (émote « assis ») forcée en boucle — utilisée par les écrans de
     * sélection/création de perso pour présenter le joueur dans une pose sympa au
     * lieu de rester planté debout. No-op si l'asset {@code idle_sit.emotecraft}
     * est absent (le controller reste stoppé). À arrêter dans {@code removed()}.
     */
    public void startPose() { poseActive = true; currentState = MoveState.NONE; }

    /**
     * Arrête la pose idle et <b>coupe immédiatement</b> l'émote en cours. Sans cet
     * arrêt direct, le prochain {@code tick()} verrait {@code desired==NONE} déjà
     * égal à {@code currentState} et n'appellerait jamais {@code applyState(NONE)}
     * → l'émote assise resterait figée (bug de la pose persistante dans l'éditeur).
     */
    public void stopPose() {
        poseActive = false;
        currentState = MoveState.NONE;
        Minecraft mc = Minecraft.getInstance();
        if (available && mc.player != null) {
            PlayerAnimationController c = controllerOf(mc.player);
            if (c != null) c.stopTriggeredAnimation();
        }
    }
    public boolean hasIdlePose() { return idlePose != null; }

    /** Enregistre le layer PAL par avatar + charge les animations. */
    public void register() {
        try {
            // Pattern CANONIQUE PlayerAnimationLib (docs.zigythebird.com) : on
            // enregistre une factory qui crée un PlayerAnimationController par
            // avatar sous notre LAYER id. On récupère ensuite le controller par
            // ID via getPlayerAnimationLayer (cf. applyState) — plus fiable que
            // de mémoriser soi-même les controllers.
            PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                LAYER, PRIORITY,
                avatar -> new PlayerAnimationController(avatar, (ctrl, data, setter) -> PlayState.STOP));

            for (String[] style : WALK_STYLES) walkAnims.add(load(style[1]));
            run = load("run.emotecraft");
            narutoRun = load("naruto_run.emotecraft");
            idlePose = load("idle_sit.emotecraft");

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

        // Override pose idle (écrans perso) : force l'émote assise en boucle,
        // indépendamment du mouvement (le joueur est figé pendant la sélection).
        if (poseActive) {
            if (currentState != MoveState.POSE) {
                currentState = MoveState.POSE;
                currentWalkIndex = -1;
                applyState(player, MoveState.POSE, 0);
            }
            return;
        }

        MoveState desired = computeState(player);
        boolean walkStyleChanged = desired == MoveState.WALK && currentWalkIndex != selectedWalk;
        if (desired != currentState || walkStyleChanged) {
            currentState = desired;
            currentWalkIndex = selectedWalk;
            applyState(player, desired, selectedWalk);
        }
    }

    /** Récupère le controller Reborn de ce joueur via l'API PAL (par LAYER id). */
    private PlayerAnimationController controllerOf(AbstractClientPlayer player) {
        IAnimation layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER);
        return layer instanceof PlayerAnimationController c ? c : null;
    }

    /** Déclenche l'anim voulue sur le controller du joueur (crossfade), ou stop si idle. */
    private void applyState(AbstractClientPlayer player, MoveState desired, int walkStyle) {
        PlayerAnimationController controller = controllerOf(player);
        if (controller == null) return;
        Animation anim = switch (desired) {
            case WALK   -> walkAnims.isEmpty() ? null : walkAnims.get(clamp(walkStyle));
            case RUN    -> run;
            case NARUTO -> narutoRun;
            case POSE   -> idlePose;
            case NONE   -> null;
        };
        if (anim == null) {
            controller.stopTriggeredAnimation();
        } else {
            // Fondu doux (EASE_IN_OUT_SINE) + loop INTRINSÈQUE de l'anim
            // (then(anim, anim.loopType())) : on ne force PAS un LoopType.LOOP
            // reset-à-0 comme thenLoop() — c'est ça qui provoquait le « relance
            // dégueu » ; on respecte le point de bouclage authoré dans l'emote.
            controller.replaceAnimationWithFade(
                AbstractFadeModifier.standardFadeIn(FADE_TICKS, EasingType.EASE_IN_OUT_SINE),
                RawAnimation.begin().then(anim, anim.loopType()));
        }
    }

    private MoveState computeState(AbstractClientPlayer player) {
        // Vitesse horizontale réelle = distance parcourue depuis le tick précédent
        // (monotone quand on marche, contrairement à getDeltaMovement).
        double dx = player.getX() - lastX;
        double dz = player.getZ() - lastZ;
        double speedSq = hasLastPos ? (dx * dx + dz * dz) : 0.0;
        lastX = player.getX();
        lastZ = player.getZ();
        hasLastPos = true;

        boolean moving = speedSq > MOVE_THRESHOLD_SQ;
        if (!moving) {
            // Hystérésis : ne repasse NONE qu'après IDLE_GRACE_TICKS immobiles ;
            // pendant la grâce on conserve l'état courant (pas de stop/retrigger).
            if (++idleGrace >= IDLE_GRACE_TICKS) return MoveState.NONE;
            return currentState;
        }
        idleGrace = 0;
        if (NarutoRun.INSTANCE.isActive() && narutoRun != null) return MoveState.NARUTO;
        if (player.isSprinting() && run != null) return MoveState.RUN;
        return (!walkAnims.isEmpty() && walkAnims.get(selectedWalk) != null)
            ? MoveState.WALK : MoveState.NONE;
    }

    private static int clamp(int i) {
        return Math.max(0, Math.min(WALK_STYLES.length - 1, i));
    }
}
