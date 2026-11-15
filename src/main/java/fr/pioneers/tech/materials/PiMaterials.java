package fr.pioneers.tech.materials;

import fr.pioneers.core.item.WrenchItem;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.tech.forge.smithing.HandForged;
import fr.pioneers.world.geology.ProspectorHammerItem;
import fr.pioneers.tech.materials.area.AreaToolItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Objets, blocs et outils d'atelier des fiches materiau ({@link GeneratedMaterials}, ecrit par {@code tools/gc/materials/gen.py}). Les formes
 * fournies par Minecraft ou deja enregistrees par le mod ne sont pas dans la liste : le generateur ne leur ajoute que des tags et des recettes.
 */
public final class PiMaterials {

    private PiMaterials() {
    }

    private static final Map<String, RegistryObject<? extends Item>> ITEMS = new LinkedHashMap<>();
    private static final Map<String, RegistryObject<? extends Block>> BLOCKS = new LinkedHashMap<>();

    static {
        PiTiers.init();
        for (GeneratedMaterials.Entry entry : GeneratedMaterials.ENTRIES) {
            switch (entry.kind()) {
                case ITEM -> ITEMS.put(entry.id(), PiCoreRegistries.ITEMS.register(entry.id(), () -> new MaterialItem(entry, new Item.Properties())));
                case TOOL -> ITEMS.put(entry.id(), PiCoreRegistries.ITEMS.register(entry.id(),
                      () -> new WorkshopToolItem(entry, new Item.Properties().durability(entry.durability()))));
                case GEAR -> ITEMS.put(entry.id(), PiCoreRegistries.ITEMS.register(entry.id(), () -> gear(entry)));
                case WRENCH -> ITEMS.put(entry.id(), PiCoreRegistries.ITEMS.register(entry.id(),
                      () -> new WrenchItem(new Item.Properties().stacksTo(1), WrenchItem.PIPES)));
                case BLOCK -> {
                    var block = PiCoreRegistries.plainBlock(entry.id(), () -> new Block(BlockBehaviour.Properties.of()
                          .mapColor(entry.form().startsWith("raw") ? MapColor.RAW_IRON : MapColor.METAL).strength(entry.form().startsWith("raw") ? 5F : 4F, 6F)
                          .sound(entry.form().startsWith("raw") ? SoundType.STONE : SoundType.METAL).requiresCorrectToolForDrops()));
                    BLOCKS.put(entry.id(), block.block());
                    ITEMS.put(entry.id(), block.item());
                }
            }
        }
    }

