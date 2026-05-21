package fr.reborn.ost.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Extrait les 43 pistes OST seedées dans le jar
 * ({@code assets/reborn-ost/seed-bundle/track-NN.ogg}) vers le dossier
 * filesystem de l'utilisateur, distribuées sur les 6 catégories
 * physiques.
 *
 * <p>Politique : extraction <strong>uniquement si la library est vide</strong>
 * (zéro .ogg dans n'importe quelle catégorie). Sinon on respecte le
 * setup de l'utilisateur — s'il a réorganisé / supprimé des pistes,
 * on n'écrase rien.
 *
 * <p>Distribution : Apaisant=01-07+43, Combat=08-14, Mission=15-21,
 * Motivation=22-28, Mystère=29-35, Triste=36-42 (7 pistes par catégorie,
 * 8 pour Apaisant). Choix arbitraire — l'utilisateur peut réorganiser
 * en déplaçant les fichiers à la main.
 */
public final class OstSeedExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-ost/seed");
    private static final String BUNDLE_RESOURCE_PREFIX = "/assets/reborn-ost/seed-bundle/";

    /** Map<index1..43, category cible>. Construit une fois, jamais muté. */
    private static final Map<Integer, OstCategory> DISTRIBUTION = buildDistribution();

    private OstSeedExtractor() {}

    private static Map<Integer, OstCategory> buildDistribution() {
        Map<Integer, OstCategory> out = new LinkedHashMap<>();
        OstCategory[] order = {
            OstCategory.APAISANT,
            OstCategory.COMBAT,
            OstCategory.MISSION,
            OstCategory.MOTIVATION,
            OstCategory.MYSTERE,
            OstCategory.TRISTE
        };
        int track = 1;
        // 7 pistes par catégorie pour les 6 premières.
        for (OstCategory cat : order) {
            for (int k = 0; k < 7; k++) {
                out.put(track++, cat);
            }
        }
        // La 43e va en Apaisant (catégorie débordoir).
        out.put(43, OstCategory.APAISANT);
        return out;
    }

    /**
     * Extrait le seed bundle si {@code library} ne contient aucune
     * piste. Idempotent : ne touche jamais aux fichiers existants.
     *
     * @return nombre de fichiers extraits (0 si library non-vide ou si
     *         le bundle est absent du jar)
     */
    public static int extractIfEmpty(OstLibrary library) {
        if (!library.allTracks().isEmpty()) {
            LOGGER.info("library deja peuplee ({} pistes), skip extraction du seed.",
                library.allTracks().size());
            return 0;
        }
        Path root = library.rootDir();
        int extracted = 0;
        for (Map.Entry<Integer, OstCategory> entry : DISTRIBUTION.entrySet()) {
            int idx = entry.getKey();
            OstCategory cat = entry.getValue();
            String resourceName = String.format("track-%02d.ogg", idx);
            String resourcePath = BUNDLE_RESOURCE_PREFIX + resourceName;

            Path target = root.resolve(cat.folderName()).resolve(resourceName);
            try {
                Files.createDirectories(target.getParent());
            } catch (IOException e) {
                LOGGER.warn("mkdir {} echec : {}", target.getParent(), e.getMessage());
                continue;
            }
            if (Files.exists(target)) continue;

            try (InputStream in = OstSeedExtractor.class.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    // Bundle absent du jar (par ex. build local sans
                    // l'asset) — log warning et passe.
                    LOGGER.warn("ressource manquante : {}", resourcePath);
                    continue;
                }
                Files.copy(in, target);
                extracted++;
            } catch (IOException e) {
                LOGGER.warn("extraction {} echec : {}", target, e.getMessage());
            }
        }
        if (extracted > 0) {
            LOGGER.info("seed extracted: {} pistes deposees dans {}", extracted, root);
        }
        return extracted;
    }
}
