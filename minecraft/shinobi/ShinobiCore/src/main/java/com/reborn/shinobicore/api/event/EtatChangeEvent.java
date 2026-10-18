package com.reborn.shinobicore.api.event;

import com.reborn.shinobicore.api.Stable;
import com.reborn.shinobicore.api.etat.Couche;
import com.reborn.shinobicore.api.etat.Lien;
import com.reborn.shinobicore.api.etat.Source;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <b>Avant</b> une transition de couche (docs/technique/ETATS_JOUEUR.md §8) : annulable — veto d'un plugin (ShinobiTail
 * qui sauve son hôte d'un KO, une défense qui refuse une entrave…). Envoyé par {@code EtatService#transition} et par les
 * détenteurs qui l'annoncent (entrée en KO de la chaîne KO).
 */
@Stable
public class EtatChangeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Couche couche;
    private final String avant;
    private final String apres;
    private final Source source;
    private final Lien lien;
    private boolean cancelled;

    public EtatChangeEvent(@NotNull Player player, @NotNull Couche couche, @NotNull String avant, @NotNull String apres,
                           @NotNull Source source, @Nullable Lien lien) {
        this.player = player;
        this.couche = couche;
        this.avant = avant;
        this.apres = apres;
        this.source = source;
        this.lien = lien;
    }

    public @NotNull Player player() { return player; }

    public @NotNull Couche couche() { return couche; }

    /** Valeur actuelle de la couche ({@code VALIDE}, {@code LIBRE}, {@code MONDE}…). */
    public @NotNull String avant() { return avant; }

    /** Valeur demandée. */
    public @NotNull String apres() { return apres; }

    public @NotNull Source source() { return source; }

    public @Nullable Lien lien() { return lien; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
