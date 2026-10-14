package com.reborn.shinobicore.duel;

import com.google.gson.JsonObject;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.chakra.ChakraPool;
import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Duel consenti n'importe où (décision du 2026-10-13, docs/gameplay/KO_ET_MORT_RP.md §4.1) : un « terrain
 * d'entraînement portatif » à deux.
 *
 * <ol>
 *   <li><b>Proposition</b> ({@code /duel proposer <joueur>}, ou le menu d'interaction du mod) : la cible reçoit une
 *       invitation (plaque du mod, ou lignes cliquables au tchat) qui expire après {@code duel.expiration-secondes}.
 *       Anti-spam : un délai entre deux propositions d'un même joueur, un autre par couple.</li>
 *   <li><b>Acceptation</b> : PV et chakra des deux sont mémorisés, compte à rebours (3-2-1) pendant lequel ils ne
 *       peuvent pas se toucher, puis combat.</li>
 *   <li><b>Combat</b> : comme sur un terrain d'entraînement, PV ou chakra à 0 = « défaite » — ni KO, ni blessure,
 *       ni journal d'agression, ni XP de combat.</li>
 *   <li><b>Fin</b> : défaite, abandon, éloignement, durée maximale, déconnexion, changement de personnage,
 *       téléportation lointaine, KO ou mort RP en cours, ou <b>intervention d'un tiers</b> (un joueur qui frappe un
 *       duelliste, ou un duelliste qui frappe un tiers : le duel est annulé et ce coup-là suit les règles normales).
 *       Les deux retrouvent exactement les PV et le chakra d'avant le duel ; 3 s pendant lesquelles ils ne peuvent
 *       plus se toucher (coups en vol, combo).</li>
 * </ol>
 *
 * <p>Canal {@code reborn:duel} (S2C, JSON UTF-8 brut, champs optionnels — contrat miroir de
 * {@code fr.reborn.hud.duel.DuelClient}) :
 * <ul>
 *   <li>{@code {"t":"invit","de":pseudo,"nom":nom affiché,"s":secondes}} / {@code {"t":"invit_fin","de":pseudo}} ;</li>
 *   <li>{@code {"t":"attente","a":pseudo,"nom":…,"s":…}} / {@code {"t":"attente_fin"}} (côté proposant) ;</li>
 *   <li>{@code {"t":"debut","adv":nom,"advId":pseudo,"cr":3,"duree":300,"tech":bool}} ;</li>
 *   <li>{@code {"t":"etat","d":distance,"max":distance max,"reste":secondes}} (chaque seconde) ;</li>
 *   <li>{@code {"t":"fin","issue":"victoire"|"defaite"|"nul"|"annule","adv":nom,"raison":texte}}.</li>
 * </ul>
 * Un client sans mod reçoit des lignes de tchat cliquables et des titres vanilla.
 */
public final class DuelService implements Listener {

    public static final String CHANNEL = "reborn:duel";

    /** Valeurs mémorisées à l'acceptation, liées au personnage actif à ce moment-là. */
    private record Snapshot(UUID characterId, double hp, double chakra, double chakraDebt) { }

    /** Proposition en attente : {@code from} → {@code to}. */
    private record Invite(UUID from, UUID to, long expiresAt) { }

    private enum Phase { COMPTE, COMBAT }

    /** Un duel en cours (une seule instance partagée par les deux joueurs). */
    private static final class Duel {
        final UUID a, b;
        final Map<UUID, Snapshot> snaps = new HashMap<>();
        final long startedAt;
        Phase phase = Phase.COMPTE;
        long fightAt;
        boolean ended;
        Duel(UUID a, UUID b, long now) { this.a = a; this.b = b; this.startedAt = now; }
        UUID other(UUID id) { return id.equals(a) ? b : a; }
    }

    private final ShinobiCore plugin;
    private final File logFile;
    /** Invitations reçues, par destinataire (une seule à la fois par couple). */
    private final Map<UUID, List<Invite>> invites = new ConcurrentHashMap<>();
    private final Map<UUID, Duel> duels = new ConcurrentHashMap<>();
    /** Fin récente : couple → instant jusqu'auquel les deux ne peuvent plus se toucher. */
    private final Map<String, Long> grace = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastProposal = new ConcurrentHashMap<>();
    private final Map<String, Long> pairCooldown = new ConcurrentHashMap<>();
    private BukkitTask ticker;

