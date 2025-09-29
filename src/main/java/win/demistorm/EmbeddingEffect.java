package win.demistorm;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import static win.demistorm.VRThrowingExtensions.log;

// Embeds projectiles into entities on hit and maintains the embedded state.
public final class EmbeddingEffect {

    // How the rolled X rotation should settle after impact (in degrees, positive X)
    public static final float targetRollDegX = 85.0f;
    // How far past the collision point the projectile embeds into the entity
    public static final double embedDepth = 0.30;
    // How quickly the roll converges to target per tick (deg/tick)
    public static final float rollApproachPerTick = 20.0f;
    private static final float forwardSpinSpeedDegPerTick = 15.0f;

    private EmbeddingEffect() {}

    // Called on first entity hit to start embedding if conditions pass
    public static void startEmbedding(ThrownItemEntity proj, EntityHitResult hit) {
        if (proj.getWorld().isClient()) return;
        Entity target = hit.getEntity();
        if (!(target instanceof LivingEntity living)) {
            proj.dropAndDiscard();
            return;
        }

        // Direction at impact
        Vec3d dir = proj.getVelocity();
        if (dir.lengthSquared() < 1.0e-6) dir = hit.getPos().subtract(proj.getPos());
        if (dir.lengthSquared() < 1.0e-6) dir = new Vec3d(1, 0, 0);
        dir = dir.normalize();

        // Deeper embed from the hit point
        Vec3d hitPos = hit.getPos();
        Vec3d embedPos = hitPos.add(dir.multiply(embedDepth));

        // Base yaw/pitch as in flight rendering
        float yaw = (float)(MathHelper.atan2(dir.z, dir.x) * 180.0 / Math.PI);
        float pitch = (float)(MathHelper.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * 180.0 / Math.PI);

        // Visual hand tilt (same as used in flight: rotation around Z by -handRoll)
        float tiltDeg = -proj.getHandRoll();

        // Initial X roll continues the inflight spin for seamless transition
        float initialXRollDeg = (proj.age * forwardSpinSpeedDegPerTick) % 360.0f;

        // Fixed offset to lock to this exact embed point
        Vec3d localOffset = embedPos.subtract(target.getPos());
        proj.beginEmbedding(living, localOffset, yaw, pitch, tiltDeg, initialXRollDeg);

        // Sound effect
        proj.getWorld().playSound(null, proj.getBlockPos(),
                SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.PLAYERS, 0.45f, 0.8f);

        // DEBUG
        log.debug("[Embed] Projectile {} embedded into {} at {} (+{}), yaw={}, pitch={}, tilt={}, xRollStart={}",
                proj.getId(), target.getName().getString(), hitPos,
                String.format("%.2f", embedDepth),
                String.format("%.1f", yaw), String.format("%.1f", pitch),
                String.format("%.1f", tiltDeg), String.format("%.1f", initialXRollDeg));
    }

    // Per tick embed tracker
    // Keeps it stuck at the original hit point on the target entity and animates roll toward a stable angle.
    public static void tickEmbedded(ThrownItemEntity proj) {
        if (!proj.isEmbedded()) return;

        if (proj.getWorld().isClient()) {
            return;
        }

        Entity target = proj.getEmbeddedTarget();
        if (!(target instanceof LivingEntity living) || !living.isAlive() || target.isRemoved()) {
            // If the entity died or disappeared, drop the item
            log.debug("[Embed] Host entity lost; dropping projectile {}", proj.getId());
            proj.clearEmbedding(); // Clears state first to prevent double-drop
            proj.dropAndDiscard();
            return;
        }

        // Maintain position at the locked local offset relative to the host's base position
        Vec3d base = target.getPos();
        Vec3d newPos = base.add(proj.getEmbeddedOffset());
        proj.setPosition(newPos);

        // Freeze physics
        proj.setVelocity(Vec3d.ZERO);
        proj.setNoGravity(true);

        // Approach the nearest 85 degrees (mod 360) and stop there
        float current = proj.getEmbedRoll();
        float baseAngle = targetRollDegX;
        float nearestTarget = baseAngle + 360.0f * Math.round((current - baseAngle) / 360.0f);
        float diff = nearestTarget - current;

        if (Math.abs(diff) > 0.01f) {
            float step = Math.copySign(rollApproachPerTick, diff);
            float next = Math.abs(diff) <= rollApproachPerTick ? nearestTarget : current + step;
            proj.setEmbedRoll(next);
        }
    }


    // When catching starts, release from embedding
    public static void releaseEmbedding(ThrownItemEntity proj) {
        if (!proj.isEmbedded()) return;
        proj.clearEmbedding();
        proj.setNoGravity(true);
        proj.setVelocity(Vec3d.ZERO);
        log.debug("[Embed] Released projectile {} from embed state for catching", proj.getId());
    }
}
