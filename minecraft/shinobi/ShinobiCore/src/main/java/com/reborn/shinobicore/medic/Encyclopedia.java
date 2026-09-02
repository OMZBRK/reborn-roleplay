package com.reborn.shinobicore.medic;

import com.reborn.shinobicore.ko.injury.InjuryType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the "Les Blessures Majeures" encyclopedia book — written
 * book item carrying immersive medical entries on the five injury
 * categories. Pure in-character voice: a medic's textbook, with no
 * commands, no system references, only roleplay-appropriate French
 * medical prose.
 *
 * <h2>Pagination</h2>
 * <p>Mojang books wrap text at roughly 14 lines × 19 characters per
 * page. The {@link #paginate paginator} walks paragraphs (separated
 * by {@code "\n\n"}) and packs them into pages without splitting
 * paragraphs across pages. If a single paragraph is longer than one
 * page's budget, it's split at the nearest space. Each injury entry
 * is paginated independently and headed with a coloured title page so
 * the reader sees a clean section break.
 *
 * <p>Two consumers:
 * <ul>
 *   <li>{@code /medic bookall} hands a copy directly.</li>
 *   <li>{@link MedicArmoirManager} pins a copy at the centre slot of
 *       every armoir.</li>
 * </ul>
 */
public final class Encyclopedia {

    public static final String AUTHOR = "Iryō Itadakimasu";
    public static final String TITLE  = "Les Blessures Majeures";

    /** Conservative per-page budget in raw characters. Mojang's book
     *  font is variable-width so we under-shoot to leave headroom for
     *  wide letters (m, w) that eat more pixel real estate. */
    private static final int PAGE_CHARS = 240;
    /** Hard line cap when the budget would otherwise overflow. */
    private static final int PAGE_LINES = 13;

    /** Section headers + flowing prose body for each injury, in
     *  classical medic-textbook voice. Etiology + clinical signs
     *  first, treatment protocol second. Strict French throughout. */
    private static final Map<InjuryType, String> ENTRIES =
            new EnumMap<>(InjuryType.class);

