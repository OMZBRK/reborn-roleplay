package fr.reborn.hud.map;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.RebornSounds;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * <b>Carte du monde</b> (touche M) — texture pixel-art du lieu sur fond parchemin,
 * à la façon d'une « carte du hub » : glisser pour déplacer, molette pour zoomer,
 * libellés de zones, marqueurs losange pour les lieux / PNJ / portes, flèche pour
 * le joueur (sa propre position seulement — PLAN : aucune mini-map qui révèle
 * les autres joueurs).
 *
 * <p>Clic sur un marqueur = le sélectionner ; le staff ({@code canTp}) voit alors
 * « Y ALLER », qui envoie {@code tp:<id>} — le serveur revérifie la permission.
 */
public class WorldMapScreen extends Screen {

    private static final int PAPER = 0xFFECE0BE;
    private static final int PAPER_SPOT = 0xFFF4EBD2;
    private static final int BAR = 0xF0231A16;
    private static final int BAR_LINE = 0xFF6B5638;
    private static final int INK = 0xFFF5E9D0;
    private static final int INK_DIM = 0xFFB9A98A;
    private static final int OUTLINE = 0xFF2B2018;

    private static final int BAR_TOP = 22, BAR_BOTTOM = 20;
    private static final float MAX_ZOOM_FACTOR = 8f, MIN_ZOOM_FACTOR = 0.75f;

    private MapData.Snapshot snap;
    private MapDefs.Def def;
    private int seenVersion = -1;

    // Vue : pixel texture (u, v) → écran (offX + u * zoom, offY + v * zoom).
    private float zoom = -1, offX, offY, fitZoom = 1;
    private int vx0, vy0, vx1, vy1;

    private boolean pressed, dragging;
    private double pressX, pressY;
    private String selected, hovered;
    private int goX, goY, goW, goH; // bouton « Y ALLER » (w = 0 → absent)

    public WorldMapScreen() {
        super(Component.literal("Carte du monde"));
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(false);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Nouveau push serveur (lieux modifiés, autre carte) : resynchronise sans perdre la vue. */
    public void refresh() {
        seenVersion = -1;
    }

    private void resync() {
        seenVersion = MapData.version();
        String prevMap = snap == null ? null : snap.map();
        snap = MapData.get();
        def = MapDefs.get(snap.map());
        if (!snap.map().equals(prevMap)) zoom = -1;
        if (selected != null && find(selected) == null) selected = null;
    }

    /* --------------------------------------------------------------- vue */

    private void layout() {
        vx0 = 0;
        vy0 = BAR_TOP;
        vx1 = this.width;
        vy1 = this.height - BAR_BOTTOM;
        if (def == null) return;
        fitZoom = Math.min((vx1 - vx0) / (float) def.width(), (vy1 - vy0) / (float) def.height()) * 0.96f;
        if (zoom < 0) {
            zoom = fitZoom;
            float cu = def.width() / 2f, cv = def.height() / 2f;
            MapData.Snapshot s = snap;
            Minecraft mc = Minecraft.getInstance();
            if (s.here() && mc.player != null) {
                // Ouvre zoomé ×2 sur le joueur s'il est sur cette carte.
                zoom = fitZoom * 2f;
                cu = def.toPixelX(mc.player.getX());
                cv = def.toPixelZ(mc.player.getZ());
            }
            centerOn(cu, cv);
        }
        clampPan();
    }

    private void centerOn(float u, float v) {
        offX = (vx0 + vx1) / 2f - u * zoom;
        offY = (vy0 + vy1) / 2f - v * zoom;
    }

    /** Garde au moins un quart de la carte dans la vue. */
    private void clampPan() {
        float w = def.width() * zoom, h = def.height() * zoom;
        float mw = Math.min(w, (vx1 - vx0)) * 0.25f, mh = Math.min(h, (vy1 - vy0)) * 0.25f;
        offX = Math.max(vx0 + mw - w, Math.min(vx1 - mw, offX));
        offY = Math.max(vy0 + mh - h, Math.min(vy1 - mh, offY));
    }

    private float sx(double worldX) { return offX + def.toPixelX(worldX) * zoom; }

    private float sy(double worldZ) { return offY + def.toPixelZ(worldZ) * zoom; }

    private MapData.Place find(String id) {
        for (MapData.Place p : snap.places()) if (p.id().equals(id)) return p;
        return null;
    }

    /* ---------------------------------------------------------------- rendu */

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, PAPER);
        // Petites taches claires sur le parchemin (motif fixe, pas d'aléa par frame).
        for (int i = 0; i < 70; i++) {
            int x = (int) ((i * 7919L * 31) % Math.max(1, this.width));
            int y = (int) ((i * 104729L * 17) % Math.max(1, this.height));
            int s = 3 + (i % 3) * 2;
            ctx.fill(x, y, x + s, y + s, PAPER_SPOT);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (MapData.version() != seenVersion || snap == null) resync();
        Font f = this.font;
        layout();
        hovered = null;
        goW = 0;

        if (def == null) {
            Component msg = RebornFont.arcade("CARTE \"" + snap.map().toUpperCase(Locale.ROOT)
                + "\" ABSENTE DU MOD - METS A JOUR LE LAUNCHER");
            ctx.text(f, msg, (this.width - f.width(msg)) / 2, this.height / 2, OUTLINE, false);
        } else {
            float intro = intro();
            ctx.enableScissor(vx0, vy0, vx1, vy1);
            ctx.pose().pushMatrix();
            if (intro < 1f) {
                // La carte « se pose » sous les nuages : léger zoom avant depuis le centre.
                float s = 0.88f + 0.12f * easeOut(intro);
                float cx = (vx0 + vx1) / 2f, cy = (vy0 + vy1) / 2f;
                ctx.pose().translate(cx, cy);
                ctx.pose().scale(s, s);
                ctx.pose().translate(-cx, -cy);
            }
            ctx.pose().pushMatrix();
            ctx.pose().translate(offX, offY);
            ctx.pose().scale(zoom, zoom);
            ctx.blit(RenderPipelines.GUI_TEXTURED, def.texture(), 0, 0, 0f, 0f,
                def.width(), def.height(), def.width(), def.height(), def.width(), def.height());
            ctx.pose().popMatrix();

            drawZones(ctx, f);
            drawMarkers(ctx, f, mouseX, mouseY);
            drawPlayer(ctx);
            drawReticle(ctx);
            ctx.pose().popMatrix();
            ctx.disableScissor();
        }

        drawBars(ctx, f);
        drawSelection(ctx, f, mouseX, mouseY);
        if (hovered != null && !dragging) {
            MapData.Place p = find(hovered);
            if (p != null && !p.id().equals(selected)) drawTip(ctx, f, p.name(), mouseX, mouseY);
        }
        drawClouds(ctx);
    }

