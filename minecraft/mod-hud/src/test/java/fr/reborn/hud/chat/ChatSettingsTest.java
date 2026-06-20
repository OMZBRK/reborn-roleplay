package fr.reborn.hud.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatSettingsTest {

    @Test
    void defaultsAreSensible() {
        ChatSettings d = ChatSettings.defaults();
        assertFalse(d.showTimestamps);
        assertFalse(d.compactConsecutive);
        assertTrue(d.autoScroll);
        assertTrue(d.highlightMentions);
        assertTrue(d.soundOnMention);
        assertEquals(92, d.opacity);
        assertEquals(12, d.textSize);
    }

    @Test
    void copyIsIndependent() {
        ChatSettings a = ChatSettings.defaults();
        a.opacity = 50;
        a.showTimestamps = true;
        ChatSettings b = a.copy();
        assertEquals(50, b.opacity);
        assertTrue(b.showTimestamps);
        b.opacity = 70;
        assertEquals(50, a.opacity, "mutation de b ne touche pas a");
    }
}
