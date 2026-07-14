package com.yourshika.wildbosses.integration;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.util.Text;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Self-updater: fetches the latest GitHub release and, if newer than the running version, downloads
 * its jar into the server's plugin update folder (Bukkit swaps it in on the next restart).
 *
 * <p>Runs fully off the main thread; results are reported back on the main thread. JSON is parsed
 * with simple regexes to avoid any extra dependency.</p>
 */
public final class Updater {

    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern JAR_URL =
            Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.jar)\"");

    private Updater() {
    }

    public static void run(WildBossesPlugin plugin, CommandSender sender) {
        String repo = plugin.config().updateRepo();
        if (repo == null || repo.isBlank() || !repo.contains("/")) {
            reply(plugin, sender, "<red>No valid update-repo configured (integrations.update-repo).");
            return;
        }
        reply(plugin, sender, "<gray>Checking GitHub for the latest release...");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> doUpdate(plugin, sender, repo));
    }

    private static void doUpdate(WildBossesPlugin plugin, CommandSender sender, String repo) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
            HttpResponse<String> res = client.send(HttpRequest.newBuilder(
                            URI.create("https://api.github.com/repos/" + repo + "/releases/latest"))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "WildBosses-Updater")
                    .GET().build(), HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() != 200) {
                reply(plugin, sender, "<red>Update check failed (HTTP " + res.statusCode()
                        + "). Repo: <yellow>" + repo);
                return;
            }

            String body = res.body();
            Matcher tagMatcher = TAG.matcher(body);
            if (!tagMatcher.find()) {
                reply(plugin, sender, "<red>Could not read the latest version from GitHub.");
                return;
            }
            String latest = tagMatcher.group(1).replaceFirst("^[vV]", "");
            String current = plugin.getPluginMeta().getVersion();
            if (latest.equals(current)) {
                reply(plugin, sender, "<green>WildBosses is already up to date (v" + current + ").");
                return;
            }

            Matcher urlMatcher = JAR_URL.matcher(body);
            if (!urlMatcher.find()) {
                reply(plugin, sender, "<red>Latest release v" + latest + " has no downloadable jar.");
                return;
            }
            String jarUrl = urlMatcher.group(1);

            HttpResponse<InputStream> download = client.send(HttpRequest.newBuilder(URI.create(jarUrl))
                    .header("User-Agent", "WildBosses-Updater").GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (download.statusCode() != 200) {
                reply(plugin, sender, "<red>Download failed (HTTP " + download.statusCode() + ").");
                return;
            }

            File updateDir = plugin.getServer().getUpdateFolderFile();
            if (!updateDir.exists() && !updateDir.mkdirs()) {
                reply(plugin, sender, "<red>Could not create the update folder.");
                return;
            }
            File target = new File(updateDir, plugin.pluginFile().getName());
            try (InputStream in = download.body()) {
                Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            plugin.getLogger().info("Downloaded WildBosses v" + latest + " to the update folder.");
            reply(plugin, sender, "<green>Downloaded WildBosses <yellow>v" + latest
                    + "<green> (you have v" + current + "). <gold>Restart the server to apply.");
        } catch (Exception e) {
            plugin.getLogger().warning("Self-update failed: " + e);
            reply(plugin, sender, "<red>Update failed: <gray>" + e.getMessage());
        }
    }

    private static void reply(WildBossesPlugin plugin, CommandSender sender, String mini) {
        plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(Text.mm(mini)));
    }
}
