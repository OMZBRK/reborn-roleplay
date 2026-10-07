package com.reborn.shinobicore.staff.panel;

import org.bukkit.entity.Player;

/**
 * Grades staff du Poste de garde : 0 aucun, 1 Helper, 2 Modérateur, 3 Admin, 4 Owner.
 * Lu depuis les permissions {@code reborn.staff.<grade>} (à brancher sur les groupes LuckPerms /
 * le grade du panel web) ; un op est Owner.
 */
public final class StaffGrades {

    public static final int NONE = 0, HELPER = 1, MODO = 2, ADMIN = 3, OWNER = 4;

    private StaffGrades() {}

    public static int of(Player p) {
        if (p == null) return NONE;
        if (p.isOp() || p.hasPermission("reborn.staff.owner")) return OWNER;
        if (p.hasPermission("reborn.staff.admin")) return ADMIN;
        if (p.hasPermission("reborn.staff.modo")) return MODO;
        if (p.hasPermission("reborn.staff.helper")) return HELPER;
        return NONE;
    }

    public static String name(int g) {
        return switch (g) {
            case HELPER -> "Helper";
            case MODO -> "Modérateur";
            case ADMIN -> "Admin";
            case OWNER -> "Owner";
            default -> "Joueur";
        };
    }
}
