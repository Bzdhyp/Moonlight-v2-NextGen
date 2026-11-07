package wtf.moonlight.ui.esp;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import wtf.moonlight.module.impl.render.Esp;

import java.util.ArrayList;
import java.util.List;

public final class EspRenderer {
    private final Esp module;
    private final Painter painter;
    private final Matrix4f matrix = new Matrix4f();
    private final Vector4f corner = new Vector4f();

    public EspRenderer(Esp module) {
        this.module = module;
        this.painter = new Painter(module);
    }

    public void frame() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer self = minecraft.player;
        if (!module.isEnabled() || minecraft.level == null || self == null) return;
        Camera camera = minecraft.gameRenderer.mainCamera();
        camera.getViewRotationProjectionMatrix(matrix);
        Vec3 eye = camera.position();
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
        ImGuiIO io = ImGui.getIO();
        List<Target> targets = new ArrayList<>();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!accepts(entity, self, firstPerson)) continue;
            Target target = project(entity, eye, partial, io.getDisplaySizeX(), io.getDisplaySizeY());
            if (target != null) targets.add(target);
        }
        if (targets.isEmpty()) return;
        float unit = minecraft.getWindow().getGuiScale();
        ImDrawList draw = ImGui.getBackgroundDrawList();
        for (Painter.Pass pass : Painter.Pass.values()) {
            for (Target target : targets) painter.paint(draw, target, unit, pass);
        }
    }

    private boolean accepts(Entity entity, LocalPlayer self, boolean firstPerson) {
        if (!entity.isAlive() || entity.isInvisible() && !module.showsInvisible()) return false;
        if (entity instanceof Player player) {
            if (player == self) return !firstPerson && module.shows(Esp.PLAYERS);
            return module.shows(Esp.PLAYERS) && !(module.hidesTeammates() && self.isAlliedTo(player));
        }
        if (entity instanceof Enemy) return module.shows(Esp.MOBS);
        if (entity instanceof Animal || entity instanceof AmbientCreature || entity instanceof WaterAnimal) return module.shows(Esp.ANIMALS);
        if (entity instanceof ItemEntity) return module.shows(Esp.ITEMS);
        if (entity instanceof PrimedTnt) return module.shows(Esp.TNT);
        if (entity instanceof EndCrystal) return module.shows(Esp.CRYSTALS);
        return entity instanceof Fireball && module.shows(Esp.FIREBALLS);
    }

    private @Nullable Target project(Entity entity, Vec3 eye, float partial, float width, float height) {
        Vec3 position = entity.getPosition(partial).subtract(eye);
        double half = (entity.getBbWidth() + 0.14) / 2;
        double tall = entity.getBbHeight() + (entity.isCrouching() ? -0.1 : 0.2) + 0.01;
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double x = position.x + ((i & 1) == 0 ? -half : half);
            double y = position.y + ((i & 2) == 0 ? 0 : tall);
            double z = position.z + ((i & 4) == 0 ? -half : half);
            matrix.transform(corner.set((float) x, (float) y, (float) z, 1));
            if (corner.w <= 0) continue;
            float screenX = (corner.x / corner.w + 1) / 2 * width;
            float screenY = (1 - corner.y / corner.w) / 2 * height;
            minX = Math.min(minX, screenX);
            minY = Math.min(minY, screenY);
            maxX = Math.max(maxX, screenX);
            maxY = Math.max(maxY, screenY);
        }
        if (minX > maxX || maxX < 0 || maxY < 0 || minX > width || minY > height) return null;
        String name = entity.getName().getString();
        if (entity instanceof LivingEntity living) {
            return new Target(minX, minY, maxX, maxY, name, true, living.getHealth(), living.getMaxHealth(), living.getArmorValue(),
                    living.hasEffect(MobEffects.STRENGTH));
        }
        return new Target(minX, minY, maxX, maxY, name, false, 0, 1, 0, false);
    }
}