    /* ------------------------------------------------- apparition (nuages) */

    private final fr.reborn.hud.ui.CloudIntro clouds = new fr.reborn.hud.ui.CloudIntro(1000, 0xF4F6FA, 0xFFFFFFFF);

    private float intro() { return clouds.progress(); }

    private static float easeOut(float t) { return 1f - (1f - t) * (1f - t) * (1f - t); }

    private void drawClouds(GuiGraphicsExtractor ctx) { clouds.draw(ctx, this.width, this.height); }

    private void drawBars(GuiGraphicsExtractor ctx, Font f) {
        ctx.fill(0, 0, this.width, BAR_TOP, BAR);
        ctx.fill(0, BAR_TOP - 1, this.width, BAR_TOP, BAR_LINE);
        Component title = RebornFont.arcade("CARTE DE " + snap.title().toUpperCase(Locale.ROOT));
        drawScaled(ctx, f, title, this.width / 2f, 5, 1.4f, Colors.GOLD, true);
        if (!MapData.fromServer()) {
            Component demo = RebornFont.arcade("APERCU HORS LIGNE");
            ctx.text(f, demo, this.width - 8 - f.width(demo), 7, Colors.withAlpha(Colors.WARNING, 0.85f), false);
        }

        int by = this.height - BAR_BOTTOM;
        ctx.fill(0, by, this.width, this.height, BAR);
        ctx.fill(0, by, this.width, by + 1, BAR_LINE);
        String hint = "[GLISSER] DEPLACER    [MOLETTE] ZOOM    [CLIC] CHOISIR UN LIEU"
            + (snap.canTp() ? "    [DOUBLE-CLIC] Y ALLER" : "")
            + (snap.here() ? "    [ESPACE] MA POSITION" : "")
            + "    [M] FERMER";
        drawScaled(ctx, f, RebornFont.arcade(hint), this.width / 2f, by + 6, 0.8f, INK_DIM, false);
    }

    /** Libellés de zone : texte pixel crème cerné de sombre, taille fixe à l'écran. */
    private void drawZones(GuiGraphicsExtractor ctx, Font f) {
        for (MapData.Place p : snap.places()) {
            if (p.type() != MapData.Type.ZONE) continue;
            drawOutlined(ctx, f, RebornFont.arcade(p.name().toUpperCase(Locale.ROOT)), sx(p.x()), sy(p.z()), 1.25f);
        }
    }

