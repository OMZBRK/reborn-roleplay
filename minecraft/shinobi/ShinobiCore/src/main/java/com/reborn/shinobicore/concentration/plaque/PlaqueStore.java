package com.reborn.shinobicore.concentration.plaque;

import com.reborn.shinobicore.staff.outils.Narrations;
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
 * Stockage des plaques : {@code plugins/ShinobiCore/plaques.yml} (lot 4, étape B).
 *
 * <pre>
 * prochain: 43
 * plaques:
 *   P1A:
 *     titre: "Stèle du premier gardien"
 *     segs: [["Ici repose ", ""], ["le premier gardien", "or"], [".", ""]]
 *     ancre: { type: bloc, monde: world, x: 12, y: 64, z: -30, boite: [1, 2, 1] }   # ou entite: &lt;uuid&gt; + pos, ou objet
 *     portee: 6
 *     actif: true
 *     expire: 0
 *     rev: 3
 *     temporaire: false
 *     cree: { le: 1762376580000, par: &lt;uuid&gt;, pseudo: Omar }
 *     modifie: { le: …, par: …, pseudo: … }
 *     lectures: 37
 *     derniere-lecture: 1762455000000
 *     supprimee: { le, par, pseudo, motif }   # absent hors corbeille
 * </pre>
 *
 * Écriture <b>atomique</b> ({@code .tmp} puis renommage, comme {@code player-ids.yml}) ; l'appelant choisit le fil (le
 * service sérialise sur le fil principal et écrit en tâche de fond, la plus récente gagne). Les coordonnées restent dans
 * le dossier du plugin, jamais dans le dépôt.
 */
public final class PlaqueStore {

    private final Logger log;
    private final File fichier;
    final Map<String, Plaque> plaques = new LinkedHashMap<>();
    int prochain = 1;
    private final Object verrou = new Object();
    private long dernierEcrit = -1;
    /** Contenu écrit ou lu en dernier (une différence sur le disque = retouche à la main). */
    private String dernierContenu;
    /**
     * Le dernier chargement a échoué (fichier illisible ou YAML invalide) : le fichier est mis de côté
     * ({@code plaques.yml.corrompu-<date>}) et <b>aucune écriture ne part</b> tant que ce drapeau tient — sinon la première
     * sauvegarde remplacerait toutes les plaques par une table vide et redonnerait les identifiants P1, P2… déjà portés par
     * des objets du monde. Levé par un chargement réussi ({@code /sc reload} après correction à la main).
     */
    private volatile boolean bloque;

    public PlaqueStore(Logger log, File fichier) {
        this.log = log;
        this.fichier = fichier;
    }

    public Map<String, Plaque> plaques() { return plaques; }

    /** Le fichier n'a pas pu être lu : écritures et créations bloquées (voir {@link #bloque}). */
    public boolean bloque() { return bloque; }

    /** Nouvel identifiant libre ({@code P1}, {@code P2}…). */
    public String nouvelId() {
        String id;
        do id = PlaqueRegles.id(prochain++); while (plaques.containsKey(id));
        return id;
    }

    /* ================================================================ lecture */

    /** Démarrage : lit {@code plaques.yml} (voir {@link #charger(String)} pour ce qui bloque les écritures). */
    public void charger() {
        synchronized (verrou) { chargerSousVerrou(false); }
    }

