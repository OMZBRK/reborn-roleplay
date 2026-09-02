package com.reborn.shinobitail.gui;

import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobitail.ShinobiTail;
import com.reborn.shinobitail.beast.BeastDefinition;
import com.reborn.shinobitail.data.JinchurikiData;
import com.reborn.shinobitail.data.JinchurikiStore;
import com.reborn.shinobitail.transform.ActiveTransformation;
import com.reborn.shinobitail.transform.TransformationManager;
import com.reborn.shinobitail.util.Fmt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GM administration GUI — the command-free way to run the system.
 *
 * <p><b>List view</b>: every loaded character with a sealed beast.
 * Click → editor.
 *
 * <p><b>Editor</b> (one jinchūriki):
 * <ul>
 *   <li>relation dyes + rage — left-click +1, SHIFT +10,
 *       right-click −1, SHIFT −10</li>
 *   <li>one star per stage — same clicks for mastery %</li>
 *   <li>action buttons: transform/next, stop, confrontation,
 *       pause — they need the owner online on that character</li>
 * </ul>
 * Every click saves and re-renders in place.
 */
public final class AdminGui implements Listener {

    private static final int SLOT_BACK = 0;
    private static final int SLOT_INFO = 4;
    private static final int SLOT_TRANSFORM = 14;
    private static final int SLOT_STOP = 15;
    private static final int SLOT_CONFRONT = 16;
    private static final int SLOT_PAUSE = 17;
    private static final int REL_BASE = 9;       // 9..13 = the 5 dials
    private static final int MASTERY_BASE = 18;  // 18..26 = stages 1..9

    private static final String[] REL_KEYS =
            {"trust", "anger", "cooperation", "influence", "rage"};
    private static final String[] REL_NAMES =
            {"Confiance", "Colère", "Coopération", "Emprise", "Rage"};
    private static final Material[] REL_ICONS = {
            Material.LIME_DYE, Material.RED_DYE, Material.CYAN_DYE,
            Material.PURPLE_DYE, Material.BLAZE_POWDER};

    private final ShinobiTail plugin;

    public AdminGui(ShinobiTail plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------------------------------------ holders */

    private static final class ListHolder implements InventoryHolder {
        private Inventory inventory;
        private final List<UUID> slots = new ArrayList<>();
        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class EditHolder implements InventoryHolder {
        private Inventory inventory;
        private final UUID characterId;
        private EditHolder(UUID characterId) { this.characterId = characterId; }
        @Override public Inventory getInventory() { return inventory; }
    }

    /* -------------------------------------------------------------- views */

    public void openList(Player gm) {
        ListHolder holder = new ListHolder();
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Jinchūriki — administration",
                        NamedTextColor.DARK_RED));
        holder.inventory = inv;

        int slot = 0;
        var characters = plugin.characters();
        if (characters != null) {
            for (var entry : characters.rosterView().entrySet()) {
                for (ShinobiCharacter c : entry.getValue()) {
                    if (slot >= 54) break;
                    JinchurikiData data = plugin.jinchuriki().of(c.id());
                    if (data.beastId() == null) continue;
                    BeastDefinition beast = plugin.beasts().byId(data.beastId());
                    List<Component> lore = new ArrayList<>();
                    lore.add(line(beast != null
                            ? beast.beastName() + " (" + beast.tails() + " queues)"
                            : "Démon inconnu : " + data.beastId(),
                            NamedTextColor.RED));
                    lore.add(line("Rage " + Fmt.pct(data.rage())
                            + " — Étape max atteinte : "
                            + data.highestStageReached(), NamedTextColor.GRAY));
                    if (data.finalReleaseReached()) {
                        lore.add(line("☠ LIBÉRATION FINALE — hôte perdu",
                                NamedTextColor.DARK_RED));
                    }
                    lore.add(Component.empty());
                    lore.add(line("Clic : ouvrir l'éditeur", NamedTextColor.DARK_AQUA));
                    inv.setItem(slot, item(Material.PLAYER_HEAD,
                            Component.text(c.name(), NamedTextColor.GOLD), lore));
                    holder.slots.add(c.id());
                    slot++;
                }
            }
        }
        if (slot == 0) {
            inv.setItem(22, item(Material.BARRIER,
                    Component.text("Aucun jinchūriki", NamedTextColor.RED),
                    List.of(line("Utilise /tail bind <personnage> <démon>",
                            NamedTextColor.GRAY))));
        }
        gm.openInventory(inv);
    }

    public void openEditor(Player gm, UUID characterId) {
        EditHolder holder = new EditHolder(characterId);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Édition jinchūriki", NamedTextColor.DARK_RED));
        holder.inventory = inv;
        render(inv, characterId);
        gm.openInventory(inv);
    }

    private void render(Inventory inv, UUID characterId) {
        JinchurikiStore.ResolvedCharacter resolved =
                plugin.jinchuriki().resolveById(characterId);
        JinchurikiData data = plugin.jinchuriki().of(characterId);
        BeastDefinition beast = plugin.beasts().byId(data.beastId());
        String charName = resolved != null ? resolved.character().name() : "?";

        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE,
                Component.empty(), List.of());
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        inv.setItem(SLOT_BACK, item(Material.ARROW,
                Component.text("← Liste des jinchūriki", NamedTextColor.YELLOW),
                List.of()));

