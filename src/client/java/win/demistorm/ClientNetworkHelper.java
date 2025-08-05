package win.demistorm;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.math.Vec3d;

/**
 * Lightweight façade – all heavy lifting is done in NetworkHelper.
 */
public final class ClientNetworkHelper {
    private ClientNetworkHelper() {}

    public static void initClient() { /* nothing yet */ }

    public static void sendToServer(Vec3d pos, Vec3d velocity, boolean wholeStack) {
        System.out.println("ClientNetworkHelper: Sending throw. pos=" + pos +
                " vel=" + velocity + " all=" + wholeStack);
        ClientPlayNetworking.send(new NetworkHelper.ThrowPacket(pos, velocity, wholeStack));
    }
}