package win.demistorm;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

/**
 * Enhanced boomerang logic with smoother physics and better origin detection
 * All state lives inside the {@link ThrownItemEntity} instances,
 * this class just keeps the helper maths and "what is allowed to bounce" list.
 */
public final class BoomerangEffect {
    /* ------------------------------------------------------------ */
    /*  TUNABLE PARAMETERS                                           */
    /* ------------------------------------------------------------ */
    // Base return speed - easily tunable multiplier for overall speed
    public static final double BASE_RETURN_SPEED = 0.05; // Adjust this to make all returns faster/slower

    // Dynamic speed scaling - fully configurable curve
    public static final double CLOSE_DISTANCE = 3.0;     // Distance considered "close"
    public static final double FAR_DISTANCE = 30.0;      // Distance considered "far"
    public static final double CLOSE_SPEED_MULT = 0.7;   // Speed multiplier for close throws
    public static final double FAR_SPEED_MULT = 1.8;     // Speed multiplier for far throws

    // Optional: Non-linear scaling factor (1.0 = linear, >1.0 = exponential curve)
    public static final double SCALING_CURVE = 1.2;
    /* ------------------------------------------------------------ */
    /*  ELIGIBLE ITEMS                                               */
    /* ------------------------------------------------------------ */
    public static final Set<Item> bounceTools = new HashSet<>();
    static {
        Registries.ITEM.stream().filter(i -> !Registries.ITEM.getId(i)
                        .equals(Identifier.of("minecraft","air")))
                .forEach(bounceTools::add);
    }

    public static boolean canBounce(Item i) {
        return bounceTools.contains(i);
    }

    /*  ENHANCED ACTIVATION                                          */
    /* ------------------------------------------------------------ */
    /**
     * Starts the boomerang-style return path with improved physics.
     * Uses the original throw position and stored hand roll for realistic trajectory.
     */
    public static void startBounce(ThrownItemEntity proj) {
        proj.hasBounced = true;
        proj.bounceActive = true;

        Vec3d currentPos = proj.getPos();
        Vec3d toOrigin = proj.originalThrowPos.subtract(currentPos);
        double distanceToOrigin = toOrigin.length();

        // Avoid division by zero and handle very close origins
        if (distanceToOrigin < 0.1) {
            VRThrowingExtensions.log.debug("[Boomerang] Too close to origin, dropping normally");
            proj.bounceActive = false;
            return;
        }

        Vec3d dir = toOrigin.normalize();

        // Convert stored hand roll into radians for spin effect
        float rollRad = (float) Math.toRadians(proj.getHandRoll());

        // Compute lift axis from spin: perpendicular to motion in roll direction
        Vec3d sideAxis = new Vec3d(-dir.z, 0, dir.x).normalize();
        Vec3d liftAxis = dir.crossProduct(sideAxis);

        // Enhanced lift calculation based on distance and roll
        double liftStrength = MathHelper.sin(rollRad) * 0.12; // Slightly reduced for stability
        Vec3d lift = liftAxis.multiply(liftStrength);

        // Calculate dynamic return speed based on distance and base speed
        double speedMultiplier = calculateSpeedMultiplier(distanceToOrigin);
        double bounceSpeed = BASE_RETURN_SPEED * speedMultiplier;

        // Calculate initial return velocity with arc
        Vec3d baseVel = dir.multiply(bounceSpeed);
        Vec3d finalVel = baseVel.add(lift);

        // Add slight upward component for more natural arc
        finalVel = finalVel.add(new Vec3d(0, 0.02, 0));

        proj.setVelocity(finalVel);
        proj.setNoGravity(true); // No gravity during return flight for smoother arc

        // Play boomerang sound
        if (!proj.getWorld().isClient()) {
            proj.getWorld().playSound(null, proj.getBlockPos(),
                    SoundEvents.ENTITY_GENERIC_BIG_FALL, SoundCategory.PLAYERS,
                    0.6f, 1.5f);
        }

        VRThrowingExtensions.log.debug(
                "[Boomerang] Projectile {} started return flight. Distance={}, Roll={}°, Speed={} (mult={}), Lift={}",
                proj.getId(), String.format("%.2f", distanceToOrigin),
                String.format("%.1f", proj.getHandRoll()),
                String.format("%.3f", bounceSpeed),
                String.format("%.2f", speedMultiplier),
                liftAxis);
    }

