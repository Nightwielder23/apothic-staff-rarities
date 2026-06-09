package com.nightwielder.apothicstaffrarities.affix;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import dev.shadowsoffire.apotheosis.adventure.affix.Affix;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixRegistry;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry;
import dev.shadowsoffire.placebo.util.StepFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import com.nightwielder.apothicstaffrarities.ApothicStaffRarities;
import com.nightwielder.apothicstaffrarities.config.ApothicStaffRaritiesConfig;
import com.nightwielder.apothicstaffrarities.config.ApothicStaffRaritiesConfig.AffixDescriptor;

@Mod.EventBusSubscriber(modid = ApothicStaffRarities.MODID)
public final class AffixOverrideHandler {
    private static final String VALUES_FIELD_NAME = "values";
    private static final String AA_NAMESPACE = "apotheotic_additions";

    private static final Map<ResourceLocation, AffixSnapshot> snapshots = new HashMap<>();
    private static String lastAppliedSignature;

    private AffixOverrideHandler() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAddReloadListeners(final AddReloadListenerEvent event) {
        // LOWEST so this listener registers after FG&A's affix registry, which puts the post-barrier apply after the affixes are loaded.
        event.addListener(new ApplyAfterAffixesListener());
    }

    public static ReloadReport reloadAndApply() {
        ApothicStaffRaritiesConfig.load();
        // signature() runs after load(), so an edited file always differs from lastAppliedSignature and re-applies.
        final String signature = ApothicStaffRaritiesConfig.signature();
        if (!snapshots.isEmpty() && signature.equals(lastAppliedSignature)) {
            final ReloadReport unchanged = new ReloadReport();
            unchanged.markNoChanges();
            ApothicStaffRarities.LOGGER.info("Apothic Staff Rarities override pass: config unchanged since last apply");
            return unchanged;
        }
        final ReloadReport report = applyAll();
        lastAppliedSignature = signature;
        return report;
    }

    private static ReloadReport applyAll() {
        final ReloadReport report = new ReloadReport();
        if (!ModList.get().isLoaded(ApothicStaffRarities.APOTHEOSIS)) {
            return report;
        }
        captureNewAffixes();
        for (final Map.Entry<ResourceLocation, AffixSnapshot> entry : snapshots.entrySet()) {
            try {
                applyOne(entry.getKey(), entry.getValue(), report);
            } catch (final Exception e) {
                ApothicStaffRarities.LOGGER.warn("Failed to apply config to affix {}", entry.getKey(), e);
                report.addWarning(entry.getKey() + ": " + e.getMessage());
            }
        }
        ApothicStaffRarities.LOGGER.info("Apothic Staff Rarities override pass: applied={}, disabled={}, warnings={}",
                report.totalApplied(), report.totalDisabled(), report.warnings().size());
        return report;
    }

    private static void clearSnapshots() {
        snapshots.clear();
    }

    private static void captureNewAffixes() {
        final Set<ResourceLocation> liveIds = new HashSet<>(AffixRegistry.INSTANCE.getKeys());
        for (final ResourceLocation id : liveIds) {
            if (!ApothicStaffRarities.MODID.equals(id.getNamespace())) continue;
            if (snapshots.containsKey(id)) continue;
            final Affix affix = AffixRegistry.INSTANCE.getValue(id);
            if (affix == null) {
                ApothicStaffRarities.LOGGER.warn("Registry key {} present but getValue returned null", id);
                continue;
            }
            try {
                final Field valuesField = ReflectionAccess.findFieldUp(affix.getClass(), VALUES_FIELD_NAME);
                if (valuesField == null) {
                    ApothicStaffRarities.LOGGER.warn("No 'values' field on {} (class {})", id, affix.getClass().getName());
                    continue;
                }
                @SuppressWarnings("unchecked")
                final Map<LootRarity, Object> liveValues = (Map<LootRarity, Object>) ReflectionAccess.getField(valuesField, affix);
                snapshots.put(id, new AffixSnapshot(affix, valuesField, new LinkedHashMap<>(liveValues)));
            } catch (final Exception e) {
                ApothicStaffRarities.LOGGER.warn("Could not snapshot affix {}", id, e);
            }
        }
    }

