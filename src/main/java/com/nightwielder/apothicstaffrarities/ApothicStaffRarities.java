package com.nightwielder.apothicstaffrarities;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import com.nightwielder.apothicstaffrarities.command.ReloadCommand;
import com.nightwielder.apothicstaffrarities.config.ApothicStaffRaritiesConfig;

@Mod(ApothicStaffRarities.MODID)
public class ApothicStaffRarities {
    public static final String MODID = "apothic_staff_rarities";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ApothicStaffRarities(final FMLJavaModLoadingContext context) {
        final IEventBus modBus = context.getModEventBus();
        modBus.addListener(this::onCommonSetup);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        ApothicStaffRaritiesConfig.load();
    }

    private void onRegisterCommands(final RegisterCommandsEvent event) {
        ReloadCommand.register(event.getDispatcher());
    }
}
