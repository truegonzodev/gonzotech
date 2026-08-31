package com.gonzotech.core.ore;

import net.minecraft.util.valueproviders.UniformInt;

import java.util.List;

/**
 * Полная классификация рудных блоков (см. addendum: таблица высот/жил/host-вариантов).
 * <p>
 * Единственный источник правды для регистрации блоков/предметов
 * ({@link com.gonzotech.core.registry.ModBlocks}, {@link com.gonzotech.core.registry.ModItems})
 * и для генератора ассетов/датапака ({@code tools/generate_ore_content.py}) —
 * держите оба места синхронными, пока не подключён нормальный datagen.
 *
 * @param id           id металла, напр. "aluminum"
 * @param minY         нижняя граница генерации (оверворлд)
 * @param maxY         верхняя граница генерации (оверворлд)
 * @param bestY        Y с пиковой плотностью
 * @param veinSize     максимальный размер жилы (баланс-параметр, независим от toolTier)
 * @param toolTier     минимальный тир кирки — задаётся явно, НЕ выводится из veinSize
 *                     (раньше выводился автоматически, но это ломало тир при
 *                     балансных правках veinSize — см. addendum по фиксу v0.5)
 * @param avgPerChunk  среднее число блоков руды на чанк (все host-варианты суммарно)
 * @param baseHardness твёрдость STONE/CALCITE-варианта; DEEPSLATE = +1.5, NETHER = ×0.6
 * @param hosts        в каких вмещающих породах встречается руда
 * @param selfDrop     true — блок дропает сам себя; false — дропает raw_<id> (заглушка)
 * @param xpMin        минимум опыта за добычу киркой (включая конечную границу)
 * @param xpMax        максимум опыта за добычу киркой (включая конечную границу);
 *                     0/0 — руда опыта не даёт. Опыт реализован по-ванильному:
 *                     блок регистрируется как DropExperienceBlock, а шёлковое
 *                     касание гасит его своим эффектом block_experience (set 0)
 *                     в самом ванильном датапаке, т.е. без XP и с самим блоком.
 */
