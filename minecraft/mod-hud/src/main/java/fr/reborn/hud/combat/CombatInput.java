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

    /** Fenêtre d'enchaînement du combo M1 (aligné sur le serveur). */
    private static final long COMBO_WINDOW_MS = 1200L;

    private boolean blocking = false;

    // Combo M1 côté client : joue l'anim à CHAQUE swing (même à vide).
    private boolean wasSwinging = false;
    private int comboIndex = 0;
    private long lastSwingMs = 0L;

    private CombatInput() {}

    public boolean isBlocking() { return blocking; }

    /** À appeler chaque client tick (piloté par {@code HudKeybinds}). */
    public void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gui.screen() != null) {
            setBlocking(false);
            if (player != null) wasSwinging = player.swinging;
            return;
        }

        boolean mainEmpty = player.getMainHandItem().isEmpty();

        // GARDE M2 : clic droit maintenu à mains nues (les deux mains vides pour
        // ne pas entrer en conflit avec l'usage d'un objet en main secondaire).
        boolean emptyHanded = mainEmpty && player.getOffhandItem().isEmpty();
        setBlocking(emptyHanded && mc.options.keyUse.isDown());

        // COMBO M1 : détecté sur le SWING (front montant) → joue même sans toucher.
        // Mains nues uniquement (taïjutsu). Le serveur gère les dégâts sur la mêlée
        // vanilla ; ici on ne fait QUE l'anim locale (immédiate, tous les coups).
        boolean sw = player.swinging;
        if (sw && !wasSwinging && mainEmpty && !blocking && CombatAnimations.INSTANCE.isAvailable()) {
            long now = System.currentTimeMillis();
            comboIndex = (now - lastSwingMs > COMBO_WINDOW_MS) ? 0 : (comboIndex + 1) % 4;
            lastSwingMs = now;
            CombatAnimations.INSTANCE.play(player, comboIndex + 1);   // animId 1..4
        }
        wasSwinging = sw;
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
            ClientPlayNetworking.send(CombatInputPayload.of(
                want ? CombatInputPayload.KIND_BLOCK_ON : CombatInputPayload.KIND_BLOCK_OFF));
        }
    }

    /** Cooldown client du dash — aligné sur le serveur ({@code DASH_COOLDOWN_MS=1500}). */
    private static final long DASH_CD_MS = CooldownState.DASH_CD_MS;
    private long lastDashMs = 0L;

    /**
     * Déclenche un dash directionnel (8 directions). La direction = input WASD
     * relatif à la vue ; sans input → arrière (backstep). Envoie la direction MONDE
     * normalisée au serveur, qui applique la vélocité (autoritaire). L'anim de slide
     * (fournie plus tard) se branchera ici.
     */
    public void dash(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gui.screen() != null) return;
        long now = System.currentTimeMillis();
        if (now - lastDashMs < DASH_CD_MS) return;

        double yr = Math.toRadians(p.getYRot());
        double fx = -Math.sin(yr), fz = Math.cos(yr);   // avant
        double lx = Math.cos(yr), lz = Math.sin(yr);    // gauche
        int f = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
        int l = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
        double dx, dz;
        if (f == 0 && l == 0) { dx = -fx; dz = -fz; }   // aucune touche → recul
        else { dx = f * fx + l * lx; dz = f * fz + l * lz; }
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-6) return;
        dx /= len; dz /= len;

        lastDashMs = now;
        CooldownState.INSTANCE.trigger(CooldownState.Ability.DASH, CooldownState.DASH_CD_MS);
        if (ClientPlayNetworking.canSend(CombatInputPayload.ID)) {
            ClientPlayNetworking.send(new CombatInputPayload(
                CombatInputPayload.KIND_DASH, (float) dx, (float) dz));
        }
    }

    /** Reset à la déconnexion (pas d'envoi). */
    public void reset() {
        blocking = false; wasSwinging = false; comboIndex = 0; lastSwingMs = 0L; lastDashMs = 0L;
        CooldownState.INSTANCE.clear();
    }
}
