package com.craftteamracing.client;

import com.craftteamracing.CraftTeamRacingMod;
import com.craftteamracing.spectate.SpectateActionPayload;
import com.craftteamracing.spectate.SpectatePayload;

import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The viewer's camera at a viewing console (see {@link com.craftteamracing.spectate.SpectateManager}): a camera entity that orbits the followed kart. The mouse turns the view round the kart,
 * the wheel zooms, left and right click go to the next and the previous racer, and holding sneak for three seconds leaves. The camera is pulled in by blocks like a third person camera.
 */
@EventBusSubscriber(modid = CraftTeamRacingMod.MODID, value = Dist.CLIENT)
public final class SpectateCamera {
    private SpectateCamera() {}

    /** How long sneak is held to leave, in ticks. */
    private static final int HOLD_TICKS = 60;
    private static final double MIN_DISTANCE = 1.5;
    private static final double MAX_DISTANCE = 24.0;
    private static final double FOCUS_Y = 0.9;

    private static boolean on;
    private static int entityId;
    private static String name = "";
    private static int rank;
    private static int field;
    private static double distance = 6.0;
    private static int hold;
    private static boolean exitSent;

    private static Marker camera;
    private static Entity previous;
    private static float yawNow;
    private static float pitchNow;

    /** No race to follow: the camera is up and in front of the Start node at {@code anchor}, which faces {@code anchorYaw}. */
    private static boolean idle;
    private static Vec3 anchor = Vec3.ZERO;
    private static float anchorYaw;
    private static long idleSince;
    private static boolean behindKart = true;
    private static java.util.UUID racer = new java.util.UUID(0L, 0L);
    private static CameraType originalType;

    public static boolean viewing() {
        return on;
    }

    /** Viewing with no racer to follow (see {@link com.craftteamracing.spectate.SpectatePayload}). */
    public static boolean isIdle() {
        return on && idle;
    }

    /** The racer the camera follows (the one whose screen is shown). */
    public static java.util.UUID racerId() {
        return racer;
    }

    /** The followed racer's kart as this client has it, or null (not sent to this client yet). */
    public static Entity viewedEntity() {
        Minecraft mc = Minecraft.getInstance();
        return on && mc.level != null ? mc.level.getEntity(entityId) : null;
    }

    public static com.craftteamracing.kart.KartEntity viewedKart() {
        return viewedEntity() instanceof com.craftteamracing.kart.KartEntity k ? k : null;
    }

    /** Whether this is the viewer's own camera entity. */
    public static boolean isCamera(Entity e) {
        return camera != null && e == camera;
    }