    private static void applyOne(final ResourceLocation id, final AffixSnapshot snapshot, final ReloadReport report) throws Exception {
        final AffixDescriptor descriptor = parseDescriptorFromId(id);
        if (descriptor == null) return;
        if (ApothicStaffRaritiesConfig.isCategoryDisabled(descriptor.category())) {
            ReflectionAccess.setField(snapshot.valuesField, snapshot.affix, new LinkedHashMap<>());
            report.bumpDisabled(descriptor.category());
            return;
        }
        final Map<LootRarity, Object> rebuilt = rebuildValuesMap(descriptor, snapshot.originalValues);
        ReflectionAccess.setField(snapshot.valuesField, snapshot.affix, rebuilt);
        report.bumpApplied(descriptor.category());
    }

    private static Map<LootRarity, Object> rebuildValuesMap(final AffixDescriptor descriptor, final Map<LootRarity, Object> originals) {
        final Map<LootRarity, Object> rebuilt = new LinkedHashMap<>();
        for (final Map.Entry<LootRarity, Object> entry : originals.entrySet()) {
            final String rarityName = resolveRarityShortName(entry.getKey());
            if (rarityName == null) {
                rebuilt.put(entry.getKey(), entry.getValue());
                continue;
            }
            try {
                rebuilt.put(entry.getKey(), rebuildValueForRarity(descriptor, rarityName, entry.getValue()));
            } catch (final Exception e) {
                ApothicStaffRarities.LOGGER.warn("rebuildValueForRarity threw for {}/{}; preserving original entry",
                        descriptor.name(), rarityName, e);
                rebuilt.put(entry.getKey(), entry.getValue());
            }
        }
        return rebuilt;
    }

    private static Object rebuildValueForRarity(final AffixDescriptor descriptor, final String rarityName, final Object originalValue) throws Exception {
        return switch (descriptor.category()) {
            case ApothicStaffRaritiesConfig.CATEGORY_AUTOCAST,
                 ApothicStaffRaritiesConfig.CATEGORY_SPELL ->
                    rebuildTriggerData(descriptor, rarityName, originalValue);
            case ApothicStaffRaritiesConfig.CATEGORY_MOB_EFFECT ->
                    rebuildEffectData(descriptor, rarityName, originalValue);
            case ApothicStaffRaritiesConfig.CATEGORY_CONCENTRATION ->
                    rebuildConcentrationFlag(descriptor, rarityName);
            case ApothicStaffRaritiesConfig.CATEGORY_COOLDOWN_RESET,
                 ApothicStaffRaritiesConfig.CATEGORY_MANA_SHIELD ->
                    rebuildPlainStepFunction(descriptor, rarityName, originalValue);
            default -> originalValue;
        };
    }

    private static Object rebuildTriggerData(final AffixDescriptor descriptor, final String rarityName, final Object original) throws Exception {
        final Object levelRange = ReflectionAccess.invokeAccessor(original, "level");
        final int defaultMin = (int) ReflectionAccess.invokeAccessor(levelRange, "min");
        final int defaultMax = (int) ReflectionAccess.invokeAccessor(levelRange, "max");
        final int defaultCooldown = (int) ReflectionAccess.invokeAccessor(original, "cooldown");
        final double multiplier = ApothicStaffRaritiesConfig.multiplierFor(rarityName);
        final int finalMin = nonNeg(resolveScaledInt(descriptor, rarityName, ApothicStaffRaritiesConfig.FIELD_LEVEL_MIN, defaultMin, multiplier));
        final int finalMax = nonNeg(resolveScaledInt(descriptor, rarityName, ApothicStaffRaritiesConfig.FIELD_LEVEL_MAX, defaultMax, multiplier));
        final int finalCooldown = nonNeg(resolveScaledInt(descriptor, rarityName, ApothicStaffRaritiesConfig.FIELD_COOLDOWN, defaultCooldown, multiplier));
        final Class<?> levelRangeClass = levelRange.getClass();
        final Object newLevelRange = ReflectionAccess.constructInstance(levelRangeClass, new Class<?>[]{int.class, int.class}, finalMin, finalMax);
        final Class<?> triggerDataClass = original.getClass();
        return ReflectionAccess.constructInstance(triggerDataClass, new Class<?>[]{levelRangeClass, int.class}, newLevelRange, finalCooldown);
    }

