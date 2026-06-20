package fr.reborn.hud.chat;

import fr.reborn.hud.ui.style.RebornColors;

/**
 * Onglets du chat custom Zenkai-like.
 *
 * <p>L'enum définit l'ordre d'affichage des tabs dans la header de chat
 * et leur couleur d'accent (dot / unread badge / border-bottom actif).
 *
 * <p>Le filtrage des messages s'appuie sur {@link MessageClassifier}.
 * Un message qui ne match aucune regex spécifique tombe par défaut dans
 * {@link #GENERAL} — cet onglet voit tout.
 */
public enum ChatTab {
    GENERAL("general", "Général", 0),
    RP     ("rp",      "RP",      RebornColors.RP_COLOR),
    GROUP  ("group",   "Groupe",  RebornColors.GROUP_COLOR),
    WHISPER("whisper", "Whisper", RebornColors.ACCENT_HOVER);

    private final String id;
    private final String displayName;
    private final int accentColor;

    ChatTab(String id, String displayName, int accentColor) {
        this.id          = id;
        this.displayName = displayName;
        this.accentColor = accentColor;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }

    /**
     * Couleur d'accent (dot + badge unread + border-bottom).
     * 0 = pas d'accent (cas {@link #GENERAL}).
     */
    public int accentColor() { return accentColor; }
}
