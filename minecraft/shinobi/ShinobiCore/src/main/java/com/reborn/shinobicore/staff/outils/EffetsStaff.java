package com.reborn.shinobicore.staff.outils;

import com.google.gson.JsonObject;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.journal.Journal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Effets staff vus par les joueurs : <b>annonce</b> globale ou personnelle ({@code reborn:annonce}), <b>écran noir</b>
 * forcé et <b>séisme</b> ({@code reborn:ecran}). Protocole : PLUGINS_REFERENCE §4 bis.3-4. Sans le mod (le client
 * n'écoute pas le canal), repli vanilla : titres, Cécité + Obscurité, son.
 */
public final class EffetsStaff implements Listener {

    public static final String CANAL_ANNONCE = "reborn:annonce";
    public static final String CANAL_ECRAN = "reborn:ecran";
    public static final String SEISME_DEFAUT = "La terre tremble sous vos pieds, le monde en est affecté.";

    private record Noir(String texte, long fin, String par, BukkitTask tache) { }

    private final ShinobiCore plugin;
    private final Map<UUID, Noir> noirs = new ConcurrentHashMap<>();

    public EffetsStaff(ShinobiCore plugin) {
        this.plugin = plugin;
        var m = Bukkit.getMessenger();
        for (String c : new String[]{CANAL_ANNONCE, CANAL_ECRAN}) {
            if (!m.isOutgoingChannelRegistered(plugin, c)) m.registerOutgoingPluginChannel(plugin, c);
        }
    }

    private static boolean ecoute(Player p, String canal) { return p.getListeningPluginChannels().contains(canal); }

    private void envoyer(Player p, String canal, JsonObject o) {
        p.sendPluginMessage(plugin, canal, o.toString().getBytes(StandardCharsets.UTF_8));
    }

    /* ================================================================ annonces */

    /** Annonce à tous. {@code titre} facultatif ; {@code secondes} d'affichage. */
    public void annonceGlobale(CommandSender staff, String titre, String texte, int secondes) {
        int n = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            annoncer(p, "global", titre, texte, secondes);
            n++;
        }
        Journal.entree(Categorie.STAFF, "ANNONCE_GLOBALE").acteur(staff)
                .detail((titre == null || titre.isBlank() ? "" : "« " + titre + " » ") + texte + " · " + n + " joueur(s)")
                .ecrire();
    }

    public void annoncePerso(CommandSender staff, Player cible, String texte, int secondes) {
        annoncer(cible, "perso", null, texte, secondes);
        Journal.entree(Categorie.STAFF, "ANNONCE_PERSO").acteur(staff).cible(cible).detail(texte).ecrire();
    }

    private void annoncer(Player p, String portee, String titre, String texte, int secondes) {
        int s = Math.max(2, Math.min(60, secondes <= 0 ? 8 : secondes));
        if (ecoute(p, CANAL_ANNONCE)) {
            JsonObject o = new JsonObject();
            o.addProperty("t", "annonce");
            o.addProperty("portee", portee);
            o.addProperty("titre", titre == null ? "" : titre);
            o.addProperty("texte", texte);
            o.addProperty("s", s);
            o.addProperty("son", true);
            envoyer(p, CANAL_ANNONCE, o);
            return;
        }
        Title.Times times = Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(s), Duration.ofMillis(800));
        if ("global".equals(portee)) {
            p.showTitle(Title.title(Component.text(titre == null || titre.isBlank() ? "Annonce" : titre, NamedTextColor.GOLD),
                    Component.text(texte, NamedTextColor.WHITE), times));
        } else {
            p.showTitle(Title.title(Component.empty(), Component.text(texte, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, true), times));
            p.sendMessage(Component.text(texte, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true));
        }
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 0.8f);
    }

    /* ================================================================ écran noir */

    public boolean enNoir(UUID id) { return id != null && noirs.containsKey(id); }

    /** Écran noir : {@code secondes} 0 = jusqu'à {@link #finNoir}. */
    public void noir(CommandSender staff, Player cible, String texte, int secondes) {
        Noir old = noirs.remove(cible.getUniqueId());
        if (old != null && old.tache() != null) old.tache().cancel();
        BukkitTask t = secondes > 0 ? Bukkit.getScheduler().runTaskLater(plugin, () -> finNoir(null, cible.getUniqueId()),
                secondes * 20L) : null;
        noirs.put(cible.getUniqueId(), new Noir(texte == null ? "" : texte,
                secondes > 0 ? System.currentTimeMillis() + secondes * 1000L : 0, staff.getName(), t));
        appliquerNoir(cible, texte == null ? "" : texte, secondes);
        Journal.entree(Categorie.STAFF, "ECRAN_NOIR").acteur(staff).cible(cible)
                .detail((secondes > 0 ? secondes + " s" : "jusqu'à levée") + (texte == null || texte.isBlank() ? "" : " · « " + texte + " »"))
                .ecrire();
    }

    /** Lève l'écran noir ({@code staff} null = fin du minuteur). */
    public boolean finNoir(CommandSender staff, UUID cible) {
        Noir n = noirs.remove(cible);
        if (n == null) return false;
        if (n.tache() != null && staff != null) n.tache().cancel();
        Player p = Bukkit.getPlayer(cible);
        if (p != null) {
            if (ecoute(p, CANAL_ECRAN)) {
                JsonObject o = new JsonObject();
                o.addProperty("t", "noir");
                o.addProperty("on", false);
                o.addProperty("fondu", 20);
                envoyer(p, CANAL_ECRAN, o);
            }
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.removePotionEffect(PotionEffectType.DARKNESS);
            p.clearTitle();
        }
        var e = Journal.entree(Categorie.STAFF, "ECRAN_NOIR_FIN").cible(cible);
        if (staff != null) e.acteur(staff); else e.detail("fin du minuteur (posé par " + n.par() + ")");
        e.ecrire();
        return true;
    }

    private void appliquerNoir(Player p, String texte, int secondes) {
        if (ecoute(p, CANAL_ECRAN)) {
            JsonObject o = new JsonObject();
            o.addProperty("t", "noir");
            o.addProperty("on", true);
            o.addProperty("texte", texte);
            o.addProperty("fondu", 20);
            o.addProperty("s", Math.max(0, secondes));
            envoyer(p, CANAL_ECRAN, o);
            return;
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        if (!texte.isEmpty()) {
            p.showTitle(Title.title(Component.empty(), Component.text(texte, NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(800), Duration.ofSeconds(Math.max(3, Math.min(30, secondes > 0 ? secondes : 6))),
                            Duration.ofMillis(800))));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent ev) {
        Player p = ev.getPlayer();
        Noir n = noirs.get(p.getUniqueId());
        if (n == null) return;
        int reste = n.fin() <= 0 ? 0 : (int) Math.max(1, (n.fin() - System.currentTimeMillis()) / 1000L);
        // Le mod n'a déclaré ses canaux qu'après quelques ticks.
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline() && noirs.containsKey(p.getUniqueId())) appliquerNoir(p, n.texte(), reste); }, 40L);
    }

    /* ================================================================ séisme */

    /**
     * Séisme : {@code rayon} 0 = tout le serveur (et annonce globale), sinon les joueurs du monde du centre à moins de
     * {@code rayon} blocs. {@code force} 1-10, {@code secondes} de secousse.
     */
    public int seisme(CommandSender staff, Location centre, int rayon, int force, int secondes, String message) {
        int f = Math.max(1, Math.min(10, force));
        int s = Math.max(1, Math.min(30, secondes));
        List<Player> touches = new ArrayList<>();
        Collection<? extends Player> en = Bukkit.getOnlinePlayers();
        for (Player p : en) {
            if (rayon > 0) {
                if (centre == null || !p.getWorld().equals(centre.getWorld())
                        || p.getLocation().distanceSquared(centre) > (double) rayon * rayon) continue;
            }
            touches.add(p);
        }
        for (Player p : touches) secouer(p, f, s);
        String msg = message == null || message.isBlank() ? (rayon <= 0 ? SEISME_DEFAUT : "") : message;
        if (!msg.isEmpty()) for (Player p : touches) annoncer(p, rayon <= 0 ? "global" : "perso", null, msg, Math.max(5, s + 2));
        Journal.entree(Categorie.STAFF, "SEISME").acteur(staff).lieu(centre)
                .detail((rayon <= 0 ? "tout le serveur" : "rayon " + rayon) + " · force " + f + " · " + s + " s · "
                        + touches.size() + " joueur(s)" + (msg.isEmpty() ? "" : " · « " + msg + " »"))
                .ecrire();
        return touches.size();
    }

    /** Séisme sur un seul joueur (bouton du Poste de garde). */
    public void secouerUn(CommandSender staff, Player cible, int force, int secondes) {
        secouer(cible, Math.max(1, Math.min(10, force)), Math.max(1, Math.min(30, secondes)));
        Journal.entree(Categorie.STAFF, "SEISME").acteur(staff).cible(cible).lieu(cible.getLocation())
                .detail("joueur seul · force " + force + " · " + secondes + " s").ecrire();
    }

    private void secouer(Player p, int force, int secondes) {
        if (ecoute(p, CANAL_ECRAN)) {
            JsonObject o = new JsonObject();
            o.addProperty("t", "seisme");
            o.addProperty("f", force);
            o.addProperty("s", secondes);
            o.addProperty("son", true);
            envoyer(p, CANAL_ECRAN, o);
        } else {
            p.sendActionBar(Component.text("Le sol tremble…", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true));
        }
        float vol = 0.25f + force * 0.06f;
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, vol, 0.5f);
        int coups = Math.max(1, secondes * 2);
        for (int i = 1; i < coups; i++) {
            final float v = vol * (1f - (float) i / coups);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) p.playSound(p.getLocation(), Sound.BLOCK_GRAVEL_BREAK, Math.max(0.1f, v), 0.5f);
            }, i * 10L);
        }
    }

    public void arreter() {
        for (UUID id : List.copyOf(noirs.keySet())) finNoir(null, id);
    }
}
