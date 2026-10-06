package com.reborn.shinobicore.medic;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.StatsService;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.ko.injury.Injury;
import com.reborn.shinobicore.ko.injury.Severity;
import com.reborn.shinobicore.stats.StatFormulas;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paume de soin maintenue (lot KO-4, docs/PROPOSITION_KO_PAINRP.md §5).
 *
 * <p>Le médic cible un patient ({@code /paume [joueur]}) ; le patient accepte
 * (un corps à terre ou inconscient est soigné sans demande). Le soin est
 * <b>tenu</b> : chaque seconde il rend
 * {@code pv-max × pct-par-seconde × (1 + bonus-controle × eff(Contrôle)) × bonus de rang}
 * et coûte au médic {@code cout-chakra-pct} de son chakra max. Il s'arrête si le
 * médic bouge, est frappé, n'a plus de chakra, ou si le patient s'éloigne.
 *
 * <p>Rangs (permissions) :
 * <ul>
 *   <li>{@code shinobicore.medic} — rang 1 : jusqu'à {@code plafond-rang1} (60 %)
 *       des PV ; lève l'ATA si le patient n'a aucune blessure Importante ou Urgente ;</li>
 *   <li>{@code shinobicore.medic.expert} — rang 2 : jusqu'à 100 %, plus rapide,
 *       lève toute ATA.</li>
 * </ul>
 * Lever l'ATA demande d'avoir tenu le soin au moins {@code ata-secondes} (30 s) :
 * c'est le temps de la scène, même si les PV sont déjà remontés.
 */
public final class PalmHealing implements Listener {

    private static final class Session {
        final UUID medic, patient;
        final int rank;
        final Location anchor;
        long heldMillis;
        int lastQuarter;
        boolean ataDone;
        BukkitTask task;

        Session(UUID medic, UUID patient, int rank, Location anchor) {
            this.medic = medic; this.patient = patient; this.rank = rank; this.anchor = anchor;
        }
    }

    private static final String[] FEELINGS = {
            "Une chaleur douce se répand dans ta poitrine…",
            "La douleur reflue, ton souffle se pose.",
            "Le flux de chakra du médic referme tes plaies.",
            "Tes forces reviennent peu à peu."
    };

    private final ShinobiCore plugin;
    private final Map<UUID, Session> byMedic = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> byPatient = new ConcurrentHashMap<>();
    /** patient → (médic, expiration) : demande de soin en attente d'acceptation. */
    private final Map<UUID, Object[]> pending = new ConcurrentHashMap<>();

