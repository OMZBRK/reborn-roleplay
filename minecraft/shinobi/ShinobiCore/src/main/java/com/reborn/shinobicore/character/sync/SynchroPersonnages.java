package com.reborn.shinobicore.character.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.douane.DouaneClient;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Temps de jeu par personnage et synchronisation vers l'API (lot J, CANDIDATURE_ET_DOUANE.md §4.5) : le serveur de jeu
 * n'a pas de port entrant, c'est le plugin qui pousse des <b>photos complètes</b> sur
 * {@code POST /v1/game/characters/sync}, signé comme les autres routes du jeu ({@link DouaneClient}).
 *
 * <ul>
 *   <li><b>Temps de jeu</b> : chaque minute, +1 au personnage actif de chaque joueur en jeu (hors sélection), et sa
 *       dernière partie. Compté même si la synchronisation est coupée. Ces deux valeurs ne déclenchent pas à elles
 *       seules l'auto-sauvegarde du roster (une fois toutes les dix minutes de jeu au plus).</li>
 *   <li><b>Déclencheurs</b> : création, changement de personnage, déconnexion, suppression (photo {@code deleted}),
 *       envoi immédiat ; et chaque minute, tout personnage chargé dont le contenu a changé (mort RP, actions du staff,
 *       grade, nom…). Le temps de jeu seul part au plus toutes les {@value #TEMPS_SEUL_MS} ms.</li>
 *   <li><b>Rattrapage complet</b> au démarrage (et quand les réglages deviennent valables) : tous les fichiers
 *       {@code characters/*.yml}, lus hors fil principal.</li>
 *   <li><b>File de reprise</b> : une photo par personnage (la plus récente remplace l'ancienne), gardée sur disque
 *       ({@code synchro-personnages.json}) pour qu'une suppression ne se perde pas. Renvoi <b>seulement</b> sur erreur
 *       réseau, 429 ({@code Retry-After}), 404 (route pas encore déployée), 5xx, ou 401 / 403 (réglage de la
 *       signature, renvoi espacé de {@value #RENVOI_MAX_MS} ms) ; jamais pour une photo {@code cree}, {@code maj},
 *       {@code perime}, {@code refuse} ou {@code conflit}, ni pour un lot refusé en 400 / 413.</li>
 * </ul>
 *
 * <p>Idempotence : chaque photo porte son {@code snapshotAt} ; l'API n'écrase jamais une photo plus récente. Un seul
 * envoi en vol à la fois, 200 photos au plus par envoi. Fil principal, sauf l'appel HTTP et la lecture du rattrapage.
 */
public final class SynchroPersonnages implements Listener {

    /** Photos au plus par envoi (contrat de l'API). */
    static final int LOT_MAX = 200;
    /** Le temps de jeu seul (sans autre changement) part au plus toutes les 5 minutes. */
    static final long TEMPS_SEUL_MS = 5 * 60_000L;
    private static final long RENVOI_MIN_MS = 30_000L, RENVOI_MAX_MS = 10 * 60_000L;

    /** Une photo prête à partir. {@code cle} : contenu sans temps de jeu ni dates de suivi. */
    record Photo(UUID id, String cle, int minutes, boolean deleted, long snapshotAt, String json) {}

    private final ShinobiCore plugin;
    private final DouaneClient client;
    private final File fichier;

    /** characterId → dernière photo à envoyer (ordre d'arrivée). */
    private final LinkedHashMap<UUID, Photo> enAttente = new LinkedHashMap<>();
    /** characterId → clé de la dernière photo traitée par l'API, et son temps de jeu, et quand. */
    private final Map<UUID, String> derniereCle = new HashMap<>();
    private final Map<UUID, Integer> dernieresMinutes = new HashMap<>();
    private final Map<UUID, Long> dernierTraitement = new HashMap<>();

    private BukkitTask minute;
    private BukkitTask envoiPlanifie;
    private boolean enVol;
    private long prochainEssai;
    private int echecs;
    private boolean etaitPret;
    private boolean rattrapageEnCours;
    /** Écritures du fichier de reprise, dans l'ordre (la plus récente gagne). */
    private final Object verrouFichier = new Object();
    private long ecritureDemandee, ecritureFaite;

    public SynchroPersonnages(ShinobiCore plugin) {
        this.plugin = plugin;
        this.client = new DouaneClient(plugin);
        this.fichier = new File(plugin.getDataFolder(), "synchro-personnages.json");
    }

    /* ================================================================ cycle de vie */

    public void demarrer() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        charger();
        minute = Bukkit.getScheduler().runTaskTimer(plugin, this::chaqueMinute, 1200L, 1200L);
        if (active() && client.nonPret() == null) {
            etaitPret = true;
            rattrapage();
        } else if (active()) {
            plugin.getLogger().warning("[synchro-personnages] en attente des réglages : " + client.nonPret() + ".");
        }
    }

    /** Arrêt du serveur : la file restante est écrite sur disque (renvoyée au prochain démarrage). */
    public void arreter() {
        HandlerList.unregisterAll(this);
        if (minute != null) minute.cancel();
        if (envoiPlanifie != null) envoiPlanifie.cancel();
        minute = envoiPlanifie = null;
        ecrire(instantane(), ++ecritureDemandee);
    }

    /** {@code reborn.sync-personnages} (vrai par défaut). */
    private boolean active() {
        return plugin.getConfig().getBoolean("reborn.sync-personnages", true);
    }

    private boolean pret() {
        return active() && client.nonPret() == null;
    }

    /* ================================================================ déclencheurs */

    /** Photo de {@code c} à envoyer ; {@code immediat} : sous 2 s, sans attendre la vérification de la minute. */
    public void signaler(ShinobiCharacter c, boolean immediat) {
        if (c == null || !pret()) return;
        ajouter(photo(c, false, System.currentTimeMillis()));
        if (immediat) planifierEnvoi(40L);
    }

    /** Suppression en jeu : dernière photo {@code deleted: true}, gardée sur disque jusqu'à la réponse de l'API. */
    public void supprime(ShinobiCharacter c) {
        if (c == null || !active()) return;
        ajouter(photo(c, true, System.currentTimeMillis()));
        ecrireAsync();
        planifierEnvoi(40L);
    }

    /** Avant la sauvegarde de la déconnexion ({@code CharacterLifecycleListener}) : dernière partie, photo. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        ShinobiCharacter c = plugin.characters().getActive(e.getPlayer().getUniqueId());
        if (c == null) return;
        c.setLastSeenAt(System.currentTimeMillis());
        signaler(c, true);
    }

    /* ================================================================ chaque minute */

    private void chaqueMinute() {
        long maintenant = System.currentTimeMillis();
        // Temps de jeu : le personnage actif des joueurs en jeu (pas en sélection, pas sans personnage).
        var selection = plugin.characterSelect();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (selection != null && selection.isSelecting(p.getUniqueId())) continue;
            ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
            if (c == null) continue;
            c.addPlayMinute();
            c.setLastSeenAt(maintenant);
        }
        boolean pret = pret();
        if (pret && !etaitPret) {
            plugin.getLogger().info("[synchro-personnages] réglages valables : rattrapage complet.");
            etaitPret = true;
            rattrapage();
        }
        etaitPret = pret;
        if (!pret) return;
        verifier(maintenant);
        envoyer();
    }

    /** Tout personnage chargé dont le contenu a changé, ou dont le temps de jeu n'est pas parti depuis 5 minutes. */
    private void verifier(long maintenant) {
        for (List<ShinobiCharacter> roster : plugin.characters().rosterView().values()) {
            for (ShinobiCharacter c : new ArrayList<>(roster)) {
                PhotoPersonnage.Donnees d = PhotoPersonnage.de(c, false);
                String cle = PhotoPersonnage.cle(d);
                Photo attente = enAttente.get(c.id());
                if (attente != null && !attente.deleted() && attente.cle().equals(cle)
                        && attente.minutes() == d.playMinutes()) continue;
                boolean change = !cle.equals(derniereCle.get(c.id()));
                Integer min = dernieresMinutes.get(c.id());
                boolean temps = min == null || min != d.playMinutes();
                long depuis = maintenant - dernierTraitement.getOrDefault(c.id(), 0L);
                if (change || (temps && depuis >= TEMPS_SEUL_MS)) ajouter(nouvelle(d, maintenant));
            }
        }
    }

    /* ================================================================ file */

    private Photo photo(ShinobiCharacter c, boolean deleted, long snapshotAt) {
        return nouvelle(PhotoPersonnage.de(c, deleted), snapshotAt);
    }

    private static Photo nouvelle(PhotoPersonnage.Donnees d, long snapshotAt) {
        return new Photo(d.characterId(), PhotoPersonnage.cle(d), d.playMinutes(), d.deleted(), snapshotAt,
                PhotoPersonnage.json(d, snapshotAt).toString());
    }

    /** La photo la plus récente remplace l'ancienne ; une suppression n'est jamais remplacée par une photo plus vieille. */
    private void ajouter(Photo p) {
        Photo avant = enAttente.get(p.id());
        if (avant != null && avant.snapshotAt() > p.snapshotAt()) return;
        if (avant != null && avant.deleted() && !p.deleted()) return;
        enAttente.remove(p.id());
        enAttente.put(p.id(), p);
    }

    private void planifierEnvoi(long ticks) {
        if (envoiPlanifie != null || minute == null) return;
        envoiPlanifie = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            envoiPlanifie = null;
            envoyer();
        }, ticks);
    }

    /* ================================================================ envoi */

    private void envoyer() {
        if (enVol || enAttente.isEmpty() || !pret()) return;
        long maintenant = System.currentTimeMillis();
        if (maintenant < prochainEssai) {
            planifierEnvoi(Math.max(20L, (prochainEssai - maintenant) / 50L));
            return;
        }
        List<Photo> lot = new ArrayList<>(Math.min(LOT_MAX, enAttente.size()));
        for (Photo p : enAttente.values()) {
            lot.add(p);
            if (lot.size() >= LOT_MAX) break;
        }
        StringBuilder corps = new StringBuilder(lot.size() * 400 + 20).append("{\"characters\":[");
        for (int i = 0; i < lot.size(); i++) {
            if (i > 0) corps.append(',');
            corps.append(lot.get(i).json());
        }
        corps.append("]}");
        String body = corps.toString();
        enVol = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DouaneClient.Reponse r;
            try {
                r = client.personnages(body);
            } catch (RuntimeException e) {
                r = new DouaneClient.Reponse(0, null, "envoi impossible (" + e.getMessage() + ")", 0, true);
            }
            DouaneClient.Reponse rep = r;
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> traiter(lot, rep));
        });
    }

    private void traiter(List<Photo> lot, DouaneClient.Reponse r) {
        enVol = false;
        long maintenant = System.currentTimeMillis();
        if (r.ok()) {
            echecs = 0;
            prochainEssai = 0L;
            boolean[] vus = new boolean[lot.size()];
            JsonElement res = r.corps().get("resultats");
            if (res != null && res.isJsonArray()) {
                for (JsonElement el : res.getAsJsonArray()) {
                    if (!el.isJsonObject()) continue;
                    JsonObject o = el.getAsJsonObject();
                    int i = entier(o.get("index"));
                    if (i < 0 || i >= lot.size() || vus[i]) continue;
                    vus[i] = true;
                    resultat(lot.get(i), o, maintenant);
                }
            }
            int sans = 0;
            for (boolean v : vus) if (!v) sans++;
            if (sans > 0) {
                // Réponse incomplète : les photos sans résultat restent dans la file, renvoi plus tard.
                plugin.getLogger().warning("[synchro-personnages] réponse sans résultat pour " + sans + " photo(s) : "
                        + "renvoi plus tard.");
                reporter(0L, maintenant);
            }
        } else if (r.code() == 0 && !r.reseau()) {
            return;                                          // client pas prêt (réglages) : DouaneClient l'a signalé
        } else if (r.reseau() || r.code() == 429 || r.code() == 404 || r.code() >= 500) {
            reporter(r.retryAfterS() * 1000L, maintenant);
            if (echecs == 1 || echecs % 10 == 0) {
                plugin.getLogger().warning("[synchro-personnages] envoi reporté (" + (r.reseau() ? r.message()
                        : "HTTP " + r.code()) + "), " + enAttente.size() + " photo(s) en attente.");
            }
        } else if (r.code() == 401 || r.code() == 403) {
            // Signature refusée : le plus souvent un réglage passager (secret changé, serveur pas encore déclaré côté
            // API, horloge décalée). Les photos restent dans la file et sur disque, renvoi espacé : une photo
            // « supprimé » perdue ici ne reviendrait jamais (le rattrapage ne lit que les personnages encore sur disque).
            reporter(RENVOI_MAX_MS, maintenant);
            if (echecs == 1 || echecs % 10 == 0) {
                plugin.getLogger().severe("[synchro-personnages] envoi refusé par l'API (HTTP " + r.code() + " : "
                        + r.message() + "), " + enAttente.size() + " photo(s) gardée(s), nouvel essai dans "
                        + (RENVOI_MAX_MS / 60_000L) + " min. Vérifier reborn.server-id et le secret du jeu.");
            }
        } else {
            // 400, 413… : définitif (contrat). On n'insiste pas avec les mêmes photos.
            plugin.getLogger().severe("[synchro-personnages] envoi refusé par l'API (HTTP " + r.code() + " : "
                    + r.message() + ") : " + lot.size() + " photo(s) abandonnée(s). Vérifier reborn.server-id et le "
                    + "secret du jeu.");
            for (Photo p : lot) retirer(p, maintenant);
        }
        ecrireAsync();
        if (!enAttente.isEmpty() && prochainEssai <= maintenant) planifierEnvoi(20L);
    }

    /** Un résultat {@code {index, characterId, statut, message?, avertissements?}}. */
    private void resultat(Photo p, JsonObject o, long maintenant) {
        String statut = texte(o.get("statut"));
        String message = texte(o.get("message"));
        switch (statut == null ? "" : statut) {
            case "cree", "maj", "perime" -> { }
            case "refuse" -> plugin.getLogger().warning("[synchro-personnages] photo refusée pour " + p.id()
                    + (message == null ? "" : " : " + message));
            case "conflit" -> plugin.getLogger().warning("[synchro-personnages] conflit de N° pour " + p.id()
                    + (message == null ? "" : " : " + message));
            // Statut inconnu (API plus récente) : traité comme définitif, pour ne jamais renvoyer la même photo en boucle.
            default -> plugin.getLogger().warning("[synchro-personnages] statut inconnu « " + statut + " » pour "
                    + p.id() + (message == null ? "" : " : " + message));
        }
        JsonElement av = o.get("avertissements");
        if (av != null && av.isJsonArray() && !av.getAsJsonArray().isEmpty() && "cree".equals(statut)) {
            plugin.getLogger().info("[synchro-personnages] " + p.id() + " : " + av.getAsJsonArray());
        }
        retirer(p, maintenant);
    }

    /** Photo traitée (définitivement) : sortie de la file si elle n'a pas été remplacée entre-temps. */
    private void retirer(Photo p, long maintenant) {
        if (enAttente.get(p.id()) == p) enAttente.remove(p.id());
        if (p.deleted()) {
            derniereCle.remove(p.id());
            dernieresMinutes.remove(p.id());
            dernierTraitement.remove(p.id());
            return;
        }
        derniereCle.put(p.id(), p.cle());
        dernieresMinutes.put(p.id(), p.minutes());
        dernierTraitement.put(p.id(), maintenant);
    }

    private void reporter(long retryAfterMs, long maintenant) {
        echecs++;
        long attente = Math.min(RENVOI_MAX_MS, RENVOI_MIN_MS << Math.min(5, echecs - 1));
        prochainEssai = maintenant + Math.max(attente, Math.min(RENVOI_MAX_MS, retryAfterMs));
        planifierEnvoi(Math.max(20L, (prochainEssai - maintenant) / 50L));
    }

    /* ================================================================ rattrapage */

    /**
     * Tous les personnages sur disque, lus hors fil principal (SnakeYAML seul, aucun objet Bukkit), puis ajoutés à la
     * file sur le fil principal. Les comptes chargés en mémoire passent par {@link #verifier} (état plus frais).
     */
    private void rattrapage() {
        if (rattrapageEnCours) return;
        rattrapageEnCours = true;
        File dossier = new File(plugin.getDataFolder(), "characters");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PhotoPersonnage.Donnees> lus = new ArrayList<>();
            int illisibles = 0;
            File[] fichiers = dossier.listFiles((d, n) -> n.endsWith(".yml"));
            if (fichiers != null) {
                LoaderOptions opts = new LoaderOptions();
                opts.setCodePointLimit(64 * 1024 * 1024);
                opts.setMaxAliasesForCollections(200);
                for (File f : fichiers) {
                    UUID owner;
                    try {
                        owner = UUID.fromString(f.getName().substring(0, f.getName().length() - 4));
                    } catch (IllegalArgumentException e) { continue; }
                    try {
                        Object racine = new Yaml(new SafeConstructor(opts))
                                .load(Files.readString(f.toPath(), StandardCharsets.UTF_8));
                        if (!(racine instanceof Map<?, ?> m)) continue;
                        for (Map.Entry<?, ?> e : m.entrySet()) {
                            if (!(e.getValue() instanceof Map<?, ?> s)) continue;
                            UUID id;
                            try { id = UUID.fromString(String.valueOf(e.getKey())); }
                            catch (IllegalArgumentException ex) { continue; }
                            lus.add(PhotoPersonnage.deYaml(id, owner, s));
                        }
                    } catch (Exception ex) {
                        illisibles++;
                    }
                }
            }
            int nIllisibles = illisibles;
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                rattrapageEnCours = false;
                long maintenant = System.currentTimeMillis();
                var charges = plugin.characters().rosterView();
                int n = 0;
                for (PhotoPersonnage.Donnees d : lus) {
                    if (charges.containsKey(d.ownerId())) continue;
                    ajouter(nouvelle(d, maintenant));
                    n++;
                }
                verifier(maintenant);
                plugin.getLogger().info("[synchro-personnages] rattrapage : " + n + " personnage(s) sur disque"
                        + (nIllisibles > 0 ? ", " + nIllisibles + " fichier(s) illisible(s)" : "") + ", "
                        + enAttente.size() + " photo(s) en file.");
                envoyer();
            });
        });
    }

    /* ================================================================ fichier de reprise */

    /** Copie de la file pour l'écriture (fil principal). */
    private String instantane() {
        JsonArray a = new JsonArray();
        for (Photo p : enAttente.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("cle", p.cle());
            o.addProperty("minutes", p.minutes());
            o.addProperty("deleted", p.deleted());
            o.addProperty("snapshotAt", p.snapshotAt());
            o.add("photo", JsonParser.parseString(p.json()));
            a.add(o);
        }
        return a.toString();
    }

    private void ecrireAsync() {
        String contenu = instantane();
        long n = ++ecritureDemandee;
        if (!plugin.isEnabled()) { ecrire(contenu, n); return; }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> ecrire(contenu, n));
    }

    /** Écriture atomique (fichier temporaire puis renommage) ; une écriture plus ancienne que la dernière est sautée. */
    private void ecrire(String contenu, long n) {
        synchronized (verrouFichier) {
            if (n <= ecritureFaite) return;
            try {
                Path cible = fichier.toPath();
                Files.createDirectories(cible.getParent());
                Path tmp = cible.resolveSibling(fichier.getName() + ".tmp");
                Files.writeString(tmp, contenu, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, cible, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, cible, StandardCopyOption.REPLACE_EXISTING);
                }
                ecritureFaite = n;
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "[synchro-personnages] écriture de la file impossible", e);
            }
        }
    }

    /** Relit la file laissée par le dernier arrêt (fil principal, au démarrage). */
    private void charger() {
        if (!fichier.isFile()) return;
        try {
            JsonElement racine = JsonParser.parseString(Files.readString(fichier.toPath(), StandardCharsets.UTF_8));
            if (!racine.isJsonArray()) return;
            for (JsonElement el : racine.getAsJsonArray()) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                JsonElement ph = o.get("photo");
                if (ph == null || !ph.isJsonObject()) continue;
                UUID id;
                try { id = UUID.fromString(ph.getAsJsonObject().get("characterId").getAsString()); }
                catch (RuntimeException e) { continue; }
                ajouter(new Photo(id, texte(o.get("cle")) == null ? "" : texte(o.get("cle")), entier(o.get("minutes")),
                        o.has("deleted") && o.get("deleted").getAsBoolean(),
                        o.has("snapshotAt") ? o.get("snapshotAt").getAsLong() : 0L, ph.toString()));
            }
            if (!enAttente.isEmpty()) {
                plugin.getLogger().info("[synchro-personnages] " + enAttente.size() + " photo(s) reprise(s) du dernier arrêt.");
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("[synchro-personnages] file de reprise illisible, ignorée : " + e.getMessage());
        }
    }

    /* ================================================================ outils */

    private static String texte(JsonElement e) {
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static int entier(JsonElement e) {
        try { return e != null && e.isJsonPrimitive() ? e.getAsInt() : -1; } catch (RuntimeException ex) { return -1; }
    }
}
