package fr.reborn.hud.animation;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu d'actions Reborn — DA « mur de dojo » : linteau doré, onglets laqués
 * ({@code ANIMATIONS} / {@code OPTIONS}) et rangées en plaques nominatives de bois
 * suspendues (curseur ► vermillon au survol). Le monde reste visible ; aperçu du perso
 * à droite. Remplace l'ancien {@code WalkStyleScreen}.
 *
 * <p>Contenu actuel :
 * <ul>
 *   <li><b>OPTIONS</b> → Démarche (sous-liste des styles de marche, fonctionnel),
 *       Charge chakra / Méditation / Posture combat (placeholders — mécaniques
 *       plugin à venir).</li>
 *   <li><b>ANIMATIONS</b> → Favoris / Bind / Liste d'émotes (placeholders — la
 *       liste d'émotes façon Reborn viendra ; émotes via Emotecraft touche B en
 *       attendant).</li>
 * </ul>
 */
public class AnimationMenuScreen extends Screen {

    private final Screen parent;

    private enum Section { ANIMATIONS, OPTIONS }
    private enum Sub { NONE, WALK }

    private Section section = Section.OPTIONS;
    private Sub sub = Sub.NONE;

    /** Fenêtre de défilement de la liste d'emotes (onglet ANIMATIONS). */
    private int emoteScroll = 0;
    private static final int MAX_EMOTE_ROWS = 10;

    private static final int PANEL_X = 28;
    private static final int PANEL_W = 244;
    private static final int TAB_H = 26;
    private static final int ROW_H = 24;
    private static final int HEADER_H = 16;

    /** Une rangée : label + marqueur optionnel (●/○) + action + style placeholder. */
    private record Row(String label, String marker, boolean placeholder, Runnable action) {}

    public AnimationMenuScreen(Screen parent) {
        super(Component.literal("Menu Reborn"));
        this.parent = parent;
    }

    /** Ouvre le menu directement sur l'onglet ANIMATIONS (liste d'emotes). */
    public static AnimationMenuScreen emotesTab(Screen parent) {
        AnimationMenuScreen s = new AnimationMenuScreen(parent);
        s.section = Section.ANIMATIONS;
        return s;
    }

