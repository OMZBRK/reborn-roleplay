package com.reborn.shinobicore.mudra;

import com.reborn.shinobicore.technique.Mudra;

import java.util.List;
import java.util.UUID;

/**
 * Une porte scellée (demande du 14/11) : une dalle de blocs qui coulisse vers le haut quand la bonne suite de mudras est
 * composée devant elle. Posée par le staff ({@code /porte}), gardée dans {@code plugins/ShinobiCore/mudras.yml}
 * ({@link PorteStore}).
 *
 * <p><b>La suite ne part jamais chez un client</b> : ni la présence d'une porte ni la justesse d'un signe ne sont
 * envoyées ({@link MudraService}).
 */
public final class PorteScellee {

    public final String id;
    /** Nom vu du staff seulement (32 caractères au plus). */
    public String nom = "";
    public String monde = "";
    /** Boîte de la dalle, porte fermée. */
    public MudraRegles.Boite boite;
    /** Suite qui ouvre (2 à 12 mudras). */
    public List<Mudra> signes = List.of();
    /** Blocs de levée (1 à la hauteur de la dalle). */
    public int leve = 1;
    public long creeLe, modifLe;
    public UUID creePar, modifPar;
    public String creePseudo = "", modifPseudo = "";
    /**
     * La dalle n'est pas à sa place (levée, en mouvement, ou restée ouverte à un arrêt brutal) : {@link #blocs} dit quoi
     * remettre. Écrit <b>avant</b> de toucher au monde.
     */
    public boolean ouverte;
    /**
     * {@code BlockData#getAsString()} de chaque bloc de la dalle, dans l'ordre fixe de {@link MudraRegles.Boite#indice},
     * relevés à l'ouverture ; vide porte fermée.
     */
    public List<String> blocs = List.of();

    public PorteScellee(String id) { this.id = id; }

    public void changer(UUID par, String pseudo, long now) {
        modifLe = now;
        modifPar = par;
        modifPseudo = pseudo == null ? "" : pseudo;
    }

    /** « D1 "Porte du temple" » pour le staff et le journal. */
    public String libelle() { return nom.isEmpty() ? id : id + " « " + nom + " »"; }
}
