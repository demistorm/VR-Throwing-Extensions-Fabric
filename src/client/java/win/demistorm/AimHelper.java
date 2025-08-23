package win.demistorm;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.Optional;

import static win.demistorm.VRThrowingExtensions.log;

/**
 * Optimized aim assist that balances accuracy with efficiency.
 * Uses smart ballistic calculation for better center-mass targeting.
 */
public final class AimHelper {

    // Tunable parameters
    private static final double MAX_ASSIST_DISTANCE = 25.0;
    private static final double MAX_ASSIST_ANGLE = 35.0;
    private static final double ASSIST_STRENGTH = 0.5;
    private static final double MAX_PREDICTION_TIME = 2.5;

    // Minecraft physics constants (based on research data)
    private static final double GRAVITY_ACCELERATION = 0.04; // blocks/tick² (roughly matches thrown items)
    private static final double TICKS_PER_SECOND = 20.0;

    /**
     * Applies smart aim assist with ballistic trajectory compensation.
     */
    public static Vec3d applyAimAssist(ClientPlayerEntity player, Vec3d origin, Vec3d originalVelocity) {
        if (!ConfigHelper.CLIENT.aimAssist) {
            return originalVelocity;
        }

        Optional<TargetInfo> bestTarget = findBestTarget(player, origin, originalVelocity);

        if (bestTarget.isEmpty()) {
            log.debug("[Aim Assist] No suitable target found");
            return originalVelocity;
        }

        TargetInfo target = bestTarget.get();
        Vec3d assistedVelocity = calculateBallisticAssist(origin, originalVelocity, target);

        double adjustment = assistedVelocity.subtract(originalVelocity).length();
        log.debug("[Aim Assist] Target: {}, distance: {}, time: {}s, adjustment: {}",
                target.entity.getName().getString(), target.distance, target.interceptTime, adjustment);

        return assistedVelocity;
    }

    /**
     * Efficient target finding with smart filtering.
     */
    private static Optional<TargetInfo> findBestTarget(ClientPlayerEntity player, Vec3d origin, Vec3d velocity) {
        Vec3d throwDirection = velocity.normalize();
        double throwSpeed = velocity.length();

        // Optimized search box
        Box searchBox = Box.of(origin, MAX_ASSIST_DISTANCE * 2, MAX_ASSIST_DISTANCE * 2, MAX_ASSIST_DISTANCE * 2);

        List<LivingEntity> candidates = player.getWorld()
                .getEntitiesByClass(LivingEntity.class, searchBox, entity ->
                        entity != player && entity.isAlive() && !entity.isSpectator());

        TargetInfo bestTarget = null;
        double bestScore = 0.0;

        for (LivingEntity entity : candidates) {
            TargetInfo targetInfo = evaluateTarget(entity, origin, throwDirection, throwSpeed);

            if (targetInfo != null) {
                double score = calculateTargetScore(targetInfo);
                if (score > bestScore) {
                    bestScore = score;
                    bestTarget = targetInfo;
                }
            }
        }

        return Optional.ofNullable(bestTarget);
    }

    /**
     * Smart target evaluation with optimized ballistic intercept calculation.
     */
    private static TargetInfo evaluateTarget(LivingEntity entity, Vec3d origin, Vec3d throwDirection, double throwSpeed) {
        Vec3d targetPos = entity.getPos().add(0, entity.getHeight() / 2, 0); // Center mass
        Vec3d toTarget = targetPos.subtract(origin);
        double distance = toTarget.length();

        // Quick distance and angle filtering
        if (distance > MAX_ASSIST_DISTANCE) return null;

        double angle = Math.toDegrees(Math.acos(MathHelper.clamp(
                throwDirection.dotProduct(toTarget.normalize()), -1.0, 1.0)));
        if (angle > MAX_ASSIST_ANGLE) return null;

        // Smart intercept calculation - only 3-5 iterations instead of 25+
        Vec3d entityVelocity = entity.getVelocity();
        double interceptTime = calculateOptimalInterceptTime(origin, targetPos, entityVelocity, throwSpeed);

        if (interceptTime < 0 || interceptTime > MAX_PREDICTION_TIME) return null;

        // Calculate predicted position and trajectory feasibility
        Vec3d predictedPos = targetPos.add(entityVelocity.multiply(interceptTime * TICKS_PER_SECOND));
        double trajectoryConfidence = calculateTrajectoryFeasibility(origin, predictedPos, throwSpeed, interceptTime);

        if (trajectoryConfidence < 0.3) return null; // Skip impossible shots

        return new TargetInfo(entity, targetPos, predictedPos, distance, angle, trajectoryConfidence, interceptTime);
    }

