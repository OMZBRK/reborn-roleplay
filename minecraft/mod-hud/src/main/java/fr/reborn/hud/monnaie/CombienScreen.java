package fr.reborn.hud.monnaie;

import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * « Combien ? » — le petit écran commun (maquettes 02 et 05) : <b>poser</b> au sol, <b>ranger</b> dans un coffre,
 * <b>prendre</b> dans la bourse d'un corps. Panneau laqué 170 × 104, titre, question, champ avec la pièce, raccourcis
 * en mots (10 · 100 · moitié · tout, ou 500 · 1 000 · le plafond), une seule plaque « Poser · Entrée », « Annuler ».
 * L'écran d'avant (sacoche, coffre, fouille) reste peint derrière ; Échap y revient.
 */
public final class CombienScreen extends Screen {

    static final Identifier PANNEAU = DaKit.tex("monnaie", "panneau"), PIECE = DaKit.tex("monnaie", "piece");
    static final int CREME = 0xFFEEE4D2, CREME_DOUX = 0xFFC2B59A, OR = 0xFFD9A95E, OR_CLAIR = 0xFFF2D08A,
        AMBRE = 0xFFE2A04A, ROUGE = 0xFFE46676;

    private record Mot(String label, long valeur) {}

    private final Screen parent;
    private final String mode, titre, question, action;
    private final String info;
    private final long max;
    private final LongConsumer valider;
    private final List<Mot> mots = new ArrayList<>();
    private final List<float[]> motsZones = new ArrayList<>();
    private EditBox champ;
    private final Pinceau pz = new Pinceau();
    /** Ancre (coin haut gauche du panneau) ; null = centré. */
    private Float ancreX, ancreY;
    private int x0, y0, w = 170, h = 104;
    private float bx, by, bw, ax;
    private String erreur;

    public CombienScreen(Screen parent, String mode, String titre, String question, long max, String action, String info,
                         LongConsumer valider) {
        super(Component.literal(titre));
        this.parent = parent;
        this.mode = mode == null ? "" : mode;
        this.titre = titre;
        this.question = question;
        this.max = Math.max(0, max);
        this.action = action;
        this.info = info;
        this.valider = valider;
        if (this.mode.equals("vol")) {
            mots.add(new Mot("500", 500));
            mots.add(new Mot("1 000", 1000));
            mots.add(new Mot(info != null && info.contains("au plus") ? "le plafond" : "tout", this.max));
        } else {
            mots.add(new Mot("10", 10));
            mots.add(new Mot("100", 100));
            mots.add(new Mot("moitié", this.max / 2));
            mots.add(new Mot("tout", this.max));
        }
        if (info != null && !info.isEmpty()) h = 114;
    }

    /** Bourse → sol : « Combien veux-tu poser ? ». */
    public static CombienScreen poser(Screen parent, long bourse) {
        return new CombienScreen(parent, "poser", "Poser au sol", "Combien veux-tu poser ?", bourse, "Poser", null,
            n -> MonnaieClient.envoyer("poser:" + n));
    }

    public CombienScreen ancre(float x, float y) { this.ancreX = x; this.ancreY = y; return this; }

    public Screen parent() { return parent; }

    @Override
    protected void init() {
        x0 = ancreX != null ? Math.round(ancreX) : (this.width - w) / 2;
        y0 = ancreY != null ? Math.round(ancreY) : (this.height - h) / 2 - 10;
        x0 = Math.max(4, Math.min(this.width - w - 4, x0));
        y0 = Math.max(4, Math.min(this.height - h - 4, y0));
        String avant = champ != null ? champ.getValue() : "";
        champ = new EditBox(this.font, 0, 0, 74, 10, Component.literal("montant"));
        champ.setBordered(false);
        champ.setMaxLength(12);
        champ.setTextColor(CREME);
        final EditBox cb = champ;
        champ.setResponder(v -> { String d = v.replaceAll("[^0-9]", ""); if (!d.equals(v)) cb.setValue(d); });
        champ.setValue(valeurDebut >= 0 ? String.valueOf(valeurDebut) : avant);
        valeurDebut = -1;
        int cx = x0 + w / 2;
        champ.setX(cx - 40);
        champ.setY(y0 + 38);
        addRenderableWidget(champ);
        setFocused(champ);
        champ.setFocused(true);
        if (parent != null && (parent.width != this.width || parent.height != this.height)) parent.resize(this.width, this.height);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        if (parent != null) {
            parent.extractBackground(ctx, -1, -1, delta);
            parent.extractRenderState(ctx, -1, -1, delta);
            ctx.nextStratum();
        }
        ctx.fill(0, 0, this.width, this.height, DaKit.argb(0x08040A, parent != null ? 0.38f : 0.30f));
    }

