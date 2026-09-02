package com.reborn.shinobicombat.command;

import com.reborn.shinobicombat.combat.StaminaManager;
import com.reborn.shinobicombat.net.CombatChannel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * {@code /scendurance [player] <amount>} — débite l'ENDURANCE (barre de combat,
 * {@link StaminaManager}) d'un joueur et pousse la valeur à son HUD. Pont pour les
 * techniques MagicSpells : le M1 kenjutsu (sort {@code MS_KENJTSU_M1}, item Nexo
 * {@code epee_kenjutsu} = paper) appelle cette commande via un {@code ExternalCommandSpell}
 * — exactement comme il appelle déjà {@code /playemote} — pour que chaque coup
 * <b>coûte de l'endurance au lanceur</b> (le kenjutsu ne passe PAS par le moteur
 * mêlée vanilla de ShinobiCombat puisque l'arme est un {@code paper}).
 *
 * <p>Sans nom de joueur (1 argument) la cible = l'émetteur (le lanceur du sort,
 * contexte joueur avec op temporaire du sort). Avec 2 arguments, cible explicite.
 * Draine borné à 0 (ne bloque pas le sort déjà lancé) et ne renvoie AUCUN message
 * (appelé à chaque swing → éviterait de spammer le chat). Perm {@code shinobicombat.endurance}
 * (op par défaut ; l'op temporaire du sort la satisfait).
 */
public final class EnduranceCommand implements CommandExecutor {

    private final Plugin plugin;
    private final StaminaManager stamina;

    public EnduranceCommand(Plugin plugin, StaminaManager stamina) {
        this.plugin = plugin;
        this.stamina = stamina;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player target;
        String amountArg;
        if (args.length == 1) {
            if (!(sender instanceof Player self)) {
                sender.sendMessage(Component.text(
                        "Console : /scendurance <joueur> <montant>", NamedTextColor.RED));
                return true;
            }
            target = self;
            amountArg = args[0];
        } else if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[0]);
            amountArg = args[1];
            if (target == null) {
                sender.sendMessage(Component.text(
                        "Joueur introuvable ou hors ligne : " + args[0], NamedTextColor.RED));
                return true;
            }
        } else {
            sender.sendMessage(Component.text("Usage : /scendurance [joueur] <montant>", NamedTextColor.YELLOW));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountArg);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Montant invalide : " + amountArg, NamedTextColor.RED));
            return true;
        }
        if (amount <= 0) return true; // rien à débiter

        double remaining = stamina.drain(target.getUniqueId(), amount);
        CombatChannel.sendStamina(plugin, target, remaining, stamina.max());
        return true;
    }
}
