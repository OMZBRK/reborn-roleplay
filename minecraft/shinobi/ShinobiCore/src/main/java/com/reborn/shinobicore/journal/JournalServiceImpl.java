package com.reborn.shinobicore.journal;

import com.google.gson.JsonObject;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService;
import com.reborn.shinobicore.character.PlayerIdRegistry;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.staff.panel.StaffGrades;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implémentation du {@link JournalService} : index mémoire + fichiers journaliers par catégorie.
 *
 * <p>Format d'une ligne de fichier ({@code logs/<categorie>-AAAA-MM-JJ.log}, UTF-8) :
 * <pre>2026-10-14 21:03:12 | COMBAT | DEGATS | #00042 Omar (Itachi Uchiha) [Admin] | #00017 Bob (Kakashi) | 6,5 PV | world 12 64 -30</pre>
 * Les {@code |} et retours à la ligne des champs sont remplacés à l'écriture : la ligne se relit sans ambiguïté
 * (requêtes du Poste de garde sur une période antérieure à l'index mémoire).
 *
 * <p>Fils : l'identité est résolue sur le thread principal ; l'écriture disque, la rotation, la rétention et la
 * relecture passent par <b>un seul</b> fil dédié ({@code Reborn-Journal}), ce qui garantit qu'une relecture voit
 * toutes les écritures précédentes.
 */
public final class JournalServiceImpl implements JournalService {

    /* ================================================================ modèle */

    /** Une personne telle qu'elle était au moment de l'action. {@code grade} : 0-4 staff, -1 inconnu. */
    public record Personne(String uuid, String pseudo, String pid, String perso, int grade) {

        public static final Personne CONSOLE = new Personne("", "CONSOLE", "", "", 4);

        public String gradeName() {
            if ("CONSOLE".equals(pseudo) || "RCON".equals(pseudo)) return "Console";
            return grade < 0 ? "" : StaffGrades.name(grade);
        }

        /** {@code #00042 Omar (Itachi Uchiha) [Admin]} — grade noté seulement pour le staff. */
        public String texte() {
            StringBuilder b = new StringBuilder();
            if (!pid.isEmpty()) b.append('#').append(pid).append(' ');
            b.append(pseudo.isEmpty() ? "?" : pseudo.replace(' ', '_'));
            if (!perso.isEmpty()) b.append(" (").append(perso).append(')');
            if (grade > 0) b.append(" [").append(gradeName()).append(']');
            return b.toString();
        }

        private static final Pattern P = Pattern.compile("^(?:#(\\d+) )?(\\S+)(?: \\((.*?)\\))?(?: \\[(.+)])?$");

        static Personne parse(String s) {
            if (s == null || s.isBlank() || s.equals("—")) return null;
            Matcher m = P.matcher(s.trim());
            if (!m.matches()) return new Personne("", s.trim(), "", "", -1);
            String g = m.group(4);
            int grade = g == null ? 0 : switch (g) {
                case "Helper" -> 1;
                case "Modérateur" -> 2;
                case "Admin" -> 3;
                case "Owner", "Console" -> 4;
                default -> 0;
            };
            return new Personne("", m.group(2), m.group(1) == null ? "" : m.group(1),
                    m.group(3) == null ? "" : m.group(3), grade);
        }

        public JsonObject json() {
            JsonObject j = new JsonObject();
            j.addProperty("uuid", uuid);
            j.addProperty("pseudo", pseudo);
            j.addProperty("pid", pid);
            j.addProperty("perso", perso);
            j.addProperty("grade", Math.max(0, grade));
            j.addProperty("gradeName", grade < 0 ? "" : gradeName());
            return j;
        }

        boolean concerne(String pidFmt) { return !pid.isEmpty() && pid.equals(pidFmt); }

        boolean nomContient(String bas) {
            return pseudo.toLowerCase(Locale.ROOT).contains(bas) || perso.toLowerCase(Locale.ROOT).contains(bas);
        }
    }

    /** Une entrée du journal. {@code cible} peut être null ; {@code cibleTexte} sert aux cibles sans compte. */
    public record Ligne(String id, long at, Categorie cat, String action, Personne acteur, Personne cible,
                        String detail, String lieu) { }

    /** Filtres d'une requête (tous facultatifs) : {@code pid} -1 = aucun, {@code du}/{@code au} 0 = sans borne. */
    public record Requete(Set<Categorie> cats, int pid, String joueur, long du, long au, String q, int page, int par) {
        public Requete {
            cats = cats == null || cats.isEmpty() ? EnumSet.allOf(Categorie.class) : EnumSet.copyOf(cats);
            joueur = joueur == null ? "" : joueur.trim().toLowerCase(Locale.ROOT);
            q = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
            page = Math.max(0, page);
            par = par <= 0 ? 50 : Math.min(200, par);
        }
    }

    public record Resultat(int total, int page, int pages, String source, List<Ligne> lignes) { }

    /* ================================================================ état */

    private static final DateTimeFormatter HORO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern FICHIER = Pattern.compile("^([a-z]+)-(\\d{4}-\\d{2}-\\d{2})\\.log$");
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final ShinobiCore plugin;
    private final AtomicLong seq = new AtomicLong();
    private final Deque<Ligne> memoire = new ArrayDeque<>();
    private final ThreadPoolExecutor fil;
    private final Map<Categorie, Ouvert> ouverts = new EnumMap<>(Categorie.class);
    private final long demarrage = System.currentTimeMillis();
    /** Début de la période couverte par l'index mémoire (relu au démarrage, puis glissant). */
    private volatile long couvertDepuis = demarrage;

    private volatile boolean actif = true;
    private volatile int memoireMax = 20000;
    private volatile int retentionJours = 90;
    private volatile int disqueMaxJours = 31;
    private volatile File dossier;

    private record Ouvert(LocalDate jour, BufferedWriter w) { }

    public JournalServiceImpl(ShinobiCore plugin) {
        this.plugin = plugin;
        this.fil = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "Reborn-Journal");
            t.setDaemon(true);
            return t;
        });
        relireConfig();
    }

    public void relireConfig() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("journal");
        actif = s == null || s.getBoolean("actif", true);
        memoireMax = Math.max(500, s == null ? 20000 : s.getInt("memoire", 20000));
        retentionJours = Math.max(0, s == null ? 90 : s.getInt("retention-jours", 90));
        disqueMaxJours = Math.max(1, s == null ? 31 : s.getInt("disque-max-jours", 31));
        dossier = new File(plugin.getDataFolder(), s == null ? "logs" : s.getString("dossier", "logs"));
    }

    public ConfigurationSection config() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("journal");
        return s != null ? s : plugin.getConfig().createSection("journal");
    }

    /** Relit les deux derniers jours dans l'index (le Poste de garde garde son historique après un redémarrage). */
    public void demarrer() {
        fil.execute(() -> {
            try {
                if (!dossier.exists()) dossier.mkdirs();
                nettoyer();
                long depuis = LocalDate.now(ZONE).minusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli();
                List<Ligne> lues = lireDisque(EnumSet.allOf(Categorie.class), depuis, Long.MAX_VALUE, memoireMax);
                if (!lues.isEmpty()) {
                    synchronized (memoire) {
                        for (Ligne l : lues) {          // lues : du plus récent au plus ancien
                            if (memoire.size() >= memoireMax) break;
                            memoire.addLast(l);
                        }
                        couvertDepuis = memoire.size() >= memoireMax ? memoire.peekLast().at() : depuis;
                    }
                } else couvertDepuis = depuis;
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Journal : relecture au démarrage impossible", e);
            }
        });
        // Rétention : toutes les 6 h.
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> fil.execute(this::nettoyer),
                20L * 3600 * 6, 20L * 3600 * 6);
    }

    public void arreter() {
        fil.execute(this::fermerTout);
        fil.shutdown();
        try {
            if (!fil.awaitTermination(5, TimeUnit.SECONDS)) fil.shutdownNow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /* ================================================================ écriture */

    @Override
    public Entree entree(Categorie categorie, String action) { return new EntreeImpl(categorie, action); }

    private final class EntreeImpl implements Entree {
        private final Categorie cat;
        private final String action;
        private Object acteur, cible;                // Player | CommandSender | UUID | UUID[2] | String
        private String detail = "";
        private Location lieu;

        EntreeImpl(Categorie cat, String action) {
            this.cat = cat == null ? Categorie.SYSTEME : cat;
            this.action = action == null || action.isBlank() ? "?" : action;
        }

        @Override public Entree acteur(Player p) { acteur = p; return this; }
        @Override public Entree acteur(CommandSender s) { acteur = s; return this; }
        @Override public Entree acteur(UUID compte) { acteur = compte; return this; }
        @Override public Entree cible(Player p) { cible = p; return this; }
        @Override public Entree cible(UUID compte) { cible = compte; return this; }
        @Override public Entree ciblePersonnage(UUID compte, UUID perso) { cible = new UUID[]{compte, perso}; return this; }
        @Override public Entree cibleTexte(String t) { cible = t == null || t.isBlank() ? null : t; return this; }
        @Override public Entree detail(String d) { detail = d == null ? "" : d; return this; }
        @Override public Entree lieu(Location l) { lieu = l == null ? null : l.clone(); return this; }

        @Override
        public void ecrire() {
            if (!actif) return;
            long at = System.currentTimeMillis();
            if (Bukkit.isPrimaryThread() || !plugin.isEnabled()) { enregistrer(at); return; }
            try {
                Bukkit.getScheduler().runTask(plugin, () -> enregistrer(at));
            } catch (RuntimeException e) {
                enregistrer(at);          // arrêt en cours : on écrit quand même
            }
        }

        private void enregistrer(long at) {
            try {
                Ligne l = new Ligne(Long.toString(at, 36) + "-" + seq.incrementAndGet(), at, cat, propre(action),
                        personne(acteur), personne(cible), propre(detail), lieuTexte(lieu));
                ajouter(l);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Journal : entrée ignorée (" + cat + " " + action + ")", e);
            }
        }
    }

    /** Ajoute une ligne déjà construite (index + disque). */
    void ajouter(Ligne l) {
        synchronized (memoire) {
            memoire.addFirst(l);
            while (memoire.size() > memoireMax) {
                Ligne old = memoire.pollLast();
                if (old != null) couvertDepuis = Math.max(couvertDepuis, old.at() + 1);
            }
        }
        if (fil.isShutdown()) return;
        fil.execute(() -> ecrireDisque(l));
    }

    private void ecrireDisque(Ligne l) {
        try {
            LocalDate jour = Instant.ofEpochMilli(l.at()).atZone(ZONE).toLocalDate();
            Ouvert o = ouverts.get(l.cat());
            if (o == null || !o.jour().equals(jour)) {
                if (o != null) o.w().close();
                if (!dossier.exists()) dossier.mkdirs();
                File f = new File(dossier, l.cat().fichier() + "-" + jour + ".log");
                o = new Ouvert(jour, Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND));
                ouverts.put(l.cat(), o);
            }
            o.w().write(formater(l));
            o.w().newLine();
            if (fil.getQueue().isEmpty()) for (Ouvert x : ouverts.values()) x.w().flush();
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Journal : écriture impossible (" + l.cat().fichier() + ")", e);
        }
    }

    private void fermerTout() {
        for (Ouvert o : ouverts.values()) {
            try { o.w().close(); } catch (IOException ignored) { }
        }
        ouverts.clear();
    }

    private void flushTout() {
        for (Ouvert o : ouverts.values()) {
            try { o.w().flush(); } catch (IOException ignored) { }
        }
    }

    static String formater(Ligne l) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(l.at()), ZONE).format(HORO)
                + " | " + l.cat().name()
                + " | " + l.action()
                + " | " + (l.acteur() == null ? "—" : l.acteur().texte())
                + " | " + (l.cible() == null ? "—" : l.cible().texte())
                + " | " + (l.detail().isEmpty() ? "—" : l.detail())
                + " | " + (l.lieu().isEmpty() ? "—" : l.lieu());
    }

    static Ligne relire(String ligne, Categorie defaut, long n) {
        String[] f = ligne.split(" \\| ", -1);
        if (f.length < 7) return null;
        long at;
        try {
            at = LocalDateTime.parse(f[0], HORO).atZone(ZONE).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            return null;
        }
        Categorie c = Categorie.parse(f[1]);
        if (c == null) c = defaut;
        String detail = f[5].equals("—") ? "" : f[5];
        String lieu = f[6].equals("—") ? "" : f[6];
        return new Ligne("d" + Long.toString(at, 36) + "-" + n, at, c, f[2], Personne.parse(f[3]), Personne.parse(f[4]),
                detail, lieu);
    }

    /** Retire les {@code |} et retours à la ligne (le format du fichier reste lisible et relisible). */
    static String propre(String s) {
        if (s == null) return "";
        String t = s.replace('\n', ' ').replace('\r', ' ').replace("|", "/").replaceAll("§[0-9a-fk-orA-FK-OR]", "");
        return t.length() > 600 ? t.substring(0, 600) + "…" : t;
    }

    private static String lieuTexte(Location l) {
        if (l == null || l.getWorld() == null) return "";
        return l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
    }

    /* ================================================================ identité */

    private Personne personne(Object o) {
        if (o == null) return null;
        if (o instanceof Player p) return deJoueur(p);
        if (o instanceof UUID id) {
            Player p = Bukkit.getPlayer(id);
            return p != null ? deJoueur(p) : new Personne(id.toString(), nomDe(id), "", "", -1);
        }
        if (o instanceof UUID[] pair) {
            ShinobiCharacter c = null;
            try {
                if (plugin.characters() != null) c = plugin.characters().findById(pair[0], pair[1]).orElse(null);
            } catch (RuntimeException ignored) { }
            Player p = Bukkit.getPlayer(pair[0]);
            return new Personne(pair[0].toString(), p != null ? p.getName() : nomDe(pair[0]),
                    c != null && c.hasPlayerId() ? PlayerIdRegistry.format(c.playerId()) : "",
                    c != null ? propre(c.name()) : "", p != null ? StaffGrades.of(p) : -1);
        }
        if (o instanceof ConsoleCommandSender) return Personne.CONSOLE;
        if (o instanceof RemoteConsoleCommandSender) return new Personne("", "RCON", "", "", 4);
        if (o instanceof BlockCommandSender b) return new Personne("", "BLOC_DE_COMMANDE", "", lieuTexte(b.getBlock().getLocation()), 0);
        if (o instanceof CommandSender s) return new Personne("", propre(s.getName()), "", "", -1);
        if (o instanceof String t) return new Personne("", "", "", propre(t), -1);
        return null;
    }

    private Personne deJoueur(Player p) {
        ShinobiCharacter c = plugin.characters() != null ? plugin.characters().getActive(p.getUniqueId()) : null;
        return new Personne(p.getUniqueId().toString(), p.getName(),
                c != null && c.hasPlayerId() ? PlayerIdRegistry.format(c.playerId()) : "",
                c != null ? propre(c.name()) : "", StaffGrades.of(p));
    }

    private String nomDe(UUID id) {
        if (plugin.staffPanel() != null) {
            String n = plugin.staffPanel().store().nameOf(id);
            if (n != null && !n.startsWith(id.toString().substring(0, 8))) return n;
        }
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        return op.getName() != null ? op.getName() : id.toString().substring(0, 8);
    }

    /* ================================================================ lecture */

    /** Dernières entrées (index mémoire), du plus récent au plus ancien. */
    public List<Ligne> recentes(int max) {
        List<Ligne> out = new ArrayList<>();
        synchronized (memoire) {
            for (Ligne l : memoire) {
                if (out.size() >= max) break;
                out.add(l);
            }
        }
        return out;
    }

    /**
     * Cherche. Si la période demandée commence avant ce que couvre l'index mémoire, les fichiers sont relus sur le
     * fil du journal ; {@code rappel} est alors appelé sur le thread principal.
     */
    public void chercher(Requete r, Consumer<Resultat> rappel) {
        if (r.du() <= 0 || r.du() >= couvertDepuis) {
            rappel.accept(page(filtrer(instantane(), r), r, "memoire"));
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            flushTout();
            long du = Math.max(r.du(), (r.au() > 0 ? r.au() : System.currentTimeMillis()) - disqueMaxJours * 86_400_000L);
            List<Ligne> lues = lireDisque(r.cats(), du, r.au() <= 0 ? Long.MAX_VALUE : r.au(), 200_000);
            return page(filtrer(lues, r), r, "disque");
        }, fil).whenComplete((res, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "Journal : relecture impossible", err);
                rappel.accept(page(filtrer(instantane(), r), r, "memoire"));
            } else rappel.accept(res);
        }));
    }

    private List<Ligne> instantane() {
        synchronized (memoire) { return new ArrayList<>(memoire); }
    }

    static List<Ligne> filtrer(List<Ligne> source, Requete r) {
        String pid = r.pid() >= 0 ? PlayerIdRegistry.format(r.pid()) : null;
        List<Ligne> out = new ArrayList<>();
        for (Ligne l : source) {
            if (!r.cats().contains(l.cat())) continue;
            if (r.du() > 0 && l.at() < r.du()) continue;
            if (r.au() > 0 && l.at() > r.au()) continue;
            if (pid != null && !((l.acteur() != null && l.acteur().concerne(pid))
                    || (l.cible() != null && l.cible().concerne(pid)))) continue;
            if (!r.joueur().isEmpty() && !((l.acteur() != null && l.acteur().nomContient(r.joueur()))
                    || (l.cible() != null && l.cible().nomContient(r.joueur())))) continue;
            if (!r.q().isEmpty() && !(l.action() + " " + l.detail() + " " + l.lieu() + " "
                    + (l.cible() != null ? l.cible().perso() : "")).toLowerCase(Locale.ROOT).contains(r.q())) continue;
            out.add(l);
        }
        return out;
    }

    static Resultat page(List<Ligne> tout, Requete r, String source) {
        int total = tout.size();
        int pages = Math.max(1, (total + r.par() - 1) / r.par());
        int from = Math.min(total, r.page() * r.par());
        int to = Math.min(total, from + r.par());
        return new Resultat(total, r.page(), pages, source, new ArrayList<>(tout.subList(from, to)));
    }

    /** Relit les fichiers des catégories et de la période ; renvoie du plus récent au plus ancien. */
    private List<Ligne> lireDisque(Set<Categorie> cats, long du, long au, int max) {
        List<Ligne> out = new ArrayList<>();
        File[] fs = dossier.listFiles();
        if (fs == null) return out;
        LocalDate d0 = Instant.ofEpochMilli(Math.max(0, du)).atZone(ZONE).toLocalDate();
        LocalDate d1 = au == Long.MAX_VALUE ? LocalDate.now(ZONE) : Instant.ofEpochMilli(au).atZone(ZONE).toLocalDate();
        long n = 0;
        for (File f : fs) {
            Matcher m = FICHIER.matcher(f.getName());
            if (!m.matches()) continue;
            Categorie c = Categorie.parse(m.group(1));
            if (c == null || !cats.contains(c)) continue;
            LocalDate jour;
            try { jour = LocalDate.parse(m.group(2)); } catch (DateTimeParseException e) { continue; }
            if (jour.isBefore(d0) || jour.isAfter(d1)) continue;
            try (BufferedReader br = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
                String s;
                while ((s = br.readLine()) != null) {
                    Ligne l = relire(s, c, n++);
                    if (l == null || l.at() < du || l.at() > au) continue;
                    out.add(l);
                }
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().log(Level.FINE, "Journal : lecture de " + f.getName() + " impossible", e);
            }
        }
        out.sort(Comparator.comparingLong(Ligne::at).reversed());
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** Rétention : supprime les fichiers de plus de {@code retention-jours} (0 = jamais). */
    private void nettoyer() {
        if (retentionJours <= 0) return;
        File[] fs = dossier.listFiles();
        if (fs == null) return;
        LocalDate limite = LocalDate.now(ZONE).minusDays(retentionJours);
        int n = 0;
        for (File f : fs) {
            Matcher m = FICHIER.matcher(f.getName());
            if (!m.matches()) continue;
            try {
                if (LocalDate.parse(m.group(2)).isBefore(limite) && f.delete()) n++;
            } catch (DateTimeParseException ignored) { }
        }
        if (n > 0) plugin.getLogger().info("Journal : " + n + " fichier(s) de plus de " + retentionJours + " jours supprimé(s).");
    }

    /** Pour les tests hors serveur : itère l'index. */
    Iterator<Ligne> iterateur() { return instantane().iterator(); }
}
