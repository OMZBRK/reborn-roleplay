package com.reborn.shinobicore.banque;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.character.PlayerIds;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.inventory.InventoryManager;
import com.reborn.shinobicore.inventory.Notifs;
import com.reborn.shinobicore.inventory.RareteItems;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.monnaie.Bourse;
import com.reborn.shinobicore.monnaie.BourseService;
import com.reborn.shinobicore.monnaie.MonnaieConfig;
import com.reborn.shinobicore.monnaie.MonnaieModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L'échange garanti (dépôt fiduciaire) au guichet — « l'échange garanti à la banque » de la décision du 14/10,
 * MONNAIE_ET_BANQUE.md §4.3, décision 5 du 15/10 (scellé par le guichetier, frais 2 %) :
 * <ol>
 *   <li>les deux personnages sont au même guichet ({@code banque.echange.distance}) ; l'un ouvre et choisit l'autre ;</li>
 *   <li>chacun dépose des ryo (bourse ou compte) et / ou des objets de sa sacoche : <b>retirés dès le dépôt</b> (pas
 *       copiés) ; on ne voit que ce que l'autre dépose ;</li>
 *   <li>chacun confirme ; tout changement annule les deux confirmations ;</li>
 *   <li>avec un guichetier joueur en service, il <b>scelle</b> (ou refuse) ; sinon le guichet scelle seul ;</li>
 *   <li>exécution d'un bloc : objets dans la sacoche (sinon le coffre du compte, sinon refus avant le sceau) ; ryo dans la
 *       bourse ou sur le compte (au choix de celui qui reçoit), <b>frais 2 %</b> (min. 10) retenus sur ce qui est reçu ;</li>
 *   <li>annulation (bouton, déconnexion, éloignement) : tout revient à sa place. Un seul échange par personnage. Journal à
 *       chaque étape.</li>
 * </ol>
 */
public final class EchangeGaranti implements Listener {

    static final class Cote {
        UUID joueur, perso;
        String nom;
        long ryo;
        String source = "bourse";
        String recevoir = "compte";
        final List<ItemStack> objets = new ArrayList<>();
        boolean confirme;
    }

    static final class Echange {
        long id;
        String guichet;
        Location at;
        String village;
        final Cote a = new Cote(), b = new Cote();
        boolean attenteSceau;
        Cote cote(UUID j) { return a.joueur.equals(j) ? a : b.joueur.equals(j) ? b : null; }
        Cote autre(UUID j) { return a.joueur.equals(j) ? b : a; }
    }

    private final ShinobiCore plugin;
    private final MonnaieModule module;
    private final Map<UUID, Echange> parJoueur = new ConcurrentHashMap<>();
    private long prochain = 1;

    public EchangeGaranti(ShinobiCore plugin, MonnaieModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    public void start() { Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L); }

    Echange de(UUID joueur) { return parJoueur.get(joueur); }

    private BanqueService banque() { return module.banque(); }

    private void journal(String action, Player acteur, Echange e, String detail) {
        Journal.entree(Categorie.ECONOMIE, action).acteur(acteur).cibleTexte(e.a.nom + " ↔ " + e.b.nom)
                .detail("échange n° " + e.id + " · " + detail).lieu(e.at).ecrire();
    }

    /* ================================================================ ouvrir */

