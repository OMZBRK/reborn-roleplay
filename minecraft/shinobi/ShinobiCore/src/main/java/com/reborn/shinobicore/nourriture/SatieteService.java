package com.reborn.shinobicore.nourriture;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.modificateur.ModificateurRegistry;
import com.reborn.shinobicore.modificateur.TypeModificateur;
import com.reborn.shinobicore.staff.outils.ModeStaff;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>Satiété</b> et <b>régénération passive</b> (docs/gameplay/NOURRITURE.md §3) : une jauge 0-100 % par personnage,
 * distincte de la faim vanilla.
 * <ul>
 *   <li><b>Faim vanilla neutralisée</b> en continu : aucune baisse naturelle (événement annulé), remise à 20 chaque
 *       seconde — sauf quand l'ATA ou la douleur la tiennent à 6 pour couper le trot (compatibilité gardée). C'est le
 *       correctif du défaut relevé le 14/10 (effet SATURATION de 90 s jamais renouvelé) ;</li>
 *   <li>perte {@code 0,0125 %/s} × activité (assis ×0,5, marche, trot ×1,5, sprint ×4, nage ×2, combat ×3) ×
 *       modificateurs {@code satiete_perte}, plus {@code 0,2 %} par 10 chakra dépensés ; <b>figée</b> en KO, porté,
 *       AFK, mode staff, créatif / spectateur, et hors ligne ;</li>
 *   <li><b>régénération</b> dès 90 %, 30 s sans coup, ni KO, ni douleur, ni duel, hors terrain d'entraînement :
 *       {@code 0,25 %} des PV/s × modificateurs {@code regen}, jusqu'à 100 % (30 % pendant l'ATA) ;</li>
 *   <li><b>FAIM</b> à 0 % : plus de sprint ni de course ninja, récupération d'endurance −50 %, jamais de dégâts
 *       (contributeur du registre des modificateurs).</li>
 * </ul>
 * Persistance : {@code plugins/ShinobiCore/satiete.yml} (par personnage).
 */
public final class SatieteService implements Listener, ModificateurRegistry.Contributeur {

    /** Drapeaux poussés dans {@code reborn:vitals}. */
    public static final int F_REGEN = 1, F_AFFAME = 2, F_FAIM = 4, F_ATA = 8, F_GELE = 16;

    private static final class Suivi {
        long dernierCoup, dernierCombat, derniereActivite = System.currentTimeMillis();
        double x = Double.NaN, z, chakra = -1;
        float yaw, pitch;
        boolean regen;
        int pauseS;
        boolean gele;
    }

    private final ShinobiCore plugin;
    private final NourritureModule module;
    private final File fichier;
    private final Map<UUID, Double> parPerso = new ConcurrentHashMap<>();
    private final Map<UUID, Suivi> suivis = new HashMap<>();
    private boolean sale;
    private int ticks;

    SatieteService(ShinobiCore plugin, NourritureModule module) {
        this.plugin = plugin;
        this.module = module;
        this.fichier = new File(plugin.getDataFolder(), "satiete.yml");
    }

    private NourritureConfig cfg() { return module.cfg(); }

    /* ================================================================ lecture */

    /** Satiété (0-100) du personnage actif ; la valeur de départ si inconnue. */
    public double satiete(Player p) {
        ShinobiCharacter c = p == null ? null : plugin.characters().getActive(p.getUniqueId());
        return c == null ? cfg().depart : parPerso.getOrDefault(c.id(), cfg().depart);
    }

    public double satiete(UUID compte) {
        ShinobiCharacter c = plugin.characters().getActive(compte);
        return c == null ? cfg().depart : parPerso.getOrDefault(c.id(), cfg().depart);
    }

    /** Ajoute (ou retire) de la satiété sans dépasser {@code plafond} ; renvoie ce qui a été réellement ajouté. */
    public double ajouter(Player p, double delta, double plafond) {
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        if (c == null) return 0;
        double avant = parPerso.getOrDefault(c.id(), cfg().depart);
        double apres = delta >= 0 ? Math.max(avant, Math.min(plafond, avant + delta)) : Math.max(0, avant + delta);
        parPerso.put(c.id(), apres);
        sale = true;
        return apres - avant;
    }

    public void fixer(UUID persoId, double v) {
        parPerso.put(persoId, Math.max(0, Math.min(100, v)));
        sale = true;
    }

    /** Millisecondes depuis le dernier coup reçu (très grand si jamais). */
    public long depuisCoup(UUID compte) {
        Suivi s = suivis.get(compte);
        return s == null || s.dernierCoup == 0 ? Long.MAX_VALUE : System.currentTimeMillis() - s.dernierCoup;
    }

