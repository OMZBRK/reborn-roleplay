package fr.reborn.ost.ui;

import fr.reborn.ost.RebornOstClient;
import fr.reborn.ost.audio.OstAudioEngine;
import fr.reborn.ost.audio.OstCategory;
import fr.reborn.ost.audio.OstLibrary;
import fr.reborn.ost.audio.OstPlayback;
import fr.reborn.ost.audio.OstTrack;
import fr.reborn.ost.audio.OstTrackMeta;
import fr.reborn.ost.config.OstConfig;
import fr.reborn.ost.network.OstNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Menu OST — DA Reborn, disposition de la planche validée. Le monde reste visible derrière.
 *
 * <ul>
 *   <li>En-tête : plaque laquée suspendue « MUSIQUES » ; les catégories pendent dessous en onglets.</li>
 *   <li>Gauche : carte « en lecture » en laque noire — grand <b>disque de laque à maki-e</b> qui tourne
 *       pendant la lecture, titre, catégorie, commandes en plaques (précédent / lecture-pause / suivant /
 *       stop, aléatoire, répétition), plaque Mode solo, puis trois <b>cordes de shamisen</b> empilées :
 *       lecture (cliquer = avancer), volume, distance de diffusion — toutes glissables.</li>
 *   <li>Droite : <b>rouleau de papier</b> (baguettes de bois) avec la recherche en tête et la liste
 *       des pistes à l'encre ; piste en cours sur bandeau vermillon ; étoile = favori.</li>
 *   <li>Bas : bande de parchemin d'aide.</li>
 * </ul>
 *
 * <p>Optimisé : l'existence des pochettes / du disque est mise en cache (pas de requête au
 * gestionnaire de ressources à chaque image et pour chaque ligne).
 */
public class OstScreen extends Screen {

    // Palette DA Reborn.
    private static final int GOLD = 0xFFF6CC78, GOLD_D = 0xFFAA8034, CREAM = 0xFFFAEED6, MUTED = 0xFFC8B4A0,
        LACQ = 0xFF5C1418, RED = 0xFFAA1E22, RED_HOV = 0xFFC82A2E, BLACK = 0xE60E0A0C, PLATE = 0xFF3C1E24,
        PLATE_HOV = 0xFF4A2830, PLATE_EDGE = 0xFF6E4646, PAPER = 0xF2EADCB4, PAPER_HOV = 0xF2F4EAD0,
        INK = 0xFF3C2814, INK_MUTED = 0xFF8C6E50, ROD = 0xFF6E4628, STRING = 0xFFE6DCC8, CORD = 0xFFC8A05A;

    private static final Identifier DISC = tex("disc");
    private static final Identifier IC_PREV = tex("beforebutton"), IC_NEXT = tex("afterbutton"), IC_PLAY = tex("playbutton"),
        IC_PAUSE = tex("pausebutton"), IC_STOP = tex("stopbutton");
    private static final int COVER_PX = 64;
    private static final int ROW_H = 20, THUMB = 14;
    private static final float SMALL = 0.75f;
    /** Cache d'existence des textures (évite un getResource par image et par ligne). */
    private static final java.util.Map<Identifier, Boolean> EXISTS = new java.util.concurrent.ConcurrentHashMap<>();

    private static Identifier tex(String n) { return Identifier.fromNamespaceAndPath("reborn-ost", "textures/gui/" + n + ".png"); }

    private static boolean exists(Identifier id) {
        if (id == null) return false;
        return EXISTS.computeIfAbsent(id, k -> Minecraft.getInstance().getResourceManager().getResource(k).isPresent());
    }

    private final Screen parent;
    private final OstLibrary library;
    private final OstAudioEngine engine;
    private final OstConfig config;

    private OstCategory selectedCategory = OstCategory.APAISANT;
    private EditBox searchField;
    private int scrollOffset = 0;
    /** Corde en cours de glisser : 0 lecture, 1 volume, 2 distance ; -1 = aucune. */
    private int dragging = -1;
    private long openedAt;

