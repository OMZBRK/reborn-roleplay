package fr.reborn.hud.menu.esc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Data live du menu ESC (compteur Discord, dernières patch notes, streams).
 * Fetchée en tâche de fond depuis l'endpoint PUBLIC {@code GET /v1/menu/panel}
 * de l'API Reborn ; les panels lisent le dernier snapshot sans bloquer le
 * render thread. En cas d'échec (API injoignable, hors-launcher), le snapshot
 * reste {@code null} et les panels retombent sur leurs placeholders.
 *
 * <p>L'URL de base vient de la sysprop {@code reborn.apiUrl} passée par le
 * launcher (fallback prod). Le mod ne connaît aucun secret — l'endpoint est
 * public et ne renvoie que des données non sensibles.
 */
public final class EscData {

    private static final Logger LOG = LoggerFactory.getLogger("reborn-hud/esc-data");

    private static final String DEFAULT_API = "https://api.reborn-rp.com/v1";
    private static final long TTL_MS = 60_000L;

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(4))
        .build();

    private EscData() {}

    /** Une chaîne Twitch suivie (nom d'affichage nettoyé + URL brute). */
    public record Stream(String name, String url, boolean live, String title) {}

    /** Une patch note (champs nettoyés ArcadePix + URL web brute). */
    public record PatchNote(String version, String title, String date, String url) {}

    /** Snapshot immuable exposé au render thread (volatile publish). */
    public record Snapshot(
        int discordMembers, int discordOnline,
        List<PatchNote> patchNotes, List<Stream> streams
    ) {
        public int liveStreamCount() {
            int n = 0;
            for (Stream s : streams) if (s.live()) n++;
            return n;
        }
    }

    private static volatile Snapshot snapshot = null;
    private static volatile long lastFetch = 0L;
    private static volatile boolean fetching = false;

    public static Snapshot get() { return snapshot; }

    /** À appeler à l'ouverture du menu ESC : relance un fetch si périmé. */
    public static void refreshIfStale() {
        long now = System.currentTimeMillis();
        if (fetching) return;
        if (snapshot != null && now - lastFetch < TTL_MS) return;
        fetching = true;
        Thread t = new Thread(EscData::fetch, "reborn-esc-data");
        t.setDaemon(true);
        t.start();
    }

    /** URL de base de l'API Reborn (sysprop {@code reborn.apiUrl}, fallback prod). */
    public static String apiBase() {
        String v = System.getProperty("reborn.apiUrl");
        if (v == null || v.isBlank()) return DEFAULT_API;
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }

    private static void fetch() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiBase() + "/menu/panel"))
                .timeout(Duration.ofSeconds(6))
                .header("Accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                LOG.warn("menu/panel HTTP {}", res.statusCode());
                return;
            }
            snapshot = parse(JsonParser.parseString(res.body()).getAsJsonObject());
            lastFetch = System.currentTimeMillis();
            LOG.info("esc data ok : discord={}/{}, patchNotes={}, streams={} (live={})",
                snapshot.discordMembers(), snapshot.discordOnline(),
                snapshot.patchNotes().size(), snapshot.streams().size(),
                snapshot.liveStreamCount());
        } catch (Exception e) {
            LOG.warn("esc data fetch échoué : {}", e.toString());
        } finally {
            fetching = false;
        }
    }

    private static Snapshot parse(JsonObject root) {
        int members = -1, online = -1;
        JsonElement d = root.get("discord");
        if (d != null && d.isJsonObject()) {
            JsonObject dj = d.getAsJsonObject();
            members = optInt(dj, "members", -1);
            online = optInt(dj, "online", -1);
        }

        List<PatchNote> notes = new ArrayList<>();
        JsonElement pn = root.get("patchNotes");
        if (pn != null && pn.isJsonArray()) {
            for (JsonElement e : pn.getAsJsonArray()) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                notes.add(new PatchNote(
                    arcadeSafe(optStr(o, "version", null), 16),
                    arcadeSafe(optStr(o, "title", null), 60),
                    formatDate(optStr(o, "publishedAt", null)),
                    optStr(o, "url", null)));
            }
        }

        List<Stream> streams = new ArrayList<>();
        JsonElement st = root.get("streams");
        if (st != null && st.isJsonArray()) {
            for (JsonElement e : st.getAsJsonArray()) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                streams.add(new Stream(
                    arcadeSafe(optStr(o, "name", null), 20),
                    optStr(o, "url", null),
                    o.has("live") && !o.get("live").isJsonNull() && o.get("live").getAsBoolean(),
                    arcadeSafe(optStr(o, "title", null), 60)));
            }
        }

        return new Snapshot(members, online, notes, streams);
    }

    private static int optInt(JsonObject o, String k, int def) {
        try {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : def;
        } catch (Exception e) { return def; }
    }

    private static String optStr(JsonObject o, String k, String def) {
        try {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
        } catch (Exception e) { return def; }
    }

    /** ISO-8601 → "12 MAI 2026" (majuscules ASCII pour ArcadePix). */
    private static String formatDate(String iso) {
        if (iso == null) return null;
        try {
            OffsetDateTime dt = OffsetDateTime.parse(iso);
            String month = dt.getMonth().getDisplayName(TextStyle.SHORT, Locale.FRENCH);
            return arcadeSafe(dt.getDayOfMonth() + " " + month + " " + dt.getYear(), 24);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * ArcadePix ne connaît que l'ASCII majuscule : on retire les accents,
     * on met en majuscules, on jette les glyphes non imprimables, et on
     * tronque. {@code null} → {@code null}.
     */
    public static String arcadeSafe(String s, int max) {
        if (s == null) return null;
        String noAccent = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "");
        StringBuilder sb = new StringBuilder(noAccent.length());
        for (char c : noAccent.toUpperCase(Locale.ROOT).toCharArray()) {
            if (c >= 0x20 && c < 0x7F) sb.append(c);
            else sb.append(' ');
        }
        String out = sb.toString().replaceAll(" {2,}", " ").trim();
        if (out.length() > max) out = out.substring(0, max).trim();
        return out;
    }
}
