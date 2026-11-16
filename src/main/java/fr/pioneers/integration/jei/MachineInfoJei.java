package fr.pioneers.integration.jei;

import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.machine.MachineKind;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.tech.machine.PiMachineKit;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * MAJ Recettes (lot 8a, brief §5.11) : pages d'information JEI des machines refondues. 1.10.2 : il n'en reste que ce qui ne se lit pas
 * dans les recettes (la case du liant du compacteur, l'objectif de gravure qui n'est pas consomme).
 */
final class MachineInfoJei {

    private MachineInfoJei() {
    }

    /** Machine d'atelier (4 tiers) et son multibloc. */
    private static List<ItemStack> atelier(IndustryMachine machine) {
        List<ItemStack> out = new ArrayList<>();
        if (PiIndustry.ATELIERS.get(machine) != null) {
            PiIndustry.ATELIERS.get(machine).values().forEach(b -> out.add(new ItemStack(b.getBlock())));
        }
        if (PiIndustry.controller(machine) != null) {
            out.add(new ItemStack(PiIndustry.controller(machine).getBlock()));
        }
        return out;
    }

    /** Page des lignes {@code from + 1} a {@code to} de la cle. */
    private static void page(IRecipeRegistration registration, List<ItemStack> stacks, String key, int from, int to) {
        stacks.removeIf(ItemStack::isEmpty);
        if (stacks.isEmpty()) {
            return;
        }
        Component[] text = new Component[to - from];
        for (int i = from; i < to; i++) {
            text[i - from] = Component.translatable("jei.pioneers." + key + ".info_" + (i + 1));
        }
        registration.addIngredientInfo(stacks, VanillaTypes.ITEM_STACK, text);
    }

    static void info(IRecipeRegistration registration) {
        //1.10.2 : seulement l'usage qui ne se voit pas dans les recettes (case du liant ; objectif non consomme qui s'use) ; l'extracteur, le
        //graveur, la decoupeuse et le four de verrerie se lisent dans leurs recettes, l'usure de l'objectif dans « Gestes »
        List<ItemStack> compactor = new ArrayList<>();
        if (PiMachineKit.MACHINES.get(MachineKind.COMPRESSOR) != null) {
            PiMachineKit.MACHINES.get(MachineKind.COMPRESSOR).values().forEach(b -> compactor.add(new ItemStack(b.getBlock())));
        }
        if (PiIndustry.controller(IndustryMachine.COMPRESSOR) != null) {
            compactor.add(new ItemStack(PiIndustry.controller(IndustryMachine.COMPRESSOR).getBlock()));
        }
        page(registration, compactor, "compactor", 1, 2);
        List<ItemStack> objectives = new ArrayList<>();
        for (String kind : new String[]{"glass", "uv", "euv"}) {
            for (String id : new String[]{kind + "_engraving_objective", "worn_" + kind + "_engraving_objective"}) {
                Item item = PiIndustry.item(id);
                if (item != Items.AIR) {
                    objectives.add(new ItemStack(item));
                }
            }
        }
        page(registration, objectives, "engraving_objective", 0, 2);
    }
}
