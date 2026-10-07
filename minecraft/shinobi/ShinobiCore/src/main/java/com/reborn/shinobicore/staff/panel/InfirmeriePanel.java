package com.reborn.shinobicore.staff.panel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.ko.HospitalBeds;
import com.reborn.shinobicore.ko.KoState;
import com.reborn.shinobicore.ko.ata.AtaManager;
import com.reborn.shinobicore.ko.zone.TrainingZone;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Infirmerie — gestion visuelle du KO pour le staff ({@code /infirmerie}, aussi depuis le Poste de garde).
 * Même canal que le Poste de garde ({@value StaffPanel#CHANNEL}) ; actions {@code ko_*}, {@code zone_*},
 * {@code bed_*}, {@code settings_*} ; réponses {@code open_ko} et {@code ko_snap}.
 *
 * <ul>
 *   <li><b>Blessés</b> : KO (à terre, inconscient, épuisé) et ATA en direct ; réveiller, faire perdre
 *       connaissance, rapatrier, lever l'ATA, TP (Modérateur+ ; TP dès Helper) ;</li>
 *   <li><b>Zones</b> : créées et modifiées depuis la carte du mod (entraînement, repos, hôpital), contour montré
 *       en jeu avec des particules (Admin+) ;</li>
 *   <li><b>Lits</b> : mode sélection en jeu — on vise un meuble de lit et clic droit pour l'ajouter à l'hôpital,
 *       à nouveau pour le retirer, s'accroupir pour terminer (Admin+) ;</li>
 *   <li><b>Réglages</b> : durées et seuils du KO, de l'ATA et des soins, appliqués tout de suite et gardés dans
 *       {@code ko-reglages.yml} sans toucher au config.yml (Admin+).</li>
 * </ul>
 */
public final class InfirmeriePanel implements Listener {

    /** clé d'affichage → (chemin config, défaut). */
    private static final Map<String, Object[]> SETTINGS = new LinkedHashMap<>();

    static {
        SETTINGS.put("terre", new Object[] {"ko.a-terre-secondes", 45});
        SETTINGS.put("inco", new Object[] {"ko.inconscient-secondes", 300});
        SETTINGS.put("hopital", new Object[] {"ko.hopital-propose-apres", 120});
        SETTINGS.put("plancher", new Object[] {"ko.plancher-pct", 30});
        SETTINGS.put("pleine", new Object[] {"ko.ata.repos-minutes-pleine", 20});
        SETTINGS.put("allegee", new Object[] {"ko.ata.repos-minutes-allegee", 10});
        SETTINGS.put("peur", new Object[] {"ko.ata.peur-minutes", 20});
        SETTINGS.put("chuchot", new Object[] {"ko.chuchotement-blocs", 4});
        SETTINGS.put("paume", new Object[] {"medic.paume.pct-par-seconde", 15});
        SETTINGS.put("rang1", new Object[] {"medic.paume.plafond-rang1", 60});
        SETTINGS.put("paumeAta", new Object[] {"medic.paume.ata-secondes", 30});
    }

    private final ShinobiCore plugin;
    private final StaffPanel panel;
    private final File settingsFile;
    private final Map<UUID, String> picking = new ConcurrentHashMap<>();   // staff → zone hôpital
    private final Map<UUID, BukkitTask> outlines = new ConcurrentHashMap<>();
    private BukkitTask pickTicker;

    public InfirmeriePanel(ShinobiCore plugin, StaffPanel panel) {
        this.plugin = plugin;
        this.panel = panel;
        this.settingsFile = new File(plugin.getDataFolder(), "ko-reglages.yml");
        loadSettings();
        pickTicker = Bukkit.getScheduler().runTaskTimer(plugin, this::pickTick, 20L, 4L);
    }

    public void stop() {
        if (pickTicker != null) pickTicker.cancel();
        outlines.values().forEach(BukkitTask::cancel);
    }

    /* ================================================================ ouverture + routage */

    public void open(Player p) {
        if (StaffGrades.of(p) == StaffGrades.NONE) { p.sendMessage("§cRéservé au staff."); return; }
        if (!panel.modded(p)) { p.sendMessage("§7L'Infirmerie demande le mod Reborn à jour."); return; }
        picking.remove(p.getUniqueId());
        JsonObject o = new JsonObject();
        o.addProperty("t", "open_ko");
        panel.send(p, o);
        snapshot(p);
    }

    /** Messages de l'Infirmerie ; false si l'action n'est pas la nôtre. */
    boolean handle(Player p, int grade, String a, JsonObject in) {
        switch (a) {
            case "ko_open" -> open(p);
            case "ko_refresh" -> snapshot(p);
            case "ko_act" -> koAct(p, grade, StaffPanel.str(in, "op"), StaffPanel.uuid(in, "t"));
            case "zone_save" -> { if (admin(p, grade)) zoneSave(p, in); }
            case "zone_delete" -> { if (admin(p, grade)) zoneDelete(p, StaffPanel.str(in, "id")); }
            case "zone_show" -> zoneShow(p, StaffPanel.str(in, "id"));
            case "zone_tp" -> zoneTp(p, StaffPanel.str(in, "id"));
            case "bed_pick" -> { if (admin(p, grade)) bedPick(p, StaffPanel.str(in, "id")); }
            case "bed_remove" -> { if (admin(p, grade)) bedRemove(p, StaffPanel.str(in, "id"), in.get("i").getAsInt()); }
            case "bed_tp" -> bedTp(p, StaffPanel.str(in, "id"), in.get("i").getAsInt());
            case "settings_set" -> { if (admin(p, grade)) settingsSet(p, in.getAsJsonObject("v")); }
            case "settings_reset" -> { if (admin(p, grade)) settingsReset(p); }
            default -> { return false; }
        }
        return true;
    }

    private boolean admin(Player p, int grade) {
        if (grade >= StaffGrades.ADMIN) return true;
        panel.toast(p, "Réservé aux Admins.");
        return false;
    }

    /* ================================================================ état */

    private void snapshot(Player viewer) {
        int grade = StaffGrades.of(viewer);
        JsonObject o = new JsonObject();
        o.addProperty("t", "ko_snap");
        o.addProperty("grade", grade);
        o.addProperty("gradeName", StaffGrades.name(grade));

        JsonArray inj = new JsonArray();
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
            KoState st = plugin.ko().getKo(p.getUniqueId());
            AtaManager.View ata = c != null && plugin.ata() != null ? plugin.ata().view(c.id()) : null;
            if (st == null && ata == null) continue;
            JsonObject j = new JsonObject();
            j.addProperty("uuid", p.getUniqueId().toString());
            j.addProperty("name", p.getName());
            j.addProperty("perso", c != null ? c.name() : p.getName());
            JsonArray meta = new JsonArray();
            if (st != null) {
                String state = st.isDowned() ? "down" : st.cause() == KoState.Cause.CHAKRA ? "chakra" : "ko";
                j.addProperty("state", state);
                j.addProperty("label", switch (state) { case "down" -> "À terre"; case "chakra" -> "Épuisé"; default -> "Inconscient"; });
                long total = plugin.ko().phaseMillis(st);
                j.addProperty("total", total / 1000L);
                j.addProperty("left", Math.max(0, (st.phaseStartMillis() + total - now) / 1000L));
                j.addProperty("unit", "s");
                if (!st.attackers().isEmpty()) {
                    UUID a = st.attackers().iterator().next();
                    Player ap = Bukkit.getPlayer(a);
                    ShinobiCharacter ac = ap != null ? plugin.characters().getActive(a) : null;
                    meta.add("Par " + (ac != null ? ac.name() : ap != null ? ap.getName() : "?"));
                } else if (st.cause() == KoState.Cause.CHAKRA) meta.add("Chakra vide");
                if (st.carrierPlayerId() != null) {
                    Player cp = Bukkit.getPlayer(st.carrierPlayerId());
                    meta.add("Porté par " + (cp != null ? cp.getName() : "?"));
                }
                if (st.hospitalOffered()) meta.add("/hopital proposé");
            } else {
                j.addProperty("state", ata.level() == AtaManager.Level.PLEINE ? "ata" : "light");
                j.addProperty("label", ata.level() == AtaManager.Level.PLEINE ? "ATA pleine" : "ATA allégée");
                j.addProperty("left", ata.restMinutes());
                j.addProperty("total", ata.requiredMinutes());
                j.addProperty("unit", "repos");
                meta.add("Repos " + ata.restMinutes() + "/" + ata.requiredMinutes() + " min" + (ata.resting() ? " · en cours" : ""));
            }
            TrainingZone z = plugin.trainingZones().anyZoneAt(p.getLocation());
            meta.add(z != null ? z.id() : p.getWorld().getName() + " " + p.getLocation().getBlockX() + " " + p.getLocation().getBlockZ());
            j.add("meta", meta);
            inj.add(j);
        }
        o.add("injured", inj);

        JsonArray zs = new JsonArray();
        for (TrainingZone z : plugin.trainingZones().all()) {
            JsonObject j = new JsonObject();
            j.addProperty("id", z.id());
            j.addProperty("kind", z.kind().label());
            j.addProperty("village", z.village());
            j.addProperty("world", z.world());
            j.addProperty("x1", z.minX()); j.addProperty("z1", z.minZ());
            j.addProperty("x2", z.maxX()); j.addProperty("z2", z.maxZ());
            j.addProperty("y0", z.minY()); j.addProperty("y1", z.maxY());
            JsonArray beds = new JsonArray();
            if (z.kind() == TrainingZone.Kind.HOPITAL && plugin.beds() != null) {
                for (HospitalBeds.Bed b : plugin.beds().of(z.id())) {
                    JsonObject bj = new JsonObject();
                    bj.addProperty("x", b.x()); bj.addProperty("y", b.y()); bj.addProperty("z", b.z());
                    UUID who = plugin.beds().occupant(b);
                    Player wp = who != null ? Bukkit.getPlayer(who) : null;
                    bj.addProperty("who", wp != null ? wp.getName() : "");
                    beds.add(bj);
                }
            }
            j.add("beds", beds);
            zs.add(j);
        }
        o.add("zones", zs);

        JsonArray maps = new JsonArray();
        if (plugin.places() != null) for (var m : plugin.places().maps()) {
            JsonObject j = new JsonObject();
            j.addProperty("id", m.id());
            j.addProperty("title", m.title());
            j.addProperty("world", m.world());
            maps.add(j);
        }
        o.add("maps", maps);
        JsonObject me = new JsonObject();
        Location l = viewer.getLocation();
        me.addProperty("world", l.getWorld().getName());
        me.addProperty("x", l.getX()); me.addProperty("y", l.getBlockY()); me.addProperty("z", l.getZ());
        o.add("me", me);

        JsonObject set = new JsonObject(), def = new JsonObject();
        SETTINGS.forEach((k, v) -> {
            set.addProperty(k, displayValue(k, plugin.getConfig().get((String) v[0])));
            def.addProperty(k, ((Number) v[1]).intValue());
        });
        o.add("settings", set);
        o.add("defaults", def);
        panel.send(viewer, o);
    }

    /** Valeur config → valeur affichée (les fractions deviennent des pourcentages / pour mille). */
    private static int displayValue(String key, Object raw) {
        double v = raw instanceof Number n ? n.doubleValue() : ((Number) SETTINGS.get(key)[1]).doubleValue();
        return switch (key) {
            case "plancher", "rang1" -> (int) Math.round(v <= 1.0 ? v * 100 : v);
            case "paume" -> (int) Math.round(v <= 1.0 ? v * 1000 : v);
            default -> (int) Math.round(v);
        };
    }

    private static Object configValue(String key, int shown) {
        return switch (key) {
            case "plancher", "rang1" -> shown / 100.0;
            case "paume" -> shown / 1000.0;
            default -> shown;
        };
    }

    /* ================================================================ blessés */

    private void koAct(Player staff, int grade, String op, UUID id) {
        if (id == null) return;
        int need = "tp".equals(op) ? StaffGrades.HELPER : StaffGrades.MODO;
        if (grade < need) { panel.toast(staff, "Ton grade ne permet pas cette action."); return; }
        Player t = Bukkit.getPlayer(id);
        if (t == null) { panel.toast(staff, "Ce joueur n'est plus en ligne."); return; }
        ShinobiCharacter c = plugin.characters().getActive(id);
        String who = c != null ? c.name() : t.getName();
        switch (op) {
            case "tp" -> { staff.teleport(t.getLocation()); panel.log(staff, "tp", "TP vers", who); panel.toast(staff, "Téléporté vers " + who + "."); }
            case "wake" -> {
                if (!plugin.ko().isKo(id)) { panel.toast(staff, who + " n'est pas KO."); break; }
                plugin.ko().forceClear(id);
                t.setHealth(Math.max(1.0, t.getMaxHealth() * plugin.ko().floorPct()));
                panel.log(staff, "ko", "Réveil forcé", who);
                panel.toast(staff, who + " est réveillé.");
            }
            case "knockout" -> {
                if (!plugin.ko().isDowned(id)) { panel.toast(staff, who + " n'est pas à terre."); break; }
                plugin.ko().knockOut(id);
                panel.log(staff, "ko", "Mis inconscient", who);
                panel.toast(staff, who + " perd connaissance.");
            }
            case "hospital" -> {
                if (!plugin.ko().isKo(id)) { panel.toast(staff, who + " n'est pas KO."); break; }
                plugin.ko().hospitalize(id);
                panel.log(staff, "ko", "Rapatrié à l'hôpital", who);
                panel.toast(staff, who + " est rapatrié à l'hôpital.");
            }
            case "lift_ata" -> {
                if (c == null || plugin.ata() == null || !plugin.ata().lift(c.id(), "Le staff a levé ton ATA.")) {
                    panel.toast(staff, who + " n'a pas d'ATA.");
                    break;
                }
                panel.log(staff, "ko", "ATA levée", who);
                panel.toast(staff, "ATA levée pour " + who + ".");
            }
            default -> { return; }
        }
        snapshot(staff);
    }

    /* ================================================================ zones */

    private void zoneSave(Player staff, JsonObject in) {
        String name = StaffPanel.str(in, "id").trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9àâäéèêëîïôöùûüç]+", "-").replaceAll("^-|-$", "");
        if (name.isEmpty()) { panel.toast(staff, "Donne un nom à la zone."); return; }
        TrainingZone.Kind kind = TrainingZone.Kind.parse(StaffPanel.str(in, "kind"));
        if (kind == null) kind = TrainingZone.Kind.ENTRAINEMENT;
        String world = StaffPanel.str(in, "world");
        if (Bukkit.getWorld(world) == null) world = staff.getWorld().getName();
        String old = StaffPanel.str(in, "old");
        if (!old.isEmpty() && !old.equals(name)) {
            plugin.trainingZones().remove(old);
            if (plugin.beds() != null) plugin.beds().dropZone(old);
        }
        TrainingZone z = TrainingZone.of(name, kind, world,
                in.get("x1").getAsInt(), in.get("y0").getAsInt(), in.get("z1").getAsInt(),
                in.get("x2").getAsInt(), in.get("y1").getAsInt(), in.get("z2").getAsInt(),
                kind == TrainingZone.Kind.HOPITAL ? StaffPanel.str(in, "village") : "");
        boolean fresh = plugin.trainingZones().get(name) == null;
        plugin.trainingZones().put(z);
        panel.log(staff, "monde", fresh ? "Zone créée" : "Zone modifiée", kind.label() + " « " + name + " »");
        panel.toast(staff, (fresh ? "Zone créée : " : "Zone enregistrée : ") + name);
        outline(staff, z, 20);
        snapshot(staff);
    }

    private void zoneDelete(Player staff, String id) {
        if (!plugin.trainingZones().remove(id)) { panel.toast(staff, "Zone introuvable."); return; }
        if (plugin.beds() != null) plugin.beds().dropZone(id);
        panel.log(staff, "monde", "Zone supprimée", id);
        panel.toast(staff, "Zone « " + id + " » supprimée.");
        snapshot(staff);
    }

    private void zoneShow(Player staff, String id) {
        TrainingZone z = plugin.trainingZones().get(id);
        if (z == null) return;
        outline(staff, z, 30);
        panel.toast(staff, "Contour de « " + id + " » visible 30 s autour de toi.");
    }

    private void zoneTp(Player staff, String id) {
        TrainingZone z = plugin.trainingZones().get(id);
        World w = z == null ? null : Bukkit.getWorld(z.world());
        if (w == null) return;
        int cx = (z.minX() + z.maxX()) / 2, cz = (z.minZ() + z.maxZ()) / 2;
        int y = Math.max(z.minY(), Math.min(z.maxY(), w.getHighestBlockYAt(cx, cz) + 1));
        staff.teleport(new Location(w, cx + 0.5, y, cz + 0.5, staff.getLocation().getYaw(), 20f));
        outline(staff, z, 20);
    }

    /** Contour de la zone en particules, visible seulement par {@code viewer}, pendant {@code seconds}. */
    private void outline(Player viewer, TrainingZone z, int seconds) {
        BukkitTask old = outlines.remove(viewer.getUniqueId());
        if (old != null) old.cancel();
        World w = Bukkit.getWorld(z.world());
        if (w == null) return;
        Color col = switch (z.kind()) {
            case ENTRAINEMENT -> Color.fromRGB(200, 30, 50);
            case REPOS -> Color.fromRGB(60, 170, 80);
            case HOPITAL -> Color.fromRGB(70, 130, 220);
        };
        Particle.DustOptions dust = new Particle.DustOptions(col, 1.4f);
        long end = System.currentTimeMillis() + seconds * 1000L;
        int x0 = z.minX(), x1 = z.maxX() + 1, z0 = z.minZ(), z1 = z.maxZ() + 1;
        int perim = 2 * ((x1 - x0) + (z1 - z0));
        double step = Math.max(0.5, perim / 360.0);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!viewer.isOnline() || System.currentTimeMillis() > end) {
                BukkitTask t = outlines.remove(viewer.getUniqueId());
                if (t != null) t.cancel();
                return;
            }
            double ey = Math.max(z.minY(), Math.min(z.maxY(), viewer.getLocation().getY())) + 0.2;
            for (double x = x0; x <= x1; x += step) {
                viewer.spawnParticle(Particle.DUST, x, ey, z0, 1, 0, 0, 0, 0, dust);
                viewer.spawnParticle(Particle.DUST, x, ey, z1, 1, 0, 0, 0, 0, dust);
            }
            for (double zz = z0; zz <= z1; zz += step) {
                viewer.spawnParticle(Particle.DUST, x0, ey, zz, 1, 0, 0, 0, 0, dust);
                viewer.spawnParticle(Particle.DUST, x1, ey, zz, 1, 0, 0, 0, 0, dust);
            }
            for (double y = z.minY(); y <= z.maxY() + 1; y += 1.0) {
                viewer.spawnParticle(Particle.DUST, x0, y, z0, 1, 0, 0, 0, 0, dust);
                viewer.spawnParticle(Particle.DUST, x1, y, z0, 1, 0, 0, 0, 0, dust);
                viewer.spawnParticle(Particle.DUST, x0, y, z1, 1, 0, 0, 0, 0, dust);
                viewer.spawnParticle(Particle.DUST, x1, y, z1, 1, 0, 0, 0, 0, dust);
            }
        }, 0L, 10L);
        outlines.put(viewer.getUniqueId(), task);
    }

    /* ================================================================ lits */

    private void bedPick(Player staff, String zoneId) {
        TrainingZone z = plugin.trainingZones().get(zoneId);
        if (z == null || z.kind() != TrainingZone.Kind.HOPITAL) { panel.toast(staff, "Choisis d'abord un hôpital."); return; }
        picking.put(staff.getUniqueId(), zoneId);
        JsonObject o = new JsonObject();
        o.addProperty("t", "close");
        panel.send(staff, o);
        outline(staff, z, 600);
        staff.sendMessage("§6[Infirmerie] §fVise un lit et fais §eclic droit§f pour l'ajouter à « " + zoneId
                + " » (encore pour le retirer). §eAccroupis-toi§f pour terminer.");
        staff.playSound(staff.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.4f);
    }

    private void bedRemove(Player staff, String zoneId, int index) {
        if (plugin.beds() == null || !plugin.beds().remove(zoneId, index)) return;
        panel.log(staff, "monde", "Lit retiré", zoneId + " · lit " + (index + 1));
        snapshot(staff);
    }

    private void bedTp(Player staff, String zoneId, int index) {
        if (plugin.beds() == null) return;
        List<HospitalBeds.Bed> list = plugin.beds().of(zoneId);
        if (index < 0 || index >= list.size()) return;
        Location l = list.get(index).location();
        if (l != null) staff.teleport(l.clone().add(1.2, 0.2, 0).setDirection(l.toVector().subtract(l.clone().add(1.2, 0, 0).toVector())));
    }

    private void pickTick() {
        for (Map.Entry<UUID, String> e : picking.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            HospitalBeds.Bed bed = plugin.beds() != null ? plugin.beds().probe(aimed(p)) : null;
            int count = plugin.beds() != null ? plugin.beds().of(e.getValue()).size() : 0;
            if (bed != null) {
                Location l = bed.location();
                Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(217, 169, 94), 1.0f);
                for (int k = 0; k < 16; k++) {
                    double a = k / 16.0 * Math.PI * 2;
                    p.spawnParticle(Particle.DUST, l.getX() + Math.cos(a) * 0.9, l.getY() + 0.3, l.getZ() + Math.sin(a) * 0.9,
                            1, 0, 0, 0, 0, gold);
                }
                p.sendActionBar(Component.text("Lit visé · clic droit pour l'ajouter ou le retirer · " + count
                        + " lit(s) · accroupis-toi pour finir", NamedTextColor.GOLD));
            } else {
                p.sendActionBar(Component.text("Vise un lit (meuble Nexo) · " + count + " lit(s) dans « "
                        + e.getValue() + " » · accroupis-toi pour finir", NamedTextColor.GRAY));
            }
        }
    }

    /** Point visé : l'entité (meuble) ou le bloc sous le viseur, à 6 blocs. */
    private static Location aimed(Player p) {
        Entity ent = p.getTargetEntity(6);
        if (ent != null) return ent.getLocation();
        Block b = p.getTargetBlockExact(6);
        return b != null ? b.getLocation().add(0.5, 0.5, 0.5) : null;
    }

    private void pickAt(Player p, Location at) {
        String zoneId = picking.get(p.getUniqueId());
        if (zoneId == null || plugin.beds() == null) return;
        TrainingZone z = plugin.trainingZones().get(zoneId);
        HospitalBeds.Bed bed = plugin.beds().probe(at);
        if (bed == null) { p.sendActionBar(Component.text("Ce n'est pas un lit.", NamedTextColor.RED)); return; }
        Location bl = bed.location();
        if (z == null || bl == null || !(bl.getWorld().getName().equals(z.world())
                && bl.getBlockX() >= z.minX() - 1 && bl.getBlockX() <= z.maxX() + 1
                && bl.getBlockZ() >= z.minZ() - 1 && bl.getBlockZ() <= z.maxZ() + 1)) {
            p.sendActionBar(Component.text("Ce lit est hors de la zone de l'hôpital.", NamedTextColor.RED));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.6f);
            return;
        }
        if (plugin.beds().removeNear(zoneId, bl) != null) {
            p.sendMessage("§7[Infirmerie] Lit retiré de « " + zoneId + " ».");
            p.playSound(p.getLocation(), Sound.BLOCK_WOOD_BREAK, 0.7f, 1.2f);
            panel.log(p, "monde", "Lit retiré", zoneId);
        } else if (plugin.beds().add(zoneId, bed)) {
            int n = plugin.beds().of(zoneId).size();
            p.sendMessage("§a[Infirmerie] Lit " + n + " ajouté à « " + zoneId + " ».");
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.3f);
            panel.log(p, "monde", "Lit ajouté", zoneId + " · lit " + n);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickBlock(PlayerInteractEvent ev) {
        if (!picking.containsKey(ev.getPlayer().getUniqueId())) return;
        if (ev.getHand() != EquipmentSlot.HAND || ev.getAction() != Action.RIGHT_CLICK_BLOCK || ev.getClickedBlock() == null) return;
        ev.setCancelled(true);
        pickAt(ev.getPlayer(), ev.getClickedBlock().getLocation().add(0.5, 0.5, 0.5));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickEntity(PlayerInteractEntityEvent ev) {
        if (!picking.containsKey(ev.getPlayer().getUniqueId()) || ev.getHand() != EquipmentSlot.HAND) return;
        if (ev instanceof PlayerInteractAtEntityEvent) return;   // traité par l'événement simple
        ev.setCancelled(true);
        pickAt(ev.getPlayer(), ev.getRightClicked().getLocation());
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent ev) {
        if (!ev.isSneaking()) return;
        String zone = picking.remove(ev.getPlayer().getUniqueId());
        if (zone == null) return;
        BukkitTask t = outlines.remove(ev.getPlayer().getUniqueId());
        if (t != null) t.cancel();
        ev.getPlayer().sendActionBar(Component.text("Sélection des lits terminée.", NamedTextColor.GREEN));
        Bukkit.getScheduler().runTaskLater(plugin, () -> open(ev.getPlayer()), 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent ev) {
        picking.remove(ev.getPlayer().getUniqueId());
        BukkitTask t = outlines.remove(ev.getPlayer().getUniqueId());
        if (t != null) t.cancel();
    }

    /* ================================================================ réglages */

    private void settingsSet(Player staff, JsonObject values) {
        if (values == null) return;
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(settingsFile);
        int changed = 0;
        for (Map.Entry<String, JsonElement> e : values.entrySet()) {
            Object[] def = SETTINGS.get(e.getKey());
            if (def == null) continue;
            int shown = Math.max(0, e.getValue().getAsInt());
            Object v = configValue(e.getKey(), shown);
            plugin.getConfig().set((String) def[0], v);
            saved.set((String) def[0], v);
            changed++;
        }
        try { saved.save(settingsFile); }
        catch (IOException ex) { plugin.getLogger().log(Level.WARNING, "Échec d'écriture de ko-reglages.yml", ex); }
        reloadManagers();
        panel.log(staff, "monde", "Réglages du KO", changed + " valeur(s)");
        panel.toast(staff, "Réglages appliqués.");
        snapshot(staff);
    }

    private void settingsReset(Player staff) {
        SETTINGS.forEach((k, v) -> plugin.getConfig().set((String) v[0], configValue(k, ((Number) v[1]).intValue())));
        if (settingsFile.isFile() && !settingsFile.delete()) plugin.getLogger().warning("ko-reglages.yml non supprimé");
        reloadManagers();
        panel.log(staff, "monde", "Réglages du KO", "valeurs d'origine");
        panel.toast(staff, "Valeurs d'origine rétablies.");
        snapshot(staff);
    }

    /** Applique {@code ko-reglages.yml} par-dessus le config.yml (sans le réécrire). */
    private void loadSettings() {
        if (!settingsFile.isFile()) return;
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(settingsFile);
        for (Object[] def : SETTINGS.values()) {
            String path = (String) def[0];
            if (saved.contains(path)) plugin.getConfig().set(path, saved.get(path));
        }
        reloadManagers();
    }

    private void reloadManagers() {
        if (plugin.ko() != null) plugin.ko().reloadConfig();
        if (plugin.ata() != null) plugin.ata().reloadConfig();
        if (plugin.trainingZones() != null) plugin.trainingZones().reloadConfig();
    }
}
