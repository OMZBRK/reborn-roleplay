package fr.reborn.hud.staff;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Poste de garde — panel staff en jeu, dans la DA Reborn (kit commun : voile sur le jeu, plaque laquée suspendue,
 * onglets pendus, cadre de bois et papier washi). Motif propre à l'écran : chaque joueur est une plaquette de bois
 * (fuda) et sanctionner revient à apposer le sceau rouge 罰.
 *
 * <p>Onglets : Alertes, Joueurs (liste puis fiche : personnage, actions rapides, comptes liés, casier, infractions),
 * Chat staff, Journal, Commandes. Toutes les données viennent du serveur ({@link StaffClient}) ; chaque bouton envoie
 * une demande que le serveur revérifie (grade, cible, palier).
 */
public class StaffScreen extends Screen {

    /* --------------------------------------------------------------- palette (kit DA Reborn) */
    protected static final int LACQUER = 0xFFA0182B, LACQUER_HI = 0xFFC01E35, LACQUER_LO = 0xFF7A1322;
    protected static final int GOLD = 0xFFD9A95E, GOLD_SOFT = 0xFF3A2C14, IVORY = 0xFFF5E9D0, IVORY_2 = 0xFFC2B59A;
    protected static final int WASHI = 0xFFE3D1A8, WASHI_2 = 0xFFD4BF91, WOOD = 0xFF5A3A22, WOOD_DARK = 0xFF2E1C11;
    protected static final int INK = 0xFF2A1A10, INK_SOFT = 0xFF6B5434, FUDA = 0xFFF0E2C2, FUDA_2 = 0xFFE2CFA4;
    protected static final int OK = 0xFF2F7A3A;

    protected static final String[][] TABS = {
            {"alerts", "Alertes"}, {"players", "Joueurs"}, {"chat", "Chat staff"}, {"journal", "Journal"}, {"cmds", "Commandes"}};
    protected static final String[][] LOG_FILTERS = {
            {"all", "Tout"}, {"soin", "Soins"}, {"ko", "KO / ATA"}, {"sanction", "Sanctions"}, {"tp", "Téléportations"}, {"monde", "Monde"}};

    protected record Hit(int x, int y, int w, int h, Runnable action) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    protected final List<Hit> hits = new ArrayList<>();
    protected String tab = "alerts";
    protected String profileUuid;
    protected JsonObject confirm;
    protected final Set<Integer> flags = new HashSet<>();
    protected long sealAt;
    protected String logFilter = "all";
    protected int scroll, contentH;
    protected long openedAt, viewAt;
    protected int cardIndex;
    protected int ticks;
    protected int mouseX, mouseY;
    protected EditBox chatBox, noteBox, searchBox;

    public StaffScreen() {
        super(Component.literal("Poste de garde"));
    }

