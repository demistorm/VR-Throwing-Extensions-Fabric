package win.demistorm;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

// Server networking
public final class NetworkHelper {

    public static final Identifier CHANNEL = Identifier.of("vr-throwing-extensions", "throw_packet");

    private NetworkHelper() {}

    // Custom payload record for the throw packet
    public record ThrowPacket(Vec3d velocity) implements CustomPayload {
        public static final Id<ThrowPacket> ID = new Id<>(CHANNEL);

        public static final PacketCodec<RegistryByteBuf, ThrowPacket> CODEC = PacketCodec.of(
                (value, buf) -> {
                    buf.writeDouble(value.velocity.x);
                    buf.writeDouble(value.velocity.y);
                    buf.writeDouble(value.velocity.z);
                },
                (buf) -> new ThrowPacket(new Vec3d(
                        buf.readDouble(),
                        buf.readDouble(),
                        buf.readDouble()
                ))
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // Initializes server logic
    public static void initServer() {
        // Register the payload type (both client and server registration)
        PayloadTypeRegistry.playC2S().register(ThrowPacket.ID, ThrowPacket.CODEC);

        // DEBUG
        System.out.println("NetworkHelper: Registered payload type " + ThrowPacket.ID);

        // Register the server receiver
        ServerPlayNetworking.registerGlobalReceiver(ThrowPacket.ID, (payload, context) -> {
            // DEBUG
            System.out.println("NetworkHelper: Received packet with velocity " + payload.velocity());

            // Execute on server thread
            context.server().execute(() -> {
                // DEBUG
                System.out.println("NetworkHelper: Executing on server thread, sending message to player");

                // DEBUG
                context.player().sendMessage
                        (Text.literal("Got throw velocity: " + payload.velocity()), false);
            });
        });
        // DEBUG
        System.out.println("NetworkHelper: Registered server receiver");
    }
}