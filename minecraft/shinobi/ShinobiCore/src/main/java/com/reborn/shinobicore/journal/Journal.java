package com.reborn.shinobicore.journal;

import com.reborn.shinobicore.api.JournalService;
import com.reborn.shinobicore.api.JournalService.Categorie;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Accès statique au journal universel pour le code de ShinobiCore ({@code Journal.entree(...)...ecrire()}).
 * Les add-ons passent par le service {@link JournalService} du {@code ServicesManager}. Avant le démarrage du
 * journal (ou s'il est coupé), les appels ne font rien : journaliser n'est jamais bloquant.
 */
public final class Journal {

    private static volatile JournalServiceImpl instance;

    private Journal() {}

    static void installer(JournalServiceImpl j) { instance = j; }

    /** L'implémentation (Poste de garde, /journal), ou null. */
    public static JournalServiceImpl impl() { return instance; }

    public static JournalService.Entree entree(Categorie c, String action) {
        JournalServiceImpl j = instance;
        return j == null ? VIDE : j.entree(c, action);
    }

    public static void noter(Categorie c, String action, Player acteur, Player cible, String detail) {
        JournalServiceImpl j = instance;
        if (j != null) j.noter(c, action, acteur, cible, detail);
    }

    private static final JournalService.Entree VIDE = new JournalService.Entree() {
        @Override public JournalService.Entree acteur(Player p) { return this; }
        @Override public JournalService.Entree acteur(CommandSender s) { return this; }
        @Override public JournalService.Entree acteur(UUID compte) { return this; }
        @Override public JournalService.Entree cible(Player p) { return this; }
        @Override public JournalService.Entree cible(UUID compte) { return this; }
        @Override public JournalService.Entree ciblePersonnage(UUID compte, UUID personnage) { return this; }
        @Override public JournalService.Entree cibleTexte(String texte) { return this; }
        @Override public JournalService.Entree detail(String detail) { return this; }
        @Override public JournalService.Entree lieu(Location l) { return this; }
        @Override public void ecrire() { }
    };
}
