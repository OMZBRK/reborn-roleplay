package fr.reborn.hud.monnaie;

/**
 * Solde de <b>RBCoins</b> du compte Reborn, pour le bloc RÉCOMPENSES de l'Échap (MONNAIE_ET_BANQUE.md §12, décision 12
 * du 15/10). Les RBCoins restent <b>distincts du ryo</b> (ADR 0006) : jamais dans la sacoche, jamais au guichet.
 *
 * <p>Canal prévu : le <b>launcher</b> passe le solde au lancement du jeu, comme le play-token — propriété système
 * {@code -Dreborn.rbcoins=<n>} (ou un fichier dont le chemin est donné par {@code -Dreborn.rbcoinsPath=<chemin>}, une
 * ligne = le nombre). Rien de sensible. Tant que le launcher ne l'envoie pas : {@code −1} → l'Échap affiche « — ».
 */
public final class RbCoins {

    private static long solde = Long.MIN_VALUE;

    private RbCoins() {}

    /** Solde connu, ou −1 si le launcher ne l'a pas transmis. */
    public static long solde() {
        if (solde != Long.MIN_VALUE) return solde;
        long n = -1;
        try {
            String v = System.getProperty("reborn.rbcoins");
            if (v != null && !v.isBlank()) n = Long.parseLong(v.trim());
            else {
                String p = System.getProperty("reborn.rbcoinsPath");
                if (p != null && !p.isBlank()) {
                    String t = java.nio.file.Files.readString(java.nio.file.Path.of(p)).trim();
                    if (!t.isEmpty()) n = Long.parseLong(t.split("\\s+")[0]);
                }
            }
        } catch (Exception ignored) {
            n = -1;
        }
        solde = n;
        return n;
    }

    /** Banc d'essai : solde imposé. */
    public static void debug(long n) { solde = n; }
}
