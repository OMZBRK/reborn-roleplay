package com.reborn.shinobicore.character.gui;

import com.reborn.shinobicore.character.Affinity;
import com.reborn.shinobicore.character.AffinityMultipliers;
import com.reborn.shinobicore.character.ChakraAffinity;
import com.reborn.shinobicore.character.CharacterDisplay;
import com.reborn.shinobicore.character.LevelTable;
import com.reborn.shinobicore.character.NinjaArt;
import com.reborn.shinobicore.character.Rank;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.gui.CoreGuiRouter;
import com.reborn.shinobicore.gui.CoreScreen;
import com.reborn.shinobicore.gui.framework.Ui;
import com.reborn.shinobicore.gui.framework.View;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Per-character staff editor — framework port of the legacy
 * {@code CharacterEditGui}: same 3-row layout (header strip, eight
 * attributes on row 1, level controls + destructive actions on row 2),
 * same chat-prompt flows through {@code ChatInputManager} (anvil input
 * lands in B3). Player-facing strings that used to sit in the
 * GuiListener handlers are now French, per the house rules.
 */
public final class CharacterEditScreen extends CoreScreen {

    static final String S_OWNER   = "owner";
    static final String S_CHAR_ID = "charId";

    private static final int SLOT_NAME     = 10;
    private static final int SLOT_CLAN     = 11;
    private static final int SLOT_AGE      = 12;
    private static final int SLOT_SIZE     = 13;
    private static final int SLOT_ART      = 14;
    private static final int SLOT_AFFINITY = 15;
    private static final int SLOT_ELEMENT  = 16;
    private static final int SLOT_RANK     = 17;
    private static final int SLOT_LVL_DOWN = 19;
    private static final int SLOT_LVL      = 20;
    private static final int SLOT_LVL_UP   = 21;
    private static final int SLOT_ABILITIES = 24;
    private static final int SLOT_DEATH    = 25;
    private static final int SLOT_DELETE   = 26;

    public CharacterEditScreen(CoreGuiRouter router) {
        super(router);
    }

    /* ------------------------------------------------------------- open */

    public void open(Player viewer, ShinobiCharacter c) {
        router.screens().open(viewer, this, Map.of(
                S_OWNER, c.ownerId(),
                S_CHAR_ID, c.id()));
    }

    /* -------------------------------------------------------- rendering */

    @Override
    public Component title(Player viewer, View view) {
        return GuiTitles.framedWithCharacter("Édition", character(view));
    }

    @Override
    public int rows(View view) {
        return 3;
    }

