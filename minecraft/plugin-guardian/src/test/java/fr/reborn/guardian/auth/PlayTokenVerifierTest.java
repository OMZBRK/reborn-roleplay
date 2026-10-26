package fr.reborn.guardian.auth;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PlayTokenVerifierTest {

    private static final String SECRET = "test-secret-with-32-or-more-chars-ok!";
    private static final long NOW = 1_800_000_000L;
    private static final Clock FIXED = Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC);

    /** Payload d'un jeton douane, octet pour octet celui du vecteur partage avec play.service.spec.ts. */
    private static final String DOUANE_JSON = "{\"sub\":\"user-1\",\"mcUuid\":\"069a79f4-44e9-4726-a5be-fca90e38aaf5\","
        + "\"mcUsername\":\"Notch\",\"iat\":" + NOW + ",\"exp\":" + (NOW + 300)
        + ",\"scope\":\"douane\",\"aud\":\"dev\"}";
    /** Signature attendue de DOUANE_JSON avec la cle douane derivee de SECRET (calculee cote API). */
    private static final String DOUANE_SIG_FROM_API = "QIT09w5-HKMdiOMPB7ZR5WFvWqCAsNp8m6CQocI668U";

    private static byte[] hmac(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    /** Cle des jetons douane : HMAC-SHA256(secret, "reborn-douane-v1"), comme l'API. */
    static byte[] douaneKey(String secret) throws Exception {
        return hmac(secret.getBytes(StandardCharsets.UTF_8), PlayTokenVerifier.DOUANE_KEY_LABEL);
    }

    /** Signe un JSON brut comme le fait l'API pour un jeton play (HMAC sur le base64url du payload). */
    static String signRawJson(String secret, String json) throws Exception {
        return signRawJson(secret.getBytes(StandardCharsets.UTF_8), json);
    }

    /** Signe un JSON brut avec une cle quelconque (secret brut ou cle douane). */
    static String signRawJson(byte[] key, String json) throws Exception {
        String payloadB64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.getBytes(StandardCharsets.UTF_8));
        byte[] sig = hmac(key, payloadB64);
        return payloadB64 + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
    }

    @Test
    void verifyAcceptsTokenJustSigned() {
        PlayTokenVerifier verifier = new PlayTokenVerifier(SECRET);
        long now = System.currentTimeMillis() / 1000;
        String token = verifier.sign(new PlayTokenVerifier.TokenPayload(
            "user-id",
            "00000000-0000-0000-0000-000000000001",
            "OMZ",
            now,
            now + 300
        ));
        Optional<PlayTokenVerifier.TokenPayload> verified = verifier.verify(token);
        assertTrue(verified.isPresent(), "fresh token must verify");
        assertEquals("OMZ", verified.get().mcUsername());
    }

    @Test
    void verifyRejectsExpiredToken() {
        PlayTokenVerifier verifier = new PlayTokenVerifier(SECRET);
        long pastSec = System.currentTimeMillis() / 1000 - 600;
        String token = verifier.sign(new PlayTokenVerifier.TokenPayload(
            "user-id", "00000000-0000-0000-0000-000000000002", "OMZ", pastSec - 60, pastSec
        ));
        assertTrue(verifier.verify(token).isEmpty(), "expired token must be rejected");
    }

    @Test
    void checkReportsExpiryAgainstInjectedClock() {
        PlayTokenVerifier verifier = new PlayTokenVerifier(SECRET, FIXED);
        String justValid = verifier.sign(new PlayTokenVerifier.TokenPayload(
            "u", "00000000-0000-0000-0000-000000000005", "OMZ", NOW - 300, NOW
        ));
        String expired = verifier.sign(new PlayTokenVerifier.TokenPayload(
            "u", "00000000-0000-0000-0000-000000000005", "OMZ", NOW - 301, NOW - 1
        ));
        assertEquals(PlayTokenVerifier.Status.VALID, verifier.check(justValid).status(),
            "exp == maintenant : encore valide pendant la seconde en cours");
        assertEquals(PlayTokenVerifier.Status.EXPIRED, verifier.check(expired).status());
    }

    @Test
    void checkRejectsTokenWithoutExp() throws Exception {
        PlayTokenVerifier verifier = new PlayTokenVerifier(SECRET, FIXED);
        String token = signRawJson(SECRET,
            "{\"sub\":\"u\",\"mcUuid\":\"00000000-0000-0000-0000-000000000006\",\"mcUsername\":\"OMZ\",\"iat\":1}");
        assertEquals(PlayTokenVerifier.Status.MALFORMED, verifier.check(token).status());
    }

    @Test
    void verifyRejectsTokenSignedByDifferentSecret() {
        PlayTokenVerifier good = new PlayTokenVerifier(SECRET);
        PlayTokenVerifier evil = new PlayTokenVerifier("another-secret-with-32-or-more-chars!");
        long now = System.currentTimeMillis() / 1000;
        String token = evil.sign(new PlayTokenVerifier.TokenPayload(
            "u", "00000000-0000-0000-0000-000000000003", "OMZ", now, now + 60
        ));
        assertTrue(good.verify(token).isEmpty(), "token signed by another secret must not verify");
        assertEquals(PlayTokenVerifier.Status.BAD_SIGNATURE, good.check(token).status());
    }

    @Test
    void verifyRejectsMalformedTokens() {
        PlayTokenVerifier verifier = new PlayTokenVerifier(SECRET);
        assertTrue(verifier.verify(null).isEmpty());
        assertTrue(verifier.verify("").isEmpty());
        assertTrue(verifier.verify(".").isEmpty());
        assertTrue(verifier.verify("noDot").isEmpty());
        assertTrue(verifier.verify(".trailing").isEmpty());
        assertTrue(verifier.verify("leading.").isEmpty());
        assertTrue(verifier.verify("a.b").isEmpty(), "garbage b64 must not crash");
    }

    @Test
    void verifyRejectsSignedButNonObjectPayload() throws Exception {
        PlayTokenVerifier verifier = new PlayTokenVerifier(SECRET, FIXED);
        assertEquals(PlayTokenVerifier.Status.MALFORMED, verifier.check(signRawJson(SECRET, "[1,2]")).status());
        assertEquals(PlayTokenVerifier.Status.MALFORMED, verifier.check(signRawJson(SECRET, "null")).status());
        assertEquals(PlayTokenVerifier.Status.MALFORMED, verifier.check(signRawJson(SECRET, "{pas du json")).status());
    }

    @Test
    void verifyRejectsTamperedPayload() {
        PlayTokenVerifier verifier = new PlayTokenVerifier(SECRET);
        long now = System.currentTimeMillis() / 1000;
        String token = verifier.sign(new PlayTokenVerifier.TokenPayload(
            "user-id", "00000000-0000-0000-0000-000000000004", "OMZ", now, now + 60
        ));
        int dot = token.indexOf('.');
        // On bidouille le payload mais on garde la signature originale.
        String tampered = "X" + token.substring(1, dot) + token.substring(dot);
        assertTrue(verifier.verify(tampered).isEmpty(), "tampered payload must fail HMAC");
    }

    @Test
    void readsScopeAndAudEmittedByTheApi() throws Exception {
        // Ordre des champs et format exacts de play.service.ts (JSON.stringify), signature calculee
        // par l'API : meme derivation de la cle douane des deux cotes.
        PlayTokenVerifier verifier = new PlayTokenVerifier(SECRET, FIXED);
        String token = signRawJson(douaneKey(SECRET), DOUANE_JSON);
        assertEquals(DOUANE_SIG_FROM_API, token.substring(token.indexOf('.') + 1));
        PlayTokenVerifier.Result result = verifier.check(token);
        assertTrue(result.valid(), result.toString());
        assertEquals("douane", result.payload().scope());
        assertEquals("dev", result.payload().aud());
    }

    @Test
    void douaneTokenSignedWithRawSecretIsRefused() throws Exception {
        // Un payload douane doit etre signe par la cle douane : le secret brut ne suffit pas.
        PlayTokenVerifier verifier = new PlayTokenVerifier(SECRET, FIXED);
        assertEquals(PlayTokenVerifier.Status.BAD_SIGNATURE,
            verifier.check(signRawJson(SECRET, DOUANE_JSON)).status());
    }

    @Test
    void playTokenSignedWithDouaneKeyIsRefused() throws Exception {
        PlayTokenVerifier verifier = new PlayTokenVerifier(SECRET, FIXED);
        String base = "{\"sub\":\"user-1\",\"mcUuid\":\"069a79f4-44e9-4726-a5be-fca90e38aaf5\","
            + "\"mcUsername\":\"Notch\",\"iat\":" + NOW + ",\"exp\":" + (NOW + 300);
        assertEquals(PlayTokenVerifier.Status.BAD_SIGNATURE,
            verifier.check(signRawJson(douaneKey(SECRET), base + ",\"scope\":\"play\",\"aud\":\"dev\"}")).status());
        assertEquals(PlayTokenVerifier.Status.BAD_SIGNATURE,
            verifier.check(signRawJson(douaneKey(SECRET), base + "}")).status(),
            "jeton sans portee signe avec la cle douane");
        assertEquals(PlayTokenVerifier.Status.VALID,
            verifier.check(signRawJson(SECRET, base + ",\"scope\":\"play\",\"aud\":\"dev\"}")).status());
    }

    @Test
    void legacyVerifierSeesDouaneTokenAsBadSignature() throws Exception {
        // Guardian 0.1.0 verifiait avec le secret brut seul : HMAC(secret, payload) != signature douane.
        String token = signRawJson(douaneKey(SECRET), DOUANE_JSON);
        String payloadB64 = token.substring(0, token.indexOf('.'));
        String legacySig = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(hmac(SECRET.getBytes(StandardCharsets.UTF_8), payloadB64));
        assertNotEquals(legacySig, token.substring(token.indexOf('.') + 1));
    }

    @Test
    void signUsesTheKeyOfTheScope() {
        PlayTokenVerifier verifier = new PlayTokenVerifier(SECRET, FIXED);
        String douane = verifier.sign(new PlayTokenVerifier.TokenPayload(
            "u", "00000000-0000-0000-0000-000000000008", "OMZ", NOW, NOW + 60, "douane", "dev"
        ));
        assertEquals(PlayTokenVerifier.Status.VALID, verifier.check(douane).status());
        assertEquals("douane", verifier.check(douane).payload().scope());
    }

    @Test
    void legacyTokenHasNoScopeNorAud() throws Exception {
        PlayTokenVerifier verifier = new PlayTokenVerifier(SECRET, FIXED);
        String token = signRawJson(SECRET, "{\"sub\":\"user-1\",\"mcUuid\":\"069a79f4-44e9-4726-a5be-fca90e38aaf5\","
            + "\"mcUsername\":\"Notch\",\"iat\":" + NOW + ",\"exp\":" + (NOW + 300) + "}");
        PlayTokenVerifier.Result result = verifier.check(token);
        assertTrue(result.valid());
        assertNull(result.payload().scope());
        assertNull(result.payload().aud());
    }

    @Test
    void signOmitsNullClaims() {
        PlayTokenVerifier verifier = new PlayTokenVerifier(SECRET, FIXED);
        String token = verifier.sign(new PlayTokenVerifier.TokenPayload(
            "u", "00000000-0000-0000-0000-000000000007", "OMZ", NOW, NOW + 60
        ));
        String json = new String(Base64.getUrlDecoder().decode(token.substring(0, token.indexOf('.'))),
            StandardCharsets.UTF_8);
        assertFalse(json.contains("scope"), json);
        assertFalse(json.contains("aud"), json);
    }

    @Test
    void constructorRejectsShortSecret() {
        assertThrows(IllegalArgumentException.class, () -> new PlayTokenVerifier("short"));
        assertThrows(IllegalArgumentException.class, () -> new PlayTokenVerifier(null));
        assertThrows(IllegalArgumentException.class, () -> new PlayTokenVerifier("x".repeat(31)));
        assertDoesNotThrow(() -> new PlayTokenVerifier("x".repeat(32)));
        assertFalse(PlayTokenVerifier.isUsableSecret(""));
        assertTrue(PlayTokenVerifier.isUsableSecret(SECRET));
    }

    @Test
    void tokenPayloadAsUuidParsesCorrectly() {
        PlayTokenVerifier.TokenPayload payload = new PlayTokenVerifier.TokenPayload(
            "u", "069a79f4-44e9-4726-a5be-fca90e38aaf5", "Notch", 0, 0
        );
        assertNotNull(payload.asUuid());
        assertEquals("069a79f4-44e9-4726-a5be-fca90e38aaf5", payload.asUuid().toString());
    }

    @Test
    void tokenPayloadAsUuidReturnsNullOnGarbage() {
        PlayTokenVerifier.TokenPayload payload = new PlayTokenVerifier.TokenPayload(
            "u", "not-a-uuid", "x", 0, 0
        );
        assertNull(payload.asUuid());
        assertNull(new PlayTokenVerifier.TokenPayload("u", null, "x", 0, 0).asUuid());
    }
}