        // --- info ----------------------------------------------------
        List<Component> info = new ArrayList<>();
        info.add(line(beast != null
                ? beast.beastName() + " — " + beast.tails() + " queue(s), "
                        + beast.personality().id()
                : "Démon inconnu : " + data.beastId(), NamedTextColor.RED));
        info.add(line("Étape max atteinte : " + data.highestStageReached(),
                NamedTextColor.GRAY));
        info.add(line("Transformations : " + data.transformations()
                + " — Résist. : " + data.resistSuccesses() + "/"
                + (data.resistSuccesses() + data.resistFailures()),
                NamedTextColor.GRAY));
        Player owner = ownerOnline(resolved, characterId);
        ActiveTransformation t = owner != null
                ? plugin.transformations().get(owner.getUniqueId()) : null;
        if (t != null) {
            info.add(line("EN TRANSFORMATION — étape " + t.stage()
                    + (t.paused() ? " (PAUSE)" : ""), NamedTextColor.DARK_RED));
        } else {
            info.add(line(owner != null
                    ? "En ligne — non transformé"
                    : "Hors ligne (valeurs éditables)", NamedTextColor.DARK_GRAY));
        }
        if (data.finalReleaseReached()) {
            info.add(line("☠ LIBÉRATION FINALE — hôte perdu", NamedTextColor.DARK_RED));
        }
        inv.setItem(SLOT_INFO, item(Material.PLAYER_HEAD,
                Component.text(charName, NamedTextColor.GOLD), info));

        // --- the five dials -------------------------------------------
        double[] vals = {data.trust(), data.anger(), data.cooperation(),
                data.influence(), data.rage()};
        for (int i = 0; i < 5; i++) {
            List<Component> lore = new ArrayList<>();
            lore.add(line(Fmt.pct1(vals[i]), NamedTextColor.GOLD));
            lore.add(Component.empty());
            lore.add(line("Clic gauche : +1   (Maj : +10)", NamedTextColor.GRAY));
            lore.add(line("Clic droit : -1   (Maj : -10)", NamedTextColor.GRAY));
            inv.setItem(REL_BASE + i, item(REL_ICONS[i],
                    Component.text(REL_NAMES[i], NamedTextColor.YELLOW), lore));
        }

        // --- actions ---------------------------------------------------
        inv.setItem(SLOT_TRANSFORM, item(Material.DRAGON_BREATH,
                Component.text("Transformer / Étape suivante", NamedTextColor.RED),
                List.of(line("Démarre l'étape 1, ou passe à la suivante.",
                                NamedTextColor.GRAY),
                        line("Nécessite le joueur en ligne sur ce personnage.",
                                NamedTextColor.DARK_GRAY))));
        inv.setItem(SLOT_STOP, item(Material.BARRIER,
                Component.text("Stopper la transformation", NamedTextColor.RED),
                List.of(line("Arrêt immédiat du mode.", NamedTextColor.GRAY))));
        inv.setItem(SLOT_CONFRONT, item(Material.ENDER_EYE,
                Component.text("Confrontation (Monde Intérieur)",
                        NamedTextColor.LIGHT_PURPLE),
                List.of(line("Envoie l'hôte face à son démon.",
                        NamedTextColor.GRAY))));
        inv.setItem(SLOT_PAUSE, item(Material.CLOCK,
                Component.text(t != null && t.paused()
                                ? "Relancer la rage" : "Pause rage (narration)",
                        NamedTextColor.AQUA),
                List.of(line("Gèle/relance la montée de rage et les",
                                NamedTextColor.GRAY),
                        line("fenêtres de contrôle, effets conservés.",
                                NamedTextColor.GRAY))));

