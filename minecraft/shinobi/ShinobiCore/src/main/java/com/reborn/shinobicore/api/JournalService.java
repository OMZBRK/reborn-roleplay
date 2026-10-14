package com.reborn.shinobicore.api;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * <b>Journal universel</b> (décision du 14/10 : « tout est référencé et traçable, quel que soit le grade »).
 *
 * <p>Un seul point d'entrée pour tous les journaux du serveur : chaque entrée porte l'horodatage, la catégorie,
 * l'action, l'acteur (N° du personnage, pseudo, nom RP, grade staff), la cible éventuelle, un détail libre et le
 * lieu. Stockage : un fichier par catégorie et par jour ({@code plugins/ShinobiCore/logs/<categorie>-AAAA-MM-JJ.log},
 * rétention réglable) + un index en mémoire des dernières entrées pour le Poste de garde. L'écriture disque est
 * asynchrone : un appel ne bloque jamais le tick.
 *
 * <p>Utilisation (thread principal ou non, l'identité est résolue sur le thread principal) :
 * <pre>{@code
 * journal.entree(Categorie.COMBAT, "DEGATS").acteur(attaquant).cible(victime)
 *        .detail("6,5 PV · kunai").lieu(victime.getLocation()).ecrire();
 * }</pre>
 *
 * <p>Résolu via le {@code ServicesManager} de Bukkit. Règle : <b>aucune action silencieuse</b> sur l'argent, les
 * objets, les personnages et les actions staff.
 */
@Stable
public interface JournalService {

    /** Catégories du journal (protocole : docs/technique/PLUGINS_REFERENCE.md §4 bis.1). */
    enum Categorie {
        JOUEUR("Joueur"),
        INVENTAIRE("Inventaire"),
        OBJET("Objet"),
        COMBAT("Combat"),
        KO("KO / mort"),
        DUEL("Duel"),
        ECONOMIE("Économie"),
        INTERACTION("Interaction"),
        /** Radio en escouades (ShinobiRadio, 14/10) : escouades, oreillettes, émissions — jamais le contenu vocal. */
        RADIO("Radio"),
        BIBLIOTHEQUE("Bibliothèque"),
        STAFF("Staff"),
        COMMANDE("Commande"),
        CHAT("Tchat"),
        SYSTEME("Système");

        private final String label;

        Categorie(String label) { this.label = label; }

        public String label() { return label; }

        /** Nom de fichier : {@code inventaire}, {@code staff}… */
        public String fichier() { return name().toLowerCase(java.util.Locale.ROOT); }

        /** Lecture tolérante ({@code "combat"}, {@code "Économie"}, {@code "ko"}…) ; null si inconnue. */
        public static Categorie parse(String s) {
            if (s == null || s.isBlank()) return null;
            String n = java.text.Normalizer.normalize(s.trim(), java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").toUpperCase(java.util.Locale.ROOT);
            for (Categorie c : values()) if (c.name().equals(n)) return c;
            return switch (n) {
                case "INV", "SAC", "SACOCHE" -> INVENTAIRE;
                case "ITEM", "OBJETS" -> OBJET;
                case "MORT", "RPK" -> KO;
                case "ECO", "RYO", "ARGENT" -> ECONOMIE;
                case "BIBLIO", "PARCHEMIN", "PARCHEMINS" -> BIBLIOTHEQUE;
                case "CMD", "COMMANDES" -> COMMANDE;
                case "TCHAT", "MESSAGE", "MESSAGES" -> CHAT;
                case "SYSTEM", "SERVEUR" -> SYSTEME;
                case "PLAYER" -> JOUEUR;
                case "ESCOUADE", "OREILLETTE" -> RADIO;
                default -> null;
            };
        }
    }

    /** Commence une entrée ; ne rien oublier : {@link Entree#ecrire()}. */
    Entree entree(Categorie categorie, String action);

    /** Raccourci : acteur et cible en ligne (chacun peut être null), sans lieu. */
    default void noter(Categorie categorie, String action, Player acteur, Player cible, String detail) {
        Entree e = entree(categorie, action);
        if (acteur != null) e.acteur(acteur);
        if (cible != null) e.cible(cible);
        e.detail(detail).ecrire();
    }

    /** Une entrée en cours de construction. Les méthodes renvoient {@code this}. */
    interface Entree {
        /** Joueur en ligne : compte, pseudo, N° et nom RP du personnage actif, grade staff. */
        Entree acteur(Player p);

        /** Joueur, console ({@code CONSOLE}) ou bloc de commande. */
        Entree acteur(CommandSender s);

        /** Compte (en ligne ou non) ; le personnage actif s'il est en ligne. */
        Entree acteur(UUID compte);

        Entree cible(Player p);

        Entree cible(UUID compte);

        /** Cible = un personnage précis (hors ligne compris) : son N° et son nom RP. */
        Entree ciblePersonnage(UUID compte, UUID personnage);

        /** Cible sans compte (un objet, une zone, une bibliothèque…) : texte libre. */
        Entree cibleTexte(String texte);

        Entree detail(String detail);

        Entree lieu(Location l);

        /** Enregistre (index mémoire tout de suite, disque en tâche de fond). */
        void ecrire();
    }
}
