package fr.pioneers.core.network;

import fr.pioneers.equipment.gear.GearEvents;
import fr.pioneers.equipment.gear.GearModule;
import fr.pioneers.equipment.gear.ModularGear;
import fr.pioneers.equipment.gear.Modules;
import fr.pioneers.equipment.gear.electric.HydrogenJetpackItem;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/**
 * 0.20.15 : equipement modulaire, du client au serveur : poussee des reacteurs dorsaux (touche de saut maintenue, envoyee quand elle change)
 * et marche / arret d'un module d'un objet porte (ecran des modules, touche G).
 *
 * 1.0.3-beta (lot 9) : aussi la poussee du jetpack a hydrogene ({@code value} 2 = vol stationnaire) et la bascule de son mode (action 2) ;
 * paquet et version du reseau inchanges (action nouvelle en fin de liste).
 *
 * @param action 0 poussee ({@code value} 1 = en marche), 1 bascule d'un module ({@code slot} = emplacement, {@code value} = index du module),
 *               2 bascule du mode stationnaire du jetpack porte
 */
public record PacketGear(int action, int slot, int value) {

    public static final int THRUST = 0, TOGGLE = 1, HOVER = 2;

    public static PacketGear thrust(boolean on) {
        return new PacketGear(THRUST, 0, on ? 1 : 0);
    }

    /** 1.0.3-beta (lot 9) : poussee du jetpack a hydrogene ({@code value} 0 arret, 1 poussee, 2 vol stationnaire). */
    public static PacketGear jetpack(int state) {
        return new PacketGear(THRUST, 0, state);
    }

    /** 1.0.3-beta (lot 9) : bascule du mode stationnaire du jetpack a hydrogene porte (touche du mode). */
    public static PacketGear hoverToggle() {
        return new PacketGear(HOVER, 0, 0);
    }

    public static PacketGear toggle(EquipmentSlot slot, GearModule module) {
        return new PacketGear(TOGGLE, slot.ordinal(), module.ordinal());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(action);
        buffer.writeByte(slot);
        buffer.writeVarInt(value);
    }

    public static PacketGear decode(FriendlyByteBuf buffer) {
        return new PacketGear(buffer.readByte(), buffer.readByte(), buffer.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null) {
            return;
        }
        if (action == THRUST) {
            GearEvents.setThrusting(player, value != 0, value == 2);
        } else if (action == HOVER) {
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            if (HydrogenJetpackItem.is(chest)) {
                boolean on = !HydrogenJetpackItem.hover(chest);
                HydrogenJetpackItem.setHover(chest, on);
                player.displayClientMessage(Component.translatable(on ? "electric.pioneers.jetpack.mode.hover" : "electric.pioneers.jetpack.mode.normal"), true);
            }
        } else if (action == TOGGLE && slot >= 0 && slot < EquipmentSlot.values().length && value >= 0 && value < GearModule.values().length) {
            ItemStack stack = player.getItemBySlot(EquipmentSlot.values()[slot]);
            GearModule module = GearModule.values()[value];
            if (ModularGear.is(stack) && Modules.installed(stack, module) > 0) {
                Modules.setOff(stack, module, !Modules.isOff(stack, module));
            }
        }
    }
}
