package fr.reborn.hud.monnaie;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.menu.inventory.InventoryData;
import fr.reborn.hud.menu.inventory.SlotItem;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Le guichet de la banque (maquettes 03 et 04, MONNAIE_ET_BANQUE.md §4-§6) : écran <b>centré, épuré, aucun kanji</b>,
 * le jeu visible derrière un voile. Signature : la <b>balance du changeur</b> au-dessus du titre. Compte (or) et bourse
 * côte à côte, cinq onglets en mots — Déposer · Retirer · Virement · Échange garanti · Coffre — et une seule plaque
 * pour valider (Entrée). Tout est autoritaire serveur ({@code reborn:banque}) ; l'écran ne fait qu'afficher et demander.
 */
public final class GuichetScreen extends Screen {

    static final Identifier PANNEAU = CombienScreen.PANNEAU, PIECE = CombienScreen.PIECE,
        TENBIN = DaKit.tex("monnaie", "tenbin");
    static final int CREME = 0xFFEEE4D2, CREME_DOUX = 0xFFC2B59A, OR = 0xFFD9A95E, OR_CLAIR = 0xFFF2D08A, OR_SOMBRE = 0xFF6A4E24,
        AMBRE = 0xFFE2A04A, ROUGE = 0xFFE46676, VERT = 0xFF6FBF8E, GRIS = 0xFF8C8478, FILET = 0xFF4A3420;
    static final String[] ONGLETS = {"Déposer", "Retirer", "Virement", "Échange garanti", "Coffre"};
    private static final int W = 310;

    private final Pinceau pz = new Pinceau();
    private int onglet = GuichetData.onglet;
    private int x0, y0, h;
    private EditBox montant, cible, motif;
    private String cibleRef = "";
    private int contactIdx = -1;
    private final List<Zone> zones = new ArrayList<>();
    private final long ouvert = System.currentTimeMillis();
    private String erreur;
    private long erreurLe;

    private record Zone(float x, float y, float w, float h, Runnable act) {
        boolean dans(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    public GuichetScreen() {
        super(Component.literal("Guichet"));
    }

    /** Nouvel état du serveur (l'écran reste ouvert). */
    public void maj() {
        if (GuichetData.echange != null && onglet != 3 && GuichetData.onglet == 3) onglet = 3;
        layout();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
        montant = boite(montant, 12, true);
        cible = boite(cible, 40, false);
        motif = boite(motif, 80, false);
        cible.setResponder(v -> { if (contactIdx < 0 || !v.equals(nomContact(contactIdx))) { contactIdx = -1; cibleRef = v.trim(); } });
        layout();
        if (debutMontant != null) { montant.setValue(debutMontant); debutMontant = null; }
        if (debutCible != null) { cible.setValue(debutCible); debutCible = null; }
        if (debutMotif != null) { motif.setValue(debutMotif); debutMotif = null; }
    }

    private EditBox boite(EditBox ancien, int max, boolean chiffres) {
        EditBox b = new EditBox(this.font, 0, 0, 100, 10, Component.literal(""));
        b.setBordered(false);
        b.setMaxLength(max);
        b.setTextColor(CREME);
        if (chiffres) b.setResponder(v -> { String d = v.replaceAll("[^0-9]", ""); if (!d.equals(v)) b.setValue(d); });
        if (ancien != null) b.setValue(ancien.getValue());
        addRenderableWidget(b);
        return b;
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(false);
        super.removed();
    }

    @Override
    public void onClose() {
        MonnaieClient.envoyerBanque("fermer");
        super.onClose();
    }

    /* ================================================================ mise en page */

    private int hauteur() {
        return switch (onglet) {
            case 3 -> GuichetData.echange == null ? 258 : 296;
            case 4 -> {
                int rc = Math.max(1, (GuichetData.coffre.length + 8) / 9);
                int rs = Math.max(1, Math.min(2, (GuichetData.sacoche.size() + 8) / 9));
                yield Math.min(340, 153 + 12 + rc * 29 + 22 + rs * 25 + 52);
            }
            default -> 258;
        };
    }

    private void layout() {
        h = hauteur();
        x0 = (this.width - W) / 2;
        y0 = Math.max(6, (this.height - h) / 2 - 8);
        int cx = x0 + W / 2;
        int y = y0 + 153;
        montant.visible = onglet <= 2;
        cible.visible = onglet == 2;
        motif.visible = onglet == 2;
        if (onglet <= 1) {
            montant.setWidth(100);
            montant.setX(cx - 55);
            montant.setY(y + 16);
            setFocused(montant);
            montant.setFocused(true);
        } else if (onglet == 2) {
            int lx = cx - 115;
            cible.setWidth(165); cible.setX(lx + 50); cible.setY(y + 6);
            montant.setWidth(150); montant.setX(lx + 50); montant.setY(y + 26);
            motif.setWidth(175); motif.setX(lx + 50); motif.setY(y + 46);
        }
    }

    private int hauteurEcran() { return this.height; }

    /* ================================================================ rendu */

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        float a = Math.min(1f, (System.currentTimeMillis() - ouvert) / 180f);
        ctx.fill(0, 0, this.width, hauteurEcran(), DaKit.argb(0x0A0608, 0.58f * a));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        if (hauteur() != h) layout();
        zones.clear();
        Font f = this.font;
        pz.police(f);
        DaKit.coupe(ctx, PANNEAU, x0, y0, W + 6, h + 6, 0, 0, 316, 346, 16, 16, 316, 346);
        DaKit.blit(ctx, TENBIN, x0 + W / 2 - 22, y0 + 11, 0, 0, 44, 28, 44, 28);
        float cx = x0 + W / 2f;
        entete(cx, mx, my);
        List<Runnable> objets = new ArrayList<>();
        int y = y0 + 153;
        switch (onglet) {
            case 0, 1 -> depotRetrait(cx, y, mx, my, onglet == 0);
            case 2 -> virement(cx, y, mx, my);
            case 3 -> echange(ctx, cx, y, mx, my, objets);
            case 4 -> coffre(ctx, cx, y, mx, my, objets);
            default -> { }
        }
        // message du serveur (refus, confirmation) ou erreur locale, au bas du panneau
        String msg = null;
        int col = AMBRE;
        long now = System.currentTimeMillis();
        if (erreur != null && now - erreurLe < 4000) { msg = erreur; col = ROUGE; }
        else if (GuichetData.toast != null && now - GuichetData.toastLe < 4500) msg = GuichetData.toast;
        if (msg != null && onglet != 4) pz.texteC(msg, cx, y0 + h - 9, 12, col, false);
        pz.texteC("Échap  fermer", cx, y0 + h + 15, 13, CREME_DOUX, false);
        pz.vider(ctx);
        for (Runnable r : objets) r.run();
        if (onglet <= 2) DaKit.blit(ctx, PIECE, Math.round(onglet == 2 ? cx - 115 + 50 + 152 : cx + 46), onglet == 2 ? y + 22 : y + 13, 0, 0, 12, 12, 12, 12);
        for (EditBox b : new EditBox[] {montant, cible, motif}) if (b.visible) b.extractRenderState(ctx, mx, my, delta);
        if (tip != null) { ctx.setTooltipForNextFrame(f, tip, java.util.Optional.empty(), mx, my); tip = null; }
    }

