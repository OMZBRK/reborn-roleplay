package com.reborn.shinobicore.concentration.plaque;

import java.util.Locale;
import java.util.UUID;

/**
 * Ancre d'une plaque lue en concentration (lot 4, étape B) : ce que la plaque « habille ».
 * <ul>
 *   <li>{@link Type#BLOC} : un bloc, ou une boîte de blocs (stèle de plusieurs blocs) — coin bas
 *       ({@code x, y, z}), taille ({@code dx, dy, dz}, de 1 à {@link PlaqueRegles#BOITE_MAX}) et <b>face</b> visée au
 *       sélecteur ({@code north}, {@code south}, {@code east}, {@code west}, {@code up}, {@code down} ; vide = lisible de
 *       tous côtés) : une inscription posée sur la face intérieure d'un mur ne se lit pas de dehors, face au même bloc
 *       ({@link PlaqueRegles#faceVisible}) ;</li>
 *   <li>{@link Type#ENTITE} : un meuble Nexo, un {@code ItemDisplay}, un cadre… — UUID de l'entité, plus sa dernière
 *       <b>boîte de lecture</b> connue ({@link #boiteLecture}) : centre ({@code ex, ez}), bas ({@code ey}), largeurs
 *       ({@code ew} en x, {@code ed} en z) et hauteur ({@code eh}) ;</li>
 *   <li>{@link Type#OBJET} : un objet qui porte la PDC {@code reborn:plaque = <id>} (au sol, dans un cadre, sur un
 *       présentoir) : aucune position, l'entité porteuse est cherchée autour du joueur.</li>
 * </ul>
 * Immuable ; aucune dépendance au serveur (tests unitaires).
 */
