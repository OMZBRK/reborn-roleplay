package com.reborn.shinobicore.entreprise;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Une <b>entreprise</b> (docs/gameplay/ENTREPRISES.md) : un nom, un type, un responsable, des membres avec un rôle et
 * des postes de travail (blocs ou meubles marqués par le staff). Créée et gérée par le staff dans un premier temps ;
 * plus tard, le gérant la fera vivre depuis le menu Faction / entreprise (non codé).
 */
public final class Entreprise {

    /** Le type dit ce que l'entreprise permet ; RESTAURANT = cuisiner aux fourneaux (GAMEPLAN §12, §27). */
    public enum Type {
        RESTAURANT("Restaurant", "fourneau"),
        FORGE("Forge", "enclume"),
        ECHOPPE("Échoppe", "comptoir"),
        /** Banque (15/10, MONNAIE_ET_BANQUE.md §5) : ses membres sont guichetiers ; postes = guichets. */
        BANQUE("Banque", "guichet");

        public final String libelle, poste;

        Type(String libelle, String poste) {
            this.libelle = libelle;
            this.poste = poste;
        }

        public static Type parse(String s) {
            if (s == null) return null;
            String n = java.text.Normalizer.normalize(s.trim(), java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
            for (Type t : values()) if (t.name().equals(n)) return t;
            return null;
        }
    }

    public enum Role {
        GERANT("gérant"), EMPLOYE("employé");

        public final String libelle;

        Role(String libelle) { this.libelle = libelle; }

        public static Role parse(String s) {
            if (s == null) return null;
            String n = java.text.Normalizer.normalize(s.trim(), java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
            return switch (n) {
                case "GERANT", "GERANTE", "CHEF", "PATRON" -> GERANT;
                case "EMPLOYE", "EMPLOYEE", "COMMIS" -> EMPLOYE;
                default -> null;
            };
        }
    }

    /** Un membre = un <b>personnage</b> (pas un compte) ; nom et N° gardés pour l'affichage hors ligne. */
    public record Membre(UUID perso, UUID compte, String nom, int numero, Role role) {}

    /** Un poste de travail : un bloc (ou le bloc d'un meuble Nexo) ; libellé libre (« fourneau gauche »). */
    public record Poste(String monde, int x, int y, int z, String libelle) {
        public boolean proche(Location l, int tolerance) {
            if (l == null || l.getWorld() == null || !l.getWorld().getName().equals(monde)) return false;
            return Math.abs(l.getBlockX() - x) <= tolerance && Math.abs(l.getBlockY() - y) <= tolerance
                    && Math.abs(l.getBlockZ() - z) <= tolerance;
        }

        public Location centre(World w) {
            return new Location(w, x + 0.5, y + 0.5, z + 0.5);
        }

        public String court() { return x + " " + y + " " + z; }
    }

    final String id;
    String nom;
    final Type type;
    UUID responsable;
    final long creeLe;
    final String creePar;
    final Map<UUID, Membre> membres = new LinkedHashMap<>();
    final List<Poste> postes = new ArrayList<>();

    Entreprise(String id, String nom, Type type, UUID responsable, long creeLe, String creePar) {
        this.id = id;
        this.nom = nom;
        this.type = type;
        this.responsable = responsable;
        this.creeLe = creeLe;
        this.creePar = creePar;
    }

    public String id() { return id; }

    public String nom() { return nom; }

    public Type type() { return type; }

    public UUID responsable() { return responsable; }

    public long creeLe() { return creeLe; }

    public String creePar() { return creePar; }

    public List<Membre> membres() { return List.copyOf(membres.values()); }

    public Membre membre(UUID perso) { return perso == null ? null : membres.get(perso); }

    public List<Poste> postes() { return List.copyOf(postes); }
}
