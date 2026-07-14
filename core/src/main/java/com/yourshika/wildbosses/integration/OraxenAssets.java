package com.yourshika.wildbosses.integration;

import com.yourshika.wildbosses.WildBossesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Deploys WildBosses' custom mob assets into Oraxen's resource pack so a single Oraxen pack ships
 * every boss texture/model. Drop files into the plugin's {@code textures/} and {@code models/}
 * folders named after the boss id:
 *
 * <ul>
 *   <li>{@code textures/<bossId>.png} → an Oraxen item {@code wildbosses_<bossId>} (generated model)</li>
 *   <li>{@code models/<bossId>.json} → a custom model referenced by that item</li>
 * </ul>
 *
 * <p>Assets are copied into {@code Oraxen/pack/textures/wildbosses/}, {@code Oraxen/pack/models/wildbosses/}
 * and an {@code Oraxen/items/wildbosses.yml} is (re)generated. Existing pack files are backed up before
 * being overwritten. Run {@code /oraxen reload} afterwards (or {@code /wb assets redeploy}) to rebuild
 * the pack. Mirrors the workflow of yourShika-Backpacks.</p>
 */
public final class OraxenAssets {

    private final WildBossesPlugin plugin;
    private final File texturesDir;
    private final File modelsDir;
    private Path backupRoot;

    public OraxenAssets(WildBossesPlugin plugin) {
        this.plugin = plugin;
        this.texturesDir = new File(plugin.getDataFolder(), "textures");
        this.modelsDir = new File(plugin.getDataFolder(), "models");
        ensureFolders();
    }

    private void ensureFolders() {
        if (texturesDir.mkdirs() || modelsDir.mkdirs()) {
            writeReadme();
        } else if (!new File(texturesDir, "README.txt").exists()) {
            writeReadme();
        }
    }

    private void writeReadme() {
        try {
            Files.writeString(new File(texturesDir, "README.txt").toPath(),
                    "Drop a PNG named after a boss id here, e.g. warthoglin.png\n"
                            + "Run /wb assets redeploy and then /oraxen reload to build the pack.\n");
            Files.writeString(new File(modelsDir, "README.txt").toPath(),
                    "Drop a Minecraft item-model .json named after a boss id here, e.g. warthoglin.json\n"
                            + "Run /wb assets redeploy and then /oraxen reload to build the pack.\n");
        } catch (Exception ignored) {
        }
    }

    public boolean oraxenPresent() {
        return Bukkit.getPluginManager().getPlugin("Oraxen") != null;
    }

    /** Read-only summary for {@code /wb assets status}. */
    public record Status(boolean oraxen, int textures, int models) {
    }

    public Status status() {
        return new Status(oraxenPresent(), count(texturesDir, ".png"), count(modelsDir, ".json"));
    }

    /** Result of a deploy for the command to report. */
    public record DeployResult(boolean oraxen, int textures, int models, int backups, String error) {
    }

    public DeployResult deploy() {
        Plugin oraxen = Bukkit.getPluginManager().getPlugin("Oraxen");
        if (oraxen == null) {
            return new DeployResult(false, 0, 0, 0, "Oraxen is not installed");
        }
        File oxData = oraxen.getDataFolder();
        File oxTex = new File(oxData, "pack/textures/wildbosses");
        File oxModels = new File(oxData, "pack/models/wildbosses");
        File itemsFile = new File(oxData, "items/wildbosses.yml");
        backupRoot = null;
        int texCount = 0;
        int modelCount = 0;
        int backups = 0;

        YamlConfiguration items = new YamlConfiguration();
        try {
            File[] textures = texturesDir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".png"));
            if (textures != null) {
                for (File png : textures) {
                    String base = stripExt(png.getName());
                    if (copyWithBackup(png.toPath(), new File(oxTex, base + ".png").toPath())) {
                        backups++;
                    }
                    String id = "wildbosses_" + base;
                    items.set(id + ".displayname", "<white>" + base);
                    items.set(id + ".material", "PAPER");
                    items.set(id + ".Pack.generate_model", true);
                    items.set(id + ".Pack.parent_model", "item/generated");
                    items.set(id + ".Pack.textures", List.of("wildbosses/" + base));
                    texCount++;
                }
            }
            File[] models = modelsDir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".json"));
            if (models != null) {
                for (File json : models) {
                    String base = stripExt(json.getName());
                    if (copyWithBackup(json.toPath(), new File(oxModels, base + ".json").toPath())) {
                        backups++;
                    }
                    String id = "wildbosses_" + base;
                    items.set(id + ".displayname", "<white>" + base);
                    items.set(id + ".material", "PAPER");
                    items.set(id + ".Pack.model", "wildbosses/" + base);
                    modelCount++;
                }
            }
            Files.createDirectories(itemsFile.getParentFile().toPath());
            items.save(itemsFile);
        } catch (Exception ex) {
            plugin.getLogger().warning("Oraxen asset deploy failed: " + ex.getMessage());
            return new DeployResult(true, texCount, modelCount, backups, ex.getMessage());
        }

        plugin.getLogger().info("Deployed " + texCount + " texture(s) and " + modelCount
                + " model(s) to Oraxen. Run /oraxen reload to rebuild the pack.");
        return new DeployResult(true, texCount, modelCount, backups, null);
    }

    // ---- helpers --------------------------------------------------------------------------

    private boolean copyWithBackup(Path source, Path target) throws Exception {
        boolean backedUp = false;
        if (Files.exists(target)) {
            if (sha256(Files.readAllBytes(target)).equals(sha256(Files.readAllBytes(source)))) {
                return false; // already identical
            }
            backup(target);
            backedUp = true;
        }
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return backedUp;
    }

    private void backup(Path target) throws Exception {
        if (backupRoot == null) {
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            backupRoot = plugin.getDataFolder().toPath().resolve("AssetBackups").resolve(stamp);
        }
        Path backup = backupRoot.resolve(target.getFileName().toString());
        Files.createDirectories(backup.getParent());
        Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
    }

    private static int count(File dir, String ext) {
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(ext));
        return files == null ? 0 : files.length;
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        StringBuilder out = new StringBuilder();
        for (byte b : digest.digest(bytes)) {
            out.append(String.format("%02x", b));
        }
        return out.toString();
    }
}
