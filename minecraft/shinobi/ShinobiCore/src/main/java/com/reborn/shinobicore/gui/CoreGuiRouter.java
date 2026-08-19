package com.reborn.shinobicore.gui;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.ScreenRouter;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.gui.framework.ScreenManager;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Navigation hub for ShinobiCore's own screens (character, KO, medic,
 * rencontrer), mirroring ShinobiAbilities' {@code GuiRouter}. Screens
 * hold this router — never each other — and ride the one engine
 * {@link ScreenManager} listener; the legacy {@code GuiListener}
 * switchboard shrinks as each screen migrates here and dies with the
 * last one.
 */
public final class CoreGuiRouter implements ScreenRouter {

    private final ShinobiCore plugin;
    private final ScreenManager screens = new ScreenManager();

    /* Migrated screens — bound once from the main class after every
     * screen exists (screens never hold each other; they hold this). */
    private com.reborn.shinobicore.medic.gui.SoignerScreen soigner;
    private com.reborn.shinobicore.medic.gui.InjuryListScreen injuryList;
    private com.reborn.shinobicore.medic.gui.TreatmentScreen treatment;

    public CoreGuiRouter(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    private com.reborn.shinobicore.character.gui.CharacterListScreen characterList;
    private com.reborn.shinobicore.character.gui.CharacterEditScreen characterEdit;
    private com.reborn.shinobicore.character.gui.LeafTestScreen leafTest;
    private com.reborn.shinobicore.character.gui.ClanPickerScreen clanPicker;

    /** One-time wiring from the main class (character cluster). */
    public void bindCharacter(
            com.reborn.shinobicore.character.gui.CharacterListScreen characterList,
            com.reborn.shinobicore.character.gui.CharacterEditScreen characterEdit,
            com.reborn.shinobicore.character.gui.LeafTestScreen leafTest,
            com.reborn.shinobicore.character.gui.ClanPickerScreen clanPicker) {
        this.characterList = characterList;
        this.characterEdit = characterEdit;
        this.leafTest = leafTest;
        this.clanPicker = clanPicker;
    }

    /* ----------------------------------------------- character navigation */

    /** The editor screen (shared chat-prompt + nickname helpers live on it). */
    public com.reborn.shinobicore.character.gui.CharacterEditScreen characterEdit() {
        return characterEdit;
    }

    public void openCharacterList(Player viewer, java.util.UUID targetOwner,
            com.reborn.shinobicore.character.gui.CharacterListScreen.Action action) {
        characterList.open(viewer, targetOwner, action);
    }

    public void openCharacterEdit(Player viewer, ShinobiCharacter c) {
        characterEdit.open(viewer, c);
    }

    public void openLeafTest(Player viewer, ShinobiCharacter c) {
        leafTest.open(viewer, c);
    }

    public void openClanPicker(Player viewer, ShinobiCharacter c) {
        clanPicker.open(viewer, c);
    }

    private com.reborn.shinobicore.character.gui.RencontrerRootScreen rencontrerRoot;
    private com.reborn.shinobicore.character.gui.GiveNameScreen giveName;
    private com.reborn.shinobicore.character.gui.GiveNickPickerScreen giveNickPicker;
    private com.reborn.shinobicore.character.gui.GiveTargetScreen giveTarget;
    private com.reborn.shinobicore.character.gui.NameRequestScreen nameRequest;

    /** One-time wiring from the main class (rencontrer cluster). */
    public void bindRencontrer(
            com.reborn.shinobicore.character.gui.RencontrerRootScreen rencontrerRoot,
            com.reborn.shinobicore.character.gui.GiveNameScreen giveName,
            com.reborn.shinobicore.character.gui.GiveNickPickerScreen giveNickPicker,
            com.reborn.shinobicore.character.gui.GiveTargetScreen giveTarget,
            com.reborn.shinobicore.character.gui.NameRequestScreen nameRequest) {
        this.rencontrerRoot = rencontrerRoot;
        this.giveName = giveName;
        this.giveNickPicker = giveNickPicker;
        this.giveTarget = giveTarget;
        this.nameRequest = nameRequest;
    }

    /* ---------------------------------------------- rencontrer navigation */

    /** Root screen (shared ray-trace + reveal helpers live on it). */
    public com.reborn.shinobicore.character.gui.RencontrerRootScreen rencontrerRoot() {
        return rencontrerRoot;
    }

    public void openRencontrerRoot(Player viewer) {
        rencontrerRoot.open(viewer);
    }

    public void openGiveName(Player viewer) {
        giveName.open(viewer);
    }

    public void openGiveNickPicker(Player viewer) {
        giveNickPicker.open(viewer);
    }

    public void openGiveTarget(Player viewer, String pendingNickname) {
        giveTarget.open(viewer, pendingNickname);
    }

    public void openNameRequest(Player target, java.util.UUID requesterMc,
                                String requesterDisplay) {
        nameRequest.open(target, requesterMc, requesterDisplay);
    }

    private com.reborn.shinobicore.ko.gui.KoActionScreen koAction;
    private com.reborn.shinobicore.ko.gui.EtatScreen etat;
    private com.reborn.shinobicore.ko.gui.FouillerScreen fouiller;

    /** One-time wiring from the main class (KO cluster). */
    public void bindKo(com.reborn.shinobicore.ko.gui.KoActionScreen koAction,
                       com.reborn.shinobicore.ko.gui.EtatScreen etat,
                       com.reborn.shinobicore.ko.gui.FouillerScreen fouiller) {
        this.koAction = koAction;
        this.etat = etat;
        this.fouiller = fouiller;
    }

    /* ------------------------------------------------------ KO navigation */

    public void openKoAction(Player viewer, java.util.UUID targetPlayerId,
                             ShinobiCharacter targetChar) {
        koAction.open(viewer, targetPlayerId, targetChar);
    }

    public void openKoActionForDummy(Player viewer,
                                     com.reborn.shinobicore.dummy.Dummy dummy) {
        koAction.openForDummy(viewer, dummy);
    }

    public void openEtat(Player viewer, java.util.UUID targetPlayerId,
                         ShinobiCharacter targetChar) {
        etat.open(viewer, targetPlayerId, targetChar);
    }

    public void openEtatSelf(Player viewer, ShinobiCharacter own) {
        etat.openSelf(viewer, own);
    }

    public void openEtatForDummy(Player viewer,
                                 com.reborn.shinobicore.dummy.Dummy dummy) {
        etat.openForDummy(viewer, dummy);
    }

    public void openFouiller(Player viewer, java.util.UUID targetPlayerId) {
        fouiller.open(viewer, targetPlayerId);
    }

    /** One-time wiring from the main class (medic cluster). */
    public void bindMedic(com.reborn.shinobicore.medic.gui.SoignerScreen soigner,
                          com.reborn.shinobicore.medic.gui.InjuryListScreen injuryList,
                          com.reborn.shinobicore.medic.gui.TreatmentScreen treatment) {
        this.soigner = soigner;
        this.injuryList = injuryList;
        this.treatment = treatment;
    }

    /* --------------------------------------------------- medic navigation */

    /** The soigner silhouette screen (shared live-injury lookups live on it). */
    public com.reborn.shinobicore.medic.gui.SoignerScreen soigner() {
        return soigner;
    }

    public void openSoignerForPlayer(Player viewer, java.util.UUID targetPlayerId,
                                     ShinobiCharacter target) {
        soigner.openForPlayer(viewer, targetPlayerId, target);
    }

    public void openSoignerForDummy(Player viewer,
                                    com.reborn.shinobicore.dummy.Dummy dummy) {
        soigner.openForDummy(viewer, dummy);
    }

    public void openInjuryList(Player viewer,
                               com.reborn.shinobicore.medic.gui.SoignerScreen.Target target,
                               java.util.UUID targetId,
                               com.reborn.shinobicore.ko.injury.BodyPart part) {
        injuryList.open(viewer, target, targetId, part);
    }

    /** Open the treatment screen, or explain why the wound refuses care
     *  (post-treatment convalescence) — the shared gate both the
     *  silhouette and the list route through. */
    public void openTreatment(Player viewer,
                              com.reborn.shinobicore.medic.gui.SoignerScreen.Target target,
                              java.util.UUID targetId,
                              com.reborn.shinobicore.ko.injury.Injury injury) {
        if (!injury.isHealable()) {
            viewer.sendMessage(net.kyori.adventure.text.Component.text(
                    "Cette blessure est encore en convalescence. Reviens dans "
                            + com.reborn.shinobicore.medic.gui.MedicFmt
                                    .formatRemaining(injury.cooldownRemainingMillis())
                            + ".",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }
        treatment.open(viewer, target, targetId, injury);
    }

    /** The owning plugin, for screens' manager/config access. */
    public ShinobiCore plugin() {
        return plugin;
    }

    @Override
    public ScreenManager screens() {
        return screens;
    }

    /** The character system's "home": the viewer's own roster in SELECT mode. */
    @Override
    public void openHub(Player p) {
        characterList.open(p, p.getUniqueId(),
                com.reborn.shinobicore.character.gui.CharacterListScreen.Action.SELECT);
    }
}
