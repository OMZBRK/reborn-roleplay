package com.reborn.shinobicore.journal;

import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.character.ShinobiCharacter;
import org.bukkit.command.CommandSender;

/**
 * Journal économique : <b>chaque</b> mouvement de ryo passe par {@link ShinobiCharacter#ryoHook} et finit ici, quelle
 * qu'en soit la source (échoppe, staff, futur échange…). Le code appelant peut préciser le motif et l'acteur juste
 * avant le mouvement ({@link #contexte}) puis le retirer ({@link #fin}) ; sans contexte, le motif est « autre » et
 * l'acteur est le propriétaire du personnage.
 */
public final class RyoJournal {

    private record Ctx(String motif, CommandSender acteur) { }

    private static final ThreadLocal<Ctx> CTX = new ThreadLocal<>();

    private RyoJournal() {}

    public static void contexte(String motif, CommandSender acteur) { CTX.set(new Ctx(motif, acteur)); }

    public static void fin() { CTX.remove(); }

    static void installer() { ShinobiCharacter.ryoHook = RyoJournal::changement; }

    static void changement(ShinobiCharacter c, long avant, long apres) {
        Ctx ctx = CTX.get();
        long delta = apres - avant;
        String motif = ctx != null && ctx.motif() != null ? ctx.motif() : "autre";
        var e = Journal.entree(Categorie.ECONOMIE, delta >= 0 ? "GAIN" : "DEPENSE");
        if (ctx != null && ctx.acteur() != null) e.acteur(ctx.acteur());
        else e.acteur(c.ownerId());
        e.ciblePersonnage(c.ownerId(), c.id())
                .detail((delta >= 0 ? "+" : "") + delta + " ryo · solde " + avant + " → " + apres + " · " + motif)
                .ecrire();
    }
}
