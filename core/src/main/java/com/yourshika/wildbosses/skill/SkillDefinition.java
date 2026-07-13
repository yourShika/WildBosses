package com.yourshika.wildbosses.skill;

import com.yourshika.wildbosses.util.Params;

import java.util.List;

/**
 * A parsed boss skill: {@code trigger → mechanic @ targeter ?conditions}, MythicMobs-style.
 *
 * @param trigger       when it fires
 * @param triggerParams trigger parameters (e.g. {@code interval}, {@code value}, {@code radius})
 * @param mechanic      mechanic keyword (e.g. {@code summon}, {@code aoe_damage})
 * @param targeter      targeter keyword (e.g. {@code players_in_radius}, {@code self})
 * @param params        mechanic parameters
 * @param conditions    guards that must all pass for the mechanic to run
 * @param cooldownTicks minimum ticks between activations (0 = none)
 */
public record SkillDefinition(
        TriggerType trigger,
        Params triggerParams,
        String mechanic,
        String targeter,
        Params params,
        List<ConditionDefinition> conditions,
        int cooldownTicks
) {
}
