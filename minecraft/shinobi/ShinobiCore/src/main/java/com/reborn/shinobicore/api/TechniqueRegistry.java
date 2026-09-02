package com.reborn.shinobicore.api;

import java.util.List;
import java.util.Map;

/**
 * World-agnostic technique catalog, loaded from the active world's
 * data (the Naruto pack's {@code abilities.yml} supplies jutsu).
 *
 * <p><b>Phase 3 fills this.</b> No implementation is registered yet;
 * the interface exists so readers can be written against it. Accessor
 * names mirror the current ability model ({@code id/name/category/
 * description}) so the existing type can implement {@link Technique}
 * without renames.
 */
@Stable
public interface TechniqueRegistry {

    /** Read view of one technique. World packs add their own richer model behind it. */
    interface Technique {
        String id();
        String name();
        String category();
        String description();
    }

    /** Lookup by id (case-insensitive), or null. */
    Technique byId(String id);

    /** All techniques, data-file order, keyed by id. */
    Map<String, ? extends Technique> all();

    /** Techniques whose category path starts with {@code prefix} (case-insensitive). */
    List<? extends Technique> byCategoryPrefix(String prefix);
}
