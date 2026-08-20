package fr.reborn.hud.animation;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zigythebird.playeranimcore.PlayerAnimLib;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.PlayState;
import fr.reborn.hud.menu.settings.RebornPrefs;
import io.github.kosmx.emotes.server.serializer.UniversalEmoteSerializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Animations de mouvement Reborn (démarches GTA-RP) via PlayerAnimationLib.
 *
 * <p>Remplace l'anim vanilla selon l'état du joueur : <b>marche</b> (STYLE
 * sélectionnable), <b>course</b> (sprint), <b>course chakraïque</b> (Naruto run,
 * touche dédiée → {@link NarutoRun}) et <b>saut</b>. Fichiers {@code .emotecraft}
 * lus par {@link UniversalEmoteSerializer}, {@code .json} geckolib par
 * {@link PlayerAnimLib#GSON}, joués via un {@link PlayerAnimationController} par
 * avatar, en crossfade.
 *
 * <p><b>Multi-joueurs</b> : la factory PAL crée un controller <b>par avatar</b>
 * (locaux ET distants). Le {@link #tick} pilote donc TOUS les
 * {@link AbstractClientPlayer} du monde, pas seulement {@code mc.player} :
 * <ul>
 *   <li>Les états <b>observables</b> (marche / course / saut) sont recalculés
 *       localement pour chaque joueur (delta de position, {@code onGround},
 *       {@code isSprinting}) → visibles sans aucun réseau, à toute distance de
 *       rendu.</li>
 *   <li>Les états <b>non observables</b> (style de marche choisi, naruto-run) sont
 *       affinés par le canal {@code reborn:anim} : le joueur local émet son état
 *       (C2S), le relais {@code AnimRelayListener} le rediffuse aux joueurs proches
 *       (≤ 48 blocs, S2C), qu'on stocke dans {@link #remote} et applique.</li>
 * </ul>
 * Sans le plugin (solo / serveur non-Reborn), l'envoi est inerte
 * ({@code canSend}) et seuls les états observables jouent — aucune régression.
 */
public final class MovementAnimations {

    public static final MovementAnimations INSTANCE = new MovementAnimations();
    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/anim");

    /** Priorité du layer dans la pile PAL (au-dessus des anims de base). */
    private static final int PRIORITY = 1000;
    /** ID du layer d'animation Reborn (registerFactory + getPlayerAnimationLayer). */
    private static final Identifier LAYER = Identifier.fromNamespaceAndPath("reborn-hud", "movement");
    private static final double MOVE_THRESHOLD_SQ = 0.015 * 0.015;
    /** Durée du fondu (fade-in) à l'entrée d'une démarche, en ticks. */
    private static final int FADE_TICKS = 6;
    /** Fondu court pour le saut (anim one-shot ~0,667s → décollage net). */
    private static final int JUMP_FADE_TICKS = 2;
    /** Ticks immobiles requis avant de repasser NONE (anti flip-flop). */
    private static final int IDLE_GRACE_TICKS = 4;
    /** Ré-émission périodique de l'état local tant qu'on n'est pas immobile
     *  (pour que les joueurs entrant dans les 48 blocs voient l'anim en cours). */
    private static final int HEARTBEAT_TICKS = 20;
    /** Au-delà, une info relais est considérée périmée (naruto/style oubliés). */
    private static final int FRESH_TICKS = 60;

    /** Codes {@code state} du canal reborn:anim (contrat client↔client). */
    private static final byte C_NONE = 0, C_WALK = 1, C_RUN = 2, C_NARUTO = 3, C_JUMP = 4;

    /** Styles de marche : {label, fichier}. Index 0 = démarche par défaut. */
    private static final String[][] WALK_STYLES = {
        {"Marche", "walk.emotecraft"},
        {"Reborn", "basewalkreborn.json"},
        {"Défaut", "walk_default.emotecraft"},
        {"Tremblante", "walk_trembling.emotecraft"},
        {"Timide", "walk_timid.emotecraft"},
        {"Arrogante", "walk_arrogant.emotecraft"},
        {"Désespérée", "walk_hopeless.emotecraft"},
    };

    private enum MoveState { NONE, WALK, RUN, NARUTO, POSE, JUMP }

    /** État de détection par joueur (local comme distant). */
    private static final class Tracker {
        double lastX, lastZ;
        boolean hasLastPos;
        int idleGrace;
        MoveState applied = MoveState.NONE;   // anim actuellement posée sur l'avatar
        int appliedWalk = -1;                 // style posé (pour re-appliquer au changement)
    }

    /** Dernier état reçu du relais pour un joueur distant. */
    private static final class RemoteInfo {
        byte state;
        byte walk;
        int tick;   // tickCounter à la réception (pour la fraîcheur)
    }

    private final List<Animation> walkAnims = new ArrayList<>();
    private Animation run, narutoRun;
    private Animation idlePose;              // pose idle des écrans perso (asset optionnel)
    private Animation jump;                  // anim de saut (jouée en l'air, one-shot)
    private boolean poseActive = false;      // override : joue la pose idle en boucle
    private int selectedWalk = 0;
    private boolean available = false;

    private final Map<UUID, Tracker> trackers = new HashMap<>();
    private final Map<UUID, RemoteInfo> remote = new HashMap<>();
    private final Set<UUID> presentUuids = new HashSet<>();
    private int tickCounter = 0;

    private MovementAnimations() {}

    public boolean isAvailable() { return available; }

    public int walkStyleCount() { return WALK_STYLES.length; }
    public String walkStyleName(int i) { return WALK_STYLES[i][0]; }
    public int selectedWalk() { return selectedWalk; }

    public void setWalkStyle(int i) {
        selectedWalk = clamp(i);
        RebornPrefs.INSTANCE.walkStyle = selectedWalk;
        RebornPrefs.INSTANCE.save();
        // Le prochain tick re-applique : si on marche déjà, appliedWalk != selectedWalk
        // déclenche le crossfade vers le nouveau style.
    }

    public void cycleWalkStyle() { setWalkStyle((selectedWalk + 1) % WALK_STYLES.length); }

    /**
     * Pose idle (émote « assis ») forcée en boucle sur le joueur local — utilisée par
     * les écrans de sélection/création. No-op si l'asset {@code idle_sit.emotecraft}
     * est absent. À arrêter dans {@link #stopPose()}.
     */
    public void startPose() { poseActive = true; }

    /**
     * Arrête la pose idle et coupe immédiatement l'émote en cours sur l'avatar local,
     * en remettant son tracker à zéro pour que le prochain tick recompute l'état réel.
     */
    public void stopPose() {
        poseActive = false;
        Minecraft mc = Minecraft.getInstance();
        if (available && mc.player != null) {
            Tracker t = trackers.get(mc.player.getUUID());
            if (t != null) { t.applied = MoveState.NONE; t.appliedWalk = -1; }
            PlayerAnimationController c = controllerOf(mc.player);
            if (c != null) c.stopTriggeredAnimation();
        }
    }
    public boolean hasIdlePose() { return idlePose != null; }

    /** Enregistre le layer PAL par avatar + charge les animations. */
    public void register() {
        try {
            // Pattern CANONIQUE PlayerAnimationLib : une factory qui crée un
            // PlayerAnimationController PAR avatar (locaux ET distants) sous notre
            // LAYER id ; on récupère ensuite le controller par ID via
            // getPlayerAnimationLayer (cf. applyState).
            PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                LAYER, PRIORITY,
                avatar -> new PlayerAnimationController(avatar, (ctrl, data, setter) -> PlayState.STOP));

            for (String[] style : WALK_STYLES) walkAnims.add(load(style[1]));
            run = load("run.emotecraft");
            narutoRun = load("naruto_run.emotecraft");
            idlePose = load("idle_sit.emotecraft");
            jump = load("jumpanimation.json");

            RebornPrefs.INSTANCE.ensureLoaded();
            selectedWalk = clamp(RebornPrefs.INSTANCE.walkStyle);
            available = true;
            LOG.info("démarches : {} styles marche, run={}, naruto={}, jump={} (multi-joueurs)",
                walkAnims.size(), run != null, narutoRun != null, jump != null);
        } catch (Throwable t) {
            available = false;
            LOG.warn("PlayerAnimationLib/Emotecraft absent — démarches désactivées ({})", t.toString());
        }
    }

    /**
     * Charge une animation → {@code Animation}. {@code .json} = format GeckoLib (export
     * Blender), sinon {@code .emotecraft} (binaire Emotecraft).
     */
    private Animation load(String file) {
        try (InputStream in = MovementAnimations.class
                .getResourceAsStream("/assets/reborn-hud/animations/" + file)) {
            if (in == null) { LOG.warn("anim introuvable : {}", file); return null; }
            if (file.endsWith(".json")) return loadGeckolib(in, file);
            Map<String, Animation> data = UniversalEmoteSerializer.readData(in, file);
            return data.isEmpty() ? null : data.values().iterator().next();
        } catch (Exception e) {
            LOG.error("échec lecture anim {} : {}", file, e.toString());
            return null;
        }
    }

    /** Parse un {@code .json} geckolib et retourne sa première animation. */
    private Animation loadGeckolib(InputStream in, String file) {
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = PlayerAnimLib.GSON.fromJson(r, JsonObject.class);
            JsonObject anims = root == null ? null : root.getAsJsonObject("animations");
            if (anims == null || anims.isEmpty()) {
                LOG.warn("anim json sans bloc 'animations' : {}", file);
                return null;
            }
            JsonElement first = anims.entrySet().iterator().next().getValue();
            return PlayerAnimLib.GSON.fromJson(first, Animation.class);
        } catch (Exception e) {
            LOG.error("échec lecture geckolib {} : {}", file, e.toString());
            return null;
        }
    }

    /** À appeler chaque client tick (piloté par {@code HudKeybinds}). */
    public void tick(Minecraft mc) {
        if (!available) return;
        ClientLevel level = mc.level;
        AbstractClientPlayer local = mc.player;
        if (level == null || local == null) return;
        tickCounter++;

        presentUuids.clear();

        // Joueur local : piloté par son input (naruto keybind, style choisi, pose).
        tickLocal(local);
        presentUuids.add(local.getUUID());

        // Joueurs distants : état observable + affinage relais.
        for (AbstractClientPlayer p : level.players()) {
            if (p == local) continue;
            presentUuids.add(p.getUUID());
            tickRemote(p);
        }

        // Purge des joueurs partis (évite la fuite mémoire + reset propre au retour).
        trackers.keySet().retainAll(presentUuids);
        remote.keySet().retainAll(presentUuids);
    }

    private void tickLocal(AbstractClientPlayer player) {
        Tracker t = trackerOf(player.getUUID());

        // Override pose idle (écrans perso) : force l'émote assise en boucle.
        if (poseActive) {
            if (t.applied != MoveState.POSE) {
                applyState(player, MoveState.POSE, 0);
                t.applied = MoveState.POSE;
                t.appliedWalk = -1;
            }
            return;
        }

        MoveState desired = computeState(player, t, true);
        boolean walkChanged = desired == MoveState.WALK && t.appliedWalk != selectedWalk;
        if (desired != t.applied || walkChanged) {
            applyState(player, desired, selectedWalk);
            t.applied = desired;
            t.appliedWalk = selectedWalk;
            sendState(player, desired, selectedWalk);            // C2S au changement
        } else if (desired != MoveState.NONE && tickCounter % HEARTBEAT_TICKS == 0) {
            sendState(player, desired, selectedWalk);            // heartbeat en mouvement
        }
    }

    private void tickRemote(AbstractClientPlayer p) {
        Tracker t = trackerOf(p.getUUID());
        MoveState desired = computeState(p, t, false);
        int walk = 0;
        RemoteInfo ri = remote.get(p.getUUID());
        if (ri != null && fresh(ri)) walk = clamp(ri.walk);
        boolean walkChanged = desired == MoveState.WALK && t.appliedWalk != walk;
        if (desired != t.applied || walkChanged) {
            applyState(p, desired, walk);
            t.applied = desired;
            t.appliedWalk = walk;
        }
    }

    private Tracker trackerOf(UUID uuid) {
        return trackers.computeIfAbsent(uuid, k -> new Tracker());
    }

    private boolean fresh(RemoteInfo ri) { return tickCounter - ri.tick <= FRESH_TICKS; }

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
            case WALK -> {
                Animation a = walkAnims.isEmpty() ? null : walkAnims.get(clamp(walkStyle));
                if (a == null && !walkAnims.isEmpty()) a = walkAnims.get(0); // fallback style défaut
                yield a;
            }
            case RUN    -> run;
            case NARUTO -> narutoRun;
            case POSE   -> idlePose;
            case JUMP   -> jump;
            case NONE   -> null;
        };
        if (anim == null) {
            controller.stopTriggeredAnimation();
        } else {
            // Fondu doux + loop INTRINSÈQUE de l'anim (respecte le point de bouclage
            // authoré ; le saut est loop:false → one-shot). Fondu court pour le saut.
            int fade = desired == MoveState.JUMP ? JUMP_FADE_TICKS : FADE_TICKS;
            controller.replaceAnimationWithFade(
                AbstractFadeModifier.standardFadeIn(fade, EasingType.EASE_IN_OUT_SINE),
                RawAnimation.begin().then(anim, anim.loopType()));
        }
    }

    /**
     * Calcule l'état d'anim voulu à partir des données <b>observables</b> du joueur
     * (delta de position, {@code onGround}, {@code isSprinting}). Le naruto-run n'est
     * pas observable : côté local on lit la touche, côté distant l'état relayé.
     */
    private MoveState computeState(AbstractClientPlayer player, Tracker t, boolean isLocal) {
        double dx = player.getX() - t.lastX;
        double dz = player.getZ() - t.lastZ;
        double speedSq = t.hasLastPos ? (dx * dx + dz * dz) : 0.0;
        t.lastX = player.getX();
        t.lastZ = player.getZ();
        t.hasLastPos = true;

        // Saut / en l'air : prioritaire, indépendant du mouvement horizontal.
        if (!player.onGround() && jump != null) {
            t.idleGrace = 0;
            return MoveState.JUMP;
        }

        boolean moving = speedSq > MOVE_THRESHOLD_SQ;
        if (!moving) {
            // Hystérésis : ne repasse NONE qu'après IDLE_GRACE_TICKS immobiles.
            if (++t.idleGrace >= IDLE_GRACE_TICKS) return MoveState.NONE;
            return t.applied == MoveState.POSE ? MoveState.NONE : t.applied;
        }
        t.idleGrace = 0;

        boolean naruto = isLocal
            ? NarutoRun.INSTANCE.isActive()
            : remoteNaruto(player.getUUID());
        if (naruto && narutoRun != null) return MoveState.NARUTO;
        if (player.isSprinting() && run != null) return MoveState.RUN;
        return (!walkAnims.isEmpty() && walkAnims.get(0) != null) ? MoveState.WALK : MoveState.NONE;
    }

    private boolean remoteNaruto(UUID uuid) {
        RemoteInfo ri = remote.get(uuid);
        return ri != null && fresh(ri) && ri.state == C_NARUTO;
    }

    /** Émet l'état local sur le canal reborn:anim (inerte si le plugin est absent). */
    private void sendState(AbstractClientPlayer local, MoveState st, int walk) {
        if (!ClientPlayNetworking.canSend(AnimSyncPayload.ID)) return;
        ClientPlayNetworking.send(new AnimSyncPayload(local.getUUID(), code(st), (byte) clamp(walk)));
    }

    /** Réception S2C : mémorise l'état relayé d'un joueur distant (thread client). */
    public void onRemoteState(UUID uuid, byte state, byte walk) {
        if (!available || uuid == null) return;
        RemoteInfo ri = remote.computeIfAbsent(uuid, k -> new RemoteInfo());
        ri.state = state;
        ri.walk = walk;
        ri.tick = tickCounter;
    }

    /** Reset à la déconnexion (le niveau et les avatars disparaissent de toute façon). */
    public void clearRemote() {
        remote.clear();
        trackers.clear();
    }

    private static byte code(MoveState s) {
        return switch (s) {
            case WALK   -> C_WALK;
            case RUN    -> C_RUN;
            case NARUTO -> C_NARUTO;
            case JUMP   -> C_JUMP;
            default     -> C_NONE;   // NONE / POSE (POSE n'est jamais émis)
        };
    }

    private static int clamp(int i) {
        return Math.max(0, Math.min(WALK_STYLES.length - 1, i));
    }
}
