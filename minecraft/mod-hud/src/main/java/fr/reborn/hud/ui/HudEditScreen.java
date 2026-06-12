package fr.reborn.hud.ui;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.config.HudConfig;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementBounds;
import fr.reborn.hud.element.HudElementState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Éditeur interactif des positions HUD façon Lunar / Feather Client :
 * <ul>
 *   <li>Chaque élément est rendu avec une boîte dashed translucide qui suit
 *       sa position réelle (vanilla + offset utilisateur).</li>
 *   <li>Clic + drag sur le corps d'une boîte → déplace l'élément en live.</li>
 *   <li>Molette souris au-dessus d'une boîte → ajuste le scale (taille).</li>
 *   <li>Clic sur l'icône œil top-right → toggle visible / hidden.</li>
 * </ul>
 *
 * <p>{@code shouldPause = false} pour que le HUD vanilla reste visible
 * derrière le menu — l'utilisateur voit immédiatement le résultat de ses
 * modifs (chat qui se décale, scoreboard qui rétrécit, etc.).
 *
 * <p>Bordures couleur : neutre (gris) pour non-sélectionné, accent
 * (cyan Reborn) pour celui sous la souris, accent + remplissage léger
 * pour celui en cours de drag.
 */
public class HudEditScreen extends Screen {

    private static final int OUTLINE_NEUTRAL  = 0x80FFFFFF;
    private static final int OUTLINE_HOVER    = 0xFF66DDFF;
    private static final int OUTLINE_DRAGGING = 0xFFFFD27A;
    private static final int FILL_HOVER       = 0x2266DDFF;
    private static final int FILL_DRAGGING    = 0x33FFD27A;
    private static final int FILL_HIDDEN      = 0x40FF6060;
    private static final int EYE_BG           = 0xC0000000;
    private static final int EYE_OPEN         = 0xFFEEEEEE;
    private static final int EYE_CLOSED       = 0xFF666666;

    private static final int EYE_W = 18;
    private static final int EYE_H = 12;

    /** Pixels par cran de molette, et coef de scale. */
    private static final float SCALE_STEP = 0.05f;

    private final Screen parent;
    private final HudConfig config;

    private HudElement draggedElement = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    public HudEditScreen(Screen parent) {
        super(Text.translatable("reborn-hud.screen.title"));
        this.parent = parent;
        this.config = RebornHudClient.config();
    }

    @Override
    protected void init() {
        // Boutons footer : Reset all + Close
        int btnW = 100;
        int btnH = 20;
        int y = this.height - 28;
        addDrawableChild(ButtonWidget.builder(
            Text.translatable("reborn-hud.screen.reset_all"), b -> {
                config.resetAll();
                config.save();
            }
        ).dimensions(this.width / 2 - btnW - 4, y, btnW, btnH).build());
        addDrawableChild(ButtonWidget.builder(
            Text.translatable("reborn-hud.screen.close"), b -> close()
        ).dimensions(this.width / 2 + 4, y, btnW, btnH).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Overlay sombre tres leger : on veut voir le HUD derriere
        ctx.fill(0, 0, this.width, this.height, 0x30000000);

        // Hint en haut : explique le mode edit
        TextRenderer tr = this.textRenderer;
        ctx.drawText(tr, this.title, 12, 10, 0xFFFFFFFF, false);
        ctx.drawText(tr, Text.translatable("reborn-hud.screen.hint"),
            12, 22, 0xFFB0B0B0, false);

        HudElement hovered = elementUnderMouse(mouseX, mouseY);

        // Render chaque element en boucle. Z-order : non-selectionnes
        // d'abord, l'element drag en dernier pour qu'il soit au-dessus.
        for (HudElement element : HudElement.values()) {
            if (element == draggedElement) continue;
            renderElementBox(ctx, element, hovered == element, false);
        }
        if (draggedElement != null) {
            renderElementBox(ctx, draggedElement, false, true);
        }

        // Boutons footer + title viennent du super.render apres
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void renderElementBox(DrawContext ctx, HudElement element, boolean hovered, boolean dragging) {
        HudElementState state = config.stateOf(element);
        HudElementBounds bounds = HudElementBounds.currentFor(element, state, this.width, this.height);

        // Couleur outline + remplissage selon etat
        int outline = OUTLINE_NEUTRAL;
        int fill = 0;
        if (dragging) {
            outline = OUTLINE_DRAGGING;
            fill = FILL_DRAGGING;
        } else if (hovered) {
            outline = OUTLINE_HOVER;
            fill = FILL_HOVER;
        }
        if (!state.visible()) {
            fill = FILL_HIDDEN; // tint rouge si cache pour bien le voir
        }

        // Fill semi-transparent
        if (fill != 0) {
            ctx.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), fill);
        }

        // Outline (4 lignes 1px chacune)
        ctx.fill(bounds.x(),     bounds.y(),     bounds.right(), bounds.y() + 1, outline);
        ctx.fill(bounds.x(),     bounds.bottom() - 1, bounds.right(), bounds.bottom(), outline);
        ctx.fill(bounds.x(),     bounds.y(),     bounds.x() + 1, bounds.bottom(), outline);
        ctx.fill(bounds.right() - 1, bounds.y(),     bounds.right(), bounds.bottom(), outline);

        // Label nom + position + scale au-dessus de la boite
        TextRenderer tr = this.textRenderer;
        String label = String.format("%s  (%+d, %+d)  x%.2f%s",
            element.displayName(), state.x(), state.y(), state.scale(),
            state.visible() ? "" : "  [hidden]");
        int labelY = bounds.y() - tr.fontHeight - 2;
        if (labelY < 0) labelY = bounds.bottom() + 2;
        ctx.drawText(tr, Text.literal(label), bounds.x(), labelY, 0xFFFFFFFF, true);

        // Eye icon top-right de la boite
        renderEyeIcon(ctx, eyeRectFor(bounds), state.visible());
    }

