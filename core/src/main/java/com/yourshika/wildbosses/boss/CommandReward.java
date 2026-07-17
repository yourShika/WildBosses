package com.yourshika.wildbosses.boss;

/**
 * A console command run as a chance-based reward on boss death (e.g. granting a pet from another
 * plugin). {@code %player%} is replaced with the receiving player and {@code %boss%} with the boss id.
 *
 * @param command  console command template
 * @param chance   roll chance 0.0-1.0
 * @param announce optional MiniMessage line broadcast when it drops ({@code <player>} available); null = silent
 */
public record CommandReward(String command, double chance, String announce) {
}
