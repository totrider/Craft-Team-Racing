package com.craftteamracing.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.craftteamracing.kart.KartScale;
import com.craftteamracing.physics.KartClass;
import com.craftteamracing.physics.KartInput;
import com.craftteamracing.physics.KartSim;
import com.craftteamracing.physics.V3;
import com.craftteamracing.physics.WorldScale;

/** The speed scale: the same physics in lu, so a kart covers the scale's share of the blocks, and the kart's size in blocks does not change. */
class SpeedScaleTest {
    @AfterEach
    void reset() {
        WorldScale.reset();
    }

    /** Blocks driven along a flat floor in 200 frames at full gas. */
    private static double drive(double speed) {
        WorldScale.setGlobal(speed);
        double lu = KartScale.luPerBlock();
        List<double[]> floor = List.of(new double[] { -50, -1, -50, 400, 0, 400 });
        TerrainCollider collider = new TerrainCollider(new TerrainField(BombRollTest.world(floor)), lu);
        KartSim k = new KartSim(KartClass.BALANCED, new V3(6.5 * lu, 0, 0), 0);
        KartInput gas = new KartInput(0, true, false, false, false, false);
        for (int f = 0; f < 200; f++) {
            collider.prepare(k.position(), k.velocity().y / 256.0);
            k.tick(collider, gas);
        }
        return k.position().z / lu;
    }

    @Test
    void halfSpeedCoversHalfTheBlocks() {
        double full = drive(1.0);
        double half = drive(0.5);
        assertTrue(full > 100, "the full speed kart drove " + full);
        assertEquals(0.5, half / full, 0.03, "half scale drove " + half + " against " + full);
    }

    @Test
    void theKartKeepsItsSizeInBlocks() {
        for (double s : new double[] { 1.0, 0.7, 0.5 }) {
            WorldScale.setGlobal(s);
            assertEquals(KartSim.BASE_SPHERE_RADIUS / KartScale.BASE_LU_PER_BLOCK, KartSim.sphereRadius() / KartScale.luPerBlock(), 1e-9);
        }
    }

    @Test
    void theScaleIsKeptInRange() {
        WorldScale.setGlobal(0.1);
        assertEquals(0.5, WorldScale.speed(), 0.0);
        WorldScale.setGlobal(3.0);
        assertEquals(1.0, WorldScale.speed(), 0.0);
    }

    @Test
    void theRulebookReplacesTheServersScaleForTheRace() {
        WorldScale.setGlobal(0.7);
        assertEquals(0.7, WorldScale.speed(), 1e-9);
        WorldScale.setTrack(100);
        assertEquals(1.0, WorldScale.speed(), 1e-9);
        WorldScale.setTrack(50);
        assertEquals(0.5, WorldScale.speed(), 1e-9);
        WorldScale.setTrack(0);
        assertEquals(0.7, WorldScale.speed(), 1e-9);
    }
}
