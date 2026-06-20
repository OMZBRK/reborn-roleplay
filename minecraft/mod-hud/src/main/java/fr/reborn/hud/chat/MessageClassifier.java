package fr.reborn.hud.chat;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Classifie un message brut (texte plat sans formatage MC) dans un
 * {@link ChatTab} via une liste de regex par tab.
 *
 * <p>Default patterns hardcodés ci-dessous — un JSON config externe est
 * prévu en CHANTIER D (chargé depuis {@code config/reborn-hud-classifiers.json}).
 *
 * <p>Logique : on teste les patterns dans l'ordre WHISPER → GROUP → RP.
 * Le premier match gagne. Si rien ne match → {@link ChatTab#GENERAL}.
 * GENERAL est donc le "catch-all" et reçoit aussi les messages
 * matchés (puisque par sémantique, "Général" affiche tout).
 *
 * <p>Pour l'incrément unread, on utilise la classification spécifique
 * (RP/GROUP/WHISPER) si match — sinon GENERAL.
 */
public final class MessageClassifier {

    private final Map<ChatTab, List<Pattern>> patterns = new EnumMap<>(ChatTab.class);

    public MessageClassifier() {
        // Defaults (à terme remplaçables par config JSON)
        patterns.put(ChatTab.RP, List.of(
            Pattern.compile("\\[RP\\]"),
            Pattern.compile("^\\* [A-Za-z0-9_]+"),
            Pattern.compile("/me\\b")
        ));
        patterns.put(ChatTab.GROUP, List.of(
            Pattern.compile("\\[GROUPE\\]"),
            Pattern.compile("\\[PARTY\\]")
        ));
        patterns.put(ChatTab.WHISPER, List.of(
            Pattern.compile("whispers to you:"),
            Pattern.compile("you whisper to"),
            Pattern.compile("→")
        ));
    }

    /** Inject custom patterns (utile pour les tests). */
    public MessageClassifier(Map<ChatTab, List<Pattern>> custom) {
        this.patterns.putAll(custom);
    }

    /**
     * Classification stricte : WHISPER > GROUP > RP > GENERAL. Premier
     * match dans cet ordre gagne.
     */
    public ChatTab classify(String rawText) {
        if (rawText == null || rawText.isBlank()) return ChatTab.GENERAL;
        for (ChatTab tab : List.of(ChatTab.WHISPER, ChatTab.GROUP, ChatTab.RP)) {
            List<Pattern> list = patterns.getOrDefault(tab, List.of());
            for (Pattern p : list) {
                if (p.matcher(rawText).find()) return tab;
            }
        }
        return ChatTab.GENERAL;
    }

    /**
     * True si {@code rawText} doit s'afficher quand l'onglet {@code active}
     * est ouvert. GENERAL voit tout, les autres ne voient que leur
     * classification.
     */
    public boolean visibleIn(String rawText, ChatTab active) {
        if (active == ChatTab.GENERAL) return true;
        return classify(rawText) == active;
    }
}
