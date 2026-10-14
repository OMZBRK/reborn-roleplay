package com.reborn.shinobicore.staff.outils;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.staff.panel.StaffGrades;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Outils staff à la RedLine (14/10), transposés : gel persistant, notes par N°, annonces, écran noir, séisme, mode staff
 * et plaques, consultation de la sacoche, tenue imposée, emote forcée, reset / suppression de personnage, aller /
 * ramener / retour. Partagés par les commandes ({@link OutilsCommandes}) et le Poste de garde
 * ({@code StaffPanel}). <b>Tout est journalisé</b> ({@link Journal}, catégorie {@code STAFF}).
 *
 * <p>Grades (StaffGrades) : Helper 1, Modérateur 2, Admin 3, Owner 4 ; la console passe partout.
 */
public final class OutilsStaff {

    /** Grade minimum de chaque outil (commandes et boutons du Poste de garde). */
    public static final int G_JOURNAL = StaffGrades.HELPER, G_NOTE = StaffGrades.HELPER, G_ALLER = StaffGrades.HELPER,
            G_ANNONCE_PERSO = StaffGrades.HELPER, G_MODE_STAFF = StaffGrades.HELPER,
            G_RAMENER = StaffGrades.MODO, G_GEL = StaffGrades.MODO, G_VOIR_SAC = StaffGrades.MODO,
            G_NOIR = StaffGrades.MODO, G_TENUE = StaffGrades.MODO, G_EMOTE = StaffGrades.MODO,
            G_ANNONCE = StaffGrades.MODO,
            G_PRENDRE = StaffGrades.ADMIN, G_SEISME = StaffGrades.ADMIN, G_PERSO = StaffGrades.ADMIN,
            G_NOTE_SUPPR_AUTRUI = StaffGrades.ADMIN;

    private final ShinobiCore plugin;
    private final Gels gels;
    private final NotesStaff notes;
    private final EffetsStaff effets;
    private final ModeStaff mode;
    private final ModeAnimation animation;
    private final VoirSac voirSac;
    private final TenuesForcees tenues;
    private final GestionPerso perso;
    /** Position du staff avant son dernier tp / ramener / observer ({@code /retour}). */
    private final Map<UUID, Location> retourStaff = new ConcurrentHashMap<>();
    /** Position d'une cible avant d'être ramenée ({@code /retour <cible>}). */
    private final Map<UUID, Location> retourCible = new ConcurrentHashMap<>();

    public OutilsStaff(ShinobiCore plugin) {
        this.plugin = plugin;
        this.gels = new Gels(plugin);
        this.notes = new NotesStaff(plugin);
        this.effets = new EffetsStaff(plugin);
        this.mode = new ModeStaff(plugin);
        this.animation = new ModeAnimation(plugin);
        this.voirSac = new VoirSac(plugin);
        this.tenues = new TenuesForcees(plugin);
        this.perso = new GestionPerso(plugin);
    }

    public void demarrer() {
        var pm = Bukkit.getPluginManager();
        pm.registerEvents(gels, plugin);
        pm.registerEvents(effets, plugin);
        pm.registerEvents(mode, plugin);
        pm.registerEvents(animation, plugin);
        pm.registerEvents(voirSac, plugin);
        mode.demarrer();
        OutilsCommandes exec = new OutilsCommandes(plugin, this);
        for (String c : OutilsCommandes.COMMANDES) {
            PluginCommand pc = plugin.getCommand(c);
            if (pc == null) { plugin.getLogger().warning("Commande '" + c + "' absente du plugin.yml."); continue; }
            pc.setExecutor(exec);
            pc.setTabCompleter(exec);
        }
    }

    public void arreter() {
        mode.arreter();
        animation.arreter();
        effets.arreter();
    }

    public Gels gels() { return gels; }
    public NotesStaff notes() { return notes; }
    public EffetsStaff effets() { return effets; }
    public ModeStaff mode() { return mode; }
    public ModeAnimation animation() { return animation; }
    public VoirSac voirSac() { return voirSac; }
    public TenuesForcees tenues() { return tenues; }
    public GestionPerso perso() { return perso; }

    /* ================================================================ droits */

    public static int grade(CommandSender s) { return s instanceof Player p ? StaffGrades.of(p) : StaffGrades.OWNER; }

    /** Vérifie le grade ; sinon message et false. */
    public static boolean exige(CommandSender s, int min) {
        if (grade(s) >= min) return true;
        s.sendMessage(Component.text("Ton grade ne permet pas cette action (" + StaffGrades.name(min) + " requis).",
                NamedTextColor.RED));
        return false;
    }

    /* ================================================================ déplacements */

    public boolean peutRevenir(UUID staff) { return retourStaff.containsKey(staff); }

    public boolean peutRenvoyer(UUID cible) { return retourCible.containsKey(cible); }

    /** À appeler avant toute téléportation staff (Poste de garde compris) : mémorise le point de retour. */
    public void memoriser(Player staff) { retourStaff.put(staff.getUniqueId(), staff.getLocation().clone()); }

    public void aller(Player staff, Player cible) {
        memoriser(staff);
        staff.teleport(cible.getLocation());
        Journal.entree(Categorie.STAFF, "TP_VERS").acteur(staff).cible(cible).lieu(cible.getLocation()).ecrire();
    }

    public void ramener(Player staff, Player cible) {
        retourCible.put(cible.getUniqueId(), cible.getLocation().clone());
        Location depuis = cible.getLocation().clone();
        cible.teleport(staff.getLocation());
        Journal.entree(Categorie.STAFF, "RAMENE").acteur(staff).cible(cible)
                .detail("depuis " + lieu(depuis)).lieu(staff.getLocation()).ecrire();
    }

    /** Le staff revient à son point de départ. */
    public boolean retour(Player staff) {
        Location l = retourStaff.remove(staff.getUniqueId());
        if (l == null) return false;
        staff.teleport(l);
        Journal.entree(Categorie.STAFF, "RETOUR").acteur(staff).lieu(l).ecrire();
        return true;
    }

    /** La cible ramenée retourne où elle était. */
    public boolean renvoyer(CommandSender staff, Player cible) {
        Location l = retourCible.remove(cible.getUniqueId());
        if (l == null) return false;
        cible.teleport(l);
        Journal.entree(Categorie.STAFF, "RENVOYE").acteur(staff).cible(cible).lieu(l).ecrire();
        return true;
    }

    static String lieu(Location l) {
        if (l == null || l.getWorld() == null) return "";
        return l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
    }
}
