package com.wildlands.felling;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Настройки рубки деревьев (файл config/wildlands-common.toml). До загрузки конфига действуют значения по умолчанию.
 */
public final class FellingConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Включить падение деревьев при рубке ствола топором.")
            .define("felling.enabled", true);
    private static final ForgeConfigSpec.BooleanValue REQUIRE_AXE = BUILDER
            .comment("Дерево падает только если ствол срубили топором.")
            .define("felling.requireAxe", true);
    private static final ForgeConfigSpec.BooleanValue SNEAK_DISABLES = BUILDER
            .comment("Если игрок рубит присев (Shift), дерево не падает, ломается один блок как обычно.")
            .define("felling.sneakDisables", true);
    private static final ForgeConfigSpec.IntValue MAX_BLOCKS = BUILDER
            .comment("Самое большое дерево (бревна и листва вместе), которое может упасть.")
            .defineInRange("felling.maxBlocks", 9000, 200, 60000);
    private static final ForgeConfigSpec.IntValue MAX_WOOD = BUILDER
            .comment("Самая большая постройка из древесины, которую ещё считают деревом. Больше этого не падает.")
            .defineInRange("felling.maxWood", 3000, 100, 20000);
    private static final ForgeConfigSpec.DoubleValue SPEED = BUILDER
            .comment("Скорость падения: 1.0 как в жизни, меньше медленнее, больше быстрее.")
            .defineInRange("felling.speed", 1.0D, 0.3D, 3.0D);
    private static final ForgeConfigSpec.IntValue BLOCKS_PER_TICK = BUILDER
            .comment("Сколько блоков за тик можно менять во время падения (защита от просадки TPS).")
            .defineInRange("felling.blocksPerTick", 2500, 200, 20000);
    private static final ForgeConfigSpec.BooleanValue HURT = BUILDER
            .comment("Падающее дерево ранит мобов, которых задело стволом. Игроков не давит: дерево останавливается.")
            .define("felling.hurtMobs", true);
    private static final ForgeConfigSpec.IntValue TOOL_DAMAGE_CAP = BUILDER
            .comment("Сколько прочности топора максимум уходит дополнительно за падение дерева (зависит от размера).")
            .defineInRange("felling.toolDamageCap", 8, 0, 100);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private FellingConfig() {
    }

    public static boolean enabled() {
        return get(ENABLED, true);
    }

    public static boolean requireAxe() {
        return get(REQUIRE_AXE, true);
    }

    public static boolean sneakDisables() {
        return get(SNEAK_DISABLES, true);
    }

    public static int maxBlocks() {
        return get(MAX_BLOCKS, 9000);
    }

    public static int maxWood() {
        return get(MAX_WOOD, 3000);
    }

    public static double speed() {
        return get(SPEED, 1.0D);
    }

    public static int blocksPerTick() {
        return get(BLOCKS_PER_TICK, 2500);
    }

    public static boolean hurtMobs() {
        return get(HURT, true);
    }

    public static int toolDamageCap() {
        return get(TOOL_DAMAGE_CAP, 8);
    }

    /** Чтение с запасным значением: до загрузки конфига get() бросает исключение. */
    private static <T> T get(ForgeConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            return value.get();
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
