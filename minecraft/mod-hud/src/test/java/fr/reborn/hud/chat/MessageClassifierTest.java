package fr.reborn.hud.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests du {@link MessageClassifier} : un message bien formé tombe sur
 * le bon onglet selon les patterns hardcodés.
 */
class MessageClassifierTest {

    private final MessageClassifier classifier = new MessageClassifier();

    @Test
    void blankFallsBackToGeneral() {
        assertEquals(ChatTab.GENERAL, classifier.classify(""));
        assertEquals(ChatTab.GENERAL, classifier.classify(null));
        assertEquals(ChatTab.GENERAL, classifier.classify("   "));
    }

    @Test
    void rpBracketTagsClassifiesAsRP() {
        assertEquals(ChatTab.RP, classifier.classify("[RP] Kazama_Rin: bonjour"));
        assertEquals(ChatTab.RP, classifier.classify("<Akira_Sato> [RP] *salue*"));
    }

    @Test
    void asteriskActionStartsClassifiesAsRP() {
        assertEquals(ChatTab.RP, classifier.classify("* Hikami_Nara serre le poing"));
        assertEquals(ChatTab.RP, classifier.classify("* OMZ regarde au loin"));
    }

    @Test
    void meCommandClassifiesAsRP() {
        assertEquals(ChatTab.RP, classifier.classify("/me danse au milieu de la place"));
    }

    @Test
    void groupTagsClassifiesAsGroup() {
        assertEquals(ChatTab.GROUP, classifier.classify("[GROUPE] Alice: on va au donjon ?"));
        assertEquals(ChatTab.GROUP, classifier.classify("[PARTY] Bob: ready"));
    }

    @Test
    void whisperPatternsClassifyAsWhisper() {
        assertEquals(ChatTab.WHISPER, classifier.classify("Kazama_Rin whispers to you: salut"));
        assertEquals(ChatTab.WHISPER, classifier.classify("you whisper to Akira_Sato: ok"));
        assertEquals(ChatTab.WHISPER, classifier.classify("→ Kazama_Rin: message privé"));
    }

    @Test
    void whisperWinsOverRP() {
        // Un message qui matche RP ET whisper doit aller dans whisper (priorité)
        String msg = "* Akira whispers to you: [RP] hé";
        assertEquals(ChatTab.WHISPER, classifier.classify(msg));
    }

    @Test
    void generalReceivesAllInVisibleIn() {
        assertTrue(classifier.visibleIn("[RP] test", ChatTab.GENERAL));
        assertTrue(classifier.visibleIn("normal msg", ChatTab.GENERAL));
        assertTrue(classifier.visibleIn("[GROUPE] x", ChatTab.GENERAL));
    }

    @Test
    void rpTabFiltersToRPOnly() {
        assertTrue(classifier.visibleIn("[RP] test", ChatTab.RP));
        assertFalse(classifier.visibleIn("normal msg", ChatTab.RP));
        assertFalse(classifier.visibleIn("[GROUPE] x", ChatTab.RP));
    }
}
