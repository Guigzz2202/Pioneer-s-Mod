package fr.pioneers.tech.mechanics;

import com.mojang.serialization.Codec;
import fr.pioneers.tech.kinetics.KineticsConfig;
import java.util.regex.Pattern;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;

/**
 * Age du bronze : AUCUN minerai ne se genere plus sous terre, dans aucune dimension (Overworld, Nether, End, astres du mod, autres mods). Les
 * minerais viennent du tamisage puis des gisements virtuels (foreuses). Sont retirees toutes les poches « ore » / « scattered_ore » dont une
 * cible est un minerai (tag forge:ores, nom en « _ore », debris antiques). Les poches de terre, gravier, granite... restent.
 * Reglage {@code world.vanillaOres} (pioneers-mechanics.toml) : true = on garde tout.
 */
public final class RemoveOresModifier implements BiomeModifier {

    public static final RemoveOresModifier INSTANCE = new RemoveOresModifier();
    public static final Codec<RemoveOresModifier> CODEC = Codec.unit(INSTANCE);
    private static final Pattern ORE_NAME = Pattern.compile("(^|_)ores?($|_)");

    private RemoveOresModifier() {
    }

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.REMOVE || KineticsConfig.vanillaOres()) {
            return;
        }
        for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
            builder.getGenerationSettings().getFeatures(step).removeIf(RemoveOresModifier::isOre);
        }
    }

    static boolean isOre(Holder<PlacedFeature> placed) {
        ConfiguredFeature<?, ?> feature = placed.value().feature().value();
        if (!(feature.config() instanceof OreConfiguration ore)) {
            return false;
        }
        for (OreConfiguration.TargetBlockState target : ore.targetStates) {
            if (isOreBlock(target.state)) {
                return true;
            }
        }
        return false;
    }

    static boolean isOreBlock(BlockState state) {
        Block block = state.getBlock();
        if (state.is(Tags.Blocks.ORES) || block == Blocks.ANCIENT_DEBRIS) {
            return true;
        }
        return ORE_NAME.matcher(BuiltInRegistries.BLOCK.getKey(block).getPath()).find();
    }

    @Override
    public Codec<? extends BiomeModifier> codec() {
        return CODEC;
    }
}