    static {
        ENTRIES.put(InjuryType.HEMATOME,
                "L'hématome naît d'un choc contondant qui déchire les "
              + "petits vaisseaux sous la peau sans rompre l'épiderme. "
              + "Le sang s'épanche dans les tissus, gonfle la zone "
              + "et la teinte d'abord en rouge sombre, puis en violet, "
              + "enfin en jaune verdâtre au fil des jours.\n\n"
              + "À l'examen, la peau reste close mais la palpation "
              + "réveille une douleur sourde, parfois battante. "
              + "Vérifie qu'aucun craquement ne trahisse une fracture "
              + "associée : un hématome profond peut masquer un os "
              + "fissuré.\n\n"
              + "Pour le traitement, applique une fine couche de Gel "
              + "d'Arnica directement sur la zone marbrée. "
              + "Masse délicatement, du centre vers la périphérie, "
              + "afin de favoriser la résorption du sang accumulé. "
              + "Renouvelle deux à trois fois par jour jusqu'à "
              + "disparition de la coloration. Le repos du membre "
              + "concerné accélère la guérison.");

        ENTRIES.put(InjuryType.BRULURE,
                "La brûlure résulte d'une agression thermique : "
              + "flamme directe, lave, ou souffle d'un Katon mal "
              + "esquivé. Les couches superficielles de la peau se "
              + "rétractent, rougissent, puis cloquent. Aux stades "
              + "graves, la chair se carbonise et perd sa sensibilité.\n\n"
              + "Le danger immédiat n'est pas la douleur, mais la "
              + "déshydratation. Une brûlure étendue laisse fuir "
              + "l'eau du corps. Une plaie ouverte, exposée à l'air, "
              + "s'infecte vite. La priorité est donc de fermer la "
              + "lésion dès que possible.\n\n"
              + "Étale une couche généreuse de Biafine sur toute la "
              + "surface brûlée. L'émulsion forme un film protecteur, "
              + "calme la sensation de cuisson et attire l'humidité "
              + "des tissus sains vers la blessure. Renouvelle "
              + "l'application toutes les quatre heures, sans frotter. "
              + "Ne perce jamais une cloque : le liquide qu'elle "
              + "contient est ton meilleur pansement naturel.");

        ENTRIES.put(InjuryType.OS_CASSE,
                "Une fracture survient lorsqu'un os subit une "
              + "contrainte supérieure à ce que sa structure peut "
              + "endurer : chute de hauteur, torsion violente, choc "
              + "direct d'un Doton, ou poids écrasant. Le membre "
              + "atteint perd sa fonction d'appui et prend souvent "
              + "une forme anormale, comme plié hors de son axe.\n\n"
              + "La douleur est immédiate, intense, et s'amplifie au "
              + "moindre mouvement. Un craquement, un grincement à la "
              + "palpation, un gonflement rapide signent le "
              + "diagnostic. Vérifie toujours la sensibilité et la "
              + "circulation en aval de la fracture : un fragment "
              + "déplacé peut comprimer un nerf ou une artère.\n\n"
              + "Maintiens le membre dans la position trouvée, sans "
              + "tenter de le redresser. Place un Plâtre autour de la "
              + "zone fracturée en prenant soin d'englober "
              + "l'articulation au-dessus et en-dessous, et laisse-le "
              + "sécher complètement avant tout déplacement. "
              + "Administre un Antalgique — paracétamol — pour "
              + "soulager la douleur durant la consolidation, qui "
              + "demande de quatre à six semaines.");

        ENTRIES.put(InjuryType.INFECTION,
                "L'infection s'installe dès qu'une plaie négligée, un "
              + "corps étranger oublié, ou un poison laissent les "
              + "bactéries proliférer. Le foyer rougit, gonfle, "
              + "devient chaud au toucher. Du pus s'écoule, parfois "
              + "verdâtre. La fièvre monte et le patient frissonne "
              + "même par temps doux.\n\n"
              + "Sans intervention rapide, l'infection gagne les "
              + "vaisseaux lymphatiques puis le sang. À ce stade, "
              + "l'état général se dégrade en quelques heures, et "
              + "les chances de récupération s'effondrent. "
              + "Reconnaître les premiers signes — chaleur locale, "
              + "douleur lancinante, traînées rouges sous la peau — "
              + "sauve des vies.\n\n"
              + "Désinfecte largement le foyer avec de la Bétadine "
              + "appliquée à la compresse, en débordant sur la peau "
              + "saine. Laisse la solution agir une minute pleine "
              + "avant de couvrir. Engage simultanément une cure "
              + "d'Amoxicilline par voie orale : une gélule complète "
              + "à intervalles réguliers, sur cinq à sept jours. "
              + "Une cure interrompue rend la bactérie plus tenace "
              + "à la prochaine attaque.");

        ENTRIES.put(InjuryType.PLAIE,
                "Une plaie est toute ouverture franche de la peau : "
              + "coupure de kunai, entaille de katana, éclat de "
              + "shuriken, morsure d'animal de garde. Le sang coule, "
              + "parfois en jets si une artère est atteinte. La "
              + "blessure laisse passer l'air, les tissus profonds, "
              + "les saletés du champ de bataille — autant de portes "
              + "ouvertes à l'infection.\n\n"
              + "Évalue d'abord la profondeur et la longueur de la "
              + "plaie. Une coupure superficielle saigne abondamment "
              + "mais ne touche que le derme. Une lacération profonde "
              + "peut atteindre le muscle, le tendon, voire l'os. "
              + "Si du sang gicle au rythme du cœur, comprime "
              + "fermement en amont de la blessure jusqu'à pouvoir "
              + "garrotter ou suturer.\n\n"
              + "Verse de la Bétadine en abondance pour désinfecter "
              + "le pourtour et l'intérieur de la plaie. Pose ensuite "
              + "une Compresse stérile contre la chair, sans la faire "
              + "glisser, puis maintiens-la avec une Bande de gaze "
              + "enroulée fermement, mais sans étrangler la "
              + "circulation. Vérifie le pansement plusieurs fois "
              + "par jour : tout suintement de pus impose un "
              + "renouvellement immédiat.");
    }

    private Encyclopedia() {}

    /** Build a fresh, fully-paginated encyclopedia book. */
    public static ItemStack build() {
        ItemStack it = new ItemStack(Material.WRITTEN_BOOK);
        if (!(it.getItemMeta() instanceof BookMeta meta)) return it;
        meta.title(Component.text(TITLE, NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true));
        meta.author(Component.text(AUTHOR, NamedTextColor.DARK_GRAY));

        List<Component> pages = new ArrayList<>();
        pages.add(introPage());
        for (InjuryType t : InjuryType.values()) {
            pages.add(titlePage(t));
            for (Component body : paginate(ENTRIES.get(t))) {
                pages.add(body);
            }
        }
        meta.pages(pages);
        it.setItemMeta(meta);
        return it;
    }

