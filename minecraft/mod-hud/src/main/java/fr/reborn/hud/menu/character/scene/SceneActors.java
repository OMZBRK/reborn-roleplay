package fr.reborn.hud.menu.character.scene;

import com.zigythebird.playeranimcore.animation.Animation;
import fr.reborn.hud.animation.MovementAnimations;
import fr.reborn.hud.mixin.MannequinAccessor;
import io.github.kosmx.emotes.main.mixinFunctions.IPlayerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientMannequin;
import fr.reborn.hud.skin.RebornSkins;
import fr.reborn.hud.skin.SkinSpec;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Acteurs de mise en scène : mannequins côté client (aucun paquet serveur), posés dans le monde, avec le skin et
 * l'animation voulus. Limite connue : les mannequins ne rendent pas le <i>pli</i> des membres (coudes, genoux) —
 * les poses se font membres droits.
 */
public final class SceneActors {

    public record Skin(Identifier texture, boolean slim) {}

    private static final Map<Integer, Skin> SKINS = new ConcurrentHashMap<>();

    private SceneActors() {}

    public static Skin skinOf(int entityId) { return SKINS.get(entityId); }

    public static ClientMannequin spawn(Minecraft mc, int id, Vec3 feet, float yaw, Identifier skin, boolean slim,
                                        String anim, float animOffset) {
        ClientMannequin m = new ClientMannequin(mc.level, mc.playerSkinRenderCache());
        m.setId(id);
        m.snapTo(feet.x, feet.y, feet.z, yaw, 0);
        m.setYHeadRot(yaw);
        m.setYBodyRot(yaw);
        m.setNoGravity(true);
        ((MannequinAccessor) m).reborn$setHideDescription(true);
        ((MannequinAccessor) m).reborn$setImmovable(true);
        SKINS.put(id, new Skin(skin, slim));
        mc.level.addEntity(m);
        play(m, anim, animOffset);
        return m;
    }

    /** Acteur habillé par le compositeur de skin Reborn (même rendu qu'en jeu). */
    public static ClientMannequin spawn(Minecraft mc, int id, Vec3 feet, float yaw, SkinSpec spec, String anim,
                                        float animOffset) {
        return spawn(mc, id, feet, yaw, compose(id, spec), spec.slim, anim, animOffset);
    }

    /** Recompose le skin d'un acteur (création : chaque changement d'apparence). */
    public static void reskin(ClientMannequin m, SkinSpec spec) {
        SKINS.put(m.getId(), new Skin(compose(m.getId(), spec), spec.slim));
    }

    private static Identifier compose(int id, SkinSpec spec) {
        UUID u = UUID.nameUUIDFromBytes(("reborn-scene:" + id).getBytes(StandardCharsets.UTF_8));
        RebornSkins.applySpec(u, spec);
        Identifier t = RebornSkins.overrideFor(u);
        return t != null ? t : vanillaSkin("wide/steve");
    }

    /**
     * Échelle visible du perso : la taille choisie (0.85–1.15) et l'âge — un enfant de 10 ans fait ~0.78 de la
     * taille adulte, la croissance se termine vers 17 ans.
     */
    public static void setBody(ClientMannequin m, int age, double size) {
        double growth = age >= 17 ? 1.0 : 0.78 + (Math.max(10, age) - 10) * (0.22 / 7.0);
        var attr = m.getAttribute(Attributes.SCALE);
        if (attr != null) attr.setBaseValue(Math.max(0.5, Math.min(1.3, growth * size)));
    }

    public static double scaleOf(ClientMannequin m) {
        var attr = m.getAttribute(Attributes.SCALE);
        return attr == null ? 1.0 : attr.getValue();
    }

    /** Vrai pour les acteurs de scène (éclairage studio, voir {@code SceneLightMixin}). */
    public static boolean isActor(int id) { return SKINS.containsKey(id); }

    public static void play(ClientMannequin m, String anim, float offset) {
        if (anim == null) return;
        Animation a = MovementAnimations.INSTANCE.asset(anim);
        if (a == null) return;
        try { ((IPlayerEntity) m).emotecraft$playEmote(a, offset, true); }
        catch (Throwable ignored) { }
    }

    public static void remove(Minecraft mc, ClientMannequin m) {
        SKINS.remove(m.getId());
        if (mc.level != null) mc.level.removeEntity(m.getId(), Entity.RemovalReason.DISCARDED);
    }

    /** Skin par défaut de Minecraft (maquettes) : {@code wide/kai}, {@code slim/zuri}… */
    public static Identifier vanillaSkin(String key) {
        return Identifier.withDefaultNamespace("textures/entity/player/" + key + ".png");
    }
}
