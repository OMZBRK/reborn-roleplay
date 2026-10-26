package fr.reborn.guardian.auth;

import java.util.Optional;
import java.util.UUID;

/**
 * Décide si une attestation reçue sur {@code reborn:auth} admet le joueur, et avec quelle portée.
 *
 * <p>Classe pure (aucune API Bukkit) pour être testée unitairement. Ordre des contrôles :
 * <ol>
 *   <li>signature, avec la clé de la portée (clé douane dérivée pour {@code douane}), et
 *       expiration ({@link PlayTokenVerifier}) ;</li>
 *   <li>UUID du jeton = UUID du joueur connecté ;</li>
 *   <li>portée connue ({@code scope} absente = {@code play}) ;</li>
 *   <li>portée {@code douane} acceptée par ce serveur ({@code accept-douane-tokens}) et serveur
 *       identifié ({@code server-id}) ;</li>
 *   <li>serveur visé ({@code aud}) = {@code server-id}.</li>
 * </ol>
 *
 * <p>Règles de {@code aud} (cf {@code docs/technique/PLUGINS_REFERENCE.md}, section Guardian) :
 * <ul>
 *   <li>{@code server-id} vide : {@code aud} n'est pas contrôlée pour {@code play} ; la douane est
 *       refusée, faute de pouvoir vérifier que le jeton vise ce serveur ;</li>
 *   <li>{@code server-id} renseigné : un jeton qui porte un autre {@code aud} est refusé ; un jeton
 *       sans {@code aud} n'est accepté qu'en portée {@code play} et seulement si
 *       {@code accept-tokens-without-aud} est vrai (période de transition) ;</li>
 *   <li>un jeton {@code douane} sans {@code aud} est toujours refusé : il n'existe aucun ancien jeton
 *       douane à tolérer.</li>
 * </ul>
 */
public final class AttestationPolicy {

    /** Préfixe des messages d'expulsion affichés au joueur. */
    private static final String PREFIX = "Reborn : ";
    private static final String RELANCE = "Relance le jeu depuis le launcher.";

    /** Réglages lus dans {@code config.yml}. */
    public record Settings(String serverId, boolean acceptTokensWithoutAud, boolean acceptDouaneTokens) {
        public Settings {
            serverId = serverId == null ? "" : serverId.trim();
        }

        /** Vrai si ce serveur a un identifiant : la claim {@code aud} est alors contrôlée. */
        public boolean audChecked() {
            return !serverId.isEmpty();
        }
    }

    /**
     * Motif d'un refus : un libellé pour le journal du serveur (détaillé) et un message pour le
     * joueur (utile, sans détail interne).
     */
    public enum Reason {
        EMPTY("jeton vide", "ton accès n'a pas été transmis. " + RELANCE),
        MALFORMED("jeton illisible", "ton accès n'est pas valide. " + RELANCE),
        BAD_SIGNATURE("signature invalide", "ton accès n'est pas valide. " + RELANCE),
        EXPIRED("jeton expiré", "ton accès a expiré. " + RELANCE),
        UUID_MISMATCH("jeton émis pour un autre compte Minecraft",
            "cet accès appartient à un autre compte. " + RELANCE),
        UNKNOWN_SCOPE("portée inconnue",
            "ce type d'accès n'est pas reconnu par ce serveur. Mets ton launcher à jour."),
        DOUANE_CLOSED("portée douane refusée (accept-douane-tokens: false)",
            "la douane n'est pas ouverte sur ce serveur."),
        DOUANE_SERVER_ID_MISSING("portée douane refusée : server-id vide dans la config",
            "la douane n'est pas ouverte sur ce serveur."),
        WRONG_AUD("jeton émis pour un autre serveur (aud différent de server-id)",
            "ton accès a été délivré pour un autre serveur. " + RELANCE),
        AUD_REQUIRED("jeton sans aud refusé",
            "ton accès ne désigne pas ce serveur. " + RELANCE + " Si ça continue, préviens le staff.");

        private final String logLabel;
        private final String playerMessage;

        Reason(String logLabel, String playerMessage) {
            this.logLabel = logLabel;
            this.playerMessage = playerMessage;
        }

        /** Libellé pour le journal du serveur. */
        public String logLabel() {
            return logLabel;
        }

        /** Message d'expulsion complet, affiché au joueur. */
        public String playerMessage() {
            return PREFIX + playerMessage;
        }
    }

    /** Verdict sur une attestation. */
    public sealed interface Verdict permits Accepted, Refused {}

    /** Attestation valide : le joueur est admis avec cette portée. */
    public record Accepted(Scope scope, PlayTokenVerifier.TokenPayload payload) implements Verdict {}

    /** Attestation refusée : le joueur doit être expulsé. */
    public record Refused(Reason reason) implements Verdict {}

    private final PlayTokenVerifier verifier;
    private final Settings settings;

    public AttestationPolicy(PlayTokenVerifier verifier, Settings settings) {
        this.verifier = verifier;
        this.settings = settings;
    }

    public Settings settings() {
        return settings;
    }

    /** Évalue le jeton reçu de {@code playerUuid}. Ne lève jamais d'exception. */
    public Verdict evaluate(String token, UUID playerUuid) {
        if (token == null || token.isBlank()) return new Refused(Reason.EMPTY);

        PlayTokenVerifier.Result result = verifier.check(token);
        switch (result.status()) {
            case MALFORMED -> { return new Refused(Reason.MALFORMED); }
            case BAD_SIGNATURE -> { return new Refused(Reason.BAD_SIGNATURE); }
            case EXPIRED -> { return new Refused(Reason.EXPIRED); }
            case VALID -> { /* suite des contrôles */ }
        }
        PlayTokenVerifier.TokenPayload payload = result.payload();

        UUID tokenUuid = payload.asUuid();
        if (tokenUuid == null || playerUuid == null || !tokenUuid.equals(playerUuid)) {
            return new Refused(Reason.UUID_MISMATCH);
        }

        Optional<Scope> parsedScope = Scope.fromClaim(payload.scope());
        if (parsedScope.isEmpty()) return new Refused(Reason.UNKNOWN_SCOPE);
        Scope scope = parsedScope.get();

        if (scope == Scope.DOUANE) {
            if (!settings.acceptDouaneTokens()) return new Refused(Reason.DOUANE_CLOSED);
            if (!settings.audChecked()) return new Refused(Reason.DOUANE_SERVER_ID_MISSING);
        }

        String aud = payload.aud();
        boolean hasAud = aud != null && !aud.isBlank();
        if (!hasAud) {
            if (scope == Scope.DOUANE) return new Refused(Reason.AUD_REQUIRED);
            if (settings.audChecked() && !settings.acceptTokensWithoutAud()) {
                return new Refused(Reason.AUD_REQUIRED);
            }
        } else if (settings.audChecked() && !settings.serverId().equals(aud)) {
            return new Refused(Reason.WRONG_AUD);
        }

        return new Accepted(scope, payload);
    }
}
