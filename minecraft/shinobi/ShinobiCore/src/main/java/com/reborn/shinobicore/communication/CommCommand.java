package com.reborn.shinobicore.communication;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.communication.CommData.Cloche;
import com.reborn.shinobicore.communication.CommData.Missive;
import com.reborn.shinobicore.communication.CommData.TypeVoliere;
import com.reborn.shinobicore.communication.CommData.Voliere;
import com.reborn.shinobicore.faction.FactionData.Faction;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.monnaie.Arguments;
import com.reborn.shinobicore.staff.panel.StaffGrades;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Commandes staff de la cloche et des volières (CLOCHE_ET_MISSIVES.md §5), toutes journalisées :
 * <ul>
 *   <li>{@code /cloche poser <village> [rayon] ["lieu"]} (Modérateur, vise le meuble ou le bloc à 6 blocs),
 *       {@code /cloche liste} (Helper), {@code /cloche retirer <id>}, {@code /cloche taire <village> [minutes]}
 *       (Modérateur ; 0 = rendre la parole) ;</li>
 *   <li>{@code /voliere poser "<nom>" <petite|grande|tour|palais> <village>} (Modérateur, vise le perchoir),
 *       {@code /voliere liste} (Helper), {@code /voliere vider <id> "<motif>"}, {@code /voliere retirer <id> "<motif>"}
 *       (Admin) ;</li>
 *   <li>{@code /missive lire <id> "<motif>"} (Admin, motif obligatoire : harcèlement, enquête).</li>
 * </ul>
 * Les joueurs n'ont pas de commande : la cloche et la volière s'ouvrent au menu R ou d'un clic droit.
 */
public final class CommCommand implements TabExecutor {

    private final ShinobiCore plugin;
    private final CommService svc;

    CommCommand(ShinobiCore plugin, CommService svc) {
        this.plugin = plugin;
        this.svc = svc;
    }

    private static int grade(CommandSender s) { return s instanceof Player p ? StaffGrades.of(p) : StaffGrades.OWNER; }

    private static boolean exige(CommandSender s, int g) {
        if (grade(s) >= g) return true;
        s.sendMessage("§c[Staff] Réservé au grade " + switch (g) { case 1 -> "Helper"; case 2 -> "Modérateur"; default -> "Admin"; } + " et plus.");
        return false;
    }

    private static void info(CommandSender s, String m) { s.sendMessage("§6[Corbeau] §f" + m); }