    private List<Component> tip;

    private void entete(float cx, int mx, int my) {
        pz.texteC(GuichetData.banque, cx, y0 + 47, 22, CREME, true);
        String sous = "Compte de " + GuichetData.titulaire + (GuichetData.numero.isEmpty() ? "" : " · N° " + GuichetData.numero);
        pz.texteC(sous, cx, y0 + 60, 14, CREME_DOUX, false);
        if (GuichetData.gele != null) {
            pz.texteC("Compte gelé · " + GuichetData.gele, cx, y0 + 71, 13, ROUGE, false);
        } else if (GuichetData.guichetier != null) {
            String t = "Au guichet : " + GuichetData.guichetier;
            float tw = pz.texteC(t, cx + 3, y0 + 71, 13, CREME_DOUX, false);
            pz.pastille(cx + 3 - tw / 2f - 5, y0 + 71, 1.75f, VERT);
        } else {
            pz.texteC(GuichetData.allie ? "Guichet allié · frais " + GuichetData.fraisAllie + " %" : "Guichet en libre-service",
                cx, y0 + 71, 13, GuichetData.allie ? AMBRE : CREME_DOUX, false);
        }
        pz.px(cx - 45, y0 + 79, cx + 45, y0 + 79.5f, OR_SOMBRE);
        for (int i = 0; i < 2; i++) {
            float x = cx + (i == 0 ? -75 : 75);
            pz.arcadeC(i == 0 ? "COMPTE" : "BOURSE", x, y0 + 85, CREME_DOUX, 0.5f);
            String v = BourseClient.fmt(i == 0 ? GuichetData.compte : GuichetData.bourse) + " ryo";
            pz.texteC(v, x, y0 + 103, 26, i == 0 ? OR_CLAIR : CREME, true);
            String s = i == 0 ? (GuichetData.plafond > 0 ? "plafond " + BourseClient.fmt(GuichetData.plafond) : "sans plafond") : "sur toi";
            pz.texteC(s, x, y0 + 116, 12, CREME_DOUX, false);
        }
        pz.px(cx, y0 + 88, cx + 0.5f, y0 + 118, FILET);
        // onglets en mots
        float gap = 10, tot = -gap;
        for (int i = 0; i < ONGLETS.length; i++) tot += pz.largeur(ONGLETS[i], 15, i == onglet) + gap;
        float x = cx - tot / 2f, ym = y0 + 136;
        for (int i = 0; i < ONGLETS.length; i++) {
            boolean act = i == onglet;
            float lw = pz.largeur(ONGLETS[i], 15, act);
            boolean hov = !act && DaKit.dans(mx, my, Math.round(x - 2), Math.round(ym - 7), Math.round(lw + 4), 14);
            pz.texte(ONGLETS[i], x, ym, 15, act || hov ? CREME : CREME_DOUX, act);
            if (act) pz.px(x, ym + 6.5f, x + lw, ym + 7f, OR);
            final int k = i;
            zones.add(new Zone(x - 2, ym - 7, lw + 4, 14, () -> changerOnglet(k)));
            x += lw + gap;
        }
        pz.px(x0 + 17, y0 + 146, x0 + W - 11, y0 + 146.5f, 0xFF3A2A1E);
    }

