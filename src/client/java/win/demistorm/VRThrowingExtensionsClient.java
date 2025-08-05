package win.demistorm;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
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

		/* renderer: let vanilla handle the model */
		EntityRendererRegistry.register(
				VRThrowingExtensions.THROWN_ITEM_TYPE,
				GenericThrownItemRenderer::new
		);
	}

	// Cancel block breaking when throwing is active
	public static void registerClientEvents() {
		// cancel LEFT-CLICK block break
		AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) ->
				ThrowHelper.cancellingBreaks() ? ActionResult.FAIL : ActionResult.PASS);

		// cancel RIGHT-CLICK place / use block
		UseBlockCallback.EVENT.register((player, world, hand, hit) ->
				ThrowHelper.cancellingUse() ? ActionResult.FAIL : ActionResult.PASS);

		// cancel RIGHT-CLICK air (item use without block)
		UseItemCallback.EVENT.register((player, world, hand) ->
				ThrowHelper.cancellingUse() ? ActionResult.FAIL : ActionResult.PASS);
	}
}