package fr.reborn.hud.menu.tirage;

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
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Animation de <b>hand-seal (mudra)</b> du test de la feuille, jouée sur un layer
 * PAL dédié en {@code hold_on_last_frame} : le perso exécute le signe puis tient
 * la pose du sceau pendant toute la canalisation, jusqu'à la révélation. Mirroir
 * minimal de {@link fr.reborn.hud.combat.CombatAnimations}.
 */
public final class TirageAnimations {

    public static final TirageAnimations INSTANCE = new TirageAnimations();
    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/tirage-anim");

    /** Au-dessus du combat (2000) — le sceau prime pendant le test. */
    private static final int PRIORITY = 2100;
    private static final Identifier LAYER = Identifier.fromNamespaceAndPath("reborn-hud", "tirage");
    private static final int FADE = 3;

    private Animation mudra;
    /** La feuille tenue entre deux doigts devant le visage (16/10) : montée 0,7 s puis respiration en boucle. */
    private Animation feuille, fin;
    private boolean available = false;

    private TirageAnimations() {}

    public void register() {
        try {
            PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                LAYER, PRIORITY,
                avatar -> new PlayerAnimationController(avatar, (ctrl, data, setter) -> PlayState.STOP));
            mudra = load("mudra_chakratest.json");
            feuille = load("feuille_canal.json");
            fin = load("feuille_fin.json");
            available = mudra != null || feuille != null;
            LOG.info("anim tirage : mudra={} feuille={}", mudra != null, feuille != null);
        } catch (Throwable t) {
            available = false;
            LOG.warn("anim tirage indisponible ({})", t.toString());
        }
    }

    /** Joue le mudra (hold_on_last_frame) sur l'avatar. */
    public void playMudra(AbstractClientPlayer player) {
        if (!available || mudra == null || player == null) return;
        PlayerAnimationController c = controllerOf(player);
        if (c == null) return;
        c.replaceAnimationWithFade(
            AbstractFadeModifier.standardFadeIn(FADE, EasingType.EASE_IN_OUT_SINE),
            RawAnimation.begin().then(mudra, mudra.loopType()));
    }

    /** Sort la feuille et canalise : la main droite la monte devant le visage, puis respiration (boucle dès 0,7 s). */
    public void playFeuille(AbstractClientPlayer player) {
        if (feuille == null || player == null) return;
        PlayerAnimationController c = controllerOf(player);
        if (c == null) return;
        c.replaceAnimationWithFade(
            AbstractFadeModifier.standardFadeIn(FADE + 2, EasingType.EASE_IN_OUT_SINE),
            RawAnimation.begin().then(feuille, Animation.LoopType.returnToTickLoop(14f)));
    }

    /** Coupe l'animation en fondu (bras qui redescendent) — fin du test. */
    public void stopDoux(AbstractClientPlayer player) {
        if (player == null) return;
        PlayerAnimationController c = controllerOf(player);
        if (c == null) return;
        if (fin == null) { c.stopTriggeredAnimation(); return; }
        c.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FADE, EasingType.EASE_IN_OUT_SINE),
            RawAnimation.begin().then(fin, Animation.LoopType.PLAY_ONCE));
    }

    /** Coupe le mudra (fin du test / fermeture). */
    public void stop(AbstractClientPlayer player) {
        if (player == null) return;
        PlayerAnimationController c = controllerOf(player);
        if (c != null) c.stopTriggeredAnimation();
    }

    /** Banc : joue une animation lue dans un fichier (essais de pose sans relancer le jeu). */
    void debugJouer(AbstractClientPlayer player, java.nio.file.Path fichier) {
        try (Reader r = java.nio.file.Files.newBufferedReader(fichier, StandardCharsets.UTF_8)) {
            JsonObject root = PlayerAnimLib.GSON.fromJson(r, JsonObject.class);
            JsonElement first = root.getAsJsonObject("animations").entrySet().iterator().next().getValue();
            Animation a = PlayerAnimLib.GSON.fromJson(first, Animation.class);
            PlayerAnimationController c = controllerOf(player);
            if (c != null) c.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(2, EasingType.EASE_IN_OUT_SINE),
                RawAnimation.begin().then(a, Animation.LoopType.HOLD_ON_LAST_FRAME));
        } catch (Exception e) {
            LOG.error("banc anim {} : {}", fichier, e.toString());
        }
    }

    private PlayerAnimationController controllerOf(AbstractClientPlayer player) {
        IAnimation layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER);
        return layer instanceof PlayerAnimationController c ? c : null;
    }

    private Animation load(String file) {
        try (InputStream in = TirageAnimations.class
                .getResourceAsStream("/assets/reborn-hud/animations/" + file)) {
            if (in == null) { LOG.warn("anim tirage introuvable : {}", file); return null; }
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject root = PlayerAnimLib.GSON.fromJson(r, JsonObject.class);
                JsonObject anims = root == null ? null : root.getAsJsonObject("animations");
                if (anims == null || anims.isEmpty()) return null;
                JsonElement first = anims.entrySet().iterator().next().getValue();
                return PlayerAnimLib.GSON.fromJson(first, Animation.class);
            }
        } catch (Exception e) {
            LOG.error("échec lecture anim tirage {} : {}", file, e.toString());
            return null;
        }
    }
}
