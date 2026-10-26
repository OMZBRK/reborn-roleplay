package com.reborn.shinobicore.douane;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.character.CharacterDisplay;
import com.reborn.shinobicore.journal.Journal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>Mode examinateur</b> ({@code /modeexaminateur}, CANDIDATURE_ET_DOUANE.md §3.5), sur le modèle du mode animation :
 * l'examinateur est <b>visible</b> des candidats, porte la plaque « Examinateur », n'a <b>aucune mécanique RP</b>
 * (cadre {@code HORS_JEU.DOUANE}, comme les candidats) et <b>tout est journalisé</b> (entrée, sortie, appels,
 * questions, décisions). Grade Helper au minimum et permission {@code reborn.douane.examinateur}. Exclusif avec le
 * mode staff et le mode animation ; tombe à la déconnexion, à la sortie du monde de la douane et à la perte des droits.
 *
 * <p>Entrer mémorise la position et le mode de jeu, puis mène au hall ; sortir ramène au point de départ. À la
 * déconnexion, le retour se fait <b>avant</b> la sauvegarde du personnage : sa dernière position n'est jamais la douane.
 */
final class ModeExaminateur {

    private record Avant(Location lieu, GameMode mode, long depuis) {}

    private static final Component PLAQUE = Component.text("Examinateur", NamedTextColor.GOLD);

    private final ShinobiCore plugin;
    private final DouaneManager douane;
    private final Map<UUID, Avant> actifs = new ConcurrentHashMap<>();

    ModeExaminateur(ShinobiCore plugin, DouaneManager douane) {
        this.plugin = plugin;
        this.douane = douane;
    }

    boolean actif(UUID id) { return id != null && actifs.containsKey(id); }

    Set<UUID> actifs() { return Set.copyOf(actifs.keySet()); }

    /** Bascule ; renvoie le nouvel état. */
    boolean basculer(Player p) {
        if (actif(p.getUniqueId())) { sortir(p, true, true); return false; }
        return entrer(p);
    }

    private boolean entrer(Player p) {
        if (!douane.actif() || douane.monde() == null) {
            p.sendMessage(Component.text("La douane n'est pas active sur ce serveur.", NamedTextColor.RED));
            return false;
        }
        if (!DouaneManager.peutExaminer(p)) {
            p.sendMessage(Component.text("Réservé aux examinateurs (grade Helper au minimum et permission "
                    + DouaneManager.PERMISSION + ").", NamedTextColor.RED));
            return false;
        }
        UUID id = p.getUniqueId();
        // Même refus que le mode staff (KO, mort, porté, tenu, prison, exécution) et le gel : sinon la téléportation au
        // hall sortirait le staff de sa scène (ETATS_JOUEUR §11 n° 6). Vérifié AVANT la sortie du mode staff.
        String refus = douane.refusEtat(p);
        if (refus != null) {
            p.sendMessage(Component.text("Mode examinateur refusé : " + refus, NamedTextColor.RED));
            return false;
        }
        // Exclusifs : on sort du mode staff ou du mode animation.
        var outils = plugin.outilsStaff();
        if (outils != null) {
            if (outils.mode().actif(id)) outils.mode().basculer(p);
            if (outils.animation().actif(id)) outils.animation().basculer(p);
        }
        Location retour = douane.dansMonde(p) ? douane.retourDe(id) : p.getLocation().clone();
        douane.oublierRetour(id);
        actifs.put(id, new Avant(retour, p.getGameMode(), System.currentTimeMillis()));
        if (!douane.dansMonde(p)) p.teleport(douane.pointHall());
        p.setGameMode(GameMode.ADVENTURE);
        poserPlaque(p);
        p.sendActionBar(Component.text("Mode examinateur : visible, aucune mécanique RP, tout est journalisé.", NamedTextColor.GOLD));
        Journal.entree(Categorie.STAFF, "MODE_EXAMINATEUR").acteur(p).detail("entrée").lieu(p.getLocation()).ecrire();
        if (plugin.staffPanel() != null) plugin.staffPanel().system(p.getName() + " passe en mode examinateur.");
        douane.canal().mode(p, true);
        douane.marquerSale();
        return true;
    }

    /**
     * Sortie du mode. {@code retour} : ramène au point d'avant l'entrée si le joueur est encore dans le monde de la
     * douane.
     */
    void sortir(Player p, boolean journal, boolean retour) {
        UUID id = p.getUniqueId();
        Avant a = actifs.remove(id);
        if (a == null) return;
        douane.examinateurParti(id);
        if (a.mode() != null) p.setGameMode(a.mode());
        CharacterDisplay.apply(p, douane.actifDe(id));
        if (retour && a.lieu() != null && douane.dansMonde(p)) p.teleport(a.lieu());
        long min = (System.currentTimeMillis() - a.depuis()) / 60_000L;
        if (journal) Journal.entree(Categorie.STAFF, "MODE_EXAMINATEUR").acteur(p).detail("sortie après " + min + " min")
                .lieu(p.getLocation()).ecrire();
        if (p.isOnline()) {
            p.sendActionBar(Component.text("Mode examinateur terminé.", NamedTextColor.GRAY));
            douane.canal().mode(p, false);
        }
        if (plugin.staffPanel() != null) plugin.staffPanel().system(p.getName() + " quitte le mode examinateur.");
        douane.marquerSale();
    }

    /**
     * Toutes les secondes : droits (un examinateur rétrogradé en plein mode, grade ou permission retirés, n'encadre
     * plus le hall), exclusivité (mode staff ou animation pris entre-temps) et plaque.
     */
    void verifier() {
        var outils = plugin.outilsStaff();
        for (UUID id : List.copyOf(actifs.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) { actifs.remove(id); continue; }
            if (!DouaneManager.peutExaminer(p)) {
                sortir(p, true, true);
                p.sendMessage(Component.text("Mode examinateur retiré : tu n'as plus les droits d'examinateur (grade"
                        + " Helper au minimum et permission " + DouaneManager.PERMISSION + ").", NamedTextColor.RED));
                continue;
            }
            if (outils != null && (outils.mode().actif(id) || outils.animation().actif(id))) {
                sortir(p, true, false);
                continue;
            }
            if (!PLAQUE.equals(p.customName())) poserPlaque(p);
        }
    }

    private static void poserPlaque(Player p) {
        try { p.displayName(PLAQUE); } catch (Throwable ignored) { }
        try { p.playerListName(PLAQUE); } catch (Throwable ignored) { }
        try {
            p.customName(PLAQUE);
            p.setCustomNameVisible(true);
        } catch (Throwable ignored) { }
    }

    void arreter() {
        for (UUID id : List.copyOf(actifs.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) sortir(p, true, true);
        }
    }
}