    public PalmHealing(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    private double cfg(String key, double def) {
        return plugin.getConfig().getDouble("medic.paume." + key, def);
    }

    public static int rankOf(Player p) {
        if (p.hasPermission("shinobicore.medic.expert")) return 2;
        if (p.hasPermission("shinobicore.medic")) return 1;
        return 0;
    }

    public boolean isHealing(UUID medic) { return byMedic.containsKey(medic); }

    /* ============================================================ demande */

    /** Le médic tend la paume vers {@code patient}. */
    /** Raison du refus, ou null si le soin peut commencer. */
    private String refusal(Player medic, Player patient) {
        if (rankOf(medic) == 0) return "Tu ne maîtrises pas le chakra médical.";
        if (medic.equals(patient)) return "La paume de soin ne se pratique pas sur soi.";
        if (plugin.ko().isKo(medic.getUniqueId())) return "Tu es toi-même hors d'état.";
        if (byMedic.containsKey(medic.getUniqueId())) return "Tu soignes déjà quelqu'un (/paume stop).";
        if (byPatient.containsKey(patient.getUniqueId())) return "Quelqu'un soigne déjà cette personne.";
        double range = cfg("portee", 4.0);
        if (!medic.getWorld().equals(patient.getWorld())
                || medic.getLocation().distanceSquared(patient.getLocation()) > range * range) {
            return "Approche-toi : la paume doit toucher le blessé.";
        }
        return null;
    }

    public void request(Player medic, Player patient) {
        String no = refusal(medic, patient);
        if (no != null) { medic.sendMessage("§7" + no); return; }
        // Un corps à terre / inconscient ne peut pas refuser : on soigne directement.
        if (plugin.ko().isKo(patient.getUniqueId())) { start(medic, patient, rankOf(medic)); return; }

        pending.put(patient.getUniqueId(), new Object[] { medic.getUniqueId(), System.currentTimeMillis() + 30_000L });
        ShinobiCharacter mc = plugin.characters().getActive(medic.getUniqueId());
        String who = mc != null ? mc.name() : medic.getName();
        patient.sendMessage(Component.text(who + " veut te soigner. ", NamedTextColor.GRAY)
                .append(Component.text("[Accepter les soins]", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/paume accepter"))));
        medic.sendActionBar(Component.text("Tu attends que le blessé accepte tes soins…", NamedTextColor.GRAY));
    }

    /** Le patient accepte la dernière demande reçue. */
    public void accept(Player patient) {
        Object[] req = pending.remove(patient.getUniqueId());
        if (req == null || (long) req[1] < System.currentTimeMillis()) {
            patient.sendMessage("§7Aucune demande de soin en attente.");
            return;
        }
        Player medic = Bukkit.getPlayer((UUID) req[0]);
        if (medic == null) { patient.sendMessage("§7Le médic n'est plus là."); return; }
        String no = refusal(medic, patient);
        if (no != null) { patient.sendMessage("§7Le soin ne peut pas commencer : " + no); return; }
        start(medic, patient, rankOf(medic));
    }

    /* ============================================================ session */

    private void start(Player medic, Player patient, int rank) {
        if (byMedic.containsKey(medic.getUniqueId()) || byPatient.containsKey(patient.getUniqueId())) return;
        Session s = new Session(medic.getUniqueId(), patient.getUniqueId(), rank, medic.getLocation().clone());
        byMedic.put(s.medic, s);
        byPatient.put(s.patient, s.medic);
        s.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(s), 20L, 20L);
        medic.playSound(medic.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.6f);
        medic.sendActionBar(Component.text("Paume de soin — ne bouge pas, garde la concentration.", NamedTextColor.GREEN));
        patient.sendMessage(Component.text(FEELINGS[0], NamedTextColor.GRAY, TextDecoration.ITALIC));
    }

    public void stop(UUID medicId, String why) {
        Session s = byMedic.remove(medicId);
        if (s == null) return;
        byPatient.remove(s.patient);
        if (s.task != null) s.task.cancel();
        Player m = Bukkit.getPlayer(s.medic);
        Player p = Bukkit.getPlayer(s.patient);
        if (why != null) {
            if (m != null) m.sendActionBar(Component.text(why, NamedTextColor.GRAY));
            if (p != null) p.sendActionBar(Component.text(why, NamedTextColor.GRAY));
        }
        if (m != null) m.playSound(m.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.4f);
    }

    private void tick(Session s) {
        Player medic = Bukkit.getPlayer(s.medic);
        Player patient = Bukkit.getPlayer(s.patient);
        if (medic == null || patient == null) { stop(s.medic, "Le soin est interrompu."); return; }
        if (plugin.ko().isKo(s.medic)) { stop(s.medic, "Le médic s'est effondré."); return; }
        Location here = medic.getLocation();
        double tol = cfg("tolerance-mouvement", 1.5);
        if (!here.getWorld().equals(s.anchor.getWorld()) || here.distanceSquared(s.anchor) > tol * tol) {
            stop(s.medic, "Concentration brisée : le médic a bougé.");
            return;
        }
        double range = cfg("portee", 4.0);
        if (!patient.getWorld().equals(here.getWorld()) || patient.getLocation().distanceSquared(here) > range * range) {
            stop(s.medic, "Le blessé s'est éloigné.");
            return;
        }
        ShinobiCharacter mc = plugin.characters().getActive(s.medic);
        ShinobiCharacter pc = plugin.characters().getActive(s.patient);
        if (mc == null || pc == null) { stop(s.medic, "Le soin est interrompu."); return; }

        double cost = mc.chakra().max() * cfg("cout-chakra-pct", 0.01);
        if (!mc.chakra().consume(cost)) {
            stop(s.medic, "Ton chakra est épuisé : tu dois t'arrêter.");
            return;
        }
        s.heldMillis += 1000L;

        double max = patient.getMaxHealth();
        double cap = max * (s.rank >= 2 ? cfg("plafond-rang2", 1.0) : cfg("plafond-rang1", 0.60));
        double controle = StatFormulas.eff(mc.stats(), StatsService.Stat.CONTROLE);
        double heal = max * cfg("pct-par-seconde", 0.015)
                * (1.0 + cfg("bonus-controle", 0.10) * controle)
                * (s.rank >= 2 ? cfg("bonus-rang2", 1.5) : 1.0);
        double hp = patient.getHealth();
        if (hp < cap) {
            hp = Math.min(cap, hp + heal);
            patient.setHealth(hp);
            pc.setCurrentHp(hp);
            if (plugin.ko().isKo(s.patient)) plugin.ko().checkHealRevive(patient);
        }

        // Effets : flux vert entre les mains du médic et la poitrine du blessé.
        Location chest = patient.getLocation().add(0, 1.0, 0);
        Particle.DustOptions green = new Particle.DustOptions(Color.fromRGB(110, 230, 140), 1.2f);
        here.getWorld().spawnParticle(Particle.DUST, here.clone().add(0, 1.1, 0), 8, 0.25, 0.15, 0.25, 0, green);
        chest.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, chest, 6, 0.3, 0.4, 0.3, 0);
        if (s.heldMillis % 3000L == 0) {
            here.getWorld().playSound(here, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.5f, 1.3f);
        }

        int pct = (int) Math.round(hp / max * 100.0);
        int quarter = Math.min(3, pct / 25);
        if (quarter > s.lastQuarter) {
            s.lastQuarter = quarter;
            patient.sendMessage(Component.text(FEELINGS[quarter], NamedTextColor.GRAY, TextDecoration.ITALIC));
        }

        // ATA : lever après le temps de scène minimal.
        long ataMillis = (long) (cfg("ata-secondes", 30) * 1000L);
        boolean hasAta = plugin.ata() != null && plugin.ata().hasAta(pc.id());
        if (hasAta && !s.ataDone && s.heldMillis >= ataMillis) {
            s.ataDone = true;
            if (s.rank >= 2 || !hasSevereInjury(pc)) {
                plugin.ata().lift(pc.id(), "Les soins de " + mc.name() + " t'ont remis sur pied.");
            } else {
                medic.sendMessage("§6Ses blessures graves doivent d'abord être traitées (/soigner) avant de lever l'ATA.");
            }
        }

        boolean capReached = hp >= cap - 0.01;
        boolean ataPending = hasAta && !s.ataDone;
        String tail = capReached
                ? (ataPending ? " · maintiens encore " + Math.max(0, (ataMillis - s.heldMillis) / 1000L) + " s" : "")
                : "";
        medic.sendActionBar(Component.text("Paume de soin — " + pc.name() + " " + pct + " %"
                + " · −" + Math.round(cost) + " chakra/s" + tail, NamedTextColor.GREEN));
        patient.sendActionBar(Component.text("Tu reçois des soins — " + pct + " %" + tail, NamedTextColor.GREEN));

        if (capReached && !ataPending) {
            String done = s.rank >= 2 || cap >= max ? "Soins terminés." : "Soins terminés — le reste demande un médic plus expérimenté.";
            stop(s.medic, done);
        }
    }

    private static boolean hasSevereInjury(ShinobiCharacter c) {
        for (Injury inj : c.injuries()) {
            if (inj.hidden()) continue;
            if (inj.severity() == Severity.IMPORTANT || inj.severity() == Severity.URGENT) return true;
        }
        return false;
    }

    /* ============================================================ événements */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMedicHit(EntityDamageEvent ev) {
        if (ev.getEntity() instanceof Player p && byMedic.containsKey(p.getUniqueId())) {
            stop(p.getUniqueId(), "Concentration brisée : le médic a été frappé.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent ev) {
        UUID id = ev.getPlayer().getUniqueId();
        stop(id, "Le soin est interrompu.");
        UUID medic = byPatient.get(id);
        if (medic != null) stop(medic, "Le blessé est parti.");
        pending.remove(id);
    }

    public void stopAll() {
        for (UUID m : byMedic.keySet()) stop(m, null);
    }
}
