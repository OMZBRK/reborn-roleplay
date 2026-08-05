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
 * Menu d'actions Reborn façon GTA-RP / Zenkai — panneau-liste à gauche avec
 * onglets ({@code ANIMATIONS} / {@code OPTIONS}) et des rangées sélectionnables
 * (curseur ► + surlignage crimson au survol). Remplace l'ancien
 * {@code WalkStyleScreen}.
 *
 * <p>Contenu actuel :
 * <ul>
 *   <li><b>OPTIONS</b> → Démarche (sous-liste des styles de marche, fonctionnel),
 *       Charge chakra / Méditation / Posture combat (placeholders — mécaniques
 *       plugin à venir).</li>
 *   <li><b>ANIMATIONS</b> → Favoris / Bind / Liste d'émotes (placeholders — la
 *       liste d'émotes façon Zenkai viendra ; émotes via Emotecraft touche B en
 *       attendant).</li>
 * </ul>
 */
public class AnimationMenuScreen extends Screen {

    private final Screen parent;

    private enum Section { ANIMATIONS, OPTIONS }
    private enum Sub { NONE, WALK }

    private Section section = Section.OPTIONS;
    private Sub sub = Sub.NONE;

    private static final int PANEL_X = 28;
    private static final int PANEL_W = 244;
    private static final int TAB_H = 26;
    private static final int ROW_H = 24;
    private static final int HEADER_H = 34;

    /** Une rangée : label + marqueur optionnel (●/○) + action + style placeholder. */
    private record Row(String label, String marker, boolean placeholder, Runnable action) {}

    public AnimationMenuScreen(Screen parent) {
        super(Component.literal("Menu Reborn"));
        this.parent = parent;
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
        } else { // ANIMATIONS
            r.add(new Row("Favoris", null, true, () -> {}));
            r.add(new Row("Bind", null, true, () -> {}));
            r.add(new Row("Liste d'émotes (touche B)", null, true, () -> {}));
        }
        return r;
    }

    @Override
    public void renderBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, Colors.BACKDROP_60);
        int py = panelY();
        DrawHelpers.roundedOutlinedRect(ctx, PANEL_X, py, PANEL_W, panelH(), 10,
            Colors.BACKDROP_85, Colors.BORDER_STRONG);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        int py = panelY();

        // Titre.
        ctx.text(this.font, RebornFont.bold("REBORN"),
            PANEL_X + 14, py + 12, Colors.WHITE_PURE, false);

        // Onglets ANIMATIONS | OPTIONS.
        int tabW = PANEL_W / 2;
        drawTab(ctx, PANEL_X, tabsY(), tabW, "ANIMATIONS", section == Section.ANIMATIONS, mouseX, mouseY);
        drawTab(ctx, PANEL_X + tabW, tabsY(), tabW, "OPTIONS", section == Section.OPTIONS, mouseX, mouseY);

        // Rangées.
        List<Row> rows = rows();
        int y = firstRowY();
        for (Row row : rows) {
            boolean hovered = mouseX >= PANEL_X && mouseX < PANEL_X + PANEL_W
                && mouseY >= y && mouseY < y + ROW_H;
            renderRow(ctx, row, y, hovered);
            y += ROW_H;
        }
    }

    private void drawTab(GuiGraphicsExtractor ctx, int x, int y, int w, String label, boolean active, int mx, int my) {
        boolean hovered = mx >= x && mx < x + w && my >= y && my < y + TAB_H;
        int bg = active ? Colors.ACCENT_SOFT : (hovered ? Colors.SURFACE_ELEVATED : Colors.SURFACE);
        ctx.fill(x + 2, y + 2, x + w - 2, y + TAB_H - 2, bg);
        if (active) {
            ctx.fill(x + 2, y + TAB_H - 3, x + w - 2, y + TAB_H - 1, Colors.ACCENT);
        }
        Component t = RebornFont.arcade(label);
        int tw = this.font.width(t);
        ctx.text(this.font, t, x + (w - tw) / 2, y + (TAB_H - 8) / 2,
            active ? Colors.WHITE_PURE : Colors.FOREGROUND_SUBTLE, false);
    }

    private void renderRow(GuiGraphicsExtractor ctx, Row row, int y, boolean hovered) {
        if (hovered && !row.placeholder()) {
            ctx.fill(PANEL_X + 6, y + 2, PANEL_X + PANEL_W - 6, y + ROW_H - 2,
                Colors.withAlpha(Colors.ACCENT, 0.18f));
            // Curseur ►.
            drawArrow(ctx, PANEL_X + 12, y + (ROW_H - 8) / 2, Colors.ACCENT_HOVER);
        }
        int color = row.placeholder() ? Colors.FOREGROUND_MUTED
            : (hovered ? Colors.WHITE_PURE : Colors.FOREGROUND_SUBTLE);
        ctx.text(this.font, RebornFont.arcade(row.label()),
            PANEL_X + 26, y + (ROW_H - 8) / 2, color, false);

        if (row.marker() != null) {
            Component m = RebornFont.arcade(row.marker());
            int mw = this.font.width(m);
            ctx.text(this.font, m, PANEL_X + PANEL_W - 18 - mw, y + (ROW_H - 8) / 2,
                "●".equals(row.marker()) ? Colors.ACCENT_HOVER : Colors.FOREGROUND_MUTED, false);
        }
    }

    private void drawArrow(GuiGraphicsExtractor ctx, int x, int y, int color) {
        for (int i = 0; i < 8; i++) {
            int half = Math.min(i, 7 - i);
            ctx.fill(x, y + i, x + half + 1, y + i + 1, color);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Onglets.
            int tabW = PANEL_W / 2;
            int ty = tabsY();
            if (mouseY >= ty && mouseY < ty + TAB_H && mouseX >= PANEL_X && mouseX < PANEL_X + PANEL_W) {
                Section s = mouseX < PANEL_X + tabW ? Section.ANIMATIONS : Section.OPTIONS;
                if (s != section) { section = s; sub = Sub.NONE; }
                return true;
            }
            // Rangées.
            List<Row> rows = rows();
            int y = firstRowY();
            for (Row row : rows) {
                if (mouseX >= PANEL_X && mouseX < PANEL_X + PANEL_W && mouseY >= y && mouseY < y + ROW_H) {
                    if (!row.placeholder()) row.action().run();
                    return true;
                }
                y += ROW_H;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        Minecraft.getInstance().setScreen(parent);
    }
}
