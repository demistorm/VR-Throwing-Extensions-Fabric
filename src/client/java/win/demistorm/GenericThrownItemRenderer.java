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
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public class GenericThrownItemRenderer extends EntityRenderer<GenericThrownItemEntity, GenericThrownItemRenderer.GenericThrownItemRenderState> {

    private final ItemRenderer itemRenderer;
    private final float scale;

    public GenericThrownItemRenderer(Context ctx) {
        super(ctx);
        this.itemRenderer = MinecraftClient.getInstance().getItemRenderer();
        this.scale = 0.5f;
        this.shadowOpacity = 0.5f;
    }

    @Override
    public GenericThrownItemRenderState createRenderState() {
        return new GenericThrownItemRenderState();
    }

    @Override
    public void updateRenderState(GenericThrownItemEntity entity, GenericThrownItemRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);

        state.itemStack = entity.getStack();
        state.velocity = entity.getVelocity();
        state.age = entity.age + tickDelta;
        state.yaw = entity.getYaw();
        state.pitch = entity.getPitch();
        state.roll = entity.getRoll();
    }

    @Override
    public void render(GenericThrownItemRenderState state,
                       MatrixStack matrices,
                       VertexConsumerProvider vcp,
                       int light) {

        matrices.push();

        /* --- 1. Use actual hand rotation instead of calculating from velocity --- */
        // Apply the stored rotation from when the item was thrown
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(state.yaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(state.pitch));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(state.roll));

        /* --- 2. Keep the spin for natural rotation -------------------------------- */
        float spin = (state.age * 12.0F) % 360F;
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin));

        /* --- 3. scale ------------------------------------------------------------- */
        matrices.scale(scale, scale, scale);

        /* --- 4. render the model -------------------------------------------------- */
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

    public static class GenericThrownItemRenderState extends EntityRenderState {
        public ItemStack itemStack = ItemStack.EMPTY;
        public Vec3d velocity = Vec3d.ZERO;
        public float age = 0.0f;
        public float yaw = 0.0f;
        public float pitch = 0.0f;
        public float roll = 0.0f;
    }
}