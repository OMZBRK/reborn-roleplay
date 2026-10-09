package com.reborn.shinobiabilities.parchemin;

import com.reborn.shinobiabilities.techniques.ParcheminItems;
import com.reborn.shinobicore.technique.Ability;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Commandes staff des parchemins.
 * <ul>
 *   <li>{@code /bibliotheque creer <nom>} — fait du bloc (ou du meuble) visé une bibliothèque.</li>
 *   <li>{@code /bibliotheque regler} — ouvre les réglages de la bibliothèque visée ou la plus proche.</li>
 *   <li>{@code /bibliotheque tirage} — force un nouveau tirage · {@code suppr} · {@code liste}.</li>
 *   <li>{@code /parchemin donner <joueur> <technique>} — donne un rouleau · {@code slots <technique>}.</li>
 *   <li>{@code /parchemin seance <joueur> <technique> [n]} — accorde (ou retire, n négatif) des séances.</li>
 *   <li>{@code /parchemin attente} · {@code valider|refuser <joueur> <technique>} — demandes du rang S.</li>
 * </ul>
 */
public final class ParcheminCommand implements TabExecutor {

    private final LibraryService libs;
    private final ScrollCatalog catalog;
    private final SlotRegistry slots;
    private SeanceService seances;

    public ParcheminCommand(LibraryService libs, ScrollCatalog catalog, SlotRegistry slots) {
        this.libs = libs;
        this.catalog = catalog;
        this.slots = slots;
    }

    public void wire(SeanceService seances) { this.seances = seances; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (!s.hasPermission("shinobiabilities.staff")) { s.sendMessage("§cPermission refusée."); return true; }
        boolean biblio = cmd.getName().equalsIgnoreCase("bibliotheque");
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        return biblio ? library(s, sub, args) : scroll(s, sub, args);
    }

