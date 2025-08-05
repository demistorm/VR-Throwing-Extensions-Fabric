package win.demistorm;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * C2S → one packet carrying both start-position and velocity.
 */
public final class NetworkHelper {

    public static final Identifier CHANNEL =
            Identifier.of("vr-throwing-extensions", "throw_packet");

    /* --------------------------------------------------------------------- */
    public record ThrowPacket(Vec3d pos, Vec3d vel) implements CustomPayload {
        public static final Id<ThrowPacket> ID = new Id<>(CHANNEL);

        public static final PacketCodec<RegistryByteBuf, ThrowPacket> CODEC =
                PacketCodec.of(
                        (value, buf) -> {          // WRITE
                            buf.writeDouble(value.pos.x);
                            buf.writeDouble(value.pos.y);
                            buf.writeDouble(value.pos.z);
                            buf.writeDouble(value.vel.x);
                            buf.writeDouble(value.vel.y);
                            buf.writeDouble(value.vel.z);
                        },
                        (buf) -> new ThrowPacket(  // READ
                                new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                                new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()))
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
    private static void handleThrow(PlayerEntity player, ThrowPacket packet) {
        // Sanity checks
        if (player == null || !player.isAlive()) return;

        // What item are we throwing?
        var stackInHand = player.getMainHandStack();
        if (stackInHand.isEmpty() || ModCompat.throwingDisabled(stackInHand)) return;

        // Create projectile
        var proj = new GenericThrownItemEntity(player.getWorld(), player, stackInHand);

        // Position = exact hand position sent by client
        proj.setPosition(packet.pos());

        // Velocity from packet
        proj.setVelocity(packet.vel());

        // Spawn into world
        player.getWorld().spawnEntity(proj);

        // remove ONE item / or entire tool if non-stackable
        if (stackInHand.getCount() > 1) {
            stackInHand.decrement(1);
        } else {
            player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, net.minecraft.item.ItemStack.EMPTY);
        }
    }
}