package win.demistorm;

import net.fabricmc.api.ModInitializer;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VRThrowingExtensions implements ModInitializer {

	public static final String MOD_ID = "vr-throwing-extensions";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/* Public so both sides can access it during renderer/logic registration */
	public static EntityType<GenericThrownItemEntity> THROWN_ITEM_TYPE;

	@Override
	public void onInitialize() {
		LOGGER.info("VR Throwing Extensions (SERVER) loaded!");

		// 1. Register EntityType (server + client) - Updated to use new EntityType.Builder
		RegistryKey<EntityType<?>> entityTypeKey = RegistryKey.of(RegistryKeys.ENTITY_TYPE,
				Identifier.of(MOD_ID, "generic_thrown_item"));

		THROWN_ITEM_TYPE = Registry.register(
				Registries.ENTITY_TYPE,
				entityTypeKey.getValue(),
				EntityType.Builder.<GenericThrownItemEntity>create(GenericThrownItemEntity::new, SpawnGroup.MISC)
						.dimensions(0.25f, 0.25f)
						.maxTrackingRange(64)
						.trackingTickInterval(10) // 10 updates per second (20 ticks / 2 = 10 updates/sec), edited to 1
						.build(entityTypeKey));

		// 2. Register server-side network receiver
		NetworkHelper.initServer();
	}
}