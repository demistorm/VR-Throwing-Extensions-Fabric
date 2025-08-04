package win.demistorm;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import org.vivecraft.api.VRAPI;
import org.vivecraft.api.client.VRClientAPI;
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.api.data.VRBodyPartData;
import org.vivecraft.api.data.VRPose;
import org.vivecraft.api.data.VRPoseHistory;

// Main class containing VR throwing logic and handling VRPose history, etc
public class ThrowHelper {
    // All sorts of values
    private static boolean active = false; // If the throw logic is active or not
    private static Vec3d startPoint = Vec3d.ZERO; // Where the hand is on attack/destroy input
    private static ItemStack heldItem = ItemStack.EMPTY; // Item placeholder
    private static int ticksHeld = 0; // Counts how long attack is held

    // Activation thresholds
    private static final double minThrowDistance = 0.08; // Min arm movement distance to allow throw
    private static final int maxPoseHistoryTicks = 6; // Number of ticks to look back for vel data
    private static final double speedThreshold = 0.10; // Min speed to allow throw
    private static final double throwVelocityThreshold = 0.07; // Min vel to allow throw

    // Velocity multiplier for projectile launch strength
    public static final double velocityMultiplier = 8.0; // Multiplies velocity sent for the throw

    // Vanilla block breaking canceling
    private static boolean cancelBreaking = false; // Break canceling defaults to off

    // Initialize and register throw logic
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(ThrowHelper::onTick);
    }

    // Function to check if block breaking has been cancelled
    public static boolean cancellingBreaks() {
        return cancelBreaking && active;
    }


    // Throw initialization logic
    private static void onTick(MinecraftClient mc) {
        // Checks if Vivecraft player is in VR
        if (mc.player == null || !VRAPI.instance().isVRPlayer(mc.player)) return;

        // Hand position debug function
        debugLogHands();

        boolean attackPressed = mc.options.attackKey.isPressed(); // Attack/Destroy keybind

        // Checks if throwing logic should start
        if (!active && attackPressed) {
            ItemStack held = mc.player.getMainHandStack();
            if (ModCompat.throwingDisabled(held)) {
                return; // Skips tracking for this item if it's blacklisted
            }

            // Gets VR hand positions
            VRPose pose = VRAPI.instance().getVRPose(mc.player);
            if (pose == null) return;
            VRBodyPartData handData = pose.getHand(Hand.MAIN_HAND);
            if (handData == null) return;

            // Sets default values
            startPoint = handData.getPos();
            heldItem = held.copy();
            ticksHeld = 0;
            active = true;

            // DEBUG
            System.out.println("[VR Throw] Hold trace started with item: " + heldItem);

            // Attack/Destroy release logic
        } else if (active && !attackPressed) {
            // Evaluate throw only if held long enough
            if (ticksHeld >= 5) { // Min required hold duration
                VRPoseHistory history = VRAPI.instance().getHistoricalVRPoses(mc.player);
                if (history != null) {
                    int usedTicks = Math.min(ticksHeld, maxPoseHistoryTicks);

                    Vec3d avgPos = history.averagePosition(org.vivecraft.api.data.VRBodyPart.MAIN_HAND, usedTicks);
                    double movedDistance = startPoint.distanceTo(avgPos);

                    if (movedDistance > minThrowDistance) {
                        Vec3d avgVelocity = history.averageVelocity(org.vivecraft.api.data.VRBodyPart.MAIN_HAND, usedTicks);
                        assert avgVelocity != null;
                        double velLength = avgVelocity.length();

                        //  ... inside the existing `onTick` method – replace the block that
//      currently sends only the velocity with the following:
                        VRPose pose = VRAPI.instance().getVRPose(mc.player);
                        assert pose != null;
                        VRBodyPartData handDataNow = pose.getHand(Hand.MAIN_HAND);

                        if (velLength >= throwVelocityThreshold) {
                            // Apply velocity multiplier to make throws more powerful
                            Vec3d multipliedVelocity = avgVelocity.multiply(velocityMultiplier);

                            // send pos + velocity
                            ClientNetworkHelper.sendToServer(handDataNow.getPos(), multipliedVelocity);

                            mc.player.sendMessage(Text.literal(
                                    "[VR Throw] originalV=" + avgVelocity + " multipliedV=" + multipliedVelocity + " p=" + handDataNow.getPos()), false);

                            // haptics
                            VRClientAPI.instance().triggerHapticPulse(
                                    VRBodyPart.fromInteractionHand(Hand.MAIN_HAND), 0.2f);
                        } else {
                            // DEBUG
                            System.out.println("[VR Throw] Too slow. Velocity = " + velLength);
                        }
                    } else {
                        // DEBUG
                        System.out.println("[VR Throw] Insufficient motion. Distance=" + movedDistance);
                    }
                }
            } else {
                // DEBUG
                System.out.println("[VR Throw] Released too early. Held for " + ticksHeld + " ticks.");
            }

            reset(); // Resets literals

            // If the throw logic is active and attack/destroy is held
        } else if (active && attackPressed) {
            // While holding - keep counting ticks (capped at 10)
            ticksHeld = Math.min(ticksHeld + 1, maxPoseHistoryTicks);

            // Check movement speed to cancel vanilla breaking
            VRPoseHistory history = VRAPI.instance().getHistoricalVRPoses(mc.player);
            if (history != null) {
                double currentSpeed = history.averageSpeed(org.vivecraft.api.data.VRBodyPart.MAIN_HAND, 3);
                if (currentSpeed > speedThreshold) {
                    cancelBreaking = true; // Cancel vanilla breaking
                } else {
                    cancelBreaking = false; // Disable if moved back or too slow
                }
            }
        }
    }

    // Resets values
    private static void reset() {
        active = false;
        startPoint = Vec3d.ZERO;
        heldItem = ItemStack.EMPTY;
        ticksHeld = 0;
        cancelBreaking = false; // clear suppression
    }

    // DEBUG hand position logging
    private static int debugTick = 0;
    public static void debugLogHands() {
        if (++debugTick < 20) return;
        debugTick = 0;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !VRAPI.instance().isVRPlayer(mc.player)) return;

        VRPose pose = VRAPI.instance().getVRPose(mc.player);
        if (pose == null) return;

        for (Hand h : Hand.values()) {
            VRBodyPartData handData = pose.getHand(h);
            if (handData == null) continue;

            String label = h == Hand.MAIN_HAND ? "Main" : "Off";
            Vec3d pos = handData.getPos();
            Vec3d dir = handData.getDir();

            VRThrowingExtensionsClient.LOGGER.info(
                    "{} hand -> pos[{},{},{}] dir[{},{},{}]",
                    label,
                    pos.x, pos.y, pos.z,
                    dir.x, dir.y, dir.z
            );
        }
    }
}