    private void changerOnglet(int k) {
        if (k == onglet) return;
        onglet = k;
        erreur = null;
        montant.setValue("");
        MonnaieClient.envoyerBanque("onglet:" + k);
        RebornSounds.uiClick();
        layout();
    }

    /* ---------------------------------------------------------------- champs, plaques, mots */

    private void champ(float x, float y, float w, boolean focus) {
        pz.fond(x, y, x + w, y + 15, 0xEB080507);
        pz.px(x, y + 14.5f, x + w, y + 15, focus ? OR : OR_SOMBRE);
    }

    /** Plaque laquée (une seule par écran) + « Annuler » à droite. */
    private void plaque(String lab, float centre, float y, boolean actif, int mx, int my, Runnable act, Runnable annuler) {
        float bw = pz.largeur(lab, 15, true) + 22;
        float bx = centre - bw / 2f;
        boolean hov = actif && DaKit.dans(mx, my, Math.round(bx), Math.round(y), Math.round(bw), 16);
        pz.fond(bx, y, bx + bw, y + 16, !actif ? 0xFF28201F : hov ? 0xFF5A1622 : 0xFF3C0E16);
        pz.cadre(bx, y, bw, 16, actif ? OR : 0xFF6A5C5C);
        pz.px(bx + 0.5f, y + 0.5f, bx + bw - 0.5f, y + 1, actif ? 0xFF962834 : 0xFF46403F);
        pz.texteC(lab, centre, y + 8, 15, actif ? CREME : GRIS, true);
        if (actif && act != null) zones.add(new Zone(bx, y, bw, 16, act));
        if (annuler != null) {
            float ax = bx + bw + 9, aw = pz.largeur("Annuler", 15, false);
            boolean ha = DaKit.dans(mx, my, Math.round(ax - 2), Math.round(y), Math.round(aw + 4), 16);
            pz.texte("Annuler", ax, y + 8, 15, ha ? CREME : CREME_DOUX, false);
            zones.add(new Zone(ax - 2, y, aw + 4, 16, annuler));
        }
    }

    /** Mots cliquables centrés ; {@code actif} = indice souligné. Renvoie la largeur. */
    private float mots(String[] l, float cx, float ym, int taille, int actif, int mx, int my, java.util.function.IntConsumer clic) {
        float gap = 9, tot = -gap;
        for (int i = 0; i < l.length; i++) tot += pz.largeur(l[i], taille, i == actif) + gap;
        float x = cx - tot / 2f;
        for (int i = 0; i < l.length; i++) {
            boolean act = i == actif;
            float lw = pz.largeur(l[i], taille, act);
            boolean hov = DaKit.dans(mx, my, Math.round(x - 2), Math.round(ym - 6), Math.round(lw + 4), 12);
            pz.texte(l[i], x, ym, taille, act || hov ? CREME : CREME_DOUX, act);
            if (act) pz.px(x, ym + 6, x + lw, ym + 6.5f, OR);
            final int k = i;
            zones.add(new Zone(x - 2, ym - 6, lw + 4, 12, () -> clic.accept(k)));
            x += lw + gap;
        }
        return tot;
    }

    private long valeur(EditBox b) {
        try { return b.getValue().isEmpty() ? 0 : Long.parseLong(b.getValue()); } catch (NumberFormatException e) { return 0; }
    }

    /* ---------------------------------------------------------------- déposer, retirer */

