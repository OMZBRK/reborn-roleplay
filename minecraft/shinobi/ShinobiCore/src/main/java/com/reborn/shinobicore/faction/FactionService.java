package com.reborn.shinobicore.faction;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.api.event.CharacterSwitchEvent;
import com.reborn.shinobicore.character.PlayerIdRegistry;
import com.reborn.shinobicore.character.Rank;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.faction.FactionData.Annonce;
import com.reborn.shinobicore.faction.FactionData.Charge;
import com.reborn.shinobicore.faction.FactionData.Faction;
import com.reborn.shinobicore.faction.FactionData.GradeSection;
import com.reborn.shinobicore.faction.FactionData.Ligne;
import com.reborn.shinobicore.faction.FactionData.Membre;
import com.reborn.shinobicore.faction.FactionData.Section;
import com.reborn.shinobicore.inventory.Notifs;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.staff.panel.StaffGrades;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le système Faction côté serveur (docs/gameplay/FACTION.md, lots beta F1 à F4 et F6) : registre des personnages,
 * factions (six villages d'office), sections, grades de section, charges, seuils du village, invitations, caisse et
 * taxe, annonces. <b>Tout est revérifié ici</b> ; le volet (canal {@code reborn:faction}, {@link FactionCanal}) ne fait
 * qu'afficher ce que {@link FactionVue} a filtré. Chaque changement va au journal universel (catégorie {@code FACTION},
 * en ajout seul) et à la ligne courte de la section ou de la faction.
 *
 * <p>Les méthodes d'action renvoient {@code null} si c'est fait, sinon la raison du refus (une phrase courte).
 */
public final class FactionService implements Listener {

    /** Les six villages d'office : {tag, nom, emblème, valeur du champ village}. */
    static final String[][] VILLAGES = {
            {"KON", "Konohagakure", "konoha", "Konohagakure"}, {"SUN", "Sunagakure", "suna", "Sunagakure"},
            {"KIR", "Kirigakure", "kiri", "Kirigakure"}, {"KUM", "Kumogakure", "kumo", "Kumogakure"},
            {"IWA", "Iwagakure", "iwa", "Iwagakure"}, {"AME", "Amegakure", "ame", "Amegakure"}};

    public static final double PORTEE_FACE = 3.5, PORTEE_PANNEAU = 30;
    public static final int SECS_FACE = 10, SECS_PANNEAU = 30;

    /** Invitation en attente (une par invité). */
    public record Invit(String section, UUID de, UUID dePerso, UUID a, long at, int secs, boolean panneau) {
        boolean expiree(long now) { return now - at > secs * 1000L; }
    }

    private final ShinobiCore plugin;
    final FactionStore store;
    private FactionCanal canal;
    final Map<UUID, Invit> invitations = new ConcurrentHashMap<>();

    public FactionService(ShinobiCore plugin) {
        this.plugin = plugin;
        this.store = new FactionStore(plugin);
    }

