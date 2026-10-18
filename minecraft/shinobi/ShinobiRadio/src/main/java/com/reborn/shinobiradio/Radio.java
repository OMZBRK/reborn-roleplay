package com.reborn.shinobiradio;

import com.reborn.shinobicore.api.CharacterService;
import com.reborn.shinobicore.api.InventoryService;
import com.reborn.shinobicore.api.JournalService;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.api.KoService;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.etat.Verrous;
import com.reborn.shinobicore.inventory.Notifs;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le cœur de la radio (docs/gameplay/RADIO.md) : règles des escouades et des oreillettes, états du joueur, instantané
 * lu par la voix (Plasmo Voice, autre thread), messages {@code reborn:radio}, journal. Tout tourne sur le thread
 * principal, sauf {@link #peutEmettre}, {@link #emetteur}, {@link #destinataires} et {@link #debutEmission} /
 * {@link #finEmission} qui lisent l'instantané immuable ou repostent sur le thread principal.
 *
 * <p>Règles d'états (docs/technique/ETATS_JOUEUR.md) : <b>inconscient</b> = ni émettre ni entendre ; <b>à terre</b>
 * (et porté) = radio hachée dans les deux sens ; <b>mort RP</b> = rien ; <b>mode staff</b> = aucune mécanique RP
 * (le staff n'émet jamais ; il n'entend que par l'écoute journalisée).
 */
final class Radio {

    /** Qui peut émettre en ce moment, sur quelle escouade, sous quelle identité (tête et nom du propriétaire). */
    record Emetteur(UUID compte, UUID escouade, boolean hache, UUID tete, String court, String proprioNom, boolean volee) { }

    /** Instantané immuable, reconstruit toutes les 5 ticks : lu par les threads de Plasmo Voice. */
    record Instantane(Map<UUID, Emetteur> emetteurs, Map<UUID, List<UUID>> auditeurs, Set<UUID> haches) {
        static final Instantane VIDE = new Instantane(Map.of(), Map.of(), Set.of());
    }

    /** Une prise de parole en cours. */
    static final class Emission {
        final UUID compte, escouade;
        final long debut = System.currentTimeMillis();
        final boolean hache, volee;
        volatile UUID src;
        volatile long dernier = System.currentTimeMillis();

        Emission(UUID compte, UUID escouade, boolean hache, boolean volee) {
            this.compte = compte;
            this.escouade = escouade;
            this.hache = hache;
            this.volee = volee;
        }
    }

    /** Invitation en attente (escouade null = création d'une escouade avec l'inviteur). */
    record Invit(UUID deCompte, UUID dePerso, String deNom, UUID escouade, String nomEscouade, long fin) { }

    /** Écoute staff en cours. */
    record Ecoute(UUID escouade, String nomEscouade, long fin, String motif, UUID cible) { }

    /** Vue d'un joueur en ligne, calculée à chaque rafraîchissement. */
    record Vu(Player p, ShinobiCharacter c, ItemStack item, Oreillettes.Info info, Escouade canal, Escouade membreDe,
              boolean aTerre, boolean inconscient, boolean mort, boolean horsJeu, boolean porte) {
        boolean ko() { return aTerre || inconscient; }
    }

    private final ShinobiRadio plugin;
    final RadioConfig cfg;
    final Escouades escouades;
    final Oreillettes oreillettes;
    final Canal canal;
    private final CharacterService chars;
    private final KoService ko;
    private final InventoryService inv;
    private final JournalService journal;

    private volatile Instantane inst = Instantane.VIDE;
    private final Map<UUID, Vu> vus = new HashMap<>();
    private final Map<UUID, Vu> vusParPerso = new HashMap<>();
    private final Map<UUID, Invit> invits = new HashMap<>();
    private final Map<UUID, Ecoute> ecoutes = new HashMap<>();
    private final Map<UUID, Long> coupures = new HashMap<>();
    private final Map<UUID, String> derniersEtats = new HashMap<>();
    private final ConcurrentHashMap<UUID, Emission> emissions = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> notifies = new HashMap<>();
    private long tick;

    Radio(ShinobiRadio plugin, RadioConfig cfg, Escouades escouades, Oreillettes oreillettes, Canal canal,
          CharacterService chars, KoService ko, InventoryService inv, JournalService journal) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.escouades = escouades;
        this.oreillettes = oreillettes;
        this.canal = canal;
        this.chars = chars;
        this.ko = ko;
        this.inv = inv;
        this.journal = journal;
    }

    /* ================================================================== lecture (tous threads) */

    boolean peutEmettre(UUID compte) { return inst.emetteurs().containsKey(compte); }

    Emetteur emetteur(UUID compte) { return inst.emetteurs().get(compte); }

    /** Comptes qui entendent l'escouade (membres équipés, staff à l'écoute), l'émetteur compris s'il en fait partie. */
    List<UUID> destinataires(UUID escouade) {
        List<UUID> l = inst.auditeurs().get(escouade);
        return l == null ? List.of() : l;
    }

    /** Appelé par la voix (thread Plasmo Voice) à la première trame d'une prise de parole. */
    void debutEmission(UUID compte, UUID src) {
        Emetteur e = emetteur(compte);
        if (e == null) return;
        Emission em = emissions.computeIfAbsent(compte, k -> new Emission(compte, e.escouade(), e.hache(), e.volee()));
        em.src = src;
        em.dernier = System.currentTimeMillis();
        Bukkit.getScheduler().runTask(plugin, this::diffuserParole);
    }

    boolean enEmission(UUID compte) { return emissions.containsKey(compte); }

    /** Chaque trame relayée (thread Plasmo Voice) : garde l'émission vivante. */
    void trame(UUID compte) {
        Emission em = emissions.get(compte);
        if (em != null) em.dernier = System.currentTimeMillis();
    }

    /** Fin de la prise de parole (thread Plasmo Voice ou délai dépassé). */
    void finEmission(UUID compte) {
        Emission em = emissions.remove(compte);
        if (em == null) return;
        if (Bukkit.isPrimaryThread()) clore(em);
        else Bukkit.getScheduler().runTask(plugin, () -> clore(em));
    }

    /* ================================================================== boucle (thread principal) */

    void tick() {
        tick++;
        long now = System.currentTimeMillis();
        expirer(now);
        rafraichir(now);
        diffuserParole();
        for (Emission em : new ArrayList<>(emissions.values())) {
            if (now - em.dernier > 1500) finEmission(em.compte);
        }
        if (tick % 1200 == 0) escouades.marquer();                     // ~5 min : dernières connexions
        if (tick % 80 == 0) escouades.sauverSiBesoin();                // ~20 s
        if (tick % 14_400 == 0) dissolutionsAutomatiques(now);        // ~1 h
    }

    /** Recalcule l'instantané, les états des joueurs et pousse ce qui a changé. */
    void rafraichir(long now) {
        vus.clear();
        vusParPerso.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Vu v = vu(p);
            if (v == null) continue;
            vus.put(p.getUniqueId(), v);
            vusParPerso.put(v.c().id(), v);
            if (v.membreDe() != null) v.membreDe().derniereConnexion = now;
        }
        Map<UUID, Emetteur> em = new HashMap<>();
        Map<UUID, List<UUID>> aud = new HashMap<>();
        Set<UUID> hach = new HashSet<>();
        for (Vu v : vus.values()) {
            if (!entend(v)) continue;
            UUID compte = v.p().getUniqueId();
            aud.computeIfAbsent(v.canal().id, k -> new ArrayList<>()).add(compte);
            boolean h = hache(v);
            if (h) hach.add(compte);
            if (!coupe(compte, now)) {
                Oreillettes.Info i = v.info();
                boolean volee = i.proprio() != null && !i.proprio().equals(v.c().id());
                UUID tete = volee && i.proprioCompte() != null ? i.proprioCompte() : compte;
                String nom = volee && !i.proprioNom().isBlank() ? i.proprioNom() : v.c().name();
                em.put(compte, new Emetteur(compte, v.canal().id, h, tete, court(nom), i.proprioNom(), volee));
            }
        }
        for (Map.Entry<UUID, Ecoute> e : ecoutes.entrySet()) {
            List<UUID> l = aud.computeIfAbsent(e.getValue().escouade(), k -> new ArrayList<>());
            if (!l.contains(e.getKey()) && Bukkit.getPlayer(e.getKey()) != null) l.add(e.getKey());
        }
        Map<UUID, List<UUID>> figes = new HashMap<>();
        aud.forEach((k, l) -> figes.put(k, List.copyOf(l)));
        inst = new Instantane(Map.copyOf(em), Map.copyOf(figes), Set.copyOf(hach));

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!Canal.ecoute(p)) continue;
            String etat = etat(p, now);
            if (!etat.equals(derniersEtats.get(p.getUniqueId()))) {
                derniersEtats.put(p.getUniqueId(), etat);
                canal.envoyer(p, etat);
            }
        }
    }

    /** Rafraîchit tout de suite (après une action). */
    void maintenant() { rafraichir(System.currentTimeMillis()); }

    private Vu vu(Player p) {
        ShinobiCharacter c = chars.getActive(p.getUniqueId());
        if (c == null) return null;
        UUID id = p.getUniqueId();
        ItemStack item = inv.cosmetique(p, cfg.emplacement);
        Oreillettes.Info info = oreillettes.lire(item);
        Escouade canalE = null;
        if (info != null && info.liee() && !info.coupee()) {
            Escouade e = escouades.get(info.escouade());
            if (e != null && !e.exclues.contains(info.numero())) canalE = e;
        }
        boolean koB = ko != null && ko.isKo(id);
        boolean aTerre = koB && ko.isDowned(id);
        boolean horsJeu = ko != null && ko.isOutOfPlay(id);
        // Étape 9 d'ETATS_JOUEUR : par l'API des états (plus de lecture des classes internes de ShinobiCore) ; repli.
        var etats = com.reborn.shinobicore.api.EtatService.get();
        var e = etats != null ? etats.etat(id) : null;
        boolean mort = e != null ? e.vital() == com.reborn.shinobicore.api.etat.Vital.MORT : Verrous.mort(id);
        boolean porte = e != null ? e.controle() == com.reborn.shinobicore.api.etat.Controle.PORTE
                && e.lien() != null && "PORTE".equals(e.lien().type()) : Verrous.porte(id);   // porté, pas tenu à la gorge
        return new Vu(p, c, item, info, canalE, escouades.dePerso(c.id()), aTerre, koB && !aTerre, mort,
                horsJeu, porte);
    }

    private boolean entend(Vu v) {
        return v.canal() != null && !v.inconscient() && !v.mort() && !v.horsJeu();
    }

    private boolean hache(Vu v) {
        return v.aTerre() || (cfg.porteHache && v.porte());
    }

    private boolean coupe(UUID compte, long now) {
        Long fin = coupures.get(compte);
        return fin != null && fin > now;
    }

    /* ------------------------------------------------------------------ état poussé au joueur */

    private String etat(Player p, long now) {
        Vu v = vus.get(p.getUniqueId());
        StringBuilder b = new StringBuilder(256).append("{\"t\":\"etat\"");
        if (v == null) return b.append(",\"ore\":\"aucune\"}").toString();
        Oreillettes.Info i = v.info();
        String ore;
        if (i == null) ore = "aucune";
        else if (i.coupee()) ore = "coupee";
        else if (!i.liee() || escouades.get(i.escouade()) == null) ore = "neuve";
        else if (v.canal() == null) ore = "exclue";
        else ore = "ok";
        b.append(',').append(Canal.kv("ore", ore));
        if (i != null && i.numero() >= 0) b.append(",\"num\":").append(i.numero());
        if (i != null && i.proprio() != null && !i.proprio().equals(v.c().id())) {
            b.append(",\"volee\":true,").append(Canal.kv("proprio", i.proprioNom()));
            if (i.proprioCompte() != null) b.append(',').append(Canal.kv("proprioId", i.proprioCompte().toString()));
        }
        if (v.canal() != null && v.membreDe() != v.canal()) b.append(',').append(Canal.kv("canal", v.canal().nom));
        b.append(",\"h\":").append(hache(v)).append(",\"muet\":").append(v.inconscient() || v.mort())
                .append(",\"staff\":").append(v.horsJeu()).append(",\"coupe\":").append(coupe(p.getUniqueId(), now));
        Escouade e = v.membreDe();
        if (e != null) {
            b.append(",\"esc\":{").append(Canal.kv("id", e.id.toString())).append(',').append(Canal.kv("nom", e.nom))
                    .append(",\"n\":").append(e.membres.size()).append(",\"max\":").append(cfg.tailleMax)
                    .append(",\"createur\":").append(e.estCreateur(v.c().id())).append(",\"membres\":[");
            boolean premier = true;
            for (Escouade.Membre m : e.ordonnes()) {
                if (!premier) b.append(',');
                premier = false;
                Vu mv = vusParPerso.get(m.perso);
                String et = mv == null ? "hors_ligne" : mv.mort() ? "mort" : mv.inconscient() ? "inconscient"
                        : mv.aTerre() ? "a_terre" : "en_ligne";
                String mo;
                if (mv == null) mo = m.prise ? "prise" : "";
                else if (mv.canal() == e) mo = "ok";
                else if (mv.info() != null && mv.info().coupee()) mo = "coupee";
                else mo = m.prise ? "prise" : "sans";
                b.append('{').append(Canal.kv("id", m.compte == null ? "" : m.compte.toString())).append(',')
                        .append(Canal.kv("perso", m.perso.toString())).append(',').append(Canal.kv("nom", m.nom)).append(',')
                        .append(Canal.kv("court", court(m.nom))).append(',').append(Canal.kv("etat", et)).append(',')
                        .append(Canal.kv("ore", mo)).append(",\"createur\":").append(e.estCreateur(m.perso))
                        .append(",\"moi\":").append(m.perso.equals(v.c().id())).append('}');
            }
            b.append("]}");
        }
        return b.append('}').toString();
    }

    /* ------------------------------------------------------------------ « parle » / « tait » */

    private void diffuserParole() {
        Instantane in = inst;
        for (Emission em : emissions.values()) {
            Emetteur e = in.emetteurs().get(em.compte);
            Set<UUID> deja = notifies.computeIfAbsent(em.compte, k -> new LinkedHashSet<>());
            Set<UUID> dest = new LinkedHashSet<>(in.auditeurs().getOrDefault(em.escouade, List.of()));
            dest.remove(em.compte);
            for (UUID d : dest) {
                if (deja.contains(d)) continue;
                Player p = Bukkit.getPlayer(d);
                if (p == null) continue;
                boolean h = em.hache || in.haches().contains(d);
                String tete = e != null ? e.tete().toString() : em.compte.toString();
                String nom = e != null ? e.court() : "?";
                canal.envoyer(p, "{\"t\":\"parle\"," + Canal.kv("src", em.src == null ? "" : em.src.toString()) + ','
                        + Canal.kv("id", tete) + ',' + Canal.kv("nom", nom) + ",\"h\":" + h + '}');
                deja.add(d);
            }
            for (Iterator<UUID> it = deja.iterator(); it.hasNext(); ) {
                UUID d = it.next();
                if (dest.contains(d)) continue;
                it.remove();
                tait(d, em);
            }
        }
    }

    private void tait(UUID d, Emission em) {
        Player p = Bukkit.getPlayer(d);
        if (p != null) canal.envoyer(p, "{\"t\":\"tait\"," + Canal.kv("src", em.src == null ? "" : em.src.toString())
                + ',' + Canal.kv("compte", em.compte.toString()) + '}');
    }

    private void clore(Emission em) {
        Set<UUID> deja = notifies.remove(em.compte);
        if (deja != null) for (UUID d : deja) tait(d, em);
        long ms = System.currentTimeMillis() - em.debut;
        if (cfg.journalEmissions && ms >= cfg.emissionMinSecondes * 1000L) {
            Escouade e = escouades.get(em.escouade);
            Player p = Bukkit.getPlayer(em.compte);
            JournalService.Entree j = journal.entree(Categorie.RADIO, "EMISSION");
            if (p != null) j.acteur(p).lieu(p.getLocation()); else j.acteur(em.compte);
            j.cibleTexte("Escouade " + (e == null ? "?" : e.nom))
                    .detail(Math.max(1, Math.round(ms / 1000f)) + " s" + (em.hache ? " · haché" : "")
                            + (em.volee ? " · oreillette d'autrui" : "")).ecrire();
        }
    }

    /* ------------------------------------------------------------------ expirations */

    private void expirer(long now) {
        for (Iterator<Map.Entry<UUID, Invit>> it = invits.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Invit> e = it.next();
            if (e.getValue().fin() > now) continue;
            it.remove();
            Player b = Bukkit.getPlayer(e.getKey());
            Player a = Bukkit.getPlayer(e.getValue().deCompte());
            if (b != null) canal.envoyer(b, "{\"t\":\"invit_fin\"}");
            if (a != null) dire(a, "info", "Pas de réponse", b == null ? "" : "de " + nomDe(b) + ".");
            journal.entree(Categorie.RADIO, "INVITATION_EXPIREE").acteur(e.getValue().deCompte()).cible(e.getKey())
                    .detail(e.getValue().nomEscouade() == null ? "création" : "Escouade " + e.getValue().nomEscouade()).ecrire();
        }
        for (Iterator<Map.Entry<UUID, Ecoute>> it = ecoutes.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Ecoute> e = it.next();
            boolean finie = e.getValue().fin() <= now || escouades.get(e.getValue().escouade()) == null;
            if (!finie) continue;
            it.remove();
            Player s = Bukkit.getPlayer(e.getKey());
            if (s != null) {
                canal.envoyer(s, "{\"t\":\"ecoute_fin\"}");
                s.sendMessage(ChatColor.GRAY + "[Radio] Fin de l'écoute de l'escouade " + e.getValue().nomEscouade() + ".");
            }
            journal.entree(Categorie.STAFF, "RADIO_ECOUTE_FIN").acteur(e.getKey()).cible(e.getValue().cible())
                    .detail("Escouade " + e.getValue().nomEscouade() + " · fin automatique").ecrire();
        }
        coupures.values().removeIf(fin -> fin <= now);
    }

    private void dissolutionsAutomatiques(long now) {
        for (Escouade e : escouades.abandonnees(cfg.dissolutionJours, now)) {
            escouades.dissoudre(e);
            journal.entree(Categorie.RADIO, "DISSOLUTION").acteur(Bukkit.getConsoleSender()).cibleTexte("Escouade " + e.nom)
                    .detail("automatique · personne connecté depuis " + cfg.dissolutionJours + " jours").ecrire();
        }
    }

    void demarrage() {
        dissolutionsAutomatiques(System.currentTimeMillis());
    }

    /* ================================================================== connexion */

    void quitte(Player p) {
        UUID id = p.getUniqueId();
        finEmission(id);
        derniersEtats.remove(id);
        Invit i = invits.remove(id);
        if (i != null) {
            Player a = Bukkit.getPlayer(i.deCompte());
            if (a != null) dire(a, "info", "Invitation annulée", nomDe(p) + " s'est déconnecté.");
        }
        invits.entrySet().removeIf(e -> {
            if (!e.getValue().deCompte().equals(id)) return false;
            Player b = Bukkit.getPlayer(e.getKey());
            if (b != null) canal.envoyer(b, "{\"t\":\"invit_fin\"}");
            return true;
        });
        Ecoute ec = ecoutes.remove(id);
        if (ec != null) journal.entree(Categorie.STAFF, "RADIO_ECOUTE_FIN").acteur(id).cible(ec.cible())
                .detail("Escouade " + ec.nomEscouade() + " · déconnexion").ecrire();
        for (Set<UUID> s : notifies.values()) s.remove(id);
    }

    void change(Player p) {
        derniersEtats.remove(p.getUniqueId());
        finEmission(p.getUniqueId());
    }

    /* ================================================================== actions des joueurs */

    /** Raison qui empêche {@code v} d'agir sur la radio (menu R, écran N), ou null. */
    private String refusActeur(Vu v) {
        if (v == null) return "Il te faut un personnage.";
        if (v.horsJeu()) return "Mode staff : aucune mécanique RP.";
        if (v.mort()) return "Tu es mort.";
        if (v.ko()) return "Tu es à terre.";
        return null;
    }

    private Vu vuFrais(Player p) {
        Vu v = vu(p);
        if (v != null) {
            vus.put(p.getUniqueId(), v);
            vusParPerso.put(v.c().id(), v);
        }
        return v;
    }

    private boolean proche(Player a, Player b) {
        Location la = a.getLocation(), lb = b.getLocation();
        return la.getWorld() != null && la.getWorld().equals(lb.getWorld())
                && la.distanceSquared(lb) <= (cfg.distance + 0.75) * (cfg.distance + 0.75);
    }

    /** Menu R : « Créer une escouade avec lui ». */
    void creer(Player a, Player b) {
        Vu va = vuFrais(a);
        String r = refusActeur(va);
        if (r != null) { refus(a, r); return; }
        if (va.info() == null) { refus(a, "Il te faut une oreillette (emplacement Boucle d'oreille)."); return; }
        if (va.info().coupee()) { refus(a, "Ton oreillette est coupée."); return; }
        if (va.membreDe() != null) { refus(a, "Tu as déjà une escouade : « " + va.membreDe().nom + " »."); return; }
        if (!inviter(a, va, b, null)) return;
    }

    /** Menu R : « Ajouter à « … » ». */
    void ajouter(Player a, Player b) {
        Vu va = vuFrais(a);
        String r = refusActeur(va);
        if (r != null) { refus(a, r); return; }
        Escouade e = va.membreDe();
        if (e == null) { refus(a, "Tu n'as pas d'escouade."); return; }
        if (e.membres.size() >= cfg.tailleMax) { refus(a, "L'escouade est complète (" + cfg.tailleMax + ")."); return; }
        inviter(a, va, b, e);
    }

    private boolean inviter(Player a, Vu va, Player b, Escouade e) {
        if (b == null || b.equals(a)) { refus(a, "Personne ici."); return false; }
        Vu vb = vuFrais(b);
        if (vb == null || vb.horsJeu()) { refus(a, "Personne de ce nom ici."); return false; }
        if (!proche(a, b)) { refus(a, "Approche-toi (" + (int) cfg.distance + " blocs, face à face)."); return false; }
        if (vb.mort() || vb.ko()) { refus(a, nomDe(b) + " ne peut pas répondre."); return false; }
        if (vb.info() == null) { refus(a, nomDe(b) + " ne porte pas d'oreillette."); return false; }
        if (vb.info().coupee()) { refus(a, "L'oreillette de " + nomDe(b) + " est coupée."); return false; }
        if (vb.membreDe() != null && vb.membreDe() != e) {
            refus(a, nomDe(b) + " est déjà dans une autre escouade.");
            return false;
        }
        if (e != null && vb.membreDe() == e && vb.canal() == e) { refus(a, nomDe(b) + " est déjà dans l'escouade."); return false; }
        Invit deja = invits.get(b.getUniqueId());
        if (deja != null && deja.fin() > System.currentTimeMillis()) { refus(a, nomDe(b) + " a déjà une invitation."); return false; }
        long fin = System.currentTimeMillis() + cfg.invitationSecondes * 1000L;
        String nomEsc = e == null ? prenom(va.c().name()) : e.nom;
        invits.put(b.getUniqueId(), new Invit(a.getUniqueId(), va.c().id(), va.c().name(), e == null ? null : e.id,
                e == null ? null : e.nom, fin));
        String de = court(va.c().name());
        if (Canal.ecoute(b)) {
            canal.envoyer(b, "{\"t\":\"invit\"," + Canal.kv("de", de) + ',' + Canal.kv("esc", nomEsc) + ",\"creation\":"
                    + (e == null) + ",\"s\":" + cfg.invitationSecondes + '}');
        } else {
            b.sendMessage(ChatColor.GOLD + "[Radio] " + ChatColor.WHITE + de + " t'ajoute à l'escouade " + nomEsc
                    + ChatColor.GRAY + " — /radio accepter ou /radio refuser (" + cfg.invitationSecondes + " s)");
        }
        dire(a, "info", "Invitation envoyée", "à " + nomDe(b) + " · " + cfg.invitationSecondes + " s");
        journal.entree(Categorie.RADIO, "INVITATION").acteur(a).cible(b)
                .detail(e == null ? "création d'une escouade" : "Escouade " + e.nom).lieu(a.getLocation()).ecrire();
        return true;
    }

    void accepter(Player b) {
        Invit i = invits.remove(b.getUniqueId());
        canal.envoyer(b, "{\"t\":\"invit_fin\"}");
        if (i == null || i.fin() < System.currentTimeMillis()) { refus(b, "Aucune invitation en attente."); return; }
        Player a = Bukkit.getPlayer(i.deCompte());
        Vu va = a == null ? null : vuFrais(a);
        Vu vb = vuFrais(b);
        if (va == null || !va.c().id().equals(i.dePerso())) { refus(b, "L'invitation n'est plus valable."); return; }
        String r = refusActeur(vb);
        if (r != null) { refus(b, r); return; }
        if (!proche(a, b)) { refus(b, "Trop loin : rapprochez-vous."); refus(a, "Trop loin : rapprochez-vous."); return; }
        if (vb.info() == null || vb.info().coupee()) { refus(b, "Il te faut une oreillette en état."); return; }
        long now = System.currentTimeMillis();
        Escouade e;
        if (i.escouade() == null) {
            if (va.info() == null || va.info().coupee() || va.membreDe() != null) { refus(b, "L'invitation n'est plus valable."); return; }
            if (vb.membreDe() != null) { refus(b, "Tu as déjà une escouade."); return; }
            e = escouades.creer(prenom(va.c().name()), va.c().id(), now);
            lierEtAjouter(e, a, va, null, now);
            journal.entree(Categorie.RADIO, "CREATION").acteur(a).cible(b).detail("Escouade " + e.nom).lieu(a.getLocation()).ecrire();
        } else {
            e = escouades.get(i.escouade());
            if (e == null || e.membre(va.c().id()) == null) { refus(b, "L'escouade n'existe plus."); return; }
            if (vb.membreDe() != null && vb.membreDe() != e) { refus(b, "Quitte d'abord ton escouade."); return; }
            if (vb.membreDe() == null && e.membres.size() >= cfg.tailleMax) { refus(b, "L'escouade est complète."); return; }
        }
        int n = lierEtAjouter(e, b, vb, va.c().id(), now);
        journal.entree(Categorie.RADIO, "AJOUT").acteur(a).cible(b)
                .detail("Escouade " + e.nom + " · oreillette n° " + n).lieu(b.getLocation()).ecrire();
        dire(b, "info", "Escouade " + e.nom, "tu entends l'escouade (B pour parler, N pour la voir)");
        dire(a, "info", nomDe(b) + " a rejoint", "l'escouade " + e.nom);
        maintenant();
    }

    /** Lie l'oreillette portée par {@code p} à l'escouade et l'inscrit (ou la relie) ; renvoie le numéro. */
    private int lierEtAjouter(Escouade e, Player p, Vu v, UUID ajoutePar, long now) {
        Oreillettes.Info i = v.info();
        int n = i.numero() >= 0 ? i.numero() : escouades.nouveauNumero();
        ItemStack lie = oreillettes.lier(v.item(), n, e.id, v.c().id(), p.getUniqueId(), v.c().name());
        inv.poserCosmetique(p, cfg.emplacement, lie);
        Escouade.Membre m = e.membre(v.c().id());
        if (m == null) {
            escouades.ajouter(e, new Escouade.Membre(v.c().id(), p.getUniqueId(), v.c().name(), ajoutePar, now, n));
        } else {
            if (m.oreillette >= 0 && m.oreillette != n) e.exclues.add(m.oreillette);
            m.oreillette = n;
            m.prise = false;
            m.nom = v.c().name();
            e.exclues.remove(n);
            escouades.marquer();
        }
        e.derniereConnexion = now;
        return n;
    }

    void refuser(Player b) {
        Invit i = invits.remove(b.getUniqueId());
        canal.envoyer(b, "{\"t\":\"invit_fin\"}");
        if (i == null) return;
        Player a = Bukkit.getPlayer(i.deCompte());
        if (a != null) dire(a, "info", nomDe(b) + " a refusé", null);
        journal.entree(Categorie.RADIO, "INVITATION_REFUSEE").acteur(b).cible(i.deCompte())
                .detail(i.nomEscouade() == null ? "création" : "Escouade " + i.nomEscouade()).ecrire();
    }

    void quitter(Player p) {
        Vu v = vuFrais(p);
        Escouade e = v == null ? null : v.membreDe();
        if (e == null) { refus(p, "Tu n'as pas d'escouade."); return; }
        escouades.retirer(e, v.c().id());
        if (v.info() != null && e.id.equals(v.info().escouade())) inv.poserCosmetique(p, cfg.emplacement, oreillettes.delier(v.item()));
        journal.entree(Categorie.RADIO, "DEPART").acteur(p).cibleTexte("Escouade " + e.nom).ecrire();
        dire(p, "info", "Tu as quitté", "l'escouade " + e.nom);
        if (e.membres.isEmpty()) {
            escouades.dissoudre(e);
            journal.entree(Categorie.RADIO, "DISSOLUTION").acteur(p).cibleTexte("Escouade " + e.nom).detail("vide").ecrire();
        }
        maintenant();
    }

    void renommer(Player p, String brut) {
        Vu v = vuFrais(p);
        Escouade e = v == null ? null : v.membreDe();
        if (e == null) { refus(p, "Tu n'as pas d'escouade."); return; }
        if (!e.estCreateur(v.c().id())) { refus(p, "Seul le créateur renomme l'escouade."); return; }
        String nom = nettoyer(brut);
        if (nom.length() < 2) { refus(p, "Nom trop court."); return; }
        String ancien = e.nom;
        e.nom = nom;
        escouades.marquer();
        journal.entree(Categorie.RADIO, "RENOMMAGE").acteur(p).cibleTexte("Escouade " + nom).detail(ancien + " → " + nom).ecrire();
        dire(p, "info", "Escouade renommée", "« " + nom + " »");
        maintenant();
    }

    /** Créateur : exclut un membre (son oreillette ne capte plus rien). */
    void exclure(Player p, String cible) {
        Vu v = vuFrais(p);
        Escouade e = v == null ? null : v.membreDe();
        if (e == null) { refus(p, "Tu n'as pas d'escouade."); return; }
        if (!e.estCreateur(v.c().id())) { refus(p, "Seul le créateur exclut."); return; }
        Escouade.Membre m = membre(e, cible);
        if (m == null) { refus(p, "Ce membre n'existe pas."); return; }
        if (m.perso.equals(v.c().id())) { refus(p, "Pour partir : Quitter."); return; }
        escouades.retirer(e, m.perso);
        journal.entree(Categorie.RADIO, "EXCLUSION").acteur(p).ciblePersonnage(m.compte, m.perso)
                .detail("Escouade " + e.nom + (m.oreillette >= 0 ? " · oreillette n° " + m.oreillette + " exclue" : "")).ecrire();
        Player cp = m.compte == null ? null : Bukkit.getPlayer(m.compte);
        if (cp != null) dire(cp, "info", "Exclu", "de l'escouade " + e.nom);
        dire(p, "info", m.nom + " exclu", null);
        maintenant();
    }

    /** Créateur : coupe l'oreillette liée à un membre (parade au vol), le membre reste. */
    void exclureOreillette(Player p, String cible) {
        Vu v = vuFrais(p);
        Escouade e = v == null ? null : v.membreDe();
        if (e == null) { refus(p, "Tu n'as pas d'escouade."); return; }
        if (!e.estCreateur(v.c().id())) { refus(p, "Seul le créateur exclut une oreillette."); return; }
        Escouade.Membre m = membre(e, cible);
        if (m == null || m.oreillette < 0) { refus(p, "Pas d'oreillette liée."); return; }
        int n = m.oreillette;
        e.exclues.add(n);
        m.oreillette = -1;
        m.prise = false;
        escouades.marquer();
        journal.entree(Categorie.RADIO, "OREILLETTE_EXCLUE").acteur(p).ciblePersonnage(m.compte, m.perso)
                .detail("Escouade " + e.nom + " · oreillette n° " + n).ecrire();
        dire(p, "info", "Oreillette de " + m.nom, "exclue : elle ne capte plus rien");
        maintenant();
    }

    void dissoudre(Player p) {
        Vu v = vuFrais(p);
        Escouade e = v == null ? null : v.membreDe();
        if (e == null) { refus(p, "Tu n'as pas d'escouade."); return; }
        if (!e.estCreateur(v.c().id())) { refus(p, "Seul le créateur dissout l'escouade."); return; }
        List<UUID> comptes = e.membres.values().stream().map(m -> m.compte).toList();
        escouades.dissoudre(e);
        journal.entree(Categorie.RADIO, "DISSOLUTION").acteur(p).cibleTexte("Escouade " + e.nom).detail("par le créateur").ecrire();
        for (UUID c : comptes) {
            Player m = c == null ? null : Bukkit.getPlayer(c);
            if (m != null) dire(m, "info", "Escouade " + e.nom, "dissoute");
        }
        maintenant();
    }

    private Escouade.Membre membre(Escouade e, String cible) {
        if (cible == null) return null;
        for (Escouade.Membre m : e.membres.values()) {
            if (m.perso.toString().equalsIgnoreCase(cible) || (m.compte != null && m.compte.toString().equalsIgnoreCase(cible))
                    || m.nom.equalsIgnoreCase(cible)) return m;
        }
        Player p = Bukkit.getPlayerExact(cible);
        if (p != null) for (Escouade.Membre m : e.membres.values()) if (p.getUniqueId().equals(m.compte)) return m;
        return null;
    }

    /* ------------------------------------------------------------------ l'oreillette d'un joueur à terre */

    private String refusOreillette(Player a, Vu va, Player b, Vu vb) {
        String r = refusActeur(va);
        if (r != null) return r;
        if (b == null || vb == null || vb.horsJeu() || b.equals(a)) return "Personne ici.";
        if (!vb.ko()) return nomDe(b) + " n'est pas à terre.";
        if (!proche(a, b)) return "Approche-toi.";
        if (vb.info() == null) return nomDe(b) + " ne porte pas d'oreillette.";
        String verrou = inv.lockReason(a);
        if (verrou != null) return "Impossible (" + verrou + ").";
        return null;
    }

    void couperOreillette(Player a, Player b) {
        Vu va = vuFrais(a), vb = b == null ? null : vuFrais(b);
        String r = refusOreillette(a, va, b, vb);
        if (r != null) { refus(a, r); return; }
        if (vb.info().coupee()) { refus(a, "Son oreillette est déjà coupée."); return; }
        inv.poserCosmetique(b, cfg.emplacement, oreillettes.couper(vb.item(), true));
        Escouade e = escouades.get(vb.info().escouade());
        journal.entree(Categorie.RADIO, "OREILLETTE_COUPEE").acteur(a).cible(b)
                .detail("oreillette n° " + vb.info().numero() + (e == null ? "" : " · Escouade " + e.nom)).lieu(b.getLocation()).ecrire();
        dire(a, "info", "Oreillette coupée", "fil arraché");
        dire(b, "refus", "Ton oreillette est coupée", null);
        maintenant();
    }

    void prendreOreillette(Player a, Player b) {
        Vu va = vuFrais(a), vb = b == null ? null : vuFrais(b);
        String r = refusOreillette(a, va, b, vb);
        if (r != null) { refus(a, r); return; }
        ItemStack prise = inv.poserCosmetique(b, cfg.emplacement, null);
        if (prise == null) { refus(a, "Plus d'oreillette."); return; }
        inv.give(a, prise);
        Oreillettes.Info i = vb.info();
        Escouade e = escouades.get(i.escouade());
        if (e != null) {
            for (Escouade.Membre m : e.membres.values()) {
                if (m.oreillette == i.numero()) { m.prise = true; escouades.marquer(); }
            }
        }
        journal.entree(Categorie.RADIO, "OREILLETTE_PRISE").acteur(a).cible(b)
                .detail("oreillette n° " + i.numero() + (e == null ? "" : " · Escouade " + e.nom)).lieu(b.getLocation()).ecrire();
        dire(a, "info", "Oreillette prise", "dans ta sacoche");
        dire(b, "refus", "On t'a pris ton oreillette", null);
        maintenant();
    }

    /* ================================================================== staff */

    String info(Player cible) {
        Vu v = vuFrais(cible);
        if (v == null) return "Pas de personnage actif.";
        StringBuilder s = new StringBuilder();
        Oreillettes.Info i = v.info();
        s.append("Oreillette : ").append(i == null ? "aucune" : "n° " + i.numero() + (i.coupee() ? " (coupée)" : "")
                + (i.proprio() != null && !i.proprio().equals(v.c().id()) ? " · appartient à " + i.proprioNom() : ""));
        Escouade e = v.membreDe();
        s.append("\nEscouade : ").append(e == null ? "aucune" : e.nom + " (" + e.membres.size() + "/" + cfg.tailleMax + ")");
        if (e != null) {
            for (Escouade.Membre m : e.ordonnes()) {
                s.append("\n · ").append(m.nom).append(e.estCreateur(m.perso) ? " [créateur]" : "")
                        .append(m.oreillette >= 0 ? " · n° " + m.oreillette : " · sans oreillette").append(m.prise ? " (prise)" : "")
                        .append(vusParPerso.containsKey(m.perso) ? " · en ligne" : " · hors ligne");
            }
            if (!e.exclues.isEmpty()) s.append("\nOreillettes exclues : ").append(e.exclues);
        }
        if (v.canal() != null && v.canal() != e) s.append("\nEntend : ").append(v.canal().nom).append(" (oreillette d'autrui)");
        if (coupe(cible.getUniqueId(), System.currentTimeMillis())) s.append("\nRadio coupée par le staff.");
        return s.toString();
    }

    /** L'escouade que le staff écoute en visant {@code cible} : la sienne, sinon celle de l'oreillette qu'il porte. */
    Escouade escouadeDe(Player cible) {
        Vu v = vuFrais(cible);
        if (v == null) return null;
        return v.membreDe() != null ? v.membreDe() : v.canal();
    }

    void ecouter(Player staff, Player cible, int minutes, String motif) {
        Escouade e = escouadeDe(cible);
        if (e == null) { staff.sendMessage(ChatColor.RED + "[Radio] " + cible.getName() + " n'a pas d'escouade."); return; }
        long fin = System.currentTimeMillis() + minutes * 60_000L;
        ecoutes.put(staff.getUniqueId(), new Ecoute(e.id, e.nom, fin, motif, cible.getUniqueId()));
        canal.envoyer(staff, "{\"t\":\"ecoute\"," + Canal.kv("esc", e.nom) + ",\"reste\":" + minutes * 60 + '}');
        journal.entree(Categorie.STAFF, "RADIO_ECOUTE").acteur(staff).cible(cible)
                .detail("Escouade " + e.nom + " — " + motif + " — " + minutes + " min").ecrire();
        staff.sendMessage(ChatColor.GOLD + "[Radio] " + ChatColor.WHITE + "Tu écoutes l'escouade " + e.nom + " (" + minutes
                + " min). " + ChatColor.GRAY + "Inscrit au journal. /radio stop pour arrêter.");
        maintenant();
    }

    void stop(Player staff) {
        Ecoute ec = ecoutes.remove(staff.getUniqueId());
        canal.envoyer(staff, "{\"t\":\"ecoute_fin\"}");
        if (ec == null) { staff.sendMessage(ChatColor.GRAY + "[Radio] Aucune écoute en cours."); return; }
        journal.entree(Categorie.STAFF, "RADIO_ECOUTE_FIN").acteur(staff).cible(ec.cible()).detail("Escouade " + ec.nomEscouade()).ecrire();
        staff.sendMessage(ChatColor.GRAY + "[Radio] Fin de l'écoute de l'escouade " + ec.nomEscouade() + ".");
        maintenant();
    }

    void couperStaff(CommandSender staff, Player cible, int minutes, String motif) {
        coupures.put(cible.getUniqueId(), System.currentTimeMillis() + minutes * 60_000L);
        finEmission(cible.getUniqueId());
        journal.entree(Categorie.STAFF, "RADIO_COUPURE").acteur(staff).cible(cible)
                .detail(minutes + " min" + (motif.isBlank() ? "" : " — " + motif)).ecrire();
        dire(cible, "refus", "Radio coupée par le staff", minutes + " min");
        staff.sendMessage(ChatColor.GOLD + "[Radio] " + ChatColor.WHITE + cible.getName() + " ne peut plus émettre (" + minutes + " min).");
        maintenant();
    }

    void retirerStaff(CommandSender staff, Player cible, String motif) {
        Vu v = vuFrais(cible);
        Escouade e = v == null ? null : v.membreDe();
        if (e == null) { staff.sendMessage(ChatColor.RED + "[Radio] " + cible.getName() + " n'a pas d'escouade."); return; }
        escouades.retirer(e, v.c().id());
        if (v.info() != null && e.id.equals(v.info().escouade())) inv.poserCosmetique(cible, cfg.emplacement, oreillettes.delier(v.item()));
        if (e.membres.isEmpty()) escouades.dissoudre(e);
        journal.entree(Categorie.STAFF, "RADIO_RETRAIT").acteur(staff).cible(cible)
                .detail("Escouade " + e.nom + (motif.isBlank() ? "" : " — " + motif)).ecrire();
        dire(cible, "info", "Retiré de l'escouade", e.nom);
        staff.sendMessage(ChatColor.GOLD + "[Radio] " + ChatColor.WHITE + cible.getName() + " retiré de l'escouade " + e.nom + ".");
        maintenant();
    }

    void donner(CommandSender staff, Player cible) {
        inv.give(cible, oreillettes.neuve());
        journal.entree(Categorie.STAFF, "RADIO_DONNER").acteur(staff).cible(cible).detail("oreillette neuve").ecrire();
        staff.sendMessage(ChatColor.GOLD + "[Radio] " + ChatColor.WHITE + "Oreillette donnée à " + cible.getName() + ".");
    }

    void reparer(CommandSender qui, Player cible) {
        ItemStack porte = inv.cosmetique(cible, cfg.emplacement);
        Oreillettes.Info i = oreillettes.lire(porte);
        if (i != null && i.coupee()) {
            inv.poserCosmetique(cible, cfg.emplacement, oreillettes.couper(porte, false));
        } else {
            ItemStack main = cible.getInventory().getItemInMainHand();
            i = oreillettes.lire(main);
            if (i == null || !i.coupee()) { qui.sendMessage(ChatColor.RED + "[Radio] Aucune oreillette coupée portée ou en main."); return; }
            cible.getInventory().setItemInMainHand(oreillettes.couper(main, false));
        }
        journal.entree(Categorie.RADIO, "OREILLETTE_REPAREE").acteur(qui).cible(cible).detail("oreillette n° " + i.numero()).ecrire();
        qui.sendMessage(ChatColor.GOLD + "[Radio] " + ChatColor.WHITE + "Oreillette n° " + i.numero() + " réparée.");
        if (!qui.equals(cible)) dire(cible, "info", "Oreillette réparée", null);
        maintenant();
    }

    /* ================================================================== utilitaires */

    String nomDe(Player p) {
        ShinobiCharacter c = chars.getActive(p.getUniqueId());
        return c == null ? p.getName() : court(c.name());
    }

    /** « Aki Uchiha » → « Aki U. » ; un seul mot : tel quel. */
    static String court(String nom) {
        if (nom == null || nom.isBlank()) return "?";
        String[] m = nom.trim().split("\\s+");
        if (m.length == 1) return m[0];
        String dernier = m[m.length - 1];
        return m[0] + " " + dernier.substring(0, dernier.offsetByCodePoints(0, 1)) + ".";
    }

    static String prenom(String nom) {
        if (nom == null || nom.isBlank()) return "Escouade";
        return nom.trim().split("\\s+")[0];
    }

    private String nettoyer(String s) {
        if (s == null) return "";
        String n = ChatColor.stripColor(s).replaceAll("[\\p{Cntrl}§&<>\"\\\\]", "").replaceAll("\\s+", " ").trim();
        if (n.codePointCount(0, n.length()) > cfg.nomMax) n = n.substring(0, n.offsetByCodePoints(0, cfg.nomMax)).trim();
        return n;
    }

    void refus(Player p, String texte) { dire(p, "refus", texte, null); }

    /** Une ligne dans la pile de notifications du mod, sinon au tchat. Aucun kanji (règle du 14/10). */
    void dire(Player p, String ton, String gras, String texte) {
        if (p == null) return;
        if (p.getListeningPluginChannels().contains(Notifs.CHANNEL)) {
            Notifs.msg(p, ton, null, gras, texte);
        } else {
            p.sendMessage((ton.equals("refus") ? ChatColor.RED : ChatColor.GOLD) + "[Radio] " + ChatColor.WHITE + gras
                    + (texte == null || texte.isBlank() ? "" : ChatColor.GRAY + " " + texte));
        }
    }

    Set<UUID> ecouteurs() { return Collections.unmodifiableSet(ecoutes.keySet()); }

    static String bas(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }
}
