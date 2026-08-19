package com.reborn.shinobilearning;

import com.reborn.shinobicore.api.CharacterService;
import com.reborn.shinobilearning.academy.AcademyManager;
import com.reborn.shinobilearning.academy.AcademyStore;
import com.reborn.shinobilearning.command.AcademyCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ShinobiLearning — the path of growth: the Academy, sensei/squads, the
 * training &amp; mastery loop, parchemins, the Codex and the exams. Built
 * incrementally per {@code ShinobiLearning_DESIGN.md}.
 *
 * <p>This first increment ships the <b>Academy</b>: enrolment, the lesson
 * curriculum, and graduation to Genin (granting skill points + the Academy
 * Three) — all over the ShinobiCore character API.
 */
public final class ShinobiLearning extends JavaPlugin {

    private static ShinobiLearning instance;

    private CharacterService characters;
    private AcademyStore academy;
    private AcademyManager academyManager;
    private com.reborn.shinobilearning.squad.SquadManager squads;

    public static ShinobiLearning get() { return instance; }
    /** Engine character service, resolved from the ShinobiCore api seam. */
    public CharacterService characters() { return characters; }
    public AcademyStore academy() { return academy; }
    public AcademyManager academyManager() { return academyManager; }
    public com.reborn.shinobilearning.squad.SquadManager squads() { return squads; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Cross-plugin link — hard requirement at runtime. Resolved
        // through the ShinobiCore api seam (ServicesManager), not the
        // concrete plugin class.
        Plugin maybeCore = Bukkit.getPluginManager().getPlugin("ShinobiCore");
        CharacterService characterService = Bukkit.getServicesManager()
                .load(CharacterService.class);
        if (maybeCore == null || !maybeCore.isEnabled() || characterService == null) {
            getLogger().severe("ShinobiCore introuvable — ShinobiLearning se désactive.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        this.characters = characterService;

        // Academy progress store (YAML per character) + orchestration.
        this.academy = new AcademyStore(this);
        this.academy.start();
        this.academyManager = new AcademyManager(this);
        this.squads = new com.reborn.shinobilearning.squad.SquadManager(this);

        PluginCommand academyCmd = getCommand("academy");
        if (academyCmd != null) {
            AcademyCommand exec = new AcademyCommand(this);
            academyCmd.setExecutor(exec);
            academyCmd.setTabCompleter(exec);
        } else {
            getLogger().warning("Command 'academy' is not declared in plugin.yml.");
        }

        PluginCommand squadCmd = getCommand("squad");
        if (squadCmd != null) {
            com.reborn.shinobilearning.command.SquadCommand exec =
                    new com.reborn.shinobilearning.command.SquadCommand(this);
            squadCmd.setExecutor(exec);
            squadCmd.setTabCompleter(exec);
        } else {
            getLogger().warning("Command 'squad' is not declared in plugin.yml.");
        }

        getLogger().info("ShinobiLearning activé — Académie en ligne.");
    }

    @Override
    public void onDisable() {
        if (academy != null) academy.stop();
        getLogger().info("ShinobiLearning désactivé.");
        instance = null;
    }
}
