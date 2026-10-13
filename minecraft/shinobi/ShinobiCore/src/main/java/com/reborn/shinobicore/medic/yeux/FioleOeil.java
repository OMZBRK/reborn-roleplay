package com.reborn.shinobicore.medic.yeux;

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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * « Fiole d'œil » : un œil prélevé, conservé dans une fiole scellée. Un vrai objet (il se vole, s'échange, se
 * cache) : PAPER habillé par le modèle Nexo/pack {@code reborn:fiole_oeil}, données en PDC.
 *
 * <p>PDC (espace {@code reborn}) : {@code oeil_id} (UUID unique, le sceau), {@code oeil_type},
 * {@code oeil_cote} ({@code g}/{@code d}), {@code oeil_integrite} (1..100), {@code oeil_provenance} (nom du
 * personnage), {@code oeil_date} (ms epoch), {@code oeil_operateur}.
 */
public final class FioleOeil {

    public static final NamespacedKey MODEL = new NamespacedKey("reborn", "fiole_oeil");
    private static final NamespacedKey K_ID = new NamespacedKey("reborn", "oeil_id");
    private static final NamespacedKey K_TYPE = new NamespacedKey("reborn", "oeil_type");
    private static final NamespacedKey K_COTE = new NamespacedKey("reborn", "oeil_cote");
    private static final NamespacedKey K_INTEG = new NamespacedKey("reborn", "oeil_integrite");
    private static final NamespacedKey K_PROV = new NamespacedKey("reborn", "oeil_provenance");
    private static final NamespacedKey K_DATE = new NamespacedKey("reborn", "oeil_date");
    private static final NamespacedKey K_OPER = new NamespacedKey("reborn", "oeil_operateur");
    private static final NamespacedKey WEIGHT = new NamespacedKey("reborn", "rp_weight");

    /** Contenu d'une fiole. */
    public record Fiole(UUID id, String type, Cote cote, int integrite, String provenance, long date, String operateur) {}

    private FioleOeil() {}

    public static ItemStack create(String type, Cote cote, int integrite, String provenance, String operateur) {
        return create(new Fiole(UUID.randomUUID(), Oeil.normType(type), cote,
                Math.max(1, Math.min(100, integrite)), provenance == null ? "?" : provenance,
                System.currentTimeMillis(), operateur == null ? "?" : operateur));
    }

    public static ItemStack create(Fiole f) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(MODEL);
        meta.setMaxStackSize(1);
        meta.displayName(Component.text("Fiole d'œil · " + Oeil.label(f.type()) + " (" + f.cote().nom + ")",
                TextColor.color(0xC9A55A)).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(line("Intégrité : " + f.integrite() + " %", f.integrite() >= 60 ? NamedTextColor.GREEN
                : f.integrite() >= 25 ? NamedTextColor.GOLD : NamedTextColor.RED));
        lore.add(line("Provenance : " + f.provenance(), NamedTextColor.GRAY));
        lore.add(line("Prélevé le " + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(f.date())),
                NamedTextColor.GRAY));
        lore.add(line("Opérateur : " + f.operateur(), NamedTextColor.GRAY));
        lore.add(line("Sceau n° " + f.id().toString().substring(0, 8), NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(K_ID, PersistentDataType.STRING, f.id().toString());
        pdc.set(K_TYPE, PersistentDataType.STRING, f.type());
        pdc.set(K_COTE, PersistentDataType.STRING, f.cote().code);
        pdc.set(K_INTEG, PersistentDataType.INTEGER, f.integrite());
        pdc.set(K_PROV, PersistentDataType.STRING, f.provenance());
        pdc.set(K_DATE, PersistentDataType.LONG, f.date());
        pdc.set(K_OPER, PersistentDataType.STRING, f.operateur());
        pdc.set(WEIGHT, PersistentDataType.DOUBLE, 0.3);
        it.setItemMeta(meta);
        return it;
    }

    public static boolean isFiole(ItemStack it) {
        if (it == null || it.getType() != Material.PAPER || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(K_ID, PersistentDataType.STRING);
    }

    /** Contenu de la fiole, ou {@code null} si ce n'en est pas une (ou si elle est illisible). */
    public static Fiole read(ItemStack it) {
        if (!isFiole(it)) return null;
        PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();
        try {
            UUID id = UUID.fromString(pdc.get(K_ID, PersistentDataType.STRING));
            Cote cote = Cote.parse(pdc.get(K_COTE, PersistentDataType.STRING));
            if (cote == null) return null;
            Integer integ = pdc.get(K_INTEG, PersistentDataType.INTEGER);
            Long date = pdc.get(K_DATE, PersistentDataType.LONG);
            return new Fiole(id, Oeil.normType(pdc.get(K_TYPE, PersistentDataType.STRING)), cote,
                    integ == null ? 0 : integ, pdc.getOrDefault(K_PROV, PersistentDataType.STRING, "?"),
                    date == null ? 0L : date, pdc.getOrDefault(K_OPER, PersistentDataType.STRING, "?"));
        } catch (IllegalArgumentException | NullPointerException ex) {
            return null;
        }
    }

    private static Component line(String s, TextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }
}