    /**
     * {@code /sc reload} : l'état en mémoire ({@code memoire}, sérialisé sur le fil principal) est d'abord écrit — sauf si
     * le fichier a été <b>modifié à la main</b> depuis la dernière écriture (la retouche gagne ; avertissement) ou s'il
     * est bloqué —, toutes les écritures en attente plus anciennes que {@code seq} sont annulées (une tâche de fond qui
     * arriverait après la relecture ne remet pas un état périmé, et une plaque créée juste avant n'est jamais perdue ni
     * son identifiant redonné), puis le fichier est relu. Un fichier qui ne se relit pas <b>garde l'état valide en
     * mémoire</b> : il est mis de côté et les écritures sont bloquées. Le tout sous le verrou des écritures.
     */
    public void recharger(String memoire, long seq) {
        synchronized (verrou) {
            boolean retouche = modifieSurDisque();
            if (memoire != null && !bloque) {
                if (!retouche) ecrireSousVerrou(memoire, seq);
                else log.warning("plaques.yml modifié à la main depuis la dernière écriture : la retouche est relue, les"
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
        Map<String, Plaque> avant = new LinkedHashMap<>(plaques);
        int prochainAvant = prochain;
        if (!fichier.exists()) {
            if (!garder) { plaques.clear(); prochain = 1; }
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
            log.log(Level.SEVERE, "plaques.yml illisible : écritures BLOQUÉES", e);
            contenu = null;
            l = null;
        }
        if (l != null && l.erreurs.isEmpty()) {
            plaques.clear();
            plaques.putAll(l.plaques);
            // un rechargement ne redonne jamais un identifiant déjà distribué
            prochain = garder ? Math.max(prochainAvant, l.prochain) : l.prochain;
            bloque = false;
            dernierContenu = contenu;
            return;
        }
        if (l != null) for (String e : l.erreurs) log.severe("plaques.yml : " + e);
        if (garder) {
            // rechargement raté : l'état valide en mémoire est gardé tel quel
            plaques.clear();
            plaques.putAll(avant);
            prochain = prochainAvant;
            log.severe("plaques.yml : rechargement refusé, les " + plaques.size() + " plaque(s) en mémoire sont gardées.");
        } else if (l != null) {
            // démarrage : ce qui a pu être lu se lit en jeu, mais rien n'est réécrit
            plaques.clear();
            plaques.putAll(l.plaques);
            prochain = l.prochain;
        } else {
            plaques.clear();
            prochain = 1;
        }
        mettreDeCote();
    }

    /**
     * Lit un contenu YAML (tests, démarrage) : les plaques valides sont chargées ; vrai si <b>tout</b> le fichier a été
     * compris. Faux — et les écritures sont <b>bloquées</b> ({@link #bloque}) — si le YAML est invalide, si une plaque est
     * rejetée (ancre absente, type inconnu, entité sans UUID valide, entrée qui n'est pas une section…), si la section
     * {@code plaques} n'en est pas une, ou si une clé inconnue paraît à la racine (indentation abîmée) : la première
     * sauvegarde réécrirait sinon le fichier sans les plaques perdues. Chaque rejet est journalisé en SEVERE.
     */
    public boolean charger(String yaml) {
        Lecture l = lire(yaml);
        plaques.clear();
        plaques.putAll(l.plaques);
        prochain = l.prochain;
        for (String e : l.erreurs) log.severe("plaques.yml : " + e);
        bloque = !l.erreurs.isEmpty();
        return !bloque;
    }

    /** Résultat d'une lecture : plaques valides, compteur (jamais en dessous d'un id vu, même rejeté), erreurs. */
    private record Lecture(Map<String, Plaque> plaques, int prochain, List<String> erreurs) { }

    private static Lecture lire(String yaml) {
        Map<String, Plaque> out = new LinkedHashMap<>();
        List<String> err = new ArrayList<>();
        YamlConfiguration y = new YamlConfiguration();
        try { y.loadFromString(yaml); }
        catch (InvalidConfigurationException e) {
            err.add("YAML invalide (" + premiereLigne(e.getMessage()) + ") : aucune plaque chargée, écritures BLOQUÉES");
            return new Lecture(out, 1, err);
        }
        for (String k : y.getKeys(false))
            if (!k.equals("prochain") && !k.equals("plaques")) err.add("clé inconnue à la racine : « " + k + " » (indentation ?)");
        int prochain = Math.max(1, y.getInt("prochain", 1));
        ConfigurationSection s = y.getConfigurationSection("plaques");
        if (s == null) {
            if (y.contains("plaques") && y.get("plaques") != null) err.add("« plaques » n'est pas une liste de plaques (indentation ?)");
            return new Lecture(out, prochain, err);
        }
        for (String id : s.getKeys(false)) {
            // un compteur abîmé ne redonne jamais un identifiant déjà vu, même celui d'une plaque rejetée
            if (id.length() >= 2 && id.charAt(0) == 'P') {
                try { prochain = Math.max(prochain, Integer.parseInt(id.substring(1), 36) + 1); }
                catch (NumberFormatException ignored) { }
            }
            ConfigurationSection c = s.getConfigurationSection(id);
            if (c == null) { err.add("plaque " + id + " rejetée : ce n'est pas une section"); continue; }
            try {
                out.put(id, lire(id, c));
            } catch (RuntimeException e) {
                err.add("plaque " + id + " rejetée : " + e.getMessage());
            }
        }
        return new Lecture(out, prochain, err);
    }

    private static String premiereLigne(String m) {
        if (m == null) return "?";
        int i = m.indexOf('\n');
        return i < 0 ? m : m.substring(0, i);
    }

    /**
     * Fichier illisible : copié à côté ({@code plaques.yml.corrompu-<date>}), jamais écrasé ; écritures bloquées. Le
     * staff corrige le fichier (ou remet la copie) puis recharge.
     */
    private void mettreDeCote() {
        bloque = true;
        Path src = fichier.toPath();
        Path cote = src.resolveSibling(fichier.getName() + ".corrompu-"
                + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(new java.util.Date()));
        try {
            Files.copy(src, cote, StandardCopyOption.REPLACE_EXISTING);
            log.severe("plaques.yml mis de côté : " + cote.getFileName() + " ; plaques.yml n'est plus écrit tant qu'il n'est pas"
                    + " corrigé (puis /sc reload).");
        } catch (IOException e) {
            log.log(Level.SEVERE, "plaques.yml : copie de sauvegarde impossible (le fichier reste intact, écritures bloquées)", e);
        }
    }

    /** Une plaque du fichier ; lève {@link IllegalArgumentException} (motif lisible) si elle ne se comprend pas. */
    private static Plaque lire(String id, ConfigurationSection c) {
        Plaque p = new Plaque(id);
        p.titre = c.getString("titre", "");
        List<Narrations.Segment> segs = new ArrayList<>();
        for (Object o : c.getList("segs", List.of())) {
            if (o instanceof List<?> l && !l.isEmpty()) {
                String t = String.valueOf(l.get(0));
                String col = l.size() > 1 && l.get(1) != null ? String.valueOf(l.get(1)) : "";
                segs.add(new Narrations.Segment(t, col));
            } else if (o != null) segs.add(Narrations.Segment.blanc(String.valueOf(o)));
        }
        p.segs = List.copyOf(segs);
        ConfigurationSection a = c.getConfigurationSection("ancre");
        if (a == null) throw new IllegalArgumentException("ancre absente");
        Ancre.Type type = Ancre.Type.lire(a.getString("type", ""));
        if (type == null) throw new IllegalArgumentException("type d'ancre inconnu « " + a.getString("type", "") + " »");
        String monde = a.getString("monde", "");
        if (type != Ancre.Type.OBJET && monde.isBlank()) throw new IllegalArgumentException("monde de l'ancre absent");
        p.ancre = switch (type) {
            case BLOC -> {
                List<Integer> b = a.getIntegerList("boite");
                yield Ancre.bloc(monde, a.getInt("x"), a.getInt("y"), a.getInt("z"),
                        b.size() > 0 ? b.get(0) : 1, b.size() > 1 ? b.get(1) : 1, b.size() > 2 ? b.get(2) : 1,
                        a.getString("face", ""));
            }
            case ENTITE -> {
                UUID e = uuid(a.getString("entite"));
                if (e == null) throw new IllegalArgumentException("UUID d'entité invalide « " + a.getString("entite") + " »");
                double w = a.getDouble("w", 1);
                yield Ancre.entite(monde, e, a.getDouble("x"), a.getDouble("y"), a.getDouble("z"), w, a.getDouble("h", 1),
                        a.getDouble("d", w));
            }
            case OBJET -> Ancre.objet();
        };
        p.portee = PlaqueRegles.bornerPortee(c.getInt("portee", PlaqueRegles.PORTEE_DEFAUT));
        p.actif = c.getBoolean("actif", true);
        p.expire = c.getLong("expire", 0);
        p.rev = Math.max(1, c.getInt("rev", 1));
        p.temporaire = c.getBoolean("temporaire", false);
        p.creeLe = c.getLong("cree.le", 0);
        p.creePar = uuid(c.getString("cree.par"));
        p.creePseudo = c.getString("cree.pseudo", "");
        p.modifLe = c.getLong("modifie.le", 0);
        p.modifPar = uuid(c.getString("modifie.par"));
        p.modifPseudo = c.getString("modifie.pseudo", "");
        p.lectures = c.getLong("lectures", 0);
        p.derniereLecture = c.getLong("derniere-lecture", 0);
        ConfigurationSection su = c.getConfigurationSection("supprimee");
        if (su != null) p.supprimee = new Plaque.Suppression(su.getLong("le"), uuid(su.getString("par")),
                su.getString("pseudo", ""), su.getString("motif", ""));
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
        y.options().setHeader(List.of("Plaques lues en concentration (lot 4). Modifiées en jeu : /plaque ou Poste de garde (F7) › Outils › Plaques."));
        y.set("prochain", prochain);
        for (Plaque p : plaques.values()) {
            String k = "plaques." + p.id + ".";
            y.set(k + "titre", p.titre);
            List<List<String>> segs = new ArrayList<>();
            for (Narrations.Segment s : p.segs) segs.add(List.of(s.texte(), s.couleur()));
            y.set(k + "segs", segs);
            Ancre a = p.ancre;
            y.set(k + "ancre.type", a.type().id());
            if (a.type() != Ancre.Type.OBJET) y.set(k + "ancre.monde", a.monde());
            if (a.type() == Ancre.Type.BLOC) {
                y.set(k + "ancre.x", a.x());
                y.set(k + "ancre.y", a.y());
                y.set(k + "ancre.z", a.z());
                y.set(k + "ancre.boite", List.of(a.dx(), a.dy(), a.dz()));
                if (!a.face().isEmpty()) y.set(k + "ancre.face", a.face());
            } else if (a.type() == Ancre.Type.ENTITE) {
                y.set(k + "ancre.entite", a.entite().toString());
                y.set(k + "ancre.x", a.ex());
                y.set(k + "ancre.y", a.ey());
                y.set(k + "ancre.z", a.ez());
                y.set(k + "ancre.w", a.ew());
                y.set(k + "ancre.h", a.eh());
                y.set(k + "ancre.d", a.ed());
            }
            y.set(k + "portee", p.portee);
            y.set(k + "actif", p.actif);
            y.set(k + "expire", p.expire);
            y.set(k + "rev", p.rev);
            y.set(k + "temporaire", p.temporaire);
            y.set(k + "cree.le", p.creeLe);
            if (p.creePar != null) y.set(k + "cree.par", p.creePar.toString());
            y.set(k + "cree.pseudo", p.creePseudo);
            if (p.modifLe > 0) {
                y.set(k + "modifie.le", p.modifLe);
                if (p.modifPar != null) y.set(k + "modifie.par", p.modifPar.toString());
                y.set(k + "modifie.pseudo", p.modifPseudo);
            }
            y.set(k + "lectures", p.lectures);
            y.set(k + "derniere-lecture", p.derniereLecture);
            if (p.supprimee != null) {
                y.set(k + "supprimee.le", p.supprimee.le());
                if (p.supprimee.par() != null) y.set(k + "supprimee.par", p.supprimee.par().toString());
                y.set(k + "supprimee.pseudo", p.supprimee.pseudo());
                y.set(k + "supprimee.motif", p.supprimee.motif());
            }
        }
        return y.saveToString();
    }

    /**
     * Écrit {@code yaml} (numéro {@code seq} de la sérialisation) de façon atomique ; une écriture plus ancienne qui
     * arriverait après une plus récente est sautée. N'importe quel fil.
     */
    public void ecrire(String yaml, long seq) {
        synchronized (verrou) { ecrireSousVerrou(yaml, seq); }
    }

    private void ecrireSousVerrou(String yaml, long seq) {
        if (bloque) {
            log.warning("plaques.yml : écriture refusée (le fichier n'a pas pu être chargé, voir plus haut).");
            return;
        }
        if (seq <= dernierEcrit) return;
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
        } catch (IOException e) {
            log.log(Level.WARNING, "plaques.yml : écriture impossible", e);
        }
    }
}
