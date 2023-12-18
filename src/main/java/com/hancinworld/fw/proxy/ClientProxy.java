// Copyright (c) 2015, David Larochelle-Pratte
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice, this
// list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice,
// this list of conditions and the following disclaimer in the documentation
// and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
// ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
package com.hancinworld.fw.proxy;

import java.awt.*;
import java.io.File;
import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

import com.hancinworld.fw.handler.ConfigurationHandler;
import com.hancinworld.fw.handler.DrawScreenEventHandler;
import com.hancinworld.fw.handler.KeyInputEventHandler;
import com.hancinworld.fw.reference.Reference;
import com.hancinworld.fw.utility.LogHelper;

import cpw.mods.fml.client.SplashProgress;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.ReflectionHelper;

public class ClientProxy extends CommonProxy {

    private Rectangle _savedWindowedBounds;
    public static boolean currentState;
    public static KeyBinding fullscreenKeyBinding;
    public DrawScreenEventHandler dsHandler;
    private boolean _startupRequestedSetting;

    /**
     * This keybind replaces the default MC fullscreen keybind in their logic handler. Without it, the game crashes.
     * If this is set to any valid key, problems may occur.
     */
    public static KeyBinding ignoreKeyBinding = new KeyBinding(
        "key.fullscreenwindowed.unused",
        Keyboard.KEY_NONE,
        "key.categories.misc");

    public ClientProxy() {
        Minecraft mc = Minecraft.getMinecraft();
        _startupRequestedSetting = mc.gameSettings.fullScreen;
        mc.gameSettings.fullScreen = false;
    }

    @Override
    public void registerKeyBindings() {

        /* FIXME: Overrides the minecraft hotkey for fullscreen, as there are no hooks */
        if (fullscreenKeyBinding == null && ConfigurationHandler.instance()
            .isFullscreenWindowedEnabled()) {
            Minecraft mc = Minecraft.getMinecraft();
            fullscreenKeyBinding = mc.gameSettings.field_152395_am;
            mc.gameSettings.field_152395_am = ignoreKeyBinding;

            if (Display.isFullscreen()) {
                toggleFullScreen(true, Reference.AUTOMATIC_MONITOR_SELECTION);
            }
        } else if (fullscreenKeyBinding != null && !ConfigurationHandler.instance()
            .isFullscreenWindowedEnabled()) {

                Minecraft mc = Minecraft.getMinecraft();
                mc.gameSettings.field_152395_am = fullscreenKeyBinding;
                fullscreenKeyBinding = null;

                if (currentState) {
                    mc.toggleFullscreen();
                }
            }
    }

    @Override
    public void subscribeEvents(File configurationFile) {

        ConfigurationHandler.instance()
            .init(configurationFile);
        FMLCommonHandler.instance()
            .bus()
            .register(ConfigurationHandler.instance());
        FMLCommonHandler.instance()
            .bus()
            .register(new KeyInputEventHandler());
        dsHandler = new DrawScreenEventHandler();
        MinecraftForge.EVENT_BUS.register(dsHandler);
    }

    private Rectangle findCurrentScreenDimensionsAndPosition(int x, int y) {
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] screens = env.getScreenDevices();

        for (GraphicsDevice dev : screens) {
            GraphicsConfiguration displayMode = dev.getDefaultConfiguration();
            Rectangle bounds = displayMode.getBounds();

            if (bounds.contains(x, y)) return bounds;
        }

