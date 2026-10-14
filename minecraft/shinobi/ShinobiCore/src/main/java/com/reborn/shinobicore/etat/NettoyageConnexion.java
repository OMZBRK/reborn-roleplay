package com.reborn.shinobicore.etat;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.staff.StaffBuildManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * <b>Nettoyage à la connexion</b> (ETATS_JOUEUR §2.4 n° 5, §9 étape 0) : les états tenus en mémoire (scène de mort,
 * sélection de personnage, cinématique, mode staff, monde intérieur, porteur, gorge, stades de bijū, écran noir,
 * construction) laissent des <b>drapeaux vanilla dans le NBT</b> du joueur quand le serveur tombe. À la connexion,
 * aucun de ces états n'existe encore : on efface donc
 * <ul>
 *   <li>{@code Invulnerable} (cinq écrivains) ;</li>
 *   <li>les potions <b>infinies</b> ou de plus de 30 min qui gênent (lenteur, cécité, obscurité, nausée, faiblesse,
 *       fatigue, saut bloqué), et les potions infinies « ambiantes » (stades de bijū) ;</li>
 *   <li>une vitesse de marche à 0 (cinématique) ;</li>
 *   <li>le créatif d'un mode construction resté marqué ({@link StaffBuildManager#marqueConstruction}).</li>
 * </ul>
 * Priorité {@code LOWEST} : les systèmes qui rétablissent un état au retour (KO, écran noir, verrou sans personnage,
 * sélection) passent après et reposent leurs propres effets.
 */
public final class NettoyageConnexion implements Listener {

    /** Au-delà, une potion d'un de ces types est un reste d'état (les vraies durées de jeu sont courtes). */
    private static final int LONGUE = 20 * 60 * 30;
    private static final Set<PotionEffectType> GENANTS = Set.of(
            PotionEffectType.SLOWNESS, PotionEffectType.BLINDNESS, PotionEffectType.DARKNESS,
            PotionEffectType.NAUSEA, PotionEffectType.WEAKNESS, PotionEffectType.MINING_FATIGUE,
            PotionEffectType.JUMP_BOOST);

    private final ShinobiCore plugin;

    public NettoyageConnexion(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent ev) {
        Player p = ev.getPlayer();
        List<String> fait = new ArrayList<>();
        if (p.isInvulnerable()) {
            p.setInvulnerable(false);
            fait.add("invulnérable");
        }
        for (PotionEffect pe : List.copyOf(p.getActivePotionEffects())) {
            if (reste(pe.getType(), pe.isInfinite(), pe.getDuration(), pe.isAmbient())) {
                p.removePotionEffect(pe.getType());
                fait.add(pe.getType().getKey().getKey() + (pe.isInfinite() ? " ∞" : ""));
            }
        }
        if (p.getWalkSpeed() <= 0.001f) {
            p.setWalkSpeed(0.2f);
            fait.add("vitesse de marche 0");
        }
        if (StaffBuildManager.marqueConstruction(p)) {
            if (p.getGameMode() == GameMode.CREATIVE) p.setGameMode(GameMode.SURVIVAL);
            fait.add("créatif (construction)");
        }
        if (fait.isEmpty()) return;
        plugin.getLogger().info("[etats] nettoyage à la connexion de " + p.getName() + " : " + String.join(", ", fait));
        Journal.entree(Categorie.SYSTEME, "NETTOYAGE_CONNEXION").acteur(p)
                .detail("restes d'états après un arrêt : " + String.join(", ", fait)).ecrire();
    }

    /** Règle pure : cet effet est-il un reste d'état à effacer ? */
    static boolean reste(PotionEffectType type, boolean infini, int duree, boolean ambiant) {
        if (GENANTS.contains(type)) return infini || duree > LONGUE;
        return infini && ambiant;
    }
}
