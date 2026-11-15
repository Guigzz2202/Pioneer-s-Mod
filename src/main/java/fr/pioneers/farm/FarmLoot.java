package fr.pioneers.farm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.pioneers.farm.crop.Climate;
import fr.pioneers.farm.crop.CropDef;
import fr.pioneers.farm.crop.Genes;
import fr.pioneers.farm.tree.TreeDef;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

/** Butins de l'Agriculture 2.0 : graines sauvages des herbes (selon le climat du biome) et pousses d'arbres fruitiers des feuilles vanilla. */
public final class FarmLoot {

    private FarmLoot() {
    }

    /** Biome de l'origine du butin : temperature et humidite, ou null hors du monde. */
    private static float[] climate(LootContext context) {
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        ServerLevel level = context.getLevel();
        if (origin == null) {
            return null;
        }
        BlockPos pos = BlockPos.containing(origin);
        return new float[]{Climate.temperature(level, pos), Climate.humidity(level, pos)};
    }

    /**
     * Graines sauvages : quand une herbe donne des graines de ble vanilla, elles sont remplacees par la graine d'une culture adaptee au biome
     * (climat ideal, irrigation comptee), aux genes sauvages (1 a 3). {@code "chance"} ajoute une graine meme sans graine vanilla.
     */
    public static class WildSeeds extends LootModifier {

        public static final Codec<WildSeeds> CODEC = RecordCodecBuilder.create(i -> codecStart(i).and(
              Codec.FLOAT.optionalFieldOf("chance", 0F).forGetter(m -> m.chance)).apply(i, WildSeeds::new));

        private final float chance;

        public WildSeeds(LootItemCondition[] conditions, float chance) {
            super(conditions);
            this.chance = chance;
        }

        @NotNull
        @Override
        protected ObjectArrayList<ItemStack> doApply(@NotNull ObjectArrayList<ItemStack> loot, @NotNull LootContext context) {
            float[] c = climate(context);
            if (c == null) {
                return loot;
            }
            RandomSource random = context.getRandom();
            int seeds = 0;
            for (int i = loot.size() - 1; i >= 0; i--) {
                if (loot.get(i).is(Items.WHEAT_SEEDS) || loot.get(i).getItem() == PiFarm.seedOf("wheat")) {
                    seeds += loot.remove(i).getCount();
                }
            }
            if (random.nextFloat() < chance) {
                seeds++;
            }
            if (seeds == 0) {
                return loot;
            }
            List<CropDef> fit = new ArrayList<>();
            for (CropDef def : GeneratedFarm.CROPS) {
                if ("mushroom".equals(def.arch())) {
                    continue;                                               //1.0.3 (lot 13) : pas de spores dans les herbes
                }
                if (c[0] >= def.tMin() && c[0] <= def.tMax() && Math.min(1F, c[1] + Climate.IRRIGATION) >= def.hMin() && c[1] <= def.hMax()) {
                    fit.add(def);
                }
            }
            if (fit.isEmpty()) {
                return loot;
            }
            for (int i = 0; i < seeds; i++) {
                CropDef def = fit.get(random.nextInt(fit.size()));
                loot.add(Genes.wild(random).apply(new ItemStack(PiFarm.seedOf(def.id()))));
            }
            return loot;
        }

        @Override
        public Codec<? extends IGlobalLootModifier> codec() {
            return CODEC;
        }
    }

    /**
     * 1.0.3 (lot 13) : spores sauvages : un champignon sauvage cueilli (petit ou geant, sans cisaille ni toucher de soie) donne parfois les
     * spores de sa culture ({@code "blocks"} : bloc -> culture), aux genes sauvages.
     */
    public static class WildSpores extends LootModifier {

        public static final Codec<WildSpores> CODEC = RecordCodecBuilder.create(i -> codecStart(i)
              .and(Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("blocks").forGetter(m -> m.blocks))
              .and(Codec.FLOAT.optionalFieldOf("chance", 0.5F).forGetter(m -> m.chance)).apply(i, WildSpores::new));

        private final java.util.Map<String, String> blocks;
        private final float chance;

        public WildSpores(LootItemCondition[] conditions, java.util.Map<String, String> blocks, float chance) {
            super(conditions);
            this.blocks = blocks;
            this.chance = chance;
        }

        @NotNull
        @Override
        protected ObjectArrayList<ItemStack> doApply(@NotNull ObjectArrayList<ItemStack> loot, @NotNull LootContext context) {
            BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
            if (state == null || context.getRandom().nextFloat() >= chance) {
                return loot;
            }
            String crop = blocks.get(String.valueOf(ForgeRegistries.BLOCKS.getKey(state.getBlock())));
            if (crop != null && PiFarm.SEEDS.containsKey(crop)) {
                loot.add(Genes.wild(context.getRandom()).apply(new ItemStack(PiFarm.SEEDS.get(crop).get())));
            }
            return loot;
        }

        @Override
        public Codec<? extends IGlobalLootModifier> codec() {
            return CODEC;
        }
    }

    /** Pousses d'arbres fruitiers : rares dans les feuilles vanilla (ids listes par l'arbre), si le climat du biome convient a l'arbre. */
    public static class FruitSaplings extends LootModifier {

        public static final Codec<FruitSaplings> CODEC = RecordCodecBuilder.create(i -> codecStart(i).and(
              Codec.FLOAT.optionalFieldOf("chance", 0.006F).forGetter(m -> m.chance)).apply(i, FruitSaplings::new));

        private final float chance;

        public FruitSaplings(LootItemCondition[] conditions, float chance) {
            super(conditions);
            this.chance = chance;
        }

        @NotNull
        @Override
        protected ObjectArrayList<ItemStack> doApply(@NotNull ObjectArrayList<ItemStack> loot, @NotNull LootContext context) {
            BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
            float[] c = climate(context);
            if (state == null || c == null || context.getRandom().nextFloat() >= chance) {
                return loot;
            }
            String leaves = ForgeRegistries.BLOCKS.getKey(state.getBlock()).getPath();
            if (!ForgeRegistries.BLOCKS.getKey(state.getBlock()).getNamespace().equals("minecraft")) {
                return loot;
            }
            List<TreeDef> fit = new ArrayList<>();
            for (TreeDef def : GeneratedFarm.TREES) {
                if (List.of(def.sources()).contains(leaves) && c[0] >= def.tMin() - 0.2F && c[0] <= def.tMax() + 0.2F) {
                    fit.add(def);
                }
            }
            if (!fit.isEmpty()) {
                TreeDef def = fit.get(context.getRandom().nextInt(fit.size()));
                loot.add(new ItemStack(PiFarm.SAPLINGS.get(def.id()).get()));
            }
            return loot;
        }

        @Override
        public Codec<? extends IGlobalLootModifier> codec() {
            return CODEC;
        }
    }
}
