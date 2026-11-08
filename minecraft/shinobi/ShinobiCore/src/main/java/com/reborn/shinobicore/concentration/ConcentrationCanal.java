package com.reborn.shinobicore.concentration;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.concentration.plaque.Plaque;
import com.reborn.shinobicore.concentration.plaque.PlaqueRegles;
import com.reborn.shinobicore.concentration.plaque.PlaqueService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Canal {@value #CANAL} (lot 4 « Concentration », étape B ; PLUGINS_REFERENCE §4 undecies). JSON UTF-8 brut, champ
 * {@code v} = {@value #VERSION}, champs inconnus ignorés. <b>Jamais</b> {@code reborn:plaques} (nametags staff).
 *
 * <table>
 *   <tr><th>Sens</th><th>Message</th><th>Effet</th></tr>
 *   <tr><td>C → S</td><td>{@code {"v":1,"a":"on"}} / {@code {"a":"off"}}</td><td>entrer (4 par seconde au plus ; au-delà,
 *       réponse {@code etat on:false}) / sortir (jamais limité : sortir ne fait que retirer un droit)</td></tr>
 *   <tr><td>S → C</td><td>{@code {"v":1,"t":"etat","on":true}}</td><td>accepté : les ancres suivent aussitôt</td></tr>
 *   <tr><td>S → C</td><td>{@code {"v":1,"t":"etat","on":false,"raison":"…"}}</td><td>refus ou fin imposée : capacité
 *       {@code CONCENTRER} refusée par la matrice d'états (mort, à terre, inconscient, cinématique, sélection ou hors
 *       jeu, douane, monde intérieur…), écran noir du staff, spectateur, changement de personnage</td></tr>
 *   <tr><td>S → C</td><td>{@code {"v":1,"t":"ancres","l":[…]}}</td><td>plaques lisibles à 32 blocs, <b>sans le
 *       texte</b> ; à l'entrée puis toutes les 2 s si la liste change</td></tr>
 *   <tr><td>C → S</td><td>{@code {"v":1,"a":"lire","id":"P1A"}}</td><td>10 par seconde au plus</td></tr>
 *   <tr><td>S → C</td><td>{@code {"v":1,"t":"texte","id","rev","segs","halo"}}</td><td>seulement concentré, à portée
 *       + 1,5, dans le cône, en vue ({@link PlaqueService#verifierLecture}) ; {@code halo} absent = ivoire-or, {@code "aucun"}
 *       ou la lueur {@code "RRGGBB"} (planche c6)</td></tr>
 *   <tr><td>S → C</td><td>{@code {"v":1,"t":"texte_refus","id"}}</td><td>silencieux côté mod</td></tr>
 *   <tr><td>S → C</td><td>{@code {"v":1,"t":"traces","plein","o","a","r"}}</td><td>traces de pas des autres joueurs
 *       (étape C, {@link com.reborn.shinobicore.concentration.trace.TraceService}) : complètes à l'entrée, en différentiel
 *       chaque seconde, aux seuls concentrés</td></tr>
 * </table>
 * Rien n'est journalisé pour l'entrée et la sortie (bruit) ; personne d'autre ne sait qu'un joueur est concentré. Une
 * lecture n'est comptée (« lue N fois », journal facultatif) qu'une fois par joueur, plaque et révision pendant sa
 * connexion : un client qui redemande le texte en boucle ne gonfle rien.
 */
public final class ConcentrationCanal implements PluginMessageListener, Listener {

    public static final String CANAL = "reborn:concentration";
    public static final int VERSION = 1;

    private final ShinobiCore plugin;
    private final PlaqueService plaques;
    private final Gson gson = new Gson();
    private final Set<UUID> concentres = new HashSet<>();
    /** Lectures déjà comptées par joueur ({@code id:rev}), pendant sa connexion. */
    private final Map<UUID, Set<String>> comptees = new HashMap<>();
    /** Dernière empreinte d'ancres envoyée (et la version du service à cet instant). */
    private final Map<UUID, String> derniere = new HashMap<>();
    private final PlaqueRegles.Limiteur<UUID> bascules = new PlaqueRegles.Limiteur<>(PlaqueRegles.BASCULES_S, 1000);
    private final PlaqueRegles.Limiteur<UUID> lectures = new PlaqueRegles.Limiteur<>(PlaqueRegles.LECTURES_S, 1000);
    private int tache = -1;
    /** Traces de pas (étape C) : la liste complète part à chaque entrée. */
    private com.reborn.shinobicore.concentration.trace.TraceService traces;

    public ConcentrationCanal(ShinobiCore plugin, PlaqueService plaques) {
        this.plugin = plugin;
        this.plaques = plaques;
    }

    public void demarrer() {
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CANAL)) m.registerOutgoingPluginChannel(plugin, CANAL);
        m.registerIncomingPluginChannel(plugin, CANAL, this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        tache = Bukkit.getScheduler().runTaskTimer(plugin, this::pousser, 40L, 40L).getTaskId();
    }

    public void arreter() {
        if (tache >= 0) Bukkit.getScheduler().cancelTask(tache);
        var m = Bukkit.getMessenger();
        m.unregisterIncomingPluginChannel(plugin, CANAL, this);
        concentres.clear();
        derniere.clear();
        comptees.clear();
    }

    public void traces(com.reborn.shinobicore.concentration.trace.TraceService t) { traces = t; }

    public boolean concentre(UUID id) { return concentres.contains(id); }

    /** Les joueurs concentrés (vue en lecture seule, fil principal). */
    public Set<UUID> concentres() { return java.util.Collections.unmodifiableSet(concentres); }

    /**
     * Relit {@link #refus} avant d'envoyer quelque chose à un concentré (traces, chaque seconde : {@link #pousser} ne passe
     * que toutes les 2 s) ; faux : il n'est plus concentré, le mod en est averti. On ne fait pas confiance au client.
     */
    public boolean maintenir(Player p) {
        String r = refus(p);
        if (r == null) return true;
        concentres.remove(p.getUniqueId());
        derniere.remove(p.getUniqueId());
        etat(p, false, r);
        return false;
    }

    /** Les ancres seront renvoyées à tous au prochain passage (une plaque a changé). */
    public void relire() { derniere.clear(); }

    /* ================================================================ réception */

    @Override
    public void onPluginMessageReceived(String channel, Player p, byte[] bytes) {
        if (!CANAL.equals(channel) || bytes.length > 512) return;
        String raw = new String(bytes, StandardCharsets.UTF_8);
        Bukkit.getScheduler().runTask(plugin, () -> recevoir(p, raw));
    }

    private void recevoir(Player p, String raw) {
        if (!p.isOnline()) return;
        JsonObject in;
        String a, id;
        try {
            in = JsonParser.parseString(raw).getAsJsonObject();
            if (!in.has("v") || in.get("v").getAsInt() != VERSION) return;
            a = in.has("a") ? in.get("a").getAsString() : "";
            id = in.has("id") ? in.get("id").getAsString() : "";
        } catch (RuntimeException e) { return; }
        long now = System.currentTimeMillis();
        switch (a) {
            case "on" -> {
                if (!bascules.essayer(p.getUniqueId(), now)) {
                    // trop de bascules : refus explicite, le mod sort du mode (jamais un client « en mode » sans plaques)
                    concentres.remove(p.getUniqueId());
                    derniere.remove(p.getUniqueId());
                    etat(p, false, "Trop vite.");
                    return;
                }
                String refus = refus(p);
                if (refus != null) { etat(p, false, refus); concentres.remove(p.getUniqueId()); return; }
                concentres.add(p.getUniqueId());
                etat(p, true, null);
                derniere.remove(p.getUniqueId());
                ancres(p, true);
                if (traces != null) traces.entree(p);
            }
            case "off" -> {
                // jamais limité : sortir ne fait que retirer un droit
                concentres.remove(p.getUniqueId());
                derniere.remove(p.getUniqueId());
            }
            case "lire" -> {
                if (!lectures.essayer(p.getUniqueId(), now) || id.length() > 16) return;
                lire(p, id);
            }
            default -> { }
        }
    }

    /**
     * Pourquoi la concentration est refusée (null = permise), relu à {@code on}, à chaque demande de texte et toutes les
     * 2 s. Les cas de la décision du 07/11 sont relus <b>en dur</b> chez leurs détenteurs — mort, à terre ou inconscient
     * (chaîne KO), cinématique, douane, écran de sélection ou sans personnage, spectateur — et non seulement par la
     * capacité {@code CONCENTRER} de la matrice : un {@code etats.yml} du disque antérieur au lot 4 garde ses lignes
     * {@code vital.A_TERRE}, {@code vital.INCONSCIENT}, {@code cadre.HORS_JEU[.DOUANE]} sans {@code CONCENTRER} (les lignes
     * du disque gagnent sur celles du jar), et un client modifié lirait alors les plaques à terre ou en douane. La matrice
     * reste consultée ensuite (monde intérieur, contrôle, lignes ajoutées plus tard), puis l'écran noir du staff. Le mod
     * bloque d'avance ; le serveur revérifie.
     */
    private String refus(Player p) {
        UUID id = p.getUniqueId();
        if (p.isDead() || p.getHealth() <= 0) return "Tu es à terre.";
        if (p.getGameMode() == GameMode.SPECTATOR) return "Impossible en spectateur.";
        if (com.reborn.shinobicore.etat.Verrous.mort(id)) return "Ton personnage est mort.";
        var ko = plugin.ko() != null ? plugin.ko().getKo(id) : null;
        if (ko != null) return ko.isDowned() ? "Tu es à terre." : "Tu es inconscient.";
        if (com.reborn.shinobicore.etat.Verrous.cinematique(id)) return "Cinématique en cours.";
        if (plugin.douane() != null && plugin.douane().cadreDouane(id)) return "Douane : aucune mécanique RP.";
        var o = plugin.outilsStaff();
        boolean modeStaff = o != null && o.mode().horsJeu(id);
        if (plugin.characterSelect() != null && plugin.characterSelect().isSelecting(id)) return "Hors jeu.";
        // sans personnage : hors jeu (le staff en mode staff garde la lecture, comme la matrice : cadre STAFF)
        if (!modeStaff && plugin.characters() != null && plugin.characters().getActive(id) == null) return "Hors jeu.";
        String r = com.reborn.shinobicore.etat.Verrous.refus(id, com.reborn.shinobicore.api.etat.Capacite.CONCENTRER);
        if (r != null) return r;
        if (o != null && o.effets() != null && o.effets().enNoir(id)) return "Écran noir.";
        return null;
    }

    private void lire(Player p, String id) {
        Plaque pl = plaques.get(id);
        String motif = !concentres.contains(p.getUniqueId()) ? "pas concentré" : null;
        if (motif == null) {
            // relu à chaque demande, pas seulement toutes les 2 s : à terre, inconscient, cinématique, sélection, douane…
            String r = refus(p);
            if (r != null) {
                concentres.remove(p.getUniqueId());
                derniere.remove(p.getUniqueId());
                etat(p, false, r);
                motif = "refus : " + r;
            }
        }
        if (motif == null) motif = plaques.verifierLecture(p, pl);
        if (motif != null) {
            JsonObject o = base("texte_refus");
            o.addProperty("id", id == null ? "" : id);
            envoyer(p, o);
            return;
        }
        JsonObject o = base("texte");
        o.addProperty("id", pl.id);
        o.addProperty("rev", pl.rev);
        o.add("segs", PlaqueService.segsJson(pl.segs));
        if (pl.halo != null) o.addProperty("halo", pl.halo);   // absent = ivoire-or (anciens mods : ignoré)
        envoyer(p, o);
        // une seule fois par (joueur, plaque, révision) : un client qui redemande en boucle ne gonfle ni le compteur ni le journal
        if (comptees.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>()).add(pl.id + ":" + pl.rev)) plaques.lue(p, pl);
    }

    /* ================================================================ envoi */

    private void pousser() {
        if (concentres.isEmpty()) return;
        for (UUID id : Set.copyOf(concentres)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) { concentres.remove(id); derniere.remove(id); continue; }
            String r = refus(p);
            if (r != null) {
                concentres.remove(id);
                derniere.remove(id);
                etat(p, false, r);
                continue;
            }
            ancres(p, false);
        }
    }

    /** Ancres du joueur ; envoyées si {@code force} ou si la liste a changé depuis le dernier envoi. */
    private void ancres(Player p, boolean force) {
        StringBuilder sig = new StringBuilder();
        JsonArray l = plaques.ancres(p, sig);
        String s = sig.toString();
        if (!force && s.equals(derniere.get(p.getUniqueId()))) return;
        derniere.put(p.getUniqueId(), s);
        JsonObject o = base("ancres");
        o.add("l", l);
        envoyer(p, o);
    }

    private void etat(Player p, boolean on, String raison) {
        JsonObject o = base("etat");
        o.addProperty("on", on);
        if (raison != null) o.addProperty("raison", raison);
        envoyer(p, o);
    }

    private static JsonObject base(String t) {
        JsonObject o = new JsonObject();
        o.addProperty("v", VERSION);
        o.addProperty("t", t);
        return o;
    }

    private void envoyer(Player p, JsonObject o) {
        envoyerBrut(p, gson.toJson(o));
    }

    /** Un message déjà composé (traces : JSON écrit à la main, plusieurs centaines de nombres à plat). */
    public void envoyerBrut(Player p, String json) {
        if (!plugin.isEnabled() || !p.getListeningPluginChannels().contains(CANAL)) return;
        p.sendPluginMessage(plugin, CANAL, json.getBytes(StandardCharsets.UTF_8));
    }

    /* ================================================================ fins imposées */

    @EventHandler
    public void quitter(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        concentres.remove(id);
        derniere.remove(id);
        comptees.remove(id);
        bascules.oublier(id);
        lectures.oublier(id);
    }

    /** Changement de personnage : la concentration s'arrête (le mod la ferme aussi, à l'écran de sélection). */
    @EventHandler
    public void personnage(com.reborn.shinobicore.api.event.CharacterSwitchEvent e) {
        Player p = e.player();
        derniere.remove(p.getUniqueId());
        if (concentres.remove(p.getUniqueId())) etat(p, false, "Changement de personnage.");
    }

    @EventHandler
    public void mort(PlayerDeathEvent e) {
        if (concentres.remove(e.getPlayer().getUniqueId())) etat(e.getPlayer(), false, "Tu es à terre.");
    }

    @EventHandler
    public void monde(PlayerChangedWorldEvent e) {
        derniere.remove(e.getPlayer().getUniqueId());
        if (concentres.contains(e.getPlayer().getUniqueId())) ancres(e.getPlayer(), true);
    }

    @EventHandler
    public void teleport(PlayerTeleportEvent e) {
        derniere.remove(e.getPlayer().getUniqueId());
    }
}
