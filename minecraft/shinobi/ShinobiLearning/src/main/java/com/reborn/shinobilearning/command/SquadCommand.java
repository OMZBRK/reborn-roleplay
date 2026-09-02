package com.reborn.shinobilearning.command;

import com.reborn.shinobicore.character.Rank;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobilearning.ShinobiLearning;
import com.reborn.shinobilearning.academy.AcademyStore;
import com.reborn.shinobilearning.squad.SquadManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /squad} — Genin squad management plus sensei teaching. A ranking
 * sensei (Chūnin+) forms a squad and teaches techniques: {@code /squad teach}
 * transmits per-ability mastery up to just below the master's own — a student
 * surpasses their teacher only through their own training afterward.
 */
public final class SquadCommand implements CommandExecutor, TabCompleter {

    private final ShinobiLearning plugin;

    public SquadCommand(ShinobiLearning plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(err("Réservé aux joueurs."));
            return true;
        }
        ShinobiCharacter self = plugin.characters().getActive(p.getUniqueId());
        if (self == null) { p.sendMessage(err("Aucun personnage actif.")); return true; }

        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info" -> { return info(p, self); }
            case "create", "creer", "créer" -> { return create(p, self, args); }
            case "add", "ajouter" -> { return add(p, self, args); }
            case "leave", "quitter" -> { return leave(p, self); }
            case "disband", "dissoudre" -> { return disband(p, self); }
            default -> {
                p.sendMessage(err("Sous-commande inconnue : info | create | add | leave | disband"));
                return true;
            }
        }
    }

    private boolean isSensei(ShinobiCharacter c) {
        return c.rank().ordinal() >= Rank.CHUNIN.ordinal();
    }

    private boolean info(Player p, ShinobiCharacter self) {
        SquadManager.Squad s = plugin.squads().squadOf(self.id());
        if (s == null) {
            p.sendMessage(Component.text("Tu n'es dans aucune équipe.", NamedTextColor.GRAY));
            return true;
        }
        p.sendMessage(Component.text("— Équipe « " + s.name() + " » —", NamedTextColor.GOLD));
        for (var e : s.members().entrySet()) {
            boolean leader = e.getKey().equals(s.leaderCharId());
            p.sendMessage(Component.text((leader ? "★ " : "• ") + e.getValue()
                    + (leader ? " (sensei)" : ""),
                    leader ? NamedTextColor.YELLOW : NamedTextColor.GRAY));
        }
        return true;
    }

    private boolean create(Player p, ShinobiCharacter self, String[] a) {
        if (!isSensei(self)) { p.sendMessage(err("Seul un Chūnin ou plus peut former une équipe.")); return true; }
        if (a.length < 2) { p.sendMessage(err("Usage : /squad create <nom>")); return true; }
        if (plugin.squads().squadOf(self.id()) != null) { p.sendMessage(err("Tu es déjà dans une équipe.")); return true; }
        String name = String.join(" ", Arrays.copyOfRange(a, 1, a.length));
        SquadManager.Squad s = plugin.squads().create(self.id(), self.name(), name);
        if (s == null) { p.sendMessage(err("Impossible de créer l'équipe.")); return true; }
        p.sendMessage(ok("Équipe « " + name + " » formée. Tu en es le sensei."));
        return true;
    }

    private boolean add(Player p, ShinobiCharacter self, String[] a) {
        SquadManager.Squad s = plugin.squads().squadOf(self.id());
        if (s == null || !s.leaderCharId().equals(self.id())) {
            p.sendMessage(err("Seul le sensei de l'équipe peut ajouter des membres."));
            return true;
        }
        if (a.length < 2) { p.sendMessage(err("Usage : /squad add <personnage>")); return true; }
        AcademyStore.ResolvedCharacter r = plugin.academy().resolveCharacter(a[1]);
        if (r == null) { p.sendMessage(err("Personnage introuvable : " + a[1])); return true; }
        if (!plugin.squads().addMember(s, r.character().id(), r.character().name())) {
            p.sendMessage(err("Impossible d'ajouter " + r.character().name()
                    + " (équipe pleine ou déjà en équipe)."));
            return true;
        }
        p.sendMessage(ok(r.character().name() + " rejoint l'équipe « " + s.name() + " »."));
        return true;
    }

    private boolean leave(Player p, ShinobiCharacter self) {
        SquadManager.Squad s = plugin.squads().removeMember(self.id());
        if (s == null) { p.sendMessage(err("Tu n'es dans aucune équipe.")); return true; }
        p.sendMessage(ok("Tu quittes l'équipe « " + s.name() + " »."));
        return true;
    }

    private boolean disband(Player p, ShinobiCharacter self) {
        SquadManager.Squad s = plugin.squads().squadOf(self.id());
        if (s == null || !s.leaderCharId().equals(self.id())) {
            p.sendMessage(err("Seul le sensei peut dissoudre l'équipe."));
            return true;
        }
        plugin.squads().disband(s);
        p.sendMessage(ok("Équipe « " + s.name() + " » dissoute."));
        return true;
    }

    private static Component ok(String t)  { return Component.text(t, NamedTextColor.GREEN); }
    private static Component err(String t) { return Component.text(t, NamedTextColor.RED); }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String low = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String o : List.of("info", "create", "add", "leave", "disband")) {
                if (o.startsWith(low)) out.add(o);
            }
            return out;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("add")) {
                String low = args[1].toLowerCase(Locale.ROOT);
                List<String> out = new ArrayList<>();
                if (plugin.characters() != null) {
                    for (var roster : plugin.characters().rosterView().values()) {
                        for (ShinobiCharacter c : roster) {
                            if (c.name().toLowerCase(Locale.ROOT).startsWith(low)) out.add(c.name());
                        }
                    }
                }
                return out;
            }
        }
        return List.of();
    }
}
