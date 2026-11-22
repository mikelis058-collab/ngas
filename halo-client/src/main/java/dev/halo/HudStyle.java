package dev.halo;

import dev.halo.module.ModuleManager;
import dev.halo.module.Modules;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Glass versions of the vanilla hotbar, health/food/armor/air, XP and crosshair. */
public final class HudStyle {
    private HudStyle() {}

    private interface Draw { void run(DrawContext ctx); }

    private static float selX = -1f, hp, hpLag, fd, fdLag, armor, air, airShown, gap = 3f;

    public static void register() {
        replace(VanillaHudElements.HOTBAR, HudStyle::hotbar, true);
        replace(VanillaHudElements.HEALTH_BAR, null, false);
        replace(VanillaHudElements.FOOD_BAR, null, false);
        replace(VanillaHudElements.ARMOR_BAR, null, false);
        replace(VanillaHudElements.AIR_BAR, null, false);
        replace(VanillaHudElements.EXPERIENCE_LEVEL, null, false);
        replace(VanillaHudElements.INFO_BAR, null, false);
        replace(VanillaHudElements.CROSSHAIR, HudStyle::crosshair, false);
    }

    private static void replace(Identifier id, Draw draw, boolean hotbar) {
        HudElementRegistry.replaceElement(id, original -> (ctx, tick) -> {
            if (active()) { if (draw != null) draw.run(ctx); }
            else original.render(ctx, tick);
        });
    }

    private static boolean active() {
        MinecraftClient mc = MinecraftClient.getInstance();
        var t = ModuleManager.HUDTHEME;
        return t != null && t.enabled && mc.player != null && !mc.player.isSpectator();
    }

    // ------------------------------------------------------------------ hotbar
    private static void hotbar(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity p = mc.player;
        float dt = ModuleManager.dt;
        int acc = Gfx.accent();
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        int w = 188, h = 24, px = (sw - w) / 2, py = sh - 28;

        Gfx.glass(ctx, px, py, w, h, 7, 1f);

        int sel = p.getInventory().getSelectedSlot();
        selX = selX < 0 ? sel : Anim.approach(selX, sel, 22f, dt);
        int hx = px + 2 + Math.round(selX * 20f);
        Gfx.rrect(ctx, hx, py + 2, 20, 20, 5, Gfx.alpha(0xFFFFFF, 0.17f));
        Gfx.rrect(ctx, hx + 4, py + h - 4, 12, 2, 1, Gfx.alpha(acc, 1f));

        for (int i = 0; i < 9; i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (st.isEmpty()) continue;
            int ix = px + 4 + i * 20, iy = py + 4;
            ctx.drawItem(st, ix, iy);
            ctx.drawStackOverlay(mc.textRenderer, st, ix, iy);
        }

        ItemStack off = p.getOffHandStack();
        if (!off.isEmpty()) {
            Gfx.glass(ctx, px - 27, py + 1, 22, 22, 6, 1f);
            ctx.drawItem(off, px - 24, py + 4);
            ctx.drawStackOverlay(mc.textRenderer, off, px - 24, py + 4);
        }

        if (mc.interactionManager == null || !mc.interactionManager.hasStatusBars()) return;

        // xp: thin line under the panel + level above it
        float xp = Math.max(0f, Math.min(1f, p.experienceProgress));
        Gfx.rrect(ctx, px + 10, py + h + 1, w - 20, 2, 1, Gfx.alpha(0xFFFFFF, 0.12f));
        if (xp > 0.01f) Gfx.rrect(ctx, px + 10, py + h + 1, Math.max(2, Math.round((w - 20) * xp)), 2, 1, Gfx.alpha(acc, 0.95f));
        if (p.experienceLevel > 0) Gfx.centered(ctx, String.valueOf(p.experienceLevel), sw / 2, py - 11, Gfx.alpha(0xFFFFFF, 1f));

        if (!ModuleManager.HUDTHEME.stats.value) return;

        float maxHp = Math.max(1f, p.getMaxHealth());
        hp = Anim.approach(hp, p.getHealth() / maxHp, 14f, dt);
        hpLag = hpLag > hp ? Anim.approach(hpLag, hp, 2.5f, dt) : hp;
        float food = p.getHungerManager().getFoodLevel() / 20f;
        fd = Anim.approach(fd, food, 14f, dt);
        fdLag = fdLag > fd ? Anim.approach(fdLag, fd, 2.5f, dt) : fd;

        boolean low = p.getHealth() <= 6f;
        float pulse = low ? 0.62f + 0.38f * (float) Math.sin(System.nanoTime() / 1.6e8) : 1f;
        float abs = p.getAbsorptionAmount();
        String hpText = Math.round(p.getHealth()) + (abs > 0.5f ? "+" + Math.round(abs) : "");

        statPill(ctx, px, py - 19, hpText, hp, hpLag, pulse);
        statPill(ctx, px + w - 92, py - 19, String.valueOf(p.getHungerManager().getFoodLevel()), fd, fdLag, 1f);

        float a = p.getArmor() / 20f;
        armor = Anim.approach(armor, a, 12f, dt);
        if (p.getArmor() > 0) statPill(ctx, px, py - 36, String.valueOf(p.getArmor()), armor, armor, 0.75f);

        // air bar, only while it matters
        boolean needAir = p.getAir() < p.getMaxAir();
        airShown = Anim.approach(airShown, needAir ? 1f : 0f, 12f, dt);
        if (airShown > 0.02f) {
            air = Anim.approach(air, Math.max(0f, p.getAir() / (float) p.getMaxAir()), 14f, dt);
            int aw = 92, ax = sw / 2 - aw / 2, ay = py - 36 - Math.round((1f - airShown) * 6f);
            Gfx.glass(ctx, ax, ay, aw, 15, 5, airShown);
            Gfx.text(ctx, "AIR", ax + 7, ay + 4, Gfx.alpha(0x8A8A8A, airShown));
            int bx = ax + 34, bw = aw - 34 - 7;
            Gfx.rrect(ctx, bx, ay + 5, bw, 5, 2, Gfx.alpha(0xFFFFFF, 0.12f * airShown));
            if (air > 0.01f) Gfx.rrect(ctx, bx, ay + 5, Math.max(2, Math.round(bw * air)), 5, 2, Gfx.alpha(0xFFFFFF, airShown));
        }
    }

