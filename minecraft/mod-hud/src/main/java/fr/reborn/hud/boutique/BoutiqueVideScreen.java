package fr.reborn.hud.boutique;

import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.menu.settings.ReglagesArt;
import fr.reborn.hud.monnaie.Pinceau;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * La <b>Boutique</b> de l'Échap (décision du 15/10 : « Change le nom ÉCHOPPE par BOUTIQUE tout simplement, et ne mets
 * RIEN dedans ; je te passerai des screens ») : un écran vide et propre en attendant les visuels de l'utilisateur —
 * voile sur le jeu comme les autres écrans de l'Échap, « BOUTIQUE » et un filet d'or au centre, « Bientôt. », Échap pour
 * revenir. Aucun kanji, aucune tenue, aucun RBCoins. Les tenues s'achètent chez le tailleur (boutiques-lieux).
 */
public final class BoutiqueVideScreen extends Screen {

    private final Screen parent;
    private final Pinceau pz = new Pinceau();
    private final long ouvertLe = System.currentTimeMillis();

    public BoutiqueVideScreen(Screen parent) {
        super(Component.literal("Boutique"));
        this.parent = parent;
    }

    private boolean hudAvant, hudPris;

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (!hudPris && mc.gui != null) {
            hudAvant = mc.gui.hud.isHidden();
            ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(true);
            hudPris = true;
        }
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (hudPris && mc.gui != null) ((fr.reborn.hud.mixin.HudAccessor) (Object) mc.gui.hud).reborn$setHidden(hudAvant);
        hudPris = false;
        super.removed();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        float a = Math.min(1f, (System.currentTimeMillis() - ouvertLe) / 180f);
        ctx.fill(0, 0, this.width, this.height, Minecraft.getInstance().level == null ? 0xFF0A0608 : DaKit.argb(0x080406, 0.78f * a));
        DaKit.etire(ctx, ReglagesArt.VIGNETTE, 0, 0, this.width, this.height, 160, 90, DaKit.blanc(0.8f * a));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        pz.police(this.font);
        float cx = this.width / 2f, cy = this.height / 2f - 14;
        pz.arcadeC("BOUTIQUE", cx, cy - 16, BoutiqueScreen.OR_CLAIR, 2f);
        pz.filetH(cx - 70, cx + 70, cy + 4, BoutiqueScreen.OR);
        pz.texteC("Bientôt.", cx, cy + 18, 16, BoutiqueScreen.CREME_DOUX, false);
        boolean h = DaKit.dans(mouseX, mouseY, Math.round(cx - 40), Math.round(cy + 46), 80, 14);
        pz.texteC("Échap  revenir", cx, cy + 53, 13, h ? BoutiqueScreen.CREME : BoutiqueScreen.GRIS, false);
        pz.vider(ctx);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        float cx = this.width / 2f, cy = this.height / 2f - 14;
        if (event.button() == 0 && DaKit.dans(event.x(), event.y(), Math.round(cx - 40), Math.round(cy + 46), 80, 14)) {
            RebornSounds.uiClick();
            onClose();
            return true;
        }
        return super.mouseClicked(event, dbl);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() { Minecraft.getInstance().setScreenAndShow(parent); }
}