    private static Object rebuildEffectData(final AffixDescriptor descriptor, final String rarityName, final Object original) throws Exception {
        final StepFunction defaultDuration = (StepFunction) ReflectionAccess.invokeAccessor(original, "duration");
        final StepFunction defaultAmplifier = (StepFunction) ReflectionAccess.invokeAccessor(original, "amplifier");
        final int defaultCooldown = (int) ReflectionAccess.invokeAccessor(original, "cooldown");
        final double multiplier = ApothicStaffRaritiesConfig.multiplierFor(rarityName);
        final StepFunction newDuration = resolveScaledStepFunction(descriptor, rarityName, defaultDuration, multiplier,
                ApothicStaffRaritiesConfig.FIELD_DURATION_MIN,
                ApothicStaffRaritiesConfig.FIELD_DURATION_STEPS,
                ApothicStaffRaritiesConfig.FIELD_DURATION_STEP, false);
        final StepFunction newAmplifier = resolveScaledStepFunction(descriptor, rarityName, defaultAmplifier, multiplier,
                ApothicStaffRaritiesConfig.FIELD_AMPLIFIER,
                ApothicStaffRaritiesConfig.FIELD_AMPLIFIER_STEPS,
                ApothicStaffRaritiesConfig.FIELD_AMPLIFIER_STEP, !descriptor.amplifierIsObject());
        final int newCooldown = descriptor.hasCooldown()
                ? nonNeg(resolveScaledInt(descriptor, rarityName, ApothicStaffRaritiesConfig.FIELD_COOLDOWN, defaultCooldown, multiplier))
                : defaultCooldown;
        return ReflectionAccess.constructInstance(original.getClass(),
                new Class<?>[]{StepFunction.class, StepFunction.class, int.class},
                newDuration, newAmplifier, newCooldown);
    }

    private static Object rebuildConcentrationFlag(final AffixDescriptor descriptor, final String rarityName) {
        return ApothicStaffRaritiesConfig.overrideBool(
                descriptor.category(), descriptor.name(), rarityName,
                ApothicStaffRaritiesConfig.FIELD_ENABLED, true);
    }

    private static Object rebuildPlainStepFunction(final AffixDescriptor descriptor, final String rarityName, final Object original) {
        final StepFunction defaults = (StepFunction) original;
        final double multiplier = ApothicStaffRaritiesConfig.multiplierFor(rarityName);
        return resolveScaledStepFunction(descriptor, rarityName, defaults, multiplier,
                ApothicStaffRaritiesConfig.FIELD_MIN,
                ApothicStaffRaritiesConfig.FIELD_STEPS,
                ApothicStaffRaritiesConfig.FIELD_STEP, false);
    }

    private static StepFunction resolveScaledStepFunction(final AffixDescriptor descriptor, final String rarityName, final StepFunction defaults, final double multiplier,
                                                          final String minField, final String stepsField, final String stepField, final boolean scaleConstant) {
        final float scaledMin = scaleConstant ? (float) Math.round(defaults.min() * multiplier) : defaults.min();
        final float scaledStep = scaleConstant ? defaults.step() : (float) (defaults.step() * multiplier);
        final OptionalDouble overrideMin = ApothicStaffRaritiesConfig.overrideDouble(descriptor.category(), descriptor.name(), rarityName, minField);
        final OptionalInt overrideSteps = ApothicStaffRaritiesConfig.overrideInt(descriptor.category(), descriptor.name(), rarityName, stepsField);
        final OptionalDouble overrideStep = ApothicStaffRaritiesConfig.overrideDouble(descriptor.category(), descriptor.name(), rarityName, stepField);
        final float finalMin = overrideMin.isPresent() ? (float) overrideMin.getAsDouble() : scaledMin;
        final int finalSteps = overrideSteps.isPresent() ? overrideSteps.getAsInt() : defaults.steps();
        final float finalStep = overrideStep.isPresent() ? (float) overrideStep.getAsDouble() : scaledStep;
        return new StepFunction(nonNeg(finalMin), nonNeg(finalSteps), nonNeg(finalStep));
    }

