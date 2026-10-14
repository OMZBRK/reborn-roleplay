package com.reborn.shinobicore.staff.outils;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.CharacterService;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.character.PlayerIds;
import com.reborn.shinobicore.character.Rank;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.journal.RyoJournal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Suppression et remise à zéro d'un personnage par le staff ({@code /persosuppr}, {@code /persoreset}), avec
 * <b>confirmation</b> (même commande + {@code confirmer} sous 60 s) et <b>sauvegarde</b> préalable des fichiers du
 * personnage dans {@code sauvegardes/perso/<date>-<N°>-<op>/} (fichier du compte {@code characters/<compte>.yml} et
 * sacoche {@code rpbags/<perso>.yml}).
 *
 * <ul>
 *   <li><b>Reset</b> (RedLine « Reset character ») : XP, niveau, stats, techniques apprises, grade (Genin), ryo, sacoche,
 *       inventaire, blessures, apparence (à recréer). Gardés : N°, nom, clan, village, âge, taille, sexe, natures de
 *       chakra, cosmétiques du compte.</li>
 *   <li><b>Suppression</b> : le personnage disparaît, le slot est libéré ; le N° reste réservé (index « supprimé »). Si le
 *       joueur le joue, il est déconnecté avec un message.</li>
 * </ul>
 */
public final class GestionPerso {

    private record Attente(String op, UUID owner, UUID perso, long expire) { }

    private final ShinobiCore plugin;
    private final Map<String, Attente> attentes = new ConcurrentHashMap<>();

    public GestionPerso(ShinobiCore plugin) { this.plugin = plugin; }

    private static String cle(CommandSender s) { return s instanceof Player p ? p.getUniqueId().toString() : "console"; }

    /** {@code op} = "reset" ou "suppr". {@code confirmer} = l'argument « confirmer » est présent. */
    public void demander(CommandSender s, String op, String ref, boolean confirmer) {
        CharacterService.ResolvedCharacter rc = PlayerIds.character(s, ref);
        if (rc == null) { s.sendMessage(Component.text("Personnage introuvable : " + ref, NamedTextColor.RED)); return; }
        ShinobiCharacter c = rc.character();
        String qui = PlayerIds.prefix(c) + c.name();
        Attente a = attentes.get(cle(s));
        if (!confirmer || a == null || !a.op().equals(op) || !a.perso().equals(c.id()) || a.expire() < System.currentTimeMillis()) {
            attentes.put(cle(s), new Attente(op, rc.ownerId(), c.id(), System.currentTimeMillis() + 60_000L));
            s.sendMessage(Component.text(("reset".equals(op) ? "Remise à zéro" : "SUPPRESSION") + " de " + qui
                    + " (compte " + nom(rc.ownerId()) + ")", NamedTextColor.GOLD));
            s.sendMessage(Component.text("reset".equals(op)
                    ? "XP, niveau, stats, techniques, grade, ryo, sacoche, inventaire, blessures et apparence seront effacés."
                    : "Le personnage sera supprimé et son slot libéré (le N° reste réservé).", NamedTextColor.GRAY));
            s.sendMessage(Component.text("Une sauvegarde est faite avant. Pour confirmer, sous 60 s : /"
                    + ("reset".equals(op) ? "persoreset " : "persosuppr ") + ref + " confirmer", NamedTextColor.YELLOW));
            Journal.entree(Categorie.STAFF, "reset".equals(op) ? "PERSO_RESET_DEMANDE" : "PERSO_SUPPR_DEMANDE").acteur(s)
                    .ciblePersonnage(rc.ownerId(), c.id()).ecrire();
            return;
        }
        attentes.remove(cle(s));
        String dossier = sauvegarder(rc.ownerId(), c, op);
        if (dossier == null) {
            s.sendMessage(Component.text("Sauvegarde impossible : opération annulée (voir la console).", NamedTextColor.RED));
            return;
        }
        if ("reset".equals(op)) reset(s, rc.ownerId(), c, dossier);
        else supprimer(s, rc.ownerId(), c, dossier);
    }

