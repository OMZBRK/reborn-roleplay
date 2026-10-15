package com.reborn.shinobicore.meteo;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService;
import com.reborn.shinobicore.api.KoService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;

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
 * {@code /meteo <type> [intensité] [durée] [cible]} — météo visuelle du mod client (mod-hud {@code MeteoClient} :
 * brouillard, filtre plein écran, particules, sons), envoyée sur le canal {@code reborn:meteo}.
 *
 * <p><b>Types</b> : {@code clair} (0), {@code pluie} (1), {@code sable} (2, vent de sable), {@code brume} (3),
 * {@code blizzard} (4, tempête de neige) et {@code tempetesable} (5, tempête de sable dense). Les alias
 * {@code /blizzard}, {@code /tempetedeneige} et {@code /tempetedesable} lancent directement le type correspondant.
 *
 * <p><b>Intensité</b> : 0-100, {@code leger} (25), {@code moyen} (55), {@code fort}/{@code tempete} (90), ou pour le
 * blizzard et la tempête de sable une <b>visibilité en blocs</b> {@code v8} (convertie par la courbe
 * {@link #visibilite(float)} que le mod applique à l'identique).
 *
 * <p><b>Durée</b> (facultative) : {@code 30s}, {@code 10m}, {@code 2h} ; à l'échéance la météo posée s'efface.
 *
 * <p><b>Cible</b> : {@code tous} (défaut : météo globale, efface les autres), {@code monde} (le monde de l'émetteur),
 * {@code ici [rayon]} (une zone ronde autour de l'émetteur, fondue sur son bord), ou un joueur. Priorité : joueur &gt;
 * zone &gt; monde &gt; globale. {@code /meteo clair ici} retire les zones où l'on se trouve, {@code /meteo clair monde}
 * la météo du monde, {@code /meteo liste} affiche ce qui est posé.
 *
 * <p><b>Paquet</b> = {@code byte type} + {@code float intensité} 0..1 (DataOutputStream, big-endian) — contrat miroir de
 * {@code MeteoPayload} côté mod (un mod plus ancien ignore les types 4 et 5). Renvoyé à la reconnexion et à chaque
 * changement de la météo effective d'un joueur (il entre dans une zone…). Rien n'est sauvegardé au redémarrage.
 *
 * <p><b>Effets de jeu</b> (facultatifs, {@code meteo.effets.*}) : ralentissement à découvert dans le blizzard (défaut
 * -8 % à 100 %), rien pour la tempête de sable. Chaque pose est <b>journalisée</b> ({@code STAFF / METEO}).
 */
public final class MeteoCommand implements TabExecutor, Listener {

    public static final String CHANNEL = "reborn:meteo";
    public static final byte CLAIR = 0, PLUIE = 1, SABLE = 2, BRUME = 3, BLIZZARD = 4, TEMPETE_SABLE = 5;
    private static final List<String> TYPES = List.of("clair", "pluie", "sable", "brume", "blizzard", "tempetesable");
    private static final List<String> NOMS = List.of("clair", "pluie", "vent de sable", "brume", "blizzard",
            "tempête de sable");
    private static final NamespacedKey CLE_LENTEUR = new NamespacedKey("reborn", "meteo");

    /** Une météo posée : type, intensité 0..1, échéance (ms epoch, 0 = sans fin). */
    private record Meteo(byte type, float intensite, long fin) {
        boolean expiree(long now) { return fin > 0 && now >= fin; }
    }

    /** Zone ronde (plan horizontal) avec sa météo. */
    private record Zone(int id, UUID monde, double x, double z, double rayon, Meteo meteo) {}

    private final ShinobiCore plugin;
    private Meteo globale = new Meteo(CLAIR, 0f, 0L);
    private final Map<UUID, Meteo> mondes = new HashMap<>();
    private final List<Zone> zones = new ArrayList<>();
    private final Map<UUID, Meteo> individuelles = new HashMap<>();
    /** Dernière météo effective envoyée à chaque joueur : {type, intensité}. */
    private final Map<UUID, float[]> envoyees = new HashMap<>();
    private final Map<UUID, Double> lenteurs = new HashMap<>();
    private int prochainId = 1;

    public MeteoCommand(ShinobiCore plugin) {
        this.plugin = plugin;
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL)) m.registerOutgoingPluginChannel(plugin, CHANNEL);
        // Résolution de la météo effective (zones, échéances, effets) deux fois par seconde.
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 10L);
    }

    /* ------------------------------------------------------------------ commande */

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String l = label.toLowerCase(Locale.ROOT);
        if (l.contains(":")) l = l.substring(l.indexOf(':') + 1);
        // /blizzard, /tempetedeneige, /tempetedesable : le type est dans le nom de la commande
        Byte impose = switch (l) {
            case "blizzard", "tempetedeneige" -> BLIZZARD;
            case "tempetedesable" -> TEMPETE_SABLE;
            default -> null;
        };
        if (impose == null && args.length >= 1 && (args[0].equalsIgnoreCase("liste") || args[0].equalsIgnoreCase("list"))) {
            liste(sender);
            return true;
        }
        int debut = impose != null ? 0 : 1;
        Byte type = impose != null ? impose : args.length >= 1 ? type(args[0]) : null;
        if (type == null) {
            sender.sendMessage("§cUsage : /" + label + " <pluie|sable|brume|blizzard|tempetesable|clair> [intensité 0-100 | "
                    + "leger | moyen | fort | v<blocs>] [durée 30s|10m|2h] [tous|monde|ici [rayon]|joueur] — /" + label + " liste");
            return true;
        }
        float intensite = type == CLAIR ? 0f : 0.8f;
        long dureeMs = 0L;
        String cible = "tous";
        double rayon = plugin.getConfig().getDouble("meteo.zone-rayon-defaut", 150.0);
        boolean intensiteLue = false, dureeLue = false, cibleLue = false;
        for (int i = debut; i < args.length; i++) {
            String a = args[i];
            if (!intensiteLue && type != CLAIR) {
                Float n = niveau(a, type);
                if (n != null) { intensite = n; intensiteLue = true; continue; }
            }
            if (!dureeLue) {
                Long d = duree(a);
                if (d != null) { dureeMs = d; dureeLue = true; continue; }
            }
            if (!cibleLue) {
                cible = a;
                cibleLue = true;
                if ((a.equalsIgnoreCase("ici") || a.equalsIgnoreCase("zone")) && i + 1 < args.length) {
                    try { rayon = Math.max(8, Math.min(2000, Double.parseDouble(args[i + 1]))); i++; }
                    catch (NumberFormatException ignored) { }
                }
                continue;
            }
            sender.sendMessage("§cArgument inattendu : " + a);
            return true;
        }
        long fin = dureeMs > 0 ? System.currentTimeMillis() + dureeMs : 0L;
        Meteo meteo = new Meteo(type, intensite, fin);
        String desc = description(meteo, dureeMs);
        String portee;
        Location lieu = sender instanceof Player sp ? sp.getLocation() : null;

        switch (cible.toLowerCase(Locale.ROOT)) {
            case "tous", "all", "global", "partout" -> {
                globale = meteo;
                mondes.clear();
                zones.clear();
                individuelles.clear();
                portee = "partout";
            }
            case "monde", "world" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cDepuis la console : « tous » ou un joueur."); return true; }
                if (type == CLAIR && dureeMs == 0) mondes.remove(p.getWorld().getUID());
                else mondes.put(p.getWorld().getUID(), meteo);
                portee = "monde " + p.getWorld().getName();
            }
            case "ici", "zone" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cDepuis la console : « tous » ou un joueur."); return true; }
                Location c = p.getLocation();
                if (type == CLAIR) {
                    int avant = zones.size();
                    zones.removeIf(z -> z.monde().equals(c.getWorld().getUID()) && distance(z, c) <= z.rayon());
                    portee = (avant - zones.size()) + " zone(s) retirée(s)";
                } else {
                    Zone z = new Zone(prochainId++, c.getWorld().getUID(), c.getX(), c.getZ(), rayon, meteo);
                    zones.add(z);
                    portee = "zone n°" + z.id() + " (rayon " + Math.round(rayon) + ")";
                }
            }
            default -> {
                Player p = com.reborn.shinobicore.character.PlayerIds.online(sender, cible);
                if (p == null) { sender.sendMessage("§cJoueur introuvable : " + cible); return true; }
                individuelles.put(p.getUniqueId(), meteo);   // « clair » = beau temps forcé pour ce joueur
                portee = "joueur " + p.getName();
                lieu = p.getLocation();
            }
        }
        sender.sendMessage("§aMétéo : §f" + desc + " §7— " + portee);
        journaliser(sender, desc + " · " + portee, lieu);
        tick();   // envoi immédiat
        return true;
    }

    private void liste(CommandSender s) {
        long now = System.currentTimeMillis();
        s.sendMessage("§6Météo globale : §f" + description(globale, globale.fin() > 0 ? globale.fin() - now : 0));
        for (var e : mondes.entrySet()) {
            World w = Bukkit.getWorld(e.getKey());
            s.sendMessage("§6Monde " + (w == null ? "?" : w.getName()) + " : §f" + description(e.getValue(), reste(e.getValue(), now)));
        }
        for (Zone z : zones) {
            World w = Bukkit.getWorld(z.monde());
            s.sendMessage("§6Zone n°" + z.id() + " §7(" + (w == null ? "?" : w.getName()) + ", rayon " + Math.round(z.rayon())
                    + ") : §f" + description(z.meteo(), reste(z.meteo(), now)));
        }
        for (var e : individuelles.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            s.sendMessage("§6Joueur " + (p == null ? e.getKey() : p.getName()) + " : §f" + description(e.getValue(), reste(e.getValue(), now)));
        }
    }

    private static long reste(Meteo m, long now) { return m.fin() > 0 ? Math.max(0, m.fin() - now) : 0; }

    private static String description(Meteo m, long dureeMs) {
        String s = NOMS.get(m.type());
        if (m.type() != CLAIR) {
            s += " " + Math.round(m.intensite() * 100) + " %";
            if (m.type() == BLIZZARD || m.type() == TEMPETE_SABLE) s += " (visibilité ~" + Math.round(visibilite(m.intensite())) + " blocs)";
        }
        if (dureeMs > 0) s += " pendant " + texteDuree(dureeMs);
        return s;
    }

    private static String texteDuree(long ms) {
        long s = Math.max(1, Math.round(ms / 1000.0));
        if (s % 3600 == 0) return (s / 3600) + " h";
        if (s >= 60 && s % 60 == 0) return (s / 60) + " min";
        if (s > 90) return (s / 60) + " min " + (s % 60) + " s";
        return s + " s";
    }

    private void journaliser(CommandSender sender, String detail, Location lieu) {
        try {
            JournalService j = plugin.journal();
            if (j == null) return;
            JournalService.Entree e = j.entree(JournalService.Categorie.STAFF, "METEO").acteur(sender).detail(detail);
            if (lieu != null) e.lieu(lieu);
            e.ecrire();
        } catch (RuntimeException ignored) {
            // journal indisponible (démarrage) : la commande reste valable
        }
    }

    /* ------------------------------------------------------------------ analyse des arguments */

    private static Byte type(String s) {
        String n = java.text.Normalizer.normalize(s.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace("_", "").replace("-", "");
        return switch (n) {
            case "clair", "beau", "aucune" -> CLAIR;
            case "pluie", "orage" -> PLUIE;
            case "sable", "ventdesable", "vent" -> SABLE;
            case "brume", "brouillard" -> BRUME;
            case "blizzard", "neige", "tempetedeneige", "tempeteneige" -> BLIZZARD;
            case "tempetesable", "tempetedesable", "simoun" -> TEMPETE_SABLE;
            default -> null;
        };
    }

    /**
     * Intensité : un pourcentage 0-100, un niveau nommé (leger 25, moyen 55, fort / tempete 90) ou, pour le blizzard et
     * la tempête de sable, une visibilité en blocs ({@code v8}, {@code 8b}).
     */
    private static Float niveau(String s, byte type) {
        String t = s.toLowerCase(Locale.ROOT);
        switch (t) {
            case "leger", "léger", "faible" -> { return 0.25f; }
            case "moyen" -> { return 0.55f; }
            case "fort", "tempete", "tempête" -> { return 0.9f; }
            case "max", "extreme", "extrême" -> { return 1f; }
            default -> { }
        }
        if (type == BLIZZARD || type == TEMPETE_SABLE) {
            String b = t.startsWith("v") ? t.substring(1) : t.endsWith("blocs") ? t.substring(0, t.length() - 5)
                    : t.endsWith("b") ? t.substring(0, t.length() - 1) : null;
            if (b != null) {
                try { return intensitePourVisibilite(Float.parseFloat(b.replace(',', '.'))); }
                catch (NumberFormatException ignored) { return null; }
            }
        }
        try {
            return Math.max(0, Math.min(100, Integer.parseInt(t))) / 100f;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Durée : {@code 30s}, {@code 10m} / {@code 10min}, {@code 2h} ; null si ce n'en est pas une. */
    private static Long duree(String s) {
        var m = java.util.regex.Pattern.compile("^(\\d{1,5})(s|sec|m|min|h)$").matcher(s.toLowerCase(Locale.ROOT));
        if (!m.matches()) return null;
        long n = Long.parseLong(m.group(1));
        return switch (m.group(2)) {
            case "s", "sec" -> n * 1000L;
            case "m", "min" -> n * 60_000L;
            default -> n * 3_600_000L;
        };
    }

    /**
     * Visibilité (blocs) du blizzard et de la tempête de sable selon l'intensité — <b>même courbe que le mod</b>
     * ({@code MeteoClient.visibilite}) : 100 % → 6 blocs, 90 % → ~8, 75 % → 12, 55 % → ~21, 25 % → ~48.
     */
    public static float visibilite(float i) {
        return (float) (6.0 * Math.exp(K_VIS * (1.0 - Math.max(0f, Math.min(1f, i)))));
    }

    private static final double K_VIS = 4.0 * Math.log(2.0);   // 12 blocs à 75 %

    public static float intensitePourVisibilite(float blocs) {
        double b = Math.max(6.0, Math.min(96.0, blocs));
        return (float) Math.max(0.0, Math.min(1.0, 1.0 - Math.log(b / 6.0) / K_VIS));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String debut = args[args.length - 1].toLowerCase(Locale.ROOT);
        String l = alias.toLowerCase(Locale.ROOT);
        int decal = (l.equals("blizzard") || l.equals("tempetedeneige") || l.equals("tempetedesable")) ? 1 : 0;
        int pos = args.length + decal;
        if (pos == 1) {
            out.addAll(TYPES);
            out.add("liste");
        } else if (pos == 2) {
            out.addAll(List.of("leger", "moyen", "fort", "100", "v8", "v12"));
        } else if (pos == 3) {
            out.addAll(List.of("5m", "15m", "1h", "tous", "monde", "ici"));
        } else {
            out.addAll(List.of("tous", "monde", "ici"));
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        }
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(debut));
        return out;
    }

    /* ------------------------------------------------------------------ résolution */

    private static double distance(Zone z, Location l) {
        double dx = l.getX() - z.x(), dz = l.getZ() - z.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Météo effective d'un joueur : {type, intensité} (fondu au bord des zones). */
    private float[] effective(Player p) {
        Meteo ind = individuelles.get(p.getUniqueId());
        if (ind != null) return new float[]{ind.type(), ind.intensite()};
        Location l = p.getLocation();
        double bord = Math.max(0.01, Math.min(0.9, plugin.getConfig().getDouble("meteo.zone-bord", 0.25)));
        Zone meilleure = null;
        float facteur = 0f;
        for (Zone z : zones) {
            if (!z.monde().equals(l.getWorld().getUID())) continue;
            double d = distance(z, l);
            if (d > z.rayon()) continue;
            // dernière posée = prioritaire (la liste est dans l'ordre de pose)
            meilleure = z;
            double u = (z.rayon() - d) / (z.rayon() * bord);           // 0 au bord, 1 à l'intérieur
            u = Math.max(0, Math.min(1, u));
            facteur = (float) (u * u * (3 - 2 * u));
        }
        if (meilleure != null && facteur > 0.02f) {
            return new float[]{meilleure.meteo().type(), meilleure.meteo().intensite() * facteur};
        }
        Meteo m = mondes.get(l.getWorld().getUID());
        if (m != null) return new float[]{m.type(), m.intensite()};
        return new float[]{globale.type(), globale.intensite()};
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (globale.expiree(now)) globale = new Meteo(CLAIR, 0f, 0L);
        mondes.values().removeIf(m -> m.expiree(now));
        zones.removeIf(z -> z.meteo().expiree(now));
        individuelles.values().removeIf(m -> m.expiree(now));

        KoService ko = Bukkit.getServicesManager().load(KoService.class);
        for (Player p : Bukkit.getOnlinePlayers()) {
            float[] eff = effective(p);
            float[] avant = envoyees.get(p.getUniqueId());
            if (avant == null || avant[0] != eff[0] || Math.abs(avant[1] - eff[1]) > 0.03f
                    || (eff[1] == 0f && avant[1] != 0f)) {
                envoyees.put(p.getUniqueId(), eff);
                if (avant != null || eff[0] != CLAIR) envoyer(p, (byte) eff[0], eff[1]);
            }
            effets(p, (byte) eff[0], eff[1], ko);
        }
    }

    /** Effets de jeu sobres : ralentissement à découvert (blizzard ; tempête de sable si réglé). */
    private void effets(Player p, byte type, float i, KoService ko) {
        double l = 0;
        if (type == BLIZZARD) l = plugin.getConfig().getDouble("meteo.effets.blizzard-lenteur", 0.08);
        else if (type == TEMPETE_SABLE) l = plugin.getConfig().getDouble("meteo.effets.tempete-sable-lenteur", 0.0);
        l = Math.max(0, Math.min(0.5, l)) * i;
        GameMode gm = p.getGameMode();
        if (l > 0.001 && (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR
                || (ko != null && ko.isOutOfPlay(p.getUniqueId())) || !decouvert(p))) l = 0;
        Double avant = lenteurs.get(p.getUniqueId());
        if (l <= 0.001) {
            if (avant != null) { retirerLenteur(p); lenteurs.remove(p.getUniqueId()); }
            return;
        }
        if (avant != null && Math.abs(avant - l) < 0.005) return;
        AttributeInstance a = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (a == null) return;
        retirerLenteur(p);
        a.addTransientModifier(new AttributeModifier(CLE_LENTEUR, -l, AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                EquipmentSlotGroup.ANY));
        lenteurs.put(p.getUniqueId(), l);
    }

    /** À découvert : rien au-dessus de la tête jusqu'au ciel. */
    private static boolean decouvert(Player p) {
        Location e = p.getEyeLocation();
        return e.getWorld().getHighestBlockYAt(e.getBlockX(), e.getBlockZ()) <= e.getBlockY();
    }

    private static void retirerLenteur(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (a == null) return;
        for (AttributeModifier m : a.getModifiers().toArray(new AttributeModifier[0])) {
            if (CLE_LENTEUR.equals(m.getKey())) a.removeModifier(m);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        envoyees.remove(p.getUniqueId());
        retirerLenteur(p);
        // délai : le client doit avoir fini d'annoncer ses canaux (sinon le message est ignoré)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            float[] eff = effective(p);
            envoyees.put(p.getUniqueId(), eff);
            if (eff[0] != CLAIR) envoyer(p, (byte) eff[0], eff[1]);
        }, 60L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        // une météo individuelle ne survit pas à la déconnexion (on retombe sur la zone, le monde ou la globale)
        individuelles.remove(id);
        envoyees.remove(id);
        if (lenteurs.remove(id) != null) retirerLenteur(e.getPlayer());
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
