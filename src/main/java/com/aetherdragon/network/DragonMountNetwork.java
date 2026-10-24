package com.aetherdragon.network;

import com.aetherdragon.AetherDragon;
import com.aetherdragon.entity.TamedAetherDragonEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class DragonMountNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AetherDragon.MOD_ID, "mount"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private DragonMountNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(LaserMessage.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LaserMessage::encode)
                .decoder(LaserMessage::decode)
                .consumerMainThread(LaserMessage::handle)
                .add();
    }

    public record LaserMessage(boolean firing) {
        private void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(this.firing);
        }

        private static LaserMessage decode(FriendlyByteBuf buf) {
            return new LaserMessage(buf.readBoolean());
        }

        private void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null && player.getVehicle() instanceof TamedAetherDragonEntity dragon && dragon.getControllingPassenger() == player) {
                dragon.setLaserFiring(this.firing);
            }
        }
    }
}
