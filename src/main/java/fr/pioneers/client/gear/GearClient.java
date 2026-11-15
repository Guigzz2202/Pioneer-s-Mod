package fr.pioneers.client.gear;

import fr.pioneers.Pioneers;
import fr.pioneers.client.PiKeys;
import fr.pioneers.gear.GearEvents;
import fr.pioneers.gear.GearModule;
import fr.pioneers.gear.ModularGear;
import fr.pioneers.gear.Modules;
import fr.pioneers.gear.electric.HydrogenJetpackItem;
import fr.pioneers.network.PacketGear;
import fr.pioneers.network.PiNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 0.20.15 : equipement modulaire, cote client : poussee des reacteurs dorsaux (touche de saut maintenue en l'air : le mouvement du joueur
 * se calcule chez lui ; le serveur est prevenu pour l'energie et les flammes), pas de vol libre sans le module de vol (le serveur tolere le
 * joueur en l'air mais le double saut ne doit pas le faire voler), touche G : ecran des modules portes. 1.0.3-beta (lot 9) : jetpack a
 * hydrogene (poussee, vol stationnaire, touche du mode).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
public final class GearClient {

    private GearClient() {
    }

    private static boolean thrusting;
    /** 1.0.3-beta (lot 9) : etat du jetpack a hydrogene envoye au serveur (0 arret, 1 poussee, 2 stationnaire). */
    private static int jetState;

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(event.player instanceof LocalPlayer player) || player != Minecraft.getInstance().player) {
            return;
        }
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        boolean creative = player.isCreative() || player.isSpectator();
        if (!creative && player.getAbilities().flying && player.getAbilities().mayfly && ModularGear.is(chest)
            && Modules.level(chest, GearModule.FLIGHT) <= 0 && Modules.level(chest, GearModule.JETPACK) > 0) {
            player.getAbilities().flying = false;
        }
        if (HydrogenJetpackItem.is(chest)) {
            hydrogen(player, chest, creative);
            return;
        }
        if (jetState != 0) {
            jetState = 0;
            PiNetwork.CHANNEL.sendToServer(PacketGear.jetpack(0));
        }
        int jet = Modules.level(chest, GearModule.JETPACK);
        boolean thrust = jet > 0 && !creative && !player.getAbilities().flying && !player.onGround() && !player.isPassenger()
                         && Minecraft.getInstance().options.keyJump.isDown() && Minecraft.getInstance().screen == null;
        if (thrust) {
            Vec3 v = player.getDeltaMovement();
            double maxUp = 0.22 + 0.08 * jet;
            double vy = Math.min(maxUp, v.y + 0.12 + 0.03 * jet);
            double vx = v.x, vz = v.z;
            if (player.input.forwardImpulse > 0) {
                float yaw = player.getYRot() * Mth.DEG_TO_RAD;
                vx += -Mth.sin(yaw) * 0.012 * jet;
                vz += Mth.cos(yaw) * 0.012 * jet;
            }
            player.setDeltaMovement(vx, vy, vz);
            player.fallDistance = 0;
        }
        if (thrust != thrusting) {
            thrusting = thrust;
            PiNetwork.CHANNEL.sendToServer(PacketGear.thrust(thrust));
        }
    }

    /**
     * 1.0.3-beta (lot 9) : jetpack a hydrogene (case du plastron). Saut maintenu en l'air : poussee (montee jusqu'a 0,36 bloc/tick, avancer
     * donne de l'elan) ; mode stationnaire (touche du mode) : en l'air sans sauter, l'altitude se tient (accroupi : descente douce). Le
     * serveur preleve hydrogene et energie et arrete tout quand l'un manque ; ici on ne pousse que si la pile en a encore (NBT synchronise).
     */
    private static void hydrogen(LocalPlayer player, ItemStack chest, boolean creative) {
        Minecraft mc = Minecraft.getInstance();
        if (!creative && player.getAbilities().flying && player.getAbilities().mayfly && !GearEvents.grantsFlight(player)) {
            player.getAbilities().flying = false;                   //le serveur tolere le joueur en l'air : pas de vol libre au double saut
        }
        boolean free = !creative && !player.getAbilities().flying && !player.onGround() && !player.isPassenger() && !player.isInWater()
                       && !player.isFallFlying();
        //1.0.3-beta (correctif, Guillaume : « jetpack en mode stationnaire qui tombe quand on ouvre un inventaire ») : seule la poussee
        //demande la touche de saut, donc aucun ecran ouvert ; le vol stationnaire continue derriere un inventaire ou un menu
        boolean jump = mc.screen == null && mc.options.keyJump.isDown();
        int state = 0;
        if (free && jump && HydrogenJetpackItem.fueled(chest, false)) {
            state = 1;
        } else if (free && HydrogenJetpackItem.hover(chest) && HydrogenJetpackItem.fueled(chest, true)) {
            state = 2;
        }
        if (state != 0) {
            Vec3 v = player.getDeltaMovement();
            double vy = state == 1 ? Math.min(0.36, v.y + 0.15) : player.isShiftKeyDown() ? Math.max(v.y, -0.18) : 0.0;
            double vx = v.x, vz = v.z;
            if (player.input.forwardImpulse > 0) {
                float yaw = player.getYRot() * Mth.DEG_TO_RAD;
                double push = state == 1 ? 0.03 : 0.015;
                vx += -Mth.sin(yaw) * push;
                vz += Mth.cos(yaw) * push;
            }
            player.setDeltaMovement(vx, vy, vz);
            player.fallDistance = 0;
        }
        if (state != jetState) {
            jetState = state;
            PiNetwork.CHANNEL.sendToServer(PacketGear.jetpack(state));
        }
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        while (PiKeys.JETPACK_MODE.consumeClick()) {
            if (mc.player != null && HydrogenJetpackItem.is(mc.player.getItemBySlot(EquipmentSlot.CHEST))) {
                PiNetwork.CHANNEL.sendToServer(PacketGear.hoverToggle());
            }
        }
        while (PiKeys.GEAR.consumeClick()) {
            if (mc.player == null) {
                return;
            }
            if (GearScreen.anyGear(mc.player)) {
                mc.setScreen(new GearScreen());
            } else {
                mc.player.displayClientMessage(Component.translatable("gear.pioneers.screen.none"), true);
            }
        }
    }
}