    private static void err(CommandSender s, String m) { s.sendMessage("§c[Corbeau] " + m); }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, @NotNull String[] raw) {
        List<String> a = Arguments.lire(raw);
        String op = a.isEmpty() ? "" : a.get(0).toLowerCase(Locale.ROOT);
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "cloche" -> cloche(s, op, a);
            case "voliere" -> voliere(s, op, a);
            case "missive" -> missive(s, op, a);
            default -> { }
        }
        return true;
    }

    /** Le meuble (entité) ou le bloc visé à 6 blocs. */
    private static Object[] vise(Player p) {
        Entity e = p.getTargetEntity(6);
        Block b = e == null ? p.getTargetBlockExact(6) : null;
        return new Object[]{b, e};
    }

    /* ================================================================ /cloche */

    private void cloche(CommandSender s, String op, List<String> a) {
        switch (op) {
            case "poser" -> {
                if (!exige(s, StaffGrades.MODO)) return;
                if (!(s instanceof Player p)) { err(s, "En jeu : vise le meuble de la cloche."); return; }
                if (a.size() < 2) { err(s, "/cloche poser <village> [rayon] [\"lieu\"]"); return; }
                Faction f = svc.resoudreVillage(a.get(1));
                if (f == null || !f.village()) { err(s, "Village inconnu : KON, SUN, KIR, KUM, IWA, AME."); return; }
                int rayon = 0;
                String lieu = "";
                for (int i = 2; i < a.size(); i++) {
                    try { rayon = Integer.parseInt(a.get(i)); } catch (NumberFormatException ex) { lieu = a.get(i); }
                }
                Object[] v = vise(p);
                if (v[0] == null && v[1] == null) { err(s, "Vise le meuble ou le bloc de la cloche (6 blocs)."); return; }
                Cloche c = svc.poserCloche(p, (Block) v[0], (Entity) v[1], f, rayon, lieu);
                info(s, "Cloche §e" + c.id + "§f posée pour " + f.nom + " · zone " + c.rayon + " blocs"
                        + (c.lieu.isEmpty() ? "" : " · « " + c.lieu + " »") + ".");
            }
            case "liste" -> {
                if (!exige(s, StaffGrades.HELPER)) return;
                if (svc.store.cloches.isEmpty()) { info(s, "Aucune cloche. Vise le meuble : /cloche poser <village> [rayon] [\"lieu\"]"); return; }
                long now = System.currentTimeMillis();
                for (Cloche c : svc.store.cloches.values())
                    s.sendMessage("§e" + c.id + " §7[" + c.faction + "] §f" + (c.lieu.isEmpty() ? "—" : c.lieu) + " §7· " + c.monde + " "
                            + c.x + " " + c.y + " " + c.z + " · rayon " + c.rayon
                            + (c.derniere > 0 ? " · a sonné il y a " + CommRegles.minutes(now - c.derniere) : "")
                            + (c.taireJusqua > now ? " · §ctue encore " + CommRegles.minutes(c.taireJusqua - now) : ""));
            }
            case "retirer" -> {
                if (!exige(s, StaffGrades.MODO)) return;
                if (a.size() < 2) { err(s, "/cloche retirer <id>"); return; }
                if (svc.retirerCloche(s, a.get(1))) info(s, "Cloche " + a.get(1) + " retirée.");
                else err(s, "Cloche inconnue (/cloche liste).");
            }
            case "taire" -> {
                if (!exige(s, StaffGrades.MODO)) return;
                if (a.size() < 2) { err(s, "/cloche taire <village> [minutes] (0 = rendre la parole)"); return; }
                Faction f = svc.resoudreVillage(a.get(1));
                if (f == null) { err(s, "Village inconnu."); return; }
                int min = 30;
                if (a.size() > 2) try { min = Integer.parseInt(a.get(2)); } catch (NumberFormatException ex) { err(s, "Minutes : un nombre."); return; }
                int n = svc.taire(f.tag, min);
                Journal.entree(Categorie.STAFF, "CLOCHE_TUE").acteur(s).cibleTexte(f.tag)
                        .detail(f.nom + " · " + n + " cloche(s) · " + (min <= 0 ? "parole rendue" : min + " min")).ecrire();
                info(s, min <= 0 ? "Les cloches de " + f.nom + " peuvent de nouveau sonner."
                        : n + " cloche(s) de " + f.nom + " tue(s) pour " + min + " min ; la ligne en cours est retirée.");
            }
            default -> info(s, "/cloche poser <village> [rayon] [\"lieu\"] · liste · retirer <id> · taire <village> [min]");
        }
    }

    /* ================================================================ /voliere */

    private void voliere(CommandSender s, String op, List<String> a) {
        switch (op) {
            case "poser" -> {
                if (!exige(s, StaffGrades.MODO)) return;
                if (!(s instanceof Player p)) { err(s, "En jeu : vise le perchoir."); return; }
                if (a.size() < 4) { err(s, "/voliere poser \"<nom>\" <petite|grande|tour|palais> <village>"); return; }
                TypeVoliere t = TypeVoliere.lire(a.get(2));
                if (t == null) { err(s, "Type : petite, grande, tour ou palais."); return; }
                Faction f = svc.resoudreVillage(a.get(3));
                if (f == null || !f.village()) { err(s, "Village inconnu : KON, SUN, KIR, KUM, IWA, AME."); return; }
                Object[] v = vise(p);
                if (v[0] == null && v[1] == null) { err(s, "Vise le perchoir (meuble ou bloc, 6 blocs)."); return; }
                Voliere vo = svc.poserVoliere(p, (Block) v[0], (Entity) v[1], f, t, a.get(1));
                info(s, "Volière §e" + vo.id + "§f posée : « " + vo.nom + " » (" + t.libelle + ", " + t.capacite + " missives).");
            }
            case "liste" -> {
                if (!exige(s, StaffGrades.HELPER)) return;
                if (svc.store.volieres.isEmpty()) { info(s, "Aucune volière. Vise le perchoir : /voliere poser \"<nom>\" <type> <village>"); return; }
                Map<String, Integer> n = svc.attenteParVoliere();
                for (Voliere v : svc.store.volieres.values())
                    s.sendMessage("§e" + v.id + " §7[" + v.faction + "] §f" + v.nom + " §7· " + v.type.libelle + " · "
                            + n.getOrDefault(v.id, 0) + "/" + v.type.capacite + " · " + v.monde + " " + v.x + " " + v.y + " " + v.z);
            }
            case "vider", "retirer" -> {
                if (!exige(s, StaffGrades.ADMIN)) return;
                if (a.size() < 3) { err(s, "/voliere " + op + " <id> \"<motif>\""); return; }
                Voliere v = svc.store.volieres.get(a.get(1));
                if (v == null) { err(s, "Volière inconnue (/voliere liste)."); return; }
                String motif = Arguments.reste(a, 2);
                if (op.equals("vider")) info(s, svc.viderVoliere(s, v, motif) + " missive(s) brûlée(s) à « " + v.nom + " ».");
                else { svc.retirerVoliere(s, v.id, motif); info(s, "Volière « " + v.nom + " » retirée."); }
            }
            default -> info(s, "/voliere poser \"<nom>\" <petite|grande|tour|palais> <village> · liste · vider <id> \"<motif>\" · retirer <id> \"<motif>\"");
        }
    }

    /* ================================================================ /missive */

    private void missive(CommandSender s, String op, List<String> a) {
        if (!"lire".equals(op)) { info(s, "/missive lire <id> \"<motif>\" (Admin, journalisé)"); return; }
        if (!exige(s, StaffGrades.ADMIN)) return;
        if (a.size() < 3 || Arguments.reste(a, 2).isBlank()) { err(s, "/missive lire <id> \"<motif>\" — motif obligatoire."); return; }
        Missive m = svc.missive(a.get(1));
        if (m == null) { err(s, "Missive inconnue."); return; }
        String motif = Arguments.reste(a, 2);
        Journal.entree(Categorie.STAFF, "MISSIVE_LUE").acteur(s).cibleTexte(m.id).detail(m.id + " lue par le staff · « " + motif + " »").ecrire();
        List<String> d = new ArrayList<>();
        for (CommData.Dest x : m.dests) d.add(x.nom());
        s.sendMessage("§6[Missive " + m.id + "] §7" + m.etat + " · " + (m.scellee ? "scellée" : "cachet brisé") + " · de §f" + m.auteurNom
                + (m.signee ? " §7(signée)" : " §7(anonyme)") + " · pour §f" + String.join(", ", d));
        s.sendMessage("§f" + Riche.plat(Riche.deBalises(m.texte)));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, @NotNull String[] a) {
        List<String> r = new ArrayList<>();
        String n = cmd.getName().toLowerCase(Locale.ROOT);
        if (grade(s) < StaffGrades.HELPER) return r;
        if (a.length == 1) {
            r.addAll(switch (n) {
                case "cloche" -> List.of("poser", "liste", "retirer", "taire");
                case "voliere" -> List.of("poser", "liste", "vider", "retirer");
                default -> List.of("lire");
            });
        } else if (a.length == 2 && n.equals("cloche") && (a[0].equalsIgnoreCase("poser") || a[0].equalsIgnoreCase("taire"))) {
            r.addAll(List.of("KON", "SUN", "KIR", "KUM", "IWA", "AME"));
        } else if (a.length == 2 && n.equals("cloche") && a[0].equalsIgnoreCase("retirer")) {
            r.addAll(svc.store.cloches.keySet());
        } else if (a.length == 2 && n.equals("voliere") && (a[0].equalsIgnoreCase("vider") || a[0].equalsIgnoreCase("retirer"))) {
            r.addAll(svc.store.volieres.keySet());
        } else if (a.length == 3 && n.equals("voliere") && a[0].equalsIgnoreCase("poser")) {
            r.addAll(List.of("petite", "grande", "tour", "palais"));
        } else if (a.length == 4 && n.equals("voliere") && a[0].equalsIgnoreCase("poser")) {
            r.addAll(List.of("KON", "SUN", "KIR", "KUM", "IWA", "AME"));
        }
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        r.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return r;
    }
}
