package fr.reborn.hud.menu.character;

import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.nameplate.Nameplates;
import fr.reborn.hud.skin.RebornSkins;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Maquettes, <b>inactives en production</b> ({@code REBORN_MOCKUPS=1}) : sélection du personnage en « scène »
 * (illustration du village en fond, persos posés devant, plaques de nom du jeu) et parcours de création narratif
 * (une question par écran, logos des villages). Capture dans run/screenshots/maquette_*.png. Canevas 960×540.
 */
public final class SceneMockups {

    private static final float VW = 960, VH = 540;
    private static final int GOLD = 0xFFD9A95E, CREAM = 0xFFF5E9D0, SUB = 0xFFC2B59A, MUTED = 0xFF7A6E5C,
            LACQUER = 0xFFA0182B;

    private record Who(String name, String clan, int clanColor, String rank, String village, String skin, boolean slim) {}

    private static final Who[] CAST = {
        new Who("Kazuki", "Uchiha", 0xC01E35, "Chūnin", "konoha", "wide/kai", false),
        new Who("Aya", "Hyūga", 0xB8A6D8, "Genin", "konoha", "slim/zuri", true),
        new Who("Renji", "Sabaku", 0xC8963C, "Tokubetsu Jōnin", "suna", "wide/efe", false),
    };
    private static final String[] VILLAGES = {"konoha", "suna", "kiri", "kumo", "iwa"};
    private static final String[] VILLAGE_NAMES = {"Konohagakure", "Sunagakure", "Kirigakure", "Kumogakure", "Iwagakure"};

    private SceneMockups() {}

    /* ===================================================================== banc */

    private static int ticks = -1, index = 0, local = 0;
    private static final List<String> SHOTS = List.of("selection", "selection_suna", "parcours_village",
            "parcours_village_kiri", "parcours_nom", "parcours_recap");

    public static void init() {
        if (!"1".equals(System.getenv("REBORN_MOCKUPS"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null || mc.level == null) { ticks = -1; return; }
            ticks++;
            if (ticks < 60) return;
            if (index >= SHOTS.size()) { if (local++ == 10) mc.stop(); return; }
            String name = SHOTS.get(index);
            if (local == 0) mc.setScreenAndShow(switch (name) {
                case "selection" -> new Selection(0);
                case "selection_suna" -> new Selection(2);
                case "parcours_village" -> new Journey(0, 0);
                case "parcours_village_kiri" -> new Journey(0, 2);
                case "parcours_nom" -> new Journey(1, 0);
                default -> new Journey(2, 0);
            });
            if (local == 40) Screenshot.grab(mc.gameDirectory, "maquette_" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> { });
            if (++local > 44) { local = 0; index++; }
        });
    }

    /* ============================================================ outils communs */

    private abstract static class Canvas extends Screen {
        float s, ox, oy;
        final long born = System.currentTimeMillis();

        Canvas(String t) { super(Component.literal(t)); }

        @Override
        public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float d) { }

        int px(float v) { return Math.round(ox + v * s); }

        int py(float v) { return Math.round(oy + v * s); }

        void rect(GuiGraphicsExtractor g, float x, float y, float w, float h, int c) {
            g.fill(px(x), py(y), px(x + w), py(y + h), c);
        }

        void vgrad(GuiGraphicsExtractor g, float x, float y, float w, float h, int top, int bot) {
            g.fillGradient(px(x), py(y), px(x + w), py(y + h), top, bot);
        }

        void text(GuiGraphicsExtractor g, Component c, float x, float y, int col, float scale, boolean center) {
            float sc = scale * s;
            float w = font.width(c) * sc;
            g.pose().pushMatrix();
            g.pose().translate(px(x) - (center ? w / 2f : 0), py(y));
            g.pose().scale(sc, sc);
            g.text(font, c, 0, 0, col, true);
            g.pose().popMatrix();
        }

        void image(GuiGraphicsExtractor g, String path, float x, float y, float w, float h, int tw, int th, int tint) {
            Identifier id = Identifier.fromNamespaceAndPath("reborn", path);
            g.blit(RenderPipelines.GUI_TEXTURED, id, px(x), py(y), 0f, 0f, px(x + w) - px(x), py(y + h) - py(y),
                    tw, th, tw, th, tint);
        }

        void logo(GuiGraphicsExtractor g, String village, float cx, float cy, float size, int tint) {
            image(g, "textures/gui/esc/village_" + village + ".png", cx - size / 2, cy - size / 2, size, size, 48, 48, tint);
        }

