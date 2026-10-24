package fr.reborn.hud.monnaie;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.menu.inventory.InventoryData;
import fr.reborn.hud.menu.inventory.SlotItem;

/**
 * La sacoche d'un corps en mode fouille ({@code {"t":"fouille",…}} sur {@code reborn:monnaie}) : nom et état de la
 * victime, sa barre et son sac, sa bourse et ce que l'on peut encore y prendre (30 % par scène, sans plafond à la mort
 * RP). {@code lecture} (GAMEPLAN §14, 24/10) : fouille <b>consentie</b> d'une personne consciente, on regarde sans rien
 * prendre.
 */
public record FouilleVue(String cible, String etat, String uuid, long bourse, long reste, boolean plafond, int pourcent,
                         SlotItem[] hotbar, SlotItem[] bag, boolean lecture) {

    static FouilleVue lire(JsonObject o) {
        SlotItem[] h = new SlotItem[9];
        JsonArray ha = o.has("hotbar") && o.get("hotbar").isJsonArray() ? o.getAsJsonArray("hotbar") : new JsonArray();
        for (int i = 0; i < 9 && i < ha.size(); i++) h[i] = item(ha.get(i));
        JsonArray ba = o.has("bag") && o.get("bag").isJsonArray() ? o.getAsJsonArray("bag") : new JsonArray();
        SlotItem[] b = new SlotItem[ba.size()];
        for (int i = 0; i < ba.size(); i++) b[i] = item(ba.get(i));
        return new FouilleVue(MonnaieClient.str(o, "cible"), MonnaieClient.str(o, "etat"), MonnaieClient.str(o, "uuid"),
            MonnaieClient.num(o, "bourse"), MonnaieClient.num(o, "reste"), !o.has("plafond") || o.get("plafond").getAsBoolean(),
            (int) MonnaieClient.num(o, "pourcent"), h, b,
            o.has("lecture") && o.get("lecture").getAsBoolean());
    }

    private static SlotItem item(JsonElement e) {
        return e != null && e.isJsonObject() ? InventoryData.parseItem(e.getAsJsonObject()) : null;
    }
}
