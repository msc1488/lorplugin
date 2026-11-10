package ru.lor.plugin.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import ru.lor.plugin.LorPlugin;
import ru.lor.plugin.auction.AuctionListing;
import ru.lor.plugin.auction.AuctionManager;
import ru.lor.plugin.data.Profile;
import ru.lor.plugin.items.CustomItems;
import ru.lor.plugin.items.RecipeBook;
import ru.lor.plugin.quest.Chapter;
import ru.lor.plugin.quest.QuestManager;
import ru.lor.plugin.util.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** All chat-free interfaces: profile, chapters, codex, relics, stats, leaderboard. */
public final class GuiManager implements Listener {

    private static final int AUCTION_PAGE_SIZE = 45;

    private final LorPlugin plugin;
    private final RecipeBook recipeBook;

    public GuiManager(LorPlugin plugin) {
        this.plugin = plugin;
        this.recipeBook = new RecipeBook(plugin);
    }

    public void openMain(Player player) {
        MenuHolder holder = new MenuHolder(MenuHolder.Type.MAIN, 0);
        Inventory inventory = Bukkit.createInventory(holder, 27, plugin.messages().raw("gui.main.title"));
        holder.inventory(inventory);
        fill(inventory);
        Profile profile = plugin.profiles().get(player);
        Chapter chapter = plugin.quests().current(profile);

        List<Component> echoLore = new ArrayList<>();
        echoLore.add(plugin.messages().raw("gui.main.echo-value", "echo", profile.echo()));
        echoLore.add(plugin.messages().raw("gui.main.corruption-value", "percent", Math.round(profile.corruption())));
        echoLore.add(plugin.messages().raw("gui.main.chapter-value",
                "chapter", QuestManager.roman(profile.chapter()),
                "title", chapter == null ? "—" : Text.plain(chapter.title())));
        inventory.setItem(4, icon(Material.ECHO_SHARD, plugin.messages().raw("gui.main.echo"), echoLore));
        inventory.setItem(10, icon(Material.WRITTEN_BOOK, plugin.messages().raw("gui.main.quests"),
                List.of(plugin.messages().raw("gui.main.quests-lore"))));
        inventory.setItem(12, icon(Material.KNOWLEDGE_BOOK, plugin.messages().raw("gui.main.codex"),
                List.of(plugin.messages().raw("gui.main.codex-lore"))));
        inventory.setItem(14, icon(Material.AMETHYST_SHARD, plugin.messages().raw("gui.main.relics"),
                List.of(plugin.messages().raw("gui.main.relics-lore", "count", profile.relics().size()))));
        inventory.setItem(16, icon(Material.PAPER, plugin.messages().raw("gui.main.stats"),
                List.of(plugin.messages().raw("gui.main.stats-lore"))));
        inventory.setItem(18, icon(Material.EMERALD, plugin.messages().raw("gui.main.auction"),
                List.of(plugin.messages().raw("gui.main.auction-lore"))));
        inventory.setItem(20, icon(Material.GOLD_INGOT, plugin.messages().raw("gui.main.top"),
                List.of(plugin.messages().raw("gui.main.top-lore"))));
        inventory.setItem(22, icon(Material.SCULK_SENSOR, plugin.messages().raw("gui.main.ritual"),
                plugin.messages().list("gui.main.ritual-lore")));
        inventory.setItem(24, icon(Material.CRAFTING_TABLE, plugin.messages().raw("gui.main.recipes"),
                List.of(plugin.messages().raw("gui.main.recipes-lore"))));
        inventory.setItem(26, icon(Material.BARRIER, plugin.messages().raw("gui.main.close"), List.of()));
        player.openInventory(inventory);
    }