    public String ouvrir(Player p, UUID autreId, Guichets.Ouverture o) {
        if (parJoueur.containsKey(p.getUniqueId())) return "Tu as déjà un échange en cours.";
        Player q = Bukkit.getPlayer(autreId);
        if (q == null || q.equals(p)) return "Personne ici.";
        if (parJoueur.containsKey(q.getUniqueId())) return "Cette personne a déjà un échange en cours.";
        double d = MonnaieConfig.get().echangeDistance;
        if (o.at() == null || !q.getWorld().equals(o.at().getWorld()) || q.getLocation().distance(o.at()) > d + 1
                || p.getLocation().distance(o.at()) > d + 1) return "Vous devez être tous les deux au guichet.";
        ShinobiCharacter cp = plugin.characters().getActive(p.getUniqueId()), cq = plugin.characters().getActive(q.getUniqueId());
        if (cp == null || cq == null) return "Personne ici.";
        String r1 = banque().acces(banque().compte(cp), o.village()).refus();
        if (r1 != null) return r1;
        String r2 = banque().acces(banque().compte(cq), o.village()).refus();
        if (r2 != null) return "Le guichetier ne peut pas servir " + nom(q, p) + ".";
        Echange e = new Echange();
        e.id = prochain++;
        e.guichet = o.id();
        e.at = o.at();
        e.village = o.village();
        e.a.joueur = p.getUniqueId(); e.a.perso = cp.id(); e.a.nom = cp.name();
        e.b.joueur = q.getUniqueId(); e.b.perso = cq.id(); e.b.nom = cq.name();
        parJoueur.put(p.getUniqueId(), e);
        parJoueur.put(q.getUniqueId(), e);
        journal("ECHANGE_OUVERT", p, e, "ouvert par " + PlayerIds.prefix(cp) + cp.name() + " · " + o.libelle());
        Notifs.msg(q, "info", null, nom(p, q) + " te propose un échange garanti", "au guichet");
        module.guichetCanal().ouvrirSurOnglet(q, 3);
        return null;
    }

    private String nom(Player de, Player pour) {
        ShinobiCharacter c = plugin.characters().getActive(de.getUniqueId());
        ShinobiCharacter v = plugin.characters().getActive(pour.getUniqueId());
        if (c == null) return de.getName();
        if (v != null) {
            var k = v.knownNameOf(c.id());
            if (k != null) return k.display();
        }
        return c.name();
    }

    /* ================================================================ déposer */

    private void changement(Echange e) {
        e.a.confirme = false;
        e.b.confirme = false;
        e.attenteSceau = false;
        rafraichir(e);
    }

    /** Fixe les ryo déposés (l'ancien dépôt revient d'abord à sa source). */
    public String deposerRyo(Player p, long n, String source) {
        Echange e = parJoueur.get(p.getUniqueId());
        if (e == null) return "Aucun échange.";
        Cote c = e.cote(p.getUniqueId());
        rendreRyo(p, c);
        if (n <= 0) { changement(e); return null; }
        c.source = "compte".equals(source) ? "compte" : "bourse";
        if ("compte".equals(c.source)) {
            Compte a = banque().compte(p);
            if (a == null || a.solde < n) { changement(e); return "Pas autant sur ton compte."; }
            a.solde -= n;
            a.noter(-n, "dépôt d'échange garanti n° " + e.id);
            banque().store.marquer();
        } else if (!module.bourses().debiter(p, n, "ECHANGE_DEPOT", "dépôt d'échange garanti n° " + e.id, p)) {
            changement(e);
            return "Pas autant dans ta bourse.";
        }
        c.ryo = n;
        journal("ECHANGE_DEPOT", p, e, c.nom + " dépose " + Bourse.fmt(n) + " ryo (" + c.source + ")");
        changement(e);
        return null;
    }

    private void rendreRyo(Player p, Cote c) {
        if (c.ryo <= 0) return;
        long n = c.ryo;
        c.ryo = 0;
        if ("compte".equals(c.source) || p == null || !module.bourses().crediter(p, n, "ECHANGE_RENDU", "dépôt d'échange rendu", p)) {
            Compte a = banque().compte(c.perso);
            if (a != null) { a.solde += n; a.noter(n, "dépôt d'échange rendu"); banque().store.marquer(); }
        }
    }

    public String deposerObjet(Player p, String ref) {
        Echange e = parJoueur.get(p.getUniqueId());
        if (e == null) return "Aucun échange.";
        BourseService.Emplacements em = module.bourses().sur(p);
        ItemStack s = em.lire(ref);
        if (s == null || s.getType().isAir()) return null;
        if (Bourse.est(s)) return "Pour les ryo, utilise « Déposer des ryo ».";
        Cote c = e.cote(p.getUniqueId());
        if (c.objets.size() >= 9) return "Neuf objets au plus.";
        c.objets.add(s.clone());
        em.ecrire(ref, null);
        p.updateInventory();
        InventoryManager im = plugin.rpInventory();
        if (im != null) { im.push(p, false); im.recalc(p); }
        journal("ECHANGE_DEPOT", p, e, c.nom + " dépose " + s.getAmount() + " × " + RareteItems.nom(s));
        changement(e);
        return null;
    }

