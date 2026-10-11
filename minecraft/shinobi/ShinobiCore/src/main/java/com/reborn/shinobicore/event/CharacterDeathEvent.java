package com.reborn.shinobicore.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Mort RP validée d'un personnage (validée par le staff). Le personnage est déjà marqué
 * mort et sauvegardé quand l'événement part. Les addons s'en servent pour libérer ce que le personnage détenait
 * (ex. slots des techniques de rang S dans ShinobiAbilities).
 */
public class CharacterDeathEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final UUID characterId;
    private final String characterName;

    public CharacterDeathEvent(@NotNull UUID playerId, @NotNull UUID characterId, @NotNull String characterName) {
        this.playerId = playerId;
        this.characterId = characterId;
        this.characterName = characterName;
    }

    public @NotNull UUID playerId() { return playerId; }

    public @NotNull UUID characterId() { return characterId; }

    public @NotNull String characterName() { return characterName; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
