package fr.reborn.hud.chat;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.menu.settings.ReglagesArt;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Réglages du chat, au niveau de la fiche shinobi (DA §4.1, §6) : une fenêtre de laque tenue entre deux poutres de
 * bois comme le panneau du chat lui-même (vagues seigaiha d'or dans le coin), deux colonnes (Affichage ; Mentions et
 * Dimensions), contrôles laqués des Paramètres ({@link ReglagesArt} : interrupteurs, réglettes d'or, plaques
 * segmentées), texte {@link DaKit}. Décor fixe : {@code textures/gui/chat/reglages.png}
 * ({@code tools/gen_chat_textures.py}), un blit. Le jeu reste visible derrière, sous un voile.
 *
 * <p>Ouverte depuis le chat (roue), l'éditeur HUD ou Paramètres › Interface. Chaque réglage est enregistré
 * immédiatement (pas de bouton Appliquer).
 */
public final class ChatSettingsScreen extends Screen {

    private static final Identifier ART = DaKit.tex("chat", "reglages");
    private static final int TW = 548, TH = 280;
    /** Fenêtre 520×272 (texture : contour au texel 0, panneau en (1, 1)). */
    private static final int W = 520, H = 272, BEAM = 26;
    private static final int COL_W = 232, ROW = 24;

    private static final int[] HL_COLORS = {
        0xFFA0182B, 0xFFEF4444, 0xFFD9A95E, 0xFF4ADE80, 0xFF38BDF8, 0xFF8B5CF6
    };
    private static final String[] STYLES = {"Grossir", "Fondu", "Glisser"};

    private final Screen parent;
    private final ChatSettings settings;

    /** Lignes de réglage (mise en page calculée à l'init, rejouée telle quelle à chaque image). */
    private sealed interface Ligne permits Bascule, Choix, Nuancier, Reglette {}

    private record Bascule(String label, int x, int ym, Supplier<Boolean> get, Consumer<Boolean> set) implements Ligne {}

    private record Choix(String label, int x, int ym, IntSupplier get, IntConsumer set) implements Ligne {}

    private record Nuancier(String label, int x, int ym) implements Ligne {}

    private record Reglette(String label, int x, int ym, int min, int max, String unite, IntSupplier get,
                            IntConsumer set) implements Ligne {}

    private record Titre(String texte, int x, int y) {}

    private final List<Ligne> lignes = new ArrayList<>();
    private final List<Titre> titres = new ArrayList<>();
    private int px, py;
    private Reglette glisse;
    private boolean modifie;

    public ChatSettingsScreen(Screen parent) {
        super(Component.literal("Réglages du chat"));
        this.parent = parent;
        this.settings = RebornHudClient.config().getChatSettings();
    }

    // ═════════════════════════════════════════════════════════════ mise en page

    @Override
    protected void init() {
        px = (this.width - W) / 2;
        py = Math.max(0, (this.height - H) / 2);
        lignes.clear();
        titres.clear();
        ChatSettings s = settings;

        int xl = px + 14, xr = px + W - 14 - COL_W;
        // ── gauche : affichage ──
        titres.add(new Titre("Affichage", xl, py + 34));
        int y = py + 50;
        lignes.add(new Bascule("Heure des messages", xl, y + 11, () -> s.showTimestamps, b -> s.showTimestamps = b));
        y += ROW;
        lignes.add(new Bascule("Têtes des joueurs", xl, y + 11, () -> s.chatHeads, b -> s.chatHeads = b));
        y += ROW;
        lignes.add(new Bascule("Badges de rang", xl, y + 11, () -> s.chatBadges, b -> s.chatBadges = b));
        y += ROW;
        lignes.add(new Bascule("Animation d'arrivée", xl, y + 11, () -> s.chatAnimation, b -> s.chatAnimation = b));
        y += ROW;
        lignes.add(new Bascule("Machine à écrire", xl, y + 11, () -> s.chatTyping, b -> s.chatTyping = b));
        y += ROW;
        lignes.add(new Bascule("Texte animé à la saisie", xl, y + 11, () -> s.animatedTyping, b -> s.animatedTyping = b));
        y += ROW;
        lignes.add(new Choix("Style d'animation", xl, y + 11, () -> s.typingCursorStyle, v -> s.typingCursorStyle = v));

        // ── droite : mentions, dimensions ──
        titres.add(new Titre("Mentions", xr, py + 34));
        y = py + 50;
        lignes.add(new Bascule("Surligner les mentions", xr, y + 11, () -> s.highlightMentions, b -> s.highlightMentions = b));
        y += ROW;
        lignes.add(new Bascule("Son sur mention", xr, y + 11, () -> s.soundOnMention, b -> s.soundOnMention = b));
        y += ROW;
        lignes.add(new Nuancier("Couleur des mentions", xr, y + 11));
        y += ROW + 14;
        titres.add(new Titre("Dimensions", xr, y));
        y += 16;
        lignes.add(new Reglette("Opacité", xr, y + 11, 30, 100, " %", () -> s.opacity, v -> s.opacity = v));
        y += ROW;
        lignes.add(new Reglette("Taille du texte", xr, y + 11, 10, 16, " px", () -> s.textSize, v -> s.textSize = v));
    }

