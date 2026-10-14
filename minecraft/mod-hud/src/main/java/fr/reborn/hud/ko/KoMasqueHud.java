package fr.reborn.hud.ko;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * À terre (agonie) et inconscient : <b>plus de HUD</b> (retour du 14/10) — viseur, barre d'action, cœurs, faim,
 * armure, air, expérience, effets, barre de boss, tableau, message d'action, et le HUD Reborn de jeu (vitals RP,
 * endurance, recharges, concentration, allure, notifications, ramassage, interaction, duel). Restent : le tchat (on
 * chuchote, on lit les /me), les titres, la liste des joueurs, les bulles et les noms, et l'info du KO ({@link KoHud}).
 * Le contour de sélection de bloc disparaît aussi. En douleur, le HUD reste normal.
 *
 * <p>Chaque élément est enveloppé une fois, au démarrage du client (après l'enregistrement de tous les éléments du
 * mod), par {@link HudElementRegistry#replaceElement}.
 */
public final class KoMasqueHud {

    private static final String[] REBORN = {"vitals", "combat", "combat-endurance", "cooldowns", "concentration",
        "allure", "notifs", "ramasser", "interaction", "duel", "byakugan_activation"};

    private KoMasqueHud() {}

    public static void init() {
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> envelopper());
        // ni contour de sélection de bloc (on ne vise plus rien)
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.BEFORE_BLOCK_OUTLINE
            .register((ctx, etat) -> !masque());
    }

    /** Agonie ou inconscient : le HUD de jeu est masqué. */
    public static boolean masque() {
        KoPayload s = KoClient.state();
        return s != null && (s.phase() == 1 || s.phase() == 2);
    }

    private static void envelopper() {
        List<Identifier> ids = new ArrayList<>(List.of(VanillaHudElements.CROSSHAIR, VanillaHudElements.HOTBAR,
            VanillaHudElements.ARMOR_BAR, VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR,
            VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.INFO_BAR,
            VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.MOB_EFFECTS,
            VanillaHudElements.BOSS_BAR, VanillaHudElements.SCOREBOARD, VanillaHudElements.OVERLAY_MESSAGE));
        for (String r : REBORN) ids.add(Identifier.fromNamespaceAndPath("reborn-hud", r));
        for (Identifier id : ids) {
            try {
                HudElementRegistry.replaceElement(id, orig -> (g, dt) -> {
                    if (!masque()) orig.extractRenderState(g, dt);
                });
            } catch (RuntimeException ignored) {
                // élément absent (désactivé, ou pas encore enregistré) : on laisse
            }
        }
    }
}
