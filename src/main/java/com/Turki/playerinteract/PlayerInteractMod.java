package com.Turki.playerinteract;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import java.util.concurrent.TimeUnit;

@Mod(modid = "playerinteract", version = "1.3", clientSideOnly = true)
public class PlayerInteractMod {
    private static boolean isScrollHeld = false;
    private static EntityPlayer targetPlayer = null;
    private static int selectedOption = -1;
    // For head rotation effect
    private static float headYaw = 0f;
    private static float headPitch = 0f;
    private static long lastCursorUpdate = System.nanoTime();
    
    private static final MenuOption[] LEFT_OPTIONS = {
        new MenuOption("Fly", "/hfly <name>", 0xFF4CAF50),
        new MenuOption("KillAura", "/hkillaura <name>", 0xFF2196F3),
        new MenuOption("AimBot", "/haimbot <name>", 0xFFF44336),
        new MenuOption("NoKnockback", "/hnokb <name>", 0xFFFFC107),
        new MenuOption("AutoArmor", "/hAutoArmor <name>", 0xFF9C27B0)
    };
    
    private static final MenuOption[] RIGHT_OPTIONS = {
        new MenuOption("Bedwars", "/bhw <name>", 0xFFE91E63),
        new MenuOption("Skywars", "/hsw <name>", 0xFF673AB7),
        new MenuOption("Eggwars", "/hteamingew <name>", 0xFF009688)
    };

    private static final int OPTION_HEIGHT = 28;
    private static final int ELEMENT_WIDTH = 100;
    private static final int ELEMENT_HEIGHT = 20;
    private static final int MENU_OFFSET_FROM_CENTER = 60;
    private static final int BACKGROUND_COLOR = 0xDD2D2D2D;
    private static final int HOVER_COLOR = 0xDD404040;
    private static final float MOUSE_SENSITIVITY = 3.5f;
    // Instead of a linear smoothing factor, we use an exponential smoothing constant.
    private static final float SMOOTHING_CONSTANT = 10.0f;
    private static final long FRAME_TIME = TimeUnit.SECONDS.toNanos(1) / 60;
    private static final float HEAD_ROTATION_SPEED = 0.15f;
    // Size for the rendered head in 3D (the rendered model will be scaled accordingly)
    private static final int HEAD_SIZE = 40;
    private static final int NAME_HEAD_SPACING = 15; // Added spacing constant
    
    private static float cursorX;
    private static float cursorY;
    private static boolean wasMouseGrabbed = true;

    private static class MenuOption {
        String name;
        String command;
        int color;

