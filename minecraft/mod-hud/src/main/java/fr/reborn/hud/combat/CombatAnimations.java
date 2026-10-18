package fr.reborn.hud.combat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.PlayerAnimLib;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.PlayState;
import io.github.kosmx.emotes.server.serializer.UniversalEmoteSerializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Animations de COMBAT (taïjutsu) jouées sur un layer PAL dédié, au-dessus des
 * démarches ({@link fr.reborn.hud.animation.MovementAnimations}) via une priorité
 * plus haute. Deux familles :
 * <ul>
 *   <li><b>Coups (one-shot)</b> : combo M1 = {@code strike1/2/3} + {@code pyramid}
 *       (finisher). Jouées une fois puis le layer se tait (les démarches reprennent).</li>
 *   <li><b>Garde (hold)</b> : touche Parade (C) = {@code block}, jouée en {@code hold_on_last_frame}
 *       (windup ~0,4s puis dernière frame figée) tant que la touche est maintenue ; à la relâche,
 *       fondu vers {@link #repos}. <b>Parade réussie</b> (7) = {@code block_parry} (coup de garde).</li>
 * </ul>
 *
 * <p>Piloté par le serveur ({@code reborn:combat} TYPE_ANIM) pour voir les coups des
 * AUTRES joueurs, ET localement par {@code HudKeybinds} pour un retour immédiat sur
 * son propre avatar. Le controller PAL existe par avatar → {@link #play} marche pour
 * n'importe quel {@link AbstractClientPlayer}.
 */
public final class CombatAnimations {

    public static final CombatAnimations INSTANCE = new CombatAnimations();
    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/combat-anim");

    /** Au-dessus du layer démarches (priorité 1000) → les coups couvrent la marche. */
    private static final int PRIORITY = 2000;
    private static final Identifier LAYER = Identifier.fromNamespaceAndPath("reborn-hud", "combat");
    private static final int STRIKE_FADE = 2, BLOCK_FADE = 2, PARADE_FADE = 1;
    /** Fondu de sortie de garde (ticks) : la pose redescend au lieu de sauter. */
    private static final int FONDU_SORTIE = 4;
    /** Une relâche avant ce délai (ticks) est différée : la montée de garde (0,417 s) va au bout et se lit. */
    private static final int GARDE_MIN_TICKS = 10;
    /** Durée du coup de garde de la parade réussie (block_parry.json, 0,3 s) + une marge. */
    private static final int PARADE_TICKS = 7;

    // animId réseau (contrat serveur↔client, cf. ShinobiCombat).
    public static final int ANIM_STRIKE1 = 1, ANIM_STRIKE2 = 2, ANIM_STRIKE3 = 3,
        ANIM_PYRAMID = 4, ANIM_BLOCK_ON = 5, ANIM_BLOCK_OFF = 6, ANIM_PARRY = 7;

    /** Coups M1 indexés par (animId-1) : strike1, strike2, strike3, pyramid(finisher). */
    private final Animation[] strikes = new Animation[4];
    private Animation block;
    /** Coup de garde (parade réussie), dérivé de block.json ; pose finale = garde. */
    private Animation parade;
    /** Animation vide : cible du fondu de sortie de garde. */
    private Animation repos;
    private boolean available = false;

    /** Garde par avatar (id d'entité) : relâches différées, fin de geste minimale, gardes en cours. */
    private final Map<Integer, Integer> relacheA = new HashMap<>();
    private final Map<Integer, Integer> tenueJusqua = new HashMap<>();
    private final Set<Integer> garde = new HashSet<>();
    private int horloge;

    private CombatAnimations() {}

    public boolean isAvailable() { return available; }

    /** Enregistre le layer PAL par avatar + charge les animations de combat. */
    public void register() {
        try {
            PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                LAYER, PRIORITY,
                avatar -> new PlayerAnimationController(avatar, (ctrl, data, setter) -> PlayState.STOP));

            strikes[0] = load("strike1.emotecraft");
            strikes[1] = load("strike2.emotecraft");
            strikes[2] = load("strike3.emotecraft");
            strikes[3] = load("heavy_punch.emotecraft");   // 4e coup du combo = Pyramid Head Punch
            block = load("block.json");
            parade = load("block_parry.json");
            repos = PlayerAnimLib.GSON.fromJson(
                "{\"animation_length\":0.25,\"loop\":false,\"bones\":{}}", Animation.class);
            available = true;
            LOG.info("anims combat : strikes={}, block={}, parade={}", countNonNull(strikes), block != null, parade != null);
        } catch (Throwable t) {
            available = false;
            LOG.warn("anims combat indisponibles ({})", t.toString());
        }
    }

    /**
     * Joue une animation de combat sur l'avatar. {@code animId} : 1-4 = coups (one-shot),
     * 5 = garde ON (hold), 6 = garde OFF, 7 = parade réussie (coup de garde). No-op si l'anim est absente.
     *
     * <p>Garde (18/10) : une parade se joue d'une <b>tape</b> sur C (~150 ms), plus courte que la montée de la garde
     * (0,417 s) — l'arrêt sec à la relâche coupait le geste avant qu'il se voie. La relâche est donc différée jusqu'à
     * ce que la garde soit montée ({@link #GARDE_MIN_TICKS}), puis la pose redescend en fondu ({@link #FONDU_SORTIE}).
     */
    public void play(Avatar player, int animId) {
        if (!available || player == null) return;
        PlayerAnimationController c = controllerOf(player);
        if (c == null) return;
        int id = player.getId();

        switch (animId) {
            case ANIM_BLOCK_OFF -> {
                garde.remove(id);
                int tenue = tenueJusqua.getOrDefault(id, 0);
                if (horloge < tenue) relacheA.put(id, tenue);     // geste pas encore lisible : on le finit
                else relacher(player);
                return;
            }
            case ANIM_BLOCK_ON -> {
                if (block == null) return;
                relacheA.remove(id);
                garde.add(id);
                tenueJusqua.put(id, horloge + GARDE_MIN_TICKS);
                // block.json est en hold_on_last_frame → tient la pose de garde jusqu'à la relâche.
                lancer(c, block, BLOCK_FADE);
                return;
            }
            case ANIM_PARRY -> {
                Animation a = parade != null ? parade : block;
                if (a == null) return;
                tenueJusqua.merge(id, horloge + PARADE_TICKS, Math::max);
                // Relâche déjà reçue (tape courte) ou parade hors garde : redescendre après le coup de garde.
                if (!garde.contains(id)) relacheA.merge(id, horloge + PARADE_TICKS, Math::max);
                lancer(c, a, PARADE_FADE);
                return;
            }
            default -> { }
        }
        Animation anim = animId >= ANIM_STRIKE1 && animId <= ANIM_PYRAMID ? strikes[animId - 1] : null;
        if (anim == null) return;
        garde.remove(id);
        relacheA.remove(id);
        tenueJusqua.remove(id);
        // Coups : loop intrinsèque = play-once.
        lancer(c, anim, STRIKE_FADE);
    }

    private static void lancer(PlayerAnimationController c, Animation anim, int fade) {
        c.replaceAnimationWithFade(
            AbstractFadeModifier.standardFadeIn(fade, EasingType.EASE_IN_OUT_SINE),
            RawAnimation.begin().then(anim, anim.loopType()));
    }

    /** Fin de garde : la pose redescend en fondu vers la posture normale (marche, course… reprennent dessous). */
    private void relacher(Avatar player) {
        int id = player.getId();
        relacheA.remove(id);
        tenueJusqua.remove(id);
        PlayerAnimationController c = controllerOf(player);
        if (c == null || !c.isActive()) return;
        Animation cur = c.getCurrentAnimationInstance();
        if (cur != block && cur != parade) return;              // un coup a pris la main : on n'y touche pas
        if (repos != null) {
            c.replaceAnimationWithFade(
                AbstractFadeModifier.standardFadeIn(FONDU_SORTIE, EasingType.EASE_IN_OUT_SINE),
                RawAnimation.begin().then(repos, repos.loopType()));
        } else {
            c.stopTriggeredAnimation();
        }
    }

    /** Horloge des relâches différées : à appeler à chaque tick client. */
    public void tick(Minecraft mc) {
        horloge++;
        if (relacheA.isEmpty()) return;
        ClientLevel level = mc.level;
        if (level == null) { relacheA.clear(); tenueJusqua.clear(); garde.clear(); return; }
        var it = relacheA.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (e.getValue() > horloge) continue;
            it.remove();
            if (level.getEntity(e.getKey()) instanceof Avatar a) relacher(a);
            else tenueJusqua.remove(e.getKey());
        }
    }

    /** Joue par id d'entité (réception réseau) : résout l'avatar puis délègue. */
    public void playByEntityId(int entityId, int animId) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        if (level.getEntity(entityId) instanceof Avatar p) play(p, animId);
    }

    /** Banc (REBORN_PARADE_DEBUG) : état du layer combat de cet avatar, pour le log. */
    public String diag(Avatar player) {
        if (!available) return "indisponible";
        IAnimation layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER);
        if (!(layer instanceof PlayerAnimationController c)) return "pas de layer (" + layer + ")";
        var anim = c.getCurrentAnimationInstance();
        return "actif=" + c.isActive() + " etat=" + c.getAnimationState() + " anim="
            + (anim == null ? "-" : anim == block ? "block" : anim == parade ? "parade" : anim == repos ? "repos" : "coup")
            + " t=" + c.getAnimationTicks();
    }

    private PlayerAnimationController controllerOf(Avatar player) {
        IAnimation layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER);
        return layer instanceof PlayerAnimationController c ? c : null;
    }

    private Animation load(String file) {
        try (InputStream in = CombatAnimations.class
                .getResourceAsStream("/assets/reborn-hud/animations/" + file)) {
            if (in == null) { LOG.warn("anim combat introuvable : {}", file); return null; }
            if (file.endsWith(".json")) return loadGeckolib(in, file);
            Map<String, Animation> data = UniversalEmoteSerializer.readData(in, file);
            return data.isEmpty() ? null : data.values().iterator().next();
        } catch (Exception e) {
            LOG.error("échec lecture anim combat {} : {}", file, e.toString());
            return null;
        }
    }

    private Animation loadGeckolib(InputStream in, String file) {
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = PlayerAnimLib.GSON.fromJson(r, JsonObject.class);
            JsonObject anims = root == null ? null : root.getAsJsonObject("animations");
            if (anims == null || anims.isEmpty()) return null;
            JsonElement first = anims.entrySet().iterator().next().getValue();
            return PlayerAnimLib.GSON.fromJson(first, Animation.class);
        } catch (Exception e) {
            LOG.error("échec lecture geckolib combat {} : {}", file, e.toString());
            return null;
        }
    }

    private static int countNonNull(Animation[] a) {
        int n = 0;
        for (Animation x : a) if (x != null) n++;
        return n;
    }
}
