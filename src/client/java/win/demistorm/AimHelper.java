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

// Aim assist joys
public final class AimHelper {

    // Tunable parameters
    private static final double maxAssistDistance = 25.0;
    private static final double assistViewAngle = 35.0;
    private static final double assistStrength = 0.5;
    private static final double maxPredictionTime = 2.5;

    /*
    Had gravityCalc set to 0.04 however based on my research it should be more like 0.9? That did not work,
    am trying something more modest like a 50% bump up to 0.06.

    Testing for 0.04. RESULT = Works somewhat effectively, does not handle vertical great, but otherwise mostly works.
    Testing for 0.9 needed. RESULT = Oh goodness no. You have to totally throw not at the target and then it will
    activate and you will never ever find your sword again  (RIP Netheritey).
    Testing for 0.06. RESULT = Seems pretty decent, not too different from 0.04 but off vibes I think it is better.
     */
    // Minecraft physics constants
    private static final double gravityCalc = 0.06; // blocks/tick squared
    private static final double ticksPerSecond = 20.0;

    // Applies aim assist
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

    // Filters targets to find the intended one
    private static Optional<TargetInfo> findBestTarget(ClientPlayerEntity player, Vec3d origin, Vec3d velocity) {
        Vec3d throwDirection = velocity.normalize();
        double throwSpeed = velocity.length();

        // Search box
        Box searchBox = Box.of(origin, maxAssistDistance * 2, maxAssistDistance * 2, maxAssistDistance * 2);

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

    // Evaluates potential targets
    private static TargetInfo evaluateTarget(LivingEntity entity, Vec3d origin, Vec3d throwDirection, double throwSpeed) {
        Vec3d targetPos = entity.getPos().add(0, entity.getHeight() / 1.5, 0); // Target on mob to hit
        Vec3d toTarget = targetPos.subtract(origin);
        double distance = toTarget.length();

        // Quick distance and angle filtering
        if (distance > maxAssistDistance) return null;

        double angle = Math.toDegrees(Math.acos(MathHelper.clamp(
                throwDirection.dotProduct(toTarget.normalize()), -1.0, 1.0)));
        if (angle > assistViewAngle) return null;

        // Intercept calc
        Vec3d entityVelocity = entity.getVelocity();
        double interceptTime = calculateOptimalInterceptTime(origin, targetPos, entityVelocity, throwSpeed);

        if (interceptTime < 0 || interceptTime > maxPredictionTime) return null;

        // Calculate predicted position and trajectory possibility
        Vec3d predictedPos = targetPos.add(entityVelocity.multiply(interceptTime * ticksPerSecond));
        double trajectoryConfidence = calculateTrajectoryPossibility(origin, predictedPos, throwSpeed, interceptTime);

        if (trajectoryConfidence < 0.3) return null; // Skip impossible shots

        return new TargetInfo(entity, targetPos, predictedPos, distance, angle, trajectoryConfidence, interceptTime);
    }

    // Calc for interception time
    private static double calculateOptimalInterceptTime(Vec3d origin, Vec3d targetPos, Vec3d targetVel, double throwSpeed) {
        // Smart initial guess based on linear distance
        Vec3d displacement = targetPos.subtract(origin);
        double linearDistance = displacement.length();
        double initialGuess = linearDistance / throwSpeed / ticksPerSecond;

        // Test initial guess + or - 50% with 5 steps
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
            if (testTime <= 0 || testTime > maxPredictionTime) continue;

            Vec3d predictedTarget = targetPos.add(targetVel.multiply(testTime * ticksPerSecond));
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

    // Calc required initial vel for our trajectory accounting for gravity
    private static Vec3d calculateRequiredBallisticVelocity(Vec3d origin, Vec3d target, double flightTimeSeconds) {
        Vec3d displacement = target.subtract(origin);
        double flightTimeTicks = flightTimeSeconds * ticksPerSecond;

        // Horizontal components
        double vx = displacement.x / flightTimeTicks;
        double vz = displacement.z / flightTimeTicks;

        // Vertical component (compensate for gravity)
        double vy = (displacement.y + 0.5 * gravityCalc * flightTimeTicks * flightTimeTicks) / flightTimeTicks;

        return new Vec3d(vx, vy, vz);
    }

    // Checks if the trajectory is doable
    private static double calculateTrajectoryPossibility(Vec3d origin, Vec3d target, double throwSpeed, double timeSeconds) {
        Vec3d requiredVel = calculateRequiredBallisticVelocity(origin, target, timeSeconds);

        double requiredSpeed = requiredVel.length();

        // Higher confidence for trajectories that match throw speed closely
        return Math.min(throwSpeed, requiredSpeed) / Math.max(throwSpeed, requiredSpeed);
    }

    // Calc assisted velocity
    private static Vec3d calculateBallisticAssist(Vec3d origin, Vec3d originalVelocity, TargetInfo target) {
        // Calculate the ideal ballistic velocity
        Vec3d idealVelocity = calculateRequiredBallisticVelocity(origin, target.predictedPos, target.interceptTime);

        // Scale ideal velocity to reasonable speed to prevent crazy throws
        double originalSpeed = originalVelocity.length();
        double idealSpeed = idealVelocity.length();

        if (idealSpeed > originalSpeed * 1.5) {
            // If ideal is much faster, scale it down but preserve direction
            idealVelocity = idealVelocity.normalize().multiply(originalSpeed * 1.2);
        }

        // Blend with confidence-based strength
        double effectiveStrength = assistStrength * target.confidence;
        return blendVelocities(originalVelocity, idealVelocity, effectiveStrength);
    }

    // Smooth vel blending
    private static Vec3d blendVelocities(Vec3d current, Vec3d target, double strength) {
        return current.multiply(1.0 - strength).add(target.multiply(strength));
    }

    // Priority system for target scoring
    private static double calculateTargetScore(TargetInfo target) {
        double baseScore = target.confidence;

        // Distance bonus (closer is better)
        double distanceScore = 1.0 - (target.distance / maxAssistDistance);

        // Angle bonus (more aligned with throw is better)
        double angleScore = 1.0 - (target.angle / assistViewAngle);

        // Target type multipliers
        double typeMultiplier = 0.7; // Default for animals, etc.
        if (target.entity instanceof Monster) typeMultiplier = 1.0;      // Prefer hostile mobs
        if (target.entity instanceof PlayerEntity) typeMultiplier = 0.9;  // Then players

        return (baseScore + distanceScore + angleScore) / 3.0 * typeMultiplier;
    }
        // Target info
        private record TargetInfo(LivingEntity entity, Vec3d currentPos, Vec3d predictedPos, double distance, double angle,
                                  double confidence, double interceptTime) {
    }

    private AimHelper() {} // Utility class
}