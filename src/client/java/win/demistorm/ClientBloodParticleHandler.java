package win.demistorm;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class ClientBloodParticleHandler {

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(
                NetworkHelper.BloodParticlePacket.ID,
                (payload, context) -> {
                    if (!ClientOnlyConfig.ACTIVE.bloodEffect) return;

                    context.client().execute(() -> spawnBloodParticles(payload.pos(), payload.velocity()));
                });
    }

    private static void spawnBloodParticles(Vec3d pos, Vec3d velocity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        for (int i = 0; i < 8; i++) {
            double ox = (Math.random() - 0.5) * 0.3;
            double oy = (Math.random() - 0.5) * 0.3;
            double oz = (Math.random() - 0.5) * 0.3;
            double vx = velocity.x + (Math.random() - 0.5) * 0.2;
            double vy = velocity.y + (Math.random() - 0.5) * 0.2;
            double vz = velocity.z + (Math.random() - 0.5) * 0.2;

            client.world.addParticleClient(ParticleTypes.DAMAGE_INDICATOR,
                    pos.x + ox, pos.y + oy, pos.z + oz, vx, vy, vz);
        }
    }

    private ClientBloodParticleHandler() {}
}