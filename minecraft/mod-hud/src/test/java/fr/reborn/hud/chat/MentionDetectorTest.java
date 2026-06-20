package fr.reborn.hud.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MentionDetectorTest {

    private static final String PLAYER = "Kazama_Rin";

    @Test
    void detectsAtMention() {
        assertTrue(MentionDetector.isMentioned("Salut @Kazama_Rin tu viens ?", PLAYER));
        assertTrue(MentionDetector.isMentioned("Hey @kazama_rin ok ?", PLAYER), "case insensitive");
    }

    @Test
    void detectsWordBoundaryMention() {
        assertTrue(MentionDetector.isMentioned("Kazama_Rin tu viens ?", PLAYER));
        assertTrue(MentionDetector.isMentioned("Hey kazama_rin", PLAYER));
        assertTrue(MentionDetector.isMentioned("dit a Kazama_Rin de venir", PLAYER));
    }

    @Test
    void ignoresWordContainingPseudoAsSubstring() {
        // "RinSomething" ne devrait pas matcher "Rin" (mais on cherche Kazama_Rin entier)
        // Plus interessant : "KazamaRinSomething" ne match pas "Kazama_Rin"
        assertFalse(MentionDetector.isMentioned("KazamaRinSomething bonjour", PLAYER));
    }

    @Test
    void ignoresEmptyOrNull() {
        assertFalse(MentionDetector.isMentioned("", PLAYER));
        assertFalse(MentionDetector.isMentioned(null, PLAYER));
        assertFalse(MentionDetector.isMentioned("Salut Kazama_Rin", ""));
        assertFalse(MentionDetector.isMentioned("Salut Kazama_Rin", null));
    }

    @Test
    void noFalsePositiveOnDifferentPseudo() {
        assertFalse(MentionDetector.isMentioned("Salut Akira_Sato", PLAYER));
        assertFalse(MentionDetector.isMentioned("@OMZ tu viens ?", PLAYER));
    }
}
