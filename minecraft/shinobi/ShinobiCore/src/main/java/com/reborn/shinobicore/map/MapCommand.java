package com.reborn.shinobicore.map;

import com.reborn.shinobicore.map.PlaceRegistry.Place;
import com.reborn.shinobicore.map.PlaceRegistry.Type;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /carte} — ouvre la carte du monde (mod client), ou liste les lieux en chat.
 *
 * <pre>
 * /carte                          ouvre la carte tenue en main
 * /carte donner &lt;joueur&gt; &lt;carte&gt;  (shinobicore.map.admin) donne l'objet carte
 * /carte liste                    lieux de la carte courante
 * /carte tp &lt;id&gt;                  (shinobicore.map.tp)
 * /carte set &lt;id&gt; &lt;type&gt; &lt;nom…&gt;  (shinobicore.map.admin) à ta position
 * /carte del &lt;id&gt;                 (shinobicore.map.admin)
 * /carte reload                   (shinobicore.map.admin)
 * </pre>
 */
public final class MapCommand implements TabExecutor {

    private static final String ADMIN = "shinobicore.map.admin";

    private final JavaPlugin plugin;
    private final PlaceRegistry places;
    private final MapChannel channel;

    public MapCommand(JavaPlugin plugin, PlaceRegistry places, MapChannel channel) {
        this.plugin = plugin;
        this.places = places;
        this.channel = channel;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> {
                if (!(s instanceof Player p)) return list(s);
                channel.open(p);
                return true;
            }
            case "donner", "give" -> {
                if (!s.hasPermission(ADMIN)) return deny(s);
                if (a.length < 3) return usage(s, "/carte donner <joueur> <carte>");
                Player target = Bukkit.getPlayerExact(a[1]);
                if (target == null) return usage(s, "Joueur introuvable : " + a[1]);
                var def = places.map(a[2].toLowerCase(Locale.ROOT));
                if (def == null) return usage(s, "Carte inconnue : " + a[2]);
                MapItem.give(plugin, target, def);
                s.sendMessage(Component.text("Carte « " + def.title() + " » donnée à " + target.getName() + ".",
                        NamedTextColor.GOLD));
                return true;
            }
            case "liste", "list" -> { return list(s); }
            case "tp" -> {
                if (!(s instanceof Player p) || a.length < 2) return usage(s, "/carte tp <id>");
                channel.teleport(p, a[1]);
                return true;
            }
            case "set" -> {
                if (!s.hasPermission(ADMIN)) return deny(s);
                if (!(s instanceof Player p) || a.length < 4) return usage(s, "/carte set <id> <zone|lieu|pnj|porte> <nom…>");
                Type t = Type.parse(a[2]);
                if (t == null) return usage(s, "Type : zone, lieu, pnj ou porte.");
                String name = String.join(" ", Arrays.copyOfRange(a, 3, a.length));
                Place pl = places.put(a[1].toLowerCase(Locale.ROOT), t, name, p.getLocation());
                if (pl == null) return usage(s, "Aucune carte n'est liée à ce monde (places.yml → maps).");
                s.sendMessage(Component.text("Lieu « " + pl.name() + " » enregistré (" + pl.id() + ", "
                        + t.key() + ", carte " + pl.map() + ").", NamedTextColor.GOLD));
                return true;
            }
            case "del", "suppr" -> {
                if (!s.hasPermission(ADMIN)) return deny(s);
                if (a.length < 2) return usage(s, "/carte del <id>");
                boolean ok = places.remove(a[1].toLowerCase(Locale.ROOT));
                s.sendMessage(Component.text(ok ? "Lieu supprimé." : "Lieu inconnu.",
                        ok ? NamedTextColor.GOLD : NamedTextColor.RED));
                return true;
            }
            case "reload" -> {
                if (!s.hasPermission(ADMIN)) return deny(s);
                places.load();
                s.sendMessage(Component.text("places.yml rechargé.", NamedTextColor.GOLD));
                return true;
            }
            default -> { return usage(s, "/carte [liste|tp|donner|set|del|reload]"); }
        }
    }

    private boolean list(CommandSender s) {
        var def = s instanceof Player p ? places.mapFor(p.getWorld()) : null;
        s.sendMessage(Component.text("— Carte" + (def != null ? " : " + def.title() : "") + " —", NamedTextColor.GOLD));
        for (Place pl : def != null ? places.placesOf(def.id()) : List.copyOf(places.places())) {
            s.sendMessage(Component.text(" • " + pl.name(), NamedTextColor.YELLOW)
                    .append(Component.text("  [" + pl.id() + ", " + pl.type().key() + "]  "
                            + Math.round(pl.x()) + ", " + Math.round(pl.z()), NamedTextColor.GRAY)));
        }
        return true;
    }

    private static boolean usage(CommandSender s, String msg) {
        s.sendMessage(Component.text(msg, NamedTextColor.RED));
        return true;
    }

    private static boolean deny(CommandSender s) {
        return usage(s, "Réservé au staff.");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("liste", "tp"));
            if (s.hasPermission(ADMIN)) out.addAll(List.of("donner", "set", "del", "reload"));
        } else if (a.length == 2 && List.of("tp", "set", "del", "suppr").contains(a[0].toLowerCase(Locale.ROOT))) {
            for (Place pl : places.places()) out.add(pl.id());
        } else if (a.length == 2 && List.of("donner", "give").contains(a[0].toLowerCase(Locale.ROOT))) {
            for (Player pl : Bukkit.getOnlinePlayers()) out.add(pl.getName());
        } else if (a.length == 3 && List.of("donner", "give").contains(a[0].toLowerCase(Locale.ROOT))) {
            for (var m : places.maps()) out.add(m.id());
        } else if (a.length == 3 && a[0].equalsIgnoreCase("set")) {
            for (Type t : Type.values()) out.add(t.key());
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(o -> !o.startsWith(last));
        return out;
    }
}
