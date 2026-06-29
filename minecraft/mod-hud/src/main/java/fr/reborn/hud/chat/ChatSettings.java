package fr.reborn.hud.chat;

/**
 * Settings du chat custom Zenkai. Persistés dans
 * {@link fr.reborn.hud.config.HudConfig}.
 *
 * <p>Tous les champs sont des defaults — initialisés à des valeurs sensées
 * en {@link #defaults}.
 *
 * <p>Volontairement une classe mutable (pas un record) parce que Gson
 * deserialize plus naturellement, et qu'on mutate field-par-field depuis
 * le settings screen.
 */
public final class ChatSettings {

    /** Affiche [HH:MM] en debut de chaque ligne. */
    public boolean showTimestamps = false;
    /** Compacte les messages consecutifs du meme pseudo. */
    public boolean compactConsecutive = false;
    /** Auto-scroll en bas a l'arrivee d'un nouveau message. */
    public boolean autoScroll = true;
    /** Highlight les messages contenant @<pseudo>. */
    public boolean highlightMentions = true;
    /** Joue un son discret quand on est mentionne. */
    public boolean soundOnMention = true;
    /** Opacite du panel chat en pourcentage (0-100). */
    public int opacity = 92;
    /** Taille du texte du chat (echelle, 10-16). */
    public int textSize = 12;
    /** Affiche la tête du joueur à côté de ses messages (façon chat_heads). */
    public boolean chatHeads = true;
    /** Effet machine à écrire sur le dernier message reçu. */
    public boolean chatTyping = true;
    /** Couleur du surlignage quand on est mentionné (ARGB, alpha ignoré). */
    public int highlightColor = 0xFFA0182B;
    /** Affiche le badge de rang (préfixe d'équipe scoreboard, ex. LuckPerms). */
    public boolean chatBadges = true;

    public static ChatSettings defaults() {
        return new ChatSettings();
    }

    public ChatSettings copy() {
        ChatSettings c = new ChatSettings();
        c.showTimestamps     = this.showTimestamps;
        c.compactConsecutive = this.compactConsecutive;
        c.autoScroll         = this.autoScroll;
        c.highlightMentions  = this.highlightMentions;
        c.soundOnMention     = this.soundOnMention;
        c.opacity            = this.opacity;
        c.textSize           = this.textSize;
        c.chatHeads          = this.chatHeads;
        c.chatTyping         = this.chatTyping;
        c.highlightColor     = this.highlightColor;
        c.chatBadges         = this.chatBadges;
        return c;
    }
}
