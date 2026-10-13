package com.reborn.shinobicore.fiche;

import com.reborn.shinobicore.data.CharacterData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Journal des états de service d'un personnage — <b>append-only</b> : on ajoute,
 * on masque, on ne supprime jamais (trace pour le staff).
 */
public final class ServiceLog implements CharacterData {

    private final UUID characterId;
    private final List<ServiceEntry> entries = new ArrayList<>();
    private boolean dirty;

    public ServiceLog(UUID characterId) {
        this.characterId = characterId;
    }

    @Override public UUID characterId() { return characterId; }
    @Override public boolean dirty() { return dirty; }
    @Override public void markClean() { dirty = false; }

    /** Toutes les entrées, masquées comprises, de la plus ancienne à la plus récente. */
    public List<ServiceEntry> all() { return Collections.unmodifiableList(entries); }

    /** Entrées visibles, de la plus ancienne à la plus récente. */
    public List<ServiceEntry> visible() {
        List<ServiceEntry> out = new ArrayList<>();
        for (ServiceEntry e : entries) if (!e.hidden()) out.add(e);
        return out;
    }

    public boolean isEmpty() { return entries.isEmpty(); }

    /** Ajoute une entrée à la fin. */
    public void append(ServiceEntry e) {
        if (e == null) return;
        entries.add(e);
        dirty = true;
    }

    /**
     * Masque l'entrée d'index {@code index} (0 = la plus ancienne).
     * Faux si l'index est hors limites ou l'entrée déjà masquée.
     */
    public boolean hide(int index, String by) {
        if (index < 0 || index >= entries.size()) return false;
        ServiceEntry e = entries.get(index);
        if (e.hidden()) return false;
        entries.set(index, e.asHidden(by));
        dirty = true;
        return true;
    }

    /** Chargement depuis le disque (sans marquer sale). */
    void load(List<ServiceEntry> loaded) {
        entries.clear();
        entries.addAll(loaded);
        dirty = false;
    }
}
