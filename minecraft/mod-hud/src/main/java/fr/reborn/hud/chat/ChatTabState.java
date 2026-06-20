package fr.reborn.hud.chat;

import java.util.EnumMap;
import java.util.Map;

/**
 * État runtime des onglets de chat : onglet actif + compteurs unread.
 *
 * <p>Non persisté (vit dans {@code RebornChatRenderer} pour la session).
 * À la première arrivée de message non-vu sur un tab non-actif, le
 * compteur unread s'incrémente. Click sur le tab → activé + reset.
 *
 * <p>Thread-safety : {@link EnumMap} non thread-safe mais toutes les
 * mutations passent par le render thread (HudRenderCallback /
 * Screen.mouseClicked).
 */
public final class ChatTabState {

    private ChatTab activeTab = ChatTab.GENERAL;
    private final Map<ChatTab, Integer> unread = new EnumMap<>(ChatTab.class);

    public ChatTab activeTab() { return activeTab; }

    public void setActiveTab(ChatTab tab) {
        this.activeTab = tab;
        unread.put(tab, 0);
    }

    public int unreadOf(ChatTab tab) {
        Integer n = unread.get(tab);
        return n == null ? 0 : n;
    }

    /**
     * Notifie l'arrivée d'un message classé sur {@code tab}. Si le tab
     * n'est pas actif et que ce n'est pas GENERAL (qui reçoit tout),
     * incrémente unread.
     */
    public void onMessageReceived(ChatTab tab) {
        if (tab == activeTab) return;
        unread.merge(tab, 1, Integer::sum);
    }

    public void resetAll() {
        unread.clear();
        activeTab = ChatTab.GENERAL;
    }
}
