package com.reborn.shinobiabilities.techniques;

import com.reborn.shinobicore.technique.Ability;
import com.reborn.shinobicore.technique.AbilityRegistry;
import com.reborn.shinobiabilities.CoreServices;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.util.Players;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;

/**
 * Orchestrates the learning flow: shelf click → checks → minigame →
 * on success the ability id lands in the character's
 * {@code knownAbilities} (persisted through ShinobiCore's repository)
 * and the parchemin is consumed from the shelf.
 */
public final class TechniquesService {

    private final JavaPlugin plugin;
    private final CoreServices core;
    private final AbilityRegistry registry;
    private final LearningShelfManager shelves;
    private final LearningMinigame minigame;

    public TechniquesService(JavaPlugin plugin, CoreServices core,
                             AbilityRegistry registry,
                             LearningShelfManager shelves,
                             LearningMinigame minigame) {
        this.plugin = plugin;
        this.core = core;
        this.registry = registry;
        this.shelves = shelves;
        this.minigame = minigame;
        minigame.wire(this::onMinigameEnd);
        shelves.wire(this);
    }

    public LearningMinigame minigame() { return minigame; }

    /* ---------------------------------------------------------------- flow */

    public void attemptLearn(Player p, Location shelfLoc, int slot, String abilityId) {
        Ability a = registry.byId(abilityId);
        if (a == null) {
            p.sendMessage(Component.text("Ce parchemin est illisible…", NamedTextColor.RED));
            return;
        }
        ShinobiCharacter c = Players.activeOrWarn(core.characters(), p);
        if (c == null) return;
        if (core.ko() != null && core.ko().isKo(p.getUniqueId())) return;
        if (c.knowsAbility(a.id())) {
            p.sendMessage(Component.text(
                    "Tu connais déjà « " + a.name() + " ».", NamedTextColor.YELLOW));
            return;
        }
        if (minigame.isActive(p)) {
            p.sendMessage(Component.text(
                    "Termine d'abord ton entraînement en cours.", NamedTextColor.YELLOW));
            return;
        }
        minigame.start(p, a, shelfLoc, slot);
    }

    private void onMinigameEnd(Player p, LearningMinigame.Result result) {
        if (!result.success()) return; // failure feedback already sent

        ShinobiCharacter c = Players.active(core.characters(), p);
        if (c == null) return;

        // The scroll must still be on the shelf — someone may have taken
        // it mid-training.
        if (!shelves.consume(result.shelfLoc(), result.slot(), result.ability().id())) {
            p.sendMessage(Component.text(
                    "Le parchemin a disparu de l'étagère…", NamedTextColor.RED));
            return;
        }

        c.learnAbility(result.ability().id());
        core.characters().save(c);

        p.showTitle(Title.title(
                Component.text("Technique apprise !", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(result.ability().name(), NamedTextColor.AQUA),
                Title.Times.times(Duration.ofMillis(200),
                        Duration.ofSeconds(2), Duration.ofMillis(600))));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        p.sendMessage(Component.text(
                "« " + result.ability().name() + " » rejoint tes techniques connues"
                        + (result.ability().isCastable()
                        ? " — lie-la via Accroupi + F sur l'objet "
                        + result.ability().jutsu().itemType().displayName() + "."
                        : "."), NamedTextColor.GREEN));
    }
}
