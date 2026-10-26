package com.reborn.shinobicore.douane;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.select.CandidatureClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Client des routes signées du jeu (contrat v1 : {@code apps/api/src/game/game-signature.ts},
 * PLUGINS_REFERENCE §4 decies) : accès à la connexion, dossier de l'examinateur, décision, présence, et
 * synchronisation des personnages (lot J, {@code SynchroPersonnages}).
 *
 * <p>Réglages ({@code config.yml}, section {@code reborn}) :
 * <ul>
 *   <li>{@code api-url} : base de l'API, finit par {@code /v1} ;</li>
 *   <li>{@code server-id} : identifiant de ce serveur, même valeur que {@code server-id} de Guardian (à défaut, la
 *       variable d'environnement {@code REBORN_SERVER_ID}) ;</li>
 *   <li>secret propre au jeu : variable d'environnement {@code REBORN_GAME_SECRET} si elle est posée, sinon
 *       {@code game-secret}. Jamais écrit au journal.</li>
 * </ul>
 *
 * <p>Tous les appels sont <b>bloquants</b> : jamais sur le fil principal. Délais courts (2 à 3 s) : le pré-login
 * attend la réponse.
 */
public final class DouaneClient {

    /** Réponse brute d'une route : {@code code} 0 = pas de réponse (réseau, délai dépassé, client non prêt). */
    public record Reponse(int code, JsonObject corps, String message, long retryAfterS, boolean reseau) {
        public boolean ok() { return code == 200 && corps != null; }
    }

    /** Bloc {@code douane} de {@code GET /game/acces} : la place en cours. Heures en ms epoch, 0 si absentes. */
    public record Place(String reservationId, String seanceId, long convocationAt, long entreeOuvreAt,
                        long entreeFermeAt, long arriveeAt, boolean deuxiemePassage, String village) {}

    /** Réponse de {@code GET /game/acces/:mcUuid}. {@code portee} null = valeur inconnue (refus par défaut). */
    public record Acces(Aiguillage.Portee portee, String raison, String message, String phase, boolean staff,
                        String pseudo, boolean douaneActive, CandidatureClient.Candidature candidature, Place douane) {}

    private static final long DELAI_DEFAUT_MS = 3000L;

    private final ShinobiCore plugin;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(2000))
            .build();
    /** Dernier avertissement de configuration écrit au journal (pour ne pas le répéter à chaque appel). */
    private volatile String dernierAvertissement;

    public DouaneClient(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    /* ================================================================ réglages */

    private String apiUrl() {
        String u = plugin.getConfig().getString("reborn.api-url", "http://localhost:3000/v1");
        if (u == null || u.isBlank()) u = "http://localhost:3000/v1";
        u = u.strip();
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

    /** Identifiant de ce serveur ({@code X-Reborn-Server}), ou chaîne vide. */
    public String serveur() {
        String s = plugin.getConfig().getString("reborn.server-id", "");
        if (s == null || s.isBlank()) s = System.getenv("REBORN_SERVER_ID");
        return s == null ? "" : s.strip();
    }

    private String secret() {
        String s = System.getenv("REBORN_GAME_SECRET");
        if (s == null || s.isBlank()) s = plugin.getConfig().getString("reborn.game-secret", "");
        return s == null ? "" : s.strip();
    }

    /** null si le client peut signer ; sinon la raison, sans jamais la valeur du secret. */
    public String nonPret() {
        if (!SignatureJeu.serveurValide(serveur())) {
            return "reborn.server-id absent ou invalide (minuscules, chiffres, tirets ; 32 au plus)";
        }
        String s = secret();
        if (s.isEmpty()) return "aucun secret du jeu (variable REBORN_GAME_SECRET ou reborn.game-secret)";
        if (s.length() < SignatureJeu.SECRET_LONGUEUR_MIN) {
            return "secret du jeu trop court (" + SignatureJeu.SECRET_LONGUEUR_MIN + " caractères au moins)";
        }
        return null;
    }

    private void avertir(String raison) {
        if (raison.equals(dernierAvertissement)) return;
        dernierAvertissement = raison;
        plugin.getLogger().warning("[douane] routes du jeu indisponibles : " + raison + ".");
    }

    /* ================================================================ routes */

    /**
     * {@code GET /v1/game/acces/:mcUuid} — répond toujours 200, même pour un compte inconnu. Renvoie null sans réponse
     * exploitable (réseau, délai, garde 401 / 429 / 503) : l'appelant applique le refus par défaut.
     */
    public Acces acces(UUID mcUuid, long delaiMs) {
        Reponse r = envoyer("GET", "/game/acces/" + mcUuid, null, delaiMs);
        if (!r.ok()) {
            if (r.code() != 0) plugin.getLogger().warning("[douane] accès " + mcUuid + " → HTTP " + r.code()
                    + (r.message() == null ? "" : " (" + r.message() + ")"));
            return null;
        }
        try {
            return lireAcces(r.corps());
        } catch (RuntimeException e) {
            plugin.getLogger().warning("[douane] réponse d'accès illisible pour " + mcUuid + " : " + e.getMessage());
            return null;
        }
    }

    /** {@code GET /v1/game/douane/dossier/:mcUuid?examinateur=<mcUuid>} (lecture inscrite à l'audit côté API). */
    public Reponse dossier(UUID candidat, UUID examinateur) {
        return envoyer("GET", "/game/douane/dossier/" + candidat + "?examinateur=" + examinateur, null, DELAI_DEFAUT_MS);
    }

    /** {@code POST /v1/game/douane/decision} — {@code corps} est le JSON exact (mêmes octets à chaque essai). */
    public Reponse decision(String corps) {
        return envoyer("POST", "/game/douane/decision", corps, DELAI_DEFAUT_MS);
    }

    /** {@code POST /v1/game/douane/presence} : {@code arrivee}, {@code appel} ou {@code depart}. */
    public Reponse presence(UUID mcUuid, String evenement, String reservationId, UUID examinateur) {
        return presence(mcUuid, evenement, reservationId, examinateur, null);
    }

    /**
     * {@code POST /v1/game/douane/presence} avec un motif facultatif (2 000 caractères au plus) : {@code retrait}
     * (motif, examinateur) et {@code report}. Le motif n'est jamais envoyé avec les autres événements.
     */
    public Reponse presence(UUID mcUuid, String evenement, String reservationId, UUID examinateur, String motif) {
        JsonObject o = new JsonObject();
        o.addProperty("mcUuid", mcUuid.toString());
        o.addProperty("evenement", evenement);
        if (reservationId != null && !reservationId.isBlank()) o.addProperty("reservationId", reservationId);
        if (examinateur != null) o.addProperty("mcUuidExaminateur", examinateur.toString());
        if (motif != null && !motif.isBlank()) {
            String m = motif.strip();
            o.addProperty("motif", m.length() > ReglesDouane.MOTIF_MAX ? m.substring(0, ReglesDouane.MOTIF_MAX) : m);
        }
        return envoyer("POST", "/game/douane/presence", o.toString(), DELAI_DEFAUT_MS);
    }

    /**
     * {@code POST /v1/game/characters/sync} (lot J, CANDIDATURE_ET_DOUANE.md §4.5) : {@code corps} est le JSON exact
     * {@code {"characters":[…]}}, de 1 à 200 photos. Bloquant : jamais sur le fil principal.
     */
    public Reponse personnages(String corps) {
        return envoyer("POST", "/game/characters/sync", corps, 8000L);
    }

    /* ================================================================ envoi signé */

    /** Un envoi : nouvel horodatage, nouveau nonce, nouvelle signature. {@code corps} null pour un GET. */
    Reponse envoyer(String methode, String suffixe, String corps, long delaiMs) {
        String raison = nonPret();
        if (raison != null) {
            avertir(raison);
            return new Reponse(0, null, raison, 0, false);
        }
        dernierAvertissement = null;
        String serveur = serveur();
        try {
            URI uri = URI.create(apiUrl() + suffixe);
            byte[] octets = corps == null ? new byte[0] : corps.getBytes(StandardCharsets.UTF_8);
            String horodatage = Long.toString(System.currentTimeMillis() / 1000L);
            String nonce = UUID.randomUUID().toString();
            String signature = SignatureJeu.signer(secret(),
                    SignatureJeu.chaine(methode, SignatureJeu.cheminSigne(uri), serveur, horodatage, nonce, octets));
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofMillis(Math.max(500L, delaiMs)))
                    .header("X-Reborn-Server", serveur)
                    .header("X-Reborn-Timestamp", horodatage)
                    .header("X-Reborn-Nonce", nonce)
                    .header("X-Reborn-Signature", signature);
            if ("POST".equals(methode)) {
                b.header("Content-Type", "application/json; charset=utf-8")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(octets));
            } else {
                b.GET();
            }
            HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonObject o = null;
            try {
                JsonElement el = JsonParser.parseString(res.body());
                if (el.isJsonObject()) o = el.getAsJsonObject();
            } catch (RuntimeException ignored) { }
            long retry = res.headers().firstValue("Retry-After").map(DouaneClient::entier).orElse(0L);
            return new Reponse(res.statusCode(), o, res.statusCode() == 200 ? null : messageErreur(o, res.statusCode()),
                    retry, false);
        } catch (java.io.IOException e) {
            return new Reponse(0, null, "API injoignable (" + e.getClass().getSimpleName() + ")", 0, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Reponse(0, null, "envoi interrompu", 0, true);
        } catch (RuntimeException e) {
            return new Reponse(0, null, "envoi impossible (" + e.getMessage() + ")", 0, true);
        }
    }

    private static long entier(String s) {
        try { return Math.max(0L, Long.parseLong(s.strip())); } catch (RuntimeException e) { return 0L; }
    }

    /** Message d'une erreur Nest : {@code message} (texte ou liste), sinon le code. */
    static String messageErreur(JsonObject o, int code) {
        if (o != null && o.has("message")) {
            JsonElement m = o.get("message");
            if (m.isJsonPrimitive()) return m.getAsString();
            if (m.isJsonArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonElement e : (JsonArray) m) {
                    if (sb.length() > 0) sb.append(" ; ");
                    sb.append(e.isJsonPrimitive() ? e.getAsString() : e.toString());
                }
                return sb.toString();
            }
        }
        return "HTTP " + code;
    }

    /* ================================================================ lecture */

    static Acces lireAcces(JsonObject o) {
        CandidatureClient.Candidature cand = null;
        if (o.has("candidature") && o.get("candidature").isJsonObject()) {
            // Tout le bloc (prénom, nom de famille, village, âge, type, candidature…) : lot J, §4.1.
            cand = CandidatureClient.Candidature.lire(o.getAsJsonObject("candidature"));
        }
        Place place = null;
        if (o.has("douane") && o.get("douane").isJsonObject()) {
            JsonObject d = o.getAsJsonObject("douane");
            place = new Place(str(d, "reservationId"), str(d, "seanceId"), instant(d, "convocationAt"),
                    instant(d, "entreeOuvreAt"), instant(d, "entreeFermeAt"), instant(d, "arriveeAt"),
                    bool(d, "deuxiemePassage"), str(d, "village"));
        }
        return new Acces(Aiguillage.Portee.lire(str(o, "portee")), str(o, "raison"), str(o, "message"),
                str(o, "phase"), bool(o, "staff"), str(o, "pseudo"), bool(o, "douaneActive"), cand, place);
    }

    static String str(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : null;
    }

    static boolean bool(JsonObject o, String k) {
        try { return o != null && o.has(k) && o.get(k).isJsonPrimitive() && o.get(k).getAsBoolean(); }
        catch (RuntimeException e) { return false; }
    }

    /** Date ISO 8601 de l'API → ms epoch ; 0 si absente ou illisible. */
    static long instant(JsonObject o, String k) {
        String s = str(o, k);
        if (s == null || s.isBlank()) return 0L;
        try { return Instant.parse(s).toEpochMilli(); } catch (RuntimeException e) { return 0L; }
    }
}
