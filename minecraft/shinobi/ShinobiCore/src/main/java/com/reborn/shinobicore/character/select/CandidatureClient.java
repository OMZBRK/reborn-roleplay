package com.reborn.shinobicore.character.select;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.douane.DouaneClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Lecture de la candidature whitelist <b>acceptée</b> d'un joueur, pour pré-remplir et verrouiller la création de son
 * premier personnage (CANDIDATURE_ET_DOUANE.md §4.1-4.2 ; le staff n'est jamais verrouillé).
 *
 * <p>Source : le bloc {@code candidature} de {@code GET <api>/game/acces/<mcUuid>}, signé par le garde du jeu
 * ({@link DouaneClient}, contrat {@code apps/api/src/game/game-signature.ts}) : prénom, nom de famille, clan (« Autre »,
 * sans clan), village, âge, type, identifiant de la candidature, staff.
 *
 * <p>Repli historique : si les routes signées ne sont pas réglées ({@code reborn.server-id} / secret du jeu absents),
 * {@code GET <api>/game/candidature/<mcUuid>} signé HMAC-SHA256 sur le mcUuid avec {@code REBORN_WEBHOOK_SECRET}
 * (même vue côté API). L'API peut couper cette route (410) : la lecture échoue alors proprement.
 *
 * <p>Dégradation : toute erreur (réseau, non-200) renvoie {@code null}. {@link #configure()} dit si une lecture est
 * possible du tout : sans aucun secret, la création reste libre comme avant (serveur de dev sans API).
 */
public final class CandidatureClient {

    /**
     * Vue candidature (champs absents = {@code null} / {@code false}).
     *
     * @param found         candidature acceptée (phase ACCEPTE)
     * @param name          prénom, pré-rempli et modifiable
     * @param familyName    nom de famille libre, pré-rempli et modifiable ({@code null} pour l'ancien formulaire)
     * @param clan          toujours « Autre » (sans clan) depuis le 24/10 : le clan se rejoint en session
     * @param village       village verrouillé (« Konohagakure »…)
     * @param age           âge du personnage verrouillé, ou {@code null}
     * @param type          {@code NINJA} / {@code CIVIL}, ou {@code null} (= NINJA)
     * @param applicationId identifiant de la candidature (marque {@code application} du premier personnage)
     * @param sex           sexe déclaré, simple suggestion ({@code Homme} / {@code Femme}) : le sexe reste libre
     * @param consumed      vrai si l'API sait déjà la candidature liée à un personnage
     * @param staff         compte staff : rien n'est verrouillé
     */
    public record Candidature(boolean found, String name, String familyName, String clan, String village,
                              Integer age, String type, String applicationId, String sex, boolean consumed,
                              boolean staff) {

        /** Bloc {@code candidature} de l'API (même vue sur {@code /game/acces} et {@code /game/candidature}). */
        public static Candidature lire(JsonObject o) {
            if (o == null) return null;
            boolean staff = bool(o, "staff");
            if (!bool(o, "found")) {
                return new Candidature(false, null, null, null, null, null, null, null, null, false, staff);
            }
            return new Candidature(true, str(o, "name"), premier(o, "familyName", "lastName"), str(o, "clan"),
                    str(o, "village"), entier(o, "age"), type(premier(o, "type", "characterType")),
                    premier(o, "applicationId", "application"), sexe(premier(o, "sex", "sexe", "characterSex")),
                    bool(o, "consumed"), staff);
        }

        private static String premier(JsonObject o, String... cles) {
            for (String k : cles) {
                String v = str(o, k);
                if (v != null && !v.isBlank()) return v.strip();
            }
            return null;
        }

        private static String type(String v) {
            if (v == null) return null;
            return "CIVIL".equalsIgnoreCase(v) ? "CIVIL" : "NINJA".equalsIgnoreCase(v) ? "NINJA" : null;
        }

        /** HOMME / Homme → « Homme » (valeur du mod). */
        private static String sexe(String v) {
            if (v == null) return null;
            if ("HOMME".equalsIgnoreCase(v)) return "Homme";
            if ("FEMME".equalsIgnoreCase(v)) return "Femme";
            return null;
        }

        private static String str(JsonObject o, String k) {
            JsonElement e = o.get(k);
            return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
        }

        private static boolean bool(JsonObject o, String k) {
            try {
                JsonElement e = o.get(k);
                return e != null && e.isJsonPrimitive() && e.getAsBoolean();
            } catch (RuntimeException ex) { return false; }
        }

        private static Integer entier(JsonObject o, String k) {
            try {
                JsonElement e = o.get(k);
                return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsInt() : null;
            } catch (RuntimeException ex) { return null; }
        }
    }

    private final ShinobiCore plugin;
    private final DouaneClient jeu;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public CandidatureClient(ShinobiCore plugin) {
        this.plugin = plugin;
        this.jeu = new DouaneClient(plugin);
    }

    private String apiUrl() {
        String u = plugin.getConfig().getString("reborn.api-url", "http://localhost:3000/v1");
        if (u == null || u.isBlank()) u = "http://localhost:3000/v1";
        u = u.strip();
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

    private String secretHistorique() {
        String s = plugin.getConfig().getString("reborn.webhook-secret", "");
        if (s == null || s.isBlank()) s = System.getenv("REBORN_WEBHOOK_SECRET");
        return s;
    }

    /** Vrai si une lecture est possible (routes signées réglées, ou secret historique présent). */
    public boolean configure() {
        if (jeu.nonPret() == null) return true;
        String s = secretHistorique();
        return s != null && !s.isBlank();
    }

    /**
     * Interroge l'API. <b>Bloquant → À APPELER EN ASYNC</b> (jamais sur le fil principal). Renvoie {@code null} si
     * indisponible / erreur.
     */
    public Candidature fetch(UUID mcUuid) {
        if (jeu.nonPret() == null) {
            DouaneClient.Acces a = jeu.acces(mcUuid, 4000L);
            if (a == null) return null;
            // Bloc absent : compte inconnu de l'API. Seul le grade (staff) est connu.
            return a.candidature() != null ? a.candidature()
                    : new Candidature(false, null, null, null, null, null, null, null, null, false, a.staff());
        }
        return fetchHistorique(mcUuid);
    }

    /** {@code GET /game/candidature/<mcUuid>} (route historique, secret partagé). */
    private Candidature fetchHistorique(UUID mcUuid) {
        String secret = secretHistorique();
        if (secret == null || secret.isBlank()) {
            plugin.getLogger().warning("[candidature] ni routes signées (reborn.server-id + secret du jeu) ni secret "
                    + "historique (reborn.webhook-secret / env REBORN_WEBHOOK_SECRET) → création non verrouillée.");
            return null;
        }
        String id = mcUuid.toString();
        try {
            String sig = hmacHex(secret, id);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl() + "/game/candidature/" + id))
                    .timeout(Duration.ofSeconds(6))
                    .header("X-Reborn-Signature", sig)
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) {
                plugin.getLogger().info("[candidature] " + id + " → HTTP " + res.statusCode());
                return null;
            }
            return Candidature.lire(JsonParser.parseString(res.body()).getAsJsonObject());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            plugin.getLogger().warning("[candidature] échec pour " + id + " : " + e.getMessage());
            return null;
        }
    }

    private static String hmacHex(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(out.length * 2);
        for (byte b : out) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
