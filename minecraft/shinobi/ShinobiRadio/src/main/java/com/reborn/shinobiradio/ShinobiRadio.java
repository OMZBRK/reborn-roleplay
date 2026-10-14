package com.reborn.shinobiradio;

import com.reborn.shinobicore.api.CharacterService;
import com.reborn.shinobicore.api.InventoryService;
import com.reborn.shinobicore.api.JournalService;
import com.reborn.shinobicore.api.KoService;
import com.reborn.shinobicore.api.event.CharacterSwitchEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import su.plo.voice.api.server.PlasmoVoiceServer;

/**
 * ShinobiRadio — la radio en escouades du serveur RP Reborn (docs/gameplay/RADIO.md v3, décisions du 14/10).
 *
 * <p>Une escouade = un petit groupe persistant (8 au plus) qui s'entend partout dans le monde, à condition de porter
 * son <b>oreillette</b> (objet Nexo, emplacement cosmétique Boucle d'oreille). Création et ajout face à face par le menu
 * R du mod (commandes {@code /radio creer|ajouter}, acceptation 10 s), écran N, élément HUD « Escouade ». Voix : addon
 * serveur Plasmo Voice 2.1.x ({@link RadioAddon}, {@link VoixRadio}) — activation « radio » non transitive et sans
 * proximité, ligne de sources « radio » à volume séparé, chuchoté à 4 blocs. Staff : écoute journalisée (motif,
 * durée), coupure, retrait. Journal universel de ShinobiCore (catégorie RADIO) : des faits, jamais la voix.
 *
 * <p>Dépend de ShinobiCore (services {@code CharacterService}, {@code KoService}, {@code InventoryService},
 * {@code JournalService}) et de PlasmoVoice (plugin serveur {@code spigot-2.1.17}).
 */
public final class ShinobiRadio extends JavaPlugin implements Listener {

    private RadioAddon addon;
    private RadioConfig cfg;
    private volatile Radio radio;
    private Escouades escouades;
    private Canal canal;
    private BukkitTask tache;
    private volatile VoixRadio voix;

    @Override
    public void onLoad() {
        // Plasmo Voice charge ses addons au démarrage : l'addon doit être déclaré AVANT l'activation des plugins.
        addon = new RadioAddon(this);
        try {
            PlasmoVoiceServer.getAddonsLoader().load(addon);
        } catch (Throwable t) {
            getLogger().severe("[radio] Chargement de l'addon Plasmo Voice impossible : " + t);
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cfg = new RadioConfig(getConfig());
        ServicesManager s = getServer().getServicesManager();
        CharacterService chars = s.load(CharacterService.class);
        InventoryService inv = s.load(InventoryService.class);
        JournalService journal = s.load(JournalService.class);
        KoService ko = s.load(KoService.class);
        if (chars == null || inv == null || journal == null) {
            getLogger().severe("ShinobiCore introuvable ou trop ancien (personnages, sacoche, journal) : ShinobiRadio s'arrête.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        escouades = new Escouades(getDataFolder(), getLogger());
        escouades.charger();
        canal = new Canal(this);
        canal.ouvrir();
        radio = new Radio(this, cfg, escouades, new Oreillettes(this, cfg), canal, chars, ko, inv, journal);
        radio.demarrage();

        PluginCommand cmd = getCommand("radio");
        if (cmd != null) {
            RadioCommande rc = new RadioCommande(radio, cfg);
            cmd.setExecutor(rc);
            cmd.setTabCompleter(rc);
        }
        getServer().getPluginManager().registerEvents(this, this);
        tache = Bukkit.getScheduler().runTaskTimer(this, radio::tick, 20L, 5L);
        getLogger().info("ShinobiRadio activé — " + escouades.toutes().size() + " escouade(s)"
                + (voix != null ? ", voix radio prête." : ", voix radio en attente de Plasmo Voice."));
    }

    @Override
    public void onDisable() {
        if (tache != null) tache.cancel();
        if (escouades != null) escouades.sauver();
        if (canal != null) canal.fermer();
    }

    /** Appelé par l'addon quand Plasmo Voice a enregistré l'activation. */
    void voixPrete(VoixRadio v) {
        this.voix = v;
    }

    Radio radio() { return radio; }

    RadioConfig cfg() {
        if (cfg == null) cfg = new RadioConfig(getConfig());
        return cfg;
    }

    /* ------------------------------------------------------------------ connexion */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (radio != null) radio.change(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        if (radio != null) radio.quitte(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwitch(CharacterSwitchEvent e) {
        if (radio != null) radio.change(e.player());
    }
}
