package win.demistorm;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Pure glue: hooks up the S2C packet and the disconnect event.
 * Lives in the client source-set so it may import the client networking API.
 */
@Environment(EnvType.CLIENT)
public final class ClientConfigHelper {
    private ClientConfigHelper() {}

    public static void init() {
        // local file (single-player) first
        ConfigHelper.loadOrCreateClientConfig();

        // handle the sync packet
        ClientPlayNetworking.registerGlobalReceiver(
                ConfigHelper.SyncPayload.ID,
                (payload, context) ->
                        ConfigHelper.clientReceivedRemote(payload.json())
        );

        // restore the local copy when leaving the server
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> ConfigHelper.clientDisconnected()
        );
    }
}