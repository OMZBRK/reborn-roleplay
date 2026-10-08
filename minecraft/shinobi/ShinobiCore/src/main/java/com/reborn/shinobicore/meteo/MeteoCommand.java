package com.reborn.shinobicore.meteo;

import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /meteo <pluie|sable|brume|clair> [intensité 0-100 | leger | moyen | fort | tempete] [joueur|tous]} — météo visuelle du mod client
 * (mod-hud {@code MeteoClient} : filtre plein écran + brouillard), envoyée sur le canal {@code reborn:meteo}.
 *
 * <p>Corps du paquet = {@code byte type} (0 clair, 1 pluie, 2 sable, 3 brume) + {@code float intensité} 0..1
 * (DataOutputStream, big-endian) — contrat miroir de {@code MeteoPayload} côté mod. La météo est permanente (le client
 * ajoute de petites variations) jusqu'à la prochaine commande ; elle est renvoyée à la reconnexion.
 * « tous » règle la météo globale (et efface les météos individuelles) ; un joueur nommé reçoit une météo à lui.
 * Les zones (pays du Vent, de la Pluie…) viendront plus tard et pourront réutiliser {@link #envoyer}.
 */
public final class MeteoCommand implements TabExecutor, Listener {

    public static final String CHANNEL = "reborn:meteo";
    private static final List<String> TYPES = List.of("clair", "pluie", "sable", "brume");

    private final ShinobiCore plugin;
    private byte globalType = 0;
    private float globalIntensite = 0f;
    private final Map<UUID, float[]> individuelles = new HashMap<>();   // {type, intensité}

    public MeteoCommand(ShinobiCore plugin) {
        this.plugin = plugin;
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL)) m.registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || !TYPES.contains(args[0].toLowerCase(Locale.ROOT))) {
            sender.sendMessage("§cUsage : /" + label + " <pluie|sable|brume|clair> [intensité 0-100 | leger | moyen | fort | tempete] [joueur|tous]");
            return true;
        }
        byte type = (byte) TYPES.indexOf(args[0].toLowerCase(Locale.ROOT));
        float intensite = type == 0 ? 0f : 0.8f;
        int suivant = 1;
        if (args.length > 1 && type != 0) {
            Float niveau = niveau(args[1]);
            if (niveau != null) { intensite = niveau; suivant = 2; }
        } else if (args.length > 1) {
            try { Integer.parseInt(args[1]); suivant = 2; } catch (NumberFormatException ignored) { }
        }
        String cible = args.length > suivant ? args[suivant] : "tous";
        String nom = TYPES.get(type);

        if (cible.equalsIgnoreCase("tous") || cible.equalsIgnoreCase("all")) {
            globalType = type;
            globalIntensite = intensite;
            individuelles.clear();
            for (Player p : Bukkit.getOnlinePlayers()) envoyer(p, type, intensite);
            sender.sendMessage("§aMétéo globale : §f" + nom + (type == 0 ? "" : " §7(" + Math.round(intensite * 100) + " %)"));
        } else {
            Player p = Bukkit.getPlayerExact(cible);
            if (p == null) {
                sender.sendMessage("§cJoueur introuvable : " + cible);
                return true;
            }
            individuelles.put(p.getUniqueId(), new float[]{type, intensite});
            envoyer(p, type, intensite);
            sender.sendMessage("§aMétéo de §f" + p.getName() + "§a : §f" + nom
                    + (type == 0 ? "" : " §7(" + Math.round(intensite * 100) + " %)"));
        }
        return true;
    }

    /** Intensité : un pourcentage 0-100, ou un niveau nommé (leger 25, moyen 55, fort / tempete 90). */
    private static Float niveau(String s) {
        switch (s.toLowerCase(Locale.ROOT)) {
            case "leger", "léger", "faible" -> { return 0.25f; }
            case "moyen" -> { return 0.55f; }
            case "fort", "tempete", "tempête" -> { return 0.9f; }
            default -> { }
        }
        try {
            return Math.max(0, Math.min(100, Integer.parseInt(s))) / 100f;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String debut = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            out.addAll(TYPES);
        } else if (args.length == 2) {
            out.addAll(List.of("leger", "moyen", "fort", "tempete", "30", "60", "80", "100", "tous"));
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (args.length == 3) {
            out.add("tous");
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        }
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(debut));
        return out;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // délai : le client doit avoir fini d'annoncer ses canaux (sinon le message est ignoré)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            float[] perso = individuelles.get(p.getUniqueId());
            if (perso != null) envoyer(p, (byte) perso[0], perso[1]);
            else if (globalType != 0) envoyer(p, globalType, globalIntensite);
        }, 60L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // une météo individuelle ne survit pas à la déconnexion (on retombe sur la globale)
        individuelles.remove(e.getPlayer().getUniqueId());
    }

    /** Envoie la météo à un joueur (sans effet si son client n'a pas le mod). */
    public void envoyer(Player p, byte type, float intensite) {
        ByteArrayOutputStream b = new ByteArrayOutputStream(5);
        try (DataOutputStream out = new DataOutputStream(b)) {
            out.writeByte(type);
            out.writeFloat(intensite);
        } catch (IOException ex) {
            return;
        }
        try { p.sendPluginMessage(plugin, CHANNEL, b.toByteArray()); } catch (Exception ignored) { }
    }
}
