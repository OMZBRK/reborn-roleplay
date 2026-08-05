package fr.reborn.hud.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(InGameHud.class)
public interface InGameHudInvoker {

    @Invoker("renderHealthBar")
    void reborn$invokeRenderHealthBar(GuiGraphicsExtractor ctx, Player player, int x, int y, int lines, int regenOff, float maxHealth, int lastHealth, int health, int absorption, boolean blinking);

    @Invoker("renderFood")
    void reborn$invokeRenderFood(GuiGraphicsExtractor ctx, Player player, int top, int right);

    @Invoker("renderArmor")
    static void reborn$invokeRenderArmor(GuiGraphicsExtractor ctx, Player player, int top, int rowOff, int armorIcon, int left) {
        throw new AssertionError("Invoker stub — replaced by Mixin at runtime.");
    }
}