    // ═════════════════════════════════════════════════════════════ rendu

    /** Le jeu reste visible derrière, sous un voile et une vignette ; hors partie, un fond de nuit opaque. */
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (Minecraft.getInstance().level == null) {
            ctx.fill(0, 0, this.width, this.height, 0xFF0A0608);
        } else {
            ctx.fill(0, 0, this.width, this.height, DaKit.argb(0x08040A, 0.45f));
        }
        DaKit.etire(ctx, ReglagesArt.VIGNETTE, 0, 0, this.width, this.height, 160, 90, DaKit.blanc(1f));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Font f = this.font;
        DaKit.blit(ctx, ART, px - 1, py - 1, 0, 0, W + 4, H + 4, TW, TH);
        DaKit.arcX1(ctx, f, DaKit.arcade("Réglages du chat"), px + W / 2f, py + 8, DaKit.OR_CLAIR, 1, true);

        for (Titre t : titres) {
            int w = DaKit.arcX1(ctx, f, DaKit.arcade(t.texte), t.x, t.y, DaKit.OR_CLAIR, 0, true);
            ReglagesArt.filet(ctx, t.x + w + 8, t.x + COL_W, t.y + 3);
        }

        // filets sombres entre les lignes (un seul élément GUI)
        fr.reborn.hud.ui.LotGui sep = fr.reborn.hud.ui.LotGui.ouvrir(ctx);
        for (int i = 0; i < lignes.size(); i++) {
            Ligne l = lignes.get(i);
            Ligne n = i + 1 < lignes.size() ? lignes.get(i + 1) : null;
            if (n != null && x(n) == x(l) && ym(n) - ym(l) == ROW) {
                sep.fill(x(l), ym(l) + 12, x(l) + COL_W, ym(l) + 13, DaKit.SEP);
            }
        }
        sep.fermer();

        for (Ligne l : lignes) {
            DaKit.inter9(ctx, f, DaKit.inter(label(l)), x(l), ym(l), DaKit.CREME, 0);
            switch (l) {
                case Bascule b -> {
                    int bx = b.x + COL_W - 40, by = b.ym - 7;
                    ReglagesArt.interrupteur(ctx, bx, by, b.get.get(), DaKit.dans(mouseX, mouseY, bx - 2, by - 4, 44, 22));
                }
                case Choix c -> {
                    int cur = Math.max(0, Math.min(STYLES.length - 1, c.get.getAsInt()));
                    for (int i = 0; i < STYLES.length; i++) {
                        int sx = segX(c, i), sy = c.ym - 8;
                        boolean actif = i == cur, survol = DaKit.dans(mouseX, mouseY, sx, sy, SEG_W, 16);
                        ReglagesArt.segment(ctx, sx, sy, SEG_W - 2, actif ? 2 : survol ? 1 : 0);
                        int col = actif ? DaKit.CREME : survol ? DaKit.CREME_VAL : DaKit.NEUTRE_TXT;
                        DaKit.inter8(ctx, f, DaKit.inter(STYLES[i]), sx + (SEG_W - 2) / 2f, c.ym, col, 1);
                    }
                }
                case Nuancier n -> {
                    for (int i = 0; i < HL_COLORS.length; i++) {
                        int sx = pastilleX(n, i), sy = n.ym - 7;
                        boolean choisie = (HL_COLORS[i] & 0xFFFFFF) == (settings.highlightColor & 0xFFFFFF);
                        boolean survol = DaKit.dans(mouseX, mouseY, sx, sy, 15, 15);
                        DaKit.blit(ctx, ART, sx, sy, 528, choisie ? 32 : survol ? 16 : 0, 15, 15, TW, TH);
                        ctx.fill(sx + 2, sy + 2, sx + 13, sy + 13, HL_COLORS[i]);
                    }
                }
                case Reglette r -> {
                    int v = r.get.getAsInt();
                    float t = (v - r.min) / (float) Math.max(1, r.max - r.min);
                    boolean chaud = glisse == r || DaKit.dans(mouseX, mouseY, regX(r) - 2, r.ym - 8, REG_W + 4, 16);
                    ReglagesArt.reglette(ctx, regX(r), r.ym - 5, REG_W, t, chaud);
                    DaKit.inter9(ctx, f, DaKit.inter(v + r.unite), r.x + COL_W, r.ym, DaKit.OR_CLAIR, 2);
                }
            }
        }

