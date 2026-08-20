package fr.reborn.hud.combat;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Capture de l'input de GARDE (M2) côté client. La garde = <b>clic droit maintenu
 * à mains nues</b> (sinon le clic droit sert à utiliser l'objet — pas de conflit).
 *
 * <p>À l'entrée/sortie de garde : joue l'anim localement (retour immédiat sur son
 * propre avatar) ET envoie l'état au serveur ({@code reborn:combatin}) qui applique
 * la réduction de dégâts + rediffuse l'anim aux autres. Le M1 (combo) ne passe pas
 * par ici (mêlée vanilla → serveur).
 */
public final class CombatInput {

    public static final CombatInput INSTANCE = new CombatInput();

    private boolean blocking = false;

    private CombatInput() {}

    public boolean isBlocking() { return blocking; }

    /** À appeler chaque client tick (piloté par {@code HudKeybinds}). */
    public void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) { setBlocking(false); return; }
        // Un écran ouvert coupe la garde (l'input va à l'UI).
        if (mc.gui.screen() != null) { setBlocking(false); return; }

        // Mains nues uniquement : sinon le clic droit = utiliser l'objet.
        boolean emptyHanded = player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();
        boolean want = emptyHanded && mc.options.keyUse.isDown();
        setBlocking(want);
    }

    private void setBlocking(boolean want) {
        if (want == blocking) return;
        blocking = want;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            // Retour immédiat sur son propre avatar (le broadcast serveur pour soi
            // est ignoré côté réception pour éviter le double).
            CombatAnimations.INSTANCE.play(mc.player,
                want ? CombatAnimations.ANIM_BLOCK_ON : CombatAnimations.ANIM_BLOCK_OFF);
        }
        if (ClientPlayNetworking.canSend(CombatInputPayload.ID)) {
            ClientPlayNetworking.send(new CombatInputPayload(
                want ? CombatInputPayload.KIND_BLOCK_ON : CombatInputPayload.KIND_BLOCK_OFF));
        }
    }

    /** Reset à la déconnexion (pas d'envoi). */
    public void reset() { blocking = false; }
}
