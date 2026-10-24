package fr.reborn.hud.nourriture;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementBounds;
import fr.reborn.hud.element.HudElementState;
import fr.reborn.hud.etat.EtatClient;
import fr.reborn.hud.interaction.InteractionMode;
import fr.reborn.hud.notif.NotifClient;
import fr.reborn.hud.runtime.RebornSession;
import fr.reborn.hud.runtime.VitalsFeed;
import fr.reborn.hud.runtime.VitalsHud;
import fr.reborn.hud.ui.DaKit;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <b>Zone d'effets</b> (GAMEPLAN §4, maquettes nourriture v3 03 et 04 ; élément {@link HudElement#EFFETS} de l'éditeur
 * de HUD depuis le 19/10, déplaçable et masquable) : une rangée de petites icônes (10 GUI), chacune avec son minuteur
 * et un filet d'1 px sous l'icône (temps restant). <b>Or</b> = bonus, <b>vermillon</b> = malus, <b>gris</b> = en
 * pause. Le nom s'affiche 3 s à l'apparition, puis seulement au survol en mode interaction (curseur libre). Rien
 * d'autre (DA §6, règle 9) : pas de cadre, pas de plaque.
 *
 * <p>Sources, sans en inventer : l'état du joueur tenu par le serveur ({@code reborn:etat}, {@link EtatClient} :
 * douleur, ATA, prison et sa peine, menottes, étourdi, gel du staff, Affamé / Faim), la satiété des vitals (plat
 * chaud / froid en main, régénération en pause, Affamé, Faim — repli quand {@code reborn:etat} manque) et les
 * modificateurs du registre ({@code reborn:modifs} : repas, pilule, poison, fatigue…).
 */
public final class EffetsHud {

    /** Boîte de l'élément à l'échelle 1 (GUI) : cinq icônes et leurs minuteurs. */
    public static final int LARGEUR = 170, HAUTEUR = 14;

    private static final int VERM = 0xFFD2523E, AMBRE = 0xFFE0A050, GRIS = 0xFF8A8480, CREME_DOUX = 0xFFC2B59A;
    private static final long NOM_MS = 3000;

    private record Case(String ic, int teinte, String minuteur, float frac, int colFilet, String nom, int colNom,
                        String sous, boolean montrerNom) {}

    /** Première apparition des états (nom montré 3 s), par clé. */
    private static final Map<String, Long> VUS = new HashMap<>();

    private EffetsHud() {}

