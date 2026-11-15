package fr.pioneers.farm.aquarium;

import fr.pioneers.farm.PiFishing;
import fr.pioneers.farm.crustacean.GeneratedCrustaceans;
import fr.pioneers.farm.fish.GeneratedFish;
import fr.pioneers.farm.fish.LiveRelease;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.0.4-beta (lot 28) : les gestes de l'aquarium (code commun, cote serveur ; le client repond SUCCESS pour l'animation de la main).
 * <ul>
 *     <li>seau d'eau : remplit la case VIDE LA PLUS BASSE du bassin (bassin = aquariums relies, au plus {@link #MAX_TANK} cases) ; l'eau est
 *     finie dans le mod : un seau = une case ;</li>
 *     <li>seau de poisson (du mod, ou cabillaud, saumon, poisson-globe, poisson tropical vanilla) : le poisson rejoint le bassin s'il y a
 *     de la place (1.0.5-beta lot 14 : PLACES selon sa taille reelle, 16 par case d'eau, {@link AquariumRules} ; avant : 4 poissons par
 *     case) ; le seau revient vide ;</li>
 *     <li>1.0.4-beta (lot 35, Guillaume : « Dans les aquarium on devrait pouvoir clic droit avec le poisson pour le mettre directement sans
 *     utiliser le seau d'eau de poisson (A condition que l'aquarium contienne de l'eau) ») : POISSON CRU EN MAIN (poisson du mod, ou
 *     cabillaud, saumon, poisson-globe, poisson tropical vanilla : {@link LiveRelease#fishSpecies}) : il rejoint le bassin comme au seau, un
 *     objet consomme (pas en creatif), sa taille gardee ({@link LiveRelease#fishSize}) ; poisson cuit, filet... : geste inchange.
 *     Seau ou main : la case visee si elle est pleine d'eau et a de la place, sinon la case pleine du bassin la plus proche qui a de la
 *     place ({@link #fishCell}) ; bassin sans eau : « L'aquarium doit contenir de l'eau » ; bassin plein : message, rien consomme ;</li>
 *     <li>seau de crustace : le crustace descend au fond (case pleine la plus basse de la colonne) ;</li>
 *     <li>seau vide : reprend le dernier habitant de la case visee (dans son seau), sinon l'eau de la case pleine la plus haute sans
 *     habitant ;</li>
 *     <li>main vide, accroupi : nombre d'habitants et cases pleines du bassin ; 1.0.5-beta (lot 25) : Maj + clic droit (accroupi), QUEL QUE
 *     SOIT l'objet en main, ouvre l'interface de COMPOSITION du bassin ({@code AquariumEvents}, {@code AquariumMenu}) qui montre aussi cet
 *     etat ; le clic droit simple garde tous les gestes ci-dessus ;</li>
 *     <li>1.0.5-beta (lot 25) : un requin ({@link AquariumRules#shark}) demande un bassin d'au moins {@link AquariumRules#SHARK_MIN_CELLS}
 *     cases d'eau (« aquarium.pioneers.too_small ») et va dans la case qui a le plus d'eau autour d'elle.</li>
 * </ul>
 */
public final class Aquariums {

    private Aquariums() {
    }

    public static final int MAX_TANK = 512;
    /** Poissons vanilla qui se mettent au seau : seau -> espece du mod du meme id. */
    public static final String[][] VANILLA_BUCKETS = {{"cod_bucket", "cod"}, {"salmon_bucket", "salmon"}, {"pufferfish_bucket", "pufferfish"},
                                                      {"tropical_fish_bucket", "tropical_fish"}};

    public static InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        boolean water = held.is(Items.WATER_BUCKET), empty = held.is(Items.BUCKET), crab = held.is(PiAquarium.CRUSTACEAN_BUCKET.get());
        int fish = fishSpecies(held);
        //1.0.4-beta (lot 35) : poisson cru en main (pas un seau : fishSpecies ne voit que les seaux)
        //1.0.5-beta (lot 31, retour 42) : PANIER DE PECHE en main : le poisson CHOISI dans le panier (sinon le premier qui peut vivre au bassin)
        ItemStack creel = held.getItem() instanceof fr.pioneers.building.home.bag.BagItem b && b.kind == fr.pioneers.building.home.bag.BagKind.FISH_BAG ? held : ItemStack.EMPTY;
        ItemStack live = creel.isEmpty() ? held
                         : fr.pioneers.building.home.bag.BagStore.takeOne(creel, fr.pioneers.building.home.bag.BagKind.FISH_BAG, s -> LiveRelease.fishSpecies(s) >= 0, true);
        int handFish = fish < 0 && !crab && !water && !empty && !live.isEmpty() ? LiveRelease.fishSpecies(live) : -1;
        if (!water && !empty && !crab && fish < 0 && handFish < 0) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        List<BlockPos> tank = tank(level, pos);
        if (water) {
            BlockPos target = null;
            for (BlockPos p : tank) {
                if (!level.getBlockState(p).getValue(AquariumBlock.FILLED) && (target == null || p.getY() < target.getY())) {
                    target = p;
                }
            }
            if (target == null) {
                player.displayClientMessage(Component.translatable("aquarium.pioneers.full_water"), true);
                return InteractionResult.SUCCESS;
            }
            level.setBlock(target, level.getBlockState(target).setValue(AquariumBlock.FILLED, true), 3);
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1F, 1F);
            player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.BUCKET)));
            return InteractionResult.SUCCESS;
        }
        if (fish >= 0 || handFish >= 0) {
            float size;
            if (handFish >= 0) {
                size = LiveRelease.fishSize(live, handFish, level.random);
            } else {
                CompoundTag t = held.getTag();
                size = t != null && t.contains("Size") ? t.getFloat("Size") : 1F;
            }
            //1.0.5-beta (lot 14) : places du poisson (taille reelle) contre les places du bassin (16 par case d'eau)
            int species = handFish >= 0 ? handFish : fish;
            int need = AquariumRules.places(GeneratedFish.SPECIES[species], size), wetCells = wetCells(level, tank), used = usedPlaces(level, tank);
            //1.0.5-beta (lot 25) : un requin demande un grand bassin
            int min = AquariumRules.minWetCells(GeneratedFish.SPECIES[species]);
            if (wetCells > 0 && wetCells < min) {
                player.displayClientMessage(Component.translatable("aquarium.pioneers.too_small", min, wetCells), true);
                return InteractionResult.SUCCESS;
            }
            BlockPos target = wetCells == 0 || !AquariumRules.fits(used, need, wetCells) ? null
                              : AquariumRules.shark(GeneratedFish.SPECIES[species]) ? roomiestCell(level, tank) : fishCell(level, pos, tank);
            if (target == null) {
                player.displayClientMessage(wetCells > 0 ? Component.translatable("aquarium.pioneers.full_fish", need,
                      Math.max(0, AquariumRules.capacity(wetCells) - used), AquariumRules.capacity(wetCells))
                                                     : Component.translatable("aquarium.pioneers.need_water"), true);
                return InteractionResult.SUCCESS;
            }
            AquariumTile tile = (AquariumTile) level.getBlockEntity(target);
            tile.add(false, handFish >= 0 ? handFish : fish, size);
            level.playSound(null, target, SoundEvents.BUCKET_EMPTY_FISH, SoundSource.BLOCKS, 1F, 1F);
            if (level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.SPLASH, target.getX() + 0.5, target.getY() + 0.9, target.getZ() + 0.5, 8, 0.25, 0.05, 0.25, 0.1);
            }
            if (handFish >= 0) {
                if (!player.getAbilities().instabuild) {
                    if (!creel.isEmpty()) {
                        fr.pioneers.building.home.bag.BagStore.takeOne(creel, fr.pioneers.building.home.bag.BagKind.FISH_BAG, s -> LiveRelease.fishSpecies(s) >= 0, false);
                    } else {
                        held.shrink(1);
                    }
                }
            } else {
                player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.BUCKET)));
            }
            return InteractionResult.SUCCESS;
        }
        if (crab) {
            BlockPos target = pos;
            while (level.getBlockState(target.below()).getBlock() instanceof AquariumBlock && level.getBlockState(target.below()).getValue(AquariumBlock.FILLED)) {
                target = target.below();
            }
            if (!level.getBlockState(target).getValue(AquariumBlock.FILLED)) {
                player.displayClientMessage(Component.translatable("aquarium.pioneers.no_water"), true);
                return InteractionResult.SUCCESS;
            }
            if (!(level.getBlockEntity(target) instanceof AquariumTile tile) || !tile.hasRoom(true)) {
                player.displayClientMessage(Component.translatable("aquarium.pioneers.full_crustaceans", AquariumTile.CRUSTACEAN_MAX), true);
                return InteractionResult.SUCCESS;
            }
            CompoundTag t = held.getTag();
            float size = t != null && t.contains("Size") ? t.getFloat("Size") : 1F;
            int species = Mth.clamp(t != null ? t.getInt("Species") : 0, 0, GeneratedCrustaceans.SPECIES.length - 1);
            tile.add(true, species, size);
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_FISH, SoundSource.BLOCKS, 1F, 1F);
            player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.BUCKET)));
            return InteractionResult.SUCCESS;
        }
        //seau vide : un habitant de la case visee, sinon de l'eau
        if (level.getBlockEntity(pos) instanceof AquariumTile tile && !tile.dwellers().isEmpty()) {
            AquariumTile.Dweller d = tile.take();
            ItemStack bucket = new ItemStack(d.crustacean() ? PiAquarium.CRUSTACEAN_BUCKET.get() : PiFishing.FISH_BUCKET.get());
            CompoundTag tag = bucket.getOrCreateTag();
            tag.putInt("Species", d.species());
            tag.putFloat("Size", d.size());
            level.playSound(null, pos, SoundEvents.BUCKET_FILL_FISH, SoundSource.BLOCKS, 1F, 1F);
            player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, bucket));
            return InteractionResult.SUCCESS;
        }
        BlockPos source = null;
        for (BlockPos p : tank) {
            if (level.getBlockState(p).getValue(AquariumBlock.FILLED) && (source == null || p.getY() > source.getY())
                  && level.getBlockEntity(p) instanceof AquariumTile t && t.dwellers().isEmpty()) {
                source = p;
            }
        }
        if (source == null) {
            player.displayClientMessage(Component.translatable("aquarium.pioneers.nothing"), true);
            return InteractionResult.SUCCESS;
        }
        level.setBlock(source, level.getBlockState(source).setValue(AquariumBlock.FILLED, false), 3);
        level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1F, 1F);
        player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.WATER_BUCKET)));
        return InteractionResult.SUCCESS;
    }

    /**
     * 1.0.4-beta (lot 35) : case ou va un poisson (seau ou main) : la case visee si elle est pleine d'eau et a de la place, sinon la case pleine
     * d'eau du bassin la plus proche qui a de la place (null : pas d'eau dans le bassin, ou toutes ses cases d'eau pleines).
     */
    public static BlockPos fishCell(Level level, BlockPos pos, List<BlockPos> tank) {
        //1.0.5-beta (lot 14) : la case d'eau la moins chargee (places), la case visee d'abord a egalite, puis la plus proche : les poissons se
        //repartissent dans le bassin
        BlockPos best = null;
        int bestUsed = Integer.MAX_VALUE;
        for (BlockPos p : tank) {
            if (!room(level, p)) {
                continue;
            }
            int u = ((AquariumTile) level.getBlockEntity(p)).places();
            if (best == null || u < bestUsed || u == bestUsed && (p.equals(pos) || !best.equals(pos) && p.distSqr(pos) < best.distSqr(pos))) {
                best = p;
                bestUsed = u;
            }
        }
        return best;
    }

    /**
     * 1.0.5-beta (lot 25) : case d'un gros poisson (requin) : celle qui a le plus de cases pleines autour d'elle (3 x 3 x 3 : la nage s'etend
     * d'une case par axe), parmi celles qui ont de la place.
     */
    public static BlockPos roomiestCell(Level level, List<BlockPos> tank) {
        BlockPos best = null;
        int bestN = -1;
        for (BlockPos p : tank) {
            if (!room(level, p)) {
                continue;
            }
            int n = 0;
            for (BlockPos q : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
                BlockState st = level.getBlockState(q);
                n += st.getBlock() instanceof AquariumBlock && st.getValue(AquariumBlock.FILLED) ? 1 : 0;
            }
            if (n > bestN) {
                best = p;
                bestN = n;
            }
        }
        return best;
    }

    /** 1.0.5-beta (lot 14) : cases pleines d'eau du bassin. */
    public static int wetCells(Level level, List<BlockPos> tank) {
        int n = 0;
        for (BlockPos p : tank) {
            BlockState st = level.getBlockState(p);
            n += st.getBlock() instanceof AquariumBlock && st.getValue(AquariumBlock.FILLED) ? 1 : 0;
        }
        return n;
    }

    /** 1.0.5-beta (lot 14) : places prises par tous les poissons du bassin. */
    public static int usedPlaces(Level level, List<BlockPos> tank) {
        int n = 0;
        for (BlockPos p : tank) {
            if (level.getBlockEntity(p) instanceof AquariumTile t) {
                n += t.places();
            }
        }
        return n;
    }

    private static boolean room(Level level, BlockPos p) {
        BlockState st = level.getBlockState(p);
        return st.getBlock() instanceof AquariumBlock && st.getValue(AquariumBlock.FILLED) && level.getBlockEntity(p) instanceof AquariumTile t
               && t.hasRoom(false);
    }

    /** Espece du mod d'un seau de poisson (-1 si ce n'en est pas un). */
    public static int fishSpecies(ItemStack stack) {
        if (stack.is(PiFishing.FISH_BUCKET.get())) {
            CompoundTag t = stack.getTag();
            return Mth.clamp(t != null ? t.getInt("Species") : 0, 0, GeneratedFish.SPECIES.length - 1);
        }
        var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null || !"minecraft".equals(key.getNamespace())) {
            return -1;
        }
        for (String[] vb : VANILLA_BUCKETS) {
            if (vb[0].equals(key.getPath())) {
                for (int i = 0; i < GeneratedFish.SPECIES.length; i++) {
                    if (GeneratedFish.SPECIES[i].id().equals(vb[1])) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    /** Le bassin : aquariums relies par leurs faces a celui-ci (largeur d'abord, au plus {@link #MAX_TANK}). */
    public static List<BlockPos> tank(Level level, BlockPos start) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && out.size() < MAX_TANK) {
            BlockPos p = queue.poll();
            if (!(level.getBlockState(p).getBlock() instanceof AquariumBlock)) {
                continue;
            }
            out.add(p);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.add(n) && level.isLoaded(n)) {
                    queue.add(n);
                }
            }
        }
        return out;
    }
}
