package com.ctrcraft.client;

import com.ctrcraft.CtrCraftMod;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Client side debug commands (they only change what this client draws, so they need no operator rights and work in multiplayer):
 * {@code /ctr debug flame <size 1-8> [ultra]} shows the boost flame on every kart whatever it is doing, {@code /ctr debug flame off} gives the flame back to the game; {@code flipx}, {@code flipz} and {@code bright <0.05-1>} are trial settings.
 */
@EventBusSubscriber(modid = CtrCraftMod.MODID, value = Dist.CLIENT)
public final class DebugCommands {
    private DebugCommands() {}

    /** The flame size forced on every kart, 0 for none (the game decides). */
    public static volatile int flameSize = 0;
    public static volatile boolean flameUltra = false;
    /** Trial settings for the flame's placement, while its orientation is being found: turn it about the x or the z axis of the pipe (a mirror), and its brightness (0.1 to 1). */
    public static volatile boolean flameFlipX = false;
    public static volatile boolean flameFlipZ = true;
    /** The model turned half a revolution about its x axis (y and z negated): what lines the original's flame model up with the exhaust (found in a model viewer). */
    public static volatile boolean flameRotX = true;
    /**
     * The shield bubble's pose, to be found by trial (/ctr debug shield ...): the model's own origin sits on the kart's origin; the vertex is turned by {@code shieldRot} (degrees about the
     * model's x, then y, then z), scaled by {@code shieldScale} (times the game's opening tables when {@code shieldTable}) along the turned axes, then moved by {@code shieldOffset}
     * (blocks, y up) and, in kart mode, turned with the kart (in camera mode: to face the camera). {@code shieldHold} is a frame of the opening to keep (-1: play it).
     */
    /**
     * The defaults are the author's Blender fit of the footage: the cone's axis (the model's y) along the kart with the tip toward its BACK (the opening in front, wrapping round the driver), its short axis (z) up, x across, the model at the size
     * it has in the file times 0.388 along x (and unscaled along the other two): against the game's resting scale 0.4375, that is 0.887 along x and 2.286 (= 1 / 0.4375) along the other two;
     * the model's own origin (off its middle) put 0.1 of a crate (about 0.15 block) lower.
     */
    public static final float[] shieldRot = { -90, 180, 0 };
    public static final float[] shieldScale = { 1.65F, 4.25F, 0.5F };
    public static final float[] shieldOffset = { 0, 0.55F, 0 };
    /** The sheen: {@code sheenAngle} holds one of its eight frames (a turn in degrees, 270 to 427; negative: play the sweep), {@code sheenOn} hides it. */
    public static volatile float sheenAngle = -1.0F;
    /** The sweep's speed against the game's (1 = a step a frame); the baked pivot makes it look faster than the footage. */
    public static volatile float sheenSpeed = 0.6F;
    public static volatile boolean sheenOn = true;
    public static volatile boolean shieldCamera = true;
    /** Camera mode: false turns about the vertical only (the game's), true faces the camera along the line to the bubble whatever the height (a full billboard). */
    public static volatile boolean shieldSphere = true;
    /** 0 outside faces only, 1 inside faces only, 2 both. */
    public static volatile int shieldFaces = 0;
    /** Drawn over everything, depth test off (the bubble shows over the kart it is round, whether the kart is in front of its faces or not). */
    /** 0 auto (drawn over the ground it sinks into, but with the depth test back on when a block stands between the camera and the bubble), 1 depth test on, 2 off. */
    /** Strength of the bubble's layers (1 = the model's colours added in full); negative: the game's (0.45, rising in the last second). */
    public static volatile float shieldIntensity = -1.0F;
    public static volatile int shieldDepth = 3;
    /** Depth mode 3 (shift): blocks the bubble is pulled toward the camera by, to lie in front of the kart. */
    public static volatile float shieldShift = 1.5F;
    public static volatile boolean shieldTable = true;
    public static volatile boolean shieldShow = false;
    public static volatile int shieldHold = -1;
    public static volatile float flameBright = 1.0F;
    /** Trial nudge of the flame's root from the pipe's tip, blocks: out to the side (away from the middle), up, back. */
    public static volatile float flameDx = 0.0F;
    public static volatile float flameDy = 0.0F;
    public static volatile float flameDz = 0.0F;

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ctr").then(Commands.literal("debug").then(shieldCommands())));
        event.getDispatcher().register(Commands.literal("ctr").then(Commands.literal("debug").then(hudCommands())));
        event.getDispatcher().register(Commands.literal("ctr").then(Commands.literal("debug").then(Commands.literal("flame")
                .then(Commands.literal("off").executes(ctx -> {
                    flameSize = 0;
                    ctx.getSource().sendSuccess(() -> Component.literal("Boost flame: left to the game"), false);
                    return 1;
                }))
                .then(Commands.literal("flipx").executes(ctx -> {
                    flameFlipX = !flameFlipX;
                    ctx.getSource().sendSuccess(() -> Component.literal("Boost flame mirrored in x: " + flameFlipX), false);
                    return 1;
                }))
                .then(Commands.literal("rotx").executes(ctx -> {
                    flameRotX = !flameRotX;
                    ctx.getSource().sendSuccess(() -> Component.literal("Boost flame turned 180 degrees about x: " + flameRotX), false);
                    return 1;
                }))
                .then(Commands.literal("flipz").executes(ctx -> {
                    flameFlipZ = !flameFlipZ;
                    ctx.getSource().sendSuccess(() -> Component.literal("Boost flame mirrored in z (front/back): " + flameFlipZ), false);
                    return 1;
                }))
                .then(Commands.literal("nudge").then(Commands.argument("out", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(-2.0F, 2.0F))
                        .then(Commands.argument("up", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(-2.0F, 2.0F))
                                .then(Commands.argument("back", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(-2.0F, 2.0F)).executes(ctx -> {
                                    flameDx = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "out");
                                    flameDy = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "up");
                                    flameDz = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "back");
                                    ctx.getSource().sendSuccess(() -> Component.literal("Boost flame nudged (blocks): out " + flameDx + ", up " + flameDy + ", back " + flameDz), false);
                                    return 1;
                                })))))
                .then(Commands.literal("bright").then(Commands.argument("value", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0.05F, 1.0F)).executes(ctx -> {
                    flameBright = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "value");
                    ctx.getSource().sendSuccess(() -> Component.literal("Boost flame brightness: " + flameBright), false);
                    return 1;
                })))
                .then(Commands.argument("size", IntegerArgumentType.integer(1, 8)).executes(ctx -> set(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "size"), false))
                        .then(Commands.literal("ultra").executes(ctx -> set(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "size"), true)))))));
    }

    private static int set(net.minecraft.commands.CommandSourceStack source, int size, boolean ultra) {
        flameSize = size;
        flameUltra = ultra;
        source.sendSuccess(() -> Component.literal("Boost flame forced on every kart: size " + size + (ultra ? ", ultra" : "") + " (/ctr debug flame off to stop)"), false);
        return 1;
    }

    /**
     * The held power-up's HUD pieces, adjusted by a width and a height in the original's REFERENCE pixels (the 512 x 216 screen; a reference pixel is 0.7361 as wide as tall, so a change of
     * one is the same part of the picture in every window): the icon (its top stays, a taller one grows downwards), the burst behind the box, the box itself and the fruit's burst.
     */
    public static final float[] hudIcon = { -6.0F, 1.0F };
    public static final float[] hudBurst = { -12.0F, 4.0F };
    public static final float[] hudBox = { 0.0F, 0.0F };
    public static final float[] hudFruitBurst = { 0.0F, 0.0F };
    /** The pieces' places: a move right and down in reference pixels, from where the original puts them. */
    public static final float[] hudIconPos = { 0.0F, 0.0F };
    public static final float[] hudBurstPos = { 0.0F, 0.0F };
    public static final float[] hudBoxPos = { 0.0F, 0.0F };
    public static final float[] hudFruitBurstPos = { 0.0F, 0.0F };

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> hudCommands() {
        var f = com.mojang.brigadier.arguments.FloatArgumentType.floatArg(-60.0F, 60.0F);
        return Commands.literal("hud")
                .then(vec2("icon", hudIcon, f))
                .then(vec2("burst", hudBurst, f))
                .then(vec2("box", hudBox, f))
                .then(vec2("fruitburst", hudFruitBurst, f))
                .then(vec2("iconpos", hudIconPos, f))
                .then(vec2("burstpos", hudBurstPos, f))
                .then(vec2("boxpos", hudBoxPos, f))
                .then(vec2("fruitburstpos", hudFruitBurstPos, f))
                .then(Commands.literal("print").executes(ctx -> say(ctx, () -> { }, "hud: icon " + java.util.Arrays.toString(hudIcon) + " burst " + java.util.Arrays.toString(hudBurst) + " box "
                        + java.util.Arrays.toString(hudBox) + " fruitburst " + java.util.Arrays.toString(hudFruitBurst) + "; positions (right, down): icon " + java.util.Arrays.toString(hudIconPos) + " burst "
                        + java.util.Arrays.toString(hudBurstPos) + " box " + java.util.Arrays.toString(hudBoxPos) + " fruitburst " + java.util.Arrays.toString(hudFruitBurstPos) + " (reference pixels)")))
                .then(Commands.literal("reset").executes(ctx -> say(ctx, () -> {
                    hudIcon[0] = -6.0F; hudIcon[1] = 1.0F;
                    hudBurst[0] = -12.0F; hudBurst[1] = 4.0F;
                    hudBox[0] = 0; hudBox[1] = 0;
                    hudFruitBurst[0] = 0; hudFruitBurst[1] = 0;
                    for (float[] pos : new float[][] { hudIconPos, hudBurstPos, hudBoxPos, hudFruitBurstPos }) { pos[0] = 0; pos[1] = 0; }
                }, "hud adjustments reset")));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> vec2(String name, float[] target, com.mojang.brigadier.arguments.FloatArgumentType type) {
        return Commands.literal(name).then(Commands.argument("width", type).then(Commands.argument("height", type).executes(ctx -> {
            target[0] = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "width");
            target[1] = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "height");
            return say(ctx, () -> { }, "hud " + name + " (two numbers, reference pixels) " + java.util.Arrays.toString(target));
        })));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> shieldCommands() {
        var f = com.mojang.brigadier.arguments.FloatArgumentType.floatArg(-3600.0F, 3600.0F);
        return Commands.literal("shield")
                .then(vec3("rot", shieldRot, f))
                .then(vec3("scale", shieldScale, f))
                .then(vec3("offset", shieldOffset, f))
                .then(Commands.literal("mode").then(Commands.literal("kart").executes(ctx -> say(ctx, () -> shieldCamera = false, "kart mode: turns with the kart")))
                        .then(Commands.literal("camera").executes(ctx -> say(ctx, () -> shieldCamera = true, "camera mode: faces the camera"))))
                .then(Commands.literal("table").then(Commands.literal("on").executes(ctx -> say(ctx, () -> shieldTable = true, "the game's opening tables apply")))
                        .then(Commands.literal("off").executes(ctx -> say(ctx, () -> shieldTable = false, "no opening tables: rest size"))))
                .then(Commands.literal("billboard").then(Commands.literal("vertical").executes(ctx -> say(ctx, () -> shieldSphere = false, "camera mode turns about the vertical only (the game's)")))
                        .then(Commands.literal("full").executes(ctx -> say(ctx, () -> shieldSphere = true, "camera mode faces the camera at any height"))))
                .then(Commands.literal("faces").then(Commands.literal("outside").executes(ctx -> say(ctx, () -> shieldFaces = 0, "outside faces only")))
                        .then(Commands.literal("inside").executes(ctx -> say(ctx, () -> shieldFaces = 1, "inside faces only")))
                        .then(Commands.literal("both").executes(ctx -> say(ctx, () -> shieldFaces = 2, "both faces"))))
                .then(Commands.literal("sheen")
                        .then(Commands.literal("angle").then(Commands.argument("degrees", f).executes(ctx -> say(ctx, () -> sheenAngle = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "degrees"), "sheen held at this turn (negative: the game's sweep)"))))
                        .then(Commands.literal("speed").then(Commands.argument("factor", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0.05F, 2.0F)).executes(ctx -> say(ctx, () -> sheenSpeed = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "factor"), "sheen sweep speed set (1 = the game's step a frame)"))))
                        .then(Commands.literal("on").executes(ctx -> say(ctx, () -> sheenOn = true, "sheen on")))
                        .then(Commands.literal("off").executes(ctx -> say(ctx, () -> sheenOn = false, "sheen off")))
                        .then(Commands.literal("print").executes(ctx -> say(ctx, () -> { }, "sheen: angle " + sheenAngle + " speed " + sheenSpeed + " on " + sheenOn))))
                .then(Commands.literal("intensity").then(Commands.argument("value", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(-1.0F, 4.0F)).executes(ctx -> say(ctx, () -> shieldIntensity = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "value"), "bubble strength set (negative: the default)"))))
                .then(Commands.literal("depth").then(Commands.literal("shift").executes(ctx -> say(ctx, () -> shieldDepth = 3, "depth: shifted toward the camera past the kart, depth test on (blocks and entities in front hide it)"))
                                .then(Commands.argument("blocks", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0.0F, 10.0F)).executes(ctx -> say(ctx, () -> { shieldDepth = 3; shieldShift = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "blocks"); }, "depth shift set"))))
                        .then(Commands.literal("auto").executes(ctx -> say(ctx, () -> shieldDepth = 0, "depth: over the ground, hidden by a block between the camera and the bubble")))
                        .then(Commands.literal("on").executes(ctx -> say(ctx, () -> shieldDepth = 1, "depth test on: the world hides parts of the bubble")))
                        .then(Commands.literal("off").executes(ctx -> say(ctx, () -> shieldDepth = 2, "depth test off: the bubble is drawn over everything"))))
                .then(Commands.literal("show").then(Commands.literal("on").executes(ctx -> say(ctx, () -> shieldShow = true, "a bubble is drawn on every kart")))
                        .then(Commands.literal("off").executes(ctx -> say(ctx, () -> shieldShow = false, "only real shields are drawn"))))
                .then(Commands.literal("hold").then(Commands.argument("frame", IntegerArgumentType.integer(-1, 40)).executes(ctx -> {
                    shieldHold = IntegerArgumentType.getInteger(ctx, "frame");
                    return say(ctx, () -> { }, "opening frame held: " + shieldHold + " (-1 plays it; 0 to 7 the opening, 8 and up the pulse)");
                })))
                .then(Commands.literal("print").executes(ctx -> say(ctx, () -> { }, "shield: faces " + shieldFaces + " billboard " + (shieldSphere ? "full" : "vertical") + " rot " + java.util.Arrays.toString(shieldRot) + " scale " + java.util.Arrays.toString(shieldScale) + " offset "
                        + java.util.Arrays.toString(shieldOffset) + " mode " + (shieldCamera ? "camera" : "kart") + " table " + shieldTable + " hold " + shieldHold)))
                .then(Commands.literal("reset").executes(ctx -> say(ctx, () -> {
                    shieldRot[0] = -90; shieldRot[1] = 180; shieldRot[2] = 0;
                    shieldScale[0] = 1.65F; shieldScale[1] = 4.25F; shieldScale[2] = 0.5F;
                    shieldOffset[0] = 0; shieldOffset[1] = 0.55F; shieldOffset[2] = 0;
                    shieldCamera = true;
                    shieldSphere = true;
                    shieldFaces = 0;
                    shieldIntensity = -1.0F;
                    shieldDepth = 3;
                    shieldShift = 1.5F;
                    sheenAngle = -1; sheenSpeed = 0.6F; sheenOn = true;
                    shieldTable = true;
                    shieldHold = -1;
                }, "shield pose reset to the Blender fit")));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> vec3(String name, float[] target, com.mojang.brigadier.arguments.FloatArgumentType type) {
        return Commands.literal(name).then(Commands.argument("x", type).then(Commands.argument("y", type).then(Commands.argument("z", type).executes(ctx -> {
            target[0] = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "x");
            target[1] = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "y");
            target[2] = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "z");
            return say(ctx, () -> { }, "shield " + name + " " + java.util.Arrays.toString(target));
        }))));
    }

    private static int say(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx, Runnable change, String text) {
        change.run();
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
