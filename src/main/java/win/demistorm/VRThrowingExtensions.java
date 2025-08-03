package win.demistorm;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VRThrowingExtensions implements ModInitializer {
	public static final String MOD_ID = "vr-throwing-extensions";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("VR Throwing Extensions (SERVER) loaded!");

		// Register server-side packet receiving
		NetworkHelper.initServer();
	}
}