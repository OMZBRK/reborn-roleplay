package fr.reborn.hud.staff;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotGui;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Effets staff vus par les joueurs (PLUGINS_REFERENCE §4 bis.3-4), en <b>HUD subtil</b> (DA §6, règle 9) :
 * <ul>
 *   <li><b>Annonce</b> ({@code reborn:annonce}) : une ligne fine en haut au centre, sur un voile nuit très léger —
 *       minuscule marque carrée (globale vermillon, personnelle or ; aucun kanji), titre en petites capitales d'or,
 *       texte Inter 8 crème ; un filet d'or d'1 px se résorbe pendant la durée ; fondu d'entrée et de sortie.</li>
 *   <li><b>Écran noir</b> ({@code reborn:ecran} {@code noir}) : fondu au noir au-dessus du HUD, le tchat reste
 *       lisible par-dessus ; texte centré sobre (Inter 9 crème, filet d'or).</li>
 *   <li><b>Séisme</b> ({@code reborn:ecran} {@code seisme}) : légère secousse de la caméra
 *       ({@code mixin/CameraSeismeMixin}), amplitude ∝ force / 10, décroissante ; multipliée par le réglage
 *       d'accessibilité vanilla « Effets de distorsion » (0 % = aucune secousse). Le son vient du serveur.</li>
 * </ul>
 */
public final class EffetsStaff {

    private static final Identifier VOILE = DaKit.tex("duel", "voile");
    private static final int VERMILLON = 0xFFA0182B;

    /** Annonce en cours (null = aucune). */
    record Annonce(boolean perso, String titre, String texte, long at, long dureeMs) { }

    static volatile Annonce annonce;

    /* écran noir */
    private static volatile boolean noirOn;

    /** Écran noir du staff en cours (voile imposé : ni mode photo, ni F1 ne le lèvent). */
    public static boolean noirActif() { return noirOn; }
    private static volatile long noirDepuis, noirFonduMs = 1000;
    private static volatile float noirDepart;
    private static volatile String noirTexte = "";

    /* séisme */
    private static volatile long seismeAt, seismeMs;
    private static volatile float seismeForce;

    private static HudElement chatOrigine;

    private EffetsStaff() {}

    static void init() {
        PayloadTypeRegistry.clientboundPlay().register(CanalJson.ANNONCE, CanalJson.codec(CanalJson.ANNONCE));
        PayloadTypeRegistry.clientboundPlay().register(CanalJson.ECRAN, CanalJson.codec(CanalJson.ECRAN));
        ClientPlayNetworking.registerGlobalReceiver(CanalJson.ANNONCE, (p, c) -> c.client().execute(() -> recevoir(p.json())));
        ClientPlayNetworking.registerGlobalReceiver(CanalJson.ECRAN, (p, c) -> c.client().execute(() -> recevoir(p.json())));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> effacer());
        // Le tchat passe au-dessus du noir : on le retire de sa place tant que le noir est visible et on le redessine
        // juste après le noir.
        HudElementRegistry.replaceElement(VanillaHudElements.CHAT, orig -> {
            chatOrigine = orig;
            return (g, dt) -> { if (opaciteNoir() <= 0f) orig.extractRenderState(g, dt); };
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("reborn-hud", "staff-effets"), EffetsStaff::render);
    }

    static void effacer() {
        annonce = null;
        noirOn = false;
        noirDepart = 0f;
        noirDepuis = 0;
        seismeMs = 0;
    }

    /** Message des canaux {@code reborn:annonce} et {@code reborn:ecran} (aussi utilisé par le banc). */
    static void recevoir(String raw) {
        JsonObject o;
        try { o = JsonParser.parseString(raw).getAsJsonObject(); } catch (RuntimeException e) { return; }
        String t = StaffScreen.str(o, "t");
        long now = System.currentTimeMillis();
        switch (t) {
            case "annonce" -> {
                String texte = StaffScreen.str(o, "texte");
                if (texte.isBlank()) return;
                int s = o.has("s") ? Math.max(2, Math.min(60, StaffScreen.num(o, "s"))) : 8;
                annonce = new Annonce("perso".equals(StaffScreen.str(o, "portee")), StaffScreen.str(o, "titre"), texte, now, s * 1000L);
            }
            case "noir" -> {
                boolean on = StaffScreen.bool(o, "on");
                float actuel = opaciteNoir();
                noirFonduMs = Math.max(50, (o.has("fondu") ? StaffScreen.num(o, "fondu") : 20) * 50L);
                noirDepart = actuel;
                noirDepuis = now;
                noirOn = on;
                if (on) noirTexte = StaffScreen.str(o, "texte");
            }
            case "seisme" -> {
                seismeForce = Math.max(1, Math.min(10, o.has("f") ? StaffScreen.num(o, "f") : 5)) / 10f;
                seismeMs = Math.max(500, (o.has("s") ? StaffScreen.num(o, "s") : 4) * 1000L);
                seismeAt = now;
            }
            default -> { }
        }
    }

    /** Opacité du noir (0..1), fondu compris. */
    static float opaciteNoir() {
        if (noirDepuis == 0) return 0f;
        float u = Math.min(1f, (System.currentTimeMillis() - noirDepuis) / (float) noirFonduMs);
        float cible = noirOn ? 1f : 0f;
        return noirDepart + (cible - noirDepart) * u;
    }

    /**
     * Décalage de la caméra du séisme {lacet, tangage} en degrés (0 hors séisme) ; réglage d'accessibilité
     * « Effets de distorsion » appliqué.
     */
    public static float[] secousse() {
        long ms = seismeMs;
        if (ms <= 0) return null;
        long dt = System.currentTimeMillis() - seismeAt;
        if (dt >= ms) { seismeMs = 0; return null; }
        Minecraft mc = Minecraft.getInstance();
        double acces = mc.options == null ? 1.0 : mc.options.screenEffectScale().get();
        if (acces <= 0.001) return null;
        float rest = 1f - dt / (float) ms;
        float amp = (float) (seismeForce * rest * Math.sqrt(rest) * acces);
        double t = dt / 1000.0;
        float yaw = (float) ((Math.sin(t * 71.0) * 0.6 + Math.sin(t * 43.0 + 1.3) * 0.4) * 1.6 * amp);
        float pitch = (float) ((Math.sin(t * 59.0 + 0.7) * 0.6 + Math.sin(t * 37.0 + 2.1) * 0.4) * 1.1 * amp);
        return new float[]{yaw, pitch};
    }

    /* ================================================================ rendu */

    private static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float noir = opaciteNoir();
        if (noir > 0f) {
            int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
            g.fill(0, 0, w, h, DaKit.argb(0x000000, noir));
            if (!noirOn && noir <= 0.001f) noirDepuis = 0;
            if (chatOrigine != null) chatOrigine.extractRenderState(g, dt);
            if (noirTexte != null && !noirTexte.isBlank()) texteNoir(g, mc.font, noir);
        }
        Annonce a = annonce;
        if (a != null) {
            long age = System.currentTimeMillis() - a.at();
            if (age > a.dureeMs() + 450) annonce = null;
            else if (!mc.gui.hud.isHidden() || noir > 0f) ligneAnnonce(g, mc.font, a, age);
        }
        if ((StaffClient.modeAnim || StaffClient.modeStaff) && !mc.gui.hud.isHidden() && !(mc.gui.screen() instanceof StaffScreen))
            indicateurMode(g, mc.font);
    }

    /**
     * Indicateur discret du mode du staff (mode staff / mode animation), en haut à droite : pastille et un mot,
     * Inter 7 sur rien (DA §6, règle 9). Seul le staff concerné le voit.
     */
    private static void indicateurMode(GuiGraphicsExtractor g, Font f) {
        var win = Minecraft.getInstance().getWindow();
        int km = DaKit.echelleMenu();
        int sw = win.getWidth() / km;
        DaKit.unites(g, km);
        boolean anim = StaffClient.modeAnim;
        Component c = DaKit.inter(anim ? "Mode animation · journalisé" : "Mode staff · hors RP");
        float w = DaKit.largeur7(f, c);
        float x = sw - 8 - w;
        int col = anim ? 0xFFB48CD8 : 0xFF80B68C;
        LotGui l = LotGui.ouvrir(g);
        l.fill(Math.round(x) - 6, 7, Math.round(x) - 3, 10, DaKit.alpha(col, 0.85f));
        l.fermer();
        DaKit.inter7o(g, f, c, x, 8.5f, DaKit.alpha(DaKit.CREME_VAL, 0.8f), 0);
        DaKit.finUnites(g);
    }

    private static void texteNoir(GuiGraphicsExtractor g, Font f, float a) {
        var win = Minecraft.getInstance().getWindow();
        int km = DaKit.echelleMenu();
        int sw = win.getWidth() / km, sh = win.getHeight() / km;
        DaKit.unites(g, km);
        DaKit.Paragraphe p = DaKit.paragraphe(f, noirTexte, Math.min(320, sw * 0.6f), 1f, 11, 3);
        int y = sh / 2 - p.hauteur() / 2 - 4;
        for (int i = 0; i < p.lignes().size(); i++) {
            var l = p.lignes().get(i);
            float lw = f.width(l);
            DaKit.texteEchelle(g, f, l, sw / 2f - lw / 2f, y + i * 11, DaKit.alpha(DaKit.CREME_VAL, a), 1f);
        }
        int fy = y + p.hauteur() + 4;
        g.fill(sw / 2 - 18, fy, sw / 2 + 18, fy + 1, DaKit.alpha(DaKit.OR_ETEINT, a));
        DaKit.finUnites(g);
    }

    /** Ligne d'annonce, en unités menu, haut de l'écran (sous l'indicateur de duel). */
    private static void ligneAnnonce(GuiGraphicsExtractor g, Font f, Annonce an, long age) {
        float a = Math.min(1f, age / 250f);
        if (age > an.dureeMs()) a = Math.max(0f, 1f - (age - an.dureeMs()) / 450f);
        if (a <= 0f) return;
        var win = Minecraft.getInstance().getWindow();
        int km = DaKit.echelleMenu();
        int sw = win.getWidth() / km;
        DaKit.unites(g, km);
        float maxTexte = Math.max(120, sw * 0.42f);
        Component titre = an.titre().isBlank() ? null : DaKit.arcade(an.titre());
        float wTitre = titre == null ? 0 : DaKit.largeurFin(f, titre) + 8;
        DaKit.Paragraphe p = DaKit.paragraphe(f, an.texte(), maxTexte, DaKit.S8, 9, 2);
        float wTexte = 0;
        for (var l : p.lignes()) wTexte = Math.max(wTexte, f.width(l) * DaKit.S8);
        float w = 10 + 6 + wTitre + wTexte;
        float x = sw / 2f - w / 2f;
        int y0 = 26, h = 8 + p.hauteur();
        DaKit.etire(g, VOILE, Math.round(x) - 22, y0 - 3, Math.round(w) + 44, h + 6, 64, 16, DaKit.blanc(0.8f * a));
        // sceau
        int sx = Math.round(x), sy = y0 + h / 2 - 5;
        LotGui l = LotGui.ouvrir(g);
        // petite marque carrée (aucun kanji, DA §3) : vermillon = à tous, or = personnelle
        l.fill(sx + 3, sy + 3, sx + 7, sy + 7, DaKit.alpha(an.perso() ? DaKit.OR_ETEINT : VERMILLON, a));
        // filet d'or qui se résorbe (durée restante)
        float reste = Math.max(0f, 1f - age / (float) an.dureeMs());
        int fw = Math.round((w - 16) * reste), fx = Math.round(x + 16 + (w - 16 - fw) / 2f), fy = y0 + h + 1;
        l.fill(fx, fy, fx + fw, fy + 1, DaKit.alpha(DaKit.OR_ETEINT, a));
        l.fermer();
        float tx = x + 16;
        float ym = y0 + 4 + (p.lignes().size() > 1 ? 0 : 0.5f);
        if (titre != null) {
            DaKit.arcFin(g, f, titre, tx, ym + 0.5f, DaKit.alpha(DaKit.OR_CLAIR, a), 0, false);
            tx += wTitre;
        }
        for (int i = 0; i < p.lignes().size(); i++) {
            DaKit.texteEchelle(g, f, p.lignes().get(i), tx, y0 + 1 + i * 9, DaKit.alpha(DaKit.CREME_VAL, a), DaKit.S8);
        }
        DaKit.finUnites(g);
    }

    /* ================================================================ banc */

    static void debugAnnonce(boolean perso, String titre, String texte, long ageMs) {
        annonce = new Annonce(perso, titre, texte, System.currentTimeMillis() - ageMs, 8000);
    }
}
