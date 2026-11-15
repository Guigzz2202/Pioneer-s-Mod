package fr.pioneers.space.farm;

import fr.pioneers.core.common.PiLang;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.farm.crop.Genes;
import fr.pioneers.farm.crop.GeneticSeedItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Graines spatiales : ne se plantent que dans une serre spatiale, sur l'astre d'origine de la plante.
 * <p>
 * 1.12.3 (lot F3, demande de Guillaume : « les graines spatiales suivent les caracteristiques des autres graines avec leurs stats ») : elles
 * portent les memes quatre genes que les graines de la Terre ({@link Genes}, NBT {@code Genes} ; sans NBT = genes de base), affiches pareil
 * (etoiles apres le nom, jauges dans l'infobulle). Dans la serre : Croissance = vitesse, Rendement = recolte, Fertilite = graines rendues et
 * mutations, Resistance = marge de temperature autour du climat de l'astre d'origine ({@link SpacePlant#climate}). Les graines filles
 * heritent et mutent comme a la Terre.
 */
public class ItemSpaceSeeds extends Item {

    private final SpacePlant plant;

    public ItemSpaceSeeds(SpacePlant plant) {
        super(new Properties());
        this.plant = plant;
    }

    public SpacePlant getPlant() {
        return plant;
    }

    /** Graine de cette plante portant ces genes (rien pour les genes de base : la pile s'empile avec les graines sans NBT). */
    public ItemStack stack(Genes genes, int count) {
        return genes.apply(new ItemStack(this, count));
    }

    @NotNull
    @Override
    public Component getName(@NotNull ItemStack stack) {
        MutableComponent name = Component.translatable(getDescriptionId(stack));
        if (Genes.has(stack)) {
            name.append(" ").append(GeneticSeedItem.stars(Genes.of(stack).stars()));
        }
        return name;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc(PiLang.DESCRIPTION_SPACE_SEEDS.translateColored(ChatFormatting.GRAY, Component.translatable(plant.planet.getTranslationKey())));
        Genes genes = Genes.of(stack);
        GeneticSeedItem.genesTooltip(genes, tooltip);
        tips.spec("farm.pioneers.space_seed.climate", Component.translatable(plant.planet.getTranslationKey()), plant.originTemperature());
        tips.spec("farm.pioneers.space_seed.tolerance", SpacePlant.toleranceCelsius(genes));
        tips.hint("farm.pioneers.space_seed.elsewhere");
    }
}
