package fr.reborn.guardian.auth;

import com.google.gson.Gson;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Verifie un play-token emis par l'API Reborn (cf
 * {@code apps/api/src/play/play.service.ts}).
 *
 * <p>Format :
 * <pre>
 *   <base64url(JSON payload)>.<base64url(HMAC-SHA256(cle, payload))>
 * </pre>
 *
 * <p>Cle : le secret brut pour un jeton {@code play} (ou sans {@code scope}) ; pour un jeton
 * {@code douane}, une cle derivee {@code HMAC-SHA256(secret, "reborn-douane-v1")}. La cle qui a
 * signe fixe la portee : un payload {@code douane} signe avec le secret brut, ou un autre payload
 * signe avec la cle douane, est refuse ({@link Status#BAD_SIGNATURE}). Un Guardian 0.1.0, qui
 * ignore {@code scope}, voit donc tout jeton douane comme une signature invalide plutot que comme
 * un acces normal (refus par defaut, {@code CANDIDATURE_ET_DOUANE.md} §3.12).
 *
 * Le format n'est pas un JWT complet (pas de header) pour minimiser le code
 * cote plugin : pas de dependance JWT, juste HMAC + Gson pour le payload.
 *
 * <p>Toutes les operations sont {@link MessageDigest#isEqual constant-time}
 * pour eviter les timing attacks sur la signature.
 *
 * <p>Ce verifier ne controle que la signature et l'expiration. L'UUID du
 * joueur, la portee ({@code scope}) et le serveur vise ({@code aud}) sont
 * controles par {@link AttestationPolicy}, qui connait le joueur et la config.
 */
public final class PlayTokenVerifier {

    private static final Gson GSON = new Gson();
    private static final Base64.Decoder B64_URL = Base64.getUrlDecoder();
    private static final String HMAC_ALGO = "HmacSHA256";

    /** Longueur minimale du secret, identique a celle exigee par l'API. */
    public static final int MIN_SECRET_LENGTH = 32;

    /**
     * Etiquette de derivation de la cle des jetons douane. Identique a {@code DOUANE_KEY_LABEL}
     * dans {@code apps/api/src/play/play.service.ts}.
     */
    public static final String DOUANE_KEY_LABEL = "reborn-douane-v1";

    private final byte[] secret;
    /** Cle des jetons douane, derivee du secret. */
    private final byte[] douaneKey;
    private final Clock clock;

    public PlayTokenVerifier(String secret) {
        this(secret, Clock.systemUTC());
    }

    /** Horloge injectable pour tester l'expiration sans attendre. */
    public PlayTokenVerifier(String secret, Clock clock) {
        if (!isUsableSecret(secret)) {
            throw new IllegalArgumentException(
                "secret du play-token absent ou trop court (" + MIN_SECRET_LENGTH + " caractères minimum)"
            );
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        try {
            this.douaneKey = hmac(this.secret, DOUANE_KEY_LABEL);
        } catch (Exception e) {
            // HmacSHA256 fait partie des algorithmes exiges de toute JVM : ne doit pas arriver.
            throw new IllegalStateException("HmacSHA256 indisponible", e);
        }
        this.clock = clock;
    }

    /** Vrai si le secret peut servir a verifier les jetons (non nul, 32 caracteres ou plus). */
    public static boolean isUsableSecret(String secret) {
        return secret != null && secret.length() >= MIN_SECRET_LENGTH;
    }

    /** Issue de la verification, pour journaliser la vraie cause cote serveur. */
    public enum Status { VALID, MALFORMED, BAD_SIGNATURE, EXPIRED }

    /** Resultat de {@link #check}. {@code payload} n'est renseigne que si {@code status == VALID}. */
    public record Result(Status status, TokenPayload payload) {
        static Result of(Status status) {
            return new Result(status, null);
        }

        public boolean valid() {
            return status == Status.VALID;
        }
    }

    /**
     * Verifie la signature HMAC (avec les deux cles), l'accord entre la cle et la
     * portee, puis l'expiration. La signature est toujours verifiee avant de lire
     * le contenu : rien du payload n'est interprete tant qu'il n'est pas prouve
     * emis par l'API.
     */
    public Result check(String token) {
        if (token == null) return Result.of(Status.MALFORMED);
        int dot = token.indexOf('.');
        if (dot <= 0 || dot >= token.length() - 1) return Result.of(Status.MALFORMED);

        String payloadB64 = token.substring(0, dot);
        String signatureB64 = token.substring(dot + 1);

        byte[] expectedPlay;
        byte[] expectedDouane;
        try {
            expectedPlay = hmac(secret, payloadB64);
            expectedDouane = hmac(douaneKey, payloadB64);
        } catch (Exception e) {
            return Result.of(Status.MALFORMED);
        }

        byte[] providedSig;
        try {
            providedSig = B64_URL.decode(signatureB64);
        } catch (IllegalArgumentException e) {
            return Result.of(Status.MALFORMED);
        }
        // MessageDigest.isEqual est documente constant-time depuis JDK 6u17.
        // Les deux comparaisons sont toujours faites.
        boolean signedPlay = MessageDigest.isEqual(expectedPlay, providedSig);
        boolean signedDouane = MessageDigest.isEqual(expectedDouane, providedSig);
        if (!signedPlay && !signedDouane) {
            return Result.of(Status.BAD_SIGNATURE);
        }

        TokenPayload payload;
        try {
            byte[] payloadJson = B64_URL.decode(payloadB64);
            payload = GSON.fromJson(new String(payloadJson, StandardCharsets.UTF_8), TokenPayload.class);
        } catch (RuntimeException e) {
            // IllegalArgumentException (base64), JsonParseException et derivees (JSON, type).
            return Result.of(Status.MALFORMED);
        }
        if (payload == null) return Result.of(Status.MALFORMED);
        // La cle qui a signe fixe la portee (comparaison exacte, comme Scope.fromClaim).
        if (signedDouane != isDouaneClaim(payload.scope())) return Result.of(Status.BAD_SIGNATURE);
        if (payload.exp() == 0L) return Result.of(Status.MALFORMED);
        if (payload.exp() * 1000L < clock.millis()) return Result.of(Status.EXPIRED);
        return new Result(Status.VALID, payload);
    }

    /**
     * Verifie la signature HMAC + l'expiration. Renvoie le payload decode
     * si tout est OK, sinon {@link Optional#empty()}.
     *
     * <p>NB : ne verifie ni l'UUID, ni la portee, ni le serveur vise —
     * c'est {@link AttestationPolicy} qui s'en charge.
     */
    public Optional<TokenPayload> verify(String token) {
        Result r = check(token);
        return r.valid() ? Optional.of(r.payload()) : Optional.empty();
    }

    /**
     * Test utility : signe un payload avec ce verifier, avec la cle de sa portee
     * (cle douane pour {@code scope = "douane"}, secret brut sinon), comme l'API.
     * Utilise par les tests unitaires pour generer un token valide sans dependre
     * de l'API. Les claims {@code scope} et {@code aud} nulles ne sont pas ecrites
     * (Gson omet les null), comme un jeton d'avant le 25/10.
     */
    public String sign(TokenPayload payload) {
        try {
            String payloadJson = GSON.toJson(payload);
            String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            byte[] key = isDouaneClaim(payload.scope()) ? douaneKey : secret;
            String sigB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(key, payloadB64));
            return payloadB64 + "." + sigB64;
        } catch (Exception e) {
            throw new IllegalStateException("signing failed", e);
        }
    }

    private static boolean isDouaneClaim(String scope) {
        return Scope.DOUANE.claim().equals(scope);
    }

    private static byte[] hmac(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGO);
        mac.init(new SecretKeySpec(key, HMAC_ALGO));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Contenu du jeton. {@code scope} et {@code aud} sont nulles sur un jeton
     * emis avant le 25/10 (ou par une API sans identifiant de serveur pour
     * {@code aud}).
     */
    public record TokenPayload(
        String sub,
        String mcUuid,
        String mcUsername,
        long iat,
        long exp,
        String scope,
        String aud
    ) {
        /** Jeton d'avant le 25/10 : ni portee ni serveur vise. */
        public TokenPayload(String sub, String mcUuid, String mcUsername, long iat, long exp) {
            this(sub, mcUuid, mcUsername, iat, exp, null, null);
        }

        /**
         * UUID Mojang stocke dans le payload ('xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx').
         * Retourne null si malforme — le caller doit alors rejeter le token.
         */
        public UUID asUuid() {
            if (mcUuid == null) return null;
            try {
                return UUID.fromString(mcUuid);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
