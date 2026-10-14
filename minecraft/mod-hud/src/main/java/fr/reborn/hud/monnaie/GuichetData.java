package fr.reborn.hud.monnaie;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.reborn.hud.menu.inventory.InventoryData;
import fr.reborn.hud.menu.inventory.SlotItem;

import java.util.ArrayList;
import java.util.List;

/** Dernier état du guichet reçu sur {@code reborn:banque} ({@code {"t":"guichet",…}}). */
public final class GuichetData {

    public record Palier(int n, String nom, long cout, int cases, long plafond) {}
    public record Case(String ref, SlotItem item, String refus) {}
    public record Ligne(long le, long delta, String texte) {}
    public record Contact(String ref, String nom) {}
    public record Proche(String uuid, String nom) {}

    public static String banque = "", titulaire = "", numero = "", guichetier, gele, toast;
    public static boolean allie;
    public static int fraisAllie = 5, onglet, palier = 1;
    public static long compte, bourse, plafond, soldeMontant, toastLe;
    public static String soldeSemaine;
    public static final List<Palier> paliers = new ArrayList<>();
    public static SlotItem[] coffre = new SlotItem[0];
    public static final List<Case> sacoche = new ArrayList<>();
    public static final List<Ligne> historique = new ArrayList<>();
    public static final List<Contact> contacts = new ArrayList<>();
    public static final List<Proche> proches = new ArrayList<>();
    public static int virPourcent = 1, virAutre = 5;
    public static long virMin = 5, virPlafond = 20000, virDeja;
    public static JsonObject echange;
    public static String refusRef, refusTexte;
    public static long refusLe;
    public static long version;

    private GuichetData() {}

    public static void clear() {
        version++;
        echange = null;
    }

    static void update(JsonObject o) {
        banque = MonnaieClient.str(o, "banque");
        titulaire = MonnaieClient.str(o, "titulaire");
        numero = MonnaieClient.str(o, "numero");
        guichetier = o.has("guichetier") ? MonnaieClient.str(o, "guichetier") : null;
        gele = o.has("gele") ? MonnaieClient.str(o, "gele") : null;
        allie = o.has("allie") && o.get("allie").getAsBoolean();
        fraisAllie = (int) MonnaieClient.num(o, "fraisAllie");
        onglet = (int) MonnaieClient.num(o, "onglet");
        compte = MonnaieClient.num(o, "compte");
        bourse = MonnaieClient.num(o, "bourse");
        plafond = MonnaieClient.num(o, "plafond");
        palier = (int) Math.max(1, MonnaieClient.num(o, "palier"));
        paliers.clear();
        for (JsonElement e : arr(o, "paliers")) {
            JsonObject p = e.getAsJsonObject();
            paliers.add(new Palier((int) MonnaieClient.num(p, "n"), MonnaieClient.str(p, "nom"), MonnaieClient.num(p, "cout"),
                (int) MonnaieClient.num(p, "cases"), MonnaieClient.num(p, "plafond")));
        }
        JsonArray co = arr(o, "coffre");
        coffre = new SlotItem[co.size()];
        for (int i = 0; i < co.size(); i++) coffre[i] = co.get(i).isJsonObject() ? InventoryData.parseItem(co.get(i).getAsJsonObject()) : null;
        sacoche.clear();
        for (JsonElement e : arr(o, "sacoche")) {
            JsonObject c = e.getAsJsonObject();
            SlotItem it = c.has("item") && c.get("item").isJsonObject() ? InventoryData.parseItem(c.getAsJsonObject("item")) : null;
            if (it != null) sacoche.add(new Case(MonnaieClient.str(c, "ref"), it, c.has("refus") ? MonnaieClient.str(c, "refus") : null));
        }
        historique.clear();
        for (JsonElement e : arr(o, "historique")) {
            JsonObject l = e.getAsJsonObject();
            historique.add(new Ligne(MonnaieClient.num(l, "le"), MonnaieClient.num(l, "d"), MonnaieClient.str(l, "t")));
        }
        if (o.has("solde") && o.get("solde").isJsonObject()) {
            soldeMontant = MonnaieClient.num(o.getAsJsonObject("solde"), "montant");
            soldeSemaine = MonnaieClient.str(o.getAsJsonObject("solde"), "semaine");
        } else { soldeMontant = 0; soldeSemaine = null; }
        contacts.clear();
        for (JsonElement e : arr(o, "contacts")) {
            JsonObject c = e.getAsJsonObject();
            contacts.add(new Contact(MonnaieClient.str(c, "ref"), MonnaieClient.str(c, "nom")));
        }
        if (o.has("virement") && o.get("virement").isJsonObject()) {
            JsonObject v = o.getAsJsonObject("virement");
            virPourcent = (int) MonnaieClient.num(v, "pourcent");
            virMin = MonnaieClient.num(v, "min");
            virAutre = (int) MonnaieClient.num(v, "autre");
            virPlafond = MonnaieClient.num(v, "plafondJour");
            virDeja = MonnaieClient.num(v, "deja");
        }
        proches.clear();
        for (JsonElement e : arr(o, "proches")) {
            JsonObject p = e.getAsJsonObject();
            proches.add(new Proche(MonnaieClient.str(p, "uuid"), MonnaieClient.str(p, "nom")));
        }
        echange = o.has("echange") && o.get("echange").isJsonObject() ? o.getAsJsonObject("echange") : null;
        if (o.has("toast")) { toast = MonnaieClient.str(o, "toast"); toastLe = System.currentTimeMillis(); }
        if (o.has("refus") && o.get("refus").isJsonObject()) {
            refusRef = MonnaieClient.str(o.getAsJsonObject("refus"), "ref");
            refusTexte = MonnaieClient.str(o.getAsJsonObject("refus"), "texte");
            refusLe = System.currentTimeMillis();
        }
        version++;
    }

    private static JsonArray arr(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray();
    }

    /** Banc d'essai : état injecté. */
    public static void debug(String json) {
        update(com.google.gson.JsonParser.parseString(json).getAsJsonObject());
    }
}
