package fr.reborn.hud.camera;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.settings.SliderWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Menu de repositionnement de la caméra épaule — panneau latéral <b>live</b>
 * (le jeu reste visible derrière, la caméra bouge en temps réel quand on
 * règle les sliders). Distance / latéral / hauteur / vitesse de rotation +
 * presets + swap épaule. Les réglages sont persistés dans
 * {@link fr.reborn.hud.menu.settings.RebornPrefs}.
 */
public class CameraScreen extends Screen {

    private final Screen parent;

    private static final int PANEL_W = 236;
    private static final int PAD = 14;
    private static final int ROW_H = 22;
    private static final int LABEL_H = 12;
    private static final int GAP = 8;

    private SliderWidget distSlider, sideSlider, upSlider, turnSlider;
    private ButtonWidget presetBtn, swapBtn;

    public CameraScreen(Screen parent) {
        super(Text.literal("Caméra"));
        this.parent = parent;
    }

    private int panelX() { return this.width - PANEL_W - 12; }

    @Override
    protected void init() {
        RebornCamera cam = RebornCamera.INSTANCE;
        int x = panelX() + PAD;
        int w = PANEL_W - 2 * PAD;
        int y = 54;

        // Distance (2.0..6.5 → 20..65).
        distSlider = new SliderWidget(x, y, w, ROW_H,
            (int) Math.round(cam.distance() * 10), 20, 65, "",
            v -> { cam.setDistance(v / 10.0); });
        y += ROW_H + LABEL_H + GAP;

        // Latéral / épaule (0..1.2 → 0..120).
        sideSlider = new SliderWidget(x, y, w, ROW_H,
            (int) Math.round(cam.rightMagnitude() * 100), 0, 120, "",
            v -> { cam.setRight(v / 100.0); });
        y += ROW_H + LABEL_H + GAP;

        // Hauteur (-0.6..1.2 → -60..120).
        upSlider = new SliderWidget(x, y, w, ROW_H,
            (int) Math.round(cam.upOffset() * 100), -60, 120, "",
            v -> { cam.setUp(v / 100.0); });
        y += ROW_H + LABEL_H + GAP;

        // Vitesse de rotation (0.1..1.0 → 10..100).
        turnSlider = new SliderWidget(x, y, w, ROW_H,
            (int) Math.round(cam.turnSpeed() * 100), 10, 100, "%",
            v -> { cam.setTurnSpeed(v / 100.0); });
        y += ROW_H + LABEL_H + GAP + 6;

        // Preset (cycle) + Swap épaule.
        presetBtn = ButtonWidget.builder(presetLabel(), b -> {
            cam.cyclePreset();
            syncSlidersToCam();
        }).dimensions(x, y, w, 20).build();
        this.addDrawableChild(presetBtn);
        y += 20 + 6;

        swapBtn = ButtonWidget.builder(swapLabel(), b -> {
            cam.swapShoulder();
            syncSlidersToCam();
        }).dimensions(x, y, w, 20).build();
        this.addDrawableChild(swapBtn);
        y += 20 + 6;

        // Réinitialiser + Fermer.
        int half = (w - 6) / 2;
        this.addDrawableChild(ButtonWidget.builder(RebornFont.body("Réinitialiser"), b -> {
            cam.setPreset(CameraPreset.DEFAUT);
            cam.setSide(1);
            cam.setTurnSpeed(0.5);
            syncSlidersToCam();
        }).dimensions(x, y, half, 20).build());
        this.addDrawableChild(ButtonWidget.builder(RebornFont.body("Fermer"), b -> close())
            .dimensions(x + half + 6, y, w - half - 6, 20).build());

        this.addDrawableChild(distSlider);
        this.addDrawableChild(sideSlider);
        this.addDrawableChild(upSlider);
        this.addDrawableChild(turnSlider);
    }

    private void syncSlidersToCam() {
        RebornCamera cam = RebornCamera.INSTANCE;
        distSlider.setValue((int) Math.round(cam.distance() * 10));
        sideSlider.setValue((int) Math.round(cam.rightMagnitude() * 100));
        upSlider.setValue((int) Math.round(cam.upOffset() * 100));
        turnSlider.setValue((int) Math.round(cam.turnSpeed() * 100));
        presetBtn.setMessage(presetLabel());
        swapBtn.setMessage(swapLabel());
    }

    private Text presetLabel() {
        return RebornFont.body("Preset : " + RebornCamera.INSTANCE.preset().label);
    }

    private Text swapLabel() {
        return RebornFont.body("Épaule : " + (RebornCamera.INSTANCE.side() > 0 ? "Droite" : "Gauche"));
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Pas de fond plein écran : on laisse le jeu visible pour le live.
        // Juste le panneau latéral semi-opaque.
        int px = panelX();
        DrawHelpers.roundedOutlinedRect(ctx, px, 12, PANEL_W, this.height - 24, 10,
            Colors.BACKDROP_85, Colors.BORDER_STRONG);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        int x = panelX() + PAD;

        // Titre.
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, 24, 0);
        ctx.getMatrices().scale(1.3f, 1.3f, 1f);
        ctx.drawText(this.textRenderer, RebornFont.bold("CAMÉRA"), 0, 0, Colors.WHITE_PURE, false);
        ctx.getMatrices().pop();

        // Labels au-dessus des sliders.
        label(ctx, "Distance", distSlider);
        label(ctx, "Décalage épaule", sideSlider);
        label(ctx, "Hauteur", upSlider);
        label(ctx, "Vitesse de rotation", turnSlider);
    }

    private void label(DrawContext ctx, String text, SliderWidget s) {
        ctx.drawText(this.textRenderer, RebornFont.body(text),
            s.getX(), s.getY() - LABEL_H, Colors.FOREGROUND_SUBTLE, false);
    }

    @Override
    public boolean shouldPause() {
        return false; // live : la caméra bouge en temps réel derrière le menu.
    }

    @Override
    public void close() {
        RebornCamera.INSTANCE.saveToPrefs();
        MinecraftClient.getInstance().setScreen(parent);
    }
}
