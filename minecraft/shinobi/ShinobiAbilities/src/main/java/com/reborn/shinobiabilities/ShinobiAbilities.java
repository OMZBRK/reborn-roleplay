package com.reborn.shinobiabilities;

import com.reborn.shinobicore.technique.AbilityRegistry;
import com.reborn.shinobiabilities.command.MenuCommand;
import com.reborn.shinobiabilities.command.SaCommand;
import com.reborn.shinobiabilities.command.TechniquesCommand;
import com.reborn.shinobiabilities.command.TrailCommand;
import com.reborn.shinobiabilities.gui.AdminGui;
import com.reborn.shinobiabilities.gui.CatalogueGui;
import com.reborn.shinobiabilities.gui.ChannelPickerGui;
import com.reborn.shinobiabilities.gui.CharacterPickerGui;
import com.reborn.shinobiabilities.gui.GuiRouter;
import com.reborn.shinobiabilities.gui.HubGui;
import com.reborn.shinobiabilities.gui.KnownAbilitiesGui;
import com.reborn.shinobiabilities.gui.StaffValidationGui;
import com.reborn.shinobiabilities.hud.JutsuHudSection;
import com.reborn.shinobiabilities.hud.MobilityHudSection;
import com.reborn.shinobiabilities.jutsu.JutsuBindingStore;
import com.reborn.shinobiabilities.jutsu.JutsuEditGui;
import com.reborn.shinobiabilities.jutsu.JutsuEffectRegistry;
import com.reborn.shinobiabilities.jutsu.JutsuExecutionManager;
import com.reborn.shinobiabilities.jutsu.JutsuHotbarManager;
import com.reborn.shinobicore.technique.JutsuItemType;
import com.reborn.shinobiabilities.jutsu.JutsuItems;
import com.reborn.shinobiabilities.jutsu.JutsuListener;
import com.reborn.shinobiabilities.jutsu.IncantationManager;
import com.reborn.shinobiabilities.mobility.MobilityListener;
import com.reborn.shinobiabilities.mobility.MobilityModule;
import com.reborn.shinobiabilities.techniques.EncyclopediaBook;
import com.reborn.shinobiabilities.techniques.LearningMinigame;
import com.reborn.shinobiabilities.techniques.LearningShelfManager;
import com.reborn.shinobiabilities.techniques.ParcheminItems;
import com.reborn.shinobiabilities.techniques.TechniquesService;
import com.reborn.shinobiabilities.util.CooldownTracker;
import com.reborn.shinobiabilities.util.Keys;
import com.reborn.shinobicore.mobility.hud.CooldownHud;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * ShinobiAbilities — jutsu, mobility and techniques for the Reborn
 * Naruto roleplay server.
 *
 * <p>Soft-depends on ShinobiCore (so core can boot alone); without it
 * this plugin disables itself — every system reads character data,
 * chakra and the cooldown HUD from core.
 *
 * <p>Boot order mirrors the recreation prompt: ability registry →
 * jutsu (items, bindings, picker, incantation, execution, effects) →
 * mobility → techniques/learning → HUD sections → commands → itemgive
 * tokens.
 */
public final class ShinobiAbilities extends JavaPlugin {

    private static ShinobiAbilities instance;

    private CoreServices core;
    private AbilityRegistry abilities;
    private JutsuEffectRegistry effects;
    private JutsuBindingStore bindings;
    private CooldownTracker cooldowns;
    private IncantationManager incantation;
    private JutsuHotbarManager hotbar;
    private JutsuExecutionManager execution;
    private JutsuEditGui editGui;
    private MobilityModule mobility;
    private com.reborn.shinobiabilities.mobility.MobilityPaths mobilityPaths;
    private com.reborn.shinobiabilities.mobility.MobilityPathTwoModule pathTwo;
    private LearningShelfManager shelves;
    private LearningMinigame minigame;
    private TechniquesService techniques;
    private KnownAbilitiesGui knownGui;
    private GuiRouter guiRouter;

    private final List<CooldownHud.HudSection> hudSections = new ArrayList<>();

