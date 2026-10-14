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

    public static void clear() { current = null; satiete = null; }

    /**
     * Satiété (nourriture, 14/10) : 0..1, à venir (repas en cours) 0..1, pause de la régénération (s), drapeaux
     * ({@link #F_REGEN}…). {@code null} : serveur sans satiété → la barre or retombe sur la faim vanilla.
     */
    public record Satiete(float valeur, float aVenir, int pauseRegen, int drapeaux, long recuA) {
        public boolean regenere() { return (drapeaux & F_REGEN) != 0; }
        public boolean affame() { return (drapeaux & F_AFFAME) != 0; }
        public boolean faim() { return (drapeaux & F_FAIM) != 0; }
        /** Secondes de pause restantes, décomptées localement depuis la réception. */
        public int pauseRestante() {
            return Math.max(0, pauseRegen - (int) ((System.currentTimeMillis() - recuA) / 1000));
        }
    }

    public static final int F_REGEN = 1, F_AFFAME = 2, F_FAIM = 4, F_ATA = 8, F_GELE = 16;

    private static volatile Satiete satiete = null;

    public static Satiete satiete() { return satiete; }

    public static void satiete(int[] ext) {
        if (ext == null || ext.length < 4) return;
        satiete = new Satiete(Math.max(0, Math.min(1000, ext[0])) / 1000f, Math.max(0, ext[1]) / 1000f, ext[2], ext[3],
            System.currentTimeMillis());
    }

    /** Banc d'essai : satiété simulée. */
    public static void debugSatiete(float v, float aVenir, int pause, int drapeaux) {
        satiete = new Satiete(v, aVenir, pause, drapeaux, System.currentTimeMillis());
    }
}
