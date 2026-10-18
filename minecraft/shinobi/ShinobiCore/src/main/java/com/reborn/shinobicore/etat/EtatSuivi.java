package com.reborn.shinobicore.etat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.api.etat.Cadre;
import com.reborn.shinobicore.api.etat.Capacite;
import com.reborn.shinobicore.api.etat.Controle;
import com.reborn.shinobicore.api.etat.Etat;
import com.reborn.shinobicore.api.etat.Modificateur;
import com.reborn.shinobicore.api.etat.Vital;
import com.reborn.shinobicore.api.event.EtatChangedEvent;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.ko.zone.TrainingZone;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le <b>suivi</b> des états, chaque tick (docs/technique/ETATS_JOUEUR.md §7) : pour chaque joueur en ligne, entretien
 * des valeurs tenues (durées, liens), actions longues, applicateur des effets physiques, puis — <b>au changement</b>
 * seulement — journal {@code ETAT}, {@link EtatChangedEvent} et paquet {@code reborn:etat} au mod. Peines de prison
 * (fin, évasion hors de la zone).
 *
 * <p><b>Paquet {@code reborn:etat}</b> (S2C, JSON UTF-8 brut, comme les autres canaux) : voir
 * {@code PLUGINS_REFERENCE.md} §4 quater. Envoyé à chaque changement (regroupé par tick), au JOIN et au changement de
 * personnage ; jamais à un client qui n'écoute pas le canal.
 */
public final class EtatSuivi implements Listener {

    public static final String CHANNEL = "reborn:etat";

    private final ShinobiCore plugin;
    private final EtatServiceImpl service;
    private final Applicateur applicateur;
    private final Map<UUID, Etat> derniers = new ConcurrentHashMap<>();
    private final Map<UUID, String> envoyes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> evasions = new ConcurrentHashMap<>();
    private BukkitTask tache;
    private long seq;
    private int ticks;

    EtatSuivi(ShinobiCore plugin, EtatServiceImpl service, Applicateur applicateur) {
        this.plugin = plugin;
        this.service = service;
        this.applicateur = applicateur;
    }

