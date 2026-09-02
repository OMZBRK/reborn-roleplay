package com.reborn.shinobiabilities.gui;

import com.reborn.shinobiabilities.jutsu.JutsuEditGui;
import com.reborn.shinobicore.technique.JutsuItemType;
import com.reborn.shinobiabilities.CoreServices;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.util.Players;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Navigation hub between every ShinobiAbilities GUI. Screens never hold
 * each other directly (the graph is cyclic — hub ↔ children, admin →
 * catalogue → back); they all hold the router, which is bound once in
 * the main class after every screen exists.
 */
public final class GuiRouter implements com.reborn.shinobicore.api.ScreenRouter {

    private final CoreServices core;
    private final com.reborn.shinobicore.gui.framework.ScreenManager screens =
            new com.reborn.shinobicore.gui.framework.ScreenManager();
    private HubGui hub;
    private ChannelPickerGui channelPicker;
    private CatalogueGui catalogue;
    private StaffValidationGui staffValidation;
    private AdminGui admin;
    private KnownAbilitiesGui known;
    private JutsuEditGui editGui;
    private CharacterPickerGui characterPicker;

    public GuiRouter(CoreServices core) {
        this.core = core;
    }

    /** One-time wiring from the main class. */
    public void bind(HubGui hub, ChannelPickerGui channelPicker,
                     CatalogueGui catalogue, StaffValidationGui staffValidation,
                     AdminGui admin, KnownAbilitiesGui known,
                     JutsuEditGui editGui,
                     CharacterPickerGui characterPicker) {
        this.hub = hub;
        this.channelPicker = channelPicker;
        this.catalogue = catalogue;
        this.staffValidation = staffValidation;
        this.admin = admin;
        this.known = known;
        this.editGui = editGui;
        this.characterPicker = characterPicker;
    }

    /* ------------------------------------------------------------ resolve */

    public CoreServices core() { return core; }

    /** The shared screen framework — the single listener every GUI
     *  rides on. Registered once in the main class. */
    public com.reborn.shinobicore.gui.framework.ScreenManager screens() {
        return screens;
    }

    /** The viewer's active character, with the standard warning. */
    public ShinobiCharacter activeOrWarn(Player p) {
        return Players.activeOrWarn(core.characters(), p);
    }

    /** Find any loaded character by id — admin screens target characters
     *  that aren't the viewer's. Returns null when unloaded/unknown. */
    public ShinobiCharacter characterById(UUID characterId) {
        if (characterId == null) return null;
        for (Map.Entry<UUID, List<ShinobiCharacter>> e
                : core.characters().rosterView().entrySet()) {
            for (ShinobiCharacter c : e.getValue()) {
                if (c.id().equals(characterId)) return c;
            }
        }
        return null;
    }

    /** Owner account UUID of a loaded character, or null. */
    public UUID ownerOf(UUID characterId) {
        if (characterId == null) return null;
        for (Map.Entry<UUID, List<ShinobiCharacter>> e
                : core.characters().rosterView().entrySet()) {
            for (ShinobiCharacter c : e.getValue()) {
                if (c.id().equals(characterId)) return e.getKey();
            }
        }
        return null;
    }

    /* --------------------------------------------------------- navigation */

    public void openHub(Player p) {
        ShinobiCharacter c = activeOrWarn(p);
        if (c == null) return;
        hub.open(p, c);
    }

    public void openChannelPicker(Player p, ShinobiCharacter target) {
        channelPicker.open(p, target);
    }

    public void openCatalogueBrowse(Player p) {
        catalogue.openBranches(p, CatalogueGui.Mode.BROWSE, null);
    }

    public void openCatalogueManage(Player p, ShinobiCharacter target) {
        catalogue.openBranches(p, CatalogueGui.Mode.ADMIN_MANAGE, target.id());
    }

    /** Staff scroll-delivery picker for a chosen ability id. */
    public void openCharacterPicker(Player p, String abilityId) {
        characterPicker.open(p, abilityId);
    }

    public void openStaffValidation(Player p) {
        staffValidation.open(p);
    }

    public void openAdmin(Player p) {
        admin.openCharacterList(p, 0);
    }

    public void openAdminManage(Player p, UUID characterId) {
        admin.openManage(p, characterId);
    }

    public void openKnown(Player p) {
        ShinobiCharacter c = activeOrWarn(p);
        if (c == null) return;
        known.open(p, c);
    }

    public void openBindingEditor(Player p, JutsuItemType type, ShinobiCharacter target) {
        editGui.open(p, type, target);
    }
}