    public void start() {
        store.start();
        villagesDOffice();
        canal = new FactionCanal(plugin, this);
        canal.start();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getPluginManager().registerEvents(canal, plugin);
        FactionCommand cmd = new FactionCommand(plugin, this);
        var pc = plugin.getCommand("faction");
        if (pc != null) { pc.setExecutor(cmd); pc.setTabCompleter(cmd); }
        // registre : après le chargement des personnages (premier tick), puis les joueurs en ligne toutes les 30 s
        Bukkit.getScheduler().runTask(plugin, this::scanRegistre);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) synchro(plugin.characters().getActive(p.getUniqueId()), true);
        }, 600L, 600L);
        plugin.getLogger().info("[faction] Factions et sections prêtes (canal " + FactionCanal.CHANNEL + ").");
    }

    public void stop() { store.sauver(); }

    public FactionCanal canal() { return canal; }

    /* ================================================================ réglages */

    public int plafond() { return Math.max(0, Math.min(100, plugin.getConfig().getInt("faction.taxe.plafond", 30))); }

    public int tauxDefaut() { return plugin.getConfig().getInt("faction.taxe.defaut", 10); }

    public int taux(Faction f) { return Regles.borner(f.taux < 0 ? tauxDefaut() : f.taux, plafond()); }

    public int placesDefaut() { return Math.max(1, plugin.getConfig().getInt("faction.sections.places-defaut", 8)); }

    /* ================================================================ accès */

    public Faction faction(String tag) { return tag == null ? null : store.factions.get(tag.toUpperCase(Locale.ROOT)); }

    public Section section(String id) { return id == null || id.isEmpty() ? null : store.sections.get(id); }

    public Membre membre(UUID perso) { return perso == null ? null : store.membres.get(perso); }

    public Membre membre(Player p) {
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        if (c == null) return null;
        Membre m = store.membres.get(c.id());
        return m != null ? m : synchro(c, true);
    }

    public java.util.Collection<Faction> factions() { return store.factions.values(); }

    public List<Section> sections(Faction f) {
        List<Section> l = new ArrayList<>();
        for (Section s : store.sections.values()) if (s.faction.equals(f.tag)) l.add(s);
        return l;
    }

    public List<Membre> membres(Faction f) {
        List<Membre> l = new ArrayList<>();
        for (Membre m : store.membres.values()) if (!m.mort && f.tag.equals(m.faction)) l.add(m);
        return l;
    }

    public List<Membre> membres(Section s) {
        List<Membre> l = new ArrayList<>();
        for (Membre m : store.membres.values()) if (!m.mort && s.id.equals(m.section)) l.add(m);
        l.sort((a, b) -> Integer.compare(s.rang(a.grade), s.rang(b.grade)));
        return l;
    }

    public int effectif(Section s) {
        int n = 0;
        for (Membre m : store.membres.values()) if (!m.mort && s.id.equals(m.section)) n++;
        return n;
    }

    /** Joueur en ligne qui joue ce personnage, ou null. */
    public Player enLigne(Membre m) {
        if (m == null || m.owner == null) return null;
        Player p = Bukkit.getPlayer(m.owner);
        if (p == null || !p.isOnline()) return null;
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        return c != null && c.id().equals(m.perso) ? p : null;
    }

    public ShinobiCharacter perso(Player p) { return plugin.characters().getActive(p.getUniqueId()); }

    public Regles.Lecteur lecteur(Membre m) {
        if (m == null) return null;
        Faction f = faction(m.faction);
        Section s = section(m.section);
        boolean chefF = f != null && m.perso.equals(f.chef);
        boolean chefS = s != null && m.perso.equals(s.chef);
        return new Regles.Lecteur(m.faction, m.section, m.rang, chefF, chefS,
                f != null && charge(f, m.perso, Droits.C_SECTIONS), f != null && charge(f, m.perso, Droits.C_CAISSES));
    }

    /** La charge de ce personnage a-t-elle ce droit ? */
    public boolean charge(Faction f, UUID perso, String droit) {
        for (Charge k : f.charges.values()) if (k.titulaires.contains(perso) && k.droits.contains(droit)) return true;
        return false;
    }

    public Charge chargeDe(Faction f, UUID perso) {
        for (Charge k : f.charges.values()) if (k.titulaires.contains(perso)) return k;
        return null;
    }

    /** Droit de grade de section (le chef de section les a tous). */
    public boolean droit(Membre m, String droit) {
        Section s = m == null ? null : section(m.section);
        if (s == null) return false;
        if (m.perso.equals(s.chef)) return true;
        GradeSection g = s.grade(m.grade);
        return g != null && g.droits.contains(droit);
    }

    /** Chef de faction, ou charge qui a ce droit. */
    public boolean gestion(Membre m, Faction f, String droitCharge) {
        if (m == null || f == null || !f.tag.equals(m.faction)) return false;
        return m.perso.equals(f.chef) || (droitCharge != null && charge(f, m.perso, droitCharge));
    }

    /* ================================================================ registre et migration */

    private void villagesDOffice() {
        boolean neuf = false;
        for (String[] v : VILLAGES) {
            if (store.factions.containsKey(v[0])) continue;
            Faction f = new Faction();
            f.tag = v[0]; f.nom = v[1]; f.embleme = v[2]; f.village = v[3];
            f.type = FactionData.Type.VILLAGE;
            f.cree = System.currentTimeMillis();
            store.factions.put(f.tag, f);
            neuf = true;
        }
        if (neuf) store.marquer();
    }

    static String cle(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** La faction-village d'une valeur du champ village (« Konohagakure », « Konoha »…), ou null (Déserteur, aucun). */
    public Faction factionDuVillage(String village) {
        String k = cle(village);
        if (k.isEmpty()) return null;
        for (Faction f : store.factions.values()) {
            if (!f.village()) continue;
            String fv = cle(f.village), fn = cle(f.nom), fe = cle(f.embleme);
            if (k.equals(fv) || k.equals(fn) || (!fe.isEmpty() && (k.equals(fe) || k.startsWith(fe)))) return f;
        }
        return null;
    }

    /**
     * Migration (une fois, puis à chaque démarrage pour rattraper) : tout personnage connu du registre des N° est
     * inscrit, rattaché à la faction de son village actuel (Konoha → KON…), sans section.
     */
    private void scanRegistre() {
        PlayerIdRegistry ids = plugin.playerIds();
        if (ids == null) return;
        int n = 0;
        java.util.Set<UUID> owners = new java.util.HashSet<>();
        for (PlayerIdRegistry.Entry e : ids.entries()) if (!e.deleted() && e.ownerId() != null) owners.add(e.ownerId());
        for (UUID o : owners) {
            try {
                for (ShinobiCharacter c : plugin.characters().getAll(o)) { synchro(c, false); n++; }
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("[faction] Registre : personnages de " + o + " illisibles (" + ex.getMessage() + ").");
            }
        }
        for (PlayerIdRegistry.Entry e : ids.entries()) {
            if (e.deleted() && store.membres.remove(e.characterId()) != null) store.marquerMembres();
        }
        if (!store.migre) {
            store.migre = true;
            store.marquer();
            Journal.entree(Categorie.FACTION, "MIGRATION").cibleTexte("registre")
                    .detail(n + " personnage(s) rattaché(s) à la faction de leur village, sans section").ecrire();
            plugin.getLogger().info("[faction] Migration : " + n + " personnage(s) rattaché(s) à leur village.");
        }
    }

    private static String nomPropre(ShinobiCharacter c) {
        String name = c.name() == null ? "" : c.name();
        String clan = c.clan();
        if (clan != null && !clan.isBlank() && !clan.equalsIgnoreCase("None")) return (name + " " + clan).trim();
        return name.trim();
    }

    /** Tient le registre à jour pour ce personnage (nom, grade, village → faction) ; renvoie son entrée. */
    public Membre synchro(ShinobiCharacter c, boolean actif) {
        if (c == null) return null;
        Membre m = store.membres.get(c.id());
        boolean change = false;
        if (m == null) {
            m = new Membre();
            m.perso = c.id();
            m.depuis = System.currentTimeMillis();
            store.membres.put(c.id(), m);
            change = true;
        }
        String nom = nomPropre(c);
        if (!nom.equals(m.nom)) { m.nom = nom; change = true; }
        if (m.owner == null || !m.owner.equals(c.ownerId())) { m.owner = c.ownerId(); change = true; }
        if (!c.sexe().equals(m.sexe)) { m.sexe = c.sexe(); change = true; }
        if (c.rank() != m.rang) { m.rang = c.rank(); change = true; }
        if (!c.village().equals(m.village)) { m.village = c.village(); change = true; }
        if (c.playerId() != m.pid) { m.pid = c.playerId(); change = true; }
        if (c.dead() != m.mort) { m.mort = c.dead(); change = true; }
        if (actif) { m.vu = System.currentTimeMillis(); }
        // faction : celle du village ; une organisation est gardée tant que le personnage n'a pas de village
        Faction fv = factionDuVillage(c.village());
        Faction actuelle = faction(m.faction);
        String voulue = fv != null ? fv.tag : actuelle != null && !actuelle.village() ? actuelle.tag : "";
        if (!voulue.equals(m.faction)) {
            String avant = m.faction;
            m.faction = voulue;
            change = true;
            if (!m.section.isEmpty()) {
                Section s = section(m.section);
                if (s == null || !s.faction.equals(voulue)) retirerDeSection(m, s, "changement de village");
            }
            if (store.migre) Journal.entree(Categorie.FACTION, "VILLAGE").ciblePersonnage(m.owner, m.perso)
                    .detail(m.nom + " : " + (avant.isEmpty() ? "aucune" : avant) + " → " + (voulue.isEmpty() ? "aucune" : voulue)).ecrire();
        }
        if (m.mort && !m.section.isEmpty()) retirerDeSection(m, section(m.section), "mort RP");
        if (change) store.marquerMembres();
        Section s = section(m.section);
        c.setPretTenue(s == null ? "" : s.tenue);
        return m;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent ev) {
        Player p = ev.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) synchro(plugin.characters().getActive(p.getUniqueId()), true);
        }, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwitch(CharacterSwitchEvent ev) {
        Player p = ev.player();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) synchro(plugin.characters().getActive(p.getUniqueId()), true);
            canal.envoyerVue(p, false);
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent ev) {
        UUID id = ev.getPlayer().getUniqueId();
        invitations.remove(id);
        invitations.values().removeIf(i -> i.de().equals(id));
        Membre m = membre(ev.getPlayer());
        if (m != null) { m.vu = System.currentTimeMillis(); store.marquerMembres(); }
    }

    /* ================================================================ journal */

    void journal(Faction f, Section s, CommandSender acteur, Membre cible, String action, String texte) {
        Ligne l = new Ligne(System.currentTimeMillis(), texte, 0);
        if (s != null) FactionStore.ajouter(s.journal, l);
        if (f != null) FactionStore.ajouter(f.journal, l);
        store.marquer();
        var e = Journal.entree(Categorie.FACTION, action);
        if (acteur != null) e.acteur(acteur);
        if (cible != null && cible.owner != null) e.ciblePersonnage(cible.owner, cible.perso);
        else e.cibleTexte(s != null ? s.nom + " (" + (f == null ? s.faction : f.tag) + ")" : f != null ? f.nom : "faction");
        e.detail((f != null ? "[" + f.tag + "] " : "") + texte).ecrire();
    }

    private void ligne(Membre m, String ton, String gras, String texte) {
        Player p = enLigne(m);
        if (p != null) Notifs.msg(p, ton, null, gras, texte);
    }

    /** Une ligne au membre et au chef de section (s'il n'est pas l'acteur). */
    private void prevenir(Section s, Membre cible, UUID acteur, String gras, String texte) {
        if (cible != null && !cible.perso.equals(acteur)) ligne(cible, "info", gras, texte);
        if (s != null && s.chef != null && !s.chef.equals(acteur) && (cible == null || !s.chef.equals(cible.perso)))
            ligne(membre(s.chef), "info", gras, texte);
    }

    void changement(Faction f) {
        store.marquer();
        store.marquerMembres();
        if (canal != null) canal.rafraichir(f);
    }

    /* ================================================================ sections */

    private static String slug(String s) {
        String k = cle(s).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return k.isEmpty() ? "section" : k.length() > 24 ? k.substring(0, 24) : k;
    }

    private String idLibre(String tag, String nom) {
        String base = tag.toLowerCase(Locale.ROOT) + "_" + slug(nom), id = base;
        for (int i = 2; store.sections.containsKey(id); i++) id = base + "_" + i;
        return id;
    }

    /**
     * Crée une section (chef de faction, charge « sections » ou staff). {@code chef} : un membre de la faction sans
     * section ; il prend le premier grade.
     */
    public String creerSection(CommandSender acteur, Membre parQui, Faction f, String modele, String nom, String insigne,
                               UUID chef, int places, boolean cachee, boolean staff) {
        if (f == null) return "Faction introuvable.";
        if (!staff && !gestion(parQui, f, Droits.C_SECTIONS)) return "Seuls le chef de faction et ses chargés créent des sections.";
        Droits.Modele mo = Droits.modele(modele);
        String n = nom == null || nom.isBlank() ? mo != null ? mo.nom() : "" : nom.trim();
        if (n.isEmpty()) return "Donne un nom à la section.";
        if (n.length() > 32) return "Nom trop long (32 caractères).";
        for (Section x : sections(f)) if (cle(x.nom).equals(cle(n))) return "Une section porte déjà ce nom.";
        Membre mc = membre(chef);
        if (mc == null) return "Choisis le chef de la section.";
        if (!f.tag.equals(mc.faction)) return mc.nom + " n'est pas de " + f.nom + ".";
        if (!mc.section.isEmpty()) return mc.nom + " est déjà dans une section.";
        Section s = new Section();
        s.id = idLibre(f.tag, n);
        s.faction = f.tag;
        s.nom = n;
        s.insigne = Droits.insigne(insigne != null && !insigne.isBlank() ? insigne : mo != null ? mo.insigne() : "missions");
        s.cachee = cachee;
        s.places = Math.max(1, places > 0 ? places : placesDefaut());
        s.chef = chef;
        s.cree = System.currentTimeMillis();
        s.creePar = parQui == null ? null : parQui.perso;
        Droits.Modele src = mo != null ? mo : Droits.libre(n, s.insigne, cachee);
        for (int i = 0; i < src.grades().length; i++) {
            GradeSection g = new GradeSection();
            g.id = slug(src.grades()[i]);
            for (int k = 2; s.grade(g.id) != null; k++) g.id = slug(src.grades()[i]) + k;
            g.nom = src.grades()[i];
            g.droits.addAll(i == 0 ? Droits.tousSection() : src.droits().get(i));
            s.grades.add(g);
        }
        store.sections.put(s.id, s);
        mc.section = s.id;
        mc.grade = s.grades.get(0).id;
        mc.depuis = System.currentTimeMillis();
        journal(f, s, acteur, mc, "SECTION", "Section « " + n + " » créée" + (cachee ? " (cachée)" : "") + (staff ? " par le staff" : "")
                + ", chef : " + mc.nom + ", " + s.places + " places");
        ligne(mc, "info", "Chef de section", "tu diriges « " + n + " »");
        synchroPret(mc, s);
        changement(f);
        return null;
    }

    private boolean peutGererSection(Membre m, Section s, boolean staff) {
        if (staff) return true;
        if (m == null || s == null) return false;
        Faction f = faction(s.faction);
        return m.perso.equals(s.chef) || gestion(m, f, Droits.C_SECTIONS);
    }

    public String places(CommandSender acteur, Membre m, Section s, int n, boolean staff) {
        if (s == null) return "Section introuvable.";
        if (!staff && (m == null || !(m.perso.equals(s.chef) || gestion(m, faction(s.faction), Droits.C_SECTIONS))))
            return "Seul le chef de section fixe les places.";
        if (n < 1 || n > 999) return "Entre 1 et 999 places.";
        int eff = effectif(s);
        if (n < eff) return "Il y a déjà " + eff + " membres : " + eff + " places au moins.";
        if (n == s.places) return null;
        journal(faction(s.faction), s, acteur, null, "PLACES", s.nom + " : places " + s.places + " → " + n);
        s.places = n;
        changement(faction(s.faction));
        return null;
    }

    public String tenue(CommandSender acteur, Membre m, Section s, String id, String nom, String accessoire, boolean staff) {
        if (s == null) return "Section introuvable.";
        if (!staff && (m == null || !m.perso.equals(s.chef))) return "Seul le chef de section choisit la tenue.";
        String t = id == null ? "" : id.trim();
        if (t.length() > 48 || !t.matches("[A-Za-z0-9_\\-]*")) return "Tenue inconnue.";
        s.tenue = t;
        s.tenueNom = nom == null ? "" : nom.trim().length() > 40 ? nom.trim().substring(0, 40) : nom.trim();
        if (accessoire != null) s.accessoire = accessoire.trim().length() > 60 ? accessoire.trim().substring(0, 60) : accessoire.trim();
        for (Membre x : membres(s)) synchroPret(x, s);
        journal(faction(s.faction), s, acteur, null, "TENUE", s.nom + " : tenue de section " + (t.isEmpty() ? "retirée" : "« " + (s.tenueNom.isEmpty() ? t : s.tenueNom) + " » (" + t + ")"));
        changement(faction(s.faction));
        return null;
    }

    /** Prête (ou retire) la tenue de section au personnage s'il est en ligne. */
    private void synchroPret(Membre m, Section s) {
        Player p = enLigne(m);
        if (p == null) return;
        ShinobiCharacter c = perso(p);
        if (c != null) c.setPretTenue(s == null || !s.id.equals(m.section) ? "" : s.tenue);
    }

    public String cacher(CommandSender acteur, Membre m, Section s, boolean cachee, boolean staff) {
        if (s == null) return "Section introuvable.";
        if (!staff && !gestion(m, faction(s.faction), Droits.C_SECTIONS)) return "Seuls le chef de faction et le staff cachent une section.";
        if (s.cachee == cachee) return null;
        s.cachee = cachee;
        journal(faction(s.faction), s, acteur, null, "CACHER", s.nom + (cachee ? " est désormais cachée" : " n'est plus cachée"));
        changement(faction(s.faction));
        return null;
    }

    public String renommer(CommandSender acteur, Membre m, Section s, String nom, String insigne, boolean staff) {
        if (s == null) return "Section introuvable.";
        if (!staff && !gestion(m, faction(s.faction), Droits.C_SECTIONS)) return "Seuls le chef de faction et le staff renomment une section.";
        String n = nom == null ? "" : nom.trim();
        if (n.isEmpty() || n.length() > 32) return "Nom de 1 à 32 caractères.";
        String avant = s.nom;
        s.nom = n;
        if (insigne != null && !insigne.isBlank()) s.insigne = Droits.insigne(insigne);
        journal(faction(s.faction), s, acteur, null, "RENOMMER", "« " + avant + " » → « " + n + " »");
        changement(faction(s.faction));
        return null;
    }

    /** Nomme le chef d'une section (chef de faction, charge « sections », staff) ; l'ancien chef passe au 2ᵉ grade. */
    public String nommerChef(CommandSender acteur, Membre m, Section s, UUID nouveau, boolean staff) {
        if (s == null) return "Section introuvable.";
        Faction f = faction(s.faction);
        if (!staff && !gestion(m, f, Droits.C_SECTIONS)) return "Seuls le chef de faction et le staff nomment un chef de section.";
        Membre n = membre(nouveau);
        if (n == null) return "Personnage introuvable.";
        if (!s.faction.equals(n.faction)) return n.nom + " n'est pas de cette faction.";
        if (!n.section.isEmpty() && !n.section.equals(s.id)) return n.nom + " est déjà dans une autre section.";
        if (nouveau.equals(s.chef)) return null;
        Membre ancien = membre(s.chef);
        if (ancien != null && s.id.equals(ancien.section)) ancien.grade = s.grades.size() > 1 ? s.grades.get(1).id : s.grades.get(0).id;
        if (n.section.isEmpty() && effectif(s) >= s.places) s.places = effectif(s) + 1;
        n.section = s.id;
        n.grade = s.grades.get(0).id;
        s.chef = nouveau;
        journal(f, s, acteur, n, "CHEF", s.nom + " : " + n.nom + " devient chef de section" + (ancien != null ? " (avant : " + ancien.nom + ")" : ""));
        ligne(n, "info", "Chef de section", "tu diriges « " + s.nom + " »");
        synchroPret(n, s);
        changement(f);
        return null;
    }

    /** Dissout une section (chef de faction, staff) : membres libérés, caisse versée au trésor du village. */
    public String dissoudre(CommandSender acteur, Membre m, Section s, String motif, boolean staff) {
        if (s == null) return "Section introuvable.";
        Faction f = faction(s.faction);
        if (!staff && (m == null || f == null || !m.perso.equals(f.chef))) return "Seuls le chef de faction et le staff dissolvent une section.";
        if (motif == null || motif.isBlank()) return "Donne un motif.";
        for (Membre x : membres(s)) {
            x.section = ""; x.grade = "";
            synchroPret(x, null);
            ligne(x, "info", "Section dissoute", "« " + s.nom + " » n'existe plus");
        }
        long caisse = s.caisse;
        if (caisse > 0 && f != null) verserTresor(f, caisse, "dissolution de « " + s.nom + " »");
        store.sections.remove(s.id);
        journal(f, s, acteur, null, "DISSOUDRE", "Section « " + s.nom + " » dissoute · « " + motif.trim() + " »"
                + (caisse > 0 ? " · caisse de " + caisse + " ryo versée au trésor" : ""));
        changement(f);
        return null;
    }

    /* ================================================================ grades de section */

    private String gradeChef(Membre m, Section s, boolean staff) {
        if (s == null) return "Section introuvable.";
        if (staff) return null;
        if (m == null || !(m.perso.equals(s.chef) || m.perso.equals(faction(s.faction).chef))) return "Seul le chef de section gère les grades.";
        return null;
    }

    public String gradeCreer(CommandSender acteur, Membre m, Section s, String nom, boolean staff) {
        String r = gradeChef(m, s, staff);
        if (r != null) return r;
        String n = nom == null ? "" : nom.trim();
        if (n.isEmpty() || n.length() > 24) return "Nom de 1 à 24 caractères.";
        if (s.grades.size() >= 12) return "12 grades au plus.";
        for (GradeSection g : s.grades) if (cle(g.nom).equals(cle(n))) return "Ce grade existe déjà.";
        GradeSection g = new GradeSection();
        g.id = slug(n);
        for (int k = 2; s.grade(g.id) != null; k++) g.id = slug(n) + k;
        g.nom = n;
        // juste au-dessus du dernier (le dernier reste celui des nouveaux)
        s.grades.add(Math.max(1, s.grades.size() - 1), g);
        journal(faction(s.faction), s, acteur, null, "GRADE", s.nom + " : grade « " + n + " » créé");
        changement(faction(s.faction));
        return null;
    }

    public String gradeRenommer(CommandSender acteur, Membre m, Section s, String id, String nom, boolean staff) {
        String r = gradeChef(m, s, staff);
        if (r != null) return r;
        GradeSection g = s.grade(id);
        if (g == null) return "Grade introuvable.";
        String n = nom == null ? "" : nom.trim();
        if (n.isEmpty() || n.length() > 24) return "Nom de 1 à 24 caractères.";
        journal(faction(s.faction), s, acteur, null, "GRADE", s.nom + " : grade « " + g.nom + " » → « " + n + " »");
        g.nom = n;
        changement(faction(s.faction));
        return null;
    }

    public String gradeSupprimer(CommandSender acteur, Membre m, Section s, String id, boolean staff) {
        String r = gradeChef(m, s, staff);
        if (r != null) return r;
        GradeSection g = s.grade(id);
        if (g == null) return "Grade introuvable.";
        int i = s.grades.indexOf(g);
        if (i == 0) return "Le grade du chef ne se supprime pas.";
        if (s.grades.size() <= 2) return "Une section garde au moins deux grades.";
        s.grades.remove(g);
        GradeSection repli = s.dernier();
        for (Membre x : membres(s)) if (x.grade.equals(g.id)) x.grade = repli.id;
        journal(faction(s.faction), s, acteur, null, "GRADE", s.nom + " : grade « " + g.nom + " » supprimé (membres → " + repli.nom + ")");
        changement(faction(s.faction));
        return null;
    }

    /** Déplace un grade d'un cran (sens -1 = plus haut) ; le premier (chef) ne bouge pas. */
    public String gradeOrdre(CommandSender acteur, Membre m, Section s, String id, int sens, boolean staff) {
        String r = gradeChef(m, s, staff);
        if (r != null) return r;
        GradeSection g = s.grade(id);
        if (g == null) return "Grade introuvable.";
        int i = s.grades.indexOf(g), j = i + (sens < 0 ? -1 : 1);
        if (i == 0 || j < 1 || j >= s.grades.size()) return "Ce grade ne peut pas aller plus loin.";
        s.grades.remove(i);
        s.grades.add(j, g);
        journal(faction(s.faction), s, acteur, null, "GRADE", s.nom + " : « " + g.nom + " » passe " + (j + 1) + "ᵉ");
        changement(faction(s.faction));
        return null;
    }

    /** Coche les droits d'un grade : on ne donne que des droits qu'on a (le chef les a tous). */
    public String gradeDroits(CommandSender acteur, Membre m, Section s, String id, java.util.Set<String> droits, boolean staff) {
        String r = gradeChef(m, s, staff);
        if (r != null) return r;
        GradeSection g = s.grade(id);
        if (g == null) return "Grade introuvable.";
        if (s.grades.indexOf(g) == 0) return "Le grade du chef a tous les droits.";
        java.util.Set<String> ok = new java.util.LinkedHashSet<>();
        for (String d : droits) if (Droits.droitSection(d)) ok.add(d);
        if (ok.equals(g.droits)) return null;
        g.droits.clear();
        g.droits.addAll(ok);
        journal(faction(s.faction), s, acteur, null, "DROITS", s.nom + " : droits de « " + g.nom + " » → " + (ok.isEmpty() ? "aucun" : String.join(", ", ok)));
        changement(faction(s.faction));
        return null;
    }

    /* ================================================================ membres */

    /** Monter ({@code sens} = -1) ou descendre (+1) un membre d'un cran, toujours sous son propre grade. */
    public String deplacer(CommandSender acteur, Membre m, Membre cible, int sens, boolean staff) {
        if (cible == null) return "Membre introuvable.";
        Section s = section(cible.section);
        if (s == null) return "Pas de section.";
        if (cible.perso.equals(s.chef)) return "Le chef se change par le chef de faction.";
        int rc = s.rang(cible.grade), nr = rc + (sens < 0 ? -1 : 1);
        if (!staff) {
            boolean chefF = m != null && m.perso.equals(faction(s.faction).chef);
            if (!chefF) {
                if (m == null || !s.id.equals(m.section) || !droit(m, Droits.MONTER)) return "Ton grade ne permet pas de monter ou descendre.";
                int ra = m.perso.equals(s.chef) ? 0 : s.rang(m.grade);
                if (!Regles.sous(ra, rc)) return "Seulement sous ton propre grade.";
                if (!Regles.sous(ra, nr)) return "Tu ne peux pas monter quelqu'un à ton grade.";
            }
        }
        if (nr < 1 || nr >= s.grades.size()) return sens < 0 ? "Plus haut, c'est le chef." : "C'est déjà le dernier grade.";
        GradeSection avant = s.grade(cible.grade), apres = s.grades.get(nr);
        cible.grade = apres.id;
        journal(faction(s.faction), s, acteur, cible, sens < 0 ? "MONTER" : "DESCENDRE",
                s.nom + " : " + cible.nom + " " + (avant == null ? "?" : avant.nom) + " → " + apres.nom);
        prevenir(s, cible, m == null ? null : m.perso, sens < 0 ? "Promotion" : "Changement de grade", s.nom + " : " + apres.nom);
        changement(faction(s.faction));
        return null;
    }

    public String exclure(CommandSender acteur, Membre m, Membre cible, String motif, boolean staff) {
        if (cible == null) return "Membre introuvable.";
        Section s = section(cible.section);
        if (s == null) return "Pas de section.";
        if (cible.perso.equals(s.chef)) return "On n'exclut pas le chef de section.";
        if (!staff) {
            boolean chefF = m != null && m.perso.equals(faction(s.faction).chef);
            if (!chefF) {
                if (m == null || !s.id.equals(m.section) || !droit(m, Droits.EXCLURE)) return "Ton grade ne permet pas d'exclure.";
                int ra = m.perso.equals(s.chef) ? 0 : s.rang(m.grade);
                if (!Regles.sous(ra, s.rang(cible.grade))) return "Seulement sous ton propre grade.";
            }
        }
        String mt = motif == null ? "" : motif.trim();
        retirerDeSection(cible, s, null);
        journal(faction(s.faction), s, acteur, cible, "EXCLURE", s.nom + " : " + cible.nom + " exclu" + (cible.feminin() ? "e" : "")
                + (mt.isEmpty() ? "" : " · « " + mt + " »"));
        ligne(cible, "refus", "Exclu de la section", s.nom + (mt.isEmpty() ? "" : " · " + mt));
        prevenir(s, null, m == null ? null : m.perso, "Exclusion", cible.nom + " quitte " + s.nom);
        changement(faction(s.faction));
        return null;
    }

    public String quitter(Player p) {
        Membre m = membre(p);
        Section s = m == null ? null : section(m.section);
        if (s == null) return "Tu n'es dans aucune section.";
        if (m.perso.equals(s.chef)) return "Tu diriges la section : le chef de faction doit d'abord nommer un autre chef.";
        retirerDeSection(m, s, null);
        journal(faction(s.faction), s, p, m, "QUITTER", s.nom + " : " + m.nom + " quitte la section");
        prevenir(s, null, m.perso, "Départ", m.nom + " quitte " + s.nom);
        changement(faction(s.faction));
        return null;
    }

    private void retirerDeSection(Membre m, Section s, String raison) {
        m.section = "";
        m.grade = "";
        store.marquerMembres();
        synchroPret(m, null);
        if (raison != null && s != null) journal(faction(s.faction), s, null, m, "QUITTER", s.nom + " : " + m.nom + " retiré(e) · " + raison);
    }

    /* ================================================================ invitations */

    /** Peut-il inviter ? (null = oui) — pour le menu R du client et avant chaque invitation. */
    public String refusInviter(Membre m) {
        Section s = m == null ? null : section(m.section);
        if (s == null) return "Tu n'es dans aucune section.";
        if (!droit(m, Droits.INVITER)) return "Ton grade de section ne permet pas d'inviter.";
        if (effectif(s) >= s.places) return "La section est pleine (" + s.places + " places).";
        return null;
    }

    /** Pourquoi ce personnage ne peut pas rejoindre la section (null = il peut). */
    public String refusCible(Section s, Membre c) {
        if (c == null) return "Personnage introuvable.";
        if (!s.faction.equals(c.faction)) return "il n'est pas de ton village";
        if (!c.section.isEmpty()) return "déjà dans une section";
        if (invitations.containsKey(c.owner)) return "une invitation l'attend déjà";
        return null;
    }

    public String inviter(Player p, Player cible, boolean panneau) {
        if (cible == null || !cible.isOnline() || cible.equals(p)) return "Personne à inviter.";
        Membre m = membre(p);
        String r = refusInviter(m);
        if (r != null) return r;
        Section s = section(m.section);
        Membre c = membre(cible);
        String rc = refusCible(s, c);
        if (rc != null) return (c == null ? "" : c.nom + " : ") + rc + ".";
        double d = p.getWorld().equals(cible.getWorld()) ? p.getLocation().distance(cible.getLocation()) : Double.MAX_VALUE;
        ShinobiCharacter pc = perso(p);
        if (panneau) {
            if (pc == null || !pc.knows(c.perso)) return "Tu ne connais pas cette personne : présente-toi d'abord.";
            if (d > PORTEE_PANNEAU && !droit(m, Droits.INVITER_DISTANCE)) return "Trop loin : inviter à distance est réservé à certains grades.";
        } else if (d > PORTEE_FACE) return "Trop loin : approche-toi (3 blocs).";
        int secs = panneau ? SECS_PANNEAU : SECS_FACE;
        Invit inv = new Invit(s.id, p.getUniqueId(), m.perso, cible.getUniqueId(), System.currentTimeMillis(), secs, panneau);
        invitations.put(cible.getUniqueId(), inv);
        ShinobiCharacter cc = perso(cible);
        ShinobiCharacter.KnownName kn = cc == null ? null : cc.knownNameOf(m.perso);
        String de = kn != null ? kn.display() : "Quelqu'un";
        canal.invitation(cible, inv, de, s);
        journal(faction(s.faction), s, p, c, "INVITER", s.nom + " : " + m.nom + " invite " + c.nom + (panneau ? " (panneau)" : " (face à face)"));
        return null;
    }

    public String repondre(Player p, boolean oui) {
        Invit inv = invitations.remove(p.getUniqueId());
        if (inv == null || inv.expiree(System.currentTimeMillis())) return "Aucune invitation en attente.";
        Section s = section(inv.section());
        Membre c = membre(p);
        Membre de = membre(inv.dePerso());
        Player pde = Bukkit.getPlayer(inv.de());
        canal.invitationFin(p);
        if (s == null || c == null) return "La section n'existe plus.";
        if (!oui) {
            if (pde != null) Notifs.msg(pde, "info", null, "Invitation refusée", c.nom + " · " + s.nom);
            journal(faction(s.faction), s, p, c, "REFUSER", s.nom + " : " + c.nom + " refuse l'invitation");
            return null;
        }
        String rc = refusCible(s, c);
        if (rc != null) return rc + ".";
        if (effectif(s) >= s.places) return "La section est pleine.";
        c.section = s.id;
        c.grade = s.dernier().id;
        c.depuis = System.currentTimeMillis();
        synchroPret(c, s);
        Faction f = faction(s.faction);
        journal(f, s, p, c, "REJOINDRE", c.nom + " rejoint la section " + s.nom + (de != null ? " (invité" + (c.feminin() ? "e" : "") + " par " + de.nom + ")" : ""));
        Notifs.msg(p, "info", null, "Tu rejoins la section " + s.nom, f == null ? null : f.nom);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 0.5f, 1.4f);
        if (pde != null) Notifs.msg(pde, "info", null, c.nom + " rejoint la section " + s.nom, null);
        prevenir(s, null, de == null ? null : de.perso, "Nouveau membre", c.nom + " rejoint " + s.nom);
        changement(f);
        return null;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Invit> e : new ArrayList<>(invitations.entrySet())) {
            if (!e.getValue().expiree(now)) continue;
            invitations.remove(e.getKey());
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) canal.invitationFin(p);
            Player de = Bukkit.getPlayer(e.getValue().de());
            Membre c = p == null ? null : membre(p);
            if (de != null) Notifs.msg(de, "info", null, "Sans réponse", c == null ? "l'invitation a expiré" : c.nom + " n'a pas répondu");
        }
    }

    /* ================================================================ faction : taxe, seuils, charges, annonces */

    public String taux(CommandSender acteur, Membre m, Faction f, int n, boolean staff) {
        if (f == null) return "Faction introuvable.";
        if (!staff && !gestion(m, f, Droits.C_TAXE)) return "Seuls le chef de faction et ses chargés règlent la taxe.";
        int b = Regles.borner(n, plafond());
        if (b != n) return "Taux de 0 à " + plafond() + " %.";
        int avant = taux(f);
        if (avant == b && f.taux >= 0) return null;
        f.taux = b;
        journal(f, null, acteur, null, "TAXE", "Taxe des sections : " + avant + " % → " + b + " %");
        changement(f);
        return null;
    }

    public String seuil(CommandSender acteur, Membre m, Faction f, String droit, Rank r, boolean staff) {
        if (f == null || !f.village()) return "Seuls les villages ont des seuils.";
        if (!staff && !gestion(m, f, Droits.C_TAXE)) return "Seuls le chef de faction et ses chargés règlent les seuils.";
        if (!Droits.seuilConnu(droit) || r == null) return "Seuil inconnu.";
        Rank avant = f.seuils.getOrDefault(droit, Droits.defaut(droit));
        if (avant == r && f.seuils.containsKey(droit)) return null;
        f.seuils.put(droit, r);
        if (avant != r)
            journal(f, null, acteur, null, "SEUIL", "« " + libelleSeuil(droit) + " » dès " + r.displayName() + (avant == null ? "" : " (avant : " + avant.displayName() + ")"));
        changement(f);
        return null;
    }

    static String libelleSeuil(String id) {
        for (Object[] s : Droits.SEUILS) if (s[0].equals(id)) return (String) s[1];
        return id;
    }

    public String chargeCreer(CommandSender acteur, Membre m, Faction f, String nom, boolean staff) {
        if (f == null) return "Faction introuvable.";
        if (!staff && (m == null || !m.perso.equals(f.chef))) return "Seul le chef de faction crée les charges.";
        String n = nom == null ? "" : nom.trim();
        if (n.isEmpty() || n.length() > 24) return "Nom de 1 à 24 caractères.";
        String id = slug(n);
        if (f.charges.containsKey(id)) return "Cette charge existe déjà.";
        Charge k = new Charge();
        k.id = id;
        k.nom = n;
        f.charges.put(id, k);
        journal(f, null, acteur, null, "CHARGE", "Charge « " + n + " » créée");
        changement(f);
        return null;
    }

    public String chargeDroits(CommandSender acteur, Membre m, Faction f, String id, java.util.Set<String> droits, boolean publique, boolean staff) {
        if (f == null) return "Faction introuvable.";
        if (!staff && (m == null || !m.perso.equals(f.chef))) return "Seul le chef de faction règle les charges.";
        Charge k = f.charges.get(id);
        if (k == null) return "Charge introuvable.";
        k.droits.clear();
        for (String d : droits) if (Droits.droitCharge(d)) k.droits.add(d);
        k.publique = publique;
        journal(f, null, acteur, null, "CHARGE", "Charge « " + k.nom + " » : " + (k.droits.isEmpty() ? "aucun droit" : String.join(", ", k.droits)) + (publique ? ", publique" : ""));
        changement(f);
        return null;
    }

    public String chargeTitulaire(CommandSender acteur, Membre m, Faction f, String id, UUID perso, boolean ajouter, boolean staff) {
        if (f == null) return "Faction introuvable.";
        if (!staff && (m == null || !m.perso.equals(f.chef))) return "Seul le chef de faction nomme aux charges.";
        Charge k = f.charges.get(id);
        if (k == null) return "Charge introuvable.";
        Membre t = membre(perso);
        if (t == null || !f.tag.equals(t.faction)) return "Ce personnage n'est pas de " + f.nom + ".";
        if (ajouter) {
            Charge deja = chargeDe(f, perso);
            if (deja != null && deja != k) return t.nom + " a déjà la charge « " + deja.nom + " ».";
            if (!k.titulaires.add(perso)) return null;
            ligne(t, "info", "Charge du village", k.nom + " de " + f.nom);
        } else if (!k.titulaires.remove(perso)) return null;
        journal(f, null, acteur, t, "CHARGE", "Charge « " + k.nom + " » : " + t.nom + (ajouter ? " nommé" + (t.feminin() ? "e" : "") : " retiré" + (t.feminin() ? "e" : "")));
        changement(f);
        return null;
    }

    public String chargeSupprimer(CommandSender acteur, Membre m, Faction f, String id, boolean staff) {
        if (f == null) return "Faction introuvable.";
        if (!staff && (m == null || !m.perso.equals(f.chef))) return "Seul le chef de faction supprime les charges.";
        Charge k = f.charges.remove(id);
        if (k == null) return "Charge introuvable.";
        journal(f, null, acteur, null, "CHARGE", "Charge « " + k.nom + " » supprimée");
        changement(f);
        return null;
    }

    /**
     * Publie une annonce (sans la cloche : elle s'y branchera) : au village (seuil « cloche du village » ou charge
     * « annonces ») ou à sa section (droit « annoncer à la section »). Les membres en ligne reçoivent une ligne sobre.
     */
    public String annoncer(Player p, boolean section, String texte) {
        Membre m = membre(p);
        Faction f = m == null ? null : faction(m.faction);
        if (f == null) return "Tu n'as pas de faction.";
        String t = texte == null ? "" : texte.trim();
        if (t.isEmpty()) return "Écris l'annonce.";
        if (t.length() > 240) return "240 caractères au plus.";
        Section s = section ? section(m.section) : null;
        if (section) {
            if (s == null) return "Tu n'es dans aucune section.";
            if (!droit(m, Droits.CLOCHE_SECTION)) return "Ton grade de section ne permet pas d'annoncer.";
        } else if (!Regles.seuil(lecteur(m), f, "cloche_village") && !charge(f, m.perso, Droits.C_ANNONCES))
            return "Annoncer au village : dès " + f.seuils.getOrDefault("cloche_village", Rank.JONIN).displayName() + ".";
        Annonce a = new Annonce();
        a.le = System.currentTimeMillis();
        a.auteur = m.perso;
        a.titre = titre(m, f, s);
        a.section = s == null ? "" : s.id;
        a.texte = t;
        f.annonces.add(0, a);
        while (f.annonces.size() > 30) f.annonces.remove(f.annonces.size() - 1);
        journal(f, s, p, m, "ANNONCE", (s == null ? "Annonce au village" : "Annonce à " + s.nom) + " : « " + t + " »");
        for (Membre x : s == null ? membres(f) : membres(s)) {
            Player q = enLigne(x);
            if (q != null) Notifs.msg(q, "info", null, s == null ? "Annonce du village" : "Annonce · " + s.nom, t.length() > 70 ? t.substring(0, 69) + "…" : t);
        }
        changement(f);
        return null;
    }

    /**
     * La cloche du village (communication/CommService, CLOCHE_ET_MISSIVES.md §2.4) : l'annonce sonnée rejoint l'onglet
     * Annonces du volet J ({@code a.section} vide = tout le village).
     */
    public void noterAnnonce(Faction f, Annonce a) {
        if (f == null || a == null) return;
        f.annonces.add(0, a);
        while (f.annonces.size() > 30) f.annonces.remove(f.annonces.size() - 1);
        changement(f);
    }

    /** Titre RP d'un auteur : « Hokage », « Commissaire · Police », sinon son grade du village. */
    String titre(Membre m, Faction f, Section s) {
        if (f != null && m.perso.equals(f.chef)) return f.village() ? kage(f) : "Chef de " + f.nom;
        if (s != null) {
            GradeSection g = s.grade(m.grade);
            return (g == null ? "" : g.nom + " · ") + s.nom;
        }
        Charge k = f == null ? null : chargeDe(f, m.perso);
        return k != null ? k.nom : m.rang.displayName();
    }

    public static String kage(Faction f) {
        return switch (f.embleme) {
            case "konoha" -> "Hokage";
            case "suna" -> "Kazekage";
            case "kiri" -> "Mizukage";
            case "kumo" -> "Raikage";
            case "iwa" -> "Tsuchikage";
            case "ame" -> "Chef d'Amegakure";
            default -> "Kage";
        };
    }

    /* ================================================================ caisse et taxe */

    private boolean auGuichet(Player p) {
        var mo = plugin.monnaie();
        return mo != null && mo.guichets().proche(p.getLocation(), 6) != null;
    }

    private static String semaine() {
        LocalDate d = LocalDate.now();
        return d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + String.format("%02d", d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    public long taxeSemaine(Faction f) { return f.taxeSemaine.getOrDefault(semaine(), 0L); }

    private void verserTresor(Faction f, long n, String motif) {
        var mo = plugin.monnaie();
        if (mo == null || n <= 0) return;
        mo.banque().tresorVarier(Bukkit.getConsoleSender(), f.village.isEmpty() ? f.nom : f.village, "ajouter", n, motif);
    }

    /**
     * Une entrée dans la caisse : la taxe du village est prélevée (sauf dotation) et versée au trésor. Renvoie le net.
     */
    public long crediter(Section s, long montant, String libelle, boolean dotation) {
        Faction f = faction(s.faction);
        long[] nt = Regles.taxer(montant, f == null ? 0 : taux(f), dotation);
        s.caisse += nt[0] + nt[1];
        FactionStore.ajouter(s.mouvements, new Ligne(System.currentTimeMillis(), libelle, nt[0] + nt[1]));
        if (nt[1] > 0 && f != null) {
            s.caisse -= nt[1];
            FactionStore.ajouter(s.mouvements, new Ligne(System.currentTimeMillis(), "Taxe du village", -nt[1]));
            f.taxeSemaine.merge(semaine(), nt[1], Long::sum);
            while (f.taxeSemaine.size() > 8) f.taxeSemaine.remove(f.taxeSemaine.keySet().iterator().next());
            verserTresor(f, nt[1], "taxe de « " + s.nom + " » (" + taux(f) + " %)");
        }
        store.marquer();
        return nt[0];
    }

    /** Un membre verse à la caisse depuis sa bourse, au guichet (entrée taxée). */
    public String verser(Player p, long n, String motif) {
        Membre m = membre(p);
        Section s = m == null ? null : section(m.section);
        if (s == null) return "Tu n'es dans aucune section.";
        if (n <= 0) return "Montant invalide.";
        if (!auGuichet(p)) return "Cela se fait au guichet du village.";
        var mo = plugin.monnaie();
        String mt = motif == null || motif.isBlank() ? "don" : motif.trim();
        if (!mo.bourses().debiter(p, n, "CAISSE_SECTION", "versé à « " + s.nom + " » : " + mt, p)) return "Pas assez dans ta bourse.";
        long net = crediter(s, n, mt + " · " + m.nom, false);
        journal(faction(s.faction), s, p, m, "CAISSE", s.nom + " : +" + n + " ryo de " + m.nom + " (net " + net + ") · « " + mt + " »");
        changement(faction(s.faction));
        return null;
    }

    /** Prime à un membre : de la caisse vers sa bourse, au guichet, droit « dépenser ». */
    public String prime(Player p, Membre cible, long n, String motif) {
        Membre m = membre(p);
        Section s = m == null ? null : section(m.section);
        if (s == null) return "Tu n'es dans aucune section.";
        if (!droit(m, Droits.DEPENSER)) return "Ton grade ne permet pas de dépenser la caisse.";
        if (cible == null || !s.id.equals(cible.section)) return "Ce n'est pas un membre de ta section.";
        if (n <= 0 || n > s.caisse) return "La caisse contient " + s.caisse + " ryo.";
        if (motif == null || motif.isBlank()) return "Donne un motif.";
        if (!auGuichet(p)) return "Cela se fait au guichet du village.";
        Player q = enLigne(cible);
        if (q == null) return cible.nom + " doit être en ligne.";
        if (!plugin.monnaie().bourses().crediter(q, n, "PRIME_SECTION", "prime de « " + s.nom + " » : " + motif.trim(), p))
            return "Sa bourse ne peut pas recevoir la somme.";
        s.caisse -= n;
        FactionStore.ajouter(s.mouvements, new Ligne(System.currentTimeMillis(), "Prime à " + cible.nom, -n));
        journal(faction(s.faction), s, p, cible, "CAISSE", s.nom + " : prime de " + n + " ryo à " + cible.nom + " · « " + motif.trim() + " »");
        ligne(cible, "info", "Prime de la section", "+" + n + " ryo · " + s.nom);
        changement(faction(s.faction));
        return null;
    }

    /** Dotation depuis le trésor du village (chef de faction, charge « doter »), au guichet ; jamais taxée. */
    public String doter(Player p, Section s, long n, String motif) {
        Membre m = membre(p);
        Faction f = s == null ? null : faction(s.faction);
        if (f == null) return "Section introuvable.";
        if (!gestion(m, f, Droits.C_DOTER)) return "Seuls le chef de faction et ses chargés dotent une section.";
        if (n <= 0) return "Montant invalide.";
        if (!auGuichet(p)) return "Cela se fait au guichet du village.";
        var mo = plugin.monnaie();
        if (mo == null) return "La banque n'est pas active.";
        String v = f.village.isEmpty() ? f.nom : f.village;
        if (mo.banque().tresor(v) < n) return "Le trésor ne contient que " + mo.banque().tresor(v) + " ryo.";
        mo.banque().tresorVarier(p, v, "retirer", n, "dotation de « " + s.nom + " »" + (motif == null || motif.isBlank() ? "" : " : " + motif.trim()));
        crediter(s, n, "Dotation du village", true);
        journal(f, s, p, null, "DOTATION", s.nom + " : dotation de " + n + " ryo depuis le trésor");
        changement(f);
        return null;
    }

    /** Correction staff d'une caisse (ajouter / retirer / fixer), motivée. */
    public String caisseStaff(CommandSender acteur, Section s, String op, long n, String motif) {
        if (s == null) return "Section introuvable.";
        if (motif == null || motif.isBlank()) return "Donne un motif.";
        long avant = s.caisse;
        s.caisse = switch (op) {
            case "ajouter" -> avant + Math.max(0, n);
            case "retirer" -> Math.max(0, avant - Math.max(0, n));
            case "fixer" -> Math.max(0, n);
            default -> avant;
        };
        FactionStore.ajouter(s.mouvements, new Ligne(System.currentTimeMillis(), "Correction staff", s.caisse - avant));
        journal(faction(s.faction), s, acteur, null, "CAISSE", s.nom + " : caisse " + avant + " → " + s.caisse + " (staff) · « " + motif.trim() + " »");
        changement(faction(s.faction));
        return null;
    }

    /* ================================================================ staff : factions */

    public String creerFaction(CommandSender acteur, String tag, String nom, boolean organisation, String embleme, UUID chef, String motif) {
        String t = tag == null ? "" : tag.trim().toUpperCase(Locale.ROOT);
        if (!t.matches("[A-Z]{3}")) return "Tag : 3 lettres.";
        if (store.factions.containsKey(t)) return "Le tag " + t + " est pris.";
        String n = nom == null ? "" : nom.trim();
        if (n.isEmpty() || n.length() > 32) return "Nom de 1 à 32 caractères.";
        for (Faction f : store.factions.values()) if (cle(f.nom).equals(cle(n))) return "Ce nom est pris.";
        if (motif == null || motif.isBlank()) return "Donne un motif.";
        Faction f = new Faction();
        f.tag = t;
        f.nom = n;
        f.type = organisation ? FactionData.Type.ORGANISATION : FactionData.Type.VILLAGE;
        f.embleme = embleme == null ? "" : embleme;
        f.village = organisation ? "" : n;
        f.cree = System.currentTimeMillis();
        store.factions.put(t, f);
        if (organisation) {
            // la section racine de l'organisation porte ses grades (pas de grade du village)
            Section s = new Section();
            s.id = t.toLowerCase(Locale.ROOT);
            s.faction = t;
            s.nom = n;
            s.insigne = Droits.insigne(f.embleme);
            s.places = 12;
            s.cree = f.cree;
            for (String g : new String[]{"Chef", "Lieutenant", "Membre"}) {
                GradeSection gs = new GradeSection();
                gs.id = slug(g);
                gs.nom = g;
                gs.droits.addAll(g.equals("Chef") ? Droits.tousSection() : g.equals("Lieutenant")
                        ? java.util.Set.of(Droits.INVITER, Droits.VOIR_CAISSE, Droits.JOURNAL) : java.util.Set.of());
                s.grades.add(gs);
            }
            store.sections.put(s.id, s);
        }
        journal(f, null, acteur, null, "FACTION", "Faction « " + n + " » [" + t + "] créée (" + (organisation ? "organisation" : "village")
                + ") · « " + motif.trim() + " »");
        if (chef != null) nommerChefFaction(acteur, f, chef, motif);
        changement(f);
        return null;
    }

    public String nommerChefFaction(CommandSender acteur, Faction f, UUID chef, String motif) {
        if (f == null) return "Faction introuvable.";
        if (motif == null || motif.isBlank()) return "Donne un motif.";
        Membre m = membre(chef);
        if (chef != null && m == null) return "Personnage introuvable.";
        if (m != null && !f.village()) {
            // organisation : le chef y entre (s'il n'a pas de village) et prend la section racine
            if (factionDuVillage(m.village) != null) return m.nom + " a un village : une organisation accueille les sans-village.";
            m.faction = f.tag;
            Section racine = section(f.tag.toLowerCase(Locale.ROOT));
            if (racine != null) { m.section = racine.id; m.grade = racine.grades.get(0).id; racine.chef = m.perso; }
        } else if (m != null && !f.tag.equals(m.faction)) return m.nom + " n'est pas de " + f.nom + ".";
        UUID avant = f.chef;
        f.chef = chef;
        Membre ma = membre(avant);
        journal(f, null, acteur, m, "CHEF_FACTION", (m == null ? "Chef retiré" : m.nom + " devient chef de " + f.nom)
                + (ma != null ? " (avant : " + ma.nom + ")" : "") + " · « " + motif.trim() + " »");
        if (m != null) ligne(m, "info", "Chef de faction", f.nom);
        changement(f);
        return null;
    }

    public String renommerFaction(CommandSender acteur, Faction f, String nom, String embleme, String motif) {
        if (f == null) return "Faction introuvable.";
        if (motif == null || motif.isBlank()) return "Donne un motif.";
        String n = nom == null ? "" : nom.trim();
        if (n.isEmpty() || n.length() > 32) return "Nom de 1 à 32 caractères.";
        String avant = f.nom;
        f.nom = n;
        if (embleme != null && !embleme.isBlank() && !f.village()) f.embleme = embleme;
        journal(f, null, acteur, null, "FACTION", "« " + avant + " » → « " + n + " » · « " + motif.trim() + " »");
        changement(f);
        return null;
    }

    /** Dissoudre une organisation (les villages existent d'office et ne se dissolvent pas). */
    public String dissoudreFaction(CommandSender acteur, Faction f, String motif) {
        if (f == null) return "Faction introuvable.";
        if (f.village()) return "Un village ne se dissout pas.";
        if (motif == null || motif.isBlank()) return "Donne un motif.";
        for (Section s : sections(f)) store.sections.remove(s.id);
        for (Membre m : store.membres.values()) if (f.tag.equals(m.faction)) { m.faction = ""; m.section = ""; m.grade = ""; synchroPret(m, null); }
        store.factions.remove(f.tag);
        journal(null, null, acteur, null, "FACTION", "Faction « " + f.nom + " » [" + f.tag + "] dissoute · « " + motif.trim() + " »");
        store.marquer();
        store.marquerMembres();
        if (canal != null) canal.rafraichirStaff();
        return null;
    }

    /** Invitation dans une organisation (chef ou grade « inviter » de sa section racine) : passe par {@link #inviter}. */
    public boolean organisation(Faction f) { return f != null && !f.village(); }

    /* ================================================================ outils */

    public static boolean staff(Player p, int grade) { return StaffGrades.of(p) >= grade; }

    /** Personnage par « #N° », nom exact ou UUID (registre, hors ligne compris). */
    public Membre resoudre(String ref) {
        if (ref == null || ref.isBlank()) return null;
        String r = ref.trim();
        try { return membre(UUID.fromString(r)); } catch (IllegalArgumentException ignored) { }
        int n = PlayerIdRegistry.parse(r);
        if (n >= 0) for (Membre m : store.membres.values()) if (m.pid == n) return m;
        String k = cle(r);
        for (Membre m : store.membres.values()) if (cle(m.nom).equals(k)) return m;
        return null;
    }
}
