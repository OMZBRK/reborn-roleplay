package fr.reborn.hud.staff;

import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fenêtre de formulaire du Poste de garde, posée sur la vue : action qui demande un paramètre (motif du gel, texte de
 * l'écran noir, message d'une annonce, force du séisme…) ou <b>confirmation d'une action destructive</b> (le bouton
 * reste éteint tant que le N° du personnage n'a pas été retapé). Papier nu dans le cadre de bois, titre à l'encre
 * rouge, libellés alignés dans une colonne, choix en puces, Annuler à gauche, l'action à droite.
 */
final class GardeFormulaire implements StaffScreen.Fenetre {

    /** Un champ : texte libre ({@code choix == null}) ou choix en puces ({id, libellé}…, le premier par défaut). */
    record Champ(String cle, String label, String hint, String[][] choix, boolean requis) {
        static Champ texte(String cle, String label, String hint, boolean requis) { return new Champ(cle, label, hint, null, requis); }
        static Champ choix(String cle, String label, String[][] choix) { return new Champ(cle, label, null, choix, false); }
    }

    private final StaffScreen s;
    private final EditBox[] boites = new EditBox[3];
    private EditBox exigerBox;
    /** Vrai pendant le dessin de la fenêtre (les info-bulles y sont permises). */
    boolean dessine;

    private String titre = "", texte = "", bouton = "Valider", exiger;
    private boolean danger;
    private List<Champ> liste = List.of();
    private final Map<String, String> valeurs = new HashMap<>();
    private Consumer<Map<String, String>> action;

    GardeFormulaire(StaffScreen s) { this.s = s; }

    void init() {
        for (int i = 0; i < boites.length; i++) boites[i] = s.box(boites[i], "", 200);
        exigerBox = s.box(exigerBox, "N° du personnage", 12);
    }

    boolean possede(EditBox b) {
        if (b == exigerBox) return true;
        for (EditBox x : boites) if (x == b) return true;
        return false;
    }

    /**
     * Ouvre la fenêtre. {@code exiger} : texte à retaper pour confirmer (un N°, comparé sans # ni zéros de tête) ou
     * null ; {@code danger} : bouton rouge et bandeau d'avertissement.
     */
    void ouvrir(String titre, String texte, String bouton, boolean danger, String exiger, List<Champ> champs,
                Consumer<Map<String, String>> action) {
        this.titre = titre;
        this.texte = texte == null ? "" : texte;
        this.bouton = bouton;
        this.danger = danger;
        this.exiger = exiger;
        this.liste = new ArrayList<>(champs);
        this.action = action;
        valeurs.clear();
        int k = 0;
        for (Champ c : liste) {
            if (c.choix() != null) valeurs.put(c.cle(), c.choix()[0][0]);
            else if (k < boites.length) {
                boites[k].setValue("");
                s.indice(boites[k], c.hint());
                k++;
            }
        }
        exigerBox.setValue("");
        s.fenetre = this;
        RebornSounds.uiClick();
    }

    /** Valeur d'un champ texte (pour les bancs). */
    void debugValeur(int i, String v) { if (i < boites.length) boites[i].setValue(v); }

    private boolean valide() {
        int k = 0;
        for (Champ c : liste) {
            if (c.choix() != null) continue;
            if (k < boites.length && c.requis() && boites[k].getValue().isBlank()) return false;
            k++;
        }
        if (exiger != null) {
            String a = StaffClient.normPid(exigerBox.getValue()), b = StaffClient.normPid(exiger);
            return !a.isEmpty() && a.equals(b);
        }
        return true;
    }

    /** Entrée : valide si possible. */
    boolean entree() {
        if (!valide()) return false;
        valider();
        return true;
    }

    private void valider() {
        Map<String, String> v = new HashMap<>(valeurs);
        int k = 0;
        for (Champ c : liste) {
            if (c.choix() == null && k < boites.length) v.put(c.cle(), boites[k++].getValue().trim());
        }
        s.fenetre = null;
        if (action != null) action.accept(v);
        RebornSounds.confirm();
    }

