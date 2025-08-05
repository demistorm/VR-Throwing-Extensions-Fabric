package win.demistorm;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
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

/**
 * Projectile that carries an arbitrary ItemStack, deals the same damage
 * (including Sharpness / Smite / Bane bonuses) the stack would in melee,
 * and then drops the item.
 */
public class GenericThrownItemEntity extends ThrownItemEntity {
    private int stackSize = 1; // <-- added field

    /* ------------------------------------------------------------ */
    /* Constructors                                                 */
    /* ------------------------------------------------------------ */

    public GenericThrownItemEntity(EntityType<? extends GenericThrownItemEntity> type, World world) {
        super(type, world);
    }

    public GenericThrownItemEntity(World world, LivingEntity owner, ItemStack carried, boolean isWholeStack) {
        super(VRThrowingExtensions.THROWN_ITEM_TYPE, world);
        setOwner(owner);
        setItem(carried.copyWithCount(1)); // Visual model is always 1 item

        // Set stackSize based on whether this is a whole stack throw
        this.stackSize = isWholeStack ? carried.getCount() : 1;
    }

    /* ------------------------------------------------------------ */
    /* Collision handling                                           */
    /* ------------------------------------------------------------ */

    @Override
    protected void onCollision(HitResult hit) {
        if (!getWorld().isClient) {
            if (hit.getType() == HitResult.Type.ENTITY) {
                onEntityHit((EntityHitResult) hit);
            }

            // Drop the correct number of items
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

    @Override
    protected void onEntityHit(EntityHitResult res) {
        Entity target     = res.getEntity();
        ServerWorld world = (ServerWorld) getWorld();

        DamageSources sources = world.getDamageSources();
        DamageSource  src     = sources.thrown(this,
                getOwner() == null ? this : getOwner());

        /* ------------- full vanilla-accurate damage ------------------ */
        float base   = getBaseAttackDamage(getStack());                 // +1 hand, + item bonus
        float damage = EnchantmentHelper.getDamage(
                world, getStack(), target, src, base);       // adds Sharpness/Smite/…

        // Debug logging for damage calculation
        VRThrowingExtensions.LOGGER.info("Thrown item damage calculation: Item={}, Base={}, Final={}, Target={}",
                getStack().getItem().toString(),
                base,
                damage,
                target.getName().getString());

        target.damage(world, src, damage);

        // tiny knock-back
        Vec3d push = getVelocity().normalize().multiply(0.5);
        target.addVelocity(push.x, 0.1 + push.y, push.z);
    }

    /* ------------------------------------------------------------ */
    /* Helpers                                                      */
    /* ------------------------------------------------------------ */

    /** Copy + add 1 durability damage (if the item is damageable). */
    private ItemStack createDropStack() {
        ItemStack drop = getStack().copy();
        if (drop.isDamageable()) {
            int totalDamage = MathHelper.clamp(
                    drop.getDamage() + stackSize, // Damage penalty per item represented
                    0, drop.getMaxDamage());
            drop.setDamage(totalDamage);
        }
        return drop;
    }

    /**
     * Returns 1 + sum of the GENERIC_ATTACK_DAMAGE modifiers the stack
     * contributes for the MAIN_HAND slot (same maths vanilla uses).
     */
    private static float getBaseAttackDamage(ItemStack stack) {
        final float[] bonus = {0};

        EnchantmentHelper.applyAttributeModifiers(
                stack, EquipmentSlot.MAINHAND,
                (attrEntry, modifier) -> {
                /* 1.21.5: constant is EntityAttributes.ATTACK_DAMAGE
                   and we compare against the entry's VALUE            */
                    if (attrEntry.value() == EntityAttributes.ATTACK_DAMAGE) {
                        bonus[0] += (float) modifier.value();
                    }
                });

        return 1.0F + bonus[0];   // bare-hand 1 ♥  + item bonus
    }

    /* ------------------------------------------------------------ */

    @Override
    protected Item getDefaultItem() {
        return net.minecraft.item.Items.STICK;   // any non-empty placeholder item
    }
}