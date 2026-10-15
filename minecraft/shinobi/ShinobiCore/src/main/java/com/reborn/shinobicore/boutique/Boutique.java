package com.reborn.shinobicore.boutique;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Une <b>boutique-lieu</b> (BOUTIQUES.md §3, §5, §9 ; décision du 15/10) : un tailleur (essayage, caméra en face du
 * personnage) ou un bazar (liste simple). Le décor est construit par les bâtisseurs ; le staff marque des
 * <b>comptoirs</b> — chacun vend une ou plusieurs <b>catégories</b> (hauts, bas, masques…) et, s'il le faut, une liste
 * précise d'articles — et des <b>estrades</b> d'essayage (place du client, la caméra est calculée en face).
 */
public final class Boutique {

    public enum Genre {
        TAILLEUR("Tailleur"), BAZAR("Bazar");

        public final String libelle;

        Genre(String libelle) { this.libelle = libelle; }

        public static Genre parse(String s) {
            if (s == null) return null;
            return switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "tailleur", "boutique", "vetements", "vêtements" -> TAILLEUR;
                case "bazar", "marchand", "epicerie", "épicerie" -> BAZAR;
                default -> null;
            };
        }

        /** Catégories d'un nouveau comptoir quand le staff n'en donne pas. */
        public List<Catalogue.Categorie> parDefaut() {
            if (this == BAZAR) return List.of(Catalogue.Categorie.VIVRES, Catalogue.Categorie.CARTES);
            return List.of(Catalogue.Categorie.TENUES, Catalogue.Categorie.HAUTS, Catalogue.Categorie.BAS,
                    Catalogue.Categorie.MANTEAUX, Catalogue.Categorie.COIFFES, Catalogue.Categorie.MASQUES,
                    Catalogue.Categorie.ACCESSOIRES);
        }
    }

    /** Un comptoir : bloc, meuble Nexo ou PNJ visé par le staff ; ses catégories et, éventuellement, ses articles. */
    public static final class Comptoir {
        final String type;   // bloc | entite
        final String monde;
        final int x, y, z;
        final UUID entite;   // PNJ (entité précise) ; null pour un bloc ou un meuble Nexo repéré par position
        String libelle;
        final List<Catalogue.Categorie> categories = new ArrayList<>();
        /** Liste précise (ids du catalogue) ; vide = tous les articles de ses catégories. */
        final List<String> articles = new ArrayList<>();

        Comptoir(String type, String monde, int x, int y, int z, UUID entite, String libelle) {
            this.type = type;
            this.monde = monde;
            this.x = x;
            this.y = y;
            this.z = z;
            this.entite = entite;
            this.libelle = libelle;
        }

        public String libelle() { return libelle; }
        public List<Catalogue.Categorie> categories() { return List.copyOf(categories); }
        public List<String> articles() { return List.copyOf(articles); }
        public String court() { return x + " " + y + " " + z; }

        public Location centre() {
            World w = Bukkit.getWorld(monde);
            return w == null ? null : new Location(w, x + 0.5, y + 0.5, z + 0.5);
        }

        boolean a(Location l, int tolerance) {
            if (l == null || l.getWorld() == null || !l.getWorld().getName().equals(monde)) return false;
            return Math.abs(l.getBlockX() - x) <= tolerance && Math.abs(l.getBlockY() - y) <= tolerance
                    && Math.abs(l.getBlockZ() - z) <= tolerance;
        }

        /** « Hauts · Bas » */
        public String resumeCategories() {
            StringBuilder b = new StringBuilder();
            for (Catalogue.Categorie c : categories) { if (!b.isEmpty()) b.append(" · "); b.append(c.libelle); }
            return b.toString();
        }
    }

    /** Une estrade d'essayage : la place du client (pieds) et la direction où il regarde (vers la caméra). */
    public record Estrade(String monde, double x, double y, double z, float yaw) {
        public Location lieu() {
            World w = Bukkit.getWorld(monde);
            return w == null ? null : new Location(w, x, y, z, yaw, 0f);
        }

        public String court() { return String.format(Locale.ROOT, "%.1f %.1f %.1f", x, y, z); }
    }

    final String id;
    String nom;
    final Genre genre;
    String village;
    String entreprise;
    boolean ouverte = true;
    /** Caméra d'essayage : distance devant le client (blocs) et hauteur du regard visé au-dessus des pieds. */
    double camDistance = 2.7, camHauteur = 1.0;
    final long creeLe;
    final String creePar;
    final List<Comptoir> comptoirs = new ArrayList<>();
    final List<Estrade> estrades = new ArrayList<>();
    /** Prix propres à cette boutique (id → ryo) ; sinon le prix du catalogue. */
    final Map<String, Long> prix = new LinkedHashMap<>();
    /** Articles que cette boutique ne vend pas, quelle que soit la catégorie du comptoir. */
    final Set<String> exclus = new LinkedHashSet<>();

    Boutique(String id, String nom, Genre genre, String village, String entreprise, long creeLe, String creePar) {
        this.id = id;
        this.nom = nom;
        this.genre = genre;
        this.village = village == null ? "" : village;
        this.entreprise = entreprise == null ? "" : entreprise;
        this.creeLe = creeLe;
        this.creePar = creePar;
    }

    public String id() { return id; }
    public String nom() { return nom; }
    public Genre genre() { return genre; }
    public String village() { return village; }
    public String entreprise() { return entreprise; }
    public boolean ouverte() { return ouverte; }
    public List<Comptoir> comptoirs() { return List.copyOf(comptoirs); }
    public List<Estrade> estrades() { return List.copyOf(estrades); }

    public long prixDe(Catalogue.Article a) {
        Long p = prix.get(a.id());
        return p != null ? p : a.prix();
    }
}