    private void depotRetrait(float cx, int y, int mx, int my, boolean depot) {
        pz.texteC(depot ? "Combien veux-tu déposer ?" : "Combien veux-tu retirer ?", cx, y + 4, 14, CREME_DOUX, false);
        champ(cx - 60, y + 12, 120, montant.isFocused());
        long n = valeur(montant);
        long tout = depot ? GuichetData.bourse : GuichetData.compte;
        long[] vals = {100, 500, 1000, tout};
        int act = -1;
        for (int i = 0; i < vals.length; i++) if (n > 0 && n == vals[i]) act = i;
        mots(new String[] {"100", "500", "1 000", "tout"}, cx, y + 41, 15, act, mx, my, i -> { montant.setValue(String.valueOf(vals[i])); RebornSounds.uiClick(); });
        long frais = GuichetData.allie && n > 0 ? Math.max(1, (long) Math.ceil(n * GuichetData.fraisAllie / 100.0)) : 0;
        if (n > 0) {
            long c = depot ? GuichetData.compte + n - frais : GuichetData.compte - n - frais;
            long b = depot ? GuichetData.bourse - n : GuichetData.bourse + n;
            pz.texteC("Après : compte " + BourseClient.fmt(Math.max(0, c)) + " · bourse " + BourseClient.fmt(Math.max(0, b))
                + (frais > 0 ? " · frais " + BourseClient.fmt(frais) : ""), cx, y + 53, 13, CREME_DOUX, false);
        }
        plaque(depot ? "Déposer · Entrée" : "Retirer · Entrée", cx - 18, y + 64, true, mx, my, this::valider, this::annuler);
        String pied;
        GuichetData.Palier pa = palier(GuichetData.palier);
        String pal = "palier " + GuichetData.palier + (pa != null ? ", " + pa.nom() : "");
        if (GuichetData.soldeMontant > 0) pied = "Solde de la semaine reçue dimanche : +" + BourseClient.fmt(GuichetData.soldeMontant) + " · " + pal;
        else pied = "Le plafond du palier ne limite que les dépôts · " + pal;
        if (!(erreur != null || GuichetData.toast != null && System.currentTimeMillis() - GuichetData.toastLe < 4500))
            pz.texteC(pied, cx, y + 89, 12, CREME_DOUX, false);
    }

    private GuichetData.Palier palier(int n) {
        for (GuichetData.Palier p : GuichetData.paliers) if (p.n() == n) return p;
        return null;
    }

    /* ---------------------------------------------------------------- virement */

    private String nomContact(int i) {
        return i >= 0 && i < GuichetData.contacts.size() ? GuichetData.contacts.get(i).nom() : "";
    }

    private void virement(float cx, int y, int mx, int my) {
        float lx = cx - 115;
        String[] labs = {"À", "Montant", "Motif"};
        EditBox[] bs = {cible, montant, motif};
        for (int i = 0; i < 3; i++) {
            float yy = y + 2 + i * 20;
            pz.texte(labs[i], lx, yy + 7.5f, 14, CREME_DOUX, false);
            champ(lx + 45, yy, 185, bs[i].isFocused());
        }
        if (cible.getValue().isEmpty() && !cible.isFocused())
            pz.texte(GuichetData.contacts.isEmpty() ? "N° de compte (#00017)" : "Un contact (›) ou un N°", lx + 50, y + 9.5f, 13, GRIS, false);
        if (motif.getValue().isEmpty() && !motif.isFocused()) pz.texte("motif obligatoire", lx + 50, y + 49.5f, 13, GRIS, false);
        if (!GuichetData.contacts.isEmpty()) {
            boolean hov = DaKit.dans(mx, my, Math.round(lx + 220), y + 1, 12, 14);
            pz.texte("›", lx + 222, y + 9, 18, hov ? OR_CLAIR : OR, false);
            zones.add(new Zone(lx + 218, y, 14, 15, this::contactSuivant));
        }
        long n = valeur(montant);
        long frais = n <= 0 ? 0 : Math.max(GuichetData.virMin, (long) Math.ceil(n * (GuichetData.allie ? GuichetData.virAutre : GuichetData.virPourcent) / 100.0));
        String l = n > 0 ? "Frais " + (GuichetData.allie ? GuichetData.virAutre : GuichetData.virPourcent) + " % : " + BourseClient.fmt(frais)
            + " ryo · reçu tout de suite sur son compte" : "Frais " + GuichetData.virPourcent + " % (min. " + GuichetData.virMin + "), "
            + GuichetData.virAutre + " % vers un autre village";
        pz.texteC(l, cx, y + 68, 13, CREME_DOUX, false);
        plaque("Virer · Entrée", cx - 15, y + 76, true, mx, my, this::valider, this::annuler);
        if (!(erreur != null || GuichetData.toast != null && System.currentTimeMillis() - GuichetData.toastLe < 4500))
            pz.texteC("Aujourd'hui : " + BourseClient.fmt(GuichetData.virDeja) + " / " + BourseClient.fmt(GuichetData.virPlafond) + " ryo",
                cx, y + 99, 12, CREME_DOUX, false);
    }

