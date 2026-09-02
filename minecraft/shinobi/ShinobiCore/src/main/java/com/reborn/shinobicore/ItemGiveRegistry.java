package com.reborn.shinobicore;

import com.reborn.shinobicore.medic.Encyclopedia;
import com.reborn.shinobicore.medic.MedicArmoirManager;
import com.reborn.shinobicore.medic.Medicine;
import com.reborn.shinobicore.medic.MedicineItem;
import com.reborn.shinobicore.mobility.ability.GrappleItem;
import com.reborn.shinobicore.mobility.ability.ZiplineItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Central registry of every "givable" custom item the plugin ships,
 * exposed through the unified {@code /sc itemgive <name>} command.
 *
 * <p>Each entry maps a stable lowercase token to a factory that takes
 * the {@link ShinobiCore} plugin + the receiving {@link Player} and
 * returns a fresh {@link ItemStack}. Player-aware factories let us
 * register backpacks (which need a freshly-minted record per call)
 * cleanly without exposing the registration plumbing here.
 *
 * <p>The registry replaces the old per-item commands ({@code /grappin},
 * {@code /zipline}, {@code /learnshelf}, {@code /sac give},
 * {@code /medic armoir}, {@code /medic bookall}, {@code /medic give}).
 * Callers of those commands should be updated to
 * {@code /sc itemgive <token>}.
 */
public final class ItemGiveRegistry implements com.reborn.shinobicore.api.ItemGiveService {

    /** Result of a give: the freshly built stack + a receiver-facing
     *  display label, both used by {@code /sc itemgive} for the
     *  confirmation message. */
    public record GiveResult(ItemStack item, String label) {}

    private final ShinobiCore plugin;

    /** lower-case-key → entry, so {@code give()} can match the user's
     *  input regardless of how they capitalise it. The Entry keeps
     *  the original-case token for {@link #tokens()} (and therefore
     *  for tab-completion display). {@link LinkedHashMap} preserves
     *  insertion order so tab-complete groups by category — utility
     *  first, then bag, techniques shelf, medic. */
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public ItemGiveRegistry(ShinobiCore plugin) {
        this.plugin = plugin;
        registerDefaults();
    }

    /* ---------------------------------------------------------- API */

    /** Original-case tokens, in registration order. The tab completer
     *  surfaces these verbatim so admins see {@code Med_ARNICA_GEL}
     *  instead of {@code med_arnica_gel}. Matching during {@code give}
     *  is case-insensitive, so typing it lowercase still works. */
    public List<String> tokens() {
        List<String> out = new ArrayList<>(entries.size());
        for (Entry e : entries.values()) out.add(e.displayToken);
        return out;
    }

    public boolean has(String token) {
        return token != null && entries.containsKey(token.toLowerCase());
    }

    /** Build the item for {@code token} and hand it to {@code recipient}.
     *  Returns the result + display label, or {@code null} when the
     *  token is unknown. Token matching is case-insensitive. */
    public GiveResult give(Player recipient, String token) {
        if (recipient == null || token == null) return null;
        Entry e = entries.get(token.toLowerCase());
        if (e == null) return null;
        ItemStack item = e.factory.apply(plugin, recipient);
        return new GiveResult(item, e.label);
    }

    /* --------------------------------------------------------- defaults */

    /** Token prefixes that group items by category in the
     *  tab-completion list:
     *  <ul>
     *    <li>{@code Util_} — active mobility tools (grappin, zipline)</li>
     *    <li>{@code Block_} — placeable blocks (armoir, learning shelf)</li>
     *    <li>{@code Book_} — any written-book item</li>
     *    <li>{@code Med_}  — medicines</li>
     *  </ul>
     */
    private static final String UTIL  = "Util_";
    private static final String BLOCK = "Block_";
    private static final String BOOK  = "Book_";
    private static final String MED   = "Med_";

    private void registerDefaults() {
        // Mobility tools — Util_ prefix.
        register(UTIL + "grappin", "Grappin de Ninja",
                (p, r) -> GrappleItem.create(p));
        register(UTIL + "zipline", "Zipline de Ninja",
                (p, r) -> ZiplineItem.create(p));

        // Jutsu items moved out to the ShinobiAbilities plugin —
        // ShinobiCore no longer ships Jutsu_* tokens here. The new
        // plugin registers its own /sc itemgive tokens (or its own
        // /jutsugive command) at boot.

        // (Legacy Sac_ backpack tokens removed with the old chestplate-slot
        // Backpack system. The RP bag is now the tiered Sacoche, granted via
        // /sacoche give — see the inventory package.)

        // Placeable blocks — Block_ prefix groups the iron-door
        // medic armoir and the learning shelf together.
        register(BLOCK + "armoir",        "Armoire à Pharmacie",
                (p, r) -> MedicArmoirManager.armoirItem(p));
        // Block_learningshelf moved out to ShinobiAbilities.

        // The encyclopedia is a written book — Book_ prefix + the
        // in-character title to match what players actually see when
        // they hold it.
        register(BOOK + "LesBlessuresMajeursV1",
                "Encyclopédie 'Les Blessures Majeures'",
                (p, r) -> Encyclopedia.build());

        // Recipe medicines — Med_<UPPERCASE_NAME>, max-stack each.
        // The Pilule du Soldat is a special consumable, registered
        // separately below with a CamelCase token to match its
        // bespoke nature.
        for (Medicine m : Medicine.values()) {
            if (m == Medicine.PILULE_SOLDAT) continue;
            register(MED + m.name(), m.displayName(),
                    (p, r) -> MedicineItem.create(p, m,
                            m.material().getMaxStackSize()));
        }
        register(MED + "PiluleSoldat",
                Medicine.PILULE_SOLDAT.displayName(),
                (p, r) -> MedicineItem.create(p, Medicine.PILULE_SOLDAT, 1));
    }

    /**
     * Register a givable item. Public so addon plugins (ShinobiAbilities)
     * can surface their own items under {@code /sc itemgive} with full
     * tab-completion. Tokens are matched case-insensitively; keep the
     * {@code Prefix_name} convention so the dropdown stays grouped.
     */
    public void register(String token, String label,
                         BiFunction<ShinobiCore, Player, ItemStack> factory) {
        entries.put(token.toLowerCase(), new Entry(token, label, factory));
    }

    /** {@link com.reborn.shinobicore.api.ItemGiveService} registration —
     *  plugin-agnostic factory shape for dependents on the api seam. */
    @Override
    public void register(String token, String label,
                         java.util.function.Function<Player, ItemStack> factory) {
        register(token, label, (core, recipient) -> factory.apply(recipient));
    }

    /** ROULEAU → "Rouleau", FUMA → "Fuma", etc. — only the leading
     *  character is upper-cased so multi-word enum names stay
     *  readable in the tab dropdown. */
    private static String capitalise(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0))
                + s.substring(1).toLowerCase();
    }

    /* ----------------------------------------------------------- types */

    private record Entry(String displayToken,
                         String label,
                         BiFunction<ShinobiCore, Player, ItemStack> factory) {}
}