    public String retirerObjet(Player p, int i) {
        Echange e = parJoueur.get(p.getUniqueId());
        if (e == null) return "Aucun échange.";
        Cote c = e.cote(p.getUniqueId());
        if (i < 0 || i >= c.objets.size()) return null;
        ItemStack s = c.objets.get(i);
        InventoryManager im = plugin.rpInventory();
        String r = im == null ? null : im.refusReception(p, s);
        if (r != null) return Character.toUpperCase(r.charAt(0)) + r.substring(1) + ".";
        c.objets.remove(i);
        if (im != null) im.recevoir(p, s.clone()); else p.getInventory().addItem(s.clone());
        journal("ECHANGE_DEPOT", p, e, c.nom + " reprend " + s.getAmount() + " × " + RareteItems.nom(s));
        changement(e);
        return null;
    }

    public void recevoir(Player p, String ou) {
        Echange e = parJoueur.get(p.getUniqueId());
        if (e == null) return;
        e.cote(p.getUniqueId()).recevoir = "bourse".equals(ou) ? "bourse" : "compte";
        rafraichir(e);
    }

    /* ================================================================ confirmer, sceller */

    public String confirmer(Player p) {
        Echange e = parJoueur.get(p.getUniqueId());
        if (e == null) return "Aucun échange.";
        Cote c = e.cote(p.getUniqueId());
        if (c.ryo <= 0 && c.objets.isEmpty() && e.autre(p.getUniqueId()).ryo <= 0 && e.autre(p.getUniqueId()).objets.isEmpty())
            return "Rien n'est déposé.";
        c.confirme = true;
        journal("ECHANGE_CONFIRME", p, e, c.nom + " confirme");
        if (e.a.confirme && e.b.confirme) {
            BanqueStore.Guichet g = banque().store.guichets.get(e.guichet);
            Player gu = g == null ? null : module.guichets().guichetier(g, null);
            if (gu != null && !gu.getUniqueId().equals(e.a.joueur) && !gu.getUniqueId().equals(e.b.joueur)) {
                e.attenteSceau = true;
                Notifs.msg(gu, "info", null, "Échange prêt à sceller", e.a.nom + " ↔ " + e.b.nom + " · /banque sceller");
                module.guichetCanal().envoyerSceau(gu, e);
                rafraichir(e);
                return null;
            }
            return executer(e, null);
        }
        rafraichir(e);
        return null;
    }

    /** Le guichetier joueur scelle (ou refuse) l'échange qui attend près de lui. */
    public String sceller(Player gu, boolean ok) {
        BanqueStore.Guichet g = module.guichets().enService(gu);
        if (g == null) return "Tu n'es pas guichetier en service ici.";
        for (Echange e : new ArrayList<>(parJoueur.values())) {
            if (!e.attenteSceau || !e.guichet.equals(g.id())) continue;
            if (!ok) { annuler(e, "refusé par le guichetier", gu); return null; }
            return executer(e, gu);
        }
        return "Aucun échange n'attend ton sceau.";
    }

