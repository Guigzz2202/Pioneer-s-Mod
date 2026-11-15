package fr.pioneers.client.gear;

import fr.pioneers.Pioneers;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.gear.GearEvents;
import fr.pioneers.gear.GearModule;
import fr.pioneers.gear.ModularArmorItem;
import fr.pioneers.gear.ModularGear;
import fr.pioneers.gear.Modules;
import fr.pioneers.gear.electric.ElectricCharge;
import fr.pioneers.gear.electric.FlamethrowerItem;
import fr.pioneers.gear.electric.HydrogenJetpackItem;
import fr.pioneers.gear.electric.PoweredItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 0.20.15 : affichage de l'armure modulaire (en bas a gauche) : une jauge d'energie par piece portee (couleur du tier), et les etats des
 * reacteurs (poussee) et du vol ; enregistre aussi la couche lumineuse des armures sur les joueurs. 1.0.3-beta (lot 9) : aussi l'armure
 * Voltaique et le jetpack (energie en jaune), l'hydrogene du jetpack et du lance-flammes tenu (blanc bleute), et leur etat.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GearHud {

    private GearHud() {
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "modular_gear", GearHud::render);
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new ModularGlowLayer(renderer));
            }
        }
    }

    /** Jauge verticale 4 x 22 (fond sombre, remplissage {@code ratio}, clignote en rouge sous 10 %). */
    private static void gauge(GuiGraphics g, Player player, int x, int y, float ratio, int color) {
        g.fill(x, y, x + 4, y + 22, 0xC005080B);
        int h = Math.round(20 * Math.max(0, Math.min(1, ratio)));
        if (h > 0) {
            g.fill(x + 1, y + 21 - h, x + 3, y + 21, ratio < 0.1F && player.tickCount % 20 < 10 ? 0xFFFF5050 : 0xFF000000 | color);
        }
    }

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || player.isSpectator()) {
            fr.pioneers.client.look.HudZones.clear("gear");
            return;
        }
        //1.0.5-beta (lot 26, retour 16) : coin bas gauche fixe (HudLayouts.gear), les jauges s'ajoutent vers la droite
        int[] anchorBox = fr.pioneers.client.look.HudLayouts.gear(width, height, 0, 23);
        int x = anchorBox[0], y = anchorBox[1];
        boolean any = false;
        //Une jauge d'energie par piece portee : modulaire (couleur du tier), Voltaique ou jetpack (jaune) ; 1.0.3-beta (lot 9)
        for (EquipmentSlot slot : GearEvents.ARMOR) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof ModularArmorItem armor) {
                gauge(g, player, x, y, Modules.ratio(stack), armor.tier().color);
                any = true;
            } else if (PoweredItem.is(stack)) {
                gauge(g, player, x, y, ElectricCharge.ratio(stack), ElectricCharge.ENERGY_BAR);
                any = true;
            }
            x += 6;
        }
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack held = player.getMainHandItem().getItem() instanceof FlamethrowerItem ? player.getMainHandItem()
                         : player.getOffhandItem().getItem() instanceof FlamethrowerItem ? player.getOffhandItem() : ItemStack.EMPTY;
        //Hydrogene du jetpack et du lance-flammes tenu : jauge blanc bleute
        if (HydrogenJetpackItem.is(chest)) {
            gauge(g, player, x, y, ElectricCharge.hydrogenRatio(chest), ElectricCharge.HYDROGEN_BAR);
            x += 6;
            any = true;
        }
        if (!held.isEmpty()) {
            x += 2;
            gauge(g, player, x, y, ElectricCharge.hydrogenRatio(held), ElectricCharge.HYDROGEN_BAR);
            gauge(g, player, x + 6, y, ElectricCharge.ratio(held), ElectricCharge.ENERGY_BAR);
            x += 12;
            any = true;
        }
        if (!any) {
            fr.pioneers.client.look.HudZones.clear("gear");
            return;
        }
        //1.0.5-beta (lot 14) : place prise (jauges et lignes de 0,6 a leur droite), evitee par la fiche au regard
        fr.pioneers.client.look.HudZones.mark("gear", 4, y, x + 2 + 140, y + 23);
        int ty = y + 1;
        if (ModularGear.is(chest) && Modules.level(chest, GearModule.FLIGHT) > 0) {
            GuiDraw.text(g, mc.font, Component.translatable(player.getAbilities().flying ? "gear.pioneers.hud.flying" : "gear.pioneers.hud.flight"),
                  x + 2, ty, 0.6F, player.getAbilities().flying ? 0xFF8CF5C6 : 0xFF8FA3A0, 120);
            ty += 7;
        }
        if (ModularGear.is(chest) && Modules.level(chest, GearModule.JETPACK) > 0) {
            boolean thrust = !player.onGround() && mc.options.keyJump.isDown() && !player.getAbilities().flying;
            GuiDraw.text(g, mc.font, Component.translatable(thrust ? "gear.pioneers.hud.thrust" : "gear.pioneers.hud.jetpack"), x + 2, ty, 0.6F,
                  thrust ? 0xFFFFB050 : 0xFF8FA3A0, 120);
            ty += 7;
        }
        if (HydrogenJetpackItem.is(chest)) {
            boolean dry = !HydrogenJetpackItem.fueled(chest, false);
            boolean thrust = !dry && !player.onGround() && mc.options.keyJump.isDown();
            String key = dry ? "electric.pioneers.hud.jetpack.empty" : thrust ? "gear.pioneers.hud.thrust"
                         : HydrogenJetpackItem.hover(chest) ? "electric.pioneers.hud.jetpack.hover" : "electric.pioneers.hud.jetpack.ready";
            GuiDraw.text(g, mc.font, Component.translatable(key, Math.round(100 * ElectricCharge.hydrogenRatio(chest)) + "%"), x + 2, ty, 0.6F,
                  dry ? 0xFFFF6060 : thrust ? 0xFFFFB050 : 0xFF8FA3A0, 140);
            ty += 7;
        }
        if (!held.isEmpty()) {
            boolean dry = !FlamethrowerItem.fueled(held);
            GuiDraw.text(g, mc.font, Component.translatable(dry ? "electric.pioneers.hud.flamethrower.empty" : "electric.pioneers.hud.flamethrower",
                  Math.round(100 * ElectricCharge.hydrogenRatio(held)) + "%"), x + 2, ty, 0.6F, dry ? 0xFFFF6060 : 0xFFFFC070, 140);
        }
    }
}
