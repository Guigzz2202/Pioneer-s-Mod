package fr.pioneers.core.registries;

import fr.pioneers.Pioneers;
import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.DataType;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.tech.computing.DataDiskItem;
import fr.pioneers.core.gas.PiGasTypes;
import fr.pioneers.core.item.GasCapsuleItem;
import fr.pioneers.core.data.CreativeDataTank;
import fr.pioneers.core.data.PiDataTypes;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.core.registries.CreativeSort.Layout;
import fr.pioneers.core.registries.CreativeSort.Placement;
import fr.pioneers.core.registries.CreativeSort.Section;
import fr.pioneers.core.registries.CreativeSort.Tab;
import fr.pioneers.space.farm.SpacePlant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Onglets creatifs du mod (1.0 alpha). Le PLAN (onglets, sections, ordre) est entierement dans {@link CreativeSort} : ici on enregistre un
 * onglet par {@link Tab}, dans l'ordre, et on y verse les objets du registre places et tries par le plan, plus les variantes a NBT
 * (capsules pleines, banque creative pleine, disquette d'exemples). Controle hors jeu : {@code tools/creative/check_tabs.py}.
 */
public final class PiCreativeTabs {

    private PiCreativeTabs() {
    }

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Pioneers.MODID);

    /** Onglet enregistre de chaque {@link Tab}. */
    public static final Map<Tab, RegistryObject<CreativeModeTab>> BY_TAB = new EnumMap<>(Tab.class);

    static {
        RegistryObject<CreativeModeTab> previous = null;
        for (Tab tab : Tab.values()) {
            RegistryObject<CreativeModeTab> after = previous;
            previous = TABS.register(tab.id, () -> {
                CreativeModeTab.Builder builder = CreativeModeTab.builder()
                      .title(Component.translatable("itemGroup." + Pioneers.MODID + "." + tab.id))
                      .icon(() -> icon(tab))
                      .displayItems((parameters, output) -> contents().getOrDefault(tab, List.of()).forEach(item -> {
                          output.accept(new ItemStack(item));
                          variants(item).forEach(output::accept);
                      }));
                if (after != null) {
                    builder.withTabsBefore(after.getKey());
                }
                return builder.build();
            });
            BY_TAB.put(tab, previous);
        }
    }

    // ------------------------------------------------------------------
    // Contenu
    // ------------------------------------------------------------------

    /** Objets de chaque onglet, dans l'ordre (calcule une fois : le registre des objets est fige quand les onglets se remplissent). */
    private static Map<Tab, List<Item>> contents;
    /** 1.6 : objets vanilla ranges dans nos onglets. */
    private static Set<Item> movedVanilla = Set.of();

    /** 1.6 : objets vanilla que nos onglets rangent (ils quittent les onglets vanilla : {@code core/vanilla/VanillaTabs}). */
    public static Set<Item> movedVanilla() {
        contents();
        return movedVanilla;
    }

    private static synchronized Map<Tab, List<Item>> contents() {
        if (contents == null) {
            List<CreativeSort.Machine> machines = new ArrayList<>();
            for (IndustryMachine m : IndustryMachine.values()) {
                machines.add(new CreativeSort.Machine(m.key, m.kind.name(), m.atelier.name(), m.module == null ? "" : m.module.key, m.multiId()));
            }
            List<CreativeSort.Plant> plants = new ArrayList<>();
            for (SpacePlant plant : SpacePlant.values()) {
                plants.add(new CreativeSort.Plant(plant.blockName, plant.produceName));
            }
            Layout layout = CreativeSort.layout(machines, plants);
            Map<String, Item> items = new LinkedHashMap<>();
            for (Item item : ForgeRegistries.ITEMS.getValues()) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                if (id != null && Pioneers.MODID.equals(id.getNamespace()) && item != Items.AIR) {
                    items.put(id.getPath(), item);
                }
            }
            Set<String> all = new HashSet<>(items.keySet());
            Map<Section, List<Map.Entry<String, Integer>>> bySection = new LinkedHashMap<>();
            items.forEach((id, item) -> {
                Placement p = layout.place(id, item instanceof BlockItem, all);
                bySection.computeIfAbsent(p.section(), s -> new ArrayList<>()).add(Map.entry(id, p.rank()));
            });
            //1.6 (unification vanilla) : objets vanilla ranges dans nos onglets (pommes, carottes... seaux), retires des onglets vanilla
            Set<Item> moved = new HashSet<>();
            layout.vanilla().forEach((id, p) -> {
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
                if (item != null && item != Items.AIR && p.section().tab != null) {
                    items.put(id, item);
                    moved.add(item);
                    bySection.computeIfAbsent(p.section(), s -> new ArrayList<>()).add(Map.entry(id, p.rank()));
                }
            });
            movedVanilla = moved;
            Map<Tab, List<Item>> out = new EnumMap<>(Tab.class);
            for (Section s : CreativeSort.LAYOUT) {
                if (s.tab != null && bySection.containsKey(s)) {
                    List<Item> list = out.computeIfAbsent(s.tab, t -> new ArrayList<>());
                    layout.sort(s, bySection.get(s)).forEach(id -> list.add(items.get(id)));
                }
            }
            contents = out;
        }
        return contents;
    }

    private static ItemStack icon(Tab tab) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(Pioneers.MODID, tab.icon));
        if (item == null || item == Items.AIR) {
            List<Item> list = contents().getOrDefault(tab, List.of());
            item = list.isEmpty() ? Items.BARRIER : list.get(0);
        }
        return new ItemStack(item);
    }

    // ------------------------------------------------------------------
    // Variantes a NBT, juste apres l'objet de base
    // ------------------------------------------------------------------

    /** Variantes d'un objet par id : une capsule pleine par gaz, la banque creative pleine de chaque donnee, la disquette d'exemples. */
    private static final Map<String, Function<Item, List<ItemStack>>> VARIANTS = Map.of(
          "gas_capsule", PiCreativeTabs::filledCapsules,
          "creative_data_bank", PiCreativeTabs::filledDataBanks,
          "data_disk", item -> List.of(DataDiskItem.examples(new ItemStack(item))));

    private static List<ItemStack> variants(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        Function<Item, List<ItemStack>> f = id == null ? null : VARIANTS.get(id.getPath());
        return f == null ? List.of() : f.apply(item);
    }

    private static List<ItemStack> filledCapsules(Item capsule) {
        List<ItemStack> out = new ArrayList<>();
        for (Gas gas : PiGasTypes.REGISTRY.get().getValues()) {
            if (!gas.isEmptyType() && !gas.isHidden()) {
                out.add(GasCapsuleItem.filled(capsule, gas));
            }
        }
        return out;
    }

    private static List<ItemStack> filledDataBanks(Item bank) {
        List<ItemStack> out = new ArrayList<>();
        for (DataType type : PiDataTypes.REGISTRY.get().getValues()) {
            if (!type.isEmptyType()) {
                ItemStack stack = new ItemStack(bank);
                CompoundTag mekData = new CompoundTag();
                mekData.put("dataTank", DataStack.of(type, CreativeDataTank.INFINITE).write(new CompoundTag()));
                stack.getOrCreateTag().put("mekData", mekData);
                out.add(stack);
            }
        }
        return out;
    }
}