    /** Exécute d'un bloc (vérifie d'abord que tout tient). */
    private String executer(Echange e, Player guichetier) {
        Player pa = Bukkit.getPlayer(e.a.joueur), pb = Bukkit.getPlayer(e.b.joueur);
        if (pa == null || pb == null) { annuler(e, "un des deux est parti", null); return "Échange annulé."; }
        String pbA = place(pb, e.a.objets), pbB = place(pa, e.b.objets);
        if (pbA != null || pbB != null) {
            e.a.confirme = false; e.b.confirme = false; e.attenteSceau = false;
            rafraichir(e);
            String qui = pbA != null ? e.b.nom : e.a.nom;
            return "L'échange ne tient pas dans la sacoche ni le coffre de " + qui + ".";
        }
        MonnaieConfig k = MonnaieConfig.get();
        long fa = BanqueService.frais(e.a.ryo, k.echangePourcent, k.echangeMin);
        long fb = BanqueService.frais(e.b.ryo, k.echangePourcent, k.echangeMin);
        long op = banque().store.nouvelleOp("ECHANGE", e.a.perso, e.b.perso, e.a.ryo + e.b.ryo, fa + fb,
                guichetier != null ? guichetier.getName() : "guichet", "échange n° " + e.id);
        livrer(pb, e.b, e.a.objets, Math.max(0, e.a.ryo - fa), e);
        livrer(pa, e.a, e.b.objets, Math.max(0, e.b.ryo - fb), e);
        StringBuilder d = new StringBuilder();
        d.append(e.a.nom).append(" donne ").append(resume(e.a)).append(" · ").append(e.b.nom).append(" donne ").append(resume(e.b));
        if (fa + fb > 0) d.append(" · frais ").append(Bourse.fmt(fa + fb)).append(" ryo");
        d.append(" · scellé par ").append(guichetier != null ? nomPerso(guichetier) : "le guichet").append(" · op ").append(op);
        journal("ECHANGE_SCELLE", guichetier != null ? guichetier : pa, e, d.toString());
        if (e.a.ryo + e.b.ryo >= k.echangeAlerte && plugin.staffPanel() != null)
            plugin.staffPanel().onAlerteEconomie(pa.getUniqueId(), "Échange garanti de " + Bourse.fmt(e.a.ryo + e.b.ryo)
                    + " ryo · " + e.a.nom + " ↔ " + e.b.nom + " · op " + op, e.at, 30);
        e.a.ryo = 0; e.b.ryo = 0; e.a.objets.clear(); e.b.objets.clear();
        fin(e);
        Notifs.msg(pa, "info", null, "Échange scellé", null);
        Notifs.msg(pb, "info", null, "Échange scellé", null);
        return null;
    }

    private String nomPerso(Player p) {
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        return c != null ? c.name() : p.getName();
    }

    private static String resume(Cote c) {
        List<String> l = new ArrayList<>();
        if (c.ryo > 0) l.add(Bourse.fmt(c.ryo) + " ryo");
        for (ItemStack s : c.objets) l.add(s.getAmount() + " × " + RareteItems.nom(s));
        return l.isEmpty() ? "rien" : String.join(", ", l);
    }

    /** Ces objets tiennent-ils dans la sacoche (puis le coffre du compte) de {@code p} ? null si oui. */
    private String place(Player p, List<ItemStack> objets) {
        if (objets.isEmpty()) return null;
        InventoryManager im = plugin.rpInventory();
        Compte a = banque().compte(p);
        int libresCoffre = 0;
        if (a != null) for (ItemStack s : a.coffre) if (s == null) libresCoffre++;
        int horsSac = 0;
        for (ItemStack s : objets) if (im != null && im.refusReception(p, s) != null) horsSac++;
        // approximation prudente : ce qui ne rentre pas seul dans la sacoche doit tenir au coffre
        return horsSac <= libresCoffre ? null : "plus de place";
    }

    private void livrer(Player p, Cote recoit, List<ItemStack> objets, long ryo, Echange e) {
        InventoryManager im = plugin.rpInventory();
        Compte a = banque().compte(p);
        for (ItemStack s : objets) {
            boolean ok = false;
            if (im != null && im.refusReception(p, s) == null) ok = im.recevoir(p, s.clone()) == 0;
            if (!ok && a != null) {
                for (int i = 0; i < a.coffre.length && !ok; i++) if (a.coffre[i] == null) { a.coffre[i] = s.clone(); ok = true; }
                banque().store.marquer();
            }
            if (!ok) com.reborn.shinobicore.inventory.InventoryManager.donner(p, s.clone());
        }
        if (ryo > 0) {
            boolean ok = "bourse".equals(recoit.recevoir) && module.bourses().crediter(p, ryo, "ECHANGE_RECU", "échange garanti n° " + e.id, p);
            if (!ok && a != null) { a.solde += ryo; a.noter(ryo, "échange garanti n° " + e.id); banque().store.marquer(); }
        }
    }

    /* ================================================================ annuler */

    public void annuler(Player p) {
        Echange e = parJoueur.get(p.getUniqueId());
        if (e != null) annuler(e, "annulé par " + e.cote(p.getUniqueId()).nom, p);
    }

    void annuler(Echange e, String raison, Player acteur) {
        Player pa = Bukkit.getPlayer(e.a.joueur), pb = Bukkit.getPlayer(e.b.joueur);
        rendre(pa, e.a);
        rendre(pb, e.b);
        journal("ECHANGE_ANNULE", acteur != null ? acteur : pa, e, raison + " · tout est rendu");
        fin(e);
        if (pa != null) Notifs.msg(pa, "refus", null, "Échange annulé", raison);
        if (pb != null) Notifs.msg(pb, "refus", null, "Échange annulé", raison);
    }

