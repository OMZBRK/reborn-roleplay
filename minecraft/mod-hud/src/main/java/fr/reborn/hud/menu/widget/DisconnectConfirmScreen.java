package fr.reborn.hud.menu.widget;

import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.menu.settings.ReglagesArt;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Confirmation « Se déconnecter ? » (ouverte depuis l'Échap), au niveau de la fiche shinobi (DA §4.1, §6), version
 * épurée. Signature : le <b>chapeau de voyageur</b> (sugegasa) posé sur la planche — se déconnecter, c'est prendre la
 * route. Annuler revient au menu pause ; Se déconnecter quitte le serveur et retourne au menu principal. Décor :
 * {@code textures/gui/deconnexion/*} ({@code tools/gen_deconnexion_textures.py}).
 */
public class DisconnectConfirmScreen extends Screen {

    private static final Identifier PLANCHE = DaKit.tex("deconnexion", "planche"), CHAPEAU = DaKit.tex("deconnexion", "chapeau");
    private static final int W = 260, H = 124, BW = 108;

    private final Screen parent;

    public DisconnectConfirmScreen(Screen parent) {
        super(Component.literal("Se déconnecter"));
        this.parent = parent;
    }

    private int px() { return (this.width - W) / 2; }

    private int py() { return (this.height - H) / 2 + 8; }

    private void disconnect() {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) {
            client.level.disconnect(Component.translatable("menu.disconnect"));
        }
        // 26.1 : Minecraft#disconnect prend l'écran de destination + un flag
        // (garde des resource packs). On revient au menu principal Reborn.
        client.disconnect(new TitleScreen(), false);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, Minecraft.getInstance().level == null ? 0xFF0A0608 : 0xC8080406);
        DaKit.etire(ctx, ReglagesArt.VIGNETTE, 0, 0, this.width, this.height, 160, 90, DaKit.blanc(0.8f));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Font f = this.font;
        int x = px(), y = py();
        DaKit.blit(ctx, PLANCHE, x - 2, y - 2, 0, 0, 268, 132, 268, 132);
        DaKit.blit(ctx, CHAPEAU, x + W / 2 - 32, y - 20, 0, 0, 64, 30, 64, 30);
        DaKit.arcX1(ctx, f, DaKit.arcade("Se déconnecter ?"), x + W / 2f, y + 24, DaKit.OR_CLAIR, 1, true);
        DaKit.inter8(ctx, f, DaKit.inter("Tu quittes le serveur pour le menu principal."), x + W / 2f, y + 46,
            DaKit.CREME_VAL, 1);
        boolean h1 = DaKit.dans(mouseX, mouseY, b1(), by(), BW, 20), h2 = DaKit.dans(mouseX, mouseY, b2(), by(), BW, 20);
        ReglagesArt.bouton(ctx, b1(), by(), BW, h1 ? 1 : 0);
        ReglagesArt.bouton(ctx, b2(), by(), BW, 3);
        DaKit.inter9(ctx, f, DaKit.inter("Annuler"), b1() + BW / 2f, by() + 10, h1 ? DaKit.CREME : DaKit.CREME_VAL, 1);
        DaKit.inter9(ctx, f, DaKit.inter("Se déconnecter"), b2() + BW / 2f, by() + 10, h2 ? DaKit.CREME : DaKit.CREME_VAL, 1);
    }

    private int b1() { return px() + W / 2 - BW - 6; }

    private int b2() { return px() + W / 2 + 6; }

    private int by() { return py() + H - 36; }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            if (DaKit.dans(event.x(), event.y(), b1(), by(), BW, 20)) { RebornSounds.uiClick(); onClose(); return true; }
            if (DaKit.dans(event.x(), event.y(), b2(), by(), BW, 20)) { RebornSounds.confirm(); disconnect(); return true; }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
