package com.reborn.shinobicore.medic.yeux;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.AutoMe;
import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /bandage <joueur> [retirer]} — médic : pose (ou retire) un bandage sur les orbites du patient. Persiste
 * sur le personnage et renvoie la cécité ({@code reborn:yeux}, le mod adoucit l'assombrissement côté bandé).
 * Rien à voir avec l'objet « bande » ({@code BandageListener}, soin des PV).
 */
public final class BandageCommand implements TabExecutor {

    private final ShinobiCore plugin;
    private final YeuxService yeux;

    public BandageCommand(ShinobiCore plugin, YeuxService yeux) {
        this.plugin = plugin;
        this.yeux = yeux;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, @NotNull String[] a) {
        if (!(s instanceof Player medic)) { s.sendMessage("Commande de joueur."); return true; }
        boolean staff = medic.hasPermission(YeuxCommand.PERM_STAFF);
        if (!medic.hasPermission(YeuxCommand.PERM_MEDIC) && !staff) {
            msg(medic, "Seul un médic sait bander des yeux blessés.");
            return true;
        }
        if (a.length < 1) { msg(medic, "Usage : /bandage <joueur> [retirer]"); return true; }
        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null) t = plugin.characters().findOnlinePlayerByCharacter(a[0]);
        if (t == null) { bar(medic, "Personne ici."); return true; }
        if (t == medic && !staff) { msg(medic, "Tu ne peux pas te bander les yeux toi-même."); return true; }
        if (plugin.ko().isKo(medic.getUniqueId())
                || (plugin.deathScene() != null && plugin.deathScene().isDead(medic.getUniqueId()))) {
            bar(medic, "Pas dans ton état.");
            return true;
        }
        if (!staff && (!t.getWorld().equals(medic.getWorld())
                || t.getLocation().distanceSquared(medic.getLocation()) > yeux.portee() * yeux.portee())) {
            bar(medic, "Trop loin.");
            return true;
        }
        ShinobiCharacter c = plugin.characters().getActive(t.getUniqueId());
        if (c == null) { msg(medic, "Cette personne n'a pas de personnage."); return true; }
        boolean retirer = a.length >= 2 && a[1].toLowerCase(Locale.ROOT).startsWith("retir");
        if (c.yeuxBandes() == !retirer) {
            msg(medic, retirer ? "Ses yeux ne sont pas bandés." : "Ses yeux sont déjà bandés.");
            return true;
        }
        yeux.setBandage(c, !retirer);
        String who = t == medic ? "ses propres yeux" : "les yeux de " + c.name();
        AutoMe.broadcast(plugin, medic, retirer ? "retire le bandage qui couvrait " + who + "."
                : "pose un bandage sur " + who + ".");
        if (t != medic) {
            t.sendMessage(Component.text(retirer ? "On retire le bandage de tes yeux." : "On te bande les yeux.",
                    NamedTextColor.GRAY));
        }
        return true;
    }

    private static void msg(CommandSender s, String text) { s.sendMessage(Component.text(text, NamedTextColor.GRAY)); }

    private static void bar(Player p, String text) { p.sendActionBar(Component.text(text, NamedTextColor.GRAY)); }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String alias, @NotNull String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            String p = a[0].toLowerCase(Locale.ROOT);
            for (Player pl : Bukkit.getOnlinePlayers()) if (pl.getName().toLowerCase(Locale.ROOT).startsWith(p)) out.add(pl.getName());
        } else if (a.length == 2 && "retirer".startsWith(a[1].toLowerCase(Locale.ROOT))) {
            out.add("retirer");
        }
        return out;
    }
}
