package fr.reborn.hud.parchemin;

import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.ui.Da;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Lecture d'un parchemin (clic droit, rouleau en main). Motif de l'écran : le rouleau se déroule horizontalement entre ses deux
 * baguettes de bois ; la lecture part de la droite, comme un makimono. On y lit la technique, ses signes, et la
 * progression de l'apprentissage en séances (perles laquées), avec le délai avant la prochaine séance.
 */
public final class LectureScreen extends Screen {

    private Technique t;
    private int done;
    private String nextIn;
    private boolean withMaster;
    /** Données du serveur : sinon démo du banc d'essai. */
    private boolean live, known, pending;
    private final long openedAt = System.currentTimeMillis();
    private int[] button;

    public LectureScreen(Technique t, int done, String nextIn, boolean withMaster) {
        super(Component.literal(t.name()));
        this.t = t;
        this.done = done;
        this.nextIn = nextIn;
        this.withMaster = withMaster;
    }

    /** Lecture envoyée par le serveur ({@code {"t":"read"}}). */
    public static LectureScreen fromJson(com.google.gson.JsonObject o) {
        LectureScreen s = new LectureScreen(Technique.fromJson(o.getAsJsonObject("tech")), 0, "", false);
        s.update(o);
        return s;
    }

    public String techId() { return t.id(); }

    public void update(com.google.gson.JsonObject o) {
        t = Technique.fromJson(o.getAsJsonObject("tech"));
        done = o.get("done").getAsInt();
        nextIn = o.get("nextIn").getAsString();
        withMaster = o.has("master") && o.get("master").getAsBoolean();
        known = o.has("known") && o.get("known").getAsBoolean();
        pending = o.has("pending") && o.get("pending").getAsBoolean();
        live = true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float d) {
        g.fillGradient(0, 0, width, height, 0xA0080406, 0xD0080406);
    }

    private static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        long now = System.currentTimeMillis();
        int fw = Math.min(width - 60, 430), fh = Math.min(height - 70, 210);
        int cx = width / 2, top = height / 2 - fh / 2 - 6;
        float u = ease((now - openedAt) / 520f);
        int half = Math.max(6, Math.round(fw / 2f * u));
        int x0 = cx - half, x1 = cx + half;

        // papier
        g.fill(x0 + 4, top + 6, x1 + 4, top + fh + 6, 0x60000000);
        g.fillGradient(x0, top, x1, top + fh, ScrollArt.WASHI_HI, ScrollArt.WASHI);
        long s = 0x5DEECE66DL;
        for (int i = 0; i < 160; i++) {                                                            // fibres
            s = s * 6364136223846793005L + 1442695040888963407L;
            int fx = cx - fw / 2 + (int) ((s >>> 33) % fw), fy = top + (int) ((s >>> 17) % fh);
            if (fx > x0 && fx < x1 - 6) g.fill(fx, fy, fx + 2 + (int) ((s >>> 50) % 5), fy + 1, 0x14462810);
        }
        g.fill(x0, top, x1, top + 2, 0x30000000);
        g.fill(x0, top + fh - 2, x1, top + fh, 0x30000000);
        // bandes de brocart en haut et en bas
        int brocade = ScrollArt.rankColor(t.rank());
        g.fill(x0, top + 3, x1, top + 7, brocade);
        g.fill(x0, top + fh - 7, x1, top + fh - 3, brocade);
        g.fill(x0, top + 7, x1, top + 8, ScrollArt.GOLD);
        g.fill(x0, top + fh - 8, x1, top + fh - 7, ScrollArt.GOLD);
        // baguettes
        roller(g, x0 - 7, top - 6, fh + 12);
        roller(g, x1 - 1, top - 6, fh + 12);

