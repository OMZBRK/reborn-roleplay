package fr.reborn.hud.recit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.reborn.hud.staff.CanalJson;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Narration du staff</b> vue par le joueur (lot 2 « Reprises Ketto », étape 2d ; décision du 01/11 « oui pour
 * /narration, en plus des plaques ») : receveur du canal {@code reborn:narration} (serveur → client, JSON UTF-8 brut,
 * {@link CanalJson#NARRATION}), protocole v1 de PLUGINS_REFERENCE §4 bis.3 bis. Le serveur (ShinobiCore
 * {@code staff/outils/Narrations} : {@code /narration}, Poste de garde, API) choisit les destinataires, journalise, se
 * replie sur le tchat et rattrape le joueur qui se connecte ; ici, le message est lu et passé tel quel au service du
 * texte au centre ({@link Recit} : file, minuteurs, pages ; dessin, place et visibilité : {@link RecitHud}), <b>dans
 * l'allure de la narration</b> : {@link Gabarit#NARRATION}, plein écran, blanc pur, plus grande (décision du 02/11) —
 * jamais celle des stèles et des plaques ({@link Gabarit#STELE}, lot 4).
 *
 * <p>Le type S2C enregistré ici est ce qui fait apparaître {@code reborn:narration} dans
 * {@code getListeningPluginChannels()} côté serveur : sans lui, le joueur reçoit le repli en lignes de tchat italiques.
 *
 * <ul>
 *   <li>JSON illisible, {@code v} ≠ 1, {@code t} inconnu : ignoré ; champs inconnus : ignorés.</li>
 *   <li>{@code narration} : {@code id} (chaîne non vide ; déjà à l'écran ou en file = doublon, ignoré), {@code segs}
 *       {@code [[texte, couleur]…]} (clé inconnue = blanc ; sans texte lisible : ignorée), {@code s} (0 automatique, -1
 *       tenue, 2 à 180 ; toute autre valeur = 0), {@code mode} ({@code remplace}, lu comme le serveur : sans tenir
 *       compte de la casse ni des espaces autour ; sinon {@code file}), {@code pos} facultatif (entier 0 à 100, sinon la
 *       place par défaut), {@code ecoule} facultatif (ms, rattrapage d'une narration déjà commencée) →
 *       {@link Recit#montrer(String, List, int, Recit.Mode, int, long, Gabarit)}.</li>
 *   <li>{@code narration_fin} : {@code id} → {@link Recit#cacher} (fondu de sortie du gabarit : 700 ms pour la narration, ou
 *       retirée de la file) ;
 *       {@code id} vide = tout effacer, écran et file.</li>
 *   <li>Déconnexion ({@link RecitHud#init}) et nouvelle connexion (changement de serveur) : tout est oublié, sans
 *       fondu ; le rattrapage du serveur refait l'état.</li>
 * </ul>
 * Réception sur le fil client ({@code client.execute}), comme les autres canaux Reborn : l'analyse n'alloue qu'à la
 * réception, rien par image.
 */
public final class NarrationClient {

    /** Version du protocole (champ {@code v}) : un message d'une autre version est ignoré. */
    public static final int VERSION = 1;

    /** Banc : allure imposée aux narrations reçues (variante de la planche d1), null = {@link Gabarit#NARRATION}. */
    public static Gabarit debugGabarit;

    private NarrationClient() {}

    /**
     * Allure des narrations reçues : {@link Gabarit#NARRATION} (ou celle du banc). L'aperçu de l'outil « Narration » du
     * Poste de garde la prend aussi : le staff voit ce que verront les joueurs.
     */
    public static Gabarit gabarit() { return debugGabarit != null ? debugGabarit : Gabarit.NARRATION; }

    /** Type S2C et receveur du canal ; tout oublié à chaque nouvelle connexion (la déconnexion : {@link RecitHud#init}). */
    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(CanalJson.NARRATION, CanalJson.codec(CanalJson.NARRATION));
        ClientPlayNetworking.registerGlobalReceiver(CanalJson.NARRATION, (p, c) -> c.client().execute(() -> recevoir(p.json())));
        ClientPlayConnectionEvents.JOIN.register((h, s, c) -> c.execute(Recit::vider));
    }

    /**
     * Un message du canal (fil client ; le banc y passe des messages simulés). Vrai s'il a été pris : narration montrée
     * ou mise en file, {@code narration_fin} appliqué.
     */
    public static boolean recevoir(String raw) {
        JsonObject o;
        try { o = JsonParser.parseString(raw).getAsJsonObject(); } catch (RuntimeException e) { return false; }
        if (entier(o.get("v"), Long.MIN_VALUE) != VERSION) return false;
        String t = chaine(o.get("t")), id = chaine(o.get("id"));
        if ("narration".equals(t)) {
            if (id == null || id.isEmpty()) return false;
            long s = entier(o.get("s"), Recit.AUTO);
            int secondes = s == Recit.TENIR || s >= Recit.DUREE_MIN && s <= Recit.DUREE_MAX ? (int) s : Recit.AUTO;
            long p = entier(o.get("pos"), Recit.POS_DEFAUT);
            int pos = p >= 0 && p <= 100 ? (int) p : Recit.POS_DEFAUT;
            String m = chaine(o.get("mode"));
            Recit.Mode mode = m != null && m.trim().equalsIgnoreCase("remplace") ? Recit.Mode.REMPLACE : Recit.Mode.FILE;
            long ecoule = Math.max(0, entier(o.get("ecoule"), 0));
            return Recit.montrer(id, segments(o.get("segs")), secondes, mode, pos, ecoule, gabarit());
        }
        if ("narration_fin".equals(t)) {
            if (id == null) return false;   // champ absent : message mal formé (« tout effacer » s'écrit avec un id vide)
            Recit.cacher(id);
            return true;
        }
        return false;
    }

    /**
     * {@code segs} : {@code [[texte, couleur]…]} ; un élément illisible est sauté (une simple chaîne = un segment blanc).
     * La couleur reste une clé ({@link Recit} : {@code rouge}, {@code or}, {@code ombre}, {@code gris} ; autre = blanc).
     */
    static List<Recit.Segment> segments(JsonElement e) {
        ArrayList<Recit.Segment> l = new ArrayList<>();
        if (e == null || !e.isJsonArray()) return l;
        for (JsonElement x : e.getAsJsonArray()) {
            String texte = chaine(x), couleur = "";
            if (texte == null && x.isJsonArray() && !x.getAsJsonArray().isEmpty()) {
                JsonArray a = x.getAsJsonArray();
                texte = chaine(a.get(0));
                String c = a.size() > 1 ? chaine(a.get(1)) : null;
                if (c != null) couleur = c;
            }
            if (texte != null && !texte.isEmpty()) l.add(new Recit.Segment(texte, couleur));
        }
        return l;
    }

    /** Chaîne JSON, sinon null. */
    private static String chaine(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }

    /** Entier JSON (nombre sans partie décimale), sinon {@code defaut} (chaîne, booléen, 2.5, absent…). */
    private static long entier(JsonElement e, long defaut) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return defaut;
        double d;
        try { d = e.getAsDouble(); } catch (RuntimeException ex) { return defaut; }
        return d == Math.rint(d) && Math.abs(d) <= 1e15 ? (long) d : defaut;
    }
}
