package fr.reborn.hud.staff;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.map.MapDefs;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.RebornSounds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Infirmerie — gestion visuelle du KO pour le staff ({@code /infirmerie}, bouton « 医 Infirmerie » du Poste de
 * garde). Même décor que le Poste de garde ; motif propre : chaque blessé est une bande de papier (tanzaku)
 * suspendue à une corde, et son temps restant est un bâton d'encens qui se consume.
 *
 * <ul>
 *   <li><b>Blessés</b> : cinq bandes à la fois, flèches / perles / molette pour les suivantes, filtres par état ;</li>
 *   <li><b>Zones</b> : sur la carte du monde du mod — glisser pour tracer, tirer les coins, déplacer ; type, nom,
 *       village, hauteur ; contour montré en jeu ;</li>
 *   <li><b>Hôpitaux</b> : zones de type hôpital et leurs lits (mode sélection en jeu pour en ajouter) ;</li>
 *   <li><b>Réglages</b> : le parcours d'un blessé en frise à étirer, les seuils de soin sur une barre de vie.</li>
 * </ul>
 * Toutes les actions passent par le serveur ({@code InfirmeriePanel}), qui revérifie le grade.
 */
public class InfirmerieScreen extends StaffScreen {

    private static final String[][] KO_TABS = {{"blesses", "Blessés"}, {"zones", "Zones"}, {"hopitaux", "Hôpitaux"}, {"reglages", "Réglages"}};
    private static final String[][] FILTERS = {{"all", "Tous"}, {"down", "À terre"}, {"ko", "Inconscients"}, {"ata", "En ATA"}};
    private static final int PER_PAGE = 5;
    private static final int C_TRAIN = 0xFFA0182B, C_REST = 0xFF2F7A3A, C_HOSP = 0xFF3F78C8;

    private String filter = "all";
    private int page;

    /* carte */
    private double camX, camZ, bpp = 2.0;      // centre (monde) et blocs par pixel d'écran
    private boolean camReady;
    private int mx0, my0, mx1, my1;             // zone de la carte à l'écran
    private String selId;                       // zone sélectionnée (enregistrée)
    private JsonObject edit;                    // copie modifiable (nouvelle zone ou zone sélectionnée)
    private boolean dirty;
    private String dragMode;                    // draw | move | resize | pan | slider
    private int dragCorner;
    private double dragWx, dragWz, dragStartX, dragStartY;
    private int[] dragOrig;
    private EditBox nameBox, villageBox;

    /* réglages */
    private final Map<String, Integer> local = new LinkedHashMap<>();
    private boolean settingsDirty;
    private record Slider(String key, int x, int y, int w, int min, int max, boolean delta, double perPx) { }
    private final List<Slider> sliders = new ArrayList<>();
    private Slider dragSlider;
    private int dragSliderStart;

    public InfirmerieScreen() {
        super();
        tab = "blesses";
    }

    @Override
    protected void init() {
        super.init();
        nameBox = koBox("Nom de la zone", 32);
        villageBox = koBox("Village (konoha…)", 24);
        nameBox.setResponder(v -> { if (edit != null && !v.equals(str(edit, "id"))) { edit.addProperty("id", v); dirty = true; } });
        villageBox.setResponder(v -> { if (edit != null && !v.equals(str(edit, "village"))) { edit.addProperty("village", v); dirty = true; } });
        StaffClient.send("ko_refresh");
    }

    private EditBox koBox(String hint, int max) {
        EditBox b = new EditBox(this.font, 0, 0, 80, 12, Component.literal(hint));
        b.setMaxLength(max);
        b.setHint(Component.literal(hint));
        b.setBordered(false);
        b.setTextColor(INK);
        b.setVisible(false);
        this.addRenderableWidget(b);
        return b;
    }

    @Override
    public void tick() {
        if (++ticks % 20 == 0) StaffClient.send("ko_refresh");
    }

    @Override
    protected String[][] tabList() { return KO_TABS; }

    @Override
    protected int tabCount(String id, JsonObject snap) {
        return "blesses".equals(id) && snap != null ? arr(snap, "injured").size() : 0;
    }

    @Override
    protected void switchTab(String id) {
        if (!id.equals(tab)) { RebornSounds.uiClick(); viewAt = System.currentTimeMillis(); }
        tab = id;
        scroll = 0;
        page = 0;
        edit = null;
        dirty = false;
        if ("hopitaux".equals(id)) { selId = null; camReady = false; }
    }

    @Override
    protected void infirmerieButton(GuiGraphicsExtractor ctx) {
        int w = font.width(RebornFont.body("Poste de garde")) + 14;
        button(ctx, sx1() - w + 8, height - 18, "Poste de garde", "plain", () -> StaffClient.send("open"), false);
    }

    /* =================================================================== rendu */

    @Override
    protected void drawScreen(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        sliders.clear();
        JsonObject snap = StaffClient.koSnapshot();
        frame(ctx);
        tabs(ctx, snap);
        String title = switch (tab) {
            case "zones" -> "Infirmerie · zones";
            case "hopitaux" -> "Infirmerie · hôpitaux";
            case "reglages" -> "Infirmerie · réglages";
            default -> "Infirmerie · " + (snap == null ? 0 : arr(snap, "injured").size()) + " blessé(s)";
        };
        plaque(ctx, title);
        nameBox.setVisible(false);
        villageBox.setVisible(false);
        if (snap == null) {
            text(ctx, "Connexion à l'infirmerie…", vx0(), vy0() + 4, INK_SOFT);
        } else if (paperBottom() >= sy1() - 2) {
            if (local.isEmpty() || !settingsDirty) loadSettings(snap);
            switch (tab) {
                case "zones" -> zones(ctx, snap, false);
                case "hopitaux" -> zones(ctx, snap, true);
                case "reglages" -> reglages(ctx, snap);
                default -> blesses(ctx, snap);
            }
        }
        closeButton(ctx);
        infirmerieButton(ctx);
    }

    /* =================================================================== blessés */

    private List<JsonObject> injured(JsonObject snap) {
        List<JsonObject> out = new ArrayList<>();
        for (JsonElement e : arr(snap, "injured")) {
            JsonObject j = e.getAsJsonObject();
            String s = str(j, "state");
            boolean keep = switch (filter) {
                case "down" -> "down".equals(s);
                case "ko" -> "ko".equals(s) || "chakra".equals(s);
                case "ata" -> "ata".equals(s) || "light".equals(s);
                default -> true;
            };
            if (keep) out.add(j);
        }
        return out;
    }

    private int stateColor(String s) {
        return switch (s) {
            case "down" -> LACQUER;
            case "chakra" -> 0xFF3F78C8;
            case "ata" -> 0xFFD97706;
            case "light" -> 0xFFB9A04A;
            default -> 0xFF3B2A12;
        };
    }

