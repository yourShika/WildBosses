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

    private DiscordWebhook() {
    }

    public static void send(WildBossesPlugin plugin, String content) {
        String url = plugin.config().discordWebhook();
        if (url == null || url.isBlank()) {
            return;
        }
        String json = "{\"content\":\"" + escape(content) + "\"}";
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(t -> {
                        plugin.getLogger().warning("Discord webhook failed: " + t.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            plugin.getLogger().warning("Discord webhook error: " + e.getMessage());
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
