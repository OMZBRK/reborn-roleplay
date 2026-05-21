package fr.reborn.ost.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Scan filesystem des pistes OST. Recherche dans la racine donnée tous
 * les sous-dossiers correspondant aux {@link OstCategory#folderName()},
 * puis pour chaque dossier liste les fichiers {@code .ogg} (insensible
 * à la casse).
 *
 * <p>Convention : un fichier {@code apaisant/aurore.ogg} produit un
 * {@link OstTrack} de catégorie {@link OstCategory#APAISANT} avec
 * {@code trackId="apaisant/aurore"}.
 *
 * <p>La catégorie virtuelle {@link OstCategory#FAVORIS} n'est pas
 * scannée — elle est calculée en runtime depuis la liste de favoris
 * de la config utilisateur croisée avec les tracks scannées.
 *
 * <p>TODO (futur) : intégration avec le manifest signé du launcher
 * pour valider les hashes des .ogg avant de les exposer (cf
 * {@code PLAN_CONCEPTION_LAUNCHER.md §9}).
 */
public final class OstLibrary {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-ost/library");
    private static final String OGG_EXTENSION = ".ogg";
    private static final String README_NAME = "README.txt";

    private static final String README_CONTENT = """
        Reborn OST — dossier de pistes locales
        ======================================

        Place tes fichiers audio ici, organisés par catégorie :
            apaisant/<nom>.ogg
            combat/<nom>.ogg
            mission/<nom>.ogg
            motivation/<nom>.ogg
            mystere/<nom>.ogg
            triste/<nom>.ogg

        Format requis :
          - Conteneur : Ogg Vorbis (.ogg)
          - Canaux    : mono (sinon l'attenuation par distance est désactivée)
          - SampleRate: 44100 Hz recommandé

        Le launcher Reborn pourra plus tard alimenter ce dossier
        automatiquement via son manifest signé.
        """;

    private final Path rootDir;
    /** Index immutable : category → tracks. Rebuild via {@link #scan()}. */
    private volatile Map<OstCategory, List<OstTrack>> tracksByCategory = Collections.emptyMap();
    /** Lookup direct par trackId — utile pour résoudre un broadcast serveur. */
    private volatile Map<String, OstTrack> tracksById = Collections.emptyMap();

    public OstLibrary(Path rootDir) {
        this.rootDir = rootDir;
    }

    public Path rootDir() { return rootDir; }

    /**
     * Crée le dossier racine s'il n'existe pas + un sous-dossier par
     * catégorie physique + un README expliquant le format attendu.
     * Idempotent.
     */
    public void ensureLayout() {
        try {
            Files.createDirectories(rootDir);
            for (OstCategory cat : OstCategory.values()) {
                if (!cat.isPhysical()) continue;
                Files.createDirectories(rootDir.resolve(cat.folderName()));
            }
            Path readme = rootDir.resolve(README_NAME);
            if (!Files.exists(readme)) {
                Files.writeString(readme, README_CONTENT, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOGGER.warn("layout OST init echec ({}) : {}", rootDir, e.getMessage());
        }
    }

    /**
     * Re-scanne le filesystem. Thread-safe : remplace les maps internes
     * atomiquement à la fin.
     *
     * @return nombre total de tracks indexées
     */
    public synchronized int scan() {
        Map<OstCategory, List<OstTrack>> byCat = new EnumMap<>(OstCategory.class);
        Map<String, OstTrack> byId = new HashMap<>();
        int count = 0;

        for (OstCategory cat : OstCategory.values()) {
            if (!cat.isPhysical()) continue;
            Path dir = rootDir.resolve(cat.folderName());
            if (!Files.isDirectory(dir)) {
                byCat.put(cat, List.of());
                continue;
            }
            List<OstTrack> list = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path entry : stream) {
                    if (!Files.isRegularFile(entry)) continue;
                    String name = entry.getFileName().toString();
                    if (!name.toLowerCase(Locale.ROOT).endsWith(OGG_EXTENSION)) continue;
                    String baseName = name.substring(0, name.length() - OGG_EXTENSION.length());
                    if (baseName.isBlank()) continue;
                    String trackId = OstTrack.composeTrackId(cat, baseName);
                    OstTrack t = new OstTrack(cat, baseName, trackId, entry.toAbsolutePath());
                    list.add(t);
                    byId.put(trackId, t);
                    count++;
                }
            } catch (IOException e) {
                LOGGER.warn("scan {} echec : {}", dir, e.getMessage());
            }
            list.sort(Comparator.comparing(OstTrack::fileName, String.CASE_INSENSITIVE_ORDER));
            byCat.put(cat, Collections.unmodifiableList(list));
        }
        byCat.put(OstCategory.FAVORIS, List.of()); // resolved later via config

        this.tracksByCategory = Collections.unmodifiableMap(byCat);
        this.tracksById = Collections.unmodifiableMap(byId);
        LOGGER.info("OST library scan : {} tracks", count);
        return count;
    }

    public List<OstTrack> tracks(OstCategory category) {
        return tracksByCategory.getOrDefault(category, List.of());
    }

    public Optional<OstTrack> resolve(String trackId) {
        return Optional.ofNullable(tracksById.get(trackId));
    }

    public List<OstTrack> allTracks() {
        List<OstTrack> all = new ArrayList<>();
        for (OstCategory cat : OstCategory.values()) {
            if (!cat.isPhysical()) continue;
            all.addAll(tracks(cat));
        }
        return all;
    }

    /**
     * Liste les favoris d'après la config. Filtré aux tracks
     * effectivement présentes sur disque.
     */
    public List<OstTrack> favorites(Collection<String> favoriteIds) {
        List<OstTrack> out = new ArrayList<>();
        for (String id : favoriteIds) {
            OstTrack t = tracksById.get(id);
            if (t != null) out.add(t);
        }
        return out;
    }

    /**
     * Recherche par sous-chaîne sur le nom de fichier (insensible casse).
     */
    public List<OstTrack> search(String query) {
        if (query == null || query.isBlank()) return allTracks();
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<OstTrack> out = new ArrayList<>();
        for (OstTrack t : allTracks()) {
            if (t.fileName().toLowerCase(Locale.ROOT).contains(q)) out.add(t);
        }
        return out;
    }
}
