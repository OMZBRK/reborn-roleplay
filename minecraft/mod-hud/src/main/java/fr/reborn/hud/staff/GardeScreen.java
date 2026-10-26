package fr.reborn.hud.staff;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.map.MapDefs;
import fr.reborn.hud.menu.RebornSounds;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderPipelines;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static fr.reborn.hud.staff.GardeArt.*;

/**
 * Poste de garde complet : le volet ({@link StaffScreen}) et l'<b>Infirmerie</b> (gestion visuelle du KO), rangée dans
 * l'onglet Outils. Ouvert par F7, {@code /garde} (Alertes) ou {@code /infirmerie} (Outils › Blessés).
 *
 * <ul>
 *   <li><b>Blessés</b> (dans le volet) : à terre, inconscients, en ATA ; filtres en mots, temps restant en barre fine,
 *       actions en mots (Réveiller, KO / Hôpital, TP, Lever l'ATA, Fiche) ;</li>
 *   <li><b>Zones</b> et <b>Hôpitaux</b> (volet élargi) : la carte du monde du mod — glisser pour tracer, tirer les
 *       coins, déplacer ; type, nom, village, hauteur ; lits des hôpitaux (mode sélection en jeu) ;</li>
 *   <li><b>Réglages</b> (volet élargi) : le parcours d'un blessé en frise à étirer, seuils de soin et réglettes.</li>
 * </ul>
 * Toutes les actions passent par le serveur ({@code InfirmeriePanel}), qui revérifie le grade.
 */
public class GardeScreen extends StaffScreen {

    private static final String[][] FILTERS = {{"all", "Tous"}, {"down", "À terre"}, {"ko", "Inconscients"}, {"ata", "En ATA"}};
    private static final int C_TRAIN = 0xFFD05060, C_REST = 0xFF70B880, C_HOSP = 0xFF6A9CE0;

    private String filter = "all";

    /* carte */
    private double camX, camZ, bpp = 2.0;
    private boolean camReady;
    private int mx0, my0, mx1, my1;
    private String selId;
    private JsonObject edit;
    private boolean dirty;
    private String dragMode;
    private int dragCorner;
    private double dragWx, dragWz, dragStartX, dragStartY;
    private int[] dragOrig;
    private EditBox nameBox, villageBox;

    /* réglages */
    private final Map<String, Integer> local = new LinkedHashMap<>();
    private boolean settingsDirty;
    private record Slider(String key, int x, int y, int w, int h, int min, int max, boolean delta, double perPx) { }
    private final List<Slider> sliders = new ArrayList<>();
    private Slider dragSlider;
    private int dragSliderStart;

    public GardeScreen(String initialTab) {
        super();
        if ("ko".equals(initialTab)) { tab = "outils"; outil = "blesses"; }
        else tab = "alerts";
    }

    public boolean onKo() {
        return "outils".equals(tab) && outil != null && List.of("blesses", "zones", "hopitaux", "reglages").contains(outil);
    }

    /** Ouvre l'Infirmerie ({@code ko}) ou un onglet sans recréer l'écran. */
    public void show(String id) {
        if ("ko".equals(id)) {
            if (!onKo()) { switchTab("outils"); outil = "blesses"; }
        } else if (!id.equals(tab)) switchTab(id);
    }

    @Override
    protected void init() {
        super.init();
        nameBox = box(nameBox, "Nom de la zone", 32);
        villageBox = box(villageBox, "Village (konoha…)", 24);
        nameBox.setResponder(v -> { if (edit != null && !v.equals(str(edit, "id"))) { edit.addProperty("id", v); dirty = true; } });
        villageBox.setResponder(v -> { if (edit != null && !v.equals(str(edit, "village"))) { edit.addProperty("village", v); dirty = true; } });
        StaffClient.send("ko_refresh");
    }

    @Override
    public void tick() {
        super.tick();
        if (ticks % (onKo() ? 20 : 60) == 0) StaffClient.send("ko_refresh");
    }

    @Override
    protected boolean large() {
        return super.large() || "outils".equals(tab) && ("zones".equals(outil) || "hopitaux".equals(outil) || "reglages".equals(outil));
    }

    @Override
    protected void dessiner(net.minecraft.client.gui.GuiGraphicsExtractor ctx) {
        sliders.clear();
        String avant = outil;
        super.dessiner(ctx);
        if (avant != null && !avant.equals(outil)) { edit = null; dirty = false; selId = null; camReady = false; }
    }

    /** Urgents (KO en cours) — compteur de l'outil Blessés. */
    private int urgents(JsonObject ko) {
        int n = 0;
        if (ko != null) for (JsonElement e : arr(ko, "injured")) {
            String s = str(e.getAsJsonObject(), "state");
            if ("down".equals(s) || "ko".equals(s) || "chakra".equals(s)) n++;
        }
        return n;
    }

