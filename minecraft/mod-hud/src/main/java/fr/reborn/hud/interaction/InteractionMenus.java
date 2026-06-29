package fr.reborn.hud.interaction;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
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

    /** Affiche un message d'info côté client (chat local, pas envoyé au serveur). */
    public static void info(String text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null) {
            mc.inGameHud.getChatHud().addMessage(Text.literal("§6[Reborn] §f" + text));
        }
    }

    /** Copie du texte dans le presse-papiers. */
    public static void copy(String text) {
        MinecraftClient.getInstance().keyboard.setClipboard(text);
        info("Copié : §e" + text);
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
            InteractionItem.action("Inspecter (" + type + ")", () -> {
                String hp = (e instanceof LivingEntity le)
                    ? " §7| PV " + (int) le.getHealth() + "/" + (int) le.getMaxHealth() : "";
                info("§e" + type + " §7| pos " + e.getBlockPos().toShortString() + hp);
            }),
            InteractionItem.action("Copier la position",
                () -> copy(e.getBlockX() + " " + e.getBlockY() + " " + e.getBlockZ()))
        );
    }

    public static List<InteractionItem> forBlock(BlockPos pos) {
        return List.of(
            InteractionItem.action("Inspecter le bloc", () -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.world == null) return;
                BlockState bs = mc.world.getBlockState(pos);
                info("§e" + Registries.BLOCK.getId(bs.getBlock())
                    + " §7@ " + pos.toShortString());
            }),
            InteractionItem.action("Copier les coordonnées",
                () -> copy(pos.getX() + " " + pos.getY() + " " + pos.getZ())),
            InteractionItem.submenu("Outils de debug", List.of(
                InteractionItem.action("Afficher l'état complet", () -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.world != null) info("§7" + mc.world.getBlockState(pos).toString());
                })
            ))
        );
    }

    public static List<InteractionItem> generic() {
        return List.of(
            InteractionItem.action("Rien à proximité", () -> {})
        );
    }
}