    public boolean faim(UUID compte) { return satiete(compte) <= 0.0001; }

    /** Régénération figée par un coup récent (et le reste des conditions remplies) : secondes restantes, 0 sinon. */
    public int pauseRegen(UUID compte) {
        Suivi s = suivis.get(compte);
        return s == null ? 0 : s.pauseS;
    }

    /** Les champs ajoutés à {@code reborn:vitals} : satiété ‰, à venir ‰ (repas), pause (s), drapeaux. */
    public int[] vitalsEtendus(Player p) {
        double sat = satiete(p);
        Suivi s = suivis.get(p.getUniqueId());
        int f = 0;
        if (s != null && s.regen) f |= F_REGEN;
        if (sat <= 0.0001) f |= F_FAIM;
        else if (sat < cfg().affame) f |= F_AFFAME;
        if (plugin.ata() != null && plugin.ata().isImpaired(p.getUniqueId())) f |= F_ATA;
        if (s != null && s.gele) f |= F_GELE;
        double venir = module.repas() == null ? 0 : module.repas().aVenir(p, sat);
        return new int[]{(int) Math.round(sat * 10), (int) Math.round(venir * 10), s == null ? 0 : s.pauseS, f};
    }

    /* ================================================================ contributeur (FAIM) */

    @Override
    public double multiplicateur(UUID compte, String effet) {
        if ("endurance_regen".equals(effet) && faim(compte) && plugin.characters().getActive(compte) != null) {
            return cfg().faimEndurance;
        }
        return 1.0;
    }

    @Override
    public String raison(UUID compte, String capacite) {
        if (("SPRINTER".equals(capacite) || "COURSE_NINJA".equals(capacite)) && faim(compte)
                && plugin.characters().getActive(compte) != null) return "Faim";
        return null;
    }

    /** Pour le registre : une régénération est « en pause » (icône grise) quand la régénération passive l'est. */
    boolean enPause(UUID compte, TypeModificateur t) {
        return t.effets().containsKey("regen") && pauseRegen(compte) > 0;
    }

    /* ================================================================ tick (1 s) */

