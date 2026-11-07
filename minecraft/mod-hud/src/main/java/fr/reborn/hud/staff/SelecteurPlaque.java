package fr.reborn.hud.staff;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.reborn.hud.interaction.CursorRaycast;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * <b>Sélecteur de blocs façon Axiom</b> de l'outil « Plaques » (lot 4, étape B ; planche c5) : le volet se replie en
 * bandeau, la souris reste libre et vise dans le monde depuis la caméra ({@link CursorRaycast}).
 *
 * <ul>
 *   <li>Survol : fil de fer crème sur le bloc ou l'entité visé ; gris au-delà de {@value #PORTEE} blocs des yeux
 *       (le serveur refuserait l'ancre) ;</li>
 *   <li>Clic : choisit un bloc (coin 1) ou une entité (meuble Nexo, cadre, présentoir…) ; <b>Maj + clic</b> sur un autre
 *       bloc : second coin d'une boîte (32 × 32 × 32 au plus) ; un clic simple repart d'un bloc seul ;</li>
 *   <li>Ce qui est choisi : contour d'or épais, coordonnées ; Entrée valide, Échap annule (rien ne change).</li>
 * </ul>
 * Survol relu à chaque tick (pas à chaque image) depuis la dernière position de la souris.
 */
final class SelecteurPlaque {

    /** Visée du staff (blocs depuis les yeux) : celle que le serveur vérifie, sans sa tolérance d'un bloc. */
    static final double PORTEE = 8.0;
    /** Taille maximale d'une boîte, par côté. */
    static final int BOITE_MAX = 32;
    /** Rayon de survol (au-delà de la portée : montré gris). */
    private static final double RAYON = 24.0;

    boolean actif;
    /** Pourquoi : « creer » (ancre de la nouvelle plaque) ou l'id d'une plaque à déplacer. */
    String but = "creer";

    /* survol : 0 rien, 1 bloc, 2 entité */
    int viseK;
    BlockPos viseBloc;
    UUID viseEnt;
    String viseNom = "";
    double viseDist;
    boolean viseLoin;
    final double[] viseBoite = new double[6];
    /** Face du bloc survolé (voile de 14 % dessus), null pour une entité. */
    net.minecraft.core.Direction viseFace;

    /* choix : 0 rien, 1 bloc, 2 boîte, 3 entité */
    int choixK;
    BlockPos c1, c2;
    UUID ent;
    String choixNom = "";
    final double[] choixBoite = new double[6];
    double choixDist;
    /** Face cliquée du premier coin (la plaque ne se lit que de ce côté, sauf « tous côtés » dans l'éditeur). */
    net.minecraft.core.Direction choixFace;

    void ouvrir(String but) {
        this.but = but;
        actif = true;
        viseK = 0;
    }

    void fermer() {
        actif = false;
        viseK = 0;
    }

    /** Oublie le choix (nouvelle ancre). */
    void effacer() {
        choixK = 0;
        c1 = c2 = null;
        ent = null;
        choixNom = "";
        choixFace = null;
    }

    boolean choisi() { return choixK != 0; }

    /** Survol à la position GUI ({@code gx}, {@code gy}) de la souris. */
    void survoler(Minecraft mc, double gx, double gy) {
        viseK = 0;
        viseFace = null;
        if (mc.player == null || mc.level == null) return;
        HitResult h = CursorRaycast.raycast(mc, gx, gy, RAYON);
        Vec3 eye = mc.player.getEyePosition();
        if (h instanceof EntityHitResult eh && !(eh.getEntity() instanceof Player)) {
            Entity e = eh.getEntity();
            AABB bb = boite(e);
            viseK = 2;
            viseEnt = e.getUUID();
            viseNom = nomEntite(e);
            viseBoite[0] = bb.minX; viseBoite[1] = bb.minY; viseBoite[2] = bb.minZ;
            viseBoite[3] = bb.maxX; viseBoite[4] = bb.maxY; viseBoite[5] = bb.maxZ;
        } else if (h instanceof BlockHitResult bh && h.getType() == HitResult.Type.BLOCK) {
            BlockPos p = bh.getBlockPos();
            viseK = 1;
            viseBloc = p.immutable();
            viseFace = bh.getDirection();
            viseNom = mc.level.getBlockState(p).getBlock().getName().getString();
            viseBoite[0] = p.getX(); viseBoite[1] = p.getY(); viseBoite[2] = p.getZ();
            viseBoite[3] = p.getX() + 1; viseBoite[4] = p.getY() + 1; viseBoite[5] = p.getZ() + 1;
        } else return;
        viseDist = distance(eye, viseBoite);
        viseLoin = viseDist > PORTEE;
    }

    /**
     * Boîte de lecture d'une entité, même règle que le serveur ({@code Ancre.boiteLecture}) : sa vraie boîte, chaque côté
     * porté à 1 bloc au moins autour de son centre ; en hauteur, une entité accrochée (cadre, tableau) s'étend autour de
     * son centre, les autres partent de leur bas (meuble Nexo sans boîte : 1 × 1 × 1 depuis son pied).
     */
    static AABB boite(Entity e) {
        AABB bb = e.getBoundingBox();
        boolean suspendue = e instanceof net.minecraft.world.entity.decoration.HangingEntity;
        double cx = (bb.minX + bb.maxX) / 2, cz = (bb.minZ + bb.maxZ) / 2;
        double wx = Math.max(1.0, bb.getXsize()), wz = Math.max(1.0, bb.getZsize()), h = Math.max(1.0, bb.getYsize());
        double bas = suspendue ? (bb.minY + bb.maxY) / 2 - h / 2 : bb.minY;
        return new AABB(cx - wx / 2, bas, cz - wz / 2, cx + wx / 2, bas + h, cz + wz / 2);
    }

    private static String nomEntite(Entity e) {
        String n = e.getName().getString();
        String type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        return n.isBlank() ? type : n;
    }

    static double distance(Vec3 eye, double[] b) {
        double dx = Math.max(Math.max(b[0] - eye.x, 0), eye.x - b[3]);
        double dy = Math.max(Math.max(b[1] - eye.y, 0), eye.y - b[4]);
        double dz = Math.max(Math.max(b[2] - eye.z, 0), eye.z - b[5]);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Clic dans le monde : choisit ce qui est survolé (Maj + clic sur un bloc : second coin). Renvoie le message d'erreur
     * à montrer (hors de portée, boîte trop grande), ou null.
     */
    String clic(Minecraft mc, boolean maj) {
        if (viseK == 0) return null;
        if (viseLoin) return "Hors de portée : approche-toi à " + (int) PORTEE + " blocs.";
        if (viseK == 2) {
            choixK = 3;
            ent = viseEnt;
            c1 = c2 = null;
            choixFace = null;
            choixNom = viseNom;
            System.arraycopy(viseBoite, 0, choixBoite, 0, 6);
        } else if (maj && c1 != null && choixK != 3) {
            BlockPos a = c1, b = viseBloc;
            int dx = Math.abs(a.getX() - b.getX()) + 1, dy = Math.abs(a.getY() - b.getY()) + 1, dz = Math.abs(a.getZ() - b.getZ()) + 1;
            if (dx > BOITE_MAX || dy > BOITE_MAX || dz > BOITE_MAX) return "Boîte trop grande (" + BOITE_MAX + " blocs par côté au plus).";
            c2 = b;
            choixK = dx * dy * dz > 1 ? 2 : 1;
            choixBoite[0] = Math.min(a.getX(), b.getX()); choixBoite[1] = Math.min(a.getY(), b.getY());
            choixBoite[2] = Math.min(a.getZ(), b.getZ());
            choixBoite[3] = Math.max(a.getX(), b.getX()) + 1; choixBoite[4] = Math.max(a.getY(), b.getY()) + 1;
            choixBoite[5] = Math.max(a.getZ(), b.getZ()) + 1;
        } else {
            choixK = 1;
            c1 = viseBloc;
            c2 = null;
            ent = null;
            choixNom = viseNom;
            choixFace = viseFace;
            System.arraycopy(viseBoite, 0, choixBoite, 0, 6);
        }
        if (mc.player != null) choixDist = distance(mc.player.getEyePosition(), choixBoite);
        return null;
    }

    int dx() { return (int) Math.round(choixBoite[3] - choixBoite[0]); }

    int dy() { return (int) Math.round(choixBoite[4] - choixBoite[1]); }

    int dz() { return (int) Math.round(choixBoite[5] - choixBoite[2]); }

    /**
     * Ancre au format du serveur ({@code {"k":"bloc","x","y","z","b":[…],"f"}} ou {@code {"k":"entite","e":uuid}}) ;
     * {@code f} = face cliquée si {@code face}, sinon tous côtés.
     */
    JsonObject ancreJson(boolean face) {
        JsonObject a = new JsonObject();
        if (choixK == 3) {
            a.addProperty("k", "entite");
            a.addProperty("e", ent.toString());
            return a;
        }
        a.addProperty("k", "bloc");
        a.addProperty("x", (int) Math.floor(choixBoite[0]));
        a.addProperty("y", (int) Math.floor(choixBoite[1]));
        a.addProperty("z", (int) Math.floor(choixBoite[2]));
        JsonArray b = new JsonArray();
        b.add(dx());
        b.add(dy());
        b.add(dz());
        a.add("b", b);
        if (face && choixFace != null) a.addProperty("f", choixFace.getName());
        return a;
    }

    /** Face cliquée en français (« nord »…), « tous côtés » sans face. */
    static String faceFr(net.minecraft.core.Direction f) {
        if (f == null) return "tous côtés";
        return switch (f) {
            case NORTH -> "nord";
            case SOUTH -> "sud";
            case EAST -> "est";
            case WEST -> "ouest";
            case UP -> "dessus";
            case DOWN -> "dessous";
        };
    }

    /** Aperçu de la boîte de deux coins (Maj enfoncée, bloc survolé) : faux s'il n'y a rien à montrer. */
    boolean apercuBoite(double[] out) {
        if (c1 == null || choixK == 3 || viseK != 1 || viseBloc == null || viseLoin) return false;
        BlockPos a = c1, b = viseBloc;
        out[0] = Math.min(a.getX(), b.getX()); out[1] = Math.min(a.getY(), b.getY()); out[2] = Math.min(a.getZ(), b.getZ());
        out[3] = Math.max(a.getX(), b.getX()) + 1; out[4] = Math.max(a.getY(), b.getY()) + 1; out[5] = Math.max(a.getZ(), b.getZ()) + 1;
        return out[3] - out[0] <= BOITE_MAX && out[4] - out[1] <= BOITE_MAX && out[5] - out[2] <= BOITE_MAX;
    }

    /** « Bloc · 11 64 -30 », « Boîte 3 × 2 × 1 · 6 blocs », « Meuble · lanterne ». */
    String libelleChoix() {
        return switch (choixK) {
            case 1 -> "Bloc · " + coin(choixBoite);
            case 2 -> "Boîte " + dx() + " × " + dy() + " × " + dz() + " · " + dx() * dy() * dz() + " blocs";
            case 3 -> "Entité · " + choixNom;
            default -> "rien encore";
        };
    }

    /** Ligne sous le choix : « 11 64 -30 → 13 65 -30 », « Maj + clic sur un autre bloc : boîte »… */
    String detailChoix() {
        return switch (choixK) {
            case 1 -> "Maj + clic sur un autre bloc : boîte";
            case 2 -> coin(choixBoite) + " → " + ((int) choixBoite[3] - 1) + " " + ((int) choixBoite[4] - 1) + " " + ((int) choixBoite[5] - 1);
            case 3 -> "entité · " + coin(choixBoite) + " · " + un(choixDist) + " blocs";
            default -> "";
        };
    }

    /** Type suivi par la sélection : 0 bloc, 1 boîte, 2 entité. */
    int type() {
        if (choixK == 2) return 1;
        if (choixK == 3 || choixK == 0 && viseK == 2) return 2;
        return 0;
    }

    static String coin(double[] b) {
        return (int) Math.floor(b[0]) + " " + (int) Math.floor(b[1]) + " " + (int) Math.floor(b[2]);
    }

    static String un(double v) { return String.format(java.util.Locale.FRANCE, "%.1f", v); }

    /* ================================================================ banc */

    void debugVise(Minecraft mc, BlockPos p) {
        viseK = 1;
        viseBloc = p;
        viseNom = mc.level.getBlockState(p).getBlock().getName().getString();
        viseBoite[0] = p.getX(); viseBoite[1] = p.getY(); viseBoite[2] = p.getZ();
        viseBoite[3] = p.getX() + 1; viseBoite[4] = p.getY() + 1; viseBoite[5] = p.getZ() + 1;
        viseFace = net.minecraft.core.Direction.NORTH;
        viseDist = distance(mc.player.getEyePosition(), viseBoite);
        viseLoin = viseDist > PORTEE;
    }
}
