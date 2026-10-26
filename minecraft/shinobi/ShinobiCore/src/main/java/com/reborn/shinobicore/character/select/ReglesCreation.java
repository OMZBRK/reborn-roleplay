package com.reborn.shinobicore.character.select;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Règles de création d'un personnage, <b>côté serveur</b> (CANDIDATURE_ET_DOUANE.md §4.2, décisions du 24/10) — règles
 * pures, sans Bukkit. Le mod applique les mêmes, mais un client modifié qui envoie autre chose est refusé ici.
 *
 * <ul>
 *   <li><b>Tous les clans passent par une session de clan</b> (Q4) : hors staff, aucun clan de la liste à la création ;
 *       « Sans clan » = clan « Autre » du mod, avec un nom de famille libre, qui voyage dans le champ clan.</li>
 *   <li><b>Premier personnage issu de la candidature</b> (Q8, Q13) : village et âge imposés ; prénom et nom de famille
 *       pré-remplis mais modifiables (3 à 20 caractères) ; sexe libre.</li>
 *   <li>Personnages suivants : libres (village, âge), toujours sans clan de session.</li>
 *   <li>Staff : aucun verrou (seul le prénom non vide est exigé, comme avant).</li>
 * </ul>
 *
 * <p>Les listes doivent rester alignées sur celles du mod ({@code CharacterCreateScreen.VILLAGES} et {@code CLANS}).
 */
public final class ReglesCreation {

    private ReglesCreation() {}

    /** Villages ouverts à la création (Déserteur exclu, comme dans le mod). */
    public static final List<String> VILLAGES = List.of(
            "Konohagakure", "Sunagakure", "Kirigakure", "Kumogakure", "Iwagakure", "Amegakure");

    /** Clans de la liste du mod (« Autre » exclu) : ils se rejoignent en session de clan. */
    public static final List<String> CLANS = List.of(
            "Senju", "Uchiha", "Hyuga", "Nara", "Sarutobi", "Uzumaki", "Aburame", "Akimichi", "Hatake", "Yamanaka",
            "Kurama", "Sabaku", "Hoki", "Hozuki", "Hoshigaki", "Kaguya", "Yuki", "Terumi", "Yotsuki", "Kamizuru",
            "Onoki");

    /** Clan personnalisé du mod : « Sans clan » côté candidature. */
    public static final String AUTRE = "Autre";

    public static final int AGE_MIN = 10, AGE_MAX = 60;
    public static final int NOM_MIN = 3, NOM_MAX = 20;
    /** Taille adulte, en multiplicateur de 180 cm : de 153 à 207 cm. */
    public static final double TAILLE_MIN = 153 / 180.0, TAILLE_MAX = 207 / 180.0;

    /** Demande de création telle qu'envoyée par le mod ({@code create\n<nom>\n<clan>\n<village>\n<sexe>\n<âge>\n<taille>}). */
    public record Demande(String nom, String clan, String village, String sexe, int age, double taille) {}

    /**
     * Refus : {@code etape} = étape de la création à rouvrir côté mod ({@code village}, {@code clan}, {@code sexe},
     * {@code nom}, {@code age} ou {@code general}), {@code message} en français, montré au joueur.
     */
    public record Refus(String etape, String message) {}

    /** Verrous du premier personnage issu de la candidature ; {@code null} = aucun (village / âge libres). */
    public record Verrous(String village, Integer age) {
        /** Verrous applicables d'une candidature : une valeur hors des listes du jeu n'est pas imposée. */
        public static Verrous de(CandidatureClient.Candidature cand) {
            if (cand == null) return new Verrous(null, null);
            String v = villageCanonique(cand.village());
            Integer a = cand.age() != null && cand.age() >= AGE_MIN && cand.age() <= AGE_MAX ? cand.age() : null;
            return new Verrous(v, a);
        }
    }

