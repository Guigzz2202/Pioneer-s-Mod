package fr.pioneers.gametest;

import fr.pioneers.farm.PiFishing;
import fr.pioneers.farm.aquarium.AquariumBlock;
import fr.pioneers.farm.aquarium.AquariumRules;
import fr.pioneers.farm.aquarium.AquariumTile;
import fr.pioneers.farm.aquarium.Aquariums;
import fr.pioneers.farm.aquarium.PiAquarium;
import fr.pioneers.farm.crop.Climate;
import fr.pioneers.farm.fish.GeneratedFish;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.tech.machine.MachineKind;
import fr.pioneers.core.registries.PiMachineKit;
import fr.pioneers.space.environment.EnvironmentSync;
import fr.pioneers.logistics.transport.CableTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.5-beta, lot 14 (retours de jeu 2 : monde et lisibilite, GameTests) : AQUARIUM aux places (16 par case d'eau, un poisson prend sa
 * taille reelle / 12,5 cm : deux morues + un saumon refuse, quatre poissons tropicaux puis plus rien ; thon de 18 places refuse dans une case,
 * accepte dans deux) ; FOUR ELECTRIQUE MKI qui cuit en 40 ticks (rien a 34, fait a 46) ; CARTE : climat et humidite envoyes a la minimap =
 * ceux des cultures (meme lieu, memes fonctions).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot14Tests {

    private PatchP105Lot14Tests() {
    }

    /** Deux aquariums empiles (bas, haut) ; {@code wetLow} / {@code wetHigh} : cases pleines d'eau. */
    static BlockPos[] tank(GameTestHelper h, boolean wetLow, boolean wetHigh) {
        BlockPos lo = h.absolutePos(new BlockPos(4, 2, 4)), hi = lo.above();
        BlockState glass = PiAquarium.AQUARIUM.getBlock().defaultBlockState();
        h.getLevel().setBlock(lo, glass.setValue(AquariumBlock.FILLED, wetLow), 3);
        h.getLevel().setBlock(hi, glass.setValue(AquariumBlock.FILLED, wetHigh), 3);
        return new BlockPos[]{lo, hi};
    }

    static void click(GameTestHelper h, Player p, BlockPos at, ItemStack held) {
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        Aquariums.use(h.getLevel().getBlockState(at), h.getLevel(), at, p, InteractionHand.MAIN_HAND);
    }

    static long fish(GameTestHelper h, BlockPos... cells) {
        long n = 0;
        for (BlockPos c : cells) {
            n += ((AquariumTile) h.getLevel().getBlockEntity(c)).count(false);
        }
        return n;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_14_places", timeoutTicks = 40)
    public static void p105_14_aquarium_places(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos[] t = tank(h, true, false);
        Player p = h.makeMockPlayer();
        click(h, p, t[0], new ItemStack(Items.COD_BUCKET));
        click(h, p, t[0], new ItemStack(Items.COD_BUCKET));
        Gt.equal(2L, fish(h, t[0]), "deux morues (6 places chacune) dans une case (16 places)");
        Gt.equal(12L, (long) Aquariums.usedPlaces(h.getLevel(), Aquariums.tank(h.getLevel(), t[0])), "12 places prises");
        click(h, p, t[0], new ItemStack(Items.SALMON_BUCKET));
        Gt.equal(2L, fish(h, t[0]), "saumon (6 places) refuse : 12 + 6 > 16");
        Gt.check(p.getMainHandItem().is(Items.SALMON_BUCKET), "saumon refuse : le seau reste plein");
        for (int i = 0; i < 4; i++) {
            click(h, p, t[0], new ItemStack(Items.TROPICAL_FISH_BUCKET));
        }
        Gt.equal(6L, fish(h, t[0]), "quatre poissons tropicaux (1 place chacun) : 16 places");
        click(h, p, t[0], new ItemStack(Items.TROPICAL_FISH_BUCKET));
        Gt.equal(6L, fish(h, t[0]), "bassin plein : pas un poisson tropical de plus");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_14_gros", timeoutTicks = 40)
    public static void p105_14_aquarium_gros_poisson(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        int tuna = -1;
        for (int i = 0; i < GeneratedFish.SPECIES.length; i++) {
            tuna = GeneratedFish.SPECIES[i].id().equals("tuna") ? i : tuna;
        }
        Gt.check(tuna >= 0, "thon absent des especes");
        Gt.equal(18, AquariumRules.places(GeneratedFish.SPECIES[tuna], 1F), "thon moyen (225 cm) : 18 places");
        BlockPos[] t = tank(h, true, false);
        Player p = h.makeMockPlayer();
        ItemStack bucket = new ItemStack(PiFishing.FISH_BUCKET.get());
        bucket.getOrCreateTag().putInt("Species", tuna);
        bucket.getOrCreateTag().putFloat("Size", 1F);
        click(h, p, t[0], bucket.copy());
        Gt.equal(0L, fish(h, t[0], t[1]), "thon refuse dans une seule case d'eau (16 places)");
        h.getLevel().setBlock(t[1], h.getLevel().getBlockState(t[1]).setValue(AquariumBlock.FILLED, true), 3);
        click(h, p, t[0], bucket.copy());
        Gt.equal(1L, fish(h, t[0], t[1]), "thon accepte dans deux cases d'eau (32 places)");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_14_four", timeoutTicks = 200)
    public static void p105_14_four_electrique(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        //1.0.5-beta (lot 21) : 80 / 40 / 20 / 10 ticks (4 / 2 / 1 / 0,5 s ; lot 14 : 40 / 5)
        Gt.equal(80, MachineKind.FURNACE.ticks(CableTier.BASIC), "four electrique MKI : 80 ticks par cuisson");
        Gt.equal(10, MachineKind.FURNACE.ticks(CableTier.ULTIMATE), "four electrique MKIV : 10 ticks par cuisson");
        BlockPos rel = new BlockPos(2, 2, 2);
        BlockState st = PiMachineKit.MACHINES.get(MachineKind.FURNACE).get(CableTier.BASIC).getBlock().defaultBlockState();
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        }
        h.setBlock(rel, st);
        Gt.check(h.getBlockEntity(rel) instanceof AtelierTile, "pas de four electrique MKI");
        AtelierTile f = (AtelierTile) h.getBlockEntity(rel);
        f.getEnergy().setStored(f.getEnergy().getCapacity());
        f.itemInputs().get(0).setStack(new ItemStack(Items.BEEF, 3));
        //diagnostic : tick du premier boeuf cuit
        long[] first = {-1}, start = {h.getTick()};
        h.onEachTick(() -> {
            if (first[0] < 0 && PatchP105Lot14Tests.cooked(f) > 0) {
                first[0] = h.getTick();
            }
        });
        h.startSequence()
              .thenExecuteAfter(74, () -> Gt.equal(0, cooked(f), "rien de cuit avant 80 ticks"))
              .thenWaitUntil(() -> Gt.check(cooked(f) > 0, "pas encore de boeuf cuit ; cases " + f.guiSlots().stream()
                    .map(sl -> sl.getStack().toString()).toList() + ", energie " + f.getEnergy().getStored()))
              .thenExecute(() -> Gt.check(first[0] - start[0] <= 86, "boeuf cuit en 80 ticks : premier cuit " + (first[0] - start[0]) + " ticks apres le depart, energie "
                    + f.getEnergy().getStored() + " / " + f.getEnergy().getCapacity()))
              .thenExecute(s::done);
    }

    static int cooked(AtelierTile m) {
        int n = 0;
        for (var slot : m.itemOutputs()) {
            if (slot.getStack().is(Items.COOKED_BEEF)) {
                n += slot.getCount();
            }
        }
        return n;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_14_carte", timeoutTicks = 20)
    public static void p105_14_climat_de_la_carte(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos pos = h.absolutePos(new BlockPos(2, 3, 2));
        float[] c = EnvironmentSync.climate(h.getLevel(), pos);
        Gt.check(c[0] == Climate.temperature(h.getLevel(), pos), "carte : temperature = celle des cultures");
        Gt.check(c[1] == Climate.humidity(h.getLevel(), pos), "carte : humidite = celle des cultures");
        Gt.check(Climate.humidityZone(c[1]) >= 0 && Climate.humidityZone(c[1]) <= 4 && Climate.zone(c[0]) >= 0, "carte : libelles lisibles");
        s.done();
    }
}