    public void openQuests(Player player) {
        MenuHolder holder = new MenuHolder(MenuHolder.Type.QUESTS, 0);
        Inventory inventory = Bukkit.createInventory(holder, 54, plugin.messages().raw("gui.quests.title"));
        holder.inventory(inventory);
        fill(inventory);
        Profile profile = plugin.profiles().get(player);
        int slot = 10;
        for (Chapter chapter : plugin.quests().chapters().values()) {
            if (slot > 16) {
                break;
            }
            boolean completed = profile.hasCompleted(chapter.id());
            boolean current = profile.chapter() == chapter.id();
            Material material = completed ? Material.LIME_DYE : current ? Material.ECHO_SHARD : Material.GRAY_DYE;
            List<Component> lore = new ArrayList<>();
            lore.add(plugin.messages().raw(completed ? "gui.quests.completed"
                    : current ? "gui.quests.current" : "gui.quests.locked"));
            lore.add(plugin.messages().raw("gui.quests.reward", "echo", chapter.rewardEcho()));
            inventory.setItem(slot, icon(material, Text.mm(chapter.title()), lore));
            slot++;
        }
        inventory.setItem(49, icon(Material.ARROW, plugin.messages().raw("gui.common.back"), List.of()));
        player.openInventory(inventory);
    }

