package com.reborn.shinobiabilities.command;

import com.reborn.shinobicore.technique.Ability;
import com.reborn.shinobicore.technique.AbilityRegistry;
import com.reborn.shinobiabilities.jutsu.JutsuBindingStore;
import com.reborn.shinobicore.technique.JutsuItemType;
import com.reborn.shinobiabilities.mobility.MobilityActionSlot;
import com.reborn.shinobiabilities.mobility.MobilityModule;
import com.reborn.shinobiabilities.techniques.LearningMinigame;
import com.reborn.shinobiabilities.techniques.ParcheminItems;
import com.reborn.shinobiabilities.util.CooldownTracker;
import com.reborn.shinobiabilities.CoreServices;
import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /sa} — the ShinobiAbilities admin + staff suite.
 *
 * <pre>
 * /sa help
 * /sa reload                                   (admin)
 * /sa learn   &lt;perso&gt; &lt;technique&gt;              (admin)
 * /sa forget  &lt;perso&gt; &lt;technique&gt;              (admin)
 * /sa learnall &lt;perso&gt; [préfixe-catégorie]     (admin)
 * /sa bind    &lt;perso&gt; &lt;canal&gt; &lt;slot 1-5&gt; &lt;technique|none&gt;  (admin)
 * /sa cooldowns clear &lt;joueur&gt;                 (admin)
 * /sa abilities list [préfixe] | info &lt;id&gt;
 * /sa parchemin &lt;technique|random&gt; [joueur]    (admin)
 * /sa valider &lt;joueur&gt; | refuser &lt;joueur&gt;      (staff — suivi)
 * /sa toggles &lt;perso&gt;                          (admin)
 * /sa tokens  [joueur]                         (admin)
 * </pre>
 */
public final class SaCommand implements CommandExecutor, TabCompleter {

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final CoreServices core;
    private final AbilityRegistry registry;
    private final JutsuBindingStore bindings;
    private final CooldownTracker cooldowns;
    private final MobilityModule mobility;
    private final LearningMinigame minigame;
    private final Runnable reloadHook;

    public SaCommand(org.bukkit.plugin.java.JavaPlugin plugin,
                     CoreServices core, AbilityRegistry registry,
                     JutsuBindingStore bindings, CooldownTracker cooldowns,
                     MobilityModule mobility, LearningMinigame minigame,
                     Runnable reloadHook) {
        this.plugin = plugin;
        this.core = core;
        this.registry = registry;
        this.bindings = bindings;
        this.cooldowns = cooldowns;
        this.mobility = mobility;
        this.minigame = minigame;
        this.reloadHook = reloadHook;
    }

    /* ------------------------------------------------------------ helpers */

    private record CharRef(UUID owner, ShinobiCharacter character) {}

    /** Case-insensitive character search across every loaded roster. */
    private CharRef findCharacter(String name) {
        for (Map.Entry<UUID, List<ShinobiCharacter>> e
                : core.characters().rosterView().entrySet()) {
            for (ShinobiCharacter c : e.getValue()) {
                if (c.name().equalsIgnoreCase(name)) return new CharRef(e.getKey(), c);
            }
        }
        return null;
    }

    private static void msg(CommandSender to, String text, NamedTextColor color) {
        to.sendMessage(Component.text(text, color));
    }

    private boolean requirePerm(CommandSender sender, String perm) {
        if (sender.hasPermission(perm)) return true;
        msg(sender, "Permission insuffisante.", NamedTextColor.RED);
        return false;
    }