    Object[] resumeKo(String id) {
        JsonObject ko = StaffClient.koSnapshot();
        return switch (id) {
            case "blesses" -> {
                int n = ko == null ? 0 : arr(ko, "injured").size();
                yield new Object[]{urgents(ko), n == 0 ? "Aucun blessé" : n + " blessé(s) · " + urgents(ko) + " urgent(s)"};
            }
            case "zones" -> new Object[]{0, (ko == null ? 0 : arr(ko, "zones").size()) + " zone(s) · tracer sur la carte"};
            case "hopitaux" -> new Object[]{0, (ko == null ? 0 : hospitals(ko).size()) + " hôpital(aux) · choisir les lits"};
            case "reglages" -> new Object[]{0, ko != null && grade(ko) >= 3 ? "Durées du KO et de l'ATA, seuils de soin" : "Lecture seule (Admin)"};
            default -> new Object[]{0, ""};
        };
    }

    @Override
    protected int compteOnglet(String id, JsonObject snap) {
        if ("outils".equals(id)) return urgents(StaffClient.koSnapshot());
        return super.compteOnglet(id, snap);
    }

    /** Corps d'un outil de l'Infirmerie. */
    int outilKo(JsonObject snap, int y) {
        JsonObject ko = StaffClient.koSnapshot();
        if (ko == null) return vide(y, "Connexion à l'infirmerie…", null);
        if (local.isEmpty() || !settingsDirty) loadSettings(ko);
        return switch (outil) {
            case "zones" -> { zones(ko, false); yield vy1(); }
            case "hopitaux" -> { zones(ko, true); yield vy1(); }
            case "reglages" -> reglages(ko, y + 2);
            default -> blesses(ko, y);
        };
    }

    /* =================================================================== blessés */

