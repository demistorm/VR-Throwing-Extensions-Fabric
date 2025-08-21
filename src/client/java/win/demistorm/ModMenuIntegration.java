package win.demistorm;

import com.terraformersmc.modmenu.api.ModMenuApi;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * A super-small Cloth-Config screen that exposes the one Boolean value.
 */
// No Cloth-Config imports needed
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SimpleToggleScreen::new;
    }

    // dumb, single-button screen
    private static class SimpleToggleScreen extends Screen {
        private final Screen parent;
        private boolean value = ConfigHelper.CLIENT.boomerangEffect;

        protected SimpleToggleScreen(Screen parent) {
            super(Text.literal("VR Throwing – Config"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            addDrawableChild(
                    ButtonWidget.builder(
                                    Text.literal("Boomerang: " + (value ? "ON" : "OFF")),
                                    btn -> {
                                        value = !value;
                                        btn.setMessage(Text.literal(
                                                "Boomerang: " + (value ? "ON" : "OFF")));
                                    })
                            .dimensions(width / 2 - 75, height / 2 - 10, 150, 20)
                            .build());

            addDrawableChild(
                    ButtonWidget.builder(Text.literal("Done"),
                                    btn -> {
                                        ConfigHelper.CLIENT.boomerangEffect = value;
                                        ConfigHelper.loadOrCreateClientConfig(); // save file
                                        assert client != null;
                                        client.setScreen(parent);
                                    })
                            .dimensions(width / 2 - 50, height / 2 + 20, 100, 20)
                            .build());
        }

        @Override public void close() {
            assert client != null;
            client.setScreen(parent); }
    }
}