package com.reborn.shinobicore.banque;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.CharacterService;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.character.PlayerIdRegistry;
import com.reborn.shinobicore.character.PlayerIds;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.inventory.InventoryManager;
import com.reborn.shinobicore.inventory.Notifs;
import com.reborn.shinobicore.inventory.RareteItems;
import com.reborn.shinobicore.inventory.RpRarity;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.monnaie.Bourse;
import com.reborn.shinobicore.monnaie.BourseService;
import com.reborn.shinobicore.monnaie.FouilleService;
import com.reborn.shinobicore.monnaie.MonnaieConfig;
import com.reborn.shinobicore.monnaie.MonnaieModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le compte et le guichet (MONNAIE_ET_BANQUE.md §4-§7, §9-§11 ; décisions du 15/10) : comptes par personnage,
 * migration de l'ancien solde, dépôt / retrait / virement, paliers et coffre (objets refusés), gel, contre-passation,
 * trésors des villages, succession à la mort RP (gel 7 jours puis trésor, ou héritiers d'un testament validé), masse
 * monétaire. Tout mouvement : une ligne {@code ECONOMIE} au journal universel avec un N° d'opération.
 */
public final class BanqueService implements org.bukkit.event.Listener {

    private static final NamespacedKey PARCHEMIN_ID = new NamespacedKey("shinobiabilities", "parchemin_id");

    private final ShinobiCore plugin;
    private final MonnaieModule module;
    final BanqueStore store;
    /** Virements récents par émetteur → destinataire (alerte : 3 en 24 h). */
    private final Map<String, Deque<Long>> recents = new ConcurrentHashMap<>();

    public BanqueService(ShinobiCore plugin, MonnaieModule module) {
        this.plugin = plugin;
        this.module = module;
        this.store = new BanqueStore(plugin);
    }

    public void start() {
        store.start();
        MonnaieConfig.get().tresorInitial.forEach((v, n) -> store.tresors.putIfAbsent(v, n));
        Bukkit.getScheduler().runTaskLater(plugin, this::migrerTout, 100L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::successions, 1200L, 6000L);
    }

    public void stop() { store.sauver(); }

    public BanqueStore store() { return store; }

    /* ================================================================ comptes */

    /** Le compte du personnage (créé et migré au besoin) ; identité tenue à jour. */
    public Compte compte(ShinobiCharacter c) {
        if (c == null) return null;
        Compte a = store.comptes.computeIfAbsent(c.id(), Compte::new);
        a.owner = c.ownerId();
        a.nom = c.name();
        a.numero = c.hasPlayerId() ? c.playerId() : a.numero;
        String v = MonnaieConfig.village(c.village());
        if (!v.equals(a.village)) { a.village = v; store.marquer(); }
        a.taille(MonnaieConfig.get().palier(a.palier).cases());
        if (!a.migre || c.ryo() > 0) migrer(c, a);
        return a;
    }

    public Compte compte(UUID perso) { return perso == null ? null : store.comptes.get(perso); }

    /** Compte d'un joueur en ligne (personnage actif), ou null. */
    public Compte compte(Player p) {
        return p == null ? null : compte(plugin.characters().getActive(p.getUniqueId()));
    }

    /** Cible staff : pseudo, nom RP ou {@code #N°} (même hors ligne). */
    public CharacterService.ResolvedCharacter resoudre(CommandSender s, String ref) {
        return PlayerIds.character(s, ref);
    }

    /* ================================================================ migration (décision 1 : vers le compte) */

    /**
     * Le solde actuel de chaque personnage ({@code ShinobiCharacter.ryo}, clé {@code ryo} du YAML des personnages) va
     * sur son <b>compte</b>. Idempotent : le compte est marqué {@code migre} et <b>écrit sur disque avant</b> que
     * l'ancien champ soit remis à 0 ; un reliquat trouvé sur un compte déjà migré (arrêt brutal entre les deux) est
     * remis à 0 sans être recompté. Journal {@code ECONOMIE MIGRATION} par personnage.
     */
    private void migrer(ShinobiCharacter c, Compte a) {
        long ancien = c.ryo();
        if (!a.migre) {
            a.solde += ancien;
            a.migre = true;
            if (ancien > 0) a.noter(ancien, "reprise de l'ancien solde");
            store.sauver();
            Journal.entree(Categorie.ECONOMIE, "MIGRATION").ciblePersonnage(c.ownerId(), c.id())
                    .detail("ancien solde " + Bourse.fmt(ancien) + " ryo → compte (" + Bourse.fmt(a.solde) + ")").ecrire();
        } else if (ancien > 0) {
            Journal.entree(Categorie.ECONOMIE, "MIGRATION").ciblePersonnage(c.ownerId(), c.id())
                    .detail("reliquat " + Bourse.fmt(ancien) + " ryo déjà migré : ancien champ remis à 0").ecrire();
        }
        if (ancien > 0) {
            c.restoreRyo(0);
            plugin.characters().save(c);
        }
    }

    /** Au démarrage : migre tous les personnages connus de l'index des N°. */
    private void migrerTout() {
        PlayerIdRegistry ids = plugin.playerIds();
        if (ids == null) return;
        int n = 0;
        long total = 0;
        for (PlayerIdRegistry.Entry e : ids.entries()) {
            if (e.deleted() || e.ownerId() == null) continue;
            Compte deja = store.comptes.get(e.characterId());
            if (deja != null && deja.migre) continue;
            for (ShinobiCharacter c : plugin.characters().getAll(e.ownerId())) {
                if (!c.id().equals(e.characterId())) continue;
                long r = c.ryo();
                compte(c);
                n++;
                total += r;
            }
        }
        if (n > 0) {
            Journal.entree(Categorie.ECONOMIE, "MIGRATION").detail(n + " compte(s) ouvert(s) · " + Bourse.fmt(total)
                    + " ryo repris de l'ancien solde").ecrire();
            plugin.getLogger().info("[banque] Migration : " + n + " compte(s), " + total + " ryo repris de l'ancien solde.");
        }
    }

    /* ================================================================ bourse (mémoire) */

    public void noterBourse(UUID perso, long n) {
        Compte a = store.comptes.get(perso);
        if (a != null && a.derniereBourse != n) { a.derniereBourse = n; store.marquer(); }
    }

    /* ================================================================ journal */

    private void journal(String action, CommandSender acteur, Compte a, String detail, long op, Location lieu) {
        var j = Journal.entree(Categorie.ECONOMIE, action);
        if (acteur != null) j.acteur(acteur);
        if (a != null) j.ciblePersonnage(a.owner, a.perso);
        j.detail(detail + (op > 0 ? " · op " + op : ""));
        if (lieu != null) j.lieu(lieu);
        j.ecrire();
    }

    private static String nomCourt(Compte a) {
        if (a == null) return "?";
        String n = a.nom;
        int sp = n.indexOf(' ');
        return sp > 0 && sp + 1 < n.length() ? n.substring(0, sp) + " " + n.charAt(sp + 1) + "." : n;
    }

    /* ================================================================ accès au guichet */

    /** Rapport d'accès : refusé (raison), ou accepté avec ou sans frais d'allié. */
    public record Acces(String refus, boolean allie) {}

    public Acces acces(Compte a, String villageGuichet) {
        MonnaieConfig k = MonnaieConfig.get();
        if (a.gele()) return new Acces("Ton compte est gelé (" + a.gelMotif + ").", false);
        String v = a.village;
        if (v.isEmpty() || k.villagesRefuses.contains(v)) return new Acces("Le guichetier ne te connaît pas.", false);
        if (v.equals(villageGuichet)) return new Acces(null, false);
        if (k.allies.getOrDefault(villageGuichet, java.util.Set.of()).contains(v)) return new Acces(null, true);
        return new Acces("Le guichetier ne te connaît pas.", false);
    }

    static long frais(long n, int pourcent, long min) {
        if (n <= 0 || pourcent <= 0) return 0;
        return Math.max(min, (long) Math.ceil(n * pourcent / 100.0));
    }

    /* ================================================================ dépôt, retrait */

    /** Bourse → compte. {@code null} si fait, sinon la raison. */
    public String deposer(Player p, long n, Guichets.Ouverture o) {
        Compte a = compte(p);
        if (a == null) return "Aucun personnage.";
        Acces ac = acces(a, o.village());
        if (ac.refus() != null) return ac.refus();
        if (n <= 0) return "Montant invalide.";
        long frais = ac.allie() ? frais(n, MonnaieConfig.get().fraisAlliePourcent, 1) : 0;
        long net = n - frais;
        long plafond = MonnaieConfig.get().palier(a.palier).plafond();
        if (plafond > 0 && a.solde + net > plafond)
            return "Plafond du palier atteint (" + Bourse.fmt(plafond) + ") : il reste " + Bourse.fmt(Math.max(0, plafond - a.solde)) + " ryo.";
        long op = store.nouvelleOp("DEPOT", null, a.perso, net, frais, p.getName(), "dépôt");
        if (!module.bourses().debiter(p, n, "DEPOT", "dépôt au guichet · op " + op, p)) return "Pas autant dans ta bourse.";
        a.solde += net;
        a.noter(net, "dépôt au guichet");
        store.marquer();
        journal("DEPOT", p, a, "+" + Bourse.fmt(net) + " ryo · compte " + Bourse.fmt(a.solde - net) + " → " + Bourse.fmt(a.solde)
                + " · " + o.libelle() + (o.guichetier() != null ? " · guichetier " + o.guichetier() : ""), op, p.getLocation());
        if (frais > 0) journal("FRAIS", p, a, "−" + Bourse.fmt(frais) + " ryo · frais de guichet allié", op, p.getLocation());
        return null;
    }

    /** Compte → bourse. */
    public String retirer(Player p, long n, Guichets.Ouverture o) {
        Compte a = compte(p);
        if (a == null) return "Aucun personnage.";
        Acces ac = acces(a, o.village());
        if (ac.refus() != null) return ac.refus();
        if (n <= 0) return "Montant invalide.";
        long frais = ac.allie() ? frais(n, MonnaieConfig.get().fraisAlliePourcent, 1) : 0;
        if (a.solde < n + frais) return "Pas autant sur ton compte (" + Bourse.fmt(a.solde) + " ryo" + (frais > 0 ? ", frais " + Bourse.fmt(frais) : "") + ").";
        long op = store.nouvelleOp("RETRAIT", a.perso, null, n, frais, p.getName(), "retrait");
        if (!module.bourses().crediter(p, n, "RETRAIT", "retrait au guichet · op " + op, p)) return "Plus de place pour ta bourse.";
        a.solde -= n + frais;
        a.noter(-n, "retrait au guichet");
        store.marquer();
        journal("RETRAIT", p, a, "−" + Bourse.fmt(n) + " ryo · compte " + Bourse.fmt(a.solde + n + frais) + " → " + Bourse.fmt(a.solde)
                + " · " + o.libelle() + (o.guichetier() != null ? " · guichetier " + o.guichetier() : ""), op, p.getLocation());
        if (frais > 0) journal("FRAIS", p, a, "−" + Bourse.fmt(frais) + " ryo · frais de guichet allié", op, p.getLocation());
        return null;
    }

    /* ================================================================ virement */

    /** Résout la cible d'un virement : {@code #N°} (numéro de compte) ou {@code c:<uuid>} (un contact). */
    public ShinobiCharacter cibleVirement(ShinobiCharacter de, String ref) {
        if (ref == null || ref.isBlank()) return null;
        String r = ref.trim();
        if (r.startsWith("c:")) {
            try {
                UUID id = UUID.fromString(r.substring(2));
                if (!de.knows(id)) return null;
                Compte a = store.comptes.get(id);
                CharacterService.ResolvedCharacter rc = plugin.characters().resolveOwnedById(id);
                if (rc != null) return rc.character();
                if (a != null && a.owner != null) return plugin.characters().findById(a.owner, id).orElse(null);
                return null;
            } catch (IllegalArgumentException e) { return null; }
        }
        // « Aki Uchiha · N° 00017 » (contact affiché) ou « Aki Uchiha » : le contact de ce nom
        String nom = r.contains(" · N° ") ? r.substring(0, r.indexOf(" · N° ")).trim() : r;
        for (var e : de.contacts().entrySet()) {
            if (!e.getValue().display().equalsIgnoreCase(nom)) continue;
            CharacterService.ResolvedCharacter rc = plugin.characters().resolveOwnedById(e.getKey());
            if (rc != null) return rc.character();
            Compte a = store.comptes.get(e.getKey());
            if (a != null && a.owner != null) return plugin.characters().findById(a.owner, e.getKey()).orElse(null);
        }
        if (r.contains(" · N° ")) r = r.substring(r.indexOf(" · N° ") + 6).trim();
        int n = PlayerIdRegistry.parse(r.startsWith("#") ? r : "#" + r);
        if (n < 0) return null;
        CharacterService.ResolvedCharacter rc = plugin.characters().findByPlayerId(n);
        return rc == null ? null : rc.character();
    }

    public String virer(Player p, String ref, long n, String motif, Guichets.Ouverture o) {
        ShinobiCharacter cd = plugin.characters().getActive(p.getUniqueId());
        Compte a = compte(cd);
        if (a == null) return "Aucun personnage.";
        Acces ac = acces(a, o.village());
        if (ac.refus() != null) return ac.refus();
        if (motif == null || motif.isBlank()) return "Le motif est obligatoire.";
        if (n <= 0) return "Montant invalide.";
        ShinobiCharacter cc = cibleVirement(cd, ref);
        if (cc == null) return "Destinataire inconnu.";
        if (cc.id().equals(cd.id())) return "Pas à toi-même.";
        if (cc.dead()) return "Ce compte est fermé.";
        Compte b = compte(cc);
        if (b.gele()) return "Le compte du destinataire est gelé.";
        MonnaieConfig k = MonnaieConfig.get();
        String jour = java.time.LocalDate.now(java.time.ZoneId.of(k.fuseau)).toString();
        if (!jour.equals(a.virementJour)) { a.virementJour = jour; a.virementTotal = 0; }
        if (a.virementTotal + n > k.virementPlafondJour)
            return "Plafond du jour : encore " + Bourse.fmt(Math.max(0, k.virementPlafondJour - a.virementTotal)) + " ryo.";
        boolean autre = !b.village.equals(a.village);
        int pct = ac.allie() || autre ? k.virementAutreVillagePourcent : k.virementPourcent;
        long frais = frais(n, pct, k.virementMin);
        if (a.solde < n + frais) return "Pas autant sur ton compte (frais " + Bourse.fmt(frais) + ").";
        long op = store.nouvelleOp("VIREMENT", a.perso, b.perso, n, frais, p.getName(), motif);
        a.solde -= n + frais;
        b.solde += n;
        a.virementTotal += n;
        a.noter(-n, "virement à " + nomCourt(b));
        b.noter(n, "virement de " + nomCourt(a));
        store.marquer();
        journal("VIREMENT", p, a, "−" + Bourse.fmt(n) + " ryo → " + PlayerIds.prefix(cc) + cc.name() + " · « " + motif
                + " » · compte " + Bourse.fmt(a.solde + n + frais) + " → " + Bourse.fmt(a.solde) + " · " + o.libelle(), op, p.getLocation());
        journal("VIREMENT", p, b, "+" + Bourse.fmt(n) + " ryo ← " + PlayerIds.prefix(cd) + cd.name() + " · « " + motif + " »", op, null);
        if (frais > 0) journal("FRAIS", p, a, "−" + Bourse.fmt(frais) + " ryo · frais de virement (" + pct + " %)", op, null);
        Player dest = Bukkit.getPlayer(cc.ownerId());
        if (dest != null) {
            ShinobiCharacter act = plugin.characters().getActive(dest.getUniqueId());
            if (act != null && act.id().equals(cc.id())) {
                var kn = cc.knownNameOf(cd.id());
                Notifs.msg(dest, "info", null, "Virement reçu : " + Bourse.fmt(n) + " ryo", "de " + (kn != null ? kn.display() : "N° " + PlayerIdRegistry.format(cd.playerId())));
            }
        }
        alertesVirement(p, cd, cc, a, b, n, op);
        return null;
    }

    private void alertesVirement(Player p, ShinobiCharacter cd, ShinobiCharacter cc, Compte a, Compte b, long n, long op) {
        var sp = plugin.staffPanel();
        if (sp == null) return;
        MonnaieConfig k = MonnaieConfig.get();
        String qui = PlayerIds.prefix(cd) + cd.name() + " → " + PlayerIds.prefix(cc) + cc.name();
        if (n >= k.virementAlerte) sp.onAlerteEconomie(p.getUniqueId(), "Virement de " + Bourse.fmt(n) + " ryo · " + qui + " · op " + op, p.getLocation(), 30);
        String cle = a.perso + ">" + b.perso;
        Deque<Long> d = recents.computeIfAbsent(cle, x -> new java.util.ArrayDeque<>());
        long now = System.currentTimeMillis();
        d.addLast(now);
        while (!d.isEmpty() && now - d.peekFirst() > 86_400_000L) d.pollFirst();
        if (d.size() >= 3) sp.onAlerteEconomie(p.getUniqueId(), d.size() + " virements en 24 h · " + qui, p.getLocation(), 20);
        if (cc.ownerId() != null && sp.comptesLies(cd.ownerId(), cc.ownerId()))
            sp.onAlerteEconomie(p.getUniqueId(), "Virement entre comptes liés (même IP) · " + Bourse.fmt(n) + " ryo · " + qui, p.getLocation(), 40);
    }

    /* ================================================================ paliers, coffre */

    public String passerPalier(Player p, int k, Guichets.Ouverture o) {
        Compte a = compte(p);
        if (a == null) return "Aucun personnage.";
        Acces ac = acces(a, o.village());
        if (ac.refus() != null) return ac.refus();
        if (ac.allie()) return "Les paliers se prennent au guichet de ton village.";
        MonnaieConfig cfg = MonnaieConfig.get();
        if (k <= a.palier || k > cfg.palierMax()) return "Palier invalide.";
        if (k != a.palier + 1) return "Un palier après l'autre.";
        MonnaieConfig.Palier pa = cfg.palier(k);
        if (a.solde < pa.cout()) return "Il faut " + Bourse.fmt(pa.cout()) + " ryo sur ton compte.";
        long op = store.nouvelleOp("PALIER", a.perso, null, pa.cout(), 0, p.getName(), pa.nom());
        a.solde -= pa.cout();
        a.palier = k;
        a.taille(pa.cases());
        a.noter(-pa.cout(), "palier " + k + " · " + pa.nom());
        store.marquer();
        journal("PALIER", p, a, "−" + Bourse.fmt(pa.cout()) + " ryo · palier " + k + " « " + pa.nom() + " » · " + pa.cases()
                + " cases · plafond " + (pa.plafond() > 0 ? Bourse.fmt(pa.plafond()) : "aucun"), op, p.getLocation());
        return null;
    }

    /** Pourquoi cet objet ne va pas au coffre (décision 8), ou null. */
    public String refus(ItemStack s) {
        if (s == null || s.getType().isAir()) return "Rien à ranger.";
        MonnaieConfig k = MonnaieConfig.get();
        String id = RareteItems.itemId(s);
        PersistentDataContainer pdc = s.hasItemMeta() ? s.getItemMeta().getPersistentDataContainer() : null;
        if (k.refuserParchemins && ((pdc != null && pdc.has(PARCHEMIN_ID)) || (id != null && (id.startsWith("parchemin_")
                || id.startsWith("rouleau")))))
            return "Ce parchemin ne va pas au coffre — il reste volable";
        if (k.refuserUniques && pdc != null && (pdc.has(RareteItems.UNIQUE) || pdc.has(RareteItems.QUETE)))
            return "Cet objet unique ne va pas au coffre — il doit circuler";
        if (k.refuserVoles && pdc != null && pdc.has(FouilleService.VOLE_A, PersistentDataType.STRING))
            return "Un objet volé ne va pas au coffre";
        if (id != null && k.refusIds.contains(id)) return "Cet objet ne va pas au coffre";
        boolean legendaire = (id != null && k.armesRefuseesIds.contains(id))
                || (pdc != null && pdc.has(new NamespacedKey("reborn", k.tagLegendaire)))
                || (RareteItems.arme(s) && RareteItems.of(s).auMoins(k.armesRefuseesRarete));
        if (legendaire) return "Cette arme légendaire ne va pas au coffre — elle doit circuler";
        return null;
    }

    /** Sacoche → coffre du compte (la bourse glissée sur le coffre = dépôt sur le compte). */
    public String coffreDeposer(Player p, String ref, Guichets.Ouverture o) {
        Compte a = compte(p);
        if (a == null) return "Aucun personnage.";
        Acces ac = acces(a, o.village());
        if (ac.refus() != null) return ac.refus();
        if (ac.allie()) return "Le coffre s'ouvre au guichet de ton village.";
        BourseService.Emplacements e = module.bourses().sur(p);
        ItemStack s = e.lire(ref);
        if (s == null || s.getType().isAir()) return null;
        if (Bourse.est(s)) return deposer(p, Bourse.montant(s), o);
        String r = refus(s);
        if (r != null) {
            journal("COFFRE_REFUS", p, a, RareteItems.nom(s) + " · " + r, 0, p.getLocation());
            return "!" + r;
        }
        int libre = -1;
        for (int i = 0; i < a.coffre.length; i++) if (a.coffre[i] == null || a.coffre[i].getType().isAir()) { libre = i; break; }
        if (libre < 0) return "Coffre plein (" + a.coffre.length + " cases) : passe au palier suivant.";
        a.coffre[libre] = s.clone();
        e.ecrire(ref, null);
        store.marquer();
        p.updateInventory();
        if (plugin.rpInventory() != null) { plugin.rpInventory().push(p, false); plugin.rpInventory().recalc(p); }
        journal("COFFRE_DEPOT", p, a, s.getAmount() + " × " + RareteItems.nom(s) + " · coffre " + a.occupees() + "/" + a.coffre.length, 0, p.getLocation());
        return null;
    }

    public String coffreRetirer(Player p, int i, Guichets.Ouverture o) {
        Compte a = compte(p);
        if (a == null) return "Aucun personnage.";
        Acces ac = acces(a, o.village());
        if (ac.refus() != null) return ac.refus();
        if (i < 0 || i >= a.coffre.length || a.coffre[i] == null) return null;
        ItemStack s = a.coffre[i];
        InventoryManager im = plugin.rpInventory();
        if (im != null) {
            String r = im.refusReception(p, s);
            if (r != null) return Character.toUpperCase(r.charAt(0)) + r.substring(1) + ".";
            a.coffre[i] = null;
            int reste = im.recevoir(p, s.clone());
            if (reste > 0) { ItemStack back = s.clone(); back.setAmount(reste); a.coffre[i] = back; }
        } else {
            a.coffre[i] = null;
            p.getInventory().addItem(s.clone());
        }
        store.marquer();
        journal("COFFRE_RETRAIT", p, a, s.getAmount() + " × " + RareteItems.nom(s) + " · coffre " + a.occupees() + "/" + a.coffre.length, 0, p.getLocation());
        return null;
    }

    /* ================================================================ staff */

    /** Staff : variation du compte (motif obligatoire). */
    public long staffCompte(CommandSender s, Compte a, String op, long n, String motif) {
        long avant = a.solde;
        switch (op) {
            case "donner" -> a.solde += n;
            case "retirer" -> a.solde = Math.max(0, a.solde - n);
            case "fixer" -> a.solde = Math.max(0, n);
            default -> { return -1; }
        }
        long delta = a.solde - avant;
        long o = store.nouvelleOp("STAFF", delta < 0 ? a.perso : null, delta >= 0 ? a.perso : null, Math.abs(delta), 0, s.getName(), motif);
        a.noter(delta, "staff : " + motif);
        store.marquer();
        journal("STAFF", s, a, (delta >= 0 ? "+" : "") + Bourse.fmt(delta) + " ryo · compte " + Bourse.fmt(avant) + " → "
                + Bourse.fmt(a.solde) + " · /ryo " + op + " · « " + motif + " »", o, null);
        return a.solde;
    }

    public void geler(CommandSender s, Compte a, String motif) {
        a.gelMotif = motif;
        a.gelPar = s.getName();
        a.gelLe = System.currentTimeMillis();
        store.marquer();
        journal("STAFF", s, a, "compte gelé · « " + motif + " »", 0, null);
    }

    public void degeler(CommandSender s, Compte a, String motif) {
        a.gelMotif = null;
        a.gelPar = null;
        a.gelLe = 0;
        store.marquer();
        journal("STAFF", s, a, "compte dégelé · « " + motif + " »", 0, null);
    }

    public void staffPalier(CommandSender s, Compte a, int k, String motif) {
        int avant = a.palier;
        a.palier = Math.max(1, Math.min(MonnaieConfig.get().palierMax(), k));
        a.taille(MonnaieConfig.get().palier(a.palier).cases());
        store.marquer();
        journal("STAFF", s, a, "palier " + avant + " → " + a.palier + " · « " + motif + " »", 0, null);
    }

    /** Ligne inverse d'une opération (virement, staff, solde, succession) ; l'originale reste. */
    public String contrepasser(CommandSender s, long n, String raison) {
        BanqueStore.Operation o = store.operations.get(n);
        if (o == null) return "Opération inconnue (seules les 500 dernières sont gardées).";
        if (o.annulee()) return "Déjà contre-passée.";
        Compte de = o.de() == null ? null : store.comptes.get(o.de());
        Compte a = o.a() == null ? null : store.comptes.get(o.a());
        switch (o.type()) {
            case "VIREMENT" -> {
                if (a == null || de == null) return "Comptes introuvables.";
                long rendu = Math.min(o.montant(), a.solde);
                a.solde -= rendu;
                de.solde += rendu;
                a.noter(-rendu, "contre-passation op " + n);
                de.noter(rendu, "contre-passation op " + n);
            }
            case "STAFF", "SOLDE", "HERITAGE" -> {
                if (a != null) { long r = Math.min(o.montant(), a.solde); a.solde -= r; a.noter(-r, "contre-passation op " + n); }
                if (de != null) { de.solde += o.montant(); de.noter(o.montant(), "contre-passation op " + n); }
                if ("SOLDE".equals(o.type()) && a != null) store.tresors.merge(a.village, o.montant(), Long::sum);
            }
            default -> { return "Cette opération ne se contre-passe pas (" + o.type() + ") : corrige avec /ryo."; }
        }
        store.operations.put(n, o.annuler());
        long op = store.nouvelleOp("CONTREPASSATION", o.a(), o.de(), o.montant(), 0, s.getName(), raison);
        store.marquer();
        journal("CONTREPASSATION", s, a != null ? a : de, "op " + n + " (" + o.type() + ", " + Bourse.fmt(o.montant())
                + " ryo) annulée · « " + raison + " »", op, null);
        return null;
    }

    /** Remise à zéro du personnage (staff) : compte à 0, palier 1, coffre versé aux saisies du village (journalisé). */
    public void reinitialiser(CommandSender s, ShinobiCharacter c) {
        Compte a = compte(c);
        List<String> objets = new ArrayList<>();
        for (ItemStack it : a.coffre) {
            if (it == null || it.getType().isAir()) continue;
            objets.add(it.getAmount() + "× " + RareteItems.nom(it));
            store.saisies.computeIfAbsent(a.village, v -> new ArrayList<>()).add(it);
        }
        long avant = a.solde;
        a.solde = 0;
        a.palier = 1;
        a.coffre = new ItemStack[MonnaieConfig.get().palier(1).cases()];
        a.noter(-avant, "remise à zéro du personnage");
        store.marquer();
        journal("STAFF", s, a, "remise à zéro : compte " + Bourse.fmt(avant) + " → 0, palier 1"
                + (objets.isEmpty() ? "" : ", coffre aux saisies de " + a.village + " (" + String.join(", ", objets) + ")"), 0, null);
    }

    /* ================================================================ trésor */

    public long tresor(String village) { return store.tresors.getOrDefault(MonnaieConfig.village(village), 0L); }

    public long tresorVarier(CommandSender s, String village, String op, long n, String motif) {
        String v = MonnaieConfig.village(village);
        long avant = tresor(v);
        long apres = switch (op) {
            case "ajouter", "donner" -> avant + n;
            case "retirer" -> Math.max(0, avant - n);
            case "fixer" -> Math.max(0, n);
            default -> avant;
        };
        store.tresors.put(v, apres);
        store.marquer();
        Journal.entree(Categorie.ECONOMIE, "TRESOR").acteur(s).cibleTexte("trésor de " + v)
                .detail(Bourse.fmt(avant) + " → " + Bourse.fmt(apres) + " ryo · « " + motif + " »").ecrire();
        return apres;
    }

    /* ================================================================ mort RP, succession */

    /** Le personnage vient de mourir (RPK exécuté, ou staff) : compte et coffre gelés {@code banque.mort-rp.gel-jours}. */
    public void onMortRp(ShinobiCharacter c) {
        if (c == null) return;
        Compte a = compte(c);
        if (a.mortLe > 0 && !a.regle) return;
        a.mortLe = System.currentTimeMillis();
        a.regle = false;
        store.marquer();
        BanqueStore.Testament t = store.testaments.get(c.id());
        journal("GEL_MORT", null, a, "mort RP : compte " + Bourse.fmt(a.solde) + " ryo et coffre " + a.occupees()
                + " objet(s) gelés " + MonnaieConfig.get().gelMortJours + " jours"
                + (t != null && t.valide() ? " · testament validé (" + t.heritiers().size() + " héritier(s))" : " · puis trésor du village"), 0, null);
    }

    /** Mort RP exécutée (RPK validé). */
    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onMort(com.reborn.shinobicore.event.CharacterDeathEvent ev) {
        plugin.characters().findById(ev.playerId(), ev.characterId()).ifPresent(this::onMortRp);
    }

    private void successions() {
        long now = System.currentTimeMillis();
        // morts posées par le staff (fiche du personnage) : pas d'événement, on les voit ici
        for (Compte a : new ArrayList<>(store.comptes.values())) {
            if (a.mortLe > 0 || a.owner == null) continue;
            CharacterService.ResolvedCharacter rc = plugin.characters().resolveOwnedById(a.perso);
            if (rc != null && rc.character().dead()) onMortRp(rc.character());
        }
        long gel = MonnaieConfig.get().gelMortJours * 86_400_000L;
        for (Compte a : new ArrayList<>(store.comptes.values())) {
            if (a.mortLe <= 0 || a.regle) continue;
            ShinobiCharacter c = a.owner == null ? null : plugin.characters().findById(a.owner, a.perso).orElse(null);
            if (c != null && !c.dead()) {   // le staff a annulé la mort
                a.mortLe = 0;
                store.marquer();
                journal("DEGEL_MORT", null, a, "mort RP annulée : compte dégelé", 0, null);
                continue;
            }
            if (now < a.mortLe + gel) continue;
            regler(a);
        }
    }

    /** Verse le compte et le coffre d'un mort aux héritiers d'un testament validé, sinon au trésor du village. */
    void regler(Compte a) {
        BanqueStore.Testament t = store.testaments.get(a.perso);
        long solde = a.solde;
        List<ItemStack> objets = new ArrayList<>();
        for (ItemStack s : a.coffre) if (s != null && !s.getType().isAir()) objets.add(s);
        if (t != null && t.valide() && !t.heritiers().isEmpty()) {
            int total = t.heritiers().stream().mapToInt(BanqueStore.Heritier::part).sum();
            long verse = 0;
            Compte premier = null;
            for (int i = 0; i < t.heritiers().size(); i++) {
                BanqueStore.Heritier h = t.heritiers().get(i);
                ShinobiCharacter hc = h.owner() == null ? null : plugin.characters().findById(h.owner(), h.perso()).orElse(null);
                Compte b = hc != null ? compte(hc) : store.comptes.get(h.perso());
                if (b == null) continue;
                if (premier == null) premier = b;
                long part = i == t.heritiers().size() - 1 ? solde - verse : solde * h.part() / Math.max(1, total);
                verse += part;
                b.solde += part;
                b.noter(part, "héritage de " + nomCourt(a));
                long op = store.nouvelleOp("HERITAGE", a.perso, b.perso, part, 0, "testament", "héritage");
                journal("HERITAGE", null, b, "+" + Bourse.fmt(part) + " ryo · héritage de " + a.nom + " (" + h.part() + " %)", op, null);
            }
            for (ItemStack s : objets) {
                boolean place = false;
                if (premier != null) {
                    for (int i = 0; i < premier.coffre.length && !place; i++)
                        if (premier.coffre[i] == null) { premier.coffre[i] = s; place = true; }
                }
                if (!place) store.saisies.computeIfAbsent(a.village, v -> new ArrayList<>()).add(s);
            }
            if (verse < solde) store.tresors.merge(a.village, solde - verse, Long::sum);
        } else {
            store.tresors.merge(a.village, solde, Long::sum);
            store.saisies.computeIfAbsent(a.village, v -> new ArrayList<>()).addAll(objets);
            long op = store.nouvelleOp("SUCCESSION", a.perso, null, solde, 0, "banque", "trésor de " + a.village);
            journal("SUCCESSION", null, a, Bourse.fmt(solde) + " ryo et " + objets.size() + " objet(s) versés au trésor de "
                    + a.village + " (pas de testament validé)", op, null);
        }
        a.solde = 0;
        a.coffre = new ItemStack[a.coffre.length];
        a.regle = true;
        a.noter(-solde, "succession");
        store.marquer();
    }

    /* ================================================================ testaments */

    public BanqueStore.Testament testament(UUID perso) { return store.testaments.get(perso); }

    public void deposerTestament(CommandSender s, ShinobiCharacter c, List<BanqueStore.Heritier> hs, String texte, boolean valide) {
        store.testaments.put(c.id(), new BanqueStore.Testament(c.id(), texte, hs, valide ? "VALIDE" : "DEPOSE",
                System.currentTimeMillis(), s.getName(), valide ? s.getName() : ""));
        store.marquer();
        StringBuilder b = new StringBuilder();
        for (BanqueStore.Heritier h : hs) b.append(b.length() > 0 ? ", " : "").append(h.nom()).append(" ").append(h.part()).append(" %");
        Journal.entree(Categorie.ECONOMIE, "TESTAMENT").acteur(s).ciblePersonnage(c.ownerId(), c.id())
                .detail((valide ? "testament enregistré et validé" : "testament déposé (à valider)") + " · " + b
                        + (texte == null || texte.isBlank() ? "" : " · « " + texte + " »")).ecrire();
    }

    public String validerTestament(CommandSender s, UUID perso, boolean valider) {
        BanqueStore.Testament t = store.testaments.get(perso);
        if (t == null) return "Aucun testament.";
        if (valider) store.testaments.put(perso, new BanqueStore.Testament(t.perso(), t.texte(), t.heritiers(), "VALIDE", t.le(), t.par(), s.getName()));
        else store.testaments.remove(perso);
        store.marquer();
        Compte a = store.comptes.get(perso);
        journal("TESTAMENT", s, a, valider ? "testament validé" : "testament révoqué", 0, null);
        return null;
    }

    /* ================================================================ masse monétaire */

    public record Masse(long bourses, long comptes, long tresor, int n) {}

    public Masse masse(String village) {
        String v = village == null ? null : MonnaieConfig.village(village);
        Map<UUID, Long> enLigne = module.bourses() == null ? new HashMap<>() : module.bourses().enLigneMontants();
        long bourses = 0, comptes = 0;
        int n = 0;
        for (Compte a : store.comptes.values()) {
            if (v != null && !v.equals(a.village)) continue;
            if (a.mortLe > 0) continue;
            comptes += a.solde;
            bourses += enLigne.getOrDefault(a.perso, a.derniereBourse);
            n++;
        }
        long t = 0;
        if (v != null) t = tresor(v);
        else for (long x : store.tresors.values()) t += x;
        return new Masse(bourses, comptes, t, n);
    }

    /* ================================================================ fiche staff (Poste de garde) */

    /** Section « Économie » de la fiche joueur du Poste de garde (volet v3). */
    public com.google.gson.JsonObject ficheStaff(ShinobiCharacter c, Player p) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        Compte a = compte(c);
        long bourse = p != null && module.bourses() != null ? module.bourses().montant(p) : a.derniereBourse;
        MonnaieConfig k = MonnaieConfig.get();
        MonnaieConfig.Palier pa = k.palier(a.palier);
        o.addProperty("bourse", bourse);
        o.addProperty("compte", a.solde);
        o.addProperty("palier", a.palier);
        o.addProperty("palierNom", pa.nom());
        o.addProperty("plafond", pa.plafond());
        o.addProperty("cases", a.coffre.length);
        o.addProperty("occupees", a.occupees());
        StringBuilder co = new StringBuilder();
        for (ItemStack s : a.coffre) {
            if (s == null || s.getType().isAir()) continue;
            if (co.length() > 0) co.append(", ");
            co.append(RareteItems.nom(s)).append(s.getAmount() > 1 ? " ×" + s.getAmount() : "");
        }
        o.addProperty("coffre", co.toString());
        o.addProperty("solde", k.bareme.getOrDefault(c.rank(), 0L));
        o.addProperty("grade", c.rank().displayName());
        o.addProperty("soldeRecue", a.soldeMontant > 0 && !a.soldeSemaine.isEmpty());
        Compte.Ligne der = a.historique.peekFirst();
        if (der != null) {
            o.addProperty("dernier", der.delta());
            o.addProperty("dernierTexte", der.texte() + " · " + new java.text.SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(new java.util.Date(der.le())));
        }
        if (a.gele()) o.addProperty("gele", a.gelMotif + " · par " + a.gelPar);
        BanqueStore.Testament t = store.testaments.get(c.id());
        if (t != null) o.addProperty("testament", t.valide() ? "validé" : "déposé");
        return o;
    }

    static boolean refusRarete(ItemStack s, RpRarity min) { return RareteItems.of(s).auMoins(min); }
}