    @Override
    public void render(Player viewer, View view, Inventory inv) {
        ShinobiCharacter c = character(view);
        if (c == null) return;
        // Header strip (legacy borderTop).
        for (int i = 0; i < 9; i++) inv.setItem(i, GuiIcons.border());

        // Editable attributes — yellow (secondary) for prompt items,
        // aqua (info) for cycle items so the palette telegraphs the action.
        inv.setItem(SLOT_NAME, Ui.action(GuiIcons.secondary(Material.NAME_TAG,
                "Nom : " + c.name(),
                "&eClique pour modifier dans le chat"), "name"));
        inv.setItem(SLOT_CLAN, Ui.action(GuiIcons.secondary(Material.PAPER,
                "Clan : " + c.clan(),
                "&eClique pour ouvrir le sélecteur de clan"), "clan"));
        inv.setItem(SLOT_AGE, Ui.action(GuiIcons.secondary(Material.CLOCK,
                "Âge : " + c.age(),
                "&eClique pour modifier dans le chat"), "age"));
        inv.setItem(SLOT_SIZE, Ui.action(GuiIcons.secondary(Material.RABBIT_FOOT,
                "Taille : " + String.format("%.2f", c.size()),
                "&eClique pour modifier dans le chat"), "size"));

        inv.setItem(SLOT_ART, Ui.action(GuiIcons.info(artIcon(c.ninjaArt()),
                "Art Ninja : " + c.ninjaArt().displayName(),
                "&bClique pour changer"), "art"));
        inv.setItem(SLOT_AFFINITY, Ui.action(GuiIcons.coloured(affinityIcon(c.affinity()),
                "Affinité : " + c.affinity().displayName(), NamedTextColor.LIGHT_PURPLE,
                "Multiplicateur : x" + String.format("%.3f",
                        AffinityMultipliers.multiplier(c.affinity(), c.level())),
                "&dClique pour changer"), "affinity"));
        // Informational only — the chakra affinities are rolled through
        // the in-game « Test de la Feuille » item flow (/testfeuille),
        // not from this editor.
        inv.setItem(SLOT_ELEMENT, GuiIcons.info(Material.OAK_LEAVES,
                "Éléments Chakra (" + c.chakraAffinities().size() + "/"
                        + ShinobiCharacter.MAX_CHAKRA_AFFINITIES + ")",
                elementLines(c)));
        inv.setItem(SLOT_RANK, Ui.action(GuiIcons.accent(rankIcon(c.rank()),
                "Rang : " + c.rank().displayName(),
                "&6Clique pour changer"), "rank"));

        inv.setItem(SLOT_LVL_DOWN, Ui.action(GuiIcons.destructive(Material.REDSTONE,
                "Niveau −1",
                "&cClique pour diminuer"), "lvldown"));
        inv.setItem(SLOT_LVL, GuiIcons.primary(Material.EXPERIENCE_BOTTLE,
                "Niveau : " + c.level(),
                "PV :     " + format(c.maxHp()),
                "Chakra : " + format(c.chakra().max())));
        inv.setItem(SLOT_LVL_UP, Ui.action(GuiIcons.primary(Material.EMERALD,
                "Niveau +1",
                "&aClique pour augmenter"), "lvlup"));

        inv.setItem(SLOT_ABILITIES, Ui.action(GuiIcons.info(Material.KNOWLEDGE_BOOK,
                "Techniques (" + c.knownAbilities().size() + " connue(s))",
                "&bClique pour ouvrir l'encyclopédie",
                "&bcomme panneau staff accorder/retirer.",
                "&7Clic gauche : accorder la technique",
                "&7Clic droit sur une connue : retirer"), "abilities"));
        if (c.dead()) {
            inv.setItem(SLOT_DEATH, Ui.action(GuiIcons.primary(Material.TOTEM_OF_UNDYING,
                    "Ressusciter",
                    "&aLe personnage est marqué Mort (RPK).",
                    "&aShift-clic pour le rendre jouable."), "death_revive"));
        } else {
            inv.setItem(SLOT_DEATH, Ui.action(GuiIcons.destructive(Material.WITHER_SKELETON_SKULL,
                    "Marquer Mort (RPK)",
                    "&cShift-clic pour le marquer Mort —",
                    "&csélection bloquée jusqu'à résurrection staff."), "death_kill"));
        }
        inv.setItem(SLOT_DELETE, Ui.action(GuiIcons.destructive(Material.BARRIER,
                "Supprimer le Personnage",
                "&cShift-clic pour confirmer"), "delete"));

        Ui.fillEmpty(inv);
    }

    /* ------------------------------------------------------------ clicks */

