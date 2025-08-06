package win.demistorm;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.vivecraft.api.VRAPI;
import org.vivecraft.api.client.VRClientAPI;
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.api.data.VRBodyPartData;
import org.vivecraft.api.data.VRPose;
import org.vivecraft.api.data.VRPoseHistory;
import static win.demistorm.VRThrowingExtensions.log;

// Client throw logic
public class ThrowHelper {

    // Various literals
    private static boolean active          = false;          // Throwing logic active
    private static boolean throwWholeStack = false;          // Whether the whole stack should be thrown
    private static boolean cancelBreaking  = false;          // Cancels breaking after a certain speed
    private static Vec3d      startPoint = Vec3d.ZERO;       // Hand position when attack/destroy pressed
    private static ItemStack  heldItem   = ItemStack.EMPTY;  // Checks what item is in hand
    private static int        ticksHeld  = 0;                // How long trigger is pressed

    // Tunables
    private static final double minThrowDistance        = 0.08; // Min arm movement to activate throw
    private static final int    maxPoseHistoryTicks     = 6; // How many ticks to look back for velocity
    private static final double speedThreshold          = 0.10; // How fast you can move your arm before canceling block breaking
    private static final double throwVelocityThreshold  = 0.06; // Min velocity to activate throw
    public  static final double velocityMultiplier      = 6.0; // Velocity multiplier because raw velocity is way too low

    // Initializer
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(ThrowHelper::onTick);
    }

    // Interaction callbacks
    public static boolean cancellingBreaks() { return active && cancelBreaking; }
    public static boolean cancellingUse   () { return active; } // Always cancel place/use while throwing is active

    // Main throwing logic
    private static void onTick(MinecraftClient mc) {

        // Checks that player is in VR
        if (mc.player == null || !VRAPI.instance().isVRPlayer(mc.player)) return;

        boolean attackPressed = mc.options.attackKey.isPressed(); // Attack/Destroy keybind
        boolean placePressed = mc.options.useKey.isPressed();    // Place/Use keybind

        // When Attack/Destroy is pressed, Start Tracking
        if (!active && attackPressed) {

            ItemStack held = mc.player.getMainHandStack();
            if (ModCompat.throwingDisabled(held)) return;

            VRPose pose = VRAPI.instance().getVRPose(mc.player);
            if (pose == null)                 return;
            VRBodyPartData hand = pose.getHand(Hand.MAIN_HAND);
            if (hand == null)                 return;

            // Activates throw states
            startPoint       = hand.getPos();
            heldItem         = held.copy();
            ticksHeld        = 0;
            active           = true;
            throwWholeStack  = placePressed;    // Throws the whole stack if pressed
            cancelBreaking   = false;         // Doesn't cancel breaking until speed is too fast

            log.debug("[VR Throw] Hold trace started with item: {}", heldItem);
        }

        // Holding Attack/Destroy
        else if (active && attackPressed) {

            ticksHeld        = Math.min(ticksHeld + 1, maxPoseHistoryTicks);
            throwWholeStack |= placePressed;         // Throws whole stack

            // Checks arm speed to determine if should cancel block breaking
            if (!cancelBreaking) {
                VRPoseHistory hist = VRAPI.instance().getHistoricalVRPoses(mc.player);
                if (hist != null) {
                    double speed = hist.averageSpeed(VRBodyPart.MAIN_HAND, 2);
                    if (speed > speedThreshold) {
                        cancelBreaking = true;
                        log.debug("[VR Throw] speed threshold crossed, mining blocked");
                    }
                }
            }
        }

        // Released Attack/Destroy, Sends throw packet
        else if (active) {

            if (ticksHeld >= 5) {                         // Minimum button press time to avoid accidental activation
                VRPoseHistory history = VRAPI.instance().getHistoricalVRPoses(mc.player);

                if (history != null) {
                    int   usedTicks     = Math.min(ticksHeld, maxPoseHistoryTicks);
                    Vec3d avgPos        = history.averagePosition(VRBodyPart.MAIN_HAND, usedTicks);
                    double movedDist    = startPoint.distanceTo(avgPos);

                    // Check that the arm has moved enough to throw
                    if (movedDist > minThrowDistance) {
                        Vec3d avgVel     = history.averageVelocity(VRBodyPart.MAIN_HAND, usedTicks);
                        assert avgVel != null;
                        double velLen    = avgVel.length();

                        // Check that the velocity is high off to activate throw
                        if (velLen >= throwVelocityThreshold) {

                            // Gets past hand position for accurate throwing because of trigger press times
                            Vec3d origin = historicalHandPosition(history, mc);

                            // Multiplies velocity before sending it to the server
                            Vec3d launchVel = avgVel.multiply(velocityMultiplier);

                            // Gathers hand rotation data
                            VRPose pose = VRAPI.instance().getVRPose(mc.player);
                            assert pose != null;
                            VRBodyPartData hand = pose.getHand(Hand.MAIN_HAND);
                            Vector3f euler = new Vector3f();
                            hand.getRotation().getEulerAnglesXYZ(euler);   // X-pitch, Y-yaw, Z-roll  (rad)
                            float rollDeg = (float) Math.toDegrees(euler.z);   // Controller roll in degrees

                            // Sends throw packet to server
                            ClientNetworkHelper.sendToServer(origin, launchVel, throwWholeStack, rollDeg);

                            // DEBUG
                            if (VRThrowingExtensions.debugMode) {
                                mc.player.sendMessage(Text.literal(
                                        "[VR Throw] origin=" + origin +
                                                " vel=" + launchVel +
                                                " stack=" + throwWholeStack), false);
                            }

                            // Sends haptic confirmation
                            VRClientAPI.instance().triggerHapticPulse(
                                    VRBodyPart.fromInteractionHand(Hand.MAIN_HAND), 0.2f);
                        } else {
                            log.debug("[VR Throw] Too slow. Velocity = {}", velLen);
                        }

                    } else {
                        log.debug("[VR Throw] Insufficient motion. Distance = {}", movedDist);
                    }
                }

            } else {
                log.debug("[VR Throw] Released too early. Held {} ticks.", ticksHeld);
            }

            reset();    // Resets everything :)
        }
    }

    // Checks historical hand positions
    private static Vec3d historicalHandPosition(VRPoseHistory hist, MinecraftClient mc) {
        try {
            VRPose pose2 = hist.getHistoricalData(2); // Gets pose from 2 ticks back
            VRBodyPartData hand2 = pose2.getHand(Hand.MAIN_HAND);
            if (hand2 != null) return hand2.getPos();
        } catch (IllegalArgumentException ignored) { }

        // Fallback to current pose if historical data isn't present (that guy joined and threw really fast!)
        VRPose now = VRAPI.instance().getVRPose(mc.player);
        assert now != null;
        return now.getHand(Hand.MAIN_HAND).getPos();
    }

    // Ol' reset
    private static void reset() {
        active          = false;
        throwWholeStack = false;
        cancelBreaking  = false;
        startPoint      = Vec3d.ZERO;
        heldItem        = ItemStack.EMPTY;
        ticksHeld       = 0;
    }

}