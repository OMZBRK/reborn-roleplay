package com.reborn.shinobicore.ko.gui;

import com.reborn.shinobicore.character.AutoMe;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.character.gui.GuiIcons;
import com.reborn.shinobicore.character.gui.GuiTitles;
import com.reborn.shinobicore.dummy.Dummy;
import com.reborn.shinobicore.gui.CoreGuiRouter;
import com.reborn.shinobicore.gui.CoreScreen;
import com.reborn.shinobicore.gui.framework.Ui;
import com.reborn.shinobicore.gui.framework.View;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;

/**
 * Right-click on a KO body opens this one-row action menu: État /
 * Tuer / Fouiller / Porter. Framework port of the legacy
 * {@code KoActionGui}; the two target kinds (real KO player vs
 * training dummy) keep their exact button behaviours, including the
 * dummy's "non applicable" replies for Tuer/Fouiller.
 */
public final class KoActionScreen extends CoreScreen {

    public enum Target { PLAYER, DUMMY }

    static final String S_KIND      = "kind";
    static final String S_TARGET_ID = "targetId";
    static final String S_TITLE     = "title";

    private static final int SLOT_ETAT     = 1;
    private static final int SLOT_TUER     = 3;
    private static final int SLOT_FOUILLER = 5;
    private static final int SLOT_PORTER   = 7;
    private static final int SLOT_CLOSE    = 8;

    private static final String ACT_ETAT     = "ko:etat";
    private static final String ACT_TUER     = "ko:tuer";
    private static final String ACT_FOUILLER = "ko:fouiller";
    private static final String ACT_PORTER   = "ko:porter";

    public KoActionScreen(CoreGuiRouter router) {
        super(router);
    }

    /* ------------------------------------------------------------- open */

    /** Open the menu against a real KO player. */
    public void open(Player viewer, UUID targetPlayerId, ShinobiCharacter targetChar) {
        router.screens().open(viewer, this, Map.of(
                S_KIND, Target.PLAYER,
                S_TARGET_ID, targetPlayerId,
                S_TITLE, targetChar != null ? "KO — " + targetChar.name() : "KO"));
    }

    /** Open the menu against a training dummy. */
    public void openForDummy(Player viewer, Dummy dummy) {
        router.screens().open(viewer, this, Map.of(
                S_KIND, Target.DUMMY,
                S_TARGET_ID, dummy.id(),
                S_TITLE, "Dummy — " + dummy.name()));
    }

    /* -------------------------------------------------------- rendering */

    @Override
    public Component title(Player viewer, View view) {
        return GuiTitles.framed(view.string(S_TITLE));
    }

    @Override
    public int rows(View view) {
        return 1;
    }

    @Override
    public void render(Player viewer, View view, Inventory inv) {
        boolean dummy = view.get(S_KIND) == Target.DUMMY;
        String porterTip = dummy
                ? "Mets la dummy sur tes épaules."
                : "Mets la personne sur tes épaules.";
        String fouillerTip = dummy
                ? "&7(Une dummy n'a pas d'inventaire.)"
                : "Ouvre l'inventaire et le sac de la personne.";
        String tuerTip = dummy
                ? "&7(Pas applicable. Utilise /dummy delete.)"
                : "Demande aux Staff à proximité de valider la mort.";

        inv.setItem(SLOT_ETAT, Ui.action(GuiIcons.info(Material.PAPER,
                "État",
                "&bExaminer les blessures",
                "et leur gravité."), ACT_ETAT));
        inv.setItem(SLOT_TUER, Ui.action(GuiIcons.destructive(Material.IRON_SWORD,
                "Tuer", tuerTip), ACT_TUER));
        inv.setItem(SLOT_FOUILLER, Ui.action(GuiIcons.secondary(Material.CHEST,
                "Fouiller", fouillerTip), ACT_FOUILLER));
        inv.setItem(SLOT_PORTER, Ui.action(GuiIcons.primary(Material.LEAD,
                "Porter",
                "&a" + porterTip,
                "Re-clique pour la déposer."), ACT_PORTER));
        inv.setItem(SLOT_CLOSE, Ui.action(GuiIcons.closeButton(), Ui.ACTION_CLOSE));
        Ui.fillEmpty(inv);
    }

