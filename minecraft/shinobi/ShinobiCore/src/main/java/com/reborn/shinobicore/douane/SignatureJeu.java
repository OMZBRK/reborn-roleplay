package com.reborn.shinobicore.douane;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.regex.Pattern;

/**
 * Signature des routes du jeu (contrat v1, {@code apps/api/src/game/game-signature.ts}) — règles pures, sans Bukkit.
 *
 * <p>Chaîne signée (UTF-8, lignes séparées par « \n », pas de « \n » final) :
 * <pre>
 * REBORN-GAME-V1
 * MÉTHODE en majuscules
 * chemin brut (préfixe /v1 et requête « ?… » compris, sans schéma ni hôte)
 * X-Reborn-Server
 * X-Reborn-Timestamp (secondes Unix)
 * X-Reborn-Nonce
 * corps brut, octet pour octet (vide pour un GET : la chaîne finit alors par « \n »)
 * </pre>
 * Signature : hex minuscule de HMAC-SHA256(secret en UTF-8, chaîne). Une nouvelle tentative est un nouvel envoi :
 * nouvel horodatage, nouveau nonce, nouvelle signature.
 */
public final class SignatureJeu {

    /** Première ligne de la chaîne : version du schéma. */
    public static final String SCHEMA = "REBORN-GAME-V1";
    /** Longueur minimale d'un secret du jeu (l'API ignore un secret plus court). */
    public static final int SECRET_LONGUEUR_MIN = 32;

    private static final Pattern SERVEUR = Pattern.compile("^[a-z0-9][a-z0-9-]{0,31}$");
    private static final Pattern NONCE = Pattern.compile("^[A-Za-z0-9_-]{16,64}$");

    private SignatureJeu() {}

    /** Identifiant de serveur acceptable pour {@code X-Reborn-Server} (même forme que {@code server-id} de Guardian). */
    public static boolean serveurValide(String id) {
        return id != null && SERVEUR.matcher(id).matches();
    }

    /** Nonce acceptable : 16 à 64 caractères [A-Za-z0-9_-]. */
    public static boolean nonceValide(String nonce) {
        return nonce != null && NONCE.matcher(nonce).matches();
    }

    /** Chaîne signée, en octets (voir l'en-tête de la classe). {@code corps} null = corps vide. */
    public static byte[] chaine(String methode, String chemin, String serveur, String horodatage, String nonce,
                                byte[] corps) {
        String entete = SCHEMA + "\n"
                + methode.toUpperCase(java.util.Locale.ROOT) + "\n"
                + chemin + "\n"
                + serveur + "\n"
                + horodatage + "\n"
                + nonce + "\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream(entete.length() + (corps == null ? 0 : corps.length));
        out.writeBytes(entete.getBytes(StandardCharsets.UTF_8));
        if (corps != null) out.writeBytes(corps);
        return out.toByteArray();
    }

    /** Hex minuscule de HMAC-SHA256(secret, chaîne). */
    public static String signer(String secret, byte[] chaine) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return hex(mac.doFinal(chaine));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 indisponible", e);
        }
    }

    /** Chemin signé d'une URI : chemin brut, plus « ?requête » brute s'il y en a une. */
    public static String cheminSigne(java.net.URI uri) {
        String chemin = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return uri.getRawQuery() == null ? chemin : chemin + "?" + uri.getRawQuery();
    }

    static String hex(byte[] octets) {
        StringBuilder sb = new StringBuilder(octets.length * 2);
        for (byte b : octets) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
