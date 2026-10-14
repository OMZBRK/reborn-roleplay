package com.reborn.shinobicore.monnaie;

import com.reborn.shinobicore.inventory.RpWeights;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * La <b>bourse</b> (docs/gameplay/MONNAIE_ET_BANQUE.md §3) : le ryo porté est un objet. Une case, <b>sans poids</b>
 * ({@code reborn:rp_weight = 0}), une seule pile ({@code max_stack_size 1}) ; le montant est dans l'objet
 * ({@code reborn:ryo}, LONG) avec un identifiant unique ({@code reborn:bourse_id}) contre la duplication.
 *
 * <p>Visuel : {@code PAPER} habillé par l'item_model {@code reborn:bourse_plate|bourse_garnie|bourse_lourde} selon le
 * montant (seuils {@code monnaie.etats}) — provisoires du pack Nexo, l'utilisateur modélise les vrais. Le mod lit
 * {@code reborn:ryo} (custom_data, PublicBukkitValues) et peint le montant abrégé en or sur la case.
 *
 * <p>Nom : « Bourse » (item_name) ; une bourse <b>posée</b> (au sol, dans un coffre) porte aussi un nom affiché
 * « Bourse · 250 ryo » (invite « [F] Ramasser », coffre vanilla). Les lignes de description donnent le montant et
 * les gestes.
 */
public final class Bourse {

    public static final NamespacedKey RYO = new NamespacedKey("reborn", "ryo");
    public static final NamespacedKey ID = new NamespacedKey("reborn", "bourse_id");
    /** Sur l'entité au sol : qui l'a posée (UUID du compte), pour le journal du ramassage. */
    public static final NamespacedKey POSEE_PAR = new NamespacedKey("reborn", "bourse_posee_par");
    /** Sur l'entité au sol : fin du délai (ms epoch). */
    public static final NamespacedKey FIN_SOL = new NamespacedKey("reborn", "bourse_fin");

    public static final String MODELE_PLATE = "bourse_plate", MODELE_GARNIE = "bourse_garnie",
            MODELE_LOURDE = "bourse_lourde";

    private static final TextColor OR = TextColor.color(0xF2D08A);

    private Bourse() {}

    public static boolean est(ItemStack s) {
        if (s == null || s.getType().isAir() || !s.hasItemMeta()) return false;
        return s.getItemMeta().getPersistentDataContainer().has(RYO, PersistentDataType.LONG);
    }

    public static long montant(ItemStack s) {
        if (!est(s)) return 0L;
        Long v = s.getItemMeta().getPersistentDataContainer().get(RYO, PersistentDataType.LONG);
        return v == null ? 0L : Math.max(0L, v);
    }

    public static String id(ItemStack s) {
        if (!est(s)) return null;
        return s.getItemMeta().getPersistentDataContainer().get(ID, PersistentDataType.STRING);
    }

    /** Nouvelle bourse de {@code n} ryo (nouvel identifiant). {@code posee} : nom « Bourse · n ryo » (sol, coffre). */
    public static ItemStack creer(long n, boolean posee) {
        ItemStack s = new ItemStack(Material.PAPER, 1);
        ItemMeta m = s.getItemMeta();
        m.getPersistentDataContainer().set(ID, PersistentDataType.STRING, UUID.randomUUID().toString());
        s.setItemMeta(m);
        return avecMontant(s, n, posee);
    }

    /** Copie de {@code s} avec le montant {@code n} : modèle (3 états), nom, lignes, poids nul, pile unique. */
    public static ItemStack avecMontant(ItemStack s, long n, boolean posee) {
        ItemStack c = s.clone();
        c.setAmount(1);
        ItemMeta m = c.getItemMeta();
        PersistentDataContainer pdc = m.getPersistentDataContainer();
        pdc.set(RYO, PersistentDataType.LONG, Math.max(0L, n));
        if (!pdc.has(ID, PersistentDataType.STRING)) pdc.set(ID, PersistentDataType.STRING, UUID.randomUUID().toString());
        pdc.set(RpWeights.WEIGHT_KEY, PersistentDataType.DOUBLE, 0.0);
        m.setItemModel(new NamespacedKey("reborn", modele(n)));
        m.setMaxStackSize(1);
        m.itemName(Component.text("Bourse"));
        if (posee) {
            m.displayName(Component.text("Bourse · " + fmt(n) + " ryo", NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            m.displayName(null);
        }
        m.lore(List.of(
                Component.text(fmt(n) + " ryo", OR).decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true),
                Component.text("Clic droit : poser", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Glisser dehors : poser au sol", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        c.setItemMeta(m);
        return c;
    }

    /** Modèle selon le montant (seuils réglables, {@link MonnaieConfig}). */
    public static String modele(long n) {
        MonnaieConfig k = MonnaieConfig.get();
        if (n >= k.seuilLourde) return MODELE_LOURDE;
        if (n >= k.seuilGarnie) return MODELE_GARNIE;
        return MODELE_PLATE;
    }

    /** « 1 240 » (espace simple entre les milliers : la police Inter du mod n'a pas l'espace fine). */
    public static String fmt(long n) {
        String s = String.format(Locale.ROOT, "%,d", n);
        return s.replace(',', ' ');
    }

    /** Montant abrégé de la case : 750 · 1,2k · 12k · 1,2M. */
    public static String abrege(long n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 10_000) return dec(n / 100) + "k";
        if (n < 1_000_000) return (n / 1000) + "k";
        if (n < 10_000_000) return dec(n / 100_000) + "M";
        return (n / 1_000_000) + "M";
    }

    private static String dec(long dixiemes) {
        long e = dixiemes / 10, d = dixiemes % 10;
        return d == 0 ? String.valueOf(e) : e + "," + d;
    }
}
