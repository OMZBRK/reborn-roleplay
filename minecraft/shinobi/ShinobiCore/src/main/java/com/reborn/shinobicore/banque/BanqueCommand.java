package com.reborn.shinobicore.banque;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.CharacterService;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.character.PlayerIdRegistry;
import com.reborn.shinobicore.character.PlayerIds;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.inventory.RareteItems;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.monnaie.Arguments;
import com.reborn.shinobicore.monnaie.Bourse;
import com.reborn.shinobicore.monnaie.MonnaieCanal;
import com.reborn.shinobicore.monnaie.MonnaieConfig;
import com.reborn.shinobicore.monnaie.MonnaieModule;
import com.reborn.shinobicore.staff.panel.StaffGrades;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /banque} et {@code /testament} (MONNAIE_ET_BANQUE.md §11, décision 4 du 15/10). Cible = pseudo, nom RP ou
 * {@code #N°} ; motif obligatoire pour tout ce qui change un montant ou un état. Grades : lecture Modérateur et plus ;
 * le reste Admin et plus (ou {@code shinobicore.banque.admin}). Tout est journalisé, y compris la consultation.
 */
public final class BanqueCommand implements TabExecutor, Listener {

    private final ShinobiCore plugin;
    private final MonnaieModule module;

    public BanqueCommand(ShinobiCore plugin, MonnaieModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    private BanqueService banque() { return module.banque(); }

    private static int grade(CommandSender s) {
        if (!(s instanceof Player p)) return StaffGrades.OWNER;
        int g = StaffGrades.of(p);
        if (p.hasPermission("shinobicore.banque.admin")) g = Math.max(g, StaffGrades.ADMIN);
        return g;
    }

    private static void ok(CommandSender s, String t) { s.sendMessage(Component.text(t, NamedTextColor.GREEN)); }
    private static void info(CommandSender s, String t) { s.sendMessage(Component.text(t, NamedTextColor.GRAY)); }
    private static void err(CommandSender s, String t) { s.sendMessage(Component.text(t, NamedTextColor.RED)); }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, @NotNull String[] raw) {
        List<String> a = Arguments.lire(raw);
        if (cmd.getName().equalsIgnoreCase("testament")) return testament(s, a);
        if (a.isEmpty()) {
            info(s, "Ton compte se consulte au guichet de la banque de ton village (clic droit sur le guichet).");
            if (grade(s) >= StaffGrades.MODO) info(s, "Staff : /banque voir|coffre|palier|geler|degeler|guichet|contrepasser|masse|solde|tresor");
            return true;
        }
        String sub = a.get(0).toLowerCase(Locale.ROOT);
        int g = grade(s);
        switch (sub) {
            case "sceller", "refuser" -> {
                if (!(s instanceof Player p)) return true;
                String r = module.echanges().sceller(p, sub.equals("sceller"));
                if (r != null) err(s, r);
                return true;
            }
            case "client" -> {
                if (!(s instanceof Player p) || a.size() < 2) { err(s, "/banque client <joueur>"); return true; }
                Player c = PlayerIds.online(s, a.get(1));
                if (c == null) c = plugin.characters().findOnlinePlayerByCharacter(a.get(1));
                if (c == null) { err(s, "Personne ici."); return true; }
                String r = module.guichetCanal().ouvrirPourClient(p, c);
                if (r != null) err(s, r); else ok(s, "Compte ouvert pour ton client : il valide lui-même.");
                return true;
            }
            default -> { }
        }
        if (g < StaffGrades.MODO) { err(s, "Réservé au staff."); return true; }
        switch (sub) {
            case "voir" -> voir(s, a);
            case "coffre" -> { if (g >= StaffGrades.ADMIN) coffre(s, a); else err(s, "Réservé aux admins."); }
            case "palier" -> { if (g >= StaffGrades.ADMIN) palier(s, a); else err(s, "Réservé aux admins."); }
            case "geler", "degeler" -> { if (g >= StaffGrades.ADMIN) geler(s, a, sub.equals("geler")); else err(s, "Réservé aux admins."); }
            case "guichet" -> { if (g >= StaffGrades.ADMIN) guichet(s, a); else err(s, "Réservé aux admins."); }
            case "contrepasser" -> {
                if (g < StaffGrades.ADMIN) { err(s, "Réservé aux admins."); return true; }
                if (a.size() < 3) { err(s, "/banque contrepasser <n° d'opération> \"<raison>\""); return true; }
                String r = banque().contrepasser(s, MonnaieCanal.lire(a.get(1)), Arguments.reste(a, 2));
                if (r != null) err(s, r); else ok(s, "Opération " + a.get(1) + " contre-passée.");
            }
            case "masse" -> {
                if (g < StaffGrades.ADMIN) { err(s, "Réservé aux admins."); return true; }
                String v = a.size() > 1 ? a.get(1) : null;
                BanqueService.Masse m = banque().masse(v);
                info(s, "Masse " + (v == null ? "(tous villages)" : MonnaieConfig.village(v)) + " · " + m.n() + " compte(s) : bourses "
                        + Bourse.fmt(m.bourses()) + " · comptes " + Bourse.fmt(m.comptes()) + " · trésor " + Bourse.fmt(m.tresor())
                        + " = " + Bourse.fmt(m.bourses() + m.comptes() + m.tresor()) + " ryo (bourses hors ligne : dernier montant connu).");
            }
            case "solde" -> {
                if (g < StaffGrades.ADMIN) { err(s, "Réservé aux admins."); return true; }
                if (a.size() > 1 && a.get(1).equalsIgnoreCase("payer")) {
                    String v = a.size() > 2 ? MonnaieConfig.village(a.get(2)) : null;
                    ok(s, module.soldes().payer(s, v));
                } else {
                    StringBuilder b = new StringBuilder("Barème (par semaine) : ");
                    MonnaieConfig.get().bareme.forEach((r, n) -> b.append(r.displayName()).append(" ").append(Bourse.fmt(n)).append(" · "));
                    b.append("guichetier +").append(MonnaieConfig.get().fonctionBanque).append(" · présence ")
                            .append(MonnaieConfig.get().presenceHeures).append(" h · ").append(MonnaieConfig.get().soldeJour)
                            .append(" ").append(MonnaieConfig.get().soldeHeure).append(" h.");
                    info(s, b.toString());
                }
            }
            case "tresor" -> {
                if (g < StaffGrades.ADMIN) { err(s, "Réservé aux admins."); return true; }
                if (a.size() < 2) { err(s, "/banque tresor <village> [ajouter|retirer|fixer <montant> \"<motif>\"]"); return true; }
                if (a.size() < 5) { info(s, "Trésor de " + MonnaieConfig.village(a.get(1)) + " : " + Bourse.fmt(banque().tresor(a.get(1))) + " ryo."); return true; }
                long n = MonnaieCanal.lire(a.get(3));
                if (n < 0) { err(s, "Montant invalide."); return true; }
                long apres = banque().tresorVarier(s, a.get(1), a.get(2).toLowerCase(Locale.ROOT), n, Arguments.reste(a, 4));
                ok(s, "Trésor de " + MonnaieConfig.village(a.get(1)) + " : " + Bourse.fmt(apres) + " ryo.");
            }
            default -> info(s, "/banque voir|coffre|palier|geler|degeler|guichet|contrepasser|masse|solde|tresor");
        }
        return true;
    }

    private CharacterService.ResolvedCharacter cible(CommandSender s, List<String> a, int i) {
        if (a.size() <= i) { err(s, "Cible manquante (pseudo, nom RP ou #N°)."); return null; }
        CharacterService.ResolvedCharacter rc = PlayerIds.character(s, a.get(i));
        if (rc == null) err(s, "Personnage introuvable : " + a.get(i));
        return rc;
    }

    private void voir(CommandSender s, List<String> a) {
        CharacterService.ResolvedCharacter rc = cible(s, a, 1);
        if (rc == null) return;
        ShinobiCharacter c = rc.character();
        Compte cp = banque().compte(c);
        MonnaieConfig.Palier pa = MonnaieConfig.get().palier(cp.palier);
        Player p = Bukkit.getPlayer(rc.ownerId());
        ShinobiCharacter act = p == null ? null : plugin.characters().getActive(p.getUniqueId());
        long bourse = act != null && act.id().equals(c.id()) ? module.bourses().montant(p) : cp.derniereBourse;
        info(s, "— " + PlayerIds.prefix(c) + c.name() + " · " + cp.village + (cp.gele() ? " · GELÉ (" + cp.gelMotif + ")" : "")
                + (cp.mortLe > 0 ? " · mort RP" + (cp.regle ? " (réglé)" : " (gelé)") : ""));
        info(s, "Bourse " + Bourse.fmt(bourse) + " · compte " + Bourse.fmt(cp.solde) + " · palier " + cp.palier + " (" + pa.nom()
                + ", plafond " + (pa.plafond() > 0 ? Bourse.fmt(pa.plafond()) : "aucun") + ") · coffre " + cp.occupees() + "/" + cp.coffre.length);
        List<String> co = new ArrayList<>();
        for (ItemStack it : cp.coffre) if (it != null) co.add(it.getAmount() + "× " + RareteItems.nom(it));
        if (!co.isEmpty()) info(s, "Coffre : " + String.join(", ", co));
        SimpleDateFormat f = new SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE);
        for (Compte.Ligne l : cp.historique) info(s, "  " + f.format(new Date(l.le())) + "  " + (l.delta() >= 0 ? "+" : "") + Bourse.fmt(l.delta()) + "  " + l.texte());
        BanqueStore.Testament t = banque().testament(c.id());
        if (t != null) info(s, "Testament " + (t.valide() ? "validé" : "déposé") + " · " + heritiers(t));
        Journal.entree(Categorie.ECONOMIE, "CONSULTATION").acteur(s).ciblePersonnage(rc.ownerId(), c.id())
                .detail("/banque voir").ecrire();
    }

    private static String heritiers(BanqueStore.Testament t) {
        List<String> l = new ArrayList<>();
        for (BanqueStore.Heritier h : t.heritiers()) l.add(h.nom() + " " + h.part() + " %");
        return String.join(", ", l);
    }

    /* ================================================================ coffre (staff) */

    /** Vue staff du coffre d'un compte : un coffre vanilla ; les retraits sont relus à la fermeture et journalisés. */
    private static final class CoffreVue implements InventoryHolder {
        final UUID perso;
        final ItemStack[] avant;
        Inventory inv;
        CoffreVue(UUID perso, ItemStack[] avant) { this.perso = perso; this.avant = avant; }
        @Override public @NotNull Inventory getInventory() { return inv; }
    }

    private void coffre(CommandSender s, List<String> a) {
        if (!(s instanceof Player p)) { err(s, "En jeu seulement."); return; }
        CharacterService.ResolvedCharacter rc = cible(s, a, 1);
        if (rc == null) return;
        Compte cp = banque().compte(rc.character());
        int taille = Math.max(9, ((cp.coffre.length + 8) / 9) * 9);
        CoffreVue v = new CoffreVue(cp.perso, cp.coffre.clone());
        Inventory inv = Bukkit.createInventory(v, taille, Component.text("Coffre de " + cp.nom));
        v.inv = inv;
        for (int i = 0; i < cp.coffre.length; i++) if (cp.coffre[i] != null) inv.setItem(i, cp.coffre[i].clone());
        p.openInventory(inv);
        Journal.entree(Categorie.ECONOMIE, "CONSULTATION").acteur(s).ciblePersonnage(rc.ownerId(), cp.perso)
                .detail("/banque coffre (ouvert)").ecrire();
    }

    @EventHandler
    public void onFermeCoffre(InventoryCloseEvent ev) {
        if (!(ev.getInventory().getHolder() instanceof CoffreVue v)) return;
        Compte cp = banque().compte(v.perso);
        if (cp == null) return;
        ItemStack[] n = new ItemStack[cp.coffre.length];
        List<String> diff = new ArrayList<>();
        for (int i = 0; i < n.length; i++) {
            n[i] = ev.getInventory().getItem(i);
            ItemStack o = i < v.avant.length ? v.avant[i] : null;
            if (o != null && (n[i] == null || !n[i].isSimilar(o) || n[i].getAmount() != o.getAmount())) diff.add("retiré " + o.getAmount() + "× " + RareteItems.nom(o));
            if (n[i] != null && (o == null || !n[i].isSimilar(o))) diff.add("ajouté " + n[i].getAmount() + "× " + RareteItems.nom(n[i]));
        }
        for (int i = n.length; i < ev.getInventory().getSize(); i++) {
            ItemStack x = ev.getInventory().getItem(i);
            if (x != null) ev.getPlayer().getInventory().addItem(x);
        }
        cp.coffre = n;
        banque().store.marquer();
        if (!diff.isEmpty()) Journal.entree(Categorie.ECONOMIE, "STAFF").acteur(ev.getPlayer()).ciblePersonnage(cp.owner, cp.perso)
                .detail("/banque coffre · " + String.join(", ", diff)).ecrire();
    }

    /* ================================================================ palier, gel, guichets */

    private void palier(CommandSender s, List<String> a) {
        CharacterService.ResolvedCharacter rc = cible(s, a, 1);
        if (rc == null) return;
        if (a.size() < 4) { err(s, "/banque palier <cible> <1-" + MonnaieConfig.get().palierMax() + "> \"<motif>\""); return; }
        Compte cp = banque().compte(rc.character());
        banque().staffPalier(s, cp, (int) MonnaieCanal.lire(a.get(2)), Arguments.reste(a, 3));
        ok(s, cp.nom + " : palier " + cp.palier + ".");
    }

    private void geler(CommandSender s, List<String> a, boolean geler) {
        CharacterService.ResolvedCharacter rc = cible(s, a, 1);
        if (rc == null) return;
        String motif = Arguments.reste(a, 2);
        if (motif.isBlank()) { err(s, "Le motif est obligatoire."); return; }
        Compte cp = banque().compte(rc.character());
        if (geler) banque().geler(s, cp, motif); else banque().degeler(s, cp, motif);
        ok(s, cp.nom + " : compte " + (geler ? "gelé." : "dégelé."));
    }

    private void guichet(CommandSender s, List<String> a) {
        String op = a.size() > 1 ? a.get(1).toLowerCase(Locale.ROOT) : "liste";
        if (op.equals("liste")) {
            List<BanqueStore.Guichet> l = module.guichets().liste();
            if (l.isEmpty()) info(s, "Aucun guichet. Vise un bloc, un meuble ou un PNJ : /banque guichet ajouter <village> [\"libellé\"] [entreprise]");
            for (BanqueStore.Guichet g : l)
                info(s, g.id() + " · " + g.village() + " · « " + g.libelle() + " » · " + g.type() + " " + g.monde() + " " + g.x() + " "
                        + g.y() + " " + g.z() + (g.entreprise().isEmpty() ? "" : " · entreprise " + g.entreprise()));
            return;
        }
        if (!(s instanceof Player p)) { err(s, "En jeu seulement (vise le guichet)."); return; }
        Entity e = p.getTargetEntity(6);
        Block b = e == null ? p.getTargetBlockExact(6) : null;
        if (e == null && b == null) { err(s, "Vise un bloc, un meuble ou un PNJ à 6 blocs."); return; }
        if (op.equals("ajouter")) {
            if (a.size() < 3) { err(s, "/banque guichet ajouter <village> [\"libellé\"] [entreprise]"); return; }
            BanqueStore.Guichet g = module.guichets().ajouter(b, e, a.get(2), a.size() > 3 ? a.get(3) : null, a.size() > 4 ? a.get(4) : null);
            Journal.entree(Categorie.STAFF, "GUICHET_AJOUTER").acteur(s).detail(g.id() + " · " + g.village() + " · « " + g.libelle() + " »")
                    .lieu(Guichets.centre(g)).ecrire();
            ok(s, "Guichet " + g.id() + " ajouté (" + g.libelle() + ").");
        } else if (op.equals("retirer")) {
            BanqueStore.Guichet g = module.guichets().retirer(b, e);
            if (g == null) { err(s, "Ce n'est pas un guichet."); return; }
            Journal.entree(Categorie.STAFF, "GUICHET_RETIRER").acteur(s).detail(g.id()).ecrire();
            ok(s, "Guichet " + g.id() + " retiré.");
        } else err(s, "/banque guichet ajouter|retirer|liste");
    }

    /* ================================================================ testament */

    /**
     * {@code /testament} — un testament est un document RP <b>enregistré au guichet</b> (déposé par le joueur ou le
     * guichetier) et <b>validé par le staff</b> (vu en RP) : il désigne un ou plusieurs héritiers (parts en %). À la
     * mort RP, après le gel de 7 jours : versé aux héritiers au lieu du trésor.
     * <ul>
     *   <li>{@code /testament deposer <héritier>[:part] … ["texte"]} — le joueur, au guichet (héritier = {@code #N°} ou
     *       nom RP d'un contact) : statut « déposé » ;</li>
     *   <li>{@code /testament enregistrer <cible> <héritier>[:part] … ["texte"]} — staff : enregistré et validé ;</li>
     *   <li>{@code /testament valider|revoquer|voir <cible>} — staff.</li>
     * </ul>
     */
    private boolean testament(CommandSender s, List<String> a) {
        String sub = a.isEmpty() ? "voir" : a.get(0).toLowerCase(Locale.ROOT);
        int g = grade(s);
        switch (sub) {
            case "deposer" -> {
                if (!(s instanceof Player p)) return true;
                ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
                if (c == null) return true;
                if (module.guichets().proche(p.getLocation(), MonnaieConfig.get().porteeGuichet) == null) {
                    err(s, "Un testament se dépose au guichet de la banque."); return true;
                }
                List<BanqueStore.Heritier> hs = heritiers(s, c, a, 1);
                if (hs == null) return true;
                banque().deposerTestament(s, c, hs, texte(a), false);
                ok(s, "Testament déposé au guichet. Il sera valable une fois validé par le staff (vu en RP).");
            }
            case "enregistrer" -> {
                if (g < StaffGrades.ADMIN) { err(s, "Réservé aux admins."); return true; }
                CharacterService.ResolvedCharacter rc = cible(s, a, 1);
                if (rc == null) return true;
                List<BanqueStore.Heritier> hs = heritiers(s, rc.character(), a, 2);
                if (hs == null) return true;
                banque().deposerTestament(s, rc.character(), hs, texte(a), true);
                ok(s, "Testament de " + rc.character().name() + " enregistré et validé.");
            }
            case "valider", "revoquer" -> {
                if (g < StaffGrades.ADMIN) { err(s, "Réservé aux admins."); return true; }
                CharacterService.ResolvedCharacter rc = cible(s, a, 1);
                if (rc == null) return true;
                String r = banque().validerTestament(s, rc.character().id(), sub.equals("valider"));
                if (r != null) err(s, r); else ok(s, "Testament " + (sub.equals("valider") ? "validé." : "révoqué."));
            }
            default -> {
                if (g < StaffGrades.MODO) {
                    if (s instanceof Player p) {
                        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
                        BanqueStore.Testament t = c == null ? null : banque().testament(c.id());
                        info(s, t == null ? "Aucun testament. Il se dépose au guichet : /testament deposer <héritier>[:part] … \"texte\""
                                : "Ton testament est " + (t.valide() ? "validé" : "déposé (en attente du staff)") + " · " + heritiers(t));
                    }
                    return true;
                }
                CharacterService.ResolvedCharacter rc = a.size() > 1 ? cible(s, a, 1) : null;
                if (rc == null) { info(s, "/testament voir|enregistrer|valider|revoquer <cible> …"); return true; }
                BanqueStore.Testament t = banque().testament(rc.character().id());
                info(s, t == null ? "Aucun testament." : "Testament " + (t.valide() ? "validé par " + t.validePar() : "déposé")
                        + " · " + heritiers(t) + (t.texte().isBlank() ? "" : " · « " + t.texte() + " »"));
            }
        }
        return true;
    }

    /** Texte libre : le dernier argument s'il contient une espace (entre guillemets), sinon vide. */
    private static String texte(List<String> a) {
        String last = a.isEmpty() ? "" : a.get(a.size() - 1);
        return last.contains(" ") ? last : "";
    }

    private List<BanqueStore.Heritier> heritiers(CommandSender s, ShinobiCharacter de, List<String> a, int from) {
        List<BanqueStore.Heritier> out = new ArrayList<>();
        int sans = 0, total = 0;
        for (int i = from; i < a.size(); i++) {
            String x = a.get(i);
            if (x.contains(" ")) break;     // le texte
            String ref = x;
            int part = -1;
            int k = x.lastIndexOf(':');
            if (k > 0) { ref = x.substring(0, k); part = (int) MonnaieCanal.lire(x.substring(k + 1).replace("%", "")); }
            ShinobiCharacter h = null;
            int n = PlayerIdRegistry.parse(ref.startsWith("#") ? ref : "#" + ref);
            if (n >= 0) { var rc = plugin.characters().findByPlayerId(n); if (rc != null) h = rc.character(); }
            if (h == null) {
                for (var e : de.contacts().entrySet()) {
                    if (e.getValue().display().equalsIgnoreCase(ref)) { var rc = plugin.characters().resolveOwnedById(e.getKey()); if (rc != null) h = rc.character(); }
                }
            }
            if (h == null && grade(s) >= StaffGrades.MODO) { var rc = PlayerIds.character(s, ref); if (rc != null) h = rc.character(); }
            if (h == null) { err(s, "Héritier introuvable : " + ref + " (N° ou nom d'un contact)."); return null; }
            if (h.id().equals(de.id())) { err(s, "On n'hérite pas de soi-même."); return null; }
            if (part < 0) sans++; else total += part;
            out.add(new BanqueStore.Heritier(h.id(), h.ownerId(), h.name(), part));
        }
        if (out.isEmpty()) { err(s, "Au moins un héritier : /testament … <héritier>[:part] …"); return null; }
        int reste = Math.max(0, 100 - total);
        List<BanqueStore.Heritier> fin = new ArrayList<>();
        int i = 0;
        for (BanqueStore.Heritier h : out) {
            int p = h.part() >= 0 ? h.part() : (sans == 0 ? 0 : reste / sans + (i++ == 0 ? reste % sans : 0));
            fin.add(new BanqueStore.Heritier(h.perso(), h.owner(), h.nom(), p));
        }
        int somme = fin.stream().mapToInt(BanqueStore.Heritier::part).sum();
        if (somme != 100) { err(s, "Les parts doivent faire 100 % (ici " + somme + " %)."); return null; }
        return fin;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String al, @NotNull String[] a) {
        List<String> r = new ArrayList<>();
        if (c.getName().equalsIgnoreCase("testament")) {
            if (a.length == 1) r.addAll(grade(s) >= StaffGrades.MODO ? List.of("voir", "deposer", "enregistrer", "valider", "revoquer") : List.of("voir", "deposer"));
        } else if (a.length == 1) {
            r.addAll(List.of("sceller", "refuser", "client"));
            if (grade(s) >= StaffGrades.MODO) r.addAll(List.of("voir", "coffre", "palier", "geler", "degeler", "guichet", "contrepasser", "masse", "solde", "tresor"));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("guichet")) r.addAll(List.of("ajouter", "retirer", "liste"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("solde")) r.addAll(List.of("bareme", "payer"));
        else if (a.length == 2) for (Player p : Bukkit.getOnlinePlayers()) r.add(p.getName());
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        r.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return r;
    }
}
