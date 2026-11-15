package fr.pioneers.gear;

import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.registries.PiGear;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * API Machine (lot 8c) : LOGIQUE DE L'ETABLI DE MODULATION (code d'avant de {@link ModulationBenchTile}, deplace) : cases de l'objet
 * modulaire, du module et de la batterie (declarees par l'unite dans le meme ordre qu'avant), recharge de l'objet, installation, retrait,
 * marche / arret des modules. Rien de plus a sauver (cases : cles de la machine).
 * <p>
 * Valeur nommee (apres les 4 communes, meme indice qu'avant) : recharge de l'objet en cours (FE/t).
 */
public final class ModulationUnit extends MachineUnit {

    private static final int CHARGE_RATE = 20_000;

    final ItemSlot gear, module, battery;
    private long charging;

    public ModulationUnit(MachineTile.Access machine) {
        super(machine);
        gear = machine.slot(ItemSlot.of(ModularGear::is, machine::changed, ModulationBenchTile.GEAR_X, ModulationBenchTile.GEAR_Y).ghostIcon("ghost_gear"));
        module = machine.slot(ItemSlot.of(s -> s.getItem() instanceof ModuleItem, machine::changed, ModulationBenchTile.MODULE_X, ModulationBenchTile.MODULE_Y)
              .ghostIcon("ghost_module"));
        battery = machine.batterySlot(ModulationBenchTile.BATTERY_X, ModulationBenchTile.BATTERY_Y);
    }

    @Override
    public void tick() {
        machine.discharge(battery, CHARGE_RATE);
        long before = machine.energy().getStored();
        ItemStack stack = gear.getStack();
        if (ModularGear.is(stack) && Modules.energy(stack) < Modules.capacity(stack) && machine.energy().getStored() > 0) {
            long given = Modules.charge(stack, Math.min(CHARGE_RATE, machine.energy().getStored()));
            machine.energy().extract(given, false);
            if (given > 0) {
                machine.changed();
            }
        }
        charging = Math.max(0, before - machine.energy().getStored());
        machine.markWorking(charging > 0);
    }

    /** Actions du joueur : installer, retirer, allumer ou couper un module. */
    void handleCustom(ServerPlayer player, int action) {
        ItemStack stack = gear.getStack();
        if (!ModularGear.is(stack)) {
            return;
        }
        if (action == ModulationBenchTile.INSTALL) {
            if (!(module.getStack().getItem() instanceof ModuleItem item)) {
                return;
            }
            String refuse = Modules.refuse(stack, item.module);
            if (refuse != null) {
                player.displayClientMessage(Component.translatable(refuse).withStyle(ChatFormatting.RED), true);
                return;
            }
            Modules.install(stack, item.module);
            ItemStack rest = module.getStack().copy();
            rest.shrink(1);
            module.setStack(rest);
            level().playSound(null, pos(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.7F, 1.4F);
            machine.changed();
            return;
        }
        List<GearModule> list = Modules.list(stack);
        if (action >= ModulationBenchTile.REMOVE && action < ModulationBenchTile.REMOVE + list.size()) {
            GearModule m = list.get(action - ModulationBenchTile.REMOVE);
            if (Modules.uninstall(stack, m)) {
                ItemStack back = new ItemStack(PiGear.MODULES.get(m).get());
                ItemStack slot = module.getStack();
                if (slot.isEmpty()) {
                    module.setStack(back);
                } else if (ItemStack.isSameItemSameTags(slot, back) && slot.getCount() < slot.getMaxStackSize()) {
                    ItemStack grown = slot.copy();
                    grown.grow(1);
                    module.setStack(grown);
                } else if (!player.getInventory().add(back)) {
                    player.drop(back, false);
                }
                level().playSound(null, pos(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 0.8F);
                machine.changed();
            }
        } else if (action >= ModulationBenchTile.TOGGLE && action < ModulationBenchTile.TOGGLE + list.size()) {
            GearModule m = list.get(action - ModulationBenchTile.TOGGLE);
            Modules.setOff(stack, m, !Modules.isOff(stack, m));
            machine.changed();
        }
    }

    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(ModulationBenchTile.CHARGING, () -> charging);
    }
}
