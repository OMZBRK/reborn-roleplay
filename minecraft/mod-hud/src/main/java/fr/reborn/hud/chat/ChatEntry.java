package fr.reborn.hud.chat;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Un message du chat découpé pour l'affichage en deux lignes : un en-tête (genre, auteur, heure) puis le corps.
 *
 * <p>Le découpage lit les formats émis par ShinobiCore : {@code * Nom action} (/me, nom en gras),
 * {@code Nom » message} (parole), {@code [Staff] Grade pseudo : message}, plus le chuchotement vanilla et
 * {@code <pseudo> message}. Tout le reste est un message système, affiché sans en-tête.
 */
public final class ChatEntry {

    /** Genre d'un message : libellé, icône (textures/gui/chat) et couleur de l'en-tête, onglet où il apparaît. */
    public enum Kind {
        NARRATION("Narration", "chat_narration", 0xFFD9A95E, ChatTab.RP),
        DIALOGUE("Dialogue", "chat_dialogue", 0xFFE8D2A6, ChatTab.RP),
        WHISPER("Message privé", "chat_whisper", 0xFFF1B0CC, ChatTab.GROUP),
        GROUP("Groupe", "chat_group", 0xFF8CC9A0, ChatTab.GROUP),
        STAFF("Staff", "chat_staff", 0xFFE5535F, ChatTab.GROUP),
        SYSTEM("", "", 0xFFC2B59A, ChatTab.GENERAL);

        public final String label, icon;
        public final int color;
        public final ChatTab tab;

        Kind(String label, String icon, int color, ChatTab tab) {
            this.label = label;
            this.icon = icon;
            this.color = color;
            this.tab = tab;
        }
    }

    public final Kind kind;
    public final Component name;
    public final Component body;
    public final String plain;
    private final String plainLower;
    public final int addedTime;
    public final long seenAtMs = System.currentTimeMillis();

    private List<FormattedCharSequence> wrapped;
    private int wrappedWidth = -1;

    private ChatEntry(Kind kind, Component name, Component body, String plain, int addedTime) {
        this.kind = kind;
        this.name = name;
        this.body = body;
        this.plain = plain;
        this.plainLower = plain.toLowerCase(Locale.ROOT);
        this.addedTime = addedTime;
    }

    public boolean visibleIn(ChatTab tab) {
        return tab == ChatTab.GENERAL || kind.tab == tab;
    }

    public boolean matches(String queryLower) {
        return queryLower.isEmpty() || plainLower.contains(queryLower);
    }

    /** Corps découpé à la largeur donnée (mis en cache tant que la largeur ne change pas). */
    public List<FormattedCharSequence> lines(Font font, int width) {
        if (wrapped == null || wrappedWidth != width) {
            wrapped = font.split(body, Math.max(20, width));
            wrappedWidth = width;
        }
        return wrapped;
    }

    // ------------------------------------------------------------------ découpage

    private record Seg(String text, Style style) {}

