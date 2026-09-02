package com.reborn.shinobicore.lore;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.CharacterDisplay;
import com.reborn.shinobicore.character.Clan;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.util.Tps;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Custom player tab list — single compact vertical column.
 *
 * <p>Earlier iterations used a two-column pixel-padded layout that
 * looked good on 16:9 monitors but broke on narrow screens, ultrawides,
 * or non-default GUI scales. The server can't actually detect the
 * viewer's screen, so we ship one tight vertical layout that fits
 * every aspect ratio:
 *
 * <pre>
 *   Naruto Uzumaki [Senju]
 *   Konoha · Pays du Feu
 *   Year 495 · M6 D25
 *
 *   [empty native roster — players hidden]
 *
 *   — Staff (2)
 *     Reborn
 *     _Konoha
 *   — Amis (3)
 *     Hashirama
 *     Tobirama
 *     Sasuke
 * </pre>
 *
 * <h2>Why this shape</h2>
 * <ul>
 *   <li>Every line is short (<25 visual chars) so even a 4:3 or
 *       portrait-mode client renders the panel cleanly.</li>
 *   <li>No pixel-padding maths — Minecraft's variable-width font is
 *       only ever used for short prose, never for column alignment,
 *       so GUI scale changes can't smear the layout.</li>
 *   <li>Roster (the per-player slots between header and footer) is
 *       hidden via {@link Player#setListed(boolean)} so the panel reads
 *       as a single info block. Friend visibility is computed per-
 *       viewer at render time from the active character's friend set.</li>
 * </ul>
 *
 * <h2>Render cadence</h2>
 * Re-rendered every {@code tablist.refresh-ticks} ticks. The tick is
 * TPS-aware via {@link Tps#shouldDefer()} — if the server is already
 * lagging, the round is skipped and content stays on the previous
 * frame until TPS recovers.
 */
public class TabListManager implements Listener {

    /** Canal S2C qui nourrit le tablist custom du mod client Reborn (mod-hud).
     *  Le mod remplace le tablist vanilla par un panneau par-viewer ; le serveur
     *  reste seul autoritaire sur les données (relations, grade, clan…). Les
     *  clients sans le mod ignorent le canal et gardent le header/footer texte. */
    public static final String CLIENT_CHANNEL = "reborn:tablist";

    private final ShinobiCore plugin;
    private final LoreCalendar calendar;

    private BukkitTask ticker;
    private boolean enabled;

    /** World-name → (country, city). Keys are lowercased. */
    private final Map<String, WorldPlace> worldPlaces = new HashMap<>();
    private WorldPlace defaultPlace = new WorldPlace("Pays du Feu", "Konoha");

    public TabListManager(ShinobiCore plugin, LoreCalendar calendar) {
        this.plugin = plugin;
        this.calendar = calendar;
    }

    public void start() {
        if (!Bukkit.getMessenger().isOutgoingChannelRegistered(plugin, CLIENT_CHANNEL)) {
            Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CLIENT_CHANNEL);
        }
        reloadConfig();
        for (Player p : Bukkit.getOnlinePlayers()) setListed(p, false);
    }

    public void reloadConfig() {
        this.enabled = plugin.getConfig().getBoolean("tablist.enabled", true);
        long refresh = Math.max(1L, plugin.getConfig().getLong("tablist.refresh-ticks", 40L));

        // Re-read world overrides.
        worldPlaces.clear();
        String defCountry = plugin.getConfig().getString("tablist.default.country", "Pays du Feu");
        String defCity    = plugin.getConfig().getString("tablist.default.city",    "Konoha");
        defaultPlace = new WorldPlace(defCountry, defCity);

        ConfigurationSection worlds = plugin.getConfig().getConfigurationSection("tablist.worlds");
        if (worlds != null) {
            for (String key : worlds.getKeys(false)) {
                String country = worlds.getString(key + ".country", defCountry);
                String city    = worlds.getString(key + ".city",    defCity);
                worldPlaces.put(key.toLowerCase(), new WorldPlace(country, city));
            }
        }

        if (ticker != null) { ticker.cancel(); ticker = null; }
        if (!enabled) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
            }
            return;
        }
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, refresh);
        // Fire one render immediately so reload doesn't leave the
        // previous frame frozen on screen for refresh-ticks ticks.
        for (Player p : Bukkit.getOnlinePlayers()) render(p);
    }

    public void stop() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
        }
    }

    /* ----------------------------------------------------------- listener */

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!enabled) return;
        setListed(e.getPlayer(), false);
        // One-shot render so the joining player sees the panel
        // immediately + every existing player gets refreshed so their
        // friend list picks up the new arrival.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            render(e.getPlayer());
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.getUniqueId().equals(e.getPlayer().getUniqueId())) render(other);
            }
        }, 1L);
    }

    /** Force an immediate repaint for {@code viewer}. Called by
     *  friendship-edge flips and character swaps so the friend list
     *  reflects the new state instead of waiting for the next tick. */
    public void refresh(Player viewer) {
        if (!enabled || viewer == null || !viewer.isOnline()) return;
        setListed(viewer, false);
        render(viewer);
    }

    /* --------------------------------------------------------------- tick */

    private void tick() {
        // TPS-aware deferral — the tab is purely cosmetic, so during
        // lag spikes we'd rather drop a frame than pile work onto a
        // stressed tick.
        if (Tps.shouldDefer()) return;
        for (Player p : Bukkit.getOnlinePlayers()) render(p);
    }

    private void render(Player p) {
        WorldPlace place = placeFor(p.getWorld().getName());
        ShinobiCharacter c = plugin.characters().getActiveOrLastSeen(p.getUniqueId());

        Component header = buildHeader(place, c);
        Component footer = buildFooter(p, c);
        p.sendPlayerListHeaderAndFooter(header, footer);

        // Nourrit le tablist custom du mod client (ignoré si le client ne l'a pas).
        pushClientFeed(p, c);
    }

    /* ------------------------------------------------ client feed (mod-hud) */

    /**
     * Envoie au client {@code viewer} sa vue personnalisée du tablist Reborn en
     * JSON sur {@link #CLIENT_CHANNEL}. Pour chaque joueur en ligne visible :
     * relation vue par le viewer (SOI/AMI/CONNAISSANCE/INCONNU), flag staff, et
     * — uniquement si le viewer a le droit de voir l'identité RP (connu, ou
     * staff OOC-visible) — le vrai nom, grade, clan (+couleur), niveau, âge,
     * affinité. Un inconnu ne révèle rien (« Inconnu (Pas RP) »). Même modèle de
     * connaissance que le footer, donc pas de fuite d'info RP.
     */
    private void pushClientFeed(Player viewer, ShinobiCharacter viewerChar) {
        Set<UUID> friendIds = viewerChar != null ? viewerChar.friends() : Set.of();
        UUID viewerId = viewer.getUniqueId();

        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"date\":\"").append(esc(calendar.formatted())).append("\",\"players\":[");

        boolean first = true;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (plugin.vanish() != null && !plugin.vanish().canSee(viewer, other)) continue;

            ShinobiCharacter oc = plugin.characters().getActiveOrLastSeen(other.getUniqueId());
            boolean self  = other.getUniqueId().equals(viewerId);
            boolean staff = other.hasPermission("shinobicore.staff");

            boolean isFriend = oc != null && friendIds.contains(oc.id());
            boolean isKnown  = oc != null && viewerChar != null && viewerChar.knows(oc.id());
            String relation = self ? "SOI" : isFriend ? "AMI" : isKnown ? "CONNAISSANCE" : "INCONNU";

            // On ne révèle l'identité RP (nom, grade, clan…) que si le viewer
            // connaît vraiment le perso (rencontré) ou que c'est lui-même. Le
            // staff NE fait PAS exception : un staff inconnu reste inconnu.
            boolean reveal = self || isKnown;

            // Nom affiché : surnom si le viewer l'a enregistré comme tel, sinon
            // le vrai « Prénom Clan » (sans « None » quand il n'y a pas de clan),
            // sinon « Inconnu (Pas RP) ».
            String displayName;
            if (reveal && oc != null) {
                ShinobiCharacter.KnownName kn = self ? null : viewerChar.knownNameOf(oc.id());
                displayName = (kn != null && kn.isNickname()) ? kn.display() : cleanRealName(oc);
            } else if (self) {
                displayName = other.getName();
            } else {
                displayName = "Inconnu (Pas RP)";
            }

            if (!first) sb.append(',');
            first = false;
            sb.append('{');
            sb.append("\"u\":\"").append(other.getUniqueId()).append('"');
            sb.append(",\"r\":\"").append(relation).append('"');
            // Le staff reste TOUJOURS listé dans l'onglet Staff (flag envoyé),
            // même quand son nom RP est masqué (« Inconnu (Pas RP) ») parce que
            // le viewer ne l'a pas rencontré. On sait qu'un staff est en ligne,
            // sans connaître son identité RP.
            sb.append(",\"s\":").append(staff);
            sb.append(",\"n\":\"").append(esc(displayName)).append('"');
            if (reveal && oc != null) {
                sb.append(",\"g\":\"").append(oc.rank() != null ? oc.rank().name() : "GENIN").append('"');
                String clan = oc.clan();
                if (clan != null && !clan.isBlank() && !clan.equalsIgnoreCase("None")) {
                    sb.append(",\"c\":\"").append(esc(clan)).append('"');
                    NamedTextColor col = Clan.colourFor(clan);
                    if (col != null) sb.append(",\"cc\":").append(0xFF000000 | col.value());
                }
                sb.append(",\"lv\":").append(oc.level());
                sb.append(",\"a\":").append(oc.age());
                sb.append(",\"af\":\"").append(oc.affinity() != null ? oc.affinity().name() : "").append('"');
            } else {
                sb.append(",\"g\":\"???\"");
            }
            sb.append('}');
        }
        sb.append(']');
        // Stats perso du viewer (chakra) pour le panneau joueur du mod client
        // (canal reborn:tablist réutilisé). La vie/faim restent vanilla côté client.
        if (viewerChar != null && viewerChar.chakra() != null) {
            sb.append(",\"self\":{\"hp\":").append(Math.round(viewerChar.currentHp()))
              .append(",\"mhp\":").append(Math.round(viewerChar.maxHp()))
              .append(",\"ck\":").append(Math.round(viewerChar.chakra().current()))
              .append(",\"mck\":").append(Math.round(viewerChar.chakra().max())).append('}');
        }
        sb.append('}');

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        try {
            viewer.sendPluginMessage(plugin, CLIENT_CHANNEL, bytes);
        } catch (Exception ignore) {
            // Canal non enregistré / joueur parti entre-temps : purement cosmétique.
        }
    }

    /** « Prénom Clan » propre : le clan n'est ajouté que s'il est réel (pas
     *  null/vide/« None »), sinon on ne renvoie que le prénom. */
    private static String cleanRealName(ShinobiCharacter oc) {
        String name = oc.name() == null ? "" : oc.name();
        String clan = oc.clan();
        if (clan != null && !clan.isBlank() && !clan.equalsIgnoreCase("None")) {
            return (name + " " + clan).trim();
        }
        return name.trim();
    }

    /** Échappe une chaîne pour insertion dans une string JSON (pas de dépendance). */
    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default   -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.toString();
    }

    /* ---------------------------------------------------------- header */

    /** Three short lines: identity, place, lore date. No padding,
     *  no centering — Minecraft auto-centers each line of header and
     *  footer relative to the tab list, so a left-aligned narrow
     *  string lands centered in any aspect ratio. */
    private Component buildHeader(WorldPlace place, ShinobiCharacter c) {
        Component identity = c == null
                ? Component.text("Aucun personnage actif",
                        NamedTextColor.DARK_GRAY, TextDecoration.ITALIC)
                : CharacterDisplay.styledName(c);

        Component where = Component.text(place.city + " · " + place.country,
                NamedTextColor.YELLOW);

        Component date = Component.text(calendar.formatted(),
                NamedTextColor.GOLD, TextDecoration.BOLD);

        return Component.empty()
                .append(Component.newline())
                .append(identity)
                .append(Component.newline())
                .append(where)
                .append(Component.newline())
                .append(date)
                .append(Component.newline());
    }

    /* ---------------------------------------------------------- footer */

    /** Two stacked sections: staff online, then friends online. Each
     *  section opens with a header line ({@code — Staff (2)}) and
     *  lists members on indented lines below. Empty sections collapse
     *  to a single italic placeholder. */
    private Component buildFooter(Player viewer, ShinobiCharacter viewerChar) {
        Component out = Component.empty().append(Component.newline());

        // ---- Staff ----
        List<Player> staff = onlineStaff();
        staff.removeIf(s -> plugin.vanish() != null && !plugin.vanish().canSee(viewer, s));
        out = out.append(sectionHeader("Staff", staff.size(), NamedTextColor.LIGHT_PURPLE));
        if (staff.isEmpty()) {
            out = out.append(Component.newline())
                     .append(placeholder("aucun staff en ligne"));
        } else {
            for (Player s : staff) {
                out = out.append(Component.newline())
                         .append(memberLine(s.getName(), NamedTextColor.LIGHT_PURPLE));
            }
        }

        out = out.append(Component.newline()).append(Component.newline());

        // One pass over online players classifies each as friend / met /
        // inconnu, so the footer does N lookups per viewer instead of 2N.
        FooterLists rel = relationLines(viewer, viewerChar);

        // ---- Friends ----
        List<Component> friends = rel.friends();
        out = out.append(sectionHeader("Amis", friends.size(), NamedTextColor.AQUA));
        if (friends.isEmpty()) {
            out = out.append(Component.newline())
                     .append(placeholder("aucun ami en ligne"));
        } else {
            for (Component f : friends) {
                out = out.append(Component.newline())
                         .append(Component.text("  ").append(f));
            }
        }

        out = out.append(Component.newline()).append(Component.newline());

        // ---- Connaissances (rencontrés, hors amis) ----
        // Only characters the viewer has met (contact book) appear here;
        // an inconnu is never listed, so the tab shows people you know.
        List<Component> met = rel.met();
        out = out.append(sectionHeader("Connaissances", met.size(), NamedTextColor.GREEN));
        if (met.isEmpty()) {
            out = out.append(Component.newline())
                     .append(placeholder("personne de connu en ligne"));
        } else {
            for (Component m : met) {
                out = out.append(Component.newline())
                         .append(Component.text("  ").append(m));
            }
        }
        return out;
    }

    /** {@code — Label (n)} — section title with member count. */
    private static Component sectionHeader(String label, int count, NamedTextColor color) {
        return Component.text("— ", NamedTextColor.DARK_GRAY)
                .append(Component.text(label, color, TextDecoration.BOLD))
                .append(Component.text(" (" + count + ")",
                        NamedTextColor.DARK_GRAY));
    }

    /** Indented "  Name" line in {@code color}. */
    private static Component memberLine(String name, NamedTextColor color) {
        return Component.text("  ", NamedTextColor.GRAY)
                .append(Component.text(name, color));
    }

    /** Indented italic placeholder for empty sections. */
    private static Component placeholder(String text) {
        return Component.text("  ", NamedTextColor.GRAY)
                .append(Component.text(text, NamedTextColor.DARK_GRAY,
                        TextDecoration.ITALIC));
    }

    /* ---------------------------------------------------- collectors */

    /** Online players with the {@code shinobicore.staff} permission,
     *  sorted by Mojang username for stable ordering between renders. */
    private List<Player> onlineStaff() {
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("shinobicore.staff")) out.add(p);
        }
        out.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return out;
    }

    /** One pass over online players, classifying each relative to the
     *  viewer: friends, then "met" (rencontré, in the contact book but not
     *  a friend). Inconnu (never met) appear in neither, so the tab only
     *  ever shows people the viewer knows. Single iteration → N lookups
     *  per viewer instead of 2N. The viewer themselves is never included —
     *  their identity is already in the header. */
    private FooterLists relationLines(Player viewer, ShinobiCharacter viewerChar) {
        List<Component> friends = new ArrayList<>();
        List<Component> met = new ArrayList<>();
        if (viewerChar == null) return new FooterLists(friends, met);
        Set<UUID> friendIds = viewerChar.friends();
        UUID viewerId = viewer.getUniqueId();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(viewerId)) continue;
            if (plugin.vanish() != null && !plugin.vanish().canSee(viewer, other)) continue;
            ShinobiCharacter otherChar =
                    plugin.characters().getActiveOrLastSeen(other.getUniqueId());
            if (otherChar == null) continue;
            if (friendIds.contains(otherChar.id())) {
                friends.add(CharacterDisplay.styledNameFor(otherChar, viewerChar));
            } else if (viewerChar.knows(otherChar.id())) {
                met.add(CharacterDisplay.styledNameFor(otherChar, viewerChar));
            }
            // inconnu (neither friend nor known) → listed nowhere
        }
        return new FooterLists(friends, met);
    }

    /** Friends + met lines computed in a single roster pass. */
    private record FooterLists(List<Component> friends, List<Component> met) {}

    /* ---------------------------------------------------- world places */

    private WorldPlace placeFor(String worldName) {
        WorldPlace p = worldPlaces.get(worldName.toLowerCase());
        return p != null ? p : defaultPlace;
    }

    private record WorldPlace(String country, String city) { }

    /* --------------------------------------------------- setListed shim */

    /**
     * Paper added {@code Player#setListed(boolean)} to hide a player
     * from the vanilla tab-list roster. Older paper-api jars on the
     * build path don't expose the method, so we resolve it
     * reflectively — compiles against any API, works at runtime on
     * Paper builds that ship the method. Resolution is done once per
     * process; subsequent calls are cheap.
     */
    private static volatile Method SET_LISTED;
    private static volatile boolean SET_LISTED_RESOLVED;
    private static volatile boolean SET_LISTED_WARNED;

    private void setListed(Player p, boolean listed) {
        if (!SET_LISTED_RESOLVED) {
            synchronized (TabListManager.class) {
                if (!SET_LISTED_RESOLVED) {
                    try {
                        SET_LISTED = Player.class.getMethod("setListed", boolean.class);
                    } catch (NoSuchMethodException ignore) {
                        SET_LISTED = null;
                    }
                    SET_LISTED_RESOLVED = true;
                }
            }
        }
        Method m = SET_LISTED;
        if (m == null) {
            if (!SET_LISTED_WARNED) {
                SET_LISTED_WARNED = true;
                plugin.getLogger().warning(
                        "Player#setListed(boolean) not found on this paper-api build — "
                                + "tab list will still show connected players. "
                                + "Upgrade to a Paper build that patches setListed to hide them.");
            }
            return;
        }
        try {
            m.invoke(p, listed);
        } catch (ReflectiveOperationException ex) {
            if (!SET_LISTED_WARNED) {
                SET_LISTED_WARNED = true;
                plugin.getLogger().warning("setListed() invocation failed: " + ex.getMessage());
            }
        }
    }
}
