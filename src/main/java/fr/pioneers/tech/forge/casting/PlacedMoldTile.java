package fr.pioneers.tech.forge.casting;

import fr.pioneers.tech.bronze.BronzeEvents;
import fr.pioneers.tech.bronze.foundry.CastingRecipe;
import fr.pioneers.tech.bronze.foundry.Metals;
import fr.pioneers.tech.bronze.foundry.MoldItem;
import fr.pioneers.tech.forge.ForgeMetals;
import fr.pioneers.tech.forge.ForgeTile;
import fr.pioneers.core.registries.PiBronze;
import fr.pioneers.core.registries.PiForge;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Moule pose : recoit une coulee ({@link #pour}), refroidit ({@link CastingRecipe#coolTicks()}, fumee), puis donne la piece au clic droit main
 * vide ; le moule s'use alors d'une coulee ({@link MoldItem#wear}, 12 coulees) et se brise a la derniere. Main vide sur un moule vide : on le
 * reprend. Un moule a moitie plein que personne ne complete fige au bout de {@link #PARTIAL_TICKS} en pepites (le reste est perdu).
 * <p>
 * NBT : {@code mold} (le moule, usure comprise), {@code metal}, {@code mb}, {@code cool} (ticks restants), {@code result} (piece figee),
 * {@code wait} (ticks a moitie plein). API de fondation (signature a garder) : {@link #pour}. Evenements : {@code mold_pour} (pince),
 * {@code mold_cast}, {@code mold_cast_<metal>}, {@code mold_take}, {@code mold_broken}.
 */
public class PlacedMoldTile extends ForgeTile {

    /** Moule a moitie plein : fige au bout de 30 s sans complement. */
    public static final int PARTIAL_TICKS = 600;

    protected ItemStack mold = ItemStack.EMPTY;
    protected String metal = "";
    protected int mb;
    protected int cool;
    protected ItemStack result = ItemStack.EMPTY;
    private int wait;
    /** Le moule a ete repris ou s'est brise : la casse du bloc ne lache rien. */
    private boolean gone;
    // Client : quantite de la recette et duree totale du refroidissement (rendu)
    private int amount;
    private int coolTotal;
    private boolean clientDone;

    public PlacedMoldTile(BlockPos pos, BlockState state) {
        this(PiForge.PLACED_MOLD_TILE.get(), pos, state);
    }

    /** 1.0.2-beta (lot 2) : moules poses d'un autre type (moule d'enclume, {@link AnvilMoldTile}). */
    protected PlacedMoldTile(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * 1.0.2-beta : vrai si le moule GARDE le metal d'une coulee partielle sans limite de temps (moule d'enclume : le creuset en argile le
     * remplit en plusieurs fois) ; faux : il fige en pepites au bout de {@link #PARTIAL_TICKS}.
     */
    protected boolean keepsPartial() {
        return false;
    }

    public ItemStack mold() {
        return mold;
    }

    public void setMold(ItemStack stack) {
        mold = stack.copyWithCount(1);
        changed();
        markClient();
    }

    public String metal() {
        return metal;
    }

    /** Metal verse (mB). */
    public int amount() {
        return mb;
    }

    /** Quantite que demande la recette en cours (client : synchronisee). */
    public int capacity() {
        return amount;
    }

    public boolean done() {
        return level != null && level.isClientSide ? clientDone : !result.isEmpty();
    }

    /** 0 juste versee, 1 figee. */
    public float solid(float partial) {
        if (done()) {
            return 1F;
        }
        if (cool <= 0 || coolTotal <= 0) {
            return mb > 0 && wait > 0 ? Math.min(1F, wait / (float) PARTIAL_TICKS) : 0F;
        }
        return 1F - Math.max(0F, cool - partial) / coolTotal;
    }

    /** Recette de coulee pour ce moule et ce metal ; null s'il n'y en a pas. */
    @Nullable
    public CastingRecipe recipeFor(String pouredMetal) {
        if (level == null || mold.isEmpty()) {
            return null;
        }
        for (CastingRecipe r : level.getRecipeManager().getAllRecipesFor(PiBronze.CASTING.get())) {
            if (r.mold() == mold.getItem() && r.metal().equals(pouredMetal)) {
                return r;
            }
        }
        return null;
    }

    /**
     * Verse du metal en fusion. Renvoie la quantite acceptee (mB, 0 si refuse : pas de recette pour ce moule et ce metal, moule deja plein ou
     * occupe par un autre metal, metal trop froid).
     */
    public int pour(String pouredMetal, int amount, float temperatureC) {
        if (!result.isEmpty() || cool > 0 || amount <= 0 || !metal.isEmpty() && !metal.equals(pouredMetal)) {
            return 0;
        }
        CastingRecipe recipe = recipeFor(pouredMetal);
        if (recipe == null || temperatureC < ForgeMetals.meltingC(pouredMetal)) {
            return 0;
        }
        int take = Math.min(amount, recipe.amount() - mb);
        if (take <= 0) {
            return 0;
        }
        metal = pouredMetal;
        mb += take;
        this.amount = recipe.amount();
        wait = 0;
        if (mb >= recipe.amount()) {
            cool = coolTotal = recipe.coolTicks();
        }
        changed();
        markClient();
        return take;
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    @Override
    public void serverTick(ServerLevel level) {
        if (cool > 0) {
            if (--cool == 0) {
                finish(level);
            } else if (cool % 20 == 0) {
                changed();
            }
        } else if (mb > 0 && result.isEmpty() && keepsPartial()) {
            //Moule d'enclume : le metal partiel fige dans le moule et attend la coulee suivante (rien ne se perd)
            if (wait < PARTIAL_TICKS && ++wait % 100 == 0) {
                markClient();
                changed();
            }
        } else if (mb > 0 && result.isEmpty()) {
            if (++wait >= PARTIAL_TICKS) {
                //Personne n'a complete : la coulee fige en pepites
                List<ItemStack> nuggets = ForgeMetals.asIngotsAndNuggets(metal, mb);
                result = nuggets.isEmpty() ? ItemStack.EMPTY : nuggets.get(0);
                //Le reste (pepites apres un lingot) tombe a cote : rien ne se perd
                for (int i = 1; i < nuggets.size(); i++) {
                    Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.4, worldPosition.getZ() + 0.5, nuggets.get(i));
                }
                if (result.isEmpty()) {
                    reset();
                }
                changed();
                markClient();
            } else if (wait % 100 == 0) {
                markClient();
            }
        }
        flushClient(level, 4);
    }

    private void finish(ServerLevel level) {
        CastingRecipe recipe = recipeFor(metal);
        if (recipe != null) {
            result = recipe.result().copy();
        } else {
            List<ItemStack> back = ForgeMetals.asIngotsAndNuggets(metal, mb);
            result = back.isEmpty() ? ItemStack.EMPTY : back.get(0);
        }
        level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.35F, 1.6F);
        level.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 0.25, worldPosition.getZ() + 0.5, 4, 0.2, 0.05,
              0.2, 0.01);
        BronzeEvents.award(level, worldPosition, "mold_cast");
        BronzeEvents.award(level, worldPosition, "mold_cast_" + metal);
        changed();
        markClient();
    }

    private void reset() {
        metal = "";
        mb = 0;
        cool = 0;
        coolTotal = 0;
        amount = 0;
        wait = 0;
        result = ItemStack.EMPTY;
    }

    @Override
    public void clientTick() {
        if (level == null || mb <= 0 || done()) {
            return;
        }
        if (cool > 1) {
            cool--;                                                  //Decompte local entre deux synchros (rendu fluide)
        }
        RandomSource r = level.random;
        float hot = 1F - solid(0);
        if (r.nextFloat() < 0.05F + 0.35F * hot) {
            level.addParticle(hot > 0.5F ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, worldPosition.getX() + 0.2 + r.nextDouble() * 0.6,
                  worldPosition.getY() + 0.2, worldPosition.getZ() + 0.2 + r.nextDouble() * 0.6, 0, 0.02 + 0.03 * hot, 0);
        }
        if (hot > 0.6F && r.nextFloat() < 0.02F) {
            level.playLocalSound(worldPosition.getX() + 0.5, worldPosition.getY() + 0.2, worldPosition.getZ() + 0.5, SoundEvents.LAVA_POP,
                  SoundSource.BLOCKS, 0.3F, 1.3F, false);
        }
    }

    // ------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------

    @Override
    public InteractionResult use(Player player, InteractionHand hand, BlockHitResult hit) {
        if (level == null) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        boolean client = level.isClientSide;
        if (held.getItem() instanceof CrucibleTongsItem) {
            if (level instanceof ServerLevel server) {
                CrucibleTongsItem.pour(server, player, hand, held, this);
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (!held.isEmpty() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            takeByHand(server, player);
        }
        return InteractionResult.sidedSuccess(client);
    }

    private void takeByHand(ServerLevel level, Player player) {
        if (!result.isEmpty()) {
            give(player, result.copy());
            ItemStack worn = MoldItem.wear(mold);
            BronzeEvents.award(level, worldPosition, "mold_take");
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                fr.pioneers.core.profile.PioneerApi.gain(sp, "smithing.cast"); //1.9 : coulee au moule
            }
            reset();
            if (worn.isEmpty()) {
                gone = true;
                level.playSound(null, worldPosition, SoundEvents.DECORATED_POT_SHATTER, SoundSource.BLOCKS, 0.8F, 1.1F);
                player.displayClientMessage(Component.translatable("casting.pioneers.mold.broken", mold.getMaxDamage()).withStyle(ChatFormatting.GRAY), true);
                BronzeEvents.award(level, worldPosition, "mold_broken");
                level.removeBlock(worldPosition, false);
                return;
            }
            mold = worn;
            level.playSound(null, worldPosition, SoundEvents.DECORATED_POT_HIT, SoundSource.BLOCKS, 0.6F, 1.3F);
            changed();
            markClient();
        } else if (cool > 0) {
            player.displayClientMessage(Component.translatable("casting.pioneers.mold.cooling", (cool + 19) / 20).withStyle(ChatFormatting.GOLD), true);
        } else if (mb > 0) {
            player.displayClientMessage(Component.translatable("casting.pioneers.mold.waiting", amount - mb, Metals.name(metal))
                  .withStyle(ChatFormatting.YELLOW), true);
        } else {
            give(player, mold.copy());
            gone = true;
            level.playSound(null, worldPosition, SoundEvents.DECORATED_POT_HIT, SoundSource.BLOCKS, 0.6F, 1F);
            level.removeBlock(worldPosition, false);
        }
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** Casse : le moule (use), la piece si elle est figee, sinon le metal en pepites. */
    @Override
    public void dropContents(Consumer<ItemStack> drop) {
        if (gone) {
            return;
        }
        if (!result.isEmpty()) {
            drop.accept(result.copy());
        } else if (mb > 0 && !metal.isEmpty()) {
            for (ItemStack s : ForgeMetals.asIngotsAndNuggets(metal, mb)) {
                drop.accept(s);
            }
        }
        if (!mold.isEmpty()) {
            drop.accept(mold.copy());
        }
        reset();
        mold = ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------
    // Sauvegarde et synchro
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("mold", mold.save(new CompoundTag()));
        tag.putString("metal", metal);
        tag.putInt("mb", mb);
        tag.putInt("cool", cool);
        tag.put("result", result.save(new CompoundTag()));
        tag.putInt("wait", wait);
        tag.putInt("amount", amount);
        tag.putInt("coolTotal", coolTotal);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        mold = ItemStack.of(tag.getCompound("mold"));
        metal = tag.getString("metal");
        mb = tag.getInt("mb");
        cool = tag.getInt("cool");
        result = ItemStack.of(tag.getCompound("result"));
        wait = tag.getInt("wait");
        amount = tag.getInt("amount");
        coolTotal = tag.getInt("coolTotal");
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        tag.putString("metal", metal);
        tag.putInt("mb", mb);
        tag.putInt("cool", cool);
        tag.putInt("coolTotal", coolTotal);
        tag.putInt("amount", amount);
        tag.putInt("wait", wait);
        tag.putBoolean("done", !result.isEmpty());
    }

    @Override
    protected void readClient(CompoundTag tag) {
        metal = tag.getString("metal");
        mb = tag.getInt("mb");
        cool = tag.getInt("cool");
        coolTotal = tag.getInt("coolTotal");
        amount = tag.getInt("amount");
        wait = tag.getInt("wait");
        clientDone = tag.getBoolean("done");
    }
}
