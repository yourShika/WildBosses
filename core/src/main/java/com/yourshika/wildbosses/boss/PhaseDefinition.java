package com.yourshika.wildbosses.boss;

/**
 * A boss phase, entered once when health first drops to/below {@link #atHealthPercent()}.
 *
 * @param atHealthPercent health threshold (0-100) at which this phase begins
 * @param enrage          whether to apply a speed/damage enrage on entering
 * @param message         optional MiniMessage broadcast to nearby players (nullable)
 * @param animation       optional model animation to play on entering (nullable)
 */
public record PhaseDefinition(double atHealthPercent, boolean enrage, String message, String animation) {
}
