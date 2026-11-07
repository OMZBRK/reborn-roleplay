package com.reborn.shinobicore.character.repository;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Écritures des fichiers {@code characters/<compte>.yml}, sans aucun objet Bukkit (testable hors serveur).
 *
 * <p>Correctif du 07/11 (personnage supprimé qui revenait) : avant, chaque sauvegarde relisait le fichier sur le
 * disque, le réécrivait en entier et confiait l'écriture au planificateur asynchrone (tâches sans ordre garanti) ;
 * la suppression écrivait tout de suite, puis les écritures périmées de la sauvegarde arrivaient au tick suivant et
 * restauraient le personnage. Désormais :
 * <ul>
 *   <li><b>Génération par fichier</b> : chaque contenu reçoit un numéro croissant au moment où il est produit (fil
 *       principal) ; une écriture ne touche le disque que si son numéro est plus récent que le dernier écrit (sous
 *       le verrou du fichier). Les écritures dans le désordre ne peuvent plus revenir en arrière.</li>
 *   <li><b>Contenu en attente</b> : tant qu'une écriture n'a pas atteint le disque, {@link #lire} rend ce contenu
 *       plutôt que le fichier (deux sauvegardes du même tick ne s'écrasent plus l'une l'autre).</li>
 *   <li><b>Pierres tombales</b> : les personnages supprimés pendant la session ; le dépôt ne les réécrit jamais.</li>
 *   <li><b>Écriture atomique</b> : fichier temporaire puis renommage (jamais de fichier à moitié écrit).</li>
 * </ul>
 */
public final class FichiersPersonnages {

    /** Exécution des écritures en arrière-plan (le planificateur Bukkit en jeu, une file manuelle en test). */
    public interface Planificateur {
        /** Faux pendant l'arrêt du plugin : l'écriture se fait alors tout de suite (le planificateur l'annulerait). */
        boolean asyncDisponible();

        void async(Runnable tache);
    }

    /** État d'un fichier. Les champs « en mémoire » sont gardés par le moniteur de l'objet, l'écriture par {@link #verrou}. */
    private static final class Etat {
        final Object verrou = new Object();
        final File fichier;
        Etat(File fichier) { this.fichier = fichier; }
        /** Dernière génération attribuée (fil principal). */
        long genPrise;
        /** Dernière génération arrivée sur le disque (sous {@link #verrou}). */
        long genEcrite;
        /**
         * Dernière génération <i>tentée</i>, réussie ou non (sous {@link #verrou}). C'est elle qui sert de seuil : une
         * écriture plus récente qui échoue rend quand même périmées les plus anciennes encore en file (sinon une
         * suppression ratée laisserait une vieille sauvegarde remettre le personnage sur le disque).
         */
        long genVue;
        /** La dernière tentative a échoué : le contenu en attente reste à écrire ({@link #reessayerEchecs}). */
        boolean echec;
        /** Contenu le plus récent pas encore confirmé sur le disque ; {@code null} = le disque fait foi. */
        Contenu attente;
    }

    /** {@code texte == null} : le fichier doit être absent. */
    private record Contenu(String texte) {}

    private final Logger log;
    private final Planificateur planif;
    private final ConcurrentMap<String, Etat> etats = new ConcurrentHashMap<>();
    private final Set<UUID> tombes = ConcurrentHashMap.newKeySet();

    public FichiersPersonnages(Logger log, Planificateur planif) {
        this.log = log;
        this.planif = planif;
    }

    /* ================================================================ pierres tombales */

    /** Le personnage est supprimé pour la session : plus aucune sauvegarde ne le réécrit. */
    public void enterrer(UUID characterId) {
        if (characterId != null) tombes.add(characterId);
    }

    public boolean estEnterre(UUID characterId) {
        return characterId != null && tombes.contains(characterId);
    }

    /** Copie des pierres tombales (pour nettoyer un fichier avant de le réécrire). */
    public Set<UUID> tombes() {
        return Set.copyOf(tombes);
    }

    /* ================================================================ lecture */

    /**
     * Contenu le plus récent du fichier : celui en attente d'écriture s'il y en a un, sinon le disque. {@code null} si
     * le fichier est (ou doit être) absent.
     */
    public String lire(File f) throws IOException {
        Etat e = etat(f);
        Contenu c;
        synchronized (e) { c = e.attente; }
        if (c != null) return c.texte();
        // Aucune attente : toutes les écritures produites sont arrivées, le disque est à jour.
        if (!f.isFile()) return null;
        // Décodage tolérant (comme YamlConfiguration.loadConfiguration avant) : un fichier retouché dans un éditeur
        // en ANSI (accents en ISO-8859-1) se lit avec des caractères de remplacement au lieu d'être refusé en bloc.
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    /* ================================================================ écriture */

    /** Écriture différée (arrière-plan) : la génération est prise maintenant, dans l'ordre des appels. */
    public long ecrire(File f, String texte) {
        Etat e = etat(f);
        long gen = prendre(e, texte);
        if (!planif.asyncDisponible()) {
            ecrireSiPlusRecent(f, e, gen, texte);
        } else {
            planif.async(() -> ecrireSiPlusRecent(f, e, gen, texte));
        }
        return gen;
    }

    /**
     * Écriture immédiate (suppression) : prend une génération et l'écrit tout de suite sous le verrou du fichier ;
     * toute écriture plus ancienne encore en file sera ignorée. {@code texte == null} : supprime le fichier.
     */
    public long ecrireMaintenant(File f, String texte) {
        Etat e = etat(f);
        long gen = prendre(e, texte);
        ecrireSiPlusRecent(f, e, gen, texte);
        return gen;
    }

    private static long prendre(Etat e, String texte) {
        synchronized (e) {
            long gen = ++e.genPrise;
            e.attente = new Contenu(texte);
            return gen;
        }
    }

    private void ecrireSiPlusRecent(File f, Etat e, long gen, String texte) {
        boolean ok = false;
        synchronized (e.verrou) {
            // Périmée : une écriture plus récente a déjà été tentée (réussie ou non). Une génération plus ancienne ne
            // doit jamais passer après, même si la plus récente a échoué.
            if (gen <= e.genVue) return;
            e.genVue = gen;
            try {
                if (texte == null) Files.deleteIfExists(f.toPath());
                else ecritureAtomique(f.toPath(), texte);
                e.genEcrite = gen;
                ok = true;
            } catch (IOException | RuntimeException ex) {
                log.log(Level.SEVERE, "Écriture de " + f.getName() + " impossible (contenu gardé en mémoire, "
                        + "réécrit à la prochaine sauvegarde du compte ou à l'arrêt du plugin)", ex);
            }
        }
        // Le disque porte la dernière génération produite : plus besoin de garder le contenu en mémoire. En cas d'échec,
        // le contenu le plus récent reste en attente : les lectures repartent de lui et il sera réécrit.
        synchronized (e) {
            if (ok) {
                if (e.genPrise == gen) { e.attente = null; e.echec = false; }
            } else {
                e.echec = true;
            }
        }
    }

    /**
     * Réécrit tout de suite le contenu en attente des fichiers dont la dernière écriture a échoué (appelé à l'arrêt du
     * plugin, pour ne pas laisser sur le disque un état plus ancien que la mémoire, par exemple un personnage supprimé).
     */
    public void reessayerEchecs() {
        for (Etat e : etats.values()) {
            String texte;
            boolean aFaire;
            synchronized (e) {
                aFaire = e.echec && e.attente != null;
                texte = aFaire ? e.attente.texte() : null;
            }
            if (aFaire) ecrireMaintenant(e.fichier, texte);
        }
    }

    /** Fichier temporaire puis renommage atomique (repli : renommage simple, puis écriture directe). */
    static void ecritureAtomique(Path cible, String texte) throws IOException {
        Path dossier = cible.getParent();
        if (dossier != null) Files.createDirectories(dossier);
        Path tmp = cible.resolveSibling(cible.getFileName() + ".tmp");
        Files.writeString(tmp, texte, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, cible, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, cible, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            // Renommage refusé (fichier ouvert ailleurs sous Windows…) : écriture directe plutôt que rien.
            Files.writeString(cible, texte, StandardCharsets.UTF_8);
            Files.deleteIfExists(tmp);
        }
    }

    private Etat etat(File f) {
        return etats.computeIfAbsent(f.getAbsolutePath(), k -> new Etat(f.getAbsoluteFile()));
    }
}
