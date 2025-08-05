package win.demistorm;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * C2S → one packet carrying both start-position and velocity.
 */
public final class NetworkHelper {

    public static final Identifier CHANNEL =
            Identifier.of("vr-throwing-extensions", "throw_packet");

    /* --------------------------------------------------------------------- */
    public record ThrowPacket(Vec3d pos, Vec3d vel, boolean wholeStack)
            implements CustomPayload {

        public static final Id<ThrowPacket> ID = new Id<>(CHANNEL);

        public static final PacketCodec<RegistryByteBuf, ThrowPacket> CODEC =
                PacketCodec.of(
                        (value, buf) -> {           // WRITE
                            buf.writeDouble(value.pos.x);
                            buf.writeDouble(value.pos.y);
                            buf.writeDouble(value.pos.z);
                            buf.writeDouble(value.vel.x);
                            buf.writeDouble(value.vel.y);
                            buf.writeDouble(value.vel.z);
                            buf.writeBoolean(value.wholeStack);     // <-- NEW
                        },
                        (buf) -> new ThrowPacket(     // READ
                                new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                                new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                                buf.readBoolean())
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    /* --------------------------------------------------------------------- */

    private NetworkHelper() {}

    public static void initServer() {
        // (1) tell Netty how to encode / decode
        PayloadTypeRegistry.playC2S().register(ThrowPacket.ID, ThrowPacket.CODEC);

        // (2) actual packet handler
        ServerPlayNetworking.registerGlobalReceiver(ThrowPacket.ID, (payload, context) -> {
            PlayerEntity sender = context.player();
            context.server().execute(() -> handleThrow(sender, payload));
        });
    }

    /* Server logic: turn packet into real entity */
    private static void handleThrow(PlayerEntity player, ThrowPacket p) {

        if (player == null || !player.isAlive()) return;

        ItemStack handStack = player.getMainHandStack();
        if (handStack.isEmpty() || ModCompat.throwingDisabled(handStack)) return;

        int amount = p.wholeStack() ? handStack.getCount() : 1;

        for (int i = 0; i < amount; i++) {
            GenericThrownItemEntity proj =
                    new GenericThrownItemEntity(player.getWorld(), player, handStack);
            proj.setPosition(p.pos());
            // a tiny ±5° random spread for stack-throws
            Vec3d v = p.vel();
            if (amount > 1) {
                double spread = 0.1;
                v = v.add(
                        (player.getRandom().nextDouble() - 0.5) * spread,
                        (player.getRandom().nextDouble() - 0.5) * spread,
                        (player.getRandom().nextDouble() - 0.5) * spread);
            }
            proj.setVelocity(v);
            player.getWorld().spawnEntity(proj);
        }

        /* remove items that were thrown */
        if (amount == handStack.getCount()) {
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        } else {
            handStack.decrement(amount);
        }
    }
}