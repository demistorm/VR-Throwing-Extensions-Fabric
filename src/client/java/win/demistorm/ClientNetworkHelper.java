package win.demistorm;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.util.math.Vec3d;

// Client-only networking
public final class ClientNetworkHelper {

    private ClientNetworkHelper() {}

    // Initialize clientside networking
    public static void initClient() {
        // This method exists for future client-side networking setup
    }

    // Sends throw packet to server
    public static void sendToServer(Vec3d velocity) {
        System.out.println("ClientNetworkHelper: Attempting to send packet with velocity " + velocity);
        try {
            ClientPlayNetworking.send(new NetworkHelper.ThrowPacket(velocity));
            System.out.println("ClientNetworkHelper: Packet sent successfully");
        } catch (Exception e) {
            System.out.println("ClientNetworkHelper: Failed to send packet: " + e.getMessage());
            e.printStackTrace();
        }
    }
}