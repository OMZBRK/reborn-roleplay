package fr.reborn.hud.runtime;

/**
 * Dernier snapshot LIVE des vitals RP du joueur local, poussé par ShinobiCore sur
 * le canal {@code reborn:vitals} (~5×/s) — voir {@link VitalsPayload}. Lu par
 * {@link VitalsHud} en priorité sur le bloc {@code self} du tablist (qui, lui, ne
 * se rafraîchit que toutes les 2 s). {@code null} tant que rien n'est reçu (hors
 * serveur / sans perso actif) → le HUD retombe sur le tablist puis vanilla.
 */
public final class VitalsFeed {

    private VitalsFeed() {}

    /** Vie/chakra RP courants (serveur-authoritative). */
    public record Vitals(int hp, int maxHp, int chakra, int maxChakra) {}

    private static volatile Vitals current = null;

    public static Vitals get() { return current; }

    public static void update(int hp, int maxHp, int chakra, int maxChakra) {
        current = new Vitals(hp, Math.max(1, maxHp), chakra, Math.max(1, maxChakra));
    }

    public static void clear() { current = null; }
}