    public DuelService(ShinobiCore plugin) {
        this.plugin = plugin;
        this.logFile = new File(plugin.getDataFolder(), "duel-log.txt");
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL)) m.registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    /* ============================================================ réglages (config.yml, duel.*) */

    private boolean enabled() { return plugin.getConfig().getBoolean("duel.actif", true); }

    private double maxDistance() { return Math.max(5, plugin.getConfig().getDouble("duel.distance-max", 30)); }

    private double proposalDistance() { return Math.max(2, plugin.getConfig().getDouble("duel.distance-proposition", 8)); }

    private long maxDurationMillis() { return Math.max(30, plugin.getConfig().getLong("duel.duree-max-secondes", 300)) * 1000L; }

    private long expiryMillis() { return Math.max(5, plugin.getConfig().getLong("duel.expiration-secondes", 30)) * 1000L; }

    private long cooldownMillis() { return Math.max(0, plugin.getConfig().getLong("duel.cooldown-secondes", 10)) * 1000L; }

    private long pairCooldownMillis() { return Math.max(0, plugin.getConfig().getLong("duel.cooldown-couple-secondes", 60)) * 1000L; }

    private int countdown() { return Math.max(0, Math.min(10, plugin.getConfig().getInt("duel.compte-a-rebours", 3))); }

    private boolean techniquesAllowedCfg() { return plugin.getConfig().getBoolean("duel.techniques", true); }

    private boolean announce() { return plugin.getConfig().getBoolean("duel.annonce.actif", true); }

    private double announceRadius() { return Math.max(0, plugin.getConfig().getDouble("duel.annonce.rayon", 24)); }

    private double teleportMax() { return Math.max(1, plugin.getConfig().getDouble("duel.teleport-max-blocs", 16)); }

    private long graceMillis() { return Math.max(0, plugin.getConfig().getLong("duel.grace-secondes", 3)) * 1000L; }

    /* ============================================================ cycle de vie */

    public void start() {
        if (ticker != null) ticker.cancel();
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    /** Arrêt du serveur : chacun retrouve son état d'avant le duel (avant le flush des personnages). */
    public void stop() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        for (Duel d : new ArrayList<>(duels.values())) end(d, null, Issue.ANNULE, "arrêt du serveur", false);
        invites.clear();
    }

    /* ============================================================ API pour les autres systèmes */

    /** Le joueur est-il engagé dans un duel (compte à rebours compris) ? */
    public boolean inDuel(UUID id) { return id != null && duels.containsKey(id); }

    /** Les deux joueurs sont-ils en duel l'un contre l'autre ? */
    public boolean together(UUID x, UUID y) {
        if (x == null || y == null) return false;
        Duel d = duels.get(x);
        return d != null && d.other(x).equals(y);
    }

    /** Le joueur peut-il lancer une technique (faux pendant le compte à rebours, ou si le duel les interdit) ? */
    public boolean techniquesAllowed(UUID id) {
        Duel d = duels.get(id);
        if (d == null) return true;
        return d.phase == Phase.COMBAT && techniquesAllowedCfg();
    }

    /** Raison du refus d'une technique, pour la barre d'action (null = autorisée). */
    public String techniqueDenial(UUID id) {
        Duel d = duels.get(id);
        if (d == null) return null;
        if (d.phase == Phase.COMPTE) return "Attends le signal du duel.";
        return techniquesAllowedCfg() ? null : "Les techniques sont interdites en duel.";
    }

    /** PV à écrire sur le personnage lors d'une sauvegarde : ceux d'avant le duel s'il est en cours. */
    public Double hpToPersist(Player p, ShinobiCharacter c) {
        Duel d = duels.get(p.getUniqueId());
        if (d == null || c == null) return null;
        Snapshot s = d.snaps.get(p.getUniqueId());
        return s != null && s.characterId().equals(c.id()) ? s.hp() : null;
    }

    /**
     * Coup mortel reçu (appelé par KoListener avant le KO) : en duel, c'est une défaite — le duel se termine, les
     * deux retrouvent leur état d'avant. Renvoie true si le coup a été absorbé.
     */
    public boolean onLethal(Player p) {
        Duel d = duels.get(p.getUniqueId());
        if (d == null || d.ended) return false;
        Player other = Bukkit.getPlayer(d.other(p.getUniqueId()));
        end(d, other, Issue.VICTOIRE, "à bout de forces", true);
        return true;
    }

    /** Chakra à 0 pendant un duel (appelé par KoManager) : défaite, pas d'épuisement KO. */
    public boolean onChakraOut(Player p) {
        Duel d = duels.get(p.getUniqueId());
        if (d == null || d.ended) return false;
        Player other = Bukkit.getPlayer(d.other(p.getUniqueId()));
        end(d, other, Issue.VICTOIRE, "chakra épuisé", true);
        return true;
    }

    /** Changement de personnage : le duel s'arrête (forfait) avant la sauvegarde du personnage quitté. */
    public void release(Player p, String why) {
        Duel d = duels.get(p.getUniqueId());
        if (d != null) end(d, Bukkit.getPlayer(d.other(p.getUniqueId())), Issue.VICTOIRE, why, false);
        dropInvitesOf(p.getUniqueId());
    }

    /* ============================================================ proposition */

    /** Résultat d'une commande, à afficher au joueur. */
    public void propose(Player from, Player to) {
        if (!enabled()) { err(from, "Les duels sont désactivés."); return; }
        if (to == null || !to.isOnline()) { err(from, "Ce joueur n'est pas là."); return; }
        if (to.equals(from)) { err(from, "Tu ne peux pas te provoquer toi-même."); return; }
        String why = unavailable(from, true);
        if (why != null) { err(from, why); return; }
        String whyTo = unavailable(to, false);
        if (whyTo != null) { err(from, whyTo); return; }
        if (!near(from, to, proposalDistance())) {
            err(from, "Approche-toi pour proposer un duel (" + (int) proposalDistance() + " blocs au plus).");
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastProposal.get(from.getUniqueId());
        if (last != null && now - last < cooldownMillis()) {
            err(from, "Attends encore " + secs(cooldownMillis() - (now - last)) + " s avant une autre proposition.");
            return;
        }
        Long pair = pairCooldown.get(pairKey(from.getUniqueId(), to.getUniqueId()));
        if (pair != null && now < pair) {
            err(from, "Laisse-lui un peu de temps (" + secs(pair - now) + " s) avant de reproposer.");
            return;
        }
        if (findInvite(to.getUniqueId(), from.getUniqueId()) != null) { err(from, "Ta proposition attend déjà sa réponse."); return; }
        // Proposition croisée : l'autre m'a déjà proposé → c'est une acceptation.
        if (findInvite(from.getUniqueId(), to.getUniqueId()) != null) { accept(from, to); return; }

        Invite inv = new Invite(from.getUniqueId(), to.getUniqueId(), now + expiryMillis());
        invites.computeIfAbsent(to.getUniqueId(), k -> new ArrayList<>()).add(inv);
        lastProposal.put(from.getUniqueId(), now);
        pairCooldown.put(pairKey(from.getUniqueId(), to.getUniqueId()), now + pairCooldownMillis());

        int s = (int) (expiryMillis() / 1000L);
        String toName = nameFor(to, from), fromName = nameFor(from, to);
        if (modded(to)) {
            JsonObject o = obj("invit");
            o.addProperty("de", from.getName());
            o.addProperty("nom", fromName);
            o.addProperty("s", s);
            send(to, o);
            to.playSound(to.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 0.8f);
        } else {
            to.sendMessage(Component.text("⚔ ", NamedTextColor.GOLD)
                    .append(Component.text(fromName, NamedTextColor.YELLOW))
                    .append(Component.text(" te propose un duel (sans séquelles). ", NamedTextColor.GRAY))
                    .append(button("[Accepter]", NamedTextColor.GREEN, "/duel accepter " + from.getName(),
                            "Accepter le duel"))
                    .append(Component.text(" "))
                    .append(button("[Refuser]", NamedTextColor.RED, "/duel refuser " + from.getName(),
                            "Refuser le duel")));
        }
        if (modded(from)) {
            JsonObject o = obj("attente");
            o.addProperty("a", to.getName());
            o.addProperty("nom", toName);
            o.addProperty("s", s);
            send(from, o);
        }
        from.sendMessage(Component.text("⚔ Duel proposé à " + toName + ". Il a " + s + " s pour répondre.",
                NamedTextColor.GRAY));
    }

    /** {@code to} accepte la proposition de {@code from} (null = la plus récente). */
    public void accept(Player to, Player from) {
        Invite inv = from != null ? findInvite(to.getUniqueId(), from.getUniqueId()) : latestInvite(to.getUniqueId());
        if (inv == null) { err(to, "Aucune proposition de duel en attente."); return; }
        removeInvite(inv);
        Player proposer = Bukkit.getPlayer(inv.from());
        if (proposer == null || !proposer.isOnline()) { err(to, "Ce joueur est parti."); notifyInviteGone(to, inv); return; }
        notifyInviteGone(to, inv);
        String why = unavailable(to, true);
        if (why != null) { err(to, why); cancelWaiting(proposer, null); return; }
        String whyFrom = unavailable(proposer, true);
        if (whyFrom != null) { err(to, nameFor(proposer, to) + " n'est plus disponible."); cancelWaiting(proposer, null); return; }
        if (!near(proposer, to, proposalDistance() * 1.5)) {
            err(to, "Vous êtes trop loin l'un de l'autre pour commencer.");
            cancelWaiting(proposer, "trop loin pour commencer");
            return;
        }
        begin(proposer, to);
    }

    public void refuse(Player to, Player from) {
        Invite inv = from != null ? findInvite(to.getUniqueId(), from.getUniqueId()) : latestInvite(to.getUniqueId());
        if (inv == null) { err(to, "Aucune proposition de duel en attente."); return; }
        removeInvite(inv);
        notifyInviteGone(to, inv);
        Player proposer = Bukkit.getPlayer(inv.from());
        to.sendMessage(Component.text("Duel refusé.", NamedTextColor.GRAY));
        if (proposer != null) {
            cancelWaiting(proposer, null);
            proposer.sendMessage(Component.text("⚔ " + nameFor(to, proposer) + " refuse le duel.", NamedTextColor.GRAY));
        }
    }

    /** Abandon volontaire (commande ou bouton du mod). */
    public void forfeit(Player p) {
        Duel d = duels.get(p.getUniqueId());
        if (d == null) { err(p, "Tu n'es pas en duel."); return; }
        end(d, Bukkit.getPlayer(d.other(p.getUniqueId())), Issue.VICTOIRE, "abandon", true);
    }

    /** Pourquoi ce joueur ne peut pas se battre en duel maintenant (null = disponible). */
    private String unavailable(Player p, boolean self) {
        UUID id = p.getUniqueId();
        String who = self ? "Tu " : p.getName() + " ";
        ShinobiCharacter c = plugin.characters().getActive(id);
        if (c == null || c.dead()) return self ? "Il te faut un personnage actif." : "Ce joueur n'incarne aucun personnage.";
        if (plugin.isStaffBuilding(id)) return self ? "Pas pendant le mode construction." : "Ce joueur est en mode construction.";
        if (duels.containsKey(id)) return who + (self ? "es déjà en duel." : "est déjà en duel.");
        if (plugin.ko() != null && plugin.ko().isKo(id)) return self ? "Tu es à terre." : "Ce joueur est à terre.";
        if (plugin.ko() != null && plugin.ko().isImpaired(id))
            return self ? "Trop blessé pour un duel (ATA)." : "Ce joueur est trop blessé pour un duel.";
        // Correctifs urgents (ETATS_JOUEUR §2.4 n° 9) : gel, mode staff, scène de mort, porté / tenu, cinématique.
        if (com.reborn.shinobicore.etat.Verrous.horsJeu(id)) return self ? "Pas en mode staff." : "Personne de ce nom ici.";
        String corps = com.reborn.shinobicore.etat.Verrous.corpsIndisponible(id);
        if (corps != null) return self ? corps : "Ce joueur ne peut pas se battre maintenant.";
        if (com.reborn.shinobicore.etat.Verrous.porteur(id))
            return self ? "Dépose d'abord la personne que tu portes." : "Ce joueur porte quelqu'un.";
        if (plugin.trainingZones() != null && plugin.trainingZones().zoneAt(p.getLocation()) != null)
            return "Sur un terrain d'entraînement, le combat est déjà libre : pas besoin de duel.";
        return null;
    }

    /* ============================================================ déroulé */

    private void begin(Player a, Player b) {
        long now = System.currentTimeMillis();
        Duel d = new Duel(a.getUniqueId(), b.getUniqueId(), now);
        for (Player p : new Player[] { a, b }) {
            ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
            ChakraPool pool = c.chakra();
            d.snaps.put(p.getUniqueId(), new Snapshot(c.id(), p.getHealth(), pool.current(), pool.debt()));
            dropInvitesOf(p.getUniqueId());
        }
        duels.put(a.getUniqueId(), d);
        duels.put(b.getUniqueId(), d);
        pairCooldown.remove(pairKey(a.getUniqueId(), b.getUniqueId()));   // revanche possible juste après
        int cr = countdown();
        d.fightAt = now + cr * 1000L;
        if (cr == 0) d.phase = Phase.COMBAT;

        for (Player p : new Player[] { a, b }) {
            Player o = p == a ? b : a;
            cancelWaiting(p, null);
            if (modded(p)) {
                JsonObject j = obj("debut");
                j.addProperty("adv", nameFor(o, p));
                j.addProperty("advId", o.getName());
                j.addProperty("cr", cr);
                j.addProperty("duree", maxDurationMillis() / 1000L);
                j.addProperty("tech", techniquesAllowedCfg());
                send(p, j);
            } else {
                p.sendMessage(Component.text("⚔ Duel accepté contre " + nameFor(o, p)
                        + " — sans séquelles. ", NamedTextColor.GOLD)
                        .append(button("[Abandonner]", NamedTextColor.RED, "/duel abandonner", "Abandonner le duel")));
            }
        }
        // compte à rebours (titres vanilla pour les clients sans mod, sons pour tous)
        for (int i = 0; i <= cr; i++) {
            final int n = cr - i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> countdownStep(d, n), 20L * i);
        }
        if (announce()) announceNearby(a, b);
        log("DEBUT", a, b, "");
    }

    private void countdownStep(Duel d, int n) {
        if (d.ended) return;
        if (n == 0) d.phase = Phase.COMBAT;
        for (UUID id : new UUID[] { d.a, d.b }) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            p.playSound(p.getLocation(), n == 0 ? Sound.ITEM_TRIDENT_RETURN : Sound.BLOCK_NOTE_BLOCK_HAT,
                    0.8f, n == 0 ? 0.9f : 1.4f);
            if (!modded(p)) {
                p.showTitle(Title.title(
                        Component.text(n == 0 ? "Combat !" : String.valueOf(n), NamedTextColor.GOLD, TextDecoration.BOLD),
                        Component.text(n == 0 ? "Duel sans séquelles" : "Duel", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(800), Duration.ofMillis(200))));
            }
        }
    }

    private enum Issue { VICTOIRE, NUL, ANNULE }

    /**
     * Termine le duel. {@code winner} : le vainqueur pour {@link Issue#VICTOIRE} (l'autre a perdu ; null = le perdant
     * est le joueur absent). Restaure les deux, journalise, prévient.
     */
    private void end(Duel d, Player winner, Issue issue, String reason, boolean defeatEffects) {
        if (d.ended) return;
        d.ended = true;
        duels.remove(d.a, d);
        duels.remove(d.b, d);
        long now = System.currentTimeMillis();
        grace.put(pairKey(d.a, d.b), now + graceMillis());
        Player pa = Bukkit.getPlayer(d.a), pb = Bukkit.getPlayer(d.b);
        for (Player p : new Player[] { pa, pb }) if (p != null) restore(p, d.snaps.get(p.getUniqueId()));

        UUID winId = issue == Issue.VICTOIRE ? (winner != null ? winner.getUniqueId() : null) : null;
        if (issue == Issue.VICTOIRE && winId == null) {
            // vainqueur absent (déconnecté) : on ne peut pas dire qui gagne → simple annulation
            issue = Issue.ANNULE;
        }
        for (Player p : new Player[] { pa, pb }) {
            if (p == null || !p.isOnline()) continue;
            UUID oid = d.other(p.getUniqueId());
            Player o = Bukkit.getPlayer(oid);
            String advName = o != null ? nameFor(o, p) : "ton adversaire";
            String res;
            switch (issue) {
                case VICTOIRE -> res = p.getUniqueId().equals(winId) ? "victoire" : "defaite";
                case NUL -> res = "nul";
                default -> res = "annule";
            }
            if (modded(p)) {
                JsonObject j = obj("fin");
                j.addProperty("issue", res);
                j.addProperty("adv", advName);
                j.addProperty("raison", reason);
                send(p, j);
            } else {
                String title = switch (res) {
                    case "victoire" -> "Victoire";
                    case "defaite" -> "Défaite";
                    case "nul" -> "Match nul";
                    default -> "Duel annulé";
                };
                p.showTitle(Title.title(Component.text(title, NamedTextColor.GOLD, TextDecoration.BOLD),
                        Component.text(reason, NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1800), Duration.ofMillis(500))));
            }
            p.sendMessage(Component.text("⚔ Duel contre " + advName + " terminé : " + switch (res) {
                case "victoire" -> "victoire";
                case "defaite" -> "défaite";
                case "nul" -> "match nul";
                default -> "annulé";
            } + " (" + reason + "). PV et chakra d'avant le duel rendus.", NamedTextColor.GRAY));
            if (defeatEffects && "defaite".equals(res)) {
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.8f, 0.7f);
            } else if ("victoire".equals(res)) {
                p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 0.6f, 1.2f);
            }
        }
        String detail = issue + " · " + reason + (winId != null ? " · vainqueur " + name(winId) : "");
        log("FIN", pa, pb, detail);
    }

    private void restore(Player p, Snapshot s) {
        if (p == null || s == null) return;
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        if (c == null || !c.id().equals(s.characterId())) return;
        double hp = Math.max(0.5, Math.min(s.hp(), p.getMaxHealth()));
        p.setHealth(hp);
        c.setCurrentHp(hp);
        ChakraPool pool = c.chakra();
        pool.setCurrent(s.chakra());
        if (s.chakraDebt() > 0.0) pool.overdraw(s.chakraDebt());
        p.setFireTicks(0);
        p.removePotionEffect(PotionEffectType.POISON);
        p.removePotionEffect(PotionEffectType.WITHER);
    }

    /* ============================================================ ticker */

    private void tick() {
        long now = System.currentTimeMillis();
        // invitations expirées
        for (List<Invite> list : invites.values()) {
            for (Invite inv : new ArrayList<>(list)) {
                if (now < inv.expiresAt()) continue;
                removeInvite(inv);
                Player to = Bukkit.getPlayer(inv.to()), from = Bukkit.getPlayer(inv.from());
                if (to != null) notifyInviteGone(to, inv);
                if (from != null) {
                    cancelWaiting(from, null);
                    from.sendMessage(Component.text("⚔ Ta proposition de duel"
                            + (to != null ? " à " + nameFor(to, from) : "") + " a expiré.", NamedTextColor.GRAY));
                }
            }
        }
        invites.values().removeIf(List::isEmpty);
        grace.values().removeIf(t -> t < now);
        pairCooldown.values().removeIf(t -> t < now);

        boolean second = (now / 500L) % 2 == 0;
        for (Duel d : new ArrayList<>(new java.util.HashSet<>(duels.values()))) {
            if (d.ended) continue;
            Player a = Bukkit.getPlayer(d.a), b = Bukkit.getPlayer(d.b);
            if (a == null || b == null) { end(d, a != null ? a : b, Issue.VICTOIRE, "adversaire parti", false); continue; }
            String broken = brokenBy(a, d);
            if (broken == null) broken = brokenBy(b, d);
            if (broken != null) { end(d, null, Issue.ANNULE, broken, false); continue; }
            if (!a.getWorld().equals(b.getWorld())) { end(d, null, Issue.ANNULE, "trop loin l'un de l'autre", false); continue; }
            double dist = a.getLocation().distance(b.getLocation());
            if (dist > maxDistance()) { end(d, null, Issue.ANNULE, "trop loin l'un de l'autre", false); continue; }
            if (now - d.startedAt > maxDurationMillis() + countdown() * 1000L) {
                end(d, null, Issue.NUL, "temps écoulé", false);
                continue;
            }
            if (!second) continue;
            long left = Math.max(0, (d.startedAt + countdown() * 1000L + maxDurationMillis() - now) / 1000L);
            for (Player p : new Player[] { a, b }) {
                if (modded(p)) {
                    JsonObject j = obj("etat");
                    j.addProperty("d", Math.round(dist * 10) / 10.0);
                    j.addProperty("max", maxDistance());
                    j.addProperty("reste", left);
                    send(p, j);
                } else if (dist > maxDistance() * 0.8) {
                    p.sendActionBar(Component.text("Duel : tu t'éloignes de ton adversaire ("
                            + (int) dist + "/" + (int) maxDistance() + " blocs)", NamedTextColor.GOLD));
                }
            }
        }
    }

    /** Ce qui interdit à ce duelliste de continuer (KO, mort, personnage changé…), ou null. */
    private String brokenBy(Player p, Duel d) {
        UUID id = p.getUniqueId();
        ShinobiCharacter c = plugin.characters().getActive(id);
        Snapshot s = d.snaps.get(id);
        if (c == null || s == null || !c.id().equals(s.characterId())) return "changement de personnage";
        if (c.dead()) return "mort RP";
        if (plugin.ko() != null && plugin.ko().isKo(id)) return "KO";
        if (plugin.isStaffBuilding(id)) return "mode construction";
        return null;
    }

    /* ============================================================ événements */

    /**
     * Avant tout le reste (LOWEST) : compte à rebours = pas de coups entre les deux ; tiers qui frappe un duelliste
     * (ou duelliste qui frappe un tiers) = duel annulé, le coup suit alors les règles normales ; après la fin, quelques
     * secondes pendant lesquelles les deux anciens duellistes ne peuvent plus se toucher.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent ev) {
        if (!(ev.getEntity() instanceof Player victim)) return;
        Player attacker = attackerOf(ev.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        UUID v = victim.getUniqueId(), a = attacker.getUniqueId();
        Long g = grace.get(pairKey(v, a));
        if (g != null && System.currentTimeMillis() < g && !inDuel(v) && !inDuel(a)) {
            ev.setCancelled(true);
            return;
        }
        Duel dv = duels.get(v), da = duels.get(a);
        if (dv != null && dv == da) {
            if (dv.phase == Phase.COMPTE) ev.setCancelled(true);
            return;
        }
        // intervention d'un tiers : le ou les duels concernés sont annulés, ce coup suit les règles normales
        if (dv != null) end(dv, null, Issue.ANNULE, "intervention de " + attacker.getName(), false);
        if (da != null) end(da, null, Issue.ANNULE, attacker.getName() + " a frappé un tiers", false);
    }

    /** Déconnexion : forfait, et restauration AVANT la sauvegarde du personnage (CharacterLifecycleListener). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent ev) {
        Player p = ev.getPlayer();
        Duel d = duels.get(p.getUniqueId());
        if (d != null) end(d, Bukkit.getPlayer(d.other(p.getUniqueId())), Issue.VICTOIRE, "déconnexion de l'adversaire", false);
        dropInvitesOf(p.getUniqueId());
    }

    /** Téléportation lointaine ou changement de monde : le duel s'arrête (un shunshin court ne compte pas). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent ev) {
        Duel d = duels.get(ev.getPlayer().getUniqueId());
        if (d == null || ev.getTo() == null) return;
        Location f = ev.getFrom(), t = ev.getTo();
        if (!f.getWorld().equals(t.getWorld()) || f.distance(t) > teleportMax()) {
            end(d, null, Issue.ANNULE, "téléportation", false);
        }
    }

    /* ============================================================ invitations */

    private Invite findInvite(UUID to, UUID from) {
        List<Invite> l = invites.get(to);
        if (l == null) return null;
        for (Invite i : l) if (i.from().equals(from)) return i;
        return null;
    }

    private Invite latestInvite(UUID to) {
        List<Invite> l = invites.get(to);
        return l == null || l.isEmpty() ? null : l.get(l.size() - 1);
    }

    private void removeInvite(Invite inv) {
        List<Invite> l = invites.get(inv.to());
        if (l != null) l.remove(inv);
    }

    /** Retire toutes les invitations où ce joueur apparaît (départ, début de duel, changement de personnage). */
    private void dropInvitesOf(UUID id) {
        List<Invite> mine = invites.remove(id);
        if (mine != null) {
            Player me = Bukkit.getPlayer(id);
            for (Invite i : mine) {
                if (me != null) notifyInviteGone(me, i);
                Player from = Bukkit.getPlayer(i.from());
                if (from != null) cancelWaiting(from, null);
            }
        }
        for (List<Invite> l : invites.values()) {
            for (Invite i : new ArrayList<>(l)) {
                if (!i.from().equals(id)) continue;
                l.remove(i);
                Player to = Bukkit.getPlayer(i.to());
                if (to != null) notifyInviteGone(to, i);
            }
        }
    }

    private void notifyInviteGone(Player to, Invite inv) {
        if (!modded(to)) return;
        JsonObject o = obj("invit_fin");
        Player from = Bukkit.getPlayer(inv.from());
        if (from != null) o.addProperty("de", from.getName());
        send(to, o);
    }

    private void cancelWaiting(Player from, String why) {
        if (modded(from)) send(from, obj("attente_fin"));
        if (why != null) from.sendMessage(Component.text("⚔ Duel annulé : " + why + ".", NamedTextColor.GRAY));
    }

    /* ============================================================ annonce, journal */

    /** « X et Y s'affrontent en duel », en gris italique, aux joueurs proches (noms tels que chacun les connaît). */
    private void announceNearby(Player a, Player b) {
        double r = announceRadius();
        if (r <= 0) return;
        Location mid = a.getLocation().clone().add(b.getLocation()).multiply(0.5);
        double r2 = r * r;
        for (Player v : a.getWorld().getPlayers()) {
            if (v.equals(a) || v.equals(b)) continue;
            if (v.getLocation().distanceSquared(mid) > r2) continue;
            v.sendMessage(Component.text("⚔ " + nameFor(a, v) + " et " + nameFor(b, v) + " s'affrontent en duel.",
                    NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true));
        }
    }

    private void log(String kind, Player a, Player b, String detail) {
        // Journal universel (14/10) : remplace duel-log.txt → logs/duel-AAAA-MM-JJ.log.
        var je = com.reborn.shinobicore.journal.Journal.entree(
                com.reborn.shinobicore.api.JournalService.Categorie.DUEL, kind);
        if (a != null) je.acteur(a).lieu(a.getLocation());
        if (b != null) je.cible(b);
        je.detail(detail).ecrire();
        if (plugin.staffPanel() != null && a != null) {
            try {
                plugin.staffPanel().log(a, "duel", kind.toLowerCase(), describe(a) + " vs " + describe(b)
                        + (detail.isEmpty() ? "" : " · " + detail));
            } catch (RuntimeException ignored) {
                // le journal du Poste de garde est un plus, jamais bloquant
            }
        }
    }

    private String describe(Player p) {
        if (p == null) return "?";
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        return p.getName() + (c != null ? " (" + c.name() + ")" : "");
    }

    private static String name(UUID id) {
        Player p = Bukkit.getPlayer(id);
        return p != null ? p.getName() : id.toString();
    }

    /* ============================================================ outils */

    /** Nom de {@code subject} tel que {@code viewer} le connaît (prénom, surnom, ou « Inconnu »). */
    private String nameFor(Player subject, Player viewer) {
        ShinobiCharacter s = plugin.characters().getActive(subject.getUniqueId());
        ShinobiCharacter v = plugin.characters().getActive(viewer.getUniqueId());
        if (s == null) return subject.getName();
        if (v == null || s.id().equals(v.id())) return s.name();
        ShinobiCharacter.KnownName k = v.knownNameOf(s.id());
        return k == null ? "Inconnu" : k.display();
    }

    private static Player attackerOf(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    private static boolean near(Player a, Player b, double max) {
        return a.getWorld().equals(b.getWorld()) && a.getLocation().distanceSquared(b.getLocation()) <= max * max;
    }

    private static String pairKey(UUID x, UUID y) {
        String a = x.toString(), b = y.toString();
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    private static long secs(long ms) { return Math.max(1, (ms + 999) / 1000); }

    private static Component button(String label, NamedTextColor color, String cmd, String hover) {
        return Component.text(label, color, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(cmd))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }

    private static void err(Player p, String msg) {
        p.sendMessage(Component.text("⚔ " + msg, NamedTextColor.RED));
    }

    public boolean modded(Player p) { return p != null && p.getListeningPluginChannels().contains(CHANNEL); }

    private static JsonObject obj(String t) {
        JsonObject o = new JsonObject();
        o.addProperty("t", t);
        return o;
    }

    private void send(Player p, JsonObject o) {
        p.sendPluginMessage(plugin, CHANNEL, o.toString().getBytes(StandardCharsets.UTF_8));
    }
}
