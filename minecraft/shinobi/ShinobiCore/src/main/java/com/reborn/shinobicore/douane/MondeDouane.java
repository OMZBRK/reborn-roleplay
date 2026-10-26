package com.reborn.shinobicore.douane;

import com.reborn.shinobicore.ShinobiCore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.sign.Side;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

import java.util.List;

/**
 * Le monde {@code douane} (CANDIDATURE_ET_DOUANE.md §3.2) : vide, sans mobs, temps et météo figés. Chargé (et créé
 * s'il n'existe pas) au démarrage. Le décor définitif est construit par l'équipe sur le dev, puis copié vers la prod ;
 * en attendant, un <b>hall provisoire</b> en blocs simples et ses bureaux se posent si {@code douane.hall-provisoire}
 * le demande, aux positions de la config, une seule fois (rien n'est posé si le sol du hall existe déjà).
 */
final class MondeDouane {

    /** Générateur vide : aucun terrain, aucune structure, aucun mob. */
    static final class GenerateurVide extends ChunkGenerator {
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateBedrock() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
        @Override public List<BlockPopulator> getDefaultPopulators(World world) { return List.of(); }
        @Override public boolean canSpawn(World world, int x, int z) { return true; }
    }

    private MondeDouane() {}

    /** Charge ou crée le monde ; null s'il est introuvable et que la création est coupée (ou échoue). */
    static World charger(ShinobiCore plugin, DouaneConfig conf) {
        World w = Bukkit.getWorld(conf.monde);
        if (w == null && conf.creerMonde) {
            try {
                w = new WorldCreator(conf.monde)
                        .environment(World.Environment.NORMAL)
                        .generator(new GenerateurVide())
                        .generateStructures(false)
                        .createWorld();
                if (w != null) plugin.getLogger().info("[douane] monde « " + conf.monde + " » chargé (générateur vide).");
            } catch (RuntimeException e) {
                plugin.getLogger().severe("[douane] création du monde « " + conf.monde + " » impossible : " + e.getMessage());
                return null;
            }
        }
        if (w == null) {
            plugin.getLogger().severe("[douane] monde « " + conf.monde + " » introuvable (douane.creer-monde: false).");
            return null;
        }
        regler(plugin, w, conf);
        if (conf.provisoire) construireProvisoire(plugin, w, conf);
        return w;
    }

    /** Sans mobs, temps et météo figés, aucun dégât d'environnement. */
    @SuppressWarnings("deprecation")
    static void regler(ShinobiCore plugin, World w, DouaneConfig conf) {
        try {
            w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            w.setGameRule(GameRule.DO_FIRE_TICK, false);
            w.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
            w.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
            w.setGameRule(GameRule.DO_WARDEN_SPAWNING, false);
            w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        } catch (Throwable t) {
            plugin.getLogger().warning("[douane] règles du monde non posées : " + t.getMessage());
        }
        w.setDifficulty(Difficulty.PEACEFUL);
        w.setTime(conf.heureFixe);
        w.setStorm(false);
        w.setThundering(false);
        w.setSpawnLocation(conf.hall.en(w));
    }

    /* ================================================================ hall provisoire */

    /** Hall et bureaux en blocs simples, posés une seule fois (sol du hall déjà présent = rien). */
    static void construireProvisoire(ShinobiCore plugin, World w, DouaneConfig conf) {
        Location c = conf.hall.en(w);
        if (!c.clone().add(0, -1, 0).getBlock().getType().isAir()) return;
        int r = (int) Math.max(6, Math.min(20, conf.zoneHall.pave() ? 12 : conf.zoneHall.rayon() - 1));
        plateforme(w, c.getBlockX(), c.getBlockY() - 1, c.getBlockZ(), r, Material.SPRUCE_PLANKS, Material.STRIPPED_SPRUCE_LOG);
        // Panneau d'accueil près du point d'arrivée (aucun kanji, DA §3).
        Block panneau = w.getBlockAt(c.getBlockX(), c.getBlockY(), c.getBlockZ() + 3);
        panneau.setType(Material.SPRUCE_SIGN, false);
        if (panneau.getBlockData() instanceof Rotatable rot) {
            rot.setRotation(BlockFace.NORTH);
            panneau.setBlockData(rot, false);
        }
        if (panneau.getState() instanceof Sign s) {
            var face = s.getSide(Side.FRONT);
            face.line(0, Component.text("Douane"));
            face.line(1, Component.text("Attends l'appel"));
            face.line(2, Component.text("d'un examinateur."));
            face.line(3, Component.text("Règlement : launcher"));
            s.update(true, false);
        }
        for (DouaneConfig.BureauConf b : conf.bureaux) {
            int mx = (int) Math.floor((b.candidat().x() + b.examinateur().x()) / 2);
            int mz = (int) Math.floor((b.candidat().z() + b.examinateur().z()) / 2);
            int y = (int) Math.floor(Math.min(b.candidat().y(), b.examinateur().y())) - 1;
            int rb = (int) Math.max(3, Math.min(8, b.zone().pave() ? 4 : b.zone().rayon() - 1));
            plateforme(w, mx, y, mz, rb, Material.DARK_OAK_PLANKS, Material.STRIPPED_DARK_OAK_LOG);
            w.getBlockAt(mx, y + 1, mz).setType(Material.SPRUCE_SLAB, false);   // la table entre les deux
        }
        plugin.getLogger().info("[douane] hall provisoire et " + conf.bureaux.size() + " bureau(x) posés.");
    }

    /** Sol carré de rayon {@code r}, bordure en rondins, barrière basse et lanternes aux angles. */
    private static void plateforme(World w, int cx, int y, int cz, int r, Material sol, Material bord) {
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                boolean bordure = x == cx - r || x == cx + r || z == cz - r || z == cz + r;
                w.getBlockAt(x, y, z).setType(bordure ? bord : sol, false);
                if (bordure) w.getBlockAt(x, y + 1, z).setType(Material.SPRUCE_FENCE, true);
            }
        }
        int[][] coins = {{cx - r, cz - r}, {cx - r, cz + r}, {cx + r, cz - r}, {cx + r, cz + r}};
        for (int[] k : coins) w.getBlockAt(k[0], y + 2, k[1]).setType(Material.LANTERN, false);
    }
}
