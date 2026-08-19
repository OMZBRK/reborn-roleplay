package com.reborn.shinobicore.character.gui;

import com.reborn.shinobicore.character.ChakraAffinity;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.gui.CoreGuiRouter;
import com.reborn.shinobicore.gui.CoreScreen;
import com.reborn.shinobicore.gui.framework.Ui;
import com.reborn.shinobicore.gui.framework.View;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Slot-machine Leaf Test — framework port of the legacy
 * {@code LeafTestGui}. Same arcade layout (info plaque, three reels,
 * back/action/reroll), same state machine (IDLE → SPINNING → RESULTS,
 * plus LEVEL_LOCKED / COMPLETE), same level gates and reroll cap. The
 * per-open spinner state (reels, offsets, task, reroll count) lives in
 * the {@link View}'s state bag; the spin task dies with the view via
 * {@link #onClose}. Player-facing strings are now French, per the
 * house rules.
 */
public final class LeafTestScreen extends CoreScreen {

    /* ------------------------------------------------------ static layout */
    private static final int SLOT_INFO     = 4;
    private static final int[] RESULT_SLOTS = { 11, 13, 15 };
    private static final int SLOT_BACK     = 18;
    private static final int SLOT_ACTION   = 22;
    private static final int SLOT_REROLL   = 26;

    public static final int MAX_REROLLS_PER_SLOT = 10;
    public static final int LEVEL_GATE_SECOND = 6;
    public static final int LEVEL_GATE_THIRD  = 12;

    public enum State { IDLE, SPINNING, RESULTS, LEVEL_LOCKED, COMPLETE }

    /* ----------------------------------------------------- view-state keys */
    private static final String S_STATE    = "leafState";
    private static final String S_RESULTS  = "results";      // ChakraAffinity[3]
    private static final String S_OFFSETS  = "spinOffsets";  // int[3]
    private static final String S_REROLLS  = "rerollsUsed";  // Integer
    private static final String S_POOL     = "rollPool";     // ChakraAffinity[]
    private static final String S_TASK     = "spinTask";     // BukkitTask

    private static final String ACT_ACTION = "leaf:action";
    private static final String ACT_RESULT = "leaf:result";

    public LeafTestScreen(CoreGuiRouter router) {
        super(router);
    }

    /* ------------------------------------------------------------- open */

    public void open(Player viewer, ShinobiCharacter c) {
        View view = router.screens().open(viewer, this, Map.of(
                CharacterEditScreen.S_OWNER, c.ownerId(),
                CharacterEditScreen.S_CHAR_ID, c.id()));
        resetForCurrentSlot(view, c);
        refresh(viewer, view);
    }

    /* -------------------------------------------------------- rendering */

    @Override
    public Component title(Player viewer, View view) {
        return GuiTitles.framedWithCharacter("Test de la Feuille", character(view));
    }

    @Override
    public int rows(View view) {
        return 3;
    }

    @Override
    public void render(Player viewer, View view, Inventory inv) {
        ShinobiCharacter c = character(view);
        if (c == null) return;
        if (view.get(S_STATE) == null) resetForCurrentSlot(view, c);

        renderInfo(view, inv, c);
        renderReels(view, inv);
        renderAction(view, inv, c);
        inv.setItem(SLOT_BACK, Ui.action(GuiIcons.backButton(), Ui.ACTION_BACK));
        renderRerollCounter(view, inv);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) != null) continue;
            inv.setItem(i, i < 9 ? GuiIcons.border() : GuiIcons.filler());
        }
    }

    /* ------------------------------------------------------------ clicks */

    @Override
    public void onBack(Player viewer, View view) {
        cancelSpin(view);
        ShinobiCharacter c = character(view);
        if (c != null) router.openCharacterEdit(viewer, c);
        else viewer.closeInventory();
    }

    @Override
    public void onClose(Player viewer, View view) {
        // Kill the spin ticker so the scheduler doesn't keep ticking
        // against a dead view (the framework also force-closes on
        // character switch / KO, which lands here too).
        cancelSpin(view);
    }

    @Override
    public void onAction(Player viewer, View view, String action,
                         String value, InventoryClickEvent event) {
        ShinobiCharacter c = character(view);
        if (c == null) { viewer.closeInventory(); return; }
        State state = view.get(S_STATE);
        if (state == null) return;

        if (ACT_ACTION.equals(action)) {
            switch (state) {
                case IDLE -> {
                    startSpin(viewer, view, c);
                    viewer.playSound(viewer.getLocation(),
                            Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.8f);
                }
                case SPINNING -> {
                    stopSpin(viewer, view, c);
                    viewer.playSound(viewer.getLocation(),
                            Sound.BLOCK_GRASS_BREAK, 1.0f, 1.2f);
                }
                case RESULTS -> {
                    if (canReroll(view)) {
                        startSpin(viewer, view, c);
                        viewer.playSound(viewer.getLocation(),
                                Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.6f);
                    } else {
                        viewer.sendMessage(Component.text(
                                "Plus de relances — choisis un des résultats.",
                                NamedTextColor.RED));
                    }
                }
                case LEVEL_LOCKED, COMPLETE -> { }
            }
            return;
        }

        if (ACT_RESULT.equals(action) && value != null && state == State.RESULTS) {
            int reelIndex;
            try { reelIndex = Integer.parseInt(value); }
            catch (NumberFormatException ex) { return; }
            ChakraAffinity[] results = view.get(S_RESULTS);
            if (results == null || reelIndex < 0 || reelIndex >= results.length) return;
            ChakraAffinity picked = results[reelIndex];
            if (picked == null || picked == ChakraAffinity.NONE) return;
            if (!c.addChakraAffinity(picked)) return;
            core().characters().save(c);
            viewer.sendMessage(Component.text(
                    "La feuille se pose — affinité " + picked.displayName() + " éveillée.",
                    NamedTextColor.GREEN));
            viewer.playSound(viewer.getLocation(),
                    Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.4f);
            resetForCurrentSlot(view, c);
            refresh(viewer, view);
        }
    }

    /* ----------------------------------------------------- state machine */

    private void resetForCurrentSlot(View view, ShinobiCharacter c) {
        cancelSpin(view);
        view.set(S_REROLLS, 0);
        view.set(S_RESULTS, new ChakraAffinity[3]);
        view.set(S_OFFSETS, new int[3]);
        ChakraAffinity[] pool = buildRollPool(c);
        view.set(S_POOL, pool);

        int owned = c.chakraAffinities().size();
        State state;
        if (owned >= ShinobiCharacter.MAX_CHAKRA_AFFINITIES) state = State.COMPLETE;
        else if (owned == 1 && c.level() < LEVEL_GATE_SECOND)  state = State.LEVEL_LOCKED;
        else if (owned == 2 && c.level() < LEVEL_GATE_THIRD)   state = State.LEVEL_LOCKED;
        else if (pool.length == 0)                             state = State.COMPLETE;
        else                                                   state = State.IDLE;
        view.set(S_STATE, state);
    }

    private void startSpin(Player viewer, View view, ShinobiCharacter c) {
        State state = view.get(S_STATE);
        ChakraAffinity[] pool = view.get(S_POOL);
        if (state != State.IDLE && state != State.RESULTS) return;
        if (pool == null || pool.length == 0) return;
        if (state == State.RESULTS) {
            int used = view.integer(S_REROLLS, 0);
            if (used >= MAX_REROLLS_PER_SLOT) return;
            view.set(S_REROLLS, used + 1);
        }
        view.set(S_STATE, State.SPINNING);
        int[] offsets = {
                ThreadLocalRandom.current().nextInt(pool.length),
                ThreadLocalRandom.current().nextInt(pool.length),
                ThreadLocalRandom.current().nextInt(pool.length)};
        view.set(S_OFFSETS, offsets);

        cancelSpin(view);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(core(), () -> {
            Inventory inv = view.getInventory();
            if (inv == null) return;
            for (int i = 0; i < 3; i++) {
                offsets[i] = (offsets[i] + 1) % Math.max(1, pool.length);
            }
            renderReels(view, inv);
        }, 2L, 2L);
        view.set(S_TASK, task);
        refresh(viewer, view);
    }

    private void stopSpin(Player viewer, View view, ShinobiCharacter c) {
        if (view.get(S_STATE) != State.SPINNING) return;
        cancelSpin(view);
        ChakraAffinity[] pool = view.get(S_POOL);
        ChakraAffinity[] results = new ChakraAffinity[3];
        for (int i = 0; i < 3; i++) {
            results[i] = pool[ThreadLocalRandom.current().nextInt(pool.length)];
        }
        view.set(S_RESULTS, results);
        view.set(S_STATE, State.RESULTS);
        refresh(viewer, view);
    }

    private void cancelSpin(View view) {
        BukkitTask task = view.get(S_TASK);
        if (task != null) {
            try { task.cancel(); } catch (Exception ignore) { }
            view.set(S_TASK, null);
        }
    }

    private boolean canReroll(View view) {
        return view.get(S_STATE) == State.RESULTS
                && view.integer(S_REROLLS, 0) < MAX_REROLLS_PER_SLOT;
    }

    /* --------------------------------------------------------- rendering */

    private void renderInfo(View view, Inventory inv, ShinobiCharacter c) {
        State state = view.get(S_STATE);
        int owned = c.chakraAffinities().size();
        String slotLabel = slotLabel(Math.min(owned, 2));

        List<String> lore = new ArrayList<>();
        lore.add("&eAcquises : " + describeOwned(c));
        switch (state) {
            case IDLE         -> lore.add("&aPrêt à lancer pour la " + slotLabel + ".");
            case SPINNING     -> lore.add("&eLes feuilles tourbillonnent...");
            case RESULTS      -> lore.add("&aChoisis un résultat — ou relance.");
            case LEVEL_LOCKED -> {
                int required = owned == 1 ? LEVEL_GATE_SECOND : LEVEL_GATE_THIRD;
                lore.add("&cCe cran se débloque au niveau " + required + ".");
                lore.add("&eNiveau actuel : " + c.level());
            }
            case COMPLETE     -> lore.add("&aLes trois affinités sont découvertes.");
        }
        inv.setItem(SLOT_INFO, GuiIcons.primary(Material.OAK_LEAVES,
                "Test de la Feuille — " + slotLabel,
                lore.toArray(new String[0])));
    }

    private void renderReels(View view, Inventory inv) {
        State state = view.get(S_STATE);
        ChakraAffinity[] pool = view.get(S_POOL);
        int[] offsets = view.get(S_OFFSETS);
        ChakraAffinity[] results = view.get(S_RESULTS);
        if (state == null) return;
        switch (state) {
            case SPINNING -> {
                for (int i = 0; i < 3; i++) {
                    if (pool == null || pool.length == 0) {
                        inv.setItem(RESULT_SLOTS[i], GuiIcons.filler());
                        continue;
                    }
                    ChakraAffinity cycling = pool[offsets[i] % pool.length];
                    inv.setItem(RESULT_SLOTS[i], GuiIcons.secondary(
                            elementIcon(cycling),
                            "??? " + cycling.displayName() + " ???",
                            "&eLe vent n'a pas encore choisi..."));
                }
            }
            case RESULTS -> {
                for (int i = 0; i < 3; i++) {
                    ChakraAffinity r = results == null ? null : results[i];
                    if (r == null) {
                        inv.setItem(RESULT_SLOTS[i], GuiIcons.filler());
                        continue;
                    }
                    inv.setItem(RESULT_SLOTS[i], Ui.action(GuiIcons.info(
                            elementIcon(r), r.displayName(),
                            "&e" + elementDescription(r),
                            "&aClique pour choisir cette affinité."),
                            ACT_RESULT, String.valueOf(i)));
                }
            }
            case IDLE, LEVEL_LOCKED, COMPLETE -> {
                for (int slot : RESULT_SLOTS) {
                    inv.setItem(slot, GuiIcons.coloured(Material.STRUCTURE_VOID,
                            "— vide —", NamedTextColor.DARK_GRAY,
                            "&eEn attente du prochain lancer..."));
                }
            }
        }
    }

    private void renderAction(View view, Inventory inv, ShinobiCharacter c) {
        State state = view.get(S_STATE);
        int used = view.integer(S_REROLLS, 0);
        switch (state) {
            case IDLE -> inv.setItem(SLOT_ACTION, Ui.action(GuiIcons.primary(
                    Material.OAK_SAPLING, "Lancer les Feuilles",
                    "&aClique pour lancer les trois rouleaux."), ACT_ACTION));
            case SPINNING -> inv.setItem(SLOT_ACTION, Ui.action(GuiIcons.destructive(
                    Material.REDSTONE_TORCH, "Stop !",
                    "&aClique pour arrêter les rouleaux et révéler les résultats."), ACT_ACTION));
            case RESULTS -> {
                if (canReroll(view)) {
                    inv.setItem(SLOT_ACTION, Ui.action(GuiIcons.secondary(
                            Material.ENDER_PEARL, "Relancer",
                            "&eRelances utilisées : " + used + " / " + MAX_REROLLS_PER_SLOT,
                            used == 0 ? "&aLa première relance est gratuite."
                                    : "&eLes relances suivantes pourront devenir payantes — gratuites pour l'instant.",
                            "&aClique pour relancer les trois rouleaux."), ACT_ACTION));
                } else {
                    inv.setItem(SLOT_ACTION, Ui.action(GuiIcons.destructive(
                            Material.BARRIER, "Plus de relances",
                            "&eRelances utilisées : " + used + " / " + MAX_REROLLS_PER_SLOT,
                            "&cChoisis un des résultats actuels."), ACT_ACTION));
                }
            }
            case LEVEL_LOCKED -> {
                int owned = c.chakraAffinities().size();
                int required = owned == 1 ? LEVEL_GATE_SECOND : LEVEL_GATE_THIRD;
                inv.setItem(SLOT_ACTION, GuiIcons.destructive(
                        Material.BARRIER, "Verrouillé",
                        "&cSe débloque au niveau " + required + ".",
                        "&eReviens après avoir gagné des niveaux."));
            }
            case COMPLETE -> inv.setItem(SLOT_ACTION, GuiIcons.primary(
                    Material.EMERALD, "Entraînement terminé",
                    "&aLes trois affinités sont découvertes."));
        }
    }

    private void renderRerollCounter(View view, Inventory inv) {
        State state = view.get(S_STATE);
        if (state == State.RESULTS || state == State.IDLE) {
            inv.setItem(SLOT_REROLL, GuiIcons.secondary(Material.CLOCK,
                    "Relances : " + view.integer(S_REROLLS, 0) + " / " + MAX_REROLLS_PER_SLOT,
                    "&eLa première relance est gratuite.",
                    "&eLes suivantes pourront devenir payantes plus tard."));
        }
    }

    /* ----------------------------------------------------------- helpers */

    private ShinobiCharacter character(View view) {
        return core().characters().findById(
                view.uuid(CharacterEditScreen.S_OWNER),
                view.uuid(CharacterEditScreen.S_CHAR_ID)).orElse(null);
    }

    private static String slotLabel(int index) {
        return switch (index) {
            case 0  -> "Première Affinité";
            case 1  -> "Deuxième Affinité";
            case 2  -> "Troisième Affinité";
            default -> "Affinité n°" + (index + 1);
        };
    }

    private static String describeOwned(ShinobiCharacter c) {
        if (c.chakraAffinities().isEmpty()) return "(aucune pour l'instant)";
        StringBuilder sb = new StringBuilder();
        Iterator<ChakraAffinity> it = c.chakraAffinities().iterator();
        while (it.hasNext()) {
            sb.append(it.next().displayName());
            if (it.hasNext()) sb.append(", ");
        }
        return sb.toString();
    }

    private static ChakraAffinity[] buildRollPool(ShinobiCharacter c) {
        List<ChakraAffinity> pool = new ArrayList<>(ChakraAffinity.ROLLABLE.length);
        for (ChakraAffinity a : ChakraAffinity.ROLLABLE) {
            if (!c.hasChakraAffinity(a)) pool.add(a);
        }
        return pool.toArray(new ChakraAffinity[0]);
    }

    private static Material elementIcon(ChakraAffinity a) {
        return switch (a) {
            case NONE   -> Material.STRUCTURE_VOID;
            case KATON  -> Material.BLAZE_POWDER;
            case SUITON -> Material.WATER_BUCKET;
            case FUTON  -> Material.WHITE_WOOL;
            case DOTON  -> Material.DIRT;
            case RAITON -> Material.LIGHTNING_ROD;
        };
    }

    private static String elementDescription(ChakraAffinity a) {
        return switch (a) {
            case NONE   -> "Aucun";
            case KATON  -> "Feu";
            case SUITON -> "Eau";
            case FUTON  -> "Vent";
            case DOTON  -> "Terre";
            case RAITON -> "Foudre";
        };
    }
}
