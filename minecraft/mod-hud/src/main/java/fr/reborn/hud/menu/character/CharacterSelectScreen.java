package fr.reborn.hud.menu.character;

import fr.reborn.hud.menu.RebornFont;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Écran de sélection de personnage (affiché au join) — DA Reborn, planche « Horizon ».
 *
 * <p>Sobre : dégradé bleu nuit → noir, une ligne d'horizon, et un <b>disque vermillon</b>
 * derrière le perso focalisé. <b>Tous les persos sont visibles en pied</b>, en pose idle,
 * chacun avec son skin RP : le focalisé est au centre, grand et en couleur ; les autres
 * glissent de part et d'autre (carrousel), plus petits et éteints. Un perso mort (RPK) est
 * encore plus effacé. Dessous : nom, ligne « clan / village / rang / niveau », ENTRER.
 *
 * <p>Chaque perso est dessiné avec le <b>joueur local</b> (pose idle de l'écran) en lui
 * <b>prêtant</b> le skin composé du perso juste le temps de son extraction : le skin de
 * chaque perso est composé une fois sous un UUID dérivé de son id, puis l'override du
 * joueur local pointe dessus pendant l'appel de rendu et est restauré ensuite. Aucune
 * entité factice (une entité hors monde figeait le chargement au join). Un perso sans
 * apparence est rendu avec le skin Minecraft normal.
 *
 * <p>Tout est disposé dans un espace virtuel 640×360 mis à l'échelle et centré.
 */
public class CharacterSelectScreen extends Screen {

    // Palette.
    private static final int SKY_TOP = 0xFF161A30, SKY_BOT = 0xFF06060A, GROUND = 0xFF08080C,
        HORIZON = 0xFF786E82, DISC = 0xC8961E22, DISC_REFL = 0xFF46141A, GOLD = 0xFFF6CC78,
        GOLD_D = 0xFFAA8034, CREAM = 0xFFFAEED6, SUB = 0xFFAAAABE, MUTED = 0xFF8282A0,
        RED = 0xFFAA1E22, RED_HOV = 0xFFC82A2E, DEAD = 0xFFA05048, KEYS = 0xFF6E6E82;

    // Géométrie virtuelle (640×360).
    private static final float VW = 640, VH = 360, HZ = 250, CX = 320;
    private static final float SPACING = 112, SIZE_ON = 92, SIZE_OFF = 68, DISC_R = 70;

    private int focused = 0;
    private float slide = 0;            // position animée du carrousel (→ focused)
    private long lastFrame = 0;

    private boolean prevHudHidden;
    private boolean perspectiveCaptured = false;

    /** UUID « porte-skin » par perso dont la texture composée est déjà prête. */
    private final Set<UUID> composed = new HashSet<>();

    // Échelle / origine (recalculées à chaque image).
    private float s = 1, ox = 0, oy = 0;

    public CharacterSelectScreen() {
        super(Component.literal("Sélection du personnage"));
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (!perspectiveCaptured && mc.options != null) {
            prevHudHidden = mc.gui.hud.isHidden();
            ((fr.reborn.hud.mixin.HudAccessor)(Object) mc.gui.hud).reborn$setHidden(true);
            perspectiveCaptured = true;
        }
        fr.reborn.hud.animation.MovementAnimations.INSTANCE.startPose();
        slide = focused;
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (perspectiveCaptured && mc.options != null) {
            ((fr.reborn.hud.mixin.HudAccessor)(Object) mc.gui.hud).reborn$setHidden(prevHudHidden);
            perspectiveCaptured = false;
        }
        fr.reborn.hud.animation.MovementAnimations.INSTANCE.stopPose();
        for (UUID u : composed) fr.reborn.hud.skin.RebornSkins.clear(u);
        composed.clear();
        super.removed();
    }

    // ── Données / indices ─────────────────────────────────────────
    private List<CharacterCard> cards() { return CharacterData.characters(); }
    private boolean canCreate() { return cards().size() < CharacterData.slotLimit(); }
    private int tileCount() { return cards().size() + (canCreate() ? 1 : 0); }
    private boolean isCreateTile(int i) { return canCreate() && i == cards().size(); }

    private void moveFocus(int delta) {
        int n = Math.max(1, tileCount());
        int next = Math.max(0, Math.min(n - 1, focused + delta));   // carrousel borné : pas de saut de bout en bout
        if (next == focused) return;
        focused = next;
        fr.reborn.hud.menu.RebornSounds.charNav();
    }

    private void focusTo(int i) {
        if (i == focused || i < 0 || i >= tileCount()) return;
        focused = i;
        fr.reborn.hud.menu.RebornSounds.charNav();
    }

    /** UUID porteur de la texture composée de ce perso (composée une seule fois), ou null. */
    private UUID skinHolder(CharacterCard c) {
        if (!c.hasAppearance()) return null;
        UUID uuid = UUID.nameUUIDFromBytes(("reborn-select:" + c.id()).getBytes(StandardCharsets.UTF_8));
        if (composed.add(uuid)) {
            fr.reborn.hud.skin.RebornSkins.applySpec(uuid, fr.reborn.hud.skin.SkinSpec.deserialize(c.appearance()));
        }
        return uuid;
    }

    // ── Géométrie ─────────────────────────────────────────────────
    private void layout() {
        s = Math.min(this.width / VW, this.height / VH);
        ox = (this.width - VW * s) / 2f;
        oy = (this.height - VH * s) / 2f;
    }

    private int px(float vx) { return Math.round(ox + vx * s); }
    private int py(float vy) { return Math.round(oy + vy * s); }

    /** Proximité au focus (1 = focalisé, 0 = voisin ou plus loin) selon la position animée. */
    private float nearness(int i) { return Math.max(0f, 1f - Math.abs(i - slide)); }
    private float tileX(int i) { return CX + (i - slide) * SPACING; }
    private float tileSize(int i) { return SIZE_OFF + (SIZE_ON - SIZE_OFF) * nearness(i); }

    // Bouton ENTRER (virtuel).
    private static final float BTN_W = 80, BTN_H = 14, BTN_Y = 316;

    // ── Rendu ─────────────────────────────────────────────────────
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        layout();
        // Ciel plein écran (opaque : le monde n'est jamais visible ici), sol sous l'horizon.
        ctx.fillGradient(0, 0, this.width, this.height, SKY_TOP, SKY_BOT);
        int hz = py(HZ);
        // Disque vermillon derrière le perso focalisé (fixe au centre : c'est le carrousel qui glisse).
        fillDisc(ctx, px(CX), py(HZ - 120), Math.round(DISC_R * s), DISC);
        ctx.fill(0, hz, this.width, this.height, GROUND);
        ctx.fill(0, hz, this.width, hz + Math.max(1, Math.round(s * 0.6f)), HORIZON);
        // reflet du disque sur le sol
        int rw = Math.round(50 * s), rh = Math.max(2, Math.round(3 * s));
        ctx.fill(px(CX) - rw, hz + Math.round(5 * s), px(CX) + rw, hz + Math.round(5 * s) + rh, DISC_REFL);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        layout();
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        slide += (focused - slide) * Math.min(1f, dt * 12f);
        if (Math.abs(focused - slide) < 0.002f) slide = focused;

        super.extractRenderState(ctx, mouseX, mouseY, delta);
        Font f = this.font;
        List<CharacterCard> list = cards();
        int n = tileCount();
        int hz = py(HZ);

        // 1) Avatars (du plus loin au plus proche du focus, pour que le focalisé passe devant).
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Float.compare(nearness(a), nearness(b)));
        Minecraft mc = Minecraft.getInstance();
        UUID self = mc.player != null ? mc.player.getUUID() : null;
        Identifier ownSkin = self != null ? fr.reborn.hud.skin.RebornSkins.overrideFor(self) : null;
        boolean ownSlim = self != null && fr.reborn.hud.skin.RebornSkins.isSlim(self);
        try {
        for (int i : order) {
            float x = tileX(i);
            if (x < -40 || x > VW + 40 || isCreateTile(i)) continue;
            CharacterCard c = list.get(i);
            if (self == null) break;
            UUID holder = skinHolder(c);
            fr.reborn.hud.skin.RebornSkins.setOverride(self,
                holder != null ? fr.reborn.hud.skin.RebornSkins.overrideFor(holder) : null,
                holder != null && fr.reborn.hud.skin.RebornSkins.isSlim(holder));
            int size = Math.round(tileSize(i) * s);
            int cx = px(x);
            int bottom = hz + Math.round(6 * s);
            int top = bottom - Math.round(size * 1.8f) - Math.round(12 * s);
            int cy = (top + bottom) / 2;
            int half = Math.round(size * 0.5f);
            net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(
                ctx, cx - half, top, cx + half, bottom, size, 0f, cx, cy, mc.player);
        }
        } finally {
            // Rend au joueur local son vrai skin (le skin est capturé à l'extraction).
            if (self != null) fr.reborn.hud.skin.RebornSkins.setOverride(self, ownSkin, ownSlim);
        }

        // 2) Voile qui éteint les persos non focalisés (même dégradé que le ciel → « fondu » dans le fond).
        ctx.nextStratum();
        for (int i = 0; i < n; i++) {
            float x = tileX(i);
            if (x < -40 || x > VW + 40 || isCreateTile(i)) continue;
            CharacterCard c = list.get(i);
            float dim = (1f - nearness(i)) * 0.62f;
            if (c.dead()) dim = Math.max(dim, 0.72f);
            if (dim <= 0.01f) continue;
            int size = Math.round(tileSize(i) * s);
            int cx = px(x), half = Math.round(size * 0.5f) + 2;
            int bottom = hz, top = bottom - Math.round(size * 1.8f) - Math.round(12 * s);
            int a = Math.round(dim * 255) << 24;
            ctx.fillGradient(cx - half, top, cx + half, bottom,
                a | (skyAt(top) & 0xFFFFFF), a | (skyAt(bottom) & 0xFFFFFF));
        }
        ctx.fill(0, hz, this.width, hz + Math.max(1, Math.round(s * 0.6f)), HORIZON);

        // 3) Titre.
        text(ctx, f, "PERSONNAGES", CX, 16, 1.5f, CREAM, true);
        int ty = py(28);
        ctx.fill(px(CX - 60), ty, px(CX - 10), ty + 1, GOLD_D);
        ctx.fill(px(CX + 10), ty, px(CX + 60), ty + 1, GOLD_D);
        ctx.fill(px(CX) - 2, ty - 2, px(CX) + 2, ty + 2, GOLD);

        // 4) Étiquettes sous les persos non focalisés + tuile « nouveau ».
        for (int i = 0; i < n; i++) {
            float x = tileX(i);
            if (x < -40 || x > VW + 40) continue;
            float near = nearness(i);
            if (isCreateTile(i)) {
                int col = blend(MUTED, CREAM, near);
                text(ctx, f, "+", x, HZ - 56, 2f, col, true);
                if (near < 0.5f) text(ctx, f, "NOUVEAU", x, HZ + 9, 1f, col, true);
                continue;
            }
            if (near >= 0.5f) continue;
            CharacterCard c = list.get(i);
            text(ctx, f, c.firstName(), x, HZ + 9, 1f, c.dead() ? DEAD : blend(MUTED, CREAM, near * 2), true);
            if (c.dead()) text(ctx, f, "RPK", x, HZ + 18, 1f, DEAD, true);
        }

        // 5) Bloc d'info du focalisé.
        if (n > 0) {
            if (isCreateTile(focused)) {
                text(ctx, f, "NOUVEAU PERSONNAGE", CX, 280, 1.5f, CREAM, true);
                text(ctx, f, "CREE TON SHINOBI", CX, 296, 1f, SUB, true);
                drawButton(ctx, f, "CREER", true, mouseX, mouseY);
            } else {
                CharacterCard c = list.get(Math.min(focused, list.size() - 1));
                text(ctx, f, c.firstName(), CX, 280, 1.5f, c.dead() ? DEAD : CREAM, true);
                StringBuilder sb = new StringBuilder();
                if (c.hasClan()) sb.append(c.clan());
                if (c.hasVillage()) sb.append(sb.length() > 0 ? "  /  " : "").append(c.village());
                if (c.rank() != null && !c.rank().isBlank()) sb.append(sb.length() > 0 ? "  /  " : "").append(c.rank());
                sb.append(sb.length() > 0 ? "  /  " : "").append("NIV ").append(c.level());
                text(ctx, f, sb.toString(), CX, 296, 1f, SUB, true);
                if (c.dead()) drawButton(ctx, f, "RPK", false, mouseX, mouseY);
                else drawButton(ctx, f, "ENTRER", true, mouseX, mouseY);
            }
        }

        text(ctx, f, (n > 1 ? "<  >  CHANGER        " : "") + "ENTREE  JOUER", CX, 348, 1f, KEYS, true);
    }

    private void drawButton(GuiGraphicsExtractor ctx, Font f, String label, boolean enabled, int mx, int my) {
        int x0 = px(CX - BTN_W / 2), y0 = py(BTN_Y), x1 = px(CX + BTN_W / 2), y1 = py(BTN_Y + BTN_H);
        boolean hov = enabled && mx >= x0 && mx < x1 && my >= y0 && my < y1;
        ctx.fill(x0, y0, x1, y1, enabled ? (hov ? RED_HOV : RED) : 0xFF3C1416);
        int b = enabled ? GOLD : 0xFF8C3C36;
        ctx.fill(x0, y0, x1, y0 + 1, b); ctx.fill(x0, y1 - 1, x1, y1, b);
        ctx.fill(x0, y0, x0 + 1, y1, b); ctx.fill(x1 - 1, y0, x1, y1, b);
        text(ctx, f, label, CX, BTN_Y + 4, 1f, enabled ? CREAM : DEAD, true);
    }

    /** Texte ArcadePix (majuscules sans accents) à une position virtuelle, mis à l'échelle. */
    private void text(GuiGraphicsExtractor ctx, Font f, String str, float vx, float vy, float mul, int col, boolean centered) {
        Component c = RebornFont.arcade(ax(str));
        float sc = s * mul;
        float x = ox + vx * s - (centered ? f.width(c) * sc / 2f : 0);
        ctx.pose().pushMatrix();
        ctx.pose().translate(Math.round(x), Math.round(oy + vy * s));
        ctx.pose().scale(sc, sc);
        ctx.text(f, c, 0, 0, col, false);
        ctx.pose().popMatrix();
    }

    private static String ax(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT);
    }

    /** Couleur du ciel à l'ordonnée écran y (pour un voile qui se fond dans le fond). */
    private int skyAt(int y) {
        float t = this.height <= 0 ? 0 : Math.max(0, Math.min(1, y / (float) this.height));
        return blend(SKY_TOP, SKY_BOT, t);
    }

    private static int blend(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static void fillDisc(GuiGraphicsExtractor ctx, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.round(Math.sqrt((double) r * r - dy * dy));
            ctx.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    // ── Hit-tests ─────────────────────────────────────────────────
    private boolean overButton(int mx, int my) {
        return mx >= px(CX - BTN_W / 2) && mx < px(CX + BTN_W / 2) && my >= py(BTN_Y) && my < py(BTN_Y + BTN_H);
    }

    /** Tuile sous la souris (zone du perso + son étiquette), ou -1. */
    private int tileAt(int mx, int my) {
        int hz = py(HZ);
        for (int i = 0; i < tileCount(); i++) {
            float x = tileX(i);
            int size = Math.round(tileSize(i) * s);
            int half = Math.max(Math.round(size * 0.5f), Math.round(30 * s));
            int top = hz - Math.round(size * 1.8f) - Math.round(12 * s);
            if (mx >= px(x) - half && mx < px(x) + half && my >= top && my < hz + Math.round(24 * s)) return i;
        }
        return -1;
    }

    // ── Interactions ──────────────────────────────────────────────
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (v != 0) { moveFocus(v > 0 ? -1 : 1); return true; }
        return super.mouseScrolled(mouseX, mouseY, h, v);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        switch (event.key()) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_Q -> { moveFocus(-1); return true; }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> { moveFocus(1); return true; }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { confirmFocused(); return true; }
            // ÉCHAP est neutralisé : jouer exige d'avoir choisi un personnage. Sans
            // ça, ÉCHAP fermait l'écran et lâchait un joueur NON sélectionné dans le
            // monde (aucune interaction possible). Le seul moyen d'en sortir = choisir.
            case GLFW.GLFW_KEY_ESCAPE -> { return true; }
            default -> { return super.keyPressed(event); }
        }
    }

    // Renforce le blocage d'ÉCHAP (certains chemins ferment via shouldCloseOnEsc()).
    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            layout();
            int mx = (int) event.x(), my = (int) event.y();
            if (overButton(mx, my)) { confirmFocused(); return true; }
            int t = tileAt(mx, my);
            if (t >= 0) {
                if (t == focused) { if (doubleClick) confirmFocused(); }
                else focusTo(t);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void confirmFocused() {
        if (isCreateTile(focused)) { onCreate(); return; }
        List<CharacterCard> list = cards();
        if (focused < 0 || focused >= list.size()) return;
        CharacterCard c = list.get(focused);
        if (!c.dead()) onSelect(c);
        else fr.reborn.hud.menu.RebornSounds.deny();
    }

    private void onSelect(CharacterCard c) {
        // Applique + persiste le skin RP composé du perso choisi sur le joueur local
        // (l'override reste actif en jeu jusqu'à la déconnexion ; re-sélection au
        // prochain login le ré-applique). Pas d'apparence → skin Minecraft normal.
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            if (c.hasAppearance()) {
                fr.reborn.hud.skin.RebornSkins.applySpec(
                    mc.player.getUUID(), fr.reborn.hud.skin.SkinSpec.deserialize(c.appearance()));
            } else {
                fr.reborn.hud.skin.RebornSkins.clear(mc.player.getUUID());
            }
        }
        fr.reborn.hud.menu.RebornSounds.confirm();   // confirmation : entrée en jeu
        boolean sent = sendAction("select:" + c.id());
        // Écran de chargement UNIQUEMENT si la sélection est réellement partie au
        // serveur. Sinon (canSend=false), on RESTE sur l'écran de sélection au lieu
        // de lâcher un joueur non sélectionné en jeu. L'écran de chargement se ferme
        // à la confirmation serveur « selected » (RebornHudClient) ; son minuteur
        // n'est qu'un repli si la confirmation se perd.
        if (sent) {
            Minecraft.getInstance().setScreenAndShow(new CharacterLoadingScreen(c.firstName(), c.clanColor()));
        }
    }

    private void onCreate() {
        Minecraft.getInstance().setScreenAndShow(new CharacterCreateScreen());
    }

    /** Envoie une commande C2S sur reborn:character. Retourne {@code true} si envoyée
     *  (serveur présent), {@code false} sinon (feedback local, pas d'avancée d'écran). */
    private boolean sendAction(String cmd) {
        if (ClientPlayNetworking.canSend(CharacterPayload.ID)) {
            ClientPlayNetworking.send(new CharacterPayload(cmd));
            return true;
        }
        note(cmd.startsWith("select:") ? "Sélection (hors serveur)" : "Création (hors serveur)");
        return false;
    }

    private void note(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("[Reborn] " + msg));
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
