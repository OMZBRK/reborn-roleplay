package fr.reborn.hud.chat;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mémorise un horodatage réel (HH:MM) pour chaque message au moment où il
 * arrive — ChatComponent lui-même ne tracke que le tick d'ajout, pas l'heure
 * monde. On enregistre via {@link #record} depuis le mixin sur addMessage,
 * et on lit via {@link #formattedFor}.
 *
 * <p>Cap 200 entrées : LRU naïf (en ré-insérant dans une LinkedHashMap +
 * suppression du oldest).
 */
public final class MessageTimestamps {

    private static final int CAPACITY = 200;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /** Key = tick d'ajout du message (addedTime), value = heure formatée. */
    private static final Map<Integer, String> map = new LinkedHashMap<>(CAPACITY, 0.75f, false);

    private MessageTimestamps() {}

    public static synchronized void record(int addedTick) {
        String ts = LocalTime.now().format(FORMATTER);
        map.put(addedTick, ts);
        if (map.size() > CAPACITY) {
            map.remove(map.keySet().iterator().next());
        }
    }

    public static synchronized String formattedFor(int addedTick) {
        return map.getOrDefault(addedTick, "--:--");
    }
}
