package com.reborn.shinobicore.panel;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Panel command bridge — <b>outbound</b> poller that lets the Reborn web panel
 * trigger a short whitelist of console reloads (e.g. {@code /nexo reload}) on
 * the live server <b>without RCON and without any inbound port</b>.
 *
 * <p>Nothing listens for inbound connections: the game server itself calls the
 * panel API on a timer. Every {@code poll-seconds} the async task:
 * <ol>
 *   <li>{@code GET <api>/files/commands/pending} signed with
 *       {@code X-Reborn-Signature = hex(HMAC-SHA256(secret, "pending"))}.
 *       Response: {@code {"commands":[{"id":"<uuid>","command":"nexo reload"}, ...]}}.</li>
 *   <li>For each command, re-checks its prefix against a plugin-side whitelist
 *       (defense in depth — the payload is never trusted blindly), then
 *       dispatches the allowed ones on the <b>main thread</b> as the console
 *       sender and captures the boolean result.</li>
 *   <li>{@code POST <api>/files/commands/ack} with body
 *       {@code {"results":[{"id":"<uuid>","ok":true,"output":"..."}]}} signed
 *       with {@code X-Reborn-Signature = hex(HMAC-SHA256(secret, <exact body bytes>))}.</li>
 * </ol>
 *
 * <p>Config ({@code config.yml}):
 * <pre>
 * panel-bridge:
 *   enabled: false
 *   api-url: "https://api.reborn-rp.com/v1"
 *   hmac-secret: ""       # blank =&gt; env REBORN_WEBHOOK_SECRET
 *   poll-seconds: 5
 * </pre>
 *
 * <p>Robust to the API being unreachable: any failure is logged at a low level
 * and retried on the next tick — never a SEVERE spam loop.
 */
public final class PanelBridge {

    /**
     * Command prefixes the plugin is willing to dispatch. Even though the API
     * only ever enqueues whitelisted reloads, the plugin verifies again so a
     * compromised / spoofed panel response can never run arbitrary commands.
     */
    private static final String[] ALLOWED_PREFIXES = {
            "nexo reload",
            "ms reload",
            "mm reload",
            "meg reload",
    };

    private final ShinobiCore plugin;
    private final Gson gson = new Gson();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private BukkitTask task;

    public PanelBridge(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------------------------------------- lifecycle */

    /** Schedule the async poll loop. No-op (with a log line) when disabled or
     *  when no HMAC secret is resolvable. */
    public void start() {
        stop();
        // Désactivé UNIQUEMENT si explicitement mis à false. Sinon (section absente
        // ou true), on démarre dès qu'un secret est résolvable — ce qui réutilise
        // le `reborn.webhook-secret` déjà présent dans la config (robuste : pas
        // besoin d'une section panel-bridge dédiée que l'on risque d'écraser).
        boolean explicitlyDisabled = plugin.getConfig().isSet("panel-bridge.enabled")
                && !plugin.getConfig().getBoolean("panel-bridge.enabled");
        if (explicitlyDisabled) {
            plugin.getLogger().info("Panel bridge desactive (panel-bridge.enabled=false).");
            return;
        }
        String secret = secret();
        if (secret == null || secret.isBlank()) {
            plugin.getLogger().warning("Panel bridge : aucun secret configure "
                    + "(panel-bridge.hmac-secret / reborn.webhook-secret / env "
                    + "REBORN_WEBHOOK_SECRET) — bridge non demarre.");
            return;
        }
        long seconds = plugin.getConfig().getLong("panel-bridge.poll-seconds", 5L);
        if (seconds <= 0) seconds = 5L;
        long ticks = seconds * 20L;
        // Async: all HTTP + polling stays off the main thread. Only the actual
        // command dispatch hops back onto the main thread (see runOnMain).
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::poll, ticks, ticks);
        plugin.getLogger().info("Panel bridge actif — poll " + apiUrl()
                + "/files/commands/pending toutes les " + seconds + "s.");
    }

    public void stop() {
        if (task != null) {
            try { task.cancel(); } catch (Throwable ignore) {}
            task = null;
        }
    }

    /* --------------------------------------------------------------- polling */