    @Override
    public void dessiner(GuiGraphicsExtractor ctx) {
        dessine = true;
        var font = s.police();
        int mw = Math.min(360, s.width - 40), x = s.width / 2 - mw / 2;
        int cw = mw - 24, labW = 64, ctrlW = cw - labW;
        DaKit.Paragraphe p = DaKit.paragraphe(font, texte, cw, DaKit.S8, 10, 6);
        // hauteur
        int h = 12 + 16 + (p.lignes().isEmpty() ? 0 : p.hauteur() + 8) + (danger ? 0 : 0);
        List<int[][]> flux = new ArrayList<>();
        for (Champ c : liste) {
            if (c.choix() == null) { h += 20; flux.add(null); continue; }
            int[] lw = new int[c.choix().length];
            for (int i = 0; i < lw.length; i++) lw[i] = s.bw(c.choix()[i][1]);
            int[][] f = StaffScreen.flux(lw, ctrlW, 4);
            flux.add(f);
            h += (f[f.length - 1][1] + 1) * 16 + 4;
        }
        if (exiger != null) h += 34;
        h += 10 + StaffScreen.H_BTN_G + 12;
        int y = Math.max(10, s.height / 2 - h / 2);
        GardeArt.papierNu(ctx, x, y, mw, h);
        GardeArt.cadre(ctx, x, y, mw, h);
        int cx = x + 12, cy = y + 12;
        if (danger) ctx.fill(x + 2, y + 2, x + mw - 2, y + 4, StaffScreen.LACQUER);
        GardeArt.losange(ctx, cx, cy + 1);
        DaKit.arcX1(ctx, font, DaKit.arcade(s.fitArc(titre, cw - 12)), cx + 9, cy, DaKit.ENCRE_ROUGE, 0, false);
        cy += 16;
        if (!p.lignes().isEmpty()) {
            p.dessiner(ctx, font, cx, cy, danger ? StaffScreen.LACQUER_LO : StaffScreen.INK);
            cy += p.hauteur() + 8;
        }
        int k = 0;
        for (int i = 0; i < liste.size(); i++) {
            Champ c = liste.get(i);
            DaKit.texteEchelle(ctx, font, RebornFont.body(s.fit(c.label(), Math.round((labW - 6) / DaKit.S8))), cx,
                    cy + 4, StaffScreen.INK_SOFT, DaKit.S8);
            if (c.choix() == null) {
                if (k < boites.length) s.placer(ctx, boites[k++], cx + labW, cy, ctrlW);
                cy += 20;
            } else {
                int[][] f = flux.get(i);
                for (int j = 0; j < c.choix().length; j++) {
                    String id = c.choix()[j][0];
                    boolean sel = id.equals(valeurs.get(c.cle()));
                    s.button(ctx, cx + labW + f[j][0], cy + 1 + f[j][1] * 16, c.choix()[j][1], sel ? "punish" : "plain",
                            () -> { valeurs.put(c.cle(), id); RebornSounds.uiClick(); }, false);
                }
                cy += (f[f.length - 1][1] + 1) * 16 + 4;
            }
        }
        if (exiger != null) {
            cy += 2;
            s.txt8(ctx, "Pour confirmer, tape le N° « " + exiger + " » :", cx, cy, cw, StaffScreen.LACQUER_LO);
            cy += 11;
            s.placer(ctx, exigerBox, cx, cy, Math.min(140, cw));
            cy += 21;
        }
        cy += 6;
        s.bouton(ctx, cx, cy, "Annuler", "plain", () -> s.fenetre = null, false);
        int bw = s.bwG(bouton);
        boolean ok = valide();
        s.refusVerrou = exiger != null ? "Retape le N° du personnage pour confirmer." : "Remplis les champs demandés.";
        s.bouton(ctx, cx + cw - bw, cy, bw, bouton, danger ? "punish" : "watch", this::valider, !ok);
        s.refusVerrou = null;
        if (!ok) s.tip(cx + cw - bw, cy, bw, StaffScreen.H_BTN_G,
                exiger != null ? "Retape le N° du personnage pour déverrouiller." : "Remplis les champs demandés.");
        dessine = false;
    }
}
