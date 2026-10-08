package com.reborn.shinobiabilities.parchemin;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;

/**
 * Une bibliothèque posée par le staff : où elle est, ses réglages de tirage, et le tirage en cours (partagé : un
 * rouleau pris disparaît pour tout le monde jusqu'au tirage suivant).
 */
public final class Library {

    /** Modèles de départ, indices D C B A S (docs/PROPOSITION_PARCHEMINS.md §3.1). */
    public static final double[] PUBLIQUE = {35, 12, 3, 0.3, 0.02};
    public static final double[] RESERVEE = {30, 18, 7, 1.0, 0.08};
    public static final double[] PRIVEE = {20, 22, 12, 3.0, 0.3};

    public final String id;
    public String name;
    public String world;
    public int x, y, z;
    public int count = 6;
    public int delayHours = 3;
    public int model = 0;
    public double[] byRank = PUBLIQUE.clone();
    public final Map<String, Double> overrides = new HashMap<>();

    /** Tirage en cours : identifiant de technique par case (null = vide), identifiant du rouleau à créer. */
    public String[] cells = new String[9];
    public String[] scrollIds = new String[9];
    public long nextRoll = 0L;

    public Library(String id) {
        this.id = id;
    }

    public boolean at(Location l) {
        return l.getWorld() != null && l.getWorld().getName().equals(world)
                && l.getBlockX() == x && l.getBlockY() == y && l.getBlockZ() == z;
    }

    public double distanceSq(Location l) {
        if (l.getWorld() == null || !l.getWorld().getName().equals(world)) return Double.MAX_VALUE;
        double dx = l.getX() - (x + 0.5), dy = l.getY() - (y + 0.5), dz = l.getZ() - (z + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    public static double[] model(int m) {
        return (m == 1 ? RESERVEE : m == 2 ? PRIVEE : PUBLIQUE).clone();
    }
}
