package com.yourshika.wildbosses.skill;

import com.yourshika.wildbosses.util.Params;

/** A parsed condition guard on a skill: a type keyword plus its parameters. */
public record ConditionDefinition(String type, Params params) {
}
