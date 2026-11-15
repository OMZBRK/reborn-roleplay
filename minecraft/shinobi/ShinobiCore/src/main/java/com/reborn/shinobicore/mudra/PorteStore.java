package com.reborn.shinobicore.mudra;

import com.reborn.shinobicore.technique.Mudra;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stockage des mudras et des portes scellées : {@code plugins/ShinobiCore/mudras.yml} (demande du 14/11), sur le patron
 * de {@code PlaqueStore}.
 *
 * <pre>
 * actif: false            # interrupteur du système (/porte systeme on|off) ; absent = false
 * prochain: 3
 * portes:
 *   D1:
 *     nom: "Porte du temple"
 *     monde: world
 *     x: 12
 *     y: 64
 *     z: -30
 *     boite: [3, 4, 1]    # dx, dy, dz
 *     signes: [TIGER, SNAKE, DOG]
 *     leve: 4
 *     cree: { le: 1763100000000, par: &lt;uuid&gt;, pseudo: Omar }
 *     modifie: { le: …, par: …, pseudo: … }
 *     ouverte: false
 *     blocs: [ "minecraft:stone", … ]   # seulement porte ouverte : ce qu'il faut remettre
 * </pre>
 *
 * Écriture <b>atomique</b> ({@code .tmp} puis renommage) ; une écriture plus ancienne qu'une déjà faite est sautée. Les
 * coordonnées restent dans le dossier du plugin, jamais dans le dépôt.
 */
public final class PorteStore {

    private static final String FICHIER = "mudras.yml";

    private final Logger log;
    private final File fichier;
    final Map<String, PorteScellee> portes = new LinkedHashMap<>();
    int prochain = 1;
    /** Interrupteur du système : livré éteint (clé absente ou fichier absent = false). */
    boolean actif;
    private final Object verrou = new Object();
    private long dernierEcrit = -1;
    /** Contenu écrit ou lu en dernier (une différence sur le disque = retouche à la main). */
    private String dernierContenu;
    /**
     * Le dernier chargement a échoué (fichier illisible, YAML invalide, porte rejetée) : le fichier est mis de côté
     * ({@code mudras.yml.corrompu-<date>}) et <b>aucune écriture ne part</b> tant que ce drapeau tient, sinon la première
     * sauvegarde effacerait les portes perdues et redonnerait leurs identifiants. Une porte ne s'ouvre pas non plus (ses
     * blocs ne pourraient pas être notés avant de toucher au monde). Levé par un chargement réussi ({@code /sc reload}
     * après correction à la main).
     */
    private volatile boolean bloque;

    public PorteStore(Logger log, File fichier) {
        this.log = log;
        this.fichier = fichier;
    }

    public Map<String, PorteScellee> portes() { return portes; }

    public boolean actif() { return actif; }

    /** Le fichier n'a pas pu être lu : écritures, créations et ouvertures bloquées (voir {@link #bloque}). */
    public boolean bloque() { return bloque; }

    /** Nouvel identifiant libre ({@code D1}, {@code D2}…) : le compteur ne redescend jamais. */
    public String nouvelId() {
        String id;
        do id = MudraRegles.id(prochain++); while (portes.containsKey(id));
        return id;
    }

    /* ================================================================ lecture */

    /** Démarrage : lit {@code mudras.yml} (voir {@link #charger(String)} pour ce qui bloque les écritures). */
    public void charger() {
        synchronized (verrou) { chargerSousVerrou(false); }
    }

    /**
     * {@code /sc reload} : l'état en mémoire ({@code memoire}, sérialisé sur le fil principal) est d'abord écrit, sauf si
     * le fichier a été <b>modifié à la main</b> depuis la dernière écriture (la retouche gagne ; avertissement) ou s'il
     * est bloqué ; les écritures plus anciennes que {@code seq} sont annulées, puis le fichier est relu. Un fichier qui ne
     * se relit pas <b>garde l'état valide en mémoire</b> : il est mis de côté et les écritures sont bloquées.
     */
    public void recharger(String memoire, long seq) {
        synchronized (verrou) {
            boolean retouche = modifieSurDisque();
            if (memoire != null && !bloque) {
                if (!retouche) ecrireSousVerrou(memoire, seq);
                else log.warning(FICHIER + " modifié à la main depuis la dernière écriture : la retouche est relue, les"
                        + " changements en mémoire non écrits sont abandonnés.");
            }
            dernierEcrit = Math.max(dernierEcrit, seq);
            chargerSousVerrou(true);
        }
    }

