package com.reborn.shinobicore;

import com.reborn.shinobicore.chakra.ChakraDisplay;
import com.reborn.shinobicore.chakra.ChakraManager;
import com.reborn.shinobicore.character.CharacterAutoSave;
import com.reborn.shinobicore.character.CharacterManager;
import com.reborn.shinobicore.character.MeditationManager;
import com.reborn.shinobicore.character.NoCharacterLockdown;
import com.reborn.shinobicore.character.FriendshipManager;
import com.reborn.shinobicore.character.RencontrerManager;
import com.reborn.shinobicore.character.VisibilityManager;
import com.reborn.shinobicore.character.command.CharacterCommand;
import com.reborn.shinobicore.character.command.MeCommand;
import com.reborn.shinobicore.character.command.MeditationCommand;
import com.reborn.shinobicore.character.command.AmitierCommand;
import com.reborn.shinobicore.character.command.RencontrerCommand;
import com.reborn.shinobicore.character.gui.ChatInputListener;
import com.reborn.shinobicore.character.gui.ChatInputManager;
import com.reborn.shinobicore.character.listener.CharacterLifecycleListener;
import com.reborn.shinobicore.character.listener.CharacterNicknameListener;
import com.reborn.shinobicore.character.repository.CharacterRepository;
import com.reborn.shinobicore.character.repository.YamlCharacterRepository;
import com.reborn.shinobicore.lore.LoreCalendar;
import com.reborn.shinobicore.lore.TabListManager;
import com.reborn.shinobicore.mobility.ShinobiMobilityModule;
import com.reborn.shinobicore.mobility.hud.CooldownHud;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Main entry point for ShinobiCore.
 *
 * Boot order:
 * <ol>
 *   <li>{@link CharacterRepository} (storage backend)</li>
 *   <li>{@link CharacterManager} (roster + active-character tracking)</li>
 *   <li>{@link ChakraManager} + {@link ChakraDisplay}</li>
 *   <li>{@link ShinobiMobilityModule} + {@link CooldownHud}</li>
 *   <li>{@link MeditationManager}</li>
 *   <li>{@link ChatInputManager} / listener + GUI listener + lifecycle listener</li>
 *   <li>Commands: {@code /character}, {@code /meditation}</li>
 * </ol>
 */
public final class ShinobiCore extends JavaPlugin {

    private static ShinobiCore instance;

    private CharacterRepository characterRepository;
    private CharacterManager characterManager;
    private com.reborn.shinobicore.character.LeafTestManager leafTest;
    private CharacterAutoSave characterAutoSave;
    private ChakraManager chakraManager;
    private ChakraDisplay chakraDisplay;
    private ShinobiMobilityModule mobilityModule;
    private CooldownHud cooldownHud;
    private com.reborn.shinobicore.sit.SitListener sitListener;
    private com.reborn.shinobicore.sit.PostureManager postureManager;
    private com.reborn.shinobicore.optimization.OptimizationModule optimization;
    private MeditationManager meditationManager;
    private NoCharacterLockdown noCharacterLockdown;
    private ChatInputManager chatInputs;
    private LoreCalendar loreCalendar;
    private TabListManager tabList;
    private com.reborn.shinobicore.character.select.CharacterSelectManager characterSelect;
    private com.reborn.shinobicore.chat.TypingManager typing;
    private com.reborn.shinobicore.inventory.InventoryManager rpInventory;
    private RencontrerManager rencontrer;
    private FriendshipManager friendships;
    private VisibilityManager visibility;
    // TechniquesService removed alongside the jutsu / techniques rebuild.
    // Legacy chestplate-slot Backpack system removed — the RP bag lives
    // in com.reborn.shinobicore.inventory (RpBag / InventoryManager).
    private com.reborn.shinobicore.ko.KoManager koManager;
    private com.reborn.shinobicore.chakra.ExhaustionManager exhaustionManager;
    private com.reborn.shinobicore.ko.action.PorterManager porterManager;
    private com.reborn.shinobicore.ko.action.KillRequestManager killRequestManager;
    private com.reborn.shinobicore.ko.injury.InjuryHealer injuryHealer;
    private com.reborn.shinobicore.dummy.DummyManager dummyManager;
    private com.reborn.shinobicore.medic.MedicArmoirManager medicArmoirManager;
    private com.reborn.shinobicore.medic.TreatmentApplier medicApplier;
    private com.reborn.shinobicore.cinematic.CinematicManager cinematicManager;
    private com.reborn.shinobicore.vanish.VanishManager vanishManager;
    private ItemGiveRegistry itemGiveRegistry;
    private com.reborn.shinobicore.technique.AbilityRegistry techniqueRegistry;
    private com.reborn.shinobicore.gui.CoreGuiRouter coreGuiRouter;
    private com.reborn.shinobicore.staff.StaffBuildManager staffBuild;
    private com.reborn.shinobicore.panel.PanelBridge panelBridge;
    // Jutsu / techniques managers moved out to the standalone
    // ShinobiAbilities plugin. See ShinobiAbilities_RECREATION_PROMPT.md.

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        this.itemGiveRegistry = new ItemGiveRegistry(this);

        // 0b. Technique registry — engine-owned catalog shell. Registered
        //     empty here (before ANY reader can boot); the active world's
        //     content pack (ShinobiAbilities for Naruto) loads its data
        //     file into it at its own boot step 1.
        this.techniqueRegistry = new com.reborn.shinobicore.technique.AbilityRegistry(this);

        // 1. Persistence.
        this.characterRepository = new YamlCharacterRepository(this);
        this.characterRepository.init();

        // 2. Character roster + active selection.
        this.characterManager = new CharacterManager(this, characterRepository);
        this.characterManager.startWelcomeTicker();

        // 2b. Periodic auto-save — snapshot HP / chakra / position /
        //     inventory for every online player's active character on
        //     a timer, so a crash doesn't roll sessions back to the
        //     last voluntary disconnect. Interval from config; 60s
        //     default. Flushed on onDisable for graceful shutdowns.
        this.characterAutoSave = new CharacterAutoSave(this);
        this.characterAutoSave.start();