    private void renderEyeIcon(DrawContext ctx, HudElementBounds eye, boolean visible) {
        ctx.fill(eye.x(), eye.y(), eye.right(), eye.bottom(), EYE_BG);
        ctx.fill(eye.x(), eye.y(), eye.right(), eye.y() + 1, 0xFFFFFFFF);
        ctx.fill(eye.x(), eye.bottom() - 1, eye.right(), eye.bottom(), 0xFFFFFFFF);
        ctx.fill(eye.x(), eye.y(), eye.x() + 1, eye.bottom(), 0xFFFFFFFF);
        ctx.fill(eye.right() - 1, eye.y(), eye.right(), eye.bottom(), 0xFFFFFFFF);

        int color = visible ? EYE_OPEN : EYE_CLOSED;
        // Œil : ovale + pupille au centre. Approximation 3-rows pour eviter
        // de bouffer un fichier texture rien que pour ca.
        int cx = eye.centerX();
        int cy = eye.centerY();
        ctx.fill(cx - 4, cy - 1, cx + 4, cy + 1, color);
        ctx.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFF000000);
        if (!visible) {
            // barre oblique de fermeture
            for (int i = -6; i <= 6; i++) {
                int px = cx + i;
                int py = cy + i / 2;
                if (eye.contains(px, py)) {
                    ctx.fill(px, py, px + 1, py + 1, 0xFFFF6060);
                }
            }
        }
    }

    private static HudElementBounds eyeRectFor(HudElementBounds box) {
        // Place l'œil DANS la boîte, top-right, decale de 2px du bord
        int x = box.right() - EYE_W - 2;
        int y = box.y() + 2;
        return new HudElementBounds(x, y, EYE_W, EYE_H);
    }

    private HudElement elementUnderMouse(int mouseX, int mouseY) {
        // Priorite a l'element drag, sinon scan en ordre inverse (drawn last = on top)
        if (draggedElement != null) return draggedElement;
        HudElement[] values = HudElement.values();
        for (int i = values.length - 1; i >= 0; i--) {
            HudElement element = values[i];
            HudElementState state = config.stateOf(element);
            HudElementBounds bounds = HudElementBounds.currentFor(
                element, state, this.width, this.height);
            if (bounds.contains(mouseX, mouseY)) return element;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        // Cherche un element sous la souris
        HudElement element = elementUnderMouse((int) mouseX, (int) mouseY);
        if (element == null) return false;

        HudElementState state = config.stateOf(element);
        HudElementBounds bounds = HudElementBounds.currentFor(
            element, state, this.width, this.height);

        // 1. Click sur l'icone œil → toggle visible
        HudElementBounds eye = eyeRectFor(bounds);
        if (eye.contains(mouseX, mouseY)) {
            config.setState(element, state.withVisible(!state.visible()));
            config.save();
            return true;
        }

        // 2. Sinon : on commence un drag
        this.draggedElement = element;
        // On garde le point ancre offset par rapport au coin top-left
        this.dragOffsetX = (int) (mouseX - bounds.x());
        this.dragOffsetY = (int) (mouseY - bounds.y());
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button != 0 || draggedElement == null) {
            return super.mouseDragged(mouseX, mouseY, button, dx, dy);
        }
        HudElementState state = config.stateOf(draggedElement);
        // Position cible du top-left de la boite
        int targetX = (int) (mouseX - dragOffsetX);
        int targetY = (int) (mouseY - dragOffsetY);
        // Position vanilla (sans offset) pour retrouver la difference
        HudElementBounds vanilla = HudElementBounds.vanillaFor(
            draggedElement, this.width, this.height);
        int newOffsetX = targetX - vanilla.x();
        int newOffsetY = targetY - vanilla.y();
        config.setState(draggedElement, state.withPos(newOffsetX, newOffsetY));
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggedElement != null) {
            // Save a la fin du drag plutot qu'a chaque frame pour ne pas
            // marteler le disque.
            config.save();
            draggedElement = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        HudElement element = elementUnderMouse((int) mouseX, (int) mouseY);
        if (element == null) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        HudElementState state = config.stateOf(element);
        float delta = (float) verticalAmount * SCALE_STEP;
        config.setState(element, state.withScale(state.scale() + delta));
        config.save();
        return true;
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
