package fr.reborn.hud.animation;

import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotBlit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu des mouvements Reborn, au niveau de la fiche shinobi (DA §4.1, §6), version épurée. Signature :
 * l'<b>éventail</b> (sensu) entrouvert à cheval sur le haut de la planche laquée ; onglets en plaques segmentées
 * ({@code Animations} / {@code Options}), lignes laquées sobres (bande vermillon pour le choix en cours), marqueurs
 * pixel art. Le monde reste visible ; aperçu du perso à droite. Décor : {@code textures/gui/animations/*}.
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
    private static final int MAX_EMOTE_ROWS = 9;

    private static final int PANEL_X = 28;
    private static final int PANEL_W = 220;
    private static final int TAB_H = 24;
    private static final int ROW_H = 20;
    private static final int HEADER_H = 42;

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
    private int panelH() { return Math.min(340, HEADER_H + TAB_H + rowCount() * ROW_H + 12); }
    private int panelY() { return Math.max(40, (this.height - panelH()) / 2 + 8); }
    private int tabsY() { return panelY() + HEADER_H; }
    private int firstRowY() { return tabsY() + TAB_H; }

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

    // ─── DA : l'éventail (sensu) sur la planche laquée (tools/gen_animations_textures.py) ───
    private static Identifier tex(String n) { return DaKit.tex("animations", n); }

    private static final Identifier PANNEAU = tex("panneau"), EVENTAIL = tex("eventail"), LIGNES = tex("lignes"),
        PIECES = tex("pieces");
    private int hoveredRow = -1;

    /** Le monde reste visible : simple voile et vignette. */
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0x2408040C);
        DaKit.etire(ctx, fr.reborn.hud.menu.settings.ReglagesArt.VIGNETTE, 0, 0, this.width, this.height, 160, 90,
            DaKit.blanc(0.7f));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        Font f = this.font;
        int py = panelY(), ph = panelH();
        DaKit.coupe(ctx, PANNEAU, PANEL_X - 2, py - 2, PANEL_W + 8, ph + 8, 0, 0, 228, 348, 18, 18, 228, 348);
        DaKit.blit(ctx, EVENTAIL, PANEL_X + PANEL_W / 2 - 66, py + 20 - 58, 0, 0, 132, 64, 132, 64);
        DaKit.arcX1(ctx, f, DaKit.arcade("Mouvements"), PANEL_X + PANEL_W / 2f, py + 28, DaKit.OR_CLAIR, 1, true);

        int tabW = (PANEL_W - 2 * 14 + 2) / 2;
        drawTab(ctx, f, PANEL_X + 14, tabsY(), tabW - 2, "Animations", section == Section.ANIMATIONS, mouseX, mouseY);
        drawTab(ctx, f, PANEL_X + 14 + tabW, tabsY(), tabW - 2, "Options", section == Section.OPTIONS, mouseX, mouseY);

        List<Row> rows = rows();
        int y = firstRowY();
        int hov = -1;
        LotBlit marq = LotBlit.ouvrir(ctx, PIECES, 64, 16);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            boolean hovered = mouseX >= PANEL_X + 8 && mouseX < PANEL_X + PANEL_W - 8 && mouseY >= y && mouseY < y + ROW_H;
            if (hovered) hov = i;
            boolean live = !row.placeholder();
            boolean choisi = "●".equals(row.marker());
            if ((hovered && live) || choisi) {
                DaKit.coupeH(ctx, LIGNES, PANEL_X + 8, y + 1, PANEL_W - 16 + 2, 0, choisi ? 20 : 0, 208, 20, 6, 208, 40);
            }
            int col = !live ? DaKit.INACTIF : hovered || choisi ? DaKit.CREME : DaKit.CREME_VAL;
            DaKit.inter9(ctx, f, DaKit.inter(libelle(row)), PANEL_X + 20, y + ROW_H / 2f, col, 0);
            if (!live && !row.label().startsWith("Aucune")) {
                DaKit.inter7(ctx, f, DaKit.inter("bientôt"), PANEL_X + PANEL_W - 18, y + ROW_H / 2f, DaKit.INACTIF, 2);
            }
            int u = marqueur(row);
            if (u >= 0) marq.blit(PANEL_X + PANEL_W - 24, y + (ROW_H - 7) / 2, u, 0, 7, 7);
            y += ROW_H;
        }
        marq.fermer();
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
        DaKit.aides(ctx, f, fr.reborn.hud.menu.settings.ReglagesArt.PIECES, 0, 0, 120, 40,
            new String[][]{{"Clic", "choisir"}, {"Molette", "défiler"}, {"Échap", "fermer"}}, this.width - 12,
            this.height - 10, 1f);
    }

    /** Libellé sans les symboles (dessinés en marqueurs pixel art). */
    private static String libelle(Row row) {
        String l = row.label();
        if (l.startsWith("‹ ") || l.startsWith("▲ ") || l.startsWith("▼ ")) return l.substring(2);
        return l;
    }

    /** Abscisse du marqueur de ligne dans pieces.png (−1 = aucun). */
    private static int marqueur(Row row) {
        if (row.marker() != null) {
            return switch (row.marker()) {
                case "●" -> 0;
                case "○" -> 8;
                case "▸" -> 16;
                case "■" -> 24;
                default -> -1;
            };
        }
        if (row.label().startsWith("‹")) return 32;
        if (row.label().startsWith("▲")) return 40;
        if (row.label().startsWith("▼")) return 48;
        return -1;
    }

    private void drawTab(GuiGraphicsExtractor ctx, Font f, int x, int y, int w, String label, boolean active, int mx, int my) {
        boolean hovered = mx >= x && mx < x + w && my >= y && my < y + 16;
        fr.reborn.hud.menu.settings.ReglagesArt.segment(ctx, x, y, w, active ? 2 : hovered ? 1 : 0);
        DaKit.inter8(ctx, f, DaKit.inter(label), x + w / 2f, y + 8,
            active ? DaKit.CREME : hovered ? DaKit.CREME_VAL : DaKit.NEUTRE_TXT, 1);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y(); int button = event.button();
        if (button == 0) {
            // Onglets.
            int tabW = (PANEL_W - 2 * 14 + 2) / 2;
            int ty = tabsY();
            if (mouseY >= ty && mouseY < ty + 16 && mouseX >= PANEL_X + 14 && mouseX < PANEL_X + PANEL_W - 14) {
                Section s = mouseX < PANEL_X + 14 + tabW ? Section.ANIMATIONS : Section.OPTIONS;
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
                if (mouseX >= PANEL_X + 8 && mouseX < PANEL_X + PANEL_W - 8 && mouseY >= y && mouseY < y + ROW_H) {
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
