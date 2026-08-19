package com.reborn.shinobicore.character.gui;

import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.gui.CoreGuiRouter;
import com.reborn.shinobicore.gui.CoreScreen;
import com.reborn.shinobicore.gui.framework.Ui;
import com.reborn.shinobicore.gui.framework.View;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;

/**
 * Root screen of the {@code /rencontrer} flow — framework port of the
 * legacy {@code RencontrerRootGui}: "Donner ton nom" opens the give
 * flow, "Demander un nom" ray-traces the player you're looking at and
 * sends them the confirm screen. The shared ray-trace + reveal helpers
 * for the whole cluster live here (they were GuiListener privates).
 */
public final class RencontrerRootScreen extends CoreScreen {

    private static final int SLOT_GIVE  = 3;
    private static final int SLOT_ASK   = 5;
    private static final int SLOT_CLOSE = 8;

    public RencontrerRootScreen(CoreGuiRouter router) {
        super(router);
    }

    public void open(Player viewer) {
        router.screens().open(viewer, this, Map.of());
    }

    @Override
    public Component title(Player viewer, View view) {
        return GuiTitles.framedWithCharacter("Rencontrer",
                core().characters().getActive(viewer.getUniqueId()));
    }

    @Override
    public int rows(View view) {
        return 1;
    }

    @Override
    public void render(Player viewer, View view, Inventory inv) {
        Ui.frame(inv);
        inv.setItem(SLOT_GIVE, Ui.action(GuiIcons.primary(Material.WRITABLE_BOOK,
                "Donner ton nom",
                "&eOffre ton identité à quelqu'un —",
                "vrai Nom + Clan ou un surnom."), "give"));
        inv.setItem(SLOT_ASK, Ui.action(GuiIcons.info(Material.COMPASS,
                "Demander un nom",
                "&eCible le joueur que tu regardes",
                "et lui demande de se présenter."), "ask"));
        inv.setItem(SLOT_CLOSE, Ui.action(GuiIcons.closeButton(), Ui.ACTION_CLOSE));
        Ui.fillEmpty(inv);
    }

    @Override
    public void onAction(Player viewer, View view, String action,
                         String value, InventoryClickEvent event) {
        switch (action) {
            case "give" -> {
                ShinobiCharacter giverChar =
                        core().characters().getActive(viewer.getUniqueId());
                if (giverChar == null) {
                    viewer.closeInventory();
                    msg(viewer, "Il te faut un personnage actif pour /rencontrer.",
                            NamedTextColor.RED);
                    return;
                }
                core().rencontrer().startDraft(viewer.getUniqueId());
                router.openGiveName(viewer);
            }
            case "ask" -> handleAskRayTrace(viewer);
            default -> { }
        }
    }

    /* -------------------------------------- shared /rencontrer helpers */

    /** Fire the "ask for a name" flow: ray-trace a player in front of
     *  {@code viewer}, stash the request, open the confirm screen on them. */
    private void handleAskRayTrace(Player viewer) {
        ShinobiCharacter requesterChar =
                core().characters().getActive(viewer.getUniqueId());
        if (requesterChar == null) {
            viewer.closeInventory();
            msg(viewer, "Il te faut un personnage actif pour /rencontrer.",
                    NamedTextColor.RED);
            return;
        }
        Player target = rayTracePlayer(viewer, 10.0);
        viewer.closeInventory();
        if (target == null) {
            msg(viewer, "Personne en face de toi (à moins de 10 blocs).",
                    NamedTextColor.RED);
            return;
        }
        if (target.getUniqueId().equals(viewer.getUniqueId())) {
            msg(viewer, "C'est toi.", NamedTextColor.YELLOW);
            return;
        }
        ShinobiCharacter targetChar =
                core().characters().getActive(target.getUniqueId());
        if (targetChar == null) {
            msg(viewer, target.getName() + " n'a pas de personnage actif.",
                    NamedTextColor.YELLOW);
            return;
        }
        core().rencontrer().openRequest(viewer.getUniqueId(), target.getUniqueId());
        router.openNameRequest(target, viewer.getUniqueId(), viewer.getName());
        msg(viewer, "Demande envoyée à " + target.getName() + ".",
                NamedTextColor.GREEN);
        // Target hears a bell — they're the one being asked to look up.
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.6f);
        GuiSounds.navigate(viewer);
    }

    /** Ray-trace entities up to {@code maxDist} blocks from the player's
     *  eye along their look direction; first hit player, self-ignoring. */
    Player rayTracePlayer(Player viewer, double maxDist) {
        Location eye = viewer.getEyeLocation();
        var trace = viewer.getWorld().rayTraceEntities(
                eye, eye.getDirection(), maxDist,
                /* raySize */ 0.25,
                entity -> entity instanceof Player other
                        && !other.getUniqueId().equals(viewer.getUniqueId()));
        if (trace == null) return null;
        Entity hit = trace.getHitEntity();
        return hit instanceof Player p ? p : null;
    }

    /** Reveal the giver's identity to one receiver. {@code nickname}
     *  null = real-name reveal. */
    void revealGive(Player giver, ShinobiCharacter giverChar,
                    Player receiver, String nickname) {
        if (receiver == null || !receiver.isOnline()) return;
        ShinobiCharacter receiverChar =
                core().characters().getActive(receiver.getUniqueId());
        if (receiverChar == null) {
            msg(giver, receiver.getName() + " n'a pas de personnage actif.",
                    NamedTextColor.YELLOW);
            return;
        }
        if (nickname == null) {
            String display = com.reborn.shinobicore.character.CharacterDisplay
                    .realNameString(giverChar);
            core().rencontrer().reveal(giverChar, receiverChar, giver, receiver, display, false);
        } else {
            core().rencontrer().reveal(giverChar, receiverChar, giver, receiver, nickname, true);
        }
    }

    /** Reveal to every other player within {@code radius} blocks. */
    int revealAround(Player giver, ShinobiCharacter giverChar,
                     String nickname, double radius) {
        int count = 0;
        double r2 = radius * radius;
        Location origin = giver.getLocation();
        for (Player other : giver.getWorld().getPlayers()) {
            if (other.getUniqueId().equals(giver.getUniqueId())) continue;
            if (other.getLocation().distanceSquared(origin) > r2) continue;
            revealGive(giver, giverChar, other, nickname);
            count++;
        }
        return count;
    }

    static void msg(Player p, String text, NamedTextColor colour) {
        p.sendMessage(Component.text(text, colour));
    }
}
