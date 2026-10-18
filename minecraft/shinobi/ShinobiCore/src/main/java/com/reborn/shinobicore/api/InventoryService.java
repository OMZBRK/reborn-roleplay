package com.reborn.shinobicore.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * La sacoche RP du personnage actif (ShinobiCore {@code inventory.InventoryManager}), vue des add-ons : charge
 * (paliers de surcharge), arme équipée (étui), dons d'objets sans passer par les cases cachées de Minecraft.
 *
 * <p>Paliers (réglables, {@code inventaire.surcharge.*}) : {@link #NORMAL} ≤ 100 % du poids max, {@link #CHARGE}
 * 100-115 % (plus de sprint), {@link #SURCHARGE} 115-130 % (ni saut, ni dash, ni course aux murs : la mobilité ninja
 * est coupée), {@link #IMMOBILE} au-delà.
 */
@Stable
public interface InventoryService {

    int NORMAL = 0, CHARGE = 1, SURCHARGE = 2, IMMOBILE = 3;

    /** Palier de charge courant du joueur (0-3), recalculé à chaque changement et chaque seconde. */
    int palier(UUID playerId);

    /** La mobilité ninja (dash, double saut, course aux murs, escalade, course de chakra) est-elle coupée ? */
    default boolean mobiliteBloquee(UUID playerId) {
        return palier(playerId) >= SURCHARGE;
    }

    /**
     * L'arme du personnage : celle de l'étui (emplacement Arme), ou celle qu'il a dégainée et tient dans sa barre ;
     * {@code null} sinon. Sert à conditionner les techniques d'arme (Kenjutsu…).
     */
    ItemStack weapon(Player player);

    /** L'arme est-elle dégainée (en main / dans la barre) plutôt qu'à l'étui ? */
    boolean weaponDrawn(Player player);

    /** Donne un objet : barre 1-9, puis sac, puis au sol ; jamais dans les cases cachées 9-35 de Minecraft. */
    void give(Player player, ItemStack item);

    /**
     * L'objet porté dans un emplacement cosmétique du personnage actif ({@code "BOUCLE"}, {@code "TENUE"}…), copie,
     * ou {@code null}. Sert aux add-ons dont l'objet agit quand il est porté (oreillette de ShinobiRadio). Default null
     * for older cores.
     */
    default ItemStack cosmetique(Player player, String slot) { return null; }

    /**
     * Pose {@code item} (copie, quantité 1) dans l'emplacement cosmétique {@code slot} du personnage actif, ou le vide
     * si {@code item} est null ; persiste, rafraîchit la sacoche et rediffuse les cosmétiques. Renvoie l'objet qui s'y
     * trouvait (à rendre ou à jeter par l'appelant). Default null for older cores (rien n'est fait).
     */
    default ItemStack poserCosmetique(Player player, String slot, ItemStack item) { return null; }

    /**
     * Pourquoi la sacoche est en lecture seule (« KO », « porté », « étourdi », « mort », « gelé », « entravé »,
     * « menotté », « mode staff »…), ou {@code null}. Depuis le 17/10, complété par la matrice des états
     * ({@code EtatService}, capacité {@code MODIFIER_SACOCHE}).
     */
    String lockReason(Player player);
}