    private void contactSuivant() {
        if (GuichetData.contacts.isEmpty()) return;
        contactIdx = (contactIdx + 1) % GuichetData.contacts.size();
        GuichetData.Contact c = GuichetData.contacts.get(contactIdx);
        cibleRef = c.ref();
        cible.setValue(c.nom());
        RebornSounds.uiClick();
    }

    /* ---------------------------------------------------------------- échange garanti */

    private void echange(GuiGraphicsExtractor ctx, float cx, int y, int mx, int my, List<Runnable> objets) {
        JsonObject e = GuichetData.echange;
        if (e == null) {
            pz.texteC("Échange garanti avec…", cx, y + 4, 14, CREME_DOUX, false);
            if (GuichetData.proches.isEmpty()) {
                pz.texteC("Personne d'autre au guichet.", cx, y + 22, 14, GRIS, false);
            }
            int i = 0;
            for (GuichetData.Proche p : GuichetData.proches) {
                if (i >= 4) break;
                float yy = y + 20 + i * 14;
                float lw = pz.largeur(p.nom(), 15, true);
                boolean hov = DaKit.dans(mx, my, Math.round(cx - lw / 2 - 4), Math.round(yy - 7), Math.round(lw + 70), 14);
                pz.texte(p.nom(), cx - lw / 2 - 30, yy, 15, hov ? CREME : CREME_DOUX, true);
                pz.texte("Proposer ›", cx - lw / 2 - 30 + lw + 8, yy, 13, hov ? OR_CLAIR : OR, false);
                zones.add(new Zone(cx - lw / 2 - 32, yy - 7, lw + 80, 14, () -> MonnaieClient.envoyerBanque("echange:ouvrir:" + p.uuid())));
                i++;
            }
            pz.texteC("Les deux au guichet · chacun dépose, on ne voit que ce que l'autre pose", cx, y + 80, 12, CREME_DOUX, false);
            pz.texteC("le guichetier scelle (sinon le guichet) · frais " + 2 + " % des ryo échangés (min. 10)", cx, y + 92, 12, CREME_DOUX, false);
            return;
        }
        JsonObject moi = e.getAsJsonObject("moi"), lui = e.getAsJsonObject("lui");
        String[] noms = {"Toi", MonnaieClient.str(e, "autre")};
        JsonObject[] cotes = {moi, lui};
        for (int j = 0; j < 2; j++) {
            float x = cx + (j == 0 ? -75 : 75);
            pz.texteC(noms[j], x, y + 6, 16, CREME, true);
            boolean conf = cotes[j].has("confirme") && cotes[j].get("confirme").getAsBoolean();
            String et = conf ? "confirmé" : "dépose…";
            int c = conf ? VERT : AMBRE;
            float tw = pz.texteC(et, x + 3, y + 18, 13, c, false);
            pz.pastille(x + 3 - tw / 2f - 5, y + 18, 1.75f, c);
            // dépôts : ryo puis objets, cases de 22
            List<Object[]> cells = new ArrayList<>();
            long ryo = MonnaieClient.num(cotes[j], "ryo");
            if (ryo > 0) {
                SlotItem b = new SlotItem("minecraft:paper", "reborn:" + (ryo >= 5000 ? "bourse_lourde" : ryo >= 100 ? "bourse_garnie" : "bourse_plate"), 1, "Bourse", 0);
                b.ryo = ryo;
                cells.add(new Object[] {b, BourseClient.fmt(ryo) + " ryo", -1});
            }
            JsonArray ob = cotes[j].has("objets") ? cotes[j].getAsJsonArray("objets") : new JsonArray();
            for (int k = 0; k < ob.size(); k++) {
                JsonElement el = ob.get(k);
                if (!el.isJsonObject()) continue;
                SlotItem it = InventoryData.parseItem(el.getAsJsonObject());
                if (it != null) cells.add(new Object[] {it, it.name, k});
            }
            float gx = x - 62;
            if (cells.isEmpty()) pz.texteC("rien pour l'instant", x, y + 38, 13, GRIS, false);
            for (int k = 0; k < cells.size() && k < 5; k++) {
                Object[] c0 = cells.get(k);
                SlotItem it = (SlotItem) c0[0];
                float cxx = cells.size() == 1 ? x - 34 : gx + k * 25;
                cellule(cxx, y + 28, false, false);
                final float fx = cxx;
                objets.add(() -> item(ctx, it.toStack(), fx, y + 28));
                if (cells.size() == 1) {
                    pz.texte((String) c0[1], cxx + 27, y + (it.ryo >= 0 ? 39 : 35), 15, it.ryo >= 0 ? OR_CLAIR : CREME, false);
                    if (it.rarity != null && it.ryo < 0) pz.texte(it.rarity, cxx + 27, y + 45, 12, VERT, false);
                }
                int idx = (int) c0[2];
                if (j == 0 && idx >= 0) zones.add(new Zone(cxx, y + 28, 22, 22, () -> MonnaieClient.envoyerBanque("echange:retirer:" + idx)));
            }
        }
        pz.px(cx, y + 2, cx + 0.5f, y + 56, FILET);
        // déposer : ryo (Combien ?) et objets de la sacoche (une rangée)
        boolean jaiConfirme = moi.has("confirme") && moi.get("confirme").getAsBoolean();
        float ry = y + 64;
        boolean hR = DaKit.dans(mx, my, Math.round(cx - 140), Math.round(ry - 6), 90, 12);
        pz.texte("Déposer des ryo…", cx - 140, ry, 13, hR ? OR_CLAIR : OR, false);
        zones.add(new Zone(cx - 142, ry - 6, 92, 12, () -> Minecraft.getInstance().setScreenAndShow(new CombienScreen(this, "echange",
            "Déposer des ryo", "Combien mets-tu dans l'échange ?", GuichetData.bourse, "Déposer", "depuis ta bourse",
            n -> MonnaieClient.envoyerBanque("echange:ryo:" + n + ":bourse")))));
        String rec = MonnaieClient.str(e, "recevoir");
        pz.texte("Recevoir les ryo :", cx + 18, ry, 13, CREME_DOUX, false);
        float rx = cx + 18 + pz.largeur("Recevoir les ryo :", 13, false) + 5;
        for (String o : new String[] {"bourse", "compte"}) {
            boolean act = o.equals(rec);
            float lw = pz.largeur(o, 13, act);
            pz.texte(o, rx, ry, 13, act ? CREME : CREME_DOUX, act);
            if (act) pz.px(rx, ry + 5.5f, rx + lw, ry + 6, OR);
            zones.add(new Zone(rx - 2, ry - 6, lw + 4, 12, () -> MonnaieClient.envoyerBanque("echange:recevoir:" + o)));
            rx += lw + 7;
        }
        // une rangée de la sacoche : clic = déposer l'objet
        float sy = y + 74;
        int n = 0;
        for (GuichetData.Case c : GuichetData.sacoche) {
            if (c.item().ryo >= 0) continue;
            if (n >= 11) break;
            float sx = cx - 11 * 12.5f + n * 25 + 1;
            boolean hov = DaKit.dans(mx, my, Math.round(sx), Math.round(sy), 22, 22);
            cellule(sx, sy, hov, false);
            final float fx = sx;
            objets.add(() -> item(ctx, c.item().toStack(), fx, sy));
            if (hov) tip = List.of(Component.literal(c.item().name == null ? "Objet" : c.item().name), Component.literal("Clic : mettre dans l'échange"));
            if (!jaiConfirme) zones.add(new Zone(sx, sy, 22, 22, () -> MonnaieClient.envoyerBanque("echange:objet:" + c.ref())));
            n++;
        }
        long frais = MonnaieClient.num(e, "frais");
        String gu = e.has("guichetier") ? MonnaieClient.str(e, "guichetier") : null;
        boolean sceau = e.has("attenteSceau") && e.get("attenteSceau").getAsBoolean();
        String pied = sceau ? (gu != null ? gu + " va sceller l'échange" : "Le guichet scelle l'échange")
            : (gu != null ? gu + " scelle" : "Le guichet scelle") + " quand vous avez confirmé tous les deux · frais "
            + MonnaieClient.num(e, "pourcent") + " % : " + BourseClient.fmt(frais) + " ryo";
        pz.texteC(pied, cx, y + 106, 12, CREME_DOUX, false);
        plaque(jaiConfirme ? "Confirmé" : "Confirmer · Entrée", cx - 18, y + 115, !jaiConfirme, mx, my, this::valider,
            () -> MonnaieClient.envoyerBanque("echange:annuler"));
    }

