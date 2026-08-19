package com.reborn.shinobitail.util;

import com.reborn.shinobitail.beast.BeastDefinition;
import com.reborn.shinobitail.data.JinchurikiData;
import org.bukkit.configuration.ConfigurationSection;

import java.util.concurrent.ThreadLocalRandom;

/**
 * All probability formulas in one place, driven by the weight blocks in
 * config.yml so staff can retune the whole psychology without touching
 * code. Every method returns a chance in percent (already clamped).
 */
public final class Chances {

    private Chances() { }

    /**
     * "The beast WANTS to help" — rolled when HP drops under a threshold.
     *
     * @param missingHpPercent how far below 100% HP the host is (0–100)
     */
    public static double helpChance(ConfigurationSection cfg,
                                    BeastDefinition beast,
                                    JinchurikiData data,
                                    double missingHpPercent,
                                    double bonus) {
        if (cfg == null) return 0;
        double c = cfg.getDouble("base-chance", 5)
                + data.trust() * cfg.getDouble("trust-weight", 0.35)
                + data.cooperation() * cfg.getDouble("cooperation-weight", 0.25)
                - data.anger() * cfg.getDouble("anger-penalty", 0.20)
                + missingHpPercent
                    * cfg.getDouble("low-hp-bonus-per-missing-percent", 0.4)
                + beast.personality().helpBias()
                + bonus;
        return clamp(c / beast.entryDifficultyDivisor(), 0, 100);
    }

    /**
     * "The beast FORCES the gate" — rolled alongside the help roll;
     * a hostile beast doesn't wait to be invited.
     */
    public static double takeoverChance(ConfigurationSection cfg,
                                        BeastDefinition beast,
                                        JinchurikiData data) {
        if (cfg == null) return 0;
        double c = cfg.getDouble("base-chance", 2)
                + data.anger() * cfg.getDouble("anger-weight", 0.40)
                + data.influence() * cfg.getDouble("influence-weight", 0.30)
                - data.trust() * cfg.getDouble("trust-penalty", 0.25)
                - data.mastery(1) * cfg.getDouble("mastery-penalty", 0.15)
                + beast.personality().takeoverBias();
        return clamp(c / beast.entryDifficultyDivisor(), 0, 100);
    }

    /**
     * Inner World RESIST roll — chance to end the transformation instead
     * of sliding into the next stage. Mastery of the CURRENT stage is the
     * main lever; rage and influence drag the host down.
     */
    public static double resistChance(ConfigurationSection cfg,
                                      BeastDefinition beast,
                                      JinchurikiData data,
                                      int currentStage) {
        if (cfg == null) return 50;
        double c = cfg.getDouble("base-chance", 10)
                + data.mastery(currentStage) * cfg.getDouble("mastery-weight", 0.50)
                + data.trust() * cfg.getDouble("trust-weight", 0.15)
                + data.cooperation() * cfg.getDouble("cooperation-weight", 0.15)
                - data.influence() * cfg.getDouble("influence-penalty", 0.20)
                - data.rage() * cfg.getDouble("rage-penalty", 0.25)
                + beast.personality().resistBias();
        return clamp(c,
                cfg.getDouble("min-chance", 5),
                cfg.getDouble("max-chance", 95));
    }

    public static boolean roll(double percent) {
        return ThreadLocalRandom.current().nextDouble(100.0) < percent;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