    /** Le fichier n'est plus ce que le service a écrit ou lu en dernier (retouche à la main). Un fichier absent : non. */
    private boolean modifieSurDisque() {
        if (!fichier.exists()) return false;
        try {
            return !Files.readString(fichier.toPath(), StandardCharsets.UTF_8).equals(dernierContenu);
        } catch (IOException e) {
            return true;
        }
    }

    private void chargerSousVerrou(boolean garder) {
        Map<String, PorteScellee> avant = new LinkedHashMap<>(portes);
        int prochainAvant = prochain;
        boolean actifAvant = actif;
        if (!fichier.exists()) {
            if (!garder) { portes.clear(); prochain = 1; actif = false; }
            bloque = false;
            dernierContenu = null;
            return;
        }
        String contenu;
        Lecture l;
        try {
            contenu = Files.readString(fichier.toPath(), StandardCharsets.UTF_8);
            l = lire(contenu);
        } catch (IOException e) {
            log.log(Level.SEVERE, FICHIER + " illisible : écritures BLOQUÉES", e);
            contenu = null;
            l = null;
        }
        if (l != null && l.erreurs.isEmpty()) {
            portes.clear();
            portes.putAll(l.portes);
            // un rechargement ne redonne jamais un identifiant déjà distribué
            prochain = garder ? Math.max(prochainAvant, l.prochain) : l.prochain;
            actif = l.actif;
            bloque = false;
            dernierContenu = contenu;
            return;
        }
        if (l != null) for (String e : l.erreurs) log.severe(FICHIER + " : " + e);
        if (garder) {
            // rechargement raté : l'état valide en mémoire est gardé tel quel
            portes.clear();
            portes.putAll(avant);
            prochain = prochainAvant;
            actif = actifAvant;
            log.severe(FICHIER + " : rechargement refusé, les " + portes.size() + " porte(s) en mémoire sont gardées.");
        } else if (l != null) {
            // démarrage : ce qui a pu être lu se lit en jeu, mais rien n'est réécrit
            portes.clear();
            portes.putAll(l.portes);
            prochain = l.prochain;
            actif = l.actif;
        } else {
            portes.clear();
            prochain = 1;
            actif = false;
        }
        mettreDeCote();
    }

    /**
     * Lit un contenu YAML (tests, démarrage) : les portes valides sont chargées ; vrai si <b>tout</b> le fichier a été
     * compris. Faux, et les écritures sont <b>bloquées</b> ({@link #bloque}), si le YAML est invalide, si une porte est
     * rejetée (monde absent, boîte hors bornes, signe inconnu, ouverte sans ses blocs, entrée qui n'est pas une
     * section…), si la section {@code portes} n'en est pas une, ou si une clé inconnue paraît à la racine (indentation
     * abîmée). Chaque rejet est journalisé en SEVERE.
     */
    public boolean charger(String yaml) {
        Lecture l = lire(yaml);
        portes.clear();
        portes.putAll(l.portes);
        prochain = l.prochain;
        actif = l.actif;
        for (String e : l.erreurs) log.severe(FICHIER + " : " + e);
        bloque = !l.erreurs.isEmpty();
        return !bloque;
    }

    /** Résultat d'une lecture : portes valides, compteur (jamais en dessous d'un id vu, même rejeté), interrupteur, erreurs. */
    private record Lecture(Map<String, PorteScellee> portes, int prochain, boolean actif, List<String> erreurs) { }

