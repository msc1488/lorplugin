package ru.lor.plugin.economy;

import org.bukkit.entity.Player;
import ru.lor.plugin.LorPlugin;
import ru.lor.plugin.data.Profile;

/**
 * Переводы эха между игроками (/lor pay).
 * Здесь живут все проверки и списание, команда только показывает сообщения.
 */
public final class PaymentManager {

    /** Результат попытки перевода. */
    public enum Result {
        SUCCESS,
        DISABLED,
        SELF,
        INVALID_AMOUNT,
        TOO_SMALL,
        TOO_LARGE,
        NOT_ENOUGH,
        COOLDOWN
    }

    private static final String COOLDOWN_KEY = "pay";

    private final LorPlugin plugin;

    public PaymentManager(LorPlugin plugin) {
        this.plugin = plugin;
    }

    public Result pay(Player sender, Player target, long amount) {
        if (!plugin.config().paymentsEnabled()) {
            return Result.DISABLED;
        }
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            return Result.SELF;
        }
        if (amount <= 0L) {
            return Result.INVALID_AMOUNT;
        }
        if (amount < plugin.config().paymentMin()) {
            return Result.TOO_SMALL;
        }
        int max = plugin.config().paymentMax();
        if (max > 0 && amount > max) {
            return Result.TOO_LARGE;
        }
        if (plugin.cooldowns().remaining(sender.getUniqueId(), COOLDOWN_KEY) > 0L) {
            return Result.COOLDOWN;
        }

        Profile from = plugin.profiles().get(sender);
        if (from.echo() < amount) {
            return Result.NOT_ENOUGH;
        }
        Profile to = plugin.profiles().get(target);
        long tax = tax(amount);
        long received = amount - tax;

        if (!from.spendEcho(amount)) {
            return Result.NOT_ENOUGH;
        }
        to.addEcho(received);

        int cooldown = plugin.config().paymentCooldownSeconds();
        if (cooldown > 0) {
            plugin.cooldowns().set(sender.getUniqueId(), COOLDOWN_KEY, cooldown * 1000L);
        }
        plugin.profiles().saveAsync();
        plugin.paymentLogs().record(sender, target, amount, tax);
        plugin.getLogger().info("Перевод эха: " + sender.getName() + " -> " + target.getName()
                + ", сумма " + amount + (tax > 0L ? ", комиссия " + tax : ""));
        return Result.SUCCESS;
    }

    /** Комиссия сервера с суммы перевода. Никогда не забирает весь перевод целиком. */
    public long tax(long amount) {
        double percent = plugin.config().paymentTaxPercent();
        if (percent <= 0.0 || amount <= 0L) {
            return 0L;
        }
        long value = (long) Math.floor(amount * percent / 100.0);
        return Math.min(value, amount - 1L);
    }

    public long received(long amount) {
        return amount - tax(amount);
    }

    public long cooldownSeconds(Player player) {
        return plugin.cooldowns().remainingSeconds(player.getUniqueId(), COOLDOWN_KEY);
    }
}
