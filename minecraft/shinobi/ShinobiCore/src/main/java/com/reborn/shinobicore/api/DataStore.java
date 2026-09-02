package com.reborn.shinobicore.api;

import java.util.List;
import java.util.function.Predicate;

/**
 * The engine persistence seam. Every keyed store flows through this
 * interface so a backend (per-key YAML files today, embedded SQL for
 * query-shaped data) is a swap, not a rewrite — never hand-roll a
 * second persistence path (contract §5).
 *
 * <p>{@link #query} is a full-scan filter on every backend today; it
 * exists so relational needs ("all open missions", "letters to X")
 * have a seam to live behind. A typed-query extension can widen this
 * interface when a real consumer needs indexed lookups.
 *
 * @param <K> key type (per-character stores use the character UUID)
 * @param <V> record type; records know their own key
 */
@Stable
public interface DataStore<K, V> {

    /** Record for {@code key}; loads on first touch, never null
     *  (backends create a fresh record on a miss). */
    V get(K key);

    /** Persist one record (backends may skip pristine shells). */
    void save(V value);

    /** Remove a record from the backend and the cache. True if it existed. */
    boolean delete(K key);

    /** Every key known to the backend or the cache. */
    List<K> keys();

    /** All records matching {@code filter}. Full scan — see class doc. */
    List<V> query(Predicate<V> filter);

    /** Begin background work (autosave). */
    void start();

    /** Stop background work and flush. */
    void stop();

    /** Write every dirty record now. */
    void flush();
}
