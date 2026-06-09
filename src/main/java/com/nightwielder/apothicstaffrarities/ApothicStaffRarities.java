package com.nightwielder.apothicstaffrarities;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import com.nightwielder.apothicstaffrarities.command.ReloadCommand;

@Mod(ApothicStaffRarities.MODID)
public class ApothicStaffRarities {
    public static final String MODID = "apothic_staff_rarities";
    public static final String APOTHEOSIS = "apotheosis";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ApothicStaffRarities() {
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onRegisterCommands(final RegisterCommandsEvent event) {
        ReloadCommand.register(event.getDispatcher());
    }
}