    /**
     * Optimized intercept time calculation using smart initial guess + refinement.
     * Much faster than brute force iteration.
     */
    private static double calculateOptimalInterceptTime(Vec3d origin, Vec3d targetPos, Vec3d targetVel, double throwSpeed) {
        // Smart initial guess based on simple linear distance
        Vec3d displacement = targetPos.subtract(origin);
        double linearDistance = displacement.length();
        double initialGuess = linearDistance / throwSpeed / TICKS_PER_SECOND;

        // Quick refinement: test initial guess ±50% in just 5 steps
        double bestTime = -1;
        double bestError = Double.MAX_VALUE;

        double[] testTimes = {
                initialGuess * 0.5,
                initialGuess * 0.75,
                initialGuess,
                initialGuess * 1.25,
                initialGuess * 1.5
        };

        for (double testTime : testTimes) {
            if (testTime <= 0 || testTime > MAX_PREDICTION_TIME) continue;

            Vec3d predictedTarget = targetPos.add(targetVel.multiply(testTime * TICKS_PER_SECOND));
            Vec3d requiredVel = calculateRequiredBallisticVelocity(origin, predictedTarget, testTime);

            double speedError = Math.abs(requiredVel.length() - throwSpeed);
            if (speedError < bestError) {
                bestError = speedError;
                bestTime = testTime;
            }
        }

        // Accept solution if it's reasonably close to our throw speed
        return bestError < throwSpeed * 0.4 ? bestTime : -1;
    }

    /**
     * Calculate required initial velocity for ballistic trajectory.
     * This accounts for gravity drop over time.
     */
    private static Vec3d calculateRequiredBallisticVelocity(Vec3d origin, Vec3d target, double flightTimeSeconds) {
        Vec3d displacement = target.subtract(origin);
        double flightTimeTicks = flightTimeSeconds * TICKS_PER_SECOND;

        // Horizontal components (no forces acting)
        double vx = displacement.x / flightTimeTicks;
        double vz = displacement.z / flightTimeTicks;

        // Vertical component (compensate for gravity)
        // y = v0y * t - 0.5 * g * t²
        // Solve for v0y: v0y = (y + 0.5 * g * t²) / t
        double vy = (displacement.y + 0.5 * GRAVITY_ACCELERATION * flightTimeTicks * flightTimeTicks) / flightTimeTicks;

        return new Vec3d(vx, vy, vz);
    }

    /**
     * Quick check if a ballistic trajectory is physically feasible.
     */
    private static double calculateTrajectoryFeasibility(Vec3d origin, Vec3d target, double throwSpeed, double timeSeconds) {
        Vec3d requiredVel = calculateRequiredBallisticVelocity(origin, target, timeSeconds);

        double requiredSpeed = requiredVel.length();

        // Higher confidence for trajectories that match our throw speed closely
        return Math.min(throwSpeed, requiredSpeed) / Math.max(throwSpeed, requiredSpeed);
    }

    /**
     * Calculate ballistic-compensated assist velocity.
     */
    private static Vec3d calculateBallisticAssist(Vec3d origin, Vec3d originalVelocity, TargetInfo target) {
        // Calculate the ideal ballistic velocity
        Vec3d idealVelocity = calculateRequiredBallisticVelocity(origin, target.predictedPos, target.interceptTime);

        // Scale ideal velocity to reasonable speed (prevent crazy fast throws)
        double originalSpeed = originalVelocity.length();
        double idealSpeed = idealVelocity.length();

        if (idealSpeed > originalSpeed * 1.5) {
            // If ideal is much faster, scale it down but preserve direction
            idealVelocity = idealVelocity.normalize().multiply(originalSpeed * 1.2);
        }

        // Blend with confidence-based strength
        double effectiveStrength = ASSIST_STRENGTH * target.confidence;
        return blendVelocities(originalVelocity, idealVelocity, effectiveStrength);
    }

    /**
     * Smooth velocity blending.
     */
    private static Vec3d blendVelocities(Vec3d current, Vec3d target, double strength) {
        return current.multiply(1.0 - strength).add(target.multiply(strength));
    }

    /**
     * Target scoring with priority system.
     */
    private static double calculateTargetScore(TargetInfo target) {
        double baseScore = target.confidence;

        // Distance bonus (closer is better)
        double distanceScore = 1.0 - (target.distance / MAX_ASSIST_DISTANCE);

        // Angle bonus (more aligned with throw is better)
        double angleScore = 1.0 - (target.angle / MAX_ASSIST_ANGLE);

        // Target type multipliers
        double typeMultiplier = 0.7; // Default for animals, etc.
        if (target.entity instanceof Monster) typeMultiplier = 1.0;      // Prefer hostile mobs
        if (target.entity instanceof PlayerEntity) typeMultiplier = 0.9;  // Then players

        return (baseScore + distanceScore + angleScore) / 3.0 * typeMultiplier;
    }

    /**
         * Enhanced target information.
         */
        private record TargetInfo(LivingEntity entity, Vec3d currentPos, Vec3d predictedPos, double distance, double angle,
                                  double confidence, double interceptTime) {
    }

    private AimHelper() {} // Utility class
}