    /* =================================================================== cycle de vie */

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
        openedAt = System.currentTimeMillis();
        viewAt = openedAt + 120;
        chatBox = box("Écrire au staff…", 200);
        noteBox = box("Note pour le casier…", 160);
        searchBox = box("Chercher : ata, zone, météo…", 40);
        searchBox.setResponder(v -> scroll = 0);
        StaffClient.send("refresh");
        RebornSounds.playReborn("sacoche.open", 1.0f, 0.5f);
    }

    protected EditBox box(String hint, int max) {
        EditBox b = new EditBox(this.font, 0, 0, 100, 12, Component.literal(hint));
        b.setMaxLength(max);
        b.setHint(Component.literal(hint));
        b.setBordered(false);
        b.setTextColor(INK);
        b.setVisible(false);
        this.addRenderableWidget(b);
        return b;
    }

    @Override
    public void onClose() {
        RebornSounds.playReborn("sacoche.close", 1.0f, 0.5f);
        super.onClose();
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(false);
        StaffClient.clearProfile();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        super.tick();
        if (++ticks % 40 == 0) {
            StaffClient.send("refresh");
            if (profileUuid != null) requestProfile(profileUuid);
        }
    }

    /* =================================================================== géométrie */

    protected int sx0() { return Math.max(Math.round(width * 0.07f) + 6, width / 2 - 320); }
    protected int sx1() { return width - sx0(); }
    protected int sy0() { return 48; }
    protected int sy1() { return Math.min(height - 34, sy0() + GardeArt.PAPIER_H); }

    /** Zone qui défile (dans le papier), selon l'onglet : la barre de chat en bas, la recherche en haut. */
    protected int vy0() { return sy0() + 10 + ("cmds".equals(tab) ? 18 : 0); }
    protected int vy1() { return sy1() - 6 - ("chat".equals(tab) ? 18 : 0); }
    protected int vx0() { return sx0() + navW() + 12; }
    protected int vx1() { return sx1() - 8; }

    /* =================================================================== rendu */

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        float appear = Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f);
        int center = (Math.round(0x50 * appear) << 24) | 0x08040C, edge = (Math.round(0xA8 * appear) << 24) | 0x08040C;
        ctx.fill(0, 0, width, height, center);
        ctx.fillGradient(0, 0, width, height / 4, edge, 0);
        ctx.fillGradient(0, height - height / 4, width, height, 0, edge);
        DrawHelpers.horizontalGradient(ctx, 0, 0, width / 6, height, edge, 0);
        DrawHelpers.horizontalGradient(ctx, width - width / 6, 0, width / 6, height, 0, edge);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        mouseX = mx;
        mouseY = my;
        hits.clear();
        cardIndex = 0;
        drawScreen(ctx, mx, my, delta);
        super.extractRenderState(ctx, mx, my, delta);   // champs de saisie
        toasts(ctx);
    }

    /** Tout le dessin propre à l'écran (surchargé par l'Infirmerie). */
    protected void drawScreen(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        JsonObject snap = StaffClient.snapshot();

        frame(ctx);
        tabs(ctx, snap);
        plaque(ctx, plaqueTitle(snap));

        // contenu défilant
        ctx.enableScissor(vx0() - 8, vy0(), vx1() + 4, Math.min(vy1(), paperBottom()));
        int y0 = vy0() - scroll;
        int end;
        if (snap == null) end = text(ctx, "Connexion au poste de garde…", vx0(), y0 + 4, INK_SOFT);
        else end = switch (tab) {
            case "players" -> profileUuid == null ? players(ctx, snap, y0) : profile(ctx, snap, y0);
            case "chat" -> chat(ctx, snap, y0);
            case "journal" -> journal(ctx, snap, y0);
            case "cmds" -> commands(ctx, snap, y0);
            default -> alerts(ctx, snap, y0);
        };
        ctx.disableScissor();
        contentH = end - y0;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - (vy1() - vy0()) + 6)));

        layoutBoxes(ctx);
        closeButton(ctx);
        if (confirm != null) confirmModal(ctx, snap);
    }


    protected String plaqueTitle(JsonObject snap) {
        if (snap == null) return "Poste de garde";
        return switch (tab) {
            case "players" -> profileUuid != null && StaffClient.profile() != null
                    ? "Fiche · " + name(StaffClient.profile()) : "Joueurs (" + arr(snap, "players").size() + ")";
            case "chat" -> "Chat staff (" + num(snap, "staffOnline") + " en ligne)";
            case "journal" -> "Journal des actions";
            case "cmds" -> "Commandes accessibles";
            default -> "Alertes (" + arr(snap, "alerts").size() + ")";
        };
    }

    /* ------------------------------------------------------------------- décor commun */

    protected static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    /**
     * Cadre de bois veiné à ferrures d'or et papier washi qui se déroule (makimono) sous la poutre laquée ; le rouleau
     * (jiku) descend avec le papier. Décor : textures {@link GardeArt} (une coupe chacune).
     */
    protected void frame(GuiGraphicsExtractor ctx) {
        int x0 = sx0(), x1 = sx1(), y0 = sy0(), y1 = sy1();
        int yb = paperBottom();
        if (yb > y0) {
            ctx.enableScissor(x0, y0, x1, yb);
            GardeArt.papier(ctx, x0, y0, x1 - x0, y1 - y0);
            GardeArt.filigrane(ctx, x1 - 74, y1 - 70, infirmerie() ? 1 : 0);
            ctx.disableScissor();
        }
        GardeArt.cadre(ctx, x0, y0, x1 - x0, y1 - y0);
        if (yb < y1 - 1) GardeArt.jiku(ctx, x0, yb, x1 - x0);
    }

    /** Bas du papier pendant le déroulé d'ouverture. */
    protected int paperBottom() {
        float u = ease((System.currentTimeMillis() - openedAt) / 340f);
        return Math.round(sy0() + 5 + (sy1() - sy0() - 5) * u);
    }

    /** L'en-tête montre-t-il l'Infirmerie (医) plutôt que le Poste de garde (番) ? */
    protected boolean infirmerie() { return false; }

    /**
     * En-tête suspendu (texture : plaque laquée, cordons, glands, sceau et titre cuits) ; reflet doré qui passe
     * toutes les 7 s. Le détail de l'onglet est donné par la navigation et les titres de section.
     */
    protected void plaque(GuiGraphicsExtractor ctx, String title) {
        int cx = width / 2;
        GardeArt.entete(ctx, cx, infirmerie() ? 1 : 0);
        float ph2 = (System.currentTimeMillis() % 7000L) / 1100f;
        if (ph2 < 1f) {
            int x = cx - 149, y = 9, pw = 298, ph = 26;
            int bx = x + Math.round(ph2 * (pw + 24)) - 12;
            ctx.enableScissor(x, y, x + pw, y + ph);
            for (int k = 0; k < 8; k++) ctx.fill(bx + k, y + k * 3, bx + k + 5, y + 3 + k * 3, 0x22FFF2D0);
            ctx.disableScissor();
        }
    }

    /** Largeur de la colonne de navigation (dans le papier, à gauche ; sa bande est cuite dans le washi). */
    protected int navW() { return 74; }

    /**
     * Groupes de la navigation : la première ligne de chaque groupe est {sceau, titre}, les suivantes
     * {id, libellé}. Le Poste de garde seul n'a qu'un groupe ; l'écran unifié ajoute l'Infirmerie.
     */
    protected String[][][] navGroups() {
        String[][] g = new String[TABS.length + 1][];
        g[0] = new String[]{"番", "Poste de garde"};
        System.arraycopy(TABS, 0, g, 1, TABS.length);
        return new String[][][]{g};
    }

    /** Colonne de navigation : groupes à sceau vermillon, entrée active en laque rouge, compteurs en pastilles. */
    protected void tabs(GuiGraphicsExtractor ctx, JsonObject snap) {
        int x0 = sx0() + 3, w = navW(), y = sy0() + 12;
        int yb = paperBottom();
        if (yb > sy0() + 12) {
            ctx.enableScissor(x0 - 3, sy0() + 5, x0 + w + 4, yb);
            long now = System.currentTimeMillis();
            for (String[][] group : navGroups()) {
                GardeArt.sceauVierge(ctx, x0, y - 1);
                ctx.text(font, Component.literal(group[0][0]), x0 + 2, y + 1, DaKit.CREME_KJ, false);
                DaKit.etiq(ctx, font, DaKit.etiq(font, group[0][1]), x0 + 15, y + 4.5f, DaKit.ENCRE_ROUGE);
                y += 14;
                for (int i = 1; i < group.length; i++) {
                    String id = group[i][0], label = group[i][1];
                    boolean sel = id.equals(tab), hov = !sel && hover(x0, y, w, 13);
                    if (sel || hov) GardeArt.navEntree(ctx, x0, y, w, sel);
                    int count = tabCount(id, snap);
                    int room = w - 10 - (count > 0 ? 14 : 0);
                    DaKit.inter9(ctx, font, RebornFont.body(fit(label, room)), x0 + 6, y + 6.5f, sel ? IVORY : INK, 0);
                    if (count > 0) {
                        Component n = DaKit.arcade(String.valueOf(count));
                        int bw = Math.max(11, (int) Math.ceil(DaKit.largeurFin(font, n)) + 6), bx = x0 + w - bw - 2;
                        boolean urgent = "blesses".equals(id) || "alerts".equals(id);
                        GardeArt.pastille(ctx, bx, y + 1, bw, urgent && (now / 400) % 2 == 0);
                        DaKit.arcFin(ctx, font, n, bx + bw / 2f, y + 4.75f, IVORY, 1, false);
                    }
                    hits.add(new Hit(x0, y, w, 13, () -> switchTab(id)));
                    y += 14;
                }
                y += 8;
            }
            ctx.disableScissor();
        }
        if (snap != null) gradeBadge(ctx, num(snap, "grade"), str(snap, "gradeName"));
    }

    /** Onglets de l'écran (utilisés par la navigation par défaut). */
    protected String[][] tabList() { return TABS; }

    /** Compteur affiché en rouge sur une entrée de navigation (0 = aucun). */
    protected int tabCount(String id, JsonObject snap) {
        return "alerts".equals(id) && snap != null ? arr(snap, "alerts").size() : 0;
    }

    /** Badge du grade staff (laque noire, pastille à la couleur du grade, nom en ArcadePix or). */
    protected void gradeBadge(GuiGraphicsExtractor ctx, int grade, String name) {
        Component g = DaKit.arcade(name);
        int gw = font.width(g) + 22, gx = sx0() - 10, gy = height - 18;
        GardeArt.badgeGrade(ctx, gx, gy, gw);
        ctx.fill(gx + 6, gy + 5, gx + 11, gy + 10, gradeColor(grade) | 0xFF000000);
        ctx.fill(gx + 6, gy + 5, gx + 11, gy + 6, 0x40FFFFFF);
        DaKit.arcX1(ctx, font, g, gx + 15, gy + 4, DaKit.OR_CLAIR, 0, true);
    }

    /** Rectangle aux coins coupés (1 px). */
    protected static void chamfer(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + 1, y, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    protected static void chamferGradient(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int top, int bottom) {
        ctx.fillGradient(x + 1, y, x + w - 1, y + h, top, bottom);
        ctx.fillGradient(x, y + 1, x + 1, y + h - 1, top, bottom);
        ctx.fillGradient(x + w - 1, y + 1, x + w, y + h - 1, top, bottom);
    }

    protected void switchTab(String id) {
        if (!id.equals(tab)) { RebornSounds.uiClick(); viewAt = System.currentTimeMillis(); }
        tab = id;
        scroll = 0;
        if (!"players".equals(id)) profileUuid = null;
        confirm = null;
    }

    /** Planche « Fermer » aux embouts dorés (texture), libellé ArcadePix. */
    protected void closeButton(GuiGraphicsExtractor ctx) {
        int w = 124, x = width / 2 - w / 2, y = height - 20;
        boolean hov = hover(x + 4, y, 116, 17);
        GardeArt.fermer(ctx, x, y, hov);
        DaKit.arcX1(ctx, font, DaKit.arcade("Fermer"), width / 2f, y + 5, hov ? DaKit.OR_CLAIR : IVORY, 1, true);
        hits.add(new Hit(x + 4, y + 1, 116, 15, this::onClose));
    }

    /** Bandes de washi scellées, au-dessus du pied. */
    protected void toasts(GuiGraphicsExtractor ctx) {
        int y = height - 44;
        long now = System.currentTimeMillis();
        for (StaffClient.Toast t : StaffClient.toasts()) {
            Component c = RebornFont.body(t.text());
            int w = Math.min(440, font.width(c) + 26), x = width / 2 - w / 2;
            int dy = Math.round((1f - ease((now - t.at()) / 180f)) * 8);
            GardeArt.toast(ctx, x, y + dy, w);
            DaKit.inter9(ctx, font, c, x + 17, y + dy + 8, INK, 0);
            y -= 18;
        }
    }

    protected void layoutBoxes(GuiGraphicsExtractor ctx) {
        boolean chat = "chat".equals(tab) && confirm == null;
        boolean cmds = "cmds".equals(tab) && confirm == null;
        chatBox.setVisible(chat);
        searchBox.setVisible(cmds);
        noteBox.setVisible(confirm != null && sealAt == 0);
        if (chat) {
            int y = sy1() - 20, x = vx0(), w = vx1() - vx0() - bw("Envoyer") - 6;
            GardeArt.champ(ctx, x - 2, y - 1, w + 2);
            chatBox.setX(x + 2); chatBox.setY(y + 3); chatBox.setWidth(w - 6);
            button(ctx, vx1() - bw("Envoyer"), y + 1, "Envoyer", "punish", this::sendChat, false);
        }
        if (cmds) {
            DaKit.Etiq lab = DaKit.etiq(font, "Chercher");
            int y = sy0() + 9, x = vx0() + (int) Math.ceil(lab.w()) + 8, w = vx1() - x;
            DaKit.etiq(ctx, font, lab, vx0(), y + 6.5f, DaKit.ENCRE_ROUGE);
            GardeArt.champ(ctx, x - 2, y - 1, w + 2);
            searchBox.setX(x + 2); searchBox.setY(y + 3); searchBox.setWidth(w - 6);
        }
    }

    /* =================================================================== onglets */

    protected int alerts(GuiGraphicsExtractor ctx, JsonObject snap, int y) {
        y = section(ctx, "Alertes récentes", y);
        JsonArray al = arr(snap, "alerts");
        if (al.isEmpty()) return calm(ctx, y);
        for (JsonElement e : al) {
            JsonObject a = e.getAsJsonObject();
            int dx = slide();
            ctx.pose().pushMatrix();
            ctx.pose().translate(dx, 0);
            int x = vx0() + 6, w = vx1() - vx0() - 6, h = 48;
            fuda(ctx, x, y, w, h, kindColor(str(a, "kind")));
            head(ctx, str(a, "uuid"), x + 17, y + 7, 22);
            int tx = x + 46;
            Component nm = Component.empty()
                    .append(gras(str(a, "rank").isEmpty() ? "" : str(a, "rank") + "  ").withColor(LACQUER & 0xFFFFFF))
                    .append(gras(str(a, "perso").isEmpty() ? str(a, "name") : str(a, "perso")).withColor(INK & 0xFFFFFF))
                    .append(RebornFont.body("  " + str(a, "name")).withColor(INK_SOFT & 0xFFFFFF));
            ctx.text(font, nm, tx, y + 5, INK, false);
            ctx.text(font, RebornFont.body(str(a, "motif")), tx, y + 15, LACQUER_LO, false);
            small(ctx, join(arr(a, "ctx")), tx, y + 25, INK_SOFT);
            int score = num(a, "score");
            if (score > 0) badge(ctx, x + w - 8, y + 4, "SCORE " + score, score >= 100);
            String uuid = str(a, "uuid");
            int bx = tx, by = y + h - 13;
            String id = str(a, "id");
            if ("mort".equals(str(a, "kind"))) {
                bx += button(ctx, bx, by, "Valider la RPK", "punish", () -> killVerdict(id, true), grade(snap) < 2) + 4;
                bx += button(ctx, bx, by, "Refuser", "plain", () -> killVerdict(id, false), grade(snap) < 2) + 4;
                bx += button(ctx, bx, by, "Y aller", "plain", () -> act("tp", uuid), false) + 4;
                ctx.pose().popMatrix();
                y += h + 7;
                continue;
            }
            bx += button(ctx, bx, by, "Observer", "watch", () -> act("watch", uuid), grade(snap) < 2) + 4;
            if ("aide".equals(str(a, "kind")) || "vol".equals(str(a, "kind"))) bx += button(ctx, bx, by, "Y aller", "plain", () -> act("tp", uuid), false) + 4;
            else bx += button(ctx, bx, by, "Sanctionner", "punish", () -> openProfile(uuid), false) + 4;
            int dw = bw("Ignorer");
            button(ctx, x + w - dw - 6, by, "Ignorer", "plain", () -> dismiss(id), false);
            ctx.pose().popMatrix();
            y += h + 7;
        }
        return y;
    }

    protected int players(GuiGraphicsExtractor ctx, JsonObject snap, int y) {
        y = section(ctx, "Joueurs en ligne", y);
        for (JsonElement e : arr(snap, "players")) {
            JsonObject p = e.getAsJsonObject();
            int dx = slide();
            ctx.pose().pushMatrix();
            ctx.pose().translate(dx, 0);
            String st0 = str(p, "state");
            int x = vx0() + 6, w = vx1() - vx0() - 6, h = 26;
            fuda(ctx, x, y, w, h, st0.isEmpty() ? OK : LACQUER);
            head(ctx, str(p, "uuid"), x + 17, y + 5, 16);
            Component nm = Component.empty()
                    .append(gras(str(p, "rank").isEmpty() ? "" : str(p, "rank") + "  ").withColor(LACQUER & 0xFFFFFF))
                    .append(gras(str(p, "perso").isEmpty() ? "(sans personnage)" : str(p, "perso")).withColor(INK & 0xFFFFFF))
                    .append(RebornFont.body("  " + str(p, "name") + (str(p, "village").isEmpty() ? "" : " · " + str(p, "village")))
                            .withColor(INK_SOFT & 0xFFFFFF));
            ctx.text(font, nm, x + 40, y + 5, INK, false);
            String st = str(p, "state");
            small(ctx, st.isEmpty() ? "En forme" : st, x + 40, y + 15, st.isEmpty() ? OK : LACQUER_LO);
            String uuid = str(p, "uuid");
            int fw = bw("Fiche");
            button(ctx, x + w - fw - 8, y + 7, "Fiche", "punish", () -> openProfile(uuid), false);
            ctx.pose().popMatrix();
            y += h + 5;
        }
        return y;
    }

    protected int profile(GuiGraphicsExtractor ctx, JsonObject snap, int y) {
        int g = grade(snap);
        button(ctx, vx0(), y, "Retour aux joueurs", "plain", () -> { profileUuid = null; scroll = 0; StaffClient.clearProfile(); }, false);
        y += 18;
        JsonObject p = StaffClient.profile();
        if (p == null || !profileUuid.equals(str(p, "uuid"))) return text(ctx, "Chargement de la fiche…", vx0(), y, INK_SOFT);
        String uuid = str(p, "uuid");
        int x = vx0() + 6, w = vx1() - vx0() - 6;
        int h = 80;
        fuda(ctx, x, y, w, h, GOLD);
        head(ctx, uuid, x + 18, y + 10, 32);
        int tx = x + 60;
        Component nm = Component.empty()
                .append(gras(str(p, "rank").isEmpty() ? "" : str(p, "rank") + "  ").withColor(LACQUER & 0xFFFFFF))
                .append(gras(str(p, "perso").isEmpty() ? str(p, "name") : str(p, "perso")).withColor(INK & 0xFFFFFF))
                .append(RebornFont.body("  " + str(p, "name") + (str(p, "village").isEmpty() ? "" : " · " + str(p, "village")))
                        .withColor(INK_SOFT & 0xFFFFFF));
        ctx.text(font, nm, tx, y + 6, INK, false);
        String st = str(p, "state");
        String[][] facts = {
                {"PV", p.has("hp") ? num(p, "hp") + " / " + num(p, "hpMax") : "—"},
                {"État", st.isEmpty() ? "En forme" : st},
                {"Chakra", p.has("chakra") ? num(p, "chakra") + " / " + num(p, "chakraMax") : "—"},
                {"Temps de jeu", p.has("playtime") ? (num(p, "playtime") / 60) + " h " + (num(p, "playtime") % 60) : "—"},
                {"Ryo", p.has("ryo") ? String.valueOf(num(p, "ryo")) : "—"},
                {"Ping", p.has("ping") ? num(p, "ping") + " ms" : "hors ligne"}};
        int colW = (w - 64 - 60) / 2;
        for (int i = 0; i < facts.length; i++) {
            int fx = tx + (i % 2) * colW, fy = y + 18 + (i / 2) * 10;
            small(ctx, facts[i][0], fx, fy, INK_SOFT);
            small(ctx, fit(facts[i][1], colW - 50), fx + 48, fy, i == 1 && !st.isEmpty() ? LACQUER_LO : INK);
        }
        if (num(p, "score") > 0) badge(ctx, x + w - 8, y + 4, "SCORE " + num(p, "score"), true);
        int bx = x + 16, by = y + h - 16;
        bx += button(ctx, bx, by, "Aller à lui", "plain", () -> act("tp", uuid), g < 1) + 4;
        bx += button(ctx, bx, by, "Le ramener", "plain", () -> act("bring", uuid), g < 2) + 4;
        bx += button(ctx, bx, by, bool(p, "frozen") ? "Dégeler" : "Geler", "plain", () -> act("freeze", uuid), g < 2) + 4;
        bx += button(ctx, bx, by, bool(p, "watching") ? "Fin d'observation" : "Observer", "watch", () -> act("watch", uuid), g < 2) + 4;
        bx += button(ctx, bx, by, "Réveiller", "plain", () -> act("revive", uuid), g < 2) + 4;
        button(ctx, bx, by, "Lever l'ATA", "plain", () -> act("lift_ata", uuid), g < 2);
        y += h + 6;

        y = section(ctx, "Comptes liés", y);
        JsonArray alts = arr(p, "alts");
        if (alts.isEmpty()) y = text(ctx, "Aucun autre compte vu avec la même IP.", x + 2, y, INK_SOFT) + 4;
        for (JsonElement e : alts) {
            JsonObject a = e.getAsJsonObject();
            GardeArt.carte(ctx, x, y, w, 16, false);
            ctx.text(font, gras(str(a, "name")), x + 6, y + 4, INK, false);
            small(ctx, "IP partagée · vu " + str(a, "seen"), x + 10 + font.width(gras(str(a, "name"))), y + 5, INK_SOFT);
            String altId = str(a, "uuid");
            int vw = bw("Voir");
            button(ctx, x + w - vw - 3, y + 1, "Voir", "plain", () -> openProfile(altId), false);
            y += 19;
        }

        y = section(ctx, "Casier", y + 2);
        ctx.text(font, RebornFont.body("Infractions : ").append(gras(String.valueOf(num(p, "recordTotal"))).withColor(LACQUER & 0xFFFFFF))
                .append(RebornFont.body("     Actives : ")).append(gras(String.valueOf(num(p, "recordActive"))).withColor(LACQUER & 0xFFFFFF)),
                x + 2, y, INK, false);
        y += 12;
        JsonArray rec = arr(p, "record");
        if (rec.isEmpty()) y = text(ctx, "Casier vierge.", x + 2, y, INK_SOFT) + 4;
        for (JsonElement e : rec) {
            JsonObject r = e.getAsJsonObject();
            ctx.fill(x, y, x + w, y + 12, 0x40FFF8E8);
            ctx.fill(x, y + 12, x + w, y + 13, 0x30462810);
            ctx.fill(x, y, x + 2, y + 12, bool(r, "active") ? LACQUER : 0xFF7A6E5C);
            small(ctx, str(r, "when"), x + 6, y + 3, INK_SOFT);
            small(ctx, fit(str(r, "text"), w - 120), x + 62, y + 3, INK);
            small(ctx, str(r, "status"), x + w - 36, y + 3, bool(r, "active") ? LACQUER : INK_SOFT);
            y += 14;
        }

        y = section(ctx, "Infractions", y + 2);
        JsonArray offs = arr(p, "offenses");
        int cols = 3, gap = 5, cw = (w - gap * (cols - 1)) / cols, ch = 34;
        for (int i = 0; i < offs.size(); i++) {
            JsonObject o = offs.get(i).getAsJsonObject();
            int ox = x + (i % cols) * (cw + gap), oy = y + (i / cols) * (ch + gap);
            boolean locked = bool(o, "locked");
            boolean hov = hover(ox, oy, cw, ch) && !locked;
            int lift = hov ? 1 : 0;
            oy -= lift;
            GardeArt.carte(ctx, ox, oy, cw, ch, hov);
            int tone0 = locked ? 0xFF7A6E5C : switch (str(o, "tone")) { case "warn" -> 0xFF8A5A0C; case "mute" -> 0xFFB45309; default -> LACQUER; };
            ctx.fill(ox + 1, oy + 1, ox + cw - 1, oy + 3, tone0);
            ctx.fill(ox + 1, oy + 1, ox + cw - 1, oy + 2, 0x30FFFFFF);
            if (locked) ctx.fill(ox + cw - 9, oy + 6, ox + cw - 5, oy + 10, 0xFF9A8A70);
            ctx.text(font, RebornFont.body(fit(str(o, "name"), cw - 10)), ox + 5, oy + 4, INK, false);
            int s = num(o, "strikes");
            small(ctx, s == 0 ? "Aucun strike" : s + " strike" + (s > 1 ? "s" : ""), ox + 5, oy + 14, s == 0 ? INK_SOFT : LACQUER);
            String next = locked ? str(o, "next") + " · " + str(o, "lockGrade") : str(o, "next");
            int tone = locked ? 0xFF7A6E5C : switch (str(o, "tone")) { case "warn" -> 0xFF8A5A0C; case "mute" -> 0xFFB45309; default -> LACQUER; };
            int pw = Math.round(font.width(RebornFont.body(next)) * 0.75f) + 6;
            DrawHelpers.outlinedRect(ctx, ox + 5, oy + 23, pw, 9, 0, tone);
            small(ctx, next, ox + 8, oy + 25, tone);
            final JsonObject off = o;
            hits.add(new Hit(ox, oy, cw, ch, () -> {
                if (locked) { StaffClient.toast("Réservé au grade " + str(off, "lockGrade") + "."); RebornSounds.deny(); return; }
                confirm = off;
                flags.clear();
                sealAt = 0;
                noteBox.setValue("");
                RebornSounds.uiClick();
            }));
        }
        return y + ((offs.size() + cols - 1) / cols) * (ch + gap) + 4;
    }

    protected int chat(GuiGraphicsExtractor ctx, JsonObject snap, int y) {
        y = section(ctx, "Canal staff", y);
        JsonArray lines = arr(snap, "chat");
        int start = Math.max(0, lines.size() - 60);
        for (int i = start; i < lines.size(); i++) {
            JsonObject m = lines.get(i).getAsJsonObject();
            boolean sys = bool(m, "sys");
            int x = vx0(), w = vx1() - vx0();
            Component head = Component.empty()
                    .append(RebornFont.body(str(m, "time") + "  ").withColor(INK_SOFT & 0xFFFFFF))
                    .append(sys ? gras("Système  ").withColor(LACQUER & 0xFFFFFF)
                            : gras(gradeShort(num(m, "grade")) + " " + str(m, "name") + "  ").withColor(gradeColor(num(m, "grade")) & 0xFFFFFF));
            int hw = font.width(head);
            List<String> wrapped = wrap(str(m, "text"), w - hw - 12);
            int h = 4 + wrapped.size() * 10;
            ctx.fill(x, y, x + w, y + h, sys ? 0x14A0182B : 0x59FFFFFF);
            ctx.fill(x, y, x + 2, y + h, sys ? LACQUER : GOLD);
            ctx.text(font, head, x + 6, y + 2, INK, false);
            for (int k = 0; k < wrapped.size(); k++) {
                ctx.text(font, RebornFont.body(wrapped.get(k)), x + 6 + hw, y + 2 + k * 10, INK, false);
            }
            y += h + 3;
        }
        small(ctx, "En jeu, le même canal s'ouvre aussi avec # au début d'un message.", vx0(), y + 2, INK_SOFT);
        return y + 14;
    }

    protected int journal(GuiGraphicsExtractor ctx, JsonObject snap, int y) {
        int x = vx0();
        for (String[] f : LOG_FILTERS) {
            boolean sel = f[0].equals(logFilter);
            final String id = f[0];
            x += button(ctx, x, y, f[1], sel ? "punish" : "plain", () -> { logFilter = id; scroll = 0; RebornSounds.uiClick(); }, false) + 4;
        }
        y += 19;
        int count = 0;
        for (JsonElement e : arr(snap, "journal")) {
            JsonObject r = e.getAsJsonObject();
            if (!"all".equals(logFilter) && !logFilter.equals(str(r, "kind"))) continue;
            count++;
            int w = vx1() - vx0();
            int kc = kindColor(str(r, "kind"));
            ctx.fill(vx0(), y, vx0() + w, y + 14, (count % 2 == 0 ? 0x30 : 0x50) << 24 | 0xFFF8E8);
            ctx.fill(vx0(), y + 1, vx0() + 3, y + 13, kc);
            ctx.fill(vx0() + 3, y + 13, vx0() + w, y + 14, 0x2A462810);
            ctx.text(font, RebornFont.body(str(r, "time")), vx0() + 6, y + 3, INK_SOFT, false);
            Component who = gras(gradeShort(num(r, "grade")) + " " + str(r, "name")).withColor(gradeColor(num(r, "grade")) & 0xFFFFFF);
            ctx.text(font, who, vx0() + 40, y + 3, INK, false);
            ctx.text(font, gras(str(r, "type")), vx0() + 130, y + 3, kc, false);
            ctx.text(font, RebornFont.body(fit(str(r, "detail"), w - 230)), vx0() + 225, y + 3, INK, false);
            y += 16;
        }
        if (count == 0) y = text(ctx, "Aucune action de ce type pour l'instant.", vx0(), y, INK_SOFT);
        small(ctx, "Chaque ligne est gardée sur le serveur (staff-journal.yml).", vx0(), y + 4, INK_SOFT);
        return y + 16;
    }

    protected int commands(GuiGraphicsExtractor ctx, JsonObject snap, int y) {
        int g = grade(snap);
        String q = searchBox.getValue().trim().toLowerCase();
        String cat = null;
        int shown = 0;
        for (JsonElement e : arr(snap, "commands")) {
            JsonObject c = e.getAsJsonObject();
            String cmd = str(c, "cmd"), desc = str(c, "desc");
            if (!q.isEmpty() && !(cmd + " " + desc).toLowerCase().contains(q)) continue;
            if (!str(c, "cat").equals(cat)) {
                cat = str(c, "cat");
                y = section(ctx, cat, y);
            }
            shown++;
            int need = num(c, "grade");
            boolean ok = need <= g;
            int x = vx0(), w = vx1() - vx0();
            ctx.fill(x, y, x + w, y + 16, ok ? 0x48FFF8E8 : 0x1CFFF8E8);
            ctx.fill(x, y + 15, x + w, y + 16, 0x2A462810);
            ctx.fill(x, y, x + 2, y + 15, ok ? GOLD : 0xFFB8A88A);
            int cmdW = Math.round(w * 0.36f);
            ctx.text(font, RebornFont.body(fit(cmd, cmdW - 8)), x + 5, y + 4, ok ? WOOD_DARK : 0xFF9A8A70, false);
            small(ctx, fit(desc, Math.round((w - cmdW - 132) / 0.75f)), x + 5 + cmdW, y + 5, ok ? INK : 0xFF9A8A70);
            String gl = gradeName(need) + (need < 4 ? "+" : "");
            int gw = Math.round(font.width(RebornFont.body(gl)) * 0.75f) + 6;
            int gx = x + w - 76 - gw;
            DrawHelpers.outlinedRect(ctx, gx, y + 3, gw, 10, 0, gradeColor(need));
            small(ctx, gl, gx + 3, y + 5, gradeColor(need));
            if (ok) button(ctx, x + w - 68, y + 2, 64, "Préparer", "plain", () -> prepare(cmd), false);
            else button(ctx, x + w - 68, y + 2, 64, "Verrouillé", "plain", null, true);
            y += 18;
        }
        if (shown == 0) y = text(ctx, "Aucune commande ne correspond.", vx0(), y + 2, INK_SOFT);
        return y + 4;
    }

    /* =================================================================== confirmation de sanction */

    protected void confirmModal(GuiGraphicsExtractor ctx, JsonObject snap) {
        JsonObject p = StaffClient.profile();
        if (p == null) { confirm = null; return; }
        hits.clear();   // la fenêtre capte tous les clics
        ctx.fill(0, 0, width, height, 0x99060304);
        int mw = Math.min(330, width - 40), x = width / 2 - mw / 2;
        JsonArray msgs = arr(p, "msgs");
        int shownMsgs = Math.min(msgs.size(), 4);
        int mh = 132 + shownMsgs * 21 + (shownMsgs == 0 ? 12 : 0);
        int y = Math.max(10, height / 2 - mh / 2);
        GardeArt.papierNu(ctx, x, y, mw, mh);
        GardeArt.cadre(ctx, x, y, mw, mh);
        int cx = x + 10, cy = y + 11;
        String who = str(p, "perso").isEmpty() ? str(p, "name") : str(p, "perso");
        GardeArt.losange(ctx, cx, cy + 1);
        DaKit.arcX1(ctx, font, DaKit.arcade(fit("Sanctionner " + who + " ?", mw - 40)), cx + 9, cy, DaKit.ENCRE_ROUGE, 0, false);
        cy += 14;
        GardeArt.carte(ctx, cx, cy, mw - 20, 34, false);
        String[][] rows = {{"Motif", str(confirm, "name")}, {"Sanction", str(confirm, "next")},
                {"Strike", String.valueOf(num(confirm, "strikes") + 1)}};
        for (int i = 0; i < rows.length; i++) {
            small(ctx, rows[i][0], cx + 6, cy + 4 + i * 10, INK_SOFT);
            ctx.text(font, gras(rows[i][1]), cx + 60, cy + 3 + i * 10, LACQUER, false);
        }
        cy += 40;
        small(ctx, "Messages récents — marque ceux qui servent de preuve", cx, cy, INK_SOFT);
        small(ctx, "Preuves : " + flags.size(), x + mw - 60, cy, INK_SOFT);
        cy += 10;
        if (shownMsgs == 0) { small(ctx, "Aucun message récent.", cx, cy + 2, INK_SOFT); cy += 12; }
        for (int i = 0; i < shownMsgs; i++) {
            JsonObject m = msgs.get(i).getAsJsonObject();
            ctx.fill(cx, cy, x + mw - 10, cy + 19, 0x48FFF8E8);
            ctx.fill(cx, cy + 18, x + mw - 10, cy + 19, 0x2A462810);
            ctx.fill(cx, cy, cx + 2, cy + 18, flags.contains(i) ? LACQUER : GOLD);
            small(ctx, str(m, "time"), cx + 4, cy + 2, INK_SOFT);
            ctx.text(font, RebornFont.body(fit(str(m, "text"), mw - 60)), cx + 4, cy + 9, INK, false);
            boolean on = flags.contains(i);
            int fx = x + mw - 30;
            GardeArt.drapeau(ctx, fx, cy + 3, on);
            final int idx = i;
            hits.add(new Hit(fx, cy + 3, 13, 13, () -> { if (!flags.remove(idx)) flags.add(idx); RebornSounds.uiClick(); }));
            cy += 21;
        }
        cy += 2;
        small(ctx, "Note pour le casier", cx, cy, INK_SOFT);
        cy += 9;
        GardeArt.champ(ctx, cx, cy - 1, mw - 20);
        noteBox.setX(cx + 4); noteBox.setY(cy + 3); noteBox.setWidth(mw - 30);
        cy += 20;
        if (sealAt == 0) {
            button(ctx, cx, cy, "Annuler", "plain", () -> confirm = null, false);
            int ww = bw("Avertir seulement");
            button(ctx, x + mw / 2 - ww / 2, cy, "Avertir seulement", "plain", () -> sendSanction(true), false);
            int sw = bw("Apposer le sceau");
            button(ctx, x + mw - 10 - sw, cy, "Apposer le sceau", "punish", () -> sendSanction(false), false);
        } else {
            seal(ctx, x + mw - 70, y + 14);
            if (System.currentTimeMillis() - sealAt > 1100) { confirm = null; sealAt = 0; }
        }
    }

    protected void seal(GuiGraphicsExtractor ctx, int x, int y) {
        float t = Math.min(1f, (System.currentTimeMillis() - sealAt) / 220f);
        float scale = 1.6f - 0.6f * t;
        ctx.pose().pushMatrix();
        ctx.pose().translate(x + 28, y + 28);
        ctx.pose().scale(scale, scale);
        GardeArt.sceau(ctx, -32, -32, DaKit.blanc(0.92f * t));
        ctx.pose().popMatrix();
        // éclaboussures d'encre au moment de l'impact
        if (t >= 1f) {
            float fade = Math.max(0f, 1f - (System.currentTimeMillis() - sealAt - 220) / 900f);
            long s = 0x9E3779B97F4A7C15L;
            for (int k = 0; k < 14; k++) {
                s = s * 6364136223846793005L + 1442695040888963407L;
                double ang = ((s >>> 40) % 360) * Math.PI / 180.0;
                int r = 30 + (int) ((s >>> 20) % 18), sz = 1 + (int) ((s >>> 50) % 3);
                int px = x + 28 + (int) (Math.cos(ang) * r), py = y + 28 + (int) (Math.sin(ang) * r);
                ctx.fill(px, py, px + sz, py + sz, (Math.round(200 * fade) << 24) | (LACQUER & 0xFFFFFF));
            }
        }
    }

    /** État vide des alertes : grand 静 (le calme) estompé. */
    protected int calm(GuiGraphicsExtractor ctx, int y) {
        int cx = (vx0() + vx1()) / 2;
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx - 24, y + 8);
        ctx.pose().scale(6f, 6f);
        ctx.text(font, Component.literal("静"), 0, 0, 0x30462810, false);
        ctx.pose().popMatrix();
        Component c = RebornFont.body("Aucune alerte. Le village est calme.");
        ctx.text(font, c, cx - font.width(c) / 2, y + 66, INK_SOFT, false);
        return y + 84;
    }

    protected void sendSanction(boolean warnOnly) {
        JsonObject p = StaffClient.profile();
        if (p == null || confirm == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("a", "sanction");
        o.addProperty("t", str(p, "uuid"));
        o.addProperty("o", str(confirm, "id"));
        o.addProperty("warnOnly", warnOnly);
        o.addProperty("note", noteBox.getValue());
        JsonArray ev = new JsonArray();
        JsonArray msgs = arr(p, "msgs");
        for (int i : flags) if (i < msgs.size()) {
            JsonObject m = msgs.get(i).getAsJsonObject();
            ev.add(str(m, "time") + " — " + str(m, "text"));
        }
        o.add("ev", ev);
        StaffClient.send(o);
        if (warnOnly) { confirm = null; RebornSounds.uiClick(); }
        else { sealAt = System.currentTimeMillis(); RebornSounds.confirm(); }
    }

    /* =================================================================== actions */

    protected void act(String op, String uuid) {
        JsonObject o = new JsonObject();
        o.addProperty("a", "act");
        o.addProperty("op", op);
        o.addProperty("t", uuid);
        StaffClient.send(o);
        RebornSounds.uiClick();
    }

    /** Verdict sur une demande de mort RP (alerte « mort »). */
    protected void killVerdict(String id, boolean ok) {
        JsonObject o = new JsonObject();
        o.addProperty("a", "kill");
        o.addProperty("id", id);
        o.addProperty("v", ok ? "ok" : "no");
        StaffClient.send(o);
        if (ok) RebornSounds.confirm(); else RebornSounds.uiClick();
    }

    protected void dismiss(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("a", "dismiss");
        o.addProperty("id", id);
        StaffClient.send(o);
        RebornSounds.uiClick();
    }

    protected void openProfile(String uuid) {
        tab = "players";
        profileUuid = uuid;
        scroll = 0;
        requestProfile(uuid);
        RebornSounds.uiClick();
    }

    protected void requestProfile(String uuid) {
        JsonObject o = new JsonObject();
        o.addProperty("a", "profile");
        o.addProperty("t", uuid);
        StaffClient.send(o);
    }

    protected void sendChat() {
        String m = chatBox.getValue().trim();
        if (m.isEmpty()) return;
        JsonObject o = new JsonObject();
        o.addProperty("a", "chat");
        o.addProperty("m", m);
        StaffClient.send(o);
        chatBox.setValue("");
        scroll = Integer.MAX_VALUE / 2;   // descend au dernier message
    }

    protected void prepare(String cmd) {
        Minecraft.getInstance().keyboardHandler.setClipboard(cmd.startsWith("#") ? "#" : cmd);
        StaffClient.toast(cmd + " · copiée, colle-la dans le chat (Ctrl+V)");
        RebornSounds.uiClick();
    }

    /* =================================================================== banc d'essai (StaffDebug) */

    void debugTab(String id) { switchTab(id); }

    void debugProfile(String uuid) { tab = "players"; profileUuid = uuid; scroll = 0; }

    void debugSeal() { if (confirm != null) sealAt = System.currentTimeMillis() - 260; }

    void debugConfirm(int offenseIndex) {
        JsonObject p = StaffClient.profile();
        if (p == null) return;
        confirm = arr(p, "offenses").get(offenseIndex).getAsJsonObject();
        flags.clear();
        flags.add(0);
        flags.add(1);
    }

    /* =================================================================== entrées */

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        if (e.button() == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (h.in(e.x(), e.y())) {
                    if (h.action() != null) h.action().run();
                    else { StaffClient.toast("Ton grade ne permet pas cette action."); RebornSounds.deny(); }
                    return true;
                }
            }
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (confirm == null) scroll = Math.max(0, scroll - (int) Math.round(vAmount * 16));
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        int k = e.key();
        if (k == GLFW.GLFW_KEY_ESCAPE) {
            if (confirm != null) { confirm = null; return true; }
            onClose();
            return true;
        }
        if ((k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) && chatBox.isVisible() && chatBox.isFocused()) {
            sendChat();
            return true;
        }
        return super.keyPressed(e);
    }

    /* =================================================================== petits dessins */

    /** Titre de section : losange d'or, titre ArcadePix à l'encre rouge, trait de pinceau vermillon qui s'effile. */
    protected int section(GuiGraphicsExtractor ctx, String title, int y) {
        Component c = DaKit.arcade(title);
        int x = vx0();
        GardeArt.losange(ctx, x, y + 3);
        int tw = DaKit.arcX1(ctx, font, c, x + 9, y + 2, DaKit.ENCRE_ROUGE, 0, false);
        int len = Math.max(10, Math.min(240, (vx1() - x - tw - 18) * 2 / 3));
        GardeArt.trait(ctx, x + tw + 16, y + 3, len);
        return y + 16;
    }

    /** Glissement d'entrée d'une plaquette (décalé par carte), en px. */
    protected int slide() {
        float t = (System.currentTimeMillis() - viewAt - cardIndex++ * 45L) / 260f;
        return Math.round((1f - ease(t)) * 26f);
    }

    /**
     * Plaquette de hinoki (fuda, texture) : coins gauches biseautés, ficelle rouge passée dans le trou, liseré de
     * couleur selon le type. Légèrement soulevée et cerclée d'or au survol.
     */
    protected void fuda(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int accent) {
        boolean hov = confirm == null && hover(x, y, w, h);
        int lift = hov ? 1 : 0;
        y -= lift;
        GardeArt.fuda(ctx, x, y, w, h, hov);
        GardeArt.ficelle(ctx, x + 3, y + h / 2);
        ctx.fill(x + 9, y + 3, x + 11, y + h - 3, accent);
        ctx.fill(x + 9, y + 3, x + 10, y + h - 3, 0x30FFFFFF);
    }

    /** Score en sceau laqué (rouge si grave, ambre sinon), calé à droite en {@code right}. */
    protected void badge(GuiGraphicsExtractor ctx, int right, int y, String label, boolean strong) {
        Component c = DaKit.arcade(label);
        int w = (int) Math.ceil(DaKit.largeurFin(font, c)) + 12;
        GardeArt.sceauScore(ctx, right - w, y, w, strong);
        DaKit.arcFin(ctx, font, c, right - w / 2f, y + 4.75f, IVORY, 1, true);
    }

    /** Largeur d'un bouton pour ce libellé. */
    protected int bw(String label) {
        return (int) Math.ceil(DaKit.largeurFin(font, DaKit.arcade(label))) + 14;
    }

    /** Bouton-plaque laqué (texture) : noir, rouge (« punish »), vert (« watch »), éteint ; renvoie sa largeur. */
    protected int button(GuiGraphicsExtractor ctx, int x, int y, String label, String style, Runnable action, boolean locked) {
        return button(ctx, x, y, bw(label), label, style, action, locked);
    }

    /** Bouton de largeur imposée (libellé centré). */
    protected int button(GuiGraphicsExtractor ctx, int x, int y, int w, String label, String style, Runnable action, boolean locked) {
        int h = 12;
        boolean hov = !locked && hover(x, y, w, h);
        int ligne = locked ? GardeArt.B_INACTIF : switch (style) {
            case "punish" -> GardeArt.B_ROUGE;
            case "watch" -> GardeArt.B_VERT;
            default -> GardeArt.B_NOIR;
        };
        GardeArt.bouton(ctx, x, y, w, ligne, hov);
        int color = locked ? 0xFF9A8A70 : hov ? DaKit.OR_CLAIR : IVORY;
        DaKit.arcFin(ctx, font, DaKit.arcade(label), x + w / 2f - (locked ? 2 : 0), y + 4.25f, color, 1, !locked);
        if (locked) ctx.fill(x + w - 6, y + 4, x + w - 4, y + 7, 0xFF9A8A70);   // petit cadenas
        if (visible(y, h) || confirm != null) hits.add(new Hit(x, y, w, h, locked ? null : () -> {
            if (action != null) action.run();
        }));
        return w;
    }

    /** Nom ou valeur en Inter gras (comme la fiche). */
    protected static MutableComponent gras(String s) { return DaKit.interGras(s); }

    protected void head(GuiGraphicsExtractor ctx, String uuid, int x, int y, int size) {
        ctx.fill(x - 2, y - 2, x + size + 2, y + size + 2, WOOD_DARK);
        ctx.fill(x - 1, y - 1, x + size + 1, y + size + 1, GOLD);
        Identifier skin = null;
        try {
            var conn = Minecraft.getInstance().getConnection();
            PlayerInfo info = conn != null ? conn.getPlayerInfo(UUID.fromString(uuid)) : null;
            if (info != null) skin = info.getSkin().body().texturePath();
        } catch (RuntimeException ignored) { }
        if (skin == null) { ctx.fill(x, y, x + size, y + size, 0xFF3A2C14); return; }
        ctx.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 8f, 8f, size, size, 8, 8, 64, 64);
        ctx.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 40f, 8f, size, size, 8, 8, 64, 64);
    }

    protected int text(GuiGraphicsExtractor ctx, String s, int x, int y, int color) {
        ctx.text(font, RebornFont.body(s), x, y, color, false);
        return y + 11;
    }

    protected void small(GuiGraphicsExtractor ctx, String s, int x, int y, int color) {
        DaKit.texteEchelle(ctx, font, RebornFont.body(s), x, y, color, 0.75f);
    }

    protected boolean hover(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** Le bouton est-il dans la zone visible du papier (pas masqué par le défilement) ? */
    protected boolean visible(int y, int h) {
        return y >= vy0() - 1 && y + h <= vy1() + 1;
    }

    protected String fit(String s, int maxW) {
        if (s == null) return "";
        if (font.width(RebornFont.body(s)) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && font.width(RebornFont.body(t + "…")) > maxW) t = t.substring(0, t.length() - 1);
        return t + "…";
    }

    protected List<String> wrap(String s, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (font.width(RebornFont.body(next)) > maxW && !line.isEmpty()) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (!line.isEmpty()) out.add(line.toString());
        if (out.isEmpty()) out.add("");
        return out;
    }

    /* =================================================================== JSON + grades */

    protected static String str(JsonObject o, String k) {
        return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    protected static int num(JsonObject o, String k) {
        try { return o != null && o.has(k) ? o.get(k).getAsInt() : 0; } catch (RuntimeException e) { return 0; }
    }

    protected static boolean bool(JsonObject o, String k) {
        try { return o != null && o.has(k) && o.get(k).getAsBoolean(); } catch (RuntimeException e) { return false; }
    }

    protected static JsonArray arr(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray();
    }

    protected static String join(JsonArray a) {
        List<String> parts = new ArrayList<>();
        for (JsonElement e : a) parts.add(e.getAsString());
        return String.join(" · ", parts);
    }

    protected static String name(JsonObject p) {
        return str(p, "perso").isEmpty() ? str(p, "name") : str(p, "perso");
    }

    protected static int grade(JsonObject snap) { return num(snap, "grade"); }

    protected static String gradeName(int g) {
        return switch (g) { case 1 -> "Helper"; case 2 -> "Modo"; case 3 -> "Admin"; case 4 -> "Owner"; default -> "Joueur"; };
    }

    protected static String gradeShort(int g) { return "[" + gradeName(g) + "]"; }

    protected static int gradeColor(int g) {
        return switch (g) { case 1 -> 0xFF2F6A7A; case 2 -> 0xFF8A5A0C; case 3 -> LACQUER; case 4 -> 0xFF3B2A12; default -> INK_SOFT; };
    }

    protected static int kindColor(String kind) {
        return switch (kind) {
            case "agression" -> LACQUER;
            case "aide" -> 0xFFD97706;
            case "mort" -> 0xFF5A0A12;
            case "yeux" -> 0xFF7A1322;
            case "vol" -> 0xFF8A5A0C;
            case "signalement" -> 0xFF6B4FA0;
            case "soin" -> OK;
            case "ko" -> 0xFF6B4FA0;
            case "sanction" -> LACQUER;
            case "tp" -> 0xFF2F6A7A;
            case "monde" -> 0xFF8A5A0C;
            default -> INK_SOFT;
        };
    }
}
