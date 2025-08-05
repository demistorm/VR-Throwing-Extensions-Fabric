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

/**
 * Handles client-side throw detection, pose/velocity sampling and the
 * decision whether to throw a single item or the whole stack.
 */
public class ThrowHelper {

    /* ------------------------------------------------------------------ */
    /* State                                                              */
    private static boolean active          = false;          // are we tracking a potential throw?
    private static boolean throwWholeStack = false;          // ⇐ set while holding USE
    private static boolean cancelBreaking  = false;          // latched once speed-threshold crossed

    private static Vec3d      startPoint = Vec3d.ZERO;       // hand position when trigger pressed
    private static ItemStack  heldItem   = ItemStack.EMPTY;  // copy of item at start (debug/info)
    private static int        ticksHeld  = 0;                // how long trigger was held

    /* ------------------------------------------------------------------ */
    /* Tunables                                                           */
    private static final double minThrowDistance        = 0.08;
    private static final int    maxPoseHistoryTicks     = 6;
    private static final double speedThreshold          = 0.10;
    private static final double throwVelocityThreshold  = 0.07;
    public  static final double velocityMultiplier      = 8.0;

    /* ------------------------------------------------------------------ */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(ThrowHelper::onTick);
    }

    /* -------------  information for the interaction callbacks  -------- */
    public static boolean cancellingBreaks() { return active && cancelBreaking; }
    public static boolean cancellingUse   () { return active; } // always block right-click while tracking

    /* ------------------------------------------------------------------ */
    /* Main per-tick logic                                                */
    private static void onTick(MinecraftClient mc) {

        /* ---- pre-conditions ------------------------------------------ */
        if (mc.player == null || !VRAPI.instance().isVRPlayer(mc.player)) return;

        boolean attackPressed = mc.options.attackKey.isPressed(); // left click
        boolean usePressed    = mc.options.useKey.isPressed();    // right click

        /* ---- START TRACKING  ----------------------------------------- */
        if (!active && attackPressed) {

            ItemStack held = mc.player.getMainHandStack();
            if (ModCompat.throwingDisabled(held)) return;

            VRPose pose = VRAPI.instance().getVRPose(mc.player);
            if (pose == null)                 return;
            VRBodyPartData hand = pose.getHand(Hand.MAIN_HAND);
            if (hand == null)                 return;

            /* initialise state */
            startPoint       = hand.getPos();
            heldItem         = held.copy();
            ticksHeld        = 0;
            active           = true;
            throwWholeStack  = usePressed;    // remember R-click from the very first tick
            cancelBreaking   = false;         // (latched later)

            System.out.println("[VR Throw] Hold trace started with item: " + heldItem);
        }

        /* ---- STILL HOLDING ------------------------------------------- */
        else if (active && attackPressed) {

            ticksHeld        = Math.min(ticksHeld + 1, maxPoseHistoryTicks);
            throwWholeStack |= usePressed;         // latch “whole-stack” request

            /* speed test – once above threshold we keep cancelBreaking true
               until reset() is called at the end of the swing                */
            if (!cancelBreaking) {
                VRPoseHistory hist = VRAPI.instance().getHistoricalVRPoses(mc.player);
                if (hist != null) {
                    double speed = hist.averageSpeed(VRBodyPart.MAIN_HAND, 2);
                    if (speed > speedThreshold) {
                        cancelBreaking = true;
                        System.out.println("[VR Throw] speed threshold crossed, mining blocked");
                    }
                }
            }
        }

        /* ---- TRIGGER RELEASED  --------------------------------------- */
        else if (active && !attackPressed) {

            if (ticksHeld >= 5) {                         // minimal “charge” time
                VRPoseHistory history = VRAPI.instance().getHistoricalVRPoses(mc.player);

                if (history != null) {
                    int   usedTicks     = Math.min(ticksHeld, maxPoseHistoryTicks);
                    Vec3d avgPos        = history.averagePosition(VRBodyPart.MAIN_HAND, usedTicks);
                    double movedDist    = startPoint.distanceTo(avgPos);

                    if (movedDist > minThrowDistance) {
                        Vec3d avgVel     = history.averageVelocity(VRBodyPart.MAIN_HAND, usedTicks);
                        assert avgVel != null;
                        double velLen    = avgVel.length();

                        if (velLen >= throwVelocityThreshold) {

                            /* ---- origin: hand position 2 ticks ago ------------ */
                            Vec3d origin = getHandPosTwoTicksBack(history, mc);

                            /* ---- send packet ---------------------------------- */
                            Vec3d launchVel = avgVel.multiply(velocityMultiplier);

                            ClientNetworkHelper.sendToServer(origin, launchVel, throwWholeStack);

                            mc.player.sendMessage(Text.literal(
                                    "[VR Throw] origin=" + origin +
                                            " vel=" + launchVel +
                                            " stack=" + throwWholeStack), false);

                            VRClientAPI.instance().triggerHapticPulse(
                                    VRBodyPart.fromInteractionHand(Hand.MAIN_HAND), 0.2f);
                        } else {
                            System.out.println("[VR Throw] Too slow. Velocity = " + velLen);
                        }
                    } else {
                        System.out.println("[VR Throw] Insufficient motion. Distance = " + movedDist);
                    }
                }
            } else {
                System.out.println("[VR Throw] Released too early. Held " + ticksHeld + " ticks.");
            }

            reset();    // clear everything for the next click
        }
    }

    /* ------------------------------------------------------------------ */
    private static Vec3d getHandPosTwoTicksBack(VRPoseHistory hist, MinecraftClient mc) {
        try {
            VRPose pose2 = hist.getHistoricalData(2);
            VRBodyPartData hand2 = pose2.getHand(Hand.MAIN_HAND);
            if (hand2 != null) return hand2.getPos();
        } catch (IllegalArgumentException ignored) { }

        // fallback to current pose
        VRPose now = VRAPI.instance().getVRPose(mc.player);
        assert now != null;
        return now.getHand(Hand.MAIN_HAND).getPos();
    }

    /* ------------------------------------------------------------------ */
    private static void reset() {
        active          = false;
        throwWholeStack = false;
        cancelBreaking  = false;
        startPoint      = Vec3d.ZERO;
        heldItem        = ItemStack.EMPTY;
        ticksHeld       = 0;
    }

}