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
 *   <li>Demande au <b>plugin serveur</b> (ShinobiAbilities) d'activer la course
 *       IG (vitesse, auto-step, particules, chakra…) via le canal
 *       {@code reborn:run}, et <b>suit son verdict</b> : le plugin renvoie
 *       l'état autoritaire, ce qui fait sortir le client du mode quand la course
 *       est coupée serveur (coup reçu + cooldown, chakra épuisé, KO) ou refusée
 *       au démarrage.</li>
 * </ul>
 *
 * <p>Le basculement est <b>optimiste</b> : on applique l'état localement tout de
 * suite (la touche répond instantanément, et le mouvement client marche même en
 * solo / sans plugin), puis le S2C corrige si le serveur n'est pas d'accord.
 */
public final class NarutoRun {

    public static final NarutoRun INSTANCE = new NarutoRun();

    private boolean active = false;

    private NarutoRun() {}

    public boolean isActive() { return active; }

    /** Bascule l'état (optimiste) et envoie la demande au serveur. */
    public void toggle() {
        active = !active;
        requestToServer(active);
    }

    /**
     * État <b>autoritaire</b> reçu du plugin (S2C {@code reborn:run}). Appelé
     * quand le serveur démarre, refuse ou coupe la course — le client s'aligne
     * sans re-notifier le serveur (sinon boucle).
     */
    public void setActive(boolean value) {
        this.active = value;
    }

    /** Coupe la course chakraïque sans réseau (déconnexion / changement de monde). */
    public void reset() {
        active = false;
    }

    private void requestToServer(boolean wanted) {
        Minecraft mc = Minecraft.getInstance();
        // Pas de connexion play (menu principal) → rien à envoyer.
        if (mc.getConnection() == null) return;
        // canSend est faux en solo / si ShinobiAbilities n'a pas enregistré le
        // canal → on n'envoie rien (pas d'exception) et l'état local fait foi.
        if (ClientPlayNetworking.canSend(NarutoRunPayload.ID)) {
            ClientPlayNetworking.send(new NarutoRunPayload(wanted));
        }
    }
}