    public static void set(SpectatePayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (!p.on()) {
            on = false;
            exitSent = false;
            hold = 0;
            stop(mc);
            if (mc.player != null && mc.getCameraEntity() instanceof net.minecraft.world.entity.Marker && !RaceIntroCamera.wanted(mc) && !PodiumCamera.active()) mc.setCameraEntity(mc.player);
            if (originalType != null) mc.options.setCameraType(originalType);
            originalType = null;
            // the racer's screen the viewer was shown is not theirs
            com.craftteamracing.track.RaceClient.clear();
            com.craftteamracing.pickup.ItemClient.clear();
            return;
        }
        if (!on) originalType = mc.options.getCameraType();
        if (!on || idle != p.idle()) {
            idleSince = System.currentTimeMillis();
            behindKart = true;          // a new view: the orbit starts behind the kart
        }
        on = true;
        idle = p.idle();
        anchor = new Vec3(p.ax(), p.ay(), p.az());
        anchorYaw = p.yaw();
        racer = p.racer();
        entityId = p.entityId();
        name = p.name();
        rank = p.rank();
        field = p.field();
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!on || mc.player == null || mc.level == null) {
            stop(mc);
            return;
        }
        if (RaceIntroCamera.wanted(mc) || (idle && PodiumCamera.active())) {          // the race intro's pan over the grid, or the podium's group shot, has the camera until it is done
            stop(mc);
            return;
        }
        Entity target = idle ? null : mc.level.getEntity(entityId);
        if (!idle && target == null) return;          // not sent to this client (yet): the camera stays where it is
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        if (camera == null) {
            camera = new Marker(EntityType.MARKER, mc.level);
            previous = mc.player;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.setCameraEntity(camera);
        }
        if (!idle && behindKart) {
            behindKart = false;
            mc.player.setYRot(target.getYRot());          // behind the kart to begin with
            mc.player.setXRot(15.0F);
        }
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (idle) {
            idleShot(mc);
            return;
        }
        mc.player.setXRot(Mth.clamp(mc.player.getXRot(), -85.0F, 85.0F));
        yawNow = mc.player.getYRot();
        pitchNow = mc.player.getXRot();
        Vec3 focus = target.getPosition(partial).add(0.0, FOCUS_Y, 0.0);
        Vec3 dir = Vec3.directionFromRotation(pitchNow, yawNow);
        Vec3 want = focus.subtract(dir.scale(distance));
        var hit = mc.level.clip(new ClipContext(focus, want, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        Vec3 at = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().add(dir.scale(0.3)) : want;
        camera.moveTo(at.x, at.y, at.z, yawNow, pitchNow);
        camera.setOldPosAndRot();
    }

    /**
     * With no race on: the camera hangs up and in front of the Start node and sweeps slowly across the starting grid behind it, looking at the middle of the grid. A block in the way pulls it in.
     * The wheel zooms; the mouse does not turn it.
     */
    private static void idleShot(Minecraft mc) {
        double yaw = Math.toRadians(anchorYaw);
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        Vec3 look = anchor.add(-fx * 5.0, 1.2, -fz * 5.0);
        double t = (System.currentTimeMillis() - idleSince) / 1000.0;
        double sweep = Math.toRadians(35.0 * Math.sin(t / 16.0 * Math.PI * 2.0));
        double d = distance + 5.0;
        double cos = Math.cos(sweep);
        double sin = Math.sin(sweep);
        double ox = (fx * cos - fz * sin) * d;          // the way in front of the node, turned by the sweep
        double oz = (fx * sin + fz * cos) * d;
        Vec3 want = look.add(ox, 3.0 + d * 0.45, oz);
        var hit = mc.level.clip(new ClipContext(look, want, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        Vec3 at = want;
        if (hit.getType() == HitResult.Type.BLOCK) at = hit.getLocation().subtract(want.subtract(look).normalize().scale(0.3));
        double dx = look.x - at.x;
        double dy = look.y - at.y;
        double dz = look.z - at.z;
        yawNow = (float) Math.toDegrees(Math.atan2(-dx, dz));
        pitchNow = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        camera.moveTo(at.x, at.y, at.z, yawNow, pitchNow);
        camera.setOldPosAndRot();
    }

    private static void stop(Minecraft mc) {
        if (camera == null) return;
        if (mc.getCameraEntity() == camera) mc.setCameraEntity(previous != null ? previous : mc.player);
        camera = null;
        previous = null;
    }

    @SubscribeEvent
    public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        if (camera == null) return;
        event.setYaw(yawNow);
        event.setPitch(pitchNow);
        event.setRoll(0.0F);
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent event) {
        if (camera != null) event.setCanceled(true);
    }

    /** The viewer does not move: they are only brought along by the server. */
    @SubscribeEvent
    public static void onMovement(MovementInputUpdateEvent event) {
        if (!on) return;
        var in = event.getInput();
        in.leftImpulse = 0.0F;
        in.forwardImpulse = 0.0F;
        in.up = false;
        in.down = false;
        in.left = false;
        in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!on || Minecraft.getInstance().screen != null) return;
        event.setCanceled(true);          // not the spectator's flying speed or menu
        distance = Mth.clamp(distance - event.getScrollDeltaY() * 0.8, MIN_DISTANCE, MAX_DISTANCE);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!on || mc.player == null) {
            hold = 0;
            return;
        }
        // the clicks are taken here, before the game turns them into hits and block placing
        while (mc.options.keyAttack.consumeClick()) {
            if (mc.screen == null) PacketDistributor.sendToServer(new SpectateActionPayload(SpectateActionPayload.NEXT));
        }
        while (mc.options.keyUse.consumeClick()) {
            if (mc.screen == null) PacketDistributor.sendToServer(new SpectateActionPayload(SpectateActionPayload.PREVIOUS));
        }
        for (var key : mc.options.keyHotbarSlots) while (key.consumeClick()) { /* the spectator menu is not wanted */ }
        if (mc.screen == null && mc.options.keyShift.isDown()) {
            if (++hold >= HOLD_TICKS && !exitSent) {
                exitSent = true;
                PacketDistributor.sendToServer(new SpectateActionPayload(SpectateActionPayload.EXIT));
            }
        } else {
            hold = 0;
        }
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        if (!on) return;
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth();
        int h = g.guiHeight();
        if (idle) {
            // with a race counting down or cooling down, the race screen says so; otherwise there is nothing on, and the viewer is told
            if (!com.craftteamracing.track.RaceClient.active()) g.drawCenteredString(mc.font, Component.translatable("hud.craftteamracing.spectate.idle"), w / 2, 12, 0xFFFFFF);
        } else {
            g.drawCenteredString(mc.font, Component.translatable("hud.craftteamracing.spectate.following", name, rank, field), w / 2, 12, 0xFFFFFF);
        }
        g.drawCenteredString(mc.font, Component.translatable(idle ? "hud.craftteamracing.spectate.help_idle" : "hud.craftteamracing.spectate.help"), w / 2, h - 32, 0xB0B0B0);
        if (hold > 0) HoldBar.draw(g, Component.translatable("hud.craftteamracing.spectate.leaving"), hold / (float) HOLD_TICKS);
    }
}