    /**
     * 1.1.3 : outil du mod correct pour un bloc = bloc de son genre (mineable) et niveau suffisant, compare aux tags vanilla et Forge comme
     * les tiers d'avant le classement de Forge. Le classement (silex entre bois et pierre) rendait la pioche en silex inutile sur la roche.
     */
    public static boolean correctFor(Tier tier, BlockState state, TagKey<Block> mineable) {
        if (!state.is(mineable)) {
            return false;
        }
        int level = tier.getLevel();
        if (state.is(Tags.Blocks.NEEDS_NETHERITE_TOOL)) {
            return level >= 4;
        }
        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) {
            return level >= 3;
        }
        if (state.is(BlockTags.NEEDS_IRON_TOOL)) {
            return level >= 2;
        }
        return !state.is(BlockTags.NEEDS_STONE_TOOL) || level >= 1;
    }

    /** Outil de minage ou de combat (pioche, hache, pelle, houe, epee, marteau de prospecteur, outils de zone) au niveau de son materiau. */
    private static Item gear(GeneratedMaterials.Entry entry) {
        Tier tier = PiTiers.tier(entry.material());
        GeneratedMaterials.TierDef def = PiTiers.def(entry.material());
        //Densite du materiau : un metal leger rend le geste plus vif, un metal lourd le ralentit
        float swing = def == null ? 0F : def.swing();
        Item.Properties props = new Item.Properties();
        //1.4.1 : outils de zone (marteau de minage, excavateur, faux)
        AreaToolItem.Kind area = AreaToolItem.Kind.of(entry.form());
        if (area != null) {
            return new AreaToolItem(area, tier, def == null ? 0 : def.tier(), entry.durability(), swing, props);
        }
        return switch (entry.form()) {
            //Forge primaire : un outil monte avec une piece « forgee main » a +10 % de durabilite (HandForged, NBT de la pile)
            case "pickaxe" -> new PickaxeItem(tier, 1, -2.8F + swing, props) {
                @Override
                public int getMaxDamage(ItemStack stack) {
                    return HandForged.maxDamage(stack, super.getMaxDamage(stack));
                }

                @Override
                public boolean isCorrectToolForDrops(@NotNull ItemStack stack, @NotNull BlockState state) {
                    return correctFor(tier, state, BlockTags.MINEABLE_WITH_PICKAXE);
                }
            };
            case "axe" -> new AxeItem(tier, 6.0F, -3.1F + swing, props) {
                @Override
                public int getMaxDamage(ItemStack stack) {
                    return HandForged.maxDamage(stack, super.getMaxDamage(stack));
                }

                @Override
                public boolean isCorrectToolForDrops(@NotNull ItemStack stack, @NotNull BlockState state) {
                    return correctFor(tier, state, BlockTags.MINEABLE_WITH_AXE);
                }
            };
            case "shovel" -> new ShovelItem(tier, 1.5F, -3.0F + swing, props) {
                @Override
                public int getMaxDamage(ItemStack stack) {
                    return HandForged.maxDamage(stack, super.getMaxDamage(stack));
                }

                @Override
                public boolean isCorrectToolForDrops(@NotNull ItemStack stack, @NotNull BlockState state) {
                    return correctFor(tier, state, BlockTags.MINEABLE_WITH_SHOVEL);
                }
            };
            case "hoe" -> new HoeItem(tier, -(int) tier.getAttackDamageBonus(), -2.0F + swing, props) {
                @Override
                public int getMaxDamage(ItemStack stack) {
                    return HandForged.maxDamage(stack, super.getMaxDamage(stack));
                }

                @Override
                public boolean isCorrectToolForDrops(@NotNull ItemStack stack, @NotNull BlockState state) {
                    return correctFor(tier, state, BlockTags.MINEABLE_WITH_HOE);
                }
            };
            case "sword" -> new SwordItem(tier, 3, -2.4F + swing, props) {
                @Override
                public int getMaxDamage(ItemStack stack) {
                    return HandForged.maxDamage(stack, super.getMaxDamage(stack));
                }
            };
            default -> new ProspectorHammerItem(tier, PiTiers.prospectorRange(entry.material()), props) {
                @Override
                public int getMaxDamage(ItemStack stack) {
                    return HandForged.maxDamage(stack, super.getMaxDamage(stack));
                }
            };
        };
    }

    /** Force le chargement de la classe (enregistrement) avant l'inscription des registres. */
    public static void init() {
    }

    @Nullable
    public static Item item(String id) {
        RegistryObject<? extends Item> item = ITEMS.get(id);
        return item == null ? null : item.get();
    }

    /** Objets de l'age du bronze ({@code bronze} vrai : alliages, plomb, zinc, tetes et outils) ou des ages precedents. */
    public static List<RegistryObject<? extends Item>> items(boolean bronze) {
        List<RegistryObject<? extends Item>> out = new ArrayList<>();
        for (GeneratedMaterials.Entry entry : GeneratedMaterials.ENTRIES) {
            boolean age2 = Set.of("lead", "zinc", "bronze", "brass", "solder", "steel").contains(entry.material())
                           || entry.form().startsWith("head_") || entry.kind() == GeneratedMaterials.Kind.GEAR;
            RegistryObject<? extends Item> item = ITEMS.get(entry.id());
            if (item != null && age2 == bronze) {
                out.add(item);
            }
        }
        return out;
    }

    /** Composants et matieres (lingots, pieces, poudres, tetes d'outils, blocs) de tous les ages livres : onglet Core. */
    public static List<RegistryObject<? extends Item>> components() {
        List<RegistryObject<? extends Item>> out = new ArrayList<>();
        for (GeneratedMaterials.Entry entry : GeneratedMaterials.ENTRIES) {
            RegistryObject<? extends Item> item = ITEMS.get(entry.id());
            if (item != null && entry.kind() != GeneratedMaterials.Kind.TOOL && entry.kind() != GeneratedMaterials.Kind.GEAR
                && entry.kind() != GeneratedMaterials.Kind.WRENCH) {
                out.add(item);
            }
        }
        return out;
    }

    /** Outils (outils d'atelier, pioches, haches..., marteaux de prospecteur) : onglet Equipement. */
    public static List<RegistryObject<? extends Item>> tools() {
        List<RegistryObject<? extends Item>> out = new ArrayList<>();
        for (GeneratedMaterials.Kind kind : new GeneratedMaterials.Kind[]{GeneratedMaterials.Kind.TOOL, GeneratedMaterials.Kind.GEAR,
              GeneratedMaterials.Kind.WRENCH}) {
            for (GeneratedMaterials.Entry entry : GeneratedMaterials.ENTRIES) {
                RegistryObject<? extends Item> item = ITEMS.get(entry.id());
                if (item != null && entry.kind() == kind) {
                    out.add(item);
                }
            }
        }
        return out;
    }

    public static Map<String, RegistryObject<? extends Item>> items() {
        return ITEMS;
    }
}
