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
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Animations de COMBAT (taïjutsu) jouées sur un layer PAL dédié, au-dessus des
 * démarches ({@link fr.reborn.hud.animation.MovementAnimations}) via une priorité
 * plus haute. Deux familles :
 * <ul>
 *   <li><b>Coups (one-shot)</b> : combo M1 = {@code strike1/2/3} + {@code pyramid}
 *       (finisher). Jouées une fois puis le layer se tait (les démarches reprennent).</li>
 *   <li><b>Garde (hold)</b> : M2 = {@code block}, jouée en {@code hold_on_last_frame}
 *       (windup ~0,4s puis dernière frame figée) tant que le clic droit est maintenu.
 *       Arrêtée explicitement à la relâche.</li>
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
    private static final int STRIKE_FADE = 2, BLOCK_FADE = 2;

    // animId réseau (contrat serveur↔client, cf. ShinobiCombat).
    public static final int ANIM_STRIKE1 = 1, ANIM_STRIKE2 = 2, ANIM_STRIKE3 = 3,
        ANIM_PYRAMID = 4, ANIM_BLOCK_ON = 5, ANIM_BLOCK_OFF = 6;

    /** Coups M1 indexés par (animId-1) : strike1, strike2, strike3, pyramid(finisher). */
    private final Animation[] strikes = new Animation[4];
    private Animation block;
    private boolean available = false;

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
            available = true;
            LOG.info("anims combat : strikes={}, block={}", countNonNull(strikes), block != null);
        } catch (Throwable t) {
            available = false;
            LOG.warn("anims combat indisponibles ({})", t.toString());
        }
    }

    /**
     * Joue une animation de combat sur l'avatar. {@code animId} : 1-4 = coups (one-shot),
     * 5 = garde ON (hold), 6 = garde OFF (stop). No-op si l'anim est absente.
     */
    public void play(AbstractClientPlayer player, int animId) {
        if (!available || player == null) return;
        PlayerAnimationController c = controllerOf(player);
        if (c == null) return;

        if (animId == ANIM_BLOCK_OFF) {
            c.stopTriggeredAnimation();
            return;
        }
        Animation anim = switch (animId) {
            case ANIM_STRIKE1, ANIM_STRIKE2, ANIM_STRIKE3, ANIM_PYRAMID -> strikes[animId - 1];
            case ANIM_BLOCK_ON -> block;
            default -> null;
        };
        if (anim == null) return;
        int fade = animId == ANIM_BLOCK_ON ? BLOCK_FADE : STRIKE_FADE;
        // Coups : loop intrinsèque = play-once. Garde : block.json est en
        // hold_on_last_frame → tient la dernière frame jusqu'au stop (relâche M2).
        c.replaceAnimationWithFade(
            AbstractFadeModifier.standardFadeIn(fade, EasingType.EASE_IN_OUT_SINE),
            RawAnimation.begin().then(anim, anim.loopType()));
    }

    /** Joue par id d'entité (réception réseau) : résout l'avatar puis délègue. */
    public void playByEntityId(int entityId, int animId) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        if (level.getEntity(entityId) instanceof AbstractClientPlayer p) play(p, animId);
    }

    private PlayerAnimationController controllerOf(AbstractClientPlayer player) {
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