    /**
     * Calculates dynamic speed multiplier based on throw distance.
     * Uses a smooth curve that can be easily tuned with the constants above.
     * Examples with default settings:
     * - 3 blocks: 70% speed (close)
     * - 10 blocks: ~95% speed
     * - 20 blocks: ~140% speed
     * - 30+ blocks: 180% speed (far)
     */
    private static double calculateSpeedMultiplier(double distance) {
        // Handle edge cases
        if (distance <= CLOSE_DISTANCE) {
            return CLOSE_SPEED_MULT;
        } else if (distance >= FAR_DISTANCE) {
            return FAR_SPEED_MULT;
        }

        // Calculate normalized position between close and far (0.0 to 1.0)
        double normalizedDistance = (distance - CLOSE_DISTANCE) / (FAR_DISTANCE - CLOSE_DISTANCE);

        // Apply optional curve scaling for non-linear progression
        normalizedDistance = Math.pow(normalizedDistance, SCALING_CURVE);

        // Linear interpolation between close and far multipliers
        return CLOSE_SPEED_MULT + normalizedDistance * (FAR_SPEED_MULT - CLOSE_SPEED_MULT);
    }

    /**
     * Enhanced per-tick update with improved steering and termination.
     * Returns TRUE when the journey should finish.
     */
    public static boolean tickReturn(ThrownItemEntity proj) {
        Vec3d currentPos = proj.getPos();
        Vec3d toOrigin = proj.originalThrowPos.subtract(currentPos);
        double distSq = toOrigin.lengthSquared();

        // Expanded completion radius for more reliable detection
        // Also check if the projectile is moving away from the origin (overshot)
        boolean reachedOrigin = false;

        if (distSq < 0.36) { // ~0.6 block radius - close enough to origin
            reachedOrigin = true;
            VRThrowingExtensions.log.debug("[Boomerang] Projectile {} reached origin (dist={})",
                    proj.getId(), String.format("%.3f", Math.sqrt(distSq)));
        } else {
            // Check if projectile overshot the origin (moving away from it)
            Vec3d currentVel = proj.getVelocity();
            if (currentVel.length() > 0.01) {
                // Dot product: if positive, velocity is pointing away from origin
                double dotProduct = currentVel.normalize().dotProduct(toOrigin.normalize());
                if (dotProduct < -0.8 && distSq < 4.0) { // Moving away and reasonably close
                    reachedOrigin = true;
                    VRThrowingExtensions.log.debug("[Boomerang] Projectile {} overshot origin (dist={}, dot={})",
                            proj.getId(), String.format("%.3f", Math.sqrt(distSq)),
                            String.format("%.3f", dotProduct));
                }
            }
        }

        if (reachedOrigin) {
            return true;
        }

        Vec3d wantDir = toOrigin.normalize();
        Vec3d currentVel = proj.getVelocity();

        // Enhanced steering with speed preservation
        // More aggressive steering when far, gentler when close
        double steerStrength = MathHelper.clamp(distSq * 0.15, 0.05, 0.15);
        double dampFactor = 0.92; // Slight damping for stability

        // Calculate new velocity with improved steering
        Vec3d steerForce = wantDir.multiply(steerStrength);
        Vec3d newVel = currentVel.multiply(dampFactor).add(steerForce);

        // Calculate dynamic speed limits based on distance (use the same system as initial bounce)
        double distance = Math.sqrt(distSq);
        double speedMultiplier = calculateSpeedMultiplier(distance);
        double targetSpeed = BASE_RETURN_SPEED * speedMultiplier;
        double maxSpeed = targetSpeed * 1.5; // Allow 50% over target for momentum
        double minSpeed = targetSpeed * 0.4; // Minimum 40% of target to avoid stalling

        // Maintain reasonable speed bounds
        double newSpeed = newVel.length();
        if (newSpeed > maxSpeed) {
            newVel = newVel.normalize().multiply(maxSpeed);
        } else if (newSpeed < minSpeed && newSpeed > 0.001) {
            newVel = newVel.normalize().multiply(minSpeed);
        }

        proj.setVelocity(newVel);

        // Debug logging every 20 ticks
        if (proj.age % 20 == 0) {
            VRThrowingExtensions.log.debug("[Boomerang] Projectile {} returning: dist={}, speed={}, target={}",
                    proj.getId(), String.format("%.2f", distance),
                    String.format("%.3f", newVel.length()),
                    String.format("%.3f", targetSpeed));
        }

        return false;
    }

    private BoomerangEffect() { }
}