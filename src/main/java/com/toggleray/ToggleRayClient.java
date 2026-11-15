package com.toggleray;

import net.minecraft.client.gl.ShaderProgramKeys;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ToggleRayClient implements ClientModInitializer {
    public static boolean enabled = false;
    private static KeyBinding toggleKey;

    private static final List<BlockPos> debrisPositions = new ArrayList<>();
    private static final List<BlockPos> lavaPositions = new ArrayList<>();
    private static int tickCounter = 0;

    private static final int SCAN_RADIUS = 32;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.toggleray.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_MENU,
                "category.toggleray"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                enabled = !enabled;
                if (client.player != null) {
                    client.player.sendMessage(
                            Text.literal("§6[Toggle ray] §r" + (enabled ? "§aON" : "§cOFF")),
                            true
                    );
                }
                if (enabled) {
                    scanNearbyBlocks(client);
                } else {
                    debrisPositions.clear();
                    lavaPositions.clear();
                }
            }

            if (enabled && client.world != null && client.player != null) {
                tickCounter++;
                if (tickCounter >= 10) {
                    tickCounter = 0;
                    scanNearbyBlocks(client);
                }
            }
        });

        WorldRenderEvents.LAST.register(this::renderOutlines);
    }

    private void scanNearbyBlocks(MinecraftClient client) {
        if (client.world == null || client.player == null) return;

        List<BlockPos> foundDebris = new ArrayList<>();
        List<BlockPos> foundLava = new ArrayList<>();
        BlockPos playerPos = client.player.getBlockPos();

        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
        for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++) {
            for (int y = -SCAN_RADIUS; y <= SCAN_RADIUS; y++) {
                for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
                    mutablePos.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);
                    BlockState state = client.world.getBlockState(mutablePos);

                    if (state.isOf(Blocks.ANCIENT_DEBRIS)) {
                        foundDebris.add(mutablePos.toImmutable());
                    } else if (state.isOf(Blocks.LAVA)) {
                        foundLava.add(mutablePos.toImmutable());
                    }
                }
            }
        }

        debrisPositions.clear();
        debrisPositions.addAll(foundDebris);
        lavaPositions.clear();
        lavaPositions.addAll(foundLava);
    }

    private void renderOutlines(WorldRenderContext context) {
        if (!enabled || (debrisPositions.isEmpty() && lavaPositions.isEmpty())) return;

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        Vec3d cameraPos = context.camera().getPos();

        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.lineWidth(2.5f);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        for (BlockPos pos : debrisPositions) {
            drawBoxOutline(buffer, matrix, pos, 1.0f, 1.0f, 1.0f, 1.0f);
        }

        for (BlockPos pos : lavaPositions) {
            drawBoxOutline(buffer, matrix, pos, 1.0f, 0.1f, 0.1f, 0.85f);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        matrices.pop();
    }

    private void drawBoxOutline(BufferBuilder buffer, Matrix4f matrix, BlockPos pos, float r, float g, float b, float a) {
        float x1 = pos.getX();
        float y1 = pos.getY();
        float z1 = pos.getZ();
        float x2 = x1 + 1.0f;
        float y2 = y1 + 1.0f;
        float z2 = z1 + 1.0f;

        line(buffer, matrix, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(buffer, matrix, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(buffer, matrix, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(buffer, matrix, x1, y1, z2, x1, y1, z1, r, g, b, a);

        line(buffer, matrix, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(buffer, matrix, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(buffer, matrix, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(buffer, matrix, x1, y2, z2, x1, y2, z1, r, g, b, a);

        line(buffer, matrix, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(buffer, matrix, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(buffer, matrix, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(buffer, matrix, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    private void line(BufferBuilder buffer, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
    }
}
