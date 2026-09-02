package com.reborn.shinobicore.gui;

import com.reborn.shinobicore.api.Stable;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marker for inventories owned by the engine screen framework (and any
 * plugin GUI that wants the framework's lifecycle rules applied to it).
 * Neutral successor of ShinobiAbilities' {@code AbilitiesGuiHolder}.
 */
@Stable
public interface GuiHolder extends InventoryHolder {
}