    @Override
    public void onEnable() {
        instance = this;
        Keys.init(this);

        // 0. Cross-plugin link — hard requirement at runtime. Resolved
        //    through the ShinobiCore api seam (ServicesManager), not the
        //    concrete plugin class; CoreServices bundles the interfaces.
        Plugin maybeCore = Bukkit.getPluginManager().getPlugin("ShinobiCore");
        var servicesManager = Bukkit.getServicesManager();
        var characterService = servicesManager.load(com.reborn.shinobicore.api.CharacterService.class);
        if (maybeCore == null || !maybeCore.isEnabled() || characterService == null) {
            getLogger().severe("ShinobiCore est introuvable ou désactivé — "
                    + "ShinobiAbilities ne peut pas démarrer sans les personnages.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        this.core = new CoreServices(
                characterService,
                servicesManager.load(com.reborn.shinobicore.api.ResourceService.class),
                servicesManager.load(com.reborn.shinobicore.api.KoService.class),
                servicesManager.load(com.reborn.shinobicore.api.MobilityService.class),
                servicesManager.load(com.reborn.shinobicore.api.HudService.class),
                servicesManager.load(com.reborn.shinobicore.api.ItemGiveService.class));
        this.guiRouter = new GuiRouter(core);

        // 1. Config + technique catalog. The registry CODE is engine-owned
        //    (resolved from ShinobiCore's seam); the DATA stays here — this
        //    plugin is the Naruto content pack, it ships abilities.yml and
        //    loads it into the engine registry before any of its own
        //    readers initialise.
        saveDefaultConfig();
        File abilitiesFile = new File(getDataFolder(), "abilities.yml");
        if (!abilitiesFile.exists()) saveResource("abilities.yml", false);
        this.abilities = servicesManager.load(AbilityRegistry.class);
        if (abilities == null) {
            getLogger().severe("Registre de techniques introuvable — "
                    + "ShinobiAbilities ne peut pas démarrer.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        this.abilities.load(abilitiesFile);

        // 2. Jutsu pipeline.
        this.effects = new JutsuEffectRegistry();
        this.bindings = new JutsuBindingStore(this);
        this.bindings.load();
        this.cooldowns = new CooldownTracker();
        this.incantation = new IncantationManager(this);
        this.hotbar = new JutsuHotbarManager(this, abilities, bindings, incantation);
        this.execution = new JutsuExecutionManager(this, core, abilities, bindings,
                hotbar, incantation, cooldowns, effects);
        this.editGui = new JutsuEditGui(this, abilities, bindings, guiRouter);

        // 3. Mobility.
        this.mobility = new MobilityModule(this, core, cooldowns);
        this.mobility.enable();
        this.mobilityPaths = new com.reborn.shinobiabilities.mobility.MobilityPaths(this, core);
        // Naruto Run now lives on Path Two (Voie du Flux) — share the one instance
        // and make it path-aware (Flux owns speed; NarutoRun is the run-state).
        this.mobility.narutoRun().setPaths(mobilityPaths);
        this.pathTwo = new com.reborn.shinobiabilities.mobility.MobilityPathTwoModule(
                this, core, mobilityPaths, mobility.narutoRun());

        // Canal C2S reborn:run : la touche naruto-run du mod client bascule la
        // course chakraïque (garde-fous dans NarutoRun.toggle). Cf RunChannelListener.
        getServer().getMessenger().registerIncomingPluginChannel(this,
                com.reborn.shinobiabilities.mobility.RunChannelListener.CHANNEL,
                new com.reborn.shinobiabilities.mobility.RunChannelListener(mobility.narutoRun()));

        // Canal reborn:anim : relais de synchro des démarches/course/naruto vers
        // les joueurs proches (tout le monde voit les anims des autres).
        getServer().getMessenger().registerOutgoingPluginChannel(this,
                com.reborn.shinobiabilities.mobility.AnimRelayListener.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this,
                com.reborn.shinobiabilities.mobility.AnimRelayListener.CHANNEL,
                new com.reborn.shinobiabilities.mobility.AnimRelayListener(this));

        // 4. Techniques / learning.
        this.shelves = new LearningShelfManager(this, abilities, guiRouter);
        this.shelves.load();
        this.minigame = new LearningMinigame(this);
        this.techniques = new TechniquesService(this, core, abilities, shelves, minigame);
        this.knownGui = new KnownAbilitiesGui(abilities, guiRouter);

        // 4b. GUI layer — every screen styled with ShinobiCore's
        //     GuiBuilder/GuiIcons language, navigated through the router.
        HubGui hub = new HubGui(guiRouter, abilities);
        ChannelPickerGui channelPicker = new ChannelPickerGui(guiRouter);
        CatalogueGui catalogue = new CatalogueGui(guiRouter, abilities);
        StaffValidationGui staffValidation = new StaffValidationGui(guiRouter, minigame);
        AdminGui adminGui = new AdminGui(guiRouter, abilities, cooldowns, mobility);
        CharacterPickerGui characterPicker = new CharacterPickerGui(guiRouter, abilities);
        guiRouter.bind(hub, channelPicker, catalogue, staffValidation,
                adminGui, knownGui, editGui, characterPicker);

        // 5. Listeners. F-key priority contract: JutsuListener owns F at
        //    LOWEST; MobilityListener's trail handler runs at NORMAL.
        var pm = Bukkit.getPluginManager();
        // F is deferred to the mobility chain while ANY ride/swing is
        // active: trail, grapple swing, zipline ride — those F presses
        // are cancels, never picker-opens.
        pm.registerEvents(new JutsuListener(this, core, hotbar, execution, editGui,
                p -> mobility.trails().isRiding(p)
                        || mobility.training().isRunning(p)
                        || (core.mobility() != null
                            && ((core.mobility().grapple() != null
                                    && core.mobility().grapple().isGrappling(p.getUniqueId()))
                                || (core.mobility().zipline() != null
                                    && core.mobility().zipline().isTraveling(p.getUniqueId())))),
                p -> minigame.isActive(p) || mobility.training().isRunning(p)), this);
        pm.registerEvents(new MobilityListener(this, core, mobility, mobilityPaths,
                p -> hotbar.isOpen(p),
                p -> minigame.isActive(p)), this);
        pm.registerEvents(pathTwo, this);
        pathTwo.start();
        pm.registerEvents(new com.reborn.shinobiabilities.mobility.TrailEditorListener(
                mobility.trails()), this);
        pm.registerEvents(new com.reborn.shinobiabilities.mobility.TrailMapListener(
                this, mobility.trails()), this);
        pm.registerEvents(new com.reborn.shinobiabilities.mobility.training.ParkourListener(
                mobility.parkours(), mobility.training()), this);
        pm.registerEvents(shelves, this);
        pm.registerEvents(minigame, this);
        // ONE listener for every GUI screen, present and future — the
        // framework ScreenManager (clicks, drags, pagination, closes).
        pm.registerEvents(guiRouter.screens(), this);

        // 5b. Fuinjutsu — Sceau de Contention (lien de scellement qui
        //     draine la rage d'un jinchūriki transformé). Actif
        //     seulement quand le plugin ShinobiTail est présent.
        if (Bukkit.getPluginManager().getPlugin("ShinobiTail") != null) {
            pm.registerEvents(new com.reborn.shinobiabilities.fuinjutsu
                    .SealingLink(this, core, cooldowns), this);
        } else {
            getLogger().info("ShinobiTail absent — Sceau Fuinjutsu désactivé.");
        }

        // 6. Commands — GUI-first: /menu is the front door, /toggle and
        //    /techniques are direct shortcuts, /sa is the admin fallback.
        PluginCommand menu = getCommand("menu");
        if (menu != null) menu.setExecutor(new MenuCommand(guiRouter));
        else getLogger().warning("Commande 'menu' absente du plugin.yml.");
        PluginCommand sa = getCommand("shinobiabilities");
        if (sa != null) {
            SaCommand exec = new SaCommand(this, core, abilities, bindings,
                    cooldowns, mobility, minigame, this::reloadAll);
            sa.setExecutor(exec);
            sa.setTabCompleter(exec);
        } else getLogger().warning("Commande 'shinobiabilities' absente du plugin.yml.");
        PluginCommand tech = getCommand("techniques");
        if (tech != null) tech.setExecutor(new TechniquesCommand(guiRouter));
        else getLogger().warning("Commande 'techniques' absente du plugin.yml.");
        PluginCommand trail = getCommand("trail");
        if (trail != null) {
            TrailCommand exec = new TrailCommand(mobility.trails());
            trail.setExecutor(exec);
            trail.setTabCompleter(exec);
        } else getLogger().warning("Commande 'trail' absente du plugin.yml.");
        PluginCommand tg = getCommand("trainingground");
        if (tg != null) {
            com.reborn.shinobiabilities.command.ParkourCommand exec =
                    new com.reborn.shinobiabilities.command.ParkourCommand(
                            mobility.parkours(), mobility.training());
            tg.setExecutor(exec);
            tg.setTabCompleter(exec);
        } else getLogger().warning("Commande 'trainingground' absente du plugin.yml.");

        PluginCommand mob = getCommand("mobility");
        if (mob != null) {
            com.reborn.shinobiabilities.command.MobilityPathCommand exec =
                    new com.reborn.shinobiabilities.command.MobilityPathCommand(this, core, mobilityPaths);
            mob.setExecutor(exec);
            mob.setTabCompleter(exec);
        } else getLogger().warning("Commande 'mobility' absente du plugin.yml.");

        // 7. HUD sections into ShinobiCore's pluggable sidebar.
        if (core.cooldownHud() != null) {
            JutsuHudSection jutsuSection = new JutsuHudSection(abilities, cooldowns);
            MobilityHudSection mobilitySection = new MobilityHudSection(mobility, cooldowns);
            core.cooldownHud().registerSection(jutsuSection);
            core.cooldownHud().registerSection(mobilitySection);
            hudSections.add(jutsuSection);
            hudSections.add(mobilitySection);
        }

        // 8. /sc itemgive tokens — jutsu items, shelves, scrolls, book.
        if (core.itemGive() != null) {
            for (JutsuItemType type : JutsuItemType.values()) {
                final JutsuItemType t = type;
                core.itemGive().register("Jutsu_" + capitalise(type.name()),
                        type.displayName(), r -> JutsuItems.create(t));
            }
            core.itemGive().register("Block_LearningShelf",
                    "Étagère d'Apprentissage",
                    r -> LearningShelfManager.shelfItem(false));
            core.itemGive().register("Block_LearningShelfLarge",
                    "Grande Étagère d'Apprentissage",
                    r -> LearningShelfManager.shelfItem(true));
            core.itemGive().register("Parchemin_Random",
                    "Parchemin de Technique (aléatoire)",
                    r -> ParcheminItems.createRandom(abilities));
            core.itemGive().register("Book_RecueilTechniques",
                    "Recueil des Techniques",
                    r -> EncyclopediaBook.build(abilities));
            core.itemGive().register("Util_TrailMap", "Carte des Pistes",
                    r -> com.reborn.shinobiabilities.mobility.TrailMapItem.create());
        }

        getLogger().info("ShinobiAbilities activé — " + abilities.all().size()
                + " techniques, jutsu + mobilité + apprentissage en ligne.");
    }

    @Override
    public void onDisable() {
        // Restore every hijacked hotbar BEFORE anything captures
        // inventories, then cancel the transient states.
        if (hotbar != null) hotbar.closeAll();
        if (incantation != null) incantation.cancelAll();
        if (minigame != null) minigame.abortAll();
        if (mobility != null) mobility.disable();
        if (pathTwo != null) pathTwo.stop();
        if (bindings != null) bindings.saveSync();
        if (shelves != null) shelves.saveSync();

        // allowFlight hygiene for ground-game players.
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SURVIVAL
                    || p.getGameMode() == GameMode.ADVENTURE) {
                p.setAllowFlight(false);
            }
        }

        // Detach HUD sections (core may already be down on full stop).
        if (core != null && core.cooldownHud() != null) {
            for (CooldownHud.HudSection s : hudSections) {
                try { core.cooldownHud().unregisterSection(s); }
                catch (Throwable ignore) { }
            }
        }
        hudSections.clear();
        getLogger().info("ShinobiAbilities désactivé.");
    }

    /** {@code /sa reload} — config + abilities.yml. */
    public void reloadAll() {
        reloadConfig();
        if (abilities != null) {
            abilities.load(new File(getDataFolder(), "abilities.yml"));
        }
    }

    private static String capitalise(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0))
                + s.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    /* ----------------------------------------------------------- accessors */

    public static ShinobiAbilities get() { return instance; }
    /** Engine services bundle, resolved from the ShinobiCore api seam. */
    public CoreServices core()                 { return core; }
    public AbilityRegistry abilities()         { return abilities; }
    public JutsuEffectRegistry effects()       { return effects; }
    public JutsuBindingStore bindings()        { return bindings; }
    public CooldownTracker cooldowns()         { return cooldowns; }
    public IncantationManager incantation()    { return incantation; }
    public JutsuHotbarManager hotbar()         { return hotbar; }
    public JutsuExecutionManager execution()   { return execution; }
    public MobilityModule mobility()           { return mobility; }
    public com.reborn.shinobiabilities.mobility.MobilityPaths mobilityPaths() { return mobilityPaths; }
    public com.reborn.shinobiabilities.mobility.MobilityPathTwoModule pathTwo() { return pathTwo; }
    public LearningShelfManager shelves()      { return shelves; }
    public TechniquesService techniques()      { return techniques; }
}
