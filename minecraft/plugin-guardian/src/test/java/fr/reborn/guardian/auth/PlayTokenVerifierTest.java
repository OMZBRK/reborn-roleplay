package fr.reborn.guardian.auth;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PlayTokenVerifierTest {

    private static final String SECRET = "test-secret-with-32-or-more-chars-ok!";

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
    void verifyRejectsTokenSignedByDifferentSecret() {
        PlayTokenVerifier good = new PlayTokenVerifier(SECRET);
        PlayTokenVerifier evil = new PlayTokenVerifier("another-secret-with-32-or-more-chars!");
        long now = System.currentTimeMillis() / 1000;
        String token = evil.sign(new PlayTokenVerifier.TokenPayload(
            "u", "00000000-0000-0000-0000-000000000003", "OMZ", now, now + 60
        ));
        assertTrue(good.verify(token).isEmpty(), "token signed by another secret must not verify");
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
    void constructorRejectsShortSecret() {
        assertThrows(IllegalArgumentException.class, () -> new PlayTokenVerifier("short"));
        assertThrows(IllegalArgumentException.class, () -> new PlayTokenVerifier(null));
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
    }
}
