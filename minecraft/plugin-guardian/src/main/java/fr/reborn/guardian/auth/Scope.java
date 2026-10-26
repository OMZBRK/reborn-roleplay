package fr.reborn.guardian.auth;

import java.util.Optional;

/**
 * Portée d'un play-token (claim {@code scope}, émise par l'API depuis le 25/10).
 *
 * <ul>
 *   <li>{@link #PLAY} : jeu normal (joueur whitelisté ou staff).</li>
 *   <li>{@link #DOUANE} : candidat à l'écrit accepté, admis seulement au hall de la douane
 *       (cf {@code docs/gameplay/CANDIDATURE_ET_DOUANE.md} §3.3).</li>
 * </ul>
 *
 * <p>Un jeton sans {@code scope} (émis avant le 25/10) vaut {@link #PLAY}. Toute autre valeur est
 * refusée : refus par défaut.
 */
public enum Scope {
    PLAY("play"),
    DOUANE("douane");

    private final String claim;

    Scope(String claim) {
        this.claim = claim;
    }

    /** Valeur écrite dans le jeton et dans la métadonnée {@code reborn.scope}. */
    public String claim() {
        return claim;
    }

    /**
     * Lit la claim {@code scope}. Absente ou vide : {@link #PLAY} (anciens jetons). Inconnue :
     * {@link Optional#empty()}, le jeton doit être refusé. Comparaison exacte, casse comprise.
     */
    public static Optional<Scope> fromClaim(String raw) {
        if (raw == null || raw.isBlank()) return Optional.of(PLAY);
        for (Scope s : values()) {
            if (s.claim.equals(raw)) return Optional.of(s);
        }
        return Optional.empty();
    }
}