    private void drawMarkers(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        boolean showNames = zoom >= fitZoom * 1.8f;
        for (MapData.Place p : snap.places()) {
            if (p.type() == MapData.Type.ZONE) continue;
            int x = Math.round(sx(p.x())), y = Math.round(sy(p.z()));
            boolean hot = Math.abs(mx - x) + Math.abs(my - y) <= 8 && my >= vy0 && my < vy1;
            if (hot) hovered = p.id();
            boolean sel = p.id().equals(selected);
            int r = sel || hot ? 7 : 5;
            diamond(ctx, x, y + 1, r + 2, 0x66000000);
            diamond(ctx, x, y, r + 2, OUTLINE);
            diamond(ctx, x, y, r, sel ? Colors.GOLD : color(p.type()));
            diamond(ctx, x, y, Math.max(1, r - 3), 0xFFFFFFFF);
            if (showNames || sel) {
                drawOutlined(ctx, f, Component.literal(p.name()), x, y + r + 5, 0.85f);
            }
        }
    }

    private static int color(MapData.Type t) {
        return switch (t) {
            case PNJ -> 0xFF3FB8AF;
            case PORTE -> Colors.ACCENT_HOVER;
            default -> 0xFF2E86C1;
        };
    }

    /** Flèche du joueur (position + orientation), seulement s'il est sur cette carte. */
    private void drawPlayer(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!snap.here() || mc.player == null) return;
        int x = Math.round(sx(mc.player.getX())), y = Math.round(sy(mc.player.getZ()));
        double yaw = Math.toRadians(mc.player.getYRot());
        int tx = x + (int) Math.round(-Math.sin(yaw) * 10), ty = y + (int) Math.round(Math.cos(yaw) * 10);
        DrawHelpers.thickLine(ctx, x, y, tx, ty, 3, OUTLINE);
        DrawHelpers.disc(ctx, x, y, 6, OUTLINE);
        DrawHelpers.disc(ctx, x, y, 4, 0xFFFFFFFF);
        DrawHelpers.disc(ctx, x, y, 2, Colors.ACCENT);
    }

    /** Petit viseur au centre, comme sur une carte de jeu. */
    private void drawReticle(GuiGraphicsExtractor ctx) {
        int cx = (vx0 + vx1) / 2, cy = (vy0 + vy1) / 2, s = 6, c = 0x99FFFFFF;
        ctx.fill(cx - s, cy - s, cx - s + 3, cy - s + 1, c);
        ctx.fill(cx - s, cy - s, cx - s + 1, cy - s + 3, c);
        ctx.fill(cx + s - 2, cy - s, cx + s + 1, cy - s + 1, c);
        ctx.fill(cx + s, cy - s, cx + s + 1, cy - s + 3, c);
        ctx.fill(cx - s, cy + s, cx - s + 3, cy + s + 1, c);
        ctx.fill(cx - s, cy + s - 2, cx - s + 1, cy + s + 1, c);
        ctx.fill(cx + s - 2, cy + s, cx + s + 1, cy + s + 1, c);
        ctx.fill(cx + s, cy + s - 2, cx + s + 1, cy + s + 1, c);
    }

    /** Fiche du lieu sélectionné + bouton « Y ALLER » pour le staff. */
    private void drawSelection(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        MapData.Place p = selected == null ? null : find(selected);
        if (p == null) return;
        int w = Math.max(150, f.width(p.name()) + 24), h = 52;
        int x = this.width - w - 10, y = this.height - BAR_BOTTOM - h - 10;
        DrawHelpers.roundedOutlinedRectFull(ctx, x, y, w, h, 5, Colors.withAlpha(0xFF1A120E, 0.92f), BAR_LINE);
        ctx.text(f, Component.literal(p.name()), x + 10, y + 8, INK, false);
        ctx.text(f, RebornFont.arcade(typeLabel(p.type()) + "  X " + p.x() + "  Z " + p.z()), x + 10, y + 20, INK_DIM, false);
        if (snap.canTp()) {
            goW = 64;
            goH = 14;
            goX = x + w - goW - 8;
            goY = y + h - goH - 6;
            boolean hot = mx >= goX && mx < goX + goW && my >= goY && my < goY + goH;
            DrawHelpers.roundedOutlinedRectFull(ctx, goX, goY, goW, goH, 3,
                hot ? Colors.ACCENT_HOVER : Colors.ACCENT, Colors.GOLD);
            Component go = RebornFont.arcade("Y ALLER");
            ctx.text(f, go, goX + (goW - f.width(go)) / 2, goY + 3, INK, false);
        } else {
            ctx.text(f, RebornFont.arcade("TP RESERVE AU STAFF"), x + 10, y + h - 14, Colors.withAlpha(INK_DIM, 0.7f), false);
        }
    }

    private static String typeLabel(MapData.Type t) {
        return switch (t) {
            case PNJ -> "PNJ";
            case PORTE -> "PORTE";
            case ZONE -> "ZONE";
            default -> "LIEU";
        };
    }

    /* ------------------------------------------------------------ entrées */

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        if (e.button() != 0) return super.mouseClicked(e, dbl);
        double mx = e.x(), my = e.y();
        if (goW > 0 && mx >= goX && mx < goX + goW && my >= goY && my < goY + goH) {
            travel(selected);
            return true;
        }
        if (hovered != null) {
            if (dbl && snap.canTp()) {
                travel(hovered);
            } else {
                selected = hovered;
                RebornSounds.uiClick();
            }
            return true;
        }
        if (my >= vy0 && my < vy1) {
            pressed = true;
            dragging = false;
            pressX = mx;
            pressY = my;
            return true;
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent e, double dx, double dy) {
        if (!pressed || def == null) return super.mouseDragged(e, dx, dy);
        if (!dragging && Math.abs(e.x() - pressX) + Math.abs(e.y() - pressY) > 3) dragging = true;
        if (dragging) {
            offX += (float) dx;
            offY += (float) dy;
            clampPan();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent e) {
        if (pressed) {
            if (!dragging) selected = null; // clic dans le vide = désélection
            pressed = false;
            dragging = false;
            return true;
        }
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (def == null || v == 0) return true;
        float old = zoom;
        float nz = (float) (zoom * Math.pow(1.2, v));
        zoom = Math.max(fitZoom * MIN_ZOOM_FACTOR, Math.min(fitZoom * MAX_ZOOM_FACTOR, nz));
        // Zoom centré sur le curseur : le pixel sous la souris ne bouge pas.
        offX = (float) (mx - (mx - offX) * zoom / old);
        offY = (float) (my - (my - offY) * zoom / old);
        clampPan();
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        int k = e.key();
        if (k == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (fr.reborn.hud.keybind.HudKeybinds.MAP != null && fr.reborn.hud.keybind.HudKeybinds.MAP.matches(e)) {
            onClose();
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        if (k == GLFW.GLFW_KEY_SPACE && def != null && snap.here() && mc.player != null) {
            centerOn(def.toPixelX(mc.player.getX()), def.toPixelZ(mc.player.getZ()));
            clampPan();
            return true;
        }
        return super.keyPressed(e);
    }

    private void travel(String id) {
        if (id == null) return;
        if (!snap.canTp() || !ClientPlayNetworking.canSend(MapPayload.ID)) {
            RebornSounds.deny();
            return;
        }
        ClientPlayNetworking.send(new MapPayload("tp:" + id));
        RebornSounds.confirm();
        onClose();
    }

    /* ------------------------------------------------------------ helpers */

    /** Losange plein de « rayon » r centré sur (cx, cy). */
    private static void diamond(GuiGraphicsExtractor ctx, int cx, int cy, int r, int color) {
        for (int i = -r; i <= r; i++) {
            int half = r - Math.abs(i);
            ctx.fill(cx - half, cy + i, cx + half + 1, cy + i + 1, color);
        }
    }

    /** Texte centré sur (cx, top) avec contour sombre 1 px (lisible sur toute la carte). */
    private void drawOutlined(GuiGraphicsExtractor ctx, Font f, Component c, float cx, float top, float scale) {
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx - f.width(c) * scale / 2f, top);
        ctx.pose().scale(scale, scale);
        for (int[] o : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {1, 1}}) {
            ctx.text(f, c, o[0], o[1], OUTLINE, false);
        }
        ctx.text(f, c, 0, 0, INK, false);
        ctx.pose().popMatrix();
    }

    private void drawScaled(GuiGraphicsExtractor ctx, Font f, Component c, float cx, float top,
                            float scale, int color, boolean shadow) {
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx - f.width(c) * scale / 2f, top);
        ctx.pose().scale(scale, scale);
        ctx.text(f, c, 0, 0, color, shadow);
        ctx.pose().popMatrix();
    }

    private void drawTip(GuiGraphicsExtractor ctx, Font f, String text, int mx, int my) {
        Component c = Component.literal(text);
        int w = f.width(c) + 10, h = 14;
        int x = Math.min(mx + 10, this.width - w - 4), y = Math.max(my - 18, BAR_TOP + 2);
        DrawHelpers.roundedOutlinedRectFull(ctx, x, y, w, h, 3, Colors.withAlpha(0xFF1A120E, 0.94f), BAR_LINE);
        ctx.text(f, c, x + 5, y + 3, INK, false);
    }
}
