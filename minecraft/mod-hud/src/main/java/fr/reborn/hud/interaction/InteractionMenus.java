package fr.reborn.hud.interaction;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;

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
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            mc.getConnection().sendCommand(command);
        }
    }

    /** Affiche un message d'info côté client (chat local, pas envoyé au serveur). */
    public static void info(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§6[Reborn] §f" + text));
        }
    }

    /** Copie du texte dans le presse-papiers. */
    public static void copy(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        info("Copié : §e" + text);
    }

    public static List<InteractionItem> forPlayer(String name) {
        // NB : les commandes ShinobiCore /rencontrer /amitier /osculter ciblent
        // « la personne regardée » (pas d'argument) → fonctionnent si la cible est
        // à peu près devant. Les autres (porter/fouiller…) attendent un plugin.
        return List.of(
            InteractionItem.action("Saluer", () -> sendCommand("me salue " + name)),
            InteractionItem.action("Se présenter", () -> sendCommand("rencontrer")),
            InteractionItem.action("Message privé", () -> sendCommand("msg " + name + " ")),
            InteractionItem.action("Demander en ami", () -> sendCommand("amitier")),
            InteractionItem.action("Ausculter (état)", () -> sendCommand("osculter")),
            InteractionItem.action("Porter", () -> sendCommand("porter " + name)),
            InteractionItem.action("Fouiller", () -> sendCommand("fouiller " + name)),
            InteractionItem.action("Échanger", () -> sendCommand("trade " + name)),
            InteractionItem.submenu("Animations", List.of(
                InteractionItem.action("Saluer (geste)", () -> sendCommand("playemote wave")),
                InteractionItem.action("S'incliner", () -> sendCommand("playemote bow")),
                InteractionItem.action("Applaudir", () -> sendCommand("playemote clap")),
                InteractionItem.action("S'asseoir", () -> sendCommand("playemote sit")),
                InteractionItem.action("Toutes les emotes…", InteractionMenus::openEmoteMenu),
                InteractionItem.action("Arrêter", () -> sendCommand("stopemote"))
            )),
            InteractionItem.submenu("Staff", List.of(
                InteractionItem.action("Informations", () -> sendCommand("staff lookup " + name)),
                InteractionItem.action("Téléporter à", () -> sendCommand("tp " + name)),
                InteractionItem.action("Geler", () -> sendCommand("freeze " + name)),
                InteractionItem.action("Sanctionner", () -> sendCommand("sanction " + name))
            ))
        );
    }

    public static List<InteractionItem> forEntity(Entity e) {
        String type = e.getType().getDescription().getString();
        return List.of(
            InteractionItem.action("Inspecter (" + type + ")", () -> {
                String hp = (e instanceof LivingEntity le)
                    ? " §7| PV " + (int) le.getHealth() + "/" + (int) le.getMaxHealth() : "";
                info("§e" + type + " §7| pos " + e.blockPosition().toShortString() + hp);
            }),
            InteractionItem.action("Copier la position",
                () -> copy(e.getBlockX() + " " + e.getBlockY() + " " + e.getBlockZ()))
        );
    }

    public static List<InteractionItem> forBlock(BlockPos pos) {
        return List.of(
            InteractionItem.action("Inspecter le bloc", () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null) return;
                BlockState bs = mc.level.getBlockState(pos);
                info("§e" + BuiltInRegistries.BLOCK.getKey(bs.getBlock())
                    + " §7@ " + pos.toShortString());
            }),
            InteractionItem.action("Verrouiller / Cadenas", () -> sendCommand("lock")),
            InteractionItem.action("Gestion", () -> sendCommand("manage")),
            InteractionItem.action("Copier les coordonnées",
                () -> copy(pos.getX() + " " + pos.getY() + " " + pos.getZ())),
            InteractionItem.submenu("Outils de debug", List.of(
                InteractionItem.action("Afficher l'état complet", () -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.level != null) info("§7" + mc.level.getBlockState(pos).toString());
                })
            ))
        );
    }

    /** Menu « sur soi » (clic dans le vide). Mappe de vraies commandes ShinobiCore. */
    public static List<InteractionItem> forSelf() {
        return List.of(
            InteractionItem.action("Ma fiche personnage", () -> sendCommand("character")),
            InteractionItem.action("Mon état", () -> sendCommand("etat")),
            InteractionItem.action("Méditer", () -> sendCommand("meditation")),
            InteractionItem.submenu("Mes emotes", List.of(
                InteractionItem.action("Saluer (geste)", () -> sendCommand("playemote wave")),
                InteractionItem.action("S'incliner", () -> sendCommand("playemote bow")),
                InteractionItem.action("Applaudir", () -> sendCommand("playemote clap")),
                InteractionItem.action("S'asseoir", () -> sendCommand("playemote sit")),
                InteractionItem.action("Toutes les emotes…", InteractionMenus::openEmoteMenu),
                InteractionItem.action("Arrêter", () -> sendCommand("stopemote"))
            )),
            InteractionItem.action("Action RP (/me)", () -> sendCommand("me "))
        );
    }

    /** Ouvre le menu Reborn directement sur l'onglet ANIMATIONS (liste d'emotes). */
    public static void openEmoteMenu() {
        Minecraft.getInstance().setScreenAndShow(
            fr.reborn.hud.animation.AnimationMenuScreen.emotesTab(null));
    }

    public static List<InteractionItem> generic() {
        return List.of(
            InteractionItem.action("Rien à proximité", () -> {})
        );
    }
}
