package com.nightwielder.apothicstaffrarities.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import net.minecraftforge.fml.loading.FMLPaths;

import com.nightwielder.apothicstaffrarities.ApothicStaffRarities;

public final class ApothicStaffRaritiesConfig {
    private static final String FILE_NAME = "apothic_staff_rarities-common.toml";

    public static final String CATEGORY_AUTOCAST = "autocast";
    public static final String CATEGORY_SPELL = "spell";
    public static final String CATEGORY_MOB_EFFECT = "mob_effect";
    public static final String CATEGORY_CONCENTRATION = "concentration";
    public static final String CATEGORY_COOLDOWN_RESET = "cooldown_reset";
    public static final String CATEGORY_MANA_SHIELD = "mana_shield";

    private static final List<String> DISABLE_CATEGORIES = List.of(
            CATEGORY_AUTOCAST,
            CATEGORY_MOB_EFFECT,
            CATEGORY_SPELL,
            CATEGORY_CONCENTRATION,
            CATEGORY_COOLDOWN_RESET,
            CATEGORY_MANA_SHIELD);

    public static final String RARITY_HEIRLOOM = "heirloom";
    public static final String RARITY_ARTIFACT = "artifact";
    public static final String RARITY_ESOTERIC = "esoteric";
    private static final List<String> AA_RARITIES = List.of(RARITY_HEIRLOOM, RARITY_ARTIFACT, RARITY_ESOTERIC);

    public static final String FIELD_LEVEL_MIN = "level_min";
    public static final String FIELD_LEVEL_MAX = "level_max";
    public static final String FIELD_COOLDOWN = "cooldown";
    public static final String FIELD_DURATION_MIN = "duration_min";
    public static final String FIELD_DURATION_STEPS = "duration_steps";
    public static final String FIELD_DURATION_STEP = "duration_step";
    public static final String FIELD_AMPLIFIER = "amplifier";
    public static final String FIELD_AMPLIFIER_STEPS = "amplifier_steps";
    public static final String FIELD_AMPLIFIER_STEP = "amplifier_step";
    public static final String FIELD_ENABLED = "enabled";
    public static final String FIELD_MIN = "min";
    public static final String FIELD_STEPS = "steps";
    public static final String FIELD_STEP = "step";

    public static final double MULTIPLIER_FLOOR = 0.01;
    public static final double MULTIPLIER_CEILING = 100.0;

    private static final double LEGACY_UNSET = -1.0;

    public record AffixDescriptor(String name, String category, boolean amplifierIsObject, boolean hasCooldown) {
        public boolean isMisc() {
            return CATEGORY_CONCENTRATION.equals(category)
                    || CATEGORY_COOLDOWN_RESET.equals(category)
                    || CATEGORY_MANA_SHIELD.equals(category);
        }
    }

    private static final List<AffixDescriptor> ALL_AFFIXES = buildAffixCatalog();

    private static double heirloomMultiplier = 1.0;
    private static double artifactMultiplier = 1.0;
    private static double esotericMultiplier = 1.0;
    private static final Map<String, Boolean> disabledCategories = new LinkedHashMap<>();
    private static final Map<String, Map<String, Object>> overrideTable = new LinkedHashMap<>();

    private ApothicStaffRaritiesConfig() {}

    public static Optional<AffixDescriptor> findAffix(final String category, final String name) {
        for (final AffixDescriptor descriptor : ALL_AFFIXES) {
            if (descriptor.category().equals(category) && descriptor.name().equals(name)) {
                return Optional.of(descriptor);
            }
        }
        return Optional.empty();
    }

    public static double multiplierFor(final String rarityName) {
        return switch (rarityName) {
            case RARITY_HEIRLOOM -> heirloomMultiplier;
            case RARITY_ARTIFACT -> artifactMultiplier;
            case RARITY_ESOTERIC -> esotericMultiplier;
            default -> 1.0;
        };
    }

    public static boolean isCategoryDisabled(final String category) {
        return Boolean.TRUE.equals(disabledCategories.get(category));
    }

