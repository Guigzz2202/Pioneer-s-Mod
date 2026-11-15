package fr.pioneers.farm.device;

import fr.pioneers.farm.FarmCard;
import fr.pioneers.farm.crop.CropTile;
import fr.pioneers.farm.crop.FarmCropBlock;
import fr.pioneers.farm.tree.FruitLeavesBlock;
import fr.pioneers.farm.ui.FarmLayout;
import fr.pioneers.farm.ui.FluidPair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Arroseur (bronze : rayon 4 ; acier : rayon 7 et fertirrigation). Toutes les 2 s s'il a de l'eau, qu'il ne pleut pas et sans signal de
 * redstone : terre labouree du rayon humide, feu eteint, et chaque culture a une chance de pousser en plus (bronze x1,5, acier x2 en moyenne).
 * Eau : tuyaux, seaux, pluie, ou une source d'eau juste dessous (posé sur un trou d'eau, il pompe seul). L'acier prend de l'engrais
 * (une case) : 1 engrais marque {@link #PER_FERTILIZER} cultures « fertilisees ». La tete tourne (rendu client) pendant l'arrosage.
 */
public class SprinklerTile extends FarmDeviceTile {

    public static final int CYCLE = 40, PER_FERTILIZER = 8;
    /** 1.0.5-beta (lot 41) : case 0 l'engrais (acier), puis la paire « Vider » de la cuve (seau d'eau ; avant : au clic). */
    public static final int DRAIN_IN = 1, DRAIN_OUT = 2;
    /** Tick aleatoire naturel par cycle : 3 / 4096 par tick x 40 = 0,029 ; bronze +50 %, acier +100 %. */
    private static final float BRONZE_BOOST = 0.0146F, STEEL_BOOST = 0.0293F;

    /** Arrose en ce moment (synchronise : la tete tourne). */
    private boolean spraying;
    /** Cultures encore a fertiliser avec l'engrais entame. */
    private int fertilizer;
    /** Cote client : angle de la tete. */
    public float angle, lastAngle;
    private float spin;

    public SprinklerTile(BlockPos pos, BlockState state) {
        super(pos, state, 3, state.getBlock() instanceof DeviceBlock b && b.kind() == DeviceBlock.Kind.STEEL_SPRINKLER ? 8000 : 4000);
    }

    @Override
    protected FarmLayout layout() {
        FarmLayout.Builder b = FarmLayout.builder().tank(DRAIN_IN, DRAIN_OUT, -1, -1);
        if (steel()) {
            b.single(0, FarmLayout.Role.STORE, "gui.pioneers.farm.slot.fertilizer");
        }
        return b.build();
    }

    public boolean steel() {
        return kind() == DeviceBlock.Kind.STEEL_SPRINKLER;
    }

    public int radius() {
        return steel() ? 7 : 4;
    }

    private int use() {
        return steel() ? 50 : 20;
    }

    public boolean spraying() {
        return spraying;
    }

    @Override
    public void serverTick() {
        if (level == null || (level.getGameTime() + worldPosition.asLong()) % CYCLE != 0) {
            return;
        }
        //Pompe : une source d'eau juste dessous
        if (tank != null && level.getFluidState(worldPosition.below()).is(FluidTags.WATER) && level.getFluidState(worldPosition.below()).isSource()) {
            tank.fill(new FluidStack(Fluids.WATER, 200), IFluidHandler.FluidAction.EXECUTE);
        } else if (tank != null && level.isRainingAt(worldPosition.above())) {
            tank.fill(new FluidStack(Fluids.WATER, 50), IFluidHandler.FluidAction.EXECUTE);
        }
        boolean on = tank != null && tank.getFluidAmount() >= use() && !level.hasNeighborSignal(worldPosition)
                     && !level.isRainingAt(worldPosition.above());
        if (on) {
            tank.drain(use(), IFluidHandler.FluidAction.EXECUTE);
            water((ServerLevel) level);
        }
        if (on != spraying) {
            spraying = on;
            sync();
        }
    }

    private void water(ServerLevel level) {
        int r = radius();
        RandomSource random = level.random;
        float boost = steel() ? STEEL_BOOST : BRONZE_BOOST;
        boolean fertigate = steel() && (fertilizer > 0 || !items.get(0).isEmpty());
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    p.set(worldPosition.getX() + dx, worldPosition.getY() + dy, worldPosition.getZ() + dz);
                    BlockState s = level.getBlockState(p);
                    if (s.getBlock() instanceof FarmBlock) {
                        if (s.getValue(FarmBlock.MOISTURE) < 7) {
                            level.setBlock(p, s.setValue(FarmBlock.MOISTURE, 7), 2);
                        }
                    } else if (s.getBlock() instanceof FarmCropBlock crop) {
                        if (random.nextFloat() < boost && !crop.isMaxAge(s)) {
                            crop.randomTick(s, level, p.immutable(), random);
                        }
                        if (fertigate && random.nextFloat() < 0.08F) {
                            CropTile tile = FarmCropBlock.tile(level, p);
                            if (tile != null && !tile.has(CropTile.FERTILIZED) && takeFertilizer()) {
                                crop.fertilize(level, p.immutable(), s, 0);
                                fertigate = fertilizer > 0 || !items.get(0).isEmpty();
                            }
                        }
                    } else if (s.is(BlockTags.FIRE)) {
                        level.removeBlock(p, false);
                    } else if (s.getBlock() instanceof FruitLeavesBlock && random.nextFloat() < boost) {
                        s.randomTick(level, p.immutable(), random);
                    }
                }
            }
        }
        if (random.nextInt(4) == 0) {
            level.playSound(null, worldPosition, SoundEvents.WEATHER_RAIN, SoundSource.BLOCKS, 0.08F, 1.6F);
        }
    }

    private boolean takeFertilizer() {
        if (fertilizer <= 0) {
            ItemStack s = items.get(0);
            if (s.isEmpty()) {
                return false;
            }
            s.shrink(1);
            fertilizer = PER_FERTILIZER;
            sync();
        }
        fertilizer--;
        return true;
    }

    public static boolean isFertilizer(ItemStack stack) {
        return stack.is(PiFarmDevices.FERTILIZERS);
    }

    @Override
    protected boolean canInsert(int slot, ItemStack stack, @Nullable Direction side) {
        return slot == 0 ? steel() && isFertilizer(stack) : slot == DRAIN_IN && FluidPair.drainable(stack);
    }

    @Override
    protected boolean canExtract(int slot, @Nullable Direction side) {
        return slot == DRAIN_OUT;
    }

    // ------------------------------------------------------------------ client

    @Override
    public void clientTick() {
        lastAngle = angle;
        spin = Mth.lerp(0.06F, spin, spraying ? (steel() ? 9F : 12F) : 0F);
        angle = (angle + spin) % 360F;
        if (lastAngle > angle) {
            lastAngle -= 360F;
        }
    }

    @Override
    public void particles(RandomSource random) {
        if (!spraying || level == null) {
            return;
        }
        //Jets : quelques gouttes lancees dans la direction de la buse, qui retombent en arc
        double rad = Math.toRadians(angle);
        double cx = worldPosition.getX() + 0.5, cz = worldPosition.getZ() + 0.5, y = worldPosition.getY() + (steel() ? 1.05 : 0.85);
        //SPLASH avec une vitesse horizontale seule : la goutte part a 0,1 vers le haut et retombe (portee ~ vitesse x 10 blocs)
        double reach = radius() * 0.045;
        for (int i = 0; i < 6; i++) {
            double a = rad + (random.nextDouble() - 0.5) * 0.35, v = reach * (0.35 + random.nextDouble() * 0.7);
            level.addParticle(ParticleTypes.SPLASH, cx + Math.sin(a) * 0.3, y, cz + Math.cos(a) * 0.3, Math.sin(a) * v, 0, Math.cos(a) * v);
        }
        //Deuxieme buse, a l'oppose (tete a deux bras)
        for (int i = 0; i < 3; i++) {
            double a = rad + Math.PI + (random.nextDouble() - 0.5) * 0.5, v = reach * (0.2 + random.nextDouble() * 0.4);
            level.addParticle(ParticleTypes.SPLASH, cx + Math.sin(a) * 0.2, y, cz + Math.cos(a) * 0.2, Math.sin(a) * v, 0, Math.cos(a) * v);
        }
        if (random.nextInt(3) == 0) {
            double a = random.nextDouble() * Math.PI * 2, d = random.nextDouble() * radius();
            level.addParticle(ParticleTypes.SPLASH, cx + Math.sin(a) * d, worldPosition.getY() - 0.05, cz + Math.cos(a) * d, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ fiche, sauvegarde

    @Override
    protected int accent() {
        return 0x3C9CDC;
    }

    @Override
    protected void describe(FarmCard card, boolean sneaking) {
        //1.6 : fiche synthetique
        int side = radius() * 2 + 1;
        card.add(spraying ? FarmCard.Line.pair(FarmCard.label("spray"), Component.translatable("card.pioneers.area", side, side), FarmCard.OK)
                          : FarmCard.Line.pair(FarmCard.label("spray"), FarmCard.word("sprinkler.off"), FarmCard.DIM));
        if (steel()) {
            card.add(FarmCard.Line.pair(FarmCard.label("fertilizer"), FarmCard.word("sprinkler.doses", items.get(0).getCount(), fertilizer), 0xC8A050));
        }
        if (level != null && level.getBlockState(worldPosition.below()).is(Blocks.WATER)) {
            card.add(FarmCard.Line.pair(FarmCard.label("pump"), FarmCard.word("sprinkler.pump"), 0x6EB4F0));
        }
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putBoolean("spraying", spraying);
        tag.putInt("fertilizer", fertilizer);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        spraying = tag.getBoolean("spraying");
        fertilizer = tag.getInt("fertilizer");
    }
}
