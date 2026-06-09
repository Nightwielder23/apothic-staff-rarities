package com.nightwielder.apothicstaffrarities.config;

import java.util.List;

import com.nightwielder.apothicstaffrarities.config.ApothicStaffRaritiesConfig.AffixDescriptor;

import static com.nightwielder.apothicstaffrarities.config.ApothicStaffRaritiesConfig.CATEGORY_AUTOCAST;
import static com.nightwielder.apothicstaffrarities.config.ApothicStaffRaritiesConfig.CATEGORY_SPELL;
import static com.nightwielder.apothicstaffrarities.config.ApothicStaffRaritiesConfig.CATEGORY_MOB_EFFECT;
import static com.nightwielder.apothicstaffrarities.config.ApothicStaffRaritiesConfig.CATEGORY_CONCENTRATION;
import static com.nightwielder.apothicstaffrarities.config.ApothicStaffRaritiesConfig.CATEGORY_COOLDOWN_RESET;
import static com.nightwielder.apothicstaffrarities.config.ApothicStaffRaritiesConfig.CATEGORY_MANA_SHIELD;
import static com.nightwielder.apothicstaffrarities.config.ApothicStaffRaritiesConfig.FIELD_LEVEL_MIN;
import static com.nightwielder.apothicstaffrarities.config.ApothicStaffRaritiesConfig.FIELD_LEVEL_MAX;
import static com.nightwielder.apothicstaffrarities.config.ApothicStaffRaritiesConfig.FIELD_COOLDOWN;
import static com.nightwielder.apothicstaffrarities.config.ApothicStaffRaritiesConfig.FIELD_DURATION_MIN;
import static com.nightwielder.apothicstaffrarities.config.ApothicStaffRaritiesConfig.FIELD_DURATION_STEPS;
import static com.nightwielder.apothicstaffrarities.config.ApothicStaffRaritiesConfig.FIELD_DURATION_STEP;
import static com.nightwielder.apothicstaffrarities.config.ApothicStaffRaritiesConfig.FIELD_AMPLIFIER;
import static com.nightwielder.apothicstaffrarities.config.ApothicStaffRaritiesConfig.FIELD_AMPLIFIER_STEPS;
import static com.nightwielder.apothicstaffrarities.config.ApothicStaffRaritiesConfig.FIELD_AMPLIFIER_STEP;
import static com.nightwielder.apothicstaffrarities.config.ApothicStaffRaritiesConfig.FIELD_ENABLED;
import static com.nightwielder.apothicstaffrarities.config.ApothicStaffRaritiesConfig.FIELD_MIN;
import static com.nightwielder.apothicstaffrarities.config.ApothicStaffRaritiesConfig.FIELD_STEPS;
import static com.nightwielder.apothicstaffrarities.config.ApothicStaffRaritiesConfig.FIELD_STEP;

final class ConfigDefaults {

    private ConfigDefaults() {}

    static String build(final List<AffixDescriptor> affixes, final List<String> disableCategories, final List<String> rarities) {
        final StringBuilder builder = new StringBuilder();
        appendHeaderComment(builder);
        appendScalingDefaults(builder);
        appendDisableDefaults(builder, disableCategories);
        appendOverrideDefaults(builder, affixes, rarities);
        return builder.toString();
    }

    static String tomlAffixPath(final AffixDescriptor descriptor) {
        return descriptor.isMisc()
                ? "misc." + descriptor.name()
                : descriptor.category() + "." + descriptor.name();
    }

