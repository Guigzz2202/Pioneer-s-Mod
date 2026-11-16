package fr.pioneers.gametest;

import fr.pioneers.Pioneers;
import fr.pioneers.api.multiblock.FurnaceHeat;
import fr.pioneers.api.multiblock.HouseNbt;
import fr.pioneers.api.multiblock.HouseShapes;
import fr.pioneers.tech.bronze.blast.BlastFurnaceTile;
import fr.pioneers.tech.bronze.blast.BlastLayout;
import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.society.clan.territory.ClanChunkloading;
import fr.pioneers.society.clan.territory.ClanTerritory;
import fr.pioneers.society.clan.territory.TerritoryRules;
import fr.pioneers.core.vanilla.PiVanillaMaterials;
import fr.pioneers.society.economy.EconomyApi;
import fr.pioneers.tech.bronze.PiBronze;
import fr.pioneers.world.wild.Boar;
import fr.pioneers.world.wild.PiWild;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.5-beta, lot 13 (retours de jeu 1 : bugs et faune, GameTests) : le SANGLIER affame fouille et detruit une culture en plein champ,
 * jamais une culture close de clotures ni une culture en claim ; DEGATS dans la cuve d'un haut fourneau allume (et pas eteint) ; ACIER en
 * 800 ticks de travail (rien avant) ; PRIX des chunkloads (n-ieme = min(500, arrondi(50 + 108 ln n)), paye a la pose) et CLAIMS au prix fixe
 * de 100 qui ne chargent plus le chunk.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot13Tests {

    private PatchP105Lot13Tests() {
    }

    // ------------------------------------------------------------------ 1. sanglier et cultures

    /** Champ de ble mur sur terre labouree en {@code crop}, sanglier affame a 5 blocs a l'ouest. */
    static Boar field(Gt.Scope s, BlockPos crop) {
        GameTestHelper h = s.h;
        h.setBlock(crop.below(), Blocks.FARMLAND);
        h.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        Boar b = PatchP105Lot10Tests.add(s, PiWild.BOAR.get(), crop.west(5));
        b.setPersistenceRequired();
        b.makeHungry();
        return b;
    }

    static boolean wheat(GameTestHelper h, BlockPos crop) {
        return h.getBlockState(crop).is(Blocks.WHEAT);
    }

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_13_boar_open", timeoutTicks = 600)
    public static void p105_13_sanglier_champ_ouvert(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos crop = new BlockPos(12, 1, 10);
        Boar b = field(s, crop);
        Gt.check(Boar.crop(h.getBlockState(crop)), "le ble est une culture");
        Gt.check(!Boar.enclosed(s.level, h.absolutePos(crop), h.absolutePos(crop.west(5))), "champ ouvert vu comme clos");
        Gt.check(b.rootable(h.absolutePos(crop), h.absolutePos(crop.west(5))), "un sanglier affame doit pouvoir fouiller un champ ouvert");
        h.succeedWhen(() -> {
            Gt.check(!wheat(h, crop), "le sanglier affame n'a pas encore detruit le ble du champ ouvert");
            s.done();
        });
    }

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_13_boar_fenced", timeoutTicks = 500)
    public static void p105_13_sanglier_champ_clos(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos crop = new BlockPos(12, 1, 10);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    h.setBlock(crop.offset(dx, 0, dz), Blocks.OAK_FENCE);
                }
            }
        }
        Boar b = field(s, crop);
        Gt.check(Boar.enclosed(s.level, h.absolutePos(crop), h.absolutePos(crop.west(5))), "champ cloture vu comme ouvert");
        Gt.check(!b.rootable(h.absolutePos(crop), h.absolutePos(crop.west(5))), "un champ cloture est a l'abri");
        h.runAfterDelay(400, () -> {
            Gt.check(wheat(h, crop), "le sanglier a detruit le ble d'un champ cloture");
            Gt.check(h.getBlockState(crop.offset(2, 0, 0)).is(Blocks.OAK_FENCE), "cloture abimee");
            s.done();
        });
    }

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_13_boar_claim", timeoutTicks = 500)
    public static void p105_13_sanglier_champ_en_claim(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("gt13boar", "Fermiere", new BlockPos(2, 1, 2));
        Clan c = s.clan("GtSang" + ClanTests.sfx(h), p);
        BlockPos crop = new BlockPos(12, 1, 10);
        s.claim(c, h.absolutePos(crop));
        Boar b = field(s, crop);
        Gt.check(!b.rootable(h.absolutePos(crop), h.absolutePos(crop.west(5))), "une culture en claim est a l'abri");
        h.runAfterDelay(400, () -> {
            Gt.check(wheat(h, crop), "le sanglier a detruit le ble d'un claim");
            s.done();
        });
    }

    // ------------------------------------------------------------------ 2. degats dans un four allume ; acier en 800 ticks

    /** Haut fourneau forme ; {@code lit} : charbon de bois dans la cuve (allume) ou rien (eteint), cuve tenue a 1 650 degres. */
    static BlastFurnaceTile furnace(GameTestHelper h, BlockPos c, boolean lit, boolean iron) {
        BlastFurnaceTile tile = (BlastFurnaceTile) h.getBlockEntity(c);
        Gt.check(tile.formed(), "haut fourneau forme");
        if (iron) {
            tile.input(0).setStack(new ItemStack(PiVanillaMaterials.item("raw_iron"), 8));
            tile.input(2).setStack(new ItemStack(PiBronze.QUICKLIME.get(), 4));
        }
        if (lit) {
            tile.input(1).setStack(new ItemStack(PiVanillaMaterials.CHARCOAL.get(), 8));
        }
        h.onEachTick(() -> tile.testTemperature(1650));
        return tile;
    }

    /** Cochon immobile dans la case la plus basse de la cuve (cases interieures du NBT). */
    static Pig pigInside(Gt.Scope s, BlastFurnaceTile tile) {
        List<BlockPos> inside = HouseNbt.positions(tile.getBlockPos(), tile.facing(), BlastLayout.SHAPE, "I");
        Gt.check(!inside.isEmpty(), "aucune case interieure au haut fourneau");
        BlockPos low = inside.stream().min(Comparator.comparingInt(BlockPos::getY)).orElseThrow();
        Pig pig = EntityType.PIG.create(s.level);
        Gt.check(pig != null, "cochon");
        pig.moveTo(low.getX() + 0.5, low.getY(), low.getZ() + 0.5, 0, 0);
        pig.setNoAi(true);
        s.level.addFreshEntity(pig);
        s.onCleanup(pig::discard);
        return pig;
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p105_13_furnace_lit", timeoutTicks = 200)
    public static void p105_13_four_allume_brule(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos anchor = MultiblockApi6Tests.buildHouse(h, HouseShapes.BLAST_FURNACE, Direction.SOUTH);
        BlockPos c = anchor.relative(Direction.SOUTH);
        h.runAfterDelay(40, () -> {
            BlastFurnaceTile tile = furnace(h, c, true, false);
            Pig pig = pigInside(s, tile);
            float before = pig.getHealth();
            h.runAfterDelay(3 * FurnaceHeat.PERIOD + 5, () -> {
                Gt.check(pig.getHealth() < before || !pig.isAlive(), "le cochon dans la cuve d'un four allume n'est pas blesse");
                Gt.check(pig.getRemainingFireTicks() > 0 || !pig.isAlive(), "le cochon ne brule pas");
                s.done();
            });
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p105_13_furnace_cold", timeoutTicks = 200)
    public static void p105_13_four_eteint_inoffensif(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos anchor = MultiblockApi6Tests.buildHouse(h, HouseShapes.BLAST_FURNACE, Direction.SOUTH);
        BlockPos c = anchor.relative(Direction.SOUTH);
        h.runAfterDelay(40, () -> {
            BlastFurnaceTile tile = furnace(h, c, false, false);
            Pig pig = pigInside(s, tile);
            float before = pig.getHealth();
            h.runAfterDelay(4 * FurnaceHeat.PERIOD, () -> {
                Gt.check(pig.isAlive() && pig.getHealth() >= before, "un four eteint ne blesse pas");
                Gt.check(pig.getRemainingFireTicks() <= 0, "un four eteint n'enflamme pas");
                s.done();
            });
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p105_13_steel_800", timeoutTicks = 1100)
    public static void p105_13_acier_en_800_ticks(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.equal(800, BlastFurnaceTile.STEEL_TICKS, "acier en 800 ticks (40 s)");
        BlockPos anchor = MultiblockApi6Tests.buildHouse(h, HouseShapes.BLAST_FURNACE, Direction.SOUTH);
        BlockPos c = anchor.relative(Direction.SOUTH);
        h.runAfterDelay(60, () -> {
            BlastFurnaceTile tile = furnace(h, c, true, true);
            h.runAfterDelay(BlastFurnaceTile.STEEL_TICKS - 20, () -> Gt.check(tile.steelSlot().isEmpty(), "de l'acier avant 800 ticks de travail"));
            h.runAfterDelay(BlastFurnaceTile.STEEL_TICKS + 30, () -> {
                Gt.check(tile.steelSlot().getCount() >= 1, "pas d'acier apres 800 ticks de travail");
                s.done();
            });
        });
    }

    // ------------------------------------------------------------------ 3. prix des chunkloads ; claims au prix fixe, jamais charges

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_13_prices", timeoutTicks = 200)
    public static void p105_13_prix_chunkloads_et_claims(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(EconomyApi.enabled(), "economie coupee : prix non verifiables");
        for (int n = 1; n <= 80; n++) {
            long want = Math.min(500, Math.round(50 + 108 * Math.log(n)));
            Gt.equal(want, TerritoryRules.chunkloadPrice(50, 108.0, 500, n), "regle du prix du chunkload no " + n);
            Gt.equal(want, ClanChunkloading.price(n), "prix (reglages par defaut) du chunkload no " + n);
        }
        Gt.equal(50L, ClanChunkloading.price(1), "1er chunkload");
        Gt.equal(125L, ClanChunkloading.price(2), "2e chunkload");
        Gt.equal(500L, ClanChunkloading.price(200), "plafond du chunkload");
        ServerPlayer rep = s.player("gt13rep", "Intendante", new BlockPos(2, 1, 2));
        ServerPlayer mem = s.player("gt13mem", "Coursier", new BlockPos(3, 1, 2));
        Clan c = s.clan("GtPrix" + ClanTests.sfx(h), rep, mem);
        c.treasury = 10_000;
        //claims achetes : 100 chacun, quel que soit le rang
        Gt.check(ClanTerritory.buy(rep, 3, -1) == null, "achat de 3 claims refuse");
        Gt.equal(10_000L - 300L, c.treasury, "3 claims au prix fixe de 100");
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        ChunkPos f = new ChunkPos((origin.getX() >> 4) + 61, (origin.getZ() >> 4) + 5);
        String dim = ClanApi.dim(s.level);
        s.onCleanup(() -> ForgeChunkManager.forceChunk(s.level, Pioneers.MODID, c.id, f.x, f.z, false, true));
        s.claim(c, f.getWorldPosition());
        Gt.equal(0, PatchP104Lot8Tests.owners(s.level, f.toLong()), "un claim charge encore le chunk");
        Gt.check(!ClanChunkloading.isLoaded(c, dim, f.toLong()), "claim dit charge sans emplacement");
        //1er emplacement : 50 a la pose
        long before = c.treasury;
        Gt.equal(50L, ClanChunkloading.nextPrice(s.server, c), "prix affiche du 1er emplacement");
        ClanChunkloading.load(rep, s.level, f.toLong());
        Gt.check(c.chunkload.contains(ClanTerritory.key(dim, f.toLong())), "emplacement non pris");
        Gt.equal(before - 50L, c.treasury, "1er emplacement paye 50 a la pose");
        Gt.equal(125L, ClanChunkloading.nextPrice(s.server, c), "prix affiche du 2e emplacement");
        ClanChunkloading.unload(s.server, c, ClanTerritory.key(dim, f.toLong()), false);
        s.done();
    }
}