    void demarrer() {
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL)) m.registerOutgoingPluginChannel(plugin, CHANNEL);
        tache = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    void arreter() {
        if (tache != null) { tache.cancel(); tache = null; }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        ticks++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                suivre(p, now);
            } catch (RuntimeException ex) {
                if (ticks % 200 == 0) plugin.getLogger().warning("[etats] suivi de " + p.getName() + " : " + ex);
            }
        }
    }

    private void suivre(Player p, long now) {
        UUID id = p.getUniqueId();
        service.entretenir(p, service.vital(id), now);
        Etat e = service.calculer(id);
        service.occupations().verifier(p, e);
        applicateur.appliquer(p, e);
        if (ticks % 20 == 0) prison(p, e);
        Etat avant = derniers.put(id, e);
        if (avant != null && memes(avant, e)) return;
        if (avant != null && service.journalActif()) journaliser(p, avant, e);
        Bukkit.getPluginManager().callEvent(new EtatChangedEvent(p, avant, e));
        if (service.syncActif()) envoyer(p, e, false);
    }

    /** Même état observable (couches, principal, lien, modificateurs, capacités) ? Le temps restant n'en fait pas partie. */
    private static boolean memes(Etat a, Etat b) {
        if (a.principal() != b.principal() || a.vital() != b.vital() || a.controle() != b.controle() || a.cadre() != b.cadre())
            return false;
        if (!Objects.equals(a.lien() == null ? null : a.lien().source(), b.lien() == null ? null : b.lien().source())) return false;
        if (!a.capacites().equals(b.capacites())) return false;
        if (a.modificateurs().size() != b.modificateurs().size()) return false;
        for (int i = 0; i < a.modificateurs().size(); i++) {
            Modificateur x = a.modificateurs().get(i), y = b.modificateurs().get(i);
            if (!x.id().equals(y.id()) || Math.abs(x.intensite() - y.intensite()) > 1e-6) return false;
        }
        return true;
    }

    /**
     * Transitions seulement (§7.4). Le vital et le portage sont journalisés par la chaîne KO (KoJournal, catégorie
     * {@code ETAT} pour les simples passages, avec leurs détails) : pas de doublon ici. Garde et étourdi (plusieurs par
     * seconde en combat) ne le sont pas non plus.
     */
    private void journaliser(Player p, Etat avant, Etat e) {
        if (avant.controle() != e.controle()) {
            Set<Controle> ailleurs = Set.of(Controle.GARDE, Controle.ETOURDI, Controle.PORTE);
            if (!ailleurs.contains(avant.controle()) && !ailleurs.contains(e.controle())) {
                Journal.entree(Categorie.ETAT, "ETAT_CONTROLE").cible(p)
                        .detail(avant.controle() + " → " + e.controle()
                                + (e.lien() != null ? " · " + e.lien().type() + " " + e.lien().nom() : ""))
                        .lieu(p.getLocation()).ecrire();
            }
        }
        if (avant.cadre() != e.cadre()) {
            boolean connexion = (avant.cadre() == Cadre.HORS_JEU && e.cadre() == Cadre.MONDE)
                    || (avant.cadre() == Cadre.MONDE && e.cadre() == Cadre.HORS_JEU);
            if (!connexion) {
                Journal.entree(Categorie.ETAT, "ETAT_CADRE").cible(p).detail(avant.cadre() + " → " + e.cadre())
                        .lieu(p.getLocation()).ecrire();
            }
        }
    }

    /* ================================================================ prison */

    private void prison(Player p, Etat e) {
        ShinobiCharacter c = plugin.characters() != null ? plugin.characters().getActive(p.getUniqueId()) : null;
        if (c == null) return;
        Prison pr = service.prison();
        var peine = pr.toutes().get(c.id());
        if (peine == null) return;
        if (pr.echue(c.id())) {
            p.sendMessage(Component.text("Ta peine est purgée : tu es libre.", NamedTextColor.GOLD));
            Journal.entree(Categorie.ETAT, "PRISON_FIN").cible(p).detail("peine purgée").lieu(p.getLocation()).ecrire();
            return;
        }
        if (peine.zone() == null || plugin.trainingZones() == null || e.cadre() != Cadre.PRISON) return;
        TrainingZone z = plugin.trainingZones().get(peine.zone());
        if (z == null || z.kind() != TrainingZone.Kind.PRISON || z.contains(p.getLocation())) {
            evasions.remove(p.getUniqueId());
            return;
        }
        long now = System.currentTimeMillis();
        Long derniere = evasions.get(p.getUniqueId());
        if (derniere != null && now - derniere < 60_000L) return;
        evasions.put(p.getUniqueId(), now);
        Journal.entree(Categorie.ETAT, "EVASION").cible(p).detail("hors de la zone " + z.id()).lieu(p.getLocation()).ecrire();
        if (plugin.staffPanel() != null) plugin.staffPanel().onEvasion(p, z.id(), p.getLocation());
    }

    /* ================================================================ paquet reborn:etat */

    /** Envoie l'état au mod (s'il écoute le canal). {@code force} : même si rien n'a changé. */
    void envoyer(Player p, Etat e, boolean force) {
        if (p == null || !p.isOnline() || !p.getListeningPluginChannels().contains(CHANNEL)) return;
        JsonObject o = json(e);
        String corps = o.toString();
        if (!force && corps.equals(envoyes.get(p.getUniqueId()))) return;
        envoyes.put(p.getUniqueId(), corps);
        o.addProperty("seq", ++seq);
        try {
            p.sendPluginMessage(plugin, CHANNEL, o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException ignored) {
            // client sans le mod
        }
    }

    /** Le JSON (sans {@code seq}). Public pour les bancs et {@code /etats}. */
    public static JsonObject json(Etat e) {
        JsonObject o = new JsonObject();
        o.addProperty("v", 1);
        o.addProperty("p", e.principal().name());
        JsonObject c = new JsonObject();
        c.addProperty("vital", e.vital().name());
        c.addProperty("ctrl", e.controle().name());
        c.addProperty("cadre", e.cadre().name());
        o.add("c", c);
        JsonObject cap = new JsonObject();
        JsonObject raison = new JsonObject();
        for (Capacite k : Capacite.values()) {
            if (k.code() == null) continue;
            int n = e.capacites().niveau(k);
            cap.addProperty(k.code(), n);
            if (n < 2 && !k.speciale()) {
                String r = e.capacites().verdict(k).raison();
                if (r != null && !r.isEmpty()) raison.addProperty(k.code(), r);
            }
        }
        cap.addProperty("sac", e.peut(Capacite.MODIFIER_SACOCHE) ? "M" : e.peut(Capacite.OUVRIR_SACOCHE) ? "L" : "0");
        o.add("cap", cap);
        o.addProperty("vit", Math.round(e.capacites().vitesse() * 1000) / 1000.0);
        o.addProperty("saut", Math.round(e.capacites().saut() * 1000) / 1000.0);
        if (e.lien() != null) {
            JsonObject l = new JsonObject();
            l.addProperty("type", e.lien().type());
            l.addProperty("nom", e.lien().nom());
            o.add("lien", l);
        }
        JsonArray mods = new JsonArray();
        for (Modificateur m : e.modificateurs()) {
            if (!m.derive()) continue;   // les modificateurs du registre ont leur canal (reborn:modifs, icônes)
            JsonObject j = new JsonObject();
            j.addProperty("id", m.id());
            j.addProperty("n", m.intensite());
            j.addProperty("fin", m.resteMs());
            mods.add(j);
        }
        o.add("mods", mods);
        if (raison.size() > 0) o.add("raison", raison);
        return o;
    }

    /* ================================================================ événements */

    @EventHandler
    public void onJoin(PlayerJoinEvent ev) {
        Player p = ev.getPlayer();
        envoyes.remove(p.getUniqueId());
        derniers.remove(p.getUniqueId());
        // le client enregistre ses canaux juste après la connexion : on renvoie quand il écoute
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) envoyer(p, service.calculer(p.getUniqueId()), true);
        }, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwitch(com.reborn.shinobicore.api.event.CharacterSwitchEvent ev) {
        Player p = ev.player();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) envoyer(p, service.calculer(p.getUniqueId()), true);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent ev) {
        UUID id = ev.getPlayer().getUniqueId();
        derniers.remove(id);
        envoyes.remove(id);
        evasions.remove(id);
        service.oublier(id);
    }

    /** Dernier état observé (vue rapide pour l'écran staff), ou null. */
    Etat dernier(UUID id) { return derniers.get(id); }

    static boolean ko(Vital v) { return v == Vital.A_TERRE || v == Vital.INCONSCIENT; }
}
