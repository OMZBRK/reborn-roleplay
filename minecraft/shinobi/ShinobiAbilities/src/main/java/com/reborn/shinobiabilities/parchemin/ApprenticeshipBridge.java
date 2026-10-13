package com.reborn.shinobiabilities.parchemin;

import com.reborn.shinobiabilities.techniques.ParcheminItems;
import com.reborn.shinobicore.api.ApprenticeshipService;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.technique.Ability;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pont {@link ApprenticeshipService} (API de ShinobiCore) → {@link SeanceService} :
 * la fiche shinobi lit ici les apprentissages en cours, le maître à portée,
 * le droit d'enseigner (sceau 師) et la date d'apprentissage sur parchemin.
 * Lecture seule : rien n'est modifié.
 */
public final class ApprenticeshipBridge implements ApprenticeshipService {

    private final SeanceService seances;
    private final ScrollCatalog catalog;

    public ApprenticeshipBridge(SeanceService seances, ScrollCatalog catalog) {
        this.seances = seances;
        this.catalog = catalog;
    }

    @Override
    public List<Learning> inProgress(Player p, ShinobiCharacter c) {
        List<Learning> out = new ArrayList<>();
        if (c == null) return out;
        List<String> pending = seances.pendingOf(c.id());
        for (Map.Entry<String, SeanceService.Progress> e : seances.progressOf(c.id()).entrySet()) {
            String id = e.getKey();
            if (c.knowsAbility(id) || pending.contains(id)) continue;
            SeanceService.Progress pr = e.getValue();
            int total = total(id, pr.done());
            long next = pr.last() <= 0 ? 0L : pr.last() + seances.cooldownMs();
            out.add(new Learning(id, Math.min(pr.done(), total), total, next, false, carries(p, id)));
        }
        for (String id : pending) {
            if (c.knowsAbility(id)) continue;
            int total = total(id, 1);
            out.add(new Learning(id, total, total, 0L, true, carries(p, id)));
        }
        // Les plus avancés d'abord, puis par id (ordre stable).
        out.sort(Comparator.comparingDouble((Learning l) -> -(l.required() <= 0 ? 0 : l.done() / (double) l.required()))
                .thenComparing(Learning::techId));
        return out;
    }

    @Override
    public boolean masterNearby(Player p, String techId) {
        return p != null && seances.masterNear(p, techId) != null;
    }

    @Override
    public boolean canTeach(ShinobiCharacter c, String techId) {
        return seances.isMaster(c, techId);
    }

    @Override
    public Long learnedAt(UUID charId, String techId) {
        return seances.learnedAt(charId, techId);
    }

    private int total(String techId, int fallback) {
        Ability a = catalog.byId(techId);
        return a == null ? Math.max(1, fallback) : ScrollCatalog.seances(ScrollCatalog.rank(a));
    }

    /** Le rouleau de la technique est-il dans l'inventaire du joueur ? */
    private static boolean carries(Player p, String techId) {
        if (p == null) return false;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && techId.equals(ParcheminItems.abilityIdOf(it))) return true;
        }
        return false;
    }
}
