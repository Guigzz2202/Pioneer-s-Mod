package fr.pioneers.farm.fish;

import fr.pioneers.Pioneers;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.farm.PiFishing;
import fr.pioneers.farm.crustacean.Crustacean;
import fr.pioneers.farm.crustacean.CrustaceanDef;
import fr.pioneers.farm.crustacean.GeneratedCrustaceans;
import fr.pioneers.farm.crustacean.PiCrustaceans;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * 1.6 (Guillaume : « faire clic droit avec le poisson / crustace en main le pose dans le monde ») : un poisson cru (du mod, ou vanilla qui a
 * une espece : morue, saumon, poisson-globe, poisson tropical) ou la chair crue d'un crustace redevient l'animal VIVANT de son espece, a sa
 * taille si l'objet la porte (NBT {@code Size}, cm). Clic droit en visant l'eau : il y est relache ; Maj + clic droit sur un bloc : il est
 * pose dessus (un poisson frétille et s'asphyxie hors de l'eau comme ceux de vanilla ; un crustace aquatique suffoque, un crabe de rivage
 * vit). L'objet est consomme (pas en creatif). Ce qui ne change pas : clic droit hors de l'eau = manger ; clic droit sur un poste, un
 * piege, une assiette... = leur usage (Maj + clic droit ne pose jamais sur un bloc a contenu) ; les plats cuisines se posent toujours en
 * plat ({@code FarmFoodItem}). Les animaux relaches ne disparaissent pas au loin.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class LiveRelease {

    private LiveRelease() {
    }

    private static Map<Item, Integer> vanillaFish;
    private static Map<Item, Integer> crustaceans;

    /** Espece de poisson de cet objet cru (poisson du mod ou vanilla), -1 sinon. */
    public static int fishSpecies(ItemStack stack) {
        if (stack.getItem() instanceof FishItem fish) {
            return fish.species();
        }
        if (vanillaFish == null) {
            Map<Item, Integer> map = new HashMap<>();
            FishDef[] all = GeneratedFish.SPECIES;
            for (int i = 0; i < all.length; i++) {
                if (all[i].item().startsWith("minecraft:")) {
                    map.putIfAbsent(BuiltInRegistries.ITEM.get(ResourceLocation.parse(all[i].item())), i);
                }
            }
            vanillaFish = map;
        }
        return vanillaFish.getOrDefault(stack.getItem(), -1);
    }

    /** Espece de crustace dont c'est la chair crue, -1 sinon. */
    public static int crustaceanSpecies(ItemStack stack) {
        if (crustaceans == null) {
            Map<Item, Integer> map = new HashMap<>();
            CrustaceanDef[] all = GeneratedCrustaceans.SPECIES;
            for (int i = 0; i < all.length; i++) {
                var raw = PiCrustaceans.RAW.get(all[i].id());
                if (raw != null) {
                    map.put(raw.get(), i);
                }
            }
            crustaceans = map;
        }
        return crustaceans.getOrDefault(stack.getItem(), -1);
    }

    public static boolean releasable(ItemStack stack) {
        return !stack.isEmpty() && (fishSpecies(stack) >= 0 || crustaceanSpecies(stack) >= 0);
    }

    // ------------------------------------------------------------------ gestes

    /** Clic droit en visant l'eau (a portee de main) : l'animal y est relache. */
    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (!releasable(stack)) {
            return;
        }
        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockPos water = aimedWater(level, player);
        if (water == null) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (!level.isClientSide) {
            release(level, player, stack, new Vec3(water.getX() + 0.5, water.getY() + 0.25, water.getZ() + 0.5), true);
        }
    }

    /** Maj + clic droit sur un bloc (sans contenu) : l'animal est pose dessus. */
    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        Player player = event.getEntity();
        if (!player.isShiftKeyDown() || !releasable(stack)) {
            return;
        }
        //1.9.2 (retours de Guillaume : tout ingredient se pose) : sur le DESSUS d'un bloc, le poisson cru se pose a l'etal
        //(fr.pioneers.farm.food.PlacedFoodEvents) ; relacher vivant : vers l'eau, ou Maj + clic droit sur un flanc
        if (event.getFace() == net.minecraft.core.Direction.UP && fr.pioneers.farm.food.PlacedFoodBlock.placeable(stack)) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (level.getBlockEntity(pos) != null) {
            return;
        }
        BlockHitResult hit = event.getHitVec();
        BlockPos at = level.getBlockState(pos).canBeReplaced() ? pos : pos.relative(hit.getDirection());
        boolean water = level.getFluidState(at).is(FluidTags.WATER);
        if (!water && !level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (!level.isClientSide) {
            Vec3 spot = water ? new Vec3(at.getX() + 0.5, at.getY() + 0.25, at.getZ() + 0.5)
                              : new Vec3(Mth.clamp(hit.getLocation().x, at.getX() + 0.2, at.getX() + 0.8), at.getY(),
                                    Mth.clamp(hit.getLocation().z, at.getZ() + 0.2, at.getZ() + 0.8));
            release(level, player, stack, spot, water);
        }
    }

    /** Eau visee a portee de main, avant tout bloc solide (null sinon). */
    @Nullable
    private static BlockPos aimedWater(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1F).scale(player.getBlockReach()));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos p = hit.getBlockPos();
        return level.getFluidState(p).is(FluidTags.WATER) ? p : null;
    }

    /** Fait naitre l'animal vivant a cet endroit et consomme l'objet. */
    private static void release(Level level, Player player, ItemStack stack, Vec3 at, boolean water) {
        Mob mob = create(level, stack);
        if (mob == null) {
            return;
        }
        mob.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360F, 0);
        mob.yHeadRot = mob.getYRot();
        mob.yBodyRot = mob.getYRot();
        if (stack.hasCustomHoverName()) {
            mob.setCustomName(stack.getHoverName());
        }
        level.addFreshEntity(mob);
        if (water) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.BUCKET_EMPTY_FISH, SoundSource.NEUTRAL, 0.8F, 1.1F);
            ((ServerLevel) level).sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.6, at.z, 10, 0.25, 0.05, 0.25, 0.1);
        } else {
            level.playSound(null, at.x, at.y, at.z, mob instanceof PiFish ? SoundEvents.COD_FLOP : SoundEvents.TURTLE_SHAMBLE_BABY, SoundSource.NEUTRAL, 0.8F,
                  1.0F);
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.displayClientMessage(Component.translatable(water ? "farm.pioneers.fish.released" : "farm.pioneers.fish.set_down",
              mob.getName()).withStyle(ChatFormatting.AQUA), true);
    }

    /**
     * Echelle du poisson vivant d'un poisson cru (0,8 a 1,2) : d'apres sa taille (NBT {@code Size}, cm, entre les tailles mini et maxi de
     * l'espece), sinon au hasard. 1.0.4-beta (lot 35) : aussi pour un poisson mis a la main dans un aquarium.
     */
    public static float fishSize(ItemStack stack, int species, net.minecraft.util.RandomSource random) {
        FishDef d = GeneratedFish.SPECIES[species];
        float size = 0.8F + random.nextFloat() * 0.4F;
        if (stack.hasTag() && stack.getTag().contains("Size") && d.cmMax() > d.cmMin()) {
            size = 0.8F + 0.4F * Mth.clamp((stack.getTag().getInt("Size") - d.cmMin()) / (float) (d.cmMax() - d.cmMin()), 0, 1);
        }
        return size;
    }

    /** L'animal de l'objet : poisson (taille de l'objet, sinon au hasard) ou crustace (taille au hasard) ; ne disparait pas au loin. */
    @Nullable
    private static Mob create(Level level, ItemStack stack) {
        int fish = fishSpecies(stack);
        if (fish >= 0) {
            return liveFish(level, fish, fishSize(stack, fish, level.random));
        }
        int crab = crustaceanSpecies(stack);
        if (crab < 0) {
            return null;
        }
        CrustaceanDef d = GeneratedCrustaceans.SPECIES[crab];
        Crustacean c = (d.aquatic() ? PiCrustaceans.AQUATIC : PiCrustaceans.SHORE).get().create(level);
        if (c == null) {
            return null;
        }
        c.set(crab, 0.8F + level.random.nextFloat() * 0.4F);
        c.setPersistenceRequired();
        return c;
    }

    /**
     * Poisson vivant d'une espece (relache, aquarium casse) ; 1.0.5-beta (lot 25) : une espece de peche seulement (requin gris) redevient la
     * VRAIE bete du monde ({@code pioneers:gray_reef_shark}, lot 9), qui ne disparait pas au loin ; les autres : l'entite de l'espece.
     */
    @Nullable
    public static Mob liveFish(Level level, int species, float size) {
        if (FishSpawns.catchOnly(GeneratedFish.SPECIES[species])) {
            Mob shark = fr.pioneers.world.wild.PiWild9.GRAY_REEF_SHARK.get().create(level);
            if (shark instanceof fr.pioneers.world.wild.WildAnimal w) {
                w.setSex(level.random.nextBoolean() ? fr.pioneers.world.wild.WildRules.MALE : fr.pioneers.world.wild.WildRules.FEMALE);
            }
            if (shark != null) {
                shark.setPersistenceRequired();
            }
            return shark;
        }
        PiFish f = PiFishing.speciesType(species).create(level);
        if (f == null) {
            return null;
        }
        f.set(species, size);
        f.setFromBucket(true);
        return f;
    }

    /** Infobulle des poissons vanilla et des chairs crues de crustace (les poissons du mod l'ont dans {@link FishItem}). */
    @Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
    public static final class ClientEvents {

        private ClientEvents() {
        }

        @SubscribeEvent
        public static void tooltip(ItemTooltipEvent event) {
            ItemStack stack = event.getItemStack();
            if (!(stack.getItem() instanceof FishItem) && releasable(stack)) {
                Tooltips.event(event.getToolTip()).hint("farm.pioneers.fish.release_hint");
            }
        }
    }
}
