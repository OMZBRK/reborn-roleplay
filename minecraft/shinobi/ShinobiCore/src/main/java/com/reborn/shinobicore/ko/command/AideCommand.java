package com.reborn.shinobicore.ko.command;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /aide} — un shinobi à terre ou inconscient appelle à l'aide (lot KO-2).
 * Les joueurs à portée ({@code ko.aide-portee}, 48 blocs) l'entendent et voient
 * une colonne de fumée rouge au-dessus du corps ; les médics connectés
 * ({@code shinobicore.medic}) reçoivent l'alerte où qu'ils soient.
 */
public final class AideCommand implements CommandExecutor {

    private static final long COOLDOWN_MILLIS = 30_000L;

    private final ShinobiCore plugin;
    private final Map<UUID, Long> lastCall = new HashMap<>();

    public AideCommand(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("§cEn jeu uniquement."); return true; }
        if (!plugin.ko().isKo(p.getUniqueId())) {
            p.sendMessage("§7Tu n'es pas blessé au point d'appeler à l'aide.");
            return true;
        }
        long now = System.currentTimeMillis();
        Long last = lastCall.get(p.getUniqueId());
        if (last != null && now - last < COOLDOWN_MILLIS) {
            p.sendActionBar(Component.text("Tu n'as plus le souffle… réessaie dans "
                    + ((COOLDOWN_MILLIS - (now - last)) / 1000 + 1) + " s.", NamedTextColor.GRAY));
            return true;
        }
        lastCall.put(p.getUniqueId(), now);

        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        String who = c != null ? c.name() : p.getName();
        Location at = p.getLocation();
        double range = plugin.getConfig().getDouble("ko.aide-portee", 48.0);
        double r2 = range * range;

        Component near = Component.text("✚ Un appel à l'aide résonne : " + who
                + " est à terre, tout près.", NamedTextColor.RED);
        Component medic = Component.text("✚ [Médic] " + who + " est à terre ("
                + at.getWorld().getName() + " " + at.getBlockX() + " " + at.getBlockY() + " "
                + at.getBlockZ() + ").", NamedTextColor.GOLD);

        for (Player v : plugin.getServer().getOnlinePlayers()) {
            if (v.equals(p)) continue;
            boolean inRange = v.getWorld().equals(at.getWorld()) && v.getLocation().distanceSquared(at) <= r2;
            if (v.hasPermission("shinobicore.medic")) {
                v.sendMessage(medic);
                v.playSound(v.getLocation(), Sound.BLOCK_BELL_USE, 0.6f, 1.4f);
            } else if (inRange) {
                v.sendMessage(near);
                v.playSound(at, Sound.ENTITY_PLAYER_HURT, 0.7f, 0.6f);
            }
        }
        // Colonne de fumée rouge, visible de loin.
        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(200, 30, 30), 2.0f);
        for (int i = 0; i < 24; i++) {
            at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 1 + i * 0.4, 0), 6,
                    0.15, 0.1, 0.15, 0, red);
        }
        p.sendActionBar(Component.text("Tu appelles à l'aide de toutes tes forces…", NamedTextColor.RED));
        if (plugin.staffPanel() != null) plugin.staffPanel().onAide(p);
        return true;
    }
}