        if (u > 0.92f) {
            g.enableScissor(x0 + 2, top, x1 - 2, top + fh);
            content(g, cx - fw / 2, top, fw, fh, now, mx, my);
            g.disableScissor();
        }
        Da.hint(g, font, width, height, "Echap : ranger le rouleau");
        super.extractRenderState(g, mx, my, delta);
    }

    private void roller(GuiGraphicsExtractor g, int x, int y, int h) {
        g.fill(x, y, x + 8, y + h, ScrollArt.WOOD_D);
        g.fillGradient(x + 1, y, x + 7, y + h, 0xFF7A5232, 0xFF4A301B);
        g.fill(x + 2, y, x + 3, y + h, 0x40FFE0B0);
        boolean noble = t.rank() == 'A' || t.rank() == 'S';
        int cap = noble ? ScrollArt.GOLD : 0xFF3A2414;
        g.fill(x - 1, y - 4, x + 9, y + 1, cap);
        g.fill(x - 1, y + h - 1, x + 9, y + h + 4, cap);
    }

    private void content(GuiGraphicsExtractor g, int x, int top, int w, int h, long now, int mx, int my) {
        // colonne de droite : grand kanji de la nature et sceau du rang (la lecture commence ici)
        int rx = x + w - 62;
        String kanji = switch (t.nature()) {
            case "Katon" -> "火";
            case "Suiton" -> "水";
            case "Doton" -> "土";
            case "Fūton" -> "風";
            case "Raiton" -> "雷";
            default -> t.branch().startsWith("Tai") ? "体" : t.branch().startsWith("Ken") ? "剣" : "忍";
        };
        g.pose().pushMatrix();
        g.pose().translate(rx + 6, top + 22);
        g.pose().scale(4f, 4f);
        g.text(font, Component.literal(kanji), 0, 0, 0xD02A1A10, false);
        g.pose().popMatrix();
        ScrollArt.hanko(g, font, rx + 12, top + 70, 22, t.rank());
        g.fill(rx - 8, top + 16, rx - 7, top + h - 16, 0x40462810);

        // corps du texte
        int lx = x + 18, lw = rx - 30 - lx;
        g.pose().pushMatrix();
        g.pose().translate(lx, top + 16);
        g.pose().scale(1.6f, 1.6f);
        g.text(font, Component.literal(t.name()).withStyle(ChatFormatting.BOLD), 0, 0, ScrollArt.INK, false);
        g.pose().popMatrix();
        g.text(font, Component.literal(t.typeLine() + " · " + ScrollArt.rankName(t.rank()) + " · " + t.difficulty()),
                lx, top + 34, ScrollArt.INK_SOFT, false);
        for (int k = 0; k < lw; k++) {                                                             // trait de pinceau
            float q = k / (float) lw;
            int th = q < 0.05f ? 1 : q > 0.8f ? (q > 0.95f ? 0 : 1) : 2;
            if (th > 0) g.fill(lx + k, top + 46, lx + k + 1, top + 46 + th, ScrollArt.LACQUER);
        }
        int ly = top + 54;
        List<FormattedCharSequence> desc = font.split(Component.literal(t.desc()), lw);
        for (FormattedCharSequence s : desc) { g.text(font, s, lx, ly, ScrollArt.INK, false); ly += 10; }

        if (!t.signs().isEmpty()) {
            ly += 4;
            g.text(font, RebornFont.body("Signes"), lx, ly, ScrollArt.INK_SOFT, false);
            int sx = lx + 34;
            for (String sign : t.signs()) {
                int sw = font.width(sign) + 8;
                if (sx + sw > lx + lw) { sx = lx + 34; ly += 13; }
                g.fill(sx, ly - 2, sx + sw, ly + 9, 0x1C462810);
                Da.outline(g, sx, ly - 2, sw, 11, 0x50462810);
                g.text(font, Component.literal(sign), sx + 4, ly, ScrollArt.INK, false);
                sx += sw + 3;
            }
            ly += 12;
        }

        // apprentissage : une perle par séance, bouton à droite de la rangée
        int by = top + h - 64;
        g.text(font, RebornFont.body("Apprentissage"), lx, by, ScrollArt.INK_SOFT, false);
        int total = t.seances();
        int bead = total > 12 ? 7 : 9, gap = total > 12 ? 3 : 4;
        int bx = lx;
        by += 12;
        for (int i = 0; i < total; i++) {
            boolean ok = i < done, next = i == done;
            int c = ok ? ScrollArt.LACQUER : 0x30462810;
            g.fill(bx + 1, by, bx + bead - 1, by + bead, c);
            g.fill(bx, by + 1, bx + bead, by + bead - 1, c);
            if (ok) g.fill(bx + 2, by + 1, bx + 4, by + 2, 0x60FFFFFF);
            if (next && (now / 500) % 2 == 0) Da.outline(g, bx - 1, by - 1, bead + 2, bead + 2, ScrollArt.GOLD);
            bx += bead + gap;
        }
        boolean finished = done >= total;
        boolean ready = nextIn.equals("maintenant") && !known && !pending;
        int bw = 80, bh = 15, bxx = rx - 18 - bw, byy = by - 3;
        boolean hv = Da.in(mx, my, bxx, byy, bw, bh);
        button = null;
        if (!known && !pending && !(finished && !live)) {
            Da.plate(g, font, bxx, byy, bw, bh, finished ? "Terminer" : "S'entrainer", ready, hv && ready, ready);
            if (ready && live) button = new int[]{bxx, byy, bw, bh};
        }
        String state = known ? "Tu connais déjà cette technique."
                : pending || (finished && !live && t.rank() == 'S') ? "Toutes les séances sont faites : en attente de la validation du staff."
                : finished ? (live ? "Toutes les séances sont faites : termine l'apprentissage." : "Technique apprise.")
                : "Séance " + (done + 1) + " / " + total + (ready ? " · prête" : " · prochaine séance " + nextIn);
        g.text(font, Component.literal(state), lx, by + bead + 5, ScrollArt.INK, false);
        String note = withMaster ? "Un maître t'accompagne : chaque séance réussie compte double."
                : t.rank() == 'S' ? "Rang S : la dernière séance ouvre une demande de validation au staff."
                : "Avec un maître de cette technique, une séance réussie compte double.";
        int ny = by + bead + 15;
        for (FormattedCharSequence s : font.split(Component.literal(note), rx - 26 - lx)) {
            g.text(font, s, lx, ny, ScrollArt.INK_SOFT, false);
            ny += 9;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (e.button() == 0 && button != null && Da.in(e.x(), e.y(), button[0], button[1], button[2], button[3])) {
            fr.reborn.hud.menu.RebornSounds.uiClick();
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("tech", t.id());
            ParcheminClient.sendAction("train", o);
            onClose();                                     // l'épreuve se joue dans le monde
        }
        return true;
    }
}
