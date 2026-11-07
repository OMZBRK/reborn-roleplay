package com.reborn.shinobicore.concentration.plaque;

import com.reborn.shinobicore.staff.outils.Narrations;

import java.util.List;
import java.util.UUID;

/**
 * Une plaque du monde (lot 4, étape B) : un texte posé par le staff, lu <b>seulement en concentration, face à
 * l'objet</b> (décisions du 01/11 et du 07/11). Le texte n'est envoyé qu'au joueur concentré devant la plaque, à
 * portée et en vue ({@link PlaqueService#verifierLecture}) ; les autres ne reçoivent que l'ancre, sans le texte.
 *
 * <p>Données gardées dans {@code plugins/ShinobiCore/plaques.yml} ({@link PlaqueStore}) ; {@code rev} monte à chaque
 * changement du texte ou de l'ancre : le cache des clients ({@code (id, rev)}) tombe.
 */
public final class Plaque {

    /** Suppression douce (corbeille 30 jours) : quand, par qui, pourquoi. */
    public record Suppression(long le, UUID par, String pseudo, String motif) { }

    public final String id;
    /** Titre interne, vu du staff seulement. */
    public String titre = "";
    public List<Narrations.Segment> segs = List.of();
    public Ancre ancre;
    /** Portée de lecture (blocs, 2 à 16) ; le serveur tolère + 1,5 bloc. */
    public int portee = PlaqueRegles.PORTEE_DEFAUT;
    public boolean actif = true;
    /** Expiration (epoch ms), 0 = jamais. */
    public long expire;
    public int rev = 1;
    /** Plaque d'animateur (mode animation, sans grade Modérateur) : 24 h au plus, modifiable par son auteur. */
    public boolean temporaire;
    public long creeLe, modifLe;
    public UUID creePar, modifPar;
    public String creePseudo = "", modifPseudo = "";
    public long lectures, derniereLecture;
    public Suppression supprimee;

    public Plaque(String id) { this.id = id; }

    /** Dans la corbeille. */
    public boolean dansCorbeille() { return supprimee != null; }

    /** Lisible maintenant : active, hors corbeille, non expirée. */
    public boolean lisible(long now) {
        return actif && supprimee == null && (expire <= 0 || now < expire) && ancre != null && !segs.isEmpty();
    }

    /** Texte à plat (journal, liste du staff). */
    public String aPlat(int max) { return Narrations.aPlat(segs, max); }

    /** Le texte en balises de la narration (édition au tchat, {@code /plaque voir}). */
    public String balise() {
        StringBuilder b = new StringBuilder();
        for (Narrations.Segment s : segs) {
            if (s.couleur().isEmpty()) b.append(s.texte());
            else b.append('<').append(s.couleur()).append('>').append(s.texte()).append("</>");
        }
        return b.toString();
    }

    /** Changement du texte, de l'ancre ou de l'état : les caches des clients tombent. */
    public void changer(UUID par, String pseudo, long now) {
        rev++;
        modifLe = now;
        modifPar = par;
        modifPseudo = pseudo == null ? "" : pseudo;
    }
}
