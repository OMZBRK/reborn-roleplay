package fr.reborn.hud.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(InGameHud.class)
public interface InGameHudInvoker {

    @Invoker("renderHealthBar")
    void reborn$invokeRenderHealthBar(DrawContext ctx, PlayerEntity player, int x, int y, int lines, int regenOff, float maxHealth, int lastHealth, int health, int absorption, boolean blinking);

    @Invoker("renderFood")
    void reborn$invokeRenderFood(DrawContext ctx, PlayerEntity player, int top, int right);

    @Invoker("renderArmor")
    static void reborn$invokeRenderArmor(DrawContext ctx, PlayerEntity player, int top, int rowOff, int armorIcon, int left) {
        throw new AssertionError("Invoker stub — replaced by Mixin at runtime.");
    }
}
