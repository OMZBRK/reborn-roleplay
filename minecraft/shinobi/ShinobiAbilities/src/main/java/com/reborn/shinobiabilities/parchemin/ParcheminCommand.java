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
 * Commandes des parchemins et des bibliothèques (docs/gameplay/APPRENTISSAGE.md §3).
 * <p>Joueurs :
 * <ul>
 *   <li>{@code /bibliotheque perso} — fait du bloc (ou du meuble) visé sa bibliothèque personnelle (une par
 *       personnage) · {@code perso retirer} la défait (le fonds revient dans la sacoche).</li>
 *   <li>{@code /bibliotheque autoriser|retirer <joueur>} — ouvre ou ferme sa bibliothèque personnelle à quelqu'un.</li>
 *   <li>{@code /bibliotheque journal} — journal de la bibliothèque visée (propriétaire ou staff).</li>
 * </ul>
 * Staff :
 * <ul>
 *   <li>{@code /bibliotheque creer <nom> <type> [propriétaire]} — fait du bloc (ou du meuble) visé une bibliothèque ;
 *       nom entre guillemets ou avec des « _ » s'il a des espaces.</li>
 *   <li>{@code /bibliotheque regler} — ouvre les réglages de la bibliothèque visée ou la plus proche.</li>
 *   <li>{@code /bibliotheque ouvrir <minutes>} — ouvre l'accès à tous un temps (0 = refermer).</li>
 *   <li>{@code /bibliotheque tirage} — force un nouveau tirage · {@code suppr} · {@code liste}.</li>
 *   <li>{@code /parchemin donner <joueur> <technique>} — donne un rouleau · {@code slots <technique>}.</li>
 *   <li>{@code /parchemin seance <joueur> <technique> [n]} — accorde (ou retire, n négatif) des séances.</li>
 *   <li>{@code /parchemin attente} · {@code valider|refuser <joueur> <technique>} — demandes du rang S.</li>
 *   <li>{@code /parchemin concentration [D|C|B|A|S] [maitre]} — essayer la concentration du chakra, sans enjeu.</li>
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

    private com.reborn.shinobiabilities.techniques.ConcentrationGame concentration;

    public void wire(com.reborn.shinobiabilities.techniques.ConcentrationGame c) { this.concentration = c; }

    private static final String STAFF = "shinobiabilities.staff";
    private static final List<String> PLAYER_SUBS = List.of("perso", "autoriser", "retirer", "journal");

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        boolean biblio = cmd.getName().equalsIgnoreCase("bibliotheque");
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (biblio && PLAYER_SUBS.contains(sub)) return libraryPlayer(s, sub, args);
        if (!s.hasPermission(STAFF)) {
            s.sendMessage(biblio ? "§cUsage : /bibliotheque <perso [retirer]|autoriser <joueur>|retirer <joueur>|journal>"
                    : "§cPermission refusée.");
            return true;
        }
        return biblio ? library(s, sub, args) : scroll(s, sub, args);
    }

    private static String typesList() {
        StringBuilder b = new StringBuilder();
        for (Library.Type t : Library.Type.values()) b.append(b.isEmpty() ? "" : "|").append(t.name().toLowerCase(Locale.ROOT));
        return b.toString();
    }

    /** Bibliothèque visée, sinon la plus proche (6 blocs). */
    private Library aimed(Player p) {
        Location at = target(p);
        Library l = at != null ? libs.at(at) : null;
        return l != null ? l : libs.nearest(p.getLocation(), 6 * 6);
    }

    /** Joueur en ligne par nom de compte, sinon par nom de personnage. */
    private Player findPlayer(String name) {
        Player t = Bukkit.getPlayerExact(name);
        if (t == null) t = libs.characterPlayer(name);
        return t;
    }

    private boolean libraryPlayer(CommandSender s, String sub, String[] args) {
        if (!(s instanceof Player p)) { s.sendMessage("§cEn jeu uniquement."); return true; }
        var c = libs.character(p);
        switch (sub) {
            case "perso" -> {
                if (c == null) { p.sendMessage("§cAucun personnage actif."); return true; }
                Library mine = libs.personalOf(c.id());
                if (args.length >= 2 && args[1].equalsIgnoreCase("retirer")) {
                    if (mine == null) { p.sendMessage("§cTu n'as pas de bibliothèque personnelle."); return true; }
                    int n = libs.delete(mine, p);
                    p.sendMessage("§eTa bibliothèque personnelle est défaite" + (n > 0 ? " : " + n + " rouleau(x) rendu(s)." : "."));
                    return true;
                }
                if (mine != null) {
                    p.sendMessage("§cTu as déjà une bibliothèque personnelle (" + mine.world + " " + mine.x + " " + mine.y + " "
                            + mine.z + "). Défais-la d'abord : /bibliotheque perso retirer");
                    return true;
                }
                Location at = target(p);
                if (at == null) { p.sendMessage("§cVise le bloc ou le meuble qui servira de bibliothèque (6 blocs max)."); return true; }
                if (libs.at(at) != null) { p.sendMessage("§cIl y a déjà une bibliothèque ici."); return true; }
                Library l = libs.create("Bibliothèque de " + c.name(), at, Library.Type.PERSONNELLE, c.id().toString(), c.name());
                p.sendMessage("§aTa bibliothèque personnelle est prête. Dépose tes rouleaux depuis son écran ; "
                        + "/bibliotheque autoriser <joueur> pour la partager.");
                libs.open(p, l);
            }
            case "autoriser", "retirer" -> {
                if (c == null) { p.sendMessage("§cAucun personnage actif."); return true; }
                Library mine = libs.personalOf(c.id());
                if (mine == null) { p.sendMessage("§cTu n'as pas de bibliothèque personnelle (/bibliotheque perso)."); return true; }
                if (args.length < 2) {
                    if (mine.allowed.isEmpty()) p.sendMessage("§7Personne d'autre n'a accès à ta bibliothèque.");
                    else p.sendMessage("§6Autorisés : §f" + String.join(", ", mine.allowed.values()));
                    p.sendMessage("§7Usage : /bibliotheque " + sub + " <joueur>");
                    return true;
                }
                if (sub.equals("retirer")) {
                    String key = null;
                    for (var e : mine.allowed.entrySet()) {
                        if (e.getValue().equalsIgnoreCase(args[1])) key = e.getKey();
                    }
                    Player t = findPlayer(args[1]);
                    var tc = t == null ? null : libs.character(t);
                    if (key == null && tc != null && mine.allowed.containsKey(tc.id().toString())) key = tc.id().toString();
                    if (key == null) { p.sendMessage("§c" + args[1] + " n'est pas autorisé."); return true; }
                    String n = mine.allowed.remove(key);
                    libs.save();
                    p.sendMessage("§e" + n + " n'a plus accès à ta bibliothèque.");
                    return true;
                }
                Player t = findPlayer(args[1]);
                var tc = t == null ? null : libs.character(t);
                if (tc == null) { p.sendMessage("§cJoueur hors ligne ou sans personnage actif."); return true; }
                if (tc.id().equals(c.id())) { p.sendMessage("§7C'est déjà ta bibliothèque."); return true; }
                mine.allowed.put(tc.id().toString(), tc.name());
                libs.save();
                p.sendMessage("§a" + tc.name() + " peut maintenant prendre dans ta bibliothèque.");
                t.sendMessage("§6" + c.name() + " t'ouvre sa bibliothèque personnelle.");
            }
            case "journal" -> {
                Library l = aimed(p);
                if (l == null && c != null) l = libs.personalOf(c.id());
                if (l == null) { p.sendMessage("§cAucune bibliothèque visée ou à moins de 6 blocs."); return true; }
                if (!libs.canSeeJournal(p, l)) { p.sendMessage("§cSeuls le propriétaire et le staff consultent ce journal."); return true; }
                libs.open(p, l);
                libs.openJournal(p, l);
            }
            default -> { }
        }
        return true;
    }

    private boolean library(CommandSender s, String sub, String[] args) {
        if (sub.equals("liste")) {
            if (libs.all().isEmpty()) { s.sendMessage("§7Aucune bibliothèque."); return true; }
            libs.all().values().forEach(l -> s.sendMessage("§6" + l.name + " §7(" + l.id + ", " + l.type.label
                    + (LibraryService.ownerLabel(l).isEmpty() ? "" : " · " + LibraryService.ownerLabel(l)) + ", "
                    + l.access.name().toLowerCase(Locale.ROOT) + ") §f" + l.world + " " + l.x + " " + l.y + " " + l.z
                    + " §7· fonds " + l.fondsCount() + "/" + Library.FONDS
                    + (l.draws ? " · " + l.count + " arrivages toutes les " + l.delayHours + " h" : " · sans arrivages")
                    + " · réserve " + libs.reserveOf(l).size() + (l.reserveCustom ? " (réglée)" : "")
                    + (l.theft ? " · vol " + l.theftSeconds + " s" : " · vol interdit")));
            return true;
        }
        if (!(s instanceof Player p)) { s.sendMessage("§cEn jeu uniquement."); return true; }
        switch (sub) {
            case "creer" -> {
                String usage = "§cUsage : /bibliotheque creer <nom> <" + typesList() + "> [propriétaire]"
                        + " §7(nom avec espaces : entre guillemets ou avec des _)";
                List<String> rest = new ArrayList<>(java.util.Arrays.asList(args).subList(1, args.length));
                String name;
                String joined = String.join(" ", rest);
                if (joined.startsWith("\"") && joined.indexOf('"', 1) > 0) {
                    int end = joined.indexOf('"', 1);
                    name = joined.substring(1, end).trim();
                    String after = joined.substring(end + 1).trim();
                    rest = after.isEmpty() ? new ArrayList<>() : new ArrayList<>(java.util.Arrays.asList(after.split(" +")));
                } else if (!rest.isEmpty()) {
                    name = rest.remove(0).replace('_', ' ');
                } else {
                    p.sendMessage(usage);
                    return true;
                }
                Library.Type type = rest.isEmpty() ? null : Library.Type.parse(rest.remove(0));
                if (name.isEmpty() || type == null) { p.sendMessage(usage); return true; }
                String owner = String.join(" ", rest).trim(), ownerName = "";
                if (type == Library.Type.PERSONNELLE) {
                    Player t = owner.isEmpty() ? p : findPlayer(owner);
                    var tc = t == null ? null : libs.character(t);
                    if (tc == null) { p.sendMessage("§cPropriétaire hors ligne ou sans personnage actif."); return true; }
                    if (libs.personalOf(tc.id()) != null) { p.sendMessage("§c" + tc.name() + " a déjà une bibliothèque personnelle."); return true; }
                    owner = tc.id().toString();
                    ownerName = tc.name();
                } else if (type == Library.Type.CLAN && owner.isEmpty()) {
                    p.sendMessage("§cUne bibliothèque de clan demande le nom du clan : /bibliotheque creer <nom> clan <clan>");
                    return true;
                }
                Location at = target(p);
                if (at == null) { p.sendMessage("§cVise le bloc ou le meuble de la bibliothèque (6 blocs max)."); return true; }
                if (libs.at(at) != null) { p.sendMessage("§cIl y a déjà une bibliothèque ici."); return true; }
                Library l = libs.create(name, at, type, owner, ownerName);
                p.sendMessage("§aBibliothèque « " + l.name + " » créée (" + type.label
                        + (LibraryService.ownerLabel(l).isEmpty() ? "" : " · " + LibraryService.ownerLabel(l))
                        + ", accès " + l.access.name().toLowerCase(Locale.ROOT) + (l.draws ? ", arrivages actifs" : ", sans arrivages")
                        + "). Règle-la avec /bibliotheque regler.");
                libs.logStaff(p, "Bibliothèque créée", l.name + " · " + type.label);
                libs.openConfig(p, l);
            }
            case "regler", "tirage", "suppr", "ouvrir" -> {
                Library l = aimed(p);
                if (l == null) { p.sendMessage("§cAucune bibliothèque visée ou à moins de 6 blocs."); return true; }
                switch (sub) {
                    case "regler" -> libs.openConfig(p, l);
                    case "tirage" -> {
                        if (!l.draws) { p.sendMessage("§cLes arrivages de « " + l.name + " » sont désactivés (réglages)."); return true; }
                        libs.ensureRoll(l, true);
                        p.sendMessage("§aNouveau tirage : " + l.name + ".");
                    }
                    case "ouvrir" -> {
                        int min;
                        try { min = args.length >= 2 ? Integer.parseInt(args[1]) : 15; }
                        catch (NumberFormatException ex) { p.sendMessage("§cUsage : /bibliotheque ouvrir <minutes> (0 = refermer)"); return true; }
                        min = Math.max(0, Math.min(24 * 60, min));
                        l.openUntil = min == 0 ? 0L : System.currentTimeMillis() + min * 60_000L;
                        libs.save();
                        p.sendMessage(min == 0 ? "§e« " + l.name + " » est refermée aux non-membres."
                                : "§a« " + l.name + " » est ouverte à tous pendant " + min + " min.");
                        libs.logStaff(p, min == 0 ? "Bibliothèque refermée" : "Bibliothèque ouverte " + min + " min", l.name);
                    }
                    default -> {
                        int n = libs.delete(l, p);
                        p.sendMessage("§eBibliothèque « " + l.name + " » supprimée" + (n > 0 ? " ; " + n + " rouleau(x) du fonds remis dans ta sacoche." : "."));
                        libs.logStaff(p, "Bibliothèque supprimée", l.name);
                    }
                }
            }
            default -> p.sendMessage("§cUsage : /bibliotheque <creer <nom> <type> [propriétaire]|regler|ouvrir <minutes>|tirage|suppr|liste"
                    + "|perso|autoriser|retirer|journal>");
        }
        return true;
    }

    private boolean scroll(CommandSender s, String sub, String[] args) {
        switch (sub) {
            case "donner" -> {
                if (args.length < 3) { s.sendMessage("§cUsage : /parchemin donner <joueur> <technique>"); return true; }
                Player t = com.reborn.shinobicore.character.PlayerIds.online(s, args[1]);
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
                com.reborn.shinobiabilities.util.Donner.a(t, ParcheminItems.create(a, id));
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
                Player t = com.reborn.shinobicore.character.PlayerIds.online(s, args[1]);
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
            case "concentration" -> {
                if (!(s instanceof Player p)) { s.sendMessage("§cEn jeu uniquement."); return true; }
                char r = args.length >= 2 ? Character.toUpperCase(args[1].charAt(0)) : 'D';
                boolean helped = args.length >= 3 && args[2].toLowerCase(Locale.ROOT).startsWith("ma");
                if (concentration.isActive(p)) { concentration.abort(p); p.sendMessage("§7Concentration interrompue."); return true; }
                concentration.start(p, r, helped, ok -> p.sendMessage(ok ? "§aRéussi (essai sans enjeu)." : "§7Raté (essai sans enjeu)."));
            }
            default -> s.sendMessage("§cUsage : /parchemin <donner|slots|seance|attente|valider|refuser|concentration>");
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
        boolean staff = s.hasPermission(STAFF);
        if (args.length == 1) {
            if (biblio) {
                out.addAll(PLAYER_SUBS);
                if (staff) out.addAll(List.of("creer", "regler", "ouvrir", "tirage", "suppr", "liste"));
            } else if (staff) {
                out.addAll(List.of("donner", "slots", "seance", "attente", "valider", "refuser", "concentration"));
            }
        } else if (biblio && args.length == 2 && args[0].equalsIgnoreCase("perso")) {
            out.add("retirer");
        } else if (biblio && args.length == 2 && List.of("autoriser", "retirer").contains(args[0].toLowerCase(Locale.ROOT))) {
            Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
        } else if (biblio && staff && args.length == 2 && args[0].equalsIgnoreCase("ouvrir")) {
            out.addAll(List.of("10", "30", "60", "0"));
        } else if (biblio && staff && args.length == 3 && args[0].equalsIgnoreCase("creer") && !args[1].startsWith("\"")) {
            for (Library.Type t : Library.Type.values()) out.add(t.name().toLowerCase(Locale.ROOT));
        } else if (biblio && staff && args.length == 4 && args[0].equalsIgnoreCase("creer")
                && Library.Type.parse(args[2]) == Library.Type.PERSONNELLE) {
            Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
        } else if (!staff) {
            return out;
        } else if (!biblio && args.length == 2 && args[0].equalsIgnoreCase("concentration")) {
            out.addAll(List.of("D", "C", "B", "A", "S"));
        } else if (!biblio && args.length == 3 && args[0].equalsIgnoreCase("concentration")) {
            out.add("maitre");
        } else if (!biblio && args.length == 2 && List.of("donner", "seance", "valider", "refuser").contains(args[0].toLowerCase(Locale.ROOT))) {
            Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
        } else if (!biblio && ((args.length == 3 && List.of("donner", "seance", "valider", "refuser").contains(args[0].toLowerCase(Locale.ROOT)))
                || (args.length == 2 && args[0].equalsIgnoreCase("slots")))) {
            for (Ability a : catalog.reservablePool()) out.add(a.id());
        }
        String cur = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(cur));
        return out;
    }
}
