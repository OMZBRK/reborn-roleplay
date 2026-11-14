package com.reborn.shinobicore.dojutsu;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.etat.Capacite;
import com.reborn.shinobicore.api.event.CharacterSwitchEvent;
import com.reborn.shinobicore.api.event.EtatChangedEvent;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.concentration.plaque.PlaqueRegles;
import com.reborn.shinobicore.etat.Verrous;
import com.reborn.shinobicore.medic.yeux.Cote;
import com.reborn.shinobicore.medic.yeux.Oeil;
import com.reborn.shinobicore.medic.yeux.YeuxService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * <b>Byakugan</b> (lot 5 « Mobilité », étape B, Game Plan n° 17 ; décision du 09/11 : « un vrai Byakugan à nous »). La
 * vision se tient sur la touche O du mod ; le serveur décide qui y a droit, la paie et la coupe. Le rendu est entièrement
 * côté client ({@code reborn-hud}, {@code byakugan/ByakuganClient}) : rien n'est dessiné par le serveur, rien n'est dit
 * au tchat, aucun nom ni aucune lecture de chakra n'est envoyé.
 *
 * <p>Canal {@value #CANAL}, JSON UTF-8 brut, champ {@code v} = {@value #VERSION}, champs inconnus ignorés
 * (PLUGINS_REFERENCE §4 duodecies) :
 * <table>
 *   <tr><th>Sens</th><th>Message</th><th>Effet</th></tr>
 *   <tr><td>C → S</td><td>{@code {"v":1,"a":"byakugan","on":true}}</td><td>activer (4 bascules par seconde au plus) ;
 *       {@code "on":false} : arrêter, jamais limité</td></tr>
 *   <tr><td>S → C</td><td>{@code {"v":1,"t":"regles","byakugan":bool,"portee":50.0}}</td><td>à la connexion (après 2 s),
 *       au changement de personnage, au rechargement : le personnage a-t-il le Byakugan ? Sans droit, la touche ne fait
 *       rien côté mod (et le serveur refuse quand même)</td></tr>
 *   <tr><td>S → C</td><td>{@code {"v":1,"t":"byakugan","on":true,"portee":50.0}}</td><td>accepté : le mod lance le fondu</td></tr>
 *   <tr><td>S → C</td><td>{@code {"v":1,"t":"byakugan","on":false,"raison":"…"}}</td><td>refus ou fin imposée : sans droit,
 *       capacité {@code TECHNIQUE} à 0, KO, mort, cinématique, douane, hors jeu, écran noir, chakra épuisé, changement de
 *       personnage ; sans raison : arrêt demandé par le joueur</td></tr>
 *   <tr><td>S → C</td><td>{@code {"v":1,"t":"signes","ids":[…]}}</td><td>porteurs proches ({@code signe-rayon}) dont le mod
 *       peint les yeux pâles ; seulement quand la liste change, jamais de nom</td></tr>
 * </table>
 *
 * <p><b>Coexistence avec ShinobiSense</b> (le jar du serveur de dev garde {@code /sense dojutsu} et son canal
 * {@code reborn:byakugan}, non commités, que personne ne touche) : avec {@code reprendre-sense: true}, ce module intercepte
 * {@code /sense dojutsu} pour un clan du Byakugan et {@code /sense byakugan} (staff), avant ShinobiSense ; le dōjutsu de
 * ShinobiSense ne s'allume donc plus pour eux (ni coût de 8 par seconde, ni lecture dans la barre d'action, ni annonce au
 * tchat). Les autres clans (Uchiha…) gardent ShinobiSense tel quel. Le mod écoute toujours {@code reborn:byakugan} : ShinobiSense
 * le voit et ne pose ni armure de chakra ni barre de boss ; le mod l'ignore dès que ce canal-ci lui a parlé.
 */
public final class ByakuganService implements PluginMessageListener, Listener {

    public static final String CANAL = "reborn:dojutsu";
    public static final int VERSION = 1;
    /** Relecture des porteurs : états, coût, signes (ticks). */
    private static final long PERIODE_TICKS = 10L;
    private static final long PERIODE_MS = PERIODE_TICKS * 50L;

