package com.aetherdragon.network;

import com.aetherdragon.AetherDragon;
import com.aetherdragon.entity.TamedAetherDragonEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = AetherDragon.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class DragonMountNetwork {
    private DragonMountNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(LaserPayload.TYPE, LaserPayload.STREAM_CODEC, LaserPayload::handle);
    }

    public record LaserPayload(boolean firing) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<LaserPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AetherDragon.MOD_ID, "mount_laser"));
        public static final StreamCodec<ByteBuf, LaserPayload> STREAM_CODEC = ByteBufCodecs.BOOL.map(LaserPayload::new, LaserPayload::firing);

        @Override
        public CustomPacketPayload.Type<LaserPayload> type() {
            return TYPE;
        }

        private void handle(IPayloadContext context) {
            if (context.player().getVehicle() instanceof TamedAetherDragonEntity dragon && dragon.getControllingPassenger() == context.player()) {
                dragon.setLaserFiring(this.firing);
            }
        }
    }
}
