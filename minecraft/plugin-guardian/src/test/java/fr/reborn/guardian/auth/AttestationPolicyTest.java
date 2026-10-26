package fr.reborn.guardian.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AttestationPolicyTest {

    private static final String SECRET = "test-secret-with-32-or-more-chars-ok!";
    private static final long NOW = 1_800_000_000L;
    private static final Clock FIXED = Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC);
    private static final UUID PLAYER = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000042");

    private final PlayTokenVerifier verifier = new PlayTokenVerifier(SECRET, FIXED);

    private String token(UUID uuid, String scope, String aud) {
        return verifier.sign(new PlayTokenVerifier.TokenPayload(
            "user-1", uuid.toString(), "Notch", NOW, NOW + 300, scope, aud
        ));
    }

    private AttestationPolicy policy(String serverId, boolean acceptWithoutAud, boolean acceptDouane) {
        return new AttestationPolicy(verifier, new AttestationPolicy.Settings(serverId, acceptWithoutAud, acceptDouane));
    }

    private static Scope accepted(AttestationPolicy.Verdict verdict) {
        AttestationPolicy.Accepted a = assertInstanceOf(AttestationPolicy.Accepted.class, verdict, verdict.toString());
        return a.scope();
    }

    private static AttestationPolicy.Reason refused(AttestationPolicy.Verdict verdict) {
        AttestationPolicy.Refused r = assertInstanceOf(AttestationPolicy.Refused.class, verdict, verdict.toString());
        return r.reason();
    }

    // ── Signature, expiration, UUID ─────────────────────────────────────

    @Test
    void emptyTokenIsRefused() {
        assertEquals(AttestationPolicy.Reason.EMPTY, refused(policy("prod", true, false).evaluate("  ", PLAYER)));
        assertEquals(AttestationPolicy.Reason.EMPTY, refused(policy("prod", true, false).evaluate(null, PLAYER)));
    }

    @Test
    void foreignSignatureIsRefused() {
        PlayTokenVerifier evil = new PlayTokenVerifier("another-secret-with-32-or-more-chars!", FIXED);
        String forged = evil.sign(new PlayTokenVerifier.TokenPayload(
            "user-1", PLAYER.toString(), "Notch", NOW, NOW + 300, "play", "prod"
        ));
        assertEquals(AttestationPolicy.Reason.BAD_SIGNATURE, refused(policy("prod", true, false).evaluate(forged, PLAYER)));
    }

    @Test
    void expiredTokenIsRefused() {
        String old = verifier.sign(new PlayTokenVerifier.TokenPayload(
            "user-1", PLAYER.toString(), "Notch", NOW - 600, NOW - 300, "play", "prod"
        ));
        assertEquals(AttestationPolicy.Reason.EXPIRED, refused(policy("prod", true, false).evaluate(old, PLAYER)));
    }

    @Test
    void tokenOfAnotherAccountIsRefused() {
        assertEquals(AttestationPolicy.Reason.UUID_MISMATCH,
            refused(policy("prod", true, false).evaluate(token(OTHER, "play", "prod"), PLAYER)));
    }

    @Test
    void malformedUuidIsRefused() {
        String bad = verifier.sign(new PlayTokenVerifier.TokenPayload(
            "user-1", "pas-un-uuid", "Notch", NOW, NOW + 300, "play", "prod"
        ));
        assertEquals(AttestationPolicy.Reason.UUID_MISMATCH, refused(policy("prod", true, false).evaluate(bad, PLAYER)));
    }

    // ── Portée ──────────────────────────────────────────────────────────

    @Test
    void playScopeIsAccepted() {
        assertEquals(Scope.PLAY, accepted(policy("prod", false, false).evaluate(token(PLAYER, "play", "prod"), PLAYER)));
    }

    @Test
    void missingScopeMeansPlay() {
        assertEquals(Scope.PLAY, accepted(policy("prod", false, false).evaluate(token(PLAYER, null, "prod"), PLAYER)));
    }

    @Test
    void unknownScopeIsRefused() {
        AttestationPolicy p = policy("prod", true, true);
        assertEquals(AttestationPolicy.Reason.UNKNOWN_SCOPE, refused(p.evaluate(token(PLAYER, "admin", "prod"), PLAYER)));
        assertEquals(AttestationPolicy.Reason.UNKNOWN_SCOPE, refused(p.evaluate(token(PLAYER, "PLAY", "prod"), PLAYER)),
            "comparaison exacte, casse comprise");
    }

    @Test
    void douaneScopeRefusedUntilServerAcceptsIt() {
        assertEquals(AttestationPolicy.Reason.DOUANE_CLOSED,
            refused(policy("prod", true, false).evaluate(token(PLAYER, "douane", "prod"), PLAYER)));
    }

    @Test
    void douaneScopeAcceptedForThisServer() {
        assertEquals(Scope.DOUANE, accepted(policy("dev", true, true).evaluate(token(PLAYER, "douane", "dev"), PLAYER)));
    }

    @Test
    void douaneScopeNeedsServerId() {
        assertEquals(AttestationPolicy.Reason.DOUANE_SERVER_ID_MISSING,
            refused(policy("", true, true).evaluate(token(PLAYER, "douane", "dev"), PLAYER)));
    }

    @Test
    void douaneScopeWithoutAudIsAlwaysRefused() {
        assertEquals(AttestationPolicy.Reason.AUD_REQUIRED,
            refused(policy("dev", true, true).evaluate(token(PLAYER, "douane", null), PLAYER)));
    }

    // ── Serveur visé (aud) ──────────────────────────────────────────────

    @Test
    void tokenForAnotherServerIsRefused() {
        assertEquals(AttestationPolicy.Reason.WRONG_AUD,
            refused(policy("prod", true, false).evaluate(token(PLAYER, "play", "dev"), PLAYER)));
        assertEquals(AttestationPolicy.Reason.WRONG_AUD,
            refused(policy("prod", true, false).evaluate(token(PLAYER, "play", "Prod"), PLAYER)),
            "comparaison exacte, casse comprise");
        assertEquals(AttestationPolicy.Reason.WRONG_AUD,
            refused(policy("dev", true, true).evaluate(token(PLAYER, "douane", "prod"), PLAYER)));
    }

    @Test
    void tokenWithoutAudAcceptedDuringTransition() {
        assertEquals(Scope.PLAY, accepted(policy("prod", true, false).evaluate(token(PLAYER, null, null), PLAYER)));
    }

    @Test
    void tokenWithoutAudRefusedAfterTransition() {
        assertEquals(AttestationPolicy.Reason.AUD_REQUIRED,
            refused(policy("prod", false, false).evaluate(token(PLAYER, "play", null), PLAYER)));
    }

    @Test
    void emptyServerIdDoesNotCheckAudForPlay() {
        AttestationPolicy p = policy("  ", false, false);
        assertFalse(p.settings().audChecked());
        assertEquals(Scope.PLAY, accepted(p.evaluate(token(PLAYER, "play", "prod"), PLAYER)));
        assertEquals(Scope.PLAY, accepted(p.evaluate(token(PLAYER, null, null), PLAYER)));
    }

    @Test
    void serverIdIsTrimmed() {
        assertEquals(Scope.PLAY, accepted(policy(" prod ", false, false).evaluate(token(PLAYER, "play", "prod"), PLAYER)));
    }

    @Test
    void playerMessagesAreFrenchAndPrefixed() {
        for (AttestationPolicy.Reason reason : AttestationPolicy.Reason.values()) {
            assertTrue(reason.playerMessage().startsWith("Reborn : "), reason.name());
            assertFalse(reason.playerMessage().contains("aud"), "pas de détail interne : " + reason.name());
            assertFalse(reason.logLabel().isBlank(), reason.name());
        }
    }
}
