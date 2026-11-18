package fr.pioneers.integration.jei;

import fr.pioneers.tech.bronze.Hearth;
import fr.pioneers.tech.bronze.steam.BoilerPages;
import fr.pioneers.tech.bronze.steam.BoilerPart;
import fr.pioneers.tech.bronze.steam.SteamAccessory;
import fr.pioneers.tech.bronze.steam.SteamRules;
import fr.pioneers.tech.bronze.PiBronze;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.0.3-beta (lot 14, Guillaume : « travail approfondi sur la chaudiere : JEI, equilibrage, interface, pour la finaliser parfaitement ») : page
 * JEI « Chaudiere a vapeur » (age du bronze : foyer + corps + domes + cheminee, {@code pioneers:steam_boiler}) dans la categorie explicative
 * commune {@link GuideCategory}. TOUS les chiffres sortent du code du jeu ({@link BoilerPages} (sans Minecraft, lu aussi par le banc),
 * {@link Hearth#bonusOf}, duree de combustion Forge) : structure, effet de la cheminee, nombre de corps, surete, consommateurs, et une page par
 * groupe de combustibles de meme duree et meme chaleur (l'objet defile dans la case : U sur un combustible ouvre sa page).
 * La chaudiere industrielle (multibloc de l'age industriel) garde ses recettes : elle ne partage pas ce sujet.
 */
final class BoilerJei {

    static final RecipeType<GuideCategory.Entry> TYPE = JeiOrder.create("steam_boiler", GuideCategory.Entry.class);

    private BoilerJei() {
    }

    private static Component tr(BoilerPages.Line line) {
        return Component.translatable(line.key(), line.args());
    }

    private static List<ItemStack> stacks(String id) {
        List<ItemStack> out = new ArrayList<>();
        if (BoilerPages.MACHINES.equals(id)) {
            PiBronze.STEAM_MACHINES.values().forEach(b -> out.add(new ItemStack(b)));
        } else {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            if (item != null && item != Items.AIR) {
                out.add(new ItemStack(item));
            }
        }
        return out;
    }

    /** Les pages de {@link BoilerPages} (objets poses, textes traduits) puis une page par groupe de combustibles (meme duree, meme bonus). */
    static List<GuideCategory.Entry> entries() {
        List<GuideCategory.Entry> out = new ArrayList<>();
        for (BoilerPages.Page page : BoilerPages.pages()) {
            List<List<ItemStack>> slots = new ArrayList<>();
            for (List<String> ids : page.slots()) {
                List<ItemStack> alt = new ArrayList<>();
                ids.forEach(id -> alt.addAll(stacks(id)));
                if (!alt.isEmpty()) {
                    slots.add(alt);
                }
            }
            out.add(GuideCategory.Entry.of(slots, List.of(), Component.translatable(page.title()), page.lines().stream().map(BoilerJei::tr).toList()));
        }
        Map<String, List<ItemStack>> groups = new LinkedHashMap<>();
        for (Item item : ForgeRegistries.ITEMS) {
            ItemStack stack = new ItemStack(item);
            int burn = ForgeHooks.getBurnTime(stack, null);
            if (burn <= 0 || item == Items.AIR) {
                continue;
            }
            groups.computeIfAbsent(burn + ":" + Hearth.bonusOf(stack), k -> new ArrayList<>()).add(stack);
        }
        groups.entrySet().stream().sorted(Comparator.comparingDouble((Map.Entry<String, List<ItemStack>> e) -> {
            String[] k = e.getKey().split(":");
            return -SteamRules.steamPerFuel(Integer.parseInt(k[0]), Integer.parseInt(k[1]));
        })).forEach(e -> {
            String[] k = e.getKey().split(":");
            List<Component> lines = BoilerPages.fuel(Integer.parseInt(k[0]), Integer.parseInt(k[1])).stream().map(BoilerJei::tr).toList();
            out.add(GuideCategory.Entry.of(List.of(e.getValue()), List.of(), Component.translatable(BoilerPages.K + "fuel",
                  e.getValue().get(0).getHoverName()), lines));
        });
        return out;
    }

    static void catalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(PiBronze.FIREBOX), TYPE);
        for (BoilerPart part : BoilerPart.values()) {
            registration.addRecipeCatalyst(new ItemStack(PiBronze.BOILER_PARTS.get(part)), TYPE);
        }
        registration.addRecipeCatalyst(new ItemStack(PiBronze.ACCESSORIES.get(SteamAccessory.SAFETY_VALVE)), TYPE);
    }
}
