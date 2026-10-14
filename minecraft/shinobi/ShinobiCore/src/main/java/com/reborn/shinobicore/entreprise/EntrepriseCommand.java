package com.reborn.shinobicore.entreprise;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.CharacterService;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.character.PlayerIds;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.staff.panel.StaffGrades;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /entreprise} — gestion staff des entreprises (docs/gameplay/ENTREPRISES.md §3). Noms à plusieurs mots entre
 * guillemets ; cible = pseudo, nom RP ou {@code #N°}. Tout est journalisé (catégorie Staff). Sans argument, un joueur
 * voit ses propres entreprises.
 */
public final class EntrepriseCommand implements TabExecutor {

    public static final String PERM = "shinobicore.entreprise";

    private final ShinobiCore plugin;
    private final EntrepriseService service;

    public EntrepriseCommand(ShinobiCore plugin, EntrepriseService service) {
        this.plugin = plugin;
        this.service = service;
    }

    static boolean staff(CommandSender s) {
        if (!(s instanceof Player p)) return true;
        return p.isOp() || p.hasPermission(PERM) || StaffGrades.of(p) >= StaffGrades.MODO;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, @NotNull String[] raw) {
        List<String> a = jetons(raw);
        if (a.isEmpty() || !staff(s)) {
            mesEntreprises(s);
            return true;
        }
        String sub = a.get(0).toLowerCase(Locale.ROOT);
        switch (sub) {
            case "creer", "créer" -> creer(s, a);
            case "supprimer" -> supprimer(s, a);
            case "renommer" -> renommer(s, a);
            case "membre" -> membre(s, a);
            case "poste" -> poste(s, a);
            case "info" -> info(s, a);
            case "liste" -> liste(s, a);
            default -> aide(s);
        }
        return true;
    }

    /* ================================================================ sous-commandes */

    private void creer(CommandSender s, List<String> a) {
        if (a.size() < 3) { err(s, "/entreprise creer <restaurant|forge|echoppe> \"<nom>\" [responsable]"); return; }
        Entreprise.Type t = Entreprise.Type.parse(a.get(1));
        if (t == null) { err(s, "Type inconnu : " + a.get(1) + " (restaurant, forge, echoppe)."); return; }
        String nom = a.get(2);
        Entreprise e = service.creer(nom, t, s.getName());
        if (e == null) { err(s, "Une entreprise porte déjà ce nom (ou nom vide)."); return; }
        Journal.entree(Categorie.STAFF, "ENTREPRISE_CREER").acteur(s).cibleTexte(e.nom())
                .detail(t.libelle + " « " + e.nom() + " »").ecrire();
        ok(s, t.libelle + " « " + e.nom() + " » créé (id " + e.id() + ").");
        if (a.size() >= 4) ajouterMembre(s, e, a.get(3), Entreprise.Role.GERANT);
    }

    private void supprimer(CommandSender s, List<String> a) {
        Entreprise e = entreprise(s, a, 1);
        if (e == null) return;
        if (a.size() < 3 || !"confirmer".equalsIgnoreCase(a.get(2))) {
            err(s, "Supprimer « " + e.nom() + " » (" + e.membres().size() + " membres, " + e.postes().size()
                    + " postes) : /entreprise supprimer " + e.id() + " confirmer");
            return;
        }
        service.supprimer(e);
        Journal.entree(Categorie.STAFF, "ENTREPRISE_SUPPRIMER").acteur(s).cibleTexte(e.nom())
                .detail(e.type().libelle + " · " + e.membres().size() + " membres").ecrire();
        ok(s, "« " + e.nom() + " » supprimée.");
    }

    private void renommer(CommandSender s, List<String> a) {
        Entreprise e = entreprise(s, a, 1);
        if (e == null) return;
        if (a.size() < 3) { err(s, "/entreprise renommer <entreprise> \"<nouveau nom>\""); return; }
        String avant = e.nom();
        if (!service.renommer(e, a.get(2))) { err(s, "Nom déjà pris ou vide."); return; }
        Journal.entree(Categorie.STAFF, "ENTREPRISE_RENOMMER").acteur(s).cibleTexte(e.nom())
                .detail("« " + avant + " » → « " + e.nom() + " »").ecrire();
        ok(s, "« " + avant + " » s'appelle maintenant « " + e.nom() + " ».");
    }

    private void membre(CommandSender s, List<String> a) {
        if (a.size() < 4) {
            err(s, "/entreprise membre <ajouter|retirer|role> <entreprise> <pseudo|nom RP|#N°> [gerant|employe]");
            return;
        }
        String op = a.get(1).toLowerCase(Locale.ROOT);
        Entreprise e = entreprise(s, a, 2);
        if (e == null) return;
        switch (op) {
            case "ajouter" -> {
                Entreprise.Role r = a.size() >= 5 ? Entreprise.Role.parse(a.get(4)) : Entreprise.Role.EMPLOYE;
                if (r == null) { err(s, "Rôle : gerant ou employe."); return; }
                ajouterMembre(s, e, a.get(3), r);
            }
            case "retirer" -> {
                CharacterService.ResolvedCharacter rc = PlayerIds.character(s, a.get(3));
                Entreprise.Membre m = rc == null ? membreParNom(e, a.get(3)) : e.membre(rc.character().id());
                if (m == null) { err(s, "Pas membre de « " + e.nom() + " » : " + a.get(3)); return; }
                service.retirerMembre(e, m.perso());
                Journal.entree(Categorie.STAFF, "ENTREPRISE_MEMBRE_RETIRER").acteur(s)
                        .ciblePersonnage(m.compte(), m.perso()).detail(e.nom() + " · était " + m.role().libelle).ecrire();
                ok(s, m.nom() + " ne fait plus partie de « " + e.nom() + " ».");
            }
            case "role", "rôle" -> {
                Entreprise.Role r = a.size() >= 5 ? Entreprise.Role.parse(a.get(4)) : null;
                if (r == null) { err(s, "/entreprise membre role <entreprise> <cible> <gerant|employe>"); return; }
                CharacterService.ResolvedCharacter rc = PlayerIds.character(s, a.get(3));
                Entreprise.Membre m = rc == null ? membreParNom(e, a.get(3)) : e.membre(rc.character().id());
                if (m == null || !service.role(e, m.perso(), r)) { err(s, "Pas membre de « " + e.nom() + " »."); return; }
                Journal.entree(Categorie.STAFF, "ENTREPRISE_ROLE").acteur(s).ciblePersonnage(m.compte(), m.perso())
                        .detail(e.nom() + " · " + m.role().libelle + " → " + r.libelle).ecrire();
                ok(s, m.nom() + " est " + r.libelle + " de « " + e.nom() + " ».");
            }
            default -> err(s, "membre ajouter | retirer | role");
        }
    }

    private void ajouterMembre(CommandSender s, Entreprise e, String cible, Entreprise.Role r) {
        CharacterService.ResolvedCharacter rc = PlayerIds.character(s, cible);
        if (rc == null || rc.character() == null) { err(s, "Personnage introuvable : " + cible); return; }
        ShinobiCharacter c = rc.character();
        service.membre(e, rc.ownerId(), c, r);
        Journal.entree(Categorie.STAFF, "ENTREPRISE_MEMBRE_AJOUTER").acteur(s).ciblePersonnage(rc.ownerId(), c.id())
                .detail(e.nom() + " · " + r.libelle).ecrire();
        ok(s, PlayerIds.prefix(c) + c.name() + " est " + r.libelle + " de « " + e.nom() + " »"
                + (e.type() == Entreprise.Type.RESTAURANT ? " (cuisinier)." : "."));
        Player en = Bukkit.getPlayer(rc.ownerId());
        if (en != null) {
            en.sendMessage(Component.text("Tu fais maintenant partie de « " + e.nom() + " » (" + r.libelle + ").",
                    NamedTextColor.GOLD));
        }
    }

    private void poste(CommandSender s, List<String> a) {
        if (!(s instanceof Player p)) { err(s, "Vise le bloc ou le meuble en jeu."); return; }
        if (a.size() < 2) { err(s, "/entreprise poste <ajouter <entreprise> [libellé]|retirer>"); return; }
        Location l = vise(p);
        if (l == null) { err(s, "Vise un bloc ou un meuble à moins de 6 blocs."); return; }
        String op = a.get(1).toLowerCase(Locale.ROOT);
        if (op.equals("ajouter")) {
            Entreprise e = entreprise(s, a, 2);
            if (e == null) return;
            var deja = service.posteA(l, 0);
            if (deja != null) { err(s, "Déjà un poste ici (" + deja.getKey().nom() + ")."); return; }
            Entreprise.Poste po = service.ajouterPoste(e, l, a.size() >= 4 ? a.get(3) : null);
            Journal.entree(Categorie.STAFF, "ENTREPRISE_POSTE_AJOUTER").acteur(s).cibleTexte(e.nom())
                    .detail(po.libelle() + " · " + po.court()).lieu(l).ecrire();
            ok(s, "Poste « " + po.libelle() + " » ajouté à « " + e.nom() + " » (" + po.court() + ").");
            p.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, l.clone().add(0.5, 1.1, 0.5), 12, 0.3, 0.2, 0.3);
        } else if (op.equals("retirer")) {
            var trouve = service.posteA(l, 1);
            if (trouve == null) { err(s, "Aucun poste ici."); return; }
            Entreprise e = trouve.getKey();
            Location pl = trouve.getValue().centre(l.getWorld());
            service.retirerPoste(e, pl);
            Journal.entree(Categorie.STAFF, "ENTREPRISE_POSTE_RETIRER").acteur(s).cibleTexte(e.nom())
                    .detail(trouve.getValue().libelle() + " · " + trouve.getValue().court()).lieu(l).ecrire();
            ok(s, "Poste retiré de « " + e.nom() + " ».");
        } else {
            err(s, "poste ajouter | retirer");
        }
    }

    private void info(CommandSender s, List<String> a) {
        Entreprise e = entreprise(s, a, 1);
        if (e == null) return;
        s.sendMessage(Component.text("« " + e.nom() + " » — " + e.type().libelle + " (id " + e.id() + ")", NamedTextColor.GOLD));
        s.sendMessage(Component.text("  Créée le " + new SimpleDateFormat("dd/MM/yyyy").format(new Date(e.creeLe()))
                + " par " + e.creePar(), NamedTextColor.GRAY));
        if (e.membres().isEmpty()) s.sendMessage(Component.text("  Aucun membre.", NamedTextColor.GRAY));
        for (Entreprise.Membre m : e.membres()) {
            boolean resp = m.perso().equals(e.responsable());
            s.sendMessage(Component.text("  " + (m.numero() >= 0 ? "#" + PlayerIds.format(m.numero()) + " " : "") + m.nom()
                    + " — " + m.role().libelle + (resp ? " (responsable)" : ""), NamedTextColor.YELLOW));
        }
        for (Entreprise.Poste p : e.postes()) {
            s.sendMessage(Component.text("  Poste : " + p.libelle() + " · " + p.monde() + " " + p.court(), NamedTextColor.GRAY));
        }
    }

    private void liste(CommandSender s, List<String> a) {
        Entreprise.Type filtre = a.size() >= 2 ? Entreprise.Type.parse(a.get(1)) : null;
        List<Entreprise> l = service.toutes();
        if (l.isEmpty()) { s.sendMessage(Component.text("Aucune entreprise.", NamedTextColor.GRAY)); return; }
        for (Entreprise e : l) {
            if (filtre != null && e.type() != filtre) continue;
            s.sendMessage(Component.text("« " + e.nom() + " » · " + e.type().libelle + " · " + e.membres().size()
                    + " membre(s) · " + e.postes().size() + " poste(s) · id " + e.id(), NamedTextColor.YELLOW));
        }
    }

    private void mesEntreprises(CommandSender s) {
        if (!(s instanceof Player p)) { aide(s); return; }
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        List<Entreprise> l = c == null ? List.of() : service.de(c.id());
        if (l.isEmpty()) {
            s.sendMessage(Component.text("Tu ne fais partie d'aucune entreprise.", NamedTextColor.GRAY));
            return;
        }
        for (Entreprise e : l) {
            s.sendMessage(Component.text("« " + e.nom() + " » · " + e.type().libelle + " · " + e.membre(c.id()).role().libelle,
                    NamedTextColor.GOLD));
        }
    }

    private void aide(CommandSender s) {
        s.sendMessage(Component.text("/entreprise creer <type> \"<nom>\" [responsable] · supprimer <e> confirmer · "
                + "renommer <e> \"<nom>\" · membre ajouter|retirer|role <e> <cible> [gerant|employe] · "
                + "poste ajouter <e> [libellé] | poste retirer (en visant) · info <e> · liste [type]", NamedTextColor.YELLOW));
    }

    /* ================================================================ outils */

    private Entreprise entreprise(CommandSender s, List<String> a, int i) {
        if (a.size() <= i) { err(s, "Quelle entreprise ? (/entreprise liste)"); return null; }
        Entreprise e = service.trouver(a.get(i));
        if (e == null) err(s, "Entreprise inconnue : " + a.get(i));
        return e;
    }

    private static Entreprise.Membre membreParNom(Entreprise e, String nom) {
        int n = PlayerIds.parse(nom);
        for (Entreprise.Membre m : e.membres()) {
            if (n >= 0 && m.numero() == n) return m;
            if (m.nom().equalsIgnoreCase(nom)) return m;
        }
        return null;
    }

    /** Le bloc visé, ou le bloc d'un meuble (entité d'affichage / d'interaction Nexo) s'il est plus proche. */
    static Location vise(Player p) {
        Block b = p.getTargetBlockExact(6);
        Entity en = null;
        try {
            en = p.getTargetEntity(6, true);
        } catch (Throwable ignored) {
            // API Paper absente
        }
        Location lb = b == null ? null : b.getLocation();
        Location le = en == null || en instanceof Player ? null : en.getLocation().getBlock().getLocation();
        if (le != null && (lb == null || le.distanceSquared(p.getEyeLocation()) <= lb.distanceSquared(p.getEyeLocation()))) return le;
        return lb;
    }

    /** Découpe en jetons en respectant les guillemets ("…" ou «…»). */
    static List<String> jetons(String[] raw) {
        String all = String.join(" ", raw);
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        char fin = 0;
        for (char ch : all.toCharArray()) {
            if (fin != 0) {
                if (ch == fin) { out.add(cur.toString().trim()); cur.setLength(0); fin = 0; }
                else cur.append(ch);
            } else if (ch == '"' || ch == '«') {
                if (!cur.isEmpty()) { out.add(cur.toString()); cur.setLength(0); }
                fin = ch == '"' ? '"' : '»';
            } else if (ch == ' ') {
                if (!cur.isEmpty()) { out.add(cur.toString()); cur.setLength(0); }
            } else cur.append(ch);
        }
        if (!cur.isEmpty()) out.add(cur.toString().trim());
        out.removeIf(String::isEmpty);
        return out;
    }

    private static void ok(CommandSender s, String m) { s.sendMessage(Component.text(m, NamedTextColor.GREEN)); }

    private static void err(CommandSender s, String m) { s.sendMessage(Component.text(m, NamedTextColor.RED)); }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String alias,
                                      @NotNull String[] a) {
        if (!staff(s)) return List.of();
        List<String> r = new ArrayList<>();
        if (a.length == 1) r.addAll(List.of("creer", "supprimer", "renommer", "membre", "poste", "info", "liste"));
        else if (a.length == 2) {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "creer" -> r.addAll(List.of("restaurant", "forge", "echoppe"));
                case "membre" -> r.addAll(List.of("ajouter", "retirer", "role"));
                case "poste" -> r.addAll(List.of("ajouter", "retirer"));
                case "liste" -> r.addAll(List.of("restaurant", "forge", "echoppe"));
                default -> service.toutes().forEach(e -> r.add(e.id()));
            }
        } else if (a.length == 3 && (a[0].equalsIgnoreCase("membre") || a[0].equalsIgnoreCase("poste"))) {
            service.toutes().forEach(e -> r.add(e.id()));
        } else if (a.length == 4 && a[0].equalsIgnoreCase("membre")) {
            for (Player p : Bukkit.getOnlinePlayers()) r.add(p.getName());
        } else if (a.length == 5 && a[0].equalsIgnoreCase("membre")) {
            r.addAll(List.of("gerant", "employe"));
        }
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        r.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return r;
    }

    /** Pour les messages d'autres classes. */
    static String nomPoste(Map.Entry<Entreprise, Entreprise.Poste> e) {
        return e.getValue().libelle() + " de « " + e.getKey().nom() + " »";
    }
}