public record Ancre(Type type, String monde, int x, int y, int z, int dx, int dy, int dz,
                    UUID entite, double ex, double ey, double ez, double ew, double eh, double ed, String face) {

    /** Faces acceptées (noms des directions de Minecraft, comme le mod les envoie). */
    public static final java.util.List<String> FACES = java.util.List.of("north", "south", "east", "west", "up", "down");

    public enum Type {
        BLOC, ENTITE, OBJET;

        public String id() { return name().toLowerCase(Locale.ROOT); }

        public static Type lire(String s) {
            if (s == null) return null;
            return switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "bloc", "boite", "boîte" -> BLOC;
                case "entite", "entité", "meuble" -> ENTITE;
                case "objet", "item" -> OBJET;
                default -> null;
            };
        }
    }

    public Ancre {
        face = face(face);
    }

    /** Face normalisée : une des {@link #FACES}, sinon vide (tous côtés). */
    public static String face(String f) {
        if (f == null) return "";
        String s = f.trim().toLowerCase(Locale.ROOT);
        return FACES.contains(s) ? s : "";
    }

    public static Ancre bloc(String monde, int x, int y, int z, int dx, int dy, int dz) {
        return bloc(monde, x, y, z, dx, dy, dz, "");
    }

    public static Ancre bloc(String monde, int x, int y, int z, int dx, int dy, int dz, String face) {
        return new Ancre(Type.BLOC, monde, x, y, z, Math.max(1, dx), Math.max(1, dy), Math.max(1, dz), null, 0, 0, 0, 0, 0, 0,
                face);
    }

    /** Entité de boîte carrée en x / z (ancien format : largeur {@code w} dans les deux sens). */
    public static Ancre entite(String monde, UUID id, double ex, double ey, double ez, double w, double h) {
        return entite(monde, id, ex, ey, ez, w, h, w);
    }

    /** Entité : centre ({@code ex, ez}), bas ({@code ey}), largeur x {@code w}, hauteur {@code h}, largeur z {@code d}. */
    public static Ancre entite(String monde, UUID id, double ex, double ey, double ez, double w, double h, double d) {
        return new Ancre(Type.ENTITE, monde, (int) Math.floor(ex), (int) Math.floor(ey), (int) Math.floor(ez), 1, 1, 1, id,
                ex, ey, ez, w, h, d, "");
    }

    /** Entité dont la boîte de lecture est {@code b} ({@link #boiteLecture}). */
    public static Ancre entite(String monde, UUID id, double[] b) {
        return entite(monde, id, (b[0] + b[3]) / 2, b[1], (b[2] + b[5]) / 2, b[3] - b[0], b[4] - b[1], b[5] - b[2]);
    }

    public static Ancre objet() {
        return new Ancre(Type.OBJET, "", 0, 0, 0, 1, 1, 1, null, 0, 0, 0, 0, 0, 0, "");
    }

    /**
     * Boîte de lecture d'une entité depuis sa vraie boîte de collision ({@code min…max}) : chaque côté porté à 1 bloc au
     * moins autour du <b>centre</b> de la boîte (un {@code ItemDisplay} de meuble Nexo n'a pas de boîte ; un cadre fait
     * 1/16 d'épaisseur). En hauteur, une entité <b>accrochée</b> ({@code suspendue} : cadre, tableau) s'étend autour de
     * son centre ; les autres partent de leur bas (un objet au sol ne s'enfonce pas dans le sol). Le serveur et le mod
     * appliquent la même règle (revue du 07/11 : la boîte d'un cadre partait de son milieu et s'enfonçait dans le mur).
     */
    public static double[] boiteLecture(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                                        boolean suspendue) {
        double cx = (minX + maxX) / 2, cz = (minZ + maxZ) / 2;
        double wx = Math.max(1.0, maxX - minX), wz = Math.max(1.0, maxZ - minZ), h = Math.max(1.0, maxY - minY);
        double bas = suspendue ? (minY + maxY) / 2 - h / 2 : minY;
        return new double[]{cx - wx / 2, bas, cz - wz / 2, cx + wx / 2, bas + h, cz + wz / 2};
    }

    /** Boîte d'une entité placée en ({@code ex, ey, ez}) : au moins 1 × 1 × 1 (ancien format, carré, depuis le pied). */
    public static double[] boiteEntite(double ex, double ey, double ez, double w, double h) {
        return boiteEntite(ex, ey, ez, w, h, w);
    }

    static double[] boiteEntite(double ex, double ey, double ez, double w, double h, double d) {
        double dx = Math.max(1.0, w) / 2.0, dz = Math.max(1.0, d) / 2.0, haut = Math.max(1.0, h);
        return new double[]{ex - dx, ey, ez - dz, ex + dx, ey + haut, ez + dz};
    }

    /** Boîte de l'ancre (minX, minY, minZ, maxX, maxY, maxZ) ; null pour un objet (position portée par l'entité). */
    public double[] boite() {
        return switch (type) {
            case BLOC -> new double[]{x, y, z, x + dx, y + dy, z + dz};
            case ENTITE -> boiteEntite(ex, ey, ez, ew, eh, ed <= 0 ? ew : ed);
            case OBJET -> null;
        };
    }

    /** Volume de la boîte en blocs (journal, outil). */
    public int blocs() { return type == Type.BLOC ? dx * dy * dz : 1; }

    /** « bloc world 12 64 -30 », « boîte 3×2×1 world 11 64 -30 · face nord », « entité world 40 70 8 », « objet ». */
    public String libelle() {
        return switch (type) {
            case BLOC -> (blocs() > 1 ? "boîte " + dx + "×" + dy + "×" + dz : "bloc") + " " + monde + " " + x + " " + y + " " + z
                    + (face.isEmpty() ? "" : " · face " + faceFr(face));
            case ENTITE -> "entité " + monde + " " + (int) Math.floor(ex) + " " + (int) Math.floor(ey) + " " + (int) Math.floor(ez);
            case OBJET -> "objet";
        };
    }

    /** « nord », « sud »… (journal, outil). */
    public static String faceFr(String f) {
        return switch (face(f)) {
            case "north" -> "nord";
            case "south" -> "sud";
            case "east" -> "est";
            case "west" -> "ouest";
            case "up" -> "dessus";
            case "down" -> "dessous";
            default -> "tous côtés";
        };
    }

    /** Même ancre, nouvelle boîte d'entité (meuble déplacé, relu en ligne). */
    public Ancre position(double[] b) {
        return new Ancre(type, monde, (int) Math.floor(b[0]), (int) Math.floor(b[1]), (int) Math.floor(b[2]), dx, dy, dz, entite,
                (b[0] + b[3]) / 2, b[1], (b[2] + b[5]) / 2, b[3] - b[0], b[4] - b[1], b[5] - b[2], face);
    }
}
