package ru.lor.plugin.mechanics;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import ru.lor.plugin.LorPlugin;

import java.util.Random;

/** Rare deep-dark shrines that also work as ready-made ritual sites. */
public final class ShrinePopulator extends BlockPopulator {

    private final LorPlugin plugin;

    public ShrinePopulator(LorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion region) {
        if (!plugin.config().shrinesEnabled()) {
            return;
        }
        if (random.nextDouble() > plugin.config().shrineChance()) {
            return;
        }
        int centerX = chunkX * 16 + 8;
        int centerZ = chunkZ * 16 + 8;
        int minY = plugin.config().shrineMinY();
        int maxY = plugin.config().shrineMaxY();
        if (maxY <= minY) {
            return;
        }
        int y = minY + random.nextInt(maxY - minY);
        if (!region.isInRegion(centerX, y, centerZ)) {
            return;
        }
        if (region.getBiome(centerX, y, centerZ) != Biome.DEEP_DARK) {
            return;
        }
        if (region.getType(centerX, y, centerZ) != Material.AIR
                || region.getType(centerX, y + 1, centerZ) != Material.AIR) {
            return;
        }
        if (!region.getType(centerX, y - 1, centerZ).isSolid()) {
            return;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                if (!region.isInRegion(x, y - 1, z)) {
                    return;
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Material floor = random.nextDouble() < 0.2 ? Material.SCULK : Material.DEEPSLATE;
                region.setType(centerX + dx, y - 1, centerZ + dz, floor);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                region.setType(centerX + dx, y, centerZ + dz, Material.SCULK);
            }
        }
        for (int x : new int[]{-2, 2}) {
            for (int z : new int[]{-2, 2}) {
                region.setType(centerX + x, y, centerZ + z, Material.SCULK_SHRIEKER);
            }
        }
        for (int[] side : new int[][]{{-2, 0}, {2, 0}, {0, -2}, {0, 2}}) {
            region.setType(centerX + side[0], y, centerZ + side[1], Material.SCULK);
        }
        region.setType(centerX, y, centerZ, Material.SCULK_CATALYST);
        for (int attempt = 0; attempt < 24; attempt++) {
            int x = centerX + random.nextInt(15) - 7;
            int z = centerZ + random.nextInt(15) - 7;
            int yy = y - 1 + random.nextInt(3) - 1;
            if (!region.isInRegion(x, yy, z)) {
                continue;
            }
            if (region.getType(x, yy, z) == Material.AIR
                    && region.getType(x, yy - 1, z).isSolid()) {
                region.setType(x, yy, z, random.nextBoolean() ? Material.SCULK_VEIN : Material.SCULK);
            }
        }
        if (plugin.config().debug()) {
            plugin.getLogger().info("Скалк-святилище в чанке " + chunkX + "," + chunkZ + " (y=" + y + ")");
        }
    }
}
