package com.reborn.shinobicore.cinematic;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.gui.GuiSounds;
import com.reborn.shinobicore.util.Items;
import com.reborn.shinobicore.util.PdcAccess;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * The temporary-hotbar anchor editor for one player + one anchor. On
 * {@link #enter} it stashes the player's real inventory and lays out nine
 * PDC-tagged tool items on the hotbar; clicks are routed here by
 * {@link CinematicListener} (which reads the {@link #ACTION_KEY} tag, not the
 * slot). On save / abort / disconnect the real inventory is always restored.
 *
 * <p>The working anchor is the <em>live</em> anchor inside the cinematic —
 * edits are persisted as you go ({@code cinematics().save()} on each change),
 * so an abandoned editor keeps prior edits but never leaks the tool items.
 */
public final class CinematicEditorSession {

    /** PDC key stamped on every editor hotbar item. */
    public static final String ACTION_KEY = "cine_editor_action";

    public static final String SET_CAM  = "set_cam";
    public static final String TITLE    = "title";
    public static final String TEXT1    = "text1";
    public static final String TEXT2    = "text2";
    public static final String TEXT3    = "text3";
    public static final String COLORS   = "colors";
    public static final String DURATION = "duration";
    public static final String TEST     = "test";
    public static final String SAVE     = "save";

    private final ShinobiCore plugin;
    private final UUID playerId;
    private final String cinematicName;
    private final int anchorIndex;
    private final CinematicAnchor working;

    private ItemStack[] stashedContents;
    private int stashedHeldSlot;
    private boolean restored;
    private int colorTarget;   // 0=title, 1=text1, 2=text2, 3=text3

    public CinematicEditorSession(ShinobiCore plugin, UUID playerId,
                                  String cinematicName, int anchorIndex,
                                  CinematicAnchor working) {
        this.plugin = plugin;
        this.playerId = playerId;
        this.cinematicName = cinematicName;
        this.anchorIndex = anchorIndex;
        this.working = working;
    }

    public CinematicAnchor workingAnchor() { return working; }
    public int colorTarget() { return colorTarget; }
    public void setColorTarget(int t) { this.colorTarget = Math.max(0, Math.min(3, t)); }

    /* -------------------------------------------------------------- enter */

    public void enter(Player p) {
        p.closeInventory();
        PlayerInventory inv = p.getInventory();
        ItemStack[] snapshot = inv.getContents();
        // Never stash our own editor tools — defends against re-entering the
        // editor before a prior session restored (which would otherwise
        // capture, and later "restore", the temporary hotbar instead of the
        // real inventory).
        for (int i = 0; i < snapshot.length; i++) {
            ItemStack it = snapshot[i];
            if (it != null && PdcAccess.getString(it, plugin, ACTION_KEY) != null) {
                snapshot[i] = null;
            }
        }
        stashedContents = snapshot;
        stashedHeldSlot = inv.getHeldItemSlot();
        inv.clear();
        giveHotbar(p);
        inv.setHeldItemSlot(0);
        GuiSounds.open(p);
        p.sendMessage(Component.text(
                "Éditeur de plan ouvert — utilise la barre d'outils. "
                        + "Clique « Sauvegarder » pour revenir.", NamedTextColor.AQUA));
    }

    /** Re-lay the hotbar (refreshes the tool lore after an edit). */
    public void refresh(Player p) {
        if (p == null) return;
        p.getInventory().clear();
        giveHotbar(p);
        p.getInventory().setHeldItemSlot(0);
    }

    private void giveHotbar(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.setItem(0, tool(Material.COMPASS, "Définir la position caméra", SET_CAM,
                "&7Capture ta position + orientation.",
                working.hasCamera() ? "&aDéfinie" : "&cNon définie"));
        inv.setItem(1, tool(Material.NAME_TAG, "Définir le titre", TITLE,
                value("Titre", working.title())));
        inv.setItem(2, tool(Material.PAPER, "Définir le texte 1", TEXT1,
                value("Texte 1", working.text1())));
        inv.setItem(3, tool(Material.PAPER, "Définir le texte 2", TEXT2,
                value("Texte 2", working.text2())));
        inv.setItem(4, tool(Material.PAPER, "Définir le texte 3", TEXT3,
                value("Texte 3", working.text3())));
        inv.setItem(5, tool(Material.PAINTING, "Définir les couleurs", COLORS,
                "&7Couleur de chaque ligne."));
        inv.setItem(6, tool(Material.CLOCK, "Définir la durée", DURATION,
                "&7Actuel : &f" + working.durationSeconds() + "s"));
        inv.setItem(7, tool(Material.ENDER_EYE, "Tester ce plan", TEST,
                "&7Aperçu de ce plan seul."));
        inv.setItem(8, tool(Material.LIME_DYE, "Sauvegarder & retour", SAVE,
                "&7Enregistre et rouvre le menu."));
    }

    private ItemStack tool(Material m, String name, String action, String... lore) {
        return Items.of(m).name(name).lore(lore).pdc(plugin, ACTION_KEY, action).build();
    }

    private static String value(String label, String v) {
        return "&7" + label + " : " + (v != null ? "&f" + v : "&8(vide)");
    }

    /* -------------------------------------------------------------- handle */

    public void handle(String action, Player p) {
        switch (action) {
            case SET_CAM -> {
                working.setCamera(p.getLocation());
                plugin.cinematics().save();
                GuiSounds.accept(p);
                p.sendMessage(Component.text("Position caméra capturée.", NamedTextColor.GREEN));
                refresh(p);
            }
            case TITLE    -> promptText(p, "Entre le TITRE (« clear » pour vider, « cancel » pour annuler) :", working::setTitle);
            case TEXT1    -> promptText(p, "Entre le TEXTE 1 :", working::setText1);
            case TEXT2    -> promptText(p, "Entre le TEXTE 2 :", working::setText2);
            case TEXT3    -> promptText(p, "Entre le TEXTE 3 :", working::setText3);
            case DURATION -> promptDuration(p);
            case COLORS   -> new CinematicColorGui(plugin, playerId).open(p, this);
            case TEST     -> testAnchor(p);
            case SAVE     -> save(p);
            default -> { }
        }
    }

    private void promptText(Player p, String prompt, Consumer<String> setter) {
        plugin.chatInputs().prompt(p, prompt, raw -> {
            String v = raw.trim();
            if (v.equalsIgnoreCase("clear") || v.equalsIgnoreCase("vide")) v = "";
            setter.accept(v);                 // anchor setter maps blank → null
            plugin.cinematics().save();
            GuiSounds.accept(p);
            p.sendMessage(Component.text("Enregistré.", NamedTextColor.GREEN));
            refresh(p);
        }, () -> refresh(p));
    }

    private void promptDuration(Player p) {
        plugin.chatInputs().prompt(p, "Entre la DURÉE en secondes (nombre entier) :", raw -> {
            try {
                int secs = Integer.parseInt(raw.trim());
                if (secs <= 0) throw new NumberFormatException();
                working.setDurationSeconds(secs);
                plugin.cinematics().save();
                GuiSounds.accept(p);
                p.sendMessage(Component.text("Durée : " + working.durationSeconds() + "s",
                        NamedTextColor.GREEN));
            } catch (NumberFormatException ex) {
                GuiSounds.error(p);
                p.sendMessage(Component.text("Nombre invalide.", NamedTextColor.RED));
            }
            refresh(p);
        }, () -> refresh(p));
    }

    private void testAnchor(Player p) {
        if (!working.hasCamera()) {
            GuiSounds.error(p);
            p.sendMessage(Component.text("Définis d'abord la position caméra.", NamedTextColor.RED));
            return;
        }
        Cinematic temp = new Cinematic("__test__");
        temp.addAnchor(working);
        plugin.cinematics().play(p, temp);   // freezes, plays one shot, releases to here
    }

    private void save(Player p) {
        plugin.cinematics().save();
        finish(p, true);
        if (p != null) {
            GuiSounds.accept(p);
            p.sendMessage(Component.text("Plan enregistré.", NamedTextColor.GREEN));
        }
    }

    /* -------------------------------------------------- colour application */

    public NamedTextColor currentColor(int target) {
        return switch (target) {
            case 0 -> working.titleColor();
            case 1 -> working.color1();
            case 2 -> working.color2();
            case 3 -> working.color3();
            default -> null;
        };
    }

    public void applyColor(NamedTextColor c) {
        switch (colorTarget) {
            case 0 -> working.setTitleColor(c);
            case 1 -> working.setColor1(c);
            case 2 -> working.setColor2(c);
            case 3 -> working.setColor3(c);
            default -> { }
        }
        plugin.cinematics().save();
    }

    /* -------------------------------------------------------------- exit */

    /** Save-path exit: restore inventory, drop the session, reopen the menu. */
    private void finish(Player p, boolean reopenGui) {
        restoreInventory(p);
        plugin.cinematics().removeEditor(playerId);
        if (reopenGui && p != null && p.isOnline()) {
            new CinematicGui(plugin, cinematicName).open(p);
        }
    }

    /** Abandon path (quit / character-switch / shutdown): restore the real
     *  inventory and drop the session. Offline-safe. */
    public void abort() {
        restoreInventory(Bukkit.getPlayer(playerId));
        plugin.cinematics().removeEditor(playerId);
    }

    private void restoreInventory(Player p) {
        if (restored) return;
        restored = true;
        if (p == null) return;
        PlayerInventory inv = p.getInventory();
        if (stashedContents != null) inv.setContents(stashedContents);
        inv.setHeldItemSlot(Math.max(0, Math.min(8, stashedHeldSlot)));
    }
}
