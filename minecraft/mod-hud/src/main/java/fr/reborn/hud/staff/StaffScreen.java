package fr.reborn.hud.staff;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.RebornSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
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
    protected int sy0() { return 42; }
    protected int sy1() { return height - 34; }

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

    /** Planche de bois veiné, ferrures d'or aux coins, papier washi qui se déroule (makimono), rouleau en bas. */
    protected void frame(GuiGraphicsExtractor ctx) {
        int x0 = sx0(), x1 = sx1(), y0 = sy0(), y1 = sy1();
        int m = 11;
        ctx.fill(x0 - m - 2, y0 - m + 2, x1 + m + 4, y1 + m + 6, 0x70000000);              // ombre portée
        ctx.fill(x0 - m - 1, y0 - m - 1, x1 + m + 1, y1 + m + 1, WOOD_DARK);
        ctx.fillGradient(x0 - m, y0 - m, x1 + m, y1 + m, 0xFF744C2C, 0xFF4E321D);
        // veinage horizontal
        long seed = 0x2545F4914F6CDD1DL;
        for (int yy = y0 - m; yy < y1 + m; yy += 2) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int a = 0x14 + (int) ((seed >>> 59) & 0x0F);
            int sx = x0 - m + (int) ((seed >>> 33) % 60);
            ctx.fill(sx, yy, x1 + m - (int) ((seed >>> 45) % 60), yy + 1, (a << 24) | 0x1A0D06);
        }
        // biseau clair en haut / à gauche du cadre
        ctx.fill(x0 - m, y0 - m, x1 + m, y0 - m + 1, 0x40FFE0B0);
        ctx.fill(x0 - m, y0 - m, x0 - m + 1, y1 + m, 0x30FFE0B0);
        DrawHelpers.outlinedRect(ctx, x0 - 3, y0 - 3, x1 - x0 + 6, y1 - y0 + 6, 0, WOOD_DARK);
        DrawHelpers.outlinedRect(ctx, x0 - 2, y0 - 2, x1 - x0 + 4, y1 - y0 + 4, 0, GOLD_SOFT);
        kanagu(ctx, x0 - m - 1, y0 - m - 1, 1, 1);
        kanagu(ctx, x1 + m + 1, y0 - m - 1, -1, 1);
        kanagu(ctx, x0 - m - 1, y1 + m + 1, 1, -1);
        kanagu(ctx, x1 + m + 1, y1 + m + 1, -1, -1);

        // poutre laquée en tête du papier
        ctx.fillGradient(x0, y0, x1, y0 + 4, LACQUER_HI, LACQUER_LO);
        ctx.fill(x0, y0 + 4, x1, y0 + 5, GOLD_SOFT);
        // papier qui se déroule
        int yb = paperBottom();
        if (yb > y0 + 5) {
            ctx.fillGradient(x0, y0 + 5, x1, yb, WASHI, 0xFFDAC79C);
            long s = 0x5DEECE66DL;
            for (int i = 0; i < 90; i++) {
                s = s * 6364136223846793005L + 1442695040888963407L;
                int fx = x0 + (int) ((s >>> 33) % Math.max(1, x1 - x0 - 6));
                int fy = y0 + 5 + (int) ((s >>> 17) % Math.max(1, y1 - y0 - 6));
                if (fy >= yb) continue;
                ctx.fill(fx, fy, fx + 2 + (int) ((s >>> 50) % 6), fy + 1, 0x12462810);
            }
            ctx.fillGradient(x0, y0 + 5, x1, y0 + 12, 0x38000000, 0);                 // ombre sous la poutre
            DrawHelpers.horizontalGradient(ctx, x0, y0 + 5, 6, yb - y0 - 5, 0x22000000, 0);
            DrawHelpers.horizontalGradient(ctx, x1 - 6, y0 + 5, 6, yb - y0 - 5, 0, 0x22000000);
        }
        // rouleau (jiku) qui descend avec le papier, embouts dorés
        if (yb < y1 - 1) {
            ctx.fill(x0 - 4, yb, x1 + 4, yb + 5, WOOD_DARK);
            ctx.fill(x0 - 4, yb + 1, x1 + 4, yb + 2, 0xFF6A4528);
            ctx.fill(x0 - 9, yb - 1, x0 - 3, yb + 6, GOLD);
            ctx.fill(x1 + 3, yb - 1, x1 + 9, yb + 6, GOLD);
        }
    }

    /** Bas du papier pendant le déroulé d'ouverture. */
    protected int paperBottom() {
        float u = ease((System.currentTimeMillis() - openedAt) / 340f);
        return Math.round(sy0() + 5 + (sy1() - sy0() - 5) * u);
    }

    /** Ferrure d'angle (kanagu) dorée : L de 11 px avec un rivet. */
    protected void kanagu(GuiGraphicsExtractor ctx, int cx, int cy, int dx, int dy) {
        int L = 20, T = 5;
        int x0 = Math.min(cx, cx + dx * L), x1 = Math.max(cx, cx + dx * L);
        int y0 = Math.min(cy, cy + dy * L), y1 = Math.max(cy, cy + dy * L);
        int ty0 = dy > 0 ? cy : cy - T, ty1 = dy > 0 ? cy + T : cy;
        int tx0 = dx > 0 ? cx : cx - T, tx1 = dx > 0 ? cx + T : cx;
        ctx.fill(x0 - 1, ty0 - 1, x1 + 1, ty1 + 1, 0xFF5A4012);
        ctx.fill(tx0 - 1, y0 - 1, tx1 + 1, y1 + 1, 0xFF5A4012);
        ctx.fill(x0, ty0, x1, ty1, GOLD);
        ctx.fill(tx0, y0, tx1, y1, GOLD);
        ctx.fill(x0, ty0, x1, ty0 + 1, 0xFFF2D49A);                                   // reflet
        ctx.fill(tx0, y0, tx0 + 1, y1, 0xFFF2D49A);
        int rx = cx + dx * 9, ry = cy + dy * 2;                                         // rivets
        ctx.fill(rx, ry, rx + 2, ry + 2, 0xFF5A4012);
        int qx = cx + dx * 2, qy = cy + dy * 9;
        ctx.fill(qx, qy, qx + 2, qy + 2, 0xFF5A4012);
    }

    /** Plaque laquée suspendue, double filet d'or, sceau 番 (poste de garde), reflet qui passe. */
    protected void plaque(GuiGraphicsExtractor ctx, String title) {
        Component t = RebornFont.display(title);
        int tw = font.width(t);
        int pw = tw + 46, ph = 22, x = width / 2 - pw / 2, y = 6;
        ctx.fill(x + 14, 0, x + 15, y, GOLD);
        ctx.fill(x + pw - 15, 0, x + pw - 14, y, GOLD);
        ctx.fill(x + 13, y - 2, x + 16, y, GOLD);
        ctx.fill(x + pw - 16, y - 2, x + pw - 13, y, GOLD);
        ctx.fill(x + 2, y + 3, x + pw + 3, y + ph + 3, 0x66000000);
        ctx.fill(x - 1, y - 1, x + pw + 1, y + ph + 1, WOOD_DARK);
        ctx.fillGradient(x, y, x + pw, y + ph, LACQUER_HI, LACQUER_LO);
        DrawHelpers.outlinedRect(ctx, x, y, pw, ph, 0, GOLD);
        DrawHelpers.outlinedRect(ctx, x + 2, y + 2, pw - 4, ph - 4, 0, 0x70D9A95E);
        // sceau 番
        ctx.fill(x + 5, y + 4, x + 20, y + 18, 0xFF5A0E18);
        DrawHelpers.outlinedRect(ctx, x + 5, y + 4, 15, 14, 0, GOLD);
        ctx.text(font, Component.literal("番"), x + 8, y + 7, IVORY, false);
        ctx.text(font, t, x + 27, y + 7, IVORY, true);
        // reflet doré toutes les 7 s
        float ph2 = (System.currentTimeMillis() % 7000L) / 1100f;
        if (ph2 < 1f) {
            int bx = x + Math.round(ph2 * (pw + 24)) - 12;
            ctx.enableScissor(x + 1, y + 1, x + pw - 1, y + ph - 1);
            for (int k = 0; k < 6; k++) ctx.fill(bx + k, y + 1 + k * 3, bx + k + 5, y + 4 + k * 3, 0x30FFF2D0);
            ctx.disableScissor();
        }
    }

    /** Onglets en plaquettes suspendues (coins coupés), compteur d'alertes en sceau rouge. */
    /** Largeur de la colonne de navigation (dans le papier, à gauche). */
    protected int navW() { return 70; }

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

    /** Colonne de navigation compacte : groupes à sceau, entrées laquées quand elles sont actives, compteurs. */
    protected void tabs(GuiGraphicsExtractor ctx, JsonObject snap) {
        int x0 = sx0() + 3, w = navW(), y = sy0() + 10;
        int yb = paperBottom();
        if (yb > sy0() + 12) {
            ctx.enableScissor(x0 - 3, sy0() + 5, x0 + w + 4, yb);
            ctx.fill(x0 - 3, sy0() + 5, x0 + w + 2, sy1(), 0x16462810);
            ctx.fill(x0 + w + 2, sy0() + 8, x0 + w + 3, sy1() - 4, 0x50462810);
            long now = System.currentTimeMillis();
            for (String[][] group : navGroups()) {
                ctx.fill(x0 + 1, y, x0 + 11, y + 10, 0xFF5A0E18);
                DrawHelpers.outlinedRect(ctx, x0 + 1, y, 10, 10, 0, GOLD);
                ctx.text(font, Component.literal(group[0][0]), x0 + 2, y + 1, IVORY, false);
                ctx.pose().pushMatrix();
                ctx.pose().translate(x0 + 14, y + 2);
                ctx.pose().scale(0.75f, 0.75f);
                ctx.text(font, RebornFont.bold(group[0][1].toUpperCase()), 0, 0, LACQUER_LO, false);
                ctx.pose().popMatrix();
                y += 13;
                for (int i = 1; i < group.length; i++) {
                    String id = group[i][0], label = group[i][1];
                    boolean sel = id.equals(tab), hov = !sel && hover(x0, y, w, 12);
                    if (sel) {
                        chamferGradient(ctx, x0, y, w, 12, LACQUER_HI, LACQUER_LO);
                        ctx.fill(x0, y + 2, x0 + 2, y + 10, GOLD);
                    } else if (hov) {
                        chamfer(ctx, x0, y, w, 12, 0x30FFFFFF);
                    }
                    int count = tabCount(id, snap);
                    int room = w - 10 - (count > 0 ? 12 : 0);
                    ctx.text(font, RebornFont.body(fit(label, room)), x0 + 6, y + 2, sel ? IVORY : INK, false);
                    if (count > 0) {
                        String n = String.valueOf(count);
                        int bw = Math.max(9, font.width(n) + 4), bx = x0 + w - bw - 2;
                        boolean urgent = "blesses".equals(id) || "alerts".equals(id);
                        int fill = urgent && (now / 400) % 2 == 0 ? 0xFFFF4040 : LACQUER_HI;
                        chamfer(ctx, bx - 1, y + 1, bw + 2, 10, WOOD_DARK);
                        chamfer(ctx, bx, y + 2, bw, 8, fill);
                        ctx.text(font, Component.literal(n), bx + bw / 2 - font.width(n) / 2, y + 2, IVORY, false);
                    }
                    hits.add(new Hit(x0, y, w, 12, () -> switchTab(id)));
                    y += 13;
                }
                y += 7;
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

    protected void gradeBadge(GuiGraphicsExtractor ctx, int grade, String name) {
        Component g = RebornFont.bold(name);
        int gw = font.width(g) + 18, gx = sx0() - 10, gy = height - 18;
        chamfer(ctx, gx - 1, gy - 1, gw + 2, 15, WOOD_DARK);
        chamferGradient(ctx, gx, gy, gw, 13, 0xFF1F0E11, 0xFF150A0D);
        ctx.fill(gx + 5, gy + 4, gx + 10, gy + 9, gradeColor(grade) | 0xFF000000);
        ctx.text(font, g, gx + 14, gy + 3, GOLD, false);
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

    /** Planche « Fermer » aux embouts dorés. */
    protected void closeButton(GuiGraphicsExtractor ctx) {
        int w = 116, x = width / 2 - w / 2, y = height - 19;
        boolean hov = hover(x, y, w, 15);
        chamfer(ctx, x - 1, y - 1, w + 2, 17, WOOD_DARK);
        chamferGradient(ctx, x, y, w, 15, hov ? 0xFF8A603C : 0xFF6A4528, WOOD);
        ctx.fill(x - 4, y + 3, x, y + 12, GOLD);
        ctx.fill(x + w, y + 3, x + w + 4, y + 12, GOLD);
        if (hov) ctx.fill(x + 6, y + 13, x + w - 6, y + 14, GOLD);
        Component c = RebornFont.body("Fermer");
        ctx.text(font, c, x + w / 2 - font.width(c) / 2, y + 4, IVORY, false);
        hits.add(new Hit(x, y, w, 15, this::onClose));
    }

    /** Bandes de washi scellées, au-dessus du pied. */
    protected void toasts(GuiGraphicsExtractor ctx) {
        int y = height - 44;
        long now = System.currentTimeMillis();
        for (StaffClient.Toast t : StaffClient.toasts()) {
            Component c = RebornFont.body(t.text());
            int w = font.width(c) + 24, x = width / 2 - w / 2;
            int dy = Math.round((1f - ease((now - t.at()) / 180f)) * 8);
            ctx.fill(x + 2, y + dy + 2, x + w + 2, y + dy + 16, 0x66000000);
            chamfer(ctx, x - 1, y + dy - 1, w + 2, 16, WOOD_DARK);
            ctx.fillGradient(x, y + dy, x + w, y + dy + 14, WASHI, 0xFFDAC79C);
            ctx.fill(x, y + dy, x + 10, y + dy + 14, LACQUER);
            ctx.fill(x + 3, y + dy + 4, x + 7, y + dy + 10, GOLD);
            ctx.text(font, c, x + 16, y + dy + 3, INK, false);
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
            int y = sy1() - 20, x = vx0(), w = vx1() - vx0() - 60;
            ctx.fill(x - 2, y - 1, x + w, y + 14, 0x99FFFFFF);
            DrawHelpers.outlinedRect(ctx, x - 2, y - 1, w + 2, 15, 0, WOOD_DARK);
            chatBox.setX(x + 2); chatBox.setY(y + 3); chatBox.setWidth(w - 6);
            button(ctx, vx1() - 54, y - 1, "Envoyer", "punish", this::sendChat, false);
        }
        if (cmds) {
            int y = sy0() + 6, x = vx0() + 46, w = vx1() - x;
            ctx.text(font, RebornFont.body("Chercher"), vx0(), y + 3, INK_SOFT, false);
            ctx.fill(x - 2, y - 1, x + w, y + 14, 0x99FFFFFF);
            DrawHelpers.outlinedRect(ctx, x - 2, y - 1, w + 2, 15, 0, WOOD_DARK);
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
                    .append(RebornFont.bold(str(a, "rank").isEmpty() ? "" : "[" + str(a, "rank") + "] ").withColor(LACQUER & 0xFFFFFF))
                    .append(RebornFont.bold(str(a, "perso").isEmpty() ? str(a, "name") : str(a, "perso")).withColor(INK & 0xFFFFFF))
                    .append(RebornFont.body("  " + str(a, "name")).withColor(INK_SOFT & 0xFFFFFF));
            ctx.text(font, nm, tx, y + 5, INK, false);
            ctx.text(font, RebornFont.body(str(a, "motif")), tx, y + 15, LACQUER_LO, false);
            small(ctx, join(arr(a, "ctx")), tx, y + 25, INK_SOFT);
            int score = num(a, "score");
            if (score > 0) badge(ctx, x + w - 8, y + 4, "SCORE " + score, score >= 100);
            String uuid = str(a, "uuid");
            int bx = tx, by = y + h - 13;
            bx += button(ctx, bx, by, "Observer", "watch", () -> act("watch", uuid), grade(snap) < 2) + 4;
            if ("aide".equals(str(a, "kind"))) bx += button(ctx, bx, by, "Y aller", "plain", () -> act("tp", uuid), false) + 4;
            else bx += button(ctx, bx, by, "Sanctionner", "punish", () -> openProfile(uuid), false) + 4;
            String id = str(a, "id");
            int dw = font.width(RebornFont.body("Ignorer")) + 12;
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
                    .append(RebornFont.bold(str(p, "rank").isEmpty() ? "" : "[" + str(p, "rank") + "] ").withColor(LACQUER & 0xFFFFFF))
                    .append(RebornFont.bold(str(p, "perso").isEmpty() ? "(sans personnage)" : str(p, "perso")).withColor(INK & 0xFFFFFF))
                    .append(RebornFont.body("  " + str(p, "name") + (str(p, "village").isEmpty() ? "" : " · " + str(p, "village")))
                            .withColor(INK_SOFT & 0xFFFFFF));
            ctx.text(font, nm, x + 40, y + 5, INK, false);
            String st = str(p, "state");
            small(ctx, st.isEmpty() ? "En forme" : st, x + 40, y + 15, st.isEmpty() ? OK : LACQUER_LO);
            String uuid = str(p, "uuid");
            int fw = font.width(RebornFont.body("Fiche")) + 12;
            button(ctx, x + w - fw - 8, y + 7, "Fiche", "punish", () -> openProfile(uuid), false);
            ctx.pose().popMatrix();
            y += h + 5;
        }
        return y;
    }

    protected int profile(GuiGraphicsExtractor ctx, JsonObject snap, int y) {
        int g = grade(snap);
        button(ctx, vx0(), y, "← Joueurs", "plain", () -> { profileUuid = null; scroll = 0; StaffClient.clearProfile(); }, false);
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
                .append(RebornFont.bold(str(p, "rank").isEmpty() ? "" : "[" + str(p, "rank") + "] ").withColor(LACQUER & 0xFFFFFF))
                .append(RebornFont.bold(str(p, "perso").isEmpty() ? str(p, "name") : str(p, "perso")).withColor(INK & 0xFFFFFF))
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
            chamfer(ctx, x - 1, y - 1, w + 2, 18, WOOD_DARK);
            chamferGradient(ctx, x, y, w, 16, FUDA, FUDA_2);
            ctx.text(font, RebornFont.bold(str(a, "name")), x + 6, y + 4, INK, false);
            small(ctx, "IP partagée · vu " + str(a, "seen"), x + 10 + font.width(RebornFont.bold(str(a, "name"))), y + 5, INK_SOFT);
            String altId = str(a, "uuid");
            int vw = font.width(RebornFont.body("Voir")) + 12;
            button(ctx, x + w - vw - 3, y + 1, "Voir", "plain", () -> openProfile(altId), false);
            y += 19;
        }

        y = section(ctx, "Casier", y + 2);
        ctx.text(font, RebornFont.body("Infractions : ").append(RebornFont.bold(String.valueOf(num(p, "recordTotal"))).withColor(LACQUER & 0xFFFFFF))
                .append(RebornFont.body("     Actives : ")).append(RebornFont.bold(String.valueOf(num(p, "recordActive"))).withColor(LACQUER & 0xFFFFFF)),
                x + 2, y, INK, false);
        y += 12;
        JsonArray rec = arr(p, "record");
        if (rec.isEmpty()) y = text(ctx, "Casier vierge.", x + 2, y, INK_SOFT) + 4;
        for (JsonElement e : rec) {
            JsonObject r = e.getAsJsonObject();
            ctx.fill(x, y, x + w, y + 12, 0x59FFFFFF);
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
            ctx.fill(ox + 2, oy + 2 + lift, ox + cw + 2, oy + ch + 2 + lift, hov ? 0x662E1C11 : 0x4D2E1C11);
            chamfer(ctx, ox - 1, oy - 1, cw + 2, ch + 2, WOOD_DARK);
            chamferGradient(ctx, ox, oy, cw, ch, hov ? 0xFFF9EED8 : FUDA, FUDA_2);
            int tone0 = locked ? 0xFF7A6E5C : switch (str(o, "tone")) { case "warn" -> 0xFF8A5A0C; case "mute" -> 0xFFB45309; default -> LACQUER; };
            ctx.fill(ox + 1, oy + 1, ox + cw - 1, oy + 3, tone0);
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
                    .append(sys ? RebornFont.bold("Système  ").withColor(LACQUER & 0xFFFFFF)
                            : RebornFont.bold(gradeShort(num(m, "grade")) + " " + str(m, "name") + "  ").withColor(gradeColor(num(m, "grade")) & 0xFFFFFF));
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
            Component c = RebornFont.body(f[1]);
            int w = font.width(c) + 12;
            chamfer(ctx, x - 1, y - 1, w + 2, 15, WOOD_DARK);
            if (sel) chamferGradient(ctx, x, y, w, 13, LACQUER_HI, LACQUER_LO);
            else chamferGradient(ctx, x, y, w, 13, hover(x, y, w, 13) ? 0xFFFBF2DE : 0xFFF4EAD2, 0xFFE0CFA9);
            ctx.text(font, c, x + 6, y + 3, sel ? IVORY : INK, false);
            final String id = f[0];
            if (visible(y, 13)) hits.add(new Hit(x, y, w, 13, () -> { logFilter = id; scroll = 0; RebornSounds.uiClick(); }));
            x += w + 4;
        }
        y += 19;
        int count = 0;
        for (JsonElement e : arr(snap, "journal")) {
            JsonObject r = e.getAsJsonObject();
            if (!"all".equals(logFilter) && !logFilter.equals(str(r, "kind"))) continue;
            count++;
            int w = vx1() - vx0();
            int kc = kindColor(str(r, "kind"));
            chamferGradient(ctx, vx0(), y, w, 14, 0x66FFFFFF, 0x33FFFFFF);
            ctx.fill(vx0(), y + 1, vx0() + 3, y + 13, kc);
            ctx.fill(vx0() + 3, y + 13, vx0() + w - 1, y + 14, 0x22462810);
            ctx.text(font, RebornFont.body(str(r, "time")), vx0() + 6, y + 3, INK_SOFT, false);
            Component who = RebornFont.bold(gradeShort(num(r, "grade")) + " " + str(r, "name")).withColor(gradeColor(num(r, "grade")) & 0xFFFFFF);
            ctx.text(font, who, vx0() + 40, y + 3, INK, false);
            ctx.text(font, RebornFont.bold(str(r, "type")), vx0() + 130, y + 3, kc, false);
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
            ctx.fill(x, y, x + w, y + 16, ok ? 0x59FFFFFF : 0x26FFFFFF);
            DrawHelpers.outlinedRect(ctx, x, y, w, 16, 0, WASHI_2);
            int cmdW = Math.round(w * 0.36f);
            ctx.text(font, RebornFont.body(fit(cmd, cmdW - 8)), x + 5, y + 4, ok ? WOOD_DARK : 0xFF9A8A70, false);
            small(ctx, fit(desc, Math.round((w - cmdW - 132) / 0.75f)), x + 5 + cmdW, y + 5, ok ? INK : 0xFF9A8A70);
            String gl = gradeName(need) + (need < 4 ? "+" : "");
            int gw = Math.round(font.width(RebornFont.body(gl)) * 0.75f) + 6;
            int gx = x + w - 76 - gw;
            DrawHelpers.outlinedRect(ctx, gx, y + 3, gw, 10, 0, gradeColor(need));
            small(ctx, gl, gx + 3, y + 5, gradeColor(need));
            if (ok) button(ctx, x + w - 68, y + 1, "Préparer", "plain", () -> prepare(cmd), false);
            else button(ctx, x + w - 68, y + 1, "Verrouillé", "plain", null, true);
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
        ctx.fill(x - 8, y - 4, x + mw + 12, y + mh + 12, 0x80000000);
        ctx.fill(x - 9, y - 9, x + mw + 9, y + mh + 9, WOOD_DARK);
        ctx.fillGradient(x - 8, y - 8, x + mw + 8, y + mh + 8, 0xFF744C2C, 0xFF4E321D);
        DrawHelpers.outlinedRect(ctx, x - 2, y - 2, mw + 4, mh + 4, 0, GOLD);
        kanagu(ctx, x - 9, y - 9, 1, 1);
        kanagu(ctx, x + mw + 9, y - 9, -1, 1);
        kanagu(ctx, x - 9, y + mh + 9, 1, -1);
        kanagu(ctx, x + mw + 9, y + mh + 9, -1, -1);
        ctx.fillGradient(x, y, x + mw, y + mh, WASHI, 0xFFDAC79C);
        ctx.fillGradient(x, y, x + mw, y + 3, LACQUER_HI, LACQUER_LO);
        int cx = x + 10, cy = y + 8;
        String who = str(p, "perso").isEmpty() ? str(p, "name") : str(p, "perso");
        ctx.text(font, RebornFont.display("Sanctionner " + who + " ?"), cx, cy, LACQUER_LO, false);
        cy += 16;
        ctx.fill(cx, cy, x + mw - 10, cy + 34, 0xFFECDCB8);
        DrawHelpers.outlinedRect(ctx, cx, cy, mw - 20, 34, 0, WOOD_DARK);
        String[][] rows = {{"Motif", str(confirm, "name")}, {"Sanction", str(confirm, "next")},
                {"Strike", String.valueOf(num(confirm, "strikes") + 1)}};
        for (int i = 0; i < rows.length; i++) {
            small(ctx, rows[i][0], cx + 6, cy + 4 + i * 10, INK_SOFT);
            ctx.text(font, RebornFont.bold(rows[i][1]), cx + 60, cy + 3 + i * 10, LACQUER, false);
        }
        cy += 40;
        small(ctx, "Messages récents — marque ceux qui servent de preuve", cx, cy, INK_SOFT);
        small(ctx, "Preuves : " + flags.size(), x + mw - 60, cy, INK_SOFT);
        cy += 10;
        if (shownMsgs == 0) { small(ctx, "Aucun message récent.", cx, cy + 2, INK_SOFT); cy += 12; }
        for (int i = 0; i < shownMsgs; i++) {
            JsonObject m = msgs.get(i).getAsJsonObject();
            ctx.fill(cx, cy, x + mw - 10, cy + 19, 0x66FFFFFF);
            DrawHelpers.outlinedRect(ctx, cx, cy, mw - 20, 19, 0, WASHI_2);
            small(ctx, str(m, "time"), cx + 4, cy + 2, INK_SOFT);
            ctx.text(font, RebornFont.body(fit(str(m, "text"), mw - 60)), cx + 4, cy + 9, INK, false);
            boolean on = flags.contains(i);
            int fx = x + mw - 30;
            ctx.fill(fx, cy + 3, fx + 13, cy + 16, WOOD_DARK);
            ctx.fill(fx + 1, cy + 4, fx + 12, cy + 15, on ? LACQUER : 0xFFEFE3C6);
            ctx.text(font, Component.literal("⚑"), fx + 3, cy + 6, on ? IVORY : LACQUER, false);
            final int idx = i;
            hits.add(new Hit(fx, cy + 3, 13, 13, () -> { if (!flags.remove(idx)) flags.add(idx); RebornSounds.uiClick(); }));
            cy += 21;
        }
        cy += 2;
        small(ctx, "Note pour le casier", cx, cy, INK_SOFT);
        cy += 9;
        ctx.fill(cx, cy, x + mw - 10, cy + 14, 0x99FFFFFF);
        DrawHelpers.outlinedRect(ctx, cx, cy, mw - 20, 14, 0, WOOD_DARK);
        noteBox.setX(cx + 4); noteBox.setY(cy + 3); noteBox.setWidth(mw - 30);
        cy += 20;
        if (sealAt == 0) {
            button(ctx, cx, cy, "Annuler", "plain", () -> confirm = null, false);
            int ww = font.width(RebornFont.body("Avertir seulement")) + 12;
            button(ctx, x + mw / 2 - ww / 2, cy, "Avertir seulement", "plain", () -> sendSanction(true), false);
            int sw = font.width(RebornFont.body("Apposer le sceau")) + 12;
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
        ctx.pose().rotate((float) Math.toRadians(-12));
        ctx.pose().scale(scale, scale);
        int a = Math.round(235 * t) << 24;
        int c = a | (LACQUER & 0xFFFFFF);
        ctx.fill(-26, -26, 26, -23, c); ctx.fill(-26, 23, 26, 26, c);
        ctx.fill(-26, -26, -23, 26, c); ctx.fill(23, -26, 26, 26, c);
        ctx.fill(-23, -23, 23, 23, (Math.round(40 * t) << 24) | (LACQUER & 0xFFFFFF));
        ctx.pose().scale(3f, 3f);
        ctx.text(font, Component.literal("罰"), -4, -5, c, false);
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

    /** Titre de section : losange d'or, titre laqué, coup de pinceau qui s'efface. */
    protected int section(GuiGraphicsExtractor ctx, String title, int y) {
        Component c = RebornFont.display(title);
        int x = vx0();
        ctx.fill(x + 1, y + 5, x + 4, y + 8, GOLD);
        ctx.fill(x + 2, y + 4, x + 3, y + 9, GOLD);
        ctx.text(font, c, x + 8, y + 2, LACQUER_LO, false);
        int tw = font.width(c);
        int len = Math.max(10, (vx1() - x - tw - 14) * 2 / 3);
        DrawHelpers.horizontalGradient(ctx, x + tw + 14, y + 5, len, 2, LACQUER_LO, 0x007A1322);
        DrawHelpers.horizontalGradient(ctx, x + tw + 14, y + 8, len / 2, 1, GOLD, 0x00D9A95E);
        return y + 16;
    }

    /** Glissement d'entrée d'une plaquette (décalé par carte), en px. */
    protected int slide() {
        float t = (System.currentTimeMillis() - viewAt - cardIndex++ * 45L) / 260f;
        return Math.round((1f - ease(t)) * 26f);
    }

    /**
     * Plaquette de bois (fuda) : coins gauches coupés, veinage, ficelle rouge passée dans le trou, liseré de couleur
     * selon le type. Légèrement soulevée au survol.
     */
    protected void fuda(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int accent) {
        boolean hov = confirm == null && hover(x, y, w, h);
        int lift = hov ? 1 : 0;
        y -= lift;
        ctx.fill(x + 3, y + 3 + lift, x + w + 3, y + h + 3 + lift, hov ? 0x662E1C11 : 0x4D2E1C11);
        // silhouette de l'étiquette : coins gauches biseautés
        ctx.fill(x + 5, y - 1, x + w + 1, y + h + 1, WOOD_DARK);
        ctx.fill(x - 1, y + 5, x + 5, y + h - 5, WOOD_DARK);
        for (int k = 0; k < 5; k++) ctx.fill(x + 4 - k, y + k, x + 5, y + h - k, WOOD_DARK);
        ctx.fillGradient(x + 5, y, x + w, y + h, hov ? 0xFFF7EAD0 : FUDA, FUDA_2);
        for (int k = 0; k < 5; k++) ctx.fillGradient(x + 5 - k, y + 1 + k, x + 5, y + h - 1 - k, FUDA, FUDA_2);
        ctx.fill(x, y + 5, x + 1, y + h - 5, FUDA_2);
        // veinage
        long s = (long) y * 31 + w;
        for (int k = 0; k < 4; k++) {
            s = s * 6364136223846793005L + 1442695040888963407L;
            int gy = y + 3 + (int) ((s >>> 40) % Math.max(1, h - 6));
            ctx.fill(x + 8 + (int) ((s >>> 20) % 30), gy, x + w - 4 - (int) ((s >>> 30) % 60), gy + 1, 0x10462810);
        }
        // trou + ficelle rouge
        int hy = y + h / 2;
        ctx.fill(x + 3, hy - 1, x + 6, hy + 2, WOOD_DARK);
        ctx.fill(x - 4, hy, x + 4, hy + 1, LACQUER);
        ctx.fill(x - 6, hy - 2, x - 4, hy + 3, LACQUER);
        // liseré de type
        ctx.fill(x + 9, y + 3, x + 11, y + h - 3, accent);
    }

    /** Score en sceau laqué (couleur selon la gravité). */
    protected void badge(GuiGraphicsExtractor ctx, int right, int y, String label, boolean strong) {
        Component c = RebornFont.bold(label);
        int w = font.width(c) + 10;
        int fill = strong ? LACQUER : 0xFF8A5A0C;
        chamfer(ctx, right - w - 1, y - 1, w + 2, 14, WOOD_DARK);
        chamferGradient(ctx, right - w, y, w, 12, fill, strong ? LACQUER_LO : 0xFF6A4508);
        DrawHelpers.outlinedRect(ctx, right - w + 1, y + 1, w - 2, 10, 0, 0x99D9A95E);
        ctx.text(font, c, right - w + 5, y + 2, IVORY, false);
    }

    /** Bouton laqué / papier, coins coupés, filet d'or au survol ; renvoie sa largeur. */
    protected int button(GuiGraphicsExtractor ctx, int x, int y, String label, String style, Runnable action, boolean locked) {
        Component c = RebornFont.body(label);
        int w = font.width(c) + 14, h = 12;
        boolean hov = !locked && hover(x, y, w, h);
        int top, bottom;
        switch (style) {
            case "punish" -> { top = hov ? 0xFFD8263F : LACQUER_HI; bottom = hov ? LACQUER : LACQUER_LO; }
            case "watch" -> { top = hov ? 0xFFE4F2CF : 0xFFD8EBBE; bottom = 0xFFB4CF92; }
            default -> { top = hov ? 0xFFFBF2DE : 0xFFF4EAD2; bottom = 0xFFE0CFA9; }
        }
        if (locked) { top = 0xFFDDD2BA; bottom = 0xFFCDBF9F; }
        chamfer(ctx, x, y + 1, w, h + 1, 0x802E1C11);                                   // relief
        chamfer(ctx, x - 1, y - 1, w + 2, h + 2, WOOD_DARK);
        chamferGradient(ctx, x, y, w, h, top, bottom);
        if ("punish".equals(style) && !locked) ctx.fill(x + 2, y + 1, x + w - 2, y + 2, 0x55FFD8A0);
        if (hov) ctx.fill(x + 3, y + h - 1, x + w - 3, y + h, GOLD);
        int color = "punish".equals(style) && !locked ? IVORY : locked ? 0xFF9A8A70 : INK;
        ctx.text(font, c, x + 7, y + 2, color, false);
        if (locked) ctx.fill(x + w - 5, y + 3, x + w - 3, y + 6, 0xFF9A8A70);   // petit cadenas
        if (visible(y, h) || confirm != null) hits.add(new Hit(x, y, w, h, locked ? null : () -> {
            if (action != null) action.run();
        }));
        return w;
    }

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
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(0.75f, 0.75f);
        ctx.text(font, RebornFont.body(s), 0, 0, color, false);
        ctx.pose().popMatrix();
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