        // 3. Chakra (per-character via active lookup) + XP-bar display.
        this.chakraManager = new ChakraManager(this);
        this.chakraManager.start();
        this.chakraDisplay = new ChakraDisplay(this);
        this.chakraDisplay.start();

        // 4. Mobility utilities (Grappin / Zipline only) + cooldown HUD.
        this.mobilityModule = new ShinobiMobilityModule(this);
        this.mobilityModule.enable(chakraManager);
        Bukkit.getPluginManager().registerEvents(
                new com.reborn.shinobicore.mobility.listener.GrappleListener(
                        this, mobilityModule.grapple()), this);
        Bukkit.getPluginManager().registerEvents(
                new com.reborn.shinobicore.mobility.listener.ZiplineListener(
                        this, mobilityModule.zipline()), this);
        this.cooldownHud = new CooldownHud(this);
        this.cooldownHud.start();

        // 4b. Sit on slabs & stairs + RP postures (sit-in-place / lay / crawl).
        //     SitListener: right-click a slab/stair to sit (seat armour stand).
        //     PostureManager: /allonger (lie) + /ramper (crawl) via forced pose.
        //     PostureCommand: /assoir, /allonger, /ramper.
        this.sitListener = new com.reborn.shinobicore.sit.SitListener(this);
        Bukkit.getPluginManager().registerEvents(sitListener, this);
        this.postureManager = new com.reborn.shinobicore.sit.PostureManager(this);
        Bukkit.getPluginManager().registerEvents(postureManager, this);
        com.reborn.shinobicore.sit.PostureCommand postureCmd =
                new com.reborn.shinobicore.sit.PostureCommand(this);
        for (String c : new String[]{"assoir", "allonger", "ramper"}) {
            PluginCommand pc = getCommand(c);
            if (pc != null) pc.setExecutor(postureCmd);
            else getLogger().warning("Command '" + c + "' is not declared in plugin.yml.");
        }

        // 5. Meditation.
        this.meditationManager = new MeditationManager(this);
        Bukkit.getPluginManager().registerEvents(meditationManager, this);

        // 5b. No-character lockdown (infinite blindness + French title).
        this.noCharacterLockdown = new NoCharacterLockdown(this);
        this.noCharacterLockdown.start();

        // 6. Chat-input flow.
        this.chatInputs = new ChatInputManager(this);
        Bukkit.getPluginManager().registerEvents(new ChatInputListener(chatInputs), this);

        // 6b. Rencontrer state manager — transient pending requests + save
        //     prompts for the /rencontrer (meet) identity flow.
        this.rencontrer = new RencontrerManager(this);

        // 6b'. Friendship manager — per-character mutual friend graph
        //      + pending /amitier request state. Persistence lives on
        //      each ShinobiCharacter (mirrored edges), this class just
        //      owns the "both sides together" invariant and the
        //      transient handshake state.
        this.friendships = new FriendshipManager(this);

        // 6b''. Visibility manager — hides every online player who
        //       isn't a current-character friend. Registers as a
        //       listener to catch join events; the CharacterManager
        //       + FriendshipManager call into it on switch / edge flip.
        this.visibility = new VisibilityManager(this);
        Bukkit.getPluginManager().registerEvents(visibility, this);

        // 6c. (Techniques + jutsu init removed — now owned by
        //      the ShinobiAbilities plugin.)

        // 6d. (Legacy chestplate-slot Backpack system removed — the RP
        //      bag is now the tiered Sacoche in the inventory package.)

        // 6e. KO / Tuer / Fouiller / Porter system. Owns the unconscious
        //     state machine, the proximity staff approval flow for
        //     Tuer, the carry mechanic, and the injury healing ticker.
        //     Persistence: ko-state.yml + injuries on each character.
        this.koManager = new com.reborn.shinobicore.ko.KoManager(this);
        this.koManager.start();
        this.porterManager = new com.reborn.shinobicore.ko.action.PorterManager(this);
        this.porterManager.start();
        Bukkit.getPluginManager().registerEvents(porterManager, this);
        this.killRequestManager = new com.reborn.shinobicore.ko.action.KillRequestManager(this);
        this.injuryHealer = new com.reborn.shinobicore.ko.injury.InjuryHealer(this);
        this.injuryHealer.start();
        Bukkit.getPluginManager().registerEvents(
                new com.reborn.shinobicore.ko.KoListener(this), this);

        // 6e-bis. Chakra exhaustion — the overdraw economy. Casting beyond
        //         your pool pushes it into debt; this ticks the debuff ladder
        //         and collapses the host after a deficit-scaled fuse. Needs
        //         ko + chakra (both initialised above).
        this.exhaustionManager = new com.reborn.shinobicore.chakra.ExhaustionManager(this);
        this.exhaustionManager.start();

        // 6f. Dummy training NPCs — Villager-shaped sandbags with
        //     virtual HP / chakra, used to test combat + KO flow
        //     without needing two real players. Owns dummies.yml.
        this.dummyManager = new com.reborn.shinobicore.dummy.DummyManager(this);
        this.dummyManager.load();
        Bukkit.getPluginManager().registerEvents(
                new com.reborn.shinobicore.dummy.DummyListener(this), this);

        // 6f-bis. Cinematics — GM camera tours + the first-play "bienvenue"
        //     intro. Owns cinematics.yml + the playback engine + the in-world
        //     anchor editor. CinematicListener handles GUI clicks, the editor
        //     hotbar, freeze enforcement and the first-play hook.
        this.cinematicManager = new com.reborn.shinobicore.cinematic.CinematicManager(this);
        this.cinematicManager.load();
        Bukkit.getPluginManager().registerEvents(
                new com.reborn.shinobicore.cinematic.CinematicListener(this), this);

        // 6f-ter. Vanish — staff invisibility (GUI-configured), layered on top
        //     of normal visibility. Transient (cleared on logout).
        this.vanishManager = new com.reborn.shinobicore.vanish.VanishManager(this);
        Bukkit.getPluginManager().registerEvents(
                new com.reborn.shinobicore.vanish.VanishListener(this), this);

