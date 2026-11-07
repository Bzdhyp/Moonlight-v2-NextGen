package wtf.moonlight.ui.esp;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import imgui.ImDrawList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import wtf.moonlight.ui.imgui.Textures;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

public final class Stage extends AbstractTexture implements Models {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("moonlight", "esp/preview");
    private static final EntitySpawnRequest SPAWN = new EntitySpawnRequest(EntitySpawnReason.LOAD, true);
    private static final int UNLIT_FUSE = 15;

    private final Map<Kind, Entity> entities = new EnumMap<>(Kind.class);
    private final Projection projection = new Projection();
    private final SubmitNodeStorage nodes = new SubmitNodeStorage();
    private @Nullable ProjectionMatrixBuffer matrices;
    private @Nullable GpuTexture depth;
    private @Nullable GpuTextureView depthView;
    private @Nullable ClientLevel level;
    private boolean registered;

    @Override
    public @Nullable String name(Kind kind) {
        Entity entity = entity(kind);
        return entity != null ? entity.getName().getString() : null;
    }

    @Override
    public void draw(ImDrawList draw, Kind kind, View view, @Nullable String tag, float left, float top, float right, float bottom, float scale) {
        int width = (int) (right - left);
        int height = (int) (bottom - top);
        if (render(kind, view, tag != null, width, height, scale)) draw.addImage(Textures.of(ID), left, top, left + width, top + height, 0, 1, 1, 0);
    }

    private @Nullable Entity entity(Kind kind) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel current = minecraft.level;
        if (current == null || minecraft.player == null) return null;
        if (current != level) {
            entities.clear();
            level = current;
        }
        if (kind == Kind.PLAYER) return minecraft.player;
        return entities.computeIfAbsent(kind, key -> spawn(key, current));
    }

    private boolean render(Kind kind, View view, boolean tag, int width, int height, float scale) {
        Entity entity = entity(kind);
        if (entity == null || width <= 0 || height <= 0) return false;
        Minecraft minecraft = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        EntityRenderer<? super Entity, ?> renderer = dispatcher.getRenderer(entity);
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        EntityRenderState state = renderer.createRenderState(entity, partial);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        LocalPlayer player = minecraft.player;
        if (entity != player && player != null) state.ageInTicks = player.tickCount + partial;
        state.nameTag = tag ? entity.getDisplayName() : null;
        state.nameTagAttachment = tag ? entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, 0) : null;
        state.scoreText = null;
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = 180;
            living.yRot = 0;
            living.xRot = 0;
            living.scale = 1;
        }
        if (!registered) {
            minecraft.getTextureManager().register(ID, this);
            registered = true;
        }
        prepare(width, height);
        ProjectionMatrixBuffer buffer = matrices;
        if (buffer == null) {
            buffer = new ProjectionMatrixBuffer("Moonlight ESP preview");
            matrices = buffer;
        }
        RenderSystem.backupProjectionMatrix();
        projection.setupOrtho(-1000, 1000, width, height, true);
        RenderSystem.setProjectionMatrix(buffer.getBuffer(projection), ProjectionType.ORTHOGRAPHIC);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix().identity();
        PoseStack pose = new PoseStack();
        pose.translate(width / 2.0F, height / 2.0F, 0);
        pose.scale(scale, scale, -scale);
        pose.rotate(new Quaternionf().rotateZ((float) Math.PI).rotateX(view.pitch()).rotateY(view.yaw()));
        pose.translate(0, -view.pivotY(), -view.pivotZ());
        minecraft.gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
        CameraRenderState camera = new CameraRenderState();
        if (kind != Kind.FIREBALL) camera.orientation = new Quaternionf().rotationY(-view.yaw()).rotateX(-view.pitch());
        dispatcher.submit(state, camera, 0, 0, 0, pose, nodes);
        GpuTextureView colorView = textureView;
        GpuTextureView depthTarget = depthView;
        if (colorView != null && depthTarget != null) {
            try (FeatureRenderDispatcher.PreparedFrame frame = minecraft.gameRenderer.featureRenderDispatcher().prepareFrame(nodes);
                 RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                         .createRenderPass(() -> "Moonlight ESP preview", colorView, Optional.empty(), depthTarget, OptionalDouble.empty())) {
                RenderSystem.bindDefaultUniforms(pass);
                FeatureRenderDispatcher.renderAllFeatures(pass, frame);
            }
        }
        modelView.popMatrix();
        RenderSystem.restoreProjectionMatrix();
        return true;
    }

    private void prepare(int width, int height) {
        GpuDevice device = RenderSystem.getDevice();
        GpuTexture color = texture;
        if (color != null && (color.getWidth(0) != width || color.getHeight(0) != height)) {
            if (textureView != null) textureView.close();
            color.close();
            texture = null;
            textureView = null;
            if (depthView != null) depthView.close();
            if (depth != null) depth.close();
            depth = null;
            depthView = null;
        }
        if (texture == null) {
            texture = device.createTexture(() -> "Moonlight ESP preview", 13, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
            textureView = device.createTextureView(texture);
            depth = device.createTexture(() -> "Moonlight ESP preview depth", 9, GpuFormat.D32_FLOAT, width, height, 1, 1);
            depthView = device.createTextureView(depth);
        }
        if (depth != null) device.createCommandEncoder().clearColorAndDepthTextures(texture, GuiRenderer.CLEAR_COLOR, depth, 0.0);
    }

    private static @Nullable Entity spawn(Kind kind, ClientLevel level) {
        Entity entity = switch (kind) {
            case MOB -> EntityTypes.ZOMBIE.create(level, SPAWN);
            case ITEM -> new ItemEntity(level, 0, 0, 0, new ItemStack(Items.DIAMOND_SWORD));
            case TNT -> {
                PrimedTnt tnt = EntityTypes.TNT.create(level, SPAWN);
                if (tnt != null) tnt.setFuse(UNLIT_FUSE);
                yield tnt;
            }
            case CRYSTAL -> {
                EndCrystal crystal = EntityTypes.END_CRYSTAL.create(level, SPAWN);
                if (crystal != null) crystal.setShowBottom(true);
                yield crystal;
            }
            case FIREBALL -> EntityTypes.FIREBALL.create(level, SPAWN);
            case PLAYER -> null;
        };
        if (entity != null) entity.setId(-1 - kind.ordinal());
        return entity;
    }
}
