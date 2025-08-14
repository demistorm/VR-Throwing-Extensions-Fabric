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

    public ThrownItemEntity(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
    }
    private static final TrackedData<Float> HAND_ROLL =
            DataTracker.registerData(ThrownItemEntity.class,
                    TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> IS_CATCHING =
            DataTracker.registerData(ThrownItemEntity.class,
                    TrackedDataHandlerRegistry.BOOLEAN);

    // Handles the rotation of the arm
    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(HAND_ROLL, 0f);
        builder.add(IS_CATCHING, false);
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

        // Don't apply normal physics if being caught
        if (isCatching()) {
            // Apply slight air resistance to smooth magnetism effect
            Vec3d vel = getVelocity();
            setVelocity(vel.multiply(0.95));
        }
    }

    // Handles collision mechanics
    @Override
    protected void onCollision(HitResult hit) {
        // Don't process collisions while being caught
        if (isCatching()) {
            return;
        }

        if (!getWorld().isClient) {
            if (hit.getType() == HitResult.Type.ENTITY) {
                onEntityHit((EntityHitResult) hit);
            }

            // Drop however many items
            ItemStack dropStack = createDropStack();
            dropStack.setCount(stackSize); // Set the actual count to drop
            getWorld().spawnEntity(new net.minecraft.entity.ItemEntity(
                    getWorld(), getX(), getY(), getZ(), dropStack));
            discard();
        } else {
            getWorld().addParticleClient(
                    new ItemStackParticleEffect(ParticleTypes.ITEM, getStack()),
                    getX(), getY(), getZ(), 0.0, 0.0, 0.0);
        }
    }

    // Handles damage mechanics
    @Override
    protected void onEntityHit(EntityHitResult res) {
        Entity target     = res.getEntity();
        ServerWorld world = (ServerWorld) getWorld();
        DamageSources sources = world.getDamageSources();
        DamageSource  src     = sources.thrown(this,
                getOwner() == null ? this : getOwner());

        // Grabs the base damage from the itemStack and applies enchantment bonuses on top
        float base   = getBaseAttackDamage(getStack());
        float damage = EnchantmentHelper.getDamage(
                world, getStack(), target, src, base); // Accounts for enchantments
        float multipliedDamage = damage * (2F); // Multiplies damage to make up for weird base attack damage

        // DEBUG
        log.debug("Thrown item damage calculation: Item={}, Base={}, Final={}, Target={}",
                getStack().getItem().toString(),
                base,
                multipliedDamage,
                target.getName().getString());

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

    // Checks the attack damage of a given item, seems kinda wonky right now though
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
}