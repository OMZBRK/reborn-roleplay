package fr.reborn.hud.animation;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * État client de la « course chakraïque » (Naruto run), basculé par une touche
 * dédiée (cf {@code HudKeybinds}).
 *
 * <ul>
 *   <li>Pilote le <b>mouvement libre</b> caméra-relatif (corps qui pivote vers
 *       la direction de déplacement) dans {@code KeyboardInputMixin} : ce
 *       mouvement n'est actif QUE pendant la course chakraïque ; sinon
 *       marche/course = comportement 3e-personne de base.</li>
 *   <li>Informe le <b>plugin serveur</b> (ShinobiCore) via le canal C2S
 *       {@code reborn:naruto} pour l'activer IG (vitesse, particules, chakra…).
 *       L'envoi est gardé par {@link ClientPlayNetworking#canSend} → inerte en
 *       solo / tant que le serveur n'a pas enregistré le canal, mais le mouvement
 *       client fonctionne quand même.</li>
 * </ul>
 */
public final class NarutoRun {

    public static final NarutoRun INSTANCE = new NarutoRun();

    private boolean active = false;

    private NarutoRun() {}

    public boolean isActive() { return active; }

    /** Bascule l'état et le pousse au serveur (si le canal est ouvert). */
    public void toggle() {
        active = !active;
        syncToServer();
    }

    /** Coupe la course chakraïque sans réseau (déconnexion / changement de monde). */
    public void reset() {
        active = false;
    }

    private void syncToServer() {
        Minecraft mc = Minecraft.getInstance();
        // Pas de connexion play (menu principal) → rien à envoyer.
        if (mc.getConnection() == null) return;
        // canSend est faux en solo / si ShinobiCore n'a pas enregistré le canal
        // → on n'envoie rien (pas d'exception).
        if (ClientPlayNetworking.canSend(NarutoRunPayload.ID)) {
            ClientPlayNetworking.send(new NarutoRunPayload(active));
        }
    }
}
