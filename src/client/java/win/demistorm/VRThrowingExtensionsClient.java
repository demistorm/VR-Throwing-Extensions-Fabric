package win.demistorm;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Client mod initializer
public class VRThrowingExtensionsClient implements ClientModInitializer {

	public static final Logger LOGGER = LoggerFactory.getLogger("vr-throwing(client)");

	// Initializing everything
	@Override
	public void onInitializeClient() {
		LOGGER.info("VR Throwing Extensions (CLIENT) starting…");

		// Initialize client-side networking
		ClientNetworkHelper.initClient();

		// Load throwing logic
		ThrowHelper.init();

		// Well you can see what this does, it's right under here
		registerClientEvents();
	}

	// Cancel block breaking when throwing is active
	public static void registerClientEvents() {
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			// Only cancel if actively capturing a potential throw
			if (ThrowHelper.cancellingBreaks()) {
				return ActionResult.FAIL; // Stop breaking
			}
			return ActionResult.PASS; // Allow normal breaking
		});
	}
}