    /** Prénom ou nom de famille valide : 3 à 20 caractères, lettres, espaces, apostrophes et tirets (règle du mod). */
    public static boolean nomValide(String s) {
        if (s == null) return false;
        String n = s.strip();
        if (n.length() < NOM_MIN || n.length() > NOM_MAX) return false;
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (!(Character.isLetter(c) || c == ' ' || c == '\'' || c == '’' || c == '-')) return false;
        }
        return true;
    }

    /** Village de la liste (casse ignorée), sous sa forme canonique, ou {@code null}. */
    public static String villageCanonique(String v) {
        if (v == null) return null;
        String n = v.strip();
        for (String x : VILLAGES) if (x.equalsIgnoreCase(n)) return x;
        return null;
    }

    /**
     * Clé de comparaison d'un nom : sans accents ni casse, comme {@code cleNom} côté API et {@code Conditions.norm}
     * de la boutique. Une graphie accentuée d'un clan est donc le même clan, partout.
     */
    static String cleNom(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** Clan de session (casse et accents ignorés), sous sa forme canonique, ou {@code null} (nom de famille libre, vide…). */
    public static String clanDeSession(String c) {
        if (c == null) return null;
        String k = cleNom(c);
        if (k.isEmpty()) return null;
        for (String x : CLANS) if (cleNom(x).equals(k)) return x;
        return null;
    }

    /** Vrai pour le clan « Autre » du mod (casse et accents ignorés). */
    public static boolean estAutre(String c) {
        return c != null && cleNom(AUTRE).equals(cleNom(c));
    }

    /** « Homme » / « Femme » (casse ignorée), ou {@code null}. */
    public static String sexeCanonique(String s) {
        if (s == null) return null;
        if ("homme".equalsIgnoreCase(s.strip())) return "Homme";
        if ("femme".equalsIgnoreCase(s.strip())) return "Femme";
        return null;
    }

    /** Taille ramenée dans les bornes du jeu ; une valeur illisible vaut 172 cm. */
    public static double tailleBornee(double t) {
        if (Double.isNaN(t) || Double.isInfinite(t)) return 172 / 180.0;
        return Math.max(TAILLE_MIN, Math.min(TAILLE_MAX, t));
    }

    /**
     * Contrôle d'une création. {@code staff} : aucun verrou. {@code premier} : premier personnage issu de la
     * candidature, soumis à {@code verrous}. Renvoie {@code null} si la création est acceptée.
     */
    public static Refus verifier(Demande d, boolean staff, boolean premier, Verrous verrous) {
        String nom = d.nom() == null ? "" : d.nom().strip();
        if (nom.isEmpty()) return new Refus("nom", "Le prénom ne peut pas être vide.");
        if (staff) return null;

        if (!nomValide(nom)) {
            return new Refus("nom", "Ton prénom : lettres, espaces, apostrophes et tirets, de 3 à 20 caractères.");
        }
        if (sexeCanonique(d.sexe()) == null) return new Refus("sexe", "Choisis Homme ou Femme.");
        String village = villageCanonique(d.village());
        if (village == null) return new Refus("village", "Choisis un des six villages.");
        if (d.age() < AGE_MIN || d.age() > AGE_MAX) {
            return new Refus("age", "L'âge va de " + AGE_MIN + " à " + AGE_MAX + " ans.");
        }

        String clan = d.clan() == null ? "" : d.clan().strip();
        if (clanDeSession(clan) != null) {
            return new Refus("clan", "Les clans se rejoignent en session de clan, annoncée sur le Discord : "
                    + "choisis « Sans clan ».");
        }
        if (estAutre(clan)) clan = "";
        if (premier && clan.isEmpty()) return new Refus("clan", "Écris ton nom de famille.");
        if (!clan.isEmpty() && !nomValide(clan)) {
            return new Refus("clan", "Ton nom de famille : lettres, espaces, apostrophes et tirets, de 3 à 20 caractères.");
        }

        if (premier && verrous != null) {
            if (verrous.village() != null && !verrous.village().equalsIgnoreCase(village)) {
                return new Refus("village", "Ta candidature t'attache à " + verrous.village() + ".");
            }
            if (verrous.age() != null && verrous.age() != d.age()) {
                return new Refus("age", "Ta candidature fixe l'âge de ton personnage à " + verrous.age() + " ans.");
            }
        }
        return null;
    }

    /** Clan à enregistrer : nom de famille libre (ou « None » sans nom) ; le staff garde sa saisie telle quelle. */
    public static String clanAEnregistrer(String clan, boolean staff) {
        String c = clan == null ? "" : clan.strip();
        if (staff) return c.isEmpty() ? "None" : c;
        if (c.isEmpty() || estAutre(c)) return "None";
        return c;
    }
}
