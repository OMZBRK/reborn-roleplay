package fr.reborn.hud.parchemin;

import java.util.List;

/**
 * Une technique telle qu'un parchemin la porte (données de preview, en attendant le registre serveur).
 *
 * @param rank     D, C, B, A ou S
 * @param branch   Taïjutsu, Kenjutsu ou Ninjutsu
 * @param nature   nature du chakra (Katon…), vide hors Ninjutsu
 */
public record Technique(String id, String name, String branch, String nature, char rank, String desc,
                        List<String> signs) {

    /** Séances à réussir pour apprendre (docs/PROPOSITION_PARCHEMINS.md §3.3). */
    public int seances() {
        return switch (rank) {
            case 'D' -> 1;
            case 'C' -> 3;
            case 'B' -> 6;
            case 'A' -> 12;
            default -> 20;
        };
    }

    public String difficulty() {
        return switch (rank) {
            case 'D' -> "Simple";
            case 'C' -> "Exigeante";
            case 'B' -> "Difficile";
            case 'A' -> "Redoutable";
            default -> "Légendaire";
        };
    }

    /** Poids du rouleau dans la sacoche (kg). */
    public double weight() {
        return switch (rank) {
            case 'S' -> 0.8;
            case 'A' -> 0.5;
            default -> 0.2;
        };
    }

    public int rankIndex() { return "DCBAS".indexOf(rank); }

    public String typeLine() {
        return nature.isEmpty() ? branch : branch + " · " + nature;
    }

    /** Démo : un échantillon des 39 techniques de la beta. */
    public static final List<Technique> DEMO = List.of(
        t("konoha_senpu", "Konoha Senpū", "Taïjutsu", "", 'D', "Un balayage tournoyant à hauteur de chevilles, suivi d'un coup de pied retourné.", List.of()),
        t("konoha_reppu", "Konoha Reppū", "Taïjutsu", "", 'D', "Un coup de pied rasant qui fauche l'adversaire et le met au sol.", List.of()),
        t("dynamic_entry", "Entrée fracassante", "Taïjutsu", "", 'D', "Un coup de pied sauté porté avec tout l'élan de la course.", List.of()),
        t("shishi_rendan", "Shishi Rendan", "Taïjutsu", "", 'C', "Projeter l'adversaire dans les airs, le suivre à son ombre et l'écraser au sol.", List.of()),
        t("kage_buyo", "Kage Buyō", "Taïjutsu", "", 'C', "La danse de l'ombre de la feuille : suivre un adversaire projeté, collé à son dos.", List.of()),
        t("omote_renge", "Omote Renge", "Taïjutsu", "", 'B', "Le Lotus recto : ouvrir la première porte et plonger tête la première avec l'adversaire ligoté.", List.of()),
        t("ura_renge", "Ura Renge", "Taïjutsu", "", 'A', "Le Lotus verso : enchaîner les portes, au prix du corps.", List.of()),
        t("hachimon", "Hachimon Tonkō", "Taïjutsu", "", 'S', "Ouvrir les huit portes du chakra. Une puissance démesurée, une vie mise en jeu.", List.of()),
        t("iai_giri", "Iai-giri", "Kenjutsu", "", 'D', "Dégainer et trancher d'un même mouvement.", List.of()),
        t("kesa_giri", "Kesa-giri", "Kenjutsu", "", 'D', "La coupe diagonale, de l'épaule à la hanche.", List.of()),
        t("tsubame_gaeshi", "Tsubame Gaeshi", "Kenjutsu", "", 'C', "Le retour de l'hirondelle : une coupe et son retour, trop vite pour être parés ensemble.", List.of()),
        t("mikazuki", "Mikazuki no Mai", "Kenjutsu", "", 'B', "La danse du croissant de lune : trois clones frappent ensemble.", List.of()),
        t("kiri_sakura", "Konoha Ryū : Sakura", "Kenjutsu", "", 'A', "Une coupe si nette que la cible ne la sent qu'après coup.", List.of()),
        t("kawarimi", "Kawarimi", "Ninjutsu", "", 'D', "Se substituer à un objet au dernier instant.", List.of("Bélier", "Sanglier", "Bœuf", "Chien", "Serpent")),
        t("bunshin", "Bunshin", "Ninjutsu", "", 'D', "Créer des illusions de soi pour tromper l'adversaire.", List.of("Bélier", "Serpent", "Tigre")),
        t("katon_hosenka", "Katon : Hōsenka", "Ninjutsu", "Katon", 'D', "Une volée de petites boules de feu, chacune pouvant cacher un shuriken.", List.of("Rat", "Tigre", "Chien", "Bœuf", "Lièvre", "Tigre")),
        t("katon_gokakyu", "Katon : Gōkakyū", "Ninjutsu", "Katon", 'C', "Rassembler le chakra dans la poitrine et souffler une grande boule de feu.", List.of("Serpent", "Bélier", "Singe", "Sanglier", "Cheval", "Tigre")),
        t("katon_ryuka", "Katon : Ryūka", "Ninjutsu", "Katon", 'B', "Une coulée de flammes qui suit un fil tendu jusqu'à la cible.", List.of("Serpent", "Dragon", "Lièvre", "Tigre")),
        t("suiton_mizurappa", "Suiton : Mizurappa", "Ninjutsu", "Suiton", 'D', "Un jet d'eau puissant craché vers l'adversaire.", List.of("Tigre", "Serpent", "Rat")),
        t("suiton_suiryudan", "Suiton : Suiryūdan", "Ninjutsu", "Suiton", 'B', "Un dragon d'eau qui s'abat sur la cible.", List.of("Bœuf", "Singe", "Lièvre", "Rat", "Sanglier", "Oiseau")),
        t("doton_doryuheki", "Doton : Doryūheki", "Ninjutsu", "Doton", 'C', "Dresser un mur de terre devant soi.", List.of("Tigre", "Lièvre", "Sanglier", "Chien")),
        t("doton_yomi", "Doton : Yomi Numa", "Ninjutsu", "Doton", 'A', "Changer le sol en un marécage sans fond.", List.of("Bœuf", "Lièvre", "Rat", "Tigre")),
        t("futon_daitoppa", "Fūton : Daitoppa", "Ninjutsu", "Fūton", 'C', "Une rafale qui balaie tout devant soi.", List.of("Tigre", "Bœuf", "Chien")),
        t("raiton_chidori", "Chidori", "Ninjutsu", "Raiton", 'A', "Concentrer la foudre dans la main et charger. Le chant de mille oiseaux.", List.of("Bœuf", "Lièvre", "Singe")),
        t("rasengan", "Rasengan", "Ninjutsu", "", 'S', "Une sphère de chakra en rotation, sans aucun signe. Une technique qui ne s'apprend qu'auprès d'un maître.", List.of())
    );

    private static Technique t(String id, String name, String branch, String nature, char rank, String desc, List<String> signs) {
        return new Technique(id, name, branch, nature, rank, desc, signs);
    }
}