    void tick() {
        NourritureConfig k = cfg();
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            ShinobiCharacter c = plugin.characters().getActive(id);
            if (c == null) continue;
            Suivi s = suivis.computeIfAbsent(id, x -> new Suivi());
            neutraliserFaimVanilla(p);

            // activité : regard (AFK), déplacement horizontal (marche, trot, sprint)
            Location l = p.getLocation();
            if (l.getYaw() != s.yaw || l.getPitch() != s.pitch) {
                s.derniereActivite = now;
                s.yaw = l.getYaw();
                s.pitch = l.getPitch();
            }
            double vitesse = 0;
            if (!Double.isNaN(s.x)) vitesse = Math.hypot(l.getX() - s.x, l.getZ() - s.z);
            s.x = l.getX();
            s.z = l.getZ();

            double sat = parPerso.getOrDefault(c.id(), k.depart);
            boolean ko = plugin.ko() != null && plugin.ko().isKo(id);
            boolean porte = plugin.porter() != null && plugin.porter().carriedBy(id) != null;
            boolean afk = now - s.derniereActivite > k.afkMs;
            GameMode gm = p.getGameMode();
            s.gele = ko || porte || afk || ModeStaff.horsJeu(p) || gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR;

            double cur = c.chakra() == null ? -1 : c.chakra().current();
            if (!s.gele) {
                double f;
                boolean assis = p.isInsideVehicle() || (plugin.posture() != null && plugin.posture().isPosed(p));
                if (now - s.dernierCombat < k.combatMs) f = k.fCombat;
                else if (p.isSwimming() || p.isInWater()) f = vitesse > 0.3 ? k.fNage : k.fMarche;
                else if (assis) f = k.fAssis;
                else if (vitesse >= k.vitesseSprint) f = k.fSprint;
                else if (p.isSprinting()) f = k.fTrot;
                else f = k.fMarche;
                double perte = k.pertePs * f * module.modifs().multiplicateur(id, "satiete_perte");
                if (s.chakra >= 0 && cur >= 0 && cur < s.chakra - 0.01) perte += (s.chakra - cur) / 10.0 * k.techniqueParDix;
                double avant = sat;
                sat = Math.max(0, sat - perte);
                if (avant > 0 && sat <= 0) module.passageFaim(p);
                else if (avant >= k.affame && sat < k.affame) module.passageAffame(p);
                parPerso.put(c.id(), sat);
                sale = true;
            }
            s.chakra = cur;

            regenerer(p, c, s, sat, ko, now);
        }
        if (sale && ++ticks % 60 == 0) sauver();
    }

    private void regenerer(Player p, ShinobiCharacter c, Suivi s, double sat, boolean ko, long now) {
        NourritureConfig k = cfg();
        UUID id = p.getUniqueId();
        s.regen = false;
        s.pauseS = 0;
        if (ko || s.gele || sat < k.seuilRegen) return;
        if (plugin.douleur() != null && plugin.douleur().isInPain(id)) return;
        if (plugin.duels() != null && plugin.duels().inDuel(id)) return;
        if (plugin.trainingZones() != null && plugin.trainingZones().isTraining(p)) return;
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        double max = a == null ? c.maxHp() : a.getValue();
        boolean ata = plugin.ata() != null && plugin.ata().isImpaired(id);
        double plafond = max * (ata ? k.plafondAta / 100.0 : 1.0);
        if (p.getHealth() >= plafond - 1e-6) return;
        long depuis = s.dernierCoup == 0 ? Long.MAX_VALUE : now - s.dernierCoup;
        if (depuis < k.pauseCoupMs) {
            s.pauseS = (int) Math.ceil((k.pauseCoupMs - depuis) / 1000.0);
            return;
        }
        double gain = max * k.regenPvPs / 100.0 * module.modifs().multiplicateur(id, "regen");
        if (gain <= 0) return;
        double cible = Math.min(plafond, p.getHealth() + gain);
        double avant = p.getHealth();
        try {
            p.heal(cible - avant, EntityRegainHealthEvent.RegainReason.CUSTOM);
        } catch (Throwable t) {
            p.setHealth(cible);
        }
        c.setCurrentHp(p.getHealth());
        s.regen = p.getHealth() > avant;
    }

    /** Faim vanilla : tenue pleine, sauf quand l'ATA ou la douleur la tiennent à 6 (pas de trot). */
    private void neutraliserFaimVanilla(Player p) {
        UUID id = p.getUniqueId();
        boolean tenue = com.reborn.shinobicore.etat.Applicateur.actif()
                ? com.reborn.shinobicore.etat.Applicateur.faimTenue(id)          // une seule règle (ETATS_JOUEUR §7.5)
                : (plugin.ata() != null && plugin.ata().isImpaired(id))
                        || (plugin.douleur() != null && plugin.douleur().isInPain(id));
        if (tenue) return;
        if (p.getFoodLevel() < 20) p.setFoodLevel(20);
        if (p.getSaturation() < 5f) p.setSaturation(5f);
        p.setExhaustion(0f);
    }

    /* ================================================================ événements */

    /** Aucune baisse naturelle de la faim vanilla pour un personnage actif (manger un aliment vanilla reste possible). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFaim(FoodLevelChangeEvent ev) {
        if (!(ev.getEntity() instanceof Player p) || ev.getItem() != null) return;
        if (plugin.characters().getActive(p.getUniqueId()) == null) return;
        if (ev.getFoodLevel() < p.getFoodLevel()) ev.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCoup(EntityDamageEvent ev) {
        if (!(ev.getEntity() instanceof Player p) || ev.getFinalDamage() <= 0) return;
        long now = System.currentTimeMillis();
        Suivi s = suivis.computeIfAbsent(p.getUniqueId(), x -> new Suivi());
        s.dernierCoup = now;
        if (ev instanceof EntityDamageByEntityEvent be) {
            s.dernierCombat = now;
            Entity d = be.getDamager();
            if (d instanceof Projectile pr && pr.getShooter() instanceof Entity sh) d = sh;
            if (d instanceof Player a) suivis.computeIfAbsent(a.getUniqueId(), x -> new Suivi()).dernierCombat = now;
        }
        module.coupRecu(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent ev) {
        suivis.remove(ev.getPlayer().getUniqueId());
    }

    /* ================================================================ disque */

    void charger() {
        parPerso.clear();
        if (!fichier.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier);
        for (String k : y.getKeys(false)) {
            try {
                parPerso.put(UUID.fromString(k), Math.max(0, Math.min(100, y.getDouble(k))));
            } catch (IllegalArgumentException ignored) {
                // clé illisible
            }
        }
    }

    void sauver() {
        sale = false;
        YamlConfiguration y = new YamlConfiguration();
        for (var e : parPerso.entrySet()) y.set(e.getKey().toString(), Math.round(e.getValue() * 1000) / 1000.0);
        try {
            y.save(fichier);
        } catch (IOException ex) {
            plugin.getLogger().warning("[satiété] sauvegarde impossible : " + ex.getMessage());
        }
    }
}
