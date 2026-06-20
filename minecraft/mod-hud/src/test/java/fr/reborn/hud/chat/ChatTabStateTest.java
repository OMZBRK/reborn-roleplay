package fr.reborn.hud.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests du {@link ChatTabState} : active tab + compteurs unread.
 */
class ChatTabStateTest {

    @Test
    void defaultActiveIsGeneral() {
        ChatTabState s = new ChatTabState();
        assertEquals(ChatTab.GENERAL, s.activeTab());
    }

    @Test
    void newMessageOnInactiveTabIncrementsUnread() {
        ChatTabState s = new ChatTabState();
        s.onMessageReceived(ChatTab.RP);
        assertEquals(1, s.unreadOf(ChatTab.RP));
        s.onMessageReceived(ChatTab.RP);
        assertEquals(2, s.unreadOf(ChatTab.RP));
    }

    @Test
    void newMessageOnActiveTabDoesNotIncrement() {
        ChatTabState s = new ChatTabState();
        s.setActiveTab(ChatTab.RP);
        s.onMessageReceived(ChatTab.RP);
        assertEquals(0, s.unreadOf(ChatTab.RP));
    }

    @Test
    void setActiveResetsUnreadOnNewTab() {
        ChatTabState s = new ChatTabState();
        s.onMessageReceived(ChatTab.WHISPER);
        s.onMessageReceived(ChatTab.WHISPER);
        assertEquals(2, s.unreadOf(ChatTab.WHISPER));
        s.setActiveTab(ChatTab.WHISPER);
        assertEquals(0, s.unreadOf(ChatTab.WHISPER));
    }

    @Test
    void unreadCountsAreIndependentPerTab() {
        ChatTabState s = new ChatTabState();
        s.onMessageReceived(ChatTab.RP);
        s.onMessageReceived(ChatTab.GROUP);
        s.onMessageReceived(ChatTab.GROUP);
        assertEquals(1, s.unreadOf(ChatTab.RP));
        assertEquals(2, s.unreadOf(ChatTab.GROUP));
        assertEquals(0, s.unreadOf(ChatTab.WHISPER));
    }

    @Test
    void resetAllClearsCountersAndActive() {
        ChatTabState s = new ChatTabState();
        s.onMessageReceived(ChatTab.RP);
        s.setActiveTab(ChatTab.WHISPER);
        s.resetAll();
        assertEquals(ChatTab.GENERAL, s.activeTab());
        assertEquals(0, s.unreadOf(ChatTab.RP));
        assertEquals(0, s.unreadOf(ChatTab.WHISPER));
    }
}
