package com.reborn.shinobiabilities.mobility;

import com.reborn.shinobiabilities.CoreServices;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.util.Players;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * <b>Marche sur l'eau</b> (contrôle du chakra, 15/10) : un personnage qui a appris la capacité
 * ({@link MobilityActionSlot#MARCHE_EAU}) tient debout sur l'eau, y marche, trotte et sprinte, en dépensant un peu de
 * chakra en continu. Accroupi (maintenu) = s'enfoncer volontairement.
 *
 * <h2>Autorité</h2>
 * Le mouvement de Minecraft est calculé par le client : c'est le mod qui rend la surface de l'eau « solide » pour son
 * joueur, <b>seulement quand le serveur le permet</b>. Ce module décide donc du <b>permis</b> (appris, chakra, état,
 * mode staff…) et le pousse sur le canal {@code reborn:eau} à chaque changement ; il débite le chakra tant que le joueur
 * se tient à la surface (détecté ici, d'après sa position). Chakra vide, KO, douleur ou ATA, étourdi, porté, mode
 * staff… : permis retiré, le joueur tombe à l'eau.
 *
 * <h2>Protocole {@code reborn:eau} (octets bruts, S → C)</h2>
 * <ul>
 *   <li>{@code 0} réglages : actif (booléen), coût immobile, marche, trot, sprint (float, chakra par seconde) ;</li>
 *   <li>{@code 1} permis : 0/1, puis la raison d'un refus (0 aucune, 1 pas appris, 2 chakra, 3 état, 4 hors jeu,
 *       5 surcharge, 6 désactivé).</li>
 * </ul>
 * Tout le monde voit les ondes : chaque client détecte lui-même les joueurs debout sur l'eau (rien à relayer).
 */
public final class MarcheEau implements Listener {

    public static final String CHANNEL = "reborn:eau";
    private static final byte S2C_REGLAGES = 0, S2C_PERMIS = 1;
    public static final byte OK = 0, PAS_APPRIS = 1, CHAKRA = 2, ETAT = 3, HORS_JEU = 4, SURCHARGE = 5, DESACTIVE = 6;
    private static final int PERIODE = 5;

    private final JavaPlugin plugin;
    private final CoreServices core;
    private final ToggleStore toggles;
    private final Allure allure;

    private final Map<UUID, Byte> envoye = new HashMap<>();
    private final Set<UUID> reglagesEnvoyes = new HashSet<>();
    /** Dernier passage sur l'eau (ms) : la chute amortie sur l'eau reste vraie un court instant. */
    private final Map<UUID, Long> surLEau = new HashMap<>();
    /** Position au passage précédent (x, z) : vitesse horizontale observée (celle du serveur n'est pas fiable). */
    private final Map<UUID, double[]> dernieresPositions = new HashMap<>();
    private BukkitTask tache;
    private int tick;
    private com.reborn.shinobicore.api.ModificateurService modifs;

    public MarcheEau(JavaPlugin plugin, CoreServices core, ToggleStore toggles, Allure allure) {
        this.plugin = plugin;
        this.core = core;
        this.toggles = toggles;
        this.allure = allure;
    }

    private boolean actif() { return plugin.getConfig().getBoolean("mobility.marche-eau.enabled", true); }

    private double cout(String k, double def) {
        return Math.max(0, plugin.getConfig().getDouble("mobility.marche-eau.chakra-par-seconde." + k, def));
    }

    public void start() {
        if (tache == null) tache = plugin.getServer().getScheduler().runTaskTimer(plugin, this::boucle, 20L, PERIODE);
    }

    public void stop() {
        if (tache != null) { tache.cancel(); tache = null; }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (envoye.getOrDefault(p.getUniqueId(), (byte) 0) == 1) envoyerPermis(p, false, DESACTIVE);
        }
        envoye.clear();
        dernieresPositions.clear();
        reglagesEnvoyes.clear();
        surLEau.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        envoye.remove(id);
        reglagesEnvoyes.remove(id);
        surLEau.remove(id);
        dernieresPositions.remove(id);
    }

    /** Se tenir debout sur l'eau : pas de dégâts de chute en s'y posant (l'eau les aurait annulés de toute façon). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChute(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) return;
        if (surLEau(p) || System.currentTimeMillis() - surLEau.getOrDefault(p.getUniqueId(), 0L) < 500L) e.setCancelled(true);
    }

    private void boucle() {
        tick++;
        if (tick % 20 == 1) modifs = Bukkit.getServicesManager().load(com.reborn.shinobicore.api.ModificateurService.class);
        boolean on = actif();
        double dt = PERIODE / 20.0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (!p.getListeningPluginChannels().contains(CHANNEL)) continue;   // client sans le mod récent
            if (!reglagesEnvoyes.contains(id)) {
                envoyerReglages(p, on);
                reglagesEnvoyes.add(id);
            }
            byte raison = on ? raison(p) : DESACTIVE;
            if (raison == OK && surLEau(p) && p.getGameMode() != GameMode.CREATIVE) {   // créatif : gratuit
                surLEau.put(id, System.currentTimeMillis());
                ShinobiCharacter c = Players.active(core.characters(), p);
                double par = coutActuel(p, dt);
                if (c != null && par > 0 && !c.chakra().consume(par * dt)) {
                    c.chakra().setCurrent(0);
                    raison = CHAKRA;
                }
            }
            Byte avant = envoye.get(id);
            boolean permis = raison == OK;
            byte cle = permis ? 1 : (byte) (-raison);
            if (avant == null || avant != cle) {
                envoye.put(id, cle);
                envoyerPermis(p, permis, raison);
            }
        }
    }

    /** Chakra par seconde selon l'allure : immobile, marche, trot (touche de sprint) ou sprint accordé. */
    private double coutActuel(Player p, double dt) {
        Location l = p.getLocation();
        double[] a = dernieresPositions.put(p.getUniqueId(), new double[] {l.getX(), l.getZ()});
        double dx = a == null ? 0 : l.getX() - a[0], dz = a == null ? 0 : l.getZ() - a[1];
        boolean bouge = Math.sqrt(dx * dx + dz * dz) / dt > 0.6;          // plus de 0,6 bloc/s
        if (!bouge) return cout("immobile", 0.3);
        if (allure != null && allure.sprinte(p)) return cout("sprint", 1.5);
        if (p.isSprinting()) return cout("trot", 0.8);
        return cout("marche", 0.5);
    }

    /** Pourquoi le joueur ne peut pas marcher sur l'eau maintenant ({@link #OK} sinon). */
    private byte raison(Player p) {
        UUID id = p.getUniqueId();
        GameMode gm = p.getGameMode();
        if (gm == GameMode.SPECTATOR) return HORS_JEU;
        ShinobiCharacter c = Players.active(core.characters(), p);
        if (c == null) return HORS_JEU;
        if (core.ko() != null) {
            if (core.ko().isOutOfPlay(id)) return HORS_JEU;
            if (core.ko().isKo(id)) return ETAT;
            // La matrice des états refuse aussi la mobilité au palier « Surchargé » : on garde le code SURCHARGE
            // (message propre côté mod) quand c'est la sacoche qui bloque.
            var etats = com.reborn.shinobicore.api.EtatService.get();
            var v = etats != null ? etats.verifier(id, com.reborn.shinobicore.api.etat.Capacite.MOBILITE) : null;
            if (v != null && !v.ok()) return v.ligne() != null && v.ligne().startsWith("mod.surcharge") ? SURCHARGE : ETAT;
            if (v == null && core.ko().mobilityDenial(id) != null) return ETAT;
        }
        if (core.surcharge(p)) return SURCHARGE;
        if (modifs != null && modifs.refuse(id, "MARCHE_EAU")) return ETAT;
        if (!toggles.isEnabled(c.id(), MobilityActionSlot.MARCHE_EAU)) return PAS_APPRIS;
        if (gm != GameMode.CREATIVE && c.chakra().current() <= 0.01) return CHAKRA;
        return OK;
    }

    /** Le joueur se tient-il à la surface de l'eau (pieds à la surface, pas dans l'eau) ? */
    static boolean surLEau(Player p) {
        if (p.isInWater() || p.isSwimming() || p.isFlying() || p.isInsideVehicle()) return false;
        Location l = p.getLocation();
        Block b = l.getWorld().getBlockAt(l.getBlockX(), (int) Math.floor(l.getY() - 0.06), l.getBlockZ());
        if (!eau(b)) return false;
        double h = l.getY() - b.getY();
        return h > 0.4 && h < 1.06;
    }

    private static boolean eau(Block b) {
        if (b.getType() == Material.WATER) {
            BlockData d = b.getBlockData();
            return !(d instanceof Levelled lv) || lv.getLevel() < 8;   // pas de chute d'eau
        }
        BlockData d = b.getBlockData();
        if (d instanceof Waterlogged w) return w.isWaterlogged();
        Material m = b.getType();
        return m == Material.KELP || m == Material.KELP_PLANT || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS;
    }

    /* --------------------------------------------------------------- S2C */

    private void envoyerReglages(Player p, boolean on) {
        ByteArrayOutputStream b = new ByteArrayOutputStream(24);
        try (DataOutputStream out = new DataOutputStream(b)) {
            out.writeByte(S2C_REGLAGES);
            out.writeBoolean(on);
            out.writeFloat((float) cout("immobile", 0.3));
            out.writeFloat((float) cout("marche", 0.5));
            out.writeFloat((float) cout("trot", 0.8));
            out.writeFloat((float) cout("sprint", 1.5));
        } catch (IOException ignored) {
            return;
        }
        envoyer(p, b.toByteArray());
    }

    private void envoyerPermis(Player p, boolean permis, byte raison) {
        envoyer(p, new byte[] {S2C_PERMIS, (byte) (permis ? 1 : 0), raison});
    }

    private void envoyer(Player p, byte[] data) {
        if (!p.isOnline()) return;
        try {
            p.sendPluginMessage(plugin, CHANNEL, data);
        } catch (RuntimeException ignored) {
            // canal non enregistré (arrêt en cours)
        }
    }
}