    private void blesses(GuiGraphicsExtractor ctx, JsonObject snap) {
        int y = vy0();
        // filtres
        int fx = vx0();
        for (int i = 0; i < FILTERS.length; i++) {
            String[] f = FILTERS[i];
            int count = 0;
            for (JsonElement e : arr(snap, "injured")) {
                String s = str(e.getAsJsonObject(), "state");
                if ("all".equals(f[0]) || f[0].equals(s) || ("ko".equals(f[0]) && "chakra".equals(s))
                        || ("ata".equals(f[0]) && "light".equals(s))) count++;
            }
            String label = f[1] + " " + count;
            boolean sel = f[0].equals(filter);
            final String id = f[0];
            fx += button(ctx, fx, y, label, sel ? "punish" : "plain", () -> { filter = id; page = 0; viewAt = System.currentTimeMillis(); }, false) + 4;
        }

        List<JsonObject> list = injured(snap);
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.min(page, pages - 1);
        int top = y + 20, x0 = vx0() + 16, x1 = vx1() - 16;
        // corde + flèches laquées
        ctx.fill(x0 - 18, top, x1 + 18, top + 3, 0xFF8A5A2C);
        for (int k = x0 - 18; k < x1 + 18; k += 6) ctx.fill(k, top, k + 3, top + 3, 0xFF6A4422);
        arrow(ctx, vx0() - 2, top - 6, "<", page > 0, () -> { page--; viewAt = System.currentTimeMillis(); });
        arrow(ctx, vx1() - 12, top - 6, ">", page < pages - 1, () -> { page++; viewAt = System.currentTimeMillis(); });

        int bottom = vy1() - 14;
        if (list.isEmpty()) {
            Component c = RebornFont.body("Aucun blessé pour l'instant.");
            ctx.text(font, c, (vx0() + vx1()) / 2 - font.width(c) / 2, top + 30, INK_SOFT, false);
        }
        int gap = 6, sw = (x1 - x0 - gap * (PER_PAGE - 1)) / PER_PAGE, sh = bottom - top - 8;
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= list.size()) break;
            int dx = slide();
            strip(ctx, list.get(idx), x0 + i * (sw + gap) + dx, top + 6, sw, sh, i);
        }
        // perles de pagination
        int bw = pages * 10, bx = (vx0() + vx1()) / 2 - bw / 2;
        for (int p = 0; p < pages; p++) {
            int cx = bx + p * 10;
            boolean on = p == page;
            ctx.fill(cx, bottom + 2, cx + 6, bottom + 8, WOOD_DARK);
            ctx.fill(cx + 1, bottom + 3, cx + 5, bottom + 7, on ? GOLD : 0xFFB08A5A);
            final int target = p;
            hits.add(new Hit(cx - 2, bottom, 10, 10, () -> { page = target; viewAt = System.currentTimeMillis(); }));
        }
        if (!list.isEmpty()) {
            String info = (page * PER_PAGE + 1) + "–" + Math.min(list.size(), (page + 1) * PER_PAGE) + " sur " + list.size();
            small(ctx, info, vx1() - Math.round(font.width(RebornFont.body(info)) * 0.75f) - 2, bottom + 3, INK_SOFT);
        }
    }

    private void arrow(GuiGraphicsExtractor ctx, int x, int y, String glyph, boolean enabled, Runnable action) {
        chamfer(ctx, x - 1, y - 1, 16, 16, WOOD_DARK);
        chamferGradient(ctx, x, y, 14, 14, enabled ? LACQUER_HI : 0xFF8A6A60, enabled ? LACQUER_LO : 0xFF6A4A40);
        DrawHelpers.outlinedRect(ctx, x + 1, y + 1, 12, 12, 0, enabled ? GOLD : 0x80D9A95E);
        ctx.text(font, RebornFont.bold(glyph), x + 5, y + 3, IVORY, false);
        if (enabled) hits.add(new Hit(x, y, 14, 14, () -> { action.run(); RebornSounds.uiClick(); }));
    }

    /** Bande de papier suspendue : visage, nom, état, encens, temps, détails, actions. */
    private void strip(GuiGraphicsExtractor ctx, JsonObject j, int x, int y, int w, int h, int i) {
        String state = str(j, "state");
        int col = stateColor(state);
        float sway = (float) Math.sin(System.currentTimeMillis() / 700.0 + i * 1.3) * 0.6f;
        ctx.pose().pushMatrix();
        ctx.pose().translate(x + w / 2f, y);
        ctx.pose().rotate((float) Math.toRadians(sway));
        ctx.pose().translate(-(x + w / 2f), -y);
        ctx.fill(x + w / 2, y - 6, x + w / 2 + 1, y, LACQUER);                 // ficelle
        ctx.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x4D2E1C11);
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, WOOD_DARK);
        ctx.fillGradient(x, y, x + w, y + h, FUDA, FUDA_2);
        ctx.fill(x, y, x + w, y + 3, col);
        ctx.fill(x + w / 2 - 1, y + 4, x + w / 2 + 2, y + 7, WOOD_DARK);        // trou
        // kanji vertical estompé
        String vert = switch (state) { case "down" -> "倒"; case "chakra" -> "尽"; case "ata" -> "痛"; case "light" -> "傷"; default -> "気"; };
        ctx.pose().pushMatrix();
        ctx.pose().translate(x + 2, y + 9);
        ctx.pose().scale(1.6f, 1.6f);
        ctx.text(font, Component.literal(vert), 0, 0, 0x2E2A1A10, false);
        ctx.pose().popMatrix();
        int cy = y + 9;
        head(ctx, str(j, "uuid"), x + w / 2 - 8, cy, 16);
        cy += 20;
        centered(ctx, fitBold(str(j, "perso"), w - 4), x + w / 2, cy, INK, true);
        cy += 9;
        boolean tight = h < 120;
        if (!tight) {
            smallCentered(ctx, fit(str(j, "name"), Math.round((w - 4) / 0.75f)), x + w / 2, cy, INK_SOFT);
            cy += 9;
        }
        centered(ctx, fit(str(j, "label"), w - 4), x + w / 2, cy, col, false);
        cy += tight ? 10 : 12;
        int left = num(j, "left"), total = Math.max(1, num(j, "total"));
        boolean repos = "repos".equals(str(j, "unit"));
        float frac = repos ? 1f - left / (float) total : left / (float) total;
        String tt = repos ? left + "/" + total + " min" : "s".equals(str(j, "unit")) && total <= 120 ? left + " s" : (left / 60) + ":" + String.format("%02d", left % 60);
        if (y + h - 28 - cy < 40) {
            // place réduite : encens couché + temps
            int bw2 = w - 12, fill = Math.max(1, Math.round(bw2 * Math.max(0f, Math.min(1f, frac))));
            ctx.fill(x + 6, cy + 1, x + 6 + bw2, cy + 3, 0x40462810);
            ctx.fill(x + 6, cy + 1, x + 6 + fill, cy + 3, 0xFF6A3A1A);
            int ember = (System.currentTimeMillis() / 300) % 2 == 0 ? 0xFFFFB050 : 0xFFE0602A;
            ctx.fill(x + 5 + fill, cy, x + 8 + fill, cy + 4, ember);
            smallCentered(ctx, tt, x + w / 2, cy + 5, INK);
            ctx.pose().popMatrix();
            stripButtons(ctx, j, state, x, y, w, h);
            return;
        }
        // encens qui se consume
        int stickH = Math.max(1, Math.round(22 * Math.max(0f, Math.min(1f, frac))));
        int sx = x + w / 2 - 14, base = cy + 24;
        ctx.fill(sx - 4, base, sx + 6, base + 3, 0xFF3A2C14);                   // coupelle
        ctx.fill(sx, base - stickH, sx + 2, base, 0xFF6A3A1A);
        int glow = (System.currentTimeMillis() / 300) % 2 == 0 ? 0xFFFFB050 : 0xFFE0602A;
        ctx.fill(sx - 1, base - stickH - 2, sx + 3, base - stickH, glow);
        int smokeA = 0x40 + (int) (Math.abs(Math.sin(System.currentTimeMillis() / 500.0)) * 0x30);
        ctx.fill(sx, base - stickH - 7, sx + 2, base - stickH - 3, (smokeA << 24) | 0x786E64);
        String t = repos ? left + "/" + total : "s".equals(str(j, "unit")) && total <= 120 ? left + " s" : (left / 60) + ":" + String.format("%02d", left % 60);
        ctx.text(font, RebornFont.bold(t), sx + 10, base - 14, INK, false);
        small(ctx, repos ? "min de repos" : "restant", sx + 10, base - 4, INK_SOFT);
        cy = base + 6;
        for (JsonElement m : arr(j, "meta")) {
            if (cy > y + h - 36) break;
            smallCentered(ctx, fit(m.getAsString(), Math.round((w - 6) / 0.75f)), x + w / 2, cy, INK_SOFT);
            cy += 7;
        }
        ctx.pose().popMatrix();
        stripButtons(ctx, j, state, x, y, w, h);
    }

    /** Actions d'une bande (hors rotation pour des clics exacts). */
    private void stripButtons(GuiGraphicsExtractor ctx, JsonObject j, String state, int x, int y, int w, int h) {
        String uuid = str(j, "uuid");
        int by = y + h - 28, bw = w - 6;
        boolean ko = "down".equals(state) || "ko".equals(state) || "chakra".equals(state);
        if (ko) {
            wide(ctx, x + 3, by, bw, "Réveiller", "punish", () -> koAct("wake", uuid));
            if ("down".equals(state)) half(ctx, x + 3, by + 14, bw, "KO", () -> koAct("knockout", uuid), "TP", () -> koAct("tp", uuid));
            else half(ctx, x + 3, by + 14, bw, "Hôpital", () -> koAct("hospital", uuid), "TP", () -> koAct("tp", uuid));
        } else {
            wide(ctx, x + 3, by, bw, "Lever l'ATA", "watch", () -> koAct("lift_ata", uuid));
            half(ctx, x + 3, by + 14, bw, "Fiche", () -> {
                JsonObject o = new JsonObject();
                o.addProperty("a", "open");
                StaffClient.send(o);
            }, "TP", () -> koAct("tp", uuid));
        }
    }

    private void wide(GuiGraphicsExtractor ctx, int x, int y, int w, String label, String style, Runnable r) {
        button(ctx, x, y, fit(label, w - 14), style, r, false);
    }

    private void half(GuiGraphicsExtractor ctx, int x, int y, int w, String a, Runnable ra, String b, Runnable rb) {
        int wa = button(ctx, x, y, a, "plain", ra, false);
        button(ctx, x + Math.max(wa + 2, w - font.width(RebornFont.body(b)) - 14), y, b, "plain", rb, false);
    }

    private void koAct(String op, String uuid) {
        JsonObject o = new JsonObject();
        o.addProperty("a", "ko_act");
        o.addProperty("op", op);
        o.addProperty("t", uuid);
        StaffClient.send(o);
        RebornSounds.uiClick();
    }

    private String fitBold(String s, int maxW) {
        if (font.width(RebornFont.bold(s)) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && font.width(RebornFont.bold(t + "…")) > maxW) t = t.substring(0, t.length() - 1);
        return t + "…";
    }

    private void centered(GuiGraphicsExtractor ctx, String s, int cx, int y, int color, boolean bold) {
        Component c = bold ? RebornFont.bold(s) : RebornFont.body(s);
        ctx.text(font, c, cx - font.width(c) / 2, y, color, false);
    }

    private void smallCentered(GuiGraphicsExtractor ctx, String s, int cx, int y, int color) {
        int w = Math.round(font.width(RebornFont.body(s)) * 0.75f);
        small(ctx, s, cx - w / 2, y, color);
    }

    /* =================================================================== carte : zones et hôpitaux */

    private int kindColor0(String kind) {
        return switch (kind) { case "repos" -> C_REST; case "hopital" -> C_HOSP; default -> C_TRAIN; };
    }

    private MapDefs.Def mapDef(JsonObject snap) {
        String world = str(snap.getAsJsonObject("me"), "world");
        String id = null;
        for (JsonElement e : arr(snap, "maps")) {
            JsonObject m = e.getAsJsonObject();
            if (world.equals(str(m, "world"))) { id = str(m, "id"); break; }
            if (id == null) id = str(m, "id");
        }
        return id == null ? MapDefs.get("konoha") : MapDefs.get(id);
    }

    private float sx(double wx) { return (mx0 + mx1) / 2f + (float) ((wx - camX) / bpp); }
    private float sy(double wz) { return (my0 + my1) / 2f + (float) ((wz - camZ) / bpp); }
    private double wx(double sxp) { return camX + (sxp - (mx0 + mx1) / 2.0) * bpp; }
    private double wz(double syp) { return camZ + (syp - (my0 + my1) / 2.0) * bpp; }

    private JsonObject zoneById(JsonObject snap, String id) {
        for (JsonElement e : arr(snap, "zones")) if (id != null && id.equals(str(e.getAsJsonObject(), "id"))) return e.getAsJsonObject();
        return null;
    }

    private void zones(GuiGraphicsExtractor ctx, JsonObject snap, boolean hospitals) {
        JsonObject me = snap.getAsJsonObject("me");
        int panelW = Math.max(130, (vx1() - vx0()) * 38 / 100);
        mx0 = vx0(); my0 = vy0() + (hospitals ? 18 : 2); mx1 = vx1() - panelW - 8; my1 = vy1() - 2;
        if (hospitals) hospitalChips(ctx, snap);
        if (!camReady) {
            JsonObject focus = hospitals ? zoneById(snap, selId) : null;
            if (focus != null) { camX = (num(focus, "x1") + num(focus, "x2")) / 2.0; camZ = (num(focus, "z1") + num(focus, "z2")) / 2.0; bpp = 0.6; }
            else { camX = me.get("x").getAsDouble(); camZ = me.get("z").getAsDouble(); bpp = 1.5; }
            camReady = true;
        }
        // cadre de la carte
        ctx.fill(mx0 - 3, my0 - 3, mx1 + 3, my1 + 3, WOOD_DARK);
        DrawHelpers.outlinedRect(ctx, mx0 - 2, my0 - 2, mx1 - mx0 + 4, my1 - my0 + 4, 0, GOLD_SOFT);
        ctx.fill(mx0, my0, mx1, my1, 0xFFD9C79A);
        ctx.enableScissor(mx0, my0, mx1, my1);
        MapDefs.Def def = mapDef(snap);
        if (def != null) {
            float zoom = (float) (def.blocksPerPixel() / bpp);
            ctx.pose().pushMatrix();
            ctx.pose().translate(sx(def.originX()), sy(def.originZ()));
            ctx.pose().scale(zoom, zoom);
            ctx.blit(RenderPipelines.GUI_TEXTURED, def.texture(), 0, 0, 0f, 0f,
                    def.width(), def.height(), def.width(), def.height(), def.width(), def.height());
            ctx.pose().popMatrix();
        }
        // quadrillage tous les 16 blocs quand on est proche
        if (bpp < 1.2) {
            double g0 = Math.floor(wx(mx0) / 16) * 16;
            for (double gx = g0; sx(gx) < mx1; gx += 16) ctx.fill(Math.round(sx(gx)), my0, Math.round(sx(gx)) + 1, my1, 0x18000000);
            double h0 = Math.floor(wz(my0) / 16) * 16;
            for (double gz = h0; sy(gz) < my1; gz += 16) ctx.fill(mx0, Math.round(sy(gz)), mx1, Math.round(sy(gz)) + 1, 0x18000000);
        }
        // zones
        for (JsonElement e : arr(snap, "zones")) {
            JsonObject z = e.getAsJsonObject();
            if (hospitals && !"hopital".equals(str(z, "kind"))) continue;
            boolean isSel = edit != null && str(z, "id").equals(selId);
            JsonObject src = isSel ? edit : z;
            drawZone(ctx, src, isSel);
            if ("hopital".equals(str(z, "kind"))) drawBeds(ctx, z);
        }
        if (edit != null && selId == null) drawZone(ctx, edit, true);
        // toi
        float px = sx(me.get("x").getAsDouble()), pz = sy(me.get("z").getAsDouble());
        ctx.pose().pushMatrix();
        ctx.pose().translate(px, pz);
        ctx.pose().rotate((float) Math.toRadians(45));
        ctx.fill(-3, -3, 3, 3, WOOD_DARK);
        ctx.fill(-2, -2, 2, 2, GOLD);
        ctx.pose().popMatrix();
        ctx.disableScissor();
        // échelle + aide
        String help = hospitals ? "Molette : zoom · clic droit : déplacer la carte"
                : "Glisse pour tracer une zone · clique une zone pour la modifier · molette : zoom · clic droit : déplacer";
        ctx.fill(mx0, my1 - 9, mx1, my1, 0xB3140A08);
        small(ctx, fit(help, Math.round((mx1 - mx0 - 6) / 0.75f)), mx0 + 3, my1 - 7, IVORY_2);
        String scale = Math.round(bpp * 40) + " blocs";
        ctx.fill(mx1 - 50, my0 + 4, mx1 - 10, my0 + 5, WOOD_DARK);
        small(ctx, scale, mx1 - 50, my0 + 7, WOOD_DARK);

        // panneau latéral
        int px0 = mx1 + 8, px1 = vx1();
        if (hospitals) hospitalPanel(ctx, snap, px0, px1);
        else zonePanel(ctx, snap, px0, px1);
    }

    private void drawZone(GuiGraphicsExtractor ctx, JsonObject z, boolean sel) {
        int col = kindColor0(str(z, "kind"));
        int x1 = Math.round(sx(Math.min(num(z, "x1"), num(z, "x2")))), x2 = Math.round(sx(Math.max(num(z, "x1"), num(z, "x2")) + 1));
        int y1 = Math.round(sy(Math.min(num(z, "z1"), num(z, "z2")))), y2 = Math.round(sy(Math.max(num(z, "z1"), num(z, "z2")) + 1));
        ctx.fill(x1, y1, x2, y2, ((sel ? 0x55 : 0x30) << 24) | (col & 0xFFFFFF));
        DrawHelpers.outlinedRect(ctx, x1, y1, Math.max(2, x2 - x1), Math.max(2, y2 - y1), 0, col);
        if (sel) DrawHelpers.outlinedRect(ctx, x1 - 1, y1 - 1, Math.max(2, x2 - x1) + 2, Math.max(2, y2 - y1) + 2, 0, GOLD);
        String id = str(z, "id");
        if (x2 - x1 > 24 && y2 - y1 > 9 && !id.isEmpty()) {
            Component c = RebornFont.bold(fit(id, x2 - x1 - 4));
            ctx.text(font, c, (x1 + x2) / 2 - font.width(c) / 2, (y1 + y2) / 2 - 4, col, false);
        }
        if (sel) {
            int[][] corners = {{x1, y1}, {x2, y1}, {x1, y2}, {x2, y2}};
            for (int[] c : corners) {
                ctx.fill(c[0] - 3, c[1] - 3, c[0] + 3, c[1] + 3, WOOD_DARK);
                ctx.fill(c[0] - 2, c[1] - 2, c[0] + 2, c[1] + 2, GOLD);
            }
        }
    }

    private void drawBeds(GuiGraphicsExtractor ctx, JsonObject z) {
        JsonArray beds = arr(z, "beds");
        for (int i = 0; i < beds.size(); i++) {
            JsonObject b = beds.get(i).getAsJsonObject();
            int bx = Math.round(sx(b.get("x").getAsDouble())), bz = Math.round(sy(b.get("z").getAsDouble()));
            boolean busy = !str(b, "who").isEmpty();
            ctx.fill(bx - 3, bz - 2, bx + 4, bz + 3, WOOD_DARK);
            ctx.fill(bx - 2, bz - 1, bx + 3, bz + 2, busy ? 0xFFC88A8A : 0xFFF5F0E6);
            if (bpp < 0.8) small(ctx, String.valueOf(i + 1), bx - 1, bz - 9, WOOD_DARK);
        }
    }

    /* ------------------------------------------------------------------- panneau des zones */

    private void zonePanel(GuiGraphicsExtractor ctx, JsonObject snap, int x0, int x1) {
        int y = vy0();
        int w = x1 - x0;
        if (edit != null) {
            boolean fresh = selId == null;
            panelBox(ctx, x0, y, w, 118);
            ctx.text(font, RebornFont.display(fresh ? "Nouvelle zone" : "Zone « " + fit(selId, w - 60) + " »"), x0 + 6, y + 5, LACQUER_LO, false);
            // types
            String[][] kinds = {{"entrainement", "Entraînement"}, {"repos", "Repos"}, {"hopital", "Hôpital"}};
            int tw = (w - 16) / 3;
            for (int k = 0; k < 3; k++) {
                String id = kinds[k][0];
                boolean on = id.equals(str(edit, "kind"));
                int tx = x0 + 5 + k * (tw + 3);
                chamfer(ctx, tx - 1, y + 17, tw + 2, 15, on ? GOLD : WOOD_DARK);
                chamferGradient(ctx, tx, y + 18, tw, 13, on ? 0xFFFFF6E0 : 0xFFF4EAD2, on ? 0xFFF0E0C0 : 0xFFE0CFA9);
                Component c = RebornFont.body(fit(kinds[k][1], tw - 10));
                int cw = font.width(c);
                ctx.fill(tx + tw / 2 - cw / 2 - 6, y + 22, tx + tw / 2 - cw / 2 - 2, y + 26, kindColor0(id));
                ctx.text(font, c, tx + tw / 2 - cw / 2 + 1, y + 21, on ? INK : INK_SOFT, false);
                hits.add(new Hit(tx, y + 18, tw, 13, () -> { edit.addProperty("kind", id); dirty = true; RebornSounds.uiClick(); }));
            }
            // nom (+ village pour un hôpital)
            small(ctx, "Nom", x0 + 6, y + 36, INK_SOFT);
            fieldAt(ctx, nameBox, x0 + 30, y + 34, w - 36, str(edit, "id"));
            int yy = y + 50;
            if ("hopital".equals(str(edit, "kind"))) {
                small(ctx, "Village", x0 + 6, yy + 2, INK_SOFT);
                fieldAt(ctx, villageBox, x0 + 30, yy, w - 36, str(edit, "village"));
                yy += 16;
            }
            // hauteur
            small(ctx, "Hauteur : bloc " + num(edit, "y0") + " → " + num(edit, "y1"), x0 + 6, yy + 2, INK_SOFT);
            yy += 10;
            int bx = x0 + 6;
            bx += button(ctx, bx, yy, "−", "plain", () -> bump("y0", -1), false) + 2;
            bx += button(ctx, bx, yy, "+", "plain", () -> bump("y0", 1), false) + 6;
            bx += button(ctx, bx, yy, "−", "plain", () -> bump("y1", -1), false) + 2;
            bx += button(ctx, bx, yy, "+", "plain", () -> bump("y1", 1), false) + 6;
            if (bx + font.width(RebornFont.body("Ma hauteur")) + 14 > x1) { bx = x0 + 6; yy += 14; }
            button(ctx, bx, yy, "Ma hauteur", "plain", () -> {
                int my = num(snap.getAsJsonObject("me"), "y");
                edit.addProperty("y0", my - 2);
                edit.addProperty("y1", my + 10);
                dirty = true;
            }, false);
            yy += 15;
            int sizeX = Math.abs(num(edit, "x2") - num(edit, "x1")) + 1, sizeZ = Math.abs(num(edit, "z2") - num(edit, "z1")) + 1;
            small(ctx, "Taille " + sizeX + " × " + sizeZ + " blocs", x0 + 6, yy, INK_SOFT);
            yy += 9;
            int ax = x0 + 6;
            ax += button(ctx, ax, yy, fresh ? "Créer" : "Enregistrer", (fresh || dirty) ? "punish" : "plain", this::saveZone, false) + 3;
            if (!fresh) {
                ax += button(ctx, ax, yy, "Voir en jeu", "watch", () -> zoneMsg("zone_show", selId), false) + 3;
                ax += button(ctx, ax, yy, "TP", "plain", () -> zoneMsg("zone_tp", selId), false) + 3;
                button(ctx, ax, yy, "Suppr.", "plain", () -> { zoneMsg("zone_delete", selId); edit = null; selId = null; }, false);
            } else {
                button(ctx, ax, yy, "Annuler", "plain", () -> edit = null, false);
            }
            y += 124;
        } else {
            panelBox(ctx, x0, y, w, 30);
            small(ctx, "Glisse sur la carte pour tracer", x0 + 6, y + 6, INK);
            small(ctx, "une zone, ou choisis-en une ci-dessous.", x0 + 6, y + 15, INK_SOFT);
            y += 36;
        }
        // registre des zones
        for (JsonElement e : arr(snap, "zones")) {
            JsonObject z = e.getAsJsonObject();
            if (y > vy1() - 14) break;
            String id = str(z, "id");
            boolean sel = id.equals(selId);
            ctx.fill(x0, y, x1, y + 13, sel ? 0x99FFF6E0 : 0x59FFFFFF);
            if (sel) DrawHelpers.outlinedRect(ctx, x0, y, w, 13, 0, GOLD);
            ctx.fill(x0 + 1, y + 1, x0 + 4, y + 12, kindColor0(str(z, "kind")));
            ctx.text(font, RebornFont.body(fit(id, w - 50)), x0 + 7, y + 3, INK, false);
            int sx2 = Math.abs(num(z, "x2") - num(z, "x1")) + 1, sz2 = Math.abs(num(z, "z2") - num(z, "z1")) + 1;
            small(ctx, sx2 + "×" + sz2, x1 - 30, y + 4, INK_SOFT);
            hits.add(new Hit(x0, y, w, 13, () -> selectZone(z, true)));
            y += 15;
        }
    }

    private void panelBox(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        chamfer(ctx, x - 1, y - 1, w + 2, h + 2, WOOD_DARK);
        chamferGradient(ctx, x, y, w, h, FUDA, FUDA_2);
    }

    private void fieldAt(GuiGraphicsExtractor ctx, EditBox box, int x, int y, int w, String value) {
        ctx.fill(x - 2, y - 1, x + w, y + 12, 0x99FFFFFF);
        DrawHelpers.outlinedRect(ctx, x - 2, y - 1, w + 2, 13, 0, WOOD_DARK);
        box.setVisible(true);
        box.setX(x + 1); box.setY(y + 2); box.setWidth(w - 4);
        if (!box.isFocused() && !box.getValue().equals(value)) box.setValue(value);
    }

    private void bump(String key, int d) {
        edit.addProperty(key, num(edit, key) + d);
        dirty = true;
    }

    private void selectZone(JsonObject z, boolean center) {
        selId = str(z, "id");
        edit = z.deepCopy();
        dirty = false;
        nameBox.setValue(str(z, "id"));
        villageBox.setValue(str(z, "village"));
        if (center) {
            camX = (num(z, "x1") + num(z, "x2")) / 2.0;
            camZ = (num(z, "z1") + num(z, "z2")) / 2.0;
        }
        RebornSounds.uiClick();
    }

    private void saveZone() {
        if (edit == null) return;
        if (str(edit, "id").isBlank()) { StaffClient.toast("Donne un nom à la zone."); return; }
        JsonObject o = new JsonObject();
        o.addProperty("a", "zone_save");
        o.addProperty("old", selId == null ? "" : selId);
        for (String k : new String[]{"id", "kind", "village", "world"}) o.addProperty(k, str(edit, k));
        for (String k : new String[]{"x1", "z1", "x2", "z2", "y0", "y1"}) o.addProperty(k, num(edit, k));
        StaffClient.send(o);
        selId = str(edit, "id").trim().toLowerCase();
        dirty = false;
        RebornSounds.confirm();
    }

    private void zoneMsg(String action, String id) {
        JsonObject o = new JsonObject();
        o.addProperty("a", action);
        o.addProperty("id", id);
        StaffClient.send(o);
        RebornSounds.uiClick();
    }

    /* ------------------------------------------------------------------- hôpitaux */

    private List<JsonObject> hospitals(JsonObject snap) {
        List<JsonObject> out = new ArrayList<>();
        for (JsonElement e : arr(snap, "zones")) if ("hopital".equals(str(e.getAsJsonObject(), "kind"))) out.add(e.getAsJsonObject());
        return out;
    }

    private void hospitalChips(GuiGraphicsExtractor ctx, JsonObject snap) {
        List<JsonObject> hs = hospitals(snap);
        if (selId == null && !hs.isEmpty()) { selId = str(hs.get(0), "id"); camReady = false; }
        int x = vx0(), y = vy0();
        for (JsonObject h : hs) {
            String id = str(h, "id");
            x += button(ctx, x, y, id, id.equals(selId) ? "punish" : "plain", () -> { selId = id; camReady = false; }, false) + 4;
        }
        button(ctx, x, y, "+ Nouvel hôpital", "plain", () -> {
            switchTab("zones");
            StaffClient.toast("Trace la zone sur la carte puis choisis le type Hôpital.");
        }, false);
    }

    private void hospitalPanel(GuiGraphicsExtractor ctx, JsonObject snap, int x0, int x1) {
        int y = vy0() + 18, w = x1 - x0;
        JsonObject h = zoneById(snap, selId);
        if (h == null) {
            panelBox(ctx, x0, y, w, 44);
            small(ctx, "Aucun hôpital pour l'instant.", x0 + 6, y + 6, INK);
            small(ctx, "Trace une zone de type Hôpital", x0 + 6, y + 16, INK_SOFT);
            small(ctx, "dans l'onglet Zones.", x0 + 6, y + 25, INK_SOFT);
            return;
        }
        JsonArray beds = arr(h, "beds");
        int busy = 0;
        for (JsonElement e : beds) if (!str(e.getAsJsonObject(), "who").isEmpty()) busy++;
        panelBox(ctx, x0, y, w, 52);
        ctx.text(font, RebornFont.display(fit("Hôpital « " + selId + " »", w - 10)), x0 + 6, y + 5, C_HOSP, false);
        String v = str(h, "village");
        small(ctx, "Village : " + (v.isEmpty() ? "tous (par défaut)" : v), x0 + 6, y + 17, INK);
        small(ctx, beds.size() + " lit(s) · " + (beds.size() - busy) + " libre(s) · " + busy + " occupé(s)", x0 + 6, y + 25, INK_SOFT);
        int bx = x0 + 6;
        bx += button(ctx, bx, y + 36, "+ Ajouter des lits", "punish", () -> zoneMsg("bed_pick", selId), false) + 3;
        button(ctx, bx, y + 36, "TP", "plain", () -> zoneMsg("zone_tp", selId), false);
        y += 58;
        if (beds.isEmpty()) {
            small(ctx, "Pas encore de lit : « Ajouter des lits »,", x0 + 2, y, INK_SOFT);
            small(ctx, "vise chaque meuble et clic droit.", x0 + 2, y + 8, INK_SOFT);
        }
        for (int i = 0; i < beds.size(); i++) {
            if (y > vy1() - 14) { small(ctx, "… et " + (beds.size() - i) + " autre(s)", x0 + 2, y + 2, INK_SOFT); break; }
            JsonObject b = beds.get(i).getAsJsonObject();
            String who = str(b, "who");
            ctx.fill(x0, y, x1, y + 13, 0x59FFFFFF);
            ctx.fill(x0 + 2, y + 3, x0 + 12, y + 10, WOOD_DARK);
            ctx.fill(x0 + 3, y + 4, x0 + 11, y + 9, who.isEmpty() ? 0xFF9FB7D9 : 0xFFC88A8A);
            ctx.text(font, RebornFont.body("Lit " + (i + 1)), x0 + 16, y + 3, INK, false);
            small(ctx, fit(who.isEmpty() ? "libre" : who, 80), x0 + 46, y + 4, who.isEmpty() ? OK : LACQUER_LO);
            final int idx = i;
            int xw = font.width(RebornFont.body("Retirer")) + 14, tw = font.width(RebornFont.body("TP")) + 14;
            button(ctx, x1 - xw, y, "Retirer", "plain", () -> bedMsg("bed_remove", idx), false);
            button(ctx, x1 - xw - tw - 2, y, "TP", "plain", () -> bedMsg("bed_tp", idx), false);
            y += 15;
        }
    }

    private void bedMsg(String action, int i) {
        JsonObject o = new JsonObject();
        o.addProperty("a", action);
        o.addProperty("id", selId);
        o.addProperty("i", i);
        StaffClient.send(o);
        RebornSounds.uiClick();
    }

    /* =================================================================== réglages */

    private void loadSettings(JsonObject snap) {
        JsonObject s = snap.getAsJsonObject("settings");
        if (s == null) return;
        for (Map.Entry<String, JsonElement> e : s.entrySet()) local.put(e.getKey(), e.getValue().getAsInt());
    }

    private int v(String k) { return local.getOrDefault(k, 0); }

    private void reglages(GuiGraphicsExtractor ctx, JsonObject snap) {
        boolean admin = grade(snap) >= 3;
        int y = vy0();
        section(ctx, "Le parcours d'un blessé", y - 1);
        int bx = vx1();
        int w1 = font.width(RebornFont.body("Appliquer")) + 14, w2 = font.width(RebornFont.body("Valeurs d'origine")) + 14;
        button(ctx, bx - w1, y - 1, "Appliquer", settingsDirty ? "punish" : "plain", () -> {
            JsonObject o = new JsonObject();
            o.addProperty("a", "settings_set");
            JsonObject vals = new JsonObject();
            local.forEach(vals::addProperty);
            o.add("v", vals);
            StaffClient.send(o);
            settingsDirty = false;
            RebornSounds.confirm();
        }, !admin);
        button(ctx, bx - w1 - w2 - 4, y - 1, "Valeurs d'origine", "plain", () -> {
            StaffClient.send("settings_reset");
            settingsDirty = false;
        }, !admin);
        if (!admin) small(ctx, "Lecture seule : réservé aux Admins.", vx0() + 4, y + 14, LACQUER_LO);
        else small(ctx, "Tire les poignées dorées pour allonger ou raccourcir une étape.", vx0() + 4, y + 14, INK_SOFT);

        // frise
        int fy = y + 34, fh = 30, fx0 = vx0() + 4, fx1 = vx1() - 4;
        String[][] stages = {{"terre", "倒", "À terre", "0xFFA0182B"}, {"inco", "気", "Inconscient", "0xFF3B2A12"},
                {"hop", "院", "Hôpital", "0xFF3F78C8"}, {"pleine", "痛", "ATA", "0xFFD97706"}, {"ok", "回", "Rétabli", "0xFF2F7A3A"}};
        double[] weight = {Math.sqrt(v("terre") / 120.0) * 10, Math.sqrt(v("inco") / 900.0) * 10, 2.4,
                Math.sqrt(v("pleine") / 60.0) * 10, 2.4};
        double sum = 0;
        for (double d : weight) sum += Math.max(1.6, d);
        int cx = fx0;
        for (int i = 0; i < stages.length; i++) {
            int sw = (int) Math.round((fx1 - fx0) * Math.max(1.6, weight[i]) / sum);
            if (i == stages.length - 1) sw = fx1 - cx;
            int col = (int) Long.parseLong(stages[i][3].substring(2), 16);
            ctx.fill(cx, fy, cx + sw, fy + fh, WOOD_DARK);
            ctx.fillGradient(cx + 1, fy + 1, cx + sw - 1, fy + fh - 1, col, (0xCC << 24) | (col & 0xFFFFFF));
            String val = switch (stages[i][0]) {
                case "terre" -> v("terre") + " s";
                case "inco" -> (v("inco") / 60) + " min" + (v("inco") % 60 > 0 ? " " + v("inco") % 60 + " s" : "");
                case "hop" -> "réveil " + v("plancher") + " %";
                case "pleine" -> v("pleine") + " / " + v("allegee") + " min";
                default -> "";
            };
            Component kc = Component.literal(stages[i][1]);
            ctx.text(font, kc, cx + sw / 2 - font.width(kc) / 2, fy + 3, 0xFFF5E9D0, false);
            Component lc = RebornFont.bold(fit(stages[i][2], sw - 4));
            ctx.text(font, lc, cx + sw / 2 - font.width(lc) / 2, fy + 12, IVORY, false);
            if (!val.isEmpty()) smallCentered(ctx, fit(val, Math.round((sw - 4) / 0.75f)), cx + sw / 2, fy + 22, 0xFFF5E9D0);
            String key = stages[i][0];
            if (key.equals("terre") || key.equals("inco") || key.equals("pleine")) {
                int gx = cx + sw - 3;
                ctx.fill(gx - 1, fy + 5, gx + 5, fy + fh - 5, WOOD_DARK);
                ctx.fill(gx, fy + 6, gx + 4, fy + fh - 6, GOLD);
                double perPx = switch (key) { case "terre" -> 1.0; case "inco" -> 5.0; default -> 0.25; };
                int[] lim = switch (key) { case "terre" -> new int[]{10, 120}; case "inco" -> new int[]{60, 900}; default -> new int[]{1, 60}; };
                if (admin) sliders.add(new Slider(key, gx - 3, fy, 10, lim[0], lim[1], true, perPx));
            }
            if (key.equals("inco")) {
                int hx = cx + (int) Math.round(sw * Math.min(0.95, v("hopital") / (double) Math.max(1, v("inco"))));
                ctx.fill(hx, fy + fh, hx + 1, fy + fh + 7, GOLD);
                small(ctx, "/hopital " + v("hopital") + " s", hx - 20, fy + fh + 9, INK_SOFT);
                if (admin) sliders.add(new Slider("hopital", hx - 4, fy + fh, 8, 0, 600, true, 4.0));
            }
            cx += sw;
        }
        // peur
        int peurW = (int) Math.round((fx1 - fx0) * 0.72);
        ctx.fill(fx0, fy + fh + 20, fx0 + peurW, fy + fh + 24, LACQUER);
        for (int k = fx0; k < fx0 + peurW; k += 6) ctx.fill(k, fy + fh + 20, k + 3, fy + fh + 24, 0xFFC84A5A);
        small(ctx, "Peur : au moins " + v("peur") + " min après le KO", fx0, fy + fh + 26, LACQUER_LO);

        // seuils de soin + petits réglages
        int ry = fy + fh + 40;
        int colW = (vx1() - vx0() - 12) / 2;
        int lx = vx0() + 4;
        panelBox(ctx, lx, ry, colW, 50);
        ctx.text(font, RebornFont.display("Seuils de soin"), lx + 5, ry + 4, LACQUER_LO, false);
        int hx0 = lx + 8, hx1 = lx + colW - 8, hy = ry + 26;
        ctx.fill(hx0 - 1, hy - 1, hx1 + 1, hy + 9, WOOD_DARK);
        DrawHelpers.horizontalGradient(ctx, hx0, hy, hx1 - hx0, 8, 0xFF6B1A1A, 0xFFE04A3A);
        int pl = hx0 + (hx1 - hx0) * v("plancher") / 100, r1 = hx0 + (hx1 - hx0) * v("rang1") / 100;
        ctx.fill(hx0, hy, pl, hy + 8, 0x40FFFFFF);
        marker(ctx, pl, hy, "Plancher " + v("plancher") + " %", true);
        marker(ctx, r1, hy, "Médic 1 · " + v("rang1") + " %", true);
        marker(ctx, hx1, hy, "Rang 2 · 100 %", false);
        if (admin) {
            sliders.add(new Slider("plancher", hx0, hy - 3, hx1 - hx0, 5, Math.max(6, v("rang1") - 5), false, 0));
        }
        small(ctx, "repos, bandage, hôpital", hx0, hy + 12, INK_SOFT);

        int rx = lx + colW + 4;
        panelBox(ctx, rx, ry, colW, 50);
        int full = Math.round(100f / Math.max(1, v("paume")) * 1000f / 1000f * 10f / 1.5f);
        sliderRow(ctx, rx + 5, ry + 5, colW - 10, "Paume de soin", "soin complet ≈ " + Math.max(1, Math.round(1000f / Math.max(1, v("paume")) / 1.5f)) + " s", "paume", 5, 50, admin);
        sliderRow(ctx, rx + 5, ry + 21, colW - 10, "Chuchotement", v("chuchot") + " blocs", "chuchot", 1, 16, admin);
        sliderRow(ctx, rx + 5, ry + 37, colW - 10, "Peur", v("peur") + " min", "peur", 0, 60, admin);
    }

    private void marker(GuiGraphicsExtractor ctx, int x, int y, String label, boolean above) {
        ctx.fill(x - 1, y - 4, x + 2, y + 12, WOOD_DARK);
        ctx.fill(x, y - 3, x + 1, y + 11, GOLD);
        if (above) {
            int w = Math.round(font.width(RebornFont.body(label)) * 0.75f);
            small(ctx, label, x - w / 2, y - 11, INK);
        }
    }

    private void sliderRow(GuiGraphicsExtractor ctx, int x, int y, int w, String label, String value, String key, int min, int max, boolean admin) {
        small(ctx, label, x, y, INK);
        int vw = Math.round(font.width(RebornFont.body(value)) * 0.75f);
        small(ctx, value, x + w - vw, y, LACQUER_LO);
        int ty = y + 9, tx0 = x, tx1 = x + w;
        ctx.fill(tx0, ty, tx1, ty + 2, 0xFF8A6A45);
        int kx = tx0 + (int) Math.round((tx1 - tx0) * (v(key) - min) / (double) Math.max(1, max - min));
        ctx.fill(tx0, ty, kx, ty + 2, LACQUER);
        ctx.fill(kx - 2, ty - 2, kx + 3, ty + 4, WOOD_DARK);
        ctx.fill(kx - 1, ty - 1, kx + 2, ty + 3, GOLD);
        if (admin) sliders.add(new Slider(key, tx0, ty - 3, tx1 - tx0, min, max, false, 0));
    }

    /* =================================================================== banc d'essai (StaffDebug) */

    void debugPage(int p) { page = p; }

    void debugSelect(String id) {
        JsonObject snap = StaffClient.koSnapshot();
        JsonObject z = snap == null ? null : zoneById(snap, id);
        if (z != null) { selectZone(z, true); bpp = 0.9; }
    }

    void debugDraft(int x1, int z1, int x2, int z2) {
        selId = null;
        edit = new JsonObject();
        edit.addProperty("id", "terrain-nord");
        edit.addProperty("kind", "repos");
        edit.addProperty("village", "");
        edit.addProperty("world", "world");
        edit.addProperty("x1", x1); edit.addProperty("z1", z1); edit.addProperty("x2", x2); edit.addProperty("z2", z2);
        edit.addProperty("y0", 62); edit.addProperty("y1", 80);
        dirty = true;
    }

    /* =================================================================== entrées */

    private boolean inMap(double x, double y) {
        return ("zones".equals(tab) || "hopitaux".equals(tab)) && x >= mx0 && x < mx1 && y >= my0 && y < my1 - 9;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        double x = e.x(), y = e.y();
        // réglettes
        if (e.button() == 0) for (Slider s : sliders) {
            if (x >= s.x() && x < s.x() + Math.max(8, s.w()) && y >= s.y() && y < s.y() + (s.delta() ? 30 : 9)) {
                dragSlider = s;
                dragSliderStart = v(s.key());
                dragStartX = x;
                if (!s.delta()) setSlider(s, x);
                return true;
            }
        }
        if (inMap(x, y)) {
            JsonObject snap = StaffClient.koSnapshot();
            if (e.button() == 1 || e.button() == 2) { dragMode = "pan"; dragStartX = x; dragStartY = y; dragWx = camX; dragWz = camZ; return true; }
            if ("hopitaux".equals(tab) || snap == null) return true;
            double wxv = wx(x), wzv = wz(y);
            // coin d'une zone sélectionnée
            if (edit != null) {
                int x1 = Math.round(sx(Math.min(num(edit, "x1"), num(edit, "x2")))), x2 = Math.round(sx(Math.max(num(edit, "x1"), num(edit, "x2")) + 1));
                int y1 = Math.round(sy(Math.min(num(edit, "z1"), num(edit, "z2")))), y2 = Math.round(sy(Math.max(num(edit, "z1"), num(edit, "z2")) + 1));
                int[][] corners = {{x1, y1}, {x2, y1}, {x1, y2}, {x2, y2}};
                for (int k = 0; k < 4; k++) {
                    if (Math.abs(x - corners[k][0]) <= 4 && Math.abs(y - corners[k][1]) <= 4) {
                        normalize(edit);
                        dragMode = "resize";
                        dragCorner = k;
                        return true;
                    }
                }
            }
            // une zone : sélection + déplacement
            JsonArray zs = arr(snap, "zones");
            for (int i = zs.size() - 1; i >= 0; i--) {
                JsonObject z = zs.get(i).getAsJsonObject();
                JsonObject src = z.get("id").getAsString().equals(selId) && edit != null ? edit : z;
                if (wxv >= Math.min(num(src, "x1"), num(src, "x2")) && wxv < Math.max(num(src, "x1"), num(src, "x2")) + 1
                        && wzv >= Math.min(num(src, "z1"), num(src, "z2")) && wzv < Math.max(num(src, "z1"), num(src, "z2")) + 1) {
                    if (!str(z, "id").equals(selId) || edit == null) selectZone(z, false);
                    normalize(edit);
                    dragMode = "move";
                    dragWx = wxv; dragWz = wzv;
                    dragOrig = new int[]{num(edit, "x1"), num(edit, "z1"), num(edit, "x2"), num(edit, "z2")};
                    return true;
                }
            }
            // vide : nouvelle zone
            selId = null;
            edit = new JsonObject();
            edit.addProperty("id", "");
            edit.addProperty("kind", "entrainement");
            edit.addProperty("village", "");
            edit.addProperty("world", str(snap.getAsJsonObject("me"), "world"));
            int bxv = (int) Math.floor(wxv), bzv = (int) Math.floor(wzv);
            edit.addProperty("x1", bxv); edit.addProperty("z1", bzv);
            edit.addProperty("x2", bxv); edit.addProperty("z2", bzv);
            int myy = num(snap.getAsJsonObject("me"), "y");
            edit.addProperty("y0", myy - 2); edit.addProperty("y1", myy + 10);
            nameBox.setValue("");
            villageBox.setValue("");
            dragMode = "draw";
            dirty = true;
            return true;
        }
        return super.mouseClicked(e, dbl);
    }

    private static void normalize(JsonObject z) {
        int x1 = Math.min(z.get("x1").getAsInt(), z.get("x2").getAsInt()), x2 = Math.max(z.get("x1").getAsInt(), z.get("x2").getAsInt());
        int z1 = Math.min(z.get("z1").getAsInt(), z.get("z2").getAsInt()), z2 = Math.max(z.get("z1").getAsInt(), z.get("z2").getAsInt());
        z.addProperty("x1", x1); z.addProperty("x2", x2); z.addProperty("z1", z1); z.addProperty("z2", z2);
    }

    private void setSlider(Slider s, double x) {
        int val = s.min() + (int) Math.round((x - s.x()) / Math.max(1, s.w()) * (s.max() - s.min()));
        local.put(s.key(), Math.max(s.min(), Math.min(s.max(), val)));
        settingsDirty = true;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent e, double dx, double dy) {
        double x = e.x(), y = e.y();
        if (dragSlider != null) {
            if (dragSlider.delta()) {
                int val = dragSliderStart + (int) Math.round((x - dragStartX) * dragSlider.perPx());
                local.put(dragSlider.key(), Math.max(dragSlider.min(), Math.min(dragSlider.max(), val)));
                if ("pleine".equals(dragSlider.key())) local.put("allegee", Math.max(1, local.get("pleine") / 2));
                settingsDirty = true;
            } else setSlider(dragSlider, x);
            return true;
        }
        if (dragMode == null) return super.mouseDragged(e, dx, dy);
        switch (dragMode) {
            case "pan" -> { camX = dragWx - (x - dragStartX) * bpp; camZ = dragWz - (y - dragStartY) * bpp; }
            case "draw" -> { edit.addProperty("x2", (int) Math.floor(wx(x))); edit.addProperty("z2", (int) Math.floor(wz(y))); }
            case "move" -> {
                int ox = (int) Math.round(wx(x) - dragWx), oz = (int) Math.round(wz(y) - dragWz);
                edit.addProperty("x1", dragOrig[0] + ox); edit.addProperty("z1", dragOrig[1] + oz);
                edit.addProperty("x2", dragOrig[2] + ox); edit.addProperty("z2", dragOrig[3] + oz);
                dirty = true;
            }
            case "resize" -> {
                int bxv = (int) Math.floor(wx(x)), bzv = (int) Math.floor(wz(y));
                if (dragCorner == 0 || dragCorner == 2) edit.addProperty("x1", Math.min(bxv, num(edit, "x2"))); else edit.addProperty("x2", Math.max(bxv, num(edit, "x1")));
                if (dragCorner == 0 || dragCorner == 1) edit.addProperty("z1", Math.min(bzv, num(edit, "z2"))); else edit.addProperty("z2", Math.max(bzv, num(edit, "z1")));
                dirty = true;
            }
            default -> { }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent e) {
        if (dragSlider != null) { dragSlider = null; return true; }
        if ("draw".equals(dragMode) && edit != null) {
            normalize(edit);
            if (num(edit, "x2") - num(edit, "x1") < 1 || num(edit, "z2") - num(edit, "z1") < 1) edit = null;   // simple clic
            else nameBox.setFocused(true);
        }
        dragMode = null;
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {
        if (inMap(x, y)) {
            double wxv = wx(x), wzv = wz(y);
            bpp = Math.max(0.15, Math.min(12.0, bpp * (v > 0 ? 0.8 : 1.25)));
            camX = wxv - (x - (mx0 + mx1) / 2.0) * bpp;
            camZ = wzv - (y - (my0 + my1) / 2.0) * bpp;
            return true;
        }
        if ("blesses".equals(tab)) {
            JsonObject snap = StaffClient.koSnapshot();
            int pages = snap == null ? 1 : Math.max(1, (injured(snap).size() + PER_PAGE - 1) / PER_PAGE);
            int np = Math.max(0, Math.min(pages - 1, page + (v < 0 ? 1 : -1)));
            if (np != page) { page = np; viewAt = System.currentTimeMillis(); RebornSounds.uiClick(); }
            return true;
        }
        return true;
    }
}
