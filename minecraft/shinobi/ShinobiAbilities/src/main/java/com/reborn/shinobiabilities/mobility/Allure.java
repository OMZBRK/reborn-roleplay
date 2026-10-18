package com.reborn.shinobiabilities.mobility;

import com.reborn.shinobiabilities.CoreServices;
import com.reborn.shinobicore.api.EnduranceService;
import com.reborn.shinobicore.api.InventoryService;
import com.reborn.shinobicore.api.StunService;
import com.reborn.shinobicore.util.Players;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Les <b>trois allures</b> (RedLine <i>Player movements</i>, décision du 2026-10-14) : <b>marche</b> par défaut (lente,
 * RP), <b>trot</b> tant que la touche de sprint est maintenue (le sprint Minecraft, gratuit) et <b>sprint</b> sur un
 * double appui (rapide, payé en endurance, ~5 s, puis récupération).
 *
 * <h2>Autorité</h2>
 * La vitesse est un <b>modificateur d'attribut</b> {@code reborn:allure} (MULTIPLY_SCALAR_1) posé ici, sur le serveur,
 * à partir de l'état observé : drapeau de sprint Minecraft (trot) et sprint accordé (canal {@code reborn:allure}). Le
 * mod client pose <b>le même modificateur</b> par prédiction (aucun à-coup à la latence) ; le serveur reste celui qui
 * accorde ou coupe le sprint et qui débite l'endurance.
 *
 * <h2>Protocole {@code reborn:allure}</h2>
 * <ul>
 *   <li>C2S, 2 octets : {@code 2} = je sprinte / {@code 0} = j'arrête, puis le numéro de la demande.</li>
 *   <li>S2C {@code 0} (réglages) : booléen actif, puis marche, trot, sprint en float.</li>
 *   <li>S2C {@code 1} (état) : allure accordée (0 marche, 1 trot, 2 sprint), endurance 0..1 en float, drapeaux
 *       (1 = allure neutre : course ninja, KO, véhicule, créatif… ; 2 = essoufflé), numéro de la dernière demande
 *       reçue.</li>
 * </ul>
 *
 * <h2>Endurance</h2>
 * Celle du combat ({@link EnduranceService}, ShinobiCombat) : une seule réserve pour courir et frapper, comme chez
 * RedLine ; son maximum suit la Vigueur, donc la durée du sprint progresse avec le personnage. Sans ShinobiCombat,
 * une réserve locale de 100 la remplace.
 */
public final class Allure implements PluginMessageListener, Listener {

    public static final String CHANNEL = "reborn:allure";
    static final NamespacedKey CLE = new NamespacedKey("reborn", "allure");

    public static final byte MARCHE = 0, TROT = 1, SPRINT = 2;
    private static final byte S2C_REGLAGES = 0, S2C_ETAT = 1;
    private static final int F_NEUTRE = 1, F_ESSOUFFLE = 2;
    /** Bonus du sprint Minecraft (+30 %, compensé pour obtenir la vitesse voulue). */
    private static final double SPRINT_MC = 1.3;
    /** Période de la tâche (ticks). */
    private static final int PERIODE = 2;
    /** Le drapeau de sprint peut tomber un instant (coup porté) : tolérance avant de couper le sprint (ticks). */
    private static final int GRACE_TICKS = 10;
    /** Réserve locale si ShinobiCombat est absent. */
    private static final double POOL_MAX = 100.0, POOL_REGEN = 16.0;
    private static final long POOL_DELAI_MS = 1200L;

    private final JavaPlugin plugin;
    private final CoreServices core;
    private final NarutoRun narutoRun;

    private final Set<UUID> sprint = new HashSet<>();
    private final Set<UUID> essouffle = new HashSet<>();
    private final Set<UUID> reglagesEnvoyes = new HashSet<>();
    private final Map<UUID, Double> applique = new HashMap<>();
    private final Map<UUID, Integer> horsSprint = new HashMap<>();
    private final Map<UUID, Integer> dernierEtat = new HashMap<>();
    private final Map<UUID, Long> dernierSprintMs = new HashMap<>();
    private final Map<UUID, double[]> pool = new HashMap<>();   // {courant, dernière dépense ms}
    /** Numéro de la dernière demande du client, renvoyé dans chaque état (le client ignore les vieux paquets). */
    private final Map<UUID, Byte> seqs = new HashMap<>();

