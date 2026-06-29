package fr.reborn.hud.interaction;

import java.util.List;

/**
 * Entrée d'un menu d'interaction (style GTA). Soit une action terminale
 * ({@code action != null}), soit un sous-menu ({@code children} non vide).
 */
public record InteractionItem(String label, List<InteractionItem> children, Runnable action) {

    public static InteractionItem action(String label, Runnable action) {
        return new InteractionItem(label, List.of(), action);
    }

    public static InteractionItem submenu(String label, List<InteractionItem> children) {
        return new InteractionItem(label, children, null);
    }

    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }
}
