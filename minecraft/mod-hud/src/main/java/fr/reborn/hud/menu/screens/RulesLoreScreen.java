package fr.reborn.hud.menu.screens;

import fr.reborn.hud.menu.RebornButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Screen Règlement & Lore — sidebar à gauche avec 3 catégories
 * (Règlement / Lore / Patch Notes), zone de contenu à droite.
 *
 * <p>Contenu placeholder pour la PR #6 — sera connecté à l'API Reborn
 * (/v1/patchnotes, /v1/rules/current, /v1/lore) en PR ulterieure pour
 * lire les vrais textes publies par les staffs.
 */
public class RulesLoreScreen extends Screen {

    private static final int BG = 0xFF0A0A0A;
    private static final int CARD_BG = 0xCC1A1A1A;
    private static final int ACCENT = 0xFFC9A66B;
    private static final int FG = 0xFFFFFAF0;
    private static final int FG_DIM = 0xAAFFFAF0;

    private final Screen parent;
    private int activeTab = 0;
    private int scrollY = 0;

    private static final String[] TAB_LABELS = {
        "RÈGLEMENT",
        "LORE",
        "PATCH NOTES"
    };

    public RulesLoreScreen(Screen parent) {
        super(Component.literal("Règlement & Lore"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int sidebarX = 20;
        int sidebarY = 70;
        int sidebarW = Math.min(200, Math.max(120, this.width / 4));
        int btnW = sidebarW;
        int btnH = 30;
        int spacing = 8;

        for (int i = 0; i < TAB_LABELS.length; i++) {
            final int tab = i;
            this.addRenderableWidget(new RebornButton(
                sidebarX, sidebarY + i * (btnH + spacing), btnW, btnH,
                Component.literal(TAB_LABELS[i]),
                b -> { this.activeTab = tab; this.scrollY = 0; }
            ));
        }

        // Bouton retour en bas.
        this.addRenderableWidget(new RebornButton(
            sidebarX, this.height - 50, btnW, btnH,
            Component.literal("RETOUR"),
            b -> close()
        ));
    }

    @Override
    public void renderBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Override pour eviter le panorama vanilla — fond noir uni.
        context.fill(0, 0, this.width, this.height, BG);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Ligne d'accent header.
        context.fill(0, 55, this.width, 56, ACCENT);
        // Titre — pixel-perfect via drawCenteredTextWithShadow (le scale 2x
        // floute la font MC).
        context.drawCenteredTextWithShadow(textRenderer,
            Component.literal("RÈGLEMENT & LORE"),
            this.width / 2, 28, FG);

        // Zone de contenu (cote droit). Largeurs dérivées de la sidebar
        // responsive pour ne jamais passer sous zéro sur fenêtre étroite.
        int sidebarX = 20;
        int sidebarW = Math.min(200, Math.max(120, this.width / 4));
        int contentX = sidebarX + sidebarW + 20;
        int contentY = 70;
        int contentW = Math.max(160, this.width - contentX - 20);
        int contentH = this.height - contentY - 30;
        context.fill(contentX, contentY, contentX + contentW, contentY + contentH, CARD_BG);
        context.fill(contentX, contentY, contentX + contentW, contentY + 1, ACCENT);

        // Rendu du contenu en clip.
        context.enableScissor(contentX + 1, contentY + 1, contentX + contentW - 1, contentY + contentH - 1);
        String[] lines = getContentLines(activeTab);
        int textY = contentY + 16 - scrollY;
        for (String line : lines) {
            if (line.startsWith("# ")) {
                // Titre h1 : plus gros + accent
                context.text(textRenderer, line.substring(2), contentX + 16, textY, ACCENT, true);
                textY += 16;
            } else if (line.startsWith("## ")) {
                // Titre h2
                context.text(textRenderer, line.substring(3), contentX + 16, textY, FG, true);
                textY += 14;
            } else {
                context.text(textRenderer, line, contentX + 16, textY, FG_DIM, false);
                textY += 12;
            }
        }
        context.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int sidebarX = 20;
        int sidebarW = Math.min(200, Math.max(120, this.width / 4));
        int contentX = sidebarX + sidebarW + 20;
        if (mouseX >= contentX) {
            scrollY = Math.max(0, scrollY - (int) (verticalAmount * 18));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void close() {
        Minecraft.getInstance().setScreen(parent);
    }

    /**
     * Contenu placeholder par onglet. Sera remplace en PR ulterieure
     * par un fetch HTTP vers l'API Reborn ({@code /v1/patchnotes},
     * {@code /v1/rules/current}, etc.).
     */
    private String[] getContentLines(int tab) {
        switch (tab) {
            case 0:
                return new String[] {
                    "# RÈGLEMENT REBORN",
                    "",
                    "## I. Respect des joueurs",
                    "1. Aucune forme de harcèlement ou de discrimination.",
                    "2. Respecte le RP des autres : pas de meta-gaming.",
                    "3. Garde un langage correct dans les chats publics.",
                    "",
                    "## II. Gameplay",
                    "1. Pas de cheat, x-ray, autoclick ni macros.",
                    "2. Pas d'exploit de bug — signale-le en ticket.",
                    "3. Pas de griefing en dehors des zones autorisées.",
                    "",
                    "## III. RP",
                    "1. Reste cohérent avec ton personnage.",
                    "2. Pas de PVP gratuit, le combat se justifie.",
                    "3. PK / mort RP doit être respectée (perte de mémoire).",
                    "",
                    "## IV. Sanctions",
                    "1. Avertissement -> mute -> kick -> ban.",
                    "2. Les sanctions sont publiées dans #sanctions Discord.",
                    "3. Faire appel : ouvre un ticket dans le launcher.",
                };
            case 1:
                return new String[] {
                    "# LORE REBORN",
                    "",
                    "## L'Origine",
                    "Bienvenue dans l'univers Reborn, un monde shinobi",
                    "où le chakra façonne la réalité.",
                    "",
                    "## Les Clans",
                    "Trois clans majeurs s'affrontent pour le contrôle",
                    "des routes commerciales et des techniques sacrées.",
                    "",
                    "(Contenu lore à venir — sera enrichi par l'équipe)",
                };
            case 2:
                return new String[] {
                    "# PATCH NOTES",
                    "",
                    "## v0.1.0-dev (en cours)",
                    "- Refonte du main menu Minecraft",
                    "- Lecteur OST intégré (43 pistes)",
                    "- Server info card",
                    "- Système d'attestation play-token",
                    "",
                    "(Les patch notes officiels seront récupérés depuis",
                    "l'API /v1/patchnotes dans une prochaine version)",
                };
            default:
                return new String[] { "(Onglet vide)" };
        }
    }
}
