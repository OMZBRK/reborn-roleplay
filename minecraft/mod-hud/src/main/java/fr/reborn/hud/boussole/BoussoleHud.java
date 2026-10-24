package fr.reborn.hud.boussole;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementBounds;
import fr.reborn.hud.element.HudElementState;
import fr.reborn.hud.map.MapData;
import fr.reborn.hud.runtime.RebornSession;
import fr.reborn.hud.ui.DaKit;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * <b>Boussole horizontale</b> du HUD (GAMEPLAN §4, décision du 19/10 ; RedLine : boussole en haut de l'écran) —
 * élément {@link HudElement#BOUSSOLE} de l'éditeur de HUD, <b>masqué par défaut</b>.
 *
 * <p>Fine et sans plaque (DA §6, règle 9) : un filet d'1 px écran qui s'efface vers les bords, des graduations tous
 * les 15° (plus hautes aux intercardinaux), les lettres <b>N E S O</b> (N en or), et un petit chevron d'or au centre
 * sous le filet (le cap). Dessus, ce que portent les {@link Reperes} : le <b>marqueur personnel</b> (losange d'or ; sa
 * distance s'écrit sous le filet quand on le regarde à peu près), les repères posés par d'autres systèmes, et les
 * <b>lieux de la carte proches</b> ({@code reborn:map}, lieux et portes à moins de {@value #RAYON_LIEUX} blocs) : un
 * point discret, le nom seulement quand on le regarde. Rien quand l'interface est masquée (F1) ou qu'un écran est
 * ouvert.
 */
public final class BoussoleHud {

    /** Boîte de l'élément à l'échelle 1, en GUI. */
    public static final int LARGEUR = 150, HAUTEUR = 21;
    /** Champ de vision de la bande (degrés de part et d'autre du cap). */
    private static final float DEMI_CHAMP = 70f;
    private static final int RAYON_LIEUX = 250;
    /** Y (GUI locales) : milieu des lettres, filet, chevron, texte du dessous. */
    private static final float Y_LETTRES = 4.5f, Y_FILET = 10f, Y_DESSOUS = 17.5f;

    private static final int CREME = 0xFFF5E9D0, CREME_DOUX = 0xFFC2B59A, OR = 0xFFD9A95E, OMBRE_LOSANGE = 0x660A0608;

    private BoussoleHud() {}

    /** Couche HUD + oubli des repères à la déconnexion (appelé une fois par {@code RebornHudClient}). */
    public static void init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("reborn-hud", "boussole"), (g, dt) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options == null || mc.gui.hud.isHidden() || mc.gui.screen() != null) return;
            if (!RebornSession.rpFeaturesEnabled()) return;
            HudElementState st;
            try { st = RebornHudClient.config().stateOf(HudElement.BOUSSOLE); } catch (RuntimeException e) { return; }
            if (!st.visible()) return;
            int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
            HudElementBounds b = HudElementBounds.currentFor(HudElement.BOUSSOLE, st, w, h);
            // cap de ce que l'on voit (caméra épaule, regard libre compris), pas seulement du corps
            var cam = mc.gameRenderer.mainCamera();
            float cap = (cam.isInitialized() ? cam.yRot() : mc.player.getViewYRot(dt.getGameTimeDeltaPartialTick(true))) + 180f;
            dessiner(g, b.x(), b.y(), st.scale(), cap, mc.player.getX(), mc.player.getZ(), true);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Reperes.clear());
    }

    /** Éditeur de HUD : la bande au cap nord-est, avec un marqueur d'exemple. */
    public static void apercu(GuiGraphicsExtractor g, float x, float y, float echelle) {
        dessiner(g, x, y, echelle, 30f, 0, 0, false);
    }

    /**
     * @param cap    cap en degrés boussole (0 = nord, 90 = est)
     * @param px, pz position du joueur (repères)
     * @param jeu    vrai en jeu (repères réels), faux pour l'aperçu de l'éditeur
     */
    private static void dessiner(GuiGraphicsExtractor g, float x, float y, float echelle, float cap, double px, double pz,
                                 boolean jeu) {
        Minecraft mc = Minecraft.getInstance();
        Font f = mc.font;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        if (echelle != 1f) g.pose().scale(echelle, echelle);
        float cx = LARGEUR / 2f, pxDeg = (LARGEUR / 2f - 4) / DEMI_CHAMP;

        // ------------------------------------------------ filet + graduations (pixels écran : traits d'1 px nets)
        int k = DaKit.echelleGui();
        DaKit.debutPx(g);
        int yF = Math.round(Y_FILET * k);
        int x0 = Math.round(4 * k), x1 = Math.round((LARGEUR - 4) * k);
        for (int X = x0; X < x1; X += Math.max(1, k)) {
            float a = fondu((X / (float) k - cx) / (LARGEUR / 2f - 4)) * 0.45f;
            if (a > 0.01f) g.fill(X, yF, X + Math.max(1, k), yF + 1, DaKit.argb(0xE8DCC0, a));
        }
        for (int deg = 0; deg < 360; deg += 15) {
            float rel = relatif(deg, cap);
            if (Math.abs(rel) > DEMI_CHAMP) continue;
            if (deg % 90 == 0) continue;                       // les cardinaux sont des lettres
            float a = fondu(rel / DEMI_CHAMP);
            int X = Math.round((cx + rel * pxDeg) * k);
            int haut = Math.round((deg % 45 == 0 ? 3f : 1.5f) * k);
            g.fill(X, yF - haut, X + 1, yF, DaKit.argb(0xE8DCC0, a * (deg % 45 == 0 ? 0.85f : 0.55f)));
        }
        // chevron du cap : petit triangle d'or sous le filet
        int C = Math.round(cx * k), yc = yF + Math.max(2, k);
        for (int i = 0; i < 3; i++) {
            int dl = Math.round(i * k * 0.75f);
            g.fill(C - dl, yc + i * Math.max(1, k / 2 + 1), C + dl + 1, yc + (i + 1) * Math.max(1, k / 2 + 1), OR);
        }
        DaKit.finPx(g);

        // ------------------------------------------------ lettres cardinales
        String[] lettres = {"N", "E", "S", "O"};
        for (int i = 0; i < 4; i++) {
            float rel = relatif(i * 90, cap);
            if (Math.abs(rel) > DEMI_CHAMP) continue;
            float a = fondu(rel / DEMI_CHAMP);
            int col = DaKit.argb((i == 0 ? OR : CREME) & 0xFFFFFF, a);
            DaKit.inter7o(g, f, DaKit.interGras(lettres[i]), cx + rel * pxDeg, Y_LETTRES, col, 1);
        }

        // ------------------------------------------------ lieux proches, repères, marqueur
        boolean texteDessous = false;
        if (jeu) {
            MapData.Snapshot s = MapData.fromServer() ? MapData.get() : null;
            if (s != null && s.here()) {
                for (MapData.Place p : s.places()) {
                    if (p.type() != MapData.Type.LIEU && p.type() != MapData.Type.PORTE) continue;
                    double d = Math.hypot(p.x() - px, p.z() - pz);
                    if (d > RAYON_LIEUX || d < 6) continue;
                    float rel = relatif(gisement(p.x() - px, p.z() - pz), cap);
                    if (Math.abs(rel) > DEMI_CHAMP) continue;
                    float a = fondu(rel / DEMI_CHAMP);
                    point(g, cx + rel * pxDeg, Y_FILET - 3.5f, DaKit.argb(0xE8DCC0, a * 0.7f));
                    if (!texteDessous && Math.abs(rel) < 5f) {
                        DaKit.inter7o(g, f, DaKit.inter(p.name() + " · " + distance(d)), cx, Y_DESSOUS, DaKit.argb(0xC2B59A, 0.9f), 1);
                        texteDessous = true;
                    }
                }
            }
            String dim = Reperes.dimension();
            for (Reperes.Repere r : Reperes.autres()) {
                if (r.dimension() != null && !r.dimension().equals(dim)) continue;
                float rel = borne(relatif(gisement(r.x() - px, r.z() - pz), cap));
                DaKit.losange(g, Math.round(cx + rel * pxDeg), Math.round(Y_FILET), 1, r.couleur(), OMBRE_LOSANGE);
            }
        }
        Reperes.Repere m = jeu ? Reperes.marqueur() : new Reperes.Repere("marqueur", "Marqueur", 0, 0, Reperes.OR_MARQUEUR, null);
        if (m != null && (!jeu || m.dimension() == null || m.dimension().equals(Reperes.dimension()))) {
            float rel = jeu ? relatif(gisement(m.x() - px, m.z() - pz), cap) : 22f;
            boolean dehors = Math.abs(rel) > DEMI_CHAMP;
            rel = borne(rel);
            float mx = cx + rel * pxDeg;
            DaKit.losange(g, Math.round(mx), Math.round(Y_FILET), 2, dehors ? DaKit.argb(m.couleur() & 0xFFFFFF, 0.55f) : m.couleur(), OMBRE_LOSANGE);
            double d = jeu ? Math.hypot(m.x() - px, m.z() - pz) : 340;
            if (!texteDessous && (Math.abs(rel) < 12f || !jeu)) {
                DaKit.inter7o(g, f, DaKit.inter(distance(d)), mx, Y_DESSOUS, DaKit.argb(0xE8C27A, 0.95f), 1);
            }
        }
        g.pose().popMatrix();
    }

    /** Un point d'1 GUI (lieu proche). */
    private static void point(GuiGraphicsExtractor g, float x, float y, int col) {
        int k = DaKit.echelleGui();
        DaKit.debutPx(g);
        int X = Math.round(x * k), Y = Math.round(y * k), c = Math.max(2, k);
        g.fill(X - c / 2, Y - c / 2, X - c / 2 + c, Y - c / 2 + c, col);
        DaKit.finPx(g);
    }

    /** Gisement boussole (0 = nord = −Z, 90 = est = +X) d'un écart (dx, dz). */
    static float gisement(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(dx, -dz));
    }

    /** Angle de {@code deg} par rapport au cap, ramené dans [−180, 180]. */
    static float relatif(float deg, float cap) {
        float r = (deg - cap) % 360f;
        if (r > 180f) r -= 360f;
        if (r < -180f) r += 360f;
        return r;
    }

    private static float borne(float rel) { return Math.max(-DEMI_CHAMP, Math.min(DEMI_CHAMP, rel)); }

    /** Opacité : pleine au centre, s'efface vers les bords (t = −1..1). */
    private static float fondu(float t) {
        float u = Math.abs(t);
        return u >= 1f ? 0f : Math.min(1f, (1f - u * u) * 1.35f);
    }

    private static String distance(double d) {
        if (d >= 1000) return String.format(java.util.Locale.FRANCE, "%.1f km", d / 1000.0);
        return Math.round(d) + " m";
    }
}