    private final ShinobiCore plugin;
    private ByakuganRegles.Reglages reglages = ByakuganRegles.Reglages.DEFAUT;
    private List<String> clans = List.of("Hyuga", "Hyūga", "Otsutsuki", "Ōtsutsuki");
    /** Porteurs actifs ; la valeur dit si c'est la vision de test du staff (ni clan, ni coût). */
    private final Map<UUID, Boolean> actifs = new HashMap<>();
    /** Dernière liste de signes envoyée à chaque joueur (ids d'entités, triés). */
    private final Map<UUID, String> signesEnvoyes = new HashMap<>();
    private final PlaqueRegles.Limiteur<UUID> bascules = new PlaqueRegles.Limiteur<>(4, 1000);
    private int tache = -1;

    public ByakuganService(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    /* ================================================================ cycle de vie */

    public void demarrer() {
        recharger();
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CANAL)) m.registerOutgoingPluginChannel(plugin, CANAL);
        if (!m.isIncomingChannelRegistered(plugin, CANAL)) m.registerIncomingPluginChannel(plugin, CANAL, this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        tache = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, PERIODE_TICKS, PERIODE_TICKS).getTaskId();
    }

    public void arreter() {
        if (tache >= 0) Bukkit.getScheduler().cancelTask(tache);
        tache = -1;
        for (UUID id : Set.copyOf(actifs.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) etat(p, false, null);
        }
        actifs.clear();
        signesEnvoyes.clear();
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CANAL, this);
    }

    /** Relit {@code dojutsu.byakugan} et renvoie les droits à tous (rechargement de la config). */
    public void recharger() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("dojutsu.byakugan");
        ByakuganRegles.Reglages d = ByakuganRegles.Reglages.DEFAUT;
        if (s == null) {
            reglages = d;
        } else {
            reglages = new ByakuganRegles.Reglages(s.getBoolean("actif", d.actif()), s.getDouble("portee", d.portee()),
                s.getDouble("cout-pct-seconde", d.pctParSeconde()), s.getBoolean("yeux", d.yeux()),
                s.getBoolean("reprendre-sense", d.reprendreSense()), s.getDouble("signe-rayon", d.signeRayon()));
            List<String> l = s.getStringList("clans");
            if (!l.isEmpty()) clans = List.copyOf(l);
        }
        for (Player p : Bukkit.getOnlinePlayers()) regles(p);
    }

    public ByakuganRegles.Reglages reglages() { return reglages; }

    public boolean actif(UUID id) { return actifs.containsKey(id); }

    /* ================================================================ droits */

    /** Le personnage actif du joueur a-t-il le Byakugan (clan, puis yeux) ? */
    public boolean acces(Player p) {
        if (!reglages.actif()) return false;
        ShinobiCharacter c = plugin.characters() == null ? null : plugin.characters().getActive(p.getUniqueId());
        if (c == null || !ByakuganRegles.clanAutorise(c.clan(), clans)) return false;
        if (!reglages.yeux()) return true;
        YeuxService y = plugin.yeux();
        if (y == null) return true;
        Oeil g = y.oeil(c, Cote.GAUCHE), d = y.oeil(c, Cote.DROIT);
        int seuil = y.seuilCecite();
        return ByakuganRegles.yeuxPermettent(g.type(), g.aveugle(seuil), d.type(), d.aveugle(seuil), YeuxService.typeNatif(c));
    }

    /** Pourquoi le Byakugan est refusé maintenant (null = permis) ; {@code staff} : vision de test (le cadre seul compte). */
    private String refus(Player p, boolean staff) {
        UUID id = p.getUniqueId();
        var ko = plugin.ko() != null ? plugin.ko().getKo(id) : null;
        var o = plugin.outilsStaff();
        boolean modeStaff = o != null && o.mode().horsJeu(id);
        boolean selection = plugin.characterSelect() != null && plugin.characterSelect().isSelecting(id);
        boolean sansPerso = plugin.characters() != null && plugin.characters().getActive(id) == null;
        ByakuganRegles.Contexte c = new ByakuganRegles.Contexte(
            Verrous.mort(id),
            p.isDead() || p.getHealth() <= 0 || (ko != null && ko.isDowned()),
            ko != null && !ko.isDowned(),
            p.getGameMode() == GameMode.SPECTATOR,
            Verrous.cinematique(id),
            plugin.douane() != null && plugin.douane().cadreDouane(id),
            ByakuganRegles.horsJeu(selection, staff, modeStaff, sansPerso),
            o != null && o.effets() != null && o.effets().enNoir(id),
            staff ? null : Verrous.refus(id, Capacite.TECHNIQUE));
        return ByakuganRegles.refus(c);
    }

    /* ================================================================ réception */

    @Override
    public void onPluginMessageReceived(String channel, Player p, byte[] bytes) {
        if (!CANAL.equals(channel) || bytes.length > 256) return;
        String raw = new String(bytes, StandardCharsets.UTF_8);
        Bukkit.getScheduler().runTask(plugin, () -> recevoir(p, raw));
    }

    private void recevoir(Player p, String raw) {
        if (!p.isOnline()) return;
        boolean on;
        try {
            JsonObject in = JsonParser.parseString(raw).getAsJsonObject();
            if (!in.has("v") || in.get("v").getAsInt() != VERSION) return;
            if (!"byakugan".equals(in.has("a") ? in.get("a").getAsString() : "")) return;
            on = in.has("on") && in.get("on").getAsBoolean();
        } catch (RuntimeException e) { return; }
        if (on) activer(p, false);
        else desactiver(p, null);
    }

    /** Active la vision ; {@code staff} : vision de test (sans clan ni coût). Répond toujours au mod. */
    public void activer(Player p, boolean staff) {
        UUID id = p.getUniqueId();
        if (!bascules.essayer(id, System.currentTimeMillis())) { etat(p, actifs.containsKey(id), actifs.containsKey(id) ? null : "Trop vite."); return; }
        if (actifs.containsKey(id)) { etat(p, true, null); return; }
        if (!staff && !acces(p)) { etat(p, false, "Tu n'as pas le Byakugan."); return; }
        String r = refus(p, staff);
        if (r != null) { etat(p, false, r); return; }
        if (!staff && !payer(p)) { etat(p, false, "Ton chakra est épuisé."); return; }
        actifs.put(id, staff);
        etat(p, true, null);
    }

    /** Arrête la vision ; {@code raison} null : arrêt voulu par le joueur. */
    public void desactiver(Player p, String raison) {
        boolean etait = actifs.remove(p.getUniqueId()) != null;
        if (etait || raison == null) etat(p, false, raison);
    }

    /** Une demi-seconde de vision ; faux si la réserve ne la paie plus. */
    private boolean payer(Player p) {
        if (reglages.pctParSeconde() <= 0) return true;
        ShinobiCharacter c = plugin.characters() == null ? null : plugin.characters().getActive(p.getUniqueId());
        if (c == null) return false;
        var pool = c.chakra();
        double cout = ByakuganRegles.coutPeriode(pool.max(), reglages.pctParSeconde(), PERIODE_MS);
        return cout <= 0 || pool.consume(cout);
    }

    /* ================================================================ tick */

    private void tick() {
        if (!actifs.isEmpty()) {
            for (UUID id : Set.copyOf(actifs.keySet())) {
                Player p = Bukkit.getPlayer(id);
                if (p == null) { actifs.remove(id); continue; }
                boolean staff = actifs.get(id);
                String r = refus(p, staff);
                if (r == null && !staff && !acces(p)) r = "Tu n'as pas le Byakugan.";
                if (r == null && !staff && !payer(p)) r = "Ton chakra est épuisé.";
                if (r != null) desactiver(p, r);
            }
        }
        signes();
    }

    /** Les porteurs proches de chaque joueur (yeux pâles chez lui) ; envoyés seulement quand la liste change. */
    private void signes() {
        double r = reglages.signeRayon();
        if ((r <= 0 || actifs.isEmpty()) && signesEnvoyes.isEmpty()) return;   // personne à montrer, rien à effacer
        double r2 = r * r;
        for (Player vu : Bukkit.getOnlinePlayers()) {
            if (!vu.getListeningPluginChannels().contains(CANAL)) continue;
            List<Integer> ids = new ArrayList<>(2);
            if (r > 0) {
                for (UUID id : actifs.keySet()) {
                    if (id.equals(vu.getUniqueId())) continue;
                    Player porteur = Bukkit.getPlayer(id);
                    if (porteur == null || porteur.getWorld() != vu.getWorld() || !vu.canSee(porteur)) continue;
                    if (porteur.getLocation().distanceSquared(vu.getLocation()) <= r2) ids.add(porteur.getEntityId());
                }
            }
            ids.sort(Integer::compare);
            String sig = ids.toString();
            String avant = signesEnvoyes.get(vu.getUniqueId());
            if (sig.equals(avant) || (avant == null && ids.isEmpty())) continue;
            // liste vide envoyée une fois, puis oubliée : le tour suivant n'a plus rien à faire pour ce joueur
            if (ids.isEmpty()) signesEnvoyes.remove(vu.getUniqueId());
            else signesEnvoyes.put(vu.getUniqueId(), sig);
            JsonObject o = base("signes");
            JsonArray a = new JsonArray();
            for (int i : ids) a.add(i);
            o.add("ids", a);
            envoyer(vu, o);
        }
    }

    /* ================================================================ envoi */

    private void regles(Player p) {
        JsonObject o = base("regles");
        o.addProperty("byakugan", acces(p));
        o.addProperty("portee", reglages.portee());
        envoyer(p, o);
    }

    private void etat(Player p, boolean on, String raison) {
        JsonObject o = base("byakugan");
        o.addProperty("on", on);
        if (on) o.addProperty("portee", reglages.portee());
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
        if (!plugin.isEnabled() || !p.isOnline() || !p.getListeningPluginChannels().contains(CANAL)) return;
        p.sendPluginMessage(plugin, CANAL, o.toString().getBytes(StandardCharsets.UTF_8));
    }

    /* ================================================================ événements */

    @EventHandler
    public void connexion(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // le client annonce ses canaux juste après la connexion : on attend qu'il écoute celui-ci
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) regles(p); }, 40L);
    }

    @EventHandler
    public void quitter(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        actifs.remove(id);
        signesEnvoyes.remove(id);
        bascules.oublier(id);
    }

    @EventHandler
    public void personnage(CharacterSwitchEvent e) {
        Player p = e.player();
        if (actifs.remove(p.getUniqueId()) != null) etat(p, false, "Changement de personnage.");
        Bukkit.getScheduler().runTask(plugin, () -> { if (p.isOnline()) regles(p); });
    }

    /** KO, mort, cinématique, entrave… : relu au tick même du changement d'état, la vision se coupe net. */
    @EventHandler
    public void etatChange(EtatChangedEvent e) {
        Player p = e.player();
        Boolean staff = actifs.get(p.getUniqueId());
        if (staff == null) return;
        String r = refus(p, staff);
        if (r != null) desactiver(p, r);
    }

    @EventHandler
    public void mort(PlayerDeathEvent e) {
        if (actifs.containsKey(e.getPlayer().getUniqueId())) desactiver(e.getPlayer(), "Tu es à terre.");
    }

    @EventHandler
    public void modeDeJeu(PlayerGameModeChangeEvent e) {
        if (e.getNewGameMode() == GameMode.SPECTATOR && actifs.containsKey(e.getPlayer().getUniqueId())) {
            desactiver(e.getPlayer(), "Impossible en spectateur.");
        }
    }

    @EventHandler
    public void monde(PlayerChangedWorldEvent e) {
        signesEnvoyes.remove(e.getPlayer().getUniqueId());
    }

    /**
     * Reprise de {@code /sense dojutsu} (clans du Byakugan) et de {@code /sense byakugan} (staff), avant ShinobiSense : la
     * même vision que la touche O, au lieu de l'ancienne (voir la note de classe). Le joueur sans le mod reçoit une phrase.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void commande(PlayerCommandPreprocessEvent e) {
        if (!reglages.reprendreSense()) return;
        ByakuganRegles.Reprise r = ByakuganRegles.reprise(e.getMessage());
        if (r == ByakuganRegles.Reprise.AUCUNE) return;
        Player p = e.getPlayer();
        boolean staff = r == ByakuganRegles.Reprise.STAFF;
        if (staff) {
            if (!p.hasPermission("shinobisense.dojutsu.any")) return;   // ShinobiSense répond lui-même
        } else {
            ShinobiCharacter c = plugin.characters() == null ? null : plugin.characters().getActive(p.getUniqueId());
            if (c == null || !ByakuganRegles.clanAutorise(c.clan(), clans)) return;   // Sharingan et autres : ShinobiSense
        }
        e.setCancelled(true);
        if (!p.getListeningPluginChannels().contains(CANAL)) {
            p.sendActionBar(Component.text("Le Byakugan demande le mod Reborn (touche O).", NamedTextColor.GRAY));
            return;
        }
        if (actifs.containsKey(p.getUniqueId())) desactiver(p, null);
        else activer(p, staff);
    }

    /** Banc et diagnostic : nombre de porteurs actifs. */
    public int nombreActifs() { return actifs.size(); }

    /** Pour les tests de fumée : copie des porteurs. */
    Set<UUID> porteurs() { return new HashSet<>(actifs.keySet()); }
}
