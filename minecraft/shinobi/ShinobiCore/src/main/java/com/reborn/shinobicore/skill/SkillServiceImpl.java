package com.reborn.shinobicore.skill;

import com.reborn.shinobicore.api.Internal;
import com.reborn.shinobicore.api.SkillService;
import com.reborn.shinobicore.character.ShinobiCharacter;

/**
 * {@link SkillService} over the static roll/skill helpers. Exists so
 * dependents resolve the skill system by interface; the statics stay
 * for engine-internal use.
 */
@Internal
public final class SkillServiceImpl implements SkillService {

    @Override
    public int effectiveSkill(ShinobiCharacter character, Skill skill) {
        return SkillCommands.effectiveSkill(character, skill);
    }
}
