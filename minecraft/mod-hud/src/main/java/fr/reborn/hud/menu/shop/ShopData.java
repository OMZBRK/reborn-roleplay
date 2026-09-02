package fr.reborn.hud.menu.shop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashSet;
import java.util.Set;

/**
 * Dernier état boutique reçu du serveur (canal {@code reborn:shop}) : solde
 * <b>ryo</b>, prix d'une tenue, ensemble des tenues <b>possédées</b> (ids catalog)
 * et le blob d'<b>apparence</b> RP courant (pour composer l'aperçu / renvoyer à
 * l'équipement). Alimenté par {@link ShopPayload}, lu par {@link ShopScreen}.
 */
public final class ShopData {

    private ShopData() {}

    private static volatile long ryo = 0L;
    private static volatile long price = 500L;
    private static volatile Set<String> owned = Set.of();
    private static volatile String appearance = "";
    private static volatile boolean received = false;
    private static volatile String toast = null;
    private static volatile long toastAt = 0L;

    public static long ryo()             { return ryo; }
    public static long price()           { return price; }
    public static boolean owns(String id){ return id != null && owned.contains(id); }
    public static String appearance()    { return appearance == null ? "" : appearance; }
    public static boolean received()     { return received; }
    public static String toast()         { return toast; }
    public static long toastAt()         { return toastAt; }

    public static void clear() {
        ryo = 0L; owned = Set.of(); appearance = ""; received = false; toast = null;
    }

    /** Parse le JSON serveur {@code {ryo,price,owned[],appearance,toast?}} (thread client). */
    public static void update(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            ryo = o.has("ryo") ? o.get("ryo").getAsLong() : 0L;
            price = o.has("price") ? o.get("price").getAsLong() : 500L;
            Set<String> set = new HashSet<>();
            if (o.has("owned") && o.get("owned").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("owned")) set.add(e.getAsString());
            }
            owned = set;
            appearance = (o.has("appearance") && !o.get("appearance").isJsonNull())
                    ? o.get("appearance").getAsString() : "";
            if (o.has("toast") && !o.get("toast").isJsonNull()) {
                toast = o.get("toast").getAsString();
                toastAt = System.currentTimeMillis();
            }
            received = true;
        } catch (Exception ignored) {
            // JSON malformé → on garde l'état précédent.
        }
    }
}
