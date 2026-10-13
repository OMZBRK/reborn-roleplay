package fr.reborn.hud.map;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.ui.CloudIntro;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.UiAmbience;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * <b>Carte du monde</b> (touche M, objet carte en main) — texture pixel-art du lieu sur
 * parchemin, façon « carte du hub » : glisser (avec inertie) pour déplacer, molette pour un
 * zoom fluide centré sur le curseur, libellés de zones qui s'effacent quand on zoome,
 * marqueurs illustrés (lieu / PNJ / porte), flèche du joueur (sa propre position
 * seulement — PLAN : aucune mini-map qui révèle les autres joueurs), rose des vents,
 * échelle et coordonnées sous le curseur.
 *
 * <p>Clic sur un marqueur = le sélectionner (fiche avec distance) ; le staff
 * ({@code canTp}) voit « Y ALLER », qui envoie {@code tp:<id>} — le serveur revérifie
 * la permission — avec un fondu « encre » avant de fermer.
 *
 * <p>Sons : {@code reborn:map.*} ({@code tools/ui-art/gen_map_sfx.py}) — parchemin qui se
 * déroule / s'enroule, tic de zoom, froissement au glisser, tampon à la sélection, cloche
 * au voyage, ambiance vent + oiseaux.
 *
 * <p>Habillage au niveau de la fiche (DA §4.1, §6) : cadre du rouleau (baguettes laquées, lisières
 * de brocart), en-tête suspendu, rose des vents, cartes de washi, boutons laqués, aides en
 * cabochons — textures {@code textures/gui/carte/*} ({@code tools/gen_carte_textures.py}, cf.
 * {@link CarteArt}) ; marqueurs à l'échelle entière (le survol les soulève au lieu de les agrandir).
 */
public class WorldMapScreen extends Screen implements fr.reborn.hud.ui.EcranOpaque {

    private static final int PAPER = 0xFFECE0BE;
    private static final int INK = 0xFFF5E9D0;
    private static final int OUTLINE = 0xFF2B2018;
    private static final int GOLD = 0xFFF6CC78;
    private static final int PARCH_INK = 0xFF3C2A1C;

    private static final float MAX_ZOOM_FACTOR = 8f, MIN_ZOOM_FACTOR = 0.75f;

    private static final Identifier ARROW = tex("arrow");
    private static final Map<MapData.Type, Identifier> ICON = new HashMap<>();
    static {
        ICON.put(MapData.Type.LIEU, tex("marker_lieu"));
        ICON.put(MapData.Type.PNJ, tex("marker_pnj"));
        ICON.put(MapData.Type.PORTE, tex("marker_porte"));
    }

    private static Identifier tex(String n) {
        return Identifier.fromNamespaceAndPath("reborn", "textures/gui/map/" + n + ".png");
    }

    private MapData.Snapshot snap;
    private MapDefs.Def def;
    private int seenVersion = -1;

    // Vue : pixel texture (u, v) → écran (offX + u * zoom, offY + v * zoom).
    private float zoom = -1, offX, offY, fitZoom = 1;
    private float targetZoom = -1, zoomAnchorX, zoomAnchorY;
    private float velX, velY;
    private int vx0, vy0, vx1, vy1;
    private final long openedAt = System.currentTimeMillis();
    private long lastFrame = System.nanoTime();

    private boolean pressed, dragging;
    private double pressX, pressY, lastDragX, lastDragY;
    private long lastDragSound;
    private String selected, hovered, lastHovered;
    private long selectedAt;
    private final Map<String, Float> hoverAnim = new HashMap<>();
    private int goX, goY, goW, goH; // bouton « Y ALLER » (w = 0 → absent)
    private long travelAt = -1;
    private String travelId;
    private boolean keyLeft, keyRight, keyUp, keyDown;

    /** Banc de captures ({@code REBORN_CARTE_BANC=1}, client de dev) : sans intro, premier lieu choisi. */
    private static final boolean BANC = "1".equals(System.getenv("REBORN_CARTE_BANC"));

    private final CloudIntro clouds = new CloudIntro(BANC ? 1 : 1000, 0xF4F6FA, 0xFFFFFFFF);

    public WorldMapScreen() {
        super(Component.literal("Carte du monde"));
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
        if (System.currentTimeMillis() - openedAt < 200) {
            RebornSounds.playReborn("map.open", 1.0f, 0.6f);
            UiAmbience.start("map.ambience", 0.3f);
        }
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(false);
        UiAmbience.stop();
        super.removed();
    }

    @Override
    public void onClose() {
        if (travelAt < 0) RebornSounds.playReborn("map.close", 1.0f, 0.5f);
        super.onClose();
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
        if (BANC && selected == null) {
            for (MapData.Place p : snap.places()) {
                if (p.type() != MapData.Type.ZONE) { selected = p.id(); selectedAt = System.currentTimeMillis(); break; }
            }
        }
    }

    /* --------------------------------------------------------------- vue */

    private void layout() {
        vx0 = 0;
        vy0 = 0;
        vx1 = this.width;
        vy1 = this.height;
        if (def == null) return;
        fitZoom = Math.min((vx1 - vx0) / (float) def.width(), (vy1 - vy0 - 60) / (float) def.height()) * 0.96f;
        if (zoom < 0) {
            zoom = fitZoom;
            float cu = def.width() / 2f, cv = def.height() / 2f;
            Minecraft mc = Minecraft.getInstance();
            if (snap.here() && mc.player != null) {
                // Ouvre zoomé ×2 sur le joueur s'il est sur cette carte.
                zoom = fitZoom * 2f;
                cu = def.toPixelX(mc.player.getX());
                cv = def.toPixelZ(mc.player.getZ());
            }
            targetZoom = zoom;
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

    /** Zoom fluide vers la cible (ancré sur un point écran), inertie du glisser, flèches/ZQSD. */
    private void animateView(float dt) {
        if (def == null) return;
        if (Math.abs(targetZoom - zoom) > 1e-4f) {
            float old = zoom;
            zoom += (targetZoom - zoom) * (1f - (float) Math.exp(-dt * 14f));
            if (Math.abs(targetZoom - zoom) < 1e-4f) zoom = targetZoom;
            offX = zoomAnchorX - (zoomAnchorX - offX) * zoom / old;
            offY = zoomAnchorY - (zoomAnchorY - offY) * zoom / old;
        }
        if (!pressed && (Math.abs(velX) > 1 || Math.abs(velY) > 1)) {
            offX += velX * dt;
            offY += velY * dt;
            float k = (float) Math.exp(-dt * 6f);
            velX *= k;
            velY *= k;
        }
        float kp = 240f * dt;
        if (keyLeft) offX += kp;
        if (keyRight) offX -= kp;
        if (keyUp) offY += kp;
        if (keyDown) offY -= kp;
        clampPan();
    }

    private void zoomTo(float z, float ax, float ay) {
        targetZoom = Math.max(fitZoom * MIN_ZOOM_FACTOR, Math.min(fitZoom * MAX_ZOOM_FACTOR, z));
        zoomAnchorX = ax;
        zoomAnchorY = ay;
    }

    private float sx(double worldX) { return offX + def.toPixelX(worldX) * zoom; }

    private float sy(double worldZ) { return offY + def.toPixelZ(worldZ) * zoom; }

    private double worldX(double screenX) { return def.originX() + (screenX - offX) / zoom * def.blocksPerPixel(); }

    private double worldZ(double screenY) { return def.originZ() + (screenY - offY) / zoom * def.blocksPerPixel(); }

    private MapData.Place find(String id) {
        for (MapData.Place p : snap.places()) if (p.id().equals(id)) return p;
        return null;
    }

    /* ---------------------------------------------------------------- rendu */

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, PAPER);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (MapData.version() != seenVersion || snap == null) resync();
        Font f = this.font;
        long nowNs = System.nanoTime();
        float dt = Math.min(0.1f, (nowNs - lastFrame) / 1e9f);
        lastFrame = nowNs;
        layout();
        animateView(dt);
        hovered = null;
        goW = 0;

        if (def == null) {
            CarteArt.cadre(ctx, this.width, this.height);
            DaKit.inter9(ctx, f, DaKit.inter("Carte « " + snap.map() + " » absente du mod : mets à jour le launcher."),
                this.width / 2f, this.height / 2f, DaKit.ENCRE, 1);
        } else {
            float intro = clouds.progress();
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
            drawMarkers(ctx, f, mouseX, mouseY, dt);
            drawPlayer(ctx);
            ctx.pose().popMatrix();
            CarteArt.vignette(ctx, this.width, this.height);
            CarteArt.cadre(ctx, this.width, this.height);
            drawCompass(ctx);
            drawScaleAndCursor(ctx, f, mouseX, mouseY);
        }

        drawHeader(ctx, f);
        drawHints(ctx, f);
        drawSelection(ctx, f, mouseX, mouseY);
        if (hovered != null && !dragging) {
            MapData.Place p = find(hovered);
            if (p != null && !p.id().equals(selected)) drawTip(ctx, f, p, mouseX, mouseY);
        }
        if (hovered != null && !hovered.equals(lastHovered) && clouds.done()) {
            RebornSounds.playReborn("stats.hover", 1.15f, 0.2f);
        }
        lastHovered = hovered;
        drawTravel(ctx);
        clouds.draw(ctx, this.width, this.height);
    }

    private static float easeOut(float t) { return 1f - (1f - t) * (1f - t) * (1f - t); }

    /* ---- en-tête et aide ---- */

    /** Plaque laquée suspendue à la baguette : nom de la carte, puis où se trouve le joueur. */
    private void drawHeader(GuiGraphicsExtractor ctx, Font f) {
        int cx = this.width / 2;
        CarteArt.entete(ctx, cx, 2);
        DaKit.arcX1(ctx, f, DaKit.arcade("Carte de " + snap.title()), cx, 21, DaKit.CREME, 1, true);
        int places = 0;
        for (MapData.Place p : snap.places()) if (p.type() != MapData.Type.ZONE) places++;
        String sub = (snap.here() ? "Tu es dans cette région" : "Tu n'es pas dans cette région")
            + " · " + places + " lieu" + (places > 1 ? "x" : "");
        Component c = DaKit.inter(sub);
        int w = Math.round(DaKit.largeur7(f, c)) + 16;
        CarteArt.etiquette(ctx, cx - w / 2, 44, w);
        DaKit.inter7(ctx, f, c, cx, 52, DaKit.ENCRE, 1);
        if (!MapData.fromServer()) {
            Component demo = DaKit.inter("Aperçu hors ligne");
            int dw = Math.round(DaKit.largeur7(f, demo)) + 14;
            CarteArt.etiquette(ctx, 16, 18, dw);
            DaKit.inter7(ctx, f, demo, 16 + dw / 2f, 26, DaKit.ENCRE_ROUGE, 1);
        }
    }

    /** Aides en cabochons, centrées sur une bande de washi au-dessus de la baguette basse. */
    private void drawHints(GuiGraphicsExtractor ctx, Font f) {
        java.util.List<String[]> l = new java.util.ArrayList<>();
        l.add(new String[]{"Glisser", "déplacer"});
        l.add(new String[]{"Molette", "zoom"});
        l.add(new String[]{"Clic", "choisir un lieu"});
        if (snap.canTp()) l.add(new String[]{"Double clic", "y aller"});
        if (snap.here()) l.add(new String[]{"Espace", "ma position"});
        l.add(new String[]{"M", "fermer"});
        String[][] items = l.toArray(new String[0][]);
        float total = CarteArt.largeurAides(f, items);
        if (total > this.width - 60 && items.length > 4) {
            l.remove(2);
            items = l.toArray(new String[0][]);
            total = CarteArt.largeurAides(f, items);
        }
        int w = Math.round(total) + 20, y = this.height - CarteArt.BAGUETTE - 22;
        CarteArt.bandeau(ctx, this.width / 2 - w / 2, y, w);
        CarteArt.aides(ctx, f, items, this.width / 2f + total / 2f, y + 8);
    }

    /* ---- carte : zones, marqueurs, joueur ---- */

    /** Libellés de zone : visibles vue d'ensemble, s'effacent quand on zoome sur les lieux. */
    private void drawZones(GuiGraphicsExtractor ctx, Font f) {
        float a = Math.max(0f, Math.min(1f, (fitZoom * 2.8f - zoom) / (fitZoom * 1.2f)));
        if (a < 0.03f) return;
        for (MapData.Place p : snap.places()) {
            if (p.type() != MapData.Type.ZONE) continue;
            Component c = DaKit.arcade(p.name());
            int x = Math.round(sx(p.x())), y = Math.round(sy(p.z())) - 3;
            int o = Colors.withAlpha(OUTLINE, a);
            for (int[] d : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {1, 1}}) {
                DaKit.arcX1(ctx, f, c, x + d[0], y + d[1], o, 1, false);
            }
            DaKit.arcX1(ctx, f, c, x, y, Colors.withAlpha(INK, a), 1, false);
        }
    }

    private void drawMarkers(GuiGraphicsExtractor ctx, Font f, int mx, int my, float dt) {
        float names = Math.max(0f, Math.min(1f, (zoom - fitZoom * 1.4f) / (fitZoom * 0.5f)));
        float time = (System.currentTimeMillis() - openedAt) / 1000f;
        for (MapData.Place p : snap.places()) {
            if (p.type() == MapData.Type.ZONE) continue;
            float x = sx(p.x()), y = sy(p.z());
            boolean hot = clouds.done() && Math.abs(mx - x) <= 9 && Math.abs(my - (y - 6)) <= 10;
            if (hot) hovered = p.id();
            boolean sel = p.id().equals(selected);
            float h = hoverAnim.getOrDefault(p.id(), 0f);
            h += ((hot || sel ? 1f : 0f) - h) * Math.min(1f, dt * 12f);
            hoverAnim.put(p.id(), h);
            // Ombre au sol, halo d'or au survol / à la sélection, anneau pulsé si sélectionné.
            DrawHelpers.disc(ctx, Math.round(x), Math.round(y + 1), 4, 0x55000000);
            if (h > 0.05f) CarteArt.halo(ctx, x, y - 8, 1, DaKit.argb(0xFFD070, 0.35f * h));
            if (sel) {
                float k = ((System.currentTimeMillis() - selectedAt) % 1200) / 1200f;
                DrawHelpers.ring(ctx, Math.round(x), Math.round(y), Math.round(6 + 14 * k), 1, Colors.withAlpha(GOLD, 1f - k));
            }
            // Icône à l'échelle entière : soulevée de 2 GUI au survol, sautille si sélectionnée.
            int lift = Math.round(2 * h);
            int bob = sel ? Math.round((float) Math.sin(time * 5f) * 1.5f) : 0;
            ctx.blit(RenderPipelines.GUI_TEXTURED, ICON.getOrDefault(p.type(), ICON.get(MapData.Type.LIEU)),
                Math.round(x) - 8, Math.round(y) - 16 - lift + bob, 0f, 0f, 16, 16, 16, 16);
            float na = Math.max(names, sel ? 1f : 0f);
            if (na > 0.03f) DaKit.inter9o(ctx, f, DaKit.inter(p.name()), Math.round(x), Math.round(y) + 9,
                Colors.withAlpha(INK, na), 1);
        }
    }

    /** Flèche du joueur (position + orientation), seulement s'il est sur cette carte. */
    private void drawPlayer(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!snap.here() || mc.player == null) return;
        float x = sx(mc.player.getX()), y = sy(mc.player.getZ());
        float k = ((System.currentTimeMillis() - openedAt) % 1600) / 1600f;
        DrawHelpers.ring(ctx, Math.round(x), Math.round(y), Math.round(5 + 12 * k), 1, Colors.withAlpha(0xFFFFFFFF, 0.8f * (1f - k)));
        DrawHelpers.disc(ctx, Math.round(x), Math.round(y), 8, 0x40000000);
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        // yaw MC : 0 = sud (+Z, bas de la carte) ; la texture pointe vers le haut.
        ctx.pose().rotate((float) Math.toRadians(mc.player.getYRot() + 180f));
        ctx.blit(RenderPipelines.GUI_TEXTURED, ARROW, -6, -6, 0f, 0f, 13, 13, 13, 13);
        ctx.pose().popMatrix();
    }

    /** Rose des vents laquée, en haut à droite (sous la baguette). */
    private void drawCompass(GuiGraphicsExtractor ctx) {
        CarteArt.boussole(ctx, this.width - 48 - CarteArt.LISIERE - 10, CarteArt.BAGUETTE + 8);
    }

    /** Échelle graphique à l'encre et coordonnées sous le curseur, sur une carte de washi (bas gauche). */
    private void drawScaleAndCursor(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        double blocksPerScreenPx = def.blocksPerPixel() / zoom;
        int[] nice = {10, 25, 50, 100, 250, 500, 1000, 2500};
        int len = nice[nice.length - 1];
        for (int n : nice) if (n / blocksPerScreenPx >= 40) { len = n; break; }
        int px = (int) Math.round(len / blocksPerScreenPx);
        int w = Math.max(px, 74) + 20, h = 40;
        int x0 = CarteArt.LISIERE + 10, y0 = this.height - CarteArt.BAGUETTE - 28 - h;
        CarteArt.washi(ctx, x0, y0, w, h);
        int x = x0 + 10, y = y0 + 22;
        DaKit.inter7(ctx, f, DaKit.inter(len + " blocs"), x, y0 + 13, DaKit.ENCRE_DOUCE, 0);
        ctx.fill(x, y, x + px, y + 2, PARCH_INK);
        ctx.fill(x, y - 3, x + 1, y + 5, PARCH_INK);
        ctx.fill(x + px - 1, y - 3, x + px, y + 5, PARCH_INK);
        ctx.fill(x + px / 2, y - 1, x + px / 2 + 1, y + 3, PARCH_INK);
        String coord = "X " + Math.round(worldX(mx)) + "   Z " + Math.round(worldZ(my));
        DaKit.inter7(ctx, f, DaKit.inter(coord), x, y0 + 33, DaKit.ENCRE, 0);
    }

    /* ---- fiche du lieu sélectionné ---- */

    private void drawSelection(GuiGraphicsExtractor ctx, Font f, int mx, int my) {
        MapData.Place p = selected == null ? null : find(selected);
        if (p == null) return;
        float in = easeOut(Math.min(1f, (System.currentTimeMillis() - selectedAt) / 220f));
        Minecraft mc = Minecraft.getInstance();
        String dist = null;
        if (snap.here() && mc.player != null) {
            double d = Math.hypot(mc.player.getX() - p.x(), mc.player.getZ() - p.z());
            dist = d < 1000 ? Math.round(d) + " blocs" : String.format(Locale.FRANCE, "%.1f km", d / 1000);
        }
        int nameW = f.width(DaKit.arcade(p.name()));
        int w = Math.min(188, Math.max(160, nameW + 46)), h = 66;
        int x = Math.round(this.width - CarteArt.LISIERE - 10 - w + (1f - in) * (w + 24));
        int y = this.height - CarteArt.BAGUETTE - 28 - h;
        CarteArt.washi(ctx, x, y, w, h);
        Identifier icon = ICON.getOrDefault(p.type(), ICON.get(MapData.Type.LIEU));
        ctx.blit(RenderPipelines.GUI_TEXTURED, icon, x + 8, y + 11, 0f, 0f, 16, 16, 16, 16);
        DaKit.arcX1(ctx, f, DaKit.arcade(DaKit.ellipse(f, p.name(), w - 40)), x + 30, y + 11, DaKit.ENCRE, 0, false);
        DaKit.inter7(ctx, f, DaKit.inter(typeLabel(p.type()) + " · X " + p.x() + "  Z " + p.z()), x + 30, y + 25,
            DaKit.ENCRE_DOUCE, 0);
        if (dist != null) DaKit.inter8(ctx, f, DaKit.inter("À " + dist + " de toi"), x + 30, y + 35, DaKit.ENCRE, 0);
        if (snap.canTp()) {
            goW = 78;
            goH = 20;
            goX = x + w - goW - 8;
            goY = y + h - goH - 6;
            boolean hot = mx >= goX && mx < goX + goW && my >= goY && my < goY + goH;
            CarteArt.bouton(ctx, goX, goY, goW, hot);
            DaKit.arcX1(ctx, f, DaKit.arcade("Y aller"), goX + goW / 2f, goY + 6.5f, DaKit.CREME, 1, true);
        } else {
            DaKit.inter7(ctx, f, DaKit.inter("Téléportation réservée au staff"), x + 10, y + h - 10, 0xFF8A7458, 0);
        }
    }

    private static String typeLabel(MapData.Type t) {
        return switch (t) {
            case PNJ -> "PNJ";
            case PORTE -> "Porte";
            case ZONE -> "Zone";
            default -> "Lieu";
        };
    }

    /** Voyage : le parchemin se couvre d'encre depuis le lieu choisi, puis l'écran se ferme. */
    private void drawTravel(GuiGraphicsExtractor ctx) {
        if (travelAt < 0) return;
        float k = Math.min(1f, (System.currentTimeMillis() - travelAt) / 650f);
        MapData.Place p = travelId == null ? null : find(travelId);
        int cx = p != null && def != null ? Math.round(sx(p.x())) : this.width / 2;
        int cy = p != null && def != null ? Math.round(sy(p.z())) : this.height / 2;
        int r = Math.round((float) Math.hypot(this.width, this.height) * easeOut(k));
        DrawHelpers.disc(ctx, cx, cy, r, Colors.withAlpha(0xFF140E0A, 0.92f));
        if (k >= 1f) {
            travelAt = -1;
            super.onClose();
        }
    }

    /* ------------------------------------------------------------ entrées */

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        if (travelAt >= 0) return true;
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
                if (!hovered.equals(selected)) {
                    selected = hovered;
                    selectedAt = System.currentTimeMillis();
                    RebornSounds.playReborn("map.select", 1.0f, 0.6f);
                }
            }
            return true;
        }
        if (dbl && def != null) {
            // Double-clic dans le vide : zoom ×2 à cet endroit.
            zoomTo(targetZoom * 2f, (float) mx, (float) my);
            RebornSounds.playReborn("map.zoom", 1.2f, 0.4f);
            return true;
        }
        pressed = true;
        dragging = false;
        pressX = lastDragX = mx;
        pressY = lastDragY = my;
        velX = velY = 0;
        return true;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent e, double dx, double dy) {
        if (!pressed || def == null) return super.mouseDragged(e, dx, dy);
        if (!dragging && Math.abs(e.x() - pressX) + Math.abs(e.y() - pressY) > 3) {
            dragging = true;
            long now = System.currentTimeMillis();
            if (now - lastDragSound > 400) {
                lastDragSound = now;
                RebornSounds.playReborn("map.drag", 0.9f + (float) Math.random() * 0.2f, 0.35f);
            }
        }
        if (dragging) {
            offX += (float) dx;
            offY += (float) dy;
            // Vitesse lissée pour l'inertie au lâcher.
            velX = velX * 0.6f + (float) dx * 60f * 0.4f;
            velY = velY * 0.6f + (float) dy * 60f * 0.4f;
            clampPan();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent e) {
        if (pressed) {
            if (!dragging) { selected = null; velX = velY = 0; } // clic dans le vide = désélection
            pressed = false;
            dragging = false;
            return true;
        }
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (def == null || v == 0 || travelAt >= 0) return true;
        float before = targetZoom;
        zoomTo((float) (targetZoom * Math.pow(1.25, v)), (float) mx, (float) my);
        if (targetZoom != before) {
            // La hauteur du tic suit le niveau de zoom.
            float lvl = (float) (Math.log(targetZoom / fitZoom) / Math.log(MAX_ZOOM_FACTOR));
            RebornSounds.playReborn("map.zoom", 0.8f + 0.6f * Math.max(0f, lvl), 0.35f);
        }
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
            velX = velY = 0;
            centerOn(def.toPixelX(mc.player.getX()), def.toPixelZ(mc.player.getZ()));
            clampPan();
            RebornSounds.playReborn("map.drag", 1.2f, 0.3f);
            return true;
        }
        if (k == GLFW.GLFW_KEY_HOME && def != null) {
            zoomTo(fitZoom, this.width / 2f, this.height / 2f);
            return true;
        }
        if (k == GLFW.GLFW_KEY_EQUAL || k == GLFW.GLFW_KEY_KP_ADD) { mouseScrolled(this.width / 2.0, this.height / 2.0, 0, 1); return true; }
        if (k == GLFW.GLFW_KEY_MINUS || k == GLFW.GLFW_KEY_KP_SUBTRACT) { mouseScrolled(this.width / 2.0, this.height / 2.0, 0, -1); return true; }
        if (setArrow(k, true)) return true;
        return super.keyPressed(e);
    }

    @Override
    public boolean keyReleased(net.minecraft.client.input.KeyEvent e) {
        if (setArrow(e.key(), false)) return true;
        return super.keyReleased(e);
    }

    private boolean setArrow(int k, boolean down) {
        switch (k) {
            case GLFW.GLFW_KEY_LEFT -> keyLeft = down;
            case GLFW.GLFW_KEY_RIGHT -> keyRight = down;
            case GLFW.GLFW_KEY_UP -> keyUp = down;
            case GLFW.GLFW_KEY_DOWN -> keyDown = down;
            default -> { return false; }
        }
        return true;
    }

    private void travel(String id) {
        if (id == null) return;
        if (!snap.canTp() || !ClientPlayNetworking.canSend(MapPayload.ID)) {
            RebornSounds.playReborn("stats.deny", 1.0f, 0.6f);
            return;
        }
        ClientPlayNetworking.send(new MapPayload("tp:" + id));
        RebornSounds.playReborn("map.travel", 1.0f, 0.75f);
        travelId = id;
        travelAt = System.currentTimeMillis();
    }

    /* ------------------------------------------------------------ infobulle */

    private void drawTip(GuiGraphicsExtractor ctx, Font f, MapData.Place p, int mx, int my) {
        Component c = DaKit.inter(p.name());
        Component t = DaKit.arcade(typeLabel(p.type()));
        float tw = DaKit.largeurFin(f, t);
        int w = Math.round(DaKit.largeur8(f, c) + tw) + 22;
        int x = Math.min(mx + 10, this.width - w - 12), y = Math.max(my - 22, 14);
        CarteArt.etiquette(ctx, x, y, w);
        DaKit.inter8(ctx, f, c, x + 8, y + 8, DaKit.ENCRE, 0);
        DaKit.arcFin(ctx, f, t, x + w - 8, y + 6.25f, DaKit.ENCRE_ROUGE, 2, false);
    }
}