    /* ---------------------------------------------------------------- coffre et paliers */

    private void cellule(float x, float y, boolean survol, boolean refuse) {
        pz.fond(x, y, x + 22, y + 22, 0xEB080406);
        pz.cadre(x, y, 22, 22, refuse ? ROUGE : survol ? OR_CLAIR : 0xFF8E6A30);
    }

    private void item(GuiGraphicsExtractor ctx, ItemStack s, float x, float y) {
        if (s == null || s.isEmpty()) return;
        ctx.item(s, Math.round(x) + 3, Math.round(y) + 3);
        ctx.itemDecorations(this.font, s, Math.round(x) + 3, Math.round(y) + 3);
    }

    private void coffre(GuiGraphicsExtractor ctx, float cx, int y, int mx, int my, List<Runnable> objets) {
        SlotItem[] co = GuichetData.coffre;
        int occ = 0;
        for (SlotItem s : co) if (s != null) occ++;
        pz.texteC("Coffre du compte · " + occ + " / " + co.length + " cases", cx, y + 5, 14, CREME_DOUX, false);
        int rc = Math.max(1, (co.length + 8) / 9);
        float cy0 = y + 12;
        for (int r = 0; r < rc; r++) {
            int n = Math.min(9, co.length - r * 9);
            float gx = cx - (n * 29 - 7) / 2f;
            for (int k = 0; k < n; k++) {
                int i = r * 9 + k;
                float x = gx + k * 29, yy = cy0 + r * 29;
                boolean hov = DaKit.dans(mx, my, Math.round(x), Math.round(yy), 22, 22);
                cellule(x, yy, hov && co[i] != null, false);
                if (co[i] != null) {
                    SlotItem it = co[i];
                    objets.add(() -> item(ctx, it.toStack(), x, yy));
                    if (hov) tip = List.of(Component.literal(it.name == null ? "Objet" : it.name), Component.literal("Clic : reprendre dans ta sacoche"));
                    zones.add(new Zone(x, yy, 22, 22, () -> MonnaieClient.envoyerBanque("coffre:retrait:" + i)));
                }
            }
        }
        float sy = cy0 + rc * 29 + 2;
        pz.texteC("Ta sacoche", cx, sy + 4, 14, CREME_DOUX, false);
        List<GuichetData.Case> sac = GuichetData.sacoche;
        int rs = Math.max(1, Math.min(2, (sac.size() + 8) / 9));
        long now = System.currentTimeMillis();
        for (int r = 0; r < rs; r++) {
            int n = Math.min(9, sac.size() - r * 9);
            float gx = cx - (Math.max(n, 1) * 25 - 3) / 2f;
            for (int k = 0; k < n; k++) {
                GuichetData.Case c = sac.get(r * 9 + k);
                float x = gx + k * 25, yy = sy + 11 + r * 25;
                boolean hov = DaKit.dans(mx, my, Math.round(x), Math.round(yy), 22, 22);
                boolean flash = c.ref().equals(GuichetData.refusRef) && now - GuichetData.refusLe < 1600 && ((now / 200) % 2 == 0);
                cellule(x, yy, hov, flash || (hov && c.refus() != null));
                objets.add(() -> item(ctx, c.item().toStack(), x, yy));
                if (hov) {
                    String nom = c.item().ryo >= 0 ? "Bourse · " + BourseClient.fmt(c.item().ryo) + " ryo" : c.item().name == null ? "Objet" : c.item().name;
                    if (c.refus() != null) {
                        String[] parts = c.refus().split(" — ", 2);
                        tip = parts.length == 2 ? List.of(Component.literal(nom), Component.literal(parts[0] + " :").withColor(ROUGE), Component.literal(parts[1]))
                            : List.of(Component.literal(nom), Component.literal(c.refus()).withColor(ROUGE));
                    } else tip = List.of(Component.literal(nom), Component.literal(c.item().ryo >= 0 ? "Clic : déposer sur le compte" : "Clic : ranger au coffre"));
                }
                zones.add(new Zone(x, yy, 22, 22, () -> MonnaieClient.envoyerBanque("coffre:depot:" + c.ref())));
            }
        }
        if (sac.isEmpty()) pz.texteC("vide", cx, sy + 22, 13, GRIS, false);
        // paliers
        float py = sy + 11 + rs * 25 + 6;
        pz.px(x0 + 17, py, x0 + W - 11, py + 0.5f, 0xFF3A2A1E);
        int np = GuichetData.paliers.size();
        float colw = (W - 16) / (float) Math.max(1, np);
        for (int i = 0; i < np; i++) {
            GuichetData.Palier p = GuichetData.paliers.get(i);
            float pcx = x0 + 8 + colw * i + colw / 2f;
            boolean act = p.n() == GuichetData.palier, suivant = p.n() == GuichetData.palier + 1, futur = p.n() > GuichetData.palier + 1;
            int col = act ? CREME : futur ? GRIS : CREME_DOUX;
            float lw = pz.texteC(p.n() + " · " + p.nom(), pcx, py + 10, 15, col, act);
            if (act) pz.px(pcx - lw / 2, py + 16.5f, pcx + lw / 2, py + 17, OR);
            pz.texteC(p.cases() + " cases · " + (p.plafond() > 0 ? BourseClient.fmt(p.plafond()) : "sans plafond"), pcx, py + 21, 12, futur ? GRIS : CREME_DOUX, false);
            if (act) pz.texteC("actuel", pcx, py + 32, 12, AMBRE, false);
            else if (suivant) {
                String lab = "Passer · " + BourseClient.fmt(p.cout()) + " ryo";
                float bw = pz.largeur(lab, 13, true) + 16;
                boolean ok = GuichetData.compte >= p.cout();
                boolean hov = ok && DaKit.dans(mx, my, Math.round(pcx - bw / 2), Math.round(py + 26), Math.round(bw), 14);
                pz.fond(pcx - bw / 2, py + 26, pcx + bw / 2, py + 40, !ok ? 0xFF28201F : hov ? 0xFF5A1622 : 0xFF3C0E16);
                pz.cadre(pcx - bw / 2, py + 26, bw, 14, ok ? OR : 0xFF6A5C5C);
                pz.texteC(lab, pcx, py + 33, 13, ok ? CREME : GRIS, true);
                final int k = p.n();
                if (ok) zones.add(new Zone(pcx - bw / 2, py + 26, bw, 14, () -> MonnaieClient.envoyerBanque("palier:" + k)));
            } else if (p.n() < GuichetData.palier) pz.texteC("pris", pcx, py + 32, 12, GRIS, false);
            else pz.texteC(BourseClient.fmt(p.cout()) + " ryo", pcx, py + 32, 12, GRIS, false);
            if (i > 0) pz.px(x0 + 8 + colw * i, py + 4, x0 + 8 + colw * i + 0.5f, py + 38, FILET);
        }
        long now2 = System.currentTimeMillis();
        String msg = erreur != null && now2 - erreurLe < 4000 ? erreur
            : GuichetData.toast != null && now2 - GuichetData.toastLe < 4500 ? GuichetData.toast : null;
        if (msg != null) pz.texteC(msg, cx, y0 + h - 9, 12, erreur != null ? ROUGE : AMBRE, false);
    }