    private static Lecture lire(String yaml) {
        Map<String, PorteScellee> out = new LinkedHashMap<>();
        List<String> err = new ArrayList<>();
        YamlConfiguration y = new YamlConfiguration();
        try { y.loadFromString(yaml); }
        catch (InvalidConfigurationException e) {
            err.add("YAML invalide (" + premiereLigne(e.getMessage()) + ") : aucune porte chargée, écritures BLOQUÉES");
            return new Lecture(out, 1, false, err);
        }
        for (String k : y.getKeys(false))
            if (!k.equals("actif") && !k.equals("prochain") && !k.equals("portes"))
                err.add("clé inconnue à la racine : « " + k + " » (indentation ?)");
        // l'interrupteur : un vrai booléen seulement (« actif: oui » ne doit pas allumer le système par accident)
        boolean actif = false;
        if (y.contains("actif") && y.get("actif") != null) {
            if (y.get("actif") instanceof Boolean b) actif = b;
            else err.add("« actif » doit valoir true ou false");
        }
        int prochain = Math.max(1, y.getInt("prochain", 1));
        ConfigurationSection s = y.getConfigurationSection("portes");
        if (s == null) {
            if (y.contains("portes") && y.get("portes") != null) err.add("« portes » n'est pas une liste de portes (indentation ?)");
            return new Lecture(out, prochain, actif, err);
        }
        for (String id : s.getKeys(false)) {
            // un compteur abîmé ne redonne jamais un identifiant déjà vu, même celui d'une porte rejetée
            if (id.length() >= 2 && id.charAt(0) == 'D') {
                try { prochain = Math.max(prochain, Integer.parseInt(id.substring(1), 36) + 1); }
                catch (NumberFormatException ignored) { }
            }
            ConfigurationSection c = s.getConfigurationSection(id);
            if (c == null) { err.add("porte " + id + " rejetée : ce n'est pas une section"); continue; }
            try {
                PorteScellee p = lire(id, c);
                for (PorteScellee o : out.values())
                    if (o.monde.equals(p.monde) && o.boite.recoupe(p.boite))
                        throw new IllegalArgumentException("sa dalle recoupe celle de " + o.id);
                out.put(id, p);
            } catch (RuntimeException e) {
                err.add("porte " + id + " rejetée : " + e.getMessage());
            }
        }
        return new Lecture(out, prochain, actif, err);
    }

    private static String premiereLigne(String m) {
        if (m == null) return "?";
        int i = m.indexOf('\n');
        return i < 0 ? m : m.substring(0, i);
    }

    /**
     * Fichier illisible : copié à côté ({@code mudras.yml.corrompu-<date>}), jamais écrasé ; écritures bloquées. Le staff
     * corrige le fichier (ou remet la copie) puis recharge.
     */
    private void mettreDeCote() {
        bloque = true;
        Path src = fichier.toPath();
        Path cote = src.resolveSibling(fichier.getName() + ".corrompu-"
                + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(new java.util.Date()));
        try {
            Files.copy(src, cote, StandardCopyOption.REPLACE_EXISTING);
            log.severe(FICHIER + " mis de côté : " + cote.getFileName() + " ; " + FICHIER + " n'est plus écrit tant qu'il n'est"
                    + " pas corrigé (puis /sc reload).");
        } catch (IOException e) {
            log.log(Level.SEVERE, FICHIER + " : copie de sauvegarde impossible (le fichier reste intact, écritures bloquées)", e);
        }
    }

    /** Une porte du fichier ; lève {@link IllegalArgumentException} (motif lisible) si elle ne se comprend pas. */
    private static PorteScellee lire(String id, ConfigurationSection c) {
        PorteScellee p = new PorteScellee(id);
        p.nom = MudraRegles.nom(c.getString("nom", ""));
        p.monde = c.getString("monde", "");
        if (p.monde.isBlank()) throw new IllegalArgumentException("monde absent");
        if (!c.isInt("x") || !c.isInt("y") || !c.isInt("z")) throw new IllegalArgumentException("coin bas (x, y, z) absent");
        List<Integer> b = c.getIntegerList("boite");
        if (b.size() != 3) throw new IllegalArgumentException("boite absente (dx, dy, dz)");
        String e = MudraRegles.validerBoite(b.get(0), b.get(1), b.get(2));
        if (e != null) throw new IllegalArgumentException(e);
        p.boite = new MudraRegles.Boite(c.getInt("x"), c.getInt("y"), c.getInt("z"), b.get(0), b.get(1), b.get(2));
        List<Mudra> signes = new ArrayList<>();
        for (Object o : c.getList("signes", List.of())) {
            Mudra m = o == null ? null : Mudra.from(String.valueOf(o));
            if (m == null) throw new IllegalArgumentException("signe inconnu « " + o + " »");
            signes.add(m);
        }
        e = MudraRegles.validerSuite(signes.size());
        if (e != null) throw new IllegalArgumentException("suite de " + signes.size() + " signe(s) : " + e);
        p.signes = List.copyOf(signes);
        p.leve = MudraRegles.bornerLeve(c.getInt("leve", 0), p.boite.dy());
        p.creeLe = c.getLong("cree.le", 0);
        p.creePar = uuid(c.getString("cree.par"));
        p.creePseudo = c.getString("cree.pseudo", "");
        p.modifLe = c.getLong("modifie.le", 0);
        p.modifPar = uuid(c.getString("modifie.par"));
        p.modifPseudo = c.getString("modifie.pseudo", "");
        p.ouverte = c.getBoolean("ouverte", false);
        if (p.ouverte) {
            List<String> blocs = c.getStringList("blocs");
            // sans la liste complète, la dalle ne pourrait pas être remise : la porte n'est pas perdue en silence
            if (blocs.size() != p.boite.volume())
                throw new IllegalArgumentException("ouverte sans ses blocs (" + blocs.size() + " / " + p.boite.volume() + ")");
            p.blocs = List.copyOf(blocs);
        }
        return p;
    }