    @Override
    public void onAction(Player viewer, View view, String action,
                         String value, InventoryClickEvent event) {
        ShinobiCharacter c = character(view);
        if (c == null) { viewer.closeInventory(); return; }
        var mgr = core().characters();

        switch (action) {
            case "name" -> askText(viewer, c, "Quel est le nouveau nom ?", raw -> {
                String v = raw.trim();
                if (v.isEmpty()) { msg(viewer, "Le nom ne peut pas être vide.", NamedTextColor.RED); return; }
                if (mgr.findByName(c.ownerId(), v).isPresent()) {
                    msg(viewer, "Ce joueur a déjà un personnage avec ce nom.", NamedTextColor.RED);
                    return;
                }
                c.setName(v); mgr.save(c);
                refreshNickname(c);
                msg(viewer, "Nom défini : " + v, NamedTextColor.GREEN);
            });
            case "clan" -> router.openClanPicker(viewer, c);
            case "age" -> askText(viewer, c, "Quel âge pour ce personnage ? (nombre entier)", raw -> {
                try {
                    int age = Integer.parseInt(raw.trim());
                    if (age < 0 || age > 1000) { msg(viewer, "L'âge doit être entre 0 et 1000.", NamedTextColor.RED); return; }
                    c.setAge(age); mgr.save(c);
                    msg(viewer, "Âge défini : " + age, NamedTextColor.GREEN);
                } catch (NumberFormatException ex) {
                    msg(viewer, "Ce n'est pas un nombre : " + raw, NamedTextColor.RED);
                }
            });
            case "size" -> askText(viewer, c, "Quelle taille ? (décimal 0.25 - 4.0, ex. 1.0 = normale)", raw -> {
                try {
                    double v = Double.parseDouble(raw.trim());
                    if (v < 0.25 || v > 4.0) { msg(viewer, "La taille doit être entre 0.25 et 4.0.", NamedTextColor.RED); return; }
                    c.setSize(v); mgr.save(c);
                    mgr.reapplyStatsIfActive(c);
                    msg(viewer, "Taille définie : " + v, NamedTextColor.GREEN);
                } catch (NumberFormatException ex) {
                    msg(viewer, "Ce n'est pas un nombre : " + raw, NamedTextColor.RED);
                }
            });
            case "art" -> {
                c.setNinjaArt(c.ninjaArt() == NinjaArt.TAIJUTSU ? NinjaArt.KENJUTSU : NinjaArt.TAIJUTSU);
                mgr.save(c);
                refresh(viewer, view);
            }
            case "affinity" -> {
                Affinity[] values = Affinity.values();
                c.setAffinity(values[(c.affinity().ordinal() + 1) % values.length]);
                mgr.save(c);
                mgr.reapplyStatsIfActive(c);
                refresh(viewer, view);
            }
            case "rank" -> {
                c.setRank(c.rank().cycle());
                mgr.save(c);
                refresh(viewer, view);
            }
            case "lvlup" -> {
                if (c.level() < LevelTable.MAX_LEVEL) {
                    c.setLevel(c.level() + 1);
                    mgr.save(c);
                    mgr.reapplyStatsIfActive(c);
                    refresh(viewer, view);
                }
            }
            case "lvldown" -> {
                if (c.level() > LevelTable.MIN_LEVEL) {
                    c.setLevel(c.level() - 1);
                    mgr.save(c);
                    mgr.reapplyStatsIfActive(c);
                    refresh(viewer, view);
                }
            }
            case "abilities" -> viewer.sendMessage(Component.text(
                    "Géré par ShinobiAbilities (non installé).",
                    NamedTextColor.GRAY));
            // Actions DIRECTIONNELLES (pas un toggle) + shift-clic requis + on
            // ferme le menu après → immune au double-fire : "kill" met toujours
            // dead=true (idempotent), "revive" toujours dead=false.
            case "death_kill" -> {
                if (!event.getClick().isShiftClick()) {
                    msg(viewer, "Shift-clic pour marquer ce personnage Mort (RPK).", NamedTextColor.YELLOW);
                    GuiSounds.error(viewer);
                    break;
                }
                c.setDead(true);
                mgr.save(c);
                core().ko().forceClear(c.ownerId());
                msg(viewer, c.name() + " est désormais marqué Mort (RPK).", NamedTextColor.RED);
                GuiSounds.accept(viewer);
                viewer.closeInventory();
            }
            case "death_revive" -> {
                if (!event.getClick().isShiftClick()) {
                    msg(viewer, "Shift-clic pour ressusciter ce personnage.", NamedTextColor.YELLOW);
                    GuiSounds.error(viewer);
                    break;
                }
                c.setDead(false);
                mgr.save(c);
                core().ko().forceClear(c.ownerId());
                msg(viewer, c.name() + " est désormais ressuscité.", NamedTextColor.GREEN);
                GuiSounds.accept(viewer);
                viewer.closeInventory();
            }
            case "delete" -> {
                if (event.getClick().isShiftClick()) {
                    mgr.delete(c.ownerId(), c.id());
                    viewer.closeInventory();
                    viewer.sendMessage(Component.text(c.name() + " supprimé.", NamedTextColor.RED));
                    GuiSounds.destructive(viewer);
                } else {
                    viewer.sendMessage(Component.text("Shift-clic pour confirmer la suppression.", NamedTextColor.YELLOW));
                    GuiSounds.error(viewer);
                }
            }
            default -> { }
        }
    }