    private boolean library(CommandSender s, String sub, String[] args) {
        if (sub.equals("liste")) {
            if (libs.all().isEmpty()) { s.sendMessage("§7Aucune bibliothèque."); return true; }
            libs.all().values().forEach(l -> s.sendMessage("§6" + l.name + " §7(" + l.id + ") §f" + l.world + " "
                    + l.x + " " + l.y + " " + l.z + " §7· " + l.count + " rouleaux, toutes les " + l.delayHours + " h"));
            return true;
        }
        if (!(s instanceof Player p)) { s.sendMessage("§cEn jeu uniquement."); return true; }
        switch (sub) {
            case "creer" -> {
                if (args.length < 2) { p.sendMessage("§cUsage : /bibliotheque creer <nom>"); return true; }
                Location at = target(p);
                if (at == null) { p.sendMessage("§cVise le bloc ou le meuble de la bibliothèque (6 blocs max)."); return true; }
                if (libs.at(at) != null) { p.sendMessage("§cIl y a déjà une bibliothèque ici."); return true; }
                Library l = libs.create(String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)), at);
                p.sendMessage("§aBibliothèque « " + l.name + " » créée. Règle-la avec /bibliotheque regler.");
                libs.openConfig(p, l);
            }
            case "regler", "tirage", "suppr" -> {
                Location at = target(p);
                Library l = at != null ? libs.at(at) : null;
                if (l == null) l = libs.nearest(p.getLocation(), 6 * 6);
                if (l == null) { p.sendMessage("§cAucune bibliothèque visée ou à moins de 6 blocs."); return true; }
                if (sub.equals("regler")) libs.openConfig(p, l);
                else if (sub.equals("tirage")) { libs.ensureRoll(l, true); p.sendMessage("§aNouveau tirage : " + l.name + "."); }
                else { libs.delete(l); p.sendMessage("§eBibliothèque « " + l.name + " » supprimée."); }
            }
            default -> p.sendMessage("§cUsage : /bibliotheque <creer <nom>|regler|tirage|suppr|liste>");
        }
        return true;
    }

    private boolean scroll(CommandSender s, String sub, String[] args) {
        switch (sub) {
            case "donner" -> {
                if (args.length < 3) { s.sendMessage("§cUsage : /parchemin donner <joueur> <technique>"); return true; }
                Player t = Bukkit.getPlayerExact(args[1]);
                Ability a = catalog.byId(args[2]);
                if (t == null) { s.sendMessage("§cJoueur hors ligne."); return true; }
                if (a == null) { s.sendMessage("§cTechnique inconnue."); return true; }
                String id = java.util.UUID.randomUUID().toString();
                if (ScrollCatalog.rank(a) == 'S') {
                    if (!slots.free(a.id())) {
                        s.sendMessage("§cPlus de slot libre pour " + a.name() + " (" + slots.used(a.id()) + "/" + slots.max(a.id()) + ").");
                        return true;
                    }
                    slots.reserve(a.id(), "rouleau:" + id);
                }
                t.getInventory().addItem(ParcheminItems.create(a, id)).values()
                        .forEach(rest -> t.getWorld().dropItemNaturally(t.getLocation(), rest));
                s.sendMessage("§aParchemin « " + a.name() + " » donné à " + t.getName() + ".");
            }
            case "slots" -> {
                if (args.length < 2) { s.sendMessage("§cUsage : /parchemin slots <technique>"); return true; }
                Ability a = catalog.byId(args[1]);
                if (a == null) { s.sendMessage("§cTechnique inconnue."); return true; }
                s.sendMessage("§6" + a.name() + " §7: " + slots.used(a.id()) + "/" + slots.max(a.id()) + " slots");
                slots.holders(a.id()).forEach(h -> s.sendMessage("§7 - " + h));
            }
            case "seance" -> {
                if (args.length < 3) { s.sendMessage("§cUsage : /parchemin seance <joueur> <technique> [n]"); return true; }
                Player t = Bukkit.getPlayerExact(args[1]);
                Ability a = catalog.byId(args[2]);
                if (t == null || a == null) { s.sendMessage("§cJoueur hors ligne ou technique inconnue."); return true; }
                int n = 1;
                try { if (args.length >= 4) n = Integer.parseInt(args[3].replace("+", "")); }
                catch (NumberFormatException ex) { s.sendMessage("§cNombre invalide."); return true; }
                int done = seances.grant(t, a, n);
                if (done < 0) { s.sendMessage("§cAucun personnage actif."); return true; }
                s.sendMessage("§a" + t.getName() + " · " + a.name() + " : " + done + " / "
                        + ScrollCatalog.seances(ScrollCatalog.rank(a)) + " séances (délai remis à zéro).");
            }
            case "attente" -> {
                if (seances.pending().isEmpty()) { s.sendMessage("§7Aucune demande de validation."); return true; }
                for (SeanceService.Pending p : seances.pending()) {
                    Ability a = catalog.byId(p.techId());
                    s.sendMessage("§6" + p.playerName() + " §7· " + (a == null ? p.techId() : a.name())
                            + " §7→ /parchemin valider|refuser " + p.playerName() + " " + p.techId());
                }
            }
            case "valider", "refuser" -> {
                if (!(s instanceof Player staff)) { s.sendMessage("§cEn jeu uniquement."); return true; }
                if (args.length < 3) { s.sendMessage("§cUsage : /parchemin " + sub + " <joueur> <technique>"); return true; }
                SeanceService.Pending p = seances.findPending(args[1], args[2]);
                if (p == null) { s.sendMessage("§cAucune demande pour ce joueur et cette technique."); return true; }
                String err = sub.equals("valider") ? seances.validate(staff, p) : seances.refuse(staff, p);
                s.sendMessage(err == null ? "§aFait." : "§c" + err);
            }
            default -> s.sendMessage("§cUsage : /parchemin <donner|slots|seance|attente|valider|refuser>");
        }
        return true;
    }

    /** Bloc visé, ou bloc sous le meuble (entité) visé. */
    private static Location target(Player p) {
        RayTraceResult r = p.getWorld().rayTrace(p.getEyeLocation(), p.getEyeLocation().getDirection(), 6,
                FluidCollisionMode.NEVER, true, 0.2, e -> !(e instanceof Player));
        if (r == null) return null;
        Entity e = r.getHitEntity();
        if (e != null) return e.getLocation().getBlock().getLocation();
        Block b = r.getHitBlock();
        return b == null ? null : b.getLocation();
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean biblio = cmd.getName().equalsIgnoreCase("bibliotheque");
        if (args.length == 1) {
            out.addAll(biblio ? List.of("creer", "regler", "tirage", "suppr", "liste")
                    : List.of("donner", "slots", "seance", "attente", "valider", "refuser"));
        } else if (!biblio && args.length == 2 && List.of("donner", "seance", "valider", "refuser").contains(args[0].toLowerCase(Locale.ROOT))) {
            Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
        } else if (!biblio && ((args.length == 3 && List.of("donner", "seance", "valider", "refuser").contains(args[0].toLowerCase(Locale.ROOT)))
                || (args.length == 2 && args[0].equalsIgnoreCase("slots")))) {
            for (Ability a : catalog.pool()) out.add(a.id());
        }
        String cur = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(cur));
        return out;
    }
}
