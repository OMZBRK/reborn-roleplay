package fr.reborn.hud.monnaie;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Locale;

/**
 * La bourse côté mod (MONNAIE_ET_BANQUE.md §3) : le montant est dans l'objet (ShinobiCore, PDC {@code reborn:ryo} →
 * {@code custom_data.PublicBukkitValues}). Il est peint <b>abrégé en or</b> à la place du nombre de la pile
 * ({@code mixin/ItemCountBourseMixin}) : barre d'action, sacoche, coffre, guichet. Rien sur le HUD.
 */
public final class BourseClient {

    public static final int OR = 0xFFF2D08A;
    private static final String CLE = "reborn:ryo";

    private BourseClient() {}

    /** Montant porté par cette bourse, ou −1 si ce n'est pas une bourse. */
    public static long montant(ItemStack s) {
        if (s == null || s.isEmpty()) return -1;
        CustomData cd = s.get(DataComponents.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) return -1;
        CompoundTag t = cd.copyTag();
        CompoundTag pb = t.getCompoundOrEmpty("PublicBukkitValues");
        return pb.getLongOr(CLE, -1L);
    }

    public static boolean est(ItemStack s) { return montant(s) >= 0; }

    /** Marque une pile reconstruite (sacoche, guichet) comme bourse de {@code n} ryo. */
    public static void marquer(ItemStack s, long n) {
        CompoundTag pb = new CompoundTag();
        pb.putLong(CLE, n);
        CompoundTag t = new CompoundTag();
        t.put("PublicBukkitValues", pb);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    /** « 1 240 » (espace simple : la police Inter du mod n'a pas l'espace fine insécable). */
    public static String fmt(long n) {
        return String.format(Locale.ROOT, "%,d", n).replace(',', ' ');
    }

    /** 750 · 1,2k · 12k · 1,2M. */
    public static String abrege(long n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 10_000) return dec(n / 100) + "k";
        if (n < 1_000_000) return (n / 1000) + "k";
        if (n < 10_000_000) return dec(n / 100_000) + "M";
        return (n / 1_000_000) + "M";
    }

    private static String dec(long d) {
        long e = d / 10, r = d % 10;
        return r == 0 ? String.valueOf(e) : e + "," + r;
    }

    /** Peint le montant abrégé en or, calé en bas à droite de la case (comme un nombre de pile). */
    public static void dessiner(GuiGraphicsExtractor g, Font f, long n, int x, int y) {
        String s = abrege(n);
        g.pose().pushMatrix();
        g.text(f, s, x + 19 - 2 - f.width(s), y + 6 + 3, OR, true);
        g.pose().popMatrix();
    }
}
