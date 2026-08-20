package com.reborn.shinobicombat.combat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Réserve de <b>stamina</b> taïjutsu par joueur — autoritaire côté serveur,
 * anti-spam du M1. Distincte du chakra ({@code ResourceService} de ShinobiCore).
 *
 * <p><b>Feel</b> (corrigé) : la régén ne reprend qu'après un <b>délai</b>
 * ({@link #REGEN_DELAY_MS}) sans coup — sinon, en frappant, la réserve <b>descend
 * vraiment</b> (avant, régén ≈ cadence de clic → jamais vide). Vidée = coup refusé.
 */
public final class StaminaManager {

    /** Réserve maximale. */
    public static final double MAX = 100.0;
    /** Régénération par seconde (hors délai post-coup). */
    public static final double REGEN_PER_SECOND = 16.0;
    /** Pas de régén pendant ce délai après le dernier coup (ms). */
    public static final long REGEN_DELAY_MS = 1200L;

    private final Map<UUID, Double> current = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastConsume = new ConcurrentHashMap<>();

    /** Stamina courante ({@link #MAX} si rien consommé). */
    public double get(UUID id) {
        return current.getOrDefault(id, MAX);
    }

    public double max() {
        return MAX;
    }

    /**
     * Dépense {@code amount} si disponible. Retourne {@code true} (dépensé, régén
     * gelée pendant {@link #REGEN_DELAY_MS}) ou {@code false} (insuffisant).
     */
    public boolean tryConsume(UUID id, double amount) {
        double cur = get(id);
        if (cur < amount) return false;
        current.put(id, cur - amount);
        lastConsume.put(id, System.currentTimeMillis());
        return true;
    }

    /**
     * Draine {@code amount} de stamina <b>sans échouer</b> : borne à 0 (contrairement
     * à {@link #tryConsume}). Utilisé pour la garde (M2) où un coup bloqué peut vider
     * la réserve et déclencher un guard break. Gèle la régén pendant
     * {@link #REGEN_DELAY_MS}. Retourne la stamina restante.
     */
    public double drain(UUID id, double amount) {
        double next = Math.max(0.0, get(id) - amount);
        current.put(id, next);
        lastConsume.put(id, System.currentTimeMillis());
        return next;
    }

    /** Régénère les joueurs suivis hors délai post-coup. */
    public void tickRegen(double dtSeconds) {
        long now = System.currentTimeMillis();
        double add = REGEN_PER_SECOND * dtSeconds;
        for (Map.Entry<UUID, Double> e : current.entrySet()) {
            Long last = lastConsume.get(e.getKey());
            if (last != null && now - last < REGEN_DELAY_MS) continue; // délai actif
            if (e.getValue() >= MAX) continue;
            e.setValue(Math.min(MAX, e.getValue() + add));
        }
    }

    /** Remet à plein (respawn, switch de perso, sortie de KO). */
    public void reset(UUID id) {
        current.put(id, MAX);
        lastConsume.remove(id);
    }

    /** Oublie le joueur (déconnexion). */
    public void clear(UUID id) {
        current.remove(id);
        lastConsume.remove(id);
    }
}
