package fr.reborn.integrity.ui.screens;

import fr.reborn.integrity.ui.RebornBranding;
import fr.reborn.integrity.ui.RebornButton;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.ControlsOptionsScreen;
import net.minecraft.client.gui.screen.option.SoundOptionsScreen;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;
import net.minecraft.text.Text;

/**
 * Screen Paramètres custom Reborn — remplace l'OptionsScreen vanilla
 * pour limiter les options expose au joueur a 4 categories :
 * Video / Audio / Controles / Discord.
 *
 * <p>Pas de skin, pas de resource pack, pas de language, pas de
 * accessibility, pas de online/Realms — toutes ces categories sont
 * masquees par cohérence avec un serveur RP qui gere tout cote serveur.
 */
public class RebornOptionsScreen extends Screen {

    private final Screen parent;

    private static final int BG = 0xFF0A0A0A;
    private static final int ACCENT = 0xFFC9A66B;
    private static final int FG = 0xFFFFFAF0;

    public RebornOptionsScreen(Screen parent) {
        super(Text.literal("Paramètres"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int btnW = 220;
        int btnH = 26;
        int spacing = 6;
        int totalH = 5 * btnH + 4 * spacing + 30;
        int startY = Math.max(80, (this.height - totalH) / 2);
        int centerX = this.width / 2 - btnW / 2;

        this.addDrawableChild(new RebornButton(centerX, startY, btnW, btnH,
            Text.literal("VIDÉO"),
            b -> client.setScreen(new VideoOptionsScreen(this, client, client.options))));

        this.addDrawableChild(new RebornButton(centerX, startY + (btnH + spacing), btnW, btnH,
            Text.literal("AUDIO"),
            b -> client.setScreen(new SoundOptionsScreen(this, client.options))));

        this.addDrawableChild(new RebornButton(centerX, startY + 2 * (btnH + spacing), btnW, btnH,
            Text.literal("CONTRÔLES"),
            b -> client.setScreen(new ControlsOptionsScreen(this, client.options))));

        this.addDrawableChild(new RebornButton(centerX, startY + 3 * (btnH + spacing), btnW, btnH,
            Text.literal("DISCORD"),
            b -> RebornBranding.openDiscord()));

        this.addDrawableChild(new RebornButton(centerX, startY + 4 * (btnH + spacing) + 16, btnW, btnH,
            Text.literal("RETOUR"),
            b -> close()));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Override pour eviter le panorama vanilla — fond noir uni.
        context.fill(0, 0, this.width, this.height, BG);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Ligne d'accent en haut.
        context.fill(0, 50, this.width, 51, ACCENT);
        // Titre — pas de scale (le scale linear sur la font MC floute). On
        // utilise drawCenteredTextWithShadow qui rend pixel-perfect.
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("PARAMÈTRES"),
            this.width / 2, 28, FG);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}
