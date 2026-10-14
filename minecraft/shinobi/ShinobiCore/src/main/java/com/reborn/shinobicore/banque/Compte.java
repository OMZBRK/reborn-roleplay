package com.reborn.shinobicore.banque;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * Le compte d'un personnage (MONNAIE_ET_BANQUE.md §4.2, §6) : solde (ryo à l'abri), palier, coffre d'objets, gel, état
 * de la succession à la mort RP, historique court, compteurs (virements du jour, présence de la semaine). Persisté dans
 * {@code plugins/ShinobiCore/banque/comptes.yml} par {@link BanqueStore}.
 */
public final class Compte {

    /** Une ligne d'historique : quand, variation, texte en mots. */
    public record Ligne(long le, long delta, String texte) {}

    final UUID perso;
    UUID owner;
    String nom = "";
    int numero = -1;
    String village = "";
    long solde;
    int palier = 1;
    ItemStack[] coffre = new ItemStack[0];
    /** Gel (staff, désertion) : motif, par, le ; null = libre. */
    String gelMotif, gelPar;
    long gelLe;
    /** Mort RP : date (ms), 0 = vivant ; {@code regle} = succession faite. */
    long mortLe;
    boolean regle;
    boolean migre;
    long derniereBourse;
    String virementJour = "";
    long virementTotal;
    String presenceSemaine = "";
    long presenceMinutes;
    String soldeSemaine = "";
    long soldeMontant;
    final Deque<Ligne> historique = new ArrayDeque<>();

    Compte(UUID perso) { this.perso = perso; }

    public UUID perso() { return perso; }
    public UUID owner() { return owner; }
    public String nom() { return nom; }
    public int numero() { return numero; }
    public String village() { return village; }
    public long solde() { return solde; }
    public int palier() { return palier; }
    public boolean gele() { return gelMotif != null; }
    public String gelMotif() { return gelMotif; }
    public long mortLe() { return mortLe; }
    public long derniereBourse() { return derniereBourse; }
    public Deque<Ligne> historique() { return historique; }

    /** Cases occupées du coffre. */
    public int occupees() {
        int n = 0;
        for (ItemStack s : coffre) if (s != null && !s.getType().isAir()) n++;
        return n;
    }

    public ItemStack[] coffre() { return coffre; }

    /** Agrandit (jamais ne réduit sans rendre) le coffre à {@code cases}. */
    void taille(int cases) {
        if (coffre.length >= cases) return;
        ItemStack[] n = new ItemStack[cases];
        System.arraycopy(coffre, 0, n, 0, coffre.length);
        coffre = n;
    }

    void noter(long delta, String texte) {
        historique.addFirst(new Ligne(System.currentTimeMillis(), delta, texte));
        while (historique.size() > 10) historique.removeLast();
    }
}
