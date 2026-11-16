package fr.pioneers.client;

import fr.pioneers.Pioneers;
import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.machine.MachineTooltips;
import fr.pioneers.core.Texts;
import fr.pioneers.core.item.GasCapsuleItem;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.core.data.DataUnits;
import fr.pioneers.tech.energy.MicrobialCellTile;
import fr.pioneers.tech.industry.GeneratorOutput;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.core.registries.PiMachines;
import fr.pioneers.space.tile.TileEntityDataBank;
import fr.pioneers.logistics.transport.CableTier;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fml.common.Mod;

/**
 * Infobulles calculees chez le client (toutes passent par {@link Tooltips}, 1.11.2) :
 * <ul>
 *     <li>contenu des banques de donnees conserve dans l'objet ;</li>
 *     <li>1.11.2 (lot E2) : energie des combustibles (« Combustible : X FE au total, Y FE/t dans ... » : solides, seaux et capsules des
 *     carburants, residus de la pile microbienne ; Maj : chaque tier et le multibloc). Calculs : {@link GeneratorOutput}.</li>
 *     <li>API Machine (lot 1) : infobulle standard des machines, tiree de leur fiche ({@link MachineTooltips}, qui reprend la puissance
 *     nominale des generateurs a recettes).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PiTooltips {

    private PiTooltips() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        for (var entry : PiMachines.DATA_BANKS.entrySet()) {
            if (stack.is(entry.getValue().asItem())) {
                TileEntityDataBank.Tier tier = entry.getKey();
                CompoundTag tag = stack.getTag();
                DataStack data = tag == null ? DataStack.EMPTY : DataStack.read(tag.getCompound("mekData").getCompound("dataTank"));
                Tooltips tips = Tooltips.event(event.getToolTip());
                tips.spec("data_bank.pioneers.capacity", DataUnits.format(tier.capacity));
                if (data.isEmpty()) {
                    tips.hint("data.pioneers.empty");
                } else {
                    tips.state(data.getType().getName().copy().append(" · ").append(DataUnits.format(data.getAmount())));
                }
                //API Machine (lot 1) : puis les lignes standard de sa fiche (tier, faces)
                if (stack.getItem() instanceof BlockItem block) {
                    MachineTooltips.forBlock(block.getBlock(), stack, Minecraft.getInstance().level, event.getToolTip());
                }
                return;
            }
        }
        Level level = Minecraft.getInstance().level;
        if (level == null || stack.isEmpty()) {
            return;
        }
        //API Machine (lot 1) : infobulle standard de toute machine, tiree de sa fiche (puissance des generateurs a recettes comprise)
        //API Multibloc (lot 5) : aussi les pieces et les ports des multiblocs
        if (stack.getItem() instanceof BlockItem block && MachineTooltips.forBlock(block.getBlock(), stack, level, event.getToolTip())) {
            return;
        }
        fuel(stack, level, event);
    }

    /** Ligne « Combustible » d'un objet qui brule, d'un seau ou d'une capsule de carburant, d'un residu organique. */
    private static void fuel(ItemStack stack, Level level, ItemTooltipEvent event) {
        IndustrialRecipe solid = GeneratorOutput.solidFuel(stack);
        if (solid != null) {
            long total = GeneratorOutput.total(solid, false);
            Tooltips tips = Tooltips.event(event.getToolTip());
            tips.energy("tooltip.pioneers.stat.fuel", Component.translatable("energy.pioneers.fe.fuel_line", Texts.grouped(total),
                  Texts.grouped(GeneratorOutput.perTick(solid, 0, false)), atelierName(IndustryMachine.SOLID_COMBUSTION_GENERATOR)));
            tips.details(t -> perTier(t, solid, false, true));
            return;
        }
        GeneratorOutput.Use use = null;
        long amount = 0;
        var fluid = FluidUtil.getFluidContained(stack);
        if (fluid.isPresent() && !fluid.get().isEmpty()) {
            use = GeneratorOutput.fluid(level, fluid.get());
            amount = fluid.get().getAmount();
        } else if (stack.getItem() instanceof GasCapsuleItem) {
            Gas gas = GasCapsuleItem.getGas(stack);
            if (gas != null) {
                use = GeneratorOutput.gas(level, GasStack.of(gas, GasCapsuleItem.CAPACITY));
                amount = GasCapsuleItem.CAPACITY;
            }
        }
        if (use != null) {
            GeneratorOutput.Use u = use;
            Component where = u.industrial() ? controllerName(u.machine()) : atelierName(u.machine());
            Tooltips tips = Tooltips.event(event.getToolTip());
            tips.energy("tooltip.pioneers.stat.fuel", Component.translatable("energy.pioneers.fe.fuel_line", Texts.grouped(u.totalFor(amount)),
                  Texts.grouped(u.perTick(0)), where));
            tips.details(t -> perTier(t, u.recipe(), u.industrial(), !u.industrial()));
            return;
        }
        if (stack.is(MicrobialCellTile.ORGANIC)) {
            Tooltips.event(event.getToolTip()).energy("tooltip.pioneers.stat.fuel", Component.translatable("energy.pioneers.fe.fuel_line",
                  Texts.grouped(GeneratorOutput.microbialTotal(stack, 0)), Texts.grouped(GeneratorOutput.microbialPerTick(0)),
                  Component.translatable("block.pioneers.basic_microbial_fuel_cell")));
        }
    }

    /** Detail sous Maj : puissance et energie a chaque tier (machine d'atelier), puis au multibloc. */
    private static void perTier(Tooltips tips, IndustrialRecipe r, boolean industrialOnly, boolean multiToo) {
        if (!industrialOnly) {
            for (int t = 0; t < 4; t++) {
                tips.spec(Component.translatable("energy.pioneers.fe.kit_tier", Tooltips.tierName(t), Texts.grouped(GeneratorOutput.perTick(r, t, false)),
                      Texts.grouped(GeneratorOutput.total(r, false))));
            }
        }
        if (industrialOnly || multiToo) {
            tips.spec(Component.translatable("energy.pioneers.fe.multi_tier", Tooltips.tierName(0), Texts.grouped(GeneratorOutput.perTick(r, 0, true)),
                  Texts.grouped(GeneratorOutput.total(r, true))));
        }
    }

    private static Component atelierName(IndustryMachine machine) {
        Map<CableTier, ?> tiers = PiIndustry.ATELIERS.get(machine);
        if (tiers != null && PiIndustry.ATELIERS.get(machine).get(CableTier.BASIC) != null) {
            return PiIndustry.ATELIERS.get(machine).get(CableTier.BASIC).getBlock().getName();
        }
        return controllerName(machine);
    }

    private static Component controllerName(IndustryMachine machine) {
        return PiIndustry.controller(machine) != null ? PiIndustry.controller(machine).getBlock().getName()
                                                      : Component.translatable("industry.pioneers.machine." + machine.key);
    }
}
