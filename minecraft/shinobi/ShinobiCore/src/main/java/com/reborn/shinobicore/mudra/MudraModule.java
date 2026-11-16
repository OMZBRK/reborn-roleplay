package com.reborn.shinobicore.mudra;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.technique.Mudra;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;

/**
 * Mudras au clavier et portes scellées (demande du 14/11). Rassemble le canal joueur {@link MudraService}
 * ({@code reborn:mudra}), les portes ({@link PorteService}, {@code mudras.yml}) et {@code /porte}.
 *
 * <p>Le système est <b>livré éteint</b> : l'interrupteur n'est pas dans {@code config.yml}, c'est la clé {@code actif} de
 * {@code plugins/ShinobiCore/mudras.yml} (absente = false), basculée en jeu par {@code /porte systeme on|off}. Éteint, la
 * barre ne s'ouvre pour personne, les portes restent fermées et {@link #guider} rend faux (l'ancien mini-jeu
 * d'apprentissage prend le relais).
 *
 * <p>API pour les autres plugins ({@code plugin.mudras()}) : {@link #guider}, {@link #annuler}, {@link #enCours}.
 */
public final class MudraModule {

    private final ShinobiCore plugin;
    private final PorteService portes;
    private final MudraService mudras;

    public MudraModule(ShinobiCore plugin) {
        this.plugin = plugin;
        this.portes = new PorteService(plugin);
        this.mudras = new MudraService(plugin, portes);
        this.portes.surBascule(mudras::systemeChange);
    }

    public void demarrer() {
        MudraRegles.Reglages r = lireReglages();
        portes.demarrer(r);
        mudras.demarrer(r);
        PluginCommand pc = plugin.getCommand("porte");
        if (pc == null) plugin.getLogger().warning("Commande 'porte' absente du plugin.yml.");
        else {
            PorteCommand c = new PorteCommand(portes);
            pc.setExecutor(c);
            pc.setTabCompleter(c);
        }
    }

    /** Ordre inverse : les sessions finissent d'abord ({@code coupe}), puis les dalles levées sont remises. */
    public void arreter() {
        mudras.arreter();
        portes.arreter();
    }

    /** {@code /sc reload} : section {@code mudras} du config.yml et {@code mudras.yml} relus, règles renvoyées à tous. */
    public void recharger() {
        MudraRegles.Reglages r = lireReglages();
        portes.recharger(r);
        mudras.recharger(r);
    }

    /** Lit {@code mudras} (config.yml) ; une section ou une clé absente prend le défaut du code. */
    private MudraRegles.Reglages lireReglages() {
        MudraRegles.Reglages d = MudraRegles.Reglages.DEFAUT;
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("mudras");
        if (s == null) return d;
        return new MudraRegles.Reglages(s.getLong("temps-ms", d.tempsMs()), s.getLong("gain-ms", d.gainMs()),
            s.getInt("max-signes", d.maxSignes()), s.getLong("signe-min-ms", d.signeMinMs()),
            s.getLong("recharge-ms", d.rechargeMs()), s.getDouble("geste-rayon", d.gesteRayon()),
            s.getLong("guide-marge-ms", d.guideMargeMs()), s.getDouble("portes.portee", d.portee()),
            s.getInt("portes.duree-ticks", d.dureeTicks()), s.getInt("portes.ouverte-s", d.ouverteS()),
            s.getInt("portes.essais-max", d.essaisMax()), s.getInt("portes.essais-fenetre-s", d.essaisFenetreS()),
            s.getInt("portes.blocage-s", d.blocageS()));
    }

    public MudraRegles.Reglages reglages() { return mudras.reglages(); }

    public PorteService portes() { return portes; }

    public MudraService service() { return mudras; }

    /** L'interrupteur du système ({@code /porte systeme on|off}). */
    public boolean actif() { return portes.actif(); }

    /* ================================================================ API pour les autres plugins */

    /**
     * Ouvre une barre guidée ; false si le système est éteint, si le joueur n'écoute pas reborn:mudra, n'a pas le droit,
     * ou a déjà une session. fin.accept(true) = suite complète ; false = mauvais signe, temps écoulé ou coupure. Toujours
     * appelé une seule fois, sur le fil principal.
     */
    public boolean guider(Player p, List<Mudra> suite, String titre, long tempsParSigneMs, Consumer<Boolean> fin) {
        return mudras.guider(p, suite, titre, tempsParSigneMs, fin == null ? null : res -> fin.accept(res == MudraService.Fin.OK));
    }

    /**
     * Comme {@link #guider}, mais le rappel reçoit la raison de la fin ({@code OK}, {@code RATE}, {@code TEMPS},
     * {@code COUPE}, {@code FERME}) : l'appelant garde ses propres messages d'échec (le mini-jeu d'apprentissage).
     */
    public boolean guiderAvecRaison(Player p, List<Mudra> suite, String titre, long tempsParSigneMs, Consumer<MudraService.Fin> fin) {
        return mudras.guider(p, suite, titre, tempsParSigneMs, fin);
    }

    /** Finit sans appeler {@code fin} la session guidée du joueur (abandon par l'appelant). */
    public void annuler(Player p) { mudras.annuler(p); }

    public boolean enCours(Player p) { return mudras.enCours(p); }
}
