package com.yourshika.wildbosses.integration;

import com.yourshika.wildbosses.WildBossesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Locale;

/**
 * Bridges WildBosses' BlockBench models and BetterModel's generated pack into a single Oraxen pack.
 *
 * <p>Drop {@code models/<bossId>.bbmodel} into the plugin data folder (filename = boss id). On deploy:</p>
 * <ol>
 *   <li>each {@code .bbmodel} is installed into {@code plugins/BetterModel/models/} (BetterModel loads
 *       it and renders the boss whose id matches the model name);</li>
 *   <li>BetterModel's built pack ({@code plugins/BetterModel/pack/BetterModel-Pack.zip}) is copied into
 *       {@code Oraxen/pack/uploads/bettermodel.zip} so Oraxen merges BetterModel's models + textures
 *       into the resource pack it serves.</li>
 * </ol>
 *
 * <p><b>Order matters:</b> the BetterModel pack only exists after {@code /bettermodel reload}. So the
 * flow is: <code>/wb assets redeploy</code> (install models) → <code>/bettermodel reload</code> (build
 * pack) → <code>/wb assets redeploy</code> (copy the built pack to Oraxen) → <code>/oraxen reload</code>.</p>
 */
public final class OraxenAssets {

    private final WildBossesPlugin plugin;
    private final File modelsDir;
    private Path backupRoot;

    public OraxenAssets(WildBossesPlugin plugin) {
        this.plugin = plugin;
        this.modelsDir = new File(plugin.getDataFolder(), "models");
        modelsDir.mkdirs();
        File readme = new File(modelsDir, "README.txt");
        if (!readme.exists()) {
            try {
                Files.writeString(readme.toPath(),
                        "Drop a BlockBench .bbmodel named after a boss id here, e.g. warthoglin.bbmodel\n"
                                + "The model NAME inside BetterModel must match the boss id.\n\n"
                                + "Flow:\n"
                                + "  /wb assets redeploy   (install .bbmodel into BetterModel)\n"
                                + "  /bettermodel reload   (BetterModel builds its pack)\n"
                                + "  /wb assets redeploy   (copy the built pack into Oraxen)\n"
                                + "  /oraxen reload        (rebuild the merged pack)\n");
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
    public record Status(boolean oraxen, boolean betterModel, int bbmodels, boolean packBuilt) {
    }

    public Status status() {
        return new Status(oraxenPresent(), betterModelPresent(),
                count(modelsDir, ".bbmodel"), findBetterModelPack() != null);
    }

    /** Result of a deploy for the command to report. */
    public record DeployResult(boolean oraxen, boolean betterModel, int bbmodels,
                               boolean packMerged, boolean packFound, String error) {
    }

    public DeployResult deploy() {
        Plugin oraxen = Bukkit.getPluginManager().getPlugin("Oraxen");
        Plugin betterModel = Bukkit.getPluginManager().getPlugin("BetterModel");
        backupRoot = null;
        int bbmodels = 0;
        boolean packMerged = false;
        File packZip = findBetterModelPack();

        try {
            // 1. Install .bbmodel files into BetterModel.
            if (betterModel != null) {
                File bmModels = new File(betterModel.getDataFolder(), "models");
                File[] files = modelsDir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".bbmodel"));
                if (files != null) {
                    for (File model : files) {
                        copyWithBackup(model.toPath(), new File(bmModels, model.getName()).toPath());
                        bbmodels++;
                    }
                }
            }

            // 2. Hand BetterModel's built pack to Oraxen (pack/uploads) for merging.
            if (oraxen != null && packZip != null) {
                File uploads = new File(oraxen.getDataFolder(), "pack/uploads/bettermodel.zip");
                Files.createDirectories(uploads.getParentFile().toPath());
                Files.copy(packZip.toPath(), uploads.toPath(), StandardCopyOption.REPLACE_EXISTING);
                packMerged = true;
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Asset deploy failed: " + ex.getMessage());
            return new DeployResult(oraxen != null, betterModel != null, bbmodels, packMerged, packZip != null, ex.getMessage());
        }

        if (packMerged) {
            plugin.getLogger().info("Installed " + bbmodels + " model(s) and merged BetterModel's pack ("
                    + packZip.getName() + ") into Oraxen. Run /oraxen reload.");
        } else if (betterModel != null && packZip == null) {
            plugin.getLogger().info("Installed " + bbmodels + " model(s). BetterModel's pack is not built yet - "
                    + "run /bettermodel reload, then /wb assets redeploy again to merge it into Oraxen.");
        }
        return new DeployResult(oraxen != null, betterModel != null, bbmodels, packMerged, packZip != null, null);
    }

    /** Locate BetterModel's generated pack zip: the configured path, else the newest .zip under BetterModel. */
    private File findBetterModelPack() {
        Plugin bm = Bukkit.getPluginManager().getPlugin("BetterModel");
        if (bm == null) {
            return null;
        }
        String configured = plugin.config().oraxenBetterModelPack();
        File direct = new File(bm.getDataFolder(), configured);
        if (direct.isFile()) {
            return direct;
        }
        try (var walk = Files.walk(bm.getDataFolder().toPath())) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
                    .map(Path::toFile)
                    .max(Comparator.comparingLong(File::lastModified))
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    // ---- helpers --------------------------------------------------------------------------

    private void copyWithBackup(Path source, Path target) throws Exception {
        if (Files.exists(target)
                && Files.mismatch(source, target) >= 0) {
            backup(target);
        }
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private void backup(Path target) throws Exception {
        if (backupRoot == null) {
            backupRoot = plugin.getDataFolder().toPath().resolve("AssetBackups");
        }
        Path backup = backupRoot.resolve(target.getFileName().toString());
        Files.createDirectories(backup.getParent());
        Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
    }

    private static int count(File dir, String ext) {
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(ext));
        return files == null ? 0 : files.length;
    }
}
