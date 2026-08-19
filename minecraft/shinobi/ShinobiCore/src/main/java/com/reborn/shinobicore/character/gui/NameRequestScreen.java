package com.reborn.shinobicore.character.gui;

import com.reborn.shinobicore.character.CharacterDisplay;
import com.reborn.shinobicore.character.RencontrerManager;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.gui.CoreGuiRouter;
import com.reborn.shinobicore.gui.CoreScreen;
import com.reborn.shinobicore.gui.framework.Ui;
import com.reborn.shinobicore.gui.framework.View;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;

/**
 * Confirm screen shown to the TARGET of a name request — framework
 * port of the legacy {@code NameRequestGui}: accept (real name),
 * accept with a nickname (chat prompt until B3), or decline. Lookups
 * prefer the manager's pending record so a requester disconnect never
 * leaves the screen holding a stale UUID. Notification strings are now
 * French, per the house rules.
 */
public final class NameRequestScreen extends CoreScreen {

    static final String S_REQUESTER = "requesterMc";
    static final String S_REQ_NAME  = "requesterName";

    private static final int SLOT_ACCEPT      = 2;
    private static final int SLOT_ACCEPT_NICK = 4;
    private static final int SLOT_DECLINE     = 6;

    public NameRequestScreen(CoreGuiRouter router) {
        super(router);
    }

    public void open(Player target, UUID requesterMc, String requesterDisplay) {
        router.screens().open(target, this, Map.of(
                S_REQUESTER, requesterMc,
                S_REQ_NAME, requesterDisplay));
    }

    @Override
    public Component title(Player viewer, View view) {
        return GuiTitles.framedWithCharacter("Demande de " + view.string(S_REQ_NAME),
                core().characters().getActive(viewer.getUniqueId()));
    }

    @Override
    public int rows(View view) {
        return 1;
    }

    @Override
    public void render(Player viewer, View view, Inventory inv) {
        Ui.frame(inv);
        inv.setItem(SLOT_ACCEPT, Ui.action(GuiIcons.primary(Material.LIME_DYE,
                "Accepter — révéler le vrai nom",
                "&aElle verra ton vrai Nom + Clan à partir de maintenant."), "accept"));
        inv.setItem(SLOT_ACCEPT_NICK, Ui.action(GuiIcons.secondary(Material.NAME_TAG,
                "Accepter — donner un surnom",
                "&eFerme le menu et te demande de taper le",
                "surnom dans le chat. Elle te connaîtra sous ce nom."), "acceptnick"));
        inv.setItem(SLOT_DECLINE, Ui.action(GuiIcons.destructive(Material.BARRIER,
                "Refuser",
                "&cElle n'apprendra pas ton nom.",
                "Le demandeur est notifié."), "decline"));
        Ui.fillEmpty(inv);
    }

    @Override
    public void onAction(Player target, View view, String action,
                         String value, InventoryClickEvent event) {
        ShinobiCharacter targetChar =
                core().characters().getActive(target.getUniqueId());
        if (targetChar == null) { target.closeInventory(); return; }

        // Prefer the manager's pending record over the view's stashed
        // UUID so a disconnect doesn't leave us with a stale requester.
        RencontrerManager.NameRequest pending =
                core().rencontrer().peekRequest(target.getUniqueId());
        UUID requesterMc = pending != null ? pending.requesterMc()
                : view.uuid(S_REQUESTER);
        Player requester = requesterMc == null ? null
                : core().getServer().getPlayer(requesterMc);

        switch (action) {
            case "accept" -> {
                core().rencontrer().consumeRequest(target.getUniqueId());
                target.closeInventory();
                if (requester == null || !requester.isOnline()) {
                    RencontrerRootScreen.msg(target,
                            "Le demandeur est parti.", NamedTextColor.RED);
                    return;
                }
                ShinobiCharacter requesterChar =
                        core().characters().getActive(requester.getUniqueId());
                if (requesterChar == null) {
                    RencontrerRootScreen.msg(target,
                            "Le demandeur n'a pas de personnage actif.", NamedTextColor.RED);
                    return;
                }
                String display = CharacterDisplay.realNameString(targetChar);
                // Giver here is the TARGET (they gave their identity); no
                // save-prompt fires because real names aren't nicknames.
                core().rencontrer().reveal(targetChar, requesterChar,
                        target, requester, display, false);
                GuiSounds.accept(target);
                GuiSounds.accept(requester);
            }
            case "acceptnick" -> {
                core().rencontrer().consumeRequest(target.getUniqueId());
                if (requester == null || !requester.isOnline()) {
                    target.closeInventory();
                    RencontrerRootScreen.msg(target,
                            "Le demandeur est parti.", NamedTextColor.RED);
                    return;
                }
                Player req = requester;
                core().chatInputs().prompt(target,
                        "Tape le surnom sous lequel tu veux être connu (ou 'cancel').",
                        raw -> {
                            String nick = raw.trim();
                            if (nick.isEmpty()) {
                                RencontrerRootScreen.msg(target,
                                        "Le surnom ne peut pas être vide.", NamedTextColor.RED);
                                return;
                            }
                            if (!req.isOnline()) {
                                RencontrerRootScreen.msg(target,
                                        "Le demandeur est parti.", NamedTextColor.RED);
                                return;
                            }
                            ShinobiCharacter requesterChar =
                                    core().characters().getActive(req.getUniqueId());
                            if (requesterChar == null) {
                                RencontrerRootScreen.msg(target,
                                        "Le demandeur n'a pas de personnage actif.",
                                        NamedTextColor.RED);
                                return;
                            }
                            core().rencontrer().reveal(targetChar, requesterChar,
                                    target, req, nick, true);
                            GuiSounds.accept(target);
                            GuiSounds.accept(req);
                        },
                        null);
            }
            case "decline" -> {
                core().rencontrer().consumeRequest(target.getUniqueId());
                target.closeInventory();
                RencontrerRootScreen.msg(target, "Demande refusée.", NamedTextColor.GRAY);
                GuiSounds.decline(target);
                if (requester != null && requester.isOnline()) {
                    RencontrerRootScreen.msg(requester,
                            target.getName() + " a refusé de partager son nom.",
                            NamedTextColor.RED);
                    GuiSounds.decline(requester);
                }
            }
            default -> { }
        }
    }
}