    public void openChapter(Player player, int chapterId) {
        Chapter chapter = plugin.quests().chapter(chapterId);
        if (chapter == null) {
            openQuests(player);
            return;
        }
        MenuHolder holder = new MenuHolder(MenuHolder.Type.CHAPTER, chapterId);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                plugin.messages().raw("gui.chapter.title", "chapter", QuestManager.roman(chapterId)));
        holder.inventory(inventory);
        fill(inventory);
        Profile profile = plugin.profiles().get(player);
        List<Component> lore = new ArrayList<>();
        for (String line : chapter.lore()) {
            lore.add(Text.mm("<dark_gray>» <gray>" + line));
        }
        lore.add(Component.empty());
        lore.add(plugin.messages().raw(profile.hasCompleted(chapterId)
                ? "gui.chapter.status-completed" : "gui.chapter.status-open"));
        for (QuestManager.ObjectiveStatus status : plugin.quests().statuses(profile, chapter)) {
            lore.add(plugin.messages().raw(status.done()
                            ? "gui.chapter.objective-done" : "gui.chapter.objective-open",
                    "text", status.objective().text(),
                    "current", status.current(),
                    "amount", status.objective().amount()));
        }
        lore.add(Component.empty());
        lore.add(plugin.messages().raw("gui.chapter.reward", "echo", chapter.rewardEcho()));
        inventory.setItem(22, icon(Material.WRITTEN_BOOK, Text.mm(chapter.title()), lore));
        inventory.setItem(49, icon(Material.ARROW, plugin.messages().raw("gui.common.back"), List.of()));
        player.openInventory(inventory);
    }

    public void openCodex(Player player, int page) {
        ConfigurationSection section = plugin.messages().section("codex.pages");
        List<String> keys = new ArrayList<>();
        if (section != null) {
            keys.addAll(section.getKeys(false));
            keys.sort(Comparator.comparingInt(key -> {
                try {
                    return Integer.parseInt(key);
                } catch (NumberFormatException exception) {
                    return 0;
                }
            }));
        }
        int index = Math.max(0, Math.min(page, Math.max(0, keys.size() - 1)));
        MenuHolder holder = new MenuHolder(MenuHolder.Type.CODEX, index);
        Inventory inventory = Bukkit.createInventory(holder, 54, plugin.messages().raw("gui.codex.title"));
        holder.inventory(inventory);
        fill(inventory);
        if (keys.isEmpty()) {
            inventory.setItem(22, icon(Material.BOOK, plugin.messages().raw("gui.codex.empty"), List.of()));
        } else {
            String key = keys.get(index);
            String title = section.getString(key + ".title", "?");
            List<Component> lore = new ArrayList<>();
            for (String line : section.getStringList(key + ".text")) {
                lore.add(Text.mm("<gray>" + line));
            }
            inventory.setItem(4, icon(Material.BOOK, Text.mm(title), lore));
            inventory.setItem(22, icon(Material.ARROW, plugin.messages().raw("gui.codex.prev"), List.of()));
            inventory.setItem(24, icon(Material.ARROW, plugin.messages().raw("gui.codex.next"), List.of()));
        }
        inventory.setItem(49, icon(Material.ARROW, plugin.messages().raw("gui.common.back"), List.of()));
        player.openInventory(inventory);
    }

    public void openRelics(Player player) {
        MenuHolder holder = new MenuHolder(MenuHolder.Type.RELICS, 0);
        Inventory inventory = Bukkit.createInventory(holder, 54, plugin.messages().raw("gui.relics.title"));
        holder.inventory(inventory);
        fill(inventory);
        Profile profile = plugin.profiles().get(player);
        int slot = 10;
        for (String id : CustomItems.IDS) {
            if (slot > 16 && slot < 19) {
                slot = 19;
            }
            if (slot > 25) {
                break;
            }
            ItemStack stack = plugin.items().create(id, 1);
            if (stack == null) {
                continue;
            }
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.add(Component.empty());
                lore.add(plugin.messages().raw(profile.hasRelic(id)
                        ? "gui.relics.owned" : "gui.relics.not-owned"));
                meta.lore(lore);
                stack.setItemMeta(meta);
            }
            inventory.setItem(slot, stack);
            slot++;
        }
        inventory.setItem(49, icon(Material.ARROW, plugin.messages().raw("gui.common.back"), List.of()));
        player.openInventory(inventory);
    }

    public void openStats(Player player) {
        MenuHolder holder = new MenuHolder(MenuHolder.Type.STATS, 0);
        Inventory inventory = Bukkit.createInventory(holder, 27, plugin.messages().raw("gui.stats.title"));
        holder.inventory(inventory);
        fill(inventory);
        Profile profile = plugin.profiles().get(player);
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.messages().raw("gui.stats.echo", "echo", profile.echo()));
        lore.add(plugin.messages().raw("gui.stats.corruption", "percent", Math.round(profile.corruption())));
        lore.add(plugin.messages().raw("gui.stats.mobs", "count", profile.mobKills()));
        lore.add(plugin.messages().raw("gui.stats.bosses", "count", profile.bossKills()));
        lore.add(plugin.messages().raw("gui.stats.blocks", "count", profile.blocksBroken()));
        lore.add(plugin.messages().raw("gui.stats.tides", "count", profile.tidesSurvived()));
        inventory.setItem(13, icon(Material.PAPER, plugin.messages().raw("gui.stats.title"), lore));
        inventory.setItem(22, icon(Material.ARROW, plugin.messages().raw("gui.common.back"), List.of()));
        player.openInventory(inventory);
    }

    public void openTop(Player player) {
        MenuHolder holder = new MenuHolder(MenuHolder.Type.TOP, 0);
        Inventory inventory = Bukkit.createInventory(holder, 54, plugin.messages().raw("gui.top.title"));
        holder.inventory(inventory);
        fill(inventory);
        List<Profile> top = plugin.profiles().top(10);
        int slot = 10;
        int place = 1;
        for (Profile profile : top) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(profile.uuid()));
                meta.displayName(plugin.messages().raw("gui.top.entry",
                        "place", place, "player", profile.name().isBlank() ? "—" : profile.name())
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(plugin.messages().raw("gui.top.echo", "echo", profile.echo())
                        .decoration(TextDecoration.ITALIC, false)));
                head.setItemMeta(meta);
            }
            inventory.setItem(slot, head);
            slot++;
            if (slot == 17) {
                slot = 19;
            }
            if (slot > 25) {
                break;
            }
            place++;
        }
        inventory.setItem(49, icon(Material.ARROW, plugin.messages().raw("gui.common.back"), List.of()));
        player.openInventory(inventory);
    }

    public void openRecipes(Player player) {
        MenuHolder holder = new MenuHolder(MenuHolder.Type.RECIPES, 0);
        Inventory inventory = Bukkit.createInventory(holder, 54, plugin.messages().raw("gui.recipes.title"));
        holder.inventory(inventory);
        fill(inventory);
        List<RecipeBook.Recipe> recipes = recipeBook.all();
        if (recipes.isEmpty()) {
            inventory.setItem(22, icon(Material.BARRIER, plugin.messages().raw("gui.recipes.empty"), List.of()));
        }
        int slot = 10;
        for (RecipeBook.Recipe recipe : recipes) {
            ItemStack stack = plugin.items().create(recipe.id(), 1);
            if (stack == null) {
                continue;
            }
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.add(Component.empty());
                lore.add(plugin.messages().raw("gui.recipes.entry-lore"));
                meta.lore(lore);
                stack.setItemMeta(meta);
            }
            inventory.setItem(slot, stack);
            slot++;
        }
        inventory.setItem(49, icon(Material.ARROW, plugin.messages().raw("gui.common.back"), List.of()));
        player.openInventory(inventory);
    }

    public void openRecipe(Player player, int index) {
        List<RecipeBook.Recipe> recipes = recipeBook.all();
        if (recipes.isEmpty()) {
            openRecipes(player);
            return;
        }
        int current = Math.max(0, Math.min(index, recipes.size() - 1));
        RecipeBook.Recipe recipe = recipes.get(current);
        MenuHolder holder = new MenuHolder(MenuHolder.Type.RECIPE, current);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                plugin.messages().raw("gui.recipes.recipe-title",
                        "item", plugin.messages().rawString("items." + recipe.id() + ".short")));
        holder.inventory(inventory);
        fill(inventory);
        int[] slots = {10, 11, 12, 19, 20, 21, 28, 29, 30};
        if (recipe.shaped()) {
            for (int i = 0; i < 9 && i < recipe.grid().size(); i++) {
                ItemStack ingredient = recipeBook.stack(recipe.grid().get(i));
                if (ingredient != null) {
                    inventory.setItem(slots[i], ingredient);
                }
            }
        } else {
            for (int i = 0; i < recipe.shapeless().size() && i < 9; i++) {
                ItemStack ingredient = recipeBook.stack(recipe.shapeless().get(i));
                if (ingredient != null) {
                    inventory.setItem(slots[i], ingredient);
                }
            }
        }
        inventory.setItem(4, icon(Material.CRAFTING_TABLE, plugin.messages().raw("gui.recipes.howto"),
                plugin.messages().list("gui.recipes.howto-lore")));
        inventory.setItem(14, icon(Material.ARROW, plugin.messages().raw("gui.recipes.arrow"), List.of()));
        ItemStack result = plugin.items().create(recipe.id(), 1);
        if (result != null) {
            ItemMeta meta = result.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.add(Component.empty());
                lore.add(plugin.messages().raw("gui.recipes.result"));
                meta.lore(lore);
                result.setItemMeta(meta);
            }
            inventory.setItem(16, result);
        }
        inventory.setItem(45, icon(Material.SPECTRAL_ARROW, plugin.messages().raw("gui.recipes.prev"), List.of()));
        inventory.setItem(49, icon(Material.ARROW, plugin.messages().raw("gui.common.back"), List.of()));
        inventory.setItem(53, icon(Material.SPECTRAL_ARROW, plugin.messages().raw("gui.recipes.next"), List.of()));
        player.openInventory(inventory);
    }

    // ---------------- Аукцион ----------------

    public void openAuction(Player player, int page, String query) {
        List<AuctionListing> listings = auctionList(query);
        int pages = Math.max(1, (listings.size() + AUCTION_PAGE_SIZE - 1) / AUCTION_PAGE_SIZE);
        int current = Math.max(0, Math.min(page, pages - 1));
        MenuHolder holder = new MenuHolder(MenuHolder.Type.AUCTION, current, query, null);
        String titleQuery = query == null ? "" : query;
        if (titleQuery.length() > 24) {
            titleQuery = titleQuery.substring(0, 24) + "…";
        }
        Component title = query == null || query.isBlank()
                ? plugin.messages().raw("gui.auction.title")
                : plugin.messages().raw("gui.auction.search-title", "query", titleQuery);
        Inventory inventory = Bukkit.createInventory(holder, 54, title);
        holder.inventory(inventory);
        fill(inventory);

        int start = current * AUCTION_PAGE_SIZE;
        int end = Math.min(start + AUCTION_PAGE_SIZE, listings.size());
        for (int i = start; i < end; i++) {
            inventory.setItem(i - start, auctionIcon(player, listings.get(i), false));
        }
        if (listings.isEmpty()) {
            inventory.setItem(22, icon(Material.BARRIER, plugin.messages().raw(
                    query == null || query.isBlank() ? "gui.auction.empty" : "gui.auction.empty-search",
                    "query", query == null ? "" : query), List.of()));
        }
        if (current > 0) {
            inventory.setItem(45, icon(Material.SPECTRAL_ARROW, plugin.messages().raw("gui.auction.prev"), List.of()));
        }
        if (current < pages - 1) {
            inventory.setItem(53, icon(Material.SPECTRAL_ARROW, plugin.messages().raw("gui.auction.next"), List.of()));
        }
        inventory.setItem(47, icon(Material.PAPER, plugin.messages().raw("gui.auction.search-hint"), List.of()));
        inventory.setItem(48, icon(Material.CHEST, plugin.messages().raw("gui.auction.my"),
                List.of(plugin.messages().raw("gui.auction.my-lore"))));
        inventory.setItem(49, icon(Material.BOOK,
                plugin.messages().raw("gui.auction.page", "page", current + 1, "pages", pages), List.of()));
        inventory.setItem(50, icon(Material.ECHO_SHARD,
                plugin.messages().raw("gui.auction.balance", "echo", plugin.profiles().get(player).echo()), List.of()));
        inventory.setItem(51, icon(Material.BARRIER, plugin.messages().raw("gui.auction.close"), List.of()));
        player.openInventory(inventory);
    }

    public void openMyAuctions(Player player, int page) {
        List<AuctionListing> listings = plugin.auction().bySeller(player.getUniqueId());
        int pages = Math.max(1, (listings.size() + AUCTION_PAGE_SIZE - 1) / AUCTION_PAGE_SIZE);
        int current = Math.max(0, Math.min(page, pages - 1));
        MenuHolder holder = new MenuHolder(MenuHolder.Type.AUCTION_MY, current, null, null);
        Inventory inventory = Bukkit.createInventory(holder, 54, plugin.messages().raw("gui.auction.my-title"));
        holder.inventory(inventory);
        fill(inventory);

        int start = current * AUCTION_PAGE_SIZE;
        int end = Math.min(start + AUCTION_PAGE_SIZE, listings.size());
        for (int i = start; i < end; i++) {
            inventory.setItem(i - start, auctionIcon(player, listings.get(i), true));
        }
        if (listings.isEmpty()) {
            inventory.setItem(22, icon(Material.BARRIER, plugin.messages().raw("gui.auction.empty-my"), List.of()));
        }
        if (current > 0) {
            inventory.setItem(45, icon(Material.SPECTRAL_ARROW, plugin.messages().raw("gui.auction.prev"), List.of()));
        }
        if (current < pages - 1) {
            inventory.setItem(53, icon(Material.SPECTRAL_ARROW, plugin.messages().raw("gui.auction.next"), List.of()));
        }
        inventory.setItem(48, icon(Material.ARROW, plugin.messages().raw("gui.auction.back"), List.of()));
        inventory.setItem(49, icon(Material.BOOK,
                plugin.messages().raw("gui.auction.page", "page", current + 1, "pages", pages), List.of()));
        inventory.setItem(51, icon(Material.BARRIER, plugin.messages().raw("gui.auction.close"), List.of()));
        player.openInventory(inventory);
    }

    public void openAuctionConfirm(Player player, AuctionListing listing, String query, int page) {
        MenuHolder holder = new MenuHolder(MenuHolder.Type.AUCTION_CONFIRM, page, query, listing.id().toString());
        Inventory inventory = Bukkit.createInventory(holder, 27, plugin.messages().raw("gui.auction.confirm-title"));
        holder.inventory(inventory);
        fill(inventory);

        List<Component> lore = new ArrayList<>();
        lore.add(plugin.messages().raw("gui.auction.confirm-seller", "player", listing.sellerName()));
        lore.add(plugin.messages().raw("gui.auction.confirm-price", "price", listing.price()));
        lore.add(plugin.messages().raw("gui.auction.confirm-tax", "tax", plugin.auction().tax(listing.price())));
        inventory.setItem(13, decorate(listing.item(), lore));
        inventory.setItem(11, icon(Material.LIME_CONCRETE,
                plugin.messages().raw("gui.auction.confirm-buy", "price", listing.price()), List.of()));
        inventory.setItem(15, icon(Material.RED_CONCRETE, plugin.messages().raw("gui.auction.confirm-cancel"), List.of()));
        player.openInventory(inventory);
    }

    private List<AuctionListing> auctionList(String query) {
        if (query == null || query.isBlank()) {
            return new ArrayList<>(plugin.auction().all());
        }
        return plugin.auction().search(query);
    }

    private ItemStack auctionIcon(Player player, AuctionListing listing, boolean myMenu) {
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.messages().raw("gui.auction.listing-seller", "player", listing.sellerName()));
        lore.add(plugin.messages().raw("gui.auction.listing-price", "price", listing.price()));
        lore.add(plugin.messages().raw("gui.auction.listing-id", "id", listing.shortId()));
        if (listing.sellerId().equals(player.getUniqueId())) {
            lore.add(plugin.messages().raw(myMenu
                    ? "gui.auction.listing-cancel" : "gui.auction.listing-own"));
            if (!myMenu) {
                lore.add(plugin.messages().raw("gui.auction.listing-cancel-hint"));
            }
        } else {
            lore.add(plugin.messages().raw("gui.auction.listing-buy"));
        }
        return decorate(listing.item(), lore);
    }

    private ItemStack decorate(ItemStack stack, List<Component> extra) {
        ItemStack copy = stack == null ? new ItemStack(Material.BARRIER) : stack.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Component.empty());
            lore.addAll(extra);
            meta.lore(lore);
            copy.setItemMeta(meta);
        }
        return copy;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof MenuHolder menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) {
            return;
        }
        switch (menu.type()) {
            case MAIN -> handleMain(player, raw);
            case QUESTS -> handleQuests(player, raw);
            case CHAPTER -> {
                if (raw == 49) {
                    openQuests(player);
                }
            }
            case CODEX -> handleCodex(player, menu.page(), raw);
            case RECIPES -> handleRecipes(player, raw);
            case RECIPE -> handleRecipe(player, menu.page(), raw);
            case RELICS, STATS, TOP -> {
                if (raw == 49) {
                    openMain(player);
                }
            }
            case AUCTION -> handleAuction(player, menu, raw, event.isShiftClick());
            case AUCTION_MY -> handleMyAuctions(player, menu, raw);
            case AUCTION_CONFIRM -> handleAuctionConfirm(player, menu, raw);
        }
    }

    private void handleMain(Player player, int slot) {
        switch (slot) {
            case 10 -> openQuests(player);
            case 12 -> openCodex(player, 0);
            case 14 -> openRelics(player);
            case 16 -> openStats(player);
            case 18 -> openAuction(player, 0, null);
            case 20 -> openTop(player);
            case 22 -> {
                player.closeInventory();
                plugin.rituals().sendInfo(player);
            }
            case 24 -> openRecipes(player);
            case 26 -> player.closeInventory();
            default -> {
            }
        }
    }

    private void handleQuests(Player player, int slot) {
        if (slot == 49) {
            openMain(player);
            return;
        }
        if (slot >= 10 && slot <= 16) {
            int chapterId = slot - 9;
            if (plugin.quests().chapter(chapterId) != null) {
                openChapter(player, chapterId);
            }
        }
    }

    private void handleCodex(Player player, int page, int slot) {
        if (slot == 49) {
            openMain(player);
        } else if (slot == 22) {
            openCodex(player, Math.max(0, page - 1));
        } else if (slot == 24) {
            openCodex(player, page + 1);
        }
    }

    private void handleRecipes(Player player, int slot) {
        if (slot == 49) {
            openMain(player);
            return;
        }
        int index = slot - 10;
        if (index >= 0 && index < recipeBook.all().size()) {
            openRecipe(player, index);
        }
    }

    private void handleRecipe(Player player, int index, int slot) {
        if (slot == 49) {
            openRecipes(player);
        } else if (slot == 45) {
            openRecipe(player, Math.max(0, index - 1));
        } else if (slot == 53) {
            openRecipe(player, index + 1);
        }
    }

    private void handleAuction(Player player, MenuHolder menu, int slot, boolean shift) {
        if (slot == 45) {
            if (menu.page() > 0) {
                openAuction(player, menu.page() - 1, menu.query());
            }
            return;
        }
        if (slot == 53) {
            openAuction(player, menu.page() + 1, menu.query());
            return;
        }
        if (slot == 48) {
            openMyAuctions(player, 0);
            return;
        }
        if (slot == 51) {
            player.closeInventory();
            return;
        }
        if (slot < 0 || slot >= AUCTION_PAGE_SIZE) {
            return;
        }
        List<AuctionListing> listings = auctionList(menu.query());
        int index = menu.page() * AUCTION_PAGE_SIZE + slot;
        if (index < 0 || index >= listings.size()) {
            return;
        }
        AuctionListing listing = listings.get(index);
        if (listing.sellerId().equals(player.getUniqueId())) {
            if (shift) {
                sendCancelResult(player, plugin.auction().cancel(player, listing), listing);
                openAuction(player, menu.page(), menu.query());
            } else {
                plugin.messages().send(player, "command.ah-own-lot");
            }
            return;
        }
        openAuctionConfirm(player, listing, menu.query(), menu.page());
    }

    private void handleMyAuctions(Player player, MenuHolder menu, int slot) {
        if (slot == 45) {
            if (menu.page() > 0) {
                openMyAuctions(player, menu.page() - 1);
            }
            return;
        }
        if (slot == 53) {
            openMyAuctions(player, menu.page() + 1);
            return;
        }
        if (slot == 48) {
            openAuction(player, 0, null);
            return;
        }
        if (slot == 51) {
            player.closeInventory();
            return;
        }
        if (slot < 0 || slot >= AUCTION_PAGE_SIZE) {
            return;
        }
        List<AuctionListing> listings = plugin.auction().bySeller(player.getUniqueId());
        int index = menu.page() * AUCTION_PAGE_SIZE + slot;
        if (index < 0 || index >= listings.size()) {
            return;
        }
        AuctionListing listing = listings.get(index);
        sendCancelResult(player, plugin.auction().cancel(player, listing), listing);
        openMyAuctions(player, menu.page());
    }

    private void handleAuctionConfirm(Player player, MenuHolder menu, int slot) {
        if (slot == 15) {
            openAuction(player, menu.page(), menu.query());
            return;
        }
        if (slot != 11) {
            return;
        }
        AuctionListing listing = plugin.auction().find(menu.id());
        if (listing == null) {
            plugin.messages().send(player, "command.ah-not-found",
                    "id", menu.id() == null ? "?" : menu.id());
            openAuction(player, menu.page(), menu.query());
            return;
        }
        AuctionManager.Result result = plugin.auction().buy(player, listing);
        switch (result) {
            case SUCCESS -> plugin.messages().send(player, "command.ah-bought",
                    "item", AuctionManager.itemName(listing.item()),
                    "price", listing.price(),
                    "player", listing.sellerName());
            case SUCCESS_DROPPED -> {
                plugin.messages().send(player, "command.ah-bought",
                        "item", AuctionManager.itemName(listing.item()),
                        "price", listing.price(),
                        "player", listing.sellerName());
                plugin.messages().send(player, "command.ah-dropped");
            }
            case NOT_ENOUGH -> plugin.messages().send(player, "command.ah-buy-not-enough",
                    "price", listing.price(),
                    "echo", plugin.profiles().get(player).echo());
            case OWN_LISTING -> plugin.messages().send(player, "command.ah-own-lot");
            case DISABLED -> plugin.messages().send(player, "command.ah-disabled");
            default -> plugin.messages().send(player, "command.ah-not-found", "id", listing.shortId());
        }
        openAuction(player, menu.page(), menu.query());
    }

    private void sendCancelResult(Player player, AuctionManager.Result result, AuctionListing listing) {
        switch (result) {
            case SUCCESS -> plugin.messages().send(player, "command.ah-cancelled",
                    "item", AuctionManager.itemName(listing.item()));
            case SUCCESS_DROPPED -> {
                plugin.messages().send(player, "command.ah-cancelled",
                        "item", AuctionManager.itemName(listing.item()));
                plugin.messages().send(player, "command.ah-dropped");
            }
            case NOT_OWNER -> plugin.messages().send(player, "command.ah-not-owner");
            case DISABLED -> plugin.messages().send(player, "command.ah-disabled");
            default -> plugin.messages().send(player, "command.ah-not-found", "id", listing.shortId());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    private void fill(Inventory inventory) {
        ItemStack filler = icon(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler);
            }
        }
    }

    private ItemStack icon(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            List<Component> safe = new ArrayList<>();
            for (Component line : lore) {
                safe.add(line.decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(safe);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