        /** Illustration du village en fond (480×270, agrandie au pixel près), assombrie. */
        void backdrop(GuiGraphicsExtractor g, String village, int veil) {
            g.fill(0, 0, width, height, 0xFF050304);
            image(g, "textures/gui/creation/bg_" + village + ".png", 0, 0, VW, VH, 480, 270, 0xFFFFFFFF);
            rect(g, 0, 0, VW, VH, veil);
        }

        void ellipse(GuiGraphicsExtractor g, float cx, float cy, float rx, float ry, int col) {
            for (float dy = -ry; dy <= ry; dy += 1f / s) {
                float k = (float) Math.sqrt(Math.max(0, 1 - (dy * dy) / (ry * ry)));
                g.fill(px(cx - rx * k), py(cy + dy), px(cx + rx * k), py(cy + dy) + 1, col);
            }
        }

        /** Perso rendu avec le skin donné (prêté au joueur local le temps de l'extraction), tête droite. */
        void actor(GuiGraphicsExtractor g, Who w, float footX, float footY, float size, float turn) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            UUID self = mc.player.getUUID();
            Identifier own = RebornSkins.overrideFor(self);
            boolean ownSlim = RebornSkins.isSlim(self);
            try {
                RebornSkins.setOverride(self, Identifier.withDefaultNamespace("textures/entity/player/" + w.skin + ".png"), w.slim);
                int sz = Math.round(size * s);
                int cx = px(footX), bottom = py(footY);
                int top = bottom - Math.round(sz * 1.9f);
                int half = Math.round(sz * 0.6f);
                InventoryScreen.extractEntityInInventoryFollowsMouse(g, cx - half, top, cx + half, bottom, sz, 0.0625f,
                        px(footX + turn), (top + bottom) / 2, mc.player);
            } finally {
                RebornSkins.setOverride(self, own, ownSlim);
            }
        }

        /** Plaque de nom du jeu, agrandie. */
        void plate(GuiGraphicsExtractor g, Who w, float x, float y, float scale, float alpha) {
            g.pose().pushMatrix();
            g.pose().translate(px(x), py(y));
            g.pose().scale(scale * s, scale * s);
            Nameplates.drawVillagePlate(g, font, 0, 0, w.name + " " + w.clan, w.clanColor, w.village, alpha);
            g.pose().popMatrix();
        }

        void layout() {
            s = Math.min(width / VW, height / VH);
            ox = (width - VW * s) / 2f;
            oy = (height - VH * s) / 2f;
        }

        float t() { return (System.currentTimeMillis() - born) / 1000f; }

        void footer(GuiGraphicsExtractor g, String keys) {
            vgrad(g, 0, VH - 60, VW, 60, 0x00050304, 0xE0050304);
            Component k = RebornFont.body(keys);
            text(g, k, VW - 24 - font.width(k), VH - 22, SUB, 1f, false);
        }
    }

    /* ======================================== sélection : les persos dans le village */

    static final class Selection extends Canvas {
        private final int focus;

        Selection(int focus) {
            super("Sélection");
            this.focus = focus;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float d) {
            layout();
            Who f = CAST[focus];
            backdrop(g, f.village, 0x58050304);
            // sol : bande sombre en bas pour poser les persos
            vgrad(g, 0, 330, VW, 210, 0x00050304, 0xD0050304);

            float[] xs = {300, 480, 660};
            float ground = 470;
            // ombres + lumière au sol sous le choisi
            for (int i = 0; i < 3; i++) {
                boolean on = i == focus;
                float sz = on ? 118 : 96;
                float fy = ground - (on ? 0 : 18);
                if (on) for (int k = 6; k >= 1; k--) ellipse(g, xs[i], fy + 3, 30 + k * 10, 6 + k * 2.4f, (8 + k * 4) << 24 | 0xF2C890);
                ellipse(g, xs[i], fy + 2, sz * 0.36f, 6, 0x90000000);
            }
            for (int i = 0; i < 3; i++) {
                if (i == focus) continue;
                actor(g, CAST[i], xs[i], ground - 18, 96, i < focus ? 40 : -40);
            }
            g.nextStratum();
            actor(g, CAST[focus], xs[focus], ground, 118, 0);
            g.nextStratum();
            // plaques de nom du jeu au-dessus des têtes
            for (int i = 0; i < 3; i++) {
                boolean on = i == focus;
                float sz = on ? 118 : 96, fy = ground - (on ? 0 : 18);
                float top = fy - sz * 1.9f - 6;
                plate(g, CAST[i], xs[i], top, on ? 1.6f : 1.25f, on ? 1f : 0.7f);
                if (on) {
                    float b = (float) Math.sin(t() * 3) * 2;
                    rect(g, xs[i] - 1, top - 34 + b, 2, 6, GOLD);
                    rect(g, xs[i] - 3, top - 32 + b, 6, 2, GOLD);
                }
            }
            // place libre
            ellipse(g, 840, ground - 18, 30, 6, 0x50F5E9D0);
            text(g, RebornFont.display("+ NOUVEAU SHINOBI"), 840, ground - 70, 0xD0D9A95E, 1f, true);
            text(g, RebornFont.body("1 place libre"), 840, ground - 56, MUTED, 1f, true);

            // fiche du perso choisi, en bas à gauche : emblème du village, nom, grade
            logo(g, f.village, 54, VH - 58, 34, 0xFFF5E9D0);
            text(g, RebornFont.display((f.name + " " + f.clan).toUpperCase()), 82, VH - 72, CREAM, 1.4f, false);
            int vi = java.util.Arrays.asList(VILLAGES).indexOf(f.village);
            text(g, RebornFont.body(f.rank + "  ·  " + VILLAGE_NAMES[Math.max(0, vi)]), 82, VH - 50, SUB, 1f, false);
            rect(g, 82, VH - 38, 36, 2, 0xFF000000 | f.clanColor);

            footer(g, "Clic : jouer     ← → : changer     Échap : quitter");
            // bouton
            float bx = VW / 2f - 70, by = VH - 46;
            rect(g, bx, by, 140, 24, 0xE0150A0D);
            rect(g, bx, by, 140, 1, GOLD);
            rect(g, bx, by + 23, 140, 1, GOLD);
            text(g, RebornFont.display("ENTRER EN JEU"), VW / 2f, by + 8, CREAM, 1f, true);
        }
    }

    /* ======================================== parcours : une question par écran */

    static final class Journey extends Canvas {
        private final int step, village;

        Journey(int step, int village) {
            super("Création");
            this.step = step;
            this.village = village;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float d) {
            layout();
            String v = VILLAGES[village];
            // fond : le village choisi, plongé dans la pénombre (plus sombre hors de l'écran « village »)
            backdrop(g, v, step == 0 ? 0x90050304 : 0xC0050304);
            vgrad(g, 0, 0, 520, VH, 0x00000000, 0x00000000);
            for (int i = 0; i < 140; i++) rect(g, 520 + i, 0, 1, VH, (int) (90 * i / 140f) << 24 | 0x050304);
            rect(g, 660, 0, VW - 660, VH, 0x5A050304);
            fireflies(g, t());

            boolean close = step == 1;
            float fx = 280, fy = close ? 640 : 455, size = close ? 200 : 120;
            if (!close) {
                for (int k = 7; k >= 1; k--) ellipse(g, fx, fy + 4, 40 + k * 11, 8 + k * 2.6f, (8 + k * 4) << 24 | 0xD9A95E);
                ellipse(g, fx, fy + 3, 46, 7, 0x80000000);
            }
            actor(g, CAST[0], fx, fy, size, close ? 70 : 50);
            g.nextStratum();

            float qx = 560;
            int cur = step == 0 ? 1 : step == 1 ? 2 : 6;
            switch (step) {
                case 0 -> {
                    // emblème du village choisi, en grand, derrière le titre
                    logo(g, v, 880, 470, 120, 0x22F5E9D0);
                    title(g, qx, 110, "DE QUEL VILLAGE VIENS-TU ?");
                    for (int i = 0; i < VILLAGES.length; i++) {
                        boolean on = i == village;
                        float y = 158 + i * 28;
                        if (on) {
                            rect(g, qx - 8, y - 6, 260, 24, 0x40D9A95E);
                            rect(g, qx - 8, y - 6, 2, 24, GOLD);
                        }
                        logo(g, VILLAGES[i], qx + 10, y + 6, 16, on ? 0xFFF5E9D0 : 0x907A6E5C);
                        text(g, RebornFont.display(VILLAGE_NAMES[i].toUpperCase()), qx + 28, y + 1, on ? CREAM : MUTED, 1.1f, false);
                    }
                    float y = 158 + 5 * 28;
                    text(g, RebornFont.display("DÉSERTEUR"), qx + 28, y + 1, MUTED, 1.1f, false);
                    rect(g, qx, 330, 220, 1, 0x60D9A95E);
                    para(g, qx, 342, DESC[village]);
                }
                case 1 -> {
                    title(g, qx, 160, "QUEL EST TON NOM ?");
                    String name = "Kazuki" + (((int) (t() * 2)) % 2 == 0 ? "_" : "");
                    text(g, RebornFont.display(name.toUpperCase()), qx, 206, CREAM, 1.8f, false);
                    rect(g, qx, 234, 300, 1, GOLD);
                    text(g, RebornFont.body("Ton prénom. Le nom de clan viendra avec ton sang."), qx, 244, SUB, 1f, false);
                }
                default -> {
                    title(g, qx, 110, "EST-CE BIEN TOI ?");
                    Who w = CAST[0];
                    plate(g, w, qx + 120, 178, 1.6f, 1f);
                    String[][] rows = {{"VILLAGE", "Konohagakure"}, {"CLAN", "Uchiha · Sharingan"},
                            {"NINDŌ", "Ne jamais revenir sur sa parole"}, {"GRADE", "Académicien"}};
                    for (int i = 0; i < rows.length; i++) {
                        float y = 196 + i * 20;
                        text(g, RebornFont.display(rows[i][0]), qx, y, GOLD, 1f, false);
                        text(g, RebornFont.body(rows[i][1]), qx + 84, y, CREAM, 1f, false);
                    }
                    rect(g, qx, 286, 300, 1, 0x60D9A95E);
                    rect(g, qx - 8, 298, 200, 22, 0x40D9A95E);
                    rect(g, qx - 8, 298, 2, 22, GOLD);
                    text(g, RebornFont.display("OUI, C'EST MOI"), qx + 4, 305, CREAM, 1.1f, false);
                    text(g, RebornFont.display("RECOMMENCER"), qx + 4, 333, MUTED, 1.1f, false);
                }
            }
            // étapes + touches
            footer(g, step == 1 ? "Entrée : continuer     Échap : retour" : "↑ ↓ : choisir     Entrée : valider     Échap : retour");
            for (int i = 0; i < 6; i++) {
                float x = 24 + i * 14;
                if (i < cur) rect(g, x, VH - 22, 8, 3, i == cur - 1 ? GOLD : 0xFF8A6A2A);
                else rect(g, x, VH - 22, 8, 3, 0x40F5E9D0);
            }
        }

        private static final String[] DESC = {
            "Le village caché de la Feuille, au pays du Feu. La Volonté du Feu s'y transmet d'une génération à l'autre.",
            "Le village caché du Sable, au cœur du désert du pays du Vent. On y survit d'abord, on y règne ensuite.",
            "Le village caché de la Brume. Ses ninjas ont longtemps appris à se taire — et à frapper sans être vus.",
            "Le village caché des Nuages, perché dans les montagnes de la Foudre. Puissance et fierté.",
            "Le village caché des Roches. Solide comme sa pierre, et tout aussi têtu.",
        };

        private void title(GuiGraphicsExtractor g, float x, float y, String s) {
            text(g, RebornFont.display(s), x, y, CREAM, 1.7f, false);
            rect(g, x, y + 22, 44, 2, LACQUER);
        }

        private void para(GuiGraphicsExtractor g, float x, float y, String str) {
            var lines = font.split(RebornFont.body(str), 300);
            for (int i = 0; i < lines.size(); i++) {
                g.pose().pushMatrix();
                g.pose().translate(px(x), py(y + i * 12));
                g.pose().scale(s, s);
                g.text(font, lines.get(i), 0, 0, SUB, true);
                g.pose().popMatrix();
            }
        }

        private void fireflies(GuiGraphicsExtractor g, float t) {
            Random r = new Random(5);
            for (int i = 0; i < 22; i++) {
                float bx = r.nextFloat() * 560, by = 220 + r.nextFloat() * 280;
                float x = bx + (float) Math.sin(t * 0.5 + i) * 14, y = by + (float) Math.cos(t * 0.35 + i * 2) * 10;
                float glow = 0.5f + 0.5f * (float) Math.sin(t * 2 + i * 1.3);
                rect(g, x - 1, y - 1, 3, 3, (int) (40 * glow) << 24 | 0xF2C870);
                rect(g, x, y, 1, 1, (int) (120 + 135 * glow) << 24 | 0xF5D890);
            }
        }
    }
}
