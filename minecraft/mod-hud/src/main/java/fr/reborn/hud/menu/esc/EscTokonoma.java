package fr.reborn.hud.menu.esc;

import fr.reborn.hud.menu.IconPack;
import fr.reborn.hud.menu.RebornBranding;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.menu.screens.ConfigShellScreen;
import fr.reborn.hud.menu.shop.ShopData;
import fr.reborn.hud.menu.stats.StatsData;
import fr.reborn.hud.menu.widget.DisconnectConfirmScreen;
import fr.reborn.hud.menu.widget.ReportScreen;
import fr.reborn.hud.menu.widget.ShopScreen;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.UiAmbience;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static fr.reborn.hud.menu.esc.EchapArt.*;

/**
 * Menu Échap « tokonoma » : une pièce traditionnelle de nuit. Fenêtre ronde sur le village,
 * logo Reborn Roleplay et menu en plaques laquées suspendues à gauche ; poutre aux emblèmes des
 * cinq villages et, dans l'alcôve, quatre éléments distincts : Échoppe (devanture noren),
 * Stream (kamishibai), Dev blog (kawaraban) et Récompenses (omamori). Réseaux en bas à droite.
 *
 * <p>Ouverture : deux fusuma coulissent ; fermeture par « Reprendre » : elles se referment.
 * La scène fait 640×360 unités, affichée à un nombre entier k de pixels écran par unité
 * (DA §6 : une seule grille, aucun filtrage), le plus grand qui laisse voir toute la zone utile ; la pièce
 * prolongée remplit le reste de l'écran (voir {@link EchapArt}) ; le décor fixe est une texture (piece.png), le
 * code ne pose que ce qui bouge et le texte. Textures : {@code textures/gui/echap/*}
 * ({@code tools/gen_echap_textures.py}, cf. {@link EchapArt}) ; sons : {@code sounds/esc/*}.
 */
public final class EscTokonoma {

    private static final int OPEN_MS = 720, CLOSE_MS = 300;
    /** Banc de captures : zone survolée imposée ({@code REBORN_ESC_SURVOL=<id>}, client de dev seulement). */
    private static final int SURVOL_FORCE = survolForce();

    private static int survolForce() {
        try {
            String v = System.getenv("REBORN_ESC_SURVOL");
            return v == null || v.isBlank() ? -1 : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Logo Reborn Roleplay en pixel art à l'échelle de la scène (150×52, 1 texel = 1 unité). */
    private static final Identifier LOGO = tex("logo");

    private static final String[] VILLAGE_IDS = {"konoha", "suna", "kiri", "kumo", "iwa"};
    private static final String[] VILLAGE_NAMES = {"KONOHA", "SUNA", "KIRI", "KUMO", "IWA"};
    private static final String[] VILLAGE_TITRES = {"Konohagakure", "Sunagakure", "Kirigakure", "Kumogakure", "Iwagakure"};
    private static final String[] VILLAGE_LANDS = {"Pays du Feu", "Pays du Vent", "Pays de l'Eau", "Pays de la Foudre",
        "Pays de la Terre"};

    private static final String[] MENU = {"REPRENDRE", "PARAMETRES", "SIGNALER", "DECONNEXION"};

    /** Activations qui rapportent des RBCoins par heure de jeu (état réel à brancher sur l'API). */
    private record Charm(String title, String how) {}
    private static final Charm[] CHARMS = {
        new Charm("Boost Discord", "Booste le Discord Reborn."),
        new Charm("Tag dans le pseudo", "Ajoute le tag Reborn à ton pseudo."),
        new Charm("Lien dans la bio", "Mets le lien Reborn dans ta bio."),
        new Charm("Stream Reborn", "Streame sur le serveur."),
        new Charm("Premium", "Soutiens le serveur."),
    };
    private static final int[] CHARM_COL = {0xDC5AC8, 0x468CDC, 0x46AA64, 0x965AF0, 0xE6B43C};

    private record Social(String label, String url) {}
    private static final Social[] SOCIALS = {
        new Social("Discord", RebornBranding.DISCORD_URL),
        new Social("X", RebornBranding.X_URL),
        new Social("YouTube", RebornBranding.YOUTUBE_URL),
        new Social("Site", RebornBranding.SITE_URL),
    };

    // Zones cliquables (ids) recalculées à chaque image.
    private static final int H_SHOP = 10, H_STREAM = 11, H_STREAM_PREV = 12, H_STREAM_NEXT = 13,
        H_BLOG = 14, H_BLOG_PREV = 15, H_BLOG_NEXT = 16, H_CHARM = 20, H_SOCIAL = 30, H_KAMON = 40;
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
    private final int[][] stars;
    /** Origine de la scène, en unités (entières) depuis le coin de l'écran. */
    private int ox, oy;
    private double mxU, myU;

    public EscTokonoma(Screen screen) {
        this.screen = screen;
        Random r = new Random(7);
        stars = new int[18][3];
        for (int[] s : stars) {
            double a = Math.PI + r.nextDouble() * Math.PI, d = 10 + r.nextDouble() * 38;
            s[0] = (int) Math.round(Math.cos(a) * d);
            s[1] = (int) Math.round(Math.sin(a) * d * 0.8 - 4);
            s[2] = r.nextInt(628);
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

    /** Partie visible de la fenêtre, en unités (pour caler les infobulles). */
    private int visL, visT, visR, visB;

    /**
     * Échelle et origine de la scène pour la fenêtre courante : k entier, le plus grand dont la zone utile
     * (SAFE_W × SAFE_H) tient dans la fenêtre ; origine entière en unités (négative : bord de scène rogné).
     */
    private void geometrie() {
        var w = Minecraft.getInstance().getWindow();
        int pw = w.getWidth(), ph = w.getHeight();
        k = Math.max(1, Math.min(pw / SAFE_W, ph / SAFE_H));
        int lw = -Math.floorDiv(-pw, k), lh = -Math.floorDiv(-ph, k);
        ox = Math.floorDiv(lw - VW, 2);
        oy = Math.floorDiv(lh - VH, 2);
        visL = -ox;
        visT = -oy;
        visR = lw - ox;
        visB = lh - oy;
    }

    private void versUnites(double mouseX, double mouseY) {
        double gs = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        mxU = mouseX * gs / k - ox;
        myU = mouseY * gs / k - oy;
    }

    public void render(GuiGraphicsExtractor ctx, int width, int height, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        if (!openSoundPlayed) { openSoundPlayed = true; RebornSounds.playReborn("esc.open", 1.0f, 0.6f); }
        if (closingAt > 0 && now - closingAt >= CLOSE_MS) { Minecraft.getInstance().setScreenAndShow(null); return; }

        geometrie();
        versUnites(mouseX, mouseY);
        float time = (now - openedAt) / 1000f;

        DaKit.unites(ctx, k);
        ctx.pose().translate(ox, oy);
        // la pièce prolongée couvre l'écran ; un aplat seulement au-delà (formats extrêmes)
        if (visL < -PIECE_MX || visT < -PIECE_MY || visR > VW + PIECE_MX || visB > VH + PIECE_MY) {
            ctx.fill(visL, visT, visR, visB, 0xFF0E0608);
        }

        hits.clear();
        Font f = Minecraft.getInstance().font;
        blit(ctx, PIECE, -PIECE_MX, -PIECE_MY, 0, 0, PIECE_W, PIECE_H, PIECE_W, PIECE_H);
        drawWindow(ctx, time);
        drawIdentity(ctx, f);
        drawMenu(ctx, f);
        drawKamon(ctx, f, time);
        drawShop(ctx, f, time);
        drawStream(ctx, f, now, time);
        drawBlog(ctx, f, now);
        drawRack(ctx, f, time, dt);
        drawFloor(ctx, f);

        // survol (après avoir enregistré toutes les zones)
        int h = -1;
        for (Hit hit : hits) if (hit.in(mxU, myU)) h = hit.id;
        if (SURVOL_FORCE >= 0) h = SURVOL_FORCE;
        if (h != hovered) {
            hovered = h;
            boolean charm = h >= H_CHARM && h < H_CHARM + CHARMS.length;
            if (h >= 0 && opened(now) >= 1f) RebornSounds.playReborn(charm ? "esc.charm" : "esc.hover", charm ? 1.3f : 1.0f,
                charm ? 0.18f : 0.3f);
            if (charm) charmKick[h - H_CHARM] = 1f;
        }
        for (int i = 0; i < hov.length; i++) {
            float target = (i == hovered) ? 1f : 0f;
            hov[i] += (target - hov[i]) * Math.min(1f, dt * 14f);
        }
        drawTooltip(ctx, f);
        drawFusuma(ctx, now);
        DaKit.finUnites(ctx);
    }

    private float opened(long now) { return Math.min(1f, Math.max(0f, (now - openedAt - 100) / (float) (OPEN_MS - 100))); }

    // ── fenêtre ronde : étoiles qui scintillent, lumières du village, étoile filante ──
    private void drawWindow(GuiGraphicsExtractor ctx, float time) {
        int x = 51, y = 28, cx = 106, cy = 74;
        fr.reborn.hud.ui.LotGui etoiles = fr.reborn.hud.ui.LotGui.ouvrir(ctx);   // 18 étoiles = 1 élément GUI
        for (int[] s : stars) {
            float a = 0.25f + 0.75f * (0.5f + 0.5f * (float) Math.sin(time * 1.7f + s[2] / 100f));
            int sx = cx + s[0], sy = cy + s[1];
            etoiles.fill(sx, sy, sx + 1, sy + 1, DaKit.argb(0xE8E4FF, a));
        }
        etoiles.fermer();
        float flick = 0.70f + 0.30f * (float) Math.sin(time * 4.3f) * (float) Math.sin(time * 1.9f + 1);
        blit(ctx, FENETRE, x, y, 112, 0, 112, 112, 224, 112, DaKit.blanc(flick));
        // étoile filante toutes les ~9 s, gardée dans le disque
        float cyc = time % 9f;
        if (cyc > 6.5f && cyc < 7.3f) {
            float p = (cyc - 6.5f) / 0.8f;
            fr.reborn.hud.ui.LotGui trainee = fr.reborn.hud.ui.LotGui.ouvrir(ctx);
            for (int i = 0; i < 6; i++) {
                float q = p - i * 0.035f;
                if (q < 0) continue;
                int px = Math.round(cx - 34 + q * 50), py = Math.round(cy - 34 + q * 18);
                if (Math.hypot(px - cx - 1, py - cy - 10) < 44) trainee.fill(px, py, px + 1, py + 1, DaKit.argb(0xFFFFFF, 1f - i / 6f));
            }
            trainee.fermer();
        }
        // halo de lune qui respire
        float gl = 0.10f + 0.06f * (float) Math.sin(time * 0.9f);
        halo(ctx, x + 78, y + 30, 1, DaKit.argb(0xFFECC8, gl));
        blit(ctx, FENETRE, x, y, 0, 0, 112, 112, 224, 112);
    }

    // ── logo + identité du personnage ──
    private void drawIdentity(GuiGraphicsExtractor ctx, Font f) {
        blit(ctx, LOGO, 32, 148, 0, 0, 150, 52, 150, 52);
        int vi = playerVillage();
        String who = playerName();
        float nw = largeurArc(f, who, MOYEN) + (vi >= 0 ? 13 : 0);
        float x = 107 - nw / 2f;
        if (vi >= 0) {
            blit(ctx, KAMONS, Math.round(x), 203, vi * 22, 66, 10, 10, 110, 78, 0xFF8E1628);
            x += 13;
        }
        arc(ctx, f, who, x, 205, ENCRE, 0, MOYEN, false);
        String sub = playerSub(vi);
        if (!sub.isEmpty()) inter(ctx, f, sub, 107, 215.5f, ENCRE_DOUCE, 1, 7);
    }

    private static String playerName() {
        StatsData.Snapshot s = StatsData.get();
        String name = s != null ? s.name() : null;
        if (name == null || name.isBlank() || "?".equals(name)) {
            var p = Minecraft.getInstance().player;
            name = p != null ? p.getName().getString() : "";
        }
        return EscData.arcadeSafe(name, 22);
    }

    private static String playerSub(int vi) {
        StatsData.Snapshot s = StatsData.get();
        String rank = s != null && s.rank() != null && !s.rank().isBlank() && !"?".equals(s.rank()) ? s.rank() : null;
        String vil = vi >= 0 ? VILLAGE_TITRES[vi] : null;
        if (rank == null) return vil == null ? "" : vil;
        return vil == null ? rank : rank + " · " + vil;
    }

    private static int playerVillage() {
        StatsData.Snapshot s = StatsData.get();
        if (s == null || s.village() == null) return -1;
        String v = s.village().toLowerCase(Locale.ROOT);
        for (int i = 0; i < VILLAGE_IDS.length; i++) if (v.startsWith(VILLAGE_IDS[i])) return i;
        return -1;
    }

    // ── menu : plaques laquées suspendues ──
    private void drawMenu(GuiGraphicsExtractor ctx, Font f) {
        for (int i = 0; i < MENU.length; i++) {
            int y = 230 + i * 22;
            float hv = hov[i];
            hits.add(new Hit(i, 41, y, 132, 15));
            boolean hot = hv > 0.5f;
            int x = 41 + Math.round(hv * 3);
            int row = !hot ? 0 : (i == 3 ? 2 : 1);
            blit(ctx, PLAQUES, x - 8, y - 2, 0, row * 20, 144, 20, 144, 60);
            int col = hot ? CREME : CREME_DOUX;
            arc(ctx, f, MENU[i], x + 66, y + 4.5f, col, 1, MOYEN, hot);
        }
    }

    // ── poutre aux kamon ──
    private void drawKamon(GuiGraphicsExtractor ctx, Font f, float time) {
        int mine = playerVillage();
        for (int i = 0; i < 5; i++) {
            int x = 262 + i * 70, y = 25;
            boolean m = i == mine;
            float hv = hov[H_KAMON + i];
            hits.add(new Hit(H_KAMON + i, x - 2, y - 2, 22, 22));
            if (m) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(time * 2.2f);
                halo(ctx, x + 9, y + 9, 1, DaKit.argb(0xF6CC78, 0.10f + 0.10f * pulse));
            }
            int row = m ? 2 : hv > 0.5f ? 1 : 0;
            blit(ctx, KAMONS, x - 2, y - 2, i * 22, row * 22, 22, 22, 110, 78);
            if (m) {
                int t = tailleQuiTient(f, VILLAGE_NAMES[i], 44);
                arc(ctx, f, VILLAGE_NAMES[i], x + 23, y + (t == MOYEN ? 6 : 7), OR_CLAIR, 0, t, true);
            }
        }
    }

    // ── Échoppe : devanture noren + chōchin ──
    private void drawShop(GuiGraphicsExtractor ctx, Font f, float time) {
        int x = 236, y = 60, w = 177, h = 121;
        float hv = hov[H_SHOP];
        hits.add(new Hit(H_SHOP, x, y, w, h));
        float flick = 0.8f + 0.2f * (float) Math.sin(time * 6.1f) * (float) Math.sin(time * 2.3f);
        for (int side = 0; side < 2; side++) {
            int lx = side == 0 ? x + 17 : x + w - 17, ly = y + 44;
            halo(ctx, lx, ly + 16, 1, DaKit.argb(0xFF9A50, (0.22f + 0.18f * hv) * flick));
            float sway = (float) Math.sin(time * 1.4f + side) * 0.05f;
            ctx.pose().pushMatrix();
            ctx.pose().translate(lx, ly);
            ctx.pose().rotate(sway);
            blit(ctx, CHOCHIN, -10, 0, hv > 0.5f ? 20 : 0, 0, 20, 32, 40, 32);
            ctx.pose().popMatrix();
        }
        arc(ctx, f, "ECHOPPE", x + w / 2f, y + 52, CREME, 1, GRAND, true);
        long ryo = ShopData.received() ? ShopData.ryo() : Long.MIN_VALUE;
        if (ryo != balRyo || bal == null) { balRyo = ryo; bal = ryo == Long.MIN_VALUE ? "— ryo" : fmt(ryo) + " ryo"; }
        float bw = largeurInter(f, bal, 8) + 10;
        float bx = x + w / 2f - bw / 2f;
        ryo(ctx, Math.round(bx), y + 75);
        inter(ctx, f, bal, bx + 10, y + 78, OR_CLAIR, 0, 8);
        // « Entrer ›» en bas à droite, sur le comptoir
        float ew = largeurInter(f, "Entrer", 8);
        int ex = Math.round(x + w - 12 - ew - 10 + hv * 2);
        inter(ctx, f, "Entrer", ex, y + h - 8, DaKit.argb(0xF5E9D0, 0.75f + 0.25f * hv), 0, 8);
        fleche(ctx, Math.round(ex + ew + 3), y + h - 12, true, hv > 0.5f);
        if (hv > 0.02f) contour(ctx, x - 2, y - 2, w + 4, h + 4, DaKit.argb(0xF2D08A, 0.85f * hv));
    }

    // ── Stream : kamishibai ──
    private void drawStream(GuiGraphicsExtractor ctx, Font f, long now, float time) {
        int x = 429, y = 60;
        float hv = hov[H_STREAM];
        int sx = x + 30, sy = y + 23, sw = 117, sh = 89;
        int ts = tailleQuiTient(f, "STREAM", 38);
        arc(ctx, f, "STREAM", x + 88, y - (ts == MOYEN ? 3 : 2), CREME, 1, ts, true);
        hits.add(new Hit(H_STREAM, sx, sy, sw, sh));

        EscData.Snapshot snap = EscData.get();
        List<EscData.Stream> streams = snap != null ? snap.streams() : List.of();
        if (streams.isEmpty()) {
            IconPack.twitch(ctx, sx + sw / 2 - 8, sy + 22, 16, 0xFF5A5478);
            inter(ctx, f, "Aucun stream en ce moment", sx + sw / 2f, sy + 50, 0xFF8C84A0, 1, 7);
            return;
        }
        if (streams.size() > 1 && now - lastStreamRotate > 7000) { streamIdx++; lastStreamRotate = now; }
        int idx = Math.floorMod(streamIdx, streams.size());
        EscData.Stream s = streams.get(idx);
        // badge
        String badge = s.live() ? "LIVE" : "HORS LIGNE";
        int bw = Math.round(largeurArc(f, badge, PETIT)) + 10;
        badge(ctx, sx + 5, sy + 5, bw, s.live());
        arc(ctx, f, badge, sx + 5 + bw / 2f, sy + 8.5f, CREME, 1, PETIT, false);
        if (s.live()) {
            float p = 0.5f + 0.5f * (float) Math.sin(time * 5f);
            blit(ctx, PIECES, sx + bw + 8, sy + 9, 92, 14, 3, 3, 128, 48, DaKit.blanc(0.3f + 0.7f * p));
        }
        String nom = s.nomTexte() != null && !s.nomTexte().isBlank() ? s.nomTexte() : (s.name() != null ? s.name() : "Stream");
        arc(ctx, f, ellipseArc(f, EscData.arcadeSafe(nom, 24), sw - 12, MOYEN), sx + sw / 2f, sy + 30,
            hv > 0.5f ? 0xFFFFFFFF : 0xFFE6DCF0, 1, MOYEN, true);
        String titre = s.titreTexte() != null && !s.titreTexte().isBlank() ? s.titreTexte() : null;
        if (s.live() && titre != null) {
            List<String> l = lignes(f, titre, sw - 14, 7, 2);
            for (int i = 0; i < l.size(); i++) inter(ctx, f, l.get(i), sx + sw / 2f, sy + 46 + i * 7, 0xFFA096B4, 1, 7);
        } else {
            inter(ctx, f, "Hors ligne", sx + sw / 2f, sy + 46, 0xFF7C7490, 1, 7);
        }
        if (streams.size() > 1) arrows(ctx, f, sx + 4, sy + sh - 13, sw - 8, idx, streams.size(), H_STREAM_PREV, H_STREAM_NEXT,
            0xFFC8BEDC);
        if (hv > 0.02f) contour(ctx, sx - 3, sy - 3, sw + 6, sh + 6, DaKit.argb(0xF2D08A, 0.85f * hv));
    }

    // ── Dev blog : kawaraban ──
    private void drawBlog(GuiGraphicsExtractor ctx, Font f, long now) {
        int x = 236 + 21, y = 197;
        float hv = hov[H_BLOG];
        int lift = Math.round(hv);
        blit(ctx, KAWARABAN, x, y - lift, 0, 0, 134, 122, 134, 122);
        int px = x + 15, py = y + 21 - lift, pw = 104;
        hits.add(new Hit(H_BLOG, px, py + 14, pw, 82));
        arc(ctx, f, "DEV BLOG", px + 5, py + 3.5f, CREME, 0, MOYEN, false);
        EscData.Snapshot snap = EscData.get();
        List<EscData.PatchNote> notes = snap != null ? snap.patchNotes() : List.of();
        if (notes.isEmpty()) {
            inter(ctx, f, "Aucune note pour l'instant", px + pw / 2f, py + 50, ENCRE_DOUCE, 1, 7);
            return;
        }
        if (notes.size() > 1 && now - lastBlogRotate > 8000) { blogIdx++; lastBlogRotate = now; }
        int idx = Math.floorMod(blogIdx, notes.size());
        EscData.PatchNote n = notes.get(idx);
        if (n.date() != null) {
            String[] parts = n.date().split(" ");              // « 12 MAI 2026 » → « 12 MAI »
            String d = parts.length >= 2 ? parts[0] + " " + parts[1] : n.date();
            arc(ctx, f, d, px + pw - 5, py + 4.5f, 0xFFC8BEAA, 2, PETIT, false);
        }
        arc(ctx, f, ellipseArc(f, n.version() != null ? n.version() : "PATCH", pw - 16, MOYEN), px + pw / 2f, py + 20,
            ENCRE_ROUGE, 1, MOYEN, false);
        filet(ctx, px + pw / 2 - 30, py + 31, 60);
        String titre = n.titreTexte() != null && !n.titreTexte().isBlank() ? n.titreTexte() : (n.title() != null ? n.title() : "");
        List<String> l = lignes(f, titre, pw - 14, 8, 4);
        for (int i = 0; i < l.size(); i++) inter(ctx, f, l.get(i), px + pw / 2f, py + 40 + i * 8, ENCRE, 1, 8);
        float lw = largeurInter(f, "Lire", 8);
        inter(ctx, f, "Lire", px + pw - 14 - lw, py + 88, hv > 0.5f ? 0xFFC82A2E : ENCRE_ROUGE, 0, 8);
        fleche(ctx, px + pw - 11, py + 84, true, hv > 0.5f);
        if (notes.size() > 1) arrows(ctx, f, px + 2, py + 84, 56, idx, notes.size(), H_BLOG_PREV, H_BLOG_NEXT, ENCRE_DOUCE);
    }

    // ── Récompenses : omamori ──
    private void drawRack(GuiGraphicsExtractor ctx, Font f, float time, float dt) {
        int x = 429, y = 197;
        arc(ctx, f, "RECOMPENSES", x + 10, y + 9, OR_CLAIR, 0, MOYEN, true);
        inter(ctx, f, "+5 / h", x + 167, y + 12, OR, 2, 8);
        for (int i = 0; i < CHARMS.length; i++) {
            int cx = x + 24 + i * 32, top = y + 26;
            float hv = hov[H_CHARM + i];
            charmKick[i] = Math.max(0f, charmKick[i] - dt * 0.9f);
            float ang = (float) Math.sin(time * 1.6f + i * 1.3f) * 0.05f
                + (float) Math.sin(time * 9f) * 0.22f * charmKick[i];
            hits.add(new Hit(H_CHARM + i, cx - 13, top, 26, 42));
            halo(ctx, cx, top + 24, 1, DaKit.argb(CHARM_COL[i], 0.10f + 0.25f * hv));
            ctx.pose().pushMatrix();
            ctx.pose().translate(cx, top);
            ctx.pose().rotate(ang);
            blit(ctx, OMAMORI, -12, 0, i * 24, 0, 24, 40, 120, 40);
            ctx.pose().popMatrix();
        }
        piece(ctx, x + 9, y + 102);
        inter(ctx, f, "RBCoins par heure de jeu", x + 23, y + 107, OR_CLAIR, 0, 8);
    }

    private long balRyo;
    private String bal;
    private EscData.Snapshot floorSnap;
    private String floorLine;

    // ── plancher : communauté + réseaux ──
    private void drawFloor(GuiGraphicsExtractor ctx, Font f) {
        EscData.Snapshot snap = EscData.get();
        if (snap != floorSnap || floorLine == null) {   // String.format à chaque image coûtait : calculé au paquet
            floorSnap = snap;
            floorLine = (snap != null && snap.discordMembers() >= 0)
                ? "Discord : " + fmt(snap.discordMembers()) + " membres" + (snap.discordOnline() >= 0 ? " · " + fmt(snap.discordOnline()) + " en ligne" : "")
                : "Rejoins la communauté Reborn";
        }
        String line = floorLine;
        // dans la zone utile (y ≤ 346) : le bas du plancher peut sortir de l'écran
        arc(ctx, f, "REBORN ROLEPLAY", 24, 333, CREME, 0, MOYEN, true);
        inter(ctx, f, line, 24, 342.5f, DaKit.argb(0xE8C8C0, 0.75f), 0, 7);
        for (int i = 0; i < SOCIALS.length; i++) {
            int id = H_SOCIAL + i;
            float hv = hov[id];
            int x = 526 + i * 26, y = 330 - Math.round(hv * 2);
            hits.add(new Hit(id, x - 3, 327, 20, 20));
            blit(ctx, RESEAUX, x - 1, y - 1, i * 16, hv > 0.5f ? 16 : 0, 16, 16, 64, 32);
        }
    }

    private void drawTooltip(GuiGraphicsExtractor ctx, Font f) {
        String titre = null;
        List<String> lignes = new ArrayList<>();
        if (hovered >= H_CHARM && hovered < H_CHARM + CHARMS.length) {
            Charm c = CHARMS[hovered - H_CHARM];
            titre = c.title();
            lignes.add(c.how());
            lignes.add("+5 RBCoins par heure de jeu");
        } else if (hovered >= H_SOCIAL && hovered < H_SOCIAL + SOCIALS.length) {
            titre = SOCIALS[hovered - H_SOCIAL].label();
        } else if (hovered >= H_KAMON && hovered < H_KAMON + 5) {
            int i = hovered - H_KAMON;
            titre = VILLAGE_TITRES[i];
            lignes.add(VILLAGE_LANDS[i]);
        }
        if (titre == null) return;
        String tA = EscData.arcadeSafe(titre, 30);
        float w = largeurArc(f, tA, MOYEN);
        for (String l : lignes) w = Math.max(w, largeurInter(f, l, 8));
        int bw = Math.round(w) + 14, bh = 15 + lignes.size() * 8;
        int x = (int) Math.min(Math.min(VW, visR) - bw - 8, mxU + 8), y = (int) Math.max(Math.max(0, visT) + 4, myU - bh - 6);
        infobulle(ctx, x, y, bw, bh);
        arc(ctx, f, tA, x + 7, y + 5, ENCRE_ROUGE, 0, MOYEN, false);
        for (int i = 0; i < lignes.size(); i++) inter(ctx, f, lignes.get(i), x + 7, y + 17 + i * 8, i == lignes.size() - 1 && lignes.size() > 1 ? ENCRE_DOUCE : ENCRE, 0, 8);
    }

    // ── fusuma : ouverture / fermeture ──
    private void drawFusuma(GuiGraphicsExtractor ctx, long now) {
        float p;
        if (closingAt > 0) p = 1f - ease(Math.min(1f, (now - closingAt) / (float) CLOSE_MS));
        else p = ease(opened(now));
        if (p >= 1f) return;
        int off = Math.round(320 * p);
        ctx.fill(visL, visT, visR, visB, DaKit.argb(0x000000, 0.35f * (1 - p)));
        // un second vantail de chaque côté couvre la pièce prolongée
        blit(ctx, FUSUMA_L, -320 - off, 0, 0, 0, 320, 360, 320, 360);
        blit(ctx, FUSUMA_L, -off, 0, 0, 0, 320, 360, 320, 360);
        blit(ctx, FUSUMA_R, 320 + off, 0, 0, 0, 320, 360, 320, 360);
        blit(ctx, FUSUMA_R, 640 + off, 0, 0, 0, 320, 360, 320, 360);
        ctx.fill(320 - off - 1, 0, 320 - off + 1, VH, 0xFF2E1A10);
    }

    // ═════════════════════════════════════════════════════════════ clics

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || closingAt > 0) return false;
        geometrie();
        versUnites(mouseX, mouseY);
        int id = -1;
        // les flèches passent avant le corps du carrousel
        for (Hit h : hits) if (h.in(mxU, myU) && (id < 0 || isArrow(h.id))) id = h.id;
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

    /** Carrousel : flèches « ‹ › » d'or et compteur Inter au centre. */
    private void arrows(GuiGraphicsExtractor ctx, Font f, int x, int y, int w, int idx, int size, int prevId, int nextId, int col) {
        inter(ctx, f, (idx + 1) + " / " + size, x + w / 2f, y + 4.5f, col, 1, 7);
        fleche(ctx, x, y, false, hov[prevId] > 0.5f);
        fleche(ctx, x + w - 8, y, true, hov[nextId] > 0.5f);
        hits.add(new Hit(prevId, x - 3, y - 3, 14, 15));
        hits.add(new Hit(nextId, x + w - 11, y - 3, 14, 15));
    }

    /** Contour d'or d'1 unité (survol), coins abattus. */
    private static void contour(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int c) {
        ctx.fill(x + 1, y, x + w - 1, y + 1, c);
        ctx.fill(x + 1, y + h - 1, x + w - 1, y + h, c);
        ctx.fill(x, y + 1, x + 1, y + h - 1, c);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, c);
    }

    private static String fmt(long v) {
        return String.format(Locale.FRANCE, "%,d", v).replace(' ', ' ').replace(' ', ' ');
    }

    private static float ease(float t) { return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2; }
}