    /* ================================================================ actions */

    private void valider() {
        switch (onglet) {
            case 0, 1 -> {
                long n = valeur(montant);
                if (n <= 0) { erreur("Indique un montant."); return; }
                MonnaieClient.envoyerBanque((onglet == 0 ? "deposer:" : "retirer:") + n);
                montant.setValue("");
            }
            case 2 -> {
                long n = valeur(montant);
                String ref = contactIdx >= 0 ? cibleRef : cible.getValue().trim();
                if (ref.isEmpty()) { erreur("À qui ? un contact (›) ou un N°."); return; }
                if (n <= 0) { erreur("Indique un montant."); return; }
                if (motif.getValue().isBlank()) { erreur("Le motif est obligatoire."); return; }
                MonnaieClient.envoyerBanque("virer:" + ref + "\n" + n + "\n" + motif.getValue().trim());
                montant.setValue("");
                motif.setValue("");
            }
            case 3 -> MonnaieClient.envoyerBanque("echange:confirmer");
            default -> { }
        }
        RebornSounds.playReborn("sacoche.place", 1f, 0.6f);
    }

    private void annuler() {
        if (onglet == 3 && GuichetData.echange != null) { MonnaieClient.envoyerBanque("echange:annuler"); return; }
        if (!montant.getValue().isEmpty() || !motif.getValue().isEmpty()) { montant.setValue(""); motif.setValue(""); return; }
        onClose();
    }

