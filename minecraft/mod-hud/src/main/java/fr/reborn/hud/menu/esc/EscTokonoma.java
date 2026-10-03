package fr.reborn.hud.menu.esc;

import fr.reborn.hud.menu.IconPack;
import fr.reborn.hud.menu.RebornBranding;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.menu.screens.ConfigShellScreen;
import fr.reborn.hud.menu.shop.ShopData;
import fr.reborn.hud.menu.stats.StatsData;
import fr.reborn.hud.menu.widget.DisconnectConfirmScreen;
import fr.reborn.hud.menu.widget.ReportScreen;
import fr.reborn.hud.menu.widget.ShopScreen;
import fr.reborn.hud.ui.UiAmbience;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Menu Échap « tokonoma » : une pièce traditionnelle de nuit. Fenêtre ronde sur le village,
 * logo Reborn Roleplay et menu en plaques laquées à gauche ; poutre aux emblèmes des cinq
 * villages et, dans l'alcôve, quatre éléments distincts : Échoppe (devanture noren), Stream
 * (kamishibai), Dev blog (kawaraban) et Récompenses (omamori). Réseaux en bas à droite.
 *
 * <p>Ouverture : deux fusuma coulissent ; fermeture par « Reprendre » : elles se referment.
 * Tout est dessiné dans un espace virtuel 640×360 mis à l'échelle de l'écran.
 * Textures : {@code textures/gui/esc/*} ({@code tools/ui-art/gen_esc_art.py}),
 * sons : {@code sounds/esc/*} ({@code gen_esc_sfx.py}).
 */
public final class EscTokonoma {

    private static final int VW = 640, VH = 360;
    private static final int OPEN_MS = 720, CLOSE_MS = 300;

    private static Identifier tex(String p) { return Identifier.fromNamespaceAndPath("reborn", "textures/gui/" + p + ".png"); }

    private static final Identifier ROOM = tex("esc/room"), WINDOW = tex("esc/window"), WINDOW_LIGHTS = tex("esc/window_lights"),
        WINDOW_FRAME = tex("esc/window_frame"), FUSUMA_L = tex("esc/fusuma_l"), FUSUMA_R = tex("esc/fusuma_r"),
        KAMISHIBAI = tex("esc/kamishibai"), KAWARABAN = tex("esc/kawaraban"), RACK = tex("esc/rack"),
        OMAMORI = tex("esc/omamori"), OMAMORI_LABEL = tex("esc/omamori_label"), RBCOIN = tex("esc/rbcoin"),
        KAMON_PLATE = tex("esc/kamon_plate"), LOGO = tex("title/logo"), GLOW = tex("stats/glow"),
        SHOP_WALL = tex("shop/wall"), NOREN = tex("shop/noren"), CREST = tex("shop/crest"), CHOCHIN = tex("shop/chochin");

    private static final String[] VILLAGE_IDS = {"konoha", "suna", "kiri", "kumo", "iwa"};
    private static final String[] VILLAGE_NAMES = {"KONOHA", "SUNA", "KIRI", "KUMO", "IWA"};
    private static final String[] VILLAGE_LANDS = {"FEU", "VENT", "EAU", "FOUDRE", "TERRE"};
    private static final Identifier[] VILLAGE_TEX = new Identifier[5];
    static { for (int i = 0; i < 5; i++) VILLAGE_TEX[i] = tex("esc/village_" + VILLAGE_IDS[i]); }

    private static final String[] MENU = {"REPRENDRE", "PARAMETRES", "SIGNALER", "DECONNEXION"};

    /** Activations qui rapportent des RBCoins par heure de jeu (état réel à brancher sur l'API). */
    private record Charm(String label, String title, String how, int color) {}
    private static final Charm[] CHARMS = {
        new Charm("BOOST", "BOOST DISCORD", "BOOSTE LE DISCORD REBORN", 0xFFDC5AC8),
        new Charm("PSEUDO", "TAG DANS LE PSEUDO", "AJOUTE LE TAG REBORN A TON PSEUDO", 0xFF468CDC),
        new Charm("BIO", "LIEN DANS LA BIO", "METS LE LIEN REBORN DANS TA BIO", 0xFF46AA64),
        new Charm("STREAM", "STREAM REBORN", "STREAME SUR LE SERVEUR", 0xFF965AF0),
        new Charm("PREMIUM", "PREMIUM", "SOUTIENS LE SERVEUR", 0xFFE6B43C),
    };

    private record Social(String id, String label, String url) {}
    private static final Social[] SOCIALS = {
        new Social("discord", "DISCORD", RebornBranding.DISCORD_URL),
        new Social("x", "X", RebornBranding.X_URL),
        new Social("youtube", "YOUTUBE", RebornBranding.YOUTUBE_URL),
        new Social("site", "SITE", RebornBranding.SITE_URL),
    };

    // Zones cliquables (ids) recalculées à chaque frame.
    private static final int H_SHOP = 10, H_STREAM = 11, H_STREAM_PREV = 12, H_STREAM_NEXT = 13,
        H_BLOG = 14, H_BLOG_PREV = 15, H_BLOG_NEXT = 16, H_CHARM = 20, H_SOCIAL = 30;
    private record Hit(int id, int x, int y, int w, int h) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    // ── état ──
    private static boolean active;            // un menu Échap Reborn est à l'écran
    private static boolean closeSoundPlayed;  // « Reprendre » a déjà joué la fermeture
    private static boolean silentStatsRequest;

    private final Screen screen;
    private final long openedAt = System.currentTimeMillis();
    private long closingAt = -1L;
    private long lastFrame = System.currentTimeMillis();
    private boolean openSoundPlayed;
    private final List<Hit> hits = new ArrayList<>();
    private int hovered = -1;
    private final float[] hov = new float[64];
    private final float[] charmKick = new float[CHARMS.length];
    private int streamIdx, blogIdx;
    private long lastStreamRotate = System.currentTimeMillis(), lastBlogRotate = System.currentTimeMillis();
    private final float[][] stars;
    private float scale = 1f, ox, oy;

    public EscTokonoma(Screen screen) {
        this.screen = screen;
        Random r = new Random(7);
        stars = new float[22][3];
        for (float[] s : stars) {
            double a = Math.PI + r.nextDouble() * Math.PI, d = 8 + r.nextDouble() * 52;
            s[0] = (float) (Math.cos(a) * d); s[1] = (float) (Math.sin(a) * d * 0.8 - 6); s[2] = r.nextFloat() * 6.28f;
        }
    }

    /** Appelé à chaque {@code init} de l'écran pause (ouverture, retour d'un sous-écran, redimensionnement). */
    public void init() {
        boolean wasActive = active;
        active = true;
        closeSoundPlayed = false;
        EscData.refreshIfStale();
        if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(fr.reborn.hud.menu.shop.ShopPayload.ID)) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new fr.reborn.hud.menu.shop.ShopPayload("open"));
        }
        if (StatsData.get() == null
            && net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(fr.reborn.hud.menu.stats.StatsPayload.ID)) {
            silentStatsRequest = true; // identité (nom, village) sans ouvrir la fiche
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new fr.reborn.hud.menu.stats.StatsPayload("open"));
        }
        if (!wasActive) UiAmbience.start("esc.ambience", 0.32f);
    }

    /** La réponse « stats » demandée en silence ne doit pas ouvrir la fiche. */
    public static boolean consumeSilentStatsRequest() {
        boolean s = silentStatsRequest;
        silentStatsRequest = false;
        return s;
    }

    /** Tick client : détecte la sortie du menu (Échap, sous-écran) pour couper l'ambiance. */
    public static void tick(Minecraft mc) {
        if (!active) return;
        Screen cur = mc.gui.screen();
        if (cur instanceof PauseScreen) return;
        active = false;
        UiAmbience.stop();
        if (cur == null && !closeSoundPlayed) RebornSounds.playReborn("esc.close", 1.0f, 0.55f);
    }

    // ═════════════════════════════════════════════════════════════ rendu

    public void render(GuiGraphicsExtractor ctx, int width, int height, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        if (!openSoundPlayed) { openSoundPlayed = true; RebornSounds.playReborn("esc.open", 1.0f, 0.6f); }
        if (closingAt > 0 && now - closingAt >= CLOSE_MS) { Minecraft.getInstance().setScreenAndShow(null); return; }

        scale = Math.min(width / (float) VW, height / (float) VH);
        ox = (width - VW * scale) / 2f;
        oy = (height - VH * scale) / 2f;
        double mx = (mouseX - ox) / scale, my = (mouseY - oy) / scale;
        float time = (now - openedAt) / 1000f;

        ctx.fill(0, 0, width, height, 0xFF140C0A);
        ctx.pose().pushMatrix();
        ctx.pose().translate(ox, oy);
        ctx.pose().scale(scale, scale);

        hits.clear();
        Font f = Minecraft.getInstance().font;
        ctx.blit(RenderPipelines.GUI_TEXTURED, ROOM, 0, 0, 0f, 0f, VW, VH, VW, VH);
        drawWindow(ctx, time);
        drawIdentity(ctx, f);
        drawMenu(ctx, f, mx, my);
        drawKamon(ctx, f, time);
        drawShop(ctx, f, 232, 56, 184, 134, time);
        drawStream(ctx, f, 424, 54, now, time);
        drawBlog(ctx, f, 236, 198, now);
        drawRack(ctx, f, 384, 198, time, dt);
        drawFloor(ctx, f);

        // survol (après avoir enregistré toutes les zones)
        int h = -1;
        for (Hit hit : hits) if (hit.in(mx, my)) h = hit.id;
        if (h != hovered) {
            hovered = h;
            if (h >= 0 && opened(now) >= 1f) RebornSounds.playReborn(h >= H_CHARM && h < H_CHARM + CHARMS.length ? "esc.charm" : "esc.hover",
                h >= H_CHARM && h < H_CHARM + CHARMS.length ? 1.3f : 1.0f, h >= H_CHARM && h < H_CHARM + CHARMS.length ? 0.18f : 0.3f);
            if (h >= H_CHARM && h < H_CHARM + CHARMS.length) charmKick[h - H_CHARM] = 1f;
        }
        for (int i = 0; i < hov.length; i++) {
            float target = (i == hovered) ? 1f : 0f;
            hov[i] += (target - hov[i]) * Math.min(1f, dt * 14f);
        }
        drawTooltip(ctx, f, mx, my);
        drawFusuma(ctx, now);
        ctx.pose().popMatrix();
    }

    private float opened(long now) { return Math.min(1f, Math.max(0f, (now - openedAt - 100) / (float) (OPEN_MS - 100))); }

    // ── fenêtre ronde : étoiles qui scintillent, lumières du village, étoile filante ──
    private void drawWindow(GuiGraphicsExtractor ctx, float time) {
        int x = 37, y = 22, cx = 107, cy = 92;
        ctx.blit(RenderPipelines.GUI_TEXTURED, WINDOW, x, y, 0f, 0f, 140, 140, 140, 140);
        for (float[] s : stars) {
            float a = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(time * 1.7f + s[2]));
            int sx = Math.round(cx + s[0]), sy = Math.round(cy + s[1]);
            ctx.fill(sx, sy, sx + 1, sy + 1, argb(a, 0xF0F0FF));
        }
        float flick = 0.75f + 0.25f * (float) Math.sin(time * 4.3f) * (float) Math.sin(time * 1.9f + 1);
        ctx.blit(RenderPipelines.GUI_TEXTURED, WINDOW_LIGHTS, x, y, 0f, 0f, 140, 140, 140, 140, argb(flick, 0xFFFFFF));
        // étoile filante toutes les ~9 s, gardée dans le disque
        float cyc = time % 9f;
        if (cyc > 6.5f && cyc < 7.3f) {
            float p = (cyc - 6.5f) / 0.8f;
            for (int k = 0; k < 7; k++) {
                float q = p - k * 0.03f;
                if (q < 0) continue;
                int px = Math.round(cx - 46 + q * 70), py = Math.round(cy - 50 + q * 26);
                if (Math.hypot(px - cx, py - cy) < 62) ctx.fill(px, py, px + 1, py + 1, argb(1f - k / 7f, 0xFFFFFF));
            }
        }
        // halo de lune qui respire
        float g = 0.10f + 0.05f * (float) Math.sin(time * 0.9f);
        glow(ctx, x + 104, y + 36, 44, argb(g, 0xFFECC8));
        ctx.blit(RenderPipelines.GUI_TEXTURED, WINDOW_FRAME, x, y, 0f, 0f, 140, 140, 140, 140);
    }

    // ── logo + identité du personnage ──
    private void drawIdentity(GuiGraphicsExtractor ctx, Font f) {
        int lw = 176, lh = Math.round(176 * 717f / 2048f);
        ctx.blit(RenderPipelines.GUI_TEXTURED, LOGO, 19, 166, 0f, 0f, lw, lh, 2048, 717, 2048, 717);
        int vi = playerVillage();
        String who = playerName();
        int tw = f.width(RebornFont.arcade(who)) + (vi >= 0 ? 14 : 0);
        int x = 107 - tw / 2;
        if (vi >= 0) {
            ctx.blit(RenderPipelines.GUI_TEXTURED, VILLAGE_TEX[vi], x, 229, 0f, 0f, 10, 10, 48, 48, 48, 48, 0xFF8C1C20);
            x += 14;
        }
        text(ctx, f, who, x, 231, 0xFF5A3420);
    }

    private static String playerName() {
        StatsData.Snapshot s = StatsData.get();
        String name = s != null ? s.name() : null;
        if (name == null || name.isBlank() || "?".equals(name)) {
            var p = Minecraft.getInstance().player;
            name = p != null ? p.getName().getString() : "";
        }
        String out = EscData.arcadeSafe(name, 22);
        if (s != null && s.rank() != null && !s.rank().isBlank() && !"?".equals(s.rank())) out += " - " + EscData.arcadeSafe(s.rank(), 10);
        return out;
    }

    private static int playerVillage() {
        StatsData.Snapshot s = StatsData.get();
        if (s == null || s.village() == null) return -1;
        String v = s.village().toLowerCase(Locale.ROOT);
        for (int i = 0; i < VILLAGE_IDS.length; i++) if (v.startsWith(VILLAGE_IDS[i])) return i;
        return -1;
    }

    // ── menu : plaques laquées ──
    private void drawMenu(GuiGraphicsExtractor ctx, Font f, double mx, double my) {
        for (int i = 0; i < MENU.length; i++) {
            int y = 246 + i * 20;
            float hv = hov[i];
            int x = 34 + Math.round(hv * 4);
            hits.add(new Hit(i, 34, y, 146, 17));
            boolean danger = i == 3;
            int fill = danger ? lerp(0xFF3C1E24, 0xFF6E1E22, hv) : lerp(0xFF3C1E24, 0xFFAA1E22, hv);
            int border = lerp(0xFF6E4646, 0xFFF6CC78, hv);
            ctx.fill(107, y - 4, 108, y, 0xFFC8A05A);               // cordelette
            frame(ctx, x, y, 146, 17, fill, border);
            ctx.fill(x + 2, y + 2, x + 144, y + 3, argb(0.18f + 0.2f * hv, 0xFFFFFF));
            centered(ctx, f, MENU[i], x + 73, y + 5, lerp(0xFFBEA0A0, 0xFFFAEED6, hv));
            if (hv > 0.05f) {                                          // pointe dorée
                int ax = x - 7, ay = y + 5;
                for (int k = 0; k < 4; k++) ctx.fill(ax + k, ay + k, ax + k + 1, ay + 7 - k, argb(hv, 0xF6CC78));
            }
        }
    }

    // ── poutre aux kamon ──
    private void drawKamon(GuiGraphicsExtractor ctx, Font f, float time) {
        int mine = playerVillage();
        for (int i = 0; i < 5; i++) {
            int x = 236 + i * 76, y = 23;
            boolean m = i == mine;
            int plate = m ? 0xFFFFFFFF : 0xFF9C9488;
            ctx.blit(RenderPipelines.GUI_TEXTURED, KAMON_PLATE, x, y, 0f, 0f, 22, 22, 48, 48, 48, 48, plate);
            ctx.blit(RenderPipelines.GUI_TEXTURED, VILLAGE_TEX[i], x + 3, y + 3, 0f, 0f, 16, 16, 48, 48, 48, 48,
                m ? 0xFF2A1A12 : 0xFF3C3430);
            if (m) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(time * 2.2f);
                ring(ctx, x - 1, y - 1, 24, argb(0.5f + 0.5f * pulse, 0xF6CC78));
            }
            text(ctx, f, VILLAGE_NAMES[i], x + 26, y + 3, m ? 0xFFF6CC78 : 0xFFD2BE9A);
            text(ctx, f, VILLAGE_LANDS[i], x + 26, y + 13, m ? 0xFFE6B48C : 0xFF9C8466);
        }
    }

    // ── Échoppe : devanture noren + chōchin ──
    private void drawShop(GuiGraphicsExtractor ctx, Font f, int x, int y, int w, int h, float time) {
        float hv = hov[H_SHOP];
        hits.add(new Hit(H_SHOP, x, y, w, h));
        ctx.blit(RenderPipelines.GUI_TEXTURED, SHOP_WALL, x, y, 150f, 60f, w, h, 480, 270);
        for (int nx = x + 1; nx < x + w - 1; nx += 64) {
            int nw = Math.min(64, x + w - 1 - nx);
            ctx.blit(RenderPipelines.GUI_TEXTURED, NOREN, nx, y + 1, 0f, 0f, nw, 40, 64, 40);
        }
        ctx.blit(RenderPipelines.GUI_TEXTURED, CREST, x + w / 2 - 14, y + 4, 0f, 0f, 28, 28, 28, 28);
        float flick = 0.8f + 0.2f * (float) Math.sin(time * 6.1f) * (float) Math.sin(time * 2.3f);
        for (int side = 0; side < 2; side++) {
            int lx = side == 0 ? x + 8 : x + w - 30, ly = y + 86;
            glow(ctx, lx + 11, ly + 16, 40, argb((0.35f + 0.25f * hv) * flick, 0xFFAA5A));
            float sway = (float) Math.sin(time * 1.4f + side) * 0.05f;
            ctx.pose().pushMatrix();
            ctx.pose().translate(lx + 11, ly);
            ctx.pose().rotate(sway);
            ctx.blit(RenderPipelines.GUI_TEXTURED, CHOCHIN, -11, 0, 0f, 0f, 22, 32, 22, 32);
            ctx.pose().popMatrix();
        }
        bigCentered(ctx, f, "ECHOPPE", x + w / 2, y + 52, 1.6f, 0xFFFAEED6);
        centered(ctx, f, "TENUES - CARTES DE REGION", x + w / 2, y + 72, 0xFFE6C8A0);
        int bw = 112, bx = x + w / 2 - bw / 2, by = y + 86;
        frame(ctx, bx, by, bw, 14, 0xFF1E120C, 0xFFF6CC78);
        ctx.blit(RenderPipelines.GUI_TEXTURED, RBCOIN, bx + 4, by + 1, 0f, 0f, 12, 12, 12, 12);
        String bal = ShopData.received() ? ShopData.ryo() + " RBCOINS" : "- RBCOINS";
        text(ctx, f, bal, bx + 20, by + 3, 0xFFF6CC78);
        int btw = 76, btx = x + w / 2 - btw / 2, bty = y + 108 - Math.round(hv);
        frame(ctx, btx, bty, btw, 16, lerp(0xFF8C1C20, 0xFFC82A2E, hv), 0xFFF6CC78);
        centered(ctx, f, "ENTRER", x + w / 2, bty + 4, 0xFFFAEED6);
        outline(ctx, x, y, w, h, lerp(0xFFB48C50, 0xFFF6CC78, hv));
        if (hv > 0.02f) outline(ctx, x - 1, y - 1, w + 2, h + 2, argb(hv * 0.6f, 0xF6CC78));
    }

    // ── Stream : kamishibai ──
    private void drawStream(GuiGraphicsExtractor ctx, Font f, int x, int y, long now, float time) {
        float hv = hov[H_STREAM];
        ctx.blit(RenderPipelines.GUI_TEXTURED, KAMISHIBAI, x + 6, y + 4, 0f, 0f, 178, 132, 178, 132);
        int sx = x + 36, sy = y + 26, sw = 118, sh = 100;
        ctx.fill(sx, sy, sx + sw, sy + sh, 0xFF1A1624);
        plate(ctx, f, "STREAM", x + 95, y, 0xFF28345E);
        hits.add(new Hit(H_STREAM, sx, sy, sw, sh));

        EscData.Snapshot snap = EscData.get();
        List<EscData.Stream> streams = snap != null ? snap.streams() : List.of();
        if (streams.isEmpty()) {
            IconPack.twitch(ctx, sx + sw / 2 - 8, sy + 30, 16, 0xFF6E6488);
            centered(ctx, f, "AUCUN STREAM", sx + sw / 2, sy + 54, 0xFF8C84A0);
            return;
        }
        if (streams.size() > 1 && now - lastStreamRotate > 7000) { streamIdx++; lastStreamRotate = now; }
        int idx = Math.floorMod(streamIdx, streams.size());
        EscData.Stream s = streams.get(idx);
        // badge
        int bc = s.live() ? 0xFFC82828 : 0xFF5A5664;
        String badge = s.live() ? "LIVE" : "OFFLINE";
        int bw = f.width(RebornFont.arcade(badge)) + 10;
        ctx.fill(sx + 4, sy + 4, sx + 4 + bw, sy + 15, bc);
        if (s.live()) {
            float p = 0.5f + 0.5f * (float) Math.sin(time * 5f);
            ctx.fill(sx + bw + 7, sy + 8, sx + bw + 11, sy + 12, argb(0.4f + 0.6f * p, 0xFF4040));
        }
        text(ctx, f, badge, sx + 9, sy + 6, 0xFFFAEED6);
        IconPack.twitch(ctx, sx + sw / 2 - 8, sy + 22, 16, s.live() ? 0xFFA078FF : 0xFF6E6488);
        bigCentered(ctx, f, fit(f, s.name() != null ? s.name() : "STREAM", 88), sx + sw / 2, sy + 44, 1.25f,
            lerp(0xFFE6DCF0, 0xFFFFFFFF, hv));
        if (s.live() && s.title() != null && !s.title().isBlank()) centered(ctx, f, fit(f, s.title(), sw - 8), sx + sw / 2, sy + 60, 0xFFA096B4);
        else centered(ctx, f, "HORS LIGNE", sx + sw / 2, sy + 60, 0xFF7C7490);
        if (streams.size() > 1) {
            arrows(ctx, f, sx, sy + sh - 14, sw, idx, streams.size(), H_STREAM_PREV, H_STREAM_NEXT, 0xFFC8BEDC);
        }
        outline(ctx, sx - 1, sy - 1, sw + 2, sh + 2, argb(0.3f + 0.7f * hv, 0xF6CC78));
    }

    // ── Dev blog : kawaraban ──
    private void drawBlog(GuiGraphicsExtractor ctx, Font f, int x, int y, long now) {
        float hv = hov[H_BLOG];
        int lift = Math.round(hv);
        ctx.blit(RenderPipelines.GUI_TEXTURED, KAWARABAN, x, y - lift, 0f, 0f, 134, 122, 134, 122);
        int px = x + 13, py = y + 20 - lift, pw = 108;
        hits.add(new Hit(H_BLOG, px, py + 12, pw, 86));
        text(ctx, f, "DEV BLOG", px + 4, py + 2, 0xFFFAEED6);
        EscData.Snapshot snap = EscData.get();
        List<EscData.PatchNote> notes = snap != null ? snap.patchNotes() : List.of();
        if (notes.isEmpty()) {
            centered(ctx, f, "AUCUNE NOTE", px + pw / 2, py + 46, 0xFF78603C);
            return;
        }
        if (notes.size() > 1 && now - lastBlogRotate > 8000) { blogIdx++; lastBlogRotate = now; }
        int idx = Math.floorMod(blogIdx, notes.size());
        EscData.PatchNote n = notes.get(idx);
        if (n.date() != null) {
            String[] parts = n.date().split(" ");              // « 12 MAI 2026 » → « 12 MAI »
            String d = parts.length >= 2 ? parts[0] + " " + parts[1] : n.date();
            text(ctx, f, d, px + pw - 4 - f.width(RebornFont.arcade(d)), py + 2, 0xFFC8BEAA);
        }
        bigCentered(ctx, f, fit(f, n.version() != null ? n.version() : "PATCH", 80), px + pw / 2, py + 17, 1.3f, 0xFFB42420);
        ctx.fill(px + 8, py + 31, px + pw - 8, py + 32, 0xFF96785A);
        List<String> lines = wrap(f, n.title() != null ? n.title() : "", pw - 10, 3);
        for (int i = 0; i < lines.size(); i++) centered(ctx, f, lines.get(i), px + pw / 2, py + 37 + i * 10, 0xFF46321E);
        text(ctx, f, "LIRE >", px + pw - 4 - f.width(RebornFont.arcade("LIRE >")), py + 86, lerp(0xFF8C2A20, 0xFFC82A2E, hv));
        if (notes.size() > 1) arrows(ctx, f, px, py + 86, 60, idx, notes.size(), H_BLOG_PREV, H_BLOG_NEXT, 0xFF46321E);
    }

    // ── Récompenses : omamori ──
    private void drawRack(GuiGraphicsExtractor ctx, Font f, int x, int y, float time, float dt) {
        ctx.blit(RenderPipelines.GUI_TEXTURED, RACK, x, y, 0f, 0f, 226, 122, 226, 122);
        text(ctx, f, "RECOMPENSES", x + 8, y + 8, 0xFFFAEED6);
        String sub = "+5 / H CHACUNE";
        text(ctx, f, sub, x + 218 - f.width(RebornFont.arcade(sub)), y + 8, 0xFFF6CC78);
        for (int i = 0; i < CHARMS.length; i++) {
            Charm c = CHARMS[i];
            int cx = x + 27 + i * 43, top = y + 26;
            float hv = hov[H_CHARM + i];
            charmKick[i] = Math.max(0f, charmKick[i] - dt * 0.9f);
            float ang = (float) Math.sin(time * 1.6f + i * 1.3f) * 0.05f
                + (float) Math.sin(time * 9f) * 0.22f * charmKick[i];
            hits.add(new Hit(H_CHARM + i, cx - 13, top, 26, 60));
            glow(ctx, cx, top + 22, 34, argb(0.12f + 0.25f * hv, c.color() & 0xFFFFFF));
            ctx.pose().pushMatrix();
            ctx.pose().translate(cx, top);
            ctx.pose().rotate(ang);
            ctx.blit(RenderPipelines.GUI_TEXTURED, OMAMORI, -11, 0, 0f, 0f, 22, 36, 22, 36, c.color());
            ctx.blit(RenderPipelines.GUI_TEXTURED, OMAMORI_LABEL, -6, 12, 0f, 0f, 12, 18, 12, 18);
            ctx.pose().popMatrix();
            smallCentered(ctx, f, c.label(), cx, top + 45, 0.8f, lerp(0xFFD2BEAA, 0xFFFAEED6, hv));
        }
        ctx.blit(RenderPipelines.GUI_TEXTURED, RBCOIN, x + 8, y + 105, 0f, 0f, 12, 12, 12, 12);
        text(ctx, f, "RBCOINS PAR HEURE DE JEU", x + 24, y + 107, 0xFFF6CC78);
    }

    // ── plancher : communauté + réseaux ──
    private void drawFloor(GuiGraphicsExtractor ctx, Font f) {
        EscData.Snapshot snap = EscData.get();
        String line = (snap != null && snap.discordMembers() >= 0)
            ? "DISCORD  " + snap.discordMembers() + " MEMBRES" + (snap.discordOnline() >= 0 ? "  -  " + snap.discordOnline() + " EN LIGNE" : "")
            : "REJOINS LA COMMUNAUTE REBORN";
        text(ctx, f, "REBORN ROLEPLAY", 20, 335, 0xFFFAEED6);
        text(ctx, f, line, 20, 346, 0xFFC8A096);
        for (int i = 0; i < SOCIALS.length; i++) {
            int id = H_SOCIAL + i;
            float hv = hov[id];
            int x = 520 + i * 28, y = 336 - Math.round(hv * 2);
            hits.add(new Hit(id, x - 2, 334, 20, 20));
            Identifier t = tex("esc/social_" + SOCIALS[i].id());
            ctx.blit(RenderPipelines.GUI_TEXTURED, t, x, y, 0f, 0f, 16, 16, 32, 32, 32, 32, lerp(0xFFC8C8C8, 0xFFFFFFFF, hv));
        }
    }

    private void drawTooltip(GuiGraphicsExtractor ctx, Font f, double mx, double my) {
        List<String> lines = null;
        if (hovered >= H_CHARM && hovered < H_CHARM + CHARMS.length) {
            Charm c = CHARMS[hovered - H_CHARM];
            lines = List.of(c.title(), c.how(), "+5 RBCOINS / HEURE DE JEU");
        } else if (hovered >= H_SOCIAL && hovered < H_SOCIAL + SOCIALS.length) {
            lines = List.of(SOCIALS[hovered - H_SOCIAL].label());
        }
        if (lines == null) return;
        int w = 0;
        for (String l : lines) w = Math.max(w, f.width(RebornFont.arcade(l)));
        w += 10;
        int h = lines.size() * 10 + 6;
        int x = (int) Math.min(VW - w - 4, mx + 8), y = (int) Math.max(4, my - h - 4);
        frame(ctx, x, y, w, h, 0xF0EADCB4, 0xFF8C6E46);
        for (int i = 0; i < lines.size(); i++) text(ctx, f, lines.get(i), x + 5, y + 4 + i * 10, i == 0 ? 0xFF8C1C20 : 0xFF46321E);
    }

    // ── fusuma : ouverture / fermeture ──
    private void drawFusuma(GuiGraphicsExtractor ctx, long now) {
        float p;
        if (closingAt > 0) p = 1f - ease(Math.min(1f, (now - closingAt) / (float) CLOSE_MS));
        else p = ease(opened(now));
        if (p >= 1f) return;
        int off = Math.round(320 * p);
        ctx.fill(0, 0, VW, VH, argb(0.35f * (1 - p), 0x000000));
        ctx.blit(RenderPipelines.GUI_TEXTURED, FUSUMA_L, -off, 0, 0f, 0f, 320, 360, 320, 360);
        ctx.blit(RenderPipelines.GUI_TEXTURED, FUSUMA_R, 320 + off, 0, 0f, 0f, 320, 360, 320, 360);
        ctx.fill(320 - off - 1, 0, 320 - off + 1, VH, 0xFF2E1A10);
    }

    // ═════════════════════════════════════════════════════════════ clics

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || closingAt > 0) return false;
        double mx = (mouseX - ox) / scale, my = (mouseY - oy) / scale;
        int id = -1;
        // les flèches passent avant le corps du carrousel
        for (Hit h : hits) if (h.in(mx, my) && (id < 0 || isArrow(h.id))) id = h.id;
        if (id < 0) return false;
        Minecraft mc = Minecraft.getInstance();
        EscData.Snapshot snap = EscData.get();
        switch (id) {
            case 0 -> { RebornSounds.playReborn("esc.close", 1.0f, 0.55f); closeSoundPlayed = true; closingAt = System.currentTimeMillis(); }
            case 1 -> { select(); mc.setScreenAndShow(new ConfigShellScreen(screen)); }
            case 2 -> { select(); mc.setScreenAndShow(new ReportScreen(screen)); }
            case 3 -> { select(); mc.setScreenAndShow(new DisconnectConfirmScreen(screen)); }
            case H_SHOP -> { select(); mc.setScreenAndShow(new ShopScreen(screen)); }
            case H_STREAM_PREV, H_STREAM_NEXT -> {
                streamIdx += id == H_STREAM_NEXT ? 1 : -1; lastStreamRotate = System.currentTimeMillis();
                RebornSounds.playReborn("esc.page", 1.0f, 0.5f);
            }
            case H_BLOG_PREV, H_BLOG_NEXT -> {
                blogIdx += id == H_BLOG_NEXT ? 1 : -1; lastBlogRotate = System.currentTimeMillis();
                RebornSounds.playReborn("esc.page", 1.1f, 0.5f);
            }
            case H_STREAM -> {
                if (snap != null && !snap.streams().isEmpty()) open(snap.streams().get(Math.floorMod(streamIdx, snap.streams().size())).url());
            }
            case H_BLOG -> {
                if (snap != null && !snap.patchNotes().isEmpty()) open(snap.patchNotes().get(Math.floorMod(blogIdx, snap.patchNotes().size())).url());
            }
            default -> {
                if (id >= H_CHARM && id < H_CHARM + CHARMS.length) {
                    charmKick[id - H_CHARM] = 1f;
                    RebornSounds.playReborn("esc.charm", 1.0f, 0.45f);
                } else if (id >= H_SOCIAL && id < H_SOCIAL + SOCIALS.length) {
                    open(SOCIALS[id - H_SOCIAL].url());
                }
            }
        }
        return true;
    }

    private static boolean isArrow(int id) {
        return id == H_STREAM_PREV || id == H_STREAM_NEXT || id == H_BLOG_PREV || id == H_BLOG_NEXT;
    }

    private static void select() { RebornSounds.playReborn("esc.select", 1.0f, 0.55f); }

    private static void open(String url) {
        if (url == null || url.isBlank()) return;
        select();
        try { Util.getPlatform().openUri(URI.create(url)); } catch (Exception ignored) {}
    }

    // ═════════════════════════════════════════════════════════════ petits outils

    private void arrows(GuiGraphicsExtractor ctx, Font f, int x, int y, int w, int idx, int size, int prevId, int nextId, int col) {
        String ind = (idx + 1) + "/" + size;
        centered(ctx, f, ind, x + w / 2, y + 2, col);
        text(ctx, f, "<", x + 4, y + 2, lerp(col, 0xFFF6CC78, hov[prevId]));
        text(ctx, f, ">", x + w - 10, y + 2, lerp(col, 0xFFF6CC78, hov[nextId]));
        hits.add(new Hit(prevId, x, y - 2, 16, 13));
        hits.add(new Hit(nextId, x + w - 16, y - 2, 16, 13));
    }

    private static void plate(GuiGraphicsExtractor ctx, Font f, String s, int cx, int y, int fill) {
        int w = f.width(RebornFont.arcade(s)) + 14;
        frame(ctx, cx - w / 2, y, w, 13, fill, 0xFFF6CC78);
        centered(ctx, f, s, cx, y + 3, 0xFFFAEED6);
    }

    private static void frame(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int fill, int border) {
        ctx.fill(x, y, x + w, y + h, fill);
        outline(ctx, x, y, w, h, border);
    }

    private static void outline(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int c) {
        ctx.fill(x, y, x + w, y + 1, c); ctx.fill(x, y + h - 1, x + w, y + h, c);
        ctx.fill(x, y, x + 1, y + h, c); ctx.fill(x + w - 1, y, x + w, y + h, c);
    }

    private static void ring(GuiGraphicsExtractor ctx, int x, int y, int s, int c) {
        double r = s / 2.0, cx = x + r, cy = y + r;
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI * 2 / 64;
            int px = (int) Math.round(cx + Math.cos(a) * r), py = (int) Math.round(cy + Math.sin(a) * r);
            ctx.fill(px, py, px + 1, py + 1, c);
        }
    }

    private static void glow(GuiGraphicsExtractor ctx, float cx, float cy, int size, int argb) {
        ctx.blit(RenderPipelines.GUI_TEXTURED, GLOW, Math.round(cx - size / 2f), Math.round(cy - size / 2f), 0f, 0f,
            size, size, 64, 64, 64, 64, argb);
    }

    private static void text(GuiGraphicsExtractor ctx, Font f, String s, int x, int y, int c) {
        ctx.text(f, RebornFont.arcade(s), x, y, c, false);
    }

    private static void centered(GuiGraphicsExtractor ctx, Font f, String s, int cx, int y, int c) {
        Component t = RebornFont.arcade(s);
        ctx.text(f, t, cx - f.width(t) / 2, y, c, false);
    }

    private static void bigCentered(GuiGraphicsExtractor ctx, Font f, String s, int cx, int y, float sc, int c) {
        Component t = RebornFont.arcade(s);
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx - f.width(t) * sc / 2f, y);
        ctx.pose().scale(sc, sc);
        ctx.text(f, t, 0, 0, c, false);
        ctx.pose().popMatrix();
    }

    private static void smallCentered(GuiGraphicsExtractor ctx, Font f, String s, int cx, int y, float sc, int c) {
        bigCentered(ctx, f, s, cx, y, sc, c);
    }

    private static String fit(Font f, String s, int maxW) {
        if (f.width(RebornFont.arcade(s)) <= maxW) return s;
        while (s.length() > 1 && f.width(RebornFont.arcade(s + "..")) > maxW) s = s.substring(0, s.length() - 1);
        return s.trim() + "..";
    }

    private static List<String> wrap(Font f, String s, int maxW, int maxLines) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String cand = cur.length() == 0 ? word : cur + " " + word;
            if (f.width(RebornFont.arcade(cand)) <= maxW) { cur = new StringBuilder(cand); continue; }
            if (cur.length() > 0) out.add(cur.toString());
            cur = new StringBuilder(word);
            if (out.size() == maxLines) break;
        }
        if (cur.length() > 0 && out.size() < maxLines) out.add(cur.toString());
        if (out.size() == maxLines && out.size() > 0) out.set(maxLines - 1, fit(f, out.get(maxLines - 1), maxW));
        return out;
    }

    private static float ease(float t) { return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2; }

    private static int argb(float a, int rgb) {
        int al = Math.max(0, Math.min(255, Math.round(a * 255)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    private static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = 0;
        for (int s = 0; s < 32; s += 8) {
            int ca = (a >>> s) & 0xFF, cb = (b >>> s) & 0xFF;
            r |= (Math.round(ca + (cb - ca) * t) & 0xFF) << s;
        }
        return r;
    }
}
