package fr.pioneers.farm.device;

import fr.pioneers.farm.FarmCard;
import fr.pioneers.farm.crop.FarmCropBlock;
import fr.pioneers.farm.kitchen.StationTiles;
import fr.pioneers.farm.tree.FruitLeavesBlock;
import fr.pioneers.farm.ui.FarmLayout;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Ruche : une colonie s'installe seule ({@link #SETTLE} s) s'il y a au moins 4 fleurs a butiner a {@link #RADIUS} blocs (fleurs, cultures,
 * arbres fruitiers en fleur). Le jour, sans pluie, elle remplit ses 4 cadres un a un (un cadre plein en {@link #FILL_SECONDS} s avec 12 fleurs
 * ou plus) et pollinise les alentours (pousse et graines plus souvent ameliorees ; fleurs d'arbre → fruit vert). Les cadres pleins se
 * prennent a main vide : sans feu de camp allume dessous (4 blocs), les abeilles piquent. Entonnoirs : cadres vides par le haut et les cotes,
 * cadres pleins par le bas. Abeilles dessinees par le rendu client ({@link #bees()}).
 */
public class ApiaryTile extends FarmDeviceTile {

    public static final int RADIUS = 6, SETTLE = 120, FILL_SECONDS = 300, SLOTS = 4;
    private boolean colony;
    private int settle, flowers;
    private final float[] fill = new float[SLOTS];

    public ApiaryTile(BlockPos pos, BlockState state) {
        super(pos, state, SLOTS, 0);
    }

    /**
     * 1.0.5-beta (lot 41, retour 60) : 4 places de cadres (barre du remplissage sous chacune) ; cadres vides poses, cadres pleins repris dans
     * l'interface (avant : cadre en main, main vide). Ouvrir la ruche sans feu de camp allume dessous fait piquer ({@link #opened}).
     */
    @Override
    protected FarmLayout layout() {
        return FarmLayout.builder().cells(StationTiles.range(0, SLOTS), 2).build();
    }

    @Override
    protected ItemStack ghost(FarmLayout.SlotSpec spec) {
        return new ItemStack(PiFarmDevices.HIVE_FRAME.get());
    }

    @Override
    public float cellProgress(int index) {
        if (index < 0 || index >= SLOTS) {
            return -1;
        }
        ItemStack s = items.get(index);
        return s.is(PiFarmDevices.HONEY_FRAME.get()) ? 1F : s.is(PiFarmDevices.HIVE_FRAME.get()) ? Math.min(0.99F, fill[index] / 100F) : -1;
    }

    /** Ouvrir une ruche peuplee sans fumee alors que des cadres sont pleins : les abeilles piquent (comme prendre les cadres a la main avant). */
    @Override
    public void opened(Player player) {
        boolean full = false;
        for (int i = 0; i < SLOTS; i++) {
            full |= items.get(i).is(PiFarmDevices.HONEY_FRAME.get());
        }
        if (!full || level == null) {
            return;
        }
        PiFarmDevices.award(level, worldPosition, "honey");
        if (colony && !smoked()) {
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
            level.playSound(null, worldPosition, SoundEvents.BEE_STING, SoundSource.BLOCKS, 1F, 1F);
            ((ServerLevel) level).sendParticles(ParticleTypes.ANGRY_VILLAGER, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1,
                  worldPosition.getZ() + 0.5, 4, 0.4, 0.2, 0.4, 0);
            say(player, "apiary.stung");
        } else {
            sound(SoundEvents.BEEHIVE_SHEAR, 0.8F, 1F);
        }
    }

    /** Un cadre retire (interface, entonnoir) : sa place repart de zero. */
    @Override
    protected void itemsChanged() {
        for (int i = 0; i < SLOTS; i++) {
            if (!items.get(i).is(PiFarmDevices.HIVE_FRAME.get())) {
                fill[i] = 0;
            }
        }
        super.itemsChanged();
    }

    /** 1.0.5-beta (lot 7) : la ruche s'inscrit au registre des sites (l'ours la trouve sans balayer le monde). */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            DeviceSites.add(level, worldPosition, DeviceSites.Kind.APIARY);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) {
            DeviceSites.remove(level, worldPosition, DeviceSites.Kind.APIARY);
        }
    }

    public boolean colony() {
        return colony;
    }

    public int flowers() {
        return flowers;
    }

    /** Abeilles visibles (client) : de jour, sans pluie. */
    public int bees() {
        if (!colony || level == null || !level.isDay() || level.isRaining()) {
            return 0;
        }
        return Math.min(7, 2 + flowers / 3);
    }

    private boolean active() {
        return colony && level != null && level.isDay() && !level.isRainingAt(worldPosition.above());
    }

    @Override
    public void serverTick() {
        if (level == null) {
            return;
        }
        long t = level.getGameTime() + worldPosition.asLong();
        if (t % 200 == 0) {
            int f = countFlowers();
            if (f != flowers) {
                flowers = f;
                sync();
            }
        }
        if (t % 20 == 0) {
            if (!colony) {
                if (flowers >= 4 && ++settle >= SETTLE) {
                    colony = true;
                    level.playSound(null, worldPosition, SoundEvents.BEEHIVE_ENTER, SoundSource.BLOCKS, 1F, 1F);
                    sync();
                }
            } else if (active()) {
                produce();
            }
        }
        if (t % 100 == 0 && active() && flowers > 0) {
            pollinate((ServerLevel) level);
        }
    }

    private int countFlowers() {
        int n = 0;
        //1.0.5-beta (lot 29) : palettes des sections d'abord (exact) : ni fleur, ni culture, ni fruit possible, rien a lire
        if (!fr.pioneers.core.block.ScanFilter.mayContain(level, worldPosition.offset(-RADIUS, -2, -RADIUS), worldPosition.offset(RADIUS, 3, RADIUS),
              ApiaryTile::flower)) {
            return 0;
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dy = -2; dy <= 3; dy++) {
                    BlockState s = level.getBlockState(p.set(worldPosition.getX() + dx, worldPosition.getY() + dy, worldPosition.getZ() + dz));
                    if (flower(s)) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    /** Fleur, culture mure (age 3 et plus) ou feuilles fruitieres en fleur : ce que butinent les abeilles. */
    private static boolean flower(BlockState s) {
        return s.is(BlockTags.FLOWERS) || s.getBlock() instanceof FarmCropBlock crop && crop.getAge(s) >= 3
               || s.getBlock() instanceof FruitLeavesBlock && s.getValue(FruitLeavesBlock.FRUIT) == 2;
    }

    private void produce() {
        float rate = Math.min(1F, flowers / 12F) * 100F / FILL_SECONDS;
        for (int i = 0; i < SLOTS; i++) {
            if (items.get(i).is(PiFarmDevices.HIVE_FRAME.get())) {
                fill[i] += rate;
                if (fill[i] >= 100) {
                    fill[i] = 0;
                    items.set(i, new ItemStack(PiFarmDevices.HONEY_FRAME.get(), items.get(i).getCount()));
                    level.playSound(null, worldPosition, SoundEvents.BEEHIVE_WORK, SoundSource.BLOCKS, 0.8F, 1F);
                    sync();
                } else if ((int) (fill[i] / 10) != (int) ((fill[i] - rate) / 10)) {
                    sync();
                } else {
                    setChanged();
                }
                return;
            }
        }
    }

    private void pollinate(ServerLevel level) {
        RandomSource r = level.random;
        for (int k = 0; k < 3; k++) {
            BlockPos p = worldPosition.offset(r.nextInt(RADIUS * 2 + 1) - RADIUS, r.nextInt(6) - 2, r.nextInt(RADIUS * 2 + 1) - RADIUS);
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof FarmCropBlock crop) {
                crop.pollinate(level, p, s);
                level.sendParticles(ParticleTypes.WAX_ON, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0);
            } else if (s.getBlock() instanceof FruitLeavesBlock && s.getValue(FruitLeavesBlock.FRUIT) == 2 && r.nextFloat() < 0.5F) {
                level.setBlock(p, s.setValue(FruitLeavesBlock.FRUIT, 3), 2);
                level.sendParticles(ParticleTypes.WAX_ON, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0);
            }
        }
    }

    /** Un feu de camp allume sous la ruche (4 blocs) : la fumee calme les abeilles. */
    private boolean smoked() {
        for (int dy = 1; dy <= 4; dy++) {
            BlockState s = level.getBlockState(worldPosition.below(dy));
            if (s.getBlock() instanceof CampfireBlock && s.getValue(CampfireBlock.LIT)) {
                return true;
            }
            if (!s.isAir() && !(s.getBlock() instanceof CampfireBlock) && dy > 1) {
                break;
            }
        }
        return false;
    }

    @Override
    protected boolean canInsert(int slot, ItemStack stack, @Nullable Direction side) {
        return side != Direction.DOWN && stack.is(PiFarmDevices.HIVE_FRAME.get());
    }

    @Override
    protected boolean canExtract(int slot, @Nullable Direction side) {
        return (side == Direction.DOWN || side == null) && items.get(slot).is(PiFarmDevices.HONEY_FRAME.get());
    }

    @Override
    protected int slotLimit(int slot) {
        return 1;
    }

    @Override
    public void particles(RandomSource random) {
        if (level == null) {
            return;
        }
        if (bees() > 0 && random.nextInt(60) == 0) {
            level.playLocalSound(worldPosition, SoundEvents.BEEHIVE_WORK, SoundSource.BLOCKS, 0.35F, 0.9F + random.nextFloat() * 0.2F, false);
        }
        for (int i = 0; i < SLOTS; i++) {
            if (items.get(i).is(PiFarmDevices.HONEY_FRAME.get()) && random.nextInt(20) == 0) {
                level.addParticle(ParticleTypes.DRIPPING_HONEY, worldPosition.getX() + 0.2 + random.nextDouble() * 0.6, worldPosition.getY() + 0.18,
                      worldPosition.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0, 0);
                return;
            }
        }
    }

    @Override
    protected int accent() {
        return 0xF0B030;
    }

    @Override
    protected void describe(FarmCard card, boolean sneaking) {
        //1.6 : fiche synthetique : colonie, fleurs, cadres en icones (avec leur remplissage)
        if (!colony) {
            card.add(flowers < 4 ? FarmCard.Line.pair(FarmCard.label("colony"), FarmCard.word("apiary.no_flowers"), FarmCard.WARN)
                                 : FarmCard.Line.gauge(FarmCard.label("colony"), settle / (float) SETTLE, FarmCard.word("apiary.settling"), 0xF0B030));
        } else {
            card.add(FarmCard.Line.pair(FarmCard.label("colony"), FarmCard.word("apiary.bees", bees()), 0xF0C040));
        }
        card.add(FarmCard.Line.pair(FarmCard.label("flowers"), Component.literal(String.valueOf(flowers)), flowers >= 12 ? FarmCard.OK : FarmCard.WARN));
        List<ItemStack> frames = new ArrayList<>();
        List<Float> fills = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            ItemStack s = items.get(i);
            if (s.is(PiFarmDevices.HONEY_FRAME.get())) {
                frames.add(s);
                fills.add(1F);
            } else if (s.is(PiFarmDevices.HIVE_FRAME.get())) {
                frames.add(s);
                fills.add(Math.min(0.99F, fill[i] / 100F));
            }
        }
        if (!frames.isEmpty()) {
            float[] f = new float[fills.size()];
            for (int i = 0; i < f.length; i++) {
                f[i] = fills.get(i);
            }
            card.add(FarmCard.Line.items(frames, f));
        }
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putBoolean("colony", colony);
        tag.putInt("settle", settle);
        tag.putInt("flowers", flowers);
        for (int i = 0; i < SLOTS; i++) {
            tag.putFloat("fill" + i, fill[i]);
        }
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        colony = tag.getBoolean("colony");
        settle = tag.getInt("settle");
        flowers = tag.getInt("flowers");
        for (int i = 0; i < SLOTS; i++) {
            fill[i] = tag.getFloat("fill" + i);
        }
    }
}
