package com.reborn.shinobitail.command;

import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobitail.ShinobiTail;
import com.reborn.shinobitail.beast.BeastDefinition;
import com.reborn.shinobitail.data.JinchurikiData;
import com.reborn.shinobitail.data.JinchurikiStore;
import com.reborn.shinobitail.transform.ActiveTransformation;
import com.reborn.shinobitail.transform.TransformationManager;
import com.reborn.shinobitail.util.Fmt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /tail} — the player's status view plus the whole Game Master
 * toolbox. Relationship values are deliberately writable ONLY through
 * here: the design wants staff hands on the psychology dials.
 */
public final class TailCommand implements CommandExecutor, TabCompleter {

    private static final List<String> RELATION_KEYS =
            List.of("trust", "anger", "cooperation", "influence", "rage");

    private final ShinobiTail plugin;

    public TailCommand(ShinobiTail plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------------------------------------ execute */

    @Override
    public boolean onCommand(CommandSender sender, Command cmd,
                             String label, String[] args) {
        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            return status(sender);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("help".equals(sub)) return help(sender);

        if ("union".equals(sub)) return union(sender);

        if ("reload".equals(sub)) {
            if (!sender.hasPermission("shinobitail.admin")) return noPerm(sender);
            plugin.reloadAll(sender);
            return true;
        }

        // Everything below is GM territory.
        if (!sender.hasPermission("shinobitail.gm")) return noPerm(sender);

        return switch (sub) {
            case "gui" -> gui(sender, args);
            case "pause" -> pause(sender, args);
            case "stop" -> stopCmd(sender, args);
            case "bind" -> bind(sender, args);
            case "unbind" -> unbind(sender, args);
            case "reset" -> reset(sender, args);
            case "info" -> info(sender, args);
            case "set" -> setOrAdd(sender, args, false);
            case "add" -> setOrAdd(sender, args, true);
            case "mastery" -> mastery(sender, args);
            case "transform" -> transform(sender, args);
            case "confront" -> confront(sender, args);
            case "speak" -> speak(sender, args);
            case "setinnerworld" -> setInnerWorld(sender, args);
            case "beasts" -> beasts(sender);
            default -> {
                sender.sendMessage(err("Sous-commande inconnue. Essaie /tail help"));
                yield true;
            }
        };
    }

    /* ------------------------------------------------------------- player */

    private boolean status(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(err("Cette commande nécessite un joueur."));
            return true;
        }
        ShinobiCharacter active = plugin.jinchuriki().activeCharacter(p);
        if (active == null) {
            p.sendMessage(err("Aucun personnage actif."));
            return true;
        }
        JinchurikiData data = plugin.jinchuriki().of(active.id());
        BeastDefinition beast = plugin.beasts().byId(data.beastId());
        if (beast == null) {
            p.sendMessage(Component.text(
                    "Aucune présence étrangère en toi… pour l'instant.",
                    NamedTextColor.GRAY));
            return true;
        }

        p.sendMessage(Component.text("— " + beast.beastName() + " —",
                NamedTextColor.DARK_RED));
        var t = plugin.transformations().get(p.getUniqueId());
        if (t != null) {
            p.sendMessage(Component.text("Étape : ", NamedTextColor.GRAY)
                    .append(Component.text(t.stage() + "/" + beast.tails()
                                    + " — " + beast.stage(t.stage()).displayName(),
                            NamedTextColor.GOLD)));
        }
        p.sendMessage(Component.text("Rage : ", NamedTextColor.GRAY)
                .append(Fmt.bar(data.rage(), 20, NamedTextColor.RED))
                .append(Component.text(" " + Fmt.pct(data.rage()),
                        NamedTextColor.RED)));
        for (int s = 1; s <= beast.tails(); s++) {
            p.sendMessage(Component.text("Maîtrise étape " + s + " : ",
                            NamedTextColor.GRAY)
                    .append(Fmt.bar(data.mastery(s), 10, NamedTextColor.AQUA))
                    .append(Component.text(" " + Fmt.pct(data.mastery(s)),
                            NamedTextColor.AQUA)));
        }
        if (plugin.getConfig().getBoolean("display.show-relationship-to-player", false)) {
            p.sendMessage(relationLine(data));
        }
        return true;
    }

