package com.reborn.shinobicore.boutique;

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
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /boutique} — les boutiques-lieux côté staff (BOUTIQUES.md §9, décision du 15/10 : des comptoirs à catégories
 * configurables). Noms à plusieurs mots entre guillemets ; cible = pseudo, nom RP ou {@code #N°}. Tout est journalisé
 * (catégorie Staff). Permission {@code shinobicore.boutique}, opérateur, ou Modérateur du Poste de garde et au-dessus.
 */
public final class BoutiqueCommand implements TabExecutor {

    public static final String PERM = "shinobicore.boutique";
    private static final List<String> SOUS = List.of("creer", "supprimer", "comptoir", "estrade", "camera", "article",
            "ouvrir", "fermer", "info", "liste", "voir", "donner", "reprendre", "recharger");

    private final ShinobiCore plugin;
    private final BoutiqueService service;

    BoutiqueCommand(ShinobiCore plugin, BoutiqueService service) {
        this.plugin = plugin;
        this.service = service;
    }

    static boolean staff(CommandSender s) {
        if (!(s instanceof Player p)) return true;
        return p.isOp() || p.hasPermission(PERM) || StaffGrades.of(p) >= StaffGrades.MODO;
    }

    private BoutiqueStore store() { return service.store(); }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, @NotNull String[] raw) {
        if (!staff(s)) { err(s, "Réservé au staff."); return true; }
        List<String> a = jetons(raw);
        if (a.isEmpty()) { aide(s); return true; }
        switch (a.get(0).toLowerCase(Locale.ROOT)) {
            case "creer", "créer" -> creer(s, a);
            case "supprimer" -> supprimer(s, a);
            case "comptoir" -> comptoir(s, a);
            case "estrade" -> estrade(s, a);
            case "camera", "caméra" -> camera(s, a);
            case "article" -> article(s, a);
            case "ouvrir", "fermer" -> ouvrirFermer(s, a);
            case "info" -> info(s, a);
            case "liste" -> liste(s, a);
            case "voir" -> voir(s, a);
            case "donner", "reprendre" -> garde(s, a);
            case "recharger" -> { service.recharger(); ok(s, "Catalogue relu : " + service.catalogue().tous().size() + " article(s)."); }
            default -> aide(s);
        }
        return true;
    }

    /* ================================================================ boutiques */

    private void creer(CommandSender s, List<String> a) {
        if (a.size() < 3) { err(s, "/boutique creer <tailleur|bazar> \"<nom>\" [village] [entreprise]"); return; }
        Boutique.Genre g = Boutique.Genre.parse(a.get(1));
        if (g == null) { err(s, "Genre : tailleur ou bazar."); return; }
        String village = a.size() >= 4 ? a.get(3).toLowerCase(Locale.ROOT) : "";
        String ent = a.size() >= 5 ? a.get(4) : "";
        if (!ent.isEmpty()) {
            var es = plugin.nourriture() == null ? null : plugin.nourriture().entreprises();
            var e = es == null ? null : es.trouver(ent);
            if (e == null) { err(s, "Entreprise inconnue : " + ent + " (/entreprise liste)."); return; }
            ent = e.id();
        }
        Boutique b = store().creer(a.get(2), g, village, ent, s.getName());
        if (b == null) { err(s, "Une boutique porte déjà ce nom (ou nom vide)."); return; }
        Journal.entree(Categorie.STAFF, "BOUTIQUE_CREER").acteur(s).cibleTexte(b.nom)
                .detail(g.libelle + " « " + b.nom + " »" + (village.isEmpty() ? "" : " · " + village)
                        + (ent.isEmpty() ? "" : " · entreprise " + ent)).ecrire();
        ok(s, g.libelle + " « " + b.nom + " » créé (id " + b.id + "). Vise un comptoir : /boutique comptoir ajouter " + b.id
                + (g == Boutique.Genre.TAILLEUR ? " [catégories…] ; puis tiens-toi sur l'estrade : /boutique estrade ajouter " + b.id : ""));
    }

    private void supprimer(CommandSender s, List<String> a) {
        Boutique b = boutique(s, a, 1);
        if (b == null) return;
        if (a.size() < 3 || !"confirmer".equalsIgnoreCase(a.get(2))) {
            err(s, "Supprimer « " + b.nom + " » (" + b.comptoirs.size() + " comptoir(s), " + b.estrades.size()
                    + " estrade(s)) : /boutique supprimer " + b.id + " confirmer");
            return;
        }
        service.fermerTous(b, "La boutique a fermé");
        store().supprimer(b);
        Journal.entree(Categorie.STAFF, "BOUTIQUE_SUPPRIMER").acteur(s).cibleTexte(b.nom).detail(b.genre.libelle).ecrire();
        ok(s, "« " + b.nom + " » supprimée.");
    }

    private void ouvrirFermer(CommandSender s, List<String> a) {
        boolean ouvrir = a.get(0).equalsIgnoreCase("ouvrir");
        Boutique b = boutique(s, a, 1);
        if (b == null) return;
        b.ouverte = ouvrir;
        store().sauver();
        if (!ouvrir) service.fermerTous(b, "La boutique ferme");
        Journal.entree(Categorie.STAFF, ouvrir ? "BOUTIQUE_OUVRIR" : "BOUTIQUE_FERMER").acteur(s).cibleTexte(b.nom).ecrire();
        ok(s, "« " + b.nom + " » " + (ouvrir ? "ouverte." : "fermée (« Fermé — revenez plus tard »)."));
    }

    /* ================================================================ comptoirs */

    private void comptoir(CommandSender s, List<String> a) {
        if (!(s instanceof Player p)) { err(s, "Vise le comptoir en jeu."); return; }
        if (a.size() < 2) { err(s, "/boutique comptoir ajouter <boutique> [catégories…] | retirer | categories <cat…> | articles <id…|tous> | nom \"<libellé>\""); return; }
        String op = a.get(1).toLowerCase(Locale.ROOT);
        Block bl = p.getTargetBlockExact(6);
        Entity en = viseEntite(p);
        if (op.equals("ajouter")) {
            Boutique b = boutique(s, a, 2);
            if (b == null) return;
            if (bl == null && en == null) { err(s, "Vise un bloc, un meuble ou un PNJ à moins de 6 blocs."); return; }
            boolean entite = en != null && (bl == null || en.getLocation().distanceSquared(p.getEyeLocation())
                    <= bl.getLocation().add(0.5, 0.5, 0.5).distanceSquared(p.getEyeLocation()));
            Location l = entite ? en.getLocation() : bl.getLocation();
            if ((entite ? store().comptoirDe(en) : store().comptoirA(l, 0)) != null) { err(s, "Déjà un comptoir ici."); return; }
            List<Catalogue.Categorie> cats = categories(s, a, 3);
            if (cats == null) return;
            if (cats.isEmpty()) cats = b.genre.parDefaut();
            // PNJ = entité précise ; meuble Nexo = repéré par sa position (son entité peut changer d'identifiant)
            boolean pnj = entite && en instanceof org.bukkit.entity.LivingEntity;
            Boutique.Comptoir c = new Boutique.Comptoir(entite ? "entite" : "bloc", l.getWorld().getName(), l.getBlockX(),
                    l.getBlockY(), l.getBlockZ(), pnj ? en.getUniqueId() : null, "Comptoir " + (b.comptoirs.size() + 1));
            c.categories.addAll(cats);
            b.comptoirs.add(c);
            store().sauver();
            Journal.entree(Categorie.STAFF, "BOUTIQUE_COMPTOIR_AJOUTER").acteur(s).cibleTexte(b.nom)
                    .detail(c.libelle + " · " + c.resumeCategories() + " · " + c.court()).lieu(l).ecrire();
            ok(s, c.libelle + " ajouté à « " + b.nom + " » (" + (pnj ? "PNJ" : entite ? "meuble" : "bloc") + ") : "
                    + c.resumeCategories() + ".");
            p.spawnParticle(Particle.HAPPY_VILLAGER, l.clone().add(0.5, 1.1, 0.5), 12, 0.3, 0.2, 0.3);
            return;
        }
        Map.Entry<Boutique, Boutique.Comptoir> t = en != null ? store().comptoirDe(en) : null;
        if (t == null && bl != null) t = store().comptoirA(bl.getLocation(), 1);
        if (t == null) { err(s, "Vise un comptoir (bloc, meuble ou PNJ)."); return; }
        Boutique b = t.getKey();
        Boutique.Comptoir c = t.getValue();
        switch (op) {
            case "retirer" -> {
                b.comptoirs.remove(c);
                store().sauver();
                service.fermerTous(b, null);
                Journal.entree(Categorie.STAFF, "BOUTIQUE_COMPTOIR_RETIRER").acteur(s).cibleTexte(b.nom)
                        .detail(c.libelle + " · " + c.court()).ecrire();
                ok(s, c.libelle + " retiré de « " + b.nom + " ».");
            }
            case "categories", "catégories" -> {
                List<Catalogue.Categorie> cats = categories(s, a, 2);
                if (cats == null) return;
                if (cats.isEmpty()) { err(s, "Catégories : " + toutesCategories()); return; }
                String avant = c.resumeCategories();
                c.categories.clear();
                c.categories.addAll(cats);
                store().sauver();
                Journal.entree(Categorie.STAFF, "BOUTIQUE_COMPTOIR_CATEGORIES").acteur(s).cibleTexte(b.nom)
                        .detail(c.libelle + " · " + avant + " → " + c.resumeCategories()).ecrire();
                ok(s, c.libelle + " de « " + b.nom + " » : " + c.resumeCategories() + ".");
            }
            case "articles" -> {
                if (a.size() < 3) { err(s, "/boutique comptoir articles <id…> | tous"); return; }
                List<String> ids = new ArrayList<>();
                if (!a.get(2).equalsIgnoreCase("tous")) {
                    for (String id : a.subList(2, a.size())) {
                        Catalogue.Article art = service.catalogue().article(id);
                        if (art == null && !id.startsWith("vivre:") && !id.startsWith("carte:")) { err(s, "Article inconnu : " + id); return; }
                        ids.add(art != null ? art.id() : id);
                    }
                }
                c.articles.clear();
                c.articles.addAll(ids);
                store().sauver();
                Journal.entree(Categorie.STAFF, "BOUTIQUE_ARTICLE").acteur(s).cibleTexte(b.nom)
                        .detail(c.libelle + " · liste : " + (ids.isEmpty() ? "tous" : String.join(", ", ids))).ecrire();
                ok(s, c.libelle + " vend " + (ids.isEmpty() ? "tous les articles de ses catégories." : ids.size() + " article(s) précis."));
            }
            case "nom" -> {
                if (a.size() < 3) { err(s, "/boutique comptoir nom \"<libellé>\""); return; }
                String avant = c.libelle;
                c.libelle = a.get(2);
                store().sauver();
                Journal.entree(Categorie.STAFF, "BOUTIQUE_COMPTOIR_NOM").acteur(s).cibleTexte(b.nom)
                        .detail("« " + avant + " » → « " + c.libelle + " »").ecrire();
                ok(s, "Comptoir renommé « " + c.libelle + " ».");
            }
            default -> err(s, "comptoir ajouter | retirer | categories | articles | nom");
        }
    }

    /** Catégories à partir de l'indice {@code i} (« hauts bas », « hauts,bas ») ; null si une est inconnue. */
    private static List<Catalogue.Categorie> categories(CommandSender s, List<String> a, int i) {
        List<Catalogue.Categorie> l = new ArrayList<>();
        for (int k = i; k < a.size(); k++) {
            for (String m : a.get(k).split("[,;]")) {
                if (m.isBlank()) continue;
                Catalogue.Categorie c = Catalogue.Categorie.parse(m);
                if (c == null) { err(s, "Catégorie inconnue : " + m + " (" + toutesCategories() + ")."); return null; }
                if (!l.contains(c)) l.add(c);
            }
        }
        return l;
    }

    private static String toutesCategories() {
        StringBuilder b = new StringBuilder();
        for (Catalogue.Categorie c : Catalogue.Categorie.values()) { if (!b.isEmpty()) b.append(", "); b.append(c.id); }
        return b.toString();
    }

    /* ================================================================ estrades et caméra */

    private void estrade(CommandSender s, List<String> a) {
        if (!(s instanceof Player p)) { err(s, "Tiens-toi sur l'estrade en jeu."); return; }
        if (a.size() < 3) { err(s, "/boutique estrade <ajouter|retirer> <boutique>"); return; }
        Boutique b = boutique(s, a, 2);
        if (b == null) return;
        Location l = p.getLocation();
        if (a.get(1).equalsIgnoreCase("ajouter")) {
            Boutique.Estrade e = new Boutique.Estrade(l.getWorld().getName(), l.getBlockX() + 0.5, l.getY(), l.getBlockZ() + 0.5,
                    Math.round(l.getYaw() / 15f) * 15f);
            b.estrades.add(e);
            store().sauver();
            Journal.entree(Categorie.STAFF, "BOUTIQUE_ESTRADE").acteur(s).cibleTexte(b.nom).detail("ajout · " + e.court()).lieu(l).ecrire();
            ok(s, "Estrade " + b.estrades.size() + " de « " + b.nom + " » : le client se tiendra ici, regard vers où tu regardes. "
                    + "Caméra à " + fmt(b.camDistance) + " bloc(s), hauteur " + fmt(b.camHauteur) + " (/boutique camera).");
            montrerCamera(p, b, e);
        } else if (a.get(1).equalsIgnoreCase("retirer")) {
            Boutique.Estrade best = null;
            double d = 9;
            for (Boutique.Estrade e : b.estrades) {
                Location el = e.lieu();
                if (el != null && el.getWorld().equals(l.getWorld()) && el.distanceSquared(l) < d) { d = el.distanceSquared(l); best = e; }
            }
            if (best == null) { err(s, "Aucune estrade de « " + b.nom + " » à moins de 3 blocs."); return; }
            b.estrades.remove(best);
            store().sauver();
            Journal.entree(Categorie.STAFF, "BOUTIQUE_ESTRADE").acteur(s).cibleTexte(b.nom).detail("retrait · " + best.court()).ecrire();
            ok(s, "Estrade retirée.");
        } else err(s, "estrade ajouter | retirer");
    }

    private void camera(CommandSender s, List<String> a) {
        if (a.size() < 4) { err(s, "/boutique camera <boutique> <distance 1,5-5> <hauteur 0,5-1,8>"); return; }
        Boutique b = boutique(s, a, 1);
        if (b == null) return;
        try {
            b.camDistance = Math.max(1.5, Math.min(5.0, Double.parseDouble(a.get(2).replace(',', '.'))));
            b.camHauteur = Math.max(0.5, Math.min(1.8, Double.parseDouble(a.get(3).replace(',', '.'))));
        } catch (NumberFormatException e) { err(s, "Nombres invalides."); return; }
        store().sauver();
        Journal.entree(Categorie.STAFF, "BOUTIQUE_ESTRADE").acteur(s).cibleTexte(b.nom)
                .detail("caméra " + fmt(b.camDistance) + " · " + fmt(b.camHauteur)).ecrire();
        ok(s, "Caméra de « " + b.nom + " » : " + fmt(b.camDistance) + " bloc(s) devant, hauteur " + fmt(b.camHauteur) + ".");
        if (s instanceof Player p && !b.estrades.isEmpty()) montrerCamera(p, b, b.estrades.get(0));
    }

    /** Montre 10 s le point de vue calculé (particules : la caméra et la ligne de visée). */
    private void montrerCamera(Player p, Boutique b, Boutique.Estrade e) {
        Location pied = e.lieu();
        if (pied == null) return;
        Vector dir = new Vector(-Math.sin(Math.toRadians(e.yaw())), 0, Math.cos(Math.toRadians(e.yaw())));
        Location vise = pied.clone().add(0, b.camHauteur, 0);
        Location cam = vise.clone().add(dir.clone().multiply(b.camDistance));
        boolean bloque = vise.getWorld().rayTraceBlocks(vise, dir, b.camDistance) != null;
        int[] n = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (++n[0] > 20 || !p.isOnline()) { task.cancel(); return; }
            p.spawnParticle(bloque ? Particle.ANGRY_VILLAGER : Particle.END_ROD, cam, 3, 0.05, 0.05, 0.05, 0);
            for (double t = 0.3; t < b.camDistance; t += 0.3) {
                p.spawnParticle(Particle.WAX_ON, vise.clone().add(dir.clone().multiply(t)), 1, 0, 0, 0, 0);
            }
        }, 0L, 10L);
        if (bloque) err(p, "Un bloc gêne la caméra : rapproche-la (/boutique camera " + b.id + " <distance> <hauteur>) "
                + "sinon le personnage sera dessiné sur un voile.");
    }

    /* ================================================================ articles */

    private void article(CommandSender s, List<String> a) {
        if (a.size() < 4) { err(s, "/boutique article prix <boutique> <id> <prix> | retirer <boutique> <id> | remettre <boutique> <id>"); return; }
        String op = a.get(1).toLowerCase(Locale.ROOT);
        Boutique b = boutique(s, a, 2);
        if (b == null) return;
        Catalogue.Article art = service.catalogue().article(a.get(3));
        if (art == null) { err(s, "Article inconnu : " + a.get(3) + " (boutique-catalogue.yml)."); return; }
        switch (op) {
            case "prix" -> {
                if (a.size() < 5) { err(s, "/boutique article prix <boutique> <id> <prix|catalogue>"); return; }
                if (a.get(4).equalsIgnoreCase("catalogue")) b.prix.remove(art.id());
                else {
                    long px;
                    try { px = Long.parseLong(a.get(4).replace(" ", "")); } catch (NumberFormatException e) { err(s, "Prix invalide."); return; }
                    if (px < 0) { err(s, "Prix invalide."); return; }
                    b.prix.put(art.id(), px);
                }
                store().sauver();
                Journal.entree(Categorie.STAFF, "BOUTIQUE_ARTICLE").acteur(s).cibleTexte(b.nom)
                        .detail(art.nom() + " · prix " + b.prixDe(art) + " ryo").ecrire();
                ok(s, art.nom() + " à « " + b.nom + " » : " + b.prixDe(art) + " ryo.");
            }
            case "retirer", "remettre" -> {
                if (op.equals("retirer")) b.exclus.add(art.id()); else b.exclus.remove(art.id());
                store().sauver();
                Journal.entree(Categorie.STAFF, "BOUTIQUE_ARTICLE").acteur(s).cibleTexte(b.nom)
                        .detail(art.nom() + " · " + (op.equals("retirer") ? "retiré" : "remis")).ecrire();
                ok(s, art.nom() + (op.equals("retirer") ? " n'est plus vendu" : " est de nouveau vendu") + " à « " + b.nom + " ».");
            }
            default -> err(s, "article prix | retirer | remettre");
        }
    }

    /* ================================================================ consulter */

    private void info(CommandSender s, List<String> a) {
        Boutique b = boutique(s, a, 1);
        if (b == null) return;
        s.sendMessage(Component.text("« " + b.nom + " » — " + b.genre.libelle + " (id " + b.id + ") · "
                + (b.village.isEmpty() ? "sans village" : b.village) + (b.entreprise.isEmpty() ? "" : " · entreprise " + b.entreprise)
                + " · " + (b.ouverte ? "ouverte" : "fermée") + " · " + service.ouverts(b) + " client(s) à l'écran", NamedTextColor.GOLD));
        int i = 1;
        for (Boutique.Comptoir c : b.comptoirs) {
            s.sendMessage(Component.text("  " + i++ + ". " + c.libelle + " (" + c.type + " " + c.monde + " " + c.court() + ") : "
                    + c.resumeCategories() + (c.articles.isEmpty() ? "" : " · " + c.articles.size() + " article(s) précis"), NamedTextColor.YELLOW));
        }
        if (b.comptoirs.isEmpty()) s.sendMessage(Component.text("  Aucun comptoir : /boutique comptoir ajouter " + b.id, NamedTextColor.GRAY));
        for (Boutique.Estrade e : b.estrades) s.sendMessage(Component.text("  Estrade : " + e.monde() + " " + e.court(), NamedTextColor.GRAY));
        if (b.genre == Boutique.Genre.TAILLEUR) {
            s.sendMessage(Component.text("  Caméra : " + fmt(b.camDistance) + " bloc(s) · hauteur " + fmt(b.camHauteur)
                    + (b.estrades.isEmpty() ? " · pas d'estrade : le personnage sera dessiné sur un voile" : ""), NamedTextColor.GRAY));
        }
        if (!b.prix.isEmpty()) s.sendMessage(Component.text("  Prix propres : " + b.prix, NamedTextColor.GRAY));
        if (!b.exclus.isEmpty()) s.sendMessage(Component.text("  Retirés : " + String.join(", ", b.exclus), NamedTextColor.GRAY));
    }

    private void liste(CommandSender s, List<String> a) {
        String v = a.size() >= 2 ? a.get(1).toLowerCase(Locale.ROOT) : null;
        List<Boutique> l = store().toutes();
        if (l.isEmpty()) { s.sendMessage(Component.text("Aucune boutique.", NamedTextColor.GRAY)); return; }
        for (Boutique b : l) {
            if (v != null && !b.village.equalsIgnoreCase(v)) continue;
            s.sendMessage(Component.text("« " + b.nom + " » · " + b.genre.libelle + " · " + (b.village.isEmpty() ? "—" : b.village)
                    + " · " + b.comptoirs.size() + " comptoir(s) · " + b.estrades.size() + " estrade(s) · "
                    + (b.ouverte ? "ouverte" : "fermée") + " · id " + b.id, NamedTextColor.YELLOW));
        }
    }

    private void voir(CommandSender s, List<String> a) {
        if (!(s instanceof Player p)) { err(s, "En jeu seulement."); return; }
        Boutique b = boutique(s, a, 1);
        if (b == null) return;
        if (b.comptoirs.isEmpty()) { err(s, "« " + b.nom + " » n'a pas de comptoir."); return; }
        int n = 1;
        if (a.size() >= 3) {
            try { n = Integer.parseInt(a.get(2)); } catch (NumberFormatException e) { err(s, "N° de comptoir invalide."); return; }
        }
        n = Math.max(1, Math.min(b.comptoirs.size(), n));
        service.ouvrir(p, b, b.comptoirs.get(n - 1), true);
    }

    /* ================================================================ garde-robe */

    private void garde(CommandSender s, List<String> a) {
        boolean donner = a.get(0).equalsIgnoreCase("donner");
        if (a.size() < 4) { err(s, "/boutique " + a.get(0) + " <cible> <id de tenue> \"<motif>\""); return; }
        CharacterService.ResolvedCharacter rc = PlayerIds.character(s, a.get(1));
        if (rc == null || rc.character() == null) { err(s, "Personnage introuvable : " + a.get(1)); return; }
        ShinobiCharacter c = rc.character();
        Catalogue.Article art = service.catalogue().article(a.get(2));
        String id = art != null ? art.id() : a.get(2);
        if (art != null && !art.tenue()) { err(s, art.nom() + " est un objet : donne-le avec /item ou la sacoche."); return; }
        String motif = a.get(3);
        boolean fait = donner ? !c.ownsOutfit(id) : c.removeOwnedOutfit(id);
        if (donner && fait) c.addOwnedOutfit(id);
        if (!fait) { err(s, donner ? "Elle l'a déjà." : "Pas dans sa garde-robe."); return; }
        plugin.characters().save(c);
        Journal.entree(Categorie.STAFF, donner ? "BOUTIQUE_DONNER" : "BOUTIQUE_REPRENDRE").acteur(s)
                .ciblePersonnage(rc.ownerId(), c.id()).detail((art != null ? art.nom() : id) + " · « " + motif + " »").ecrire();
        ok(s, (art != null ? art.nom() : id) + (donner ? " ajoutée à" : " retirée de") + " la garde-robe de " + PlayerIds.prefix(c) + c.name() + ".");
        Player en = Bukkit.getPlayer(rc.ownerId());
        if (en != null && donner) en.sendMessage(Component.text("Nouvelle tenue dans ta garde-robe : " + (art != null ? art.nom() : id) + ".", NamedTextColor.GOLD));
    }

    /* ================================================================ outils */

    private Boutique boutique(CommandSender s, List<String> a, int i) {
        if (a.size() <= i) { err(s, "Quelle boutique ? (/boutique liste)"); return null; }
        Boutique b = store().trouver(a.get(i));
        if (b == null) err(s, "Boutique inconnue : " + a.get(i));
        return b;
    }

    private static Entity viseEntite(Player p) {
        try {
            Entity e = p.getTargetEntity(6, true);
            return e instanceof Player ? null : e;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String fmt(double d) { return String.format(Locale.FRANCE, "%.1f", d); }

    private void aide(CommandSender s) {
        s.sendMessage(Component.text("/boutique creer <tailleur|bazar> \"<nom>\" [village] [entreprise] · supprimer <b> confirmer · "
                + "comptoir ajouter <b> [catégories…] | retirer | categories <cat…> | articles <id…|tous> | nom \"…\" (en visant) · "
                + "estrade ajouter|retirer <b> · camera <b> <distance> <hauteur> · article prix|retirer|remettre <b> <id> [prix] · "
                + "ouvrir|fermer <b> · info <b> · liste [village] · voir <b> [n°] · donner|reprendre <cible> <tenue> \"<motif>\" · recharger",
                NamedTextColor.YELLOW));
        s.sendMessage(Component.text("Catégories : " + toutesCategories(), NamedTextColor.GRAY));
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
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String alias, @NotNull String[] a) {
        if (!staff(s)) return List.of();
        List<String> r = new ArrayList<>();
        String sub = a.length >= 1 ? a[0].toLowerCase(Locale.ROOT) : "";
        if (a.length == 1) r.addAll(SOUS);
        else if (a.length == 2) {
            switch (sub) {
                case "creer" -> r.addAll(List.of("tailleur", "bazar"));
                case "comptoir" -> r.addAll(List.of("ajouter", "retirer", "categories", "articles", "nom"));
                case "estrade" -> r.addAll(List.of("ajouter", "retirer"));
                case "article" -> r.addAll(List.of("prix", "retirer", "remettre"));
                case "donner", "reprendre" -> Bukkit.getOnlinePlayers().forEach(p -> r.add(p.getName()));
                case "liste" -> r.addAll(List.of("konoha", "suna", "kiri", "kumo", "iwa"));
                default -> store().toutes().forEach(b -> r.add(b.id));
            }
        } else if (sub.equals("comptoir") && a.length == 3 && a[1].equalsIgnoreCase("ajouter")) {
            store().toutes().forEach(b -> r.add(b.id));
        } else if (sub.equals("comptoir") && (a[1].equalsIgnoreCase("categories") || a[1].equalsIgnoreCase("ajouter"))) {
            for (Catalogue.Categorie c : Catalogue.Categorie.values()) r.add(c.id);
        } else if ((sub.equals("estrade") || sub.equals("article")) && a.length == 3) {
            store().toutes().forEach(b -> r.add(b.id));
        } else if (sub.equals("article") && a.length == 4 || (sub.equals("donner") || sub.equals("reprendre")) && a.length == 3
                || sub.equals("comptoir") && a[1].equalsIgnoreCase("articles")) {
            for (Catalogue.Article x : service.catalogue().tous()) r.add(x.id());
        }
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        r.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return r;
    }
}
