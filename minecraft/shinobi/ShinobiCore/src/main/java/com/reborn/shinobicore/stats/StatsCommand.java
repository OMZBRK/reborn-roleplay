package com.reborn.shinobicore.stats;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.CharacterService;
import com.reborn.shinobicore.api.StatsService;
import com.reborn.shinobicore.api.StatsService.Stat;
import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /stats} — ouvre la fiche shinobi (mod client) ou l'affiche en chat.
 *
 * <pre>
 *   /stats                              ta fiche
 *   /stats alloc nin=2,ctr=1            dépenser des points (sans le mod)
 *   /stats respec                       tout rendre (un jeton, ou gratuit staff / FREE)
 *   /stats jeton                        ton solde de jetons de réinitialisation
 *   /stats voir &lt;perso&gt;                 fiche d'un autre           (staff)
 *   /stats give &lt;perso&gt; &lt;n&gt;             points bonus (n &lt; 0 retire) (staff)
 *   /stats set &lt;perso&gt; &lt;stat&gt; &lt;valeur&gt;  écrit une stat              (staff)
 *   /stats reset &lt;perso&gt;                respec forcé                (staff)
 *   /stats jeton give|take|set &lt;joueur&gt; &lt;n&gt;  jetons d'un compte   (staff / console)
 * </pre>
 *
 * <p>{@code jeton give} marche aussi joueur hors ligne (compte connu du serveur) :
 * c'est la commande que la boutique exécutera à la livraison.
 */
public final class StatsCommand implements TabExecutor {

    private static final String ADMIN = "shinobicore.stats.admin";

    private final ShinobiCore plugin;
    private final StatsServiceImpl stats;
    private final StatsChannel channel;