        // 6g. Medic / Soin module — medicines, /soigner flow, the
        //     placeable Armoire à Pharmacie iron door, and the
        //     "Les Blessures Majeures" encyclopedia. Owns
        //     medic-armoirs.yml.
        this.medicArmoirManager =
                new com.reborn.shinobicore.medic.MedicArmoirManager(this);
        this.medicArmoirManager.start();
        this.medicApplier =
                new com.reborn.shinobicore.medic.TreatmentApplier(this);
        Bukkit.getPluginManager().registerEvents(
                new com.reborn.shinobicore.medic.MedicArmoirListener(this), this);
        Bukkit.getPluginManager().registerEvents(
                new com.reborn.shinobicore.medic.PiluleConsumeListener(this), this);

        // 6h. (Jutsu listeners removed — now owned by ShinobiAbilities.)

        // 6i. Optimization — vanilla feature kill switches driven by
        //     config.yml → optimization. Registered before the lifecycle
        //     listeners so its WorldLoad handler catches every world,
        //     including ones that load mid-startup.
        this.optimization =
                new com.reborn.shinobicore.optimization.OptimizationModule(this);
        Bukkit.getPluginManager().registerEvents(this.optimization, this);
        this.optimization.apply();

        // 7. GUI + lifecycle listeners. Every Core screen rides the
        //    engine screen framework through CoreGuiRouter; the old
        //    GuiListener switchboard is gone.
        this.coreGuiRouter = new com.reborn.shinobicore.gui.CoreGuiRouter(this);
        coreGuiRouter.bindMedic(
                new com.reborn.shinobicore.medic.gui.SoignerScreen(coreGuiRouter),
                new com.reborn.shinobicore.medic.gui.InjuryListScreen(coreGuiRouter),
                new com.reborn.shinobicore.medic.gui.TreatmentScreen(coreGuiRouter));
        coreGuiRouter.bindKo(
                new com.reborn.shinobicore.ko.gui.KoActionScreen(coreGuiRouter),
                new com.reborn.shinobicore.ko.gui.EtatScreen(coreGuiRouter),
                new com.reborn.shinobicore.ko.gui.FouillerScreen(coreGuiRouter));
        coreGuiRouter.bindCharacter(
                new com.reborn.shinobicore.character.gui.CharacterListScreen(coreGuiRouter),
                new com.reborn.shinobicore.character.gui.CharacterEditScreen(coreGuiRouter),
                new com.reborn.shinobicore.character.gui.ClanPickerScreen(coreGuiRouter));
        coreGuiRouter.bindRencontrer(
                new com.reborn.shinobicore.character.gui.RencontrerRootScreen(coreGuiRouter),
                new com.reborn.shinobicore.character.gui.GiveNameScreen(coreGuiRouter),
                new com.reborn.shinobicore.character.gui.GiveNickPickerScreen(coreGuiRouter),
                new com.reborn.shinobicore.character.gui.GiveTargetScreen(coreGuiRouter),
                new com.reborn.shinobicore.character.gui.NameRequestScreen(coreGuiRouter));
        // 7a-bis. Staff panel cluster — in-game hub + GMod-style spawn menu
        //     (blocks/items palette) + custom-item browser. The build-mode
        //     manager guards the per-character inventory snapshot from
        //     creative junk (see StaffBuildManager).
        this.staffBuild = new com.reborn.shinobicore.staff.StaffBuildManager(this);
        Bukkit.getPluginManager().registerEvents(staffBuild, this);
        coreGuiRouter.bindStaff(
                new com.reborn.shinobicore.gui.staff.StaffPanelScreen(coreGuiRouter),
                new com.reborn.shinobicore.gui.staff.SpawnMenuScreen(coreGuiRouter),
                new com.reborn.shinobicore.gui.staff.CustomItemScreen(coreGuiRouter));
        Bukkit.getPluginManager().registerEvents(coreGuiRouter.screens(), this);
        Bukkit.getPluginManager().registerEvents(new CharacterLifecycleListener(this), this);
        // Character nickname rendering: rewrites chat to show
        // "Name Clan »" instead of the Minecraft username, and re-applies
        // display-name on join / world change so tab + nameplate stay in sync.
        Bukkit.getPluginManager().registerEvents(new CharacterNicknameListener(this), this);

        // 7b. Lore calendar + custom tab list (keeps the vanilla roster but
        //     dresses it with a lore-flavoured header/footer; player entries
        //     in the roster pick up the character nickname from step 7).
        this.loreCalendar = new LoreCalendar(this);
        this.loreCalendar.start();
        this.tabList = new TabListManager(this, loreCalendar);
        Bukkit.getPluginManager().registerEvents(tabList, this);
        this.tabList.start();

        // 7d. Sélection de personnage pilotée par le mod client (reborn:character).
        this.characterSelect = new com.reborn.shinobicore.character.select.CharacterSelectManager(this);
        Bukkit.getPluginManager().registerEvents(characterSelect, this);
        this.characterSelect.start();

        // 7e. Indicateur de frappe chat (reborn:typing) → 3 points au-dessus des têtes.
        this.typing = new com.reborn.shinobicore.chat.TypingManager(this);
        Bukkit.getPluginManager().registerEvents(typing, this);
        this.typing.start();

        // 7f. Sacoche RP (reborn:inventory) — inventaire custom lié au perso actif
        //     (capacité en cases + poids + cosmétiques), persisté à part.
        this.rpInventory = new com.reborn.shinobicore.inventory.InventoryManager(this);
        Bukkit.getPluginManager().registerEvents(rpInventory, this);
        this.rpInventory.start();
        PluginCommand sacocheCmd = getCommand("sacoche");
        if (sacocheCmd != null) sacocheCmd.setExecutor(rpInventory);
        else getLogger().warning("Command 'sacoche' is not declared in plugin.yml.");