    private static void appendHeaderComment(final StringBuilder builder) {
        builder.append("# Apothic Staff Rarities tuning for the staff affixes added at the\n");
        builder.append("# Apotheotic Additions tiers (heirloom, artifact, esoteric).\n");
        builder.append("#\n");
        builder.append("# The config has two tiers; combine them as needed.\n");
        builder.append("#\n");
        builder.append("# Tier 1 ([scaling]): one multiplier per rarity, clamped to the range\n");
        builder.append("# 0.01 to 100.0 on read. What a multiplier scales depends on the value:\n");
        builder.append("#   - Autocast and spell affixes: the level range (min and max) and the\n");
        builder.append("#     cooldown, rounded to whole numbers.\n");
        builder.append("#   - Step-function values (mob effect durations, mob effect amplifiers\n");
        builder.append("#     written as a min/steps/step block, the cooldown_reset chance, the\n");
        builder.append("#     mana_shield chance): the per-level step term, so the level-dependent\n");
        builder.append("#     part scales in proportion to the multiplier. The base floor and the\n");
        builder.append("#     step count stay as written in the JSON.\n");
        builder.append("#   - Integer mob effect amplifiers (a bare number rather than a block):\n");
        builder.append("#     the amplifier, rounded to the nearest whole level, so a multiplier\n");
        builder.append("#     close to 1.0 may leave it unchanged.\n");
        builder.append("#   - Concentration is a yes/no flag, so the multiplier does not affect it.\n");
        builder.append("# Set a multiplier to 1.0 to keep the default values for that rarity.\n");
        builder.append("#\n");
        builder.append("# Tier 2 ([overrides]): per-affix, per-rarity, per-field absolute values.\n");
        builder.append("# Leave a field out, or set it to -1 (or -1.0 for floats), to keep the\n");
        builder.append("# default value scaled by the tier 1 multiplier. Any other value replaces\n");
        builder.append("# that field outright, and the multiplier no longer affects it.\n");
        builder.append("#\n");
        builder.append("# [disable]: skip an entire affix category at the AA tiers. Affixes in a\n");
        builder.append("# disabled category get an empty rarity-values map at load, so they cannot\n");
        builder.append("# roll on heirloom, artifact, or esoteric. Only the AA entries added here\n");
        builder.append("# are affected; the common-through-ancient entries from Fallen Gems and\n");
        builder.append("# Affixes are left alone.\n");
        builder.append("#\n");
        builder.append("# Resolution order applied to each affix at load:\n");
        builder.append("#   1. Read the default value from the JSON.\n");
        builder.append("#   2. If [disable.<category>] is true, drop the AA-tier rolls and stop.\n");
        builder.append("#   3. Scale the value by the tier 1 multiplier for that rarity.\n");
        builder.append("#   4. If a tier 2 override for the field is set, replace the scaled value\n");
        builder.append("#      with the override.\n");
        builder.append("#   5. Apply the final value to the live affix instance.\n");
        builder.append("#\n");
        builder.append("# Example: make every esoteric staff affix a little stronger.\n");
        builder.append("#   [scaling]\n");
        builder.append("#   esoteric_multiplier = 1.25\n");
        builder.append("#\n");
        builder.append("# Example: the same global change, plus a fixed cooldown on one affix.\n");
        builder.append("#   [scaling]\n");
        builder.append("#   esoteric_multiplier = 1.25\n");
        builder.append("#   [overrides.autocast.acupuncture.esoteric]\n");
        builder.append("#   cooldown = 90\n");
        builder.append("#\n");
        builder.append("# Edit this file, then run /apothicstaffrarities reload (alias /asr reload,\n");
        builder.append("# requires op level 2) to apply changes without restarting the server.\n");
        builder.append("\n");
    }

    private static void appendScalingDefaults(final StringBuilder builder) {
        builder.append("[scaling]\n");
        builder.append("heirloom_multiplier = 1.0\n");
        builder.append("artifact_multiplier = 1.0\n");
        builder.append("esoteric_multiplier = 1.0\n");
        builder.append("\n");
    }

    private static void appendDisableDefaults(final StringBuilder builder, final List<String> disableCategories) {
        builder.append("[disable]\n");
        for (final String category : disableCategories) {
            builder.append(category).append(" = false\n");
        }
        builder.append("\n");
    }

    private static void appendOverrideDefaults(final StringBuilder builder, final List<AffixDescriptor> affixes, final List<String> rarities) {
        builder.append("[overrides]\n");
        builder.append("\n");
        for (final AffixDescriptor descriptor : affixes) {
            for (final String rarity : rarities) {
                appendOverrideBlock(builder, descriptor, rarity);
            }
        }
    }

    private static void appendOverrideBlock(final StringBuilder builder, final AffixDescriptor descriptor, final String rarity) {
        builder.append("[overrides.")
                .append(tomlAffixPath(descriptor))
                .append('.')
                .append(rarity)
                .append("]\n");
        switch (descriptor.category()) {
            case CATEGORY_AUTOCAST, CATEGORY_SPELL -> {
                builder.append(FIELD_LEVEL_MIN).append(" = -1\n");
                builder.append(FIELD_LEVEL_MAX).append(" = -1\n");
                builder.append(FIELD_COOLDOWN).append(" = -1\n");
            }
            case CATEGORY_MOB_EFFECT -> {
                builder.append(FIELD_DURATION_MIN).append(" = -1\n");
                builder.append(FIELD_DURATION_STEPS).append(" = -1\n");
                builder.append(FIELD_DURATION_STEP).append(" = -1\n");
                builder.append(FIELD_AMPLIFIER).append(" = -1\n");
                if (descriptor.amplifierIsObject()) {
                    builder.append(FIELD_AMPLIFIER_STEPS).append(" = -1\n");
                    builder.append(FIELD_AMPLIFIER_STEP).append(" = -1.0\n");
                }
                if (descriptor.hasCooldown()) {
                    builder.append(FIELD_COOLDOWN).append(" = -1\n");
                }
            }
            case CATEGORY_CONCENTRATION -> builder.append(FIELD_ENABLED).append(" = true\n");
            case CATEGORY_COOLDOWN_RESET, CATEGORY_MANA_SHIELD -> {
                builder.append(FIELD_MIN).append(" = -1\n");
                builder.append(FIELD_STEPS).append(" = -1\n");
                builder.append(FIELD_STEP).append(" = -1.0\n");
            }
            default -> {
            }
        }
        builder.append("\n");
    }
}