    // Géométrie (recalculée à chaque image, et avant chaque clic).
    private int bx, bw, top, bottom;          // bloc principal
    private int lx, lw, rx, rw;               // carte gauche / rouleau droit
    private int discX, discY, discS, infoX, infoW;
    private int ctrlY, soloY, soloW;
    private final int[] strY = new int[3];
    private int strX, strW;
    private int listTop, listBottom;
    private int plateY, plateH, tabsY;

    public OstScreen(Screen parent) {
        super(Component.literal("Menu des OST"));
        this.parent = parent;
        this.library = RebornOstClient.library();
        this.engine = RebornOstClient.audioEngine();
        this.config = RebornOstClient.config();
    }

    private void layout() {
        plateY = 8; plateH = 28;
        tabsY = plateY + plateH + 6;
        top = tabsY + 22;
        bottom = this.height - 24;
        bw = Math.min(this.width - 32, 600);
        bx = (this.width - bw) / 2;
        lw = Math.round(bw * 0.48f);
        lx = bx;
        rx = lx + lw + 14;
        rw = bx + bw - rx;
        int h = bottom - top;
        discS = Math.max(40, Math.min(Math.round(lw * 0.36f), h - 3 * 24 - 34));
        discS -= discS % 2;
        discX = lx + 12;
        discY = top + 12;
        infoX = discX + discS + 12;
        infoW = lx + lw - 12 - infoX;
        ctrlY = discY + 30;
        soloY = ctrlY + 24;
        soloW = Math.min(infoW, 110);
        strX = lx + 18;
        strW = lw - 36;
        int strBase = Math.max(discY + discS + 22, bottom - 3 * 24 - 4);
        for (int i = 0; i < 3; i++) strY[i] = strBase + i * 24 + 10;
        listTop = top + 32;
        listBottom = bottom - 6;
    }

    @Override
    protected void init() {
        layout();
        searchField = new EditBox(this.font, rx + 14, top + 11, Math.max(60, rw / 2), 12, Component.literal("Rechercher"));
        searchField.setBordered(false);
        searchField.setTextColor(INK);
        searchField.setHint(Component.literal("Chercher une piste…").withColor(INK_MUTED));
        this.addRenderableWidget(searchField);
        openedAt = System.currentTimeMillis();
    }