    public static List<String> getDisabledCategories() {
        final List<String> result = new ArrayList<>();
        for (final Map.Entry<String, Boolean> entry : disabledCategories.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) result.add(entry.getKey());
        }
        return result;
    }

    public static String signature() {
        final StringBuilder builder = new StringBuilder();
        builder.append(heirloomMultiplier).append(',')
                .append(artifactMultiplier).append(',')
                .append(esotericMultiplier).append(';')
                .append(getDisabledCategories()).append(';')
                .append(overrideTable);
        return builder.toString();
    }

    public static OptionalInt overrideInt(final String category, final String affixName, final String rarity, final String field) {
        final Object raw = lookupOverride(category, affixName, rarity, field);
        return raw instanceof Number n ? OptionalInt.of(n.intValue()) : OptionalInt.empty();
    }

    public static OptionalDouble overrideDouble(final String category, final String affixName, final String rarity, final String field) {
        final Object raw = lookupOverride(category, affixName, rarity, field);
        return raw instanceof Number n ? OptionalDouble.of(n.doubleValue()) : OptionalDouble.empty();
    }

    public static boolean overrideBool(final String category, final String affixName, final String rarity, final String field, final boolean fallback) {
        final Object raw = lookupOverride(category, affixName, rarity, field);
        return raw instanceof Boolean b ? b : fallback;
    }

    public static void load() {
        final Path path = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        ensureDefaultFile(path);
        try (final CommentedFileConfig config = CommentedFileConfig.builder(path).sync().build()) {
            try {
                config.load();
            } catch (final ParsingException e) {
                // NightConfig throws this on a trailing-EOF read of an otherwise valid file; the parsed data is still usable.
                if (e.getMessage() != null && e.getMessage().contains("Not enough data available")) {
                    ApothicStaffRarities.LOGGER.debug("Tolerating trailing-EOF parse hiccup in {}: {}", FILE_NAME, e.getMessage());
                } else {
                    throw e;
                }
            }
            readScalingSection(config);
            readDisableSection(config);
            readOverridesSection(config);
        } catch (final Exception e) {
            ApothicStaffRarities.LOGGER.error("Failed to read {}", FILE_NAME, e);
        }
    }

    private static Object lookupOverride(final String category, final String affixName, final String rarity, final String field) {
        final Map<String, Object> fields = overrideTable.get(joinKey(category, affixName, rarity));
        return fields == null ? null : fields.get(field);
    }

    private static String joinKey(final String category, final String affixName, final String rarity) {
        return category + "/" + affixName + "/" + rarity;
    }

    private static void readScalingSection(final CommentedFileConfig config) {
        heirloomMultiplier = clampMultiplier(readDouble(config, "scaling.heirloom_multiplier", 1.0));
        artifactMultiplier = clampMultiplier(readDouble(config, "scaling.artifact_multiplier", 1.0));
        esotericMultiplier = clampMultiplier(readDouble(config, "scaling.esoteric_multiplier", 1.0));
    }

    private static double clampMultiplier(final double value) {
        return Math.min(MULTIPLIER_CEILING, Math.max(MULTIPLIER_FLOOR, value));
    }

    private static void readDisableSection(final CommentedFileConfig config) {
        disabledCategories.clear();
        for (final String category : DISABLE_CATEGORIES) {
            disabledCategories.put(category, readBool(config, "disable." + category, false));
        }
    }

    private static void readOverridesSection(final CommentedFileConfig config) {
        overrideTable.clear();
        for (final AffixDescriptor descriptor : ALL_AFFIXES) {
            for (final String rarity : AA_RARITIES) {
                final String sectionPath = "overrides." + ConfigDefaults.tomlAffixPath(descriptor) + "." + rarity;
                final Object raw = config.get(sectionPath);
                if (!(raw instanceof UnmodifiableConfig section)) continue;
                final Map<String, Object> fields = new LinkedHashMap<>();
                for (final UnmodifiableConfig.Entry entry : section.entrySet()) {
                    if (isUnsetValue(entry.getKey(), entry.getValue())) continue;
                    fields.put(entry.getKey(), entry.getValue());
                }
                if (!fields.isEmpty()) {
                    overrideTable.put(joinKey(descriptor.category(), descriptor.name(), rarity), fields);
                }
            }
        }
    }

    private static boolean isUnsetValue(final String field, final Object value) {
        if (FIELD_ENABLED.equals(field)) {
            return Boolean.TRUE.equals(value);
        }
        return value instanceof Number n && n.doubleValue() == LEGACY_UNSET;
    }

    private static double readDouble(final CommentedFileConfig config, final String path, final double fallback) {
        final Object raw = config.get(path);
        return raw instanceof Number n ? n.doubleValue() : fallback;
    }

    private static boolean readBool(final CommentedFileConfig config, final String path, final boolean fallback) {
        final Object raw = config.get(path);
        return raw instanceof Boolean b ? b : fallback;
    }

    private static void ensureDefaultFile(final Path path) {
        if (Files.exists(path)) return;
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, ConfigDefaults.build(ALL_AFFIXES, DISABLE_CATEGORIES, AA_RARITIES));
        } catch (final IOException e) {
            ApothicStaffRarities.LOGGER.error("Failed to create default {}", FILE_NAME, e);
        }
    }

    private static List<AffixDescriptor> buildAffixCatalog() {
        final List<AffixDescriptor> list = new ArrayList<>();
        for (final String name : List.of(
                "acupuncture", "arrow_volley", "burning_dash", "ice_spikes",
                "shadow_slash", "sonic_boom", "stomp", "sunbeam", "volt_strike")) {
            list.add(new AffixDescriptor(name, CATEGORY_AUTOCAST, false, true));
        }
        for (final String name : List.of("bastion", "hemospike", "radiant", "stormlash")) {
            list.add(new AffixDescriptor(name, CATEGORY_SPELL, false, true));
        }
        addMobEffect(list, "acidic", false, true);
        addMobEffect(list, "bloodletting", true, true);
        addMobEffect(list, "bolstering", true, true);
        addMobEffect(list, "bursting", true, true);
        addMobEffect(list, "elusive", true, true);
        addMobEffect(list, "ensnaring", false, true);
        addMobEffect(list, "grievous", false, true);
        addMobEffect(list, "ivy_laced", false, true);
        addMobEffect(list, "revitalizing", true, true);
        addMobEffect(list, "satanic", false, true);
        addMobEffect(list, "sophisticated", true, true);
        addMobEffect(list, "swift", true, true);
        addMobEffect(list, "weakening", false, true);
        addMobEffect(list, "withering", false, false);
        list.add(new AffixDescriptor(CATEGORY_CONCENTRATION, CATEGORY_CONCENTRATION, false, false));
        list.add(new AffixDescriptor(CATEGORY_COOLDOWN_RESET, CATEGORY_COOLDOWN_RESET, false, false));
        list.add(new AffixDescriptor(CATEGORY_MANA_SHIELD, CATEGORY_MANA_SHIELD, false, false));
        return List.copyOf(list);
    }

    private static void addMobEffect(final List<AffixDescriptor> list, final String name, final boolean amplifierIsObject, final boolean hasCooldown) {
        list.add(new AffixDescriptor(name, CATEGORY_MOB_EFFECT, amplifierIsObject, hasCooldown));
    }
}
