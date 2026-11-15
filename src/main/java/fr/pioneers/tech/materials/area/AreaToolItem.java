package fr.pioneers.tech.materials.area;

import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.tech.forge.smithing.HandForged;
import fr.pioneers.tech.materials.PiMaterials;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.4.1 : outils de zone des fiches materiau. Le marteau de minage (pioche) et l'excavateur (pelle) cassent en 3 x 3 dans le plan de la face
 * visee ; la faux recolte et replante les cultures mures, fauche l'herbe, taille le feuillage et laboure en carre (cote 3, 5 ou 7 selon le
 * tier). Accroupi : un seul bloc. Chaque bloc de la zone use l'outil d'un point ; la durabilite est un multiple de celle du materiau
 * ({@code forms.AREA_DURABILITY}). Logique de zone : {@link AreaTools}.
 */
public class AreaToolItem extends DiggerItem {

    /** Sortes d'outils de zone ({@code form} des fiches). */
    public enum Kind {
        MINING_HAMMER("mining_hammer", BlockTags.MINEABLE_WITH_PICKAXE, 6.0F, -3.3F, 0.45F),
        EXCAVATOR("excavator", BlockTags.MINEABLE_WITH_SHOVEL, 2.5F, -3.1F, 0.55F),
        SCYTHE("scythe", BlockTags.MINEABLE_WITH_HOE, 4.0F, -2.7F, 1.0F);

        public final String form;
        public final TagKey<Block> mineable;
        final float damage, speed, dig;

        Kind(String form, TagKey<Block> mineable, float damage, float speed, float dig) {
            this.form = form;
            this.mineable = mineable;
            this.damage = damage;
            this.speed = speed;
            this.dig = dig;
        }

        @Nullable
        public static Kind of(String form) {
            for (Kind kind : values()) {
                if (kind.form.equals(form)) {
                    return kind;
                }
            }
            return null;
        }
    }

    private final Kind kind;
    private final int durability, radius;

    /**
     * @param durability durabilite de base (avant la piece « forgee main ») ; {@code toolTier} 0..5 : rayon de la faux (1, 2 ou 3) ;
     *                   {@code swing} : bonus de vitesse d'attaque du materiau.
     */
    public AreaToolItem(Kind kind, Tier tier, int toolTier, int durability, float swing, Properties properties) {
        super(kind.damage, kind.speed + swing, tier, kind.mineable, properties);
        this.kind = kind;
        this.durability = durability;
        this.radius = kind == Kind.SCYTHE ? (toolTier <= 1 ? 1 : toolTier <= 3 ? 2 : 3) : 1;
    }

    public Kind kind() {
        return kind;
    }

    /** Demi-cote de la zone (1 : 3 x 3). */
    public int radius() {
        return radius;
    }

    /** Le bloc se mine-t-il correctement avec cet outil (genre et niveau) ? */
    public boolean correct(BlockState state) {
        return PiMaterials.correctFor(getTier(), state, kind.mineable);
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return HandForged.maxDamage(stack, durability);
    }

    @Override
    public boolean isCorrectToolForDrops(@NotNull ItemStack stack, @NotNull BlockState state) {
        return correct(state);
    }

    @Override
    public float getDestroySpeed(@NotNull ItemStack stack, @NotNull BlockState state) {
        //Une masse ou un godet lourds : plus lents qu'une pioche ou une pelle du meme materiau (la zone compense)
        return state.is(kind.mineable) ? getTier().getSpeed() * kind.dig : 1.0F;
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction action) {
        return switch (kind) {
            case MINING_HAMMER -> action == ToolActions.PICKAXE_DIG;
            case EXCAVATOR -> action == ToolActions.SHOVEL_DIG || action == ToolActions.SHOVEL_FLATTEN;
            case SCYTHE -> action == ToolActions.HOE_DIG || action == ToolActions.HOE_TILL || action == ToolActions.SWORD_SWEEP;
        };
    }

    @NotNull
    @Override
    public InteractionResult useOn(@NotNull UseOnContext context) {
        return switch (kind) {
            case EXCAVATOR -> AreaTools.reshape(context, ToolActions.SHOVEL_FLATTEN, 1);
            case SCYTHE -> AreaTools.reshape(context, ToolActions.HOE_TILL, radius);
            default -> InteractionResult.PASS;
        };
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        int side = radius * 2 + 1;
        tips.desc("tooltip.pioneers.area." + kind.form, side, side);
        tips.hint("tooltip.pioneers.area.sneak");
    }
}
