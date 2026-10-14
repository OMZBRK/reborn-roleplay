package fr.reborn.hud.radio;

import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.menu.settings.ReglagesArt;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Écran de l'escouade (touche N, réglable) — maquette validée v3/01 (14/10) : <b>centré, épuré, sans ornement ni
 * kanji</b>. Titre « Escouade Kagerō », « 4 membres sur 8 », puis une ligne par membre : tête du skin, nom, état
 * (point de couleur + « en ligne », « à terre », « hors ligne », « toi · créateur »…). Survoler un membre montre
 * « Exclure » (créateur seul ; « Couper l'oreillette » en plus si elle lui a été prise). En bas, trois mots soulignés au
 * survol : Renommer, Quitter, Dissoudre… (Renommer et Dissoudre : créateur seul ; Quitter et Dissoudre demandent un
 * second clic). Pas de bouton « Ajouter » : on ajoute en jeu, face à face, par le menu R.
 *
 * <p>Décor : {@code textures/gui/radio/panneau.png} ({@code tools/gen_radio_textures.py}), coupé à la hauteur utile ;
 * tout le reste (texte, têtes, points, survol) est posé par le code. Actions : commandes {@code /radio …}, le serveur
 * revérifie tout.
 */
public class RadioScreen extends Screen {

    private static final int W = 230, H_BASE = 196, PAS = 21, M = 4;
    private static final int CREME = 0xFFF5E9D0, DOUX = 0xFFC2B59A, VERT = 0xFF6FBF8E, VERM = 0xFFD2523E,
            AMBRE = 0xFFD9A95E, GRIS = 0xFF6A5C5C, SURVOL = 0xFF2A1A1F, OR = 0xFFD9A95E, PIED = 0xFF9A8A70,
            ROUGE = 0xFFD2523E;

    /** Action armée (second clic pour confirmer) : « quitter », « dissoudre », « exclure:<id> ». */
    private String arme;
    private long armeAt;
    private EditBox renommer;
    /** Banc d'essai : survol forcé d'une ligne (-1 aucun) ou d'une action (100 + index). */
    public int debugSurvol = -1;

    public RadioScreen() {
        super(Component.literal("Escouade"));
    }

    private int hauteur() {
        RadioClient.Escouade e = RadioClient.escouade();
        int n = e == null ? 0 : e.membres().size();
        return e == null ? 112 : H_BASE + Math.max(0, Math.min(8, n) - 5) * PAS;
    }

    private int px() { return (this.width - W) / 2; }

    private int py() { return (this.height - hauteur()) / 2 - 6; }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0x60080406);
        DaKit.etire(ctx, ReglagesArt.VIGNETTE, 0, 0, this.width, this.height, 160, 90, DaKit.blanc(0.55f));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (arme != null && System.currentTimeMillis() - armeAt > 4000) arme = null;
        Font f = this.font;
        int x = px(), y = py(), h = hauteur(), cx = x + W / 2;
        // un panneau pré-dessiné par hauteur (112, 196, 217, 238, 259) : un seul blit, pas de couture dans la laque
        Identifier panneau = DaKit.tex("radio", "panneau_" + h);
        DaKit.blit(ctx, panneau, x - M, y - M, 0, 0, W + 2 * M, h + 2 * M, W + 2 * M, h + 2 * M);
        RadioClient.Escouade e = RadioClient.escouade();
        RadioClient.Etat et = RadioClient.etat();
        if (e == null) {
            sansEscouade(ctx, f, cx, y, h, et);
        } else {
            if (renommer == null) {
                txt(ctx, f, DaKit.interGras("Escouade " + e.nom()), cx, y + 16, 10, CREME, 1);
            }
            String sous = e.n() + (e.n() > 1 ? " membres sur " : " membre sur ") + e.max();
            if (et.volee() && !et.proprio().isEmpty()) sous += "  ·  oreillette de " + et.proprio();
            txt(ctx, f, DaKit.inter(sous), cx, y + 31, 6.5f, DOUX, 1);
            membres(ctx, f, e, x, y, mouseX, mouseY);
            actions(ctx, f, e, cx, y + h - 26, mouseX, mouseY);
        }
        txt(ctx, f, DaKit.inter(RadioHud.touche(HudKeybinds.RADIO_ECRAN, "N") + "  fermer"), cx, y + h + 8, 6, PIED, 1);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void sansEscouade(GuiGraphicsExtractor ctx, Font f, int cx, int y, int h, RadioClient.Etat et) {
        txt(ctx, f, DaKit.interGras("Escouade"), cx, y + 16, 10, CREME, 1);
        String l1, l2;
        if (!RadioClient.serveur()) {
            l1 = "La radio n'est pas disponible sur ce serveur.";
            l2 = "";
        } else if ("aucune".equals(et.ore())) {
            l1 = "Il te faut une oreillette,";
            l2 = "portée en boucle d'oreille.";
        } else if ("coupee".equals(et.ore())) {
            l1 = "Ton oreillette est coupée :";
            l2 = "fais-la réparer chez un artisan.";
        } else if (!et.canal().isEmpty()) {
            l1 = "Tu entends l'escouade " + et.canal() + ",";
            l2 = "mais tu n'en fais pas partie.";
        } else {
            l1 = "Aucune escouade. Face à quelqu'un :";
            l2 = "menu R, Radio, Créer une escouade.";
        }
        txt(ctx, f, DaKit.inter(l1), cx, y + 58, 7, DOUX, 1);
        if (!l2.isEmpty()) txt(ctx, f, DaKit.inter(l2), cx, y + 70, 7, DOUX, 1);
    }

    private int survolLigne(List<RadioClient.Membre> l, int x, int y, int mx, int my) {
        if (debugSurvol >= 0 && debugSurvol < 100) return debugSurvol;
        int lx = x + W / 2 - 85;
        for (int i = 0; i < l.size(); i++) {
            int yy = y + 52 + i * PAS;
            if (DaKit.dans(mx, my, lx - 6, yy - 2, 182, 19)) return i;
        }
        return -1;
    }

    private void membres(GuiGraphicsExtractor ctx, Font f, RadioClient.Escouade e, int x, int y, int mx, int my) {
        List<RadioClient.Membre> l = e.membres();
        int lx = x + W / 2 - 85;
        int sv = survolLigne(l, x, y, mx, my);
        for (int i = 0; i < l.size(); i++) {
            RadioClient.Membre m = l.get(i);
            int yy = y + 52 + i * PAS;
            boolean actions = i == sv && e.createur() && !m.moi();
            if (i == sv) ctx.fill(lx - 6, yy - 2, lx + 176, yy + 17, SURVOL);
            boolean horsLigne = !m.enLigne();
            RadioHud.tete(ctx, m.id(), lx - 1, yy - 1, 14, horsLigne ? 0.7f : 1f);
            txt(ctx, f, m.moi() ? DaKit.interGras(m.nom()) : DaKit.inter(m.nom()), lx + 17, yy + 2, 7.5f,
                    horsLigne ? DOUX : CREME, 0);
            ctx.fill(lx + 108, yy + 6, lx + 111, yy + 9, couleur(m));
            if (actions) {
                float xr = lx + 170;
                String ex = ("exclure:" + m.id()).equals(arme) ? "Exclure ?" : "Exclure";
                float w = mot(ctx, f, ex, xr, yy + 2.5f, 6.5f, ex.endsWith("?") ? ROUGE : CREME, true, 2);
                if ("prise".equals(m.ore())) {
                    mot(ctx, f, "Couper l'oreillette", xr - w - 10, yy + 2.5f, 6.5f, DOUX, false, 2);
                }
            } else {
                txt(ctx, f, DaKit.inter(etat(m)), lx + 114, yy + 2.5f, 6.5f, DOUX, 0);
            }
        }
    }

    private static int couleur(RadioClient.Membre m) {
        return switch (m.etat()) {
            case "a_terre", "inconscient", "mort" -> VERM;
            case "hors_ligne" -> GRIS;
            default -> "ok".equals(m.ore()) || m.ore().isEmpty() ? VERT : AMBRE;
        };
    }

    static String etat(RadioClient.Membre m) {
        String base = switch (m.etat()) {
            case "a_terre" -> "à terre";
            case "inconscient" -> "inconscient";
            case "mort" -> "hors jeu";
            case "hors_ligne" -> "hors ligne";
            default -> switch (m.ore()) {
                case "coupee" -> "oreillette coupée";
                case "sans" -> "sans oreillette";
                case "prise" -> "oreillette prise";
                default -> "en ligne";
            };
        };
        if (m.moi()) return m.createur() ? "toi · créateur" : "toi";
        return m.createur() && "en ligne".equals(base) ? "en ligne · créateur" : base;
    }

    private record Act(String id, String label, int col) { }

    private List<Act> listeActions(RadioClient.Escouade e) {
        String q = "quitter".equals(arme) ? "Quitter ?" : "Quitter";
        String d = "dissoudre".equals(arme) ? "Confirmer la dissolution" : "Dissoudre…";
        if (!e.createur()) return List.of(new Act("quitter", q, "quitter".equals(arme) ? ROUGE : DOUX));
        return List.of(new Act("renommer", renommer != null ? "Valider" : "Renommer", DOUX),
                new Act("quitter", q, "quitter".equals(arme) ? ROUGE : DOUX), new Act("dissoudre", d, ROUGE));
    }

    private final float[][] zones = new float[3][];

    private void actions(GuiGraphicsExtractor ctx, Font f, RadioClient.Escouade e, int cx, int ay, int mx, int my) {
        List<Act> acts = listeActions(e);
        float tot = 0;
        for (Act a : acts) tot += f.width(DaKit.inter(a.label())) * 7f / 9f;
        tot += 18 * (acts.size() - 1);
        float xx = cx - tot / 2f;
        for (int i = 0; i < zones.length; i++) zones[i] = null;
        for (int i = 0; i < acts.size(); i++) {
            Act a = acts.get(i);
            float w = f.width(DaKit.inter(a.label())) * 7f / 9f;
            boolean hot = debugSurvol == 100 + i || DaKit.dans(mx, my, Math.round(xx) - 2, ay - 2, Math.round(w) + 4, 12);
            mot(ctx, f, a.label(), xx, ay, 7, hot && a.col() != ROUGE ? CREME : a.col(), hot, 0);
            zones[i] = new float[]{xx - 2, ay - 2, w + 4, 12};
            xx += w + 18;
        }
    }

    /** Mot cliquable : souligné d'or au survol. ancre 0 gauche, 2 droite. Renvoie la largeur. */
    private float mot(GuiGraphicsExtractor ctx, Font f, String s, float x, float ytop, float taille, int col, boolean souligne,
                      int ancre) {
        Component c = DaKit.inter(s);
        float w = f.width(c) * taille / 9f;
        float x0 = ancre == 2 ? x - w : x;
        txt(ctx, f, c, x0, ytop, taille, col, 0);
        if (souligne) ctx.fill(Math.round(x0), Math.round(ytop + taille + 1), Math.round(x0 + w) + 1, Math.round(ytop + taille + 2), OR);
        return w;
    }

    /** Inter à la taille {@code taille} (px de maquette à l'échelle des menus), haut du texte en ytop. ancre 0 / 1 / 2. */
    private static void txt(GuiGraphicsExtractor ctx, Font f, Component c, float x, float ytop, float taille, int col, int ancre) {
        float s = taille / 9f;
        float w = f.width(c) * s;
        float x0 = ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x;
        DaKit.texteEchelle(ctx, f, c, x0, ytop, col, s);
    }

    /* ------------------------------------------------------------------ entrées */

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        RadioClient.Escouade e = RadioClient.escouade();
        if (e == null) return super.mouseClicked(event, doubleClick);
        double mx = event.x(), my = event.y();
        int x = px(), y = py();
        // actions du bas
        List<Act> acts = listeActions(e);
        for (int i = 0; i < acts.size() && i < zones.length; i++) {
            float[] z = zones[i];
            if (z == null || !DaKit.dans(mx, my, Math.round(z[0]), Math.round(z[1]), Math.round(z[2]), Math.round(z[3]))) continue;
            agir(acts.get(i).id());
            return true;
        }
        // une ligne de membre (créateur : Exclure / Couper l'oreillette)
        List<RadioClient.Membre> l = e.membres();
        int i = survolLigne(l, x, y, (int) mx, (int) my);
        if (i >= 0 && e.createur() && !l.get(i).moi()) {
            RadioClient.Membre m = l.get(i);
            int lx = x + W / 2 - 85, yy = y + 52 + i * PAS;
            float wEx = this.font.width(DaKit.inter("Exclure")) * 6.5f / 9f;
            if (mx >= lx + 170 - wEx - 4) {
                String cle = "exclure:" + m.id();
                if (cle.equals(arme)) {
                    RadioClient.send("radio exclure " + m.id());
                    arme = null;
                    RebornSounds.confirm();
                } else {
                    armer(cle);
                }
                return true;
            }
            if ("prise".equals(m.ore()) && mx >= lx + 60 && my >= yy - 2) {
                RadioClient.send("radio exclure-oreillette " + m.id());
                RebornSounds.confirm();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void agir(String id) {
        switch (id) {
            case "renommer" -> {
                if (renommer != null) { validerNom(); return; }
                ouvrirRenommer();
            }
            case "quitter" -> {
                if ("quitter".equals(arme)) { RadioClient.send("radio quitter"); RebornSounds.confirm(); onClose(); }
                else armer("quitter");
            }
            case "dissoudre" -> {
                if ("dissoudre".equals(arme)) { RadioClient.send("radio dissoudre confirmer"); RebornSounds.confirm(); onClose(); }
                else armer("dissoudre");
            }
            default -> { }
        }
    }

    private void armer(String cle) {
        arme = cle;
        armeAt = System.currentTimeMillis();
        RebornSounds.uiClick();
    }

    private void ouvrirRenommer() {
        RadioClient.Escouade e = RadioClient.escouade();
        int cx = px() + W / 2;
        renommer = new EditBox(this.font, cx - 70, py() + 13, 140, 12, Component.literal("Nom de l'escouade"));
        renommer.setMaxLength(24);
        renommer.setValue(e == null ? "" : e.nom());
        renommer.setTextColor(CREME);
        this.addRenderableWidget(renommer);
        this.setFocused(renommer);
        renommer.setFocused(true);
        RebornSounds.uiClick();
    }

    private void validerNom() {
        if (renommer == null) return;
        String v = renommer.getValue().trim();
        if (v.length() >= 2) RadioClient.send("radio renommer " + v);
        this.removeWidget(renommer);
        renommer = null;
        RebornSounds.confirm();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();
        if (renommer != null) {
            if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { validerNom(); return true; }
            if (k == GLFW.GLFW_KEY_ESCAPE) { this.removeWidget(renommer); renommer = null; return true; }
            return super.keyPressed(event);
        }
        if (HudKeybinds.RADIO_ECRAN != null && HudKeybinds.RADIO_ECRAN.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    /** Banc d'essai : ouvre le champ « Renommer » avec un nom tapé. */
    public void debugRenommer(String nom) {
        ouvrirRenommer();
        renommer.setValue(nom);
    }

    /** Banc d'essai : arme une action (« quitter », « dissoudre »). */
    public void debugArmer(String cle) {
        arme = cle;
        armeAt = System.currentTimeMillis();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(null);
    }
}
