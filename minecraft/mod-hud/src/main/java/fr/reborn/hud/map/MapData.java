package fr.reborn.hud.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot de la carte poussé par ShinobiCore sur {@code reborn:map} : quelle carte,
 * ses lieux, et si le joueur peut s'y téléporter (staff). Le serveur reste seul
 * juge du {@code tp:}.
 *
 * <p>Aucun serveur (test solo / runClient) → {@link #mock()}.
 */
public final class MapData {

    private MapData() {}

    public enum Type {
        ZONE, LIEU, PNJ, PORTE;

        static Type parse(String s) {
            for (Type t : values()) if (t.name().equalsIgnoreCase(s)) return t;
            return LIEU;
        }
    }

    public record Place(String id, String name, Type type, int x, int z) {}

    /** {@code here} = le joueur est dans le monde de cette carte (sa position s'affiche). */
    public record Snapshot(String map, String title, boolean here, boolean canTp, List<Place> places) {}

    private static volatile Snapshot snapshot = null;
    private static volatile int version = 0;

    public static Snapshot get() {
        Snapshot s = snapshot;
        return s != null ? s : mock();
    }

    public static boolean fromServer() { return snapshot != null; }

    public static int version() { return version; }

    public static void clear() { snapshot = null; }

    /** Parse le JSON serveur. Retourne {@code open} ; silencieux en cas d'erreur (garde l'ancien). */
    public static boolean update(String json) {
        try {
            JsonObject r = JsonParser.parseString(json).getAsJsonObject();
            List<Place> places = new ArrayList<>();
            if (r.has("places")) {
                for (JsonElement e : r.getAsJsonArray("places")) {
                    JsonObject o = e.getAsJsonObject();
                    places.add(new Place(o.get("id").getAsString(), o.get("name").getAsString(),
                        Type.parse(o.get("type").getAsString()), o.get("x").getAsInt(), o.get("z").getAsInt()));
                }
            }
            snapshot = new Snapshot(r.get("map").getAsString(), r.get("title").getAsString(),
                r.has("here") && r.get("here").getAsBoolean(),
                r.has("canTp") && r.get("canTp").getAsBoolean(), List.copyOf(places));
            version++;
            return r.has("open") && r.get("open").getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    /** Aperçu hors ligne : mêmes lieux que le places.yml par défaut. */
    static Snapshot mock() {
        return new Snapshot("konoha", "Konoha", false, false, List.of(
            new Place("zone_mont_hokage", "Mont des Hokage", Type.ZONE, 15880, 9020),
            new Place("zone_uchiha", "Quartier Uchiha", Type.ZONE, 15220, 10140),
            new Place("zone_centre", "Centre du village", Type.ZONE, 15930, 9880),
            new Place("tour_hokage", "Tour de l'Hokage", Type.LIEU, 15872, 9600),
            new Place("porte_sud", "Grande Porte", Type.PORTE, 15900, 11450),
            new Place("porte_est", "Porte Est", Type.PORTE, 17500, 10400),
            new Place("porte_nord", "Porte Nord", Type.PORTE, 16420, 8020)));
    }
}