    private BukkitTask tache;
    private int tick;
    private EnduranceService endurance;
    private StunService stun;
    private com.reborn.shinobicore.api.ModificateurService modifs;

    public Allure(JavaPlugin plugin, CoreServices core, NarutoRun narutoRun) {
        this.plugin = plugin;
        this.core = core;
        this.narutoRun = narutoRun;
    }

    /* ------------------------------------------------------------ réglages */

    private boolean actif() { return plugin.getConfig().getBoolean("mobility.allure.enabled", true); }
    private double fMarche() { return cfg("marche", 0.75); }
    private double fTrot() { return cfg("trot", 1.15); }
    private double fSprint() { return cfg("sprint", 1.55); }
    private double coutParSeconde() { return cfg("sprint-endurance-par-seconde", 20.0); }
    private double minPourPartir() { return cfg("sprint-endurance-min", 10.0); }
    private double relance() { return cfg("sprint-relance-pourcent", 30.0) / 100.0; }

    private double cfg(String k, double def) {
        return plugin.getConfig().getDouble("mobility.allure." + k, def);
    }

    /* ----------------------------------------------------------- cycle */

    public void start() {
        if (tache != null) return;
        tache = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, PERIODE);
    }

    public void stop() {
        if (tache != null) { tache.cancel(); tache = null; }
        for (Player p : Bukkit.getOnlinePlayers()) retirer(p);
        sprint.clear();
        essouffle.clear();
        reglagesEnvoyes.clear();
        applique.clear();
        horsSprint.clear();
        dernierEtat.clear();
        dernierSprintMs.clear();
        pool.clear();
        seqs.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        retirer(e.getPlayer());
        sprint.remove(id);
        essouffle.remove(id);
        reglagesEnvoyes.remove(id);
        horsSprint.remove(id);
        dernierEtat.remove(id);
        dernierSprintMs.remove(id);
        pool.remove(id);
        seqs.remove(id);
    }

    /** Le joueur sprinte-t-il (sprint accordé, pas le simple trot) ? */
    public boolean sprinte(Player p) {
        return p != null && sprint.contains(p.getUniqueId());
    }

    /* -------------------------------------------------------------- C2S */

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player p, byte[] message) {
        if (!CHANNEL.equals(channel) || message == null || message.length == 0) return;
        seqs.put(p.getUniqueId(), message.length > 1 ? message[1] : 0);
        if (message[0] == SPRINT) demarrer(p);
        else arreter(p, false);
    }

    private void demarrer(Player p) {
        UUID id = p.getUniqueId();
        if (sprint.contains(id)) { envoyerEtat(p, true); return; }
        boolean ok = actif() && !neutre(p) && sprintPermis(p) && !essouffle.contains(id)
                && enduranceCourante(p) >= minPourPartir();
        if (ok) {
            sprint.add(id);
            horsSprint.put(id, 0);
            dernierSprintMs.put(id, System.currentTimeMillis());
        }
        envoyerEtat(p, true);   // accord ou refus : le client s'aligne
    }

    private void arreter(Player p, boolean epuise) {
        UUID id = p.getUniqueId();
        boolean etait = sprint.remove(id);
        horsSprint.remove(id);
        if (epuise) essouffle.add(id);
        if (etait || epuise) {
            dernierSprintMs.put(id, System.currentTimeMillis());
            envoyerEtat(p, true);
        }
    }

    /* ------------------------------------------------------------ tâche */

    private void tick() {
        tick++;
        if (tick % 50 == 1) {          // services optionnels : relus toutes les 5 s (ordre de chargement des plugins)
            endurance = Bukkit.getServicesManager().load(EnduranceService.class);
            stun = Bukkit.getServicesManager().load(StunService.class);
            modifs = Bukkit.getServicesManager().load(com.reborn.shinobicore.api.ModificateurService.class);
        }
        boolean on = actif();
        double dt = PERIODE / 20.0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (!reglagesEnvoyes.contains(id) && p.getListeningPluginChannels().contains(CHANNEL)) {
                envoyerReglages(p);
                reglagesEnvoyes.add(id);
            }
            if (endurance == null) regenPool(id, dt);
            if (!on) {
                if (applique.containsKey(id)) retirer(p);
                if (sprint.remove(id)) envoyerEtat(p, true);
                continue;
            }

            boolean neutre = neutre(p);
            if (sprint.contains(id)) {
                int hors = p.isSprinting() ? 0 : horsSprint.getOrDefault(id, 0) + PERIODE;
                horsSprint.put(id, hors);
                if (neutre || !sprintPermis(p) || hors > GRACE_TICKS) {
                    arreter(p, false);
                } else if (tick % 2 == 0 && !consommer(p, coutParSeconde() * dt * 2)) {
                    arreter(p, true);  // vidé : essoufflé jusqu'à la relance
                }
            }
            if (essouffle.contains(id) && fraction(p) >= relance()) {
                essouffle.remove(id);
                envoyerEtat(p, true);
            }

            poser(p, neutre ? Double.NaN : facteur(p));

            // Retour client : à chaque changement, puis 5 fois par seconde pendant le sprint et la récupération.
            boolean recup = fraction(p) < 0.999
                && System.currentTimeMillis() - dernierSprintMs.getOrDefault(id, 0L) < 30_000L;
            envoyerEtat(p, tick % 2 == 0 && (sprint.contains(id) || recup || essouffle.contains(id)));
        }
    }

    /** Vitesse totale voulue (× la marche Minecraft), convertie en modificateur selon le drapeau de sprint. */
    private double facteur(Player p) {
        boolean mc = p.isSprinting();
        double total = !mc ? fMarche() : sprint.contains(p.getUniqueId()) ? fSprint() : fTrot();
        return total / (mc ? SPRINT_MC : 1.0) - 1.0;
    }

    /**
     * Allure « neutre » : le modificateur est retiré, une autre mécanique possède la vitesse (course ninja et Voie du
     * Flux, KO, véhicule ou porté, créatif, vol, nage, planeur) ou le joueur n'a pas de personnage actif.
     */
    private boolean neutre(Player p) {
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return true;
        if (p.isFlying() || p.isGliding() || p.isSwimming() || p.isInsideVehicle()) return true;
        if (narutoRun != null && narutoRun.isActive(p)) return true;
        if (core.ko() != null && core.ko().isKo(p.getUniqueId())) return true;
        return Players.active(core.characters(), p) == null;
    }

    /** Le sprint (pas le trot) est-il permis : ni ATA, ni étourdi, ni chargé, ni en train de porter quelqu'un. */
    private boolean sprintPermis(Player p) {
        UUID id = p.getUniqueId();
        // Étape 2 d'ETATS_JOUEUR : la matrice des états (SPRINTER) — douleur, ATA, chargé, porteur, menotté, garde…
        var etats = com.reborn.shinobicore.api.EtatService.get();
        if (etats != null && !etats.peut(id, com.reborn.shinobicore.api.etat.Capacite.SPRINTER)) return false;
        if (core.ko() != null && core.ko().isImpaired(id)) return false;
        if (stun != null && stun.isStunned(id)) return false;
        if (core.inventory() != null && core.inventory().palier(id) >= InventoryService.CHARGE) return false;
        // Modificateurs (nourriture, 14/10) : FAIM, Fatigue de la pilule, repas en cours.
        if (modifs != null && modifs.refuse(id, "SPRINTER")) return false;
        return p.getPassengers().isEmpty();
    }

    /* ---------------------------------------------------------- endurance */

    private double enduranceCourante(Player p) {
        if (endurance != null) return endurance.current(p);
        return pool.computeIfAbsent(p.getUniqueId(), k -> new double[] {POOL_MAX, 0})[0];
    }

    private double fraction(Player p) {
        if (endurance != null) {
            double max = endurance.max(p);
            return max <= 0 ? 1.0 : endurance.current(p) / max;
        }
        return enduranceCourante(p) / POOL_MAX;
    }

    private boolean consommer(Player p, double montant) {
        if (endurance != null) return endurance.tryConsume(p, montant);
        double[] r = pool.computeIfAbsent(p.getUniqueId(), k -> new double[] {POOL_MAX, 0});
        if (r[0] < montant) return false;
        r[0] -= montant;
        r[1] = System.currentTimeMillis();
        return true;
    }

    private void regenPool(UUID id, double dt) {
        double[] r = pool.get(id);
        if (r == null || r[0] >= POOL_MAX) return;
        if (System.currentTimeMillis() - (long) r[1] < POOL_DELAI_MS) return;
        r[0] = Math.min(POOL_MAX, r[0] + POOL_REGEN * dt);
    }

    /* ---------------------------------------------------------- attribut */

    /** Pose le modificateur (NaN = le retirer) ; ne touche à l'attribut que si la valeur change. */
    private void poser(Player p, double valeur) {
        UUID id = p.getUniqueId();
        Double avant = applique.get(id);
        if (Double.isNaN(valeur)) {
            if (avant != null) retirer(p);
            return;
        }
        if (avant != null && Math.abs(avant - valeur) < 1e-4) return;
        AttributeInstance a = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (a == null) return;
        enlever(a);
        a.addTransientModifier(new AttributeModifier(CLE, valeur,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        applique.put(id, valeur);
    }

    private void retirer(Player p) {
        applique.remove(p.getUniqueId());
        enlever(p.getAttribute(Attribute.MOVEMENT_SPEED));
    }

    private static void enlever(AttributeInstance a) {
        if (a == null) return;
        for (AttributeModifier m : a.getModifiers().toArray(new AttributeModifier[0])) {
            if (CLE.equals(m.getKey())) a.removeModifier(m);
        }
    }

    /* --------------------------------------------------------------- S2C */

    private void envoyerReglages(Player p) {
        ByteArrayOutputStream b = new ByteArrayOutputStream(16);
        try (DataOutputStream out = new DataOutputStream(b)) {
            out.writeByte(S2C_REGLAGES);
            out.writeBoolean(actif());
            out.writeFloat((float) fMarche());
            out.writeFloat((float) fTrot());
            out.writeFloat((float) fSprint());
        } catch (IOException ignored) {
            return;
        }
        envoyer(p, b.toByteArray());
    }

    /**
     * Envoie l'état si {@code force} ou s'il a changé depuis le dernier envoi (allure, drapeaux). L'allure envoyée est
     * celle que le serveur <b>accorde</b> ; le trot reste observé par le client lui-même.
     */
    private void envoyerEtat(Player p, boolean force) {
        UUID id = p.getUniqueId();
        boolean sp = sprint.contains(id);
        byte allure = sp ? SPRINT : p.isSprinting() ? TROT : MARCHE;
        int flags = (neutre(p) ? F_NEUTRE : 0) | (essouffle.contains(id) ? F_ESSOUFFLE : 0);
        int cle = allure | (flags << 4);
        Integer avant = dernierEtat.get(id);
        if (!force && avant != null && avant == cle) return;
        dernierEtat.put(id, cle);
        ByteArrayOutputStream b = new ByteArrayOutputStream(8);
        try (DataOutputStream out = new DataOutputStream(b)) {
            out.writeByte(S2C_ETAT);
            out.writeByte(allure);
            out.writeFloat((float) Math.max(0, Math.min(1, fraction(p))));
            out.writeByte(flags);
            out.writeByte(seqs.getOrDefault(id, (byte) 0));
        } catch (IOException ignored) {
            return;
        }
        envoyer(p, b.toByteArray());
    }

    private void envoyer(Player p, byte[] data) {
        if (!p.isOnline() || !p.getListeningPluginChannels().contains(CHANNEL)) return;
        try {
            p.sendPluginMessage(plugin, CHANNEL, data);
        } catch (RuntimeException ignored) {
            // canal non enregistré côté plugin (arrêt en cours) : rien à faire.
        }
    }
}
