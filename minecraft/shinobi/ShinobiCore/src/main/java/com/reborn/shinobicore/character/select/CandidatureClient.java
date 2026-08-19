package com.reborn.shinobicore.character.select;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reborn.shinobicore.ShinobiCore;

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
 * Petit client HTTP — <b>1ʳᵉ intégration HTTP de ShinobiCore</b> — qui interroge
 * l'API Reborn pour la candidature whitelist <b>validée</b> d'un joueur, afin de
 * verrouiller le village + le clan du wizard de création de personnage
 * (les joueurs choisissent librement le reste ; le staff n'est jamais verrouillé).
 *
 * <p>Endpoint : {@code GET <api>/game/candidature/<mcUuid>}, signé HMAC-SHA256
 * sur le mcUuid avec le secret partagé {@code REBORN_WEBHOOK_SECRET} :
 * header {@code X-Reborn-Signature = hex(HMAC-SHA256(secret, mcUuid))}.
 *
 * <p>Config ({@code config.yml}) :
 * <pre>
 * reborn:
 *   api-url: "http://localhost:3000/v1"   # défaut si absent
 *   webhook-secret: ""                      # sinon lu depuis l'env REBORN_WEBHOOK_SECRET
 * </pre>
 *
 * <p>Dégradation propre : toute erreur (pas de secret, réseau, non-200) renvoie
 * {@code null} → le roster part sans bloc {@code candidature} → le mod ne
 * verrouille rien. Jamais bloquant pour la sélection.
 */
public final class CandidatureClient {

    /** Vue candidature (champs absents = {@code null} / {@code false}). */
    public record Candidature(boolean found, String name, String clan,
                              String village, boolean staff) {}

    private final ShinobiCore plugin;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public CandidatureClient(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    private String apiUrl() {
        String u = plugin.getConfig().getString("reborn.api-url", "http://localhost:3000/v1");
        if (u == null || u.isBlank()) u = "http://localhost:3000/v1";
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

    private String secret() {
        String s = plugin.getConfig().getString("reborn.webhook-secret", "");
        if (s == null || s.isBlank()) s = System.getenv("REBORN_WEBHOOK_SECRET");
        return s;
    }

    /**
     * Interroge l'API. <b>Bloquant → À APPELER EN ASYNC</b> (jamais sur le main
     * thread). Renvoie {@code null} si indisponible / erreur.
     */
    public Candidature fetch(UUID mcUuid) {
        String secret = secret();
        if (secret == null || secret.isBlank()) {
            plugin.getLogger().warning("[candidature] aucun secret configuré "
                    + "(reborn.webhook-secret / env REBORN_WEBHOOK_SECRET) → grisage désactivé.");
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
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                plugin.getLogger().info("[candidature] " + id + " → HTTP " + res.statusCode());
                return null;
            }
            JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
            boolean found = o.has("found") && o.get("found").getAsBoolean();
            boolean staff = o.has("staff") && o.get("staff").getAsBoolean();
            if (!found) {
                return new Candidature(false, null, null, null, staff);
            }
            return new Candidature(true,
                    str(o, "name"), str(o, "clan"), str(o, "village"), staff);
        } catch (Exception e) {
            plugin.getLogger().warning("[candidature] échec pour " + id + " : " + e.getMessage());
            return null;
        }
    }

    private static String str(JsonObject o, String key) {
        return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString() : null;
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
