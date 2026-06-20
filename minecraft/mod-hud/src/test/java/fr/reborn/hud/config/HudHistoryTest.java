package fr.reborn.hud.config;

import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementState;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests du {@link HudHistory} : push, undo, redo, capacite max.
 */
class HudHistoryTest {

    private static HudConfigSnapshot snap(int chatX) {
        Map<HudElement, HudElementState> states = new EnumMap<>(HudElement.class);
        states.put(HudElement.CHAT, new HudElementState(chatX, 0, 1.0f, true, null));
        return new HudConfigSnapshot(states, HudPresets.DEFAULT);
    }

    @Test
    void emptyHistoryReturnsNullOnUndo() {
        HudHistory h = new HudHistory();
        assertNull(h.undo(snap(0)));
    }

    @Test
    void pushThenUndoReturnsLatest() {
        HudHistory h = new HudHistory();
        h.push(snap(10));
        HudConfigSnapshot popped = h.undo(snap(20));
        assertNotNull(popped);
        assertEquals(10, popped.states().get(HudElement.CHAT).x());
    }

    @Test
    void redoAfterUndoRoundTrips() {
        HudHistory h = new HudHistory();
        h.push(snap(10));
        h.push(snap(20));
        // Etat actuel = snap(30)
        // Undo : on revient a snap(20), redo contient snap(30)
        HudConfigSnapshot back = h.undo(snap(30));
        assertEquals(20, back.states().get(HudElement.CHAT).x());
        // Redo : on revient a snap(30)
        HudConfigSnapshot forward = h.redo(snap(20));
        assertEquals(30, forward.states().get(HudElement.CHAT).x());
    }

    @Test
    void newPushClearsRedo() {
        HudHistory h = new HudHistory();
        h.push(snap(1));
        h.push(snap(2));
        h.undo(snap(3));
        assertEquals(1, h.redoDepth());
        h.push(snap(99));
        assertEquals(0, h.redoDepth(), "nouveau push doit clear le redo");
    }

    @Test
    void capacityCapsAt20() {
        HudHistory h = new HudHistory();
        for (int i = 0; i < 25; i++) {
            h.push(snap(i));
        }
        assertEquals(HudHistory.CAPACITY, h.undoDepth());
    }

    @Test
    void undoPushesCurrentToRedoStack() {
        HudHistory h = new HudHistory();
        h.push(snap(1));
        assertEquals(0, h.redoDepth());
        h.undo(snap(100));
        assertEquals(1, h.redoDepth());
    }
}
