package fr.pioneers.integration.jei;

import fr.pioneers.farm.kitchen.Station;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.kinetics.kitchen.KitchenMachineType;
import fr.pioneers.tech.kinetics.kitchen.PiKitchenMachines;
import fr.pioneers.core.registries.PiIndustry;
import fr.pioneers.logistics.transport.CableTier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 1.6 : catalyseurs des categories de cuisine ({@link KitchenCategory}) : les postes mecaniques qui executent les recettes du poste, puis les
 * machines electriques de l'industrie qui en ont la conversion (recettes {@code pioneers:industry/<machine>/kitchen_<poste>_<recette>},
 * ecrites par {@code tools/gc/farm2/farm_industry.py}) : atelier MKI et multibloc de chacune.
 */
final class KitchenMachineCatalysts {

    private KitchenMachineCatalysts() {
    }

    static void register(IRecipeCatalystRegistration registration) {
        for (KitchenMachineType type : KitchenMachineType.values()) {
            for (Station s : type.stations) {
                if (KitchenCategory.TYPES.containsKey(s)) {
                    registration.addRecipeCatalyst(new ItemStack(PiKitchenMachines.machine(type)), KitchenCategory.TYPES.get(s));
                }
            }
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        //Postes les plus longs d'abord : « cheese_pressing_ » avant « pressing_ »
        List<Station> stations = new ArrayList<>(List.of(Station.values()));
        stations.sort(Comparator.comparingInt((Station s) -> s.id().length()).reversed());
        Map<Station, Set<IndustryMachine>> found = new EnumMap<>(Station.class);
        for (IndustrialRecipe r : level.getRecipeManager().getAllRecipesFor(PiIndustry.INDUSTRIAL.get())) {
            String path = r.getId().getPath();
            int at = path.indexOf("/kitchen_");
            if (at < 0) {
                continue;
            }
            String rest = path.substring(at + "/kitchen_".length());
            for (Station s : stations) {
                if (rest.startsWith(s.id() + "_")) {
                    found.computeIfAbsent(s, k -> EnumSet.noneOf(IndustryMachine.class)).add(r.machine());
                    break;
                }
            }
        }
        found.forEach((station, machines) -> {
            if (!KitchenCategory.TYPES.containsKey(station)) {
                return;
            }
            for (IndustryMachine m : machines) {
                var ateliers = PiIndustry.ATELIERS.get(m);
                if (ateliers != null && ateliers.containsKey(CableTier.BASIC)) {
                    registration.addRecipeCatalyst(new ItemStack(ateliers.get(CableTier.BASIC).getBlock()), KitchenCategory.TYPES.get(station));
                }
                if (m.industrial) {
                    registration.addRecipeCatalyst(new ItemStack(PiIndustry.controller(m).getBlock()), KitchenCategory.TYPES.get(station));
                }
            }
        });
    }
}
