package fr.reborn.hud.mixin.menu;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.esc.EscMenuRenderer;
import fr.reborn.hud.menu.esc.EscPanels;
import fr.reborn.hud.menu.esc.EscTabButton;
import fr.reborn.hud.menu.esc.EscTabs;
import fr.reborn.hud.menu.screens.ConfigShellScreen;
import fr.reborn.hud.menu.widget.DisconnectConfirmScreen;
import fr.reborn.hud.menu.widget.RebornButton;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu pause (ESC) Reborn — layout façon <b>Zenkai</b> :
 * <ul>
 *   <li>logo REBORN centré en haut + rangée d'onglets (Reprendre / Paramètres /
 *       Report / Déconnexion) ;</li>
 *   <li>colonne GAUCHE : carte profil (perso + nom / rôle / monnaie) ;</li>
 *   <li>colonne DROITE : panneau Blog/News + bouton Boutique.</li>
 * </ul>
 *
 * <p>Comme {@code TitleScreenMixin} : on retire les widgets vanilla dans
 * {@code initWidgets}, on ajoute nos widgets cliquables (onglets + boutique),
 * et on dessine le chrome dans {@code render}@TAIL (Z+400) puis on re-render
 * les enfants cliquables par-dessus.
 */
@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/esc-mixin");

    private static final Identifier LOGO = Identifier.of("reborn", "textures/gui/title/logo.png");
    private static final int LOGO_TEX_W = 2048;
    private static final int LOGO_TEX_H = 717;

    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    // ── Layout partagé (initWidgets ↔ render) ──
    private int reborn$margin() { return Math.max(24, Math.round(this.width * 0.06f)); }
    private int reborn$tabsY() { return 52; }
    private int reborn$contentTop() { return reborn$tabsY() + EscTabs.tabH() + 18; }
    private int reborn$contentBottom() { return this.height - 34; }
    private int reborn$colGap() { return 16; }
    private int reborn$leftW() {
        return Math.round((this.width - 2 * reborn$margin() - reborn$colGap()) * 0.42f);
    }
    private int reborn$rightX() { return reborn$margin() + reborn$leftW() + reborn$colGap(); }
    private int reborn$rightW() { return this.width - reborn$margin() - reborn$rightX(); }
    private static final int BOUTIQUE_H = 30;
    private int reborn$boutiqueY() { return reborn$contentBottom() - BOUTIQUE_H; }

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

        // 2. Onglets (rangée centrée sous le logo).
        int tabsY = reborn$tabsY();
        int tabW = EscTabs.tabW(this.width);
        int tabH = EscTabs.tabH();
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            int tabX = EscTabs.tabX(this.width, i);
            boolean isDanger = (i == 3);
            this.addDrawableChild(new EscTabButton(
                tabX, tabsY, tabW, tabH,
                EscTabs.tabLabel(i), isDanger,
                b -> handleTab(client, idx)));
        }

        // 3. Bouton Boutique (bas de la colonne droite) — placeholder.
        int pad = 12;
        this.addDrawableChild(RebornButton.ghost(
            reborn$rightX() + pad, reborn$boutiqueY(),
            reborn$rightW() - 2 * pad, BOUTIQUE_H,
            "Boutique", b -> { /* TODO : ouvrir la boutique */ }));

        LOG.info("esc menu Zenkai : {} vanilla retirés", toRemove.size());
    }

    private void handleTab(MinecraftClient client, int idx) {
        switch (idx) {
            case 0 -> client.setScreen(null); // Reprendre.
            case 1 -> client.setScreen(new ConfigShellScreen(this));
            case 2 -> { /* TODO : Report */ }
            case 3 -> client.setScreen(new DisconnectConfirmScreen(this)); // confirmation.
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void reborn$renderOverlay(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 400);

        // Fond assombri.
        EscMenuRenderer.renderBackground(ctx, this.width, this.height);

        // Logo REBORN centré en haut.
        int logoW = Math.min(Math.round(this.width * 0.20f), 260);
        int logoH = Math.round(logoW * (float) LOGO_TEX_H / LOGO_TEX_W);
        int logoX = (this.width - logoW) / 2;
        ctx.drawTexture(LOGO, logoX, 8, logoW, logoH, 0f, 0f, LOGO_TEX_W, LOGO_TEX_H, LOGO_TEX_W, LOGO_TEX_H);

        // Colonnes.
        int top = reborn$contentTop();
        int bottom = reborn$contentBottom();
        int cardH = bottom - top;
        int leftX = reborn$margin();
        int leftW = reborn$leftW();
        int rightX = reborn$rightX();
        int rightW = reborn$rightW();

        // Gauche : carte profil (perso + nom/rôle/monnaie).
        EscPanels.renderProfile(ctx, leftX, top, leftW, cardH);

        // Droite : blog/news (au-dessus du bouton Boutique).
        int blogH = reborn$boutiqueY() - top - 12;
        EscPanels.renderBlog(ctx, rightX, top, rightW, blogH);

        // Re-render des enfants cliquables (onglets + boutique) par-dessus le chrome.
        for (Element e : this.children()) {
            if (e instanceof ClickableWidget cw && cw.visible) {
                cw.render(ctx, mouseX, mouseY, delta);
            }
        }

        // Fine bordure d'ambiance sous les onglets (séparateur discret).
        ctx.fill(reborn$margin(), top - 10, this.width - reborn$margin(), top - 9, Colors.BORDER);

        ctx.getMatrices().pop();
    }
}
