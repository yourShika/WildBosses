package com.yourshika.wildbosses.skill.condition;

import com.yourshika.wildbosses.skill.SkillContext;
import com.yourshika.wildbosses.util.Params;

/** A boolean guard evaluated before a mechanic runs. */
@FunctionalInterface
public interface Condition {

    boolean test(SkillContext ctx, Params params);
}
