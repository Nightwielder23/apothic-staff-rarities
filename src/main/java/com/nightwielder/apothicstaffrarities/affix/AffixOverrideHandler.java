package com.nightwielder.apothicstaffrarities.affix;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private AffixOverrideHandler() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAddReloadListeners(final AddReloadListenerEvent event) {
        event.addListener(new ApplyAfterAffixesListener());
    }

    public static ReloadReport reloadAndApply() {
        ApothicStaffRaritiesConfig.load();
        return applyAll();
    }

    private static ReloadReport applyAll() {
        final ReloadReport report = new ReloadReport();
        if (!ModList.get().isLoaded("apotheosis")) {
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
        report.setDisabledCategoriesFromConfig(ApothicStaffRaritiesConfig.getDisabledCategories());
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
                final Field valuesField = findFieldUp(affix.getClass(), VALUES_FIELD_NAME);
                if (valuesField == null) {
                    ApothicStaffRarities.LOGGER.warn("No 'values' field on {} (class {})", id, affix.getClass().getName());
                    continue;
                }
                valuesField.setAccessible(true);
                @SuppressWarnings("unchecked")
                final Map<LootRarity, Object> liveValues = (Map<LootRarity, Object>) valuesField.get(affix);
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
            writeValuesField(snapshot, new LinkedHashMap<>());
            report.bumpDisabled(descriptor.category());
            return;
        }
        final Map<LootRarity, Object> rebuilt = rebuildValuesMap(descriptor, snapshot.originalValues);
        writeValuesField(snapshot, rebuilt);
        report.bumpApplied(descriptor.category());
    }

    private static Map<LootRarity, Object> rebuildValuesMap(final AffixDescriptor descriptor, final Map<LootRarity, Object> originals) throws Exception {
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
        final Object levelRange = invokeAccessor(original, "level");
        final int defaultMin = (int) invokeAccessor(levelRange, "min");
        final int defaultMax = (int) invokeAccessor(levelRange, "max");
        final int defaultCooldown = (int) invokeAccessor(original, "cooldown");
        final double multiplier = ApothicStaffRaritiesConfig.multiplierFor(rarityName);
        final int finalMin = resolveScaledInt(descriptor, rarityName, ApothicStaffRaritiesConfig.FIELD_LEVEL_MIN, defaultMin, multiplier);
        final int finalMax = resolveScaledInt(descriptor, rarityName, ApothicStaffRaritiesConfig.FIELD_LEVEL_MAX, defaultMax, multiplier);
        final int finalCooldown = resolveScaledInt(descriptor, rarityName, ApothicStaffRaritiesConfig.FIELD_COOLDOWN, defaultCooldown, multiplier);
        final Class<?> levelRangeClass = levelRange.getClass();
        final Object newLevelRange = constructInstance(levelRangeClass, new Class<?>[]{int.class, int.class}, finalMin, finalMax);
        final Class<?> triggerDataClass = original.getClass();
        return constructInstance(triggerDataClass, new Class<?>[]{levelRangeClass, int.class}, newLevelRange, finalCooldown);
    }

    private static Object rebuildEffectData(final AffixDescriptor descriptor, final String rarityName, final Object original) throws Exception {
        final StepFunction defaultDuration = (StepFunction) invokeAccessor(original, "duration");
        final StepFunction defaultAmplifier = (StepFunction) invokeAccessor(original, "amplifier");
        final int defaultCooldown = (int) invokeAccessor(original, "cooldown");
        final double multiplier = ApothicStaffRaritiesConfig.multiplierFor(rarityName);
        final StepFunction newDuration = resolveScaledStepFunction(descriptor, rarityName, defaultDuration, multiplier,
                ApothicStaffRaritiesConfig.FIELD_DURATION_MIN,
                ApothicStaffRaritiesConfig.FIELD_DURATION_STEPS,
                ApothicStaffRaritiesConfig.FIELD_DURATION_STEP);
        final StepFunction newAmplifier = resolveScaledStepFunction(descriptor, rarityName, defaultAmplifier, multiplier,
                ApothicStaffRaritiesConfig.FIELD_AMPLIFIER,
                ApothicStaffRaritiesConfig.FIELD_AMPLIFIER_STEPS,
                ApothicStaffRaritiesConfig.FIELD_AMPLIFIER_STEP);
        final int newCooldown = descriptor.hasCooldown()
                ? resolveScaledInt(descriptor, rarityName, ApothicStaffRaritiesConfig.FIELD_COOLDOWN, defaultCooldown, multiplier)
                : defaultCooldown;
        return constructInstance(original.getClass(),
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
                ApothicStaffRaritiesConfig.FIELD_STEP);
    }

    private static StepFunction resolveScaledStepFunction(final AffixDescriptor descriptor, final String rarityName, final StepFunction defaults, final double multiplier,
                                                          final String minField, final String stepsField, final String stepField) {
        final float scaledMin = (float) (defaults.min() * multiplier);
        final int scaledSteps = (int) Math.round(defaults.steps() * multiplier);
        final float scaledStep = (float) (defaults.step() * multiplier);
        final double overrideMin = ApothicStaffRaritiesConfig.overrideDouble(descriptor.category(), descriptor.name(), rarityName, minField);
        final int overrideSteps = ApothicStaffRaritiesConfig.overrideInt(descriptor.category(), descriptor.name(), rarityName, stepsField);
        final double overrideStep = ApothicStaffRaritiesConfig.overrideDouble(descriptor.category(), descriptor.name(), rarityName, stepField);
        final float finalMin = overrideMin >= 0.0 ? (float) overrideMin : scaledMin;
        final int finalSteps = overrideSteps >= 0 ? overrideSteps : scaledSteps;
        final float finalStep = overrideStep >= 0.0 ? (float) overrideStep : scaledStep;
        return new StepFunction(finalMin, finalSteps, finalStep);
    }

    private static int resolveScaledInt(final AffixDescriptor descriptor, final String rarityName, final String field, final int defaultValue, final double multiplier) {
        final int scaled = (int) Math.round(defaultValue * multiplier);
        final int override = ApothicStaffRaritiesConfig.overrideInt(descriptor.category(), descriptor.name(), rarityName, field);
        return override >= 0 ? override : scaled;
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

    private static Field findFieldUp(final Class<?> startingClass, final String fieldName) {
        Class<?> current = startingClass;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (final NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Object invokeAccessor(final Object target, final String methodName) throws Exception {
        final Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static Object constructInstance(final Class<?> cls, final Class<?>[] parameterTypes, final Object... arguments) throws Exception {
        final Constructor<?> constructor = cls.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
    }

    private static void writeValuesField(final AffixSnapshot snapshot, final Map<LootRarity, Object> newValues) throws IllegalAccessException {
        snapshot.valuesField.setAccessible(true);
        snapshot.valuesField.set(snapshot.affix, newValues);
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
        private final List<String> disabledCategoriesFromConfig = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        public Map<String, Integer> appliedByCategory() {
            return Collections.unmodifiableMap(appliedByCategory);
        }

        public Map<String, Integer> disabledByCategory() {
            return Collections.unmodifiableMap(disabledByCategory);
        }

        public List<String> disabledCategoriesFromConfig() {
            return Collections.unmodifiableList(disabledCategoriesFromConfig);
        }

        public List<String> warnings() {
            return Collections.unmodifiableList(warnings);
        }

        public int totalApplied() {
            return appliedByCategory.values().stream().mapToInt(Integer::intValue).sum();
        }

        public int totalDisabled() {
            return disabledByCategory.values().stream().mapToInt(Integer::intValue).sum();
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

        private void setDisabledCategoriesFromConfig(final List<String> categories) {
            disabledCategoriesFromConfig.clear();
            disabledCategoriesFromConfig.addAll(categories);
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
                        clearSnapshots();
                        reloadAndApply();
                    }, gameExecutor);
        }

        @Override
        public String getName() {
            return ApothicStaffRarities.MODID + ":override_apply";
        }
    }
}
