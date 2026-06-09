package com.nightwielder.apothicstaffrarities.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

import com.nightwielder.apothicstaffrarities.ApothicStaffRarities;
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
        if (!ModList.get().isLoaded(ApothicStaffRarities.APOTHEOSIS)) {
            source.sendFailure(Component.literal("Apotheosis is not loaded; nothing to apply."));
            return 0;
        }
        final AffixOverrideHandler.ReloadReport report = AffixOverrideHandler.reloadAndApply();
        final String summary = formatReport(report);
        source.sendSuccess(() -> Component.literal(summary), true);
        return report.totalApplied();
    }

    private static String formatReport(final AffixOverrideHandler.ReloadReport report) {
        if (report.noChanges()) {
            return "Apothic Staff Rarities: config unchanged.";
        }
        final int applied = report.totalApplied();
        final int disabled = report.totalDisabled();
        final int warnings = report.warnings().size();
        final StringBuilder builder = new StringBuilder();
        builder.append("Apothic Staff Rarities: reloaded, applied to ")
                .append(applied)
                .append(' ')
                .append(applied == 1 ? "affix" : "affixes");
        if (disabled > 0) {
            builder.append(", disabled ").append(disabled);
        }
        if (warnings > 0) {
            builder.append(", ").append(warnings).append(" warnings");
        }
        builder.append('.');
        return builder.toString();
    }
}