    public static ChatEntry parse(Component content, int addedTime) {
        List<Seg> segs = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        content.visit((style, text) -> {
            if (!text.isEmpty()) {
                segs.add(new Seg(text, style));
                sb.append(text);
            }
            return Optional.empty();
        }, Style.EMPTY);
        String plain = sb.toString();
        int n = plain.length();

        // /me : « * » puis le nom en gras, puis l'action
        if (plain.startsWith("* ") && n > 2 && boldAt(segs, 2)) {
            int end = 2;
            while (end < n && boldAt(segs, end)) end++;
            int bodyFrom = skipSpaces(plain, end);
            return new ChatEntry(Kind.NARRATION, slice(segs, 2, trimEnd(plain, end), false),
                    slice(segs, bodyFrom, n, true), plain, addedTime);
        }
        // [Staff] Grade pseudo : message
        if (plain.startsWith("[Staff] ")) {
            int sep = plain.indexOf(" : ");
            if (sep > 8) {
                return new ChatEntry(Kind.STAFF, slice(segs, 8, sep, false), slice(segs, sep + 3, n, false), plain, addedTime);
            }
            return new ChatEntry(Kind.STAFF, Component.literal("Système"), slice(segs, 8, n, false), plain, addedTime);
        }
        // [Groupe] / [Party]
        String upper = plain.toUpperCase(Locale.ROOT);
        if (upper.startsWith("[GROUPE]") || upper.startsWith("[PARTY]")) {
            int close = plain.indexOf(']') + 1;
            int sep = plain.indexOf(" : ", close);
            if (sep < 0) sep = plain.indexOf(" » ", close);
            if (sep > close) {
                return new ChatEntry(Kind.GROUP, slice(segs, skipSpaces(plain, close), sep, false),
                        slice(segs, sep + 3, n, false), plain, addedTime);
            }
        }
        // Chuchotement vanilla (fr / en)
        int wh = indexOfAny(plain, " vous chuchote", " whispers to you");
        if (wh > 0) {
            int colon = plain.indexOf(": ", wh);
            if (colon > 0) {
                return new ChatEntry(Kind.WHISPER, slice(segs, 0, wh, false), slice(segs, colon + 2, n, false), plain, addedTime);
            }
        }
        int out = plain.startsWith("Vous chuchotez à ") ? 17 : plain.startsWith("You whisper to ") ? 15 : -1;
        if (out > 0) {
            int colon = plain.indexOf(": ", out);
            if (colon > out) {
                return new ChatEntry(Kind.WHISPER, Component.literal("→ ").append(slice(segs, out, trimEnd(plain, colon), false)),
                        slice(segs, colon + 2, n, false), plain, addedTime);
            }
        }
        // Parole : « Nom » message »
        int arrow = plain.indexOf(" » ");
        if (arrow > 0 && arrow <= 48) {
            return new ChatEntry(Kind.DIALOGUE, slice(segs, 0, arrow, false), slice(segs, arrow + 3, n, false), plain, addedTime);
        }
        // Chat vanilla « <pseudo> message »
        if (plain.startsWith("<")) {
            int close = plain.indexOf("> ");
            if (close > 1 && close <= 18) {
                return new ChatEntry(Kind.DIALOGUE, slice(segs, 1, close, false), slice(segs, close + 2, n, false), plain, addedTime);
            }
        }
        return new ChatEntry(Kind.SYSTEM, Component.empty(), content, plain, addedTime);
    }

    private static boolean boldAt(List<Seg> segs, int index) {
        int pos = 0;
        for (Seg s : segs) {
            int end = pos + s.text.length();
            if (index < end) return s.style.isBold();
            pos = end;
        }
        return false;
    }

    private static int skipSpaces(String s, int i) {
        while (i < s.length() && s.charAt(i) == ' ') i++;
        return i;
    }

    private static int trimEnd(String s, int end) {
        while (end > 0 && s.charAt(end - 1) == ' ') end--;
        return end;
    }

    private static int indexOfAny(String s, String... needles) {
        for (String nd : needles) {
            int i = s.indexOf(nd);
            if (i >= 0) return i;
        }
        return -1;
    }

    /**
     * Sous-chaîne [from, to) du message en gardant les styles (clics, survols). {@code neutral} retire la couleur
     * (le corps d'une narration s'affiche en clair, comme une phrase).
     */
    private static Component slice(List<Seg> segs, int from, int to, boolean neutral) {
        MutableComponent out = Component.empty();
        int pos = 0;
        for (Seg s : segs) {
            int start = pos, end = pos + s.text.length();
            pos = end;
            if (end <= from || start >= to) continue;
            String part = s.text.substring(Math.max(0, from - start), Math.min(s.text.length(), to - start));
            Style st = neutral ? s.style.withColor((net.minecraft.network.chat.TextColor) null) : s.style;
            out.append(Component.literal(part).setStyle(st));
        }
        return out;
    }
}
