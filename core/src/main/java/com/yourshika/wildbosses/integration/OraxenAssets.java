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
 * Bridges WildBosses' custom-mob assets into a single Oraxen resource pack.
 *
 * <p>Two folders in the plugin data folder (filename = boss id):</p>
 * <ul>
 *   <li>{@code models/<bossId>.bbmodel} → copied into BetterModel's {@code models/} folder. BetterModel
 *       loads it and builds its pack ({@code plugins/BetterModel/pack/BetterModel-Pack.zip}); that zip
 *       is then handed to Oraxen ({@code Oraxen/pack/uploads/}) which merges it into its pack.</li>
 *   <li>{@code textures/<bossId>.png} → a flat custom texture with no BetterModel: copied into
 *       {@code Oraxen/pack/textures/wildbosses/} and registered as an Oraxen item
 *       {@code wildbosses_<bossId>} (used by {@code OraxenItemAdapter}).</li>
 * </ul>
 *
 * <p>After a deploy, run {@code /bettermodel reload} (rebuild the model pack) and {@code /oraxen reload}
 * (rebuild the merged pack). Mirrors the workflow of yourShika-Backpacks.</p>
 */
public final class OraxenAssets {

    private static final String BETTERMODEL_PACK = "pack/BetterModel-Pack.zip";

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
        texturesDir.mkdirs();
        modelsDir.mkdirs();
        writeReadmeIfMissing(new File(modelsDir, "README.txt"),
                "Drop a BlockBench .bbmodel named after a boss id here, e.g. warthoglin.bbmodel\n"
                        + "It is installed into BetterModel; run /wb assets redeploy, then /bettermodel reload\n"
                        + "and /oraxen reload to build the merged texture pack.\n");
        writeReadmeIfMissing(new File(texturesDir, "README.txt"),
                "Drop a flat PNG named after a boss id here, e.g. medusa.png (used WITHOUT BetterModel).\n"
                        + "Run /wb assets redeploy, then /oraxen reload to build the pack.\n");
    }

    private void writeReadmeIfMissing(File file, String content) {
        if (!file.exists()) {
            try {
                Files.writeString(file.toPath(), content);
            } catch (Exception ignored) {
            }
        }
    }

    public boolean oraxenPresent() {
        return Bukkit.getPluginManager().getPlugin("Oraxen") != null;
    }

    public boolean betterModelPresent() {
        return Bukkit.getPluginManager().getPlugin("BetterModel") != null;
    }

    /** Read-only summary for {@code /wb assets status}. */
    public record Status(boolean oraxen, boolean betterModel, int textures, int bbmodels, boolean packBuilt) {
    }

    public Status status() {
        Plugin bm = Bukkit.getPluginManager().getPlugin("BetterModel");
        boolean packBuilt = bm != null && new File(bm.getDataFolder(), BETTERMODEL_PACK).isFile();
        return new Status(oraxenPresent(), bm != null,
                count(texturesDir, ".png"), count(modelsDir, ".bbmodel"), packBuilt);
    }

    /** Result of a deploy for the command to report. */
    public record DeployResult(boolean oraxen, boolean betterModel, int textures, int bbmodels,
                               boolean packMerged, int backups, String error) {
    }

    public DeployResult deploy() {
        Plugin oraxen = Bukkit.getPluginManager().getPlugin("Oraxen");
        Plugin betterModel = Bukkit.getPluginManager().getPlugin("BetterModel");
        backupRoot = null;
        int bbmodels = 0;
        int textures = 0;
        int backups = 0;
        boolean packMerged = false;

        try {
            // 1. Install .bbmodel files into BetterModel.
            if (betterModel != null) {
                File bmModels = new File(betterModel.getDataFolder(), "models");
                File[] files = modelsDir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".bbmodel"));
                if (files != null) {
                    for (File model : files) {
                        if (copyWithBackup(model.toPath(), new File(bmModels, model.getName()).toPath())) {
                            backups++;
                        }
                        bbmodels++;
                    }
                }
            }

            // 2. Hand BetterModel's built pack to Oraxen for merging (pack/uploads).
            if (betterModel != null && oraxen != null) {
                File packZip = new File(betterModel.getDataFolder(), BETTERMODEL_PACK);
                if (packZip.isFile()) {
                    File uploads = new File(oraxen.getDataFolder(), "pack/uploads/bettermodel.zip");
                    Files.createDirectories(uploads.getParentFile().toPath());
                    Files.copy(packZip.toPath(), uploads.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    packMerged = true;
                }
            }

            // 3. Deploy flat textures as Oraxen items (custom texture without BetterModel).
            if (oraxen != null) {
                int[] res = deployTextures(oraxen);
                textures = res[0];
                backups += res[1];
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Oraxen asset deploy failed: " + ex.getMessage());
            return new DeployResult(oraxen != null, betterModel != null, textures, bbmodels, packMerged, backups, ex.getMessage());
        }

        plugin.getLogger().info("Assets deployed: " + bbmodels + " model(s) to BetterModel, "
                + textures + " texture(s) to Oraxen, pack merged: " + packMerged
                + ". Run /bettermodel reload and /oraxen reload.");
        return new DeployResult(oraxen != null, betterModel != null, textures, bbmodels, packMerged, backups, null);
    }

    /** @return {@code [textureCount, backupCount]} */
    private int[] deployTextures(Plugin oraxen) throws Exception {
        File oxData = oraxen.getDataFolder();
        File oxTex = new File(oxData, "pack/textures/wildbosses");
        File itemsFile = new File(oxData, "items/wildbosses.yml");
        int count = 0;
        int backups = 0;
        YamlConfiguration items = new YamlConfiguration();
        File[] pngs = texturesDir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".png"));
        if (pngs != null) {
            for (File png : pngs) {
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
                count++;
            }
        }
        Files.createDirectories(itemsFile.getParentFile().toPath());
        items.save(itemsFile);
        return new int[]{count, backups};
    }

    // ---- helpers --------------------------------------------------------------------------

    private boolean copyWithBackup(Path source, Path target) throws Exception {
        boolean backedUp = false;
        if (Files.exists(target)) {
            if (sha256(Files.readAllBytes(target)).equals(sha256(Files.readAllBytes(source)))) {
                return false;
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
