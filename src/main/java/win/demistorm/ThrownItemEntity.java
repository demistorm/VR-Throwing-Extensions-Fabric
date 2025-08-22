package win.demistorm;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import static win.demistorm.VRThrowingExtensions.log;

// Projectile that carries the player's held item, deals damage, and drops the item after collision
public class ThrownItemEntity extends net.minecraft.entity.projectile.thrown.ThrownItemEntity {
    private int stackSize = 1;
    public boolean catching = false; // Whether this projectile is being caught
    private Vec3d storedVelocity = Vec3d.ZERO; // Stores velocity before catching for restoration

    // Enhanced boomerang state tracking
    private boolean hasDealtDamage = false; // Prevents multiple damage instances
    private int bounceReturnTicks = 0; // Time spent in return flight
    private boolean reachedOriginOnce = false; // Prevents oscillation at origin

    public ThrownItemEntity(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
    }

    private static final TrackedData<Float> HAND_ROLL =
            DataTracker.registerData(ThrownItemEntity.class,
                    TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> IS_CATCHING =
            DataTracker.registerData(ThrownItemEntity.class,
                    TrackedDataHandlerRegistry.BOOLEAN);

    // Enhanced tracked data for smoother client sync
    private static final TrackedData<Boolean> BOUNCE_ACTIVE =
            DataTracker.registerData(ThrownItemEntity.class,
                    TrackedDataHandlerRegistry.BOOLEAN);

    // Handles the rotation of the arm and bounce state
    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(HAND_ROLL, 0f);
        builder.add(IS_CATCHING, false);
        builder.add(BOUNCE_ACTIVE, false);
    }

    public void setHandRoll(float deg) {
        this.dataTracker.set(HAND_ROLL, deg);
    }

    public float getHandRoll() {
        return this.dataTracker.get(HAND_ROLL);
    }

    public void startCatch() {
        this.catching = true;
        this.storedVelocity = getVelocity();
        this.dataTracker.set(IS_CATCHING, true);
        // Disable gravity while being caught
        this.setNoGravity(true);
        log.debug("[VR Catch] Started catch for projectile {}", this.getId());
    }

    public void cancelCatch() {
        this.catching = false;
        this.dataTracker.set(IS_CATCHING, false);
        // Restore gravity
        this.setNoGravity(false);
        // Restore some velocity to continue flying
        if (storedVelocity.length() > 0.1) {
            setVelocity(storedVelocity.multiply(0.5));
        }
        log.debug("[VR Catch] Canceled catch for projectile {}", this.getId());
    }

    public boolean isCatching() {
        return this.dataTracker.get(IS_CATCHING);
    }

    public boolean isBounceActive() {
        return this.dataTracker.get(BOUNCE_ACTIVE);
    }

    public int getStackSize() {
        return this.stackSize;
    }

    public ThrownItemEntity(World world, LivingEntity owner, ItemStack carried, boolean isWholeStack) {
        super(VRThrowingExtensions.THROWN_ITEM_TYPE, world);
        setOwner(owner);
        setItem(carried.copyWithCount(1)); // Visually only throws 1 model

        // Sets stackSize based on whether it is throwing the whole stack or not
        this.stackSize = isWholeStack ? carried.getCount() : 1;
    }

    @Override
    public void tick() {
        // Store valid velocity for smooth transitions
        Vec3d currentVel = getVelocity();
        currentVel.length();// For smooth physics transitions

        super.tick();

        /* ---------------- Enhanced Boomerang return handling ---------------- */
        if (bounceActive && !isCatching()) {
            bounceReturnTicks++;

            // Enhanced return logic with better termination
            if (BoomerangEffect.tickReturn(this)) {
                // Reached origin -> restore normal physics smoothly
                bounceActive = false;
                reachedOriginOnce = true;
                this.dataTracker.set(BOUNCE_ACTIVE, false);

                // Restore gravity and apply a gentle downward velocity
                setNoGravity(false);
                Vec3d finalVel = new Vec3d(0, -0.2, 0); // Gentle drop
                setVelocity(finalVel);

                log.debug("[VR Throw] Projectile {} completed boomerang return after {} ticks",
                        this.getId(), bounceReturnTicks);
            }

            // Safety timeout - if it's been returning for too long, just drop
            if (bounceReturnTicks > 200) { // ~10 seconds at 20 TPS
                log.debug("[VR Throw] Projectile {} return timed out, dropping", this.getId());
                terminateBoomerang();
            }
        }

        // Don't apply normal physics if being caught
        if (isCatching()) {
            // Apply slight air resistance to smooth magnetism effect
            Vec3d vel = getVelocity();
            setVelocity(vel.multiply(0.95));
        }
    }

    // Safely terminate boomerang and restore normal physics
    private void terminateBoomerang() {
        bounceActive = false;
        this.dataTracker.set(BOUNCE_ACTIVE, false);
        setNoGravity(false);

        // Apply gentle downward velocity instead of preserving high speed
        Vec3d currentVel = getVelocity();
        double horizontalSpeed = Math.sqrt(currentVel.x * currentVel.x + currentVel.z * currentVel.z);

        // Cap horizontal speed and add downward motion
        if (horizontalSpeed > 0.5) {
            double factor = 0.3 / horizontalSpeed;
            setVelocity(new Vec3d(currentVel.x * factor, -0.2, currentVel.z * factor));
        } else {
            setVelocity(new Vec3d(currentVel.x * 0.5, -0.2, currentVel.z * 0.5));
        }
    }

