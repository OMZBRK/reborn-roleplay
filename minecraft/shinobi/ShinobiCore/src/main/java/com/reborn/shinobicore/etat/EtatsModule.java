package com.reborn.shinobicore.etat;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.EtatService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;

/**
 * Le module des <b>états du joueur</b> (docs/technique/ETATS_JOUEUR.md) : service ({@link EtatServiceImpl}, publié dans
 * le {@code ServicesManager} comme {@link EtatService}), suivi chaque tick ({@link EtatSuivi} : journal, paquet
 * {@code reborn:etat}), applicateur unique des effets physiques ({@link Applicateur}), porte des dégâts
 * ({@link PorteDegats}), actions longues ({@link Occupations}), commande {@code /etats}.
 */
public final class EtatsModule {

    private final ShinobiCore plugin;
    private final EtatServiceImpl service;
    private final Applicateur applicateur;
    private final EtatSuivi suivi;
    private final PorteDegats porte;

    public EtatsModule(ShinobiCore plugin) {
        this.plugin = plugin;
        this.service = new EtatServiceImpl(plugin);
        this.applicateur = new Applicateur(plugin, service);
        this.suivi = new EtatSuivi(plugin, service, applicateur);
        this.porte = new PorteDegats(plugin, service);
    }

    public void demarrer() {
        service.charger();
        var pm = Bukkit.getPluginManager();
        pm.registerEvents(suivi, plugin);
        pm.registerEvents(applicateur, plugin);
        pm.registerEvents(porte, plugin);
        pm.registerEvents(service.occupations(), plugin);
        suivi.demarrer();
        plugin.getServer().getServicesManager().register(EtatService.class, service, plugin, ServicePriority.Normal);
        PluginCommand c = plugin.getCommand("etats");
        if (c != null) {
            EtatsCommand ec = new EtatsCommand(plugin, service);
            c.setExecutor(ec);
            c.setTabCompleter(ec);
        } else {
            plugin.getLogger().warning("Command 'etats' is not declared in plugin.yml.");
        }
    }

    public void arreter() {
        suivi.arreter();
        applicateur.arreter();
        service.arreter();
    }

    public EtatServiceImpl service() { return service; }
}
