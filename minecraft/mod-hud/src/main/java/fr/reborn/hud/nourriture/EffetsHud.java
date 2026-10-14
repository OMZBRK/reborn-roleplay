package fr.reborn.hud.nourriture;

import fr.reborn.hud.interaction.InteractionMode;
import fr.reborn.hud.notif.NotifClient;
import fr.reborn.hud.runtime.VitalsFeed;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Effets sous les vitals</b> (maquettes v3 03 et 04) : une rangée de petites icônes d'objet (10 GUI), chacune avec son
 * minuteur et un filet d'1 px sous l'icône (temps restant). <b>Or</b> = bonus, <b>vermillon</b> = malus, <b>gris</b> =
 * en pause. Le nom s'affiche 3 s à l'apparition, puis seulement au survol en mode interaction (curseur libre). S'y
 * ajoutent les états de la satiété : plat chaud / froid en main, régénération en pause après un coup, Affamé, Faim.
 * Rien d'autre (DA §6, règle 9) : pas de cadre, pas de plaque.
 *
 * <p>Posé en coordonnées GUI sous les barres des vitals (élément {@code VITALS} de l'éditeur de HUD), sous
 * l'indicateur de surcharge quand il est affiché.
 */
public final class EffetsHud {

    private static final int VERM = 0xFFD2523E, AMBRE = 0xFFE0A050, GRIS = 0xFF8A8480, CREME_DOUX = 0xFFC2B59A;
    private static final long NOM_MS = 3000;

    private record Case(String ic, int teinte, String minuteur, float frac, int colFilet, String nom, int colNom,
                        String sous, boolean montrerNom) {}

    private EffetsHud() {}

    /**
     * @param x     gauche des barres des vitals (GUI)
     * @param y     juste sous les vitals (GUI)
     * @param scale échelle de l'élément vitals
     */
    public static void render(GuiGraphicsExtractor g, float x, float y, float scale) {
        Minecraft mc = Minecraft.getInstance();
        List<Case> cases = cases(System.currentTimeMillis());
        if (cases.isEmpty()) return;
        Font f = mc.font;
        NotifClient.Poids pd = NotifClient.poids();
        float dy = pd != null && pd.palier() >= NotifClient.CHARGE ? 12 : 0;
        g.pose().pushMatrix();
        g.pose().translate(x, y + dy * scale);
        if (scale != 1f) g.pose().scale(scale, scale);
        // survol (mode interaction, curseur libre) : coordonnées GUI → locales
        double mx = -1, my = -1;
        if (InteractionMode.INSTANCE.isActive()) {
            mx = (InteractionMode.INSTANCE.cursorGuiX() - x) / scale;
            my = (InteractionMode.INSTANCE.cursorGuiY() - y - dy * scale) / scale;
        }
        float cx = 2, ym = 6;
        for (Case c : cases) {
            EffetsIcones.poser(g, c.ic(), cx, ym - 5, 10, c.teinte());
            if (c.frac() >= 0) {
                int k = DaKit.echelleGui();
                DaKit.debutPx(g);
                int X0 = Math.round(cx * k), X1 = Math.round((cx + 10) * k), Y = Math.round((ym + 6) * k);
                g.fill(X0, Y, X1, Y + 1, DaKit.argb(0x0A0608, 0.55f));
                g.fill(X0, Y, X0 + Math.round((X1 - X0) * Math.max(0f, Math.min(1f, c.frac()))), Y + 1, c.colFilet());
                DaKit.finPx(g);
            }
            float tx = cx + 13;
            boolean survol = mx >= cx - 1 && mx <= cx + 11 && my >= ym - 6 && my <= ym + 7;
            if (c.minuteur() != null) tx += DaKit.inter7o(g, f, DaKit.inter(c.minuteur()), tx, ym, DaKit.CREME_VAL, 0) + 5;
            if ((c.montrerNom() || survol) && c.nom() != null) {
                tx += DaKit.inter7o(g, f, DaKit.interGras(c.nom()), tx, ym, c.colNom(), 0) + 3;
                if (c.sous() != null) tx += DaKit.inter7o(g, f, DaKit.inter(c.sous()), tx, ym, CREME_DOUX, 0) + 3;
            }
            cx = tx + 6;
        }
        g.pose().popMatrix();
    }

    private static List<Case> cases(long now) {
        List<Case> l = new ArrayList<>();
        // plat tenu en main
        NourritureClient.Main m = NourritureClient.main;
        if (m != null) {
            int r = m.chaudRestant(now);
            if (r > 0) l.add(new Case("chaud", DaKit.blanc(1f), "chaud · " + Math.max(1, (r + 59) / 60) + " min", r / 600f,
                DaKit.OR, null, 0, null, false));
            else l.add(new Case("froid", DaKit.blanc(0.9f), null, -1, 0, "Froid", CREME_DOUX, "effet réduit de moitié", true));
        }
        // satiété : pause de la régénération, Affamé, Faim
        VitalsFeed.Satiete s = VitalsFeed.satiete();
        if (s != null) {
            int p = s.pauseRestante();
            boolean effetEnPause = NourritureClient.effets().stream().anyMatch(NourritureClient.Effet::pause);
            if (p > 0 && !effetEnPause) l.add(new Case("pause", DaKit.blanc(0.9f), p + " s", p / 30f, GRIS, "En pause", DaKit.CREME_VAL,
                "coup reçu", true));
            if (s.faim()) l.add(new Case("faim", DaKit.blanc(1f), null, -1, 0, "Faim", VERM, "plus de sprint", true));
            else if (s.affame()) l.add(new Case("affame", DaKit.blanc(1f), null, -1, 0, "Affamé", AMBRE, "va manger", true));
        }
        // modificateurs
        for (NourritureClient.Effet e : NourritureClient.effets()) {
            int reste = e.resteMaintenant(now);
            if (reste <= 0) continue;
            boolean pause = e.pause();
            int col = pause ? GRIS : e.bonus() ? DaKit.OR : VERM;
            String min = reste >= 3600 ? reste / 3600 + " h" : reste / 60 + ":" + String.format("%02d", reste % 60);
            boolean court = e.val() != null && !e.val().isEmpty() && e.val().length() <= 8;
            String val = court ? e.val() : null;
            boolean recent = now - e.apparu() < NOM_MS;
            String nom, sous;
            boolean montrer = recent;
            if (pause) {
                // régénération figée par un coup : l'effet devient gris et dit pourquoi (une seule ligne, pas deux icônes)
                int p = s == null ? 0 : s.pauseRestante();
                nom = "En pause";
                sous = "coup reçu" + (p > 0 ? " · " + p + " s" : "");
                montrer = true;
            } else if (court) {
                nom = e.nom() + " " + val;
                sous = Math.max(1, (reste + 30) / 60) + " min";
            } else {
                nom = e.nom();
                sous = e.val() == null || e.val().isEmpty() ? Math.max(1, (reste + 30) / 60) + " min" : e.val();
            }
            // en pause : l'icône grise dédiée (« pause » = cœur gris) pour la régénération, sinon l'icône atténuée
            String ic = pause && "regeneration".equals(e.ic()) ? "pause" : e.ic();
            l.add(new Case(ic, pause ? (ic.equals("pause") ? DaKit.blanc(0.9f) : 0xD8A09A94) : DaKit.blanc(1f),
                (val != null ? val + " · " : "") + min, reste / (float) e.duree(), col,
                nom, pause ? DaKit.CREME_VAL : e.bonus() ? DaKit.OR_CLAIR : VERM, sous, montrer));
        }
        return l;
    }
}
