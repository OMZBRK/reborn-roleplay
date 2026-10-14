package com.reborn.shinobicore.monnaie;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.banque.BanqueCommand;
import com.reborn.shinobicore.banque.BanqueService;
import com.reborn.shinobicore.banque.GuichetCanal;
import com.reborn.shinobicore.banque.Guichets;
import com.reborn.shinobicore.banque.SoldeService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;

/**
 * Monnaie et banque (GAMEPLAN §19-20, docs/gameplay/MONNAIE_ET_BANQUE.md, décisions du 15/10) : la bourse
 * ({@link BourseService}), le vol sur un corps ({@link FouilleService}), le compte et le guichet
 * ({@link BanqueService}, {@link Guichets}, {@link GuichetCanal}), l'échange garanti, la solde du dimanche,
 * {@code /ryo}, {@code /banque}, {@code /testament}. Démarré par {@link ShinobiCore} hors mode build.
 */
public final class MonnaieModule {

    private final ShinobiCore plugin;
    private MonnaieCanal canal;
    private BourseService bourses;
    private FouilleService fouille;
    private BanqueService banque;
    private Guichets guichets;
    private GuichetCanal guichetCanal;
    private com.reborn.shinobicore.banque.EchangeGaranti echanges;
    private SoldeService soldes;
    private RyoCommand ryo;

    public MonnaieModule(ShinobiCore plugin) { this.plugin = plugin; }

    public void start() {
        MonnaieConfig.charger(plugin);
        canal = new MonnaieCanal(plugin, this);
        canal.start();
        banque = new BanqueService(plugin, this);
        banque.start();
        bourses = new BourseService(plugin, this);
        bourses.start();
        fouille = new FouilleService(plugin, this);
        fouille.start();
        guichets = new Guichets(plugin, this, banque.store());
        guichetCanal = new GuichetCanal(plugin, this);
        guichetCanal.start();
        echanges = new com.reborn.shinobicore.banque.EchangeGaranti(plugin, this);
        echanges.start();
        soldes = new SoldeService(plugin, this);
        soldes.start();
        var pm = Bukkit.getPluginManager();
        pm.registerEvents(bourses, plugin);
        pm.registerEvents(fouille, plugin);
        pm.registerEvents(guichets, plugin);
        pm.registerEvents(guichetCanal, plugin);
        pm.registerEvents(echanges, plugin);
        pm.registerEvents(banque, plugin);
        ryo = new RyoCommand(plugin, this);
        PluginCommand c = plugin.getCommand("ryo");
        if (c != null) { c.setExecutor(ryo); c.setTabCompleter(ryo); }
        BanqueCommand bc = new BanqueCommand(plugin, this);
        pm.registerEvents(bc, plugin);
        for (String n : new String[] {"banque", "testament"}) {
            PluginCommand pc = plugin.getCommand(n);
            if (pc != null) { pc.setExecutor(bc); pc.setTabCompleter(bc); }
        }
        plugin.getLogger().info("[monnaie] Bourse, banque et guichets prêts (canaux " + MonnaieCanal.MONNAIE + ", "
                + MonnaieCanal.BANQUE + ").");
    }

    public void stop() {
        if (echanges != null) echanges.stop();
        if (banque != null) banque.stop();
    }

    public void reload() { MonnaieConfig.charger(plugin); }

    public MonnaieCanal canal() { return canal; }
    public BourseService bourses() { return bourses; }
    public FouilleService fouille() { return fouille; }
    public BanqueService banque() { return banque; }
    public Guichets guichets() { return guichets; }
    public GuichetCanal guichetCanal() { return guichetCanal; }
    public com.reborn.shinobicore.banque.EchangeGaranti echanges() { return echanges; }
    public SoldeService soldes() { return soldes; }
}
