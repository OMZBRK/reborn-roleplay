package com.reborn.shinobicore.api;

import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.skill.Skill;

/**
 * Read access to the roll/skill system. Kept minimal: dependents
 * currently only need the effective (injury-adjusted) skill value;
 * the three-band roll resolution stays engine-internal until a
 * dependent needs it.
 */
@Stable
public interface SkillService {

    /** The character's effective value for {@code skill}, injuries applied. */
    int effectiveSkill(ShinobiCharacter character, Skill skill);
}
