package fr.pioneers.gametest;

import fr.pioneers.bronze.machines.SteamMachine;
import fr.pioneers.bronze.machines.SteamProcessTile;
import fr.pioneers.bronze.steam.SteamVolume;
import fr.pioneers.clan.Clan;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.clan.faction.Factions;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.RelativeSide;
import fr.pioneers.core.energy.EnergyPush;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import fr.pioneers.industry.IndustryMachine;
import fr.pioneers.industry.atelier.AtelierTile;
import fr.pioneers.kinetics.AxisKineticBlock;
import fr.pioneers.kinetics.KineticNetworks;
import fr.pioneers.mechanics.PiMechanics;
import fr.pioneers.mechanics.WoodenHopperBlock;
import fr.pioneers.registries.PiBronze;
import fr.pioneers.registries.PiKinetics;
import fr.pioneers.registries.PiTransport;
import fr.pioneers.transport.CableTier;
import fr.pioneers.transport.logistic.CableLevel;
import fr.pioneers.transport.logistic.LogisticCableBlock;
import fr.pioneers.transport.logistic.LogisticNetworks;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.9 (MAJ RP, GameTests) : bloc 4, transferts du mod a une frontiere de claim, par famille (liste : {@code tools/check_transfers.py}).
 * Chaque test pose deux vrais blocs de part et d'autre d'une frontiere de chunk : chunk de gauche au clan A, chunk de droite au clan B
 * (neutres) : rien ne passe ; puis A et B signent une alliance : le transfert reprend (reseaux refaits). Rail et robots : le controle
 * frontalier qu'ils appellent, avec les relations de faction (les clans d'une faction s'ouvrent leurs rails, pas aux autres).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class ClanTransferTests {

    private ClanTransferTests() {
    }

    /** Frontiere du test : clans A et B (neutres), chunk de x a A, chunk de x + 1 a B. */
    static final class Border {

        final Gt.Scope s;
        final GameTestHelper h;
        final int x;
        final ServerPlayer pa, pb;
        final Clan a, b;

        Border(GameTestHelper h, String tag) {
            this.h = h;
            s = Gt.begin(h);
            x = Gt.borderX(h, Gt.BORDER_SIZE);
            pa = s.player(tag + "a", "Gauche", new BlockPos(x, 2, 2));
            pb = s.player(tag + "b", "Droite", new BlockPos(x + 1, 2, 2));
            String suffix = Integer.toHexString(h.absolutePos(BlockPos.ZERO).hashCode() & 0xfff);
            a = s.clan("GtA" + tag + suffix, pa);
            b = s.clan("GtB" + tag + suffix, pb);
            s.claim(a, h.absolutePos(left(2)));
            s.claim(b, h.absolutePos(right(2)));
            Gt.check(!ClanApi.canTransfer(s.level, h.absolutePos(left(2)), h.absolutePos(right(2))), "la frontiere A / B devrait etre fermee");
        }

        BlockPos left(int y) {
            return new BlockPos(x, y, 10);
        }

        BlockPos right(int y) {
            return new BlockPos(x + 1, y, 10);
        }

        /** Alliance A / B : la frontiere s'ouvre. */
        void ally() {
            s.ally(a, pa, b, pb);
            Gt.check(ClanApi.canTransfer(s.level, h.absolutePos(left(2)), h.absolutePos(right(2))), "la frontiere entre allies devrait etre ouverte");
        }
    }

    static int items(GameTestHelper h, BlockPos rel) {
        return h.getBlockEntity(rel) instanceof Container c ? c.countItem(Items.COBBLESTONE) : -1;
    }

    // ================================================================== entonnoir du mod
    /** Entonnoir en bois : le bec ne verse rien dans le coffre d'un clan voisin neutre ; entre allies, si. */
    @GameTest(template = Gt.BORDER, batch = "pioneers_transfert_entonnoir", timeoutTicks = 200)
    public static void transfert_entonnoir(GameTestHelper h) {
        Border b = new Border(h, "hop");
        BlockPos hop = b.left(2), source = hop.above(), target = b.right(2);
        h.setBlock(source, Blocks.CHEST);
        ((Container) h.getBlockEntity(source)).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        h.setBlock(hop, PiMechanics.WOODEN_HOPPER.getBlock().defaultBlockState().setValue(WoodenHopperBlock.FACING, Direction.EAST));
        h.setBlock(target, Blocks.CHEST);
        h.runAfterDelay(40, () -> {
            Gt.equal(0, items(h, target), "objets passes chez le voisin neutre");
            Gt.equal(16, items(h, source), "objets retires de la source");
            b.ally();
            h.runAfterDelay(40, () -> {
                Gt.check(items(h, target) > 0, "rien n'est passe entre allies");
                Gt.equal(16, items(h, source) + items(h, target), "objets crees ou perdus");
                b.s.done();
            });
        });
    }

    // ================================================================== ejection d'une machine (faces de sortie)
    static AtelierTile recycler(GameTestHelper h, BlockPos rel) {
        Block block = BuiltInRegistries.BLOCK.get(new ResourceLocation("pioneers", IndustryMachine.RECYCLER.atelierId(CableTier.BASIC)));
        Gt.check(block != Blocks.AIR, "recycleur MKI absent");
        BlockState st = block.defaultBlockState();
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        }
        h.setBlock(rel, st);
        Gt.check(h.getBlockEntity(rel) instanceof AtelierTile, "le recycleur n'a pas de tuile d'atelier");
        return (AtelierTile) h.getBlockEntity(rel);
    }

    /** Ejection automatique d'une machine (toutes faces de sortie) : rien ne sort chez le voisin neutre ; entre allies, si. */
    @GameTest(template = Gt.BORDER, batch = "pioneers_transfert_ejection", timeoutTicks = 200)
    public static void transfert_ejection_machine(GameTestHelper h) {
        Border b = new Border(h, "eje");
        AtelierTile m = recycler(h, b.left(2));
        SideConfig sides = m.getSideConfig();
        for (RelativeSide r : RelativeSide.values()) {
            sides.set(SideType.ITEMS, r, SideMode.OUTPUT);
        }
        sides.setEjectChannel(sides.types().indexOf(SideType.ITEMS), true);
        Gt.check(sides.isEject(SideType.ITEMS), "ejection des objets non activee");
        ItemStack rest = m.itemOutputs().get(0).insert(new ItemStack(Items.COBBLESTONE, 16), Action.EXECUTE, Automation.INTERNAL);
        Gt.check(rest.isEmpty(), "case de sortie pleine");
        BlockPos target = b.right(2);
        h.setBlock(target, Blocks.CHEST);
        h.runAfterDelay(40, () -> {
            Gt.equal(0, items(h, target), "objets ejectes chez le voisin neutre");
            b.ally();
            h.runAfterDelay(40, () -> {
                Gt.check(items(h, target) > 0, "rien n'est sorti entre allies");
                Gt.equal(16, m.itemOutputs().get(0).getCount() + items(h, target), "objets crees ou perdus");
                b.s.done();
            });
        });
    }

    // ================================================================== energie
    /** Energie poussee aux voisins (producteurs du mod) : rien chez le voisin neutre ; entre allies, si. */
    @GameTest(template = Gt.BORDER, batch = "pioneers_transfert_energie", timeoutTicks = 60)
    public static void transfert_energie(GameTestHelper h) {
        Border b = new Border(h, "fe");
        AtelierTile target = recycler(h, b.right(2));
        target.getEnergy().setStored(0);
        BlockPos from = h.absolutePos(b.left(2));
        long given = EnergyPush.toNeighbours(h.getLevel(), from, 10_000, (d, be) -> true, v -> v);
        Gt.equal(0, given, "energie passee chez le voisin neutre");
        Gt.equal(0, target.getEnergy().getStored(), "energie recue du voisin neutre");
        b.ally();
        given = EnergyPush.toNeighbours(h.getLevel(), from, 10_000, (d, be) -> true, v -> v);
        Gt.check(given > 0 && target.getEnergy().getStored() == given, "energie non passee entre allies : " + given);
        b.s.done();
    }

    // ================================================================== vapeur
    /** Vapeur echangee (remplissage des reserves, 1.0.2-beta lot 3) entre deux machines a vapeur voisines : rien chez le voisin neutre ; entre allies, si. */
    @GameTest(template = Gt.BORDER, batch = "pioneers_transfert_vapeur", timeoutTicks = 200)
    public static void transfert_vapeur(GameTestHelper h) {
        Border b = new Border(h, "stm");
        Block block = PiBronze.STEAM_MACHINES.get(SteamMachine.COMPRESSOR).getBlock();
        h.setBlock(b.left(2), block);
        h.setBlock(b.right(2), block);
        SteamProcessTile src = (SteamProcessTile) h.getBlockEntity(b.left(2)), dst = (SteamProcessTile) h.getBlockEntity(b.right(2));
        SteamVolume vs = src.steam(Direction.EAST), vd = dst.steam(Direction.WEST);
        Gt.check(vs != null && vd != null, "faces de vapeur est / ouest fermees");
        vs.add(vs.capacity() * 0.75, 0);
        Gt.check(vs.fill() > 0.5, "vapeur non ajoutee");
        h.runAfterDelay(30, () -> {
            Gt.check(vd.steam() <= 1e-6, "vapeur passee chez le voisin neutre : " + vd.steam());
            b.ally();
            h.runAfterDelay(30, () -> {
                Gt.check(vd.steam() > 1e-3, "aucune vapeur passee entre allies");
                b.s.done();
            });
        });
    }

    // ================================================================== cinetique, cables, donnees (reseaux)
    @Nullable
    static Object kinetic(GameTestHelper h, BlockPos rel) {
        return KineticNetworks.network(h.getLevel(), h.absolutePos(rel));
    }

    @Nullable
    static Object cable(GameTestHelper h, BlockPos rel) {
        return LogisticNetworks.network(h.getLevel(), h.absolutePos(rel));
    }

    /** 1.11 : le reseau numerique est le reseau logistique MKI ou mieux. */
    @Nullable
    static Object data(GameTestHelper h, BlockPos rel) {
        return LogisticNetworks.digital(h.getLevel(), h.absolutePos(rel));
    }

    /**
     * 1.11 : un reseau logistique n'existe que s'il a une fiche : un coffre sous chaque cable, puis les cables poses avec leurs branchements
     * (meme regle de frontiere que les autres reseaux).
     */
    static void logisticNetwork(GameTestHelper h, Border b, LogisticCableBlock block, NetworkOf of, String what) {
        for (BlockPos rel : new BlockPos[]{b.left(2), b.right(2)}) {
            h.setBlock(rel.below(), Blocks.CHEST);
        }
        for (BlockPos rel : new BlockPos[]{b.left(2), b.right(2)}) {
            h.setBlock(rel, block.defaultBlockState());
        }
        for (BlockPos rel : new BlockPos[]{b.left(2), b.right(2)}) {
            h.setBlock(rel, block.refresh(h.getLevel(), h.absolutePos(rel), h.getBlockState(rel)));
        }
        network(h, b, null, of, what);
    }

    interface NetworkOf {

        @Nullable
        Object at(GameTestHelper h, BlockPos rel);
    }

    /** Deux blocs de reseau de part et d'autre : deux reseaux tant que la frontiere est fermee, un seul entre allies. */
    static void network(GameTestHelper h, Border b, @Nullable BlockState state, NetworkOf of, String what) {
        if (state != null) {
            h.setBlock(b.left(2), state);
            h.setBlock(b.right(2), state);
        }
        h.runAfterDelay(10, () -> {
            Object l = of.at(h, b.left(2)), r = of.at(h, b.right(2));
            Gt.check(l != null && r != null, what + " : reseaux non construits");
            Gt.check(l != r, what + " : un reseau traverse la frontiere du voisin neutre");
            b.ally();
            h.runAfterDelay(10, () -> {
                Object l2 = of.at(h, b.left(2)), r2 = of.at(h, b.right(2));
                Gt.check(l2 != null && l2 == r2, what + " : le reseau ne traverse pas la frontiere entre allies");
                b.s.done();
            });
        });
    }

    /** Arbres de transmission (energie mecanique) : le lien s'arrete a la frontiere du voisin neutre. */
    @GameTest(template = Gt.BORDER, batch = "pioneers_transfert_cinetique", timeoutTicks = 100)
    public static void transfert_cinetique(GameTestHelper h) {
        Border b = new Border(h, "kin");
        network(h, b, PiKinetics.WOODEN_SHAFT.getBlock().defaultBlockState().setValue(AxisKineticBlock.AXIS, Direction.Axis.X),
              ClanTransferTests::kinetic, "arbre");
    }

    /** Cables logistiques : le reseau s'arrete a la frontiere du voisin neutre et se refait a l'alliance. */
    @GameTest(template = Gt.BORDER, batch = "pioneers_transfert_cables", timeoutTicks = 100)
    public static void transfert_cables(GameTestHelper h) {
        Border b = new Border(h, "cab");
        logisticNetwork(h, b, PiTransport.logisticCable(CableLevel.BASIC, false).getBlock(), ClanTransferTests::cable, "cable");
    }

    /** Reseau numerique (cable logistique MKI) : meme regle que les reseaux de ressources. */
    @GameTest(template = Gt.BORDER, batch = "pioneers_transfert_donnees", timeoutTicks = 100)
    public static void transfert_cables_de_donnees(GameTestHelper h) {
        Border b = new Border(h, "dat");
        logisticNetwork(h, b, PiTransport.logisticCable(CableLevel.BASIC, false).getBlock(), ClanTransferTests::data, "cable de donnees");
    }

    // ================================================================== piston
    /** Un piston ne pousse rien chez le voisin neutre ; entre allies, si. */
    @GameTest(template = Gt.BORDER, batch = "pioneers_transfert_piston", timeoutTicks = 100)
    public static void transfert_piston(GameTestHelper h) {
        Border b = new Border(h, "pst");
        BlockPos piston = b.left(2), pushed = b.right(2), power = piston.above();
        h.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        h.setBlock(pushed, Blocks.COBBLESTONE);
        h.setBlock(power, Blocks.REDSTONE_BLOCK);
        h.runAfterDelay(10, () -> {
            h.assertBlockPresent(Blocks.COBBLESTONE, pushed);
            h.setBlock(power, Blocks.AIR);
            b.ally();
            h.runAfterDelay(4, () -> {
                h.setBlock(power, Blocks.REDSTONE_BLOCK);
                h.runAfterDelay(10, () -> {
                    h.assertBlockPresent(Blocks.COBBLESTONE, pushed.east());
                    b.s.done();
                });
            });
        });
    }

    // ================================================================== rail et robots
    /**
     * Rail et robots : le poste (ou la baie) et le vehicule (ou l'arret) de part et d'autre d'une frontiere. Le controle qu'ils appellent
     * ({@code ClanApi.canTransfer}, present a chaque transfert : {@code tools/check_transfers.py}) refuse la nature et un voisin neutre,
     * accepte les clans d'une meme faction (alliance automatique), sans ouvrir la faction a un clan exterieur. Les claims sont poses par
     * l'index (le controle ne lit que lui) : les cases n'ont pas besoin d'etre chargees.
     */
    @GameTest(template = Gt.BORDER, batch = "pioneers_transfert_rail_robots", timeoutTicks = 60)
    public static void transfert_rail_et_robots(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        int x = Gt.borderX(h, Gt.BORDER_SIZE);
        String sfx = Integer.toHexString(h.absolutePos(BlockPos.ZERO).hashCode() & 0xfff);
        ServerPlayer pa = s.player("gtraila", "Cheminote", new BlockPos(x, 2, 2));
        ServerPlayer pb = s.player("gtrailb", "Mecanicien", new BlockPos(x, 2, 2));
        ServerPlayer pc = s.player("gtrailc", "Voisine", new BlockPos(x, 2, 2));
        Clan a = s.clan("GtRailA" + sfx, pa), b = s.clan("GtRailB" + sfx, pb), c = s.clan("GtRailC" + sfx, pc);
        //ligne 1 : poste de A, voie a l'est (chunk voisin) ; ligne 2 : 64 blocs plus au sud, poste de A, voie de B
        BlockPos post = h.absolutePos(new BlockPos(x, 3, 10)), track = post.east();
        BlockPos post2 = post.offset(0, 0, 64), track2 = post2.east();
        BlockPos wildA = post.offset(0, 0, 128), wildB = wildA.east();
        s.claim(a, post);
        s.claim(a, post2);
        Gt.check(!ClanApi.canTransfer(s.level, post, track), "claim -> nature : doit etre refuse");
        Gt.check(!ClanApi.canTransfer(s.level, track, post), "nature -> claim : doit etre refuse");
        Gt.check(ClanApi.canTransfer(s.level, wildA, wildB), "nature -> nature : libre");
        Gt.check(ClanApi.canTransfer(s.level, post, post.offset(0, 0, 1)), "meme chunk : toujours libre");
        s.claim(c, track);
        s.claim(b, track2);
        Gt.check(!ClanApi.canTransfer(s.level, post, track), "A -> C (neutres) : doit etre refuse");
        Gt.check(!ClanApi.canTransfer(s.level, post2, track2), "A -> B (neutres, avant la faction) : doit etre refuse");
        //faction A + B (le Pacte est cense etre deja verifie et paye) ; C reste dehors
        CompoundTag pact = new CompoundTag();
        pact.putString("name", "GtRail" + sfx);
        pact.putString("tag", "GTR");
        pact.putInt("color", 3);
        pact.putString("regime", "empire");
        Factions.create(s.server, pact, a, List.of(b), pa.getUUID());
        Gt.check(a.faction != null && a.faction.equals(b.faction), "faction non fondee");
        Gt.equal(ClanApi.Relation.ALLY, ClanApi.relation(s.server, a, b), "relation entre clans d'une meme faction");
        Gt.equal(ClanApi.Relation.NEUTRAL, ClanApi.relation(s.server, a, c), "relation avec un clan hors faction");
        Gt.check(ClanApi.canTransfer(s.level, post2, track2), "A -> B (meme faction) : doit passer");
        Gt.check(ClanApi.canTransfer(s.level, track2, post2), "B -> A (meme faction) : doit passer");
        Gt.check(!ClanApi.canTransfer(s.level, post, track), "A -> C (hors faction) : toujours refuse");
        //en guerre : plus rien ne passe, meme entre allies d'hier
        s.war(c, a);
        Gt.equal(ClanApi.Relation.WAR, ClanApi.relation(s.server, a, c), "relation en guerre");
        Gt.check(!ClanApi.canTransfer(s.level, post, track), "A -> C (en guerre) : doit etre refuse");
        s.done();
    }
}
