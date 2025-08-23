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

    // Updated SimpleToggleScreen class with both options
    private static class SimpleToggleScreen extends Screen {
        private final Screen parent;
        private boolean boomerangValue = ConfigHelper.CLIENT.boomerangEffect;
        private boolean aimAssistValue = ConfigHelper.CLIENT.aimAssist;

        protected SimpleToggleScreen(Screen parent) {
            super(Text.literal("VR Throwing – Config"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            // Boomerang toggle button
            addDrawableChild(
                    ButtonWidget.builder(
                                    Text.literal("Boomerang: " + (boomerangValue ? "ON" : "OFF")),
                                    btn -> {
                                        boomerangValue = !boomerangValue;
                                        btn.setMessage(Text.literal(
                                                "Boomerang: " + (boomerangValue ? "ON" : "OFF")));
                                    })
                            .dimensions(width / 2 - 75, height / 2 - 30, 150, 20)
                            .build());

            // Aim assist toggle button
            addDrawableChild(
                    ButtonWidget.builder(
                                    Text.literal("Aim Assist: " + (aimAssistValue ? "ON" : "OFF")),
                                    btn -> {
                                        aimAssistValue = !aimAssistValue;
                                        btn.setMessage(Text.literal(
                                                "Aim Assist: " + (aimAssistValue ? "ON" : "OFF")));
                                    })
                            .dimensions(width / 2 - 75, height / 2 - 5, 150, 20)
                            .build());

            // Done button
            addDrawableChild(
                    ButtonWidget.builder(Text.literal("Done"),
                                    btn -> {
                                        ConfigHelper.CLIENT.boomerangEffect = boomerangValue;
                                        ConfigHelper.CLIENT.aimAssist = aimAssistValue;
                                        ConfigHelper.write(ConfigHelper.CLIENT);
                                        assert client != null;
                                        if (client.getServer() != null) { // Integrated server (singleplayer)
                                            ConfigHelper.copyInto(ConfigHelper.CLIENT, ConfigHelper.ACTIVE); // Apply immediately
                                        }
                                        assert client != null;
                                        client.setScreen(parent);
                                    })
                            .dimensions(width / 2 - 50, height / 2 + 25, 100, 20)
                            .build());
        }

        @Override public void close() {
            assert client != null;
            client.setScreen(parent);
        }
    }
}