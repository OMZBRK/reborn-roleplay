package com.reborn.shinobilearning.academy;

import com.reborn.shinobicore.character.Rank;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobilearning.ShinobiLearning;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;

/**
 * Academy orchestration over the ShinobiCore character API: enrolment, lesson
 * validation, and graduation (rank-up + skill-point grant + learning the
 * Academy Three + a broadcast). All character writes go through Core's
 * {@code characters().save(...)}; Academy progress lives in {@link AcademyStore}.
 */
public final class AcademyManager {

    private final ShinobiLearning plugin;

    public AcademyManager(ShinobiLearning plugin) { this.plugin = plugin; }

    /** Enrol a character: mark them an Academy student and set the rank. */
    public void enroll(ShinobiCharacter c) {
        AcademyData d = plugin.academy().of(c.id());
        d.setEnrolled(true);
        c.setRank(Rank.ACADEMY);
        plugin.characters().save(c);
        plugin.academy().save(d);
    }

    /** Mark a lesson complete. Returns true if it wasn't already done. */
    public boolean completeLesson(ShinobiCharacter c, Lesson lesson) {
        AcademyData d = plugin.academy().of(c.id());
        boolean added = d.complete(lesson);
        plugin.academy().save(d);
        return added;
    }

    /** True once every lesson in the curriculum is complete. */
    public boolean canGraduate(ShinobiCharacter c) {
        AcademyData d = plugin.academy().of(c.id());
        for (Lesson l : Lesson.values()) {
            if (!d.hasCompleted(l)) return false;
        }
        return true;
    }

    /**
     * Graduate to Genin: rank up, grant skill points (the bridge to the
     * Skills &amp; Rolls system), learn the Academy Three, and announce it.
     */
    public void graduate(ShinobiCharacter c) {
        AcademyData d = plugin.academy().of(c.id());
        d.setGraduated(true);
        c.setRank(Rank.GENIN);

        int pts = plugin.getConfig().getInt("academy.graduation-skill-points", 5);
        if (pts > 0) c.addSkillPoints(pts);

        // The Academy Three — recorded as known abilities (the ids resolve once
        // ShinobiAbilities ships them; storing strings is harmless meanwhile).
        c.learnAbility("henge");
        c.learnAbility("kawarimi");
        c.learnAbility("bunshin");

        plugin.characters().save(c);
        plugin.academy().save(d);

        String raw = plugin.getConfig().getString("academy.graduation-broadcast",
                "{character} est diplômé de l'Académie — Genin !");
        Bukkit.broadcast(Component.text(raw.replace("{character}", c.name()),
                NamedTextColor.GOLD));
    }
}