        MenuOption(String name, String command, int color) {
            this.name = name;
            this.command = command;
            this.color = color;
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRenderGameOverlayPre(RenderGameOverlayEvent.Pre event) {
        if (isScrollHeld && event.type == RenderGameOverlayEvent.ElementType.CROSSHAIRS) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.ClientTickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;

        boolean isMiddleMouseDown = Mouse.isButtonDown(2);

        if (isMiddleMouseDown && !isScrollHeld) {
            MovingObjectPosition mop = mc.objectMouseOver;
            if (mop != null && mop.entityHit instanceof EntityPlayer) {
                targetPlayer = (EntityPlayer) mop.entityHit;
                isScrollHeld = true;
                wasMouseGrabbed = Mouse.isGrabbed();
                mc.mouseHelper.grabMouseCursor();
                Mouse.setGrabbed(true);
                mc.inGameHasFocus = true;

                ScaledResolution scaled = new ScaledResolution(mc);
                cursorX = scaled.getScaledWidth() / 2.0f;
                cursorY = scaled.getScaledHeight() / 2.0f;
                Mouse.getDX();
                Mouse.getDY();
                lastCursorUpdate = System.nanoTime();
            }
        } else if (!isMiddleMouseDown && isScrollHeld) {
            if (selectedOption != -1) handleOptionSelected(selectedOption);
            isScrollHeld = false;
            targetPlayer = null;
            selectedOption = -1;

            if (wasMouseGrabbed) {
                mc.mouseHelper.grabMouseCursor();
                Mouse.setGrabbed(true);
            } else {
                mc.mouseHelper.ungrabMouseCursor();
                Mouse.setGrabbed(false);
            }
            mc.inGameHasFocus = wasMouseGrabbed;
        }

        if (isScrollHeld && targetPlayer != null) {
            updateCursor(mc);
            updateSelectedOption(mc);
        }
    }

    /**
     * Update the cursor position using an exponential smoothing (low-pass filter)
     * for smoother movement.
     */
    private void updateCursor(Minecraft mc) {
        long currentTime = System.nanoTime();
        long deltaNanos = currentTime - lastCursorUpdate;
        if (deltaNanos < FRAME_TIME) {
            return;
        }
        float deltaTime = deltaNanos / 1_000_000_000f;
        lastCursorUpdate = currentTime;

        float rawDeltaX = Mouse.getDX() * MOUSE_SENSITIVITY;
        float rawDeltaY = Mouse.getDY() * MOUSE_SENSITIVITY;
        ScaledResolution scaled = new ScaledResolution(mc);

        float targetX = Math.max(0, Math.min(scaled.getScaledWidth(), cursorX + rawDeltaX));
        float targetY = Math.max(0, Math.min(scaled.getScaledHeight(), cursorY - rawDeltaY));

        // Exponential smoothing: alpha = 1 - exp(-k * deltaTime)
        float alpha = 1 - (float)Math.exp(-SMOOTHING_CONSTANT * deltaTime);
        cursorX += (targetX - cursorX) * alpha;
        cursorY += (targetY - cursorY) * alpha;
    }

    private void updateSelectedOption(Minecraft mc) {
        ScaledResolution scaled = new ScaledResolution(mc);
        selectedOption = -1;
        int centerX = scaled.getScaledWidth() / 2;

        // Check left menu
        int leftMenuX = centerX - MENU_OFFSET_FROM_CENTER - ELEMENT_WIDTH;
        int leftStartY = (scaled.getScaledHeight() / 2) - ((LEFT_OPTIONS.length * OPTION_HEIGHT) / 2);
        
        for (int i = 0; i < LEFT_OPTIONS.length; i++) {
            int optionY = leftStartY + (i * OPTION_HEIGHT);
            if (cursorX >= leftMenuX && cursorX <= leftMenuX + ELEMENT_WIDTH &&
                cursorY >= optionY && cursorY <= optionY + ELEMENT_HEIGHT) {
                selectedOption = i;
                return;
            }
        }

        // Check right menu
        int rightMenuX = centerX + MENU_OFFSET_FROM_CENTER;
        int rightStartY = (scaled.getScaledHeight() / 2) - ((RIGHT_OPTIONS.length * OPTION_HEIGHT) / 2);
        
        for (int i = 0; i < RIGHT_OPTIONS.length; i++) {
            int optionY = rightStartY + (i * OPTION_HEIGHT);
            if (cursorX >= rightMenuX && cursorX <= rightMenuX + ELEMENT_WIDTH &&
                cursorY >= optionY && cursorY <= optionY + ELEMENT_HEIGHT) {
                selectedOption = i + LEFT_OPTIONS.length;
                return;
            }
        }
    }

    /**
     * Update head rotation for the dynamic effect.
     */
    private void updateHeadRotation(int centerX, int centerY) {
        float deltaX = cursorX - centerX;
        float deltaY = cursorY - centerY;
        float targetYaw = Math.max(-50, Math.min(50, deltaX * 0.3f));
        float targetPitch = Math.max(-30, Math.min(30, deltaY * 0.3f));
        
        headYaw += (targetYaw - headYaw) * HEAD_ROTATION_SPEED;
        headPitch += (targetPitch - headPitch) * HEAD_ROTATION_SPEED;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.TEXT || !isScrollHeld || targetPlayer == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRendererObj;
        ScaledResolution scaled = event.resolution;
        int centerX = scaled.getScaledWidth() / 2;
        int centerY = scaled.getScaledHeight() / 2;

        // Update head rotation for dynamic effect.
        updateHeadRotation(centerX, centerY);
        
        // Draw the 3D head. We'll position it at centerX and a Y position (e.g., centerY - 60).
        int headRenderY = centerY - 60;
        drawPlayerHead3D(centerX, headRenderY);
        
        // Draw the player name above the head.
        String playerName = "§e" + targetPlayer.getName();
        int nameWidth = fr.getStringWidth(playerName);
        int nameY = headRenderY - (HEAD_SIZE / 2) - NAME_HEAD_SPACING; // Increased spacing
        fr.drawStringWithShadow(playerName, centerX - nameWidth / 2, nameY, 0xFFFFFF00);

        // Draw left options
        int leftStartY = (centerY) - ((LEFT_OPTIONS.length * OPTION_HEIGHT) / 2);
        int leftMenuX = centerX - MENU_OFFSET_FROM_CENTER - ELEMENT_WIDTH;
        drawOptions(fr, LEFT_OPTIONS, leftMenuX, leftStartY, true);

        // Draw right options
        int rightStartY = (centerY) - ((RIGHT_OPTIONS.length * OPTION_HEIGHT) / 2);
        int rightMenuX = centerX + MENU_OFFSET_FROM_CENTER;
        drawOptions(fr, RIGHT_OPTIONS, rightMenuX, rightStartY, false);

        drawCustomCursor(fr);
    }

    /**
     * Renders only the player’s head in 3D.
     * We cast to AbstractClientPlayer to access getLocationSkin() in 1.8.9,
     * then bind the skin and render only the head part of a ModelPlayer.
     */
    private void drawPlayerHead3D(int x, int y) {
        Minecraft mc = Minecraft.getMinecraft();
        AbstractClientPlayer acp = (AbstractClientPlayer) targetPlayer;
        mc.getTextureManager().bindTexture(acp.getLocationSkin());

        GlStateManager.pushMatrix();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        RenderHelper.enableStandardItemLighting();
        
        // Set up proper view translation
        GlStateManager.translate(x, y + NAME_HEAD_SPACING, 50.0F); // Move down to create space
        GlStateManager.scale(-HEAD_SIZE, HEAD_SIZE, HEAD_SIZE);
        GlStateManager.rotate(180F, 0.0F, 1.0F, 0.0F);
        
        // Apply dynamic head rotation
        GlStateManager.rotate(-headYaw, 0.0F, 1.0F, 0.0F); // Inverted yaw rotation
        GlStateManager.rotate(headPitch, 1.0F, 0.0F, 0.0F);

        // Initialize player model
        ModelPlayer model = new ModelPlayer(0.0F, false);
        model.bipedBody.showModel = false;
        model.bipedLeftArm.showModel = false;
        model.bipedRightArm.showModel = false;
        model.bipedLeftLeg.showModel = false;
        model.bipedRightLeg.showModel = false;
        model.bipedHeadwear.showModel = true; // Keep headwear visible if needed
        
        // Render the head
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        
        model.bipedHead.render(0.0625F);
        model.bipedHeadwear.render(0.0625F);

        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.popMatrix();
        
        // Reset texture state
        mc.getTextureManager().bindTexture(Gui.icons);
    }

    private void drawOptions(FontRenderer fr, MenuOption[] options, int menuX, int startY, boolean isLeft) {
        for (int i = 0; i < options.length; i++) {
            int optionY = startY + (i * OPTION_HEIGHT);
            boolean isSelected = selectedOption == (isLeft ? i : i + LEFT_OPTIONS.length);
            int bgColor = isSelected ? HOVER_COLOR : BACKGROUND_COLOR;
            
            drawRoundedPanel(menuX, optionY, ELEMENT_WIDTH, ELEMENT_HEIGHT, bgColor, 0x60FFFFFF, 4);
            
            fr.drawStringWithShadow(options[i].name,
                    menuX + (ELEMENT_WIDTH - fr.getStringWidth(options[i].name)) / 2,
                    optionY + (ELEMENT_HEIGHT - 8) / 2,
                    options[i].color);
        }
    }

    private void drawRoundedPanel(int x, int y, int width, int height, int bgColor, int borderColor, int radius) {
        // Draw the main background.
        Gui.drawRect(x + radius, y, x + width - radius, y + height, bgColor);
        Gui.drawRect(x, y + radius, x + width, y + height - radius, bgColor);
        
        // Draw borders.
        Gui.drawRect(x + radius, y, x + width - radius, y + 1, borderColor);
        Gui.drawRect(x + radius, y + height - 1, x + width - radius, y + height, borderColor);
        Gui.drawRect(x, y + radius, x + 1, y + height - radius, borderColor);
        Gui.drawRect(x + width - 1, y + radius, x + width, y + height - radius, borderColor);
    }

    private void drawCustomCursor(FontRenderer fr) {
        String cursor = "+";
        float scale = 1.8F;
        
        GL11.glPushMatrix();
        GL11.glTranslatef(cursorX - (fr.getStringWidth(cursor) * scale / 2),
                         cursorY - (fr.FONT_HEIGHT * scale / 2), 0);
        GL11.glScalef(scale, scale, scale);
        
        fr.drawString(cursor, 1, 1, 0x80000000);
        fr.drawStringWithShadow(cursor, 0, 0, 0xFFFFFFFF);
        
        GL11.glPopMatrix();
    }

    private void handleOptionSelected(int option) {
        if (targetPlayer == null) return;

        MenuOption selectedOpt;
        if (option < LEFT_OPTIONS.length) {
            selectedOpt = LEFT_OPTIONS[option];
        } else {
            selectedOpt = RIGHT_OPTIONS[option - LEFT_OPTIONS.length];
        }

        String message = "§a[Action] §fUsed " + selectedOpt.name + " on " + targetPlayer.getName();
        Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(message));
        sendCommand(selectedOpt.command);
    }

    private void sendCommand(String command) {
        command = command.replace("<name>", targetPlayer.getName());
        Minecraft.getMinecraft().thePlayer.sendChatMessage(command);
    }
}
