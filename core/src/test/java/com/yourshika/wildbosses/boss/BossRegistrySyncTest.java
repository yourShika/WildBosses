package com.yourshika.wildbosses.boss;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards {@code /wb sync} (BossRegistry.syncPreservingLoot): it refreshes every boss's non-loot content
 * from the bundle while keeping the admin's {@code drops:} block. That splice is only safe because
 * {@code drops:} is the LAST top-level key in every bundled boss - these tests pin both facts against
 * the real bundled YAMLs so a future boss that violates the layout fails CI instead of eating loot.
 */
class BossRegistrySyncTest {

    private static final Pattern TOP_KEY =
            Pattern.compile("^([A-Za-z][A-Za-z0-9_-]*):", Pattern.MULTILINE);
    private static final Pattern TOP_DROPS = Pattern.compile("^drops:", Pattern.MULTILINE);

    private static File[] bossFiles() throws Exception {
        var url = BossRegistrySyncTest.class.getResource("/bosses");
        assertNotNull(url, "bundled /bosses resources are not on the test classpath");
        File[] files = new File(url.toURI()).listFiles((d, n) -> n.endsWith(".yml"));
        assertNotNull(files, "no boss YAMLs found");
        assertTrue(files.length >= 18, "expected >=18 bundled bosses, found " + files.length);
        return files;
    }

    @Test
    void dropsIsAlwaysTheLastTopLevelKey() throws Exception {
        for (File f : bossFiles()) {
            String text = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            Matcher m = TOP_KEY.matcher(text);
            String last = null;
            while (m.find()) {
                last = m.group(1);
            }
            assertEquals("drops", last,
                    f.getName() + ": 'drops' must be the last top-level key for the loot-preserving splice");
        }
    }

    @Test
    void spliceKeepsOnDiskLootAndTakesEverythingElseFromBundle() throws Exception {
        String sentinelDrops = "drops:\n  items: []\n  xp: 91234\n  commands: [\"SENTINEL_CMD\"]\n";
        for (File f : bossFiles()) {
            String bundled = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            Matcher m = TOP_DROPS.matcher(bundled);
            assertTrue(m.find(), f.getName() + ": expected a drops section");
            String bundledHead = bundled.substring(0, m.start());

            // An admin's on-disk file: custom loot, PLUS mangled non-loot that must be discarded.
            String onDisk = "id: THIS_SHOULD_BE_IGNORED\nskills: []\n" + sentinelDrops;
            String merged = BossRegistry.spliceKeepingDrops(bundled, onDisk);

            // Non-loot content is taken entirely from the bundle (fresh abilities/stats), never from disk.
            assertEquals(bundledHead + sentinelDrops, merged, f.getName() + ": splice result mismatch");
            assertFalse(merged.contains("THIS_SHOULD_BE_IGNORED"),
                    f.getName() + ": stale on-disk non-loot leaked into the merge");
            // The admin's loot survives verbatim.
            assertTrue(merged.contains("SENTINEL_CMD") && merged.contains("91234"),
                    f.getName() + ": on-disk loot was not preserved");
            // And the result is still valid YAML with both the kept loot and the bundled skills.
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(new StringReader(merged));
            assertNotNull(yml.getConfigurationSection("drops"), f.getName() + ": drops section broke");
            assertEquals(91234, yml.getInt("drops.xp"), f.getName() + ": kept xp not readable");
            assertNotNull(yml.get("skills"), f.getName() + ": bundled skills missing after merge");
        }
    }

    @Test
    void autoRefreshOnlyOverwritesProvablyUneditedFiles() {
        String bundledOld = "hash-bundled-old";
        String bundledNew = "hash-bundled-new";
        String merged = "hash-merged-with-custom-loot";

        // We recorded exactly what's on disk and the bundle changed -> safe to refresh.
        assertTrue(BossRegistry.mayAutoRefresh(bundledOld, bundledOld, bundledNew));
        // On-disk already equals bundled -> nothing to do.
        assertFalse(BossRegistry.mayAutoRefresh(bundledNew, bundledNew, bundledNew));
        // LOST hash store (recorded == null) and the file differs from bundled: must NOT overwrite,
        // or a synced file's custom loot would be destroyed on the next boot. This is the key guard.
        assertFalse(BossRegistry.mayAutoRefresh(null, merged, bundledNew));
        // Edited since we wrote it (record != on-disk) -> leave alone.
        assertFalse(BossRegistry.mayAutoRefresh(bundledOld, merged, bundledNew));
    }

    @Test
    void onDiskWithoutDropsLeavesBundledFileUnchanged() throws Exception {
        File f = bossFiles()[0];
        String bundled = Files.readString(f.toPath(), StandardCharsets.UTF_8);
        String merged = BossRegistry.spliceKeepingDrops(bundled, "id: x\nstats: {}\n"); // on-disk has no loot
        assertEquals(bundled, merged, "no on-disk drops -> keep the bundled file (including its loot) as-is");
    }
}
