package fr.pioneers.tech.forge.casting;

import fr.pioneers.tech.bronze.BronzeEvents;
import fr.pioneers.tech.bronze.foundry.CastingRecipe;
import fr.pioneers.tech.bronze.foundry.Metals;
import fr.pioneers.core.Texts;
import fr.pioneers.core.tooltip.GearTips;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.tech.forge.ForgeMetals;
import fr.pioneers.tech.forge.MoltenLoad;
import fr.pioneers.tech.forge.PiForge;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Pince a creuset (64 versements) : prend le creuset d'argile sur le feu, verse dans un moule pose, le repose sur le feu. Tenue en main
 * secondaire, elle protege aussi des brulures du metal chauffe ({@code HeatedMetalItem}).
 * <p>
 * Gestes : clic droit sur un creuset pose = le prendre ({@link ClayCrucibleTile#use}) ; sur un moule pose = verser ({@link #pour}, appele par
 * {@link PlacedMoldTile#use}) ; sur le dessus d'un bloc (feu de camp...) = reposer le creuset ({@link #useOn}). Le metal refroidit dans la
 * pince ({@link MoltenLoad#TONGS_TAU_TICKS} : fige en deux minutes environ).
 * <p>
 * NBT : {@code crucible} (Compound) = {@code item} (le creuset d'argile, {@link ItemStack#save}, son usure comprise, et ses solides en
 * attente sous {@code solids}) + {@code load} ({@link MoltenLoad#save()}). Les aides NBT ci-dessous sont la fondation : ne pas changer les cles.
 */
public class CrucibleTongsItem extends Item {

    public static final String CRUCIBLE = "crucible", ITEM = "item", LOAD = "load";

    public CrucibleTongsItem(Properties properties) {
        super(properties);
    }

    public static boolean hasCrucible(ItemStack tongs) {
        CompoundTag tag = tongs.getTag();
        return tag != null && tag.contains(CRUCIBLE, Tag.TAG_COMPOUND);
    }

    /** Le creuset tenu (objet, usure comprise) ; vide s'il n'y en a pas. */
    public static ItemStack crucible(ItemStack tongs) {
        return hasCrucible(tongs) ? ItemStack.of(tongs.getTag().getCompound(CRUCIBLE).getCompound(ITEM)) : ItemStack.EMPTY;
    }

    /** Contenu du creuset tenu (copie : le reecrire par {@link #setCrucible}) ; null s'il n'y a pas de creuset. */
    @Nullable
    public static MoltenLoad load(ItemStack tongs) {
        return hasCrucible(tongs) ? MoltenLoad.load(tongs.getTag().getCompound(CRUCIBLE).getCompound(LOAD)) : null;
    }

    public static void setCrucible(ItemStack tongs, ItemStack crucible, MoltenLoad load) {
        CompoundTag c = new CompoundTag();
        c.put(ITEM, crucible.save(new CompoundTag()));
        c.put(LOAD, load.save());
        tongs.getOrCreateTag().put(CRUCIBLE, c);
    }

    public static void clearCrucible(ItemStack tongs) {
        if (tongs.getTag() != null) {
            tongs.getTag().remove(CRUCIBLE);
        }
    }

    /** 0 = vide, 0,5 = creuset froid ou fige, 1 = metal en fusion (predicat de modele {@code pioneers:crucible}). */
    public static float visual(ItemStack tongs, @Nullable Level level) {
        MoltenLoad load = load(tongs);
        if (load == null) {
            return 0F;
        }
        return level != null && load.isMolten(level.getGameTime(), MoltenLoad.TONGS_TAU_TICKS) ? 1F : 0.5F;
    }

    // ------------------------------------------------------------------
    // Reposer le creuset
    // ------------------------------------------------------------------

    @NotNull
    @Override
    public InteractionResult useOn(@NotNull UseOnContext context) {
        ItemStack tongs = context.getItemInHand();
        Player player = context.getPlayer();
        if (!hasCrucible(tongs) || context.getClickedFace() != Direction.UP) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos().above();
        if (!level.getBlockState(pos).canBeReplaced()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            BlockState below = level.getBlockState(context.getClickedPos());
            BlockState state = PiForge.CLAY_CRUCIBLE.getBlock().defaultBlockState()
                  .setValue(ClayCrucibleBlock.FACING, context.getHorizontalDirection().getOpposite())
                  .setValue(ClayCrucibleBlock.STAND, ClayCrucibleBlock.standsOn(below));
            if (!server.setBlock(pos, state, 3)) {
                return InteractionResult.FAIL;
            }
            if (server.getBlockEntity(pos) instanceof ClayCrucibleTile tile) {
                MoltenLoad load = load(tongs);
                tile.setCrucible(crucible(tongs), load == null ? new MoltenLoad() : load);
            }
            clearCrucible(tongs);
            server.playSound(null, pos, SoundEvents.DECORATED_POT_PLACE, SoundSource.BLOCKS, 0.8F, 0.9F);
            if (player != null && !player.getAbilities().instabuild) {
                player.getCooldowns().addCooldown(this, 4);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ------------------------------------------------------------------
    // Verser
    // ------------------------------------------------------------------

    /** Verse le creuset tenu dans un moule pose (cote serveur). */
    public static void pour(ServerLevel level, Player player, InteractionHand hand, ItemStack tongs, PlacedMoldTile mold) {
        MoltenLoad load = load(tongs);
        if (load == null) {
            player.displayClientMessage(Component.translatable("casting.pioneers.tongs.empty").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (load.isEmpty()) {
            player.displayClientMessage(Component.translatable("casting.pioneers.tongs.nothing").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        long now = level.getGameTime();
        float t = load.temperatureAt(now, MoltenLoad.TONGS_TAU_TICKS);
        //1.0.3-beta (lot 7b) : regle commune (Metals.plan) : la premiere part coulable que ce moule prend (le bain entier s'il est un metal ou
        //un alliage, sinon la plus grande part d'alliage, puis le surplus) ; un moule deja entame ne prend que son metal
        Metals.Plan plan = load.plan(m -> mold.recipeFor(m) == null || mold.amount() > 0 && !mold.metal().equals(m) ? -1 : 0);
        String metal = plan.pours() ? plan.metal() : plan.metal().isEmpty() ? load.pourMetal() : plan.metal();
        //Meme seuil que le moule (PlacedMoldTile.pour : fusion du metal coule), sinon la pince versait et le moule refusait sans le dire
        int need = Math.max(load.pourC(), ForgeMetals.meltingC(metal));
        if (t < need) {
            player.displayClientMessage(Component.translatable("casting.pioneers.tongs.cold", Texts.grouped(Math.round(t)),
                  Texts.grouped(need)).withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        CastingRecipe recipe = plan.pours() ? mold.recipeFor(metal) : null;
        if (recipe == null) {
            //1.0.2-beta (lot 5) : le moule d'enclume ne prend que du fer (plus d'enclume en bronze) : on le dit clairement
            player.displayClientMessage((mold instanceof AnvilMoldTile ? Component.translatable("casting.pioneers.anvil_mold.iron_only", Metals.name(metal))
                  : Component.translatable("casting.pioneers.tongs.no_recipe", Metals.name(metal))).withStyle(ChatFormatting.RED), true);
            return;
        }
        int took = mold.pour(metal, Math.min(plan.mb(), recipe.amount() - mold.amount()), t);
        if (took <= 0) {
            player.displayClientMessage(Component.translatable("casting.pioneers.tongs.busy").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        load.pour(metal, took);
        BlockPos pos = mold.getBlockPos();
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 0.7F, 1.1F);
        level.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5, 3, 0.2, 0.05, 0.2, 0);
        level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 6, 0.25, 0.05, 0.25, 0.01);
        BronzeEvents.award(level, pos, "mold_pour");
        BronzeEvents.award(level, pos, "pour_" + metal);
        ItemStack crucible = crucible(tongs);
        if (load.isEmpty() && ClayCrucibleTile.worn(crucible)) {
            clearCrucible(tongs);
            level.playSound(null, player.blockPosition(), SoundEvents.DECORATED_POT_SHATTER, SoundSource.PLAYERS, 0.9F, 1F);
            player.displayClientMessage(Component.translatable("casting.pioneers.tongs.cracked", crucible.getMaxDamage())
                  .withStyle(ChatFormatting.GRAY), true);
        } else {
            setCrucible(tongs, crucible, load);
        }
        if (!player.getAbilities().instabuild) {
            //Usure d'abord (Solidite peut l'eviter) ; le creuset ne tombe que si la pince a vraiment casse
            ItemStack fallen = hasCrucible(tongs) ? crucible(tongs) : ItemStack.EMPTY;
            tongs.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            if (tongs.isEmpty() && !fallen.isEmpty()) {
                //La pince casse : le creuset tombe (son metal est perdu)
                CompoundTag tag = fallen.getTag();
                if (tag != null) {
                    tag.remove("solids");
                }
                player.drop(fallen, false);
            }
        }
    }

    /**
     * Temperature a laquelle le contenu se verse dans un moule : la coulee du melange, et la fusion du metal obtenu (seuil du moule) ;
     * 1.0.3-beta (lot 7b) : le metal qui coulerait d'abord ({@link MoltenLoad#pourMetal}).
     */
    public static int pourThreshold(MoltenLoad load) {
        return Math.max(load.pourC(), ForgeMetals.meltingC(load.pourMetal()));
    }

    // ------------------------------------------------------------------
    // Infobulle
    // ------------------------------------------------------------------

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        MoltenLoad load = load(stack);
        //1.0.4-beta (lot 28) : format commun des equipements : tier (age mecanique : premiere coulee) ; durabilite par l'infobulle commune
        GearTips.rank(tips, GearTips.MECHANICAL);
        if (load == null) {
            tips.desc("item.pioneers.crucible_tongs.desc");
            return;
        }
        if (load.isEmpty()) {
            tips.state("tooltip.pioneers.crucible_tongs.empty");
        } else {
            float t = level == null ? load.storedTemperature() : load.temperatureAt(level.getGameTime(), MoltenLoad.TONGS_TAU_TICKS);
            boolean molten = t >= pourThreshold(load);
            tips.state("tooltip.pioneers.crucible_tongs.holding", load.total(), Metals.name(load.pourMetal()));
            if (!load.metal().equals(load.pourMetal())) {
                //1.0.3-beta (lot 7b) : bain qui n'est pas exactement un alliage : ce qui coulera et ce qui restera
                tips.custom(Metals.planLine(load.plan(m -> 0)).copy().withStyle(ChatFormatting.GRAY));
            }
            int glow = ForgeMetals.glow(t);
            tips.custom(Component.translatable("tooltip.pioneers.crucible_tongs.temp", Texts.grouped(Math.round(t)),
                  Component.translatable(molten ? "tooltip.pioneers.crucible_tongs.molten" : "tooltip.pioneers.crucible_tongs.solid"))
                  .withStyle(s -> glow != 0 ? s.withColor(glow & 0xFFFFFF) : s.withColor(ChatFormatting.GRAY)));
        }
        ItemStack crucible = crucible(stack);
        tips.spec("tooltip.pioneers.crucible_tongs.uses", crucible.getMaxDamage() - crucible.getDamageValue(),
              crucible.getMaxDamage());
    }
}
