package fr.reborn.guardian.auth;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Verifie un play-token emis par l'API Reborn (cf
 * {@code apps/api/src/play/play.service.ts}).
 *
 * <p>Format :
 * <pre>
 *   <base64url(JSON payload)>.<base64url(HMAC-SHA256(secret, payload))>
 * </pre>
 *
 * Le format n'est pas un JWT complet (pas de header) pour minimiser le code
 * cote plugin : pas de dependance JWT, juste HMAC + Gson pour le payload.
 *
 * <p>Toutes les operations sont {@link MessageDigest#isEqual constant-time}
 * pour eviter les timing attacks sur la signature.
 */
public final class PlayTokenVerifier {

    private static final Gson GSON = new Gson();
    private static final Base64.Decoder B64_URL = Base64.getUrlDecoder();
    private static final String HMAC_ALGO = "HmacSHA256";

    private final byte[] secret;

    public PlayTokenVerifier(String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException(
                "REBORN_PLAY_TOKEN_SECRET manquant ou < 32 chars"
            );
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Verifie la signature HMAC + l'expiration. Renvoie le payload decode
     * si tout est OK, sinon {@link Optional#empty()}.
     *
     * <p>NB : ne verifie pas que le mcUuid du payload matche celui du
     * joueur connecte — c'est au caller (qui connait le joueur Bukkit)
     * de faire ce check.
     */
    public Optional<TokenPayload> verify(String token) {
        if (token == null) return Optional.empty();
        int dot = token.indexOf('.');
        if (dot <= 0 || dot >= token.length() - 1) return Optional.empty();

        String payloadB64 = token.substring(0, dot);
        String signatureB64 = token.substring(dot + 1);

        byte[] expectedSig;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret, HMAC_ALGO));
            expectedSig = mac.doFinal(payloadB64.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return Optional.empty();
        }

        byte[] providedSig;
        try {
            providedSig = B64_URL.decode(signatureB64);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        // MessageDigest.isEqual est documente constant-time depuis JDK 6u17.
        if (!MessageDigest.isEqual(expectedSig, providedSig)) {
            return Optional.empty();
        }

        TokenPayload payload;
        try {
            byte[] payloadJson = B64_URL.decode(payloadB64);
            payload = GSON.fromJson(new String(payloadJson, StandardCharsets.UTF_8), TokenPayload.class);
        } catch (IllegalArgumentException | JsonSyntaxException e) {
            return Optional.empty();
        }
        if (payload == null || payload.exp() == 0L) return Optional.empty();
        if (payload.exp() * 1000L < System.currentTimeMillis()) return Optional.empty();
        return Optional.of(payload);
    }

    /**
     * Test utility : signe un payload avec ce verifier. Utilise par les
     * tests unitaires pour generer un token valide sans dependre de l'API.
     */
    public String sign(TokenPayload payload) {
        try {
            String payloadJson = GSON.toJson(payload);
            String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret, HMAC_ALGO));
            byte[] sig = mac.doFinal(payloadB64.getBytes(StandardCharsets.UTF_8));
            String sigB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
            return payloadB64 + "." + sigB64;
        } catch (Exception e) {
            throw new IllegalStateException("signing failed", e);
        }
    }

    public record TokenPayload(
        String sub,
        String mcUuid,
        String mcUsername,
        long iat,
        long exp
    ) {
        /**
         * UUID Mojang stocke dans le payload ('xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx').
         * Retourne null si malforme — le caller doit alors rejeter le token.
         */
        public UUID asUuid() {
            try {
                return UUID.fromString(mcUuid);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
