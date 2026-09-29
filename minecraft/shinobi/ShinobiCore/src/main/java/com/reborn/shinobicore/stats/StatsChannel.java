package com.reborn.shinobicore.stats;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.StatsService;
import com.reborn.shinobicore.api.StatsService.Grade;
import com.reborn.shinobicore.api.StatsService.Stat;
import com.reborn.shinobicore.character.ChakraAffinity;
import com.reborn.shinobicore.character.Rank;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.technique.Ability;
import com.reborn.shinobicore.technique.TechniqueProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Canal {@code reborn:stats} (bidirectionnel) — la <b>fiche shinobi</b> du mod
 * client. Même forme que {@code reborn:inventory} : octets UTF-8 bruts, la taille
 * est portée par le packet custom payload.
 *
 * <p>S2C = un JSON : stats, points, rang, <b>leviers</b> des formules et
 * techniques connues avec leur profil de scaling. Le client recalcule les
 * valeurs dérivées avec les mêmes leviers pour prévisualiser une allocation
 * avant de la valider — le serveur reste seul juge ({@link StatsService#allocate}).
 *
 * <p>C2S : {@code open} · {@code alloc:taijutsu=1,ninjutsu=2} · {@code respec}
 * (gratuit pour le staff, sinon un jeton — {@link RespecTokens}).
 */
public final class StatsChannel implements PluginMessageListener {

    public static final String CHANNEL = "reborn:stats";

    private static final String ADMIN = "shinobicore.stats.admin";

    private final ShinobiCore plugin;
    private final StatsServiceImpl stats;
    private final RespecTokens tokens;

    public StatsChannel(ShinobiCore plugin, StatsServiceImpl stats, RespecTokens tokens) {
        this.plugin = plugin;
        this.stats = stats;
        this.tokens = tokens;
        stats.bindChannel(this);
    }

    public RespecTokens tokens() { return tokens; }

    public void start() {
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL)) m.registerOutgoingPluginChannel(plugin, CHANNEL);
        if (!m.isIncomingChannelRegistered(plugin, CHANNEL)) m.registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    /** Respec gratuit : staff, ou mode FREE (phase de test). */
    private boolean respecFree(Player p) {
        return p.hasPermission(ADMIN) || tokens.mode() == RespecTokens.Mode.FREE;
    }

    /** Résultat d'une demande de respec par le joueur lui-même. */
    public record RespecOutcome(boolean ok, String message) {}

    /**
     * Respec demandé par le joueur (fiche ou {@code /stats respec}). Gratuit pour le
     * staff et en mode FREE ; en mode TOKEN, consomme un jeton du compte ; en mode
     * STAFF, refusé. Rien de dépensé → refusé sans consommer de jeton.
     */
    public RespecOutcome requestRespec(Player p, ShinobiCharacter c) {
        if (c.stats().spent() <= 0) {
            return new RespecOutcome(false, "Aucun point à reprendre : tes stats sont déjà à la base.");
        }
        if (!respecFree(p)) {
            if (tokens.mode() == RespecTokens.Mode.STAFF) {
                return new RespecOutcome(false, "La réinitialisation passe par le staff.");
            }
            if (!tokens.tryConsume(p.getUniqueId())) {
                return new RespecOutcome(false, "Il te faut un jeton de réinitialisation (boutique).");
            }
            stats.respec(c.id());
            int left = tokens.get(p.getUniqueId());
            return new RespecOutcome(true, "Stats réinitialisées — 1 jeton utilisé, "
                    + left + " restant" + (left > 1 ? "s" : "") + ".");
        }
        stats.respec(c.id());
        return new RespecOutcome(true, "Stats réinitialisées — tous les points sont rendus.");
    }

    /* ------------------------------------------------------------ inbound */

    @Override
    public void onPluginMessageReceived(String channel, Player p, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        String msg = new String(message, StandardCharsets.UTF_8).trim();
        Bukkit.getScheduler().runTask(plugin, () -> handle(p, msg));
    }

    private void handle(Player p, String msg) {
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        if (c == null) {
            p.sendActionBar(Component.text("Aucun personnage actif.", NamedTextColor.RED));
            return;
        }
        if (msg.equals("open")) {
            push(p, true);
        } else if (msg.startsWith("alloc:")) {
            Map<Stat, Integer> deltas = StatsServiceImpl.parseDeltas(msg.substring(6));
            if (deltas == null) return;
            StatsService.AllocationResult r = stats.allocate(c.id(), deltas);
            if (!r.ok()) {
                p.sendActionBar(Component.text(r.reason(), NamedTextColor.RED));
                push(p, false);
            } else {
                p.sendActionBar(Component.text("Stats mises à jour — " + r.unspentAfter()
                        + " point(s) restant(s).", NamedTextColor.GOLD));
            }
        } else if (msg.equals("respec")) {
            RespecOutcome r = requestRespec(p, c);
            p.sendActionBar(Component.text(r.message(), r.ok() ? NamedTextColor.GOLD : NamedTextColor.RED));
            if (!r.ok()) push(p, false);
        }
    }

    /* ----------------------------------------------------------- outbound */

    /** Send the sheet. {@code open} asks the client to open the screen. */
    public void push(Player p, boolean open) {
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        if (c == null) return;
        boolean free = respecFree(p);
        boolean canRespec = free || tokens.mode() == RespecTokens.Mode.TOKEN;
        byte[] bytes = buildJson(c, open, canRespec, free, tokens.get(p.getUniqueId()))
                .getBytes(StandardCharsets.UTF_8);
        try {
            p.sendPluginMessage(plugin, CHANNEL, bytes);
        } catch (Exception ignored) {
            // pas de mod-hud → la commande /stats affiche la fiche en chat.
        }
    }

    String buildJson(ShinobiCharacter c, boolean open, boolean canRespec, boolean respecFree, int respecTokens) {
        StatFormulas.Levers l = StatFormulas.levers();
        StringBuilder sb = new StringBuilder(2048);
        sb.append('{');
        sb.append("\"open\":").append(open);
        str(sb, "name", c.name());
        str(sb, "clan", c.clan());
        str(sb, "village", c.village());
        str(sb, "rank", c.rank().displayName());
        sb.append(",\"rankTier\":").append(c.rank().statTier());
        Rank next = nextStatRank(c.rank());
        str(sb, "nextRank", next == null ? "" : next.displayName());
        sb.append(",\"natures\":[");
        boolean first = true;
        for (ChakraAffinity a : c.chakraAffinities()) {
            if (!first) sb.append(',');
            sb.append('"').append(a.name()).append('"');
            first = false;
        }
        sb.append(']');
        sb.append(",\"min\":").append(StatsService.MIN);
        sb.append(",\"max\":").append(StatsService.MAX);
        sb.append(",\"earned\":").append(StatFormulas.earnedPoints(c.rank(), c.stats()));
        sb.append(",\"bonus\":").append(c.stats().bonusPoints());
        sb.append(",\"canRespec\":").append(canRespec);
        sb.append(",\"respecFree\":").append(respecFree);
        sb.append(",\"respecTokens\":").append(respecTokens);

        sb.append(",\"stats\":{");
        first = true;
        for (Stat s : Stat.values()) {
            if (!first) sb.append(',');
            sb.append('"').append(s.key()).append("\":").append(c.stats().get(s));
            first = false;
        }
        sb.append('}');

        sb.append(",\"levers\":{");
        sb.append("\"pointsPerRank\":").append(l.pointsPerRank());
        num(sb, "softCap", l.softCap());
        num(sb, "overFactor", l.overCapFactor());
        num(sb, "hpBase", l.hpBase());
        num(sb, "hpPerVig", l.hpPerVigueur());
        num(sb, "chakraBase", l.chakraBase());
        num(sb, "chakraPerPoint", l.chakraPerPoint());
        num(sb, "stBase", l.staminaBase());
        num(sb, "stPerVig", l.staminaPerVigueur());
        num(sb, "stPerTai", l.staminaPerTaijutsu());
        num(sb, "regenBase", l.regenBase());
        num(sb, "regenPerCtrl", l.regenPerControle());
        num(sb, "costRedPerCtrl", l.costReductionPerControle());
        num(sb, "meleePerPoint", l.meleePerPoint());
        sb.append(",\"critEnabled\":").append(l.critEnabled());
        num(sb, "critBase", l.critBase());
        num(sb, "critPerCtrl", l.critPerControle());
        sb.append(",\"weights\":{");
        first = true;
        for (Grade g : Grade.values()) {
            if (!first) sb.append(',');
            sb.append('"').append(g.name()).append("\":").append(fmt(l.gradeWeights().getOrDefault(g, 0.0)));
            first = false;
        }
        sb.append('}');
        arr(sb, "chakraCost", l.chakraCostByTier());
        arr(sb, "staminaCost", l.staminaCostByTier());
        sb.append('}');

        sb.append(",\"techniques\":[");
        first = true;
        for (Ability a : knownTechniques(c)) {
            TechniqueProfile tp = a.profile();
            if (!first) sb.append(',');
            first = false;
            sb.append('{');
            sb.append("\"id\":\"").append(esc(a.id())).append('"');
            str(sb, "name", a.name());
            str(sb, "rank", a.rank().displayName());
            sb.append(",\"tier\":").append(tp.tier());
            str(sb, "cat", a.category());
            str(sb, "nature", tp.nature().name());
            str(sb, "kind", tp.costKind().name());
            num(sb, "factor", tp.costFactor());
            sb.append(",\"explicit\":").append(tp.explicitScaling());
            sb.append(",\"castable\":").append(a.isCastable());
            sb.append(",\"mastery\":").append(c.abilityMastery(a.id()));
            sb.append(",\"scaling\":{");
            boolean f2 = true;
            for (Map.Entry<Stat, Grade> e : tp.scaling().entrySet()) {
                if (!f2) sb.append(',');
                sb.append('"').append(e.getKey().key()).append("\":\"").append(e.getValue().name()).append('"');
                f2 = false;
            }
            sb.append("}}");
        }
        sb.append(']');
        sb.append('}');
        return sb.toString();
    }

    /** Known techniques, strongest rank first then by name. */
    private List<Ability> knownTechniques(ShinobiCharacter c) {
        List<Ability> out = new ArrayList<>();
        var reg = plugin.techniques();
        if (reg == null) return out;
        for (String id : c.knownAbilities()) {
            Ability a = reg.byId(id);
            if (a != null) out.add(a);
        }
        out.sort(Comparator.comparingInt((Ability a) -> -a.profile().tier())
                .thenComparing(a -> a.name().toLowerCase(Locale.ROOT)));
        return out;
    }

    /** The next rank that grants points, or null at the top of the ladder. */
    static Rank nextStatRank(Rank r) {
        for (Rank n : Rank.values()) {
            if (n.ordinal() > r.ordinal() && n.statTier() > r.statTier()) return n;
        }
        return null;
    }

    /* --------------------------------------------------------------- json */

    private static void str(StringBuilder sb, String k, String v) {
        sb.append(",\"").append(k).append("\":\"").append(esc(v == null ? "" : v)).append('"');
    }

    private static void num(StringBuilder sb, String k, double v) {
        sb.append(",\"").append(k).append("\":").append(fmt(v));
    }

    private static void arr(StringBuilder sb, String k, double[] v) {
        sb.append(",\"").append(k).append("\":[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(fmt(v[i]));
        }
        sb.append(']');
    }

    private static String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "0";
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }

    private static String esc(String s) {
        StringBuilder o = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> o.append("\\\"");
                case '\\' -> o.append("\\\\");
                case '\n' -> o.append("\\n");
                case '\r', '\t' -> o.append(' ');
                default -> {
                    if (ch < 0x20) o.append(' ');
                    else o.append(ch);
                }
            }
        }
        return o.toString();
    }
}