    /* ------------------------------------------------------------- command */

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                if (!requirePerm(sender, "shinobiabilities.admin")) return true;
                reloadHook.run();
                msg(sender, "Configuration ShinobiAbilities rechargée ("
                        + registry.all().size() + " techniques).", NamedTextColor.GREEN);
            }
            case "learn", "forget" -> handleLearnForget(sender, args);
            case "learnall" -> handleLearnAll(sender, args);
            case "importspells" -> handleImportSpells(sender, args);
            case "bind" -> handleBind(sender, args);
            case "cooldowns" -> handleCooldowns(sender, args);
            case "abilities" -> handleAbilities(sender, args);
            case "parchemin" -> handleParchemin(sender, args);
            case "valider", "refuser" -> handleSuivi(sender, args);
            case "toggles" -> handleToggles(sender, args);
            case "unlock", "lock" -> handleUnlockLock(sender, args);
            case "tokens" -> handleTokens(sender, args);
            default -> msg(sender, "Sous-commande inconnue — /sa help.", NamedTextColor.RED);
        }
        return true;
    }

    private void help(CommandSender sender) {
        msg(sender, "Tout passe par le menu : /menu", NamedTextColor.GOLD);
        msg(sender, "  (raccourcis directs : /toggle, /techniques)", NamedTextColor.GRAY);
        if (sender.hasPermission("shinobiabilities.staff")) {
            msg(sender, "Staff — le GUI « Validations » est dans /menu ;"
                    + " repli texte :", NamedTextColor.LIGHT_PURPLE);
            msg(sender, "  /sa valider <joueur> · /sa refuser <joueur>", NamedTextColor.GRAY);
            msg(sender, "  /trail create|add|finish|delete|list|tp", NamedTextColor.GRAY);
        }
        if (sender.hasPermission("shinobiabilities.admin")) {
            msg(sender, "Admin — le GUI « Administration » est dans /menu ;"
                    + " repli texte :", NamedTextColor.LIGHT_PURPLE);
            msg(sender, "  /sa reload", NamedTextColor.GRAY);
            msg(sender, "  /sa learn|forget <perso> <technique>", NamedTextColor.GRAY);
            msg(sender, "  /sa learnall <perso> [préfixe]", NamedTextColor.GRAY);
            msg(sender, "  /sa bind <perso> <canal> <slot 1-5> <technique|none>", NamedTextColor.GRAY);
            msg(sender, "  /sa cooldowns clear <joueur>", NamedTextColor.GRAY);
            msg(sender, "  /sa abilities list [préfixe] · info <id>", NamedTextColor.GRAY);
            msg(sender, "  /sa importspells [catégorie] [rang] — sorts MagicSpells → yml",
                    NamedTextColor.GRAY);
            msg(sender, "  /sa parchemin <technique|random> [joueur]", NamedTextColor.GRAY);
            msg(sender, "  /sa toggles <perso> · /sa tokens [joueur]", NamedTextColor.GRAY);
            msg(sender, "  /sa unlock|lock <perso> <capacité|all> — débloque la mobilité",
                    NamedTextColor.GRAY);
        }
    }

    /* ----------------------------------------------------- learn / forget */

    private void handleLearnForget(CommandSender sender, String[] args) {
        if (!requirePerm(sender, "shinobiabilities.admin")) return;
        if (args.length < 3) {
            msg(sender, "Usage : /sa " + args[0].toLowerCase(Locale.ROOT)
                    + " <perso> <technique>", NamedTextColor.RED);
            return;
        }
        CharRef ref = findCharacter(args[1]);
        if (ref == null) {
            msg(sender, "Personnage introuvable : " + args[1], NamedTextColor.RED);
            return;
        }
        Ability a = registry.byId(args[2]);
        if (a == null) {
            msg(sender, "Technique inconnue : " + args[2], NamedTextColor.RED);
            return;
        }
        boolean learn = args[0].equalsIgnoreCase("learn");
        boolean changed = learn
                ? ref.character().learnAbility(a.id())
                : ref.character().forgetAbility(a.id());
        if (changed) core.characters().save(ref.character());
        msg(sender, (learn ? "Apprise : " : "Oubliée : ") + a.name()
                + " pour " + ref.character().name()
                + (changed ? "." : " (déjà le cas)."), NamedTextColor.GREEN);
    }

    private void handleLearnAll(CommandSender sender, String[] args) {
        if (!requirePerm(sender, "shinobiabilities.admin")) return;
        if (args.length < 2) {
            msg(sender, "Usage : /sa learnall <perso> [préfixe-catégorie]", NamedTextColor.RED);
            return;
        }
        CharRef ref = findCharacter(args[1]);
        if (ref == null) {
            msg(sender, "Personnage introuvable : " + args[1], NamedTextColor.RED);
            return;
        }
        String prefix = args.length >= 3 ? args[2] : "";
        int added = 0;
        for (Ability a : registry.byCategoryPrefix(prefix)) {
            if (ref.character().learnAbility(a.id())) added++;
        }
        if (added > 0) core.characters().save(ref.character());
        msg(sender, added + " technique(s) apprises pour "
                + ref.character().name() + ".", NamedTextColor.GREEN);
    }

    /* ---------------------------------------------------------------- bind */

    private void handleBind(CommandSender sender, String[] args) {
        if (!requirePerm(sender, "shinobiabilities.admin")) return;
        if (args.length < 5) {
            msg(sender, "Usage : /sa bind <perso> <canal> <slot 1-5> <technique|none>",
                    NamedTextColor.RED);
            return;
        }
        CharRef ref = findCharacter(args[1]);
        if (ref == null) {
            msg(sender, "Personnage introuvable : " + args[1], NamedTextColor.RED);
            return;
        }
        JutsuItemType type = JutsuItemType.from(args[2]);
        if (type == null) {
            msg(sender, "Canal inconnu. Choix : ROULEAU, PUPILLES, KATANA, KUNAI, "
                    + "SHURIKEN, FUMA, POING.", NamedTextColor.RED);
            return;
        }
        int slot;
        try { slot = Integer.parseInt(args[3]) - 1; }
        catch (NumberFormatException ex) { slot = -1; }
        if (slot < 0 || slot >= JutsuBindingStore.SLOTS) {
            msg(sender, "Slot invalide (1-5).", NamedTextColor.RED);
            return;
        }
        if (args[4].equalsIgnoreCase("none")) {
            bindings.set(ref.character().id(), type, slot, null);
            msg(sender, "Slot " + (slot + 1) + " (" + type.displayName()
                    + ") vidé pour " + ref.character().name() + ".", NamedTextColor.GREEN);
            return;
        }
        Ability a = registry.byId(args[4]);
        if (a == null || !a.isCastable()) {
            msg(sender, "Technique inconnue ou non castable : " + args[4], NamedTextColor.RED);
            return;
        }
        if (a.jutsu().itemType() != type) {
            msg(sender, "« " + a.name() + " » appartient au canal "
                    + a.jutsu().itemType().displayName() + ".", NamedTextColor.RED);
            return;
        }
        bindings.set(ref.character().id(), type, slot, a.id());
        msg(sender, a.name() + " liée au slot " + (slot + 1) + " ("
                + type.displayName() + ") de " + ref.character().name() + ".",
                NamedTextColor.GREEN);
    }

    /* --------------------------------------------------------- importspells */

    /**
     * {@code /sa importspells [catégorie] [rang]} — scans MagicSpells'
     * loaded spells and APPENDS an abilities.yml stub for every spell
     * without an entry yet (id convention: {@code ms_<internalname>}).
     * Append-only: existing lines and comments are never touched. The
     * registry reloads at the end so the imports are live immediately.
     */
    private void handleImportSpells(CommandSender sender, String[] args) {
        if (!requirePerm(sender, "shinobiabilities.admin")) return;
        if (!com.reborn.shinobiabilities.util.MagicSpellsHook.isAvailable()) {
            msg(sender, "MagicSpells est introuvable (ou désactivé) sur ce serveur.",
                    NamedTextColor.RED);
            return;
        }
        var spells = com.reborn.shinobiabilities.util.MagicSpellsHook.listSpells();
        if (spells.isEmpty()) {
            msg(sender, "Aucun sort détecté — version de MagicSpells incompatible ?",
                    NamedTextColor.RED);
            return;
        }

        String category = args.length >= 2
                ? args[1].toLowerCase(Locale.ROOT) : "autres/divers";
        com.reborn.shinobicore.technique.JutsuRank rank = args.length >= 3
                ? com.reborn.shinobicore.technique.JutsuRank.from(args[2])
                : com.reborn.shinobicore.technique.JutsuRank.C;
        if (com.reborn.shinobicore.technique.JutsuItemType.forCategory(category) == null) {
            msg(sender, "Attention : la catégorie « " + category
                    + " » n'est routée vers aucun JutsuItem — ces jutsu ne "
                    + "seront pas liables. (autres/divers par défaut.)",
                    NamedTextColor.YELLOW);
        }

        StringBuilder block = new StringBuilder();
        int imported = 0;
        int skipped = 0;
        for (var spell : spells) {
            String id = "ms_" + sanitizeId(spell.internalName());
            if (registry.byId(id) != null) { skipped++; continue; }
            block.append("\n  - id: ").append(id)
                    .append("\n    name: \"").append(escapeYml(spell.displayName())).append('"')
                    .append("\n    category: \"").append(category).append('"')
                    .append("\n    rank: ").append(rank.name())
                    .append("\n    execution: JUTSU")
                    .append("\n    minigame: NONE")
                    .append("\n    difficulty: MEDIUM")
                    .append("\n    description: \"Importé de MagicSpells — à personnaliser.\"")
                    .append("\n    jutsu:")
                    .append("\n      method: LEFT_CLICK")
                    .append("\n      chakra-cost: 50")
                    .append("\n      cooldown-millis: 5000")
                    .append("\n      type: MAGICSPELL")
                    .append("\n      magicspell: \"").append(escapeYml(spell.internalName())).append('"')
                    .append('\n');
            imported++;
        }

        if (imported == 0) {
            msg(sender, "Rien à importer — les " + skipped
                    + " sort(s) MagicSpells ont déjà leur entrée (ids ms_…).",
                    NamedTextColor.YELLOW);
            return;
        }

        java.io.File file = new java.io.File(plugin.getDataFolder(), "abilities.yml");
        String header = "\n  # ─── Importés depuis MagicSpells (/sa importspells) — "
                + java.time.LocalDate.now() + " · catégorie " + category
                + " · rang " + rank.name() + " ───\n";
        try {
            java.nio.file.Files.writeString(file.toPath(), header + block,
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException ex) {
            msg(sender, "Écriture impossible dans abilities.yml : " + ex.getMessage(),
                    NamedTextColor.RED);
            return;
        }

        reloadHook.run();
        msg(sender, imported + " sort(s) importé(s) dans abilities.yml ("
                + skipped + " déjà présents) — registre rechargé : "
                + registry.all().size() + " techniques.", NamedTextColor.GREEN);
        msg(sender, "Affine catégorie / rang / coûts / mudras directement dans "
                + "le fichier, puis /sa reload.", NamedTextColor.GRAY);
    }

    private static String sanitizeId(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (char c : raw.toLowerCase(Locale.ROOT).toCharArray()) {
            out.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') ? c : '_');
        }
        return out.toString();
    }

    private static String escapeYml(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }

    /* ----------------------------------------------------------- cooldowns */

    private void handleCooldowns(CommandSender sender, String[] args) {
        if (!requirePerm(sender, "shinobiabilities.admin")) return;
        if (args.length < 3 || !args[1].equalsIgnoreCase("clear")) {
            msg(sender, "Usage : /sa cooldowns clear <joueur>", NamedTextColor.RED);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            msg(sender, "Joueur hors-ligne : " + args[2], NamedTextColor.RED);
            return;
        }
        cooldowns.clearAll(target.getUniqueId());
        mobility.airTokens().refill(target);
        msg(sender, "Cooldowns et jetons d'air remis à zéro pour "
                + target.getName() + ".", NamedTextColor.GREEN);
    }

    /* ----------------------------------------------------------- abilities */

    private void handleAbilities(CommandSender sender, String[] args) {
        // GUI-first: players browse the Catalogue via /menu. The text
        // dump stays as an admin/console fallback only.
        if (!requirePerm(sender, "shinobiabilities.admin")) return;
        if (args.length >= 2 && args[1].equalsIgnoreCase("info")) {
            if (args.length < 3) {
                msg(sender, "Usage : /sa abilities info <id>", NamedTextColor.RED);
                return;
            }
            Ability a = registry.byId(args[2]);
            if (a == null) {
                msg(sender, "Technique inconnue : " + args[2], NamedTextColor.RED);
                return;
            }
            msg(sender, "— " + a.name() + " (" + a.id() + ") —", NamedTextColor.GOLD);
            msg(sender, "Catégorie " + a.category() + " · Rang "
                    + a.rank().displayName() + " · " + a.execution()
                    + " · minijeu " + a.minigame() + " (" + a.difficulty() + ")",
                    NamedTextColor.GRAY);
            if (a.isCastable()) {
                msg(sender, "Canal " + a.jutsu().itemType().displayName()
                        + " · " + a.jutsu().method()
                        + " · " + (int) a.jutsu().chakraCost() + " chakra · "
                        + (a.jutsu().cooldownMillis() / 1000.0) + "s"
                        + (a.jutsu().effectKey() != null
                                ? " · effet " + a.jutsu().effectKey() : ""),
                        NamedTextColor.DARK_AQUA);
                if (a.jutsu().hasCommands()) {
                    msg(sender, "Commandes (" + (a.jutsu().runAsPlayer()
                                    ? "joueur" : "console") + ") : "
                            + String.join(" ; ", a.jutsu().commands()),
                            NamedTextColor.DARK_AQUA);
                }
            }
            if (!a.description().isBlank()) {
                msg(sender, a.description(), NamedTextColor.GRAY);
            }
            return;
        }
        String prefix = args.length >= 3 ? args[2] : "";
        List<Ability> list = registry.byCategoryPrefix(prefix);
        msg(sender, list.size() + " technique(s)"
                + (prefix.isEmpty() ? "" : " sous « " + prefix + " »") + " :",
                NamedTextColor.GOLD);
        StringBuilder line = new StringBuilder();
        int shown = 0;
        for (Ability a : list) {
            if (shown >= 40) { line.append(" …"); break; }
            if (!line.isEmpty()) line.append(", ");
            line.append(a.id());
            shown++;
        }
        msg(sender, line.toString(), NamedTextColor.GRAY);
    }

    /* ----------------------------------------------------------- parchemin */

    private void handleParchemin(CommandSender sender, String[] args) {
        if (!requirePerm(sender, "shinobiabilities.admin")) return;
        if (args.length < 2) {
            msg(sender, "Usage : /sa parchemin <technique|random> [joueur]", NamedTextColor.RED);
            return;
        }
        Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2])
                : (sender instanceof Player p ? p : null);
        if (target == null) {
            msg(sender, "Cible introuvable (précise un joueur en ligne).", NamedTextColor.RED);
            return;
        }
        ItemStack scroll;
        if (args[1].equalsIgnoreCase("random")) {
            scroll = ParcheminItems.createRandom(registry);
        } else {
            Ability a = registry.byId(args[1]);
            if (a == null) {
                msg(sender, "Technique inconnue : " + args[1], NamedTextColor.RED);
                return;
            }
            scroll = ParcheminItems.create(a);
        }
        if (scroll == null) {
            msg(sender, "Aucune technique chargée.", NamedTextColor.RED);
            return;
        }
        var leftover = target.getInventory().addItem(scroll);
        for (ItemStack stuck : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), stuck);
        }
        msg(sender, "Parchemin remis à " + target.getName() + ".", NamedTextColor.GREEN);
    }

    /* --------------------------------------------------------------- suivi */

    private void handleSuivi(CommandSender sender, String[] args) {
        if (!requirePerm(sender, "shinobiabilities.staff")) return;
        if (args.length < 2) {
            msg(sender, "Usage : /sa " + args[0].toLowerCase(Locale.ROOT)
                    + " <joueur>", NamedTextColor.RED);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg(sender, "Joueur hors-ligne : " + args[1], NamedTextColor.RED);
            return;
        }
        boolean ok = args[0].equalsIgnoreCase("valider")
                ? minigame.validate(target)
                : minigame.refuse(target);
        msg(sender, ok ? "Suivi traité pour " + target.getName() + "."
                        : target.getName() + " n'attend aucune validation.",
                ok ? NamedTextColor.GREEN : NamedTextColor.RED);
    }

    /* ------------------------------------------------------------- toggles */

    private void handleToggles(CommandSender sender, String[] args) {
        if (!requirePerm(sender, "shinobiabilities.admin")) return;
        if (args.length < 2) {
            msg(sender, "Usage : /sa toggles <perso>", NamedTextColor.RED);
            return;
        }
        CharRef ref = findCharacter(args[1]);
        if (ref == null) {
            msg(sender, "Personnage introuvable : " + args[1], NamedTextColor.RED);
            return;
        }
        var unlocked = mobility.toggles().unlockedOf(ref.character().id());
        msg(sender, "Mobilité débloquée pour " + ref.character().name() + " : "
                + (unlocked.isEmpty() ? "(aucune)" : unlocked.toString()),
                NamedTextColor.GOLD);
    }

    /** {@code /sa unlock|lock <perso> <capacité|all>} — grant/revoke a mobility
     *  ability for a character. Abilities are LOCKED by default; this is the
     *  staff path (the player path is the training-ground parkour). */
    private void handleUnlockLock(CommandSender sender, String[] args) {
        if (!requirePerm(sender, "shinobiabilities.admin")) return;
        boolean unlock = args[0].equalsIgnoreCase("unlock");
        if (args.length < 3) {
            msg(sender, "Usage : /sa " + (unlock ? "unlock" : "lock")
                    + " <perso> <capacité|all>", NamedTextColor.RED);
            msg(sender, "  capacités : " + slotNames(), NamedTextColor.GRAY);
            return;
        }
        CharRef ref = findCharacter(args[1]);
        if (ref == null) {
            msg(sender, "Personnage introuvable : " + args[1], NamedTextColor.RED);
            return;
        }
        UUID cid = ref.character().id();
        var store = mobility.toggles();
        if (args[2].equalsIgnoreCase("all")) {
            if (unlock) {
                int n = store.unlockAll(cid, List.of(MobilityActionSlot.values()));
                msg(sender, n + " capacité(s) débloquée(s) pour " + ref.character().name() + ".",
                        NamedTextColor.GREEN);
            } else {
                store.lockAll(cid);
                msg(sender, "Toutes les capacités verrouillées pour " + ref.character().name() + ".",
                        NamedTextColor.GOLD);
            }
            return;
        }
        MobilityActionSlot slot = MobilityActionSlot.from(args[2]);
        if (slot == null) {
            msg(sender, "Capacité inconnue : " + args[2] + " (" + slotNames() + ")",
                    NamedTextColor.RED);
            return;
        }
        boolean changed = unlock ? store.unlock(cid, slot) : store.lock(cid, slot);
        msg(sender, (unlock ? "Débloquée : " : "Verrouillée : ") + slot.displayName()
                + " pour " + ref.character().name()
                + (changed ? "" : " (déjà dans cet état)"),
                changed ? NamedTextColor.GREEN : NamedTextColor.GRAY);
    }

    /** Lower-cased mobility slot names for usage/help text. */
    private static String slotNames() {
        StringBuilder sb = new StringBuilder();
        for (MobilityActionSlot s : MobilityActionSlot.values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s.name().toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private static List<String> slotCompletions() {
        List<String> out = new ArrayList<>();
        for (MobilityActionSlot s : MobilityActionSlot.values())
            out.add(s.name().toLowerCase(Locale.ROOT));
        out.add("all");
        return out;
    }

    private void handleTokens(CommandSender sender, String[] args) {
        if (!requirePerm(sender, "shinobiabilities.admin")) return;
        Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1])
                : (sender instanceof Player p ? p : null);
        if (target == null) {
            msg(sender, "Cible introuvable.", NamedTextColor.RED);
            return;
        }
        msg(sender, target.getName() + " — jetons d'air : "
                + mobility.airTokens().tokens(target)
                + (mobility.airTokens().djLockedOut(target) ? " (lockout DJ armé)" : "")
                + " · sauts muraux utilisés : "
                + mobility.airTokens().wallJumpsUsed(target),
                NamedTextColor.GOLD);
    }

    /* ------------------------------------------------------------ tab */

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("help"));
            if (sender.hasPermission("shinobiabilities.staff")) {
                subs.addAll(List.of("valider", "refuser"));
            }
            if (sender.hasPermission("shinobiabilities.admin")) {
                subs.addAll(List.of("reload", "abilities", "learn", "forget",
                        "learnall", "bind", "cooldowns", "parchemin",
                        "importspells", "toggles", "tokens", "unlock", "lock"));
            }
            return filter(subs, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "learn", "forget", "learnall", "bind", "toggles" -> {
                if (args.length == 2) return filter(characterNames(), args[1]);
            }
            case "unlock", "lock" -> {
                if (args.length == 2) return filter(characterNames(), args[1]);
                if (args.length == 3) return filter(slotCompletions(), args[2]);
            }
            case "valider", "refuser", "tokens" -> {
                if (args.length == 2) return filter(onlineNames(), args[1]);
            }
            case "cooldowns" -> {
                if (args.length == 2) return filter(List.of("clear"), args[1]);
                if (args.length == 3) return filter(onlineNames(), args[2]);
            }
            case "abilities" -> {
                if (args.length == 2) return filter(List.of("list", "info"), args[1]);
                if (args.length == 3 && args[1].equalsIgnoreCase("info")) {
                    return filter(new ArrayList<>(registry.all().keySet()), args[2]);
                }
            }
            case "parchemin" -> {
                if (args.length == 2) {
                    List<String> ids = new ArrayList<>(registry.all().keySet());
                    ids.add(0, "random");
                    return filter(ids, args[1]);
                }
                if (args.length == 3) return filter(onlineNames(), args[2]);
            }
            case "importspells" -> {
                if (args.length == 2) {
                    java.util.TreeSet<String> cats = new java.util.TreeSet<>();
                    cats.add("autres/divers");
                    for (Ability a : registry.all().values()) cats.add(a.category());
                    return filter(new ArrayList<>(cats), args[1]);
                }
                if (args.length == 3) {
                    return filter(List.of("E", "D", "C", "B", "A", "HIDEN"), args[2]);
                }
            }
            default -> { }
        }
        if (sub.equals("learn") || sub.equals("forget")) {
            if (args.length == 3) {
                return filter(new ArrayList<>(registry.all().keySet()), args[2]);
            }
        }
        if (sub.equals("bind")) {
            if (args.length == 3) {
                List<String> types = new ArrayList<>();
                for (JutsuItemType t : JutsuItemType.values()) types.add(t.name());
                return filter(types, args[2]);
            }
            if (args.length == 4) return filter(List.of("1", "2", "3", "4", "5"), args[3]);
            if (args.length == 5) {
                JutsuItemType t = JutsuItemType.from(args[2]);
                List<String> ids = new ArrayList<>(List.of("none"));
                if (t != null) {
                    for (Ability a : registry.castableFor(t)) ids.add(a.id());
                }
                return filter(ids, args[4]);
            }
        }
        return List.of();
    }

    private List<String> characterNames() {
        java.util.TreeSet<String> out =
                new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (List<ShinobiCharacter> roster : core.characters().rosterView().values()) {
            for (ShinobiCharacter c : roster) out.add(c.name());
        }
        return new ArrayList<>(out);
    }

    private List<String> onlineNames() {
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        return out;
    }

    private static List<String> filter(List<String> options, String prefix) {
        String low = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(low)) out.add(o);
        }
        return out;
    }
}
