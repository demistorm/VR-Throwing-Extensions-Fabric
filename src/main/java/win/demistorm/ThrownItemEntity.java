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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import static win.demistorm.VRThrowingExtensions.log;

// Projectile that carries the player's held item, deals damage, and drops the item after collision
public class ThrownItemEntity extends net.minecraft.entity.projectile.thrown.ThrownItemEntity {
    private int stackSize = 1;
    public boolean catching = false;                // Whether this projectile is being caught
    private Vec3d storedVelocity = Vec3d.ZERO;      // Stores velocity before catching

    // Enhanced boomerang state tracking
    private int bounceReturnTicks = 0;              // Time spent in return flight
    private boolean reachedOriginOnce = false;      // Prevents oscillation at origin
    protected Vec3d originalThrowPos = Vec3d.ZERO;  // Saved when the entity is created on the server
    protected boolean hasBounced = false;           // Whether the first hit happened
    protected boolean bounceActive = false;         // If the boomerang is active
    public Vec3d bounceCurveOffset = Vec3d.ZERO;
    public Vec3d bouncePlaneNormal = Vec3d.ZERO;
    public double bounceArcMag = 0.0;
    public boolean bounceInverse = true;           // Inverts the bounce direction

    public ThrownItemEntity(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
    }

    private static final TrackedData<Float> HAND_ROLL =
            DataTracker.registerData(ThrownItemEntity.class,
                    TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> IS_CATCHING =
            DataTracker.registerData(ThrownItemEntity.class,
                    TrackedDataHandlerRegistry.BOOLEAN);

    // Tracked data for client sync
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
        super.tick();

        // Boomerang return handling
        if (bounceActive && !isCatching()) {
            bounceReturnTicks++;

            // Return logic end
            if (BoomerangEffect.tickReturn(this)) {
                // Reached origin so it converts over to a regular projectile again
                bounceActive = false;
                reachedOriginOnce = true;
                this.dataTracker.set(BOUNCE_ACTIVE, false);

                // Restore gravity but preserve the current velocity
                setNoGravity(false);

                // Instead of dropping straight down, preserve the return velocity
                Vec3d returnVel = getVelocity();

                double currentSpeed = returnVel.length();
                if (currentSpeed > 1.0) {
                    // Caps the speed to prevent items flying too far
                    returnVel = returnVel.normalize().multiply(Math.min(currentSpeed, 1.0));
                }

                // Apply the preserved velocity so that it naturally falls
                setVelocity(returnVel);

                log.debug("[VR Throw] Projectile {} completed boomerang return after {} ticks, continuing with velocity {}",
                        this.getId(), bounceReturnTicks, returnVel);
            }

            // Stops the bounce if something goes wrong
            if (bounceReturnTicks > 200) { // ~10 seconds at 20 TPS
                log.debug("[VR Throw] Projectile {} return timed out, dropping", this.getId());
                stopBoomerang();
            }
        }

        // Don't apply normal physics if being caught
        if (isCatching()) {
            // Apply slight air resistance to smooth magnetism effect
            Vec3d vel = getVelocity();
            setVelocity(vel.multiply(0.95));
        }
    }

