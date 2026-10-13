package fr.reborn.hud.parchemin;

import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Lecture d'un parchemin (clic droit, rouleau en main). Motif de l'écran : le rouleau se déroule horizontalement entre ses deux
 * baguettes de bois ; la lecture part de la droite, comme un makimono. On y lit la technique, ses signes, et la
 * progression de l'apprentissage en séances (perles laquées), avec le délai avant la prochaine séance. Washi, brocart
 * à la couleur du rang, baguettes et grand kanji : textures de {@code tools/gen_bibliotheque_textures.py}.
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
        BiblioArt.fond(g, width, height, 0.66f);
    }

    private static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private static final int PW = 440, PH = 220;

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        long now = System.currentTimeMillis();
        int cx = width / 2, top = Math.max(8, height / 2 - PH / 2 - 8);
        float u = ease((now - openedAt) / 520f);
        int half = Math.max(6, Math.round(PW / 2f * u));
        int x0 = cx - half, x1 = cx + half;

        // washi déroulé depuis le centre, brocart du rang en haut et en bas
        DaKit.blit(g, BiblioArt.ROULEAU, x0, top, 4 + PW / 2 - half, 4, 2 * half, PH + 7, 448, 232);
        BiblioArt.brocart(g, x0, top + 5, 2 * half, t.rank());
        BiblioArt.brocart(g, x0, top + PH - 13, 2 * half, t.rank());
        // baguettes (embouts d'or pour les rangs A et S)
        boolean noble = t.rank() == 'A' || t.rank() == 'S';
        DaKit.blit(g, BiblioArt.BAGUETTES, x0 - 10, top - 12, noble ? 14 : 0, 0, 14, 244, 28, 244);
        DaKit.blit(g, BiblioArt.BAGUETTES, x1 - 4, top - 12, noble ? 14 : 0, 0, 14, 244, 28, 244);

        if (u > 0.92f) {
            g.enableScissor(x0 + 2, top, x1 - 2, top + PH);
            content(g, cx - PW / 2, top, now, mx, my);
            g.disableScissor();
        }
        BiblioArt.aides(g, font, width, height, new String[][]{{"Echap", "ranger le rouleau"}});
        super.extractRenderState(g, mx, my, delta);
    }

    private static int kanjiIndex(Technique t) {
        return switch (t.nature()) {
            case "Katon" -> 0;
            case "Suiton" -> 1;
            case "Doton" -> 2;
            case "Fūton" -> 3;
            case "Raiton" -> 4;
            default -> t.branch().startsWith("Tai") ? 5 : t.branch().startsWith("Ken") ? 6 : 7;
        };
    }

    private void content(GuiGraphicsExtractor g, int x, int top, long now, int mx, int my) {
        // colonne de droite : grand kanji de la nature et sceau du rang (la lecture commence ici)
        int rx = x + PW - 76;
        DaKit.blit(g, BiblioArt.KANJI, rx + 8, top + 26, kanjiIndex(t) * 44, 0, 44, 44, 352, 44);
        BiblioArt.sceau26(g, rx + 30, top + 96, t.rank());
        g.fill(rx - 6, top + 24, rx - 5, top + PH - 22, 0x38462810);

        // corps du texte
        int lx = x + 26, lw = rx - 18 - lx;
        Component titre = DaKit.arcade(t.name());
        float ech = font.width(titre) * 2 <= lw ? 2f : 1f;
        DaKit.texteEchelle(g, font, titre, lx, top + 25, DaKit.ENCRE, ech);
        int y = top + 25 + Math.round(7 * ech) + 9;
        DaKit.inter8(g, font, DaKit.inter(t.typeLine() + " · " + ScrollArt.rankName(t.rank()) + " · " + t.difficulty()),
            lx, y, DaKit.ENCRE_DOUCE, 0);
        BiblioArt.trait(g, lx - 2, y + 7, Math.min(160, lw));
        y += 18;
        DaKit.Paragraphe desc = DaKit.paragraphe(font, t.desc(), lw, 1f, 10, 3);
        desc.dessiner(g, font, lx, y, DaKit.ENCRE);
        y += desc.hauteur() + 6;

        if (!t.signs().isEmpty()) {
            DaKit.etiq(g, font, SIGNES, lx, y + 5, DaKit.ENCRE_DOUCE);
            int sx0 = lx + Math.round(SIGNES.w()) + 8, sx = sx0;
            for (String sign : t.signs()) {
                Component c = DaKit.inter(sign);
                int sw = Math.round(DaKit.largeur7(font, c)) + 10;
                if (sx + sw > lx + lw) { sx = sx0; y += 14; }
                DaKit.coupeH(g, BiblioArt.SIGNE, sx, y, sw + 1, 0, 0, 120, 12, 4, 120, 12);
                DaKit.inter7(g, font, c, sx + sw / 2f, y + 5.5f, DaKit.ENCRE, 1);
                sx += sw + 3;
            }
            y += 14;
        }

        // apprentissage : une perle par séance, bouton à droite de la rangée
        int by = top + PH - 70;
        DaKit.etiq(g, font, APPRENTISSAGE, lx, by, DaKit.ENCRE_DOUCE);
        int total = t.seances();
        boolean petit = total > 12;
        int bead = petit ? 7 : 9, gap = petit ? 3 : 4;
        int bx = lx;
        int py = by + 7;
        for (int i = 0; i < total; i++) {
            boolean fait = i < done, next = i == done;
            int e = fait ? 0 : next && (now / 500) % 2 == 0 ? 2 : 1;
            if (petit) DaKit.blit(g, BiblioArt.PERLES, bx, py + 1, e * 8, 10, 7, 7, 30, 17);
            else DaKit.blit(g, BiblioArt.PERLES, bx, py, e * 10, 0, 9, 9, 30, 17);
            bx += bead + gap;
        }
        boolean finished = done >= total;
        boolean ready = nextIn.equals("maintenant") && !known && !pending;
        int bw = 110, bxx = rx - 16 - bw, byy = by - 2;
        boolean hv = DaKit.dans(mx, my, bxx, byy, bw, 20);
        button = null;
        if (!known && !pending && !(finished && !live)) {
            int etat = !ready ? 2 : hv ? 1 : 0;
            DaKit.coupeH(g, BiblioArt.BOUTON, bxx, byy, bw + 2, 0, etat * 22, 112, 22, 6, 112, 66);
            DaKit.arcFin(g, font, DaKit.arcade(finished ? "Terminer" : "S'entrainer"), bxx + bw / 2f, byy + 10 - 1.75f,
                ready ? DaKit.CREME : DaKit.INACTIF, 1, ready);
            if (ready && live) button = new int[]{bxx, byy, bw, 20};
        }
        String state = known ? "Tu connais déjà cette technique."
                : pending || (finished && !live && t.rank() == 'S') ? "Toutes les séances sont faites : en attente de la validation du staff."
                : finished ? (live ? "Toutes les séances sont faites : termine l'apprentissage." : "Technique apprise.")
                : "Séance " + (done + 1) + " / " + total + (ready ? " · prête" : " · prochaine séance " + nextIn);
        int sy = py + bead + 10;
        DaKit.inter8(g, font, DaKit.inter(state), lx, sy, DaKit.ENCRE, 0);
        String note = withMaster ? "Un maître t'accompagne : chaque séance réussie compte double."
                : t.rank() == 'S' ? "Rang S : la dernière séance ouvre une demande de validation au staff."
                : "Avec un maître de cette technique, une séance réussie compte double.";
        DaKit.Paragraphe n = DaKit.paragraphe(font, note, rx - 22 - lx, DaKit.S7, 9, 2);
        n.dessiner(g, font, lx, sy + 7, withMaster ? DaKit.ENCRE_ROUGE : DaKit.ENCRE_DOUCE);
    }

    private static final DaKit.Etiq SIGNES = DaKit.etiq(net.minecraft.client.Minecraft.getInstance().font, "Signes"),
        APPRENTISSAGE = DaKit.etiq(net.minecraft.client.Minecraft.getInstance().font, "Apprentissage");

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (e.button() == 0 && button != null && DaKit.dans(e.x(), e.y(), button[0], button[1], button[2], button[3])) {
            fr.reborn.hud.menu.RebornSounds.uiClick();
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("tech", t.id());
            ParcheminClient.sendAction("train", o);
            onClose();                                     // l'épreuve se joue dans le monde
        }
        return true;
    }
}
