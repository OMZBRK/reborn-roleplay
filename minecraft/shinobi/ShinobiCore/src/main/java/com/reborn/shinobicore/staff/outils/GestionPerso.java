package com.reborn.shinobicore.staff.outils;

import com.reborn.shinobicore.ShinobiCore;
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
import java.util.UUID;
import java.util.logging.Level;

/**
 * Remise à zéro et suppression d'un personnage par le staff, avec <b>sauvegarde</b> préalable des fichiers du
 * personnage dans {@code sauvegardes/perso/<date>-<N°>-<op>/} (fichier du compte {@code characters/<compte>.yml} et
 * sacoche {@code rpbags/<perso>.yml}). Appelé par le Poste de garde (F7 › fiche › Personnages, 15/10) : la
 * confirmation (N° retapé) et le motif obligatoire sont demandés dans le volet ; les anciennes commandes
 * {@code /persoreset} et {@code /persosuppr} ont été retirées.
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

    private final ShinobiCore plugin;

    public GestionPerso(ShinobiCore plugin) { this.plugin = plugin; }

    /**
     * Sauvegarde puis applique {@code op} ({@code "reset"} ou {@code "suppr"}) au personnage {@code c} du compte
     * {@code owner}. Le motif est inscrit au journal. Renvoie le dossier de sauvegarde, ou {@code null} si la
     * sauvegarde a échoué (rien n'est alors modifié).
     */
    public String executer(CommandSender s, String op, UUID owner, ShinobiCharacter c, String motif) {
        String dossier = sauvegarder(owner, c, op);
        if (dossier == null) return null;
        String m = motif == null || motif.isBlank() ? "" : " · motif : " + motif.trim();
        if ("reset".equals(op)) reset(s, owner, c, dossier, m);
        else supprimer(s, owner, c, dossier, m);
        return dossier;
    }

    private void reset(CommandSender s, UUID owner, ShinobiCharacter c, String dossier, String motif) {
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
                .detail(techniques.size() + " technique(s) oubliée(s) · sauvegarde " + dossier + motif).ecrire();
        Journal.entree(Categorie.STAFF, "PERSO_RESET").acteur(s).ciblePersonnage(owner, c.id()).detail("sauvegarde " + dossier + motif).ecrire();
        if (plugin.staffPanel() != null && s instanceof Player sp) plugin.staffPanel().log(sp, "staff", "Personnage remis à zéro", PlayerIds.prefix(c) + c.name() + motif);
    }

    private void supprimer(CommandSender s, UUID owner, ShinobiCharacter c, String dossier, String motif) {
        Player online = Bukkit.getPlayer(owner);
        boolean actif = online != null && plugin.characters().getActive(owner) != null
                && plugin.characters().getActive(owner).id().equals(c.id());
        String qui = PlayerIds.prefix(c) + c.name();
        Runnable faire = () -> {
            boolean ok = plugin.characters().delete(owner, c.id());
            if (plugin.rpInventory() != null) plugin.rpInventory().staffReinitialiserSac(c.id(), null);
            Journal.entree(Categorie.SYSTEME, "PERSO_SUPPRIME").acteur(s).ciblePersonnage(owner, c.id())
                    .detail(qui + (ok ? "" : " (introuvable au moment de supprimer)") + " · sauvegarde " + dossier + motif).ecrire();
            Journal.entree(Categorie.STAFF, "PERSO_SUPPRIME").acteur(s).cible(owner).detail(qui + " · sauvegarde " + dossier + motif).ecrire();
            if (plugin.staffPanel() != null && s instanceof Player sp) plugin.staffPanel().log(sp, "staff", "Personnage supprimé", qui + motif);
            if (!ok) s.sendMessage(Component.text(qui + " : suppression impossible. Sauvegarde : " + dossier, NamedTextColor.RED));
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
}