    // End boomerang effect and restore normal physics
    private void stopBoomerang() {
        bounceActive = false;
        this.dataTracker.set(BOUNCE_ACTIVE, false);
        setNoGravity(false);

        // Apply gentle downward velocity
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

    // Collision mechanics with damage logic
    @Override
    protected void onCollision(HitResult hit) {
        // Ignore while being caught
        if (isCatching()) return;

        // If the thrown item is mid-boomerang, have it drop on hit
        if (bounceActive || hasBounced) {
            if (hit.getType() == HitResult.Type.ENTITY) {
                onEntityHit((EntityHitResult) hit);
                log.debug("[VR Throw] Projectile {} hit entity during return flight, dropping", this.getId());
            } else if (hit.getType() == HitResult.Type.BLOCK) {
                log.debug("[VR Throw] Projectile {} hit block during return flight, dropping", this.getId());
            }

            // Always drop after collision during return flight
            dropAndDiscard();
            return;
        }

        // Thrown item first hit
        if (!getWorld().isClient) {
            boolean hitEntity = hit.getType() == HitResult.Type.ENTITY;

            if (hitEntity) {
                // Deal damage first
                onEntityHit((EntityHitResult) hit);

                // Only boomerang/embed if the item is a weapon/tool/does damage (should be mod compatible?)
                float attackDamage = stackBaseDamage(getStack());
                if (attackDamage <= 1.0F) {
                    dropAndDiscard();
                    return;
                }

                // Check to see if boomerang should activate
                boolean shouldBounce = ConfigHelper.ACTIVE.boomerangEffect
                        && BoomerangEffect.canBounce(getStack().getItem())
                        && !hasBounced
                        && !reachedOriginOnce; // Don't bounce if already completed return

                if (shouldBounce) {
                    log.debug("[VR Throw] Starting boomerang effect for projectile {}", this.getId());
                    BoomerangEffect.startBounce(this);

                    // Update tracked data for client sync
                    bounceActive = true;
                    this.dataTracker.set(BOUNCE_ACTIVE, true);
                    return; // Starts return flight
                }
            }

            // Normal drop for non-bouncing hits or block hits
            dropAndDiscard();
        } else {
            // Nice particle effects
            getWorld().addParticleClient(
                    new ItemStackParticleEffect(ParticleTypes.ITEM, getStack()),
                    getX(), getY(), getZ(), 0.0, 0.0, 0.0);
        }
    }

    // Damage mechanics
    @Override
    protected void onEntityHit(EntityHitResult res) {
        Entity target = res.getEntity();
        ServerWorld world = (ServerWorld) getWorld();
        DamageSources sources = world.getDamageSources();
        DamageSource src = sources.thrown(this, getOwner() == null ? this : getOwner());

        // Grabs the base damage from the itemStack and applies enchantment bonuses on top
        float base = stackBaseDamage(getStack());
        float totalDamage = EnchantmentHelper.getDamage(world, getStack(), target, src, base);
        float enchantmentBonus = totalDamage - base; // For DEBUG

        /* Might not need anymore, base damage should be okay now
        float multipliedDamage = damage * 2F; // Multiplies damage to make up for weird base attack damage
         */

        // DEBUG
        log.debug("[VR Throw] Damage dealt: Item={}, Base={}, EnchantBonus={}, Final={}, Target={}, BounceState={}",
                getStack().getItem().toString(), base, enchantmentBonus,
                totalDamage, target.getName().getString(),
                bounceActive ? "RETURNING" : "FORWARD");
        if (VRThrowingExtensions.debugMode) {
            if (getOwner() instanceof ServerPlayerEntity player) {
                Text msg = Text.literal(String.format(
                        "[VR Throw] Damage dealt: Item=%s, Base=%.2f, EnchantBonus=%.2f, Final=%.2f, Target=%s, BounceState=%s",
                        getStack().getItem(),
                        base,
                        enchantmentBonus,
                        totalDamage,
                        target.getName().getString(),
                        bounceActive ? "RETURNING" : "FORWARD"
                ));
                player.sendMessage(msg, false);
            }
        }


        // Actually damages the entity
        target.damage(world, src, totalDamage);

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
    private static float stackBaseDamage(ItemStack stack) {
        final float[] totalBonus = {0f};

        stack.applyAttributeModifiers(EquipmentSlot.MAINHAND, (attrEntry, modifier) -> {
            if (attrEntry == EntityAttributes.ATTACK_DAMAGE) {
                totalBonus[0] += (float) modifier.value();
            }
        });

        return 1.0F + totalBonus[0]; // 1.0 base punch + item’s modifier
    }

    // Placeholder item for ThrownItemEntity's sake
    @Override
    protected Item getDefaultItem() {
        return net.minecraft.item.Items.STICK;
    }

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