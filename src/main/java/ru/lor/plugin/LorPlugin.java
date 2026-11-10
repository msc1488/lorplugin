package ru.lor.plugin;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.lor.plugin.command.LorCommand;
import ru.lor.plugin.auction.AuctionManager;
import ru.lor.plugin.config.Config;
import ru.lor.plugin.config.Messages;
import ru.lor.plugin.data.ProfileStore;
import ru.lor.plugin.economy.PaymentLog;
import ru.lor.plugin.economy.PaymentManager;
import ru.lor.plugin.gui.GuiManager;
import ru.lor.plugin.integration.PlaceholderHook;
import ru.lor.plugin.items.CustomItems;
import ru.lor.plugin.items.RecipeManager;
import ru.lor.plugin.listeners.BlockListener;
import ru.lor.plugin.listeners.EntityListener;
import ru.lor.plugin.listeners.PlayerListener;
import ru.lor.plugin.mechanics.BossManager;
import ru.lor.plugin.mechanics.CorruptionManager;
import ru.lor.plugin.mechanics.MobManager;
import ru.lor.plugin.mechanics.RitualManager;
import ru.lor.plugin.mechanics.ShrinePopulator;
import ru.lor.plugin.mechanics.TideManager;
import ru.lor.plugin.quest.QuestManager;
import ru.lor.plugin.util.Cooldown;
import ru.lor.plugin.util.ItemNames;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Echoes of the Deep — a story-driven Sculk plugin for Paper 1.21.11.
 */
public final class LorPlugin extends JavaPlugin implements Listener {

    private Config config;
    private Messages messages;
    private ProfileStore profiles;
    private PaymentManager payments;
    private PaymentLog paymentLogs;
    private AuctionManager auction;
    private ItemNames itemNames;
    private CustomItems items;
    private RecipeManager recipes;
    private QuestManager quests;
    private CorruptionManager corruption;
    private MobManager mobs;
    private BossManager boss;
    private RitualManager rituals;
    private TideManager tide;
    private GuiManager gui;
    private final Cooldown cooldowns = new Cooldown();
    private final Set<UUID> hudDisabled = new HashSet<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private ShrinePopulator shrinePopulator;
    private PlaceholderHook placeholderHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveIfMissing("messages.yml");
        saveIfMissing("chapters.yml");

        this.messages = new Messages(this);
        this.config = new Config(this);
        this.items = new CustomItems(this);
        this.profiles = new ProfileStore(this);
        this.profiles.loadAll();
        this.payments = new PaymentManager(this);
        this.paymentLogs = new PaymentLog(this);
        this.itemNames = new ItemNames(this);
        this.auction = new AuctionManager(this);
        this.auction.load();
        this.quests = new QuestManager(this);
        this.corruption = new CorruptionManager(this);
        this.mobs = new MobManager(this);
        this.boss = new BossManager(this);
        this.rituals = new RitualManager(this);
        this.tide = new TideManager(this);
        this.gui = new GuiManager(this);
        this.recipes = new RecipeManager(this);
        this.recipes.registerAll();

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new EntityListener(this), this);
        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(this, this);

        registerCommand();
        registerPopulators();
        startTasks();
        registerPlaceholder();

        getLogger().info("Отголоски Бездны пробудились. Главы: " + quests.chapters().size()
                + ", предметы: " + CustomItems.IDS.size());
    }

    @Override
    public void onDisable() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        if (boss != null) {
            boss.stop(true);
        }
        if (tide != null) {
            tide.shutdown();
        }
        if (mobs != null && config != null && config.despawnOnDisable()) {
            mobs.despawnAll(false);
        }
        if (recipes != null) {
            recipes.unregisterAll();
        }
        if (profiles != null) {
            profiles.saveNow();
        }
        if (auction != null) {
            auction.save();
        }
        if (placeholderHook != null) {
            placeholderHook.unregister();
        }
        getLogger().info("Отголоски Бездны затихли.");
    }

    private void saveIfMissing(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) {
            saveResource(name, false);
        }
    }

    private void registerCommand() {
        LorCommand executor = new LorCommand(this);
        register("lor", executor);
        register("crafts", executor);
    }

    private void register(String name, LorCommand executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Команда /" + name + " отсутствует в plugin.yml!");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void startTasks() {
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, corruption::tick, 60L, 60L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, mobs::tick, 20L, 20L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, tide::tickMinute, 20L * 60L, 20L * 60L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                if (player.getLocation().getBlock().getBiome() == org.bukkit.block.Biome.DEEP_DARK
                        && config.isWorldEnabled(player.getWorld())) {
                    quests.onDeepDark(player);
                }
            }
        }, 40L, 40L));
        long interval = config.saveIntervalSeconds() * 20L;
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, profiles::saveAsync, interval, interval));
    }

    private void registerPopulators() {
        if (shrinePopulator == null) {
            shrinePopulator = new ShrinePopulator(this);
        }
        for (World world : Bukkit.getWorlds()) {
            if (!config.isWorldEnabled(world)) {
                continue;
            }
            if (!world.getPopulators().contains(shrinePopulator)) {
                world.getPopulators().add(shrinePopulator);
            }
        }
    }

    private void unregisterPopulators() {
        if (shrinePopulator == null) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            world.getPopulators().remove(shrinePopulator);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (config != null && shrinePopulator != null && config.isWorldEnabled(event.getWorld())) {
            if (!event.getWorld().getPopulators().contains(shrinePopulator)) {
                event.getWorld().getPopulators().add(shrinePopulator);
            }
        }
    }

    private void registerPlaceholder() {
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                placeholderHook = new PlaceholderHook(this);
                if (placeholderHook.register()) {
                    getLogger().info("PlaceholderAPI: плейсхолдеры %lor_*% зарегистрированы.");
                }
            } catch (Throwable throwable) {
                getLogger().warning("Не удалось подключить PlaceholderAPI: " + throwable.getMessage());
                placeholderHook = null;
            }
        }
    }

    public void reloadEverything() {
        config.reload();
        messages.reload();
        quests.load();
        recipes.unregisterAll();
        recipes.registerAll();
        unregisterPopulators();
        registerPopulators();
    }

    public Config config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public ProfileStore profiles() {
        return profiles;
    }

    public PaymentManager payments() {
        return payments;
    }

    public PaymentLog paymentLogs() {
        return paymentLogs;
    }

    public AuctionManager auction() {
        return auction;
    }

    public ItemNames itemNames() {
        return itemNames;
    }

    public CustomItems items() {
        return items;
    }

    public RecipeManager recipes() {
        return recipes;
    }

    public QuestManager quests() {
        return quests;
    }

    public CorruptionManager corruption() {
        return corruption;
    }

    public MobManager mobs() {
        return mobs;
    }

    public BossManager boss() {
        return boss;
    }

    public RitualManager rituals() {
        return rituals;
    }

    public TideManager tide() {
        return tide;
    }

    public GuiManager gui() {
        return gui;
    }

    public Cooldown cooldowns() {
        return cooldowns;
    }

    public boolean isHudEnabled(org.bukkit.entity.Player player) {
        return !hudDisabled.contains(player.getUniqueId());
    }

    public boolean toggleHud(org.bukkit.entity.Player player) {
        if (!hudDisabled.add(player.getUniqueId())) {
            hudDisabled.remove(player.getUniqueId());
            return true;
        }
        return false;
    }
}
