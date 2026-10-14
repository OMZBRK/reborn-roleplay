package com.reborn.shinobicore.journal;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService;
import com.reborn.shinobicore.api.JournalService.Categorie;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;

/**
 * Démarrage / arrêt du journal universel, appelé par {@link ShinobiCore} : le plus tôt possible à l'activation (pour
 * que tous les systèmes puissent journaliser) et le plus tard possible à l'arrêt (pour ne rien perdre).
 */
public final class JournalDemarrage {

    private JournalDemarrage() {}

    public static JournalServiceImpl demarrer(ShinobiCore plugin) {
        JournalServiceImpl j = new JournalServiceImpl(plugin);
        Journal.installer(j);
        RyoJournal.installer();
        j.demarrer();
        Bukkit.getPluginManager().registerEvents(new JournalEcouteur(plugin), plugin);
        plugin.getServer().getServicesManager().register(JournalService.class, j, plugin, ServicePriority.Normal);
        j.entree(Categorie.SYSTEME, "DEMARRAGE")
                .detail("ShinobiCore " + plugin.getPluginMeta().getVersion() + " · " + Bukkit.getVersion()).ecrire();
        return j;
    }

    public static void arreter(JournalServiceImpl j) {
        if (j == null) return;
        j.entree(Categorie.SYSTEME, "ARRET").detail(Bukkit.getOnlinePlayers().size() + " joueur(s) en ligne").ecrire();
        j.arreter();
        Journal.installer(null);
    }
}
