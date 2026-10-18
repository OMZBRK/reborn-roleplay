package com.reborn.shinobicore.api.etat;

import com.reborn.shinobicore.api.Stable;

import java.util.Locale;

/**
 * Une <b>capacité</b> (§4.5) : une question fermée que pose un système avant d'agir — « peut-il X ? ». Calculée par la
 * matrice à partir des couches et des modificateurs, jamais stockée.
 *
 * <p>Niveau d'une capacité : {@code 2} permis, {@code 1} limité (chuchoter, ramper, techniques sans mudra…),
 * {@code 0} refusé. Les capacités <b>spéciales</b> partent de 0 et ne sont qu'<b>accordées</b> par une ligne.
 */
@Stable
public enum Capacite {
    /* déplacement */
    BOUGER("bou"), TROTTER("tro"), SPRINTER("spr"), SAUTER("sau"), MOBILITE("mob"),
    /* combat */
    FRAPPER_MAINS("fra"), FRAPPER_ARME(null), FRAPPER_PROJECTILE(null), GARDER("gar"), TECHNIQUE("tec"), CANALISER(null),
    /* objets */
    UTILISER_OBJET(null), OUVRIR_SACOCHE(null), MODIFIER_SACOCHE(null), LACHER(null), RAMASSER("ram"), DEGAINER(null),
    CONSOMMER(null),
    /* interaction (vers autrui) */
    INTERAGIR("int"), PORTER(null), EXECUTER("exe"), SOIGNER(null), FOUILLER(null),
    /* ce que les autres peuvent faire sur lui */
    ETRE_PORTE(null), ETRE_FOUILLE(null), ETRE_EXECUTE(null),
    /* communication */
    PARLER("par"), VOCAL("voc"), RADIO(null), EMOTE(null), COMMANDES(null),
    /* présence */
    VISIBLE(null), CIBLABLE(null), NOM_VISIBLE(null),
    /* conséquences */
    TOMBE_KO(null), BLESSURES(null), XP_COMBAT(null),
    /* session */
    CHANGER_PERSO(null), SE_DECONNECTER_SANS_SUITE(null), TELEPORTE_PAR_COMMANDE(null), MODE_STAFF(null), DUEL(null),
    /* spéciales (accordées seulement) */
    SE_DEBATTRE("debattre", true), VOLONTE(null, true), HOPITAL(null, true), DISSIPER(null, true),
    ROMPRE_LIEN(null, true), VOLER(null, true);

    private final String code;
    private final boolean speciale;

    Capacite(String code) { this(code, false); }

    Capacite(String code, boolean speciale) {
        this.code = code;
        this.speciale = speciale;
    }

    /** Clé courte dans le paquet {@code reborn:etat} ({@code cap}), ou null si le client ne s'en sert pas. */
    public String code() { return code; }

    /** Spéciale : refusée par défaut, seulement accordée (★ de la matrice). */
    public boolean speciale() { return speciale; }

    /** Lecture tolérante d'un nom ({@code "technique"}, {@code "TECHNIQUE"}), null si inconnu. */
    public static Capacite lire(String s) {
        if (s == null) return null;
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