public record OreDefinition(
    String id,
    int minY,
    int maxY,
    int bestY,
    int veinSize,
    ToolTier toolTier,
    float avgPerChunk,
    float baseHardness,
    List<Host> hosts,
    boolean selfDrop,
    int xpMin,
    int xpMax
) {

    /** Вариант вмещающей породы — каждый является отдельным зарегистрированным блоком. */
    public enum Host {
        STONE(""),
        DEEPSLATE("deepslate_"),
        NETHER("nether_"),
        // Не размещается через configured_feature/placed_feature: это не жила, а шанс
        // 10%, что уже сгенерированный кальцитовый блок геоды станет рудой (только кальций).
        // Блок/лут/тег регистрируются, но автоворлдгена для него пока нет — нужен
        // отдельный Feature/mixin поверх геод.
        CALCITE("calcite_");

        public final String prefix;

        Host(String prefix) {
            this.prefix = prefix;
        }
    }

    /** Минимальный тир кирки — теперь просто данные (см. параметр {@link #toolTier}). */
    public enum ToolTier {
        STONE, IRON, DIAMOND
    }

    /** Твёрдость блока для конкретного host-варианта. */
    public float hardness(Host host) {
        return switch (host) {
            case STONE, CALCITE -> baseHardness;
            case DEEPSLATE -> baseHardness + 1.5f;
            case NETHER -> Math.round(baseHardness * 0.6f * 10f) / 10f;
        };
    }

    /** Сопротивление взрыву — общее для всех host-вариантов (как у ванильных deepslate-руд). */
    public float resistance() {
        return baseHardness;
    }

    /** id блока для конкретного host-варианта, напр. "deepslate_aluminum_ore". */
    public String blockId(Host host) {
        return host.prefix + id + "_ore";
    }

    /**
     * id "заглушки"-предмета для руд с selfDrop() == false (сера/марганец/йод/ртуть):
     * raw_<id>, единый для всех host-вариантов такой руды (принцип лазурита —
     * все host-варианты используют один и тот же raw_-предмет в своих loot table).
     * Для selfDrop() == true эта строка не используется: там каждый host-вариант
     * дропает сам себя — см. per-host loot table в data/gonzotech/loot_table/blocks/
     * и регистрацию в {@link com.gonzotech.core.registry.ModBlocks}.
     */
    public String dropItemId() {
        return selfDrop ? blockId(hosts.get(0)) : "raw_" + id;
    }

    /** true — руда даёт опыт при добыче киркой (только 4 raw-drop руды, см. {@link #xpMin}). */
    public boolean dropsExperience() {
        return xpMax > 0;
    }

    /**
     * Диапазон опыта за добычу киркой; использовать только если {@link #dropsExperience()}
     * == true (у остальных руд 0-0, но и там вызывать безопасно).
     */
    public UniformInt experience() {
        return UniformInt.of(xpMin, xpMax);
    }

    public static final List<OreDefinition> ALL = List.of(
        new OreDefinition("calcium", 0, 256, 120, 24, ToolTier.STONE, 140.0f, 5.0f, List.of(Host.STONE, Host.DEEPSLATE, Host.CALCITE), true, 0, 0),
        new OreDefinition("aluminum", 10, 160, 80, 32, ToolTier.STONE, 90.0f, 6.0f, List.of(Host.STONE), true, 0, 0),
        new OreDefinition("magnesium", -30, 120, 40, 20, ToolTier.STONE, 50.0f, 7.0f, List.of(Host.STONE, Host.DEEPSLATE), true, 0, 0),
        new OreDefinition("sulfur", -16, 120, 90, 13, ToolTier.STONE, 40.0f, 8.5f, List.of(Host.STONE, Host.NETHER), false, 0, 2),
        new OreDefinition("manganese", -40, 80, 20, 12, ToolTier.STONE, 35.0f, 9.5f, List.of(Host.STONE, Host.DEEPSLATE), false, 0, 2),
        new OreDefinition("titanium", -64, 60, -20, 12, ToolTier.STONE, 25.0f, 10.5f, List.of(Host.STONE, Host.DEEPSLATE), true, 0, 0),
        new OreDefinition("barium", -20, 90, 30, 14, ToolTier.STONE, 20.0f, 11.5f, List.of(Host.STONE, Host.DEEPSLATE), true, 0, 0),
        new OreDefinition("zinc", -60, 40, -10, 12, ToolTier.STONE, 18.0f, 13.0f, List.of(Host.STONE, Host.DEEPSLATE), true, 0, 0),
        new OreDefinition("tin", -10, 140, 60, 14, ToolTier.STONE, 15.0f, 14.0f, List.of(Host.STONE, Host.DEEPSLATE), true, 0, 0),
        new OreDefinition("boron", 20, 180, 100, 10, ToolTier.STONE, 12.0f, 15.0f, List.of(Host.STONE), true, 0, 0),
        new OreDefinition("chromium", -64, 0, -45, 8, ToolTier.IRON, 8.5f, 10.0f, List.of(Host.DEEPSLATE), true, 0, 0),
        new OreDefinition("nickel", -64, 20, -35, 8, ToolTier.IRON, 7.0f, 13.0f, List.of(Host.DEEPSLATE, Host.NETHER, Host.STONE), true, 0, 0),
        new OreDefinition("cobalt", -64, -10, -40, 6, ToolTier.IRON, 5.5f, 15.5f, List.of(Host.DEEPSLATE, Host.NETHER), true, 0, 0),
        new OreDefinition("silver", -64, 30, -20, 8, ToolTier.IRON, 4.5f, 18.5f, List.of(Host.STONE, Host.DEEPSLATE), true, 0, 0),
        new OreDefinition("iodine", -40, 60, 10, 4, ToolTier.IRON, 3.0f, 21.5f, List.of(Host.STONE, Host.DEEPSLATE, Host.NETHER), false, 1, 5),
        new OreDefinition("tungsten", -64, -20, -50, 4, ToolTier.IRON, 2.5f, 24.5f, List.of(Host.DEEPSLATE), true, 0, 0),
        new OreDefinition("mercury", -50, 30, -15, 4, ToolTier.IRON, 2.2f, 27.0f, List.of(Host.STONE, Host.DEEPSLATE), false, 2, 5),
        new OreDefinition("uranium", -64, -10, -48, 5, ToolTier.DIAMOND, 1.8f, 20.0f, List.of(Host.DEEPSLATE), true, 0, 0),
        new OreDefinition("zirconium", -64, 80, -30, 4, ToolTier.IRON, 1.5f, 30.0f, List.of(Host.STONE, Host.DEEPSLATE), true, 0, 0),
        new OreDefinition("thorium", -64, 20, -40, 10, ToolTier.DIAMOND, 1.1f, 25.5f, List.of(Host.DEEPSLATE, Host.NETHER, Host.STONE), true, 0, 0),
        new OreDefinition("platinum", -64, -30, -55, 3, ToolTier.DIAMOND, 0.8f, 31.5f, List.of(Host.DEEPSLATE), true, 0, 0),
        new OreDefinition("tellurium", -64, -20, -45, 2, ToolTier.DIAMOND, 0.5f, 37.0f, List.of(Host.DEEPSLATE), true, 0, 0),
        new OreDefinition("palladium", -64, -40, -58, 2, ToolTier.DIAMOND, 0.3f, 43.0f, List.of(Host.DEEPSLATE), true, 0, 0),
        new OreDefinition("cesium", -20, 90, 40, 2, ToolTier.DIAMOND, 0.2f, 48.5f, List.of(Host.STONE, Host.DEEPSLATE), true, 0, 0),
        new OreDefinition("iridium", -63, -45, -55, 1, ToolTier.DIAMOND, 0.05f, 54.5f, List.of(Host.DEEPSLATE), true, 0, 0),
        new OreDefinition("osmium", -63, -52, -59, 1, ToolTier.DIAMOND, 0.03f, 60.0f, List.of(Host.DEEPSLATE), true, 0, 0)
    );

    static {
        assert ALL.size() == 26 : "ожидалось 26 руд, получили " + ALL.size();
    }
}
