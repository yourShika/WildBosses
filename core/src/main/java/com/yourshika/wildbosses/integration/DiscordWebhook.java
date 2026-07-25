package com.yourshika.wildbosses.integration;

import com.yourshika.wildbosses.WildBossesPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Fire-and-forget Discord webhook poster (no external dependency; uses the JDK HTTP client, off the
 * main thread). Does nothing unless a webhook URL is configured.
 */
public final class DiscordWebhook {

    // One shared client for the whole plugin. Allocating a new HttpClient per call leaks its
    // selector + executor threads until GC, which piles up on servers that fire many boss events.
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private DiscordWebhook() {
    }

    public static void send(WildBossesPlugin plugin, String content) {
        post(plugin, "{\"content\":\"" + escape(content) + "\"}");
    }

    /** Post a single rich embed (coloured by difficulty/rarity) instead of a flat content line. */
    public static void sendEmbed(WildBossesPlugin plugin, String title, String description, int color) {
        post(plugin, "{\"embeds\":[{\"title\":\"" + escape(title) + "\",\"description\":\""
                + escape(description) + "\",\"color\":" + color + "}]}");
    }

    private static void post(WildBossesPlugin plugin, String json) {
        String url = plugin.config().discordWebhook();
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(t -> {
                        plugin.getLogger().warning("Discord webhook failed: " + t.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            plugin.getLogger().warning("Discord webhook error: " + e.getMessage());
        }
    }

    /**
     * JSON-escape a string per RFC 8259: backslash and quote, the named escapes, and EVERY other
     * control character (U+0000..U+001F, e.g. a stray TAB) as {@code \\uXXXX}. Leaving a raw control
     * char in the body produced invalid JSON, which Discord rejected with HTTP 400 - silently killing
     * every webhook. Not an injection vector (backslash/quote are handled), just correctness.
     */
    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
