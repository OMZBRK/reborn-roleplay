package fr.reborn.hud.meditation;

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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * <b>Pose de méditation</b> (v4 du 18/10) : faite dans Blender sur le rig d'Emotecraft et calée sur la vidéo de
 * référence ({@code tools/emotes/}, spec {@code specs/meditation.json}) — assis très bas en tailleur, bras croisés haut
 * sur la poitrine, dos rond, tête baissée, respiration 4 s et balancement 8 s (boucle de 8 s).
 * {@code animations/meditation.json} sur un layer PlayerAnimationLib dédié, jouée en boucle sur <b>chaque
 * méditant connu de {@link MeditationClient}</b> (soi et les autres, même liste que le halo : aucune synchro de plus),
 * fondu d'entrée de 0,4 s ; quand la méditation s'arrête, {@code meditation_fin.json} ramène les membres en fondu.
 *
 * <p>Vérifié à chaque tick (un contrôleur par méditant suivi, au plus {@value MeditationClient#CAPACITE}) : si l'entité
 * réapparaît (retour à portée), l'animation est relancée. Rien quand personne ne médite. Inerte si PlayerAnimationLib
 * manque.
 */
public final class MeditationAnimation {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/meditation-anim");
    /** Au-dessus du combat (2000), sous le test de la feuille (2100). */
    private static final int PRIORITE = 2050;
    private static final Identifier LAYER = Identifier.fromNamespaceAndPath("reborn-hud", "meditation");
    private static final int FONDU_ENTREE = 8, FONDU_SORTIE = 6;

    private static Animation pose, fin;
    private static boolean dispo;
    /** Par emplacement de {@link MeditationClient} : identifiant de l'entité animée (−1 = rien). */
    private static final int[] ANIME = new int[MeditationClient.CAPACITE];
    /** Banc : animation essayée à la place de la pose (null = la pose). */
    static Animation essai;

    private MeditationAnimation() {}

    static void register() {
        java.util.Arrays.fill(ANIME, -1);
        try {
            PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITE,
                    avatar -> new PlayerAnimationController(avatar, (ctrl, data, setter) -> PlayState.STOP));
            pose = charger("meditation.json");
            fin = charger("meditation_fin.json");
            dispo = pose != null;
        } catch (Throwable t) {
            dispo = false;
            LOG.warn("pose de méditation indisponible ({})", t.toString());
        }
    }

    /** Chaque tick (après le relevé du halo) : lance, relance ou arrête la pose de chaque méditant. */
    static void tick(ClientLevel level) {
        if (!dispo) return;
        for (int i = 0; i < MeditationClient.CAPACITE; i++) {
            boolean doit = MeditationClient.ACTIF[i] && Double.isNaN(MeditationClient.FIN[i]);
            int id = doit ? MeditationClient.ID[i] : -1;
            if (ANIME[i] >= 0 && ANIME[i] != id) {                       // fin de méditation (ou emplacement réutilisé)
                arreter(level, ANIME[i]);
                ANIME[i] = -1;
            }
            if (id < 0) continue;
            Entity e = level.getEntity(id);
            if (!(e instanceof Avatar a)) continue;
            PlayerAnimationController c = controleur(a);
            if (c == null) continue;
            if (ANIME[i] != id || !c.isActive()) {
                Animation p = essai != null ? essai : pose;
                c.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FONDU_ENTREE, EasingType.EASE_IN_OUT_SINE),
                        RawAnimation.begin().then(p, Animation.LoopType.LOOP));
                ANIME[i] = id;
            }
        }
    }

    /** Banc : rejoue tout de suite la pose (ou l'essai) sur les méditants animés. */
    static void rejouer() { java.util.Arrays.fill(ANIME, -1); }

    static void vider() { java.util.Arrays.fill(ANIME, -1); }

    private static void arreter(ClientLevel level, int id) {
        Entity e = level.getEntity(id);
        if (!(e instanceof Avatar a)) return;
        PlayerAnimationController c = controleur(a);
        if (c == null) return;
        if (fin == null) { c.stop(); return; }
        c.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FONDU_SORTIE, EasingType.EASE_IN_OUT_SINE),
                RawAnimation.begin().then(fin, Animation.LoopType.PLAY_ONCE));
    }

    private static PlayerAnimationController controleur(Avatar a) {
        try {
            IAnimation layer = PlayerAnimationAccess.getPlayerAnimationLayer(a, LAYER);
            return layer instanceof PlayerAnimationController c ? c : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Banc : lit une animation d'essai dans un fichier (null si illisible). */
    static Animation lireFichier(java.nio.file.Path f) {
        try (Reader r = java.nio.file.Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            return premiere(r);
        } catch (Exception e) {
            LOG.error("essai de pose {} : {}", f, e.toString());
            return null;
        }
    }

    private static Animation charger(String fichier) {
        try (InputStream in = MeditationAnimation.class.getResourceAsStream("/assets/reborn-hud/animations/" + fichier)) {
            if (in == null) { LOG.warn("pose de méditation introuvable : {}", fichier); return null; }
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return premiere(r);
            }
        } catch (Exception e) {
            LOG.error("lecture de {} : {}", fichier, e.toString());
            return null;
        }
    }

    private static Animation premiere(Reader r) {
        JsonObject root = PlayerAnimLib.GSON.fromJson(r, JsonObject.class);
        JsonObject anims = root == null ? null : root.getAsJsonObject("animations");
        if (anims == null || anims.isEmpty()) return null;
        JsonElement first = anims.entrySet().iterator().next().getValue();
        return PlayerAnimLib.GSON.fromJson(first, Animation.class);
    }
}
