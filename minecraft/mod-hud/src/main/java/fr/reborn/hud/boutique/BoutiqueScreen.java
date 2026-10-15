package fr.reborn.hud.boutique;

import fr.reborn.hud.cosmetic.CosmeticFeatureRenderer;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.menu.character.perso.PersoVignettes;
import fr.reborn.hud.menu.inventory.CosmeticSlot;
import fr.reborn.hud.monnaie.Pinceau;
import fr.reborn.hud.skin.RebornSkins;
import fr.reborn.hud.skin.SkinSpec;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * L'écran des boutiques-lieux (BOUTIQUES.md, décisions du 15/10 ; maquettes 02, 03, 04) — <b>par-dessus le jeu</b>, le
 * monde reste visible, centré, épuré, <b>aucun kanji</b>.
 *
 * <p><b>Tailleur</b> : la caméra se pose dans le monde en face du personnage ({@link CameraEssayage}) ; à gauche, les
 * <b>catégories du comptoir</b> (configurées par le staff) ; au centre, le personnage (glisser = il pivote pour de vrai,
 * molette = en pied / buste / visage) ; à droite, le catalogue en vignettes — chaque pièce portée sur <b>son propre
 * corps</b> (poupées {@link PersoVignettes}, rendues une fois) — sous la <b>bourse</b>, puis la pièce choisie et
 * « Acheter » / « Acheter et porter » (confirmation). Clic sur une vignette = essai gratuit, pour soi seul ; tout
 * s'efface en sortant. Repli (§3.4) : si un bloc gêne la caméra, le personnage est dessiné en interface sur un voile.
 * Signature : le <b>mètre ruban</b> d'or au-dessus du titre.
 *
 * <p><b>Bazar</b> : une liste centrée (vivres, cartes), la bourse, « Acheter » ; signature : la <b>balance romaine</b>.
 * Tout est autoritaire serveur ({@code reborn:boutique}) ; textures {@code gui/boutique/*}
 * ({@code tools/gen_boutique_textures.py}).
 */
public final class BoutiqueScreen extends Screen {

    static final Identifier PANNEAU = DaKit.tex("boutique", "panneau"), METRE = DaKit.tex("boutique", "metre"),
        BALANCE = DaKit.tex("boutique", "balance"), BOURSE = DaKit.tex("boutique", "bourse"),
        CADENAS = DaKit.tex("boutique", "cadenas"), PIECE = DaKit.tex("boutique", "piece");
    static final int CREME = 0xFFEEE4D2, CREME_DOUX = 0xFFC2B59A, OR = 0xFFD9A95E, OR_CLAIR = 0xFFF2D08A,
        AMBRE = 0xFFE2A04A, ROUGE = 0xFFE46676, VERT = 0xFF6FBF8E, GRIS = 0xFF8C8478, NUIT = 0xFF0A0608,
        CASE = 0xFF1E1116, CASE_SEL = 0xFF2C1820, BORD = 0xFF3A2A2E, LAQUE = 0xFF6E1020, LAQUE_SURVOL = 0xFF8E1628,
        INACTIF = 0xFF2A2226;

    private final Pinceau pz = new Pinceau();
    private final PersoVignettes vignettes = new PersoVignettes("boutique");
    private final long ouvertLe = System.currentTimeMillis();
    private final List<Zone> zones = new ArrayList<>();

    private String categorie;
    private String choisi;
    private int defile;
    /** Essais en cours (pour soi seul) : une tenue et des cosmétiques 3D, par emplacement. */
    private String essaiTenue;
    private final Map<CosmeticSlot, BoutiqueClient.Article> essaiObjets = new LinkedHashMap<>();
    private BoutiqueClient.Article confirmer;
    private boolean voile, bazar, fermeParServeur, cameraPosee;
    private float spin;                     // rotation du personnage dessiné (repli sur voile)
    private boolean glisse;
    private double glisseX;
    private String message;
    private int messageCouleur;
    private long messageLe;
    private int survolCase = -1, survolCasePrec = -1;

    private record Zone(float x, float y, float w, float h, Runnable act, int survol) {
        boolean dans(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    public BoutiqueScreen() {
        super(Component.literal("Boutique"));
    }

    /* ================================================================ cycle de vie */

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
        bazar = "bazar".equals(BoutiqueClient.genre);
        if (!cameraPosee) {
            cameraPosee = true;
            if (!bazar) poserCamera(mc);
            RebornSounds.playReborn("shop.open", 1.0f, 0.5f);
        }
        if (categorie == null) categorie = premiereCategorie();
    }

    private void poserCamera(Minecraft mc) {
        if (mc.player == null) { voile = true; return; }
        BoutiqueClient.Cam c = BoutiqueClient.cam;
        boolean ok;
        if (c != null) {
            ok = CameraEssayage.INSTANCE.begin(mc, new Vec3(c.x(), c.y(), c.z()), c.yaw(), c.dist(), c.haut());
            if (ok) tourner(mc, c.yaw());
        } else {
            // pas d'estrade (test staff, boutique sans estrade) : devant lui, sinon derrière, sinon sur les côtés
            ok = false;
            Vec3 pied = mc.player.position();
            float y0 = mc.player.getYRot();
            for (float d : new float[]{0, 180, 90, -90}) {
                if (CameraEssayage.INSTANCE.begin(mc, pied, y0 + d, 2.7, 1.0)) { ok = true; tourner(mc, y0 + d); break; }
            }
        }
        voile = !ok;
        if (voile) CameraEssayage.INSTANCE.repli();
    }

    /** Nouvel état du serveur (l'écran reste ouvert). */
    void maj(boolean rouvert) {
        bazar = "bazar".equals(BoutiqueClient.genre);
        if (rouvert) categorie = null;
        if (categorie == null || BoutiqueClient.categories.stream().noneMatch(c -> c.id().equals(categorie))) {
            categorie = premiereCategorie();
            defile = 0;
        }
        String t = BoutiqueClient.toast;
        if (t != null && System.currentTimeMillis() - BoutiqueClient.toastLe < 500) {
            boolean ok = t.startsWith("§a"), err = t.startsWith("§c");
            dire(t.replaceAll("§.", ""), ok ? VERT : err ? ROUGE : AMBRE);
            if (ok) RebornSounds.playReborn(t.contains("port") ? "shop.equip" : "shop.buy", 1.0f, 0.7f);
            else if (err) RebornSounds.playReborn("stats.deny", 1.0f, 0.6f);
        }
        // une tenue achetée et portée n'est plus un essai : l'apparence réelle vient du serveur
        if (essaiTenue != null && essaiTenue.equals(porteeId())) essaiTenue = null;
        appliquerEssais();
    }

    void fermerParServeur() {
        fermeParServeur = true;
        onClose();
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(false);
        CameraEssayage.INSTANCE.end(mc);
        CosmeticFeatureRenderer.ESSAI.clear();
        essaiTenue = null;
        appliquerApparence(BoutiqueClient.appearance);
        vignettes.liberer();
        if (!fermeParServeur) BoutiqueClient.envoyer("fermer");
        super.removed();
    }

    @Override
    public void onClose() {
        if (confirmer != null && !fermeParServeur) { confirmer = null; return; }
        Minecraft.getInstance().setScreenAndShow(null);
    }

    /* ================================================================ données */

    private String premiereCategorie() {
        for (BoutiqueClient.Categorie c : BoutiqueClient.categories) if (nb(c.id()) > 0) return c.id();
        return BoutiqueClient.categories.isEmpty() ? "" : BoutiqueClient.categories.get(0).id();
    }

    private int nb(String cat) {
        int n = 0;
        for (BoutiqueClient.Article a : BoutiqueClient.articles) if (a.cat().equals(cat)) n++;
        return n;
    }

    private List<BoutiqueClient.Article> liste() {
        List<BoutiqueClient.Article> l = new ArrayList<>();
        for (BoutiqueClient.Article a : BoutiqueClient.articles) if (a.cat().equals(categorie)) l.add(a);
        return l;
    }

    private BoutiqueClient.Article article(String id) {
        if (id == null) return null;
        for (BoutiqueClient.Article a : BoutiqueClient.articles) if (a.id().equals(id)) return a;
        return null;
    }

    private String nomCategorie(String id) {
        for (BoutiqueClient.Categorie c : BoutiqueClient.categories) if (c.id().equals(id)) return c.nom();
        return id;
    }

    private static SkinSpec apparence() {
        try { return SkinSpec.deserialize(BoutiqueClient.appearance); } catch (RuntimeException e) { return new SkinSpec(); }
    }

    /** La tenue réellement portée (apparence du serveur). */
    private static String porteeId() {
        String o = apparence().outfitId;
        return o == null ? "" : o;
    }

    private boolean essaye(BoutiqueClient.Article a) {
        if (a.tenue()) return a.id().equals(essaiTenue);
        CosmeticSlot s = slot(a);
        return s != null && essaiObjets.get(s) == a;
    }

    private static CosmeticSlot slot(BoutiqueClient.Article a) {
        if (a.slot() == null) return null;
        try { return CosmeticSlot.valueOf(a.slot()); } catch (IllegalArgumentException e) { return null; }
    }

    /* ================================================================ vignettes */

    private final Map<String, Identifier> skins = new java.util.HashMap<>();
    private String skinsPour;
    private boolean slimApparence;

    /** Texture de son corps portant la tenue {@code id} (composée une fois par apparence). */
    private Identifier skinDe(String id) {
        if (!BoutiqueClient.appearance.equals(skinsPour)) {
            skins.clear();
            skinsPour = BoutiqueClient.appearance;
            slimApparence = apparence().slim;
        }
        return skins.computeIfAbsent(id, k -> {
            SkinSpec spec = apparence();
            spec.outfitId = k;
            return vignettes.skin(spec);
        });
    }

    /* ================================================================ essais */

    private void essayer(BoutiqueClient.Article a) {
        if (a.tenue()) {
            essaiTenue = a.id().equals(porteeId()) ? null : a.id();
        } else if (a.objet()) {
            CosmeticSlot s = slot(a);
            if (s == null || a.item() == null) return;
            if (essaiObjets.get(s) == a) essaiObjets.remove(s); else essaiObjets.put(s, a);
        } else return;
        appliquerEssais();
    }

    private void retirerEssai(BoutiqueClient.Article a) {
        if (a == null) return;
        if (a.tenue() && a.id().equals(essaiTenue)) essaiTenue = null;
        CosmeticSlot s = slot(a);
        if (s != null && essaiObjets.get(s) == a) essaiObjets.remove(s);
        appliquerEssais();
    }

    private void toutRetirer() {
        essaiTenue = null;
        essaiObjets.clear();
        appliquerEssais();
        RebornSounds.playReborn("shop.equip", 1.2f, 0.4f);
    }

    private void appliquerEssais() {
        CosmeticFeatureRenderer.ESSAI.clear();
        for (Map.Entry<CosmeticSlot, BoutiqueClient.Article> e : essaiObjets.entrySet()) {
            if (e.getValue().item() != null) CosmeticFeatureRenderer.ESSAI.put(e.getKey(), e.getValue().item());
        }
        if (essaiTenue == null) { appliquerApparence(BoutiqueClient.appearance); return; }
        SkinSpec spec = apparence();
        spec.outfitId = essaiTenue;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) RebornSkins.applySpec(mc.player.getUUID(), spec);
    }

    private static void appliquerApparence(String blob) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || blob == null || blob.isBlank()) return;
        try { RebornSkins.applySpec(mc.player.getUUID(), SkinSpec.deserialize(blob)); } catch (RuntimeException ignored) { }
    }

    /** Le dernier essai, pour la ligne « Tu essaies : … ». */
    private BoutiqueClient.Article dernierEssai() {
        BoutiqueClient.Article sel = article(choisi);
        if (sel != null && essaye(sel)) return sel;
        if (essaiTenue != null) return article(essaiTenue);
        BoutiqueClient.Article d = null;
        for (BoutiqueClient.Article a : essaiObjets.values()) d = a;
        return d;
    }

    /* ================================================================ achats */

    private void acheter(BoutiqueClient.Article a, boolean porter) {
        if (a == null) return;
        if (porter && (a.tenue())) {
            SkinSpec spec = apparence();
            spec.outfitId = a.id();
            BoutiqueClient.envoyer("acheter_porter:" + a.id() + "\n" + spec.serialize());
        } else {
            BoutiqueClient.envoyer((porter ? "acheter_porter:" : "acheter:") + a.id());
        }
        confirmer = null;
        RebornSounds.playReborn("shop.select", 0.8f, 0.5f);
    }

    private void porter(BoutiqueClient.Article a, boolean mettre) {
        SkinSpec spec = apparence();
        spec.outfitId = mettre ? a.id() : "";
        if (mettre) essaiTenue = null;
        BoutiqueClient.envoyer((mettre ? "porter:" + a.id() : "ranger") + "\n" + spec.serialize());
    }

    private void dire(String m, int col) {
        message = m;
        messageCouleur = col;
        messageLe = System.currentTimeMillis();
    }

    /* ================================================================ rendu */

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        float a = Math.min(1f, (System.currentTimeMillis() - ouvertLe) / 220f);
        if (bazar) {
            ctx.fill(0, 0, this.width, this.height, DaKit.argb(0x0A0608, 0.5f * a));
            return;
        }
        if (voile) {
            ctx.fill(0, 0, this.width, this.height, DaKit.argb(0x0A0608, 0.62f * a));
            return;
        }
        // lisibilité des colonnes posées sur le monde : un voile en dégradé à gauche, aucun voile plein écran
        int w = Math.min(300, this.width / 3);
        int pas = 6;
        for (int x = 0; x < w; x += pas) {
            // plein sous la colonne des catégories, puis fondu jusqu'au milieu de l'écran
            float t = x < w * 0.55f ? 1f : 1f - (x - w * 0.55f) / (w * 0.45f);
            ctx.fill(x, 0, Math.min(w, x + pas), this.height, DaKit.argb(0x0A0608, 0.64f * t * t * (3 - 2 * t) * a));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        zones.clear();
        pz.police(this.font);
        if (bazar) rendreBazar(ctx, mx, my);
        else rendreTailleur(ctx, mx, my);
        if (confirmer != null) rendreConfirmation(ctx, mx, my);
    }

    private void rendreTailleur(GuiGraphicsExtractor ctx, int mx, int my) {
        int W = this.width, H = this.height;
        float cx = W / 2f;
        Font f = this.font;
        Minecraft mc = Minecraft.getInstance();

        if (voile && mc.player != null) {
            int c = CameraEssayage.INSTANCE.cadrage();
            float[] taille = {0.27f, 0.42f, 0.62f}, mil = {0.50f, 0.64f, 0.80f};
            int size = Math.round(H * taille[c]);
            int milieu = Math.round(H * 0.53f + size * (mil[c] - 0.5f) * 1.6f);
            renderAvatar360(ctx, Math.round(cx) - size, milieu - size, Math.round(cx) + size, milieu + size, size, spin, mc.player);
        }

        // ── en-tête : mètre ruban, titre, filet d'or, sous-titre ──
        DaKit.blit(ctx, METRE, Math.round(cx - 40), 12, 0, 0, 80, 9, 80, 9);
        String titreB = BoutiqueClient.nom.toUpperCase(Locale.FRENCH);
        pz.arcadeC(titreB, cx, 25, OR_CLAIR, 1.5f);
        float tw = Math.max(140, (this.font.width(DaKit.arcade(titreB)) - 1) * 1.5f + 30);
        pz.filetH(cx - tw / 2f, cx + tw / 2f, 38, OR);
        pz.texteC(BoutiqueClient.sous, cx, 48, 14, CREME_DOUX, false);

        // ── « Tu essaies : … Retirer » ──
        BoutiqueClient.Article ess = dernierEssai();
        if (ess != null) {
            String t = "Tu essaies : " + ess.nom();
            float w1 = pz.largeur(t, 14, false), w2 = pz.largeur("Retirer", 14, true);
            float bw = w1 + w2 + 22, bx = cx - bw / 2f;
            pz.fond(bx, 67, bx + bw, 83, DaKit.argb(0x0A0608, 0.86f));
            pz.texte(t, bx + 7, 75, 14, CREME, false);
            boolean h = DaKit.dans(mx, my, Math.round(bx + 7 + w1 + 6), 67, Math.round(w2 + 6), 16);
            pz.texte("Retirer", bx + 7 + w1 + 8, 75, 14, h ? OR_CLAIR : OR, true);
            if (h) pz.filetH(bx + 7 + w1 + 8, bx + 7 + w1 + 8 + w2, 80, OR_CLAIR);
            BoutiqueClient.Article e2 = ess;
            zones.add(new Zone(bx + 7 + w1 + 4, 67, w2 + 8, 16, () -> { retirerEssai(e2); RebornSounds.uiClick(); }, -1));
        }

        // ── gauche : catégories du comptoir ──
        float lx = 60, ly = Math.max(110, H / 2f - 135);
        String titre = BoutiqueClient.comptoir == null || BoutiqueClient.comptoir.isBlank() ? "Categories" : BoutiqueClient.comptoir;
        pz.arcade(titre.toUpperCase(Locale.FRENCH), lx, ly, CREME_DOUX, 0.5f);
        float y = ly + 22;
        for (BoutiqueClient.Categorie c : BoutiqueClient.categories) {
            boolean actif = c.id().equals(categorie);
            int n = nb(c.id());
            float w = pz.largeur(c.nom(), 18, actif);
            boolean h = DaKit.dans(mx, my, Math.round(lx - 4), Math.round(y - 9), Math.round(w + 30), 18);
            int col = actif ? CREME : n == 0 ? GRIS : h ? CREME : CREME_DOUX;
            pz.texte(c.nom(), lx, y, 18, col, actif);
            pz.texte(String.valueOf(n), lx + w + 6, y + 1, 12, n == 0 ? 0xFF6A6058 : CREME_DOUX, false);
            if (actif) {
                pz.fond(lx - 8, y - 4, lx - 6, y + 4, OR);
                pz.filetH(lx, lx + w, y + 8, OR);
            } else if (h) pz.filetH(lx, lx + w, y + 8, DaKit.argb(0xD9A95E, 0.5f));
            String id = c.id();
            zones.add(new Zone(lx - 4, y - 9, w + 30, 18, () -> changerCategorie(id), -1));
            y += 21;
        }
        y += 8;
        boolean h1 = DaKit.dans(mx, my, Math.round(lx - 2), Math.round(y - 7), 80, 14);
        pz.texte("Tout retirer", lx, y, 15, essaiTenue != null || !essaiObjets.isEmpty() ? (h1 ? CREME : CREME_DOUX) : 0xFF6A6058, false);
        zones.add(new Zone(lx - 2, y - 7, 80, 14, this::toutRetirer, -1));
        y += 17;
        boolean h2 = DaKit.dans(mx, my, Math.round(lx - 2), Math.round(y - 7), 60, 14);
        pz.texte("Quitter", lx, y, 15, h2 ? CREME : CREME_DOUX, false);
        zones.add(new Zone(lx - 2, y - 7, 60, 14, () -> { RebornSounds.uiClick(); onClose(); }, -1));

        // ── bas : cadrages et aides ──
        int cad = CameraEssayage.INSTANCE.cadrage();
        float fy = H - 42;
        float total = 0;
        for (String n : CameraEssayage.NOMS) total += pz.largeur(n, 15, true) + 14;
        float fx = cx - (total - 14) / 2f;
        for (int i = 0; i < 3; i++) {
            String n = CameraEssayage.NOMS[i];
            float w = pz.largeur(n, 15, i == cad);
            boolean h = DaKit.dans(mx, my, Math.round(fx - 3), Math.round(fy - 7), Math.round(w + 6), 14);
            pz.texte(n, fx, fy, 15, i == cad ? CREME : h ? CREME : CREME_DOUX, i == cad);
            if (i == cad) pz.filetH(fx, fx + w, fy + 7, OR);
            int k = i;
            zones.add(new Zone(fx - 3, fy - 7, w + 6, 14, () -> cadrer(k), -1));
            fx += w + 14;
        }
        aides(cx, H - 20, new String[][]{{"Glisser", "tourner"}, {"Molette", "en pied · buste · visage"}, {"Échap", "quitter"}});

        // ── droite : le catalogue ──
        catalogue(ctx, mx, my);

        // ── message du serveur ──
        if (message != null && System.currentTimeMillis() - messageLe < 4200) {
            float w = pz.largeur(message, 14, false) + 20;
            float my0 = ess != null ? 88 : 67;
            pz.fond(cx - w / 2f, my0, cx + w / 2f, my0 + 16, DaKit.argb(0x0A0608, 0.86f));
            pz.texteC(message, cx, my0 + 8, 14, messageCouleur, false);
        }
        pz.vider(ctx);
        for (Runnable r : differes) r.run();
        differes.clear();
        if (tip != null) {   // l'infobulle passe par-dessus tout
            infobulle(tip, tipX, tipY);
            pz.vider(ctx);
            tip = null;
        }
    }

    /** Ce qui se dessine après les lots du pinceau (poupées, icônes d'objet, pièces). */
    private final List<Runnable> differes = new ArrayList<>();
    private BoutiqueClient.Article tip;
    private int tipX, tipY;

    /** Le panneau de droite : bourse, vignettes, pièce choisie. */
    private void catalogue(GuiGraphicsExtractor ctx, int mx, int my) {
        int W = this.width, H = this.height;
        int pw = 230, ph = Math.min(382, H - 108);
        int px = W - pw - 18, py = Math.max(50, (H - ph) / 2 - 22);
        DaKit.coupe(ctx, PANNEAU, px, py, pw + 6, ph + 6, 0, 0, 236, 392, 16, 16, 236, 392);
        float x0 = px + 12, x1 = px + pw - 12;
        List<BoutiqueClient.Article> l = liste();
        // en-tête
        pz.arcade(nomCategorie(categorie).toUpperCase(Locale.FRENCH), x0, py + 14, CREME, 0.5f);
        pz.texte(l.size() + (l.size() > 1 ? " pièces" : " pièce"), x0, py + 31, 13, CREME_DOUX, false);
        String b = fmt(BoutiqueClient.bourse) + " ryo";
        float bw = pz.texteD(b, x1, py + 19, 19, CREME, true);
        int bx = Math.round(x1 - bw - 15);
        differes.add(() -> DaKit.blit(ctx, BOURSE, bx, py + 13, 0, 0, 12, 12, 12, 12));
        pz.texteD("Bourse · sur toi", x1, py + 32, 12, CREME_DOUX, false);
        pz.filetH(x0, x1, py + 41, 0xFF4A3420);

        // grille 3 × n
        int cw = 66, ch = 82, gap = 5;
        int gy = py + 48;
        int detailH = 74;
        int lignes = Math.max(1, (ph - 48 - detailH + gap) / (ch + gap));
        int nl = (l.size() + 2) / 3;
        defile = Math.max(0, Math.min(defile, Math.max(0, nl - lignes)));
        survolCase = -1;
        if (l.isEmpty()) {
            pz.texteC("Rien de cette catégorie", (x0 + x1) / 2f, gy + 60, 14, CREME_DOUX, false);
            pz.texteC("au catalogue pour l'instant.", (x0 + x1) / 2f, gy + 74, 14, CREME_DOUX, false);
        }
        int gx0 = Math.round(x0) + 1;
        for (int i = defile * 3; i < l.size() && i < (defile + lignes) * 3; i++) {
            BoutiqueClient.Article a = l.get(i);
            int r = i / 3 - defile, c = i % 3;
            int cx0 = gx0 + c * (cw + gap), cy0 = gy + r * (ch + gap);
            boolean sel = a.id().equals(choisi), h = DaKit.dans(mx, my, cx0, cy0, cw, ch);
            if (h) survolCase = i;
            pz.fond(cx0, cy0, cx0 + cw, cy0 + ch, sel ? CASE_SEL : CASE);
            pz.cadre(cx0, cy0, cw, ch, sel ? OR : h ? 0xFF6A5040 : BORD);
            boolean verr = a.verrou() != null;
            if (essaye(a)) {
                float ew = pz.largeur("essai", 11, true) + 8;
                pz.fond(cx0 + 2, cy0 + 2, cx0 + 2 + ew, cy0 + 12, 0xFFA0182B);
                pz.texte("essai", cx0 + 6, cy0 + 7, 11, CREME, true);
            }
            // figure : la pièce sur son corps (tenue) ou l'objet
            if (a.tenue()) {
                Identifier skin = skinDe(a.id());
                boolean slim = slimApparence;
                float fcx = cx0 + cw / 2f, pieds = cy0 + 57;
                int teinte = verr ? DaKit.argb(0x9A9490, 0.55f) : 0xFFFFFFFF;
                differes.add(() -> vignettes.dessiner(ctx, skin, slim, fcx, pieds, 1.5f, -16f, 0f, teinte));
            } else if (a.item() != null) {
                ItemStack st = a.item().toStack();
                int ix = cx0 + cw / 2, iy = cy0 + 32;
                differes.add(() -> {
                    ctx.pose().pushMatrix();
                    ctx.pose().translate(ix, iy);
                    ctx.pose().scale(2.5f, 2.5f);
                    ctx.item(st, -8, -8);
                    ctx.pose().popMatrix();
                });
            }
            String nom = DaKit.ellipse(this.font, a.nom(), Math.round((cw - 6) * 18f / 13f));
            pz.texteC(nom, cx0 + cw / 2f, cy0 + 65, 13, verr ? GRIS : sel ? CREME : 0xFFDDD2BE, sel);
            String porte = porteeId();
            if (a.tenue() && a.id().equals(porte)) pz.texteC("portée", cx0 + cw / 2f, cy0 + 75, 12, VERT, false);
            else if (a.possede()) pz.texteC("à toi", cx0 + cw / 2f, cy0 + 75, 12, VERT, false);
            else if (verr) {
                String court = verrouCourt(a);
                float w = pz.largeur(court, 11, false);
                int lk = Math.round(cx0 + cw / 2f - (w + 11) / 2f);
                differes.add(() -> DaKit.blit(ctx, CADENAS, lk, cy0 + 70, 0, 0, 8, 10, 8, 10, GRIS));
                pz.texte(court, lk + 11, cy0 + 75, 11, GRIS, false);
            } else prix(ctx, a.prix(), cx0 + cw / 2f, cy0 + 75, 12, BoutiqueClient.bourse >= a.prix() ? OR_CLAIR : ROUGE, true);
            zones.add(new Zone(cx0, cy0, cw, ch, () -> choisir(a), i));
            if (h && verr && confirmer == null) { tip = a; tipX = cx0; tipY = cy0 + ch / 2; }
        }
        if (nl > lignes) {   // barre de défilement
            int th = lignes * (ch + gap) - gap;
            int rx = px + pw - 6;
            pz.px(rx, gy, rx + 1, gy + th, 0xFF3A2A2E);
            int bh = Math.max(14, th * lignes / nl);
            int by = gy + (th - bh) * defile / Math.max(1, nl - lignes);
            pz.px(rx - 0.5f, by, rx + 1.5f, by + bh, OR);
        }
        if (survolCase >= 0 && survolCase != survolCasePrec) RebornSounds.playReborn("stats.hover", 1.2f, 0.12f);
        survolCasePrec = survolCase;

        // la pièce choisie
        int dy = py + ph - detailH;
        pz.filetH(x0, x1, dy, 0xFF4A3420);
        BoutiqueClient.Article a = article(choisi);
        if (a == null || !a.cat().equals(categorie)) {
            pz.texte("Clique une pièce pour l'essayer.", x0, dy + 22, 14, CREME_DOUX, false);
            pz.texte("L'essai est gratuit et ne se voit que de toi.", x0, dy + 38, 12, GRIS, false);
            return;
        }
        String nom = DaKit.ellipse(this.font, a.nom(), Math.round((x1 - x0 - 70) * 18f / 20f));
        pz.texte(nom, x0, dy + 17, 20, CREME, true);
        prix(ctx, a.prix(), 0, dy + 17, 18, a.verrou() != null ? GRIS : OR_CLAIR, true, x1);
        if (a.verrou() != null) {
            differes.add(() -> DaKit.blit(ctx, CADENAS, Math.round(x0), dy + 29, 0, 0, 8, 10, 8, 10, AMBRE));
            pz.texte(DaKit.ellipse(this.font, a.verrou() + " — " + a.raison().toLowerCase(Locale.ROOT), Math.round((x1 - x0 - 12) * 18f / 13f)),
                x0 + 12, dy + 34, 13, AMBRE, true);
        } else {
            String liaison = a.tenue() ? "Tenue · liée à ce personnage" : a.objet() ? "Objet · va dans ta sacoche" : "";
            pz.texte(liaison, x0, dy + 34, 13, CREME_DOUX, false);
        }
        // actions
        float by = dy + 47, bh = 18;
        String porte = porteeId();
        if (a.tenue() && a.possede()) {
            boolean portee = a.id().equals(porte);
            plaque(portee ? "Ranger" : "Porter", x0, by, 118, bh, true, mx, my, () -> porter(a, !portee));
            pz.texte(portee ? "Tu la portes" : "Dans ta garde-robe", x0 + 128, by + bh / 2f, 14, VERT, false);
        } else if (a.verrou() != null) {
            plaque("Acheter · " + fmt(a.prix()) + " ryo", x0, by, 118, bh, false, mx, my, null);
            pz.texte("Essayer reste libre", x0 + 128, by + bh / 2f, 14, CREME_DOUX, false);
        } else if (BoutiqueClient.bourse < a.prix()) {
            plaque("Il te manque " + fmt(a.prix() - BoutiqueClient.bourse) + " ryo", x0, by, 128, bh, false, mx, my, null);
            mot("Où est le guichet ?", x0 + 138, by + bh / 2f, mx, my,
                () -> dire("Retire des ryo au guichet de la banque du village.", AMBRE));
        } else {
            plaque("Acheter · " + fmt(a.prix()) + " ryo", x0, by, 118, bh, true, mx, my, () -> confirmer = a);
            if (a.tenue() || a.slot() != null) mot("Acheter et porter", x0 + 128, by + bh / 2f, mx, my, () -> confirmer = a);
        }
    }

    private String verrouCourt(BoutiqueClient.Article a) {
        String v = a.verrou();
        if (v.startsWith("Réservé aux ")) {
            v = v.substring("Réservé aux ".length()).replace(" et au-dessus", "+");
        } else if (v.startsWith("Réservé à la fonction ")) v = v.substring("Réservé à la fonction ".length());
        else if (v.startsWith("Réservé à ")) v = "Réservé";
        return DaKit.ellipse(this.font, v, Math.round(50 * 18f / 11f));
    }

    private void infobulle(BoutiqueClient.Article a, int x, int y) {
        int w = 0;
        String l1 = a.verrou(), l2 = a.raison(), l3 = "Tu peux l'essayer, pas l'acheter";
        w = Math.round(Math.max(Math.max(pz.largeur(a.nom(), 15, true), pz.largeur(l1, 14, true)),
            Math.max(pz.largeur(l2, 14, false), pz.largeur(l3, 14, false)))) + 16;
        int h = 60;
        int bx = Math.max(4, x - w - 6);
        int by = Math.max(4, Math.min(y - h / 2, this.height - h - 4));
        pz.fond(bx, by, bx + w, by + h, 0xF0120A0E);
        pz.cadre(bx, by, w, h, OR);
        pz.texte(a.nom(), bx + 8, by + 11, 15, CREME, true);
        pz.texte(l1, bx + 8, by + 25, 14, AMBRE, true);
        pz.texte(l2, bx + 8, by + 38, 14, CREME, false);
        pz.texte(l3, bx + 8, by + 51, 14, CREME_DOUX, false);
    }

    /** Pièce d'or + montant, centré en {@code cx} (ou aligné à droite sur {@code xd} si donné). */
    private void prix(GuiGraphicsExtractor ctx, long n, float cx, float cy, int taille, int col, boolean gras) {
        prix(ctx, n, cx, cy, taille, col, gras, Float.NaN);
    }

    private void prix(GuiGraphicsExtractor ctx, long n, float cx, float cy, int taille, int col, boolean gras, float xd) {
        String t = Float.isNaN(xd) ? fmt(n) : fmt(n) + " ryo";
        float w = pz.largeur(t, taille, gras);
        float x = Float.isNaN(xd) ? cx - (w + 11) / 2f + 11 : xd - w;
        pz.texte(t, x, cy, taille, col, gras);
        int ix = Math.round(x - 11), iy = Math.round(cy - 4.5f);
        boolean gris = col == GRIS;
        differes.add(() -> DaKit.blit(ctx, PIECE, ix, iy, 0, 0, 8, 8, 8, 8, gris ? DaKit.argb(0xFFFFFF, 0.4f) : 0xFFFFFFFF));
    }

    /** Plaque fine laquée (une seule action principale) ; inactive = grise, sans clic. */
    private void plaque(String t, float x, float y, float w, float h, boolean actif, int mx, int my, Runnable act) {
        boolean hov = actif && DaKit.dans(mx, my, Math.round(x), Math.round(y), Math.round(w), Math.round(h));
        pz.fond(x, y, x + w, y + h, actif ? (hov ? LAQUE_SURVOL : LAQUE) : INACTIF);
        pz.cadre(x, y, w, h, actif ? (hov ? OR_CLAIR : OR) : 0xFF4A4044);
        pz.texteC(t, x + w / 2f, y + h / 2f, 14, actif ? CREME : GRIS, true);
        if (actif && act != null) zones.add(new Zone(x, y, w, h, () -> { RebornSounds.uiClick(); act.run(); }, -1));
    }

    /** Un mot cliquable (souligné au survol). */
    private void mot(String t, float x, float cy, int mx, int my, Runnable act) {
        float w = pz.largeur(t, 14, false);
        boolean h = DaKit.dans(mx, my, Math.round(x - 2), Math.round(cy - 7), Math.round(w + 4), 14);
        pz.texte(t, x, cy, 14, h ? OR_CLAIR : OR, false);
        if (h) pz.filetH(x, x + w, cy + 6, OR_CLAIR);
        zones.add(new Zone(x - 2, cy - 7, w + 4, 14, () -> { RebornSounds.uiClick(); act.run(); }, -1));
    }

    /** Aides en mots, touches en capuchons : « [Glisser] tourner · [Molette] … ». */
    private void aides(float cx, float cy, String[][] a) {
        float total = 0;
        float[] wk = new float[a.length], wl = new float[a.length];
        for (int i = 0; i < a.length; i++) {
            wk[i] = pz.largeur(a[i][0], 12, true) + 10;
            wl[i] = pz.largeur(a[i][1], 14, false);
            total += wk[i] + 6 + wl[i] + (i < a.length - 1 ? 16 : 0);
        }
        float x = cx - total / 2f;
        pz.fond(x - 8, cy - 10, x + total + 8, cy + 10, DaKit.argb(0x0A0608, 0.55f));
        for (int i = 0; i < a.length; i++) {
            pz.fond(x, cy - 7, x + wk[i], cy + 7, 0xFF1A1014);
            pz.cadre(x, cy - 7, wk[i], 14, 0xFF8C8070);
            pz.texteC(a[i][0], x + wk[i] / 2f, cy, 12, CREME, true);
            x += wk[i] + 6;
            pz.texte(a[i][1], x, cy, 14, CREME_DOUX, false);
            x += wl[i] + 16;
        }
    }

    /* ================================================================ confirmation (maquette 04) */

    private void rendreConfirmation(GuiGraphicsExtractor ctx, int mx, int my) {
        BoutiqueClient.Article a = confirmer;
        int w = 230, h = 146;
        int x = (this.width - w) / 2, y = (this.height - h) / 2 - 30;
        ctx.fill(0, 0, this.width, this.height, DaKit.argb(0x0A0608, 0.35f));
        zones.clear();
        DaKit.coupe(ctx, PANNEAU, x, y, w + 6, h + 6, 0, 0, 236, 392, 16, 16, 236, 392);
        float cx = x + w / 2f;
        pz.texteC("Acheter " + a.nom() + " ?", cx, y + 22, 19, CREME, true);
        long apres = BoutiqueClient.bourse - a.prix();
        String detail = "bourse " + fmt(BoutiqueClient.bourse) + " → " + fmt(Math.max(0, apres));
        String p = fmt(a.prix()) + " ryo";
        float wp = pz.largeur(p, 18, true), wd = pz.largeur(detail, 14, false);
        float sx = cx - (11 + wp + 12 + wd) / 2f;
        int ix = Math.round(sx), iy = y + 39;
        pz.texte(p, sx + 11, y + 43, 18, OR_CLAIR, true);
        pz.texte(detail, sx + 11 + wp + 12, y + 43, 14, CREME_DOUX, false);
        pz.filetH(x + 22, x + w - 22, y + 56, 0xFF4A3420);
        String liaison = a.tenue() ? "Liée à ce personnage : perdue à sa mort RP, ni échangeable ni volable."
            : "Objet de ton personnage : il va dans ta sacoche ; échangeable, volable, perdu à la mort RP.";
        float ly = y + 67;
        for (String ligne : couper(liaison, w - 44, 12)) { pz.texte(ligne, x + 22, ly, 12, CREME_DOUX, false); ly += 10; }
        pz.vider(ctx);
        DaKit.blit(ctx, PIECE, ix, iy, 0, 0, 8, 8, 8, 8);
        boolean porter = a.tenue() || a.slot() != null;
        float by = y + 88;
        if (porter) {
            plaque("Acheter et porter · Entrée", x + 22, by, 138, 18, true, mx, my, () -> acheter(a, true));
            mot("Acheter", x + 172, by + 9, mx, my, () -> acheter(a, false));
        } else {
            plaque("Acheter · Entrée", x + 22, by, 138, 18, true, mx, my, () -> acheter(a, false));
        }
        mot("Annuler", cx - pz.largeur("Annuler", 14, false) / 2f, y + 124, mx, my, () -> confirmer = null);
        pz.vider(ctx);
    }

    /* ================================================================ bazar */

    private void rendreBazar(GuiGraphicsExtractor ctx, int mx, int my) {
        int w = 230;
        List<BoutiqueClient.Article> l = liste();
        int lignes = Math.max(1, Math.min(9, l.size()));
        int h = 118 + lignes * 22 + 60;
        int x = (this.width - w) / 2, y = Math.max(8, (this.height - h) / 2 - 10);
        DaKit.coupe(ctx, PANNEAU, x, y, w + 6, h + 6, 0, 0, 236, 392, 16, 16, 236, 392);
        float cx = x + w / 2f;
        pz.vider(ctx);
        DaKit.blit(ctx, BALANCE, Math.round(cx - 24), y + 11, 0, 0, 48, 22, 48, 22);
        pz.arcadeC(BoutiqueClient.nom.toUpperCase(Locale.FRENCH), cx, y + 41, OR_CLAIR, 1.5f);
        pz.texteC(BoutiqueClient.sous, cx, y + 65, 13, CREME_DOUX, false);
        // onglets en mots
        float total = 0;
        for (BoutiqueClient.Categorie c : BoutiqueClient.categories) total += pz.largeur(c.nom(), 16, c.id().equals(categorie)) + 18;
        float tx = cx - (total - 18) / 2f;
        for (BoutiqueClient.Categorie c : BoutiqueClient.categories) {
            boolean actif = c.id().equals(categorie);
            float tw = pz.largeur(c.nom(), 16, actif);
            boolean hv = DaKit.dans(mx, my, Math.round(tx - 2), y + 75, Math.round(tw + 4), 16);
            pz.texte(c.nom(), tx, y + 83, 16, actif || hv ? CREME : CREME_DOUX, actif);
            if (actif) pz.filetH(tx, tx + tw, y + 91, OR);
            String id = c.id();
            zones.add(new Zone(tx - 2, y + 75, tw + 4, 16, () -> changerCategorie(id), -1));
            tx += tw + 18;
        }
        pz.filetH(x + 16, x + w - 16, y + 100, 0xFF4A3420);
        // liste
        float ly = y + 108;
        defile = Math.max(0, Math.min(defile, Math.max(0, l.size() - lignes)));
        survolCase = -1;
        if (l.isEmpty()) pz.texteC("Rien à vendre ici pour l'instant.", cx, ly + 12, 14, CREME_DOUX, false);
        List<int[]> pieces = new ArrayList<>();
        for (int i = defile; i < l.size() && i < defile + lignes; i++) {
            BoutiqueClient.Article a = l.get(i);
            boolean sel = a.id().equals(choisi), hv = DaKit.dans(mx, my, x + 12, Math.round(ly), w - 24, 20);
            if (hv) survolCase = i;
            if (sel || hv) pz.fond(x + 12, ly, x + w - 12, ly + 20, sel ? CASE_SEL : 0xFF1A0E12);
            if (sel) pz.cadre(x + 12, ly, w - 24, 20, OR);
            pz.texte(DaKit.ellipse(this.font, a.nom(), Math.round((w - 110) * 18f / 15f)), x + 20, ly + 10, 15, sel ? CREME : 0xFFDDD2BE, sel);
            String p = fmt(a.prix());
            float pw2 = pz.texteD(p, x + w - 20, ly + 10, 14, BoutiqueClient.bourse >= a.prix() ? OR_CLAIR : ROUGE, true);
            pieces.add(new int[]{Math.round(x + w - 20 - pw2 - 11), Math.round(ly + 6)});
            zones.add(new Zone(x + 12, ly, w - 24, 20, () -> choisir(a), i));
            ly += 22;
        }
        if (survolCase >= 0 && survolCase != survolCasePrec) RebornSounds.playReborn("stats.hover", 1.2f, 0.12f);
        survolCasePrec = survolCase;
        // pied : la ligne choisie
        float fy = y + h - 44;
        pz.filetH(x + 16, x + w - 16, fy - 6, 0xFF4A3420);
        BoutiqueClient.Article a = article(choisi);
        // la bourse à gauche, l'achat à droite
        String b = fmt(BoutiqueClient.bourse) + " ryo";
        pz.texte(b, x + 32, fy + 6, 15, CREME, true);
        pz.texte("Bourse · sur toi", x + 32, fy + 17, 11, CREME_DOUX, false);
        int bix = x + 17, biy = Math.round(fy + 3);
        float px0 = x + w - 16 - 116;
        if (a != null && a.cat().equals(categorie)) {
            if (BoutiqueClient.bourse < a.prix()) {
                plaque("Il te manque " + fmt(a.prix() - BoutiqueClient.bourse) + " ryo", px0, fy + 2, 116, 18, false, mx, my, null);
            } else {
                plaque("Acheter · " + fmt(a.prix()) + " ryo", px0, fy + 2, 116, 18, true, mx, my, () -> acheter(a, false));
            }
        } else {
            pz.texteD("Choisis un article", x + w - 16, fy + 11, 13, CREME_DOUX, false);
        }
        pz.texteC("Échap  fermer", cx, y + h - 12, 13, CREME_DOUX, false);
        if (message != null && System.currentTimeMillis() - messageLe < 4200) {
            float mw = pz.largeur(message, 14, false) + 20;
            pz.fond(cx - mw / 2f, y + h + 10, cx + mw / 2f, y + h + 26, DaKit.argb(0x0A0608, 0.86f));
            pz.texteC(message, cx, y + h + 18, 14, messageCouleur, false);
        }
        pz.vider(ctx);
        DaKit.blit(ctx, BOURSE, bix, biy, 0, 0, 12, 12, 12, 12);
        for (int[] pc : pieces) DaKit.blit(ctx, PIECE, pc[0], pc[1], 0, 0, 8, 8, 8, 8);
    }

    /* ================================================================ entrées */

    private void changerCategorie(String id) {
        if (id.equals(categorie)) return;
        categorie = id;
        defile = 0;
        RebornSounds.playReborn("stats.tab", 1.0f, 0.5f);
    }

    private void choisir(BoutiqueClient.Article a) {
        boolean deja = a.id().equals(choisi);
        choisi = a.id();
        RebornSounds.playReborn("shop.select", 1.0f, 0.6f);
        if (bazar) return;
        if (!deja || !essaye(a)) { if (!essaye(a)) essayer(a); }
    }

    private void cadrer(int c) {
        if (c == CameraEssayage.INSTANCE.cadrage()) return;
        CameraEssayage.INSTANCE.cadrer(c);
        RebornSounds.playReborn("shop.select", 1.3f, 0.3f);
    }

    /** Le personnage pivote pour de vrai (les autres le voient se regarder) ; repli : seulement le dessin. */
    private void tourner(Minecraft mc, float yaw) {
        if (mc.player == null) return;
        mc.player.setYRot(yaw);
        mc.player.setYHeadRot(yaw);
        mc.player.setYBodyRot(yaw);
        mc.player.setXRot(0f);
    }

    /** Pour le banc : pose la rotation et le cadrage. */
    public void bancTourner(float degres) {
        Minecraft mc = Minecraft.getInstance();
        if (voile) spin = degres;
        else if (mc.player != null) tourner(mc, CameraEssayage.INSTANCE.face() + degres);
    }

    public void bancCadrer(int c) { CameraEssayage.INSTANCE.cadrer(c); }

    public void bancChoisir(String id, boolean essai) {
        BoutiqueClient.Article a = article(id);
        if (a == null) return;
        categorie = a.cat();
        choisi = id;
        if (essai && !essaye(a)) essayer(a);
    }

    public void bancConfirmer(String id) { confirmer = article(id); }

    public void bancCategorie(String id) { categorie = id; defile = 0; }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        double mx = e.x(), my = e.y();
        if (e.button() == 0) {
            for (Zone z : zones) {
                if (z.dans(mx, my)) { z.act().run(); return true; }
            }
            if (confirmer != null) return true;
            if (!bazar && dbl && centre(mx)) {
                Minecraft mc = Minecraft.getInstance();
                if (voile) spin = 0; else tourner(mc, CameraEssayage.INSTANCE.face());
                return true;
            }
        }
        if (!bazar && confirmer == null && centre(mx)) { glisse = true; glisseX = mx; return true; }
        return super.mouseClicked(e, dbl);
    }

    private boolean centre(double mx) { return mx > 170 && mx < this.width - 260; }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        glisse = false;
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (!glisse || bazar) return super.mouseDragged(e, dx, dy);
        Minecraft mc = Minecraft.getInstance();
        if (voile) spin += (float) dx * 1.2f;
        else if (mc.player != null) tourner(mc, mc.player.getYRot() - (float) dx * 1.2f);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (confirmer != null) return true;
        if (bazar || mx > this.width - 260) {
            defile -= (int) Math.signum(sy);
            return true;
        }
        int c = CameraEssayage.INSTANCE.cadrage() + (sy > 0 ? 1 : -1);
        if (voile) CameraEssayage.INSTANCE.cadrer(c); else cadrer(c);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int k = e.key();
        if (confirmer != null && (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER)) {
            acheter(confirmer, confirmer.tenue() || confirmer.slot() != null);
            return true;
        }
        if (k == GLFW.GLFW_KEY_TAB && !BoutiqueClient.categories.isEmpty()) {
            int i = 0;
            for (int j = 0; j < BoutiqueClient.categories.size(); j++) if (BoutiqueClient.categories.get(j).id().equals(categorie)) i = j;
            changerCategorie(BoutiqueClient.categories.get((i + 1) % BoutiqueClient.categories.size()).id());
            return true;
        }
        return super.keyPressed(e);
    }

    /* ================================================================ repli : personnage dessiné */

    /**
     * Le joueur local dessiné en interface avec une rotation libre (méthode de l'ancienne échoppe : on reconstruit
     * l'état de rendu et on impose {@code bodyRot}, la méthode vanilla bornant à ±31°). Repli §3.4 seulement.
     */
    private static void renderAvatar360(GuiGraphicsExtractor ctx, int x0, int y0, int x1, int y1, int scale, float spinDeg,
                                        net.minecraft.client.player.LocalPlayer player) {
        try {
            @SuppressWarnings({"rawtypes", "unchecked"})
            net.minecraft.client.renderer.entity.EntityRenderer renderer =
                Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
            @SuppressWarnings("unchecked")
            net.minecraft.client.renderer.entity.state.EntityRenderState state = renderer.createRenderState(player, 1.0f);
            state.shadowPieces.clear();
            state.outlineColor = 0;
            state.nameTag = null;
            if (state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState ls) {
                ls.bodyRot = 180.0f - spinDeg;
                ls.yRot = 0.0f;
                ls.xRot = 0.0f;
                ls.walkAnimationPos = 0f;
                ls.walkAnimationSpeed = 0f;
                ls.boundingBoxWidth = ls.boundingBoxWidth / ls.scale;
                ls.boundingBoxHeight = ls.boundingBoxHeight / ls.scale;
                ls.scale = 1.0f;
            }
            org.joml.Quaternionf pose = new org.joml.Quaternionf().rotateZ((float) Math.PI);
            org.joml.Quaternionf camOrient = new org.joml.Quaternionf();
            pose.mul(camOrient);
            org.joml.Vector3f translate = new org.joml.Vector3f(0.0f, state.boundingBoxHeight / 2.0f, 0.0f);
            ctx.entity(state, (float) scale, translate, pose, camOrient, x0, y0, x1, y1);
        } catch (RuntimeException ignored) {
            // un rendu raté ne casse pas l'écran
        }
    }

    /** Coupe un texte en lignes de {@code largeur} GUI au plus (Inter, {@code taille} px). */
    private List<String> couper(String t, float largeur, int taille) {
        List<String> l = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String m : t.split(" ")) {
            String cand = cur.isEmpty() ? m : cur + " " + m;
            if (!cur.isEmpty() && pz.largeur(cand, taille, false) > largeur) { l.add(cur.toString()); cur = new StringBuilder(m); }
            else cur = new StringBuilder(cand);
        }
        if (!cur.isEmpty()) l.add(cur.toString());
        return l;
    }

    static String fmt(long v) { return fr.reborn.hud.monnaie.BourseClient.fmt(v); }
}
