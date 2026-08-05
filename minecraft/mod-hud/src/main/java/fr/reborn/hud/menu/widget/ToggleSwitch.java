package fr.reborn.hud.menu.widget;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarrationPart;
import net.minecraft.client.gui.components.ClickableWidget;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Interrupteur on/off façon iOS (piste arrondie + pastille coulissante),
 * réutilisable dans les écrans Reborn (Paramètres, ESC…). ON = accent Reborn,
 * OFF = gris ; la pastille glisse en douceur entre les deux états.
 *
 * <p>Découplé de tout état : on lui passe un getter (état courant) et un
 * callback appelé au clic avec la nouvelle valeur — au caller de persister.
 */
public class ToggleSwitch extends ClickableWidget {

    public static final int W = 30;
    public static final int H = 16;

    private final BooleanSupplier getter;
    private final Consumer<Boolean> onToggle;
    /** Progression visuelle 0 (off) → 1 (on), lissée à chaque frame. */
    private float anim;

    public ToggleSwitch(int x, int y, BooleanSupplier getter, Consumer<Boolean> onToggle) {
        super(x, y, W, H, Component.literal("Interrupteur"));
        this.getter = getter;
        this.onToggle = onToggle;
        this.anim = getter.getAsBoolean() ? 1f : 0f;
    }

    @Override
    protected void renderWidget(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        boolean on = getter.getAsBoolean();
        float target = on ? 1f : 0f;
        anim += (target - anim) * 0.30f; // slide fps-léger (suffisant pour un toggle)
        if (Math.abs(target - anim) < 0.01f) anim = target;

        int x = getX(), y = getY();
        boolean hover = isHovered();

        // Piste : gris → accent selon l'état.
        int track = Colors.lerp(Colors.SURFACE_ELEVATED, Colors.ACCENT, anim);
        DrawHelpers.roundedRect(ctx, x, y, W, H, H / 2, track);
        // Liseré subtil au survol.
        if (hover) {
            DrawHelpers.roundedOutlinedRect(ctx, x, y, W, H, H / 2,
                Colors.TRANSPARENT, Colors.withAlpha(Colors.WHITE_PURE, 0.25f));
        }

        // Pastille blanche coulissante.
        int knobR = H / 2 - 2;
        int knobCx = Math.round(x + (H / 2f) + anim * (W - H));
        int knobCy = y + H / 2;
        DrawHelpers.disc(ctx, knobCx, knobCy, knobR, Colors.WHITE_PURE);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.active && this.visible && clicked(mouseX, mouseY)) {
            onToggle.accept(!getter.getAsBoolean());
            playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        builder.put(NarrationPart.TITLE,
            Component.literal(getter.getAsBoolean() ? "Activé" : "Désactivé"));
    }
}