    private long valeur() {
        try { return champ.getValue().isEmpty() ? 0 : Long.parseLong(champ.getValue()); } catch (NumberFormatException e) { return 0; }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        pz.police(this.font);
        DaKit.coupe(ctx, PANNEAU, x0, y0, w + 6, h + 6, 0, 0, 316, 346, 16, 16, 316, 346);
        float cx = x0 + w / 2f;
        pz.texteC(titre, cx, y0 + 15, 18, CREME, true);
        pz.texteC(question, cx, y0 + 27, 14, CREME_DOUX, false);
        // champ : fond nuit, soulignement or, la pièce à droite
        pz.fond(cx - 45, y0 + 34, cx + 45, y0 + 49, 0xEB080507);
        pz.px(cx - 45, y0 + 48.5f, cx + 45, y0 + 49, champ.isFocused() ? OR : 0xFF6A4E24);
        // raccourcis
        motsZones.clear();
        float gap = 7, tot = -gap;
        long v = valeur();
        for (Mot m : mots) tot += pz.largeur(m.label(), 14, v == m.valeur() && v > 0) + gap;
        float x = cx - tot / 2f, ym = y0 + 61;
        for (Mot m : mots) {
            boolean act = v == m.valeur() && v > 0;
            float lw = pz.largeur(m.label(), 14, act);
            boolean hov = DaKit.dans(mx, my, Math.round(x - 2), Math.round(ym - 6), Math.round(lw + 4), 12);
            pz.texte(m.label(), x, ym, 14, act || hov ? CREME : CREME_DOUX, act);
            if (act) pz.px(x, ym + 6, x + lw, ym + 6.5f, OR);
            motsZones.add(new float[] {x - 2, ym - 6, lw + 4, 12, m.valeur()});
            x += lw + gap;
        }
        if (erreur != null) pz.texteC(erreur, cx, y0 + 73, 13, ROUGE, false);
        else if (info != null && !info.isEmpty()) pz.texteC(info, cx, y0 + 73, 13, AMBRE, false);
        // plaque « Poser · Entrée » + « Annuler »
        String lab = action + " · Entrée";
        bw = pz.largeur(lab, 15, true) + 22;
        float bcx = cx - 17;
        bx = bcx - bw / 2f;
        by = y0 + h - 27;
        boolean hb = DaKit.dans(mx, my, Math.round(bx), Math.round(by), Math.round(bw), 16);
        pz.fond(bx, by, bx + bw, by + 16, hb ? 0xFF5A1622 : 0xFF3C0E16);
        pz.cadre(bx, by, bw, 16, OR);
        pz.px(bx + 0.5f, by + 0.5f, bx + bw - 0.5f, by + 1f, 0xFF962834);
        pz.texteC(lab, bcx, by + 8, 15, CREME, true);
        ax = bx + bw + 9;
        boolean ha = DaKit.dans(mx, my, Math.round(ax - 2), Math.round(by), Math.round(pz.largeur("Annuler", 15, false) + 4), 16);
        pz.texte("Annuler", ax, by + 8, 15, ha ? CREME : CREME_DOUX, false);
        pz.vider(ctx);
        DaKit.blit(ctx, PIECE, Math.round(cx + 32), y0 + 35, 0, 0, 12, 12, 12, 12);
        champ.extractRenderState(ctx, mx, my, delta);
    }

    private void confirmer() {
        long n = valeur();
        if (n <= 0) { erreur = "Indique un montant."; RebornSounds.playReborn("stats.deny", 1f, 0.6f); return; }
        if (max > 0 && n > max) { erreur = "Au plus " + BourseClient.fmt(max) + " ryo."; RebornSounds.playReborn("stats.deny", 1f, 0.6f); return; }
        RebornSounds.playReborn("sacoche.place", 1f, 0.6f);
        valider.accept(n);
        retour();
    }

    private void retour() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        double mx = e.x(), my = e.y();
        for (float[] z : motsZones) {
            if (mx >= z[0] && mx < z[0] + z[2] && my >= z[1] && my < z[1] + z[3]) {
                champ.setValue(String.valueOf((long) z[4]));
                erreur = null;
                RebornSounds.uiClick();
                return true;
            }
        }
        if (DaKit.dans(mx, my, Math.round(bx), Math.round(by), Math.round(bw), 16)) { confirmer(); return true; }
        if (DaKit.dans(mx, my, Math.round(ax - 2), Math.round(by), 50, 16)) { retour(); return true; }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        int k = e.key();
        if (k == GLFW.GLFW_KEY_ESCAPE) { retour(); return true; }
        if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { confirmer(); return true; }
        erreur = null;
        if (champ.keyPressed(e)) return true;
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        erreur = null;
        if (champ.charTyped(event)) return true;
        return super.charTyped(event);
    }

    /** Banc d'essai : valeur affichée. */
    public CombienScreen avecValeur(long n) {
        this.valeurDebut = n;
        return this;
    }

    private long valeurDebut = -1;

}
