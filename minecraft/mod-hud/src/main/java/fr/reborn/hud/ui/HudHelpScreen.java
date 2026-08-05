package fr.reborn.hud.ui;

import fr.reborn.hud.ui.style.RebornColors;
import fr.reborn.hud.ui.style.RoundedRect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

/**
 * Modal d'aide affichant tous les raccourcis du HUD editor groupés par
 * catégorie. Ouverte par click sur le {@code "?"} du keybar.
 */
public final class HudHelpScreen extends Screen {

    private static final int CARD_WIDTH = 380;
    private final Screen parent;

    public HudHelpScreen(Screen parent) {
        super(Component.literal("Reborn HUD — Aide"));
        this.parent = parent;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0x80000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        extractBackground(ctx, mouseX, mouseY, delta);
        Font tr = this.font;

        // Sections (catégorie, [keys])
        String[][] sections = {
            {"NAVIGATION",
                "H · Ouvrir / Fermer l'éditeur HUD",
                "Échap · Fermer (panel ou éditeur)",
                "Ctrl+M · Paramètres chat"
            },
            {"ÉDITION",
                "Clic + drag · Déplacer une box",
                "↑ ↓ ← → · Nudge 1px (Shift = 10px)",
                "Molette · Modifier la taille (scale)",
                "Shift + drag · Mouvement libre (sans snap)",
                "Ctrl + clic · Sélection multiple"
            },
            {"HISTORIQUE",
                "Ctrl+Z / Ctrl+W · Annuler",
                "Ctrl+Y · Refaire"
            },
            {"BOXES",
                "Clic œil · Toggle visibilité",
                "Clic engrenage · Ouvrir panneau de réglages",
                "Drag poignée bleue · Redimensionner"
            },
            {"PRESETS",
                "Clic preset · Appliquer",
                "Clic droit · Renommer / Dupliquer / Supprimer"
            }
        };

        int cardW = Math.min(CARD_WIDTH, this.width - 24);
        int cardH = Math.min(36 + countLines(sections) * 14 + 32, this.height - 24);
        int cardX = (this.width - cardW) / 2;
        int cardY = (this.height - cardH) / 2;

        // Drop shadow
        for (int i = 1; i <= 6; i++) {
            int a = (7 - i) * 8;
            ctx.fill(cardX - i, cardY + 4, cardX + cardW + i, cardY + cardH + i, a << 24);
        }
        RoundedRect.fill(ctx, cardX, cardY, cardW, cardH, 8, RebornColors.BG_PANEL_ELEVATED);
        RoundedRect.border(ctx, cardX, cardY, cardW, cardH, 8, RebornColors.BORDER_STRONG);

        ctx.text(tr, Component.literal("RACCOURCIS · Reborn HUD").withStyle(ChatFormatting.BOLD),
            cardX + 14, cardY + 12, RebornColors.FOREGROUND, false);
        ctx.fill(cardX + 14, cardY + 26, cardX + cardW - 14, cardY + 27, RebornColors.BORDER);

        int y = cardY + 36;
        for (String[] section : sections) {
            ctx.text(tr, Component.literal(section[0]).withStyle(ChatFormatting.BOLD),
                cardX + 14, y, RebornColors.ACCENT_HOVER, false);
            y += 12;
            for (int i = 1; i < section.length; i++) {
                String line = section[i];
                int sepIdx = line.indexOf(" · ");
                if (sepIdx > 0) {
                    String keys = line.substring(0, sepIdx);
                    String desc = line.substring(sepIdx + 3);
                    // Keys en accent + description en muted
                    ctx.text(tr, Component.literal(keys),
                        cardX + 22, y, RebornColors.ACCENT_HOVER, false);
                    int kW = tr.width(keys);
                    ctx.text(tr, Component.literal(" · " + desc),
                        cardX + 22 + kW, y, RebornColors.FOREGROUND_MUTED, false);
                } else {
                    ctx.text(tr, Component.literal(line),
                        cardX + 22, y, RebornColors.FOREGROUND_MUTED, false);
                }
                y += 12;
            }
            y += 4;
        }

        // Close hint
        ctx.text(tr, Component.literal("Clique n'importe où pour fermer").withStyle(ChatFormatting.ITALIC),
            cardX + (cardW - tr.width("Clique n'importe où pour fermer")) / 2,
            cardY + cardH - 14, RebornColors.FOREGROUND_MUTED, false);
    }

    private static int countLines(String[][] sections) {
        int n = 0;
        for (String[] s : sections) n += s.length;
        n += sections.length; // les separators
        return n;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y(); int button = event.button();
        onClose();
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key(), scanCode = event.scancode(), modifiers = event.modifiers();
        onClose();
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
