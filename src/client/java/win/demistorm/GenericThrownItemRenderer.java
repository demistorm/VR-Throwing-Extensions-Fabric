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
    }

    @Override
    public void render(GenericThrownItemRenderState state,
                       MatrixStack matrices,
                       VertexConsumerProvider vcp,
                       int light) {

        matrices.push();

        /* --- 1. point into travel direction -------------------------------- */
        Vec3d vel      = state.velocity;
        float yaw      = (float)(MathHelper.atan2(vel.z, vel.x) * 180.0 / Math.PI);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - yaw));

        /* optional pitch ----------------------------------------------------- */
        float hor      = MathHelper.sqrt((float)(vel.x * vel.x + vel.z * vel.z));
        float pitch    = (float)(MathHelper.atan2(vel.y, hor) * 180.0 / Math.PI);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-pitch));

        /* --- 2. flip-spin --------------------------------------------------- */
        float spin = (state.age * 15.0F // Flip speed
            ) % 360F;
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin));

        /* --- 3. scale ------------------------------------------------------- */
        matrices.scale(scale, scale, scale);

        /* --- 4. render the model – NO display transform --------------------- */
        itemRenderer.renderItem(
                state.itemStack,
                ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,      // <── here
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
    }
}