    /** Envoie une commande serveur (sans slash) puis referme le menu. */
    private void send(String command) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) conn.sendCommand(command);
        onClose();
    }

    // ─── Géométrie ───
    private int rowCount() { return rows().size(); }
    private int panelH() { return HEADER_H + TAB_H + rowCount() * ROW_H + 12; }
    private int panelY() { return Math.max(24, (this.height - panelH()) / 2); }
    private int tabsY() { return panelY() + HEADER_H; }
    private int firstRowY() { return tabsY() + TAB_H + 6; }

    private List<Row> rows() {
        List<Row> r = new ArrayList<>();
        if (section == Section.OPTIONS) {
            if (sub == Sub.WALK) {
                r.add(new Row("‹ Retour", null, false, () -> { sub = Sub.NONE; }));
                MovementAnimations anim = MovementAnimations.INSTANCE;
                for (int i = 0; i < anim.walkStyleCount(); i++) {
                    final int idx = i;
                    boolean selected = anim.selectedWalk() == i;
                    r.add(new Row(anim.walkStyleName(i), selected ? "●" : "○", false,
                        () -> anim.setWalkStyle(idx)));
                }
            } else {
                r.add(new Row("Démarche", "▸", false, () -> { sub = Sub.WALK; }));
                r.add(new Row("Charge chakra", null, true, () -> {}));
                r.add(new Row("Méditation", null, true, () -> {}));
                r.add(new Row("Posture combat", null, true, () -> {}));
            }
        } else { // ANIMATIONS — liste d'emotes EmoteCraft, jouées via /playemote.
            List<String> emotes = fr.reborn.hud.emote.EmoteAnimations.INSTANCE.names();
            if (emotes.isEmpty()) {
                r.add(new Row("Aucune emote chargée (EmoteCraft)", null, true, () -> {}));
                return r;
            }
            r.add(new Row("Arrêter l'emote", "■", false, () -> send("stopemote")));
            int total = emotes.size();
            int max = Math.max(0, total - MAX_EMOTE_ROWS);
            emoteScroll = Math.max(0, Math.min(emoteScroll, max));
            if (emoteScroll > 0) {
                r.add(new Row("▲ Précédentes", null, false,
                    () -> emoteScroll = Math.max(0, emoteScroll - MAX_EMOTE_ROWS)));
            }
            int end = Math.min(total, emoteScroll + MAX_EMOTE_ROWS);
            for (int i = emoteScroll; i < end; i++) {
                final String name = emotes.get(i);
                r.add(new Row(name, null, false, () -> send("playemote " + name)));
            }
            if (end < total) {
                r.add(new Row("▼ Suivantes (" + (total - end) + ")", null, false,
                    () -> emoteScroll = Math.min(max, emoteScroll + MAX_EMOTE_ROWS)));
            }
        }
        return r;
    }

    // ─── DA Reborn : mur de dojo, plaques nominatives suspendues (nafudakake) ───
    private static final int WOOD = 0xE6462C1A, WOOD_LINE = 0xE63A2414, BEAM = 0xFF28180E, GOLD = 0xFFF6CC78,
        GOLD_D = 0xFFAA8034, CREAM = 0xFFFAEED6, PLATE = 0xFFCEB488, PLATE_HI = 0xFFECD6AA, PLATE_OFF = 0xFFA89474,
        PLATE_EDGE = 0xFF6E4A28, INK = 0xFF46280F, INK_RED = 0xFF8C1C20, INK_MUTED = 0xFF7A6448;
    private int hoveredRow = -1;

    /** Le monde reste visible : simple voile, plus dense à gauche derrière le mur de dojo. */
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0x3008040C);
        DrawHelpers.horizontalGradient(ctx, 0, 0, PANEL_X + PANEL_W + 80, this.height, 0x80080408, 0x00000000);
        int py = panelY(), ph = panelH();
        ctx.fill(PANEL_X + 2, py + 3, PANEL_X + PANEL_W + 2, py + ph + 3, 0x70000000);
        ctx.fill(PANEL_X, py, PANEL_X + PANEL_W, py + ph, WOOD);
        for (int x = PANEL_X + 6; x < PANEL_X + PANEL_W; x += 6) ctx.fill(x, py + 2, x + 1, py + ph - 2, WOOD_LINE);
        outline(ctx, PANEL_X, py, PANEL_W, ph, 0xFF1E120A);
        ctx.fill(PANEL_X - 6, py - 8, PANEL_X + PANEL_W + 6, py + 4, BEAM);
        outline(ctx, PANEL_X - 6, py - 8, PANEL_W + 12, 12, GOLD_D);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        int py = panelY();
        Component title = RebornFont.arcade("MENU REBORN");
        ctx.text(this.font, title, PANEL_X + (PANEL_W - this.font.width(title)) / 2, py - 6, GOLD, false);

        int tabW = (PANEL_W - 24) / 2;
        drawTab(ctx, PANEL_X + 8, tabsY(), tabW, "ANIMATIONS", section == Section.ANIMATIONS, mouseX, mouseY);
        drawTab(ctx, PANEL_X + 16 + tabW, tabsY(), tabW, "OPTIONS", section == Section.OPTIONS, mouseX, mouseY);

        List<Row> rows = rows();
        int y = firstRowY();
        int hov = -1;
        for (int i = 0; i < rows.size(); i++) {
            boolean hovered = mouseX >= PANEL_X && mouseX < PANEL_X + PANEL_W && mouseY >= y && mouseY < y + ROW_H;
            if (hovered) hov = i;
            renderRow(ctx, rows.get(i), y, hovered);
            y += ROW_H;
        }
        if (hov != hoveredRow) {
            hoveredRow = hov;
            if (hov >= 0) fr.reborn.hud.menu.RebornSounds.playReborn("sacoche.hover", 1.1f, 0.2f);
        }

        // Aperçu du perso à droite (de face, suit la souris).
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && this.width > PANEL_X + PANEL_W + 160) {
            int x0 = PANEL_X + PANEL_W + 70, x1 = Math.min(this.width - 40, x0 + 180);
            int y0 = this.height / 2 - 100, y1 = this.height / 2 + 100;
            net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(ctx,
                x0, y0, x1, y1, 70, 0.0f, mouseX, (y0 + y1) / 2f, mc.player);
        }
        hint(ctx, "CLIC : CHOISIR     MOLETTE : DEFILER     ECHAP : FERMER");
    }

    private void drawTab(GuiGraphicsExtractor ctx, int x, int y, int w, String label, boolean active, int mx, int my) {
        boolean hovered = mx >= x && mx < x + w && my >= y && my < y + TAB_H - 6;
        ctx.fill(x + 1, y + 2, x + w + 1, y + TAB_H - 4, 0x70000000);
        ctx.fill(x, y, x + w, y + TAB_H - 6, active ? 0xFFAA1E22 : (hovered ? 0xFF4A2830 : 0xFF3C1E24));
        outline(ctx, x, y, w, TAB_H - 6, active ? GOLD : 0xFF6E4646);
        Component t = RebornFont.arcade(label);
        ctx.text(this.font, t, x + (w - this.font.width(t)) / 2, y + (TAB_H - 6 - 8) / 2,
            active ? CREAM : 0xFFC8B4A0, false);
    }

    /** Plaque nominative en bois clair suspendue à deux clous dorés. */
    private void renderRow(GuiGraphicsExtractor ctx, Row row, int y, boolean hovered) {
        int x0 = PANEL_X + 12, x1 = PANEL_X + PANEL_W - 12, top = y + 2, bot = y + ROW_H - 2;
        boolean live = !row.placeholder();
        ctx.fill(x0 + 18, top - 1, x0 + 20, top, GOLD); ctx.fill(x1 - 20, top - 1, x1 - 18, top, GOLD);
        ctx.fill(x0, top, x1, bot, !live ? PLATE_OFF : hovered ? PLATE_HI : PLATE);
        outline(ctx, x0, top, x1 - x0, bot - top, hovered && live ? GOLD : PLATE_EDGE);
        if (hovered && live) drawArrow(ctx, x0 + 5, y + (ROW_H - 8) / 2, INK_RED);
        int color = !live ? INK_MUTED : (hovered ? INK_RED : INK);
        ctx.text(this.font, RebornFont.arcade(row.label()), x0 + 16, y + (ROW_H - 8) / 2, color, false);
        if (row.marker() != null) {
            Component m = RebornFont.arcade(row.marker());
            int mw = this.font.width(m);
            ctx.text(this.font, m, x1 - 8 - mw, y + (ROW_H - 8) / 2, "●".equals(row.marker()) ? INK_RED : INK_MUTED, false);
        }
    }

    private void drawArrow(GuiGraphicsExtractor ctx, int x, int y, int color) {
        for (int i = 0; i < 8; i++) {
            int half = Math.min(i, 7 - i);
            ctx.fill(x, y + i, x + half + 1, y + i + 1, color);
        }
    }

    private void hint(GuiGraphicsExtractor ctx, String s) {
        Component c = RebornFont.arcade(s);
        int w = Math.round(this.font.width(c) * 0.75f) + 24, x = (this.width - w) / 2, y = this.height - 16;
        ctx.fill(x, y, x + w, y + 12, 0xF0EADCB6);
        outline(ctx, x, y, w, 12, 0xFF8C6E48);
        ctx.pose().pushMatrix();
        ctx.pose().translate(x + 12, y + 3);
        ctx.pose().scale(0.75f, 0.75f);
        ctx.text(this.font, c, 0, 0, 0xFF503C28, false);
        ctx.pose().popMatrix();
    }

    private static void outline(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int c) {
        ctx.fill(x, y, x + w, y + 1, c); ctx.fill(x, y + h - 1, x + w, y + h, c);
        ctx.fill(x, y, x + 1, y + h, c); ctx.fill(x + w - 1, y, x + w, y + h, c);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y(); int button = event.button();
        if (button == 0) {
            // Onglets.
            int tabW = (PANEL_W - 24) / 2;
            int ty = tabsY();
            if (mouseY >= ty && mouseY < ty + TAB_H - 6 && mouseX >= PANEL_X + 8 && mouseX < PANEL_X + PANEL_W - 8) {
                Section s = mouseX < PANEL_X + 12 + tabW ? Section.ANIMATIONS : Section.OPTIONS;
                if (s != section) {
                    section = s; sub = Sub.NONE; emoteScroll = 0;
                    fr.reborn.hud.menu.RebornSounds.playReborn("sacoche.tier", 1.1f, 0.4f);
                }
                return true;
            }
            // Rangées.
            List<Row> rows = rows();
            int y = firstRowY();
            for (Row row : rows) {
                if (mouseX >= PANEL_X && mouseX < PANEL_X + PANEL_W && mouseY >= y && mouseY < y + ROW_H) {
                    if (!row.placeholder()) {
                        fr.reborn.hud.menu.RebornSounds.playReborn("sacoche.place", 1.0f, 0.45f);
                        row.action().run();
                    }
                    return true;
                }
                y += ROW_H;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        if (section == Section.ANIMATIONS) {
            int total = fr.reborn.hud.emote.EmoteAnimations.INSTANCE.names().size();
            int max = Math.max(0, total - MAX_EMOTE_ROWS);
            if (vAmount < 0) emoteScroll = Math.min(max, emoteScroll + 1);
            else if (vAmount > 0) emoteScroll = Math.max(0, emoteScroll - 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