    private static UUID uuid(String s) {
        if (s == null || s.isBlank()) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    /* ================================================================ écriture */

    /** Contenu YAML de l'état courant (fil principal). */
    public String serialiser() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("Mudras au clavier et portes scellées. Modifié en jeu : /porte (systeme on|off, creer, signes…).",
                "actif : interrupteur du système (false = la barre ne s'ouvre pour personne, les portes restent fermées)."));
        y.set("actif", actif);
        y.set("prochain", prochain);
        for (PorteScellee p : portes.values()) {
            String k = "portes." + p.id + ".";
            y.set(k + "nom", p.nom);
            y.set(k + "monde", p.monde);
            y.set(k + "x", p.boite.x());
            y.set(k + "y", p.boite.y());
            y.set(k + "z", p.boite.z());
            y.set(k + "boite", List.of(p.boite.dx(), p.boite.dy(), p.boite.dz()));
            List<String> signes = new ArrayList<>();
            for (Mudra m : p.signes) signes.add(m.name());
            y.set(k + "signes", signes);
            y.set(k + "leve", p.leve);
            y.set(k + "cree.le", p.creeLe);
            if (p.creePar != null) y.set(k + "cree.par", p.creePar.toString());
            y.set(k + "cree.pseudo", p.creePseudo);
            if (p.modifLe > 0) {
                y.set(k + "modifie.le", p.modifLe);
                if (p.modifPar != null) y.set(k + "modifie.par", p.modifPar.toString());
                y.set(k + "modifie.pseudo", p.modifPseudo);
            }
            y.set(k + "ouverte", p.ouverte);
            if (p.ouverte) y.set(k + "blocs", new ArrayList<>(p.blocs));
        }
        return y.saveToString();
    }

    /**
     * Écrit {@code yaml} (numéro {@code seq} de la sérialisation) de façon atomique ; une écriture plus ancienne qui
     * arriverait après une plus récente est sautée. N'importe quel fil. Vrai si le fichier porte bien ce contenu (ou un
     * plus récent) : l'ouverture d'une porte ne touche au monde que sur un vrai.
     */
    public boolean ecrire(String yaml, long seq) {
        synchronized (verrou) { return ecrireSousVerrou(yaml, seq); }
    }

    private boolean ecrireSousVerrou(String yaml, long seq) {
        if (bloque) {
            log.warning(FICHIER + " : écriture refusée (le fichier n'a pas pu être chargé, voir plus haut).");
            return false;
        }
        if (seq <= dernierEcrit) return true;
        Path cible = fichier.toPath();
        Path tmp = cible.resolveSibling(fichier.getName() + ".tmp");
        try {
            Files.createDirectories(cible.getParent());
            Files.writeString(tmp, yaml, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, cible, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, cible, StandardCopyOption.REPLACE_EXISTING);
            }
            dernierEcrit = seq;
            dernierContenu = yaml;
            return true;
        } catch (IOException e) {
            log.log(Level.WARNING, FICHIER + " : écriture impossible", e);
            return false;
        }
    }
}