    private static int resolveScaledInt(final AffixDescriptor descriptor, final String rarityName, final String field, final int defaultValue, final double multiplier) {
        final OptionalInt override = ApothicStaffRaritiesConfig.overrideInt(descriptor.category(), descriptor.name(), rarityName, field);
        return override.isPresent() ? override.getAsInt() : (int) Math.round(defaultValue * multiplier);
    }

    private static int nonNeg(final int value) {
        return Math.max(0, value);
    }

    private static float nonNeg(final float value) {
        return Math.max(0.0f, value);
    }

    private static String resolveRarityShortName(final LootRarity rarity) {
        final ResourceLocation id = RarityRegistry.INSTANCE.getKey(rarity);
        if (id == null || !AA_NAMESPACE.equals(id.getNamespace())) return null;
        final String path = id.getPath();
        return switch (path) {
            case ApothicStaffRaritiesConfig.RARITY_HEIRLOOM,
                 ApothicStaffRaritiesConfig.RARITY_ARTIFACT,
                 ApothicStaffRaritiesConfig.RARITY_ESOTERIC -> path;
            default -> null;
        };
    }

    private static AffixDescriptor parseDescriptorFromId(final ResourceLocation id) {
        final String[] parts = id.getPath().split("/");
        if (parts.length < 2 || !"staffs".equals(parts[0])) return null;
        if (parts.length == 2) {
            return ApothicStaffRaritiesConfig.findAffix(parts[1], parts[1]).orElse(null);
        }
        return ApothicStaffRaritiesConfig.findAffix(parts[1], parts[2]).orElse(null);
    }

    private static final class AffixSnapshot {
        private final Affix affix;
        private final Field valuesField;
        private final Map<LootRarity, Object> originalValues;

        AffixSnapshot(final Affix affix, final Field valuesField, final Map<LootRarity, Object> originalValues) {
            this.affix = affix;
            this.valuesField = valuesField;
            this.originalValues = originalValues;
        }
    }

    public static final class ReloadReport {
        private final Map<String, Integer> appliedByCategory = new LinkedHashMap<>();
        private final Map<String, Integer> disabledByCategory = new LinkedHashMap<>();
        private final List<String> warnings = new ArrayList<>();
        private boolean noChanges;

        public List<String> warnings() {
            return Collections.unmodifiableList(warnings);
        }

        public int totalApplied() {
            return appliedByCategory.values().stream().mapToInt(Integer::intValue).sum();
        }

        public int totalDisabled() {
            return disabledByCategory.values().stream().mapToInt(Integer::intValue).sum();
        }

        public boolean noChanges() {
            return noChanges;
        }

        private void bumpApplied(final String category) {
            appliedByCategory.merge(category, 1, Integer::sum);
        }

        private void bumpDisabled(final String category) {
            disabledByCategory.merge(category, 1, Integer::sum);
        }

        private void addWarning(final String message) {
            warnings.add(message);
        }

        private void markNoChanges() {
            noChanges = true;
        }
    }

    private static final class ApplyAfterAffixesListener implements PreparableReloadListener {
        @Override
        public CompletableFuture<Void> reload(final PreparationBarrier barrier, final ResourceManager resourceManager,
                                              final ProfilerFiller preparationsProfiler, final ProfilerFiller reloadProfiler,
                                              final Executor backgroundExecutor, final Executor gameExecutor) {
            return CompletableFuture.<Void>supplyAsync(() -> null, backgroundExecutor)
                    .thenCompose(barrier::wait)
                    .thenRunAsync(() -> {
                        // A datapack reload recreates the affixes, so re-apply without recording the signature, leaving the first manual reload to report a change.
                        clearSnapshots();
                        ApothicStaffRaritiesConfig.load();
                        applyAll();
                    }, gameExecutor);
        }

        @Override
        public String getName() {
            return ApothicStaffRarities.MODID + ":override_apply";
        }
    }
}
