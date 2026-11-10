package ru.lor.plugin.command;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.lor.plugin.LorPlugin;
import ru.lor.plugin.auction.AuctionListing;
import ru.lor.plugin.auction.AuctionManager;
import ru.lor.plugin.data.Profile;
import ru.lor.plugin.economy.PaymentLog;
import ru.lor.plugin.economy.PaymentManager;
import ru.lor.plugin.items.CustomItems;
import ru.lor.plugin.mechanics.CustomMob;
import ru.lor.plugin.quest.QuestManager;
import ru.lor.plugin.util.ItemNames;
import ru.lor.plugin.util.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** /lor — player menu, story helpers and admin tools. */
public final class LorCommand implements CommandExecutor, TabCompleter {

    private static final int LOGS_PER_PAGE = 8;

    /** Сколько подсказок максимум отдавать клиенту, чтобы Tab не лагал. */
    private static final int SEARCH_SUGGESTION_LIMIT = 150;

    private final LorPlugin plugin;

    public LorCommand(LorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("crafts")) {
            if (!(sender instanceof Player player)) {
                plugin.messages().send(sender, "command.players-only");
                return true;
            }
            if (!hasUse(sender)) {
                return true;
            }
            plugin.gui().openRecipes(player);
            return true;
        }
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sendConsoleHelp(sender);
                return true;
            }
            if (!hasUse(sender)) {
                return true;
            }
            plugin.gui().openMain(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) {
            return admin(sender, args);
        }
        if (sub.equals("logs")) {
            return logs(sender, args);
        }
        if (sub.equals("top") && !(sender instanceof Player)) {
            printTop(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "command.players-only");
            return true;
        }
        if (!hasUse(sender)) {
            return true;
        }
        switch (sub) {
            case "help" -> help(player);
            case "menu" -> plugin.gui().openMain(player);
            case "quests" -> plugin.gui().openQuests(player);
            case "codex" -> plugin.gui().openCodex(player, 0);
            case "relics" -> plugin.gui().openRelics(player);
            case "recipes", "crafts", "craft" -> plugin.gui().openRecipes(player);
            case "stats" -> plugin.gui().openStats(player);
            case "top" -> plugin.gui().openTop(player);
            case "pay" -> pay(player, args);
            case "ah", "auction" -> auction(player, args);
            case "ritual" -> plugin.rituals().sendInfo(player);
            case "hud" -> {
                boolean enabled = plugin.toggleHud(player);
                plugin.messages().send(player, enabled ? "command.hud-on" : "command.hud-off");
            }
            default -> help(player);
        }
        return true;
    }

    private boolean hasUse(CommandSender sender) {
        if (!sender.hasPermission("lor.use")) {
            plugin.messages().send(sender, "command.no-permission");
            return false;
        }
        return true;
    }

    private void help(CommandSender sender) {
        for (String line : plugin.messages().stringList("command.help")) {
            sender.sendMessage(Text.mm(line));
        }
        if (sender.hasPermission("lor.pay")) {
            sender.sendMessage(plugin.messages().raw("command.pay-help-line"));
        }
        if (sender.hasPermission("lor.auction")) {
            sender.sendMessage(plugin.messages().raw("command.ah-help-line"));
        }
        if (sender.hasPermission("lor.logs")) {
            sender.sendMessage(plugin.messages().raw("command.logs-help"));
        }
    }

    private void sendConsoleHelp(CommandSender sender) {
        sender.sendMessage("LorPlugin: /lor top | /lor logs | /lor admin ...");
    }

    private void printTop(CommandSender sender) {
        int place = 1;
        for (Profile profile : plugin.profiles().top(10)) {
            sender.sendMessage(plugin.messages().raw("command.top-line",
                    "place", place,
                    "player", profile.name().isBlank() ? profile.uuid().toString().substring(0, 8) : profile.name(),
                    "echo", profile.echo()));
            place++;
        }
    }

    /** /lor pay &lt;ник&gt; &lt;сумма&gt; — перевод эха другому игроку. */
    private void pay(Player player, String[] args) {
        if (!player.hasPermission("lor.pay")) {
            plugin.messages().send(player, "command.no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.messages().send(player, "command.pay-usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(player, "command.player-not-found", "player", args[1]);
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            plugin.messages().send(player, "command.pay-self");
            return;
        }
        long amount = parseAmount(args[2], plugin.profiles().get(player).echo());
        if (amount <= 0L) {
            plugin.messages().send(player, "command.pay-invalid-amount");
            return;
        }

        PaymentManager.Result result = plugin.payments().pay(player, target, amount);
        switch (result) {
            case SUCCESS -> {
                long tax = plugin.payments().tax(amount);
                long received = plugin.payments().received(amount);
                plugin.messages().send(player, "command.pay-success",
                        "player", target.getName(), "amount", amount, "received", received);
                plugin.messages().send(target, "command.pay-received",
                        "player", player.getName(), "amount", received);
                if (tax > 0L) {
                    plugin.messages().send(player, "command.pay-tax", "tax", tax, "received", received);
                    plugin.messages().send(target, "command.pay-tax-received", "tax", tax);
                }
            }
            case DISABLED -> plugin.messages().send(player, "command.pay-disabled");
            case SELF -> plugin.messages().send(player, "command.pay-self");
            case INVALID_AMOUNT -> plugin.messages().send(player, "command.pay-invalid-amount");
            case TOO_SMALL -> plugin.messages().send(player, "command.pay-min",
                    "min", plugin.config().paymentMin());
            case TOO_LARGE -> plugin.messages().send(player, "command.pay-max",
                    "max", plugin.config().paymentMax());
            case NOT_ENOUGH -> plugin.messages().send(player, "command.pay-not-enough",
                    "echo", plugin.profiles().get(player).echo());
            case COOLDOWN -> plugin.messages().send(player, "command.pay-cooldown",
                    "seconds", plugin.payments().cooldownSeconds(player));
        }
    }

    /** Понимает 250, 1.5k, 2к, 1m и all/все. Возвращает -1 при ошибке. */
    private long parseAmount(String raw, long balance) {
        String value = raw.trim().toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
        if (value.isEmpty()) {
            return -1L;
        }
        if (value.equals("all") || value.equals("все") || value.equals("всё")) {
            return balance;
        }
        double multiplier = 1.0;
        if (value.endsWith("k") || value.endsWith("к")) {
            multiplier = 1_000.0;
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("m") || value.endsWith("м")) {
            multiplier = 1_000_000.0;
            value = value.substring(0, value.length() - 1);
        }
        try {
            double parsed = Double.parseDouble(value.replace(',', '.')) * multiplier;
            if (!Double.isFinite(parsed) || parsed <= 0.0) {
                return -1L;
            }
            return Math.min(Long.MAX_VALUE / 4L, Math.round(parsed));
        } catch (NumberFormatException exception) {
            return -1L;
        }
    }

    /** /lor logs [ник] [страница] — журнал переводов, доступ по lor.logs. */
    private boolean logs(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lor.logs")) {
            plugin.messages().send(sender, "command.no-permission");
            return true;
        }
        if (!plugin.config().paymentLogsEnabled()) {
            plugin.messages().send(sender, "command.logs-disabled");
            return true;
        }
        String filter = null;
        int page = 1;
        for (int i = 1; i < args.length; i++) {
            Integer number = tryInt(args[i]);
            if (number != null) {
                page = Math.max(1, number);
            } else {
                filter = args[i];
            }
        }
        List<PaymentLog.Entry> entries = plugin.paymentLogs().query(filter);
        if (entries.isEmpty()) {
            if (filter == null) {
                plugin.messages().send(sender, "command.logs-empty");
            } else {
                plugin.messages().send(sender, "command.logs-empty-player", "player", filter);
            }
            return true;
        }
        int pages = (entries.size() + LOGS_PER_PAGE - 1) / LOGS_PER_PAGE;
        page = Math.min(page, pages);
        plugin.messages().send(sender, "command.logs-header",
                "player", filter == null ? "все игроки" : filter,
                "count", entries.size(),
                "page", page,
                "pages", pages);
        int start = (page - 1) * LOGS_PER_PAGE;
        int end = Math.min(start + LOGS_PER_PAGE, entries.size());
        for (int i = start; i < end; i++) {
            PaymentLog.Entry entry = entries.get(i);
            String key = entry.tax() > 0L ? "command.logs-line-tax" : "command.logs-line";
            plugin.messages().send(sender, key,
                    "time", PaymentLog.formatTime(entry.time()),
                    "from", entry.fromName(),
                    "to", entry.toName(),
                    "amount", entry.amount(),
                    "tax", entry.tax(),
                    "received", entry.received());
        }
        if (pages > 1) {
            plugin.messages().send(sender, "command.logs-footer", "page", page, "pages", pages);
        }
        return true;
    }

    /** /lor ah — встроенный аукцион. */
    private void auction(Player player, String[] args) {
        if (!player.hasPermission("lor.auction")) {
            plugin.messages().send(player, "command.no-permission");
            return;
        }
        if (!plugin.config().auctionEnabled()) {
            plugin.messages().send(player, "command.ah-disabled");
            return;
        }
        if (args.length < 2) {
            plugin.gui().openAuction(player, 0, null);
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        Integer pageArg = tryInt(action);
        if (pageArg != null) {
            plugin.gui().openAuction(player, Math.max(0, pageArg - 1), null);
            return;
        }
        switch (action) {
            case "search" -> {
                if (args.length < 3) {
                    plugin.messages().send(player, "command.usage", "usage", "/lor ah search <запрос>");
                    return;
                }
                String query = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                plugin.gui().openAuction(player, 0, query);
            }
            case "sell" -> sell(player, args);
            case "my" -> {
                int page = args.length >= 3 ? Math.max(1, parseInt(args[2], 1)) : 1;
                plugin.gui().openMyAuctions(player, page - 1);
            }
            case "buy" -> {
                if (args.length < 3) {
                    plugin.messages().send(player, "command.usage", "usage", "/lor ah buy <id>");
                    return;
                }
                AuctionListing listing = plugin.auction().find(args[2]);
                if (listing == null) {
                    plugin.messages().send(player, "command.ah-not-found", "id", args[2]);
                    return;
                }
                sendBuyResult(player, listing, plugin.auction().buy(player, listing));
            }
            case "cancel" -> {
                if (args.length < 3) {
                    plugin.messages().send(player, "command.usage", "usage", "/lor ah cancel <id>");
                    return;
                }
                AuctionListing listing = plugin.auction().find(args[2]);
                if (listing == null) {
                    plugin.messages().send(player, "command.ah-not-found", "id", args[2]);
                    return;
                }
                sendCancelResult(player, listing, plugin.auction().cancel(player, listing));
            }
            default -> {
                for (String line : plugin.messages().stringList("command.ah-help")) {
                    player.sendMessage(Text.mm(line));
                }
            }
        }
    }

    private void sell(Player player, String[] args) {
        if (args.length < 3) {
            plugin.messages().send(player, "command.usage", "usage", "/lor ah sell <цена>");
            return;
        }
        long price = parseAmount(args[2], 0L);
        if (price <= 0L) {
            plugin.messages().send(player, "command.ah-price-invalid");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        AuctionManager.Sale sale = plugin.auction().sell(player, hand, price);
        switch (sale.result()) {
            case SUCCESS -> plugin.messages().send(player, "command.ah-sold",
                    "item", AuctionManager.itemName(sale.listing().item()),
                    "price", price,
                    "id", sale.listing().shortId());
            case DISABLED -> plugin.messages().send(player, "command.ah-disabled");
            case EMPTY_HAND -> plugin.messages().send(player, "command.ah-sell-empty");
            case BLOCKED -> plugin.messages().send(player, "command.ah-sell-blocked",
                    "item", AuctionManager.itemName(hand));
            case PRICE_TOO_LOW -> plugin.messages().send(player, "command.ah-price-low",
                    "min", plugin.config().auctionMinPrice());
            case PRICE_TOO_HIGH -> plugin.messages().send(player, "command.ah-price-high",
                    "max", plugin.config().auctionMaxPrice());
            case LIMIT_REACHED -> plugin.messages().send(player, "command.ah-limit",
                    "max", plugin.config().auctionMaxListings());
            default -> plugin.messages().send(player, "command.ah-disabled");
        }
    }

    private void sendBuyResult(Player player, AuctionListing listing, AuctionManager.Result result) {
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
    }

    private void sendCancelResult(Player player, AuctionListing listing, AuctionManager.Result result) {
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

    private Integer tryInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lor.admin")) {
            plugin.messages().send(sender, "command.no-permission");
            return true;
        }
        if (args.length < 2) {
            for (String line : plugin.messages().stringList("command.admin-help")) {
                sender.sendMessage(Text.mm(line));
            }
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "reload" -> {
                plugin.reloadEverything();
                plugin.messages().send(sender, "command.reloaded");
            }
            case "event" -> {
                if (args.length < 3) {
                    plugin.messages().send(sender, "command.usage", "usage", "/lor admin event start|stop");
                    return true;
                }
                if (args[2].equalsIgnoreCase("start")) {
                    if (plugin.tide().start(true)) {
                        plugin.messages().send(sender, "command.event-started");
                    } else {
                        plugin.messages().send(sender, "command.event-failed");
                    }
                } else {
                    plugin.tide().stopByAdmin();
                    plugin.messages().send(sender, "command.event-stopped");
                }
            }
            case "give" -> {
                if (args.length < 3) {
                    plugin.messages().send(sender, "command.usage", "usage", "/lor admin give <id> [player] [amount]");
                    return true;
                }
                String id = args[2].toLowerCase(Locale.ROOT);
                if (!CustomItems.IDS.contains(id)) {
                    plugin.messages().send(sender, "command.invalid-item", "item", id);
                    return true;
                }
                Player target = args.length >= 4 ? Bukkit.getPlayerExact(args[3])
                        : sender instanceof Player player ? player : null;
                if (target == null) {
                    plugin.messages().send(sender, "command.player-not-found",
                            "player", args.length >= 4 ? args[3] : "?");
                    return true;
                }
                int amount = args.length >= 5 ? parseInt(args[4], 1) : 1;
                plugin.items().give(target, id, Math.max(1, Math.min(64, amount)));
                plugin.messages().send(sender, "command.given",
                        "item", plugin.messages().rawString("items." + id + ".short"),
                        "player", target.getName());
            }
            case "spawn" -> {
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "command.players-only");
                    return true;
                }
                if (args.length < 3) {
                    plugin.messages().send(sender, "command.usage", "usage", "/lor admin spawn <mob> [amount]");
                    return true;
                }
                CustomMob mob = CustomMob.byId(args[2]);
                if (mob == null) {
                    plugin.messages().send(sender, "command.invalid-mob", "mob", args[2]);
                    return true;
                }
                int amount = args.length >= 4 ? parseInt(args[3], 1) : 1;
                plugin.mobs().spawnAround(player, mob, Math.max(1, Math.min(20, amount)));
                plugin.messages().send(sender, "command.spawned", "mob", mob.id(), "amount", amount);
            }
            case "boss" -> {
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "command.players-only");
                    return true;
                }
                plugin.boss().start(player, player.getLocation());
            }
            case "echo" -> {
                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : null;
                if (target == null) {
                    plugin.messages().send(sender, "command.player-not-found", "player",
                            args.length >= 3 ? args[2] : "?");
                    return true;
                }
                long amount = args.length >= 4 ? parseInt(args[3], 0) : 0;
                plugin.profiles().get(target).addEcho(amount);
                plugin.messages().send(sender, "command.echo-set",
                        "player", target.getName(), "echo", plugin.profiles().get(target).echo());
            }
            case "corruption" -> {
                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : null;
                if (target == null) {
                    plugin.messages().send(sender, "command.player-not-found", "player",
                            args.length >= 3 ? args[2] : "?");
                    return true;
                }
                double amount = args.length >= 4 ? parseDouble(args[3], 0) : 0;
                plugin.profiles().get(target).addCorruption(amount);
                plugin.messages().send(sender, "command.corruption-set",
                        "player", target.getName(),
                        "percent", Math.round(plugin.profiles().get(target).corruption()));
            }
            case "chapter" -> {
                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : null;
                if (target == null || args.length < 4) {
                    plugin.messages().send(sender, "command.usage",
                            "usage", "/lor admin chapter <player> <1-" + plugin.quests().chapters().size() + ">");
                    return true;
                }
                int chapter = parseInt(args[3], 1);
                if (plugin.quests().setChapter(target, chapter)) {
                    plugin.messages().send(sender, "command.chapter-set",
                            "player", target.getName(), "chapter", QuestManager.roman(chapter));
                }
            }
            case "complete" -> {
                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : null;
                if (target == null || args.length < 4) {
                    plugin.messages().send(sender, "command.usage",
                            "usage", "/lor admin complete <player> <chapter>");
                    return true;
                }
                int chapter = parseInt(args[3], 1);
                if (plugin.quests().forceComplete(target, chapter)) {
                    plugin.messages().send(sender, "command.chapter-completed",
                            "player", target.getName(), "chapter", QuestManager.roman(chapter));
                }
            }
            case "reset" -> {
                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : null;
                if (target == null) {
                    plugin.messages().send(sender, "command.player-not-found", "player",
                            args.length >= 3 ? args[2] : "?");
                    return true;
                }
                plugin.profiles().get(target).resetAll();
                plugin.profiles().saveAsync();
                plugin.messages().send(sender, "command.reset", "player", target.getName());
            }
            default -> {
                for (String line : plugin.messages().stringList("command.admin-help")) {
                    sender.sendMessage(Text.mm(line));
                }
            }
        }
        return true;
    }

    private int parseInt(String raw, int def) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            return def;
        }
    }

    private double parseDouble(String raw, double def) {
        try {
            return Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException exception) {
            return def;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("crafts")) {
            return result;
        }
        if (args.length == 1) {
            for (String option : List.of("help", "menu", "quests", "codex", "relics", "recipes", "stats", "top", "pay", "ah", "logs", "ritual", "hud", "admin")) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(option);
                }
            }
            return result;
        }
        if (args[0].equalsIgnoreCase("pay")) {
            if (args.length == 2) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (sender instanceof Player player && player.getUniqueId().equals(online.getUniqueId())) {
                        continue;
                    }
                    result.add(online.getName());
                }
            } else if (args.length == 3) {
                result.addAll(List.of("10", "50", "100", "250", "500", "1000", "all"));
            }
            String current = args[args.length - 1].toLowerCase(Locale.ROOT);
            result.removeIf(value -> !value.toLowerCase(Locale.ROOT).startsWith(current));
            return result;
        }
        if (args[0].equalsIgnoreCase("logs")) {
            if (!sender.hasPermission("lor.logs")) {
                return result;
            }
            if (args.length == 2) {
                result.addAll(List.of("1", "2", "3"));
                Bukkit.getOnlinePlayers().forEach(player -> result.add(player.getName()));
            } else if (args.length == 3) {
                result.addAll(List.of("2", "3", "4", "5"));
            }
            String current = args[args.length - 1].toLowerCase(Locale.ROOT);
            result.removeIf(value -> !value.toLowerCase(Locale.ROOT).startsWith(current));
            return result;
        }
        if (args[0].equalsIgnoreCase("ah") || args[0].equalsIgnoreCase("auction")) {
            if (!sender.hasPermission("lor.auction")) {
                return result;
            }
            if (args.length == 2) {
                result.addAll(List.of("search", "sell", "my", "buy", "cancel", "help"));
            } else if (args.length >= 3 && args[1].equalsIgnoreCase("search")) {
                // Поиск лота идёт по вхождению подстроки, поэтому и подсказки фильтруем так же.
                return materialSuggestions(args[args.length - 1]);
            } else if (args.length == 3) {
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "sell" -> result.addAll(List.of("10", "50", "100", "250", "500", "1000"));
                    case "buy" -> plugin.auction().all().forEach(listing -> result.add(listing.shortId()));
                    case "cancel" -> {
                        if (sender instanceof Player player) {
                            plugin.auction().bySeller(player.getUniqueId())
                                    .forEach(listing -> result.add(listing.shortId()));
                        }
                    }
                    case "my" -> result.addAll(List.of("1", "2", "3"));
                    default -> {
                    }
                }
            }
            String current = args[args.length - 1].toLowerCase(Locale.ROOT);
            result.removeIf(value -> !value.toLowerCase(Locale.ROOT).startsWith(current));
            return result;
        }
        if (!args[0].equalsIgnoreCase("admin") || !sender.hasPermission("lor.admin")) {
            return result;
        }
        if (args.length == 2) {
            for (String option : List.of("reload", "event", "give", "spawn", "boss", "echo", "corruption", "chapter", "complete", "reset")) {
                if (option.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    result.add(option);
                }
            }
            return result;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 3) {
            switch (action) {
                case "event" -> {
                    result.add("start");
                    result.add("stop");
                }
                case "give" -> result.addAll(CustomItems.IDS);
                case "spawn" -> Arrays.stream(CustomMob.values())
                        .map(mob -> mob.id()).forEach(result::add);
                case "echo", "corruption", "chapter", "complete", "reset" ->
                        Bukkit.getOnlinePlayers().forEach(player -> result.add(player.getName()));
                default -> {
                }
            }
        } else if (args.length == 4) {
            switch (action) {
                case "give" -> Bukkit.getOnlinePlayers().forEach(player -> result.add(player.getName()));
                case "echo" -> result.addAll(List.of("50", "100", "250", "500", "1000"));
                case "corruption" -> result.addAll(List.of("10", "25", "50", "75", "100"));
                case "chapter", "complete" -> {
                    for (int id : plugin.quests().chapters().keySet()) {
                        result.add(String.valueOf(id));
                    }
                }
                default -> {
                }
            }
        } else if (args.length == 5 && action.equals("give")) {
            result.addAll(List.of("1", "8", "16", "32", "64"));
        }
        String current = args[args.length - 1].toLowerCase(Locale.ROOT);
        result.removeIf(value -> !value.toLowerCase(Locale.ROOT).startsWith(current));
        return result;
    }

    /**
     * Подсказки для /lor ah search: все блоки и предметы на английском и русском.
     * Сначала идут совпадения с начала названия, затем остальные — как в самом поиске.
     */
    private List<String> materialSuggestions(String rawNeedle) {
        String needle = ItemNames.normalize(rawNeedle == null ? "" : rawNeedle.trim());
        List<String> prefix = new ArrayList<>();
        List<String> contains = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.isLegacy() || (!material.isItem() && !material.isBlock())) {
                continue;
            }
            addSuggestion(material.name().toLowerCase(Locale.ROOT), needle, prefix, contains);
            addSuggestion(plugin.itemNames().russian(material), needle, prefix, contains);
        }
        // Кастомные предметы плагина ищутся по отображаемому имени — добавим их в подсказки.
        for (String id : CustomItems.IDS) {
            String key = "items." + id + ".name";
            if (plugin.messages().has(key)) {
                addSuggestion(Text.plain(plugin.messages().raw(key)), needle, prefix, contains);
            }
        }
        prefix.sort(String.CASE_INSENSITIVE_ORDER);
        contains.sort(String.CASE_INSENSITIVE_ORDER);
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>(prefix.size() + contains.size());
        for (String value : prefix) {
            if (seen.add(value)) {
                result.add(value);
            }
        }
        for (String value : contains) {
            if (seen.add(value)) {
                result.add(value);
            }
        }
        if (result.size() > SEARCH_SUGGESTION_LIMIT) {
            return new ArrayList<>(result.subList(0, SEARCH_SUGGESTION_LIMIT));
        }
        return result;
    }

    private void addSuggestion(String value, String needle, List<String> prefix, List<String> contains) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalized = ItemNames.normalize(value);
        if (needle.isEmpty() || normalized.startsWith(needle)) {
            prefix.add(value);
        } else if (ItemNames.matches(value, needle)) {
            contains.add(value);
        }
    }
}
