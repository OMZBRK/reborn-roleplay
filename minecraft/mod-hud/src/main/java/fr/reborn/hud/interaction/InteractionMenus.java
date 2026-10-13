package fr.reborn.hud.interaction;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Construit le menu d'interaction (style GTA) selon la cible visée et
 * dispatche les actions vers des commandes serveur.
 *
 * <p><b>Dépendance serveur</b> : les commandes (saluer/porter/fouiller…) doivent
 * exister côté plugin (ShinobiCore &amp; co). Tant qu'elles n'existent pas, elles
 * apparaîtront comme « commande inconnue ». Le framework client (menu +
 * sous-menus + dispatch) est complet ; il suffira de mapper les vraies commandes.
 */
public final class InteractionMenus {

    private InteractionMenus() {}

    /** Envoie une commande au serveur (sans le slash). */
    public static void sendCommand(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            mc.getConnection().sendCommand(command);
        }
    }

    /** Affiche un message d'info côté client (chat local, pas envoyé au serveur). */
    public static void info(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§6[Reborn] §f" + text));
        }
    }

    /** Copie du texte dans le presse-papiers. */
    public static void copy(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        info("Copié : §e" + text);
    }

    /** Menu d'un joueur ; sur un corps à terre / KO / mort, celui du corps (KO-7). */
    public static List<InteractionItem> forPlayer(String name, java.util.UUID id) {
        fr.reborn.hud.ko.CorpsClient.Body b = fr.reborn.hud.ko.CorpsClient.body(id);
        return b != null ? forBody(name, b) : forPlayer(name);
    }

    /**
     * Corps à terre ou KO : ausculter, fouiller, porter, attraper à la gorge, et la mise à mort RP. Avant la
     * validation du staff : « Demander une RPK ». Après : « Achever » et les exécutions possédées — c'est cette
     * dernière action qui tue.
     */
    public static List<InteractionItem> forBody(String name, fr.reborn.hud.ko.CorpsClient.Body b) {
        if (b.phase() == 3) {
            return List.of(
                    InteractionItem.action("Constater la mort", () -> info("Ce corps ne respire plus.")),
                    InteractionItem.action("Porter", () -> sendCommand("corps porter " + name)),
                    InteractionItem.action("Fouiller", () -> sendCommand("corps fouiller " + name)),
                    prelever(name));
        }
        java.util.List<InteractionItem> l = new java.util.ArrayList<>();
        l.add(InteractionItem.action("Ausculter (état)", () -> sendCommand("osculter")));
        l.add(InteractionItem.action("Attraper à la gorge", () -> sendCommand("corps gorge " + name)));
        l.add(InteractionItem.action("Porter", () -> sendCommand("corps porter " + name)));
        l.add(InteractionItem.action("Fouiller", () -> sendCommand("corps fouiller " + name)));
        java.util.List<InteractionItem> ex = new java.util.ArrayList<>();
        switch (b.rpk()) {
            case 2 -> {
                l.add(InteractionItem.action("Achever", () -> sendCommand("corps achever " + name)));
                for (fr.reborn.hud.ko.CorpsClient.Exec e : fr.reborn.hud.ko.CorpsClient.executions()) {
                    ex.add(InteractionItem.action(e.name(), () -> sendCommand("corps executer " + name + " " + e.id())));
                }
                if (ex.isEmpty()) ex.add(InteractionItem.action("Aucune exécution possédée", () -> info("Les exécutions s'obtiennent à la boutique.")));
            }
            case 1 -> {
                l.add(InteractionItem.action("RPK en attente du staff…", () -> info("Le staff n'a pas encore tranché.")));
                ex.add(InteractionItem.action("RPK pas encore validée", () -> {}));
            }
            default -> {
                l.add(InteractionItem.action("Demander une RPK", () -> sendCommand("corps rpk " + name)));
                ex.add(InteractionItem.action("Il faut une RPK validée", () -> info("Demande d'abord une RPK au staff.")));
            }
        }
        l.add(InteractionItem.submenu("Exécution", ex));
        // Yeux : prélever (RPK validée), soigner (médic rang 2 : greffe ; médic : bandage) — le serveur tranche.
        l.add(b.rpk() == 2 ? prelever(name) : InteractionItem.submenu("Prélever un œil", List.of(
                InteractionItem.action("Il faut une RPK validée", () -> info("Prélever un œil sur un vivant exige une RPK validée par le staff.")))));
        l.add(soinsDesYeux(name));
        return l;
    }

    /** Sous-menu « Prélever un œil » (corps mort, mannequin au sol, ou KO avec RPK validée). {@code cible} : nom ou {@code corps:<uuid>}. */
    static InteractionItem prelever(String cible) {
        return InteractionItem.submenu("Prélever un œil", List.of(
                InteractionItem.action("Œil gauche", () -> sendCommand("yeux prelever " + cible + " gauche")),
                InteractionItem.action("Œil droit", () -> sendCommand("yeux prelever " + cible + " droit"))));
    }

    /** Sous-menu « Soins des yeux » : greffe (fiole en main, médic rang 2) et bandage des orbites (médic). */
    static InteractionItem soinsDesYeux(String name) {
        return InteractionItem.submenu("Soins des yeux", List.of(
                InteractionItem.action("Greffer l'œil gauche", () -> sendCommand("yeux greffer " + name + " gauche")),
                InteractionItem.action("Greffer l'œil droit", () -> sendCommand("yeux greffer " + name + " droit")),
                InteractionItem.action("Bander les yeux", () -> sendCommand("bandage " + name)),
                InteractionItem.action("Retirer le bandage", () -> sendCommand("bandage " + name + " retirer")),
                InteractionItem.action("Examiner les yeux", () -> sendCommand("yeux etat " + name))));
    }

    /**
     * Duel consenti : « Proposer un duel » ; si ce joueur m'a proposé un duel, l'accepter ou le refuser ; si je suis
     * en duel contre lui, l'abandonner. Le serveur revérifie tout (distance, KO, ATA, anti-spam).
     */
    static InteractionItem duel(String name) {
        if (fr.reborn.hud.duel.DuelClient.inDuelWith(name)) {
            return InteractionItem.action("Abandonner le duel", () -> sendCommand("duel abandonner"));
        }
        if (fr.reborn.hud.duel.DuelClient.invitedBy(name)) {
            return InteractionItem.submenu("Duel proposé", List.of(
                InteractionItem.action("Accepter le duel", () -> sendCommand("duel accepter " + name)),
                InteractionItem.action("Refuser le duel", () -> sendCommand("duel refuser " + name))));
        }
        return InteractionItem.action("Proposer un duel", () -> sendCommand("duel proposer " + name));
    }

    public static List<InteractionItem> forPlayer(String name) {
        // NB : les commandes ShinobiCore /rencontrer /amitier /osculter ciblent
        // « la personne regardée » (pas d'argument) → fonctionnent si la cible est
        // à peu près devant. Les autres (porter/fouiller…) attendent un plugin.
        return List.of(
            InteractionItem.action("Saluer", () -> sendCommand("me salue " + name)),
            InteractionItem.action("Se présenter", () -> sendCommand("rencontrer")),
            InteractionItem.action("Message privé…", () -> openChat("/msg " + name + " ")),
            InteractionItem.action("Demander en ami", () -> sendCommand("amitier")),
            InteractionItem.action("Ausculter (état)", () -> sendCommand("osculter")),
            InteractionItem.action("Porter", () -> sendCommand("porter " + name)),
            InteractionItem.action("Fouiller", () -> sendCommand("fouiller " + name)),
            InteractionItem.action("Échanger", () -> sendCommand("trade " + name)),
            duel(name),
            soinsDesYeux(name),
            InteractionItem.submenu("Animations", List.of(
                InteractionItem.action("Saluer", () -> sendCommand("playemote salut")),
                InteractionItem.action("Applaudir", () -> sendCommand("playemote applaudir")),
                InteractionItem.action("Pointer", () -> sendCommand("playemote pointer")),
                InteractionItem.action("Pleurer", () -> sendCommand("playemote pleurer")),
                InteractionItem.action("Toutes les emotes…", InteractionMenus::openEmoteMenu),
                InteractionItem.action("Arrêter", () -> sendCommand("stopemote"))
            )),
            InteractionItem.submenu("Staff", List.of(
                InteractionItem.action("Informations", () -> sendCommand("staff lookup " + name)),
                InteractionItem.action("Téléporter à", () -> sendCommand("tp " + name)),
                InteractionItem.action("Geler", () -> sendCommand("freeze " + name)),
                InteractionItem.action("Sanctionner", () -> sendCommand("sanction " + name))
            ))
        );
    }

    public static List<InteractionItem> forEntity(Entity e) {
        String type = e.getType().getDescription().getString();
        if (fr.reborn.hud.ko.CorpsClient.isCorpse(e.getUUID())) {
            return List.of(
                    InteractionItem.action("Constater la mort", () -> info("Ce corps est froid.")),
                    prelever("corps:" + e.getUUID()));
        }
        return List.of(
            InteractionItem.action("Inspecter (" + type + ")", () -> {
                String hp = (e instanceof LivingEntity le)
                    ? " §7| PV " + (int) le.getHealth() + "/" + (int) le.getMaxHealth() : "";
                info("§e" + type + " §7| pos " + e.blockPosition().toShortString() + hp);
            }),
            InteractionItem.action("Copier la position",
                () -> copy(e.getBlockX() + " " + e.getBlockY() + " " + e.getBlockZ()))
        );
    }

    public static List<InteractionItem> forBlock(BlockPos pos) {
        return List.of(
            InteractionItem.action("Inspecter le bloc", () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null) return;
                BlockState bs = mc.level.getBlockState(pos);
                info("§e" + BuiltInRegistries.BLOCK.getKey(bs.getBlock())
                    + " §7@ " + pos.toShortString());
            }),
            InteractionItem.action("Verrouiller / Cadenas", () -> sendCommand("lock")),
            InteractionItem.action("Gestion", () -> sendCommand("manage")),
            InteractionItem.action("Copier les coordonnées",
                () -> copy(pos.getX() + " " + pos.getY() + " " + pos.getZ())),
            InteractionItem.submenu("Outils de debug", List.of(
                InteractionItem.action("Afficher l'état complet", () -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.level != null) info("§7" + mc.level.getBlockState(pos).toString());
                })
            ))
        );
    }

    /** Menu « sur soi » (clic dans le vide). Mappe de vraies commandes ShinobiCore. */
    public static List<InteractionItem> forSelf() {
        return List.of(
            InteractionItem.action("Ma fiche personnage", () -> sendCommand("character")),
            InteractionItem.action("Mon état", () -> sendCommand("etat")),
            InteractionItem.action("Méditer", () -> sendCommand("meditation")),
            InteractionItem.submenu("Mes emotes", List.of(
                InteractionItem.action("Saluer", () -> sendCommand("playemote salut")),
                InteractionItem.action("Applaudir", () -> sendCommand("playemote applaudir")),
                InteractionItem.action("Pointer", () -> sendCommand("playemote pointer")),
                InteractionItem.action("Pleurer", () -> sendCommand("playemote pleurer")),
                InteractionItem.action("Toutes les emotes…", InteractionMenus::openEmoteMenu),
                InteractionItem.action("Arrêter", () -> sendCommand("stopemote"))
            )),
            InteractionItem.action("Action RP (/me)…", () -> openChat("/me "))
        );
    }

    /**
     * Ouvre le chat pré-rempli (le joueur tape la suite) — pour les commandes qui attendent un
     * texte (message privé, action RP) au lieu de les envoyer vides.
     */
    public static void openChat(String prefill) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreenAndShow(new net.minecraft.client.gui.screens.ChatScreen(prefill, false)));
    }

    /** Ouvre le menu Reborn directement sur l'onglet ANIMATIONS (liste d'emotes). */
    public static void openEmoteMenu() {
        Minecraft.getInstance().setScreenAndShow(
            fr.reborn.hud.animation.AnimationMenuScreen.emotesTab(null));
    }

    public static List<InteractionItem> generic() {
        return List.of(
            InteractionItem.action("Rien à proximité", () -> {})
        );
    }
}