    /** One poll round. Runs async; swallows every failure (logged FINE) and
     *  retries next tick. */
    private void poll() {
        String secret = secret();
        if (secret == null || secret.isBlank()) return;

        List<JsonObject> pending;
        try {
            pending = fetchPending(secret);
        } catch (Exception e) {
            // API unreachable / transient — low-level log, never SEVERE spam.
            plugin.getLogger().fine("[panel-bridge] pull impossible : " + e.getMessage());
            return;
        }
        if (pending.isEmpty()) return;

        JsonArray results = new JsonArray();
        for (JsonObject cmd : pending) {
            String id = str(cmd, "id");
            String command = str(cmd, "command");
            if (id == null || id.isBlank()) continue;

            String normalized = command == null ? "" : command.trim();
            if (normalized.startsWith("/")) normalized = normalized.substring(1).trim();

            if (!isAllowed(normalized)) {
                plugin.getLogger().warning("[panel-bridge] commande refusee (hors whitelist) : "
                        + normalized);
                results.add(result(id, false, "command not allowed"));
                continue;
            }

            boolean ok = dispatchOnMain(normalized);
            results.add(result(id, ok, ok ? "dispatched" : "dispatch returned false"));
        }

        if (results.isEmpty()) return;
        try {
            postAck(secret, results);
        } catch (Exception e) {
            plugin.getLogger().fine("[panel-bridge] ack impossible : " + e.getMessage());
        }
    }

    /** GET the pending queue. Returns an empty list on a non-200. */
    private List<JsonObject> fetchPending(String secret) throws Exception {
        String sig = hmacHex(secret, "pending".getBytes(StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl() + "/files/commands/pending"))
                .timeout(Duration.ofSeconds(10))
                .header("X-Reborn-Signature", sig)
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            plugin.getLogger().fine("[panel-bridge] pending → HTTP " + res.statusCode());
            return List.of();
        }
        List<JsonObject> out = new ArrayList<>();
        JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
        if (root.has("commands") && root.get("commands").isJsonArray()) {
            for (var el : root.getAsJsonArray("commands")) {
                if (el != null && el.isJsonObject()) out.add(el.getAsJsonObject());
            }
        }
        return out;
    }

    /** POST the ack. The signature is computed over the <b>exact bytes</b>
     *  transmitted so the API's raw-body HMAC check matches. */
    private void postAck(String secret, JsonArray results) throws Exception {
        JsonObject body = new JsonObject();
        body.add("results", results);
        byte[] bytes = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
        String sig = hmacHex(secret, bytes);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl() + "/files/commands/ack"))
                .timeout(Duration.ofSeconds(10))
                .header("X-Reborn-Signature", sig)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            plugin.getLogger().fine("[panel-bridge] ack → HTTP " + res.statusCode());
        }
    }

    /**
     * Dispatch a whitelisted command on the main thread as the console sender
     * and hand the boolean result back to this async task. Bukkit commands are
     * not thread-safe, so the actual {@code dispatchCommand} always runs on the
     * main thread; the async poller blocks on the {@link CompletableFuture}
     * until the main-thread task completes.
     */
    private boolean dispatchOnMain(String command) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                future.complete(ok);
            } catch (Throwable t) {
                plugin.getLogger().warning("[panel-bridge] echec dispatch '" + command
                        + "' : " + t.getMessage());
                future.complete(false);
            }
        });
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger().fine("[panel-bridge] dispatch non confirme '" + command
                    + "' : " + e.getMessage());
            return false;
        }
    }

    /* ---------------------------------------------------------------- config */

    private String apiUrl() {
        String u = plugin.getConfig().getString("panel-bridge.api-url", "");
        // Repli sur la section reborn: (déjà utilisée par CandidatureClient).
        if (u == null || u.isBlank()) u = plugin.getConfig().getString("reborn.api-url", "");
        if (u == null || u.isBlank()) u = "https://api.reborn-rp.com/v1";
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

    /** Secret depuis la config ; repli sur reborn.webhook-secret (même secret que
     *  l'API) puis sur l'env REBORN_WEBHOOK_SECRET. Jamais loggé. */
    private String secret() {
        String s = plugin.getConfig().getString("panel-bridge.hmac-secret", "");
        if (s == null || s.isBlank()) s = plugin.getConfig().getString("reborn.webhook-secret", "");
        if (s == null || s.isBlank()) s = System.getenv("REBORN_WEBHOOK_SECRET");
        return s;
    }

    /* ---------------------------------------------------------------- helpers */

    private static boolean isAllowed(String command) {
        String low = command.toLowerCase(Locale.ROOT);
        for (String p : ALLOWED_PREFIXES) {
            if (low.startsWith(p)) return true;
        }
        return false;
    }

    private static JsonObject result(String id, boolean ok, String output) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("ok", ok);
        o.addProperty("output", output);
        return o;
    }

    private static String str(JsonObject o, String key) {
        return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString() : null;
    }

    private static String hmacHex(String secret, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] out = mac.doFinal(data);
        StringBuilder sb = new StringBuilder(out.length * 2);
        for (byte b : out) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
