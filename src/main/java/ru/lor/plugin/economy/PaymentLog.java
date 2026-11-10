package ru.lor.plugin.economy;

import org.bukkit.entity.Player;
import ru.lor.plugin.LorPlugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Журнал переводов эха: data/payments.log и последние записи в памяти для /lor logs.
 * Доступ к чтению — право lor.logs, оно не требует lor.admin.
 */
public final class PaymentLog {

    /** Одна запись журнала. */
    public record Entry(long time, String fromName, UUID fromId, String toName, UUID toId,
                        long amount, long tax, long received) {

        static Entry parse(String line) {
            String[] parts = line.split("\t", -1);
            if (parts.length < 8) {
                return null;
            }
            try {
                return new Entry(
                        Long.parseLong(parts[0]),
                        parts[1],
                        UUID.fromString(parts[2]),
                        parts[3],
                        UUID.fromString(parts[4]),
                        Long.parseLong(parts[5]),
                        Long.parseLong(parts[6]),
                        Long.parseLong(parts[7]));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }

        String toLine() {
            return time + "\t" + clean(fromName) + "\t" + fromId + "\t" + clean(toName) + "\t" + toId
                    + "\t" + amount + "\t" + tax + "\t" + received;
        }

        private static String clean(String value) {
            return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
        }
    }

    private static final long MAX_FILE_BYTES = 5L * 1024L * 1024L;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private final LorPlugin plugin;
    private final File file;
    private final Deque<Entry> entries = new ArrayDeque<>();

    public PaymentLog(LorPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/payments.log");
        load();
    }

    public void load() {
        synchronized (entries) {
            entries.clear();
        }
        if (!plugin.config().paymentLogsEnabled() || !file.exists()) {
            return;
        }
        int keep = plugin.config().paymentLogsKeep();
        synchronized (entries) {
            try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Entry entry = Entry.parse(line);
                    if (entry == null) {
                        continue;
                    }
                    entries.addLast(entry);
                    while (entries.size() > keep) {
                        entries.removeFirst();
                    }
                }
            } catch (IOException exception) {
                plugin.getLogger().warning("Не удалось прочитать payments.log: " + exception.getMessage());
            }
        }
    }

    /** Записывает перевод в память и в файл. */
    public void record(Player from, Player to, long amount, long tax) {
        if (!plugin.config().paymentLogsEnabled()) {
            return;
        }
        Entry entry = new Entry(System.currentTimeMillis(),
                from.getName(), from.getUniqueId(),
                to.getName(), to.getUniqueId(),
                amount, tax, amount - tax);
        int keep = plugin.config().paymentLogsKeep();
        synchronized (entries) {
            entries.addLast(entry);
            while (entries.size() > keep) {
                entries.removeFirst();
            }
        }
        append(entry);
    }

    private synchronized void append(Entry entry) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Не удалось создать папку data/ для payments.log");
            return;
        }
        rotateIfNeeded();
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(entry.toLine());
            writer.newLine();
        } catch (IOException exception) {
            plugin.getLogger().warning("Не удалось записать payments.log: " + exception.getMessage());
        }
    }

    private void rotateIfNeeded() {
        if (!file.exists() || file.length() < MAX_FILE_BYTES) {
            return;
        }
        File old = new File(file.getParentFile(), "payments.log.old");
        if (old.exists() && !old.delete()) {
            return;
        }
        if (!file.renameTo(old)) {
            plugin.getLogger().warning("Не удалось ротировать payments.log");
        }
    }

    /** Записи, новые сверху. name == null — все игроки. */
    public List<Entry> query(String name) {
        List<Entry> result = new ArrayList<>();
        synchronized (entries) {
            for (Entry entry : entries) {
                if (name == null
                        || entry.fromName().equalsIgnoreCase(name)
                        || entry.toName().equalsIgnoreCase(name)) {
                    result.add(entry);
                }
            }
        }
        Collections.reverse(result);
        return result;
    }

    public int size() {
        synchronized (entries) {
            return entries.size();
        }
    }

    public static String formatTime(long epochMillis) {
        return TIME.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }
}
