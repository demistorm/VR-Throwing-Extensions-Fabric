package win.demistorm;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

/**
 * Pure utility – all state lives inside the {@link ThrownItemEntity} instances,
 * this class just keeps the helper maths and “what is allowed to bounce” list.
 */
public final class BoomerangEffect {
    /* ------------------------------------------------------------ */
    /*  ELIGIBLE ITEMS                                               */
    /* ------------------------------------------------------------ */
    // Presently all items except AIR.  Replace this set later with a real
    // damage-mapping class without touching the logic here.
    public static final Set<Item> bounceTools = new HashSet<>();
    static {
        Registries.ITEM.stream().filter(i -> !Registries.ITEM.getId(i)
                        .equals(Identifier.of("minecraft","air")))
                .forEach(bounceTools::add);
    }

    public static boolean canBounce(Item i) {
        return bounceTools.contains(i);
    }

    /*  ACTIVATION                                                   */
    /* ------------------------------------------------------------ */
    /**
     * Starts the boomerang-style return path.
     * Uses the original throw position and the stored hand roll to determine lift direction.
     */
    public static void startBounce(ThrownItemEntity proj) {
        proj.hasBounced   = true;
        proj.bounceActive = true;

        Vec3d toOrigin    = proj.originalThrowPos.subtract(proj.getPos());
        Vec3d dir         = toOrigin.normalize();

        // Convert stored hand roll into radians
        float rollRad     = (float) Math.toRadians(proj.getHandRoll());

        // Compute lift axis from spin: perpendicular to motion in roll direction
        Vec3d sideAxis    = new Vec3d(-dir.z, 0, dir.x).normalize(); // right-hand rule
        Vec3d liftAxis    = dir.crossProduct(sideAxis);

        // Add curved lift based on spin
        Vec3d lift         = liftAxis.multiply(MathHelper.sin(rollRad) * 0.15); // adjust strength as needed
        double bounceSpeed = 0.05; // Speed of boomerang effect bounce back
        Vec3d vel          = dir.multiply(bounceSpeed).add(lift);

        proj.setVelocity(vel);
        proj.setNoGravity(false); // light gravity for realism

        VRThrowingExtensions.log.debug(
                "[Boomerang] Projectile {} started return flight. Roll = {} deg, Lift dir = {}",
                proj.getId(), proj.getHandRoll(), liftAxis);
    }

    /**
     * Per-tick update. Returns TRUE when the journey is finished.
     */
    public static boolean tickReturn(ThrownItemEntity proj) {
        // gently steer towards the recorded origin each tick
        Vec3d toOrigin   = proj.originalThrowPos.subtract(proj.getPos());
        double distSq    = toOrigin.lengthSquared();
        if (distSq < 0.25) {                // ~0.5 block radius (maybe change this?)
            return true;                    // done
        }

        Vec3d wantDir    = toOrigin.normalize();
        Vec3d vel        = proj.getVelocity();

        // cheap steering: lerp velocity towards the perfect direction
        Vec3d newVel     = vel.multiply(0.90)               // damp a bit
                .add(wantDir.multiply(0.10)); // steer a bit
        proj.setVelocity(newVel);
        return false;
    }

    private BoomerangEffect() { }
}