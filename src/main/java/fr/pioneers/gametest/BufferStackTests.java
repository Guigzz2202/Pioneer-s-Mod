package fr.pioneers.gametest;

import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.registries.PiTransport;
import fr.pioneers.transport.CableTier;
import fr.pioneers.transport.buffer.BufferTile;
import fr.pioneers.transport.buffer.BufferType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/**
 * 1.12.3 (lot F3) : buffers d'objets a plusieurs piles par case (x2 MKI, x4 MKII, x6 MKIII, x8 MKIV).
 * <ul>
 *     <li>MKIV : 512 pierres dans une case par la capacite Forge, la 513e refusee ; un objet non empilable reste a 1 ;</li>
 *     <li>sauvegarde : {@code Count} vanilla borne a une pile, vraie quantite dans {@code CountL} ; relue a l'identique dans un buffer
 *     repose ;</li>
 *     <li>extraction : une pile normale (64) au plus par appel ;</li>
 *     <li>MKI : 128 par case.</li>
 * </ul>
 * Ces tests ne tournent qu'avec les GameTests de Forge (runGameTestServer).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class BufferStackTests {

    private static final BlockPos AT = new BlockPos(2, 2, 2);

    private BufferStackTests() {
    }

    private static BufferTile place(GameTestHelper h, CableTier tier) {
        h.setBlock(AT, PiTransport.BUFFERS.get(BufferType.ITEM).get(tier).getBlock().defaultBlockState());
        Gt.check(h.getBlockEntity(AT) instanceof BufferTile, "pas de tuile de buffer");
        return (BufferTile) h.getBlockEntity(AT);
    }

    private static IItemHandler items(BufferTile tile) {
        return tile.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve().orElseThrow();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_f3_buffers", timeoutTicks = 40)
    public static void buffer_mk4_512_sauve_relu_extrait(GameTestHelper h) {
        Gt.begin(h);
        BufferTile tile = place(h, CableTier.ULTIMATE);
        IItemHandler handler = items(tile);
        Gt.equal(512, handler.getSlotLimit(0), "limite d'une case MKIV");
        for (int i = 0; i < 8; i++) {
            ItemStack rest = handler.insertItem(0, new ItemStack(Items.COBBLESTONE, 64), false);
            Gt.check(rest.isEmpty(), "pile " + (i + 1) + " refusee");
        }
        Gt.equal(512, handler.getStackInSlot(0).getCount(), "quantite apres 8 piles");
        Gt.equal(1, handler.insertItem(0, new ItemStack(Items.COBBLESTONE), false).getCount(), "la 513e pierre doit etre refusee");
        Gt.check(handler.insertItem(1, new ItemStack(Items.DIAMOND_SWORD, 1), false).isEmpty(), "une epee entre");
        Gt.equal(1, handler.insertItem(1, new ItemStack(Items.DIAMOND_SWORD, 1), false).getCount(), "une seule epee par case");

        //Sauvegarde : Count borne, CountL = 512
        CompoundTag saved = tile.saveWithoutMetadata();
        ListTag list = saved.getCompound(BufferTile.NBT_CONTENTS).getList("items", Tag.TAG_COMPOUND);
        CompoundTag first = list.getCompound(0);
        Gt.equal(512, first.getInt(ItemSlot.NBT_BIG_COUNT), "CountL sauve");
        Gt.check(first.getByte("Count") > 0, "Count vanilla positif (lecteurs sans CountL)");

        //Relecture dans un buffer repose
        h.setBlock(AT, Blocks.AIR.defaultBlockState());
        BufferTile again = place(h, CableTier.ULTIMATE);
        again.load(saved);
        IItemHandler relu = items(again);
        Gt.equal(512, relu.getStackInSlot(0).getCount(), "quantite relue");
        Gt.check(relu.getStackInSlot(0).is(Items.COBBLESTONE), "objet relu");

        //Extraction : une pile normale au plus par appel
        ItemStack out = relu.extractItem(0, 512, false);
        Gt.equal(64, out.getCount(), "extraction bornee a une pile");
        Gt.equal(448, relu.getStackInSlot(0).getCount(), "reste apres extraction");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_f3_buffers_mk1", timeoutTicks = 40)
    public static void buffer_mk1_128_par_case(GameTestHelper h) {
        Gt.begin(h);
        IItemHandler handler = items(place(h, CableTier.BASIC));
        Gt.equal(128, handler.getSlotLimit(0), "limite d'une case MKI");
        Gt.check(handler.insertItem(0, new ItemStack(Items.COBBLESTONE, 64), false).isEmpty(), "premiere pile refusee");
        Gt.check(handler.insertItem(0, new ItemStack(Items.COBBLESTONE, 64), false).isEmpty(), "deuxieme pile refusee");
        Gt.equal(64, handler.insertItem(0, new ItemStack(Items.COBBLESTONE, 64), false).getCount(), "troisieme pile acceptee a tort");
        //Objet empilable par 16 : 32 par case au MKI
        handler.insertItem(1, new ItemStack(Items.EGG, 16), false);
        Gt.equal(16, handler.insertItem(1, new ItemStack(Items.EGG, 32), false).getCount(), "oeufs au-dela de 2 x 16");
        h.succeed();
    }

    /**
     * 1.0.3-beta (correctif, Guillaume : « les buffers creatifs doivent avoir une capacite de plusieurs milliards de debit ») : contenances
     * des buffers creatifs, et un fluide tire d'un coup bien au-dela du debit d'un cable MKIV (aucune limite propre).
     */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_creative_buffers", timeoutTicks = 40)
    public static void buffer_creatif_milliards(GameTestHelper h) {
        Gt.begin(h);
        Gt.equal((long) Integer.MAX_VALUE, BufferType.FLUID.capacity(CableTier.ULTIMATE, true), "fluides : 2,1 milliards de mB");
        Gt.check(BufferType.GAS.capacity(CableTier.ULTIMATE, true) >= 1_000_000_000L, "gaz : des milliards");
        Gt.check(BufferType.ENERGY.capacity(CableTier.ULTIMATE, true) >= 1_000_000_000L, "energie : des milliards");
        h.setBlock(AT, PiTransport.CREATIVE_BUFFERS.get(BufferType.FLUID).getBlock().defaultBlockState());
        BufferTile tile = (BufferTile) h.getBlockEntity(AT);
        Gt.equal((long) Integer.MAX_VALUE, tile.unit().capacity(), "contenance de la tuile");
        IFluidHandler tank = tile.getCapability(ForgeCapabilities.FLUID_HANDLER, null).resolve().orElseThrow();
        tank.fill(new FluidStack(Fluids.WATER, 1_000), IFluidHandler.FluidAction.EXECUTE);
        h.runAfterDelay(2, () -> {
            Gt.equal((long) Integer.MAX_VALUE, (long) tank.getFluidInTank(0).getAmount(), "rempli a ras bord");
            Gt.equal(500_000_000, tank.drain(500_000_000, IFluidHandler.FluidAction.EXECUTE).getAmount(), "500 millions tires d'un coup");
            h.succeed();
        });
    }
}