    private List<JsonObject> injured(JsonObject ko) {
        List<JsonObject> out = new ArrayList<>();
        for (JsonElement e : arr(ko, "injured")) {
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

    private static int stateColor(String s) {
        return switch (s) {
            case "down" -> TERRE;
            case "chakra" -> GLACE;
            case "ata" -> ROUGE;
            case "light" -> OR;
            default -> ROUGE;
        };
    }

    private int blesses(JsonObject ko, int y) {
        float x = ix();
        for (String[] f : FILTERS) {
            int count = 0;
            for (JsonElement e : arr(ko, "injured")) {
                String s = str(e.getAsJsonObject(), "state");
                if ("all".equals(f[0]) || f[0].equals(s) || ("ko".equals(f[0]) && "chakra".equals(s)) || ("ata".equals(f[0]) && "light".equals(s))) count++;
            }
            x += mot(f[1] + " " + count, x, y + 6, 13, f[0].equals(filter), () -> filter = f[0]) + 9;
        }
        y += 14;
        pz.filetH(ix(), ix() + iw(), y - 0.5f, FILET);
        List<JsonObject> list = injured(ko);
        if (list.isEmpty()) return vide(y, "Aucun blessé pour l'instant.", "À terre, inconscients et ATA apparaissent ici.");
        for (JsonObject j : list) {
            String state = str(j, "state"), uuid = str(j, "uuid");
            int col = stateColor(state);
            int h = 35;
            boolean hov = hover(px0() + 1, y, pw() - 2, h) && cliquable(y, h);
            if (hov) pz.fond(px0() + 1, y, px1() - 1, y + h, NUIT_2);
            pz.fond(px0() + 1, y + 4, px0() + 2, y + h - 4, col);
            pz.visage(uuid, ix(), y + 4, 12);
            int left = num(j, "left"), total = Math.max(1, num(j, "total"));
            boolean repos = "repos".equals(str(j, "unit"));
            String tt = repos ? left + "/" + total + " min" : "s".equals(str(j, "unit")) && total <= 120 ? left + " s"
                    : (left / 60) + ":" + String.format("%02d", left % 60);
            float tw = pz.texteD(tt, ix() + iw(), y + 7.5f, 12, col, true);
            txt(str(j, "perso").isEmpty() ? str(j, "name") : str(j, "perso"), ix() + 18, y + 7.5f, iw() - 18 - tw - 6, 15, CREME, false);
            List<String> meta = new ArrayList<>();
            meta.add(str(j, "label"));
            for (JsonElement m : arr(j, "meta")) meta.add(m.getAsString());
            txt(String.join(" · ", meta), ix() + 18, y + 16.5f, iw() - 18, 12, GRIS_2, false);
            float frac = repos ? 1f - left / (float) total : left / (float) total;
            float bx = ix() + 18, bw = iw() - 18;
            pz.px(bx, y + 21.5f, bx + bw, y + 22.5f, 0xFF2C2024);
            pz.px(bx, y + 21.5f, bx + bw * Math.max(0f, Math.min(1f, frac)), y + 22.5f, col);
            float ax = ix() + 18, cy = y + 29;
            boolean koEtat = "down".equals(state) || "ko".equals(state) || "chakra".equals(state);
            if (koEtat) {
                ax += motAction("Réveiller", ax, cy, true, () -> koAct("wake", uuid)) + 9;
                if ("down".equals(state)) ax += motAction("KO", ax, cy, false, () -> koAct("knockout", uuid)) + 9;
                else ax += motAction("Hôpital", ax, cy, false, () -> koAct("hospital", uuid)) + 9;
            } else {
                ax += motAction("Lever l'ATA", ax, cy, true, () -> koAct("lift_ata", uuid)) + 9;
                ax += motAction("Fiche", ax, cy, false, () -> openProfile(uuid)) + 9;
            }
            motAction("TP", ax, cy, false, () -> koAct("tp", uuid));
            y += h;
            pz.filetH(ix() + 18, ix() + iw(), y - 0.5f, SEP);
        }
        return y + 2;
    }

    private void koAct(String op, String uuid) {
        JsonObject o = new JsonObject();
        o.addProperty("a", "ko_act");
        o.addProperty("op", op);
        o.addProperty("t", uuid);
        StaffClient.send(o);
        RebornSounds.uiClick();
    }

    /* =================================================================== carte : zones et hôpitaux */

    private int kindColor0(String kind) {
        return switch (kind) { case "repos" -> C_REST; case "hopital" -> C_HOSP; default -> C_TRAIN; };
    }

    private MapDefs.Def mapDef(JsonObject ko) {
        String world = str(ko.getAsJsonObject("me"), "world");
        String id = null;
        for (JsonElement e : arr(ko, "maps")) {
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

    private JsonObject zoneById(JsonObject ko, String id) {
        for (JsonElement e : arr(ko, "zones")) if (id != null && id.equals(str(e.getAsJsonObject(), "id"))) return e.getAsJsonObject();
        return null;
    }

    private void zones(JsonObject ko, boolean hospitals) {
        JsonObject me = ko.getAsJsonObject("me");
        int panelW = Math.max(150, Math.min(190, (vx1() - vx0()) * 34 / 100));
        mx0 = vx0(); my0 = vy0() + 4 + (hospitals ? 16 : 0); mx1 = vx1() - panelW - 8; my1 = vy1() - 2;
        if (hospitals) hospitalChips(ko);
        if (!camReady) {
            JsonObject focus = hospitals ? zoneById(ko, selId) : null;
            if (focus != null) { camX = (num(focus, "x1") + num(focus, "x2")) / 2.0; camZ = (num(focus, "z1") + num(focus, "z2")) / 2.0; bpp = 0.6; }
            else { camX = me.get("x").getAsDouble(); camZ = me.get("z").getAsDouble(); bpp = 1.5; }
            camReady = true;
        }
        var ctx = ctxCourant;
        pz.vider(ctx);   // ce qui précède (puces des hôpitaux) sous le ciseau du corps
        // fond et carte (dessin immédiat : sous les zones du pinceau, soumises après)
        ctx.fill(mx0, my0, mx1, my1, 0xFF1E1A16);
        ctx.enableScissor(mx0, my0, mx1, my1);
        MapDefs.Def def = mapDef(ko);
        if (def != null) {
            float zoom = (float) (def.blocksPerPixel() / bpp);
            ctx.pose().pushMatrix();
            ctx.pose().translate(sx(def.originX()), sy(def.originZ()));
            ctx.pose().scale(zoom, zoom);
            ctx.blit(RenderPipelines.GUI_TEXTURED, def.texture(), 0, 0, 0f, 0f,
                    def.width(), def.height(), def.width(), def.height(), def.width(), def.height());
            ctx.pose().popMatrix();
        }
        if (bpp < 1.2) {
            double g0 = Math.floor(wx(mx0) / 16) * 16;
            for (double gx = g0; sx(gx) < mx1; gx += 16) pz.fond(Math.round(sx(gx)), my0, Math.round(sx(gx)) + 1, my1, 0x18000000);
            double h0 = Math.floor(wz(my0) / 16) * 16;
            for (double gz = h0; sy(gz) < my1; gz += 16) pz.fond(mx0, Math.round(sy(gz)), mx1, Math.round(sy(gz)) + 1, 0x18000000);
        }
        for (JsonElement e : arr(ko, "zones")) {
            JsonObject z = e.getAsJsonObject();
            if (hospitals && !"hopital".equals(str(z, "kind"))) continue;
            boolean isSel = edit != null && str(z, "id").equals(selId);
            drawZone(isSel ? edit : z, isSel);
            if ("hopital".equals(str(z, "kind"))) drawBeds(z);
        }
        if (edit != null && selId == null) drawZone(edit, true);
        float px = sx(me.get("x").getAsDouble()), pzz = sy(me.get("z").getAsDouble());
        pz.pastille(px, pzz, 2.5f, NUIT);
        pz.pastille(px, pzz, 1.6f, OR);
        pz.vider(ctx);
        ctx.disableScissor();   // revient au ciseau du corps ouvert par l'écran
        pz.cadre(mx0, my0, mx1 - mx0, my1 - my0, FILET_2);
        String help = hospitals ? "Molette : zoom · clic droit : déplacer la carte"
                : "Glisser : tracer · clic : modifier · molette : zoom · clic droit : déplacer";
        pz.fond(mx0, my1 - 9, mx1, my1, 0xC80A0708);
        pz.texte(fit(help, mx1 - mx0 - 8, 11, false), mx0 + 4, my1 - 4.5f, 11, GRIS, false);
        pz.fond(mx1 - 48, my0 + 5, mx1 - 8, my0 + 6, CREME);
        pz.texte(Math.round(bpp * 40) + " blocs", mx1 - 48, my0 + 10, 11, CREME, false);
        int px0 = mx1 + 8, px1 = vx1();
        if (hospitals) hospitalPanel(ko, px0, px1);
        else zonePanel(ko, px0, px1);
    }

    private void drawZone(JsonObject z, boolean sel) {
        int col = kindColor0(str(z, "kind"));
        int x1 = Math.round(sx(Math.min(num(z, "x1"), num(z, "x2")))), x2 = Math.round(sx(Math.max(num(z, "x1"), num(z, "x2")) + 1));
        int y1 = Math.round(sy(Math.min(num(z, "z1"), num(z, "z2")))), y2 = Math.round(sy(Math.max(num(z, "z1"), num(z, "z2")) + 1));
        pz.fond(x1, y1, x2, y2, ((sel ? 0x55 : 0x30) << 24) | (col & 0xFFFFFF));
        pz.cadre(x1, y1, Math.max(2, x2 - x1), Math.max(2, y2 - y1), col);
        if (sel) pz.cadre(x1 - 1, y1 - 1, Math.max(2, x2 - x1) + 2, Math.max(2, y2 - y1) + 2, OR);
        String id = str(z, "id");
        if (x2 - x1 > 24 && y2 - y1 > 9 && !id.isEmpty()) pz.texteC(fit(id, x2 - x1 - 4, 12, true), (x1 + x2) / 2f, (y1 + y2) / 2f, 12, CREME, true);
        if (sel) for (int[] c : new int[][]{{x1, y1}, {x2, y1}, {x1, y2}, {x2, y2}}) {
            pz.fond(c[0] - 3, c[1] - 3, c[0] + 3, c[1] + 3, NUIT);
            pz.fond(c[0] - 2, c[1] - 2, c[0] + 2, c[1] + 2, OR);
        }
    }

    private void drawBeds(JsonObject z) {
        JsonArray beds = arr(z, "beds");
        for (int i = 0; i < beds.size(); i++) {
            JsonObject b = beds.get(i).getAsJsonObject();
            int bx = Math.round(sx(b.get("x").getAsDouble())), bz = Math.round(sy(b.get("z").getAsDouble()));
            boolean busy = !str(b, "who").isEmpty();
            pz.fond(bx - 3, bz - 2, bx + 4, bz + 3, NUIT);
            pz.fond(bx - 2, bz - 1, bx + 3, bz + 2, busy ? ROUGE : CREME);
            if (bpp < 0.8) pz.texte(String.valueOf(i + 1), bx - 1, bz - 6, 11, CREME, true);
        }
    }

    /** Petit bouton à contour (11 de haut) ; renvoie sa largeur. */
    private int petit(int x, int y, String label, boolean actif, Runnable r) {
        int w = Math.round(pz.largeur(label, 12, actif)) + 10, h = 11;
        boolean hov = hover(x, y, w, h);
        pz.fond(x, y, x + w, y + h, actif ? LAQUE : hov ? NUIT_3 : NUIT_2);
        pz.cadre(x, y, w, h, actif ? LAQUE_HAUT : hov ? OR_DOUX : FILET_2);
        pz.texteC(label, x + w / 2f, y + 5.5f, 12, CREME, actif);
        hits.add(new Hit(x, y, w, h, () -> { r.run(); RebornSounds.uiClick(); }, null, null));
        return w;
    }

    private void zonePanel(JsonObject ko, int x0, int x1) {
        int y = vy0() + 4;
        int w = x1 - x0;
        if (edit != null) {
            boolean fresh = selId == null;
            boolean hosp = "hopital".equals(str(edit, "kind"));
            txt(fresh ? "Nouvelle zone" : "Zone « " + selId + " »", x0, y + 5, w, 15, CREME, true);
            y += 14;
            String[][] kinds = {{"entrainement", "Entraînement"}, {"repos", "Repos"}, {"hopital", "Hôpital"}};
            float kx = x0;
            for (String[] k : kinds) {
                boolean on = k[0].equals(str(edit, "kind"));
                pz.pastille(kx + 2, y + 4, 1.5f, kindColor0(k[0]));
                kx += 6 + mot(k[1], kx + 6, y + 4, 13, on, () -> { edit.addProperty("kind", k[0]); dirty = true; }) + 8;
            }
            y += 12;
            pz.texte("Nom", x0, y + 3, 12, GRIS, false);
            fieldAt(nameBox, x0, y + 8, w, str(edit, "id"));
            y += 30;
            if (hosp) {
                pz.texte("Village", x0, y + 3, 12, GRIS, false);
                fieldAt(villageBox, x0, y + 8, w, str(edit, "village"));
                y += 30;
            }
            pz.texte("Hauteur : bloc " + num(edit, "y0") + " → " + num(edit, "y1"), x0, y + 3, 12, GRIS, false);
            y += 9;
            int bx = x0;
            pz.texte("Bas", bx, y + 5.5f, 12, GRIS_2, false);
            bx += 16;
            bx += petit(bx, y, "−", false, () -> bump("y0", -1)) + 2;
            bx += petit(bx, y, "+", false, () -> bump("y0", 1)) + 8;
            pz.texte("Haut", bx, y + 5.5f, 12, GRIS_2, false);
            bx += 19;
            bx += petit(bx, y, "−", false, () -> bump("y1", -1)) + 2;
            petit(bx, y, "+", false, () -> bump("y1", 1));
            y += 14;
            petit(x0, y, "Ma hauteur", false, () -> {
                int my = num(ko.getAsJsonObject("me"), "y");
                edit.addProperty("y0", my - 2);
                edit.addProperty("y1", my + 10);
                dirty = true;
            });
            y += 15;
            int sizeX = Math.abs(num(edit, "x2") - num(edit, "x1")) + 1, sizeZ = Math.abs(num(edit, "z2") - num(edit, "z1")) + 1;
            pz.texte("Taille " + sizeX + " × " + sizeZ + " blocs", x0, y + 3, 12, GRIS_2, false);
            y += 10;
            int ax = x0;
            ax += petit(ax, y, fresh ? "Créer" : "Enregistrer", fresh || dirty, this::saveZone) + 4;
            if (!fresh) {
                ax += petit(ax, y, "Voir en jeu", false, () -> zoneMsg("zone_show", selId)) + 4;
                ax += petit(ax, y, "TP", false, () -> zoneMsg("zone_tp", selId)) + 4;
                if (ax + 40 > x1) { ax = x0; y += 14; }
                petit(ax, y, "Suppr.", false, () -> { zoneMsg("zone_delete", selId); edit = null; selId = null; });
            } else petit(ax, y, "Annuler", false, () -> edit = null);
            y += 18;
        } else {
            for (String l : wrap("Glisse sur la carte pour tracer une zone, ou choisis-en une ci-dessous.", w, 12)) {
                pz.texte(l, x0, y + 3, 12, GRIS, false);
                y += 9;
            }
            y += 6;
        }
        pz.filetH(x0, x1, y, FILET);
        y += 4;
        for (JsonElement e : arr(ko, "zones")) {
            JsonObject z = e.getAsJsonObject();
            if (y > vy1() - 14) break;
            String id = str(z, "id");
            boolean sel = id.equals(selId);
            boolean hovRow = hover(x0, y, w, 13);
            if (sel || hovRow) pz.fond(x0 - 2, y, x1, y + 13, sel ? NUIT_3 : NUIT_2);
            pz.pastille(x0 + 2, y + 6.5f, 1.5f, kindColor0(str(z, "kind")));
            txt(id, x0 + 8, y + 6.5f, w - 50, 13, sel ? CREME : GRIS, sel);
            int sx2 = Math.abs(num(z, "x2") - num(z, "x1")) + 1, sz2 = Math.abs(num(z, "z2") - num(z, "z1")) + 1;
            pz.texteD(sx2 + "×" + sz2, x1, y + 6.5f, 11, GRIS_2, false);
            hits.add(new Hit(x0, y, w, 13, () -> selectZone(z, true), null, null));
            y += 14;
        }
    }

    private void fieldAt(EditBox box, int x, int y, int w, String value) {
        champ(box, x, y, w, null);
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

    private List<JsonObject> hospitals(JsonObject ko) {
        List<JsonObject> out = new ArrayList<>();
        for (JsonElement e : arr(ko, "zones")) if ("hopital".equals(str(e.getAsJsonObject(), "kind"))) out.add(e.getAsJsonObject());
        return out;
    }

    private void hospitalChips(JsonObject ko) {
        List<JsonObject> hs = hospitals(ko);
        if (selId == null && !hs.isEmpty()) { selId = str(hs.get(0), "id"); camReady = false; }
        float x = vx0();
        int y = vy0() + 6;
        for (JsonObject h : hs) {
            String id = str(h, "id");
            x += mot(id, x, y, 13, id.equals(selId), () -> { selId = id; camReady = false; }) + 9;
        }
        motAction("+ Nouvel hôpital", x, y, true, () -> {
            outil = "zones";
            StaffClient.toast("Trace la zone sur la carte puis choisis le type Hôpital.");
        });
    }

    private void hospitalPanel(JsonObject ko, int x0, int x1) {
        int y = vy0() + 20, w = x1 - x0;
        JsonObject h = zoneById(ko, selId);
        if (h == null) {
            for (String l : wrap("Aucun hôpital pour l'instant. Trace une zone de type Hôpital dans l'outil Zones.", w, 12)) {
                pz.texte(l, x0, y + 3, 12, GRIS, false);
                y += 9;
            }
            return;
        }
        JsonArray beds = arr(h, "beds");
        int busy = 0;
        for (JsonElement e : beds) if (!str(e.getAsJsonObject(), "who").isEmpty()) busy++;
        txt("Hôpital « " + selId + " »", x0, y + 5, w, 15, CREME, true);
        y += 13;
        String v = str(h, "village");
        pz.texte("Village : " + (v.isEmpty() ? "tous (par défaut)" : v), x0, y + 3, 12, GRIS, false);
        y += 9;
        pz.texte(beds.size() + " lit(s) · " + (beds.size() - busy) + " libre(s) · " + busy + " occupé(s)", x0, y + 3, 12, GRIS_2, false);
        y += 10;
        int bx = x0;
        bx += petit(bx, y, "+ Ajouter des lits", true, () -> zoneMsg("bed_pick", selId)) + 4;
        petit(bx, y, "TP", false, () -> zoneMsg("zone_tp", selId));
        y += 16;
        pz.filetH(x0, x1, y, FILET);
        y += 4;
        if (beds.isEmpty()) for (String l : wrap("Pas encore de lit : « Ajouter des lits », vise chaque meuble et clic droit.", w, 12)) {
            pz.texte(l, x0, y + 3, 12, GRIS_2, false);
            y += 9;
        }
        for (int i = 0; i < beds.size(); i++) {
            if (y > vy1() - 14) { pz.texte("… et " + (beds.size() - i) + " autre(s)", x0, y + 4, 12, GRIS_2, false); break; }
            JsonObject b = beds.get(i).getAsJsonObject();
            String who = str(b, "who");
            pz.pastille(x0 + 2, y + 6, 1.5f, who.isEmpty() ? OK : ROUGE);
            pz.texte("Lit " + (i + 1), x0 + 8, y + 6, 13, CREME, false);
            txt(who.isEmpty() ? "libre" : who, x0 + 36, y + 6, w - 36 - 60, 12, who.isEmpty() ? OK : ROUGE, false);
            final int idx = i;
            float rw = pz.largeur("Retirer", 12, false);
            motCouleur("Retirer", x1 - rw, y + 6, 12, GRIS_2, () -> bedMsg("bed_remove", idx));
            motCouleur("TP", x1 - rw - 16, y + 6, 12, GRIS, () -> bedMsg("bed_tp", idx));
            y += 13;
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

    private void loadSettings(JsonObject ko) {
        JsonObject s = ko.getAsJsonObject("settings");
        if (s == null) return;
        for (Map.Entry<String, JsonElement> e : s.entrySet()) local.put(e.getKey(), e.getValue().getAsInt());
    }

    private int v(String k) { return local.getOrDefault(k, 0); }

    private int reglages(JsonObject ko, int y) {
        boolean admin = grade(ko) >= 3;
        int x0 = vx0(), x1 = vx1(), w = x1 - x0;
        pz.texte("Le parcours d'un blessé", x0, y + 5, 15, CREME, true);
        int w1 = largeurBouton("Appliquer"), w2 = largeurBouton("Valeurs d'origine");
        refusVerrou = "Réservé aux Admins.";
        bouton(x1 - w1, y - 2, w1, "Appliquer", settingsDirty, () -> {
            JsonObject o = new JsonObject();
            o.addProperty("a", "settings_set");
            JsonObject vals = new JsonObject();
            local.forEach(vals::addProperty);
            o.add("v", vals);
            StaffClient.send(o);
            settingsDirty = false;
            RebornSounds.confirm();
        }, !admin);
        bouton(x1 - w1 - w2 - 4, y - 2, w2, "Valeurs d'origine", false, () -> {
            StaffClient.send("settings_reset");
            settingsDirty = false;
        }, !admin);
        refusVerrou = null;
        y += 14;
        pz.texte(admin ? "Tire les poignées d'or pour allonger ou raccourcir une étape." : "Lecture seule : réservé aux Admins.",
                x0, y + 3, 12, admin ? GRIS_2 : ROUGE, false);
        y += 12;
        // frise (sans kanji : couleur + nom + valeur)
        int fy = y, fh = 28, fx0 = x0, fx1 = x1;
        String[][] stages = {{"terre", "À terre", "" + TERRE}, {"inco", "Inconscient", "" + ROUGE},
                {"hop", "Hôpital", "" + C_HOSP}, {"pleine", "ATA", "" + 0xFFD0885C}, {"ok", "Rétabli", "" + OK}};
        double[] weight = {Math.sqrt(v("terre") / 120.0) * 10, Math.sqrt(v("inco") / 900.0) * 10, 2.4,
                Math.sqrt(v("pleine") / 60.0) * 10, 2.4};
        double sum = 0;
        for (double d : weight) sum += Math.max(1.6, d);
        int cx = fx0;
        for (int i = 0; i < stages.length; i++) {
            int sw = (int) Math.round((fx1 - fx0) * Math.max(1.6, weight[i]) / sum);
            if (i == stages.length - 1) sw = fx1 - cx;
            int col = Integer.parseInt(stages[i][2]);
            pz.fond(cx, fy, cx + sw - 1, fy + fh, GardeArt.sombre(col, 0.35f));
            pz.fond(cx, fy, cx + sw - 1, fy + 2, col);
            String val = switch (stages[i][0]) {
                case "terre" -> v("terre") + " s";
                case "inco" -> (v("inco") / 60) + " min" + (v("inco") % 60 > 0 ? " " + v("inco") % 60 + " s" : "");
                case "hop" -> "réveil " + v("plancher") + " %";
                case "pleine" -> v("pleine") + " / " + v("allegee") + " min";
                default -> "";
            };
            pz.texteC(fit(stages[i][1], sw - 6, 13, true), cx + sw / 2f, fy + 10, 13, CREME, true);
            if (!val.isEmpty()) pz.texteC(fit(val, sw - 6, 12, false), cx + sw / 2f, fy + 20, 12, GRIS, false);
            String key = stages[i][0];
            if (key.equals("terre") || key.equals("inco") || key.equals("pleine")) {
                int gx = cx + sw - 3;
                pz.fond(gx - 1, fy + 5, gx + 3, fy + fh - 5, NUIT);
                pz.fond(gx, fy + 6, gx + 2, fy + fh - 6, OR);
                double perPx = switch (key) { case "terre" -> 1.0; case "inco" -> 5.0; default -> 0.25; };
                int[] lim = switch (key) { case "terre" -> new int[]{10, 120}; case "inco" -> new int[]{60, 900}; default -> new int[]{1, 60}; };
                if (admin) sliders.add(new Slider(key, gx - 3, fy, 9, fh, lim[0], lim[1], true, perPx));
            }
            if (key.equals("inco")) {
                int hx = cx + (int) Math.round(sw * Math.min(0.95, v("hopital") / (double) Math.max(1, v("inco"))));
                pz.fond(hx, fy + fh, hx + 1, fy + fh + 6, OR);
                pz.texte("/hopital " + v("hopital") + " s", hx - 20, fy + fh + 10, 11, GRIS_2, false);
                if (admin) sliders.add(new Slider("hopital", hx - 4, fy + fh, 8, 8, 0, 600, true, 4.0));
            }
            cx += sw;
        }
        y = fy + fh + 18;
        pz.texte("Peur : au moins " + v("peur") + " min après le KO", x0, y + 3, 12, ROUGE, false);
        y += 14;
        // seuils de soin + réglettes
        int colW = (w - 12) / 2;
        int lx = x0;
        pz.capitales("Seuils de soin", lx, y + 3, 11, GRIS_2);
        int hx0 = lx, hx1 = lx + colW, hy = y + 22;
        pz.px(hx0, hy, hx1, hy + 3, 0xFF3A2026);
        int pl = hx0 + (hx1 - hx0) * v("plancher") / 100, r1 = hx0 + (hx1 - hx0) * v("rang1") / 100;
        pz.px(hx0, hy, pl, hy + 3, ROUGE);
        marker(pl, hy, "Plancher " + v("plancher") + " %", 0);
        marker(r1, hy, "Médic 1 · " + v("rang1") + " %", 0);
        marker(hx1, hy, "Rang 2 · 100 %", 1);
        if (admin) sliders.add(new Slider("plancher", hx0, hy - 3, hx1 - hx0, 9, 5, Math.max(6, v("rang1") - 5), false, 0));
        pz.texte("repos, bandage, hôpital", hx0, hy + 9, 11, GRIS_2, false);
        int rx = lx + colW + 12;
        sliderRow(rx, y, colW, "Paume de soin", "soin complet ≈ " + Math.max(1, Math.round(1000f / Math.max(1, v("paume")) / 1.5f)) + " s", "paume", 5, 50, admin);
        sliderRow(rx, y + 18, colW, "Chuchotement", v("chuchot") + " blocs", "chuchot", 1, 16, admin);
        sliderRow(rx, y + 36, colW, "Peur", v("peur") + " min", "peur", 0, 60, admin);
        return y + 56;
    }

    private void marker(int x, int y, String label, int ancre) {
        pz.fond(x, y - 3, x + 1, y + 6, OR);
        if (ancre == 1) pz.texteD(label, x + 1, y - 7, 11, CREME, false);
        else pz.texteC(label, x, y - 7, 11, CREME, false);
    }

    private void sliderRow(int x, int y, int w, String label, String value, String key, int min, int max, boolean admin) {
        pz.texte(label, x, y + 3, 12, CREME, false);
        pz.texteD(value, x + w, y + 3, 12, OR, false);
        int ty = y + 11, tx0 = x + 2, tx1 = x + w - 2;
        pz.px(tx0, ty, tx1, ty + 1, FILET_2);
        int kx = tx0 + (int) Math.round((tx1 - tx0) * (v(key) - min) / (double) Math.max(1, max - min));
        pz.px(tx0, ty, kx, ty + 1, LAQUE_HAUT);
        pz.fond(kx - 2, ty - 3, kx + 2, ty + 4, OR);
        if (admin) sliders.add(new Slider(key, tx0, ty - 4, tx1 - tx0, 9, min, max, false, 0));
    }

    /* =================================================================== banc d'essai */

    void debugPage(int p) { }

    void debugKoTab(String id) {
        if (!"outils".equals(tab)) switchTab("outils");
        outil = id;
        scroll = 0;
        camReady = false;
    }

    void debugSelect(String id) {
        JsonObject ko = StaffClient.koSnapshot();
        JsonObject z = ko == null ? null : zoneById(ko, id);
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
        return large() && ("zones".equals(outil) || "hopitaux".equals(outil)) && x >= mx0 && x < mx1 && y >= my0 && y < my1 - 9;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        double x = e.x(), y = e.y();
        if (menu == null && "reglages".equals(outil) && e.button() == 0) for (Slider s : sliders) {
            if (x >= s.x() && x < s.x() + Math.max(8, s.w()) && y >= s.y() && y < s.y() + s.h()) {
                dragSlider = s;
                dragSliderStart = v(s.key());
                dragStartX = x;
                if (!s.delta()) setSlider(s, x);
                return true;
            }
        }
        if (menu == null && inMap(x, y)) {
            JsonObject ko = StaffClient.koSnapshot();
            if (e.button() == 1 || e.button() == 2) { dragMode = "pan"; dragStartX = x; dragStartY = y; dragWx = camX; dragWz = camZ; return true; }
            if ("hopitaux".equals(outil) || ko == null) return true;
            double wxv = wx(x), wzv = wz(y);
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
            JsonArray zs = arr(ko, "zones");
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
            selId = null;
            edit = new JsonObject();
            edit.addProperty("id", "");
            edit.addProperty("kind", "entrainement");
            edit.addProperty("village", "");
            edit.addProperty("world", str(ko.getAsJsonObject("me"), "world"));
            int bxv = (int) Math.floor(wxv), bzv = (int) Math.floor(wzv);
            edit.addProperty("x1", bxv); edit.addProperty("z1", bzv);
            edit.addProperty("x2", bxv); edit.addProperty("z2", bzv);
            int myy = num(ko.getAsJsonObject("me"), "y");
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
            if (num(edit, "x2") - num(edit, "x1") < 1 || num(edit, "z2") - num(edit, "z1") < 1) edit = null;
            else { setFocused(nameBox); nameBox.setFocused(true); }
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
        return super.mouseScrolled(x, y, h, v);
    }
}