    private void erreur(String e) {
        erreur = e;
        erreurLe = System.currentTimeMillis();
        RebornSounds.playReborn("stats.deny", 1f, 0.6f);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        double mx = e.x(), my = e.y();
        for (EditBox b : new EditBox[] {montant, cible, motif}) {
            if (b.visible && mx >= b.getX() - 5 && mx < b.getX() + b.getWidth() && my >= b.getY() - 4 && my < b.getY() + 12) {
                for (EditBox o : new EditBox[] {montant, cible, motif}) o.setFocused(o == b);
                setFocused(b);
                return true;
            }
        }
        for (Zone z : new ArrayList<>(zones)) {
            if (z.dans(mx, my)) { z.act().run(); return true; }
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        int k = e.key();
        if (k == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { valider(); return true; }
        if (k == GLFW.GLFW_KEY_TAB && onglet == 2) {
            EditBox[] o = {cible, montant, motif};
            int i = 0;
            for (int j = 0; j < 3; j++) if (o[j].isFocused()) i = j;
            int n = (i + 1) % 3;
            for (int j = 0; j < 3; j++) o[j].setFocused(j == n);
            setFocused(o[n]);
            return true;
        }
        for (EditBox b : new EditBox[] {montant, cible, motif}) if (b.visible && b.isFocused() && b.keyPressed(e)) return true;
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        for (EditBox b : new EditBox[] {montant, cible, motif}) if (b.visible && b.isFocused() && b.charTyped(event)) return true;
        return super.charTyped(event);
    }

    /* ================================================================ banc d'essai */

    public static String debutMontant, debutCible, debutMotif;

    /** Banc d'essai : onglet imposé et focus. */
    public GuichetScreen onglet(int k) {
        this.onglet = k;
        return this;
    }
}
