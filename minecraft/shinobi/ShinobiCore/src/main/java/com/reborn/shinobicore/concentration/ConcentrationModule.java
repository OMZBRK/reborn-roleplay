package com.reborn.shinobicore.concentration;

import com.google.gson.JsonObject;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.concentration.plaque.PlaqueCommand;
import com.reborn.shinobicore.concentration.plaque.PlaqueService;
import com.reborn.shinobicore.concentration.plaque.PlaquesPanel;
import com.reborn.shinobicore.concentration.trace.TraceService;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

/**
 * Lot 4 « Concentration », étape B (Game Plan n° 17) : plaques lues en concentration. Rassemble le service
 * ({@link PlaqueService}, {@code plaques.yml}), le canal joueur {@link ConcentrationCanal} ({@code reborn:concentration}),
 * l'outil du Poste de garde ({@link PlaquesPanel}, actions {@code plaque*} de {@code reborn:staff}) et {@code /plaque} ;
 * étape C : traces de pas ({@link TraceService}, mémoire seule).
 */
public final class ConcentrationModule {

    private final PlaqueService plaques;
    private final ConcentrationCanal canal;
    private final PlaquesPanel panel;
    private final TraceService traces;
    private final ShinobiCore plugin;

    public ConcentrationModule(ShinobiCore plugin) {
        this.plugin = plugin;
        this.plaques = new PlaqueService(plugin);
        this.canal = new ConcentrationCanal(plugin, plaques);
        this.panel = new PlaquesPanel(plugin, plaques, canal::relire);
        this.traces = new TraceService(plugin, canal);
        this.canal.traces(traces);
    }

    public void demarrer() {
        plaques.demarrer();
        canal.demarrer();
        traces.demarrer();
        org.bukkit.Bukkit.getPluginManager().registerEvents(panel, plugin);
        PluginCommand pc = plugin.getCommand("plaque");
        if (pc == null) plugin.getLogger().warning("Commande 'plaque' absente du plugin.yml.");
        else {
            PlaqueCommand c = new PlaqueCommand(plaques, canal::relire);
            pc.setExecutor(c);
            pc.setTabCompleter(c);
        }
    }

    public void arreter() {
        traces.arreter();
        canal.arreter();
        plaques.arreter();
    }

    public PlaqueService plaques() { return plaques; }

    public ConcentrationCanal canal() { return canal; }

    public TraceService traces() { return traces; }

    /** Actions {@code plaque*} du Poste de garde (appelé par {@code StaffPanel.handle}, grade déjà relu). */
    public void panel(Player p, int grade, String a, JsonObject in) { panel.handle(p, grade, a, in); }
}