    private void rendre(Player p, Cote c) {
        rendreRyo(p, c);
        InventoryManager im = plugin.rpInventory();
        for (ItemStack s : c.objets) {
            if (p != null) InventoryManager.donner(p, s.clone());
            else {
                Compte a = banque().compte(c.perso);
                boolean ok = false;
                if (a != null) for (int i = 0; i < a.coffre.length && !ok; i++) if (a.coffre[i] == null) { a.coffre[i] = s.clone(); ok = true; }
                if (!ok && a != null) banque().store.saisies.computeIfAbsent(a.village, v -> new ArrayList<>()).add(s.clone());
                banque().store.marquer();
            }
        }
        c.objets.clear();
        if (im != null && p != null) im.recalc(p);
    }

    private void fin(Echange e) {
        parJoueur.remove(e.a.joueur);
        parJoueur.remove(e.b.joueur);
        rafraichir(e);
    }

    private void rafraichir(Echange e) {
        for (UUID j : new UUID[] {e.a.joueur, e.b.joueur}) {
            Player p = Bukkit.getPlayer(j);
            if (p != null) module.guichetCanal().envoyerEtat(p, null);
        }
    }

    /* ================================================================ garde-fous */

    private void tick() {
        double d = MonnaieConfig.get().echangeDistance + 2;
        for (Echange e : new ArrayList<>(new java.util.HashSet<>(parJoueur.values()))) {
            for (Cote c : new Cote[] {e.a, e.b}) {
                Player p = Bukkit.getPlayer(c.joueur);
                if (p == null || e.at == null || !p.getWorld().equals(e.at.getWorld()) || p.getLocation().distance(e.at) > d) {
                    annuler(e, c.nom + " s'est éloigné du guichet", null);
                    break;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent ev) {
        Echange e = parJoueur.get(ev.getPlayer().getUniqueId());
        if (e != null) annuler(e, e.cote(ev.getPlayer().getUniqueId()).nom + " s'est déconnecté", null);
    }

    /** À l'arrêt du serveur : tout est rendu. */
    public void stop() {
        for (Echange e : new ArrayList<>(new java.util.HashSet<>(parJoueur.values()))) annuler(e, "arrêt du serveur", null);
    }

    /** Ce que voit {@code p} de l'échange (JSON pour l'onglet). */
    public com.google.gson.JsonObject etat(Player p) {
        Echange e = parJoueur.get(p.getUniqueId());
        if (e == null) return null;
        Cote moi = e.cote(p.getUniqueId()), lui = e.autre(p.getUniqueId());
        InventoryManager im = plugin.rpInventory();
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("id", e.id);
        Player q = Bukkit.getPlayer(lui.joueur);
        o.addProperty("autre", q != null ? nom(q, p) : lui.nom);
        o.add("moi", cote(moi, im));
        o.add("lui", cote(lui, im));
        o.addProperty("attenteSceau", e.attenteSceau);
        BanqueStore.Guichet g = banque().store.guichets.get(e.guichet);
        Player gu = g == null ? null : module.guichets().guichetier(g, null);
        if (gu != null && !gu.getUniqueId().equals(e.a.joueur) && !gu.getUniqueId().equals(e.b.joueur)) o.addProperty("guichetier", nomPerso(gu));
        MonnaieConfig k = MonnaieConfig.get();
        o.addProperty("frais", BanqueService.frais(moi.ryo, k.echangePourcent, k.echangeMin)
                + BanqueService.frais(lui.ryo, k.echangePourcent, k.echangeMin));
        o.addProperty("pourcent", k.echangePourcent);
        o.addProperty("recevoir", moi.recevoir);
        return o;
    }

    private static com.google.gson.JsonObject cote(Cote c, InventoryManager im) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("ryo", c.ryo);
        o.addProperty("source", c.source);
        o.addProperty("confirme", c.confirme);
        com.google.gson.JsonArray a = new com.google.gson.JsonArray();
        for (ItemStack s : c.objets) a.add(com.google.gson.JsonParser.parseString(im == null ? "null" : im.jsonObjet(s)));
        o.add("objets", a);
        return o;
    }
}
