package com.reborn.shinobicore.staff.outils;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.inventory.CosmeticSlot;
import com.reborn.shinobicore.inventory.InventoryManager;
import com.reborn.shinobicore.inventory.RareteItems;
import com.reborn.shinobicore.journal.Journal;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Tenue imposée par le staff ({@code /tenue}, RedLine « Force clothing ») : prison, uniforme d'institution, event.
 *
 * <p>Ce qui est forcé : l'emplacement cosmétique 3D <b>{@code TENUE}</b> de la sacoche (objet Nexo porté par-dessus le
 * skin). La pièce que portait le personnage est mise de côté ({@code tenues-forcees.yml}, par personnage) et rendue à
 * la levée ; tant que la tenue est imposée, l'emplacement est verrouillé ({@link #verrouille}).
 *
 * <p><b>TODO</b> : l'apparence de skin achetée à l'échoppe ({@code ShinobiCharacter#appearance}, composée côté client
 * par le créateur) ne peut pas être imposée par un id côté serveur — il faudrait un catalogue de tenues-skins
 * pré-composées (SkinSpec) sur le serveur. Non fait.
 */
public final class TenuesForcees {

    private record Forcee(String id, String par, long le, String ancienne) { }

    private final ShinobiCore plugin;
    private final File fichier;
    private final Map<UUID, Forcee> forcees = new ConcurrentHashMap<>();

    public TenuesForcees(ShinobiCore plugin) {
        this.plugin = plugin;
        this.fichier = new File(plugin.getDataFolder(), "tenues-forcees.yml");
        charger();
    }

    /** Appelé par la sacoche (message {@code cos:…}) : vrai si l'action touche l'emplacement TENUE d'une tenue imposée. */
    public static boolean verrouille(Player p, String[] parts) {
        ShinobiCore core = ShinobiCore.get();
        if (core == null || core.outilsStaff() == null || p == null) return false;
        ShinobiCharacter c = core.characters().getActive(p.getUniqueId());
        if (c == null || !core.outilsStaff().tenues().forcees.containsKey(c.id())) return false;
        String slot = parts.length >= 4 && "equip".equals(parts[1]) ? parts[3]
                : parts.length >= 3 && "unequip".equals(parts[1]) ? parts[2] : "";
        return CosmeticSlot.fromName(slot) == CosmeticSlot.TENUE;
    }

    public boolean imposee(UUID charId) { return charId != null && forcees.containsKey(charId); }

    /**
     * Fabrique l'objet de tenue : {@code main} = copie de l'objet en main du staff, sinon id Nexo (via l'API de Nexo,
     * par réflexion). null si introuvable.
     */
    public ItemStack fabriquer(CommandSender staff, String id) {
        if ("main".equalsIgnoreCase(id) && staff instanceof Player sp) {
            ItemStack m = sp.getInventory().getItemInMainHand();
            return m.getType().isAir() ? null : m.clone();
        }
        try {
            Class<?> k = Class.forName("com.nexomc.nexo.api.NexoItems");
            Object builder = k.getMethod("itemFromId", String.class).invoke(null, id);
            if (builder == null) return null;
            Object item = builder.getClass().getMethod("build").invoke(builder);
            return item instanceof ItemStack s ? s : null;
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    /** Impose {@code tenue} au personnage actif de {@code cible}. */
    public boolean imposer(CommandSender staff, Player cible, ItemStack tenue, String id) {
        InventoryManager im = plugin.rpInventory();
        ShinobiCharacter c = plugin.characters().getActive(cible.getUniqueId());
        if (im == null || c == null || tenue == null) return false;
        Forcee deja = forcees.get(c.id());
        ItemStack ancienne = im.staffPoserTenue(cible, tenue);
        // On garde la pièce d'origine du personnage, pas une tenue déjà imposée.
        String anc = deja != null ? deja.ancienne() : enc(ancienne);
        forcees.put(c.id(), new Forcee(id, staff.getName(), System.currentTimeMillis(), anc));
        sauver();
        Journal.entree(Categorie.STAFF, "TENUE_IMPOSEE").acteur(staff).cible(cible)
                .detail(RareteItems.nom(tenue) + " (" + id + ")" + (ancienne != null ? " · mise de côté : " + RareteItems.nom(ancienne) : ""))
                .ecrire();
        return true;
    }

    /** Rend sa tenue au personnage actif de {@code cible}. */
    public boolean lever(CommandSender staff, Player cible) {
        InventoryManager im = plugin.rpInventory();
        ShinobiCharacter c = plugin.characters().getActive(cible.getUniqueId());
        if (im == null || c == null) return false;
        Forcee f = forcees.remove(c.id());
        if (f == null) return false;
        sauver();
        ItemStack ancienne = dec(f.ancienne());
        im.staffPoserTenue(cible, ancienne);
        Journal.entree(Categorie.STAFF, "TENUE_LEVEE").acteur(staff).cible(cible)
                .detail("tenue « " + f.id() + " » imposée par " + f.par()
                        + (ancienne != null ? " · rendue : " + RareteItems.nom(ancienne) : ""))
                .ecrire();
        return true;
    }

    private static String enc(ItemStack s) {
        return s == null || s.getType().isAir() ? "" : Base64.getEncoder().encodeToString(s.serializeAsBytes());
    }

    private static ItemStack dec(String b64) {
        if (b64 == null || b64.isEmpty()) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(b64));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void charger() {
        if (!fichier.isFile()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier);
        ConfigurationSection s = y.getConfigurationSection("tenues");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            try {
                forcees.put(UUID.fromString(k), new Forcee(s.getString(k + ".id", "?"), s.getString(k + ".par", "?"),
                        s.getLong(k + ".le"), s.getString(k + ".ancienne", "")));
            } catch (IllegalArgumentException ignored) { }
        }
    }

    private void sauver() {
        YamlConfiguration y = new YamlConfiguration();
        forcees.forEach((id, f) -> {
            String k = "tenues." + id;
            y.set(k + ".id", f.id());
            y.set(k + ".par", f.par());
            y.set(k + ".le", f.le());
            y.set(k + ".ancienne", f.ancienne());
        });
        try {
            plugin.getDataFolder().mkdirs();
            y.save(fichier);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Échec d'écriture de tenues-forcees.yml", e);
        }
    }
}