    /** Ouverture : glissé vers le haut (ease-out cubic, 220 ms). */
    private float appear() {
        float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 220f);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    // ═══════════════════════════════════════════════════════════ rendu

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, 0x48080408);     // voile léger : le monde reste visible
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        layout();
        if (searchField != null) { searchField.setX(rx + 14); searchField.setY(top + 11); }
        Font f = this.font;
        g.pose().pushMatrix();
        g.pose().translate(0, (1f - appear()) * 18f);
        drawHeader(g, f, mouseX, mouseY);
        drawNowPlaying(g, f, mouseX, mouseY);
        drawScroll(g, f, mouseX, mouseY);
        g.pose().popMatrix();
        if (searchField != null) searchField.extractRenderState(g, mouseX, mouseY, delta);
        drawHint(g, f);
    }

    /** Plaque « MUSIQUES » suspendue par un cordon + catégories en onglets pendus. */
    private void drawHeader(GuiGraphicsExtractor g, Font f, int mx, int my) {
        int cx = this.width / 2, pw = 176, py = plateY, ph = plateH;
        for (int k = 0; k < py; k++) {                         // cordon en V
            g.fill(cx - 56 + k * 3, k, cx - 54 + k * 3, k + 1, CORD);
            g.fill(cx + 54 - k * 3, k, cx + 56 - k * 3, k + 1, CORD);
        }
        g.fill(cx - pw / 2 + 2, py + 3, cx + pw / 2 + 2, py + ph + 3, 0x80000000);
        g.fill(cx - pw / 2, py, cx + pw / 2, py + ph, LACQ);
        outline(g, cx - pw / 2, py, pw, ph, GOLD);
        outline(g, cx - pw / 2 + 2, py + 2, pw - 4, ph - 4, 0xFF963C32);
        Component t = ax("Musiques");
        g.text(f, t, cx - f.width(t) / 2, py + 5, CREAM, false);
        scaled(g, f, ax("OST du serveur Reborn"), cx, py + 17, SMALL, 0xFFE6B4A0, true);

        int[] tx = tabX(f);
        OstCategory[] cats = OstCategory.values();
        for (int i = 0; i < cats.length; i++) {
            int x = tx[i], w = tx[i + cats.length];
            boolean sel = cats[i] == selectedCategory && searchBlank();
            boolean hov = in(mx, my, x, tabsY, w, 14);
            int drop = sel ? 2 : 0;                            // l'onglet choisi pend un peu plus bas
            g.fill(x + w / 2, py + ph, x + w / 2 + 1, tabsY + drop, CORD);
            g.fill(x + 1, tabsY + drop + 2, x + w + 1, tabsY + drop + 16, 0x60000000);
            g.fill(x, tabsY + drop, x + w, tabsY + drop + 14, sel ? RED : hov ? PLATE_HOV : PLATE);
            outline(g, x, tabsY + drop, w, 14, sel || hov ? GOLD : PLATE_EDGE);
            scaled(g, f, ax(cats[i].displayName()), x + w / 2f, tabsY + drop + 4, SMALL, sel ? CREAM : MUTED, true);
        }

        // Fermer : petite plaque à droite.
        int clx = bx + bw - 16, cly = plateY + 7;
        boolean ch = in(mx, my, clx, cly, 14, 14);
        g.fill(clx, cly, clx + 14, cly + 14, ch ? RED : 0xF00E0A0C);
        outline(g, clx, cly, 14, 14, ch ? GOLD : GOLD_D);
        for (int k = -3; k <= 3; k++) {
            g.fill(clx + 7 + k, cly + 7 + k, clx + 8 + k, cly + 8 + k, CREAM);
            g.fill(clx + 7 + k, cly + 7 - k, clx + 8 + k, cly + 8 - k, CREAM);
        }
    }

    /** Positions des onglets : [x0..xn-1, w0..wn-1]. */
    private int[] tabX(Font f) {
        OstCategory[] cats = OstCategory.values();
        int n = cats.length, total = 0;
        int[] r = new int[n * 2];
        for (int i = 0; i < n; i++) { r[n + i] = Math.round(f.width(ax(cats[i].displayName())) * SMALL) + 14; total += r[n + i] + 5; }
        int x = this.width / 2 - (total - 5) / 2;
        for (int i = 0; i < n; i++) { r[i] = x; x += r[n + i] + 5; }
        return r;
    }

    /** Carte « en lecture » : disque laqué, titre, commandes, solo, cordes de shamisen. */
    private void drawNowPlaying(GuiGraphicsExtractor g, Font f, int mx, int my) {
        int h = bottom - top;
        g.fill(lx + 2, top + 3, lx + lw + 2, bottom + 3, 0x70000000);
        g.fill(lx, top, lx + lw, bottom, BLACK);
        outline(g, lx, top, lw, h, GOLD);
        outline(g, lx + 2, top + 2, lw - 4, h - 4, 0xFF963C32);
        kanagu(g, lx, top, lw, h);

        var cur = engine.currentTrack();
        boolean playing = engine.isPlaying();
        float angle = cur.isPresent() && playing ? (engine.elapsedMs() / 1000f * 40f) % 360f : 0f;
        if (cur.isPresent() && playing) {                    // halo doré pendant la lecture
            int r = discS / 2 + 3, ccx = discX + discS / 2, ccy = discY + discS / 2;
            fillDisc(g, ccx, ccy, r, 0x30F6CC78);
        }
        drawDisc(g, cur.orElse(null), discX, discY, discS, angle);

        if (cur.isPresent()) {
            OstTrack t = cur.get();
            g.text(f, fit(f, OstTrackMeta.title(t.trackId(), t.displayName()), infoW), infoX, discY + 2, GOLD, false);
            scaled(g, f, ax(t.category().displayName()), infoX, discY + 15, SMALL, MUTED, false);
        } else {
            g.text(f, fit(f, "Aucune piste", infoW), infoX, discY + 2, MUTED, false);
            scaled(g, f, ax("Choisis une piste a droite"), infoX, discY + 15, SMALL, 0xFF8C7A6A, false);
        }

        // Commandes : plaques laquées avec les icônes du pack ; lecture/pause en vermillon.
        Identifier[] icons = { IC_PREV, playing ? IC_PAUSE : IC_PLAY, IC_NEXT, IC_STOP };
        for (int i = 0; i < 4; i++) {
            int x = infoX + i * 22;
            boolean hov = in(mx, my, x, ctrlY, 20, 18);
            boolean main = i == 1;
            g.fill(x, ctrlY, x + 20, ctrlY + 18, main ? (hov ? RED_HOV : RED) : (hov ? PLATE_HOV : PLATE));
            outline(g, x, ctrlY, 20, 18, main || hov ? GOLD : PLATE_EDGE);
            g.blit(RenderPipelines.GUI_TEXTURED, icons[i], x + 2, ctrlY + 1, 0f, 0f, 16, 16, 16, 16);
        }
        int sx = infoX + 4 * 22 + 4;
        if (sx + 40 <= lx + lw - 8) {
            toggle(g, f, sx, ctrlY, "S", config.isShuffle(), in(mx, my, sx, ctrlY, 18, 18));
            int rm = config.getRepeatMode();
            toggle(g, f, sx + 21, ctrlY, rm == 1 ? "R1" : "R", rm != 0, in(mx, my, sx + 21, ctrlY, 18, 18));
        }

        boolean solo = config.isSoloMode();
        boolean shov = in(mx, my, infoX, soloY, soloW, 15);
        g.fill(infoX, soloY, infoX + soloW, soloY + 15, solo ? RED : shov ? PLATE_HOV : PLATE);
        outline(g, infoX, soloY, soloW, 15, solo || shov ? GOLD : PLATE_EDGE);
        scaled(g, f, ax(solo ? "Mode solo : oui" : "Mode solo : non"), infoX + soloW / 2f, soloY + 5, SMALL, solo ? CREAM : MUTED, true);

        // Trois cordes de shamisen.
        long el = engine.elapsedMs(), du = engine.durationMs();
        String[] labels = { "Lecture", "Volume", "Distance" };
        String[] values = {
            cur.isPresent() ? mmss(el) + " / " + mmss(du) : "-",
            Math.round(config.getVolume() * 100f) + " %",
            Math.round(config.getBroadcastDistance()) + " blocs" };
        float[] fr = { du > 0 ? (float) Math.min(1.0, el / (double) du) : 0f, config.getVolume(),
            Math.min(1f, config.getBroadcastDistance() / 128f) };
        g.fill(lx + 10, strY[0] - 16, lx + lw - 10, strY[0] - 15, 0x40F6CC78);   // filet de séparation
        for (int i = 0; i < 3; i++) {
            int y = strY[i];
            scaled(g, f, ax(labels[i]), strX, y - 9, SMALL, MUTED, false);
            Component v = ax(values[i]);
            scaled(g, f, v, strX + strW - f.width(v) * SMALL, y - 9, SMALL, GOLD, false);
            shamisen(g, strX, y, strW, fr[i], dragging == i || in(mx, my, strX - 3, y - 5, strW + 6, 10));
        }
    }

    private void toggle(GuiGraphicsExtractor g, Font f, int x, int y, String s, boolean on, boolean hov) {
        g.fill(x, y, x + 18, y + 18, on ? RED : hov ? PLATE_HOV : PLATE);
        outline(g, x, y, 18, 18, on || hov ? GOLD : PLATE_EDGE);
        Component c = Component.literal(s);
        g.text(f, c, x + 9 - f.width(c) / 2, y + 5, on ? CREAM : MUTED, false);
    }

    /** Corde : fine corde crème, partie jouée en or, chevalets de bois, plectre au curseur. */
    private static void shamisen(GuiGraphicsExtractor g, int x, int y, int w, float frac, boolean hot) {
        g.fill(x, y, x + w, y + 1, STRING);
        int fw = Math.round(w * Math.max(0f, Math.min(1f, frac)));
        g.fill(x, y - 1, x + fw, y + 1, GOLD);
        g.fill(x - 4, y - 3, x, y + 4, ROD);
        g.fill(x + w, y - 3, x + w + 4, y + 4, ROD);
        int k = x + fw, c = hot ? CREAM : 0xFFE6D2BE;
        for (int i = 0; i < 5; i++) g.fill(k - i, y - 6 + i, k + i + 1, y - 5 + i, c);
    }

    /** Rouleau de papier : baguettes de bois, recherche, liste à l'encre. */
    private void drawScroll(GuiGraphicsExtractor g, Font f, int mx, int my) {
        g.fill(rx + 2, top + 3, rx + rw + 2, bottom + 3, 0x70000000);
        g.fill(rx, top, rx + rw, bottom, PAPER);
        g.fill(rx - 4, top - 5, rx + rw + 4, top, ROD);
        g.fill(rx - 4, bottom, rx + rw + 4, bottom + 5, ROD);
        g.fill(rx - 7, top - 6, rx - 3, top + 1, GOLD_D); g.fill(rx + rw + 3, top - 6, rx + rw + 7, top + 1, GOLD_D);
        g.fill(rx - 7, bottom - 1, rx - 3, bottom + 6, GOLD_D); g.fill(rx + rw + 3, bottom - 1, rx + rw + 7, bottom + 6, GOLD_D);
        // Recherche : filet d'encre sous le champ.
        g.fill(rx + 12, top + 24, rx + 16 + Math.max(60, rw / 2), top + 25, 0xFFB4966E);

        List<OstTrack> tracks = resolveVisibleTracks();
        int left = rx + 8, right = rx + rw - 8;
        Component cnt = ax(tracks.size() + (tracks.size() > 1 ? " pistes" : " piste"));
        scaled(g, f, cnt, right - f.width(cnt) * SMALL, top + 13, SMALL, INK_MUTED, false);
        int visible = Math.max(1, (listBottom - listTop) / ROW_H);
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, tracks.size() - visible)));
        String curId = engine.currentTrack().map(OstTrack::trackId).orElse(null);
        for (int i = 0; i < visible && scrollOffset + i < tracks.size(); i++) {
            OstTrack t = tracks.get(scrollOffset + i);
            int y = listTop + i * ROW_H;
            boolean playing = t.trackId().equals(curId);
            boolean hov = in(mx, my, left, y, right - left, ROW_H);
            if (playing) g.fill(left, y + 1, right, y + ROW_H - 1, RED);
            else if (hov) g.fill(left, y + 1, right, y + ROW_H - 1, PAPER_HOV);
            else if (i > 0) g.fill(left + 4, y, right - 4, y + 1, 0x28503C28);
            drawDisc(g, t, left + 4, y + (ROW_H - THUMB) / 2, THUMB, 0f);
            String title = (playing ? "> " : "") + OstTrackMeta.title(t.trackId(), t.displayName());
            g.text(f, fit(f, title, right - left - THUMB - 64), left + THUMB + 10, y + 6, playing ? CREAM : INK, false);
            boolean fav = config.isFavorite(t.trackId());
            g.text(f, Component.literal(fav ? "★" : "☆"), right - 12, y + 6, fav ? (playing ? GOLD : 0xFFB4781E) : (playing ? MUTED : INK_MUTED), false);
            String dur = OstTrackMeta.formatDuration(OstTrackMeta.duration(t.trackId()));
            if (!dur.isEmpty()) g.text(f, Component.literal(dur), right - 22 - f.width(dur), y + 6, playing ? MUTED : INK_MUTED, false);
        }
        if (tracks.isEmpty()) {
            Component e = ax(searchBlank() ? "Aucune piste ici" : "Aucun resultat");
            scaled(g, f, e, rx + rw / 2f, listTop + 20, SMALL, INK_MUTED, true);
        }
        // Ascenseur discret le long du rouleau.
        if (tracks.size() > visible) {
            int trackH = listBottom - listTop, th = Math.max(12, trackH * visible / tracks.size());
            int ty = listTop + (trackH - th) * scrollOffset / Math.max(1, tracks.size() - visible);
            g.fill(rx + rw - 4, listTop, rx + rw - 3, listBottom, 0x40503C28);
            g.fill(rx + rw - 5, ty, rx + rw - 2, ty + th, ROD);
        }
    }

    private void drawDisc(GuiGraphicsExtractor g, OstTrack track, int x, int y, int size, float angle) {
        Identifier cover = track != null ? OstTrackMeta.coverTexture(track) : null;
        boolean rot = angle != 0f;
        if (rot) {
            g.pose().pushMatrix();
            g.pose().translate(x + size / 2f, y + size / 2f);
            g.pose().rotate((float) Math.toRadians(angle));
            g.pose().translate(-(x + size / 2f), -(y + size / 2f));
        }
        if (exists(cover)) {
            g.blit(RenderPipelines.GUI_TEXTURED, cover, x, y, 0f, 0f, size, size, COVER_PX, COVER_PX, COVER_PX, COVER_PX);
        } else if (exists(DISC)) {
            g.blit(RenderPipelines.GUI_TEXTURED, DISC, x, y, 0f, 0f, size, size, COVER_PX, COVER_PX, COVER_PX, COVER_PX);
        } else {
            fillDisc(g, x + size / 2, y + size / 2, size / 2, 0xFF140E10);
        }
        if (rot) g.pose().popMatrix();
    }

    private void drawHint(GuiGraphicsExtractor g, Font f) {
        Component c = ax("Clic : jouer    Molette : defiler    Etoile : favori    Echap : fermer");
        int w = Math.round(f.width(c) * SMALL) + 24, x = (this.width - w) / 2, y = this.height - 16;
        g.fill(x, y, x + w, y + 12, 0xF0EADCB6);
        outline(g, x, y, w, 12, 0xFF8C6E48);
        scaled(g, f, c, x + 12, y + 3, SMALL, 0xFF503C28, false);
    }

    // ═══════════════════════════════════════════════════════════ interaction

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        layout();
        int mx = (int) event.x(), my = (int) event.y();
        if (event.button() != 0) return false;

        if (in(mx, my, bx + bw - 16, plateY + 7, 14, 14)) { onClose(); return true; }

        // Onglets catégories.
        int[] tx = tabX(this.font);
        OstCategory[] cats = OstCategory.values();
        for (int i = 0; i < cats.length; i++) {
            if (in(mx, my, tx[i], tabsY, tx[i + cats.length], 16)) {
                selectedCategory = cats[i]; scrollOffset = 0;
                if (searchField != null) searchField.setValue("");
                return true;
            }
        }

        // Commandes.
        for (int i = 0; i < 4; i++) {
            if (!in(mx, my, infoX + i * 22, ctrlY, 20, 18)) continue;
            switch (i) {
                case 0 -> playRelative(-1);
                case 1 -> {
                    engine.togglePause();
                    // Propriétaire d'une diffusion → la pause se propage à toute la zone.
                    if (OstNetworking.isBroadcastOwner()) OstNetworking.requestPause(engine.isPaused());
                }
                case 2 -> playRelative(1);
                default -> {
                    OstPlayback.INSTANCE.stop(engine);
                    OstNetworking.requestStop();   // propriétaire → stop pour la zone (no-op sinon)
                }
            }
            return true;
        }
        int sx = infoX + 4 * 22 + 4;
        if (sx + 40 <= lx + lw - 8) {
            if (in(mx, my, sx, ctrlY, 18, 18)) { config.setShuffle(!config.isShuffle()); config.save(); return true; }
            if (in(mx, my, sx + 21, ctrlY, 18, 18)) { config.cycleRepeat(); config.save(); return true; }
        }
        if (in(mx, my, infoX, soloY, soloW, 15)) {
            config.setSoloMode(!config.isSoloMode()); config.save();
            // Passer en solo coupe chez soi la diffusion en cours (n'affecte que ce client).
            if (config.isSoloMode()) engine.stop();
            return true;
        }

        // Cordes.
        for (int i = 0; i < 3; i++) {
            if (in(mx, my, strX - 4, strY[i] - 7, strW + 8, 13)) { dragging = i; applyString(i, mx); return true; }
        }

        // Liste.
        List<OstTrack> tracks = resolveVisibleTracks();
        int left = rx + 8, right = rx + rw - 8;
        if (my >= listTop && my < listBottom && mx >= left && mx < right) {
            int idx = (my - listTop) / ROW_H + scrollOffset;
            if (idx >= 0 && idx < tracks.size()) {
                OstTrack t = tracks.get(idx);
                if (mx > right - 16) { config.toggleFavorite(t.trackId()); config.save(); }
                else playAndMaybeBroadcast(tracks, idx);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (dragging >= 0) { applyString(dragging, event.x()); return true; }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (dragging >= 0) { dragging = -1; config.save(); return true; }
        return super.mouseReleased(event);
    }

    private void applyString(int i, double mx) {
        float v = clamp01((mx - strX) / strW);
        switch (i) {
            case 0 -> { long du = engine.durationMs(); if (du > 0) engine.seekMs((long) (du * v)); }
            case 1 -> { config.setVolume(v); engine.setGlobalVolume(v); }
            default -> config.setBroadcastDistance(v * 128f);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        List<OstTrack> tracks = resolveVisibleTracks();
        int visible = Math.max(1, (listBottom - listTop) / ROW_H);
        int max = Math.max(0, tracks.size() - visible);
        scrollOffset = Math.max(0, Math.min(max, scrollOffset - (int) Math.signum(vAmount)));
        return true;
    }

    private void playRelative(int dir) {
        var cur = engine.currentTrack();
        OstCategory cat = cur.map(OstTrack::category).orElse(selectedCategory);
        List<OstTrack> list = library.tracks(cat);
        if (list.isEmpty()) return;
        int idx = 0;
        if (cur.isPresent()) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).trackId().equals(cur.get().trackId())) { idx = i; break; }
            }
            idx = (idx + dir + list.size()) % list.size();
        }
        playAndMaybeBroadcast(list, idx);
    }

    /** Joue la piste localement ; en solo OFF, la diffuse aussi à la zone
     *  (broadcast serveur) et devient propriétaire du son. */
    private void playAndMaybeBroadcast(List<OstTrack> list, int idx) {
        OstPlayback.INSTANCE.play(engine, config, list, idx);
        if (!config.isSoloMode()) {
            OstTrack t = list.get(idx);
            OstNetworking.requestPlay(t.trackId(), config.getBroadcastDistance(), config.getVolume());
        }
    }

    private boolean searchBlank() {
        return searchField == null || searchField.getValue().isBlank();
    }

    private List<OstTrack> resolveVisibleTracks() {
        String q = searchField != null ? searchField.getValue() : "";
        if (q != null && !q.isBlank()) return library.search(q);
        if (selectedCategory == OstCategory.FAVORIS) return library.favorites(config.getFavorites());
        return library.tracks(selectedCategory);
    }

    // ═══════════════════════════════════════════════════════════ outils

    /** Majuscules sans accents (style des plaques Reborn). */
    private static Component ax(String s) {
        return Component.literal(Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT));
    }

    private static Component fit(Font f, String s, int maxW) {
        if (f.width(s) <= maxW) return Component.literal(s);
        while (s.length() > 1 && f.width(s + "…") > maxW) s = s.substring(0, s.length() - 1);
        return Component.literal(s.trim() + "…");
    }

    private static void scaled(GuiGraphicsExtractor g, Font f, Component c, float x, float y, float sc, int col, boolean centered) {
        g.pose().pushMatrix();
        g.pose().translate(centered ? x - f.width(c) * sc / 2f : x, y);
        g.pose().scale(sc, sc);
        g.text(f, c, 0, 0, col, false);
        g.pose().popMatrix();
    }

    private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c); g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c); g.fill(x + w - 1, y, x + w, y + h, c);
    }

    private static void kanagu(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        int c = GOLD, s = 6;
        g.fill(x, y, x + s, y + 2, c); g.fill(x, y, x + 2, y + s, c);
        g.fill(x + w - s, y, x + w, y + 2, c); g.fill(x + w - 2, y, x + w, y + s, c);
        g.fill(x, y + h - 2, x + s, y + h, c); g.fill(x, y + h - s, x + 2, y + h, c);
        g.fill(x + w - s, y + h - 2, x + w, y + h, c); g.fill(x + w - 2, y + h - s, x + w, y + h, c);
    }

    private static void fillDisc(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.round(Math.sqrt((double) r * r - dy * dy));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private static float clamp01(double v) { return (float) Math.max(0, Math.min(1, v)); }

    private static String mmss(long ms) {
        long s = Math.max(0, ms) / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void onClose() {
        config.save();
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
