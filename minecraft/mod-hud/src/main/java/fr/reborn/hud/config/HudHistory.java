package fr.reborn.hud.config;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Stack mémoire de snapshots pour l'undo/redo de l'éditeur HUD.
 *
 * <p>Capacité {@link #CAPACITY} = 20 (cf brief). Quand on dépasse, le plus
 * ancien snapshot est purgé (FIFO sur la profondeur).
 *
 * <p>Stratégie :
 * <ul>
 *   <li>{@link #push} : push state actuel sur undo, clear redo. À appeler
 *       APRÈS chaque action utilisateur (drag end, scroll, eye, preset).</li>
 *   <li>{@link #undo} : pop dernier undo, push état courant sur redo,
 *       renvoie le snapshot précédent à appliquer. {@code null} si vide.</li>
 *   <li>{@link #redo} : pop dernier redo, push état courant sur undo,
 *       renvoie le snapshot à appliquer. {@code null} si vide.</li>
 * </ul>
 *
 * <p>État volatile — perdu à la fermeture de l'éditeur (HudConfig
 * persiste indépendamment).
 */
public final class HudHistory {

    public static final int CAPACITY = 20;

    private final Deque<HudConfigSnapshot> undoStack = new ArrayDeque<>();
    private final Deque<HudConfigSnapshot> redoStack = new ArrayDeque<>();

    public void push(HudConfigSnapshot snapshot) {
        if (snapshot == null) return;
        undoStack.push(snapshot);
        redoStack.clear();
        while (undoStack.size() > CAPACITY) {
            undoStack.pollLast();
        }
    }

    /** Renvoie le snapshot à appliquer, ou null si rien à undo. */
    public HudConfigSnapshot undo(HudConfigSnapshot current) {
        if (undoStack.isEmpty()) return null;
        HudConfigSnapshot previous = undoStack.pop();
        if (current != null) {
            redoStack.push(current);
            while (redoStack.size() > CAPACITY) redoStack.pollLast();
        }
        return previous;
    }

    /** Renvoie le snapshot à appliquer, ou null si rien à redo. */
    public HudConfigSnapshot redo(HudConfigSnapshot current) {
        if (redoStack.isEmpty()) return null;
        HudConfigSnapshot next = redoStack.pop();
        if (current != null) {
            undoStack.push(current);
            while (undoStack.size() > CAPACITY) undoStack.pollLast();
        }
        return next;
    }

    public int undoDepth() { return undoStack.size(); }
    public int redoDepth() { return redoStack.size(); }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }
}
