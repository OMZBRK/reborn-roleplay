package com.reborn.shinobicore.etat;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.etat.Capacite;
import com.reborn.shinobicore.api.etat.Etat;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>L'applicateur unique des effets physiques</b> (docs/technique/ETATS_JOUEUR.md §7.5, étape 5) : à partir de l'état
 * agrégé, un seul modificateur de vitesse ({@code reborn:etat_vitesse}) et un seul de saut ({@code reborn:etat_saut}),
 * tous deux <b>transitoires</b> (rien en NBT), la faim tenue à 6 (une seule règle : douleur, ATA) et le refus du
 * sprint vanilla (= le trot de Reborn) quand {@code TROTTER} est refusé.
 *
 * <p>Remplace les écritures directes de la douleur ({@code shinobicore:ko_douleur}), de la surcharge
 * ({@code reborn:surcharge}), du portage ({@code shinobicore:porteur}, {@code tient_gorge}, {@code execute}), des
 * lenteurs du KO et de l'ATA, de l'étourdi et de la garde (ShinobiCombat) — chacun ne fait plus que dire son état.
 * L'allure ({@code reborn:allure}) et la course ninja restent à part (prédiction du client) ; tous les modificateurs
 * {@code MULTIPLY_SCALAR_1} se multiplient entre eux.
 *
 * <p>Interrupteur : {@code applicateur: false} dans {@code etats.yml} rend la main aux anciens écrivains (repli).
 */
public final class Applicateur implements Listener {

    public static final NamespacedKey CLE_VITESSE = new NamespacedKey("reborn", "etat_vitesse");
    public static final NamespacedKey CLE_SAUT = new NamespacedKey("reborn", "etat_saut");

    private static volatile Applicateur instance;

    private final ShinobiCore plugin;
    private final EtatServiceImpl service;
    /** Dernières valeurs posées, pour n'écrire l'attribut qu'au changement. */
    private final Map<UUID, double[]> poses = new ConcurrentHashMap<>();
    private final Set<UUID> affames = ConcurrentHashMap.newKeySet();
    /** Potions posées par des modificateurs, par joueur (type → amplificateur). */
    private final Map<UUID, Map<PotionEffectType, Integer>> posees = new ConcurrentHashMap<>();
    private static final int DUREE_POTION = 40;

    Applicateur(ShinobiCore plugin, EtatServiceImpl service) {
        this.plugin = plugin;
        this.service = service;
        instance = this;
    }

    /** Vrai si le service pose lui-même vitesse, saut, faim et invulnérabilité (les anciens écrivains se taisent). */
    public static boolean actif() {
        Applicateur a = instance;
        return a != null && a.service.applicateurActif();
    }

    /** Faim tenue à 6 pour ce joueur (lue par la satiété, qui garde sinon la faim vanilla pleine). */
    public static boolean faimTenue(UUID id) {
        Applicateur a = instance;
        return a != null && a.affames.contains(id);
    }

    /** Réapplication immédiate (après une transition, sans attendre le tick). */
    public static void reappliquer(ShinobiCore plugin, Player p) {
        Applicateur a = instance;
        if (a == null || p == null || !a.service.applicateurActif()) return;
        try {
            a.appliquer(p, a.service.calculer(p.getUniqueId()));
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("[etats] applicateur : " + ex);
        }
    }

    /** Applique l'état (appelé chaque tick par {@link EtatSuivi}). */
    void appliquer(Player p, Etat e) {
        if (!service.applicateurActif()) { retirer(p); return; }
        double v = e.capacites().vitesse(), s = e.capacites().saut();
        double[] prec = poses.get(p.getUniqueId());
        if (prec == null || Math.abs(prec[0] - v) > 1e-4 || Math.abs(prec[1] - s) > 1e-4) {
            poses.put(p.getUniqueId(), new double[] {v, s});
            poser(p.getAttribute(Attribute.MOVEMENT_SPEED), CLE_VITESSE, v);
            poser(p.getAttribute(Attribute.JUMP_STRENGTH), CLE_SAUT, s);
        }
        UUID id = p.getUniqueId();
        if (e.capacites().faimTenue()) {
            affames.add(id);
            p.removePotionEffect(PotionEffectType.SATURATION);
            if (p.getFoodLevel() > 6) p.setFoodLevel(6);
            p.setSaturation(0f);
        } else if (affames.remove(id)) {
            p.setFoodLevel(20);
            p.setSaturation(20f);
        }
        if (!e.peut(Capacite.TROTTER) && p.isSprinting()) p.setSprinting(false);
        potions(p);
    }

