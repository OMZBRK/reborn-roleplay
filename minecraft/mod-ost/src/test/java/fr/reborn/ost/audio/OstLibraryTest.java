package fr.reborn.ost.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests du scan filesystem {@link OstLibrary}.
 */
class OstLibraryTest {

    private static void touch(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "fake-ogg-bytes");
    }

    @Test
    void ensureLayoutCreatesAllCategoryFoldersAndReadme(@TempDir Path tmp) {
        OstLibrary lib = new OstLibrary(tmp.resolve("root"));
        lib.ensureLayout();
        Path root = lib.rootDir();
        assertTrue(Files.isDirectory(root));
        assertTrue(Files.isDirectory(root.resolve("apaisant")));
        assertTrue(Files.isDirectory(root.resolve("combat")));
        assertTrue(Files.isDirectory(root.resolve("mission")));
        assertTrue(Files.isDirectory(root.resolve("motivation")));
        assertTrue(Files.isDirectory(root.resolve("mystere")));
        assertTrue(Files.isDirectory(root.resolve("triste")));
        assertFalse(Files.isDirectory(root.resolve("favoris")), "favoris est virtuel");
        assertTrue(Files.isRegularFile(root.resolve("README.txt")));
    }

    @Test
    void scanFindsOggFilesPerCategory(@TempDir Path tmp) throws IOException {
        Path root = tmp.resolve("root");
        touch(root.resolve("apaisant").resolve("aurore.ogg"));
        touch(root.resolve("apaisant").resolve("brume.ogg"));
        touch(root.resolve("combat").resolve("duel-1.ogg"));
        // Casse mixte : on tolère .OGG (filtre lowercase).
        touch(root.resolve("mission").resolve("infiltration.OGG"));
        // Fichier non-.ogg : ignoré.
        touch(root.resolve("combat").resolve("notes.txt"));

        OstLibrary lib = new OstLibrary(root);
        int n = lib.scan();
        assertEquals(4, n);

        List<OstTrack> apaisant = lib.tracks(OstCategory.APAISANT);
        assertEquals(2, apaisant.size());
        assertEquals("aurore", apaisant.get(0).fileName());
        assertEquals("brume", apaisant.get(1).fileName());
        assertEquals("apaisant/aurore", apaisant.get(0).trackId());

        assertEquals(1, lib.tracks(OstCategory.COMBAT).size());
        assertEquals(1, lib.tracks(OstCategory.MISSION).size());
        assertEquals(0, lib.tracks(OstCategory.MOTIVATION).size());
    }

    @Test
    void resolveByTrackIdReturnsCorrectTrack(@TempDir Path tmp) throws IOException {
        Path root = tmp.resolve("root");
        touch(root.resolve("triste").resolve("souvenir.ogg"));

        OstLibrary lib = new OstLibrary(root);
        lib.scan();
        assertTrue(lib.resolve("triste/souvenir").isPresent());
        assertTrue(lib.resolve("triste/inexistant").isEmpty());
        assertTrue(lib.resolve("absurde/zzz").isEmpty());
    }

    @Test
    void favoritesFilterAgainstExistingTracks(@TempDir Path tmp) throws IOException {
        Path root = tmp.resolve("root");
        touch(root.resolve("apaisant").resolve("a.ogg"));
        touch(root.resolve("combat").resolve("b.ogg"));

        OstLibrary lib = new OstLibrary(root);
        lib.scan();
        List<OstTrack> favs = lib.favorites(Set.of("apaisant/a", "combat/b", "ghost/c"));
        assertEquals(2, favs.size(), "ghost/c filtré car absent du scan");
    }

    @Test
    void searchIsCaseInsensitiveSubstring(@TempDir Path tmp) throws IOException {
        Path root = tmp.resolve("root");
        touch(root.resolve("combat").resolve("Duel-Final.ogg"));
        touch(root.resolve("apaisant").resolve("aurore.ogg"));

        OstLibrary lib = new OstLibrary(root);
        lib.scan();
        assertEquals(1, lib.search("duel").size());
        assertEquals(1, lib.search("AURORE").size());
        assertEquals(2, lib.search("").size());
    }

    @Test
    void scanOnMissingRootReturnsZero(@TempDir Path tmp) {
        OstLibrary lib = new OstLibrary(tmp.resolve("does-not-exist"));
        int n = lib.scan();
        assertEquals(0, n);
    }
}