    /** Couche HUD de l'élément (appelée une fois par {@code RebornHudClient}). */
    public static void init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("reborn-hud", "effets"), (g, dt) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options == null || mc.gui.hud.isHidden()) return;
            if (!RebornSession.rpFeaturesEnabled()) return;
            if (mc.gui.screen() instanceof fr.reborn.hud.ui.HudEditScreen) return;   // l'éditeur montre un aperçu
            HudElementState st;
            try { st = RebornHudClient.config().stateOf(HudElement.EFFETS); } catch (RuntimeException e) { return; }
            if (!st.visible()) return;
            int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
            HudElementBounds b = HudElementBounds.currentFor(HudElement.EFFETS, st, w, h);
            render(g, b.x(), b.y() + decalageSurcharge(b, w, h), st.scale());
        });
    }

    /**
     * Sous la ligne « Chargé / Surchargé » des vitals quand elle s'affiche et que la zone est restée à sa place (sous
     * les vitals) : 12 GUI plus bas, comme avant le 19/10.
     */
    private static float decalageSurcharge(HudElementBounds b, int w, int h) {
        NotifClient.Poids pd = NotifClient.poids();
        if (pd == null || pd.palier() < NotifClient.CHARGE) return 0;
        HudElementState vs;
        try { vs = RebornHudClient.config().stateOf(HudElement.VITALS); } catch (RuntimeException e) { return 0; }
        if (!vs.visible()) return 0;
        HudElementBounds v = HudElementBounds.currentFor(HudElement.VITALS, vs, w, h);
        float sc = vs.scale();
        float sx = v.x() + 56 * sc, sy = v.y() + (VitalsHud.HEIGHT + 1) * sc, sw = (VitalsHud.WIDTH - 59) * sc;
        boolean recouvre = b.x() < sx + sw && b.x() + b.width() > sx && b.y() < sy + 10 * sc && b.y() + b.height() > sy - 2;
        return recouvre ? 12 * sc : 0;
    }

    /**
     * @param x     gauche de la zone (GUI)
     * @param y     haut de la zone (GUI)
     * @param scale échelle de l'élément
     */
    public static void render(GuiGraphicsExtractor g, float x, float y, float scale) {
        dessiner(g, x, y, scale, cases(System.currentTimeMillis()), true);
    }

    /** Éditeur de HUD : des effets d'exemple pour voir la taille et la place. */
    public static void apercu(GuiGraphicsExtractor g, float x, float y, float scale) {
        List<Case> l = new ArrayList<>();
        l.add(new Case("regeneration", DaKit.blanc(1f), "×2 · 4:12", 0.84f, DaKit.OR, null, 0, null, false));
        l.add(new Case("pilule", DaKit.blanc(1f), "+40 % · 2:45", 0.9f, DaKit.OR, null, 0, null, false));
        l.add(new Case("poison", DaKit.blanc(1f), "1:24", 0.7f, VERM, null, 0, null, false));
        l.add(new Case("affame", DaKit.blanc(1f), null, -1, 0, null, 0, null, false));
        dessiner(g, x, y, scale, l, false);
    }

    private static void dessiner(GuiGraphicsExtractor g, float x, float y, float scale, List<Case> cases, boolean survolable) {
        if (cases.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Font f = mc.font;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        if (scale != 1f) g.pose().scale(scale, scale);
        // survol (mode interaction, curseur libre) : coordonnées GUI → locales
        double mx = -1, my = -1;
        if (survolable && InteractionMode.INSTANCE.isActive()) {
            mx = (InteractionMode.INSTANCE.cursorGuiX() - x) / scale;
            my = (InteractionMode.INSTANCE.cursorGuiY() - y) / scale;
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

    private static String minuteur(int reste) {
        if (reste < 0) return null;
        if (reste >= 6 * 3600) return null;              // « jusqu'à nouvel ordre » (menottes) : pas de minuteur
        if (reste >= 3600) return reste / 3600 + " h";
        return reste / 60 + ":" + String.format("%02d", reste % 60);
    }

    /** Un état vient-il d'apparaître (nom montré 3 s) ? */
    private static boolean recent(String cle, long now, Set<String> presents) {
        presents.add(cle);
        Long t = VUS.putIfAbsent(cle, now);
        return t == null || now - t < NOM_MS;
    }

    private static List<Case> cases(long now) {
        List<Case> l = new ArrayList<>();
        Set<String> presents = new HashSet<>();

        // ---------------------------------------------------------------- l'état tenu par le serveur (reborn:etat)
        EtatClient.Etat e = EtatClient.etat();
        if (e != null) {
            if ("DOULEUR".equals(e.vital())) l.add(new Case("douleur", DaKit.blanc(1f), null, -1, 0, "Douleur", VERM,
                "à bout de forces", recent("douleur", now, presents)));
            EtatClient.Mod ata = EtatClient.mod("ata");
            if (ata != null) l.add(new Case("ata", DaKit.blanc(1f), null, -1, 0, ata.n() >= 2 ? "ATA pleine" : "ATA allégée",
                VERM, "trop blessé", recent("ata", now, presents)));
            EtatClient.Mod pr = EtatClient.mod("prison");
            if (pr != null) {
                int r = pr.resteS();
                l.add(new Case("prison", DaKit.blanc(1f), minuteur(r), -1, 0, "En prison", VERM, null,
                    recent("prison", now, presents)));
            }
            if (EtatClient.mod("menottes") != null) l.add(new Case("menottes", DaKit.blanc(1f), null, -1, 0, "Menotté", VERM,
                null, recent("menottes", now, presents)));
            if ("ETOURDI".equals(e.ctrl())) l.add(new Case("etourdi", DaKit.blanc(1f), null, -1, 0, "Étourdi", AMBRE, null, false));
            if (EtatClient.mod("gel_staff") != null) l.add(new Case("pause", DaKit.blanc(0.9f), null, -1, 0, "Gelé", DaKit.CREME_VAL,
                "par le staff", recent("gel", now, presents)));
        }

        // ---------------------------------------------------------------- satiété : Faim, Affamé, pause, plat en main
        VitalsFeed.Satiete s = VitalsFeed.satiete();
        boolean faim = EtatClient.mod("faim") != null || (s != null && s.faim());
        boolean affame = !faim && (EtatClient.mod("affame") != null || (s != null && s.affame()));
        if (faim) l.add(new Case("faim", DaKit.blanc(1f), null, -1, 0, "Faim", VERM, "ni sprint ni dash", recent("faim", now, presents)));
        else if (affame) l.add(new Case("affame", DaKit.blanc(1f), null, -1, 0, "Affamé", AMBRE, "va manger",
            recent("affame", now, presents)));
        if (s != null) {
            int p = s.pauseRestante();
            boolean effetEnPause = NourritureClient.effets().stream().anyMatch(NourritureClient.Effet::pause);
            if (p > 0 && !effetEnPause) l.add(new Case("pause", DaKit.blanc(0.9f), p + " s", p / 30f, GRIS, "En pause", DaKit.CREME_VAL,
                "coup reçu", true));
        }
        NourritureClient.Main m = NourritureClient.main;
        if (m != null) {
            int r = m.chaudRestant(now);
            if (r > 0) l.add(new Case("chaud", DaKit.blanc(1f), "chaud · " + Math.max(1, (r + 59) / 60) + " min", r / 600f,
                DaKit.OR, null, 0, null, false));
            else l.add(new Case("froid", DaKit.blanc(0.9f), null, -1, 0, "Froid", CREME_DOUX, "effet réduit de moitié", true));
        }

        // ---------------------------------------------------------------- modificateurs du registre (reborn:modifs)
        for (NourritureClient.Effet ef : NourritureClient.effets()) {
            int reste = ef.resteMaintenant(now);
            if (reste <= 0) continue;
            boolean pause = ef.pause();
            int col = pause ? GRIS : ef.bonus() ? DaKit.OR : VERM;
            String min = minuteur(reste);
            boolean court = ef.val() != null && !ef.val().isEmpty() && ef.val().length() <= 8;
            String val = court ? ef.val() : null;
            boolean recentEf = now - ef.apparu() < NOM_MS;
            String nom, sous;
            boolean montrer = recentEf;
            if (pause) {
                // régénération figée par un coup : l'effet devient gris et dit pourquoi (une seule ligne, pas deux icônes)
                int p = s == null ? 0 : s.pauseRestante();
                nom = "En pause";
                sous = "coup reçu" + (p > 0 ? " · " + p + " s" : "");
                montrer = true;
            } else if (court) {
                nom = ef.nom() + " " + val;
                sous = Math.max(1, (reste + 30) / 60) + " min";
            } else {
                nom = ef.nom();
                sous = ef.val() == null || ef.val().isEmpty() ? Math.max(1, (reste + 30) / 60) + " min" : ef.val();
            }
            // en pause : l'icône grise dédiée (« pause » = cœur gris) pour la régénération, sinon l'icône atténuée
            String ic = pause && "regeneration".equals(ef.ic()) ? "pause" : ef.ic();
            String mn = min == null ? val : (val != null ? val + " · " : "") + min;
            l.add(new Case(ic, pause ? (ic.equals("pause") ? DaKit.blanc(0.9f) : 0xD8A09A94) : DaKit.blanc(1f),
                mn, min == null ? -1 : reste / (float) ef.duree(), col,
                nom, pause ? DaKit.CREME_VAL : ef.bonus() ? DaKit.OR_CLAIR : VERM, sous, montrer));
        }
        VUS.keySet().retainAll(presents);
        return l;
    }
}
