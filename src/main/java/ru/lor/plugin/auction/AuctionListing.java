package ru.lor.plugin.auction;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** Один лот на аукционе. */
public final class AuctionListing {

    private final UUID id;
    private final UUID sellerId;
    private final String sellerName;
    private final ItemStack item;
    private final long price;
    private final long createdAt;

    public AuctionListing(UUID id, UUID sellerId, String sellerName, ItemStack item, long price, long createdAt) {
        this.id = id;
        this.sellerId = sellerId;
        this.sellerName = sellerName == null ? "" : sellerName;
        this.item = item == null ? null : item.clone();
        this.price = price;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public UUID sellerId() {
        return sellerId;
    }

    public String sellerName() {
        return sellerName;
    }

    /** Копия предмета: оригинал лота менять нельзя. */
    public ItemStack item() {
        return item == null ? null : item.clone();
    }

    public long price() {
        return price;
    }

    public long createdAt() {
        return createdAt;
    }

    public String shortId() {
        return id.toString().substring(0, 8);
    }
}
