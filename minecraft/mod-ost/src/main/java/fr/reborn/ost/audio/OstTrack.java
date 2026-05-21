package fr.reborn.ost.audio;

import java.nio.file.Path;

/**
 * Une piste audio scannée depuis le filesystem.
 *
 * @param category   Catégorie déduite du sous-dossier parent.
 * @param fileName   Nom du fichier sans extension (ex: {@code "duel-1"}).
 * @param trackId    Identifiant logique {@code "<categorie>/<nom>"} —
 *                   compatible avec les packets serveur {@code reborn:ost}.
 * @param filePath   Chemin absolu vers le {@code .ogg} sur disque.
 */
public record OstTrack(OstCategory category, String fileName, String trackId, Path filePath) {

    /**
     * Format du trackId : {@code <folderName>/<fileNameSansExtension>}.
     */
    public static String composeTrackId(OstCategory category, String fileName) {
        return category.folderName() + "/" + fileName;
    }

    /**
     * Affichage humain : nom de fichier avec underscores → espaces et
     * première lettre capitalisée par mot.
     */
    public String displayName() {
        String pretty = fileName.replace('-', ' ').replace('_', ' ').trim();
        if (pretty.isEmpty()) return fileName;
        StringBuilder b = new StringBuilder(pretty.length());
        boolean upper = true;
        for (char c : pretty.toCharArray()) {
            if (Character.isWhitespace(c)) { upper = true; b.append(c); }
            else if (upper) { b.append(Character.toUpperCase(c)); upper = false; }
            else b.append(c);
        }
        return b.toString();
    }
}