    // Enhanced collision mechanics with better state management
    @Override
    protected void onCollision(HitResult hit) {
        // 1) ignore while being magnet-caught
        if (isCatching()) return;

        // 2) return-flight → damage once if not already done, then drop
        if (bounceActive || hasBounced) {
            if (hit.getType() == HitResult.Type.ENTITY && !hasDealtDamage) {
                onEntityHit((EntityHitResult) hit);
                hasDealtDamage = true; // Prevent multiple damage instances
            }
            dropAndDiscard();
            return;
        }

        // 3) first hit (forward flight) - ensure we're on server side
        if (!getWorld().isClient) {
            boolean hitEntity = hit.getType() == HitResult.Type.ENTITY;

            if (hitEntity) {
                // Deal damage first
                onEntityHit((EntityHitResult) hit);
                hasDealtDamage = true;

                // Then check for boomerang effect
                boolean shouldBounce = ConfigHelper.ACTIVE.boomerangEffect
                        && BoomerangEffect.canBounce(getStack().getItem())
                        && !hasBounced
                        && !reachedOriginOnce; // Don't bounce if already completed a return

                if (shouldBounce) {
                    log.debug("[VR Throw] Starting boomerang effect for projectile {}", this.getId());
                    BoomerangEffect.startBounce(this);

                    // Update tracked data for client sync
                    bounceActive = true;
                    this.dataTracker.set(BOUNCE_ACTIVE, true);
                    return; // Don't drop yet
                }
            }

            // Normal drop for non-bouncing hits or block hits
            dropAndDiscard();
        } else {
            // Client-side particles
            getWorld().addParticleClient(
                    new ItemStackParticleEffect(ParticleTypes.ITEM, getStack()),
                    getX(), getY(), getZ(), 0.0, 0.0, 0.0);
        }
    }

    // Enhanced damage mechanics with better logging
    @Override
    protected void onEntityHit(EntityHitResult res) {
        Entity target = res.getEntity();
        ServerWorld world = (ServerWorld) getWorld();
        DamageSources sources = world.getDamageSources();
        DamageSource src = sources.thrown(this, getOwner() == null ? this : getOwner());

        // Grabs the base damage from the itemStack and applies enchantment bonuses on top
        float base = getBaseAttackDamage(getStack());
        float damage = EnchantmentHelper.getDamage(world, getStack(), target, src, base);
        float multipliedDamage = damage * 2F; // Multiplies damage to make up for weird base attack damage

        // DEBUG - enhanced logging
        log.debug("[VR Throw] Damage dealt: Item={}, Base={}, Final={}, Target={}, BounceState={}",
                getStack().getItem().toString(), base, multipliedDamage,
                target.getName().getString(), bounceActive ? "RETURNING" : "FORWARD");

        // Actually damages the entity
        target.damage(world, src, multipliedDamage);

        // Adds a little knockback
        Vec3d push = getVelocity().normalize().multiply(0.5);
        target.addVelocity(push.x, 0.1 + push.y, push.z);
    }

    // Creates the dropped stack, also does -1 durability to tools
    private ItemStack createDropStack() {
        ItemStack drop = getStack().copy();
        if (drop.isDamageable()) {
            int totalDamage = MathHelper.clamp(
                    drop.getDamage() + stackSize, // Durability penalty
                    0, drop.getMaxDamage());
            drop.setDamage(totalDamage);
        }
        return drop;
    }

    // Checks the attack damage of a given item
    private static float getBaseAttackDamage(ItemStack stack) {
        final float[] bonus = {0f};

        EnchantmentHelper.applyAttributeModifiers(
                stack, EquipmentSlot.MAINHAND,
                (attrEntry, modifier) -> {
                    if (attrEntry == EntityAttributes.ATTACK_DAMAGE) {
                        bonus[0] += (float) modifier.value();
                    }
                });

        return 1.0F + bonus[0]; // 1 (base damage, like a punch) + item/enchantment damage
    }

    // Placeholder item for ThrownItemEntity's sake
    @Override
    protected Item getDefaultItem() {
        return net.minecraft.item.Items.STICK;
    }

    /* --------------------------------------------------------------------- */
    /*  BOUNCE / BOOMERANG STATE                                             */
    /* --------------------------------------------------------------------- */
    // Saved when the entity is created on the server.
    protected Vec3d originalThrowPos = Vec3d.ZERO;
    // whether the *first* hit already happened
    protected boolean hasBounced = false;
    // true while travelling back towards originalThrowPos
    protected boolean bounceActive = false;

    public void setOriginalThrowPos(Vec3d v) {
        this.originalThrowPos = v;
    }

    private void dropAndDiscard() {
        ItemStack dropStack = createDropStack();
        dropStack.setCount(stackSize);
        getWorld().spawnEntity(new net.minecraft.entity.ItemEntity(
                getWorld(), getX(), getY(), getZ(), dropStack));
        discard();
    }
}