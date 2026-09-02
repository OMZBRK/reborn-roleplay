package com.reborn.shinobitail;

import com.reborn.shinobicore.api.CharacterService;
import com.reborn.shinobicore.api.KoService;
import com.reborn.shinobicore.api.ResourceService;
import com.reborn.shinobitail.beast.BeastRegistry;
import com.reborn.shinobitail.command.TailCommand;
import com.reborn.shinobitail.data.JinchurikiStore;
import com.reborn.shinobitail.gui.AdminGui;
import com.reborn.shinobitail.inner.ConfrontGui;
import com.reborn.shinobitail.inner.FakeBody;
import com.reborn.shinobitail.inner.InnerWorldManager;
import com.reborn.shinobitail.transform.TransformationManager;
import com.reborn.shinobitail.trigger.TriggerListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ShinobiTail — the Tailed Beast (Bijū / Jinchūriki) system.
 *
 * <p>Design pillars, mirroring the staff brief:
 * <ul>
 *   <li>Beasts and their personalities live in {@code beasts.yml}; stage
 *       count equals tail count, stage strength follows a tunable power
 *       curve (a 1-tail's single stage ≈ a 9-tails' stage 5).</li>
 *   <li>The GM owns the psychology: trust / anger / cooperation /
 *       influence are edited with {@code /tail set} as scenes play out,
 *       and every automatic roll reads those dials.</li>
 *   <li>Rage grows with time and combat; corruption tiers blur the
 *       host's feelings into the beast's; windows of control pull the
 *       host into the Inner World to yield or resist.</li>
 * </ul>
 *
 * <p>Boot order: core link → beast registry → jinchūriki store →
 * transformation engine → inner world → GUI + listeners → command.
 */
public final class ShinobiTail extends JavaPlugin {

    private static ShinobiTail instance;

    private CharacterService characters;
    private ResourceService resources;
    private KoService ko;
    private BeastRegistry beasts;
    private JinchurikiStore jinchuriki;
    private TransformationManager transformations;
    private InnerWorldManager innerWorld;
    private ConfrontGui confrontGui;
    private AdminGui adminGui;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // 0. Cross-plugin link — hard requirement at runtime. Resolved
        //    through the ShinobiCore api seam (ServicesManager), not the
        //    concrete plugin class.
        Plugin maybeCore = Bukkit.getPluginManager().getPlugin("ShinobiCore");
        var servicesManager = Bukkit.getServicesManager();
        CharacterService characterService = servicesManager.load(CharacterService.class);
        ResourceService resourceService = servicesManager.load(ResourceService.class);
        KoService koService = servicesManager.load(KoService.class);
        if (maybeCore == null || !maybeCore.isEnabled() || characterService == null
                || resourceService == null || koService == null) {
            getLogger().severe("ShinobiCore introuvable — ShinobiTail se désactive.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        this.characters = characterService;
        this.resources = resourceService;
        this.ko = koService;

        // 1. Beast catalogue + inner-world locations.
        this.beasts = new BeastRegistry(this);
        this.beasts.load();

        // 2. Per-character jinchūriki data (YAML, autosaved).
        this.jinchuriki = new JinchurikiStore(this);
        this.jinchuriki.start();

        // 3. Transformation engine (rage / corruption / windows / aura).
        this.transformations = new TransformationManager(this);
        this.transformations.start();

        // 4. Inner World confrontation + its GUI.
        this.innerWorld = new InnerWorldManager(this);
        this.confrontGui = new ConfrontGui(this);
        Bukkit.getPluginManager().registerEvents(confrontGui, this);

        // 4b. GM administration GUI (list + per-jinchūriki editor).
        this.adminGui = new AdminGui(this);
        Bukkit.getPluginManager().registerEvents(adminGui, this);

        // 5. Trigger / hygiene listener + chakra-exhaustion poller.
        TriggerListener triggers = new TriggerListener(this);
        Bukkit.getPluginManager().registerEvents(triggers, this);
        Bukkit.getScheduler().runTaskTimer(this, triggers::tickChakraTrigger, 40L, 40L);

        // 6. Command.
        PluginCommand tail = getCommand("tail");
        if (tail != null) {
            TailCommand exec = new TailCommand(this);
            tail.setExecutor(exec);
            tail.setTabCompleter(exec);
        } else {
            getLogger().warning("Command 'tail' is not declared in plugin.yml.");
        }

        // 7. Sweep fake bodies orphaned by a crash (loaded chunks only —
        //    ChunkLoadEvent in the listener covers the rest lazily).
        int swept = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (FakeBody.isFakeBody(this, e)) { e.remove(); swept++; }
            }
        }
        if (swept > 0) getLogger().info("Removed " + swept + " orphaned fake bodies.");

        getLogger().info("ShinobiTail enabled — " + beasts.all().size()
                + " bijū awake and listening.");
    }

    @Override
    public void onDisable() {
        if (innerWorld != null) innerWorld.abortAll();
        if (transformations != null) transformations.stop();
        if (jinchuriki != null) jinchuriki.stop();
        getLogger().info("ShinobiTail disabled.");
    }

    /** Reload config.yml + beasts.yml. Used by /tail reload. */
    public void reloadAll(CommandSender to) {
        reloadConfig();
        if (beasts != null) beasts.load();
        if (to != null) {
            to.sendMessage(Component.text("Configuration ShinobiTail rechargée.",
                    NamedTextColor.GREEN));
        }
    }

    /* ---------------------------------------------------------- accessors */

    public static ShinobiTail get() { return instance; }
    /** Engine character service, resolved from the ShinobiCore api seam. */
    public CharacterService characters() { return characters; }
    /** Engine resource service (chakra), from the api seam. */
    public ResourceService resources() { return resources; }
    /** Engine KO service, from the api seam. */
    public KoService ko() { return ko; }
    public BeastRegistry beasts() { return beasts; }
    public JinchurikiStore jinchuriki() { return jinchuriki; }
    public TransformationManager transformations() { return transformations; }
    public InnerWorldManager innerWorld() { return innerWorld; }
    public ConfrontGui confrontGui() { return confrontGui; }
    public AdminGui adminGui() { return adminGui; }
}
