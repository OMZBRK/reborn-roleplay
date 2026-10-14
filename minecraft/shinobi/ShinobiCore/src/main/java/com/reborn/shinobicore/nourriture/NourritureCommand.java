package com.reborn.shinobicore.nourriture;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.api.ModificateurService;
import com.reborn.shinobicore.character.PlayerIds;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.modificateur.TypeModificateur;
import com.reborn.shinobicore.staff.panel.StaffGrades;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /nourriture} — outils staff de test et d'arbitrage (journalisés, catégorie Staff) :
 * {@code etat [cible]}, {@code satiete <cible> <0-100>}, {@code donner <cible> <plat:id|panier:id|ration|bol> [n] [froid]},
 * {@code effet <cible> <type> <intensité> <secondes>}, {@code retirer <cible> <type>}. Sans droit staff : {@code etat}
 * sur soi.
 */
public final class NourritureCommand implements TabExecutor {

    public static final String PERM = "shinobicore.nourriture.staff";

    private final ShinobiCore plugin;
    private final NourritureModule module;

    NourritureCommand(ShinobiCore plugin, NourritureModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    private static boolean staff(CommandSender s) {
        if (!(s instanceof Player p)) return true;
        return p.isOp() || p.hasPermission(PERM) || StaffGrades.of(p) >= StaffGrades.MODO;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, @NotNull String[] a) {
        String sub = a.length == 0 ? "etat" : a[0].toLowerCase(Locale.ROOT);
        if (!staff(s)) {
            if (s instanceof Player p) etat(s, p);
            return true;
        }
        Player cible = a.length >= 2 ? PlayerIds.online(s, a[1]) : s instanceof Player p ? p : null;
        if (cible == null) { err(s, "Joueur introuvable."); return true; }
        ShinobiCharacter c = plugin.characters().getActive(cible.getUniqueId());
        if (c == null) { err(s, "Pas de personnage actif."); return true; }
        switch (sub) {
            case "etat", "état" -> etat(s, cible);
            case "satiete", "satiété" -> {
                if (a.length < 3) { err(s, "/nourriture satiete <cible> <0-100>"); return true; }
                double v;
                try { v = Double.parseDouble(a[2].replace(',', '.')); } catch (NumberFormatException e) { err(s, "Nombre ?"); return true; }
                double avant = module.satiete().satiete(cible);
                module.satiete().fixer(c.id(), v);
                Journal.entree(Categorie.STAFF, "SATIETE").acteur(s).cible(cible)
                        .detail(Math.round(avant) + " % → " + Math.round(Math.max(0, Math.min(100, v))) + " %").ecrire();
                ok(s, c.name() + " : satiété " + Math.round(avant) + " % → " + Math.round(Math.max(0, Math.min(100, v))) + " %.");
            }
            case "donner" -> {
                if (a.length < 3) { err(s, "/nourriture donner <cible> <plat:id|panier:id|ration|bol> [n] [froid]"); return true; }
                int n = 1;
                if (a.length >= 4) try { n = Math.max(1, Math.min(16, Integer.parseInt(a[3]))); } catch (NumberFormatException ignored) { }
                boolean froid = a.length >= 5 && a[4].equalsIgnoreCase("froid");
                ItemStack it = objet(a[2], n, froid);
                if (it == null) { err(s, "Objet inconnu : " + a[2]); return true; }
                module.donnerSansBruit(cible, it);
                com.reborn.shinobicore.inventory.Notifs.objet(cible, true, it, n, froid ? "froid" : null);
                Journal.entree(Categorie.STAFF, "DONNER").acteur(s).cible(cible).detail(n + " × " + a[2] + (froid ? " (froid)" : "")).ecrire();
                ok(s, n + " × " + a[2] + " donné(s) à " + c.name() + ".");
            }
            case "effet" -> {
                if (a.length < 5) { err(s, "/nourriture effet <cible> <type> <intensité> <secondes>"); return true; }
                try {
                    double x = Double.parseDouble(a[3].replace(',', '.'));
                    long d = (long) (Double.parseDouble(a[4].replace(',', '.')) * 1000);
                    boolean pose = module.modifs().poser(cible, a[2], x, d, "staff " + s.getName());
                    Journal.entree(Categorie.STAFF, "MODIF_POSE").acteur(s).cible(cible)
                            .detail(a[2] + " " + x + " · " + d / 1000 + " s" + (pose ? "" : " (refusé)")).ecrire();
                    if (pose) ok(s, "Posé."); else err(s, "Refusé (type inconnu ou plus faible que l'actuel).");
                } catch (NumberFormatException e) { err(s, "Nombres ?"); }
            }
            case "retirer" -> {
                if (a.length < 3) { err(s, "/nourriture retirer <cible> <type>"); return true; }
                module.modifs().retirer(cible, a[2], "staff " + s.getName());
                Journal.entree(Categorie.STAFF, "MODIF_RETRAIT").acteur(s).cible(cible).detail(a[2]).ecrire();
                ok(s, "Retiré (s'il était actif).");
            }
            default -> err(s, "/nourriture etat|satiete|donner|effet|retirer …");
        }
        return true;
    }

    private void etat(CommandSender s, Player cible) {
        ShinobiCharacter c = plugin.characters().getActive(cible.getUniqueId());
        if (c == null) { err(s, "Pas de personnage actif."); return; }
        double sat = module.satiete().satiete(cible);
        int pause = module.satiete().pauseRegen(cible.getUniqueId());
        s.sendMessage(Component.text(c.name() + " — satiété " + Math.round(sat * 10) / 10.0 + " %"
                + (sat <= 0 ? " · FAIM" : sat < module.cfg().affame ? " · affamé" : "")
                + (pause > 0 ? " · régénération en pause " + pause + " s" : ""), NamedTextColor.GOLD));
        String ent = module.entreprises().resume(c.id());
        if (!ent.isEmpty()) s.sendMessage(Component.text("  Entreprise : " + ent, NamedTextColor.GRAY));
        for (ModificateurService.Vue v : module.modifs().actifs(cible.getUniqueId())) {
            TypeModificateur t = module.modifs().type(v.type());
            s.sendMessage(Component.text("  " + v.nom() + " " + (t == null ? "" : t.valeurAffichee(v.intensite()))
                    + " · " + v.resteMs() / 1000 + " s · " + v.source(), v.bonus() ? NamedTextColor.YELLOW : NamedTextColor.RED));
        }
    }

    private ItemStack objet(String spec, int n, boolean froid) {
        NourritureConfig k = module.cfg();
        String sp = spec.toLowerCase(Locale.ROOT);
        long chaud = froid ? 1 : System.currentTimeMillis() + k.chaudMs;
        if (sp.startsWith("plat:")) {
            NourritureConfig.Plat p = k.plats.get(sp.substring(5));
            return p == null ? null : module.plat(p, chaud, n);
        }
        if (sp.startsWith("panier:")) {
            NourritureConfig.Plat p = k.plats.get(sp.substring(7));
            return p == null ? null : NourritureItems.panier(p, n);
        }
        if (sp.equals("ration")) return NourritureItems.ration(k.ration, n);
        if (sp.equals("bol")) return NourritureItems.bolVide();
        NourritureConfig.Plat p = k.plats.get(sp);
        return p == null ? null : module.plat(p, chaud, n);
    }

    private static void ok(CommandSender s, String m) { s.sendMessage(Component.text(m, NamedTextColor.GREEN)); }

    private static void err(CommandSender s, String m) { s.sendMessage(Component.text(m, NamedTextColor.RED)); }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String al, @NotNull String[] a) {
        List<String> r = new ArrayList<>();
        if (!staff(s)) return r;
        if (a.length == 1) r.addAll(List.of("etat", "satiete", "donner", "effet", "retirer"));
        else if (a.length == 2) Bukkit.getOnlinePlayers().forEach(p -> r.add(p.getName()));
        else if (a.length == 3 && a[0].equalsIgnoreCase("donner")) {
            for (String id : module.cfg().plats.keySet()) { r.add("plat:" + id); r.add("panier:" + id); }
            r.add("ration");
            r.add("bol");
        } else if (a.length == 3 && (a[0].equalsIgnoreCase("effet") || a[0].equalsIgnoreCase("retirer"))) {
            module.modifs().types().forEach(t -> r.add(t.id()));
        }
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        r.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return r;
    }
}
