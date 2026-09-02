package com.reborn.shinobiabilities.techniques;

import com.reborn.shinobicore.technique.Ability;
import com.reborn.shinobicore.technique.AbilityRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * « Recueil des Techniques » — a WRITTEN_BOOK with RP prose about the
 * known arts, in the medic-encyclopedia style: one chapter per branch,
 * each listing its techniques by rank.
 */
public final class EncyclopediaBook {

    private EncyclopediaBook() {}

    public static ItemStack build(AbilityRegistry registry) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("Recueil des Techniques");
        meta.setAuthor("Les Archives Shinobi");

        List<Component> pages = new ArrayList<>();
        pages.add(Component.text()
                .append(Component.text("Recueil des\nTechniques\n\n",
                        NamedTextColor.DARK_RED, TextDecoration.BOLD))
                .append(Component.text("Compilé par les Archives.\n\n",
                        NamedTextColor.BLACK))
                .append(Component.text(
                        "Ces pages recensent les arts enseignés sur les "
                                + "étagères d'apprentissage. Chaque voie a ses "
                                + "exigences ; toutes réclament du chakra et de "
                                + "la discipline.", NamedTextColor.DARK_GRAY))
                .build());

        // Group by category leaf, preserving file order.
        Map<String, List<Ability>> byLeaf = new LinkedHashMap<>();
        for (Ability a : registry.all().values()) {
            byLeaf.computeIfAbsent(a.category(), k -> new ArrayList<>()).add(a);
        }

        for (var entry : byLeaf.entrySet()) {
            List<Ability> list = entry.getValue();
            var page = Component.text()
                    .append(Component.text(prettify(entry.getKey()) + "\n",
                            NamedTextColor.DARK_RED, TextDecoration.BOLD))
                    .append(Component.text("─────────────\n", NamedTextColor.GRAY));
            int onPage = 0;
            for (Ability a : list) {
                if (onPage == 6) {
                    pages.add(page.build());
                    page = Component.text()
                            .append(Component.text(prettify(entry.getKey()) + " (suite)\n",
                                    NamedTextColor.DARK_RED, TextDecoration.BOLD))
                            .append(Component.text("─────────────\n", NamedTextColor.GRAY));
                    onPage = 0;
                }
                page.append(Component.text(a.rank().displayName() + " ",
                                NamedTextColor.DARK_GRAY))
                        .append(Component.text(shorten(a.name()) + "\n",
                                NamedTextColor.BLACK));
                // Academy level: name + rank + a one-line gist — never the
                // chakra cost, cooldown, effects or mudra of the full sheet.
                String desc = a.description();
                if (desc != null && !desc.isBlank()) {
                    page.append(Component.text("  " + briefDesc(desc) + "\n",
                            NamedTextColor.DARK_GRAY));
                }
                onPage++;
            }
            pages.add(page.build());
            if (pages.size() >= 98) break; // hard book limit safety
        }

        meta.pages(pages);
        book.setItemMeta(meta);
        return book;
    }

    private static String prettify(String category) {
        String s = category.replace("/", " › ");
        return s.isEmpty() ? "Divers"
                : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String shorten(String name) {
        return name.length() <= 22 ? name : name.substring(0, 21) + "…";
    }

    /** One short academy-level line — no stats, just the gist. */
    private static String briefDesc(String desc) {
        String s = desc.strip().replace('\n', ' ');
        return s.length() <= 48 ? s : s.substring(0, 47) + "…";
    }
}
