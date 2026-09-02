package com.reborn.shinobicore.character.command;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.CharacterDisplay;
import com.reborn.shinobicore.character.FriendshipManager;
import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /amitier} — open a friendship request on whoever the sender
 * is looking at.
 *
 * <h2>Flow</h2>
 * <ol>
 *   <li>Sender runs {@code /amitier}. A ray-trace from the eye finds
 *       the closest player within {@value #RANGE} blocks in the
 *       sender's look cone. No target = polite error.</li>
 *   <li>Sender's current character + target's current character are
 *       snapshotted into a {@link FriendshipManager.Pending} request
 *       keyed by the target's Mojang UUID.</li>
 *   <li>The target receives a clickable chat prompt —
 *       {@code [Accepter]} / {@code [Refuser]}. Accept fires the hidden
 *       {@code /amitier accept} subcommand, decline fires
 *       {@code /amitier decline}.</li>
 *   <li>On accept, {@link FriendshipManager#addMutual} writes both
 *       sides of the edge and saves both characters.</li>
 * </ol>
 *
 * <p>Because the bond is per-character and not per-account, if the
 * target switches away to a different character between the request
 * and the accept click, the original character ids still bond (frozen
 * in the Pending record). Tab-list visibility resolves at render time
 * from whichever character is currently active.
 */
public class AmitierCommand implements CommandExecutor {

    /** Max distance in blocks for the ray-trace target selection. */
    public static final double RANGE = 10.0;

    private final ShinobiCore plugin;

    public AmitierCommand(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (args.length > 0) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "accept"  -> handleAccept(p);
                case "decline" -> handleDecline(p);
                default -> p.sendMessage(Component.text(
                        "Usage: /amitier", NamedTextColor.RED));
            }
            return true;
        }
        sendRequest(p);
        return true;
    }

    /* ------------------------------------------------- request (initiator) */

    private void sendRequest(Player requester) {
        ShinobiCharacter myChar = plugin.characters().getActive(requester.getUniqueId());
        if (myChar == null) {
            requester.sendMessage(Component.text(
                    "Tu dois avoir un personnage actif.", NamedTextColor.RED));
            return;
        }
        Player target = rayTracePlayer(requester, RANGE);
        if (target == null) {
            requester.sendMessage(Component.text(
                    "Regarde le joueur que tu veux ajouter en ami.",
                    NamedTextColor.RED));
            return;
        }
        if (target.getUniqueId().equals(requester.getUniqueId())) {
            requester.sendMessage(Component.text(
                    "Tu ne peux pas t'ajouter toi-même.", NamedTextColor.RED));
            return;
        }
        ShinobiCharacter theirChar = plugin.characters().getActive(target.getUniqueId());
        if (theirChar == null) {
            requester.sendMessage(Component.text(
                    "Ce joueur n'a pas de personnage actif.", NamedTextColor.RED));
            return;
        }
        if (plugin.friendships().areFriends(myChar, theirChar)) {
            requester.sendMessage(Component.text(
                    "Vous êtes déjà amis.", NamedTextColor.YELLOW));
            return;
        }

        plugin.friendships().openRequest(
                requester.getUniqueId(), myChar.id(),
                target.getUniqueId(),    theirChar.id());

        // Sender confirmation.
        requester.sendMessage(Component.text("Demande d'amitié envoyée à ",
                        NamedTextColor.GREEN)
                .append(CharacterDisplay.styledNameFor(theirChar, myChar))
                .append(Component.text(".", NamedTextColor.GREEN)));

        // Target prompt — viewer-aware rendering so strangers still
        // appear as ???? (unchanged from the chat/me conventions).
        Component who = CharacterDisplay.styledNameFor(myChar, theirChar);
        Component prompt = Component.text("", NamedTextColor.GOLD)
                .append(who)
                .append(Component.text(" veut devenir ami avec toi. ",
                        NamedTextColor.GOLD))
                .append(Component.text("[Accepter]", NamedTextColor.GREEN)
                        .decoration(TextDecoration.BOLD, true)
                        .clickEvent(ClickEvent.runCommand("/amitier accept"))
                        .hoverEvent(HoverEvent.showText(Component.text(
                                "Devenir amis", NamedTextColor.GRAY))))
                .append(Component.text("  "))
                .append(Component.text("[Refuser]", NamedTextColor.RED)
                        .decoration(TextDecoration.BOLD, true)
                        .clickEvent(ClickEvent.runCommand("/amitier decline"))
                        .hoverEvent(HoverEvent.showText(Component.text(
                                "Refuser la demande", NamedTextColor.GRAY))));
        target.sendMessage(prompt);
    }

    /* ---------------------------------------------------- target responses */

    private void handleAccept(Player target) {
        FriendshipManager.Pending req = plugin.friendships().consume(target.getUniqueId());
        if (req == null) {
            target.sendMessage(Component.text(
                    "Aucune demande d'amitié en attente.", NamedTextColor.GRAY));
            return;
        }
        // Re-resolve characters — use the ids frozen into the Pending
        // record, NOT whoever's currently active, so a character switch
        // between request and accept bonds the intended chars.
        ShinobiCharacter targetChar = plugin.characters()
                .findById(req.targetOwner(), req.targetCharacter()).orElse(null);
        ShinobiCharacter requesterChar = plugin.characters()
                .findById(req.requesterOwner(), req.requesterCharacter()).orElse(null);
        if (targetChar == null || requesterChar == null) {
            target.sendMessage(Component.text(
                    "Personnage introuvable — demande annulée.", NamedTextColor.RED));
            return;
        }
        boolean added = plugin.friendships().addMutual(requesterChar, targetChar);
        if (!added) {
            target.sendMessage(Component.text(
                    "Vous êtes déjà amis.", NamedTextColor.YELLOW));
            return;
        }
        target.sendMessage(Component.text("Vous êtes maintenant amis avec ",
                        NamedTextColor.GREEN)
                .append(CharacterDisplay.styledNameFor(requesterChar, targetChar))
                .append(Component.text(".", NamedTextColor.GREEN)));

        Player requester = plugin.getServer().getPlayer(req.requesterOwner());
        if (requester != null && requester.isOnline()) {
            requester.sendMessage(Component.text(
                    "Votre demande d'amitié a été acceptée.", NamedTextColor.GREEN));
        }
    }

    private void handleDecline(Player target) {
        FriendshipManager.Pending req = plugin.friendships().consume(target.getUniqueId());
        if (req == null) return;
        target.sendMessage(Component.text(
                "Demande refusée.", NamedTextColor.GRAY));
        Player requester = plugin.getServer().getPlayer(req.requesterOwner());
        if (requester != null && requester.isOnline()) {
            requester.sendMessage(Component.text(
                    "Votre demande d'amitié a été refusée.", NamedTextColor.RED));
        }
    }

    /* ----------------------------------------------------- FOV ray-trace */

    /** Locate the player in {@code viewer}'s look cone within
     *  {@code maxDist} blocks. Mirrors {@code RencontrerRootScreen.rayTracePlayer}
     *  (10-block, 0.25-radius cylinder) — same semantics as /rencontrer
     *  "ask for a name". */
    private Player rayTracePlayer(Player viewer, double maxDist) {
        Location eye = viewer.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult trace = viewer.getWorld().rayTraceEntities(
                eye, dir, maxDist, 0.25,
                e -> e instanceof Player other
                        && !other.getUniqueId().equals(viewer.getUniqueId()));
        if (trace == null) return null;
        Entity hit = trace.getHitEntity();
        return hit instanceof Player p ? p : null;
    }
}