    public StatsCommand(ShinobiCore plugin, StatsServiceImpl stats, StatsChannel channel) {
        this.plugin = plugin;
        this.stats = stats;
        this.channel = channel;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> {
                if (!(s instanceof Player p)) { usage(s); return true; }
                ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
                if (c == null) { err(s, "Aucun personnage actif."); return true; }
                // Mod client présent → l'écran ; sinon la fiche en chat.
                if (p.getListeningPluginChannels().contains(StatsChannel.CHANNEL)) channel.push(p, true);
                else printSheet(s, c);
            }
            case "alloc" -> {
                if (!(s instanceof Player p) || args.length < 2) { usage(s); return true; }
                ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
                if (c == null) { err(s, "Aucun personnage actif."); return true; }
                Map<Stat, Integer> deltas = StatsServiceImpl.parseDeltas(String.join("", tail(args, 1)));
                if (deltas == null) { err(s, "Format : /stats alloc nin=2,ctr=1"); return true; }
                StatsService.AllocationResult r = stats.allocate(c.id(), deltas);
                if (r.ok()) ok(s, "Points dépensés — il en reste " + r.unspentAfter() + ".");
                else err(s, r.reason());
            }
            case "respec" -> {
                if (!(s instanceof Player p)) { usage(s); return true; }
                ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
                if (c == null) { err(s, "Aucun personnage actif."); return true; }
                StatsChannel.RespecOutcome r = channel.requestRespec(p, c);
                if (r.ok()) ok(s, r.message());
                else err(s, r.message());
            }
            case "jeton", "jetons", "token" -> jeton(s, args);
            case "voir", "view" -> {
                if (!staff(s) || args.length < 2) return true;
                ShinobiCharacter c = resolve(s, args[1]);
                if (c != null) printSheet(s, c);
            }
            case "give" -> {
                if (!staff(s) || args.length < 3) { usage(s); return true; }
                ShinobiCharacter c = resolve(s, args[1]);
                if (c == null) return true;
                int n;
                try { n = Integer.parseInt(args[2]); } catch (NumberFormatException e) { err(s, "Nombre invalide."); return true; }
                c.stats().setBonusPoints(c.stats().bonusPoints() + n);
                plugin.characters().save(c);
                StatsServiceImpl.pushIfOnline(c);
                ok(s, c.name() + " : bonus " + c.stats().bonusPoints() + ", "
                        + StatFormulas.unspent(c.rank(), c.stats()) + " point(s) à répartir.");
            }
            case "set" -> {
                if (!staff(s) || args.length < 4) { usage(s); return true; }
                ShinobiCharacter c = resolve(s, args[1]);
                if (c == null) return true;
                Stat st = Stat.from(args[2]);
                if (st == null) { err(s, "Stat inconnue : " + args[2]); return true; }
                int v;
                try { v = Integer.parseInt(args[3]); } catch (NumberFormatException e) { err(s, "Nombre invalide."); return true; }
                stats.setDirect(c, st, v);
                ok(s, c.name() + " : " + st.displayName() + " = " + c.stats().get(st)
                        + " (hors budget de points — pense au bonus si besoin).");
            }
            case "reset" -> {
                if (!staff(s) || args.length < 2) { usage(s); return true; }
                ShinobiCharacter c = resolve(s, args[1]);
                if (c == null) return true;
                stats.respec(c.id());
                ok(s, c.name() + " : stats remises à " + StatsService.MIN + ".");
            }
            default -> usage(s);
        }
        return true;
    }

    /* ---------------------------------------------------------- display */

    private void printSheet(CommandSender s, ShinobiCharacter c) {
        CharacterStats st = c.stats();
        int unspent = StatFormulas.unspent(c.rank(), st);
        s.sendMessage(Component.text("━━ Fiche de " + c.name() + " — " + c.rank().displayName()
                + " ━━", NamedTextColor.GOLD));
        for (Stat stat : Stat.values()) {
            int v = st.get(stat);
            String bar = "■".repeat(v) + "□".repeat(StatsService.MAX - v);
            s.sendMessage(Component.text(String.format(Locale.ROOT, "  %-9s ", stat.displayName()), NamedTextColor.GRAY)
                    .append(Component.text(bar + " ", NamedTextColor.AQUA))
                    .append(Component.text(v + (v > StatFormulas.levers().softCap()
                            ? "  (eff. " + trim(StatFormulas.eff(v)) + ")" : ""), NamedTextColor.WHITE)));
        }
        s.sendMessage(Component.text(String.format(Locale.ROOT,
                "  PV %d · Chakra %d · Endurance %d · Régén %d/10 s · Coûts −%d %%",
                Math.round(StatFormulas.maxHp(st)), Math.round(StatFormulas.maxChakra(st)),
                Math.round(StatFormulas.maxStamina(st)), Math.round(StatFormulas.chakraRegenPer10s(st)),
                Math.round(StatFormulas.costReduction(st) * 100)), NamedTextColor.DARK_AQUA));
        s.sendMessage(Component.text("  Points à répartir : " + Math.max(0, unspent)
                + (unspent < 0 ? " (sur-alloué de " + (-unspent) + ")" : ""),
                unspent > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY));
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? Long.toString((long) d) : String.format(Locale.ROOT, "%.1f", d);
    }

    /* ---------------------------------------------------------- helpers */

    private boolean staff(CommandSender s) {
        if (s.hasPermission(ADMIN)) return true;
        err(s, "Réservé au staff.");
        return false;
    }

    /** {@code /stats jeton} (solde) · {@code /stats jeton give|take|set <joueur> <n>} (staff / console). */
    private void jeton(CommandSender s, String[] args) {
        RespecTokens tokens = channel.tokens();
        if (args.length == 1) {
            if (!(s instanceof Player p)) { usage(s); return; }
            int n = tokens.get(p.getUniqueId());
            s.sendMessage(Component.text("Jetons de réinitialisation : " + n
                    + (n == 0 ? " — disponibles en boutique." : ""), NamedTextColor.GOLD));
            return;
        }
        if (!s.hasPermission(ADMIN) || args.length < 4) { usage(s); return; }
        String op = args[1].toLowerCase(Locale.ROOT);
        OfflinePlayer target = resolvePlayer(args[2]);
        if (target == null) { err(s, "Joueur inconnu du serveur : " + args[2]); return; }
        int n;
        try { n = Integer.parseInt(args[3]); } catch (NumberFormatException e) { err(s, "Nombre invalide."); return; }
        int now;
        switch (op) {
            case "give" -> now = tokens.add(target.getUniqueId(), n);
            case "take" -> now = tokens.add(target.getUniqueId(), -n);
            case "set" -> now = tokens.set(target.getUniqueId(), n);
            default -> { usage(s); return; }
        }
        String who = target.getName() != null ? target.getName() : target.getUniqueId().toString();
        ok(s, who + " : " + now + " jeton(s) de réinitialisation.");
        Player online = target.getPlayer();
        if (online != null) {
            if (op.equals("give") && n > 0) {
                online.sendMessage(Component.text("Tu as reçu " + n + " jeton" + (n > 1 ? "s" : "")
                        + " de réinitialisation des stats (" + now + " au total).", NamedTextColor.GOLD));
            }
            channel.push(online, false);
        }
    }

    /** Joueur en ligne, sinon compte déjà vu par le serveur, sinon UUID brut. */
    private static OfflinePlayer resolvePlayer(String arg) {
        Player p = Bukkit.getPlayerExact(arg);
        if (p != null) return p;
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(arg);
        if (cached != null) return cached;
        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(arg));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ShinobiCharacter resolve(CommandSender s, String name) {
        CharacterService.ResolvedCharacter r = plugin.characters().resolveOwned(name);
        if (r == null) { err(s, "Personnage introuvable : " + name); return null; }
        return r.character();
    }

    private static String[] tail(String[] a, int from) {
        String[] out = new String[Math.max(0, a.length - from)];
        System.arraycopy(a, from, out, 0, out.length);
        return out;
    }

    private void usage(CommandSender s) {
        s.sendMessage(Component.text("/stats · /stats alloc nin=2,ctr=1 · /stats respec · /stats jeton", NamedTextColor.GRAY));
        if (s.hasPermission(ADMIN)) {
            s.sendMessage(Component.text("/stats voir|reset <perso> · /stats give <perso> <n> · "
                    + "/stats set <perso> <stat> <valeur> · /stats jeton give|take|set <joueur> <n>",
                    NamedTextColor.GRAY));
        }
    }

    private static void ok(CommandSender s, String m) { s.sendMessage(Component.text(m, NamedTextColor.GREEN)); }

    private static void err(CommandSender s, String m) { s.sendMessage(Component.text(m, NamedTextColor.RED)); }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command cmd,
                                      @NotNull String label, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = s.hasPermission(ADMIN);
        if (args.length == 1) {
            out.addAll(List.of("alloc", "respec", "jeton"));
            if (admin) out.addAll(List.of("voir", "give", "set", "reset"));
        } else if (args.length == 2 && admin && List.of("voir", "view", "give", "set", "reset")
                .contains(args[0].toLowerCase(Locale.ROOT))) {
            out.addAll(plugin.characters().allCharacterNames());
        } else if (args.length == 2 && admin && args[0].equalsIgnoreCase("jeton")) {
            out.addAll(List.of("give", "take", "set"));
        } else if (args.length == 3 && admin && args[0].equalsIgnoreCase("jeton")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("alloc")) {
            for (Stat st : Stat.values()) out.add(st.key() + "=1");
        } else if (args.length == 3 && admin && args[0].equalsIgnoreCase("set")) {
            for (Stat st : Stat.values()) out.add(st.key());
        }
        String cur = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(cur));
        return out;
    }
}