    /**
     * Étape 7 : les modificateurs du registre qui déclarent des {@code potions} (effets de techniques sur un joueur :
     * aveuglé, nausée, poison…) les reçoivent en effets <b>courts rafraîchis</b> (2 s), retirés par leur source à la fin
     * du modificateur — jamais par type (un Kai, un réveil ne lèvent plus l'effet d'un autre).
     */
    private void potions(Player p) {
        var reg = service.registre();
        UUID id = p.getUniqueId();
        Map<PotionEffectType, Integer> voulues = new java.util.HashMap<>();
        if (reg != null) {
            for (var v : reg.actifs(id)) {
                var t = reg.type(v.type());
                if (t == null || t.potions().isEmpty() || v.resteMs() <= 0) continue;
                for (var e : t.potions().entrySet()) voulues.merge(e.getKey(), e.getValue(), Math::max);
            }
        }
        Map<PotionEffectType, Integer> avant = posees.getOrDefault(id, Map.of());
        for (var e : avant.entrySet()) {
            Integer amp = voulues.get(e.getKey());
            if (amp == null || !amp.equals(e.getValue())) {
                com.reborn.shinobicore.util.Effets.retirerSiSien(p, e.getKey(), e.getValue(), DUREE_POTION);
            }
        }
        for (var e : voulues.entrySet()) {
            PotionEffect cur = p.getPotionEffect(e.getKey());
            if (cur != null && cur.getAmplifier() > e.getValue()) continue;          // plus fort d'ailleurs
            if (cur != null && cur.getAmplifier() == e.getValue() && cur.getDuration() > DUREE_POTION / 2) continue;
            p.addPotionEffect(new PotionEffect(e.getKey(), DUREE_POTION, e.getValue(), true, false, false));
        }
        if (voulues.isEmpty()) posees.remove(id); else posees.put(id, voulues);
    }

    private static void poser(AttributeInstance a, NamespacedKey cle, double facteur) {
        if (a == null) return;
        try { a.removeModifier(cle); } catch (Throwable ignored) { }
        if (Math.abs(facteur - 1.0) < 1e-4) return;
        a.addTransientModifier(new AttributeModifier(cle, Math.max(-1.0, facteur - 1.0),
                AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
    }

    /** Retire tout ce que l'applicateur a posé (repli, déconnexion, arrêt). */
    void retirer(Player p) {
        if (poses.remove(p.getUniqueId()) != null) {
            AttributeInstance ms = p.getAttribute(Attribute.MOVEMENT_SPEED);
            AttributeInstance js = p.getAttribute(Attribute.JUMP_STRENGTH);
            try { if (ms != null) ms.removeModifier(CLE_VITESSE); } catch (Throwable ignored) { }
            try { if (js != null) js.removeModifier(CLE_SAUT); } catch (Throwable ignored) { }
        }
        if (affames.remove(p.getUniqueId())) {
            p.setFoodLevel(20);
            p.setSaturation(20f);
        }
    }

    void arreter() {
        for (Player p : Bukkit.getOnlinePlayers()) retirer(p);
        instance = null;
    }

    /** Pas de sprint vanilla (le trot) quand l'état refuse de trotter : douleur, ATA, KO, garde, étourdi… */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent ev) {
        if (!ev.isSprinting() || !service.applicateurActif()) return;
        if (!service.calculer(ev.getPlayer().getUniqueId()).peut(Capacite.TROTTER)) {
            ev.setCancelled(true);
            ev.getPlayer().setSprinting(false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent ev) {
        Player p = ev.getPlayer();
        poses.remove(p.getUniqueId());
        affames.remove(p.getUniqueId());
        posees.remove(p.getUniqueId());
    }
}
