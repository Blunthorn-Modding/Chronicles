package io.github.chronicle.core.client;

import io.github.chronicle.core.network.AuthoringPreviewPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.util.math.Box;

/** Frame-rendered Core geometry: remains visible while vanilla simulation/particles are frozen. */
public final class AuthoringPreviewRenderer {
    private static AuthoringPreviewPayload preview;
    private static long expires;
    private static long renderedFrames;

    private AuthoringPreviewRenderer() { }
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(AuthoringPreviewPayload.ID, (payload, context) -> context.client().execute(() -> {
            preview = payload.visible() ? payload : null;
            expires = System.nanoTime() + 5_000_000_000L;
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { preview = null; renderedFrames = 0; });
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(AuthoringPreviewRenderer::render);
    }

    /** Used by the opt-in real-client probe to catch a renderer silently skipping frozen worlds. */
    public static long renderedFrames() { return renderedFrames; }

    private static void render(WorldRenderContext context) {
        var value = preview;
        var client = MinecraftClient.getInstance();
        if (value == null || System.nanoTime() >= expires || client.world == null) { preview = null; return; }
        if (!client.world.getRegistryKey().getValue().toString().equals(value.dimension())) return;
        var camera = context.worldState().cameraRenderState.pos;
        var box = new Box(value.minX(), value.minY(), value.minZ(), value.maxX(), value.maxY(), value.maxZ()).offset(camera.negate());
        var matrices = context.matrices();
        var vertices = context.consumers().getBuffer(RenderLayer.getLines());
        VertexRendering.drawBox(matrices.peek(), vertices, box, 0.15f, 1f, 0.75f, 1f);
        if (value.facing()) {
            double x = (value.minX() + value.maxX()) / 2 - camera.x;
            double y = (value.minY() + value.maxY()) / 2 - camera.y;
            double z = (value.minZ() + value.maxZ()) / 2 - camera.z;
            double yaw = Math.toRadians(value.yaw()), pitch = Math.toRadians(value.pitch());
            for (int step = 1; step <= 8; step++) {
                double length = step / 4.0;
                var marker = Box.of(new net.minecraft.util.math.Vec3d(x - Math.sin(yaw) * Math.cos(pitch) * length,
                        y - Math.sin(pitch) * length, z + Math.cos(yaw) * Math.cos(pitch) * length), .06, .06, .06);
                VertexRendering.drawBox(matrices.peek(), vertices, marker, 1f, .8f, .15f, 1f);
            }
        }
        renderedFrames++;
    }
}