    /* ----------------------------------------------------------- helpers */

    ShinobiCharacter character(View view) {
        return core().characters()
                .findById(view.uuid(S_OWNER), view.uuid(S_CHAR_ID)).orElse(null);
    }

    /** Chat prompt round-trip: apply the input, then re-open this editor
     *  with fresh state (also re-opened on cancel). Anvil input replaces
     *  the chat trip in B3; the contract stays the same. */
    void askText(Player viewer, ShinobiCharacter c, String prompt, Consumer<String> apply) {
        core().chatInputs().prompt(
                viewer,
                prompt,
                raw -> {
                    apply.accept(raw);
                    ShinobiCharacter latest = core().characters()
                            .findById(c.ownerId(), c.id()).orElse(null);
                    if (latest != null) open(viewer, latest);
                },
                () -> {
                    ShinobiCharacter latest = core().characters()
                            .findById(c.ownerId(), c.id()).orElse(null);
                    if (latest != null) open(viewer, latest);
                });
    }

    private void msg(Player p, String text, NamedTextColor colour) {
        p.sendMessage(Component.text(text, colour));
    }

    /** If {@code c} is somebody's ACTIVE character, push the new
     *  nickname to THEIR display/tab/nameplate right away — whether the
     *  editor is the owner or staff editing someone else's character. */
    void refreshNickname(ShinobiCharacter c) {
        ShinobiCharacter active = core().characters().getActive(c.ownerId());
        if (active == null || !active.id().equals(c.id())) return;
        Player target = core().getServer().getPlayer(c.ownerId());
        if (target != null && target.isOnline()) {
            CharacterDisplay.apply(target, c);
        }
    }

    /* -------------------------------------------------------------- icons */

    private static String[] elementLines(ShinobiCharacter c) {
        List<String> out = new ArrayList<>();
        if (c.chakraAffinities().isEmpty()) {
            out.add("&eAucune affinité élémentaire pour l'instant.");
            out.add("&eUtilise le Test de la Feuille pour en tirer une.");
        } else {
            int i = 1;
            for (ChakraAffinity a : c.chakraAffinities()) {
                out.add("&b" + i + ". " + a.displayName());
                i++;
            }
        }
        return out.toArray(new String[0]);
    }

    private static Material artIcon(NinjaArt art) {
        return switch (art) {
            case TAIJUTSU -> Material.LEATHER_HELMET;
            case KENJUTSU -> Material.IRON_SWORD;
        };
    }

    private static Material affinityIcon(Affinity a) {
        return switch (a) {
            case STRENGTH     -> Material.IRON_INGOT;
            case INTELLIGENCE -> Material.ENCHANTED_BOOK;
            case AGILITY      -> Material.FEATHER;
        };
    }

    private static Material rankIcon(Rank r) {
        return switch (r) {
            case ACADEMY       -> Material.WRITABLE_BOOK;
            case GENIN         -> Material.LEATHER_CHESTPLATE;
            case CHUNIN        -> Material.CHAINMAIL_CHESTPLATE;
            case SPECIAL_JONIN -> Material.GOLDEN_CHESTPLATE;
            case JONIN         -> Material.IRON_CHESTPLATE;
            case ANBU          -> Material.WOLF_ARMOR;
            case SANNIN        -> Material.DIAMOND_CHESTPLATE;
            case KAGE          -> Material.NETHERITE_CHESTPLATE;
        };
    }

    private static String format(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.format("%.1f", v);
    }
}