        // poutre basse : mention d'enregistrement, bouton Fermer
        DaKit.inter7(ctx, f, DaKit.inter("Enregistré à chaque changement"), px + 14, py + H - 12,
            DaKit.argb(0xC2B59A, 0.75f), 0);
        int bx = fermerX(), by = fermerY();
        boolean hov = DaKit.dans(mouseX, mouseY, bx, by, FERMER_W, 16);
        DaKit.coupeH(ctx, ChatPanel.PIECES, bx, by, FERMER_W + 1, 0, 32 + (2 + (hov ? 1 : 0)) * 18, 120, 17, 4, 150, 120);
        DaKit.inter9(ctx, f, DaKit.inter("Fermer"), bx + FERMER_W / 2f, by + 8, hov ? DaKit.CREME : DaKit.CREME_VAL, 1);
    }

    private static final int SEG_W = 42, REG_W = 110, FERMER_W = 96;

    private static int segX(Choix c, int i) { return c.x + COL_W - SEG_W * (STYLES.length - i); }

    private static int pastilleX(Nuancier n, int i) { return n.x + COL_W - 15 - (HL_COLORS.length - 1 - i) * 18; }

    private static int regX(Reglette r) { return r.x + COL_W - 36 - REG_W; }

    private int fermerX() { return px + W / 2 - FERMER_W / 2; }

    private int fermerY() { return py + H - BEAM + 5; }

    private static int x(Ligne l) {
        return switch (l) {
            case Bascule b -> b.x;
            case Choix c -> c.x;
            case Nuancier n -> n.x;
            case Reglette r -> r.x;
        };
    }

    private static int ym(Ligne l) {
        return switch (l) {
            case Bascule b -> b.ym;
            case Choix c -> c.ym;
            case Nuancier n -> n.ym;
            case Reglette r -> r.ym;
        };
    }

    private static String label(Ligne l) {
        return switch (l) {
            case Bascule b -> b.label;
            case Choix c -> c.label;
            case Nuancier n -> n.label;
            case Reglette r -> r.label;
        };
    }

    // ═════════════════════════════════════════════════════════════ entrées

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        for (Ligne l : lignes) {
            switch (l) {
                case Bascule b -> {
                    // toute la ligne bascule (libellé compris)
                    if (DaKit.dans(mx, my, b.x, b.ym - 11, COL_W, ROW - 1)) {
                        b.set.accept(!b.get.get());
                        enregistrer();
                        return true;
                    }
                }
                case Choix c -> {
                    for (int i = 0; i < STYLES.length; i++) {
                        if (DaKit.dans(mx, my, segX(c, i), c.ym - 8, SEG_W, 16)) {
                            c.set.accept(i);
                            enregistrer();
                            return true;
                        }
                    }
                }
                case Nuancier n -> {
                    for (int i = 0; i < HL_COLORS.length; i++) {
                        if (DaKit.dans(mx, my, pastilleX(n, i) - 1, n.ym - 8, 17, 17)) {
                            settings.highlightColor = HL_COLORS[i];
                            enregistrer();
                            return true;
                        }
                    }
                }
                case Reglette r -> {
                    if (DaKit.dans(mx, my, regX(r) - 4, r.ym - 8, REG_W + 8, 16)) {
                        glisse = r;
                        regler(r, mx);
                        return true;
                    }
                }
            }
        }
        if (DaKit.dans(mx, my, fermerX(), fermerY(), FERMER_W, 16)) { onClose(); return true; }
        // clic hors de la fenêtre → ferme
        if (!DaKit.dans(mx, my, px, py, W, H)) { onClose(); return true; }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (glisse != null) {
            regler(glisse, event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (glisse != null) {
            glisse = null;
            if (modifie) enregistrer();
            return true;
        }
        return super.mouseReleased(event);
    }

    private void regler(Reglette r, double mx) {
        float t = (float) (mx - regX(r) - 2) / Math.max(1, REG_W - 4);
        int v = r.min + Math.round(Math.max(0f, Math.min(1f, t)) * (r.max - r.min));
        if (v != r.get.getAsInt()) {
            r.set.accept(v);
            modifie = true;
        }
    }

    private void enregistrer() {
        modifie = false;
        RebornHudClient.config().save();
    }

    @Override
    public void onClose() {
        RebornHudClient.config().save();
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
