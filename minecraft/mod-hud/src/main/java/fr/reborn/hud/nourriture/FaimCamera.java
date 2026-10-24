package fr.reborn.hud.nourriture;

import fr.reborn.hud.etat.EtatClient;
import fr.reborn.hud.ko.KoClient;
import fr.reborn.hud.runtime.VitalsFeed;
import net.minecraft.client.Minecraft;

/**
 * <b>Caméra qui tangue</b> à 0 % de satiété (FAIM, GAMEPLAN §4 du 19/10, RedLine) : un balancement lent et léger de la
 * vue (≈ 0,9° de lacet, 0,55° de tangage, période de 5 à 7 s), qui arrive en 2 s et repart de même. Le regard du
 * joueur n'est pas touché (décalage ajouté à la fin de la mise à jour de la caméra, comme le ressenti du KO).
 *
 * <p>Désactivable : réglage d'accessibilité vanilla <b>« Effets de distorsion »</b> (0 = coupé, proportionnel
 * sinon), le même que le ressenti du KO. Rien pendant un voile imposé (KO, mort) : le KO a son propre ressenti.
 */
public final class FaimCamera {

    /** Amplitudes en degrés (à « Effets de distorsion » = 100 %). */
    private static final float LACET = 0.9f, TANGAGE = 0.55f;

    private static float force;
    private static long dernier;
    /** Banc d'essai : FAIM forcée sans serveur. */
    public static boolean debugFaim;

    private FaimCamera() {}

    /** La FAIM est-elle active (état du serveur, sinon drapeau des vitals) ? */
    public static boolean faim() {
        if (debugFaim) return true;
        if (EtatClient.mod("faim") != null) return true;
        VitalsFeed.Satiete s = VitalsFeed.satiete();
        return s != null && s.faim();
    }

    /** Décalage {lacet, tangage} en degrés, ou null. Appelé à chaque image par {@code CameraFaimMixin}. */
    public static float[] cameraDecalage() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        float dt = dernier == 0 ? 0f : Math.min(0.1f, (now - dernier) / 1000f);
        dernier = now;
        boolean actif = mc.player != null && faim() && !EtatClient.voileImpose();
        force = Math.max(0f, Math.min(1f, force + (actif ? dt : -dt) / 2f));
        float k = force * KoClient.distorsion();
        if (k <= 0.001f) return null;
        double t = now / 1000.0;
        // deux sinus de périodes premières entre elles : un roulis qui ne se répète pas mécaniquement
        float lacet = (float) (Math.sin(t * 2 * Math.PI / 6.3) * 0.75 + Math.sin(t * 2 * Math.PI / 2.9) * 0.25) * LACET * k;
        float tangage = (float) (Math.sin(t * 2 * Math.PI / 5.1 + 1.3) * 0.8 + Math.sin(t * 2 * Math.PI / 2.3) * 0.2) * TANGAGE * k;
        return new float[]{lacet, tangage};
    }
}
