package fr.reborn.hud.ui;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.config.HudConfig;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementOffset;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Édition des positions du HUD — POC. Liste tous les {@link HudElement}
 * avec leur offset courant et 5 boutons par ligne :
 * <pre>
 *   Chat                 (+3, -10)    [←] [→] [↑] [↓] [Reset]
 *   Scoreboard           (0, 0)       [←] [→] [↑] [↓] [Reset]
 *   ...
 * </pre>
 *
 * <p>Chaque clic nudge de {@link #NUDGE_PX} pixels et persiste immédiatement.
 * La rendu HUD vanilla reste visible derrière (Screen non-pause), donc on
 * voit l'élément bouger en temps réel.
 *
 * <p>Évolution future prévue : true drag-and-drop sur le canvas (clic sur
 * l'élément lui-même, glisser pour déplacer). Pour le POC on reste sur
 * boutons step-by-step parce que c'est moins de code et déjà fonctionnel
 * pour valider l'architecture mixin → config → render.
 */
public class HudEditScreen extends Screen {

    /** Décalage par clic, en pixels écran (post-scaling). */
    private static final int NUDGE_PX = 5;
    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 40;

    private final Screen parent;
    private final HudConfig config;

    public HudEditScreen(Screen parent) {
        super(Text.translatable("reborn-hud.screen.title"));
        this.parent = parent;
        this.config = RebornHudClient.config();
    }

    @Override
    protected void init() {
        int y = LIST_TOP;
        for (HudElement element : HudElement.values()) {
            addRowFor(element, y);
            y += ROW_HEIGHT;
        }
        // Bouton Close en bas.
        addDrawableChild(ButtonWidget.builder(
            Text.translatable("reborn-hud.screen.close"), b -> close()
        ).dimensions(this.width / 2 - 40, this.height - 28, 80, 20).build());
    }

    private void addRowFor(HudElement element, int y) {
        int btnW = 22;
        int btnH = 18;
        int gap = 4;
        int x = 220; // colonne ou commencent les boutons
        addDrawableChild(makeNudgeButton("<", x, y, btnW, btnH,
            () -> nudge(element, -NUDGE_PX, 0)));
        x += btnW + gap;
        addDrawableChild(makeNudgeButton(">", x, y, btnW, btnH,
            () -> nudge(element, NUDGE_PX, 0)));
        x += btnW + gap;
        addDrawableChild(makeNudgeButton("^", x, y, btnW, btnH,
            () -> nudge(element, 0, -NUDGE_PX)));
        x += btnW + gap;
        addDrawableChild(makeNudgeButton("v", x, y, btnW, btnH,
            () -> nudge(element, 0, NUDGE_PX)));
        x += btnW + gap;
        addDrawableChild(makeNudgeButton("Reset", x, y, 44, btnH,
            () -> reset(element)));
    }

    private ButtonWidget makeNudgeButton(String label, int x, int y, int w, int h, Runnable action) {
        return ButtonWidget.builder(Text.literal(label), b -> action.run())
            .dimensions(x, y, w, h)
            .build();
    }

    private void nudge(HudElement element, int dx, int dy) {
        HudElementOffset current = config.offsetOf(element);
        config.setOffset(element, current.withDelta(dx, dy));
        config.save();
    }

    private void reset(HudElement element) {
        config.setOffset(element, HudElementOffset.ZERO);
        config.save();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Fond semi-transparent qui laisse voir le HUD derrière. shouldPause()
        // est false, donc le world + HUD continuent de render.
        ctx.fill(0, 0, this.width, this.height, 0x80000000);
        super.render(ctx, mouseX, mouseY, delta);

        TextRenderer tr = this.textRenderer;
        ctx.drawText(tr, this.title, 16, 14, 0xFFFFFFFF, false);

        // Labels par ligne : nom de l'élément + offset courant.
        int y = LIST_TOP + 4;
        for (HudElement element : HudElement.values()) {
            HudElementOffset off = config.offsetOf(element);
            String label = element.displayName() + "  (" + off.x() + ", " + off.y() + ")";
            ctx.drawText(tr, Text.literal(label), 16, y, 0xFFE0E0E0, false);
            y += ROW_HEIGHT;
        }
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
