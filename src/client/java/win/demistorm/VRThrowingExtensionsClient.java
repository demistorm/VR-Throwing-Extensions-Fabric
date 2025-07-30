package win.demistorm;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivecraft.api.VRAPI;
import org.vivecraft.api.data.VRBodyPartData;
import org.vivecraft.api.data.VRPose;

/**
 * Client-entry side of VR-Throwing Extensions.
 * Handles ONLY client-owned logic such as VR polling and debug rendering.
 */
public class VRThrowingExtensionsClient implements ClientModInitializer {

	private static final Logger LOGGER = LoggerFactory.getLogger("vr-throwing-extensions/client");

	// Tick counter used to throttle Vivecraft position reporting.
	private int tickCount = 0;

	@Override
	public void onInitializeClient() {
		LOGGER.info("VR Throwing Extensions client init – yarn @ 1.21.5");

		// Register tick callback every single client tick.
		// Inside we’ll check every 20 ticks to avoid chat spam.
		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
	}

	private void onClientTick(MinecraftClient client) {
		if (!isVivecraftPresent()) {
			// Silently return; API not present.
			return;
		}

		// Only proceed if player exists and is in VR.
		if (client.player == null || !VRAPI.instance().isVRPlayer(client.player)) {
			return;
		}

		tickCount++;
		if (tickCount % 20 != 0) {
			return;
		}

		reportArmPositions(client);
	}

	/**
	 * Prints controller position & direction to both chat and the log.
	 */
	private void reportArmPositions(MinecraftClient mc) {
		VRPose pose = VRAPI.instance().getVRPose(mc.player);
		if (pose == null) return;

		for (net.minecraft.util.Hand hand : net.minecraft.util.Hand.values()) {
			VRBodyPartData handData = pose.getHand(hand);   // API method exposed in context
			if (handData == null) continue;

			Vec3d pos = handData.getPos();
			Vec3d dir = handData.getDir();

			String label = hand == net.minecraft.util.Hand.MAIN_HAND ? "Main" : "Off";

            assert mc.player != null;
            mc.player.sendMessage(
					Text.literal(String.format("%s hand: [%.2f %.2f %.2f] | [%.2f %.2f %.2f]",
							label,
							pos.x, pos.y, pos.z,
							dir.x, dir.y, dir.z)),
					false);

			LOGGER.info("{} hand pos: {}, {}, {} | dir: {}, {}, {}",
					label, pos.x, pos.y, pos.z, dir.x, dir.y, dir.z);
		}
	}

	/**
	 * Safe, Forge-Free check to see if Vivecraft API is present.
	 */
	private static boolean isVivecraftPresent() {
		try {
			Class.forName("org.vivecraft.api.client.VRClientAPI");
			return true;
		} catch (ClassNotFoundException ignored) {
			return false;
		}
	}
}