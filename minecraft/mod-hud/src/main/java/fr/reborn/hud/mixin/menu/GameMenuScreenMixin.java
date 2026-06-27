package fr.reborn.hud.mixin.menu;

import fr.reborn.hud.menu.esc.EscMenuRenderer;
import fr.reborn.hud.menu.esc.EscPanels;
import fr.reborn.hud.menu.esc.EscTabButton;
import fr.reborn.hud.menu.esc.EscTabs;
import fr.reborn.hud.menu.screens.RebornOptionsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Refonte du menu pause (ESC menu) Reborn. Référence : {@code esc-menu.jsx}.
 *
 * <p>Strategie identique au TitleScreenMixin :
 * <ol>
 *   <li>{@code init()} : on supprime TOUS les boutons vanilla (Continue,
 *       Send Feedback, Options, Advancements, etc.) et on ajoute nos
 *       4 tabs Reborn (Reprendre / Paramètres / Report / Déconnexion).</li>
 *   <li>{@code render()} : on injecte en TAIL pour dessiner notre overlay
 *       par-dessus tout (backdrop sombre + 4 panels en grille + community
 *       bar + tabs).</li>
 * </ol>
 */
@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/esc-mixin");

    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "initWidgets", at = @At("RETURN"))
    private void reborn$rebuildEscMenu(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        // 1. Drop tous les widgets vanilla.
        List<Element> toRemove = new ArrayList<>();
        for (Element child : this.children()) {
            if (child instanceof ClickableWidget) {
                toRemove.add(child);
            }
        }
        for (Element e : toRemove) {
            this.remove(e);
        }
        LOG.info("esc menu : {} widgets vanilla retirés", toRemove.size());

        // 2. Ajoute les 4 tabs Reborn (Reprendre / Paramètres / Report / Déconnexion).
        // Dimensions adaptatives (cf EscTabs) pour tenir dans la largeur même
        // à GUI Scale élevé — rendu et hitbox restent cohérents.
        int tabsY = 18;
        int tabW = EscTabs.tabW(this.width);
        int tabH = EscTabs.tabH();
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            int tabX = EscTabs.tabX(this.width, i);
            boolean isDanger = (i == 3); // Déconnexion = rouge.
            this.addDrawableChild(new EscTabButton(
                tabX, tabsY, tabW, tabH,
                EscTabs.tabLabel(i),
                isDanger,
                b -> handleTab(client, idx)
            ));
        }
    }

    private void handleTab(MinecraftClient client, int idx) {
        switch (idx) {
            case 0 -> client.setScreen(null); // Reprendre : ferme le menu, retour au jeu.
            case 1 -> client.setScreen(new RebornOptionsScreen(this));
            case 2 -> { /* TODO : Report — ouvrir un Screen Report (PR ulterieure) */ }
            case 3 -> {
                // Déconnexion : retour au title screen via disconnect.
                if (client.world != null) {
                    boolean isSingleplayer = client.isInSingleplayer();
                    client.world.disconnect();
                    if (isSingleplayer) {
                        client.disconnect();
                    } else {
                        client.disconnect();
                    }
                    client.setScreen(new net.minecraft.client.gui.screen.TitleScreen());
                }
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void reborn$renderOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 400);

        EscMenuRenderer.renderBackground(context, this.width, this.height);

        // 4 panels en grille 2x2.
        int[] bounds = EscMenuRenderer.panelGridBounds(this.width, this.height);
        int gridLeft = bounds[0];
        int gridTop = bounds[1];
        int panelW = bounds[2];
        int panelH = bounds[3];
        int gap = EscMenuRenderer.PANEL_GAP;

        EscPanels.renderProfile(context,
            gridLeft, gridTop, panelW, panelH);
        EscPanels.renderStream(context,
            gridLeft + panelW + gap, gridTop, panelW, panelH);
        EscPanels.renderBlog(context,
            gridLeft, gridTop + panelH + gap, panelW, panelH);
        EscPanels.renderRewards(context,
            gridLeft + panelW + gap, gridTop + panelH + gap, panelW, panelH);

        // Tabs en haut (passive, les widgets cliquables sont au-dessus).
        EscTabs.renderPassive(context, this.width, 18);

        // Community bar en bas.
        int barX = gridLeft;
        int barY = this.height - EscMenuRenderer.COMMUNITY_BAR_H - 16;
        int barW = this.width - 2 * gridLeft;
        EscPanels.renderCommunityBar(context, barX, barY, barW, EscMenuRenderer.COMMUNITY_BAR_H);

        context.getMatrices().pop();
    }
}
