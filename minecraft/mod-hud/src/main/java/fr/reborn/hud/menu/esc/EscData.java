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
import java.util.regex.Pattern;

/**
 * Data live du menu ESC (compteur Discord, dernières patch notes, streams).
 * Fetchée en tâche de fond depuis l'endpoint PUBLIC {@code GET /v1/menu/panel}
 * de l'API Reborn ; les panels lisent le dernier snapshot sans bloquer le
 * render thread. En cas d'échec (API injoignable, hors-launcher), le snapshot
 * reste {@code null} et les panels retombent sur leurs placeholders.
 *
 * <p>Échap e5 (lot 3) : champs facultatifs {@code shop.open} (Boutique ouverte, sinon « Bientôt ») et
 * {@code rewards} ({@code rate}, {@code intervalMinutes}, {@code nextPayoutAt}, {@code active}) ; absents, l'Échap
 * montre l'état d'aujourd'hui (Boutique fermée, +5 par heure de jeu comptée sur la session, charmes « À faire » sauf
 * ceux que le launcher passe en {@code -Dreborn.recompenses}).
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

    /**
     * Une chaîne Twitch suivie (nom d'affichage nettoyé + URL brute) ; {@code nomTexte} / {@code titreTexte} :
     * versions d'origine (accents, casse), pour le texte Inter.
     */
    public record Stream(String name, String url, boolean live, String title, String nomTexte, String titreTexte) {}

    /**
     * Une patch note (champs nettoyés ArcadePix + URL web brute) ; {@code titreTexte} : titre d'origine (Inter) ;
     * {@code versionTexte} / {@code dateCourte} : version d'origine et date « 3 nov. » de la petite carte Dev blog (e5).
     */
    public record PatchNote(String version, String title, String date, String url, String titreTexte,
                            String versionTexte, String dateCourte) {}

    /**
     * Récompenses de l'Échap (e5) : taux en RBCoins par heure de jeu, intervalle du versement, prochain versement
     * (epoch ms, -1 = inconnu : l'Échap compte alors l'heure de jeu de la session) et identifiants des charmes actifs
     * ({@code premium}, {@code boost}, {@code tag}, {@code bio}, {@code stream}).
     */
    public record Recompenses(int taux, long intervalleMs, long prochainVersement, java.util.Set<String> actifs) {
        static final Recompenses DEFAUT = new Recompenses(5, 3_600_000L, -1L, java.util.Set.of());
    }

    /**
     * Snapshot immuable exposé au render thread (volatile publish).
     * {@code discordInviteUrl} : invitation renvoyée par l'API, {@code null}
     * si absente ou refusée (voir {@link #invitationDiscord}). {@code boutiqueOuverte} : champ {@code shop.open} de
     * l'API (absent = fermée, « Bientôt », l'état d'aujourd'hui) ; {@code recompenses} : bloc {@code rewards}.
     */
    public record Snapshot(
        int discordMembers, int discordOnline,
        List<PatchNote> patchNotes, List<Stream> streams,
        String discordInviteUrl, boolean boutiqueOuverte, Recompenses recompenses
    ) {
        public int liveStreamCount() {
            int n = 0;
            for (Stream s : streams) if (s.live()) n++;
            return n;
        }
    }

    // ── Échap e5 : état de la boutique et des récompenses (bancs : valeurs imposées) ──

    /** Banc : état de la boutique imposé (null = celui de l'API). */
    public static Boolean debugBoutique;
    /** Banc : charmes actifs imposés (null = API ou launcher). */
    public static java.util.Set<String> debugActifs;

    /** La Boutique de l'Échap est ouverte (sinon « Bientôt »). */
    public static boolean boutiqueOuverte() {
        if (debugBoutique != null) return debugBoutique;
        Snapshot s = snapshot;
        return s != null && s.boutiqueOuverte();
    }

    /** Bloc Récompenses courant (jamais null). */
    public static Recompenses recompenses() {
        Snapshot s = snapshot;
        return s != null && s.recompenses() != null ? s.recompenses() : Recompenses.DEFAUT;
    }

    private static java.util.Set<String> actifsLauncher;

    /**
     * Charme actif pour ce compte : imposé par un banc, sinon passé par le launcher ({@code -Dreborn.recompenses=boost,tag},
     * comme le solde de RBCoins), sinon le bloc {@code rewards.active} de l'API.
     */
    public static boolean charmeActif(String id) {
        if (debugActifs != null) return debugActifs.contains(id);
        if (actifsLauncher == null) {
            java.util.Set<String> s = new java.util.HashSet<>();
            String v = System.getProperty("reborn.recompenses");
            if (v != null) for (String t : v.split("[,; ]+")) if (!t.isBlank()) s.add(t.trim().toLowerCase(Locale.ROOT));
            actifsLauncher = s;
        }
        if (!actifsLauncher.isEmpty()) return actifsLauncher.contains(id);
        return recompenses().actifs().contains(id);
    }

    private static long debutSession = -1L;
    private static Object connexionVue;

    /**
     * Millisecondes avant le prochain versement de RBCoins. Avec {@code rewards.nextPayoutAt} de l'API : le compte à
     * rebours réel ; sinon l'heure de jeu de la session en cours (comptée depuis la première ouverture de l'Échap de
     * la connexion), modulo l'intervalle.
     */
    public static long resteAvantVersement(long now) {
        Recompenses r = recompenses();
        long iv = Math.max(60_000L, r.intervalleMs());
        if (r.prochainVersement() > 0) {
            long reste = r.prochainVersement() - now;
            return reste > 0 ? Math.min(reste, iv) : Math.floorMod(reste, iv);
        }
        var mc = net.minecraft.client.Minecraft.getInstance();
        Object co = mc.getConnection();
        if (co != connexionVue || debutSession < 0) {
            connexionVue = co;
            debutSession = now;
        }
        return iv - Math.floorMod(now - debutSession, iv);
    }

    // ── version du serveur (pied de l'Échap, retour du 05/11 : « la version du serveur, c'est tout ») ──

    /** Banc : version du serveur imposée (null = données de l'API ; vide = rien). */
    public static String debugVersionServeur;
    /** Champ facultatif {@code server.version} (ou {@code serverVersion}) de {@code /menu/panel} (pas encore servi). */
    private static volatile String versionApi;
    /**
     * Version de la dernière patch note publiée (la plus récente par {@code publishedAt}, épinglée ou non) : la version
     * que le serveur annonce à ses joueurs, saisie par le staff dans le panneau. Déjà servie par {@code /menu/panel}.
     */
    private static volatile String versionNotes;
    private static final Pattern VERSION_SURE = Pattern.compile("[\\p{L}\\p{N} .+_-]{1,24}");

    /**
     * Version du serveur à montrer en bas à droite de l'Échap, ou {@code null} (rien n'est alors affiché — jamais la
     * version du mod ni celle du manifeste, qui suit les mods clients). Sources, dans l'ordre : banc ; un champ
     * explicite {@code server.version} de {@code /menu/panel} s'il est un jour servi ; sinon la version de la dernière
     * patch note publiée (même donnée que la carte Dev blog).
     */
    public static String versionServeur() {
        if (debugVersionServeur != null) return debugVersionServeur.isBlank() ? null : debugVersionServeur;
        String api = versionApi;
        return api != null ? api : versionNotes;
    }

    private static String versionSure(String v) {
        if (v == null) return null;
        String t = v.trim();
        return VERSION_SURE.matcher(t).matches() ? t : null;
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
        String invite = null;
        JsonElement d = root.get("discord");
        if (d != null && d.isJsonObject()) {
            JsonObject dj = d.getAsJsonObject();
            members = optInt(dj, "members", -1);
            online = optInt(dj, "online", -1);
            invite = invitationDiscord(optStr(dj, "inviteUrl", null));
        }

        List<PatchNote> notes = new ArrayList<>();
        String derniere = null;
        long derniereMs = Long.MIN_VALUE;
        JsonElement pn = root.get("patchNotes");
        if (pn != null && pn.isJsonArray()) {
            for (JsonElement e : pn.getAsJsonArray()) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                // version de la note la plus récente (les épinglées passent devant dans la liste, pas ici)
                String vn = versionSure(texte(optStr(o, "version", null), 24));
                if (vn != null) {
                    long ms = Long.MIN_VALUE + 1;
                    String pub = optStr(o, "publishedAt", null);
                    if (pub != null) {
                        try { ms = OffsetDateTime.parse(pub).toInstant().toEpochMilli(); } catch (Exception ignored) { }
                    }
                    if (ms > derniereMs) { derniereMs = ms; derniere = vn; }
                }
                notes.add(new PatchNote(
                    arcadeSafe(optStr(o, "version", null), 16),
                    arcadeSafe(optStr(o, "title", null), 60),
                    formatDate(optStr(o, "publishedAt", null)),
                    optStr(o, "url", null),
                    texte(optStr(o, "title", null), 90),
                    texte(optStr(o, "version", null), 16),
                    dateCourte(optStr(o, "publishedAt", null))));
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
                    arcadeSafe(optStr(o, "title", null), 60),
                    texte(optStr(o, "name", null), 24),
                    texte(optStr(o, "title", null), 90)));
            }
        }

        boolean boutique = false;
        JsonElement sh = root.get("shop");
        if (sh != null && sh.isJsonObject()) {
            JsonElement op = sh.getAsJsonObject().get("open");
            boutique = op != null && !op.isJsonNull() && op.getAsBoolean();
        }

        Recompenses rec = Recompenses.DEFAUT;
        JsonElement rw = root.get("rewards");
        if (rw != null && rw.isJsonObject()) {
            JsonObject r = rw.getAsJsonObject();
            java.util.Set<String> actifs = new java.util.HashSet<>();
            JsonElement ac = r.get("active");
            if (ac != null && ac.isJsonArray()) for (JsonElement e : ac.getAsJsonArray()) {
                try { actifs.add(e.getAsString().trim().toLowerCase(Locale.ROOT)); } catch (Exception ignored) { }
            }
            long prochain = -1L;
            String iso = optStr(r, "nextPayoutAt", null);
            if (iso != null) {
                try { prochain = OffsetDateTime.parse(iso).toInstant().toEpochMilli(); } catch (Exception ignored) { }
            }
            rec = new Recompenses(Math.max(0, optInt(r, "rate", 5)), Math.max(1, optInt(r, "intervalMinutes", 60)) * 60_000L,
                prochain, java.util.Set.copyOf(actifs));
        }

        String version = null;
        JsonElement sv = root.get("server");
        if (sv != null && sv.isJsonObject()) version = optStr(sv.getAsJsonObject(), "version", null);
        if (version == null) version = optStr(root, "serverVersion", null);
        versionApi = versionSure(version);
        versionNotes = derniere;

        return new Snapshot(members, online, notes, streams, invite, boutique, rec);
    }

    private static final String[] MOIS_COURTS = {"janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août",
        "sept.", "oct.", "nov.", "déc."};

    /** ISO-8601 → « 3 nov. » (petite carte Dev blog, Inter). */
    private static String dateCourte(String iso) {
        if (iso == null) return null;
        try {
            OffsetDateTime dt = OffsetDateTime.parse(iso);
            return dt.getDayOfMonth() + " " + MOIS_COURTS[dt.getMonthValue() - 1];
        } catch (Exception e) {
            return null;
        }
    }

    /** Banc : instantané imposé (aucune API). */
    public static void debug(Snapshot s) {
        snapshot = s;
        versionNotes = s == null || s.patchNotes().isEmpty() ? null : versionSure(s.patchNotes().get(0).versionTexte());
        lastFetch = System.currentTimeMillis();
    }

    /** Seuls liens acceptés pour les boutons Discord : une invitation Discord, rien d'autre. */
    private static final Pattern INVITATION_DISCORD = Pattern.compile(
        "https://(discord\\.gg|discord\\.com/invite)/[A-Za-z0-9-]{2,32}");

    /** L'URL si c'est bien une invitation Discord, sinon {@code null} (repli sur la constante). */
    static String invitationDiscord(String url) {
        if (url == null) return null;
        String u = url.trim();
        return INVITATION_DISCORD.matcher(u).matches() ? u : null;
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

    /** Texte d'origine nettoyé pour Inter : caractères de contrôle retirés, espaces réduits, tronqué. */
    private static String texte(String s, int max) {
        if (s == null) return null;
        String out = s.replaceAll("\\p{Cntrl}", " ").replaceAll(" {2,}", " ").trim();
        if (out.length() > max) out = out.substring(0, max).trim() + "…";
        return out;
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
