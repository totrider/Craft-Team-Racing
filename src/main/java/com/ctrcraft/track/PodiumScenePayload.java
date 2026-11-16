package com.ctrcraft.track;

import java.util.ArrayList;
import java.util.List;

import com.ctrcraft.CtrCraftMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to every racer: the podium ceremony has begun (the podium is full, or the time is up) and where the three podium places are. The racers on a podium watch themselves from the front
 * (the camera knows its own kart); everybody else's camera looks over all three places.
 */
public record PodiumScenePayload(List<Spot> spots) implements CustomPacketPayload {
    /** A podium place: where its kart stands (the middle of the node, the floor of its block) and the way it faces, in degrees, and which place it is. */
    public record Spot(double x, double y, double z, float yaw, int place) {}

    public static final Type<PodiumScenePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "podium_scene"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PodiumScenePayload> STREAM_CODEC = StreamCodec.of(PodiumScenePayload::write, PodiumScenePayload::read);

    private static void write(RegistryFriendlyByteBuf b, PodiumScenePayload p) {
        b.writeVarInt(p.spots.size());
        for (Spot s : p.spots) {
            b.writeDouble(s.x());
            b.writeDouble(s.y());
            b.writeDouble(s.z());
            b.writeFloat(s.yaw());
            b.writeVarInt(s.place());
        }
    }

    private static PodiumScenePayload read(RegistryFriendlyByteBuf b) {
        int n = Math.min(b.readVarInt(), 3);
        List<Spot> spots = new ArrayList<>(n);
        for (int i = 0; i < n; i++) spots.add(new Spot(b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readVarInt()));
        return new PodiumScenePayload(spots);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PodiumScenePayload p, IPayloadContext context) {
        context.enqueueWork(() -> com.ctrcraft.client.PodiumCamera.setScene(p.spots()));
    }
}
