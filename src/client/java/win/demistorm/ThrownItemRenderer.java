package win.demistorm;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

// Enhanced renderer with smoother interpolation for boomerang effects
@Environment(EnvType.CLIENT)
public class ThrownItemRenderer extends EntityRenderer<ThrownItemEntity, ThrownItemRenderer.ThrownItemRenderState> {
    private final ItemRenderer itemRenderer;
    private final float scale;

    public ThrownItemRenderer(Context ctx) {
        super(ctx);
        this.itemRenderer = MinecraftClient.getInstance().getItemRenderer();
        this.scale = 0.5f; // Item display scale
        this.shadowOpacity = 0.5f; // Shadow opacity
    }

    @Override
    public ThrownItemRenderState createRenderState() {
        return new ThrownItemRenderState();
    }

    @Override
    public void updateRenderState(ThrownItemEntity entity, ThrownItemRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.itemStack = entity.getStack();
        state.velocity = entity.getVelocity();
        state.age = entity.age + tickDelta;
        state.handRollDeg = entity.getHandRoll();
        state.isCatching = entity.isCatching();
        state.isBounceActive = entity.isBounceActive();
    }

    @Override
    public void render(ThrownItemRenderState state,
                       MatrixStack matrices,
                       VertexConsumerProvider vcp,
                       int light) {
        matrices.push();

        // Get velocity for direction calculation
        Vec3d vel = state.velocity;

        // Enhanced rotation calculation with better interpolation
        if (vel.length() > 0.001) {
            // Calculate yaw (horizontal rotation)
            float yaw = (float)(MathHelper.atan2(vel.z, vel.x) * 180.0 / Math.PI);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F - yaw));

            // Calculate pitch (vertical rotation)
            float hor = MathHelper.sqrt((float)(vel.x * vel.x + vel.z * vel.z));
            float pitch = (float)(MathHelper.atan2(vel.y, hor) * 180.0 / Math.PI);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-pitch));

            // Add hand tilt
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-state.handRollDeg));
        }

        // Enhanced spinning behavior based on state
        if (state.isCatching) {
            // Slower, smoother rotation while being caught
            float smoothSpin = (state.age * 5.0F) % 360F;
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(smoothSpin));

            // Add slight bobbing effect while being magnetized
            float bobOffset = MathHelper.sin(state.age * 0.5F) * 0.05F;
            matrices.translate(0, bobOffset, 0);
        } else if (state.isBounceActive) {
            // Special rotation for boomerang return flight
            // Smoother, more controlled spinning that suggests return trajectory
            float returnSpin = (state.age * 8.0F) % 360F; // Medium speed spin
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(returnSpin));

            // Add subtle wobble to suggest the curved flight path
            float wobble = MathHelper.sin(state.age * 0.35F) * 3.0F;
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(wobble));

            // Slight pulsing scale effect for visual feedback
            float pulseScale = 1.0F + MathHelper.sin(state.age * 0.4F) * 0.05F;
            matrices.scale(pulseScale, pulseScale, pulseScale);
        } else {
            // Normal fast spinning during forward flight
            float spinSpeed = 15.0F; // Speed of flipping motion
            float spin = (state.age * spinSpeed) % 360F;
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin));
        }

        // Apply scale to item
        matrices.scale(scale, scale, scale);

        // Render the model
        itemRenderer.renderItem(
                state.itemStack,
                ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                light,
                OverlayTexture.DEFAULT_UV,
                matrices,
                vcp,
                null,
                0
        );

        matrices.pop();
        super.render(state, matrices, vcp, light);
    }

    public static class ThrownItemRenderState extends EntityRenderState {
        public ItemStack itemStack = ItemStack.EMPTY;
        public Vec3d velocity = Vec3d.ZERO;
        public float age = 0.0f;
        public float handRollDeg = 0f;
        public boolean isCatching = false;
        public boolean isBounceActive = false; // New field for boomerang state
    }
}