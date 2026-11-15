package fr.pioneers.wild;

import fr.pioneers.farm.FarmCard;
import fr.pioneers.farm.device.DeviceSites;
import fr.pioneers.fauna.FaunaCounter;
import fr.pioneers.fauna.FaunaGroup;
import fr.pioneers.fauna.FaunaRegistry;
import fr.pioneers.fauna.FaunaRules;
import fr.pioneers.fauna.FaunaSettings;
import fr.pioneers.fauna.FaunaSpawner;
import fr.pioneers.fauna.FaunaSpecies;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 7) : tuile du NICHOIR A OISEAUX : inscrite au registre des sites ({@link DeviceSites.Kind#BIRD_HOUSE}) ; toutes les
 * {@link Wild7Rules#HOUSE_PERIOD} ticks, une chance sur 3 d'APPELER un oiseau s'il y a moins de {@link Wild7Rules#HOUSE_BIRDS} pigeons et
 * pics-verts a 32 blocs : un pic-vert si des troncs sont pres, sinon un pigeon, ARRIVE DE LOIN (70 a 100 blocs, en vol, lieu permis par le
 * repeuplement : loin et hors de vue des joueurs ; groupe des oiseaux de la zone sous son plafond) et vient se poser pres du nichoir
 * ({@link WildBird#summon}). Fiche au regard.
 */
public class BirdHouseTile extends BlockEntity implements FarmCard.Source {

    public static final int ARRIVE_MIN = 70, ARRIVE_MAX = 100;
    int clock;

    public BirdHouseTile(BlockPos pos, BlockState state) {
        super(PiWild7.BIRD_HOUSE_TILE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            DeviceSites.add(level, worldPosition, DeviceSites.Kind.BIRD_HOUSE);
            clock = Math.floorMod(worldPosition.hashCode(), Wild7Rules.HOUSE_PERIOD);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) {
            DeviceSites.remove(level, worldPosition, DeviceSites.Kind.BIRD_HOUSE);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BirdHouseTile t) {
        if (++t.clock < Wild7Rules.HOUSE_PERIOD || !(level instanceof ServerLevel sl)) {
            return;
        }
        t.clock = 0;
        if (sl.random.nextInt(3) == 0 && birdsNear(sl, pos) < Wild7Rules.HOUSE_BIRDS) {
            call(sl, pos, sl.random);
        }
    }

    /** Pigeons et pics-verts a 32 blocs. */
    public static int birdsNear(Level level, BlockPos pos) {
        return level.getEntitiesOfClass(WildBird.class, new AABB(pos).inflate(Wild7Rules.HOUSE_RADIUS)).size();
    }

    /** Troncs pres du nichoir (6 blocs) : il attire plutot un pic-vert. */
    public static boolean trees(Level level, BlockPos pos) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-6, -3, -6), pos.offset(6, 3, 6))) {
            if (level.getBlockState(p).is(BlockTags.LOGS) && ++n >= 6) {
                return true;
            }
        }
        return false;
    }

    /** Fait arriver un oiseau de loin vers le nichoir ; rend l'oiseau (null : aucune place permise). */
    public static WildBird call(ServerLevel level, BlockPos house, RandomSource r) {
        FaunaSettings s = FaunaSettings.current();
        boolean wood = trees(level, house);
        EntityType<? extends WildBird> type = wood ? PiWild7.WOODPECKER.get() : PiWild7.PIGEON.get();
        FaunaSpecies sp = FaunaRegistry.byId(wood ? "pioneers:woodpecker" : "pioneers:pigeon");
        if (sp == null) {
            return null;
        }
        FaunaSpawner.Limits limits = FaunaSpawner.Limits.repopulation(level, s);
        for (int t = 0; t < 16; t++) {
            double a = r.nextDouble() * Math.PI * 2, d = ARRIVE_MIN + r.nextDouble() * (ARRIVE_MAX - ARRIVE_MIN);
            int x = Mth.floor(house.getX() + 0.5 + Math.cos(a) * d), z = Mth.floor(house.getZ() + 0.5 + Math.sin(a) * d);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            long zone = FaunaRules.zoneKey(x, z);
            int birds = FaunaCounter.of(level).groups(zone)[FaunaGroup.BIRD.ordinal()];
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 6 + r.nextInt(4);
            BlockPos at = new BlockPos(x, y, z);
            if (birds >= s.cap(FaunaGroup.BIRD) || !FaunaSpawner.allowed(level, at, sp, limits)) {
                continue;
            }
            WildBird b = type.create(level);
            if (b == null) {
                return null;
            }
            b.moveTo(x + 0.5, y, z + 0.5, r.nextFloat() * 360F, 0);
            b.finalizeSpawn(level, level.getCurrentDifficultyAt(at), net.minecraft.world.entity.MobSpawnType.EVENT, null, null);
            if (level.noCollision(b) && level.addFreshEntity(b)) {
                b.summon(house);
                return b;
            }
        }
        return null;
    }

    @Override
    public FarmCard card(boolean sneaking) {
        BlockState st = getBlockState();
        FarmCard c = FarmCard.of(new ItemStack(st.getBlock()), st.getBlock().getName(), 0x9CC080);
        int n = level == null ? 0 : birdsNear(level, worldPosition);
        c.add(FarmCard.Line.pair(Component.translatable("wild.pioneers.house.birds"), Component.literal(String.valueOf(n)), n > 0 ? FarmCard.OK : FarmCard.DIM));
        c.add(FarmCard.Line.hint(Component.translatable("wild.pioneers.house.hint")));
        return c;
    }

    @NotNull
    public BlockPos pos() {
        return worldPosition;
    }
}
