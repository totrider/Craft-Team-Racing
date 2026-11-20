package com.craftteamracing.client;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** The mods menu's config button: NeoForge's own configuration screen for the mod's client and server settings. */
public final class ConfigScreens {
    private ConfigScreens() {}

    public static void register(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
