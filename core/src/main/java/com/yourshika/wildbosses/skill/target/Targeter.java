package com.yourshika.wildbosses.skill.target;

import com.yourshika.wildbosses.skill.SkillContext;
import com.yourshika.wildbosses.skill.Target;
import com.yourshika.wildbosses.util.Params;

import java.util.List;

/** Resolves the targets a mechanic acts on. */
@FunctionalInterface
public interface Targeter {

    List<Target> resolve(SkillContext ctx, Params params);
}
