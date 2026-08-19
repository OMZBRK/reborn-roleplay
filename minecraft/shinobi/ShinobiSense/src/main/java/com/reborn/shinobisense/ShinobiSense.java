package com.reborn.shinobisense;

import com.reborn.shinobicore.api.CharacterService;
import com.reborn.shinobicore.api.SkillService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ShinobiSense — perception &amp; information warfare: chakra sensing,
 * signatures, suppression/masking, Henge, genjutsu, Kai and dōjutsu, built
 * incrementally per {@code ShinobiSense_InfoWar_DESIGN.md}.
 *
 * <p>This first increment ships <b>chakra sensing</b>: an active, Perception-
 * scaled, deliberately fuzzy pulse, with suppression and "you feel a gaze"
 * detection — over the ShinobiCore character / skill / chakra API.
 */
public final class ShinobiSense extends JavaPlugin {

    private static ShinobiSense instance;

    private CharacterService characters;
    private SkillService skills;
    private SenseService sense;
    private GenjutsuManager genjutsu;
    private DojutsuManager dojutsu;

    public static ShinobiSense get() { return instance; }
    /** Engine character service, resolved from the ShinobiCore api seam. */
    public CharacterService characters() { return characters; }
    /** Engine skill/roll service, resolved from the ShinobiCore api seam. */
    public SkillService skills() { return skills; }
    public SenseService sense() { return sense; }
    public GenjutsuManager genjutsu() { return genjutsu; }
    public DojutsuManager dojutsu() { return dojutsu; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Resolved through the ShinobiCore api seam (ServicesManager),
        // not the concrete plugin class.
        Plugin maybeCore = Bukkit.getPluginManager().getPlugin("ShinobiCore");
        CharacterService characterService = Bukkit.getServicesManager()
                .load(CharacterService.class);
        SkillService skillService = Bukkit.getServicesManager()
                .load(SkillService.class);
        if (maybeCore == null || !maybeCore.isEnabled()
                || characterService == null || skillService == null) {
            getLogger().severe("ShinobiCore introuvable — ShinobiSense se désactive.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        this.characters = characterService;
        this.skills = skillService;
        this.sense = new SenseService(this);
        this.genjutsu = new GenjutsuManager(this);
        this.genjutsu.start();
        this.dojutsu = new DojutsuManager(this);
        this.dojutsu.start();

        PluginCommand senseCmd = getCommand("sense");
        if (senseCmd != null) {
            SenseCommand exec = new SenseCommand(this);
            senseCmd.setExecutor(exec);
            senseCmd.setTabCompleter(exec);
        } else {
            getLogger().warning("Command 'sense' is not declared in plugin.yml.");
        }

        getLogger().info("ShinobiSense activé — perception en ligne.");
    }

    @Override
    public void onDisable() {
        if (genjutsu != null) genjutsu.stop();
        if (dojutsu != null) dojutsu.stop();
        getLogger().info("ShinobiSense désactivé.");
        instance = null;
    }
}