        // if Java isn't able to find a matching screen then use the old LWJGL calcs.
        return new Rectangle(
            0,
            0,
            Display.getDesktopDisplayMode()
                .getWidth(),
            Display.getDesktopDisplayMode()
                .getHeight());
    }

    private Rectangle findScreenDimensionsByID(int monitorID) {
        if (monitorID < 1) return null;

        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] screens = env.getScreenDevices();

        if (screens == null || screens.length == 0 || screens.length < monitorID) {
            return null;
        }

        return screens[monitorID - 1].getDefaultConfiguration()
            .getBounds();
    }

    /** Calls the Minecraft resize() method so it updates its framebuffer. */
    private void callMinecraftResizeMethod(int w, int h) {
        try {
            Class[] args = new Class[2];
            args[0] = int.class;
            args[1] = int.class;
            Minecraft inst = Minecraft.getMinecraft();
            Method resizeMethod = ReflectionHelper
                .findMethod(Minecraft.class, inst, new String[] { "func_71370_a", "resize" }, args);
            if (resizeMethod != null) {
                Display.update();
                resizeMethod.invoke(inst, w, h);
            }

        } catch (Exception e) {
            LogHelper.warn(
                "Resize method not found or problem found while calling it. Are you using the correct version of the mod for this version of Minecraft?"
                    + e.toString());
        }
    }

    private Rectangle getAppropriateScreenBounds(Rectangle currentCoordinates, int desiredMonitor) {
        Rectangle screenBounds;
        Point centerCoordinates = new Point(
            (int) (currentCoordinates.getMinX() + currentCoordinates.getWidth() / 2),
            (int) (currentCoordinates.getMinY() + currentCoordinates.getHeight() / 2));

        ConfigurationHandler configuration = ConfigurationHandler.instance();
        // First feature mode: Only remove decorations. No need to calculate screen positions, we're not changing size
        // or location.
        if (configuration.areAdvancedFeaturesEnabled() && configuration.isOnlyRemoveDecorations()) {
            screenBounds = currentCoordinates;
        }
        // Custom dimensions enabled: follow requested settings if we can work with them.
        else if (configuration.areAdvancedFeaturesEnabled() && configuration.isCustomFullscreenDimensions()
            && (configuration.getCustomFullscreenDimensionsH() > 256
                && configuration.getCustomFullscreenDimensionsW() > 256)) {
                    screenBounds = new Rectangle(
                        configuration.getCustomFullscreenDimensionsX(),
                        configuration.getCustomFullscreenDimensionsY(),
                        configuration.getCustomFullscreenDimensionsW(),
                        configuration.getCustomFullscreenDimensionsH());

                    // If you've selected a monitor, then X & Y are offsets - easier to do math.
                    if (desiredMonitor > 0) {
                        Rectangle actualScreenBounds = findScreenDimensionsByID(desiredMonitor);
                        if (actualScreenBounds != null) {
                            screenBounds.setLocation(
                                actualScreenBounds.x + screenBounds.x,
                                actualScreenBounds.y + screenBounds.y);
                        }
                    }
                }
        // No specified monitor for fullscreen -> find the one the window is on right now
        else if (desiredMonitor < 0 || desiredMonitor == Reference.AUTOMATIC_MONITOR_SELECTION) {
            // find which monitor we should be using based on the center of the MC window
            screenBounds = findCurrentScreenDimensionsAndPosition(
                (int) centerCoordinates.getX(),
                (int) centerCoordinates.getY());
            // specified monitor for fullscreen -> get dimensions.
        } else {
            screenBounds = findScreenDimensionsByID(desiredMonitor);
            // you've specified a monitor but it doesn't look connected. Revert to automatic mode.
            if (screenBounds == null) {
                screenBounds = findCurrentScreenDimensionsAndPosition(
                    (int) centerCoordinates.getX(),
                    (int) centerCoordinates.getY());
            }
        }

        return screenBounds;
    }

    @Override
    public void toggleFullScreen(boolean goFullScreen, int desiredMonitor) {

        // If we're in actual fullscreen right now, then we need to fix that.
        if (Display.isFullscreen()) {
            currentState = true;
            LogHelper.warn("Display is actual fullscreen! Is Minecraft starting with the option set?");
        }

        // if we have nothing to do (we appear to be in the correct mode) and we're not in actual fullscreen ( that's
        // never an acceptable state in this mod ), just quit now.
        if (currentState == goFullScreen && !Display.isFullscreen()) return;

        // Save our current display parameters
        Rectangle currentCoordinates = new Rectangle(
            Display.getX(),
            Display.getY(),
            Display.getWidth(),
            Display.getHeight());
        if (goFullScreen) _savedWindowedBounds = currentCoordinates;

        // Changing this property and causing a Display update will cause LWJGL to add/remove decorations (borderless).
        System.setProperty("org.lwjgl.opengl.Window.undecorated", goFullScreen ? "true" : "false");

        // Get the fullscreen dimensions for the appropriate screen.
        Rectangle screenBounds = getAppropriateScreenBounds(currentCoordinates, desiredMonitor);

        // This is the new bounds we have to apply.
        Rectangle newBounds = goFullScreen ? screenBounds : _savedWindowedBounds;
        if (newBounds == null) newBounds = screenBounds;

        try {
            Display.setDisplayMode(new DisplayMode((int) newBounds.getWidth(), (int) newBounds.getHeight()));
            Display.setResizable(!goFullScreen);
            Display.setFullscreen(false);

            Display.update();

            Display.setLocation(newBounds.x, newBounds.y);

            callMinecraftResizeMethod((int) newBounds.getWidth(), (int) newBounds.getHeight());

        } catch (LWJGLException e) {
            e.printStackTrace();
        }

        currentState = goFullScreen;
    }

    @Override
    @SuppressWarnings("deprecated")
    public void performStartupChecks() {
        // If the mod is disabled by configuration, just put back the initial value.
        if (!ConfigurationHandler.instance()
            .isFullscreenWindowedEnabled()) {
            Minecraft.getMinecraft().gameSettings.fullScreen = _startupRequestedSetting;
            return;
        }

        if (ConfigurationHandler.instance()
            .isMaximumCompatibilityEnabled()) {
            dsHandler.setInitialFullscreen(
                _startupRequestedSetting,
                ConfigurationHandler.instance()
                    .getFullscreenMonitor());
            // This is the correct way to set fullscreen at launch, but LWJGL limitations means we might crash the game
            // if
            // another mod tries to do a similar Display changing operation. Doesn't help the API says "don't use this"
        } else {
            try {
                // FIXME: Living dangerously here... Is there a better way of doing this?
                SplashProgress.pause();
                toggleFullScreen(
                    _startupRequestedSetting,
                    ConfigurationHandler.instance()
                        .getFullscreenMonitor());
                SplashProgress.resume();
            } catch (NoClassDefFoundError e) {
                LogHelper.warn("Error while doing startup checks, are you using an old version of Forge ? " + e);
                toggleFullScreen(
                    _startupRequestedSetting,
                    ConfigurationHandler.instance()
                        .getFullscreenMonitor());
            }
        }
    }
}