    private boolean union(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(err("Cette commande nécessite un joueur."));
            return true;
        }
        ShinobiCharacter active = plugin.jinchuriki().activeCharacter(p);
        if (active == null) {
            p.sendMessage(err("Aucun personnage actif."));
            return true;
        }
        JinchurikiData data = plugin.jinchuriki().of(active.id());
        BeastDefinition beast = plugin.beasts().byId(data.beastId());
        if (beast == null) {
            p.sendMessage(err("Aucun démon n'est scellé en toi."));
            return true;
        }
        if (!data.sealRemoved()) {
            p.sendMessage(err("Le sceau te lie encore — l'Union se gagne face au "
                    + "démon, confiance et coopération parfaites."));
            return true;
        }
        var t = plugin.transformations().get(p.getUniqueId());
        if (t != null && t.union()) {
            plugin.transformations().stop(p,
                    TransformationManager.StopReason.UNION_END);
        } else if (t != null) {
            p.sendMessage(err("Le démon gronde déjà — reprends d'abord le contrôle."));
        } else {
            plugin.transformations().startUnion(p, data, beast);
        }
        return true;
    }

    private boolean help(CommandSender sender) {
        sender.sendMessage(Component.text("Commandes ShinobiTail :", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(
                "(survole une commande pour les détails — clique pour la préparer)",
                NamedTextColor.DARK_GRAY));

        sender.sendMessage(entry("/tail", "/tail", NamedTextColor.YELLOW,
                "Ton lien avec ton démon",
                "Affiche l'étape en cours, la jauge de rage",
                "et ta maîtrise de chaque étape.",
                "Utilisable à tout moment."));

        sender.sendMessage(entry("/tail union", "/tail union", NamedTextColor.GOLD,
                "Mode Union (sceau brisé)",
                "À confiance et coopération parfaites (100%),",
                "brise le sceau lors d'une confrontation pour",
                "ne faire plus qu'un avec ton démon. Ensuite :",
                "pleine puissance à volonté, zéro rage,",
                "et tu t'arrêtes quand TU le décides."));

        if (sender.hasPermission("shinobitail.gm")) {
            sender.sendMessage(Component.text("Maître du Jeu :",
                    NamedTextColor.LIGHT_PURPLE));

            sender.sendMessage(entry("/tail gui [personnage]",
                    "/tail gui", NamedTextColor.GRAY,
                    "Interface d'administration",
                    "Liste des jinchūriki, puis éditeur complet :",
                    "relation, rage, maîtrise, transformer,",
                    "stopper, confronter, pause — sans commandes."));

            sender.sendMessage(entry("/tail pause <personnage>",
                    "/tail pause ", NamedTextColor.GRAY,
                    "Geler / relancer la rage",
                    "Suspend la montée de rage et les fenêtres",
                    "de contrôle pendant une narration, sans",
                    "retirer les effets de l'étape.",
                    "Cible : personnage (pseudo accepté en secours)."));

            sender.sendMessage(entry("/tail stop <personnage>",
                    "/tail stop ", NamedTextColor.GRAY,
                    "Arrêt d'urgence",
                    "Stoppe immédiatement la transformation.",
                    "Cible : personnage (pseudo accepté en secours)."));

            sender.sendMessage(entry("/tail bind <personnage> <démon>",
                    "/tail bind ", NamedTextColor.GRAY,
                    "Sceller un démon",
                    "Lie un démon à un PERSONNAGE (pas au compte).",
                    "À utiliser à la création RP d'un jinchūriki.",
                    "Ex : /tail bind Naruto kurama"));

            sender.sendMessage(entry("/tail unbind <personnage>",
                    "/tail unbind ", NamedTextColor.GRAY,
                    "Briser le sceau",
                    "Retire le démon et EFFACE toutes les données",
                    "(relation, maîtrise, rage). Irréversible —",
                    "pour les extractions RP validées."));

            sender.sendMessage(entry("/tail reset <personnage>",
                    "/tail reset ", NamedTextColor.GRAY,
                    "Réinitialiser un jinchūriki",
                    "Remet à ZÉRO étape, maîtrise, relation, rage,",
                    "progression et statistiques — le démon RESTE",
                    "scellé (contrairement à /tail unbind).",
                    "Relance l'arc d'un personnage. Confirmation requise."));

            sender.sendMessage(entry("/tail info <personnage>",
                    "/tail info ", NamedTextColor.GRAY,
                    "Fiche jinchūriki (staff)",
                    "Relation complète (confiance, colère,",
                    "coopération, emprise), rage, maîtrise par",
                    "étape, statistiques et transformation en cours."));

            sender.sendMessage(entry("/tail set <personnage> <clé> <0-100>",
                    "/tail set ", NamedTextColor.GRAY,
                    "Fixer une valeur de relation",
                    "Clés : trust, anger, cooperation, influence, rage.",
                    "À ajuster après chaque scène marquante entre",
                    "le jinchūriki et son démon — c'est le cœur",
                    "du système : ces valeurs pilotent tous les jets."));

            sender.sendMessage(entry("/tail add <personnage> <clé> <±delta>",
                    "/tail add ", NamedTextColor.GRAY,
                    "Ajuster une valeur (relatif)",
                    "Mêmes clés que /tail set, en delta :",
                    "+10 après une scène de confiance,",
                    "-15 après une trahison, etc."));

            sender.sendMessage(entry("/tail mastery <personnage> <étape> <0-100>",
                    "/tail mastery ", NamedTextColor.GRAY,
                    "Maîtrise d'une étape",
                    "0-100% par étape. Plus c'est haut, plus le",
                    "joueur a de chances de RÉSISTER dans le",
                    "Monde Intérieur et de reprendre le contrôle.",
                    "À récompenser après entraînements RP."));

            sender.sendMessage(entry("/tail transform <personnage> <étape|next|stop>",
                    "/tail transform ", NamedTextColor.GRAY,
                    "Forcer une transformation",
                    "<étape> : entre/saute à cette étape.",
                    "next : étape suivante. stop : tout arrêter.",
                    "Pour piloter une scène à la main.",
                    "Cible : personnage (pseudo accepté en secours)."));

            sender.sendMessage(entry("/tail confront <personnage>",
                    "/tail confront ", NamedTextColor.GRAY,
                    "Déclencher le Monde Intérieur",
                    "Envoie immédiatement un hôte transformé",
                    "face à son démon (choix Céder / Résister).",
                    "Parfait pour un climax RP.",
                    "Cible : personnage (pseudo accepté en secours)."));

            sender.sendMessage(entry("/tail speak <personnage> <message…>",
                    "/tail speak ", NamedTextColor.GRAY,
                    "Parler avec la voix du démon",
                    "Message privé stylisé 【Démon】 + battement",
                    "de cœur, visible uniquement par l'hôte.",
                    "L'outil RP principal du MJ.",
                    "Cible : personnage (pseudo accepté en secours)."));

            sender.sendMessage(entry("/tail setinnerworld <démon>",
                    "/tail setinnerworld ", NamedTextColor.GRAY,
                    "Définir le Monde Intérieur",
                    "Enregistre TA position actuelle comme lieu",
                    "de confrontation pour ce démon (sa cage).",
                    "À faire une fois par démon, décor construit."));

            sender.sendMessage(entry("/tail beasts",
                    "/tail beasts", NamedTextColor.GRAY,
                    "Lister les démons",
                    "Tous les démons de beasts.yml, avec alerte",
                    "si leur Monde Intérieur n'est pas défini."));
        }

        if (sender.hasPermission("shinobitail.admin")) {
            sender.sendMessage(entry("/tail reload",
                    "/tail reload", NamedTextColor.GRAY,
                    "Recharger la configuration",
                    "Relit config.yml et beasts.yml à chaud,",
                    "sans redémarrage du serveur."));
        }
        return true;
    }

    /**
     * One interactive help line: hover = title + description,
     * click = pre-types {@code suggest} in the chat bar.
     */
    private static Component entry(String label, String suggest,
                                   NamedTextColor color, String title,
                                   String... lines) {
        Component hover = Component.text(title, NamedTextColor.GOLD);
        for (String l : lines) {
            hover = hover.append(Component.newline())
                    .append(Component.text(l, NamedTextColor.GRAY));
        }
        hover = hover.append(Component.newline()).append(Component.newline())
                .append(Component.text("Clic : prépare la commande dans le chat",
                        NamedTextColor.DARK_AQUA));
        return Component.text("  " + label, color)
                .hoverEvent(hover)
                .clickEvent(ClickEvent.suggestCommand(suggest));
    }

    /* ----------------------------------------------------------------- GM */

    private boolean gui(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(err("Cette commande nécessite un joueur."));
            return true;
        }
        if (args.length >= 2) {
            var resolved = plugin.jinchuriki().resolveCharacter(args[1]);
            if (resolved == null) return unknownCharacter(sender, args[1]);
            plugin.adminGui().openEditor(p, resolved.character().id());
        } else {
            plugin.adminGui().openList(p);
        }
        return true;
    }

    private boolean pause(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(err("Usage : /tail pause <personnage>"));
            return true;
        }
        Player target = resolveLiveTarget(sender, args[1]);
        if (target == null) return true;
        var t = plugin.transformations().get(target.getUniqueId());
        if (t == null) {
            sender.sendMessage(err(args[1] + " n'est pas transformé."));
            return true;
        }
        plugin.transformations().setPaused(target, !t.paused());
        sender.sendMessage(ok(t.paused()
                ? "Rage en PAUSE pour " + args[1] + " (narration libre)."
                : "Rage relancée pour " + args[1] + "."));
        return true;
    }

    private boolean stopCmd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(err("Usage : /tail stop <personnage>"));
            return true;
        }
        Player target = resolveLiveTarget(sender, args[1]);
        if (target == null) return true;
        if (!plugin.transformations().isTransformed(target.getUniqueId())) {
            sender.sendMessage(err(args[1] + " n'est pas transformé."));
            return true;
        }
        plugin.innerWorld().abort(target);
        plugin.transformations().stop(target,
                TransformationManager.StopReason.GM);
        sender.sendMessage(ok("Transformation arrêtée pour " + args[1] + "."));
        return true;
    }

    private boolean bind(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(err("Usage : /tail bind <personnage> <démon>"));
            return true;
        }
        var resolved = plugin.jinchuriki().resolveCharacter(args[1]);
        if (resolved == null) return unknownCharacter(sender, args[1]);
        BeastDefinition beast = plugin.beasts().byId(args[2]);
        if (beast == null) return unknownBeast(sender, args[2]);

        JinchurikiData data = plugin.jinchuriki().of(resolved.character().id());
        data.setBeastId(beast.id());
        plugin.jinchuriki().save(data);
        sender.sendMessage(ok(beast.beastName() + " est désormais scellé en "
                + resolved.character().name() + "."));
        return true;
    }

    private boolean unbind(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(err("Usage : /tail unbind <personnage>"));
            return true;
        }
        var resolved = plugin.jinchuriki().resolveCharacter(args[1]);
        if (resolved == null) return unknownCharacter(sender, args[1]);

        Player online = Bukkit.getPlayer(resolved.ownerId());
        if (online != null
                && plugin.transformations().isTransformed(online.getUniqueId())) {
            plugin.innerWorld().abort(online);
            plugin.transformations().stop(online,
                    TransformationManager.StopReason.UNBIND);
        }
        plugin.jinchuriki().unbind(resolved.character().id());
        sender.sendMessage(ok("Le sceau de " + resolved.character().name()
                + " a été brisé (données effacées)."));
        return true;
    }

    /**
     * Reset a jinchūriki back to the very beginning while keeping the beast
     * sealed — the counterpart to {@link #unbind} (which removes the beast
     * entirely). Destructive, so it asks for a click-to-confirm first.
     */
    private boolean reset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(err("Usage : /tail reset <personnage> [confirm]"));
            return true;
        }
        var resolved = plugin.jinchuriki().resolveCharacter(args[1]);
        if (resolved == null) return unknownCharacter(sender, args[1]);
        JinchurikiData data = plugin.jinchuriki().of(resolved.character().id());
        if (data.beastId() == null) {
            sender.sendMessage(err(resolved.character().name()
                    + " n'est pas un jinchūriki — rien à réinitialiser."));
            return true;
        }
        BeastDefinition beast = plugin.beasts().byId(data.beastId());
        String beastName = beast != null ? beast.beastName() : data.beastId();

        // Destructive — wipes a whole arc. Require an explicit confirmation.
        if (args.length < 3 || !"confirm".equalsIgnoreCase(args[2])) {
            sender.sendMessage(Component.text("⚠ Réinitialiser "
                            + resolved.character().name() + " (" + beastName + ") ?",
                    NamedTextColor.RED));
            sender.sendMessage(Component.text("Efface étape, maîtrise, relation, "
                    + "rage, statistiques et progression. Le démon reste scellé. "
                    + "Action irréversible.", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  ▶ Cliquer pour CONFIRMER",
                            NamedTextColor.GOLD)
                    .hoverEvent(Component.text("Réinitialise définitivement "
                            + resolved.character().name(), NamedTextColor.RED))
                    .clickEvent(ClickEvent.runCommand(
                            "/tail reset " + args[1] + " confirm")));
            return true;
        }

        // End any live transformation WITHOUT triggering the point-of-no-return
        // death — a reset is a clean slate, not a casualty.
        Player online = Bukkit.getPlayer(resolved.ownerId());
        if (online != null
                && plugin.transformations().isTransformed(online.getUniqueId())) {
            plugin.innerWorld().abort(online);
            plugin.transformations().stop(online,
                    TransformationManager.StopReason.RESET);
        }

        plugin.jinchuriki().resetProgress(resolved.character().id());
        sender.sendMessage(ok(resolved.character().name() + " ⇄ " + beastName
                + " : tout est réinitialisé. Le jinchūriki repart de zéro."));
        return true;
    }

    private boolean info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(err("Usage : /tail info <personnage>"));
            return true;
        }
        var resolved = plugin.jinchuriki().resolveCharacter(args[1]);
        if (resolved == null) return unknownCharacter(sender, args[1]);
        JinchurikiData data = plugin.jinchuriki().of(resolved.character().id());
        BeastDefinition beast = plugin.beasts().byId(data.beastId());
        if (beast == null) {
            sender.sendMessage(err(resolved.character().name()
                    + " n'est pas un jinchūriki."));
            return true;
        }
        sender.sendMessage(Component.text("— " + resolved.character().name()
                        + " ⇄ " + beast.beastName() + " ("
                        + beast.tails() + " queue(s), "
                        + beast.personality().id() + ") —",
                NamedTextColor.GOLD));
        sender.sendMessage(relationLine(data));
        sender.sendMessage(Component.text("Rage : " + Fmt.pct1(data.rage()),
                NamedTextColor.RED));
        StringBuilder m = new StringBuilder("Maîtrise :");
        for (int s = 1; s <= beast.tails(); s++) {
            m.append(" [").append(s).append("] ").append(Fmt.pct(data.mastery(s)));
        }
        sender.sendMessage(Component.text(m.toString(), NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Transformations : " + data.transformations()
                        + " — Résistances : " + data.resistSuccesses() + " réussies / "
                        + data.resistFailures() + " échouées — Temps transformé : "
                        + (data.secondsTransformed() / 60) + " min",
                NamedTextColor.GRAY));
        Player online = Bukkit.getPlayer(resolved.ownerId());
        if (online != null) {
            var t = plugin.transformations().get(online.getUniqueId());
            if (t != null) {
                sender.sendMessage(Component.text("EN TRANSFORMATION — étape "
                                + t.stage() + "/" + beast.tails(),
                        NamedTextColor.DARK_RED));
            }
        }
        return true;
    }

    private boolean setOrAdd(CommandSender sender, String[] args, boolean add) {
        if (args.length < 4) {
            sender.sendMessage(err("Usage : /tail " + (add ? "add" : "set")
                    + " <personnage> <" + String.join("|", RELATION_KEYS) + "> <valeur>"));
            return true;
        }
        var resolved = plugin.jinchuriki().resolveCharacter(args[1]);
        if (resolved == null) return unknownCharacter(sender, args[1]);
        JinchurikiData data = plugin.jinchuriki().of(resolved.character().id());
        if (data.beastId() == null) {
            sender.sendMessage(err(resolved.character().name()
                    + " n'est pas un jinchūriki (utilise /tail bind d'abord)."));
            return true;
        }
        double value;
        try {
            value = Double.parseDouble(args[3]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(err("Valeur invalide : " + args[3]));
            return true;
        }
        String key = args[2].toLowerCase(Locale.ROOT);
        double base = switch (key) {
            case "trust" -> data.trust();
            case "anger" -> data.anger();
            case "cooperation" -> data.cooperation();
            case "influence" -> data.influence();
            case "rage" -> data.rage();
            default -> Double.NaN;
        };
        if (Double.isNaN(base)) {
            sender.sendMessage(err("Clé inconnue : " + key));
            return true;
        }
        double target = add ? base + value : value;
        switch (key) {
            case "trust" -> data.setTrust(target);
            case "anger" -> data.setAnger(target);
            case "cooperation" -> data.setCooperation(target);
            case "influence" -> data.setInfluence(target);
            case "rage" -> data.setRage(target);
            default -> { }
        }
        plugin.jinchuriki().save(data);
        double now = switch (key) {
            case "trust" -> data.trust();
            case "anger" -> data.anger();
            case "cooperation" -> data.cooperation();
            case "influence" -> data.influence();
            default -> data.rage();
        };
        sender.sendMessage(ok(resolved.character().name() + " — " + key
                + " : " + Fmt.pct1(now)));
        return true;
    }

    private boolean mastery(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(err("Usage : /tail mastery <personnage> <étape> <0-100>"));
            return true;
        }
        var resolved = plugin.jinchuriki().resolveCharacter(args[1]);
        if (resolved == null) return unknownCharacter(sender, args[1]);
        JinchurikiData data = plugin.jinchuriki().of(resolved.character().id());
        BeastDefinition beast = plugin.beasts().byId(data.beastId());
        if (beast == null) {
            sender.sendMessage(err(resolved.character().name()
                    + " n'est pas un jinchūriki."));
            return true;
        }
        try {
            int stage = Integer.parseInt(args[2]);
            if (stage < 1 || stage > beast.tails()) {
                sender.sendMessage(err("Étape hors limites (1-" + beast.tails() + ")."));
                return true;
            }
            data.setMastery(stage, Double.parseDouble(args[3]));
        } catch (NumberFormatException ex) {
            sender.sendMessage(err("Nombre invalide."));
            return true;
        }
        plugin.jinchuriki().save(data);
        sender.sendMessage(ok(resolved.character().name() + " — maîtrise étape "
                + args[2] + " : " + Fmt.pct(data.mastery(Integer.parseInt(args[2])))));
        return true;
    }

    private boolean transform(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(err("Usage : /tail transform <personnage> <étape|next|stop>"));
            return true;
        }
        Player target = resolveLiveTarget(sender, args[1]);
        if (target == null) return true;
        JinchurikiData data = plugin.jinchuriki().ofActive(target);
        if (data == null) {
            sender.sendMessage(err("Le personnage actif de " + args[1]
                    + " n'est pas un jinchūriki."));
            return true;
        }
        BeastDefinition beast = plugin.beasts().byId(data.beastId());
        if (beast == null) {
            sender.sendMessage(err("Démon '" + data.beastId()
                    + "' absent de beasts.yml — corrige la config."));
            return true;
        }
        TransformationManager tm = plugin.transformations();
        String what = args[2].toLowerCase(Locale.ROOT);

        switch (what) {
            case "stop" -> {
                plugin.innerWorld().abort(target);
                tm.stop(target, TransformationManager.StopReason.GM);
                sender.sendMessage(ok("Transformation arrêtée pour " + args[1] + "."));
            }
            case "next" -> {
                if (tm.isTransformed(target.getUniqueId())) tm.escalate(target);
                else tm.begin(target, data, beast, 1, ActiveTransformation.Cause.GM);
                sender.sendMessage(ok("Étape suivante pour " + args[1] + "."));
            }
            default -> {
                int stage;
                try {
                    stage = Integer.parseInt(what);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(err("Étape invalide : " + what));
                    return true;
                }
                if (stage < 1 || stage > beast.tails()) {
                    sender.sendMessage(err("Étape hors limites (1-"
                            + beast.tails() + ")."));
                    return true;
                }
                tm.setStage(target, data, beast, stage);
                sender.sendMessage(ok(args[1] + " → étape " + stage + "."));
            }
        }
        return true;
    }

    private boolean confront(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(err("Usage : /tail confront <personnage>"));
            return true;
        }
        Player target = resolveLiveTarget(sender, args[1]);
        if (target == null) return true;
        if (!plugin.transformations().isTransformed(target.getUniqueId())) {
            sender.sendMessage(err(args[1] + " n'est pas transformé."));
            return true;
        }
        if (plugin.innerWorld().inSession(target.getUniqueId())) {
            sender.sendMessage(err(args[1] + " est déjà dans le Monde Intérieur."));
            return true;
        }
        plugin.innerWorld().beginConfrontation(target, "appel du démon");
        sender.sendMessage(ok("Confrontation déclenchée pour " + args[1] + "."));
        return true;
    }

    private boolean speak(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(err("Usage : /tail speak <personnage> <message…>"));
            return true;
        }
        Player target = resolveLiveTarget(sender, args[1]);
        if (target == null) return true;
        JinchurikiData data = plugin.jinchuriki().ofActive(target);
        BeastDefinition beast = data != null
                ? plugin.beasts().byId(data.beastId()) : null;
        if (beast == null) {
            sender.sendMessage(err("Le personnage actif de " + args[1]
                    + " n'est pas un jinchūriki."));
            return true;
        }
        String message = String.join(" ",
                java.util.Arrays.copyOfRange(args, 2, args.length));
        Fmt.beastWhisper(plugin, target, beast, message);
        target.playSound(target.getLocation(),
                org.bukkit.Sound.ENTITY_WARDEN_HEARTBEAT, 0.7f, 0.7f);
        sender.sendMessage(ok("【" + beast.beastName() + "】 → "
                + args[1] + " : " + message));
        return true;
    }

    private boolean setInnerWorld(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(err("Cette commande nécessite un joueur."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(err("Usage : /tail setinnerworld <démon>"));
            return true;
        }
        BeastDefinition beast = plugin.beasts().byId(args[1]);
        if (beast == null) return unknownBeast(sender, args[1]);
        plugin.beasts().setInnerWorld(beast.id(), p.getLocation());
        sender.sendMessage(ok("Monde Intérieur de " + beast.beastName()
                + " défini sur ta position."));
        return true;
    }

    private boolean beasts(CommandSender sender) {
        sender.sendMessage(Component.text("Démons configurés :", NamedTextColor.GOLD));
        for (BeastDefinition b : plugin.beasts().all().values()) {
            boolean hasInner = plugin.beasts().innerWorld(b.id()) != null;
            sender.sendMessage(Component.text("  " + b.id() + " — " + b.beastName()
                            + " (" + b.tails() + " queue(s), "
                            + b.personality().id() + ")"
                            + (hasInner ? "" : " — ⚠ monde intérieur non défini"),
                    hasInner ? NamedTextColor.GRAY : NamedTextColor.YELLOW));
        }
        return true;
    }

    /* ------------------------------------------------------------ helpers */

    /**
     * Live-action commands (transform / confront / speak / pause / stop)
     * target a CHARACTER first — the beast is sealed in a character, not
     * in an account. The Minecraft username still works as a fallback.
     * Errors are messaged here; {@code null} means "stop processing".
     */
    private Player resolveLiveTarget(CommandSender sender, String arg) {
        var resolved = plugin.jinchuriki().resolveCharacter(arg);
        if (resolved != null) {
            Player owner = Bukkit.getPlayer(resolved.ownerId());
            if (owner == null || !owner.isOnline()) {
                sender.sendMessage(err("Le joueur de "
                        + resolved.character().name() + " n'est pas en ligne."));
                return null;
            }
            ShinobiCharacter active = plugin.characters() != null
                    ? plugin.characters().getActive(owner.getUniqueId())
                    : null;
            if (active == null || !active.id().equals(resolved.character().id())) {
                sender.sendMessage(err(owner.getName()
                        + " est en ligne mais n'incarne pas "
                        + resolved.character().name()
                        + (active != null ? " (actif : " + active.name() + ")" : "")
                        + "."));
                return null;
            }
            return owner;
        }
        Player direct = Bukkit.getPlayerExact(arg);
        if (direct != null) return direct;
        sender.sendMessage(err(
                "Aucun personnage ni joueur trouvé pour : " + arg));
        return null;
    }

    private Component relationLine(JinchurikiData data) {
        return Component.text("Confiance " + Fmt.pct(data.trust())
                        + " — Colère " + Fmt.pct(data.anger())
                        + " — Coopération " + Fmt.pct(data.cooperation())
                        + " — Emprise " + Fmt.pct(data.influence()),
                NamedTextColor.YELLOW);
    }

    private boolean unknownCharacter(CommandSender sender, String name) {
        sender.sendMessage(err("Aucun personnage trouvé pour : " + name
                + " (le joueur doit être passé en ligne récemment)"));
        return true;
    }

    private boolean unknownBeast(CommandSender sender, String id) {
        sender.sendMessage(err("Démon inconnu : " + id
                + " (voir /tail beasts)"));
        return true;
    }

    private boolean noPerm(CommandSender sender) {
        sender.sendMessage(err("Permission insuffisante."));
        return true;
    }

    private static Component ok(String text) {
        return Component.text(text, NamedTextColor.GREEN);
    }

    private static Component err(String text) {
        return Component.text(text, NamedTextColor.RED);
    }

    /* ----------------------------------------------------- tab completion */

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd,
                                      String alias, String[] args) {
        boolean gm = sender.hasPermission("shinobitail.gm");
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("status", "help", "union"));
            if (gm) subs.addAll(List.of("gui", "pause", "stop",
                    "bind", "unbind", "reset", "info", "set", "add",
                    "mastery", "transform", "confront", "speak",
                    "setinnerworld", "beasts"));
            if (sender.hasPermission("shinobitail.admin")) subs.add("reload");
            return prefix(subs, args[0]);
        }
        if (!gm) return List.of();
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "bind", "unbind", "reset", "info", "set", "add", "mastery", "gui" ->
                        prefix(characterNames(), args[1]);
                case "transform", "confront", "speak", "pause", "stop" ->
                        prefix(liveTargets(), args[1]);
                case "setinnerworld" -> prefix(beastIds(), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (sub) {
                case "bind" -> prefix(beastIds(), args[2]);
                case "reset" -> prefix(List.of("confirm"), args[2]);
                case "set", "add" -> prefix(RELATION_KEYS, args[2]);
                case "transform" -> prefix(List.of("1", "next", "stop"), args[2]);
                case "mastery" -> prefix(List.of("1", "2", "3"), args[2]);
                default -> List.of();
            };
        }
        if (args.length == 4 && ("set".equals(sub) || "mastery".equals(sub))) {
            return prefix(List.of("0", "25", "50", "75", "100"), args[3]);
        }
        return List.of();
    }

    private List<String> characterNames() {
        var characters = plugin.characters();
        if (characters == null) return List.of();
        java.util.TreeSet<String> names =
                new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (var roster : characters.rosterView().values()) {
            for (var c : roster) names.add(c.name());
        }
        return new ArrayList<>(names);
    }

    /** Tab targets for live commands: ACTIVE jinchūriki character names
     *  first-class, online usernames as fallback. */
    private List<String> liveTargets() {
        java.util.TreeSet<String> out =
                new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        var characters = plugin.characters();
        for (Player p : Bukkit.getOnlinePlayers()) {
            out.add(p.getName());
            if (characters == null) continue;
            ShinobiCharacter active = characters.getActive(p.getUniqueId());
            if (active != null
                    && plugin.jinchuriki().of(active.id()).beastId() != null) {
                out.add(active.name());
            }
        }
        return new ArrayList<>(out);
    }

    private List<String> onlineNames() {
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        return out;
    }

    private List<String> beastIds() {
        return new ArrayList<>(plugin.beasts().all().keySet());
    }

    private static List<String> prefix(List<String> options, String arg) {
        String low = arg == null ? "" : arg.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(low)) out.add(o);
        }
        return out;
    }
}
