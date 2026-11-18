package fr.pioneers.gametest;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.gas.IGasHandler;
import fr.pioneers.core.Action;
import fr.pioneers.core.RelativeSide;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import fr.pioneers.tech.kinetics.rope.LinkType;
import fr.pioneers.core.network.PacketWarehouse;
import fr.pioneers.tech.kinetics.PiKinetics;
import fr.pioneers.logistics.transport.PiTransport;
import fr.pioneers.logistics.transport.CableTier;
import fr.pioneers.logistics.transport.buffer.BufferTile;
import fr.pioneers.logistics.transport.buffer.BufferType;
import fr.pioneers.logistics.transport.trash.PiTrash;
import fr.pioneers.logistics.transport.trash.TrashCanMenu;
import fr.pioneers.logistics.warehouse.CounterBlock;
import fr.pioneers.logistics.warehouse.CounterCart;
import fr.pioneers.logistics.warehouse.CounterTile;
import fr.pioneers.logistics.warehouse.CounterUnit;
import fr.pioneers.logistics.warehouse.CrateHatchTile;
import fr.pioneers.logistics.warehouse.CrateLayout;
import fr.pioneers.logistics.warehouse.CrateStorage;
import fr.pioneers.logistics.warehouse.PiWarehouse;
import fr.pioneers.logistics.warehouse.RecipeAsk;
import fr.pioneers.logistics.warehouse.StockKey;
import fr.pioneers.logistics.warehouse.WarehouseRopes;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/**
 * Patch 1.0.5-beta, lot 22 (retours de jeu 2e serie, 2/6 : logistique, GameTests) :
 * <ul>
 *     <li>POUBELLE (retour 1) : par chacune de ses faces (et sans face), objets, fluide, gaz et energie sont acceptes et detruits (rien n'est
 *     garde) ; la donnee est refusee (aucune capacite) ; lot 27 (decision de Guillaume) : la chaleur et l'air comprime aussi (aucune capacite ;
 *     test complet : {@code PatchP105Lot27Tests}) ; un entonnoir, des buffers de fluide, de gaz et d'energie qui ejectent dedans se vident ;
 *     la case « Vider » de l'interface rend le seau vide ;</li>
 *     <li>CAISSE A DEBORDEMENT (retour 2) : un objet plein deborde sur trois cases de type, un autre objet prend la suivante ; le port rend tout,
 *     les cases se liberent ; aller-retour de sauvegarde ;</li>
 *     <li>COMPTOIR (retour 24) : la recette demandee est gardee au panier avec son produit ; le nombre de fabrications change la part du
 *     panier ; 0 retire la recette ; « Commander » la fait partir ; paquet de la molette borne.</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot22Tests {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000001022");

    private PatchP105Lot22Tests() {
    }

    // ================================================================== 1. poubelle : toutes les ressources par toutes les faces, jamais la donnee
    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_22_trash_caps", timeoutTicks = 20)
    public static void p105_22_poubelle_toutes_ressources(GameTestHelper h) {
        BlockPos rel = new BlockPos(4, 1, 4);
        h.setBlock(rel, PiTrash.TRASH_CAN.getBlock());
        BlockEntity be = h.getBlockEntity(rel);
        Gt.check(be != null, "poubelle sans tuile");
        Direction[] faces = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};
        GasStack steam = GasStack.of(PioneersAPI.gas(new ResourceLocation("pioneers", "steam")), 4000);
        for (Direction d : faces) {
            String f = d == null ? "sans face" : d.getName();
            IItemHandler items = be.getCapability(ForgeCapabilities.ITEM_HANDLER, d).resolve().orElse(null);
            Gt.check(items != null, "objets refuses : " + f);
            Gt.check(items.insertItem(0, new ItemStack(Items.IRON_INGOT, 64), true).isEmpty(), "simulation d'objets refusee : " + f);
            Gt.check(items.insertItem(0, new ItemStack(Items.IRON_INGOT, 64), false).isEmpty(), "objets refuses : " + f);
            Gt.check(items.getStackInSlot(0).isEmpty() && items.extractItem(0, 64, false).isEmpty(), "objets gardes : " + f);
            IFluidHandler fluids = be.getCapability(ForgeCapabilities.FLUID_HANDLER, d).resolve().orElse(null);
            Gt.check(fluids != null, "fluides refuses : " + f);
            Gt.equal(4000, fluids.fill(new FluidStack(Fluids.WATER, 4000), IFluidHandler.FluidAction.EXECUTE), "eau acceptee : " + f);
            Gt.equal(1000, fluids.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE), "lave acceptee : " + f);
            Gt.check(fluids.getFluidInTank(0).isEmpty() && fluids.drain(1000, IFluidHandler.FluidAction.EXECUTE).isEmpty(), "fluide garde : " + f);
            IGasHandler gases = be.getCapability(PioneersAPI.GAS_HANDLER, d).resolve().orElse(null);
            Gt.check(gases != null, "gaz refuses : " + f);
            Gt.check(!steam.isEmpty() && gases.insertGas(steam, Action.EXECUTE).isEmpty(), "vapeur refusee : " + f);
            Gt.check(gases.getGasInTank(0).isEmpty() && gases.extractGas(1000, Action.EXECUTE).isEmpty(), "gaz garde : " + f);
            IEnergyStorage energy = be.getCapability(ForgeCapabilities.ENERGY, d).resolve().orElse(null);
            Gt.check(energy != null && energy.canReceive(), "energie refusee : " + f);
            Gt.equal(100_000, energy.receiveEnergy(100_000, false), "energie acceptee : " + f);
            Gt.check(energy.getEnergyStored() == 0 && energy.extractEnergy(1000, false) == 0, "energie gardee : " + f);
            //1.0.5-beta (lot 27) : ni chaleur ni air comprime
            Gt.check(!be.getCapability(PioneersAPI.HEAT_HANDLER, d).isPresent(), "la poubelle accepte la chaleur : " + f);
            Gt.check(!be.getCapability(PioneersAPI.PRESSURE_HANDLER, d).isPresent(), "la poubelle accepte l'air : " + f);
            Gt.check(!be.getCapability(PioneersAPI.DATA_HANDLER, d).isPresent(), "la poubelle accepte la donnee : " + f);
        }
        //Interface : « Vider » rend le seau vide (l'eau est detruite)
        Gt.check(TrashCanMenu.drainable(new ItemStack(Items.WATER_BUCKET)) && !TrashCanMenu.drainable(new ItemStack(Items.BUCKET)), "seau plein / vide");
        Gt.check(TrashCanMenu.drain(new ItemStack(Items.WATER_BUCKET)).is(Items.BUCKET), "le seau d'eau n'est pas rendu vide");
        Gt.check(TrashCanMenu.drain(new ItemStack(Items.LAVA_BUCKET)).is(Items.BUCKET), "le seau de lave n'est pas rendu vide");
        h.succeed();
    }

    /** Buffer MKI de cette famille, toutes faces en sortie et ejection allumee, pose en {@code rel}. */
    private static BufferTile ejecting(GameTestHelper h, BlockPos rel, BufferType type, SideType side) {
        h.setBlock(rel, PiTransport.buffer(type, CableTier.BASIC).getBlock());
        BufferTile b = (BufferTile) h.getBlockEntity(rel);
        SideConfig sides = b.unit().sides();
        for (RelativeSide r : RelativeSide.values()) {
            sides.set(side, r, SideMode.OUTPUT);
        }
        sides.setEjectChannel(sides.types().indexOf(side), true);
        return b;
    }

    // ================================================================== 1 bis. poubelle : un entonnoir et des buffers qui ejectent dedans se vident
    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_22_trash_feed", timeoutTicks = 400)
    public static void p105_22_poubelle_videe_par_ses_voisins(GameTestHelper h) {
        //Entonnoir vanilla (bec en bas) sur la poubelle : 32 pierres
        h.setBlock(new BlockPos(2, 1, 2), PiTrash.TRASH_CAN.getBlock());
        h.setBlock(new BlockPos(2, 2, 2), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        Container hopper = (Container) h.getBlockEntity(new BlockPos(2, 2, 2));
        //1.0.6 : un entonnoir vanilla verse un objet tous les 8 ticks : 64 pierres demandaient 512 ticks, plus que les 400 du test
        hopper.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
        //Buffers de fluide, de gaz, d'energie qui ejectent dans une poubelle commune
        BlockPos trash = new BlockPos(6, 1, 6);
        h.setBlock(trash, PiTrash.TRASH_CAN.getBlock());
        BufferTile fluid = ejecting(h, trash.west(), BufferType.FLUID, SideType.FLUIDS);
        BufferTile gas = ejecting(h, trash.north(), BufferType.GAS, SideType.GASES);
        BufferTile energy = ejecting(h, trash.east(), BufferType.ENERGY, SideType.ENERGY);
        int filled = fluid.getCapability(ForgeCapabilities.FLUID_HANDLER, null).map(f -> f.fill(new FluidStack(Fluids.WATER, 2000),
              IFluidHandler.FluidAction.EXECUTE)).orElse(0);
        Gt.equal(2000, filled, "buffer de fluide rempli");
        GasStack steam = GasStack.of(PioneersAPI.gas(new ResourceLocation("pioneers", "steam")), 2000);
        Gt.check(gas.getCapability(PioneersAPI.GAS_HANDLER, null).map(g -> g.insertGas(steam, Action.EXECUTE).isEmpty()).orElse(false), "buffer de gaz rempli");
        int charged = energy.getCapability(ForgeCapabilities.ENERGY, null).map(e -> e.receiveEnergy(20_000, false)).orElse(0);
        Gt.equal(20_000, charged, "buffer d'energie rempli");
        //Chaleur et air : plus de puits depuis le lot 27 (buffers de chaleur et d'air contre une poubelle : PatchP105Lot27Tests)
        h.succeedWhen(() -> {
            Gt.check(hopper.getItem(0).isEmpty(), "l'entonnoir n'a pas tout verse");
            Gt.check(fluid.unit().fluidTank().getFluidAmount() == 0, "le buffer de fluide n'est pas vide");
            Gt.check(gas.unit().gasTank().getStored() == 0, "le buffer de gaz n'est pas vide");
            Gt.check(energy.getCapability(ForgeCapabilities.ENERGY, null).map(IEnergyStorage::getEnergyStored).orElse(-1) == 0, "le buffer d'energie n'est pas vide");
        });
    }

    // ================================================================== 2. caisse a debordement
    private static CrateHatchTile crate(GameTestHelper h, BlockPos rel) {
        BlockPos origin = h.absolutePos(rel);
        BlockPos hatch = null;
        for (int w = 0; w < 3; w++) {
            for (int v = 0; v < 3; v++) {
                for (int u = 0; u < 3; u++) {
                    CrateLayout.Kind k = CrateLayout.kind(u, v, w, 3);
                    BlockPos p = CrateLayout.at(origin, Direction.Axis.Z, u, v, w);
                    if (u == 1 && v == 2 && w == 1) {
                        k = CrateLayout.Kind.HATCH;
                        hatch = p;
                    }
                    //Un port a la place du panneau du flanc ouest (u 0, v 1, w 1)
                    Block b = u == 0 && v == 1 && w == 1 ? PiWarehouse.CRATE_PORT.getBlock() : CrateLayout.block(k);
                    h.getLevel().setBlock(p, b.defaultBlockState(), 3);
                }
            }
        }
        CrateHatchTile t = (CrateHatchTile) h.getLevel().getBlockEntity(hatch);
        Gt.check(t.house().checkNow(), "caisse non formee : " + t.house().error());
        return t;
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p105_22_overflow", timeoutTicks = 40)
    public static void p105_22_caisse_deborde_puis_rend(GameTestHelper h) {
        CrateHatchTile hatch = crate(h, new BlockPos(2, 1, 2));
        CrateStorage s = hatch.storage();
        ItemStack iron = new ItemStack(Items.IRON_INGOT), diamond = new ItemStack(Items.DIAMOND);
        long cap = s.capacity(iron);
        Gt.equal(32L * 64, cap, "contenance d'une case de type (caisse courte)");
        //Le fer remplit sa case puis deborde sur deux cases libres
        Gt.equal(cap * 2 + 100, s.insert(iron, cap * 2 + 100, false), "fer refuse alors que des cases sont libres");
        Gt.equal(3, s.used(), "cases occupees par le fer");
        Gt.equal(1, s.types(), "un seul type");
        Gt.equal(3, s.slotsOf(iron), "cases du fer");
        Gt.equal(cap - 100 + (long) (s.maxTypes() - 3) * cap, s.room(iron), "place du fer : sa case entamee + les cases libres");
        Gt.equal(40, s.insert(diamond, 40, false), "diamants");
        Gt.equal(4, s.used(), "le diamant prend la case suivante");
        //Par le port : un entonnoir ou un cable rend tout, les cases se liberent
        BlockPos port = CrateLayout.at(h.absolutePos(new BlockPos(2, 1, 2)), Direction.Axis.Z, 0, 1, 1);
        IItemHandler items = h.getLevel().getBlockEntity(port).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.WEST).resolve().orElse(null);
        Gt.check(items != null, "le port n'expose pas la caisse");
        long back = 0;
        for (int guard = 0; guard < 1000 && s.total() > 0; guard++) {
            for (int i = 0; i < items.getSlots() - 1; i++) {
                back += items.extractItem(i, 64, false).getCount();
            }
        }
        Gt.equal(cap * 2 + 140, back, "tout n'est pas rendu par le port");
        Gt.equal(0, s.used(), "cases encore occupees");
        //Remise du fer par le port (case libre) : il redeborde ; aller-retour de sauvegarde
        long put = 0;
        for (int i = 0; i < 70; i++) {
            ItemStack rest = items.insertItem(items.getSlots() - 1, new ItemStack(Items.IRON_INGOT, 64), false);
            put += 64 - rest.getCount();
        }
        Gt.equal(70L * 64, put, "le port refuse le debordement");
        Gt.equal(3, s.used(), "cases du fer apres le port");
        CrateStorage again = new CrateStorage();
        again.limits(hatch.length());
        again.load(s.save());
        Gt.equal(70L * 64, again.count(iron), "fer relu");
        Gt.equal(3, again.used(), "cases relues");
        Gt.check(s.comparator() > 0 && s.fill() > 0, "comparateur");
        h.succeed();
    }

    // ================================================================== 24. comptoir : recettes demandees, nombre de fabrications
    private static BlockPos pulley(GameTestHelper h, BlockPos abs) {
        h.getLevel().setBlock(abs, PiKinetics.SMALL_PULLEY.getBlock().defaultBlockState(), 3);
        return abs;
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p105_22_crafts", timeoutTicks = 40)
    public static void p105_22_comptoir_fabrications(GameTestHelper h) {
        CrateHatchTile hatch = crate(h, new BlockPos(2, 1, 2));
        BlockPos w1 = pulley(h, hatch.getBlockPos().above());
        BlockPos c = h.absolutePos(new BlockPos(9, 3, 3));
        h.getLevel().setBlock(c.below(2), Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(c.below(), Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(c, PiWarehouse.COUNTER.getBlock().defaultBlockState().setValue(CounterBlock.FACING, Direction.WEST), 3);
        pulley(h, c.above());
        CounterTile counter = (CounterTile) h.getLevel().getBlockEntity(c);
        Gt.check(counter.station() != null, "comptoir sans poulie");
        Gt.check(WarehouseRopes.link(h.getLevel(), w1, counter.station().getBlockPos(), LinkType.FIBER_ROPE), "corde refusee");
        CounterUnit unit = counter.counter();
        hatch.storage().insert(new ItemStack(Items.OAK_PLANKS), 40, false);
        hatch.storage().insert(new ItemStack(Items.IRON_INGOT), 20, false);
        Map<StockKey, ItemStack> protos = new HashMap<>();
        for (ItemStack s : List.of(new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.IRON_INGOT))) {
            protos.put(StockKey.of(s), s);
        }
        StockKey oak = StockKey.of(new ItemStack(Items.OAK_PLANKS)), iron = StockKey.of(new ItemStack(Items.IRON_INGOT));
        //Piston (simplifie) : 3 planches + 1 lingot par fabrication, 2 fabrications, produit 1 piston
        List<RecipeAsk.Line<StockKey>> lines = List.of(new RecipeAsk.Line<>(List.of(oak), 3), new RecipeAsk.Line<>(List.of(iron), 1));
        unit.recipe(PLAYER, lines, protos, 2, false, new ItemStack(Items.PISTON), 1);
        CounterCart cart = unit.cart(PLAYER);
        Gt.equal(1, cart.crafts.size(), "recette non gardee au panier");
        CounterCart.Craft k = cart.crafts.get(0);
        Gt.check(k.result.is(Items.PISTON) && k.times == 2, "produit ou fabrications de la recette");
        Gt.equal(6, cart.count(new ItemStack(Items.OAK_PLANKS)), "planches au panier");
        //Molette : 5 fabrications -> 15 planches, 5 lingots ; une ligne ajoutee a la main reste
        unit.cartSet(PLAYER, new ItemStack(Items.IRON_INGOT), cart.count(new ItemStack(Items.IRON_INGOT)) + 3);
        unit.craftTimes(PLAYER, k.id, 5);
        Gt.equal(15, cart.count(new ItemStack(Items.OAK_PLANKS)), "planches apres la molette");
        Gt.equal(8, cart.count(new ItemStack(Items.IRON_INGOT)), "lingots apres la molette (5 + 3 a la main)");
        Gt.equal(5, cart.craft(k.id).times, "fabrications");
        //Plus que le stock : ce qui manque n'est pas ajoute, la recette le dit
        unit.craftTimes(PLAYER, k.id, 20);
        Gt.equal(40, cart.count(new ItemStack(Items.OAK_PLANKS)), "planches bornees par le stock");
        Gt.equal(20, cart.count(new ItemStack(Items.IRON_INGOT)), "lingots bornes par le stock (17 + 3 a la main)");
        Gt.check(cart.craft(k.id).missing == 23, "objets manquants (20 planches, 3 lingots) : " + cart.craft(k.id).missing);
        //Sauvegarde du panier : la recette revient
        CounterCart back = CounterCart.load(cart.save());
        Gt.check(back.craft(k.id) != null && back.craft(k.id).times == 20 && back.nextCraft > k.id, "recette relue");
        //0 : la recette et sa part sortent du panier (la ligne ajoutee a la main reste)
        unit.craftTimes(PLAYER, k.id, 0);
        Gt.check(cart.crafts.isEmpty(), "recette encore la");
        Gt.equal(0, cart.count(new ItemStack(Items.OAK_PLANKS)), "planches encore au panier");
        Gt.equal(3, cart.count(new ItemStack(Items.IRON_INGOT)), "la ligne ajoutee a la main a disparu");
        //Paquet de la molette : borne (numero, 0..64) et decodage
        Gt.check(!new PacketWarehouse.CraftEdit(c, k.id, RecipeAsk.MAX_TIMES + 1, 1).valid(), "fabrications hors bornes acceptees");
        Gt.check(!new PacketWarehouse.CraftEdit(c, 0, 3, 1).valid() && !new PacketWarehouse.CraftEdit(c, 2, -1, 1).valid(), "numero ou nombre negatif accepte");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new PacketWarehouse.CraftEdit(c, 7, 12, 99).encode(buf);
        PacketWarehouse.CraftEdit read = PacketWarehouse.CraftEdit.decode(buf);
        Gt.check(read.id() == 7 && read.times() == 12 && read.seq() == 99 && read.valid(), "paquet relu");
        //« Commander » : la recette part avec ses ingredients
        unit.cartClear(PLAYER);
        unit.recipe(PLAYER, lines, protos, 1, true, new ItemStack(Items.PISTON), 1);
        Gt.check(cart.crafts.isEmpty() && cart.lines.isEmpty(), "la recette reste au panier apres « Commander »");
        h.succeed();
    }
}
