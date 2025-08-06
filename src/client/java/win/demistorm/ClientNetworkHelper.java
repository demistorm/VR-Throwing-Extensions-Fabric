package win.demistorm;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.math.Vec3d;
import static win.demistorm.VRThrowingExtensions.log;

/**
 * Lightweight façade – all heavy lifting is done in NetworkHelper.
 */
public final class ClientNetworkHelper {
    private ClientNetworkHelper() {}

    public static void initClient() { /* nothing yet */ }

    public static void sendToServer(Vec3d pos, Vec3d velocity, boolean wholeStack) {
        log.debug("ClientNetworkHelper: Sending throw. pos={} vel={} all={}", pos, velocity, wholeStack);
        ClientPlayNetworking.send(new NetworkHelper.ThrowPacket(pos, velocity, wholeStack));
    }
}