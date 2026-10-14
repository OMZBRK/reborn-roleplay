package fr.reborn.hud.interaction;

import java.util.List;

/**
 * Entrée d'un menu d'interaction (style GTA). Soit une action terminale
 * ({@code action != null}), soit un sous-menu ({@code children} non vide).
 * Une entrée <b>grisée</b> ({@code reason != null}) s'affiche en gris avec sa raison ; un clic la rappelle
 * seulement (rien n'est envoyé au serveur).
 */
public record InteractionItem(String label, List<InteractionItem> children, Runnable action, String reason) {

    public static InteractionItem action(String label, Runnable action) {
        return new InteractionItem(label, List.of(), action, null);
    }

    public static InteractionItem submenu(String label, List<InteractionItem> children) {
        return new InteractionItem(label, children, null, null);
    }

    /** Entrée grisée : {@code reason} court (affiché à droite), rappelé au clic. */
    public static InteractionItem disabled(String label, String reason) {
        return new InteractionItem(label, List.of(), () -> InteractionMenus.info(label + " : " + reason), reason);
    }

    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    public boolean isDisabled() {
        return reason != null;
    }
}
