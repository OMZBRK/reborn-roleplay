package com.reborn.shinobicore.api.event;

import com.reborn.shinobicore.api.Stable;
import com.reborn.shinobicore.api.etat.Etat;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <b>Après</b> un changement d'état observé (une couche, l'état principal ou la liste des modificateurs), au plus une
 * fois par tick et par joueur — c'est aussi ce qui alimente le journal {@code ETAT} et le paquet {@code reborn:etat}.
 * Notification, pas de veto.
 */
@Stable
public class EtatChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Etat avant;
    private final Etat apres;

    public EtatChangedEvent(@NotNull Player player, @Nullable Etat avant, @NotNull Etat apres) {
        this.player = player;
        this.avant = avant;
        this.apres = apres;
    }

    public @NotNull Player player() { return player; }

    /** L'état précédent (null au premier calcul après la connexion). */
    public @Nullable Etat avant() { return avant; }

    public @NotNull Etat apres() { return apres; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
