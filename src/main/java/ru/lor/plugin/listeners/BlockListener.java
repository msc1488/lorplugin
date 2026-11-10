package ru.lor.plugin.listeners;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import ru.lor.plugin.LorPlugin;
import ru.lor.plugin.data.Profile;
import ru.lor.plugin.mechanics.CorruptionManager;

import java.util.Random;

/** Rewards for mining sculk and progress for "break sculk" objectives. */
public final class BlockListener implements Listener {

    private final LorPlugin plugin;
    private final Random random = new Random();

    public BlockListener(LorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!CorruptionManager.isSculk(block.getType())) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || !plugin.config().isWorldEnabled(block.getWorld())) {
            return;
        }
        Profile profile = plugin.profiles().get(player);
        profile.addEcho(plugin.config().echoPerSculkBlock());
        profile.addBlockBroken();
        profile.addCorruption(0.5);
        plugin.quests().onBreakSculk(player);

        block.getWorld().spawnParticle(Particle.SCULK_SOUL, block.getLocation().add(0.5, 0.5, 0.5),
                5, 0.3, 0.3, 0.3, 0.01);
        player.playSound(block.getLocation(), Sound.BLOCK_SCULK_BREAK, 0.7f, 1.3f);
        plugin.messages().actionBar(player, "blocks.echo",
                "echo", plugin.config().echoPerSculkBlock());

        double chance = switch (block.getType()) {
            case SCULK_CATALYST, SCULK_SHRIEKER -> 0.50;
            default -> 0.12;
        };
        if (random.nextDouble() < chance) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5),
                    plugin.items().create("echo_shard", 1));
        }
        if (block.getType() == Material.SCULK_CATALYST) {
            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_SCULK_CATALYST_BREAK, 1.2f, 0.7f);
        }
    }
}
