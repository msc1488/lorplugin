package ru.lor.plugin.mechanics;

import org.bukkit.entity.EntityType;

import java.util.Locale;
import java.util.Random;

/** Definition of the sculk-touched mobs. */
public enum CustomMob {

    CRAWLER("crawler", EntityType.ZOMBIE, 24.0, 0.34, 4.0),
    SENTINEL("sentinel", EntityType.HUSK, 46.0, 0.26, 6.0),
    DEVOURER("devourer", EntityType.SPIDER, 30.0, 0.36, 5.0),
    HERALD("herald", EntityType.VEX, 26.0, 0.32, 5.0);

    private final String id;
    private final EntityType entityType;
    private final double health;
    private final double speed;
    private final double damage;

    CustomMob(String id, EntityType entityType, double health, double speed, double damage) {
        this.id = id;
        this.entityType = entityType;
        this.health = health;
        this.speed = speed;
        this.damage = damage;
    }

    public String id() {
        return id;
    }

    public EntityType entityType() {
        return entityType;
    }

    public double health() {
        return health;
    }

    public double speed() {
        return speed;
    }

    public double damage() {
        return damage;
    }

    public static CustomMob byId(String id) {
        if (id == null) {
            return null;
        }
        for (CustomMob mob : values()) {
            if (mob.id.equals(id.toLowerCase(Locale.ROOT))) {
                return mob;
            }
        }
        return null;
    }

    public static CustomMob random(Random random) {
        CustomMob[] values = values();
        return values[random.nextInt(values.length)];
    }
}
