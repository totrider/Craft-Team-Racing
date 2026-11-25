package com.craftteamracing.compat;

import com.craftteamracing.CtrClientConfig;
import com.craftteamracing.track.RaceClient;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade (optional): its pop-up (what the crosshair is on) would sit on the race's own screen, so it is not shown while the player takes part in a race. Jade finds this class by its
 * {@code @WailaPlugin} annotation and nothing else loads it, so without Jade it is never touched. The ray trace callback returns no target, so Jade does not even ask the server for one.
 */
@WailaPlugin
public class JadeCompat implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addRayTraceCallback(10000, (hit, accessor, original) -> CtrClientConfig.HIDE_JADE_IN_RACE.get() && (RaceClient.active() || com.craftteamracing.client.SpectateCamera.viewing()) ? null : accessor);
    }
}
