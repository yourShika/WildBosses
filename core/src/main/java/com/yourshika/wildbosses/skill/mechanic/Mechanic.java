package com.yourshika.wildbosses.skill.mechanic;

import com.yourshika.wildbosses.skill.SkillContext;
import com.yourshika.wildbosses.skill.Target;
import com.yourshika.wildbosses.util.Params;

import java.util.List;

/** An effect a boss skill performs on its resolved targets. */
@FunctionalInterface
public interface Mechanic {

    void run(SkillContext ctx, List<Target> targets, Params params);
}