        // 7c. Expansion PlaceholderAPI 'shinobi' (HUD in-game via BetterHUD) —
        //     enregistrée seulement si PlaceholderAPI est présent (soft dep).
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new com.reborn.shinobicore.integration.ShinobiPlaceholders(this).register();
            getLogger().info("Expansion PlaceholderAPI 'shinobi' enregistree (BetterHUD).");
        } else {
            getLogger().info("PlaceholderAPI absent — HUD in-game (placeholders shinobi) desactive.");
        }

        // 8. Commands.
        PluginCommand characterCmd = getCommand("character");
        if (characterCmd != null) {
            CharacterCommand exec = new CharacterCommand(this);
            characterCmd.setExecutor(exec);
            characterCmd.setTabCompleter(exec);
        } else {
            getLogger().warning("Command 'character' is not declared in plugin.yml.");
        }
        PluginCommand cinematicCmd = getCommand("cinematic");
        if (cinematicCmd != null) {
            com.reborn.shinobicore.cinematic.CinematicCommand cine =
                    new com.reborn.shinobicore.cinematic.CinematicCommand(this);
            cinematicCmd.setExecutor(cine);
            cinematicCmd.setTabCompleter(cine);
        } else {
            getLogger().warning("Command 'cinematic' is not declared in plugin.yml.");
        }
        PluginCommand vanishCmd = getCommand("vanish");
        if (vanishCmd != null) {
            com.reborn.shinobicore.vanish.VanishCommand v =
                    new com.reborn.shinobicore.vanish.VanishCommand(this);
            vanishCmd.setExecutor(v);
            vanishCmd.setTabCompleter(v);
        } else {
            getLogger().warning("Command 'vanish' is not declared in plugin.yml.");
        }
        PluginCommand med = getCommand("meditation");
        if (med != null) med.setExecutor(new MeditationCommand(this));
        else getLogger().warning("Command 'meditation' is not declared in plugin.yml.");

        // Test de la feuille : manager (canal in/out reborn:tirage + clic droit
        // sur l'item) + commande. Le serveur tire la nature (pondérée par le clan),
        // l'attribue, et pousse le résultat au client (popup animé).
        this.leafTest = new com.reborn.shinobicore.character.LeafTestManager(this);
        Bukkit.getPluginManager().registerEvents(leafTest, this);
        leafTest.start();
        PluginCommand tfCmd = getCommand("testfeuille");
        if (tfCmd != null) tfCmd.setExecutor(new com.reborn.shinobicore.character.command.TestFeuilleCommand(this));
        else getLogger().warning("Command 'testfeuille' is not declared in plugin.yml.");


        PluginCommand staffCmd = getCommand("staff");
        if (staffCmd != null) {
            com.reborn.shinobicore.staff.command.StaffCommand sc =
                    new com.reborn.shinobicore.staff.command.StaffCommand(this);
            staffCmd.setExecutor(sc);
            staffCmd.setTabCompleter(sc);
        } else getLogger().warning("Command 'staff' is not declared in plugin.yml.");

        PluginCommand meCmd = getCommand("me");
        if (meCmd != null) meCmd.setExecutor(new MeCommand(this));
        else getLogger().warning("Command 'me' is not declared in plugin.yml.");

        PluginCommand rencontrerCmd = getCommand("rencontrer");
        if (rencontrerCmd != null) rencontrerCmd.setExecutor(new RencontrerCommand(this));
        else getLogger().warning("Command 'rencontrer' is not declared in plugin.yml.");

        PluginCommand amitierCmd = getCommand("amitier");
        if (amitierCmd != null) amitierCmd.setExecutor(new AmitierCommand(this));
        else getLogger().warning("Command 'amitier' is not declared in plugin.yml.");

        PluginCommand etatCmd = getCommand("etat");
        if (etatCmd != null) {
            etatCmd.setExecutor(
                    new com.reborn.shinobicore.ko.command.EtatCommand(this));
        } else getLogger().warning("Command 'etat' is not declared in plugin.yml.");

        PluginCommand osculterCmd = getCommand("osculter");
        if (osculterCmd != null) {
            osculterCmd.setExecutor(
                    new com.reborn.shinobicore.ko.command.OsculterCommand(this));
        } else getLogger().warning("Command 'osculter' is not declared in plugin.yml.");

        PluginCommand killVoteCmd = getCommand("sckillvote");
        if (killVoteCmd != null) {
            killVoteCmd.setExecutor(
                    new com.reborn.shinobicore.ko.command.SckillVoteCommand(this));
        } else getLogger().warning("Command 'sckillvote' is not declared in plugin.yml.");

        PluginCommand soignerCmd = getCommand("soigner");
        if (soignerCmd != null) {
            soignerCmd.setExecutor(
                    new com.reborn.shinobicore.medic.command.SoignerCommand(this));
        } else getLogger().warning("Command 'soigner' is not declared in plugin.yml.");

        PluginCommand dummyCmd = getCommand("dummy");
        if (dummyCmd != null) {
            com.reborn.shinobicore.dummy.DummyCommand exec =
                    new com.reborn.shinobicore.dummy.DummyCommand(this);
            dummyCmd.setExecutor(exec);
            dummyCmd.setTabCompleter(exec);
        } else getLogger().warning("Command 'dummy' is not declared in plugin.yml.");

        // Tab-completion for /shinobi (a.k.a. /sc) \u2014 top-level subcommands,
        // itemgive registry tokens, CHARACTER names for heal/chakra (these
        // take a character name, not a player name!), percentage presets,
        // and welcome on|off.
        PluginCommand shinobiCmd = getCommand("shinobi");
        if (shinobiCmd != null) {
            shinobiCmd.setTabCompleter((sender, command, alias, args) -> {
                if (args.length == 1) {
                    java.util.List<String> subs = new java.util.ArrayList<>(
                            java.util.List.of("help", "chakra", "drop", "welcome", "competences"));
                    if (sender.hasPermission("shinobicore.staff")) {
                        subs.add("roll");
                        subs.add("overdraw");
                    }
                    if (sender.hasPermission("shinobicore.admin")) {
                        subs.addAll(java.util.List.of("reload", "heal", "itemgive"));
                    }
                    return prefixFilter(subs, args[0]);
                }
                String sub = args[0].toLowerCase();
                switch (sub) {
                    case "itemgive", "give" -> {
                        if (args.length == 2) {
                            // Original-case tokens, case-insensitive match.
                            return prefixFilter(itemGiveRegistry.tokens(), args[1]);
                        }
                    }
                    case "heal", "chakra" -> {
                        // Argument is a CHARACTER name across every loaded
                        // roster \u2014 not a player name.
                        if (args.length == 2
                                && sender.hasPermission("shinobicore.admin")) {
                            return prefixFilter(loadedCharacterNames(), args[1]);
                        }
                        if (args.length == 3) {
                            return prefixFilter(
                                    java.util.List.of("25", "50", "75", "100"), args[2]);
                        }
                    }
                    case "welcome" -> {
                        if (args.length == 2) {
                            return prefixFilter(java.util.List.of("on", "off"), args[1]);
                        }
                    }
                    case "roll", "jet" -> {
                        if (args.length == 2) return prefixFilter(loadedCharacterNames(), args[1]);
                        if (args.length == 3) {
                            java.util.List<String> sn = new java.util.ArrayList<>();
                            for (var sk : com.reborn.shinobicore.skill.Skill.values()) sn.add(sk.displayName());
                            return prefixFilter(sn, args[2]);
                        }
                        if (args.length == 4) {
                            return prefixFilter(
                                    com.reborn.shinobicore.skill.RollService.presetNames(), args[3]);
                        }
                    }
                    case "competences", "compétences", "skills" -> {
                        if (args.length == 2) {
                            java.util.List<String> sn = new java.util.ArrayList<>();
                            sn.add("give");
                            for (var sk : com.reborn.shinobicore.skill.Skill.values()) sn.add(sk.displayName());
                            return prefixFilter(sn, args[1]);
                        }
                        if (args.length == 3) {
                            return prefixFilter(java.util.List.of("1", "2", "3", "5"), args[2]);
                        }
                    }
                    case "overdraw", "surcharge" -> {
                        if (args.length == 2) return prefixFilter(loadedCharacterNames(), args[1]);
                        if (args.length == 3) return prefixFilter(java.util.List.of("500", "1000", "2000"), args[2]);
                    }
                    default -> { }
                }
                return java.util.List.of();
            });
        }

        // 9. API seam \u2014 publish the engine services through Bukkit's
        //    ServicesManager. Dependents compile against the interfaces
        //    in com.reborn.shinobicore.api and resolve them here; the
        //    concrete accessors below remain as an @Internal facade.
        var services = getServer().getServicesManager();
        services.register(com.reborn.shinobicore.api.CharacterService.class,
                characterManager, this, org.bukkit.plugin.ServicePriority.Normal);
        services.register(com.reborn.shinobicore.api.ResourceService.class,
                chakraManager, this, org.bukkit.plugin.ServicePriority.Normal);
        services.register(com.reborn.shinobicore.api.KoService.class,
                koManager, this, org.bukkit.plugin.ServicePriority.Normal);
        services.register(com.reborn.shinobicore.api.SkillService.class,
                new com.reborn.shinobicore.skill.SkillServiceImpl(),
                this, org.bukkit.plugin.ServicePriority.Normal);
        services.register(com.reborn.shinobicore.api.HudService.class,
                cooldownHud, this, org.bukkit.plugin.ServicePriority.Normal);
        services.register(com.reborn.shinobicore.api.ItemGiveService.class,
                itemGiveRegistry, this, org.bukkit.plugin.ServicePriority.Normal);
        services.register(com.reborn.shinobicore.api.MobilityService.class,
                mobilityModule, this, org.bukkit.plugin.ServicePriority.Normal);
        services.register(com.reborn.shinobicore.api.ProgressionLadder.class,
                new com.reborn.shinobicore.character.RankLadder(),
                this, org.bukkit.plugin.ServicePriority.Normal);
        services.register(com.reborn.shinobicore.api.TechniqueRegistry.class,
                techniqueRegistry, this, org.bukkit.plugin.ServicePriority.Normal);
        // Concrete type too: the world pack needs load()/reload access,
        // which is deliberately NOT on the read-only api interface.
        services.register(com.reborn.shinobicore.technique.AbilityRegistry.class,
                techniqueRegistry, this, org.bukkit.plugin.ServicePriority.Normal);

        // 10. Panel command bridge \u2014 outbound poller that lets the web panel
        //     trigger a short whitelist of console reloads (/nexo reload etc.)
        //     WITHOUT RCON or any inbound port. Disabled unless panel-bridge.
        //     enabled=true with a resolvable HMAC secret. All HTTP is async;
        //     dispatched commands hop back to the main thread.
        this.panelBridge = new com.reborn.shinobicore.panel.PanelBridge(this);
        this.panelBridge.start();

        getLogger().info("ShinobiCore enabled \u2014 mobility + character + chakra systems online.");
    }

    @Override
    public void onDisable() {
        // Restore any staff builder to survival + their real RP inventory
        // BEFORE the flush below, so the capture records the survival
        // inventory rather than creative build junk.
        if (panelBridge != null) panelBridge.stop();
        if (staffBuild != null) staffBuild.restoreAll();
        // Flush live player state (HP / chakra / position / inventory)
        // before the roster save so graceful shutdowns preserve the
        // moment-to-moment session rather than the last ticker tick.
        if (characterAutoSave != null) {
            characterAutoSave.flushAll();
            characterAutoSave.stop();
        }
        if (characterManager != null) {
            for (var entry : characterManager.rosterView().entrySet()) {
                characterManager.saveAll(entry.getKey());
            }
        }
        if (meditationManager != null) meditationManager.shutdown();
        if (noCharacterLockdown != null) noCharacterLockdown.stop();
        if (tabList != null) tabList.stop();
        if (loreCalendar != null) loreCalendar.stop();
        if (cooldownHud != null) cooldownHud.stop();
        if (chakraDisplay != null) chakraDisplay.stop();
        if (mobilityModule != null) mobilityModule.disable();
        if (chakraManager != null) chakraManager.stop();
        if (characterManager != null) characterManager.stopWelcomeTicker();
        if (rpInventory != null) rpInventory.flush();
        if (porterManager != null) porterManager.stop();
        if (injuryHealer != null) injuryHealer.stop();
        if (koManager != null) koManager.stop();
        if (exhaustionManager != null) exhaustionManager.stop();
        if (dummyManager != null) dummyManager.save();
        if (cinematicManager != null) cinematicManager.shutdown();
        if (medicArmoirManager != null) medicArmoirManager.stop();
        if (characterRepository != null) characterRepository.close();
        getLogger().info("ShinobiCore disabled.");
    }

    /**
     * Reload everything that reads from {@code config.yml}. Used by both
     * {@code /shinobi reload} and {@code /sc reload}.
     */
    public void reloadAll(CommandSender to) {
        reloadConfig();
        if (chakraManager       != null) chakraManager.reloadConfig();
        if (chakraDisplay       != null) chakraDisplay.reloadConfig();
        if (cooldownHud         != null) cooldownHud.reloadConfig();
        if (noCharacterLockdown != null) noCharacterLockdown.reloadConfig();
        if (loreCalendar        != null) loreCalendar.reloadConfig();
        if (tabList             != null) tabList.reloadConfig();
        if (characterSelect     != null) characterSelect.reloadConfig();
        if (optimization        != null) optimization.apply();
        if (to != null) {
            to.sendMessage(Component.text("Configuration ShinobiCore rechargée.", NamedTextColor.GREEN));
        }
    }

    /* -------------------------------------------------- /shinobi command */

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!"shinobi".equalsIgnoreCase(cmd.getName())) return false;

        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            // RP-related commands are intentionally NOT listed here —
            // /me, /rencontrer, /meditation are discoverable through
            // roleplay, not through a command-list dump. Players see
            // only the tooling that supports core gameplay (character
            // picker) plus the social commands. Jutsu / techniques /
            // mobility commands are surfaced by the ShinobiAbilities
            // plugin's own /help. Staff additionally see admin entries.
            sender.sendMessage(Component.text("Commandes ShinobiCore :", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("  /character",  NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("  /me",         NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("  /rencontrer", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("  /amitier",    NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("  /sc competences", NamedTextColor.YELLOW));
            if (sender.hasPermission("shinobicore.staff")) {
                sender.sendMessage(Component.text("Staff :", NamedTextColor.AQUA));
                sender.sendMessage(Component.text("  /sc roll <perso> <compétence> [difficulté] <texte>", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("  /sc overdraw <perso> <montant>", NamedTextColor.GRAY));
            }
            if (sender.hasPermission("shinobicore.admin")) {
                sender.sendMessage(Component.text("Admin :", NamedTextColor.LIGHT_PURPLE));
                sender.sendMessage(Component.text("  /sc reload",                      NamedTextColor.GRAY));
                sender.sendMessage(Component.text("  /sc itemgive <objet>",            NamedTextColor.GRAY));
                sender.sendMessage(Component.text("  /sc heal <perso> <%>",            NamedTextColor.GRAY));
                sender.sendMessage(Component.text("  /sc chakra <perso> <%>",          NamedTextColor.GRAY));
            }
            return true;
        }

        if ("reload".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("shinobicore.admin")) {
                sender.sendMessage(Component.text("Permission insuffisante.", NamedTextColor.RED));
                return true;
            }
            reloadAll(sender);
            return true;
        }

        // /sc itemgive <name> — unified handout for every custom item
        // the plugin ships. Replaces /grappin, /zipline, /learnshelf,
        // /sac give, /medic armoir, /medic bookall, /medic give.
        if ("itemgive".equalsIgnoreCase(args[0])
                || "give".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("shinobicore.admin")) {
                sender.sendMessage(Component.text("Permission insuffisante.",
                        NamedTextColor.RED));
                return true;
            }
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Component.text(
                        "Cette commande nécessite un joueur.",
                        NamedTextColor.RED));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(Component.text(
                        "Usage : /sc itemgive <objet>", NamedTextColor.GRAY));
                return true;
            }
            String token = args[1];
            ItemGiveRegistry.GiveResult res = itemGiveRegistry.give(p, token);
            if (res == null) {
                sender.sendMessage(Component.text(
                        "Objet inconnu : " + token, NamedTextColor.RED));
                return true;
            }
            var leftover = p.getInventory().addItem(res.item());
            for (var stuck : leftover.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), stuck);
            }
            p.sendMessage(Component.text(
                    res.label() + " ajouté à ton inventaire.",
                    NamedTextColor.GREEN));
            return true;
        }

        // /sc roll <perso> <compétence> [difficulté] <narration> — GM skill check.
        // Placed before the player gate so a staff console can roll too.
        if ("roll".equalsIgnoreCase(args[0]) || "jet".equalsIgnoreCase(args[0])) {
            return com.reborn.shinobicore.skill.SkillCommands.handleRoll(this, sender, args);
        }

        // /sc overdraw <perso> <montant> — staff: force chakra debt (demo +
        // GM narrative tool for inflicting exhaustion).
        if ("overdraw".equalsIgnoreCase(args[0]) || "surcharge".equalsIgnoreCase(args[0])) {
            return com.reborn.shinobicore.chakra.ExhaustionManager.handleOverdraw(this, sender, args);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Cette sous-commande nécessite un joueur.", NamedTextColor.RED));
            return true;
        }

        // /sc competences [<compétence> <points> | give <perso> <points>]
        if ("competences".equalsIgnoreCase(args[0]) || "compétences".equalsIgnoreCase(args[0])
                || "skills".equalsIgnoreCase(args[0])) {
            return com.reborn.shinobicore.skill.SkillCommands.handleSkills(this, player, args);
        }

        if ("chakra".equalsIgnoreCase(args[0])) {
            // Two forms:
            //   /sc chakra                — print self's current chakra
            //   /sc chakra <char> <pct>   — admin restore (sets to pct% of max)
            if (args.length >= 3) {
                if (!sender.hasPermission("shinobicore.admin")) {
                    sender.sendMessage(Component.text(
                            "Permission insuffisante.", NamedTextColor.RED));
                    return true;
                }
                handleStatRestore(sender, args[1], args[2], /*hp=*/false);
                return true;
            }
            var pool = chakraManager.get(player);
            player.sendMessage(Component.text(
                    "Chakra : " + String.format("%.1f", pool.current())
                            + " / " + String.format("%.1f", pool.max()),
                    NamedTextColor.AQUA));
            return true;
        }

        if ("heal".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("shinobicore.admin")) {
                sender.sendMessage(Component.text(
                        "Permission insuffisante.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(Component.text(
                        "Usage : /sc heal <Personnage> <Pourcent (0-100)>",
                        NamedTextColor.RED));
                return true;
            }
            handleStatRestore(sender, args[1], args[2], /*hp=*/true);
            return true;
        }

        // /sc drop — drops anyone (or any dummy) the player is carrying.
        // Backup for the sneak-to-drop hook, which the vanilla client
        // sometimes suppresses while a passenger is on top of the player.
        if ("drop".equalsIgnoreCase(args[0])
                || "deposer".equalsIgnoreCase(args[0])
                || "déposer".equalsIgnoreCase(args[0])) {
            if (porterManager != null) porterManager.dropEverything(player);
            player.sendMessage(Component.text(
                    "Tu déposes ce que tu portais.", NamedTextColor.GRAY));
            return true;
        }

        // /sc welcome on|off — toggles the per-character welcome reminder.
        // Intentionally undocumented in /sc help: the only discovery
        // surface is the "[Ne plus afficher ce message]" button rendered
        // at the end of the welcome message itself, which runs
        // "/sc welcome off" via a runCommand click event.
        if ("welcome".equalsIgnoreCase(args[0])) {
            var active = characterManager.getActive(player.getUniqueId());
            if (active == null) {
                player.sendMessage(Component.text(
                        "Aucun personnage actif.", NamedTextColor.RED));
                return true;
            }
            String mode = args.length >= 2 ? args[1].toLowerCase() : "off";
            switch (mode) {
                case "off" -> {
                    active.setWelcomeDisabled(true);
                    characterRepository.save(active);
                    player.sendMessage(Component.text(
                            "Rappels de bienvenue désactivés pour ce personnage.",
                            NamedTextColor.GRAY));
                }
                case "on" -> {
                    active.setWelcomeDisabled(false);
                    characterRepository.save(active);
                    player.sendMessage(Component.text(
                            "Rappels de bienvenue réactivés pour ce personnage.",
                            NamedTextColor.GRAY));
                }
                default -> player.sendMessage(Component.text(
                        "Usage : /sc welcome on|off", NamedTextColor.RED));
            }
            return true;
        }

        sender.sendMessage(Component.text("Sous-commande inconnue. Essaie /sc help", NamedTextColor.RED));
        return true;
    }

    /* ----------------------------------------------- tab-complete helpers */

    /** Names of every loaded character, alphabetical, deduplicated. */
    private java.util.List<String> loadedCharacterNames() {
        java.util.TreeSet<String> names =
                new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (var roster : characterManager.rosterView().values()) {
            for (var c : roster) names.add(c.name());
        }
        return new java.util.ArrayList<>(names);
    }

    /** Case-insensitive prefix filter preserving original casing. */
    private static java.util.List<String> prefixFilter(
            java.util.List<String> options, String prefix) {
        String low = prefix == null ? "" : prefix.toLowerCase();
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase().startsWith(low)) out.add(o);
        }
        return out;
    }

    /* -------------------------------------------- /sc heal & /sc chakra */

    /**
     * Resolve {@code charName} across every loaded roster + every
     * online player's roster, then bump the chosen stat to
     * {@code pct%} of its max. When the heal pushes the player above
     * the KO heal threshold, {@link com.reborn.shinobicore.ko.KoManager#checkHealRevive}
     * wakes them on the next tick.
     *
     * @param hp true → heal HP, false → restore chakra
     */
    private void handleStatRestore(CommandSender sender, String charName,
                                   String pctRaw, boolean hp) {
        double pct;
        try { pct = Double.parseDouble(pctRaw); }
        catch (NumberFormatException ex) {
            sender.sendMessage(Component.text(
                    "Pourcentage invalide : " + pctRaw, NamedTextColor.RED));
            return;
        }
        if (pct < 0) pct = 0;
        if (pct > 100) pct = 100;
        double frac = pct / 100.0;

        // Search loaded characters across every player. Case-insensitive
        // exact match.
        com.reborn.shinobicore.character.ShinobiCharacter target = null;
        java.util.UUID owner = null;
        for (var entry : characterManager.rosterView().entrySet()) {
            for (var c : entry.getValue()) {
                if (c.name().equalsIgnoreCase(charName)) {
                    target = c;
                    owner = entry.getKey();
                    break;
                }
            }
            if (target != null) break;
        }

        // Dummy fallback — if nothing matches a real character, look
        // for a Dummy NPC of that name. /sc heal/chakra should work on
        // dummies too so test loops don't need a separate command.
        if (target == null && dummyManager != null) {
            var d = dummyManager.byName(charName);
            if (d != null) {
                if (hp) {
                    d.setCurrentHp(d.maxHp() * frac);
                } else {
                    d.setCurrentChakra(d.maxChakra() * frac);
                }
                dummyManager.save();
                dummyManager.refreshVisual(d);
                sender.sendMessage(Component.text(
                        (hp ? "PV " : "Chakra ")
                                + "du dummy " + d.name() + " : "
                                + String.format("%.0f%%", pct),
                        NamedTextColor.GREEN));
                return;
            }
        }

        if (target == null) {
            sender.sendMessage(Component.text(
                    "Aucun personnage trouvé pour : " + charName,
                    NamedTextColor.RED));
            return;
        }

        if (hp) {
            double newHp = target.maxHp() * frac;
            target.setCurrentHp(newHp);
            // Mirror to live player attribute if they're online.
            org.bukkit.entity.Player p = Bukkit.getPlayer(owner);
            if (p != null && p.isOnline()) {
                p.setHealth(Math.min(newHp, p.getMaxHealth()));
                if (koManager != null) koManager.checkHealRevive(p);
            }
        } else {
            double newCh = target.chakra().max() * frac;
            target.chakra().setCurrent(newCh);
        }
        characterRepository.save(target);
        sender.sendMessage(Component.text(
                (hp ? "PV " : "Chakra ")
                        + "de " + target.name() + " : "
                        + String.format("%.0f%%", pct),
                NamedTextColor.GREEN));
    }

    /* ---------------------------------------------------------- accessors
     *
     * @Internal facade. The stable contract is the interface set in
     * com.reborn.shinobicore.api, resolved via Bukkit's ServicesManager.
     * These concrete accessors remain for Core-internal wiring and
     * backwards compatibility; they may change without notice. Do not
     * add new cross-module call sites against them.
     */

    @com.reborn.shinobicore.api.Internal
    public static ShinobiCore get() { return instance; }
    /** Core's own screen router (character/KO/medic/rencontrer GUIs). */
    @com.reborn.shinobicore.api.Internal
    public com.reborn.shinobicore.gui.CoreGuiRouter coreGui() { return coreGuiRouter; }
    /** Staff builder creative-mode manager (guards the inventory snapshot). */
    @com.reborn.shinobicore.api.Internal
    public com.reborn.shinobicore.staff.StaffBuildManager staffBuild() { return staffBuild; }
    /** True while {@code player} is in staff build mode — capture sites
     *  (auto-save) skip these players so creative junk never overwrites
     *  the character's RP inventory. */
    @com.reborn.shinobicore.api.Internal
    public boolean isStaffBuilding(java.util.UUID playerId) {
        return staffBuild != null && staffBuild.isBuilding(playerId);
    }
    /** Givable-item registry — addon plugins register their own
     *  {@code /sc itemgive} tokens here at boot. */
    @com.reborn.shinobicore.api.Internal
    public ItemGiveRegistry itemGive() { return itemGiveRegistry; }
    @com.reborn.shinobicore.api.Internal
    public CharacterRepository characterRepository() { return characterRepository; }
    @com.reborn.shinobicore.api.Internal
    public CharacterManager characters() { return characterManager; }

    public com.reborn.shinobicore.inventory.InventoryManager rpInventory() { return rpInventory; }

    /** Manager du test de la feuille (tirage de nature pondéré par le clan). */
    public com.reborn.shinobicore.character.LeafTestManager leafTest() { return leafTest; }
    @com.reborn.shinobicore.api.Internal
    public com.reborn.shinobicore.character.select.CharacterSelectManager characterSelect() { return characterSelect; }
    @com.reborn.shinobicore.api.Internal
    public com.reborn.shinobicore.chakra.ExhaustionManager exhaustion() { return exhaustionManager; }
    @com.reborn.shinobicore.api.Internal
    public ChakraManager chakra() { return chakraManager; }
    @com.reborn.shinobicore.api.Internal
    public ChakraDisplay chakraDisplay() { return chakraDisplay; }
    @com.reborn.shinobicore.api.Internal
    public ShinobiMobilityModule mobility() { return mobilityModule; }
    @com.reborn.shinobicore.api.Internal
    public CooldownHud cooldownHud() { return cooldownHud; }
    @com.reborn.shinobicore.api.Internal
    public com.reborn.shinobicore.sit.SitListener sit() { return sitListener; }
    @com.reborn.shinobicore.api.Internal
    public com.reborn.shinobicore.sit.PostureManager posture() { return postureManager; }
    @com.reborn.shinobicore.api.Internal
    public MeditationManager meditation() { return meditationManager; }
    @com.reborn.shinobicore.api.Internal
    public NoCharacterLockdown noCharacterLockdown() { return noCharacterLockdown; }
    @com.reborn.shinobicore.api.Internal
    public ChatInputManager chatInputs() { return chatInputs; }
    @com.reborn.shinobicore.api.Internal
    public LoreCalendar loreCalendar() { return loreCalendar; }
    @com.reborn.shinobicore.api.Internal
    public TabListManager tabList() { return tabList; }
    @com.reborn.shinobicore.api.Internal
    public RencontrerManager rencontrer() { return rencontrer; }
    @com.reborn.shinobicore.api.Internal
    public FriendshipManager friendships() { return friendships; }
    @com.reborn.shinobicore.api.Internal
    public VisibilityManager visibility() { return visibility; }
    @com.reborn.shinobicore.api.Internal
    public com.reborn.shinobicore.ko.KoManager ko() { return koManager; }
    @com.reborn.shinobicore.api.Internal
    public com.reborn.shinobicore.ko.action.PorterManager porter() { return porterManager; }
    @com.reborn.shinobicore.api.Internal
    public com.reborn.shinobicore.ko.action.KillRequestManager killRequests() { return killRequestManager; }
    @com.reborn.shinobicore.api.Internal
    public com.reborn.shinobicore.ko.injury.InjuryHealer injuryHealer() { return injuryHealer; }
    @com.reborn.shinobicore.api.Internal
    public com.reborn.shinobicore.dummy.DummyManager dummies() { return dummyManager; }
    @com.reborn.shinobicore.api.Internal
    public com.reborn.shinobicore.cinematic.CinematicManager cinematics() { return cinematicManager; }
    @com.reborn.shinobicore.api.Internal
    public com.reborn.shinobicore.vanish.VanishManager vanish() { return vanishManager; }
    @com.reborn.shinobicore.api.Internal
    public com.reborn.shinobicore.medic.MedicArmoirManager armoirs() { return medicArmoirManager; }
    @com.reborn.shinobicore.api.Internal
    public com.reborn.shinobicore.medic.TreatmentApplier medicApplier() { return medicApplier; }
}
