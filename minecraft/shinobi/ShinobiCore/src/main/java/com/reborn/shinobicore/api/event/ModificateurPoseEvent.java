package com.reborn.shinobicore.api.event;

import com.reborn.shinobicore.api.Stable;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * <b>Avant</b> la pose d'un modificateur du registre : annulable (immunité, résistance — Sharingan contre un genjutsu
 * léger, insectes Aburame…). L'intensité et la durée sont modifiables (résistance).
 */
@Stable
public class ModificateurPoseEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String type;
    private final String famille;
    private final String source;
    private double intensite;
    private long dureeMs;
    private boolean cancelled;

    public ModificateurPoseEvent(@NotNull Player player, @NotNull String type, @NotNull String famille, double intensite,
                                 long dureeMs, @NotNull String source) {
        this.player = player;
        this.type = type;
        this.famille = famille;
        this.intensite = intensite;
        this.dureeMs = dureeMs;
        this.source = source;
    }

    public @NotNull Player player() { return player; }

    public @NotNull String type() { return type; }

    public @NotNull String famille() { return famille; }

    public @NotNull String source() { return source; }

    public double intensite() { return intensite; }

    public void intensite(double v) { this.intensite = Math.max(0, v); }

    public long dureeMs() { return dureeMs; }

    public void dureeMs(long v) { this.dureeMs = Math.max(0, v); }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