    /* ------------------------------------------------------------ clicks */

    @Override
    public void onAction(Player viewer, View view, String action,
                         String value, InventoryClickEvent event) {
        if (view.get(S_KIND) == Target.DUMMY) {
            onDummyAction(viewer, view, action);
            return;
        }
        UUID targetId = view.uuid(S_TARGET_ID);
        Player target = core().getServer().getPlayer(targetId);
        if (target == null || !core().ko().isKo(targetId)) {
            viewer.sendMessage(Component.text(
                    "La cible n'est plus KO.", NamedTextColor.RED));
            viewer.closeInventory();
            return;
        }
        ShinobiCharacter targetChar = core().characters().getActive(targetId);
        String tName = targetChar != null ? targetChar.name() : target.getName();
        switch (action) {
            case ACT_ETAT -> {
                viewer.closeInventory();
                router.openEtat(viewer, targetId, targetChar);
                AutoMe.broadcast(core(), viewer,
                        "examine attentivement la silhouette de " + tName + ".");
            }
            case ACT_FOUILLER -> {
                viewer.closeInventory();
                router.openFouiller(viewer, targetId);
                AutoMe.broadcast(core(), viewer,
                        "fouille les affaires de " + tName + ".");
            }
            case ACT_PORTER -> {
                viewer.closeInventory();
                if (!core().porter().carry(viewer, target)) {
                    viewer.sendMessage(Component.text(
                            "Impossible de porter cette personne maintenant.",
                            NamedTextColor.RED));
                }
            }
            case ACT_TUER -> {
                viewer.closeInventory();
                core().killRequests().request(viewer, target);
            }
            default -> { }
        }
    }

    /** Dummy path: État opens the silhouette directly off the dummy's
     *  injury list; Porter mounts the Villager on the viewer; Tuer +
     *  Fouiller emit "non applicable". */
    private void onDummyAction(Player viewer, View view, String action) {
        Dummy d = null;
        for (Dummy candidate : core().dummies().all()) {
            if (candidate.id().equals(view.uuid(S_TARGET_ID))) {
                d = candidate;
                break;
            }
        }
        if (d == null) {
            viewer.sendMessage(Component.text(
                    "Cette dummy n'existe plus.", NamedTextColor.RED));
            viewer.closeInventory();
            return;
        }
        switch (action) {
            case ACT_ETAT -> {
                viewer.closeInventory();
                router.openEtatForDummy(viewer, d);
                AutoMe.broadcast(core(), viewer,
                        "examine attentivement la silhouette de " + d.name() + ".");
            }
            case ACT_PORTER -> {
                viewer.closeInventory();
                Entity ent = d.entityId() != null
                        ? core().getServer().getEntity(d.entityId()) : null;
                if (ent == null) {
                    viewer.sendMessage(Component.text(
                            "L'entité de cette dummy n'est pas chargée.",
                            NamedTextColor.RED));
                    return;
                }
                // Direct passenger attach — PlayerQuitEvent / DummyListener
                // pop it on world unload; sneak-drop handled by
                // PorterManager's existing hook keyed on the carrier.
                if (viewer.getPassengers().contains(ent)) {
                    viewer.removePassenger(ent);
                    viewer.sendMessage(Component.text(
                            "Tu déposes la dummy.", NamedTextColor.GRAY));
                } else {
                    viewer.addPassenger(ent);
                    viewer.sendMessage(Component.text(
                            "Tu portes la dummy. Shift ou /sc drop pour la déposer.",
                            NamedTextColor.GRAY));
                }
            }
            case ACT_FOUILLER -> {
                viewer.sendMessage(Component.text(
                        "Une dummy n'a pas d'inventaire.", NamedTextColor.GRAY));
                viewer.closeInventory();
            }
            case ACT_TUER -> {
                viewer.sendMessage(Component.text(
                        "Pour supprimer une dummy : /dummy delete " + d.name(),
                        NamedTextColor.GRAY));
                viewer.closeInventory();
            }
            default -> { }
        }
    }
}