    private static void statPill(DrawContext ctx, int x, int y, String text, float ratio, float lag, float alpha) {
        int w = 92;
        Gfx.glass(ctx, x, y, w, 15, 5, 1f);
        Gfx.text(ctx, text, x + 7, y + 4, Gfx.alpha(0xFFFFFF, 1f));
        int bx = x + 34, bw = w - 34 - 7;
        Gfx.rrect(ctx, bx, y + 5, bw, 5, 2, Gfx.alpha(0xFFFFFF, 0.12f));
        if (lag > ratio + 0.01f) Gfx.rrect(ctx, bx, y + 5, Math.max(2, Math.round(bw * lag)), 5, 2, Gfx.alpha(0x8A8A8A, 0.75f));
        if (ratio > 0.01f) Gfx.rrect(ctx, bx, y + 5, Math.max(2, Math.round(bw * Math.min(1f, ratio))), 5, 2, Gfx.alpha(0xFFFFFF, alpha));
    }

    // --------------------------------------------------------------- crosshair
    private static void crosshair(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !mc.options.getPerspective().isFirstPerson()) return;
        var t = ModuleManager.HUDTHEME;
        int cx = ctx.getScaledWindowWidth() / 2, cy = ctx.getScaledWindowHeight() / 2;

        Vec3d v = mc.player.getVelocity();
        float speed = (float) Math.sqrt(v.x * v.x + v.z * v.z);
        boolean onTarget = mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY;
        float target = t.gap.f() + (t.dynamic.value ? Math.min(5f, speed * 18f) : 0f) + (onTarget ? 1.5f : 0f);
        gap = Anim.approach(gap, target, 16f, ModuleManager.dt);

        int g = Math.round(gap), len = t.size.i();
        int main = Gfx.alpha(0xFFFFFF, onTarget ? 1f : 0.88f), edge = Gfx.alpha(0x000000, 0.55f);
        int mode = t.crosshair.index;

        if (mode >= 1) {
            arm(ctx, cx - g - len, cy, cx - g, cy + 1, main, edge);
            arm(ctx, cx + 1 + g, cy, cx + 1 + g + len, cy + 1, main, edge);
            arm(ctx, cx, cy - g - len, cx + 1, cy - g, main, edge);
            arm(ctx, cx, cy + 1 + g, cx + 1, cy + 1 + g + len, main, edge);
        }
        if (mode == 0 || mode == 2) {
            ctx.fill(cx - 1, cy - 1, cx + 2, cy + 2, edge);
            ctx.fill(cx, cy, cx + 1, cy + 1, main);
        }
    }

    private static void arm(DrawContext ctx, int x1, int y1, int x2, int y2, int main, int edge) {
        ctx.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, edge);
        ctx.fill(x1, y1, x2, y2, main);
    }
}
