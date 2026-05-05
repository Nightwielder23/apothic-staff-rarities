package com.nightwielder.apothicstaffrarities.command;

import java.util.Map;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

import com.nightwielder.apothicstaffrarities.affix.AffixOverrideHandler;

public final class ReloadCommand {
    private static final String ROOT_LITERAL = "apothicstaffrarities";
    private static final String ALIAS_LITERAL = "asr";

    private ReloadCommand() {}

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        final LiteralCommandNode<CommandSourceStack> root = dispatcher.register(buildRoot(ROOT_LITERAL));
        dispatcher.register(Commands.literal(ALIAS_LITERAL).requires(src -> src.hasPermission(2)).redirect(root));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot(final String literal) {
        return Commands.literal(literal)
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("reload").executes(ctx -> runReload(ctx.getSource())));
    }

    private static int runReload(final CommandSourceStack source) {
        if (!ModList.get().isLoaded("apotheosis")) {
            source.sendFailure(Component.literal("Apotheosis is not loaded; nothing to apply."));
            return 0;
        }
        final AffixOverrideHandler.ReloadReport report = AffixOverrideHandler.reloadAndApply();
        final String summary = formatReport(report);
        source.sendSuccess(() -> Component.literal(summary), true);
        return report.totalApplied();
    }

    private static String formatReport(final AffixOverrideHandler.ReloadReport report) {
        final int applied = report.totalApplied();
        final int disabled = report.totalDisabled();
        final StringBuilder builder = new StringBuilder();
        builder.append("Apothic Staff Rarities: applied overrides to ")
                .append(applied)
                .append(" affix ")
                .append(pluralize(applied, "entry", "entries"));
        appendCategoryBreakdown(builder, report.appliedByCategory());
        builder.append('.');
        if (disabled > 0) {
            builder.append(" Disabled ")
                    .append(disabled)
                    .append(' ')
                    .append(pluralize(disabled, "entry", "entries"));
            appendCategoryBreakdown(builder, report.disabledByCategory());
            builder.append('.');
        }
        if (!report.disabledCategoriesFromConfig().isEmpty()) {
            builder.append(" Disable toggles active: ")
                    .append(String.join(", ", report.disabledCategoriesFromConfig()))
                    .append('.');
        }
        if (!report.warnings().isEmpty()) {
            builder.append(" Warnings: ").append(report.warnings().size()).append(" (see log).");
        }
        return builder.toString();
    }

    private static void appendCategoryBreakdown(final StringBuilder builder, final Map<String, Integer> counts) {
        if (counts.isEmpty()) return;
        builder.append(" (");
        boolean first = true;
        for (final Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (!first) builder.append(", ");
            builder.append(entry.getKey()).append(": ").append(entry.getValue());
            first = false;
        }
        builder.append(")");
    }

    private static String pluralize(final int count, final String singular, final String plural) {
        return count == 1 ? singular : plural;
    }
}
