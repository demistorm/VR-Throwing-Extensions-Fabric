package win.demistorm;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.math.Vec3d;

/**
 * Lightweight façade – all heavy lifting is done in NetworkHelper.
 */
public final class ClientNetworkHelper {
    private ClientNetworkHelper() {}

    public static void initClient() { /* nothing yet */ }

    public static void sendToServer(Vec3d pos, Vec3d velocity, float yaw, float pitch, float roll) {
        System.out.println("ClientNetworkHelper: Sending throw with rotation. pos=" + pos + " vel=" + velocity + " yaw=" + yaw + " pitch=" + pitch + " roll=" + roll);
        ClientPlayNetworking.send(new NetworkHelper.ThrowPacket(pos, velocity, yaw, pitch, roll));
    }
}