package fr.reborn.hud.mixin.menu;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.IconPack;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.esc.EscData;
import fr.reborn.hud.menu.esc.EscPanels;
import fr.reborn.hud.menu.esc.EscTabButton;
import fr.reborn.hud.menu.screens.ConfigShellScreen;
import fr.reborn.hud.menu.widget.DisconnectConfirmScreen;
import fr.reborn.hud.menu.widget.RebornButton;
import fr.reborn.hud.menu.widget.ReportScreen;
import fr.reborn.hud.menu.widget.ShopScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.GameMenuScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Menu pause (ESC) Reborn — layout façon <b>Zenkai</b>, cluster COMPACT centré.
 * GAUCHE : Boutique. DROITE : Stream + Dev Blog côte à côte (carrés) puis
 * Récompenses. BAS : barre communauté (Discord).
 *
 * <p>Le titre vanilla « Game Menu » est un {@code TextWidget} ajouté dans
 * {@code init()} — on retire donc TOUS les enfants au RETURN de {@code init}
 * (après leur ajout) puis on rebuild. Le render vanilla est annulé pour tout
 * dessiner nous-mêmes (fond opaque + dégradés + halo).
 */
@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/esc-mixin");

    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("reborn", "textures/gui/title/logo.png");
    private static final int LOGO_TEX_W = 2048;
    private static final int LOGO_TEX_H = 717;

    private static final String[] TAB_LABELS = {"REPRENDRE", "PARAMETRES", "REPORT", "DECONNEXION"};
    private static final int TAB_H = 18;
    private static final int TAB_PAD = 11;
    private static final int TAB_GAP = 8;
    private static final int GAP = 12;
    private static final int BOTTOM_H = 46;
    private static final int RADIUS = 10;
    private static final long ROTATE_MS = 6000L;
    private static final int ARROW_W = 12;

    // Carrousels Stream / Dev Blog : index courant + horodatage de la dernière
    // rotation (auto toutes les ROTATE_MS ; reset au clic manuel sur une flèche).
    private int reborn$streamIdx = 0;
    private int reborn$blogIdx = 0;
    private long reborn$lastStreamRotate = 0L;
    private long reborn$lastBlogRotate = 0L;

    protected GameMenuScreenMixin(Component title) {
        super(title);
    }

    // ── Layout ──
    private int reborn$logoW() { return Math.min(Math.round(this.width * 0.125f), 168); }
    private int reborn$logoH() { return Math.round(reborn$logoW() * (float) LOGO_TEX_H / LOGO_TEX_W); }
    private int reborn$logoY() { return 12; }
    private int reborn$tabsY() { return reborn$logoY() + reborn$logoH() + 8; }
    private int reborn$availTop() { return reborn$tabsY() + TAB_H + 16; }
    private int reborn$availBottom() { return this.height - 26; }

    private int reborn$boxW() { return Math.min(this.width - 100, 700); }
    private int reborn$boxH() { return Math.min(reborn$availBottom() - reborn$availTop(), 400); }
    private int reborn$boxX() { return (this.width - reborn$boxW()) / 2; }
    private int reborn$boxY() {
        int slack = (reborn$availBottom() - reborn$availTop()) - reborn$boxH();
        return reborn$availTop() + Math.max(0, slack / 2);
    }
    private int reborn$contentH() { return reborn$boxH() - BOTTOM_H - GAP; }
    private int reborn$leftW() { return Math.round(reborn$boxW() * 0.46f); }
    private int reborn$rightX() { return reborn$boxX() + reborn$leftW() + GAP; }
    private int reborn$rightW() { return reborn$boxW() - reborn$leftW() - GAP; }
    private int reborn$squareW() { return (reborn$rightW() - GAP) / 2; }
    private int reborn$squareH() { return Math.min(reborn$squareW(), Math.round(reborn$contentH() * 0.42f)); }

    private int reborn$tabW(Font tr, String label) {
        return tr.width(RebornFont.arcade(label)) + 2 * TAB_PAD;
    }

    private int reborn$tabsTotalW(Font tr) {
        int total = 0;
        for (int i = 0; i < TAB_LABELS.length; i++) {
            total += reborn$tabW(tr, TAB_LABELS[i]);
            if (i < TAB_LABELS.length - 1) total += TAB_GAP;
        }
        return total;
    }

    // Le titre « Game Menu » est ajouté dans init() ; on retire au RETURN de init.
    @Inject(method = "init", at = @At("RETURN"))
    private void reborn$rebuildEscMenu(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        Font tr = client.font;

        // Rafraîchit la data live (Discord / patch notes / streams) en fond.
        EscData.refreshIfStale();

        List<GuiEventListener> toRemove = new ArrayList<>(this.children());
        for (GuiEventListener e : toRemove) this.remove(e);

        int x = (this.width - reborn$tabsTotalW(tr)) / 2;
        int tabsY = reborn$tabsY();
        for (int i = 0; i < TAB_LABELS.length; i++) {
            final int idx = i;
            int w = reborn$tabW(tr, TAB_LABELS[i]);
            this.addRenderableWidget(new EscTabButton(
                x, tabsY, w, TAB_H, RebornFont.arcade(TAB_LABELS[i]),
                i == 3, b -> handleTab(client, idx)));
            x += w + TAB_GAP;
        }

        // Boutique — carte cliquable à GAUCHE (contenu dessiné par-dessus).
        this.addRenderableWidget(RebornButton.ghost(
            reborn$boxX(), reborn$boxY(), reborn$leftW(), reborn$contentH(),
            " ", b -> client.setScreen(new ShopScreen(this))));

        LOG.info("esc menu Zenkai : {} widgets vanilla retirés", toRemove.size());
    }

    private void handleTab(Minecraft client, int idx) {
        switch (idx) {
            case 0 -> client.setScreen(null);
            case 1 -> client.setScreen(new ConfigShellScreen(this));
            case 2 -> client.setScreen(new ReportScreen(this));
            case 3 -> client.setScreen(new DisconnectConfirmScreen(this));
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void reborn$renderOverlay(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Font tr = this.font;

        // Fond : UN seul dégradé vertical doux intégré (sombre en haut → légère
        // teinte accent en bas). Pas de rectangles/halos = pas d'effet couches.
        int bottomTint = Colors.lerp(Colors.BACKGROUND, Colors.ACCENT, 0.10f);
        DrawHelpers.verticalGradient(ctx, 0, 0, this.width, this.height, Colors.BACKGROUND, bottomTint);

        // Logo (top-centre).
        int logoW = reborn$logoW();
        int logoH = reborn$logoH();
        int logoX = (this.width - logoW) / 2;
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, LOGO, logoX, reborn$logoY(), logoW, logoH,
            0f, 0f, LOGO_TEX_W, LOGO_TEX_H, LOGO_TEX_W, LOGO_TEX_H);

        // Panneaux (dessinés avant les enfants : Boutique-content + tabs par-dessus).
        int boxY = reborn$boxY();
        int rightX = reborn$rightX();
        int rightW = reborn$rightW();
        int sqW = reborn$squareW();
        int sqH = reborn$squareH();
        reborn$streamSquare(ctx, tr, rightX, boxY, sqW, sqH);
        reborn$blogSquare(ctx, tr, rightX + sqW + GAP, boxY, sqW, sqH);
        int rewY = boxY + sqH + GAP;
        int rewH = reborn$contentH() - sqH - GAP;
        EscPanels.renderRewards(ctx, rightX, rewY, rightW, rewH);

        // Barre communauté (message + Discord) en bas.
        EscPanels.renderCommunityBar(ctx, reborn$boxX(),
            boxY + reborn$contentH() + GAP, reborn$boxW(), BOTTOM_H);

        // Enfants (bouton Boutique = fond+clic, onglets) par-dessus.
        for (GuiEventListener e : this.children()) {
            if (e instanceof AbstractWidget cw && cw.visible) {
                cw.extractRenderState(ctx, mouseX, mouseY, delta);
            }
        }
        // Contenu de la carte Boutique par-dessus son fond.
        reborn$boutiqueContent(ctx, tr, reborn$boxX(), boxY, reborn$leftW(), reborn$contentH());

        ci.cancel();
    }

    // Clics sur les carrousels Stream / Dev Blog (flèches + corps → URL). On
    // OVERRIDE mouseClicked (GameMenuScreen ne le déclare pas, il l'hérite de
    // Screen — un @Inject ne trouverait pas la cible) : si le clic tombe dans un
    // carré carrousel on le consomme, sinon super() gère onglets + Boutique.
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            EscData.Snapshot snap = EscData.get();
            if (snap != null) {
                int boxY = reborn$boxY();
                int rightX = reborn$rightX();
                int sqW = reborn$squareW();
                int sqH = reborn$squareH();
                if (reborn$handleCarousel(mx, my, rightX, boxY, sqW, sqH, snap, true)) return true;
                int blogX = rightX + sqW + GAP;
                if (reborn$handleCarousel(mx, my, blogX, boxY, sqW, sqH, snap, false)) return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private boolean reborn$handleCarousel(double mx, double my, int x, int y, int w, int h,
                                          EscData.Snapshot snap, boolean stream) {
        if (mx < x || mx >= x + w || my < y || my >= y + h) return false;
        int size = stream ? snap.streams().size() : snap.patchNotes().size();
        if (size <= 0) return false;
        long now = System.currentTimeMillis();

        boolean leftZone = mx < x + ARROW_W + 6;
        boolean rightZone = mx >= x + w - ARROW_W - 6;
        if (size > 1 && (leftZone || rightZone)) {
            int delta = rightZone ? 1 : -1;
            if (stream) {
                reborn$streamIdx = Math.floorMod(reborn$streamIdx + delta, size);
                reborn$lastStreamRotate = now;
            } else {
                reborn$blogIdx = Math.floorMod(reborn$blogIdx + delta, size);
                reborn$lastBlogRotate = now;
            }
            reborn$click();
            return true;
        }

        // Corps du carré → ouvre l'URL (chaîne Twitch ou patch note web).
        String url = stream
            ? snap.streams().get(Math.floorMod(reborn$streamIdx, size)).url()
            : snap.patchNotes().get(Math.floorMod(reborn$blogIdx, size)).url();
        if (url != null && !url.isBlank()) {
            reborn$openUrl(url);
            reborn$click();
            return true;
        }
        return false;
    }

    private void reborn$openUrl(String url) {
        try {
            Util.getPlatform().openUri(URI.create(url));
        } catch (Exception e) {
            LOG.warn("ouverture URL échouée ({}) : {}", url, e.toString());
        }
    }

    private void reborn$click() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.getSoundManager().play(
                SimpleSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    /** Carré « Stream » — carrousel des streamers (flèches ‹ ›, auto-rotation,
     *  badge LIVE/OFFLINE par chaîne, clic → ouvre la chaîne Twitch). */
    private void reborn$streamSquare(GuiGraphicsExtractor ctx, Font tr, int x, int y, int w, int h) {
        EscData.Snapshot snap = EscData.get();
        List<EscData.Stream> streams = snap != null ? snap.streams() : List.of();
        DrawHelpers.roundedOutlinedRect(ctx, x, y, w, h, RADIUS, Colors.SURFACE, Colors.BORDER_STRONG);
        IconPack.twitch(ctx, x + 14, y + 11, 14, Colors.FOREGROUND_SUBTLE);
        ctx.text(tr, RebornFont.arcade("STREAM"), x + 34, y + 13, Colors.FOREGROUND_SUBTLE, false);

        if (streams.isEmpty()) {
            Component none = RebornFont.arcade("AUCUN STREAM");
            ctx.text(tr, none, x + (w - tr.width(none)) / 2, y + h / 2, Colors.FOREGROUND_MUTED, false);
            return;
        }

        long now = System.currentTimeMillis();
        if (streams.size() > 1 && now - reborn$lastStreamRotate > ROTATE_MS) {
            reborn$streamIdx = (reborn$streamIdx + 1) % streams.size();
            reborn$lastStreamRotate = now;
        }
        int idx = Math.floorMod(reborn$streamIdx, streams.size());
        EscData.Stream s = streams.get(idx);

        // Badge LIVE / OFFLINE de la chaîne courante.
        Component badge = RebornFont.arcade(s.live() ? "LIVE" : "OFFLINE");
        int bw = tr.width(badge) + 10;
        int badgeBg = s.live() ? Colors.withAlpha(Colors.SUCCESS, 0.18f) : Colors.DANGER_SOFT;
        int badgeFg = s.live() ? Colors.SUCCESS : Colors.DANGER;
        DrawHelpers.roundedRect(ctx, x + w - bw - 12, y + 11, bw, 14, 6, badgeBg);
        ctx.text(tr, badge, x + w - bw - 7, y + 14, badgeFg, false);

        // Nom de la chaîne (cliquable) + titre si en live.
        String name = reborn$fit(tr, s.name() != null ? s.name() : "STREAM", w - 2 * (ARROW_W + 6));
        Component nameT = RebornFont.arcade(name);
        int nameY = s.live() && s.title() != null ? y + h / 2 - 7 : y + h / 2 - 2;
        ctx.text(tr, nameT, x + (w - tr.width(nameT)) / 2, nameY,
            s.live() ? Colors.WHITE_PURE : Colors.FOREGROUND_SUBTLE, false);
        if (s.live() && s.title() != null && !s.title().isEmpty()) {
            String title = reborn$fit(tr, s.title(), w - 2 * (ARROW_W + 6));
            Component tt = RebornFont.arcade(title);
            ctx.text(tr, tt, x + (w - tr.width(tt)) / 2, nameY + 12, Colors.FOREGROUND_MUTED, false);
        }

        reborn$carouselChrome(ctx, tr, x, y, w, h, streams.size(), idx);
    }

    /** Carré « Dev Blog » — carrousel des patch notes (flèches ‹ ›, auto-rotation,
     *  clic → ouvre la patch note sur le web). */
    private void reborn$blogSquare(GuiGraphicsExtractor ctx, Font tr, int x, int y, int w, int h) {
        EscData.Snapshot snap = EscData.get();
        List<EscData.PatchNote> notes = snap != null ? snap.patchNotes() : List.of();
        DrawHelpers.roundedOutlinedRect(ctx, x, y, w, h, RADIUS, Colors.SURFACE, Colors.BORDER_STRONG);
        ctx.text(tr, RebornFont.arcade("DEV BLOG"), x + 14, y + 13, Colors.FOREGROUND_SUBTLE, false);

        int thumbY = y + 30;
        int thumbH = h - 30 - 30;
        DrawHelpers.roundedRect(ctx, x + 12, thumbY, w - 24, thumbH, 6, Colors.ACCENT_SOFT);

        if (notes.isEmpty()) {
            Component ph = RebornFont.arcade("12 MAI - PATCH 1.0.5");
            ctx.text(tr, reborn$fit(tr, "12 MAI - PATCH 1.0.5", w - 24),
                x + 12, y + h - 14, Colors.FOREGROUND_MUTED, false);
            return;
        }

        long now = System.currentTimeMillis();
        if (notes.size() > 1 && now - reborn$lastBlogRotate > ROTATE_MS) {
            reborn$blogIdx = (reborn$blogIdx + 1) % notes.size();
            reborn$lastBlogRotate = now;
        }
        int idx = Math.floorMod(reborn$blogIdx, notes.size());
        EscData.PatchNote n = notes.get(idx);

        // Date en haut à droite.
        if (n.date() != null) {
            Component dt = RebornFont.arcade(n.date());
            ctx.text(tr, dt, x + w - 12 - tr.width(dt), y + 13, Colors.FOREGROUND_MUTED, false);
        }

        // Version - titre (cliquable) au-dessus du bas.
        String line = n.version() != null ? n.version() : "";
        if (n.title() != null && !n.title().isEmpty()) {
            line = line.isEmpty() ? n.title() : line + " - " + n.title();
        }
        line = reborn$fit(tr, line, w - 2 * (ARROW_W + 6));
        Component lt = RebornFont.arcade(line);
        ctx.text(tr, lt, x + (w - tr.width(lt)) / 2, y + h - 26, Colors.WHITE_PURE, false);

        reborn$carouselChrome(ctx, tr, x, y, w, h, notes.size(), idx);
    }

    /** Flèches ‹ › + indicateur i/N, communs aux deux carrousels. */
    private void reborn$carouselChrome(GuiGraphicsExtractor ctx, Font tr, int x, int y, int w, int h,
                                       int size, int idx) {
        if (size > 1) {
            Component l = RebornFont.arcade("<");
            Component r = RebornFont.arcade(">");
            int midY = y + h / 2 - 4;
            ctx.text(tr, l, x + 5, midY, Colors.FOREGROUND_SUBTLE, false);
            ctx.text(tr, r, x + w - 5 - tr.width(r), midY, Colors.FOREGROUND_SUBTLE, false);
            Component ind = RebornFont.arcade((idx + 1) + "/" + size);
            ctx.text(tr, ind, x + (w - tr.width(ind)) / 2, y + h - 12, Colors.FOREGROUND_MUTED, false);
        }
    }

    /** Tronque une chaîne ArcadePix pour qu'elle tienne dans {@code maxW} px. */
    private String reborn$fit(Font tr, String s, int maxW) {
        if (tr.width(RebornFont.arcade(s)) <= maxW) return s;
        while (s.length() > 1 && tr.width(RebornFont.arcade(s + "..")) > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s.trim() + "..";
    }

    /** Contenu de la carte Boutique (header + gros libellé centré). ArcadePix. */
    private void reborn$boutiqueContent(GuiGraphicsExtractor ctx, Font tr, int x, int y, int w, int h) {
        ctx.text(tr, RebornFont.arcade("BOUTIQUE"), x + 14, y + 13, Colors.FOREGROUND_SUBTLE, false);
        Component big = RebornFont.arcade("BOUTIQUE");
        float sc = 2.0f;
        int bw = Math.round(tr.width(big) * sc);
        ctx.pose().pushMatrix();
        ctx.pose().translate(x + (w - bw) / 2f, y + h / 2f - 12);
        ctx.pose().scale(sc, sc);
        ctx.text(tr, big, 0, 0, Colors.WHITE_PURE, false);
        ctx.pose().popMatrix();
        Component hint = RebornFont.arcade("BIENTOT DISPONIBLE");
        ctx.text(tr, hint, x + (w - tr.width(hint)) / 2, y + h / 2 + 14, Colors.FOREGROUND_MUTED, false);
    }
}
