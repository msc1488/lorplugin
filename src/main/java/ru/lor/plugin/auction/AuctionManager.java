package ru.lor.plugin.auction;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.lor.plugin.LorPlugin;
import ru.lor.plugin.data.Profile;
import ru.lor.plugin.util.ItemNames;
import ru.lor.plugin.util.Text;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Встроенный аукцион: игроки выставляют предметы за эхо.
 * Лоты хранятся в data/auction.yml, предметы — в Base64-виде (Paper ItemStack#serializeAsBytes).
 */
public final class AuctionManager {

    public enum Result {
        SUCCESS,
        SUCCESS_DROPPED,
        DISABLED,
        EMPTY_HAND,
        BLOCKED,
        PRICE_TOO_LOW,
        PRICE_TOO_HIGH,
        LIMIT_REACHED,
        NOT_FOUND,
        OWN_LISTING,
        NOT_OWNER,
        NOT_ENOUGH
    }

    /** Результат выставления лота: при успехе отдаём созданный лот (нужен его ID). */
    public record Sale(Result result, AuctionListing listing) {
    }

    private final LorPlugin plugin;
    private final File file;
    private final Map<UUID, AuctionListing> listings = new ConcurrentHashMap<>();

    public AuctionManager(LorPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/auction.yml");
    }

    public void load() {
        listings.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("listings");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            UUID id = parseUuid(key);
            UUID sellerId = parseUuid(section.getString("seller", ""));
            if (id == null || sellerId == null) {
                plugin.getLogger().warning("Пропущен некорректный лот аукциона: " + key);
                continue;
            }
            ItemStack item = decode(section.getString("item", ""));
            long price = section.getLong("price", 0L);
            if (item == null || item.getType().isAir() || price <= 0L) {
                plugin.getLogger().warning("Пропущен пустой лот аукциона: " + key);
                continue;
            }
            listings.put(id, new AuctionListing(id, sellerId,
                    section.getString("seller-name", ""), item, price,
                    section.getLong("created", System.currentTimeMillis())));
        }
        plugin.getLogger().info("Загружено лотов аукциона: " + listings.size());
    }

    public Collection<AuctionListing> all() {
        List<AuctionListing> list = new ArrayList<>(listings.values());
        list.sort(Comparator.comparingLong(AuctionListing::createdAt).reversed());
        return list;
    }

    public List<AuctionListing> bySeller(UUID sellerId) {
        List<AuctionListing> result = new ArrayList<>();
        for (AuctionListing listing : all()) {
            if (listing.sellerId().equals(sellerId)) {
                result.add(listing);
            }
        }
        return result;
    }

    public List<AuctionListing> search(String query) {
        String needle = ItemNames.normalize(query == null ? "" : query.trim());
        if (needle.isEmpty()) {
            return new ArrayList<>(all());
        }
        List<AuctionListing> result = new ArrayList<>();
        for (AuctionListing listing : all()) {
            if (matches(listing, needle)) {
                result.add(listing);
            }
        }
        return result;
    }

    private boolean matches(AuctionListing listing, String needle) {
        ItemStack item = listing.item();
        if (item == null) {
            return false;
        }
        if (ItemNames.matches(item.getType().name(), needle)) {
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()
                && ItemNames.matches(Text.plain(meta.displayName()), needle)) {
            return true;
        }
        String russian = plugin.itemNames().russian(item.getType());
        return russian != null && ItemNames.matches(russian, needle);
    }

    /** Ищет лот по полному UUID или по первым символам ID. */
    public AuctionListing find(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }
        try {
            AuctionListing exact = listings.get(UUID.fromString(value));
            if (exact != null) {
                return exact;
            }
        } catch (IllegalArgumentException ignored) {
            // не UUID — ищем по префиксу
        }
        if (value.length() < 4) {
            return null;
        }
        for (AuctionListing listing : listings.values()) {
            if (listing.id().toString().startsWith(value)) {
                return listing;
            }
        }
        return null;
    }

    public Sale sell(Player seller, ItemStack hand, long price) {
        if (!plugin.config().auctionEnabled()) {
            return new Sale(Result.DISABLED, null);
        }
        if (hand == null || hand.getType().isAir()) {
            return new Sale(Result.EMPTY_HAND, null);
        }
        if (blocked(hand.getType())) {
            return new Sale(Result.BLOCKED, null);
        }
        if (price < plugin.config().auctionMinPrice()) {
            return new Sale(Result.PRICE_TOO_LOW, null);
        }
        if (price > plugin.config().auctionMaxPrice()) {
            return new Sale(Result.PRICE_TOO_HIGH, null);
        }
        if (bySeller(seller.getUniqueId()).size() >= plugin.config().auctionMaxListings()) {
            return new Sale(Result.LIMIT_REACHED, null);
        }

        AuctionListing listing = new AuctionListing(UUID.randomUUID(),
                seller.getUniqueId(), seller.getName(), hand, price, System.currentTimeMillis());
        listings.put(listing.id(), listing);
        seller.getInventory().setItemInMainHand(null);
        save();
        plugin.getLogger().info("Аукцион: " + seller.getName() + " выставил "
                + listing.item().getType() + " x" + listing.item().getAmount()
                + " за " + price + " эха (лот " + listing.shortId() + ")");
        return new Sale(Result.SUCCESS, listing);
    }

    public Result buy(Player buyer, AuctionListing listing) {
        if (!plugin.config().auctionEnabled()) {
            return Result.DISABLED;
        }
        if (listing == null || !listings.containsKey(listing.id())) {
            return Result.NOT_FOUND;
        }
        if (listing.sellerId().equals(buyer.getUniqueId())) {
            return Result.OWN_LISTING;
        }
        Profile buyerProfile = plugin.profiles().get(buyer);
        if (buyerProfile.echo() < listing.price()) {
            return Result.NOT_ENOUGH;
        }
        if (!buyerProfile.spendEcho(listing.price())) {
            return Result.NOT_ENOUGH;
        }

        long tax = tax(listing.price());
        long payout = listing.price() - tax;
        plugin.profiles().get(listing.sellerId()).addEcho(payout);
        listings.remove(listing.id());
        boolean dropped = deliver(buyer, listing.item());

        plugin.profiles().saveAsync();
        save();

        Player seller = Bukkit.getPlayer(listing.sellerId());
        if (seller != null && seller.isOnline()) {
            plugin.messages().send(seller, "command.ah-seller-notify",
                    "item", itemName(listing.item()),
                    "price", listing.price(),
                    "tax", tax,
                    "received", payout);
        }
        plugin.getLogger().info("Аукцион: " + buyer.getName() + " купил "
                + listing.item().getType() + " у " + listing.sellerName()
                + " за " + listing.price() + " эха (лот " + listing.shortId() + ")");
        return dropped ? Result.SUCCESS_DROPPED : Result.SUCCESS;
    }

    public Result cancel(Player player, AuctionListing listing) {
        if (!plugin.config().auctionEnabled()) {
            return Result.DISABLED;
        }
        if (listing == null || !listings.containsKey(listing.id())) {
            return Result.NOT_FOUND;
        }
        if (!listing.sellerId().equals(player.getUniqueId())) {
            return Result.NOT_OWNER;
        }
        listings.remove(listing.id());
        boolean dropped = deliver(player, listing.item());
        save();
        plugin.getLogger().info("Аукцион: " + player.getName() + " снял лот " + listing.shortId());
        return dropped ? Result.SUCCESS_DROPPED : Result.SUCCESS;
    }

    public long tax(long price) {
        double percent = plugin.config().auctionTaxPercent();
        if (percent <= 0.0 || price <= 0L) {
            return 0L;
        }
        long value = (long) Math.floor(price * percent / 100.0);
        return Math.min(value, price - 1L);
    }

    private boolean blocked(Material material) {
        for (String name : plugin.config().auctionBlockedMaterials()) {
            if (name.equalsIgnoreCase(material.name())) {
                return true;
            }
        }
        return false;
    }

    /** Возвращает true, если предмет не поместился и был выброшен рядом. */
    private boolean deliver(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
        if (leftover.isEmpty()) {
            return false;
        }
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
        return true;
    }

    public static String itemName(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "?";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return Text.plain(meta.displayName());
        }
        return item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    public void save() {
        YamlConfiguration out = new YamlConfiguration();
        ConfigurationSection root = out.createSection("listings");
        for (AuctionListing listing : listings.values()) {
            ConfigurationSection section = root.createSection(listing.id().toString());
            section.set("seller", listing.sellerId().toString());
            section.set("seller-name", listing.sellerName());
            section.set("price", listing.price());
            section.set("created", listing.createdAt());
            section.set("item", Base64.getEncoder().encodeToString(listing.item().serializeAsBytes()));
        }
        write(out);
    }

    private synchronized void write(YamlConfiguration snapshot) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Не удалось создать папку data/ для auction.yml");
            }
            snapshot.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Не удалось сохранить auction.yml: " + exception.getMessage());
        }
    }

    private ItemStack decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Не удалось прочитать предмет лота: " + exception.getMessage());
            return null;
        }
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
