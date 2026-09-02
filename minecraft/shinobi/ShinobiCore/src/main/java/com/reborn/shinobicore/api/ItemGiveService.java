package com.reborn.shinobicore.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Function;

/**
 * The unified custom-item handout registry behind
 * {@code /sc itemgive <token>}. Addon plugins register their tokens
 * at boot; the engine owns the command surface.
 */
@Stable
public interface ItemGiveService {

    /**
     * Register a givable item. {@code factory} builds a fresh stack
     * for the recipient on each give.
     */
    void register(String token, String label, Function<Player, ItemStack> factory);

    /** All registered tokens, original casing, registration order. */
    List<String> tokens();

    /** True when {@code token} is registered (case-insensitive). */
    boolean has(String token);
}
