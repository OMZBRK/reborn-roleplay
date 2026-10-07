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
    private static final int LACQUER = 0xFFA0182B, LACQUER_HI = 0xFFC01E35, LACQUER_LO = 0xFF7A1322;
    private static final int GOLD = 0xFFD9A95E, GOLD_SOFT = 0xFF3A2C14, IVORY = 0xFFF5E9D0, IVORY_2 = 0xFFC2B59A;
    private static final int WASHI = 0xFFE3D1A8, WASHI_2 = 0xFFD4BF91, WOOD = 0xFF5A3A22, WOOD_DARK = 0xFF2E1C11;
    private static final int INK = 0xFF2A1A10, INK_SOFT = 0xFF6B5434, FUDA = 0xFFF0E2C2, FUDA_2 = 0xFFE2CFA4;
    private static final int OK = 0xFF2F7A3A;

    private static final String[][] TABS = {
            {"alerts", "Alertes"}, {"players", "Joueurs"}, {"chat", "Chat staff"}, {"journal", "Journal"}, {"cmds", "Commandes"}};
    private static final String[][] LOG_FILTERS = {
            {"all", "Tout"}, {"soin", "Soins"}, {"ko", "KO / ATA"}, {"sanction", "Sanctions"}, {"tp", "Téléportations"}, {"monde", "Monde"}};

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final List<Hit> hits = new ArrayList<>();
    private String tab = "alerts";
    private String profileUuid;
    private JsonObject confirm;
    private final Set<Integer> flags = new HashSet<>();
    private long sealAt;
    private String logFilter = "all";
    private int scroll, contentH;
    private long openedAt;
    private int ticks;
    private int mouseX, mouseY;
    private EditBox chatBox, noteBox, searchBox;

    public StaffScreen() {
        super(Component.literal("Poste de garde"));
    }

    /* =================================================================== cycle de vie */

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
        openedAt = System.currentTimeMillis();
        chatBox = box("Écrire au staff…", 200);
        noteBox = box("Note pour le casier…", 160);
        searchBox = box("Chercher : ata, zone, météo…", 40);
        searchBox.setResponder(v -> scroll = 0);
        StaffClient.send("refresh");
        RebornSounds.uiClick();
    }

    private EditBox box(String hint, int max) {
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

    private int sx0() { return Math.round(width * 0.07f); }
    private int sx1() { return width - sx0(); }
    private int sy0() { return 46; }
    private int sy1() { return height - 28; }

    /** Zone qui défile (dans le papier), selon l'onglet : la barre de chat en bas, la recherche en haut. */
    private int vy0() { return sy0() + 6 + ("cmds".equals(tab) ? 18 : 0); }
    private int vy1() { return sy1() - 6 - ("chat".equals(tab) ? 18 : 0); }
    private int vx0() { return sx0() + 8; }
    private int vx1() { return sx1() - 8; }

    /* =================================================================== rendu */

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        float appear = Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f);
        int center = (Math.round(0x50 * appear) << 24) | 0x08040C, edge = (Math.round(0xA8 * appear) << 24) | 0x08040C;
        ctx.fill(0, 0, width, height, center);
        ctx.fillGradient(0, 0, width, height / 4, edge, 0);
        ctx.fillGradient(0, height - height / 4, width, height, 0, edge);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        mouseX = mx;
        mouseY = my;
        hits.clear();
        JsonObject snap = StaffClient.snapshot();

        frame(ctx);
        tabs(ctx, snap);
        plaque(ctx, plaqueTitle(snap));

        // contenu défilant
        ctx.enableScissor(vx0() - 2, vy0(), vx1() + 2, vy1());
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
        super.extractRenderState(ctx, mx, my, delta);   // champs de saisie
        toasts(ctx);
    }

    private String plaqueTitle(JsonObject snap) {
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

    private void frame(GuiGraphicsExtractor ctx) {
        int x0 = sx0(), x1 = sx1(), y0 = sy0(), y1 = sy1();
        ctx.fill(x0 - 5, y0 - 5, x1 + 5, y1 + 5, 0x99000000);                 // ombre
        ctx.fill(x0 - 4, y0 - 4, x1 + 4, y1 + 4, WOOD_DARK);
        ctx.fill(x0 - 3, y0 - 3, x1 + 3, y1 + 3, WOOD);
        DrawHelpers.outlinedRect(ctx, x0 - 2, y0 - 2, x1 - x0 + 4, y1 - y0 + 4, 0, GOLD_SOFT);
        ctx.fill(x0, y0, x1, y1, WASHI);
        // fibres du washi : quelques traits plus sombres, fixes
        long seed = 0x5DEECE66DL;
        for (int i = 0; i < 70; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int fx = x0 + (int) ((seed >>> 33) % Math.max(1, x1 - x0 - 6));
            int fy = y0 + (int) ((seed >>> 17) % Math.max(1, y1 - y0 - 2));
            ctx.fill(fx, fy, fx + 3 + (int) ((seed >>> 50) % 5), fy + 1, 0x14462810);
        }
    }

    private void plaque(GuiGraphicsExtractor ctx, String title) {
        Component t = RebornFont.display(title);
        int tw = font.width(t);
        int pw = tw + 34, x = width / 2 - pw / 2, y = 8;
        ctx.fill(x + 10, 0, x + 11, y, GOLD);
        ctx.fill(x + pw - 11, 0, x + pw - 10, y, GOLD);
        ctx.fill(x - 1, y - 1, x + pw + 1, y + 19, WOOD_DARK);
        ctx.fillGradient(x, y, x + pw, y + 18, LACQUER, LACQUER_LO);
        DrawHelpers.outlinedRect(ctx, x, y, pw, 18, 0, GOLD);
        // pastille d'avertissement
        ctx.fill(x + 5, y + 4, x + 15, y + 14, GOLD);
        ctx.text(font, Component.literal("!"), x + 9, y + 5, LACQUER_LO, false);
        ctx.text(font, t, x + 21, y + 5, IVORY, true);
    }

    private void tabs(GuiGraphicsExtractor ctx, JsonObject snap) {
        int x = sx0() + 4, y = 30;
        for (String[] t : TABS) {
            boolean sel = t[0].equals(tab);
            Component c = RebornFont.body(t[1]);
            int w = font.width(c) + 14;
            int top = sel ? y - 2 : y;
            ctx.fill(x + w / 2, top - 4, x + w / 2 + 1, top, GOLD);                // cordon
            ctx.fill(x, top, x + w, y + 13, WOOD_DARK);
            ctx.fill(x + 1, top, x + w - 1, y + 12, sel ? LACQUER : (hover(x, top, w, 13 + y - top) ? 0xFF6E4A2C : WOOD));
            if (sel) DrawHelpers.outlinedRect(ctx, x, top, w, y + 13 - top, 0, GOLD);
            ctx.text(font, c, x + 7, y + 3, sel ? IVORY : IVORY_2, false);
            final String id = t[0];
            hits.add(new Hit(x, top, w, y + 13 - top, () -> switchTab(id)));
            x += w + 4;
        }
        if (snap != null) {
            Component g = RebornFont.body("Ton grade : ").append(RebornFont.bold(str(snap, "gradeName")));
            int gw = font.width(g) + 10;
            int gx = sx0(), gy = height - 21;   // pied de l'écran, à gauche de « Fermer »
            ctx.fill(gx, gy, gx + gw, gy + 13, 0xB30A0608);
            DrawHelpers.outlinedRect(ctx, gx, gy, gw, 13, 0, GOLD_SOFT);
            ctx.text(font, g, gx + 5, gy + 3, GOLD, false);
        }
    }

    private void switchTab(String id) {
        if (!id.equals(tab)) RebornSounds.uiClick();
        tab = id;
        scroll = 0;
        if (!"players".equals(id)) profileUuid = null;
        confirm = null;
    }

    private void closeButton(GuiGraphicsExtractor ctx) {
        int w = 120, x = width / 2 - w / 2, y = height - 22;
        ctx.fill(x, y, x + w, y + 16, WOOD_DARK);
        ctx.fill(x + 1, y + 1, x + w - 1, y + 15, hover(x, y, w, 16) ? 0xFF6E4A2C : WOOD);
        DrawHelpers.outlinedRect(ctx, x + 2, y + 2, w - 4, 12, 0, GOLD_SOFT);
        Component c = RebornFont.body("Fermer");
        ctx.text(font, c, x + w / 2 - font.width(c) / 2, y + 4, IVORY, false);
        hits.add(new Hit(x, y, w, 16, this::onClose));
    }

    private void toasts(GuiGraphicsExtractor ctx) {
        int y = height - 46;
        for (StaffClient.Toast t : StaffClient.toasts()) {
            Component c = RebornFont.body(t.text());
            int w = font.width(c) + 14, x = width / 2 - w / 2;
            ctx.fill(x, y, x + w, y + 14, 0xF0150A0D);
            DrawHelpers.outlinedRect(ctx, x, y, w, 14, 0, GOLD);
            ctx.text(font, c, x + 7, y + 3, IVORY, false);
            y -= 17;
        }
    }

    private void layoutBoxes(GuiGraphicsExtractor ctx) {
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

    private int alerts(GuiGraphicsExtractor ctx, JsonObject snap, int y) {
        y = section(ctx, "Alertes récentes", y);
        JsonArray al = arr(snap, "alerts");
        if (al.isEmpty()) return text(ctx, "Aucune alerte. Le village est calme.", vx0(), y + 2, INK_SOFT) + 6;
        for (JsonElement e : al) {
            JsonObject a = e.getAsJsonObject();
            int x = vx0(), w = vx1() - vx0(), h = 46;
            fuda(ctx, x, y, w, h);
            head(ctx, str(a, "uuid"), x + 12, y + 6, 20);
            int tx = x + 38;
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
            y += h + 6;
        }
        return y;
    }

    private int players(GuiGraphicsExtractor ctx, JsonObject snap, int y) {
        y = section(ctx, "Joueurs en ligne", y);
        for (JsonElement e : arr(snap, "players")) {
            JsonObject p = e.getAsJsonObject();
            int x = vx0(), w = vx1() - vx0(), h = 24;
            fuda(ctx, x, y, w, h);
            head(ctx, str(p, "uuid"), x + 12, y + 4, 16);
            Component nm = Component.empty()
                    .append(RebornFont.bold(str(p, "rank").isEmpty() ? "" : "[" + str(p, "rank") + "] ").withColor(LACQUER & 0xFFFFFF))
                    .append(RebornFont.bold(str(p, "perso").isEmpty() ? "(sans personnage)" : str(p, "perso")).withColor(INK & 0xFFFFFF))
                    .append(RebornFont.body("  " + str(p, "name") + (str(p, "village").isEmpty() ? "" : " · " + str(p, "village")))
                            .withColor(INK_SOFT & 0xFFFFFF));
            ctx.text(font, nm, x + 34, y + 4, INK, false);
            String st = str(p, "state");
            small(ctx, st.isEmpty() ? "En forme" : st, x + 34, y + 14, st.isEmpty() ? OK : LACQUER_LO);
            String uuid = str(p, "uuid");
            int fw = font.width(RebornFont.body("Fiche")) + 12;
            button(ctx, x + w - fw - 6, y + 5, "Fiche", "punish", () -> openProfile(uuid), false);
            y += h + 4;
        }
        return y;
    }

    private int profile(GuiGraphicsExtractor ctx, JsonObject snap, int y) {
        int g = grade(snap);
        button(ctx, vx0(), y, "← Joueurs", "plain", () -> { profileUuid = null; scroll = 0; StaffClient.clearProfile(); }, false);
        y += 18;
        JsonObject p = StaffClient.profile();
        if (p == null || !profileUuid.equals(str(p, "uuid"))) return text(ctx, "Chargement de la fiche…", vx0(), y, INK_SOFT);
        String uuid = str(p, "uuid");
        int x = vx0(), w = vx1() - vx0();
        int h = 78;
        fuda(ctx, x, y, w, h);
        ctx.fill(x + 9, y + 7, x + 45, y + 43, GOLD);
        head(ctx, uuid, x + 11, y + 9, 32);
        int tx = x + 54;
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
        int bx = x + 10, by = y + h - 15;
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
            ctx.fill(x, y, x + w, y + 16, 0xFFECDCB8);
            DrawHelpers.outlinedRect(ctx, x, y, w, 16, 0, WOOD_DARK);
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
            ctx.fill(ox + 2, oy + 2, ox + cw + 2, oy + ch + 2, 0x4D2E1C11);
            ctx.fill(ox, oy, ox + cw, oy + ch, hov ? 0xFFF7EAD0 : FUDA);
            DrawHelpers.outlinedRect(ctx, ox, oy, cw, ch, 0, WOOD_DARK);
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

    private int chat(GuiGraphicsExtractor ctx, JsonObject snap, int y) {
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

    private int journal(GuiGraphicsExtractor ctx, JsonObject snap, int y) {
        int x = vx0();
        for (String[] f : LOG_FILTERS) {
            boolean sel = f[0].equals(logFilter);
            Component c = RebornFont.body(f[1]);
            int w = font.width(c) + 12;
            ctx.fill(x, y, x + w, y + 13, WOOD_DARK);
            ctx.fill(x + 1, y + 1, x + w - 1, y + 12, sel ? WOOD : (hover(x, y, w, 13) ? 0xFFF7EAD0 : 0xFFEFE3C6));
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
            ctx.fill(vx0(), y, vx0() + w, y + 14, 0x59FFFFFF);
            ctx.fill(vx0(), y, vx0() + 2, y + 14, kc);
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

    private int commands(GuiGraphicsExtractor ctx, JsonObject snap, int y) {
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

    private void confirmModal(GuiGraphicsExtractor ctx, JsonObject snap) {
        JsonObject p = StaffClient.profile();
        if (p == null) { confirm = null; return; }
        hits.clear();   // la fenêtre capte tous les clics
        ctx.fill(0, 0, width, height, 0x99060304);
        int mw = Math.min(330, width - 40), x = width / 2 - mw / 2;
        JsonArray msgs = arr(p, "msgs");
        int shownMsgs = Math.min(msgs.size(), 4);
        int mh = 132 + shownMsgs * 21 + (shownMsgs == 0 ? 12 : 0);
        int y = Math.max(10, height / 2 - mh / 2);
        ctx.fill(x - 4, y - 4, x + mw + 4, y + mh + 4, WOOD_DARK);
        ctx.fill(x - 3, y - 3, x + mw + 3, y + mh + 3, GOLD);
        ctx.fill(x - 2, y - 2, x + mw + 2, y + mh + 2, WOOD);
        ctx.fill(x, y, x + mw, y + mh, WASHI);
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

    private void seal(GuiGraphicsExtractor ctx, int x, int y) {
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
    }

    private void sendSanction(boolean warnOnly) {
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

    private void act(String op, String uuid) {
        JsonObject o = new JsonObject();
        o.addProperty("a", "act");
        o.addProperty("op", op);
        o.addProperty("t", uuid);
        StaffClient.send(o);
        RebornSounds.uiClick();
    }

    private void dismiss(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("a", "dismiss");
        o.addProperty("id", id);
        StaffClient.send(o);
        RebornSounds.uiClick();
    }

    private void openProfile(String uuid) {
        tab = "players";
        profileUuid = uuid;
        scroll = 0;
        requestProfile(uuid);
        RebornSounds.uiClick();
    }

    private void requestProfile(String uuid) {
        JsonObject o = new JsonObject();
        o.addProperty("a", "profile");
        o.addProperty("t", uuid);
        StaffClient.send(o);
    }

    private void sendChat() {
        String m = chatBox.getValue().trim();
        if (m.isEmpty()) return;
        JsonObject o = new JsonObject();
        o.addProperty("a", "chat");
        o.addProperty("m", m);
        StaffClient.send(o);
        chatBox.setValue("");
        scroll = Integer.MAX_VALUE / 2;   // descend au dernier message
    }

    private void prepare(String cmd) {
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

    private int section(GuiGraphicsExtractor ctx, String title, int y) {
        Component c = RebornFont.display(title);
        ctx.text(font, c, vx0(), y + 2, LACQUER_LO, false);
        int tw = font.width(c);
        DrawHelpers.horizontalGradient(ctx, vx0() + tw + 6, y + 6, Math.max(10, (vx1() - vx0() - tw - 6) / 2), 1, LACQUER_LO, 0x007A1322);
        return y + 15;
    }

    private void fuda(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        ctx.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x592E1C11);
        ctx.fillGradient(x, y, x + w, y + h, FUDA, FUDA_2);
        DrawHelpers.outlinedRect(ctx, x, y, w, h, 0, WOOD_DARK);
        ctx.fill(x + 4, y + h / 2 - 1, x + 7, y + h / 2 + 2, WOOD_DARK);   // trou de la ficelle
    }

    private void badge(GuiGraphicsExtractor ctx, int right, int y, String label, boolean strong) {
        Component c = RebornFont.bold(label);
        int w = font.width(c) + 8;
        ctx.fill(right - w, y, right, y + 11, WOOD_DARK);
        ctx.fill(right - w + 1, y + 1, right - 1, y + 10, strong ? GOLD : WASHI_2);
        ctx.text(font, c, right - w + 4, y + 2, WOOD_DARK, false);
    }

    /** Bouton plaquette ; renvoie sa largeur. {@code locked} = visible mais grisé. */
    private int button(GuiGraphicsExtractor ctx, int x, int y, String label, String style, Runnable action, boolean locked) {
        Component c = RebornFont.body(label);
        int w = font.width(c) + 12, h = 12;
        boolean hov = !locked && hover(x, y, w, h);
        int fill = switch (style) {
            case "punish" -> hov ? LACQUER_HI : LACQUER;
            case "watch" -> hov ? 0xFFDDEDC4 : 0xFFCFE3B0;
            default -> hov ? 0xFFF7EAD0 : 0xFFEFE3C6;
        };
        if (locked) fill = 0xFFD8CCB0;
        ctx.fill(x, y + 1, x + w, y + h + 1, WOOD_DARK);                     // relief
        ctx.fill(x, y, x + w, y + h, WOOD_DARK);
        ctx.fill(x + 1, y + (hov ? 0 : 1) - (hov ? 0 : 0), x + w - 1, y + h - 1, fill);
        int color = "punish".equals(style) && !locked ? IVORY : locked ? 0xFF9A8A70 : INK;
        ctx.text(font, c, x + 6, y + 2, color, false);
        if (visible(y, h) || confirm != null) hits.add(new Hit(x, y, w, h, locked ? null : () -> {
            if (action != null) action.run();
        }));
        return w;
    }

    private void head(GuiGraphicsExtractor ctx, String uuid, int x, int y, int size) {
        ctx.fill(x - 1, y - 1, x + size + 1, y + size + 1, WOOD_DARK);
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

    private int text(GuiGraphicsExtractor ctx, String s, int x, int y, int color) {
        ctx.text(font, RebornFont.body(s), x, y, color, false);
        return y + 11;
    }

    private void small(GuiGraphicsExtractor ctx, String s, int x, int y, int color) {
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(0.75f, 0.75f);
        ctx.text(font, RebornFont.body(s), 0, 0, color, false);
        ctx.pose().popMatrix();
    }

    private boolean hover(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** Le bouton est-il dans la zone visible du papier (pas masqué par le défilement) ? */
    private boolean visible(int y, int h) {
        return y >= vy0() - 1 && y + h <= vy1() + 1;
    }

    private String fit(String s, int maxW) {
        if (s == null) return "";
        if (font.width(RebornFont.body(s)) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && font.width(RebornFont.body(t + "…")) > maxW) t = t.substring(0, t.length() - 1);
        return t + "…";
    }

    private List<String> wrap(String s, int maxW) {
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

    private static String str(JsonObject o, String k) {
        return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static int num(JsonObject o, String k) {
        try { return o != null && o.has(k) ? o.get(k).getAsInt() : 0; } catch (RuntimeException e) { return 0; }
    }

    private static boolean bool(JsonObject o, String k) {
        try { return o != null && o.has(k) && o.get(k).getAsBoolean(); } catch (RuntimeException e) { return false; }
    }

    private static JsonArray arr(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray();
    }

    private static String join(JsonArray a) {
        List<String> parts = new ArrayList<>();
        for (JsonElement e : a) parts.add(e.getAsString());
        return String.join(" · ", parts);
    }

    private static String name(JsonObject p) {
        return str(p, "perso").isEmpty() ? str(p, "name") : str(p, "perso");
    }

    private static int grade(JsonObject snap) { return num(snap, "grade"); }

    private static String gradeName(int g) {
        return switch (g) { case 1 -> "Helper"; case 2 -> "Modo"; case 3 -> "Admin"; case 4 -> "Owner"; default -> "Joueur"; };
    }

    private static String gradeShort(int g) { return "[" + gradeName(g) + "]"; }

    private static int gradeColor(int g) {
        return switch (g) { case 1 -> 0xFF2F6A7A; case 2 -> 0xFF8A5A0C; case 3 -> LACQUER; case 4 -> 0xFF3B2A12; default -> INK_SOFT; };
    }

    private static int kindColor(String kind) {
        return switch (kind) {
            case "soin" -> OK;
            case "ko" -> 0xFF6B4FA0;
            case "sanction" -> LACQUER;
            case "tp" -> 0xFF2F6A7A;
            case "monde" -> 0xFF8A5A0C;
            default -> INK_SOFT;
        };
    }
}
