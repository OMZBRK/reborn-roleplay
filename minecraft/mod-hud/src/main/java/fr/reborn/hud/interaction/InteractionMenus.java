package fr.reborn.hud.interaction;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Construit le menu d'interaction (style GTA) selon la cible visée et
 * dispatche les actions vers des commandes serveur.
 *
 * <p><b>Dépendance serveur</b> : les commandes (saluer/porter/fouiller…) doivent
 * exister côté plugin (ShinobiCore &amp; co). Tant qu'elles n'existent pas, elles
 * apparaîtront comme « commande inconnue ». Le framework client (menu +
 * sous-menus + dispatch) est complet ; il suffira de mapper les vraies commandes.
 */
public final class InteractionMenus {

    private InteractionMenus() {}

    /** Envoie une commande au serveur (sans le slash). */
    public static void sendCommand(String command) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendChatCommand(command);
        }
    }

    /** Ouvre le menu pour ce que le joueur vise actuellement (crosshairTarget). */
    public static void openForCrosshair(MinecraftClient mc) {
        if (mc == null || mc.currentScreen != null || mc.player == null) return;
        HitResult hit = mc.crosshairTarget;
        String title;
        List<InteractionItem> items;
        if (hit instanceof EntityHitResult ehr) {
            Entity e = ehr.getEntity();
            if (e instanceof PlayerEntity pe) {
                title = pe.getGameProfile().getName();
                items = forPlayer(title);
            } else {
                title = e.getType().getName().getString();
                items = forEntity(e);
            }
        } else if (hit instanceof BlockHitResult bhr) {
            title = "Bloc";
            items = forBlock(bhr.getBlockPos());
        } else {
            title = "Interaction";
            items = generic();
        }
        mc.setScreen(new InteractionMenuScreen(title, items));
    }

    public static List<InteractionItem> forPlayer(String name) {
        return List.of(
            InteractionItem.action("Saluer", () -> sendCommand("saluer " + name)),
            InteractionItem.action("Message privé", () -> sendCommand("msg " + name + " ")),
            InteractionItem.action("Porter", () -> sendCommand("porter " + name)),
            InteractionItem.action("Fouiller", () -> sendCommand("fouiller " + name)),
            InteractionItem.submenu("Animations", List.of(
                InteractionItem.action("Saluer (geste)", () -> sendCommand("emote wave")),
                InteractionItem.action("S'asseoir", () -> sendCommand("emote sit")),
                InteractionItem.action("Applaudir", () -> sendCommand("emote clap"))
            )),
            InteractionItem.submenu("Admin", List.of(
                InteractionItem.action("Informations", () -> sendCommand("staff info " + name)),
                InteractionItem.action("Téléporter à", () -> sendCommand("tp " + name))
            ))
        );
    }

    public static List<InteractionItem> forEntity(Entity e) {
        String type = e.getType().getName().getString();
        return List.of(
            InteractionItem.action("Inspecter (" + type + ")", () -> {}),
            InteractionItem.action("Outils de debug", () -> {})
        );
    }

    public static List<InteractionItem> forBlock(BlockPos pos) {
        return List.of(
            InteractionItem.action("Ouvrir / Interagir", () -> {}),
            InteractionItem.action("Inspecter le bloc", () -> {}),
            InteractionItem.submenu("Outils de debug", List.of(
                InteractionItem.action("Copier les coordonnées",
                    () -> MinecraftClient.getInstance().keyboard.setClipboard(
                        pos.getX() + " " + pos.getY() + " " + pos.getZ()))
            ))
        );
    }

    public static List<InteractionItem> generic() {
        return List.of(
            InteractionItem.action("Rien à proximité", () -> {})
        );
    }
}