    private void reset(CommandSender s, UUID owner, ShinobiCharacter c, String dossier) {
        Player online = Bukkit.getPlayer(owner);
        boolean actif = online != null && plugin.characters().getActive(owner) != null
                && plugin.characters().getActive(owner).id().equals(c.id());
        List<String> techniques = new ArrayList<>(c.knownAbilities());
        for (String t : techniques) c.forgetAbility(t);
        c.setExperience(0);
        c.setLevel(ShinobiCharacter.MIN_LEVEL);
        c.setRank(Rank.GENIN);
        c.stats().reset();
        c.setSkillPoints(0);
        RyoJournal.contexte("remise à zéro du personnage", s);
        try { c.setRyo(0); } finally { RyoJournal.fin(); }
        // Monnaie et banque (15/10) : la bourse part avec la sacoche ; le compte est vidé (le coffre va aux saisies).
        if (plugin.monnaie() != null) plugin.monnaie().banque().reinitialiser(s, c);
        c.clearInjuries();
        c.setAppearance("");
        c.clearInventorySnapshot();
        if (actif) {
            online.getInventory().clear();
            online.updateInventory();
        }
        if (plugin.rpInventory() != null) plugin.rpInventory().staffReinitialiserSac(c.id(), actif ? online : null);
        if (plugin.stats() != null) plugin.stats().respec(c.id());
        plugin.characters().save(c);
        if (actif) plugin.characters().reapplyStatsIfActive(c);
        if (actif) online.sendMessage(Component.text("Ton personnage a été remis à zéro par le staff.", NamedTextColor.GOLD));
        Journal.entree(Categorie.SYSTEME, "PERSO_RESET").acteur(s).ciblePersonnage(owner, c.id())
                .detail(techniques.size() + " technique(s) oubliée(s) · sauvegarde " + dossier).ecrire();
        Journal.entree(Categorie.STAFF, "PERSO_RESET").acteur(s).ciblePersonnage(owner, c.id()).detail("sauvegarde " + dossier).ecrire();
        if (plugin.staffPanel() != null && s instanceof Player sp) plugin.staffPanel().log(sp, "staff", "Personnage remis à zéro", PlayerIds.prefix(c) + c.name());
        s.sendMessage(Component.text(PlayerIds.prefix(c) + c.name() + " remis à zéro. Sauvegarde : " + dossier, NamedTextColor.GREEN));
    }

    private void supprimer(CommandSender s, UUID owner, ShinobiCharacter c, String dossier) {
        Player online = Bukkit.getPlayer(owner);
        boolean actif = online != null && plugin.characters().getActive(owner) != null
                && plugin.characters().getActive(owner).id().equals(c.id());
        String qui = PlayerIds.prefix(c) + c.name();
        Runnable faire = () -> {
            boolean ok = plugin.characters().delete(owner, c.id());
            if (plugin.rpInventory() != null) plugin.rpInventory().staffReinitialiserSac(c.id(), null);
            Journal.entree(Categorie.SYSTEME, "PERSO_SUPPRIME").acteur(s).ciblePersonnage(owner, c.id())
                    .detail(qui + (ok ? "" : " (introuvable au moment de supprimer)") + " · sauvegarde " + dossier).ecrire();
            Journal.entree(Categorie.STAFF, "PERSO_SUPPRIME").acteur(s).cible(owner).detail(qui + " · sauvegarde " + dossier).ecrire();
            if (plugin.staffPanel() != null && s instanceof Player sp) plugin.staffPanel().log(sp, "staff", "Personnage supprimé", qui);
            s.sendMessage(Component.text(qui + (ok ? " supprimé." : " : suppression impossible.") + " Sauvegarde : " + dossier,
                    ok ? NamedTextColor.GREEN : NamedTextColor.RED));
        };
        if (actif) {
            online.kick(Component.text("Ton personnage " + c.name() + " a été supprimé par le staff."));
            Bukkit.getScheduler().runTaskLater(plugin, faire, 5L);   // après la sauvegarde de déconnexion
        } else faire.run();
    }

    /** Copie les fichiers du personnage ; renvoie le dossier (relatif au plugin) ou null. */
    private String sauvegarder(UUID owner, ShinobiCharacter c, String op) {
        try {
            plugin.characters().saveAll(owner);
            String nom = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + "-"
                    + (c.hasPlayerId() ? com.reborn.shinobicore.character.PlayerIdRegistry.format(c.playerId()) : c.id().toString().substring(0, 8))
                    + "-" + op;
            File dest = new File(plugin.getDataFolder(), "sauvegardes/perso/" + nom);
            if (!dest.mkdirs() && !dest.isDirectory()) return null;
            File compte = new File(plugin.getDataFolder(), "characters/" + owner + ".yml");
            if (compte.isFile()) Files.copy(compte.toPath(), new File(dest, compte.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
            File sacs = plugin.rpInventory() != null ? plugin.rpInventory().staffDossierSacs() : null;
            File sac = sacs != null ? new File(sacs, c.id() + ".yml") : null;
            if (sac != null && sac.isFile()) Files.copy(sac.toPath(), new File(dest, "rpbag-" + sac.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
            return "sauvegardes/perso/" + nom;
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Sauvegarde du personnage " + c.id() + " impossible", e);
            return null;
        }
    }

    private String nom(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) return p.getName();
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n != null ? n : id.toString().substring(0, 8);
    }
}
