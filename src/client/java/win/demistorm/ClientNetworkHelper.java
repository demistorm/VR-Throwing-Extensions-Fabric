package win.demistorm;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.math.Vec3d;
import static win.demistorm.VRThrowingExtensions.log;

// Forwards client network information to NetworkHelper
public final class ClientNetworkHelper {
    private ClientNetworkHelper() {}

    public static void sendToServer(Vec3d pos, Vec3d velocity, boolean wholeStack, float rollDeg) {
        log.debug("ClientNetworkHelper: Sending throw. pos={} vel={} all={}", pos, velocity, wholeStack);
        ClientPlayNetworking.send(new NetworkHelper.ThrowPacket(pos, velocity, wholeStack, rollDeg));
    }
}