package com.reborn.shinobicore.api.event;

import com.reborn.shinobicore.api.Stable;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** <b>Après</b> le retrait d'un modificateur du registre (fin, dissipation, retrait par sa source, KO). */
@Stable
public class ModificateurRetireEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String type;
    private final String raison;

    public ModificateurRetireEvent(@NotNull Player player, @NotNull String type, @NotNull String raison) {
        this.player = player;
        this.type = type;
        this.raison = raison;
    }

    public @NotNull Player player() { return player; }

    public @NotNull String type() { return type; }

    /** {@code fin}, {@code retire}, {@code dissipe}, {@code ko}… */
    public @NotNull String raison() { return raison; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