    /* ---------------------------------------------------- page builders */

    private static Component introPage() {
        Component title = Component.text(TITLE, NamedTextColor.DARK_RED)
                .decoration(TextDecoration.BOLD, true);
        Component body = Component.text(
                "\n\nLes cinq grandes catégories de blessures, et le "
              + "geste qui sauve pour chacune.\n\n"
              + "À l'attention des élèves de l'Académie médicale : "
              + "apprends ces pages par cœur. Sur le terrain, l'erreur "
              + "ne pardonne pas.",
                NamedTextColor.BLACK);
        return title.append(body);
    }

    private static Component titlePage(InjuryType t) {
        Component header = Component.text(label(t), NamedTextColor.DARK_RED)
                .decoration(TextDecoration.BOLD, true);
        Component subtitle = Component.text("\n\n" + tagline(t),
                NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, true);
        return header.append(subtitle);
    }

    /* ----------------------------------------------------- pagination */

    /** Split a long French body into pages without splitting
     *  paragraphs. Each returned {@link Component} fits within
     *  {@link #PAGE_CHARS} chars and {@link #PAGE_LINES} virtual
     *  lines. Paragraphs are separated by {@code "\n\n"} in the
     *  source and preserved in the output. If a single paragraph is
     *  too long for one page, it's broken at the nearest word
     *  boundary. */
    private static List<Component> paginate(String src) {
        List<Component> out = new ArrayList<>();
        if (src == null || src.isEmpty()) return out;
        String[] paragraphs = src.split("\n\n");
        StringBuilder current = new StringBuilder();
        int currentLines = 0;
        for (String para : paragraphs) {
            int paraLines = estimateLines(para);
            int extra = current.length() == 0 ? 0 : 2; // \n\n separator
            // If adding this paragraph would overflow, flush.
            if (current.length() > 0
                    && (current.length() + extra + para.length() > PAGE_CHARS
                        || currentLines + paraLines + 1 > PAGE_LINES)) {
                out.add(textPage(current.toString()));
                current.setLength(0);
                currentLines = 0;
            }
            // Paragraph alone bigger than one page — split at word.
            if (para.length() > PAGE_CHARS) {
                for (String chunk : splitWordSafe(para, PAGE_CHARS)) {
                    if (current.length() > 0) current.append("\n\n");
                    current.append(chunk);
                    out.add(textPage(current.toString()));
                    current.setLength(0);
                    currentLines = 0;
                }
                continue;
            }
            if (current.length() > 0) current.append("\n\n");
            current.append(para);
            currentLines += paraLines + (current.length() == para.length() ? 0 : 1);
        }
        if (current.length() > 0) out.add(textPage(current.toString()));
        return out;
    }

    /** Greedily slice {@code s} into pieces ≤ {@code budget} chars
     *  each, never splitting a word. */
    private static List<String> splitWordSafe(String s, int budget) {
        List<String> out = new ArrayList<>();
        int idx = 0;
        while (idx < s.length()) {
            if (s.length() - idx <= budget) {
                out.add(s.substring(idx).trim());
                break;
            }
            int end = idx + budget;
            // Walk back to the last space.
            int back = s.lastIndexOf(' ', end);
            if (back <= idx) back = end; // single mega-word, hard split
            out.add(s.substring(idx, back).trim());
            idx = back + 1;
        }
        return out;
    }

    /** Crude virtual-line count: assume ~19 chars per line. Newlines
     *  count as line breaks plus an extra (paragraph spacing). */
    private static int estimateLines(String s) {
        int line = 0;
        for (String ln : s.split("\n", -1)) {
            line += Math.max(1, (ln.length() + 18) / 19);
        }
        return line;
    }

    private static Component textPage(String text) {
        return Component.text(text, NamedTextColor.BLACK);
    }

    /* ----------------------------------------------------- entry meta */

    private static String label(InjuryType t) {
        return switch (t) {
            case HEMATOME  -> "Hématome";
            case BRULURE   -> "Brûlure";
            case OS_CASSE  -> "Os cassé";
            case INFECTION -> "Infection";
            case PLAIE     -> "Plaie";
        };
    }

    private static String tagline(InjuryType t) {
        return switch (t) {
            case HEMATOME  -> "Du choc à la marbrure.";
            case BRULURE   -> "Quand le feu mord la chair.";
            case OS_CASSE  -> "L'armature qui cède.";
            case INFECTION -> "L'invisible qui ronge.";
            case PLAIE     -> "La chair ouverte au monde.";
        };
    }
}