        // --- mastery row -----------------------------------------------
        int tails = beast != null ? beast.tails() : 0;
        for (int s = 1; s <= Math.min(9, tails); s++) {
            List<Component> lore = new ArrayList<>();
            lore.add(line("Maîtrise : " + Fmt.pct(data.mastery(s)),
                    NamedTextColor.AQUA));
            if (s > data.highestStageReached() + 1 && data.mastery(s) <= 0) {
                lore.add(line("(étape jamais atteinte)", NamedTextColor.DARK_GRAY));
            }
            lore.add(Component.empty());
            lore.add(line("Clic gauche : +1   (Maj : +10)", NamedTextColor.GRAY));
            lore.add(line("Clic droit : -1   (Maj : -10)", NamedTextColor.GRAY));
            ItemStack star = item(Material.NETHER_STAR,
                    Component.text("Étape " + s
                            + (beast != null && s == tails ? " (FINALE)" : ""),
                            s == tails ? NamedTextColor.DARK_RED
                                    : NamedTextColor.GOLD), lore);
            star.setAmount(s);
            inv.setItem(MASTERY_BASE + (s - 1), star);
        }
    }

    /* ------------------------------------------------------------- clicks */

    @EventHandler
    public void onClick(InventoryClickEvent ev) {
        InventoryHolder rawHolder = ev.getInventory().getHolder();
        if (!(rawHolder instanceof ListHolder) && !(rawHolder instanceof EditHolder)) return;
        ev.setCancelled(true);
        if (!(ev.getWhoClicked() instanceof Player gm)) return;
        if (!gm.hasPermission("shinobitail.gm")) return;
        int slot = ev.getRawSlot();

        if (rawHolder instanceof ListHolder list) {
            if (slot >= 0 && slot < list.slots.size()) {
                openEditor(gm, list.slots.get(slot));
            }
            return;
        }

        EditHolder holder = (EditHolder) rawHolder;
        UUID charId = holder.characterId;
        JinchurikiData data = plugin.jinchuriki().of(charId);
        BeastDefinition beast = plugin.beasts().byId(data.beastId());
        ClickType click = ev.getClick();

        if (slot == SLOT_BACK) { openList(gm); return; }

        // Relation dials.
        if (slot >= REL_BASE && slot < REL_BASE + 5) {
            double delta = (click.isShiftClick() ? 10 : 1)
                    * (click.isRightClick() ? -1 : 1);
            switch (REL_KEYS[slot - REL_BASE]) {
                case "trust" -> data.setTrust(data.trust() + delta);
                case "anger" -> data.setAnger(data.anger() + delta);
                case "cooperation" -> data.setCooperation(data.cooperation() + delta);
                case "influence" -> data.setInfluence(data.influence() + delta);
                case "rage" -> data.setRage(data.rage() + delta);
                default -> { }
            }
            plugin.jinchuriki().save(data);
            render(ev.getInventory(), charId);
            return;
        }

        // Mastery stars.
        if (slot >= MASTERY_BASE && slot < MASTERY_BASE + 9) {
            int stage = slot - MASTERY_BASE + 1;
            if (beast == null || stage > beast.tails()) return;
            double delta = (click.isShiftClick() ? 10 : 1)
                    * (click.isRightClick() ? -1 : 1);
            data.addMastery(stage, delta);
            plugin.jinchuriki().save(data);
            render(ev.getInventory(), charId);
            return;
        }

        // Action buttons — need the owner online ON this character.
        if (slot == SLOT_TRANSFORM || slot == SLOT_STOP
                || slot == SLOT_CONFRONT || slot == SLOT_PAUSE) {
            Player owner = ownerOnline(plugin.jinchuriki().resolveById(charId), charId);
            if (owner == null) {
                gm.sendMessage(Component.text(
                        "Le joueur doit être en ligne sur CE personnage.",
                        NamedTextColor.RED));
                return;
            }
            if (beast == null) {
                gm.sendMessage(Component.text(
                        "Démon '" + data.beastId() + "' absent de beasts.yml.",
                        NamedTextColor.RED));
                return;
            }
            TransformationManager tm = plugin.transformations();
            switch (slot) {
                case SLOT_TRANSFORM -> {
                    if (tm.isTransformed(owner.getUniqueId())) tm.escalate(owner);
                    else tm.begin(owner, data, beast, 1,
                            ActiveTransformation.Cause.GM);
                }
                case SLOT_STOP -> {
                    plugin.innerWorld().abort(owner);
                    tm.stop(owner, TransformationManager.StopReason.GM);
                }
                case SLOT_CONFRONT -> {
                    if (tm.isTransformed(owner.getUniqueId())
                            && !plugin.innerWorld().inSession(owner.getUniqueId())) {
                        plugin.innerWorld().beginConfrontation(owner, "appel du démon");
                    } else {
                        gm.sendMessage(Component.text(
                                "Le joueur doit être transformé (et hors du Monde Intérieur).",
                                NamedTextColor.RED));
                    }
                }
                case SLOT_PAUSE -> {
                    var t = tm.get(owner.getUniqueId());
                    if (t != null) tm.setPaused(owner, !t.paused());
                    else gm.sendMessage(Component.text(
                            owner.getName() + " n'est pas transformé.",
                            NamedTextColor.RED));
                }
                default -> { }
            }
            render(ev.getInventory(), charId);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent ev) {
        InventoryHolder h = ev.getInventory().getHolder();
        if (h instanceof ListHolder || h instanceof EditHolder) ev.setCancelled(true);
    }

    /* ------------------------------------------------------------ helpers */

    /** The owner, if online AND currently playing this character. */
    private Player ownerOnline(JinchurikiStore.ResolvedCharacter resolved,
                               UUID characterId) {
        if (resolved == null || plugin.characters() == null) return null;
        Player p = Bukkit.getPlayer(resolved.ownerId());
        if (p == null || !p.isOnline()) return null;
        ShinobiCharacter active = plugin.characters()
                .getActive(p.getUniqueId());
        return active != null && active.id().equals(characterId) ? p : null;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack item(Material mat, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            if (!lore.isEmpty()) meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
