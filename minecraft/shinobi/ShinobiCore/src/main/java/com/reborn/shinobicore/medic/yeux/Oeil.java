package com.reborn.shinobicore.medic.yeux;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/**
 * Un œil d'un personnage (chirurgie des yeux) : son type ({@code normal}, {@code sharingan}, {@code byakugan},
 * {@code rinnegan}… — texte libre, minuscules), son état dans l'orbite et son intégrité (0..100).
 *
 * <p>Immuable : chaque modification produit un nouvel {@code Oeil} posé sur le personnage
 * ({@code ShinobiCharacter#setOeilGauche}/{@code setOeilDroit}). Un œil jamais touché n'est pas stocké
 * ({@code null}) : {@link YeuxService} le déduit du clan.
 */
public record Oeil(String type, Etat etat, int integrite) {

    /** État de l'orbite. */
    public enum Etat { NORMAL, MANQUANT }

    public static final String NORMAL_TYPE = "normal";

    public Oeil {
        type = normType(type);
        if (etat == null) etat = Etat.NORMAL;
        integrite = Math.max(0, Math.min(100, integrite));
    }

    public static Oeil intact(String type) { return new Oeil(type, Etat.NORMAL, 100); }

    /** Orbite vide (on garde le type de l'œil qui y était, pour mémoire). */
    public Oeil retire() { return new Oeil(type, Etat.MANQUANT, 0); }

    public boolean present() { return etat == Etat.NORMAL; }

    /** Aveugle de cet œil : orbite vide ou intégrité sous le seuil. */
    public boolean aveugle(int seuil) { return etat == Etat.MANQUANT || integrite < seuil; }

    public static String normType(String t) {
        if (t == null || t.isBlank()) return NORMAL_TYPE;
        return t.trim().toLowerCase(Locale.ROOT);
    }

    /** « Sharingan », « Normal »… pour l'affichage. */
    public static String label(String type) {
        String t = normType(type);
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    /* ------------------------------------------------------------- YAML */

    /** Lecture d'une section {@code yeux.gauche} ; {@code null} si absente (= œil jamais touché). */
    public static Oeil fromYaml(ConfigurationSection s) {
        if (s == null) return null;
        Etat e;
        try {
            e = Etat.valueOf(s.getString("etat", "NORMAL").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            e = Etat.NORMAL;
        }
        return new Oeil(s.getString("type", NORMAL_TYPE), e, s.getInt("integrite", 100));
    }

    /** Écrit l'œil sous {@code path} (rien si {@code null}). */
    public static void toYaml(ConfigurationSection root, String path, Oeil o) {
        if (o == null) return;
        root.set(path + ".type", o.type());
        root.set(path + ".etat", o.etat().name());
        root.set(path + ".integrite", o.integrite());
    }
}
