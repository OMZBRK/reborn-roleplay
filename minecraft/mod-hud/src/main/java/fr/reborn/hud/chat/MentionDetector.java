package fr.reborn.hud.chat;

import java.util.regex.Pattern;

/**
 * Détecte si un message contient une mention du joueur local.
 *
 * <p>Mention = {@code @<pseudo>} OU le pseudo apparait dans le texte
 * (word boundary). Le pseudo est case-insensitive.
 *
 * <p>Performance : compile le pattern à chaque appel — appel fait
 * uniquement sur addMessage donc négligeable. Si profilage montre un
 * hot path on memoize.
 */
public final class MentionDetector {

    private MentionDetector() {}

    /**
     * @param rawText texte plat du message (sans formatage)
     * @param playerName pseudo du joueur local
     * @return true si rawText mentionne playerName
     */
    public static boolean isMentioned(String rawText, String playerName) {
        if (rawText == null || rawText.isBlank()) return false;
        if (playerName == null || playerName.isBlank()) return false;
        // 1. Recherche @<pseudo>
        String escaped = Pattern.quote(playerName);
        Pattern atMention = Pattern.compile("@" + escaped, Pattern.CASE_INSENSITIVE);
        if (atMention.matcher(rawText).find()) return true;
        // 2. Recherche pseudo en tant que mot isolé
        Pattern wordBoundary = Pattern.compile("\\b" + escaped + "\\b", Pattern.CASE_INSENSITIVE);
        return wordBoundary.matcher(rawText).find();
    }
}
