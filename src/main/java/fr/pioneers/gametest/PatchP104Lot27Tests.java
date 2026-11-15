package fr.pioneers.gametest;

import fr.pioneers.core.common.PiRemaps;
import fr.pioneers.core.block.Covers;
import fr.pioneers.core.block.PiCoverFrame;
import fr.pioneers.tech.kinetics.AxisKineticBlock;
import fr.pioneers.tech.kinetics.KineticNetworks;
import fr.pioneers.tech.kinetics.parts.ShaftTile;
import fr.pioneers.tech.kinetics.rope.LinkType;
import fr.pioneers.tech.kinetics.rope.PulleyTile;
import fr.pioneers.tech.kinetics.rope.RopeItem;
import fr.pioneers.core.registries.PiKinetics;
import fr.pioneers.core.registries.PiTransport;
import fr.pioneers.logistics.transport.CableTier;
import fr.pioneers.logistics.transport.buffer.BufferType;
import fr.pioneers.logistics.transport.logistic.CableLevel;
import fr.pioneers.logistics.transport.logistic.LogisticCableBlock;
import fr.pioneers.logistics.transport.logistic.LogisticCableTile;
import fr.pioneers.logistics.transport.logistic.LogisticNetworks;
import fr.pioneers.logistics.warehouse.WarehouseRopes;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.0.4-beta (lot 27, GameTests) : retours de Guillaume du 09/11 (suppressions, logistique). Cache d'un cable logistique (lot 30 : d'abord
 * le cadre de cache, puis un bloc plein : etat couvert, forme pleine, lumiere arretee, reseau intact, sauvegarde ; casser rend la cache, puis
 * le cadre, puis laisse le cable) ; cache d'un arbre de rotation (l'arbre reste dans son reseau) ; poulies a plusieurs cordes (meme sens accepte, sens contraire refuse) ; blocs retires
 * absents des registres et remappes.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot27Tests {

    private PatchP104Lot27Tests() {
    }

    private static BlockHitResult hit(BlockPos abs) {
        return new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false);
    }

    private static String key(Component c) {
        return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(c);
    }

    /** Clic droit d'un joueur factice tenant {@code held} sur le bloc en {@code abs} ; rend la pile restante. */
    private static ItemStack rightClick(GameTestHelper h, Player p, BlockPos abs, ItemStack held) {
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockState state = h.getLevel().getBlockState(abs);
        state.use(h.getLevel(), p, InteractionHand.MAIN_HAND, hit(abs));
        return p.getItemInHand(InteractionHand.MAIN_HAND);
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_27_cable_cover", timeoutTicks = 40)
    public static void p104_27_cable_cache(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos a = h.absolutePos(new BlockPos(3, 2, 3)), b = a.east();
        h.getLevel().setBlock(a, PiTransport.LOGISTIC_CABLES.get(CableLevel.BASIC).getBlock().defaultBlockState(), 3);
        h.getLevel().setBlock(b, PiTransport.LOGISTIC_CABLES.get(CableLevel.BASIC).getBlock().defaultBlockState(), 3);
        //Un reseau sans fiche n'existe pas : un buffer d'objets au bout de la ligne
        h.getLevel().setBlock(b.east(), PiTransport.BUFFERS.get(BufferType.ITEM).get(CableTier.BASIC).getBlock().defaultBlockState(), 3);
        Player p = h.makeMockPlayer();
        //1.0.4-beta (lot 30) : sur un cable nu, un bloc plein ne cache plus rien ; le cadre de cache d'abord
        rightClick(h, p, a, new ItemStack(Items.STONE_BRICKS, 3));
        Gt.check(!Covers.framed(h.getLevel().getBlockState(a)), "cable nu : un bloc plein ne pose pas de cache sans cadre (lot 30)");
        rightClick(h, p, a, new ItemStack(PiCoverFrame.FRAME.asItem()));
        Gt.check(h.getLevel().getBlockState(a).getValue(Covers.FRAMED), "cadre de cache installe autour du cable");
        ItemStack left = rightClick(h, p, a, new ItemStack(Items.STONE_BRICKS, 3));
        BlockState covered = h.getLevel().getBlockState(a);
        Gt.check(covered.getValue(Covers.COVERED), "cable couvert apres le clic droit avec des briques");
        Gt.check(h.getLevel().getBlockEntity(a) instanceof LogisticCableTile tile && tile.cover() == Blocks.STONE_BRICKS.defaultBlockState(),
              "la tile du cable nu porte la cache (briques de pierre)");
        Gt.equal(2, (long) left.getCount(), "une brique prise");
        Gt.check(covered.isCollisionShapeFullBlock(h.getLevel(), a), "forme pleine (on marche dessus, on vise le bloc entier)");
        Gt.equal(15, (long) covered.getLightBlock(h.getLevel(), a), "la cache arrete la lumiere comme un bloc plein");
        Gt.check(LogisticCableBlock.link(covered, Direction.EAST).linked(), "le cable cache reste relie a son voisin");
        //Un bloc non plein (verre) ne cache pas, meme sur un cadre : la pose suit son cours normal (rien ne change ici)
        rightClick(h, p, b, new ItemStack(PiCoverFrame.FRAME.asItem()));
        rightClick(h, p, b, new ItemStack(Items.GLASS));
        Gt.check(!h.getLevel().getBlockState(b).getValue(Covers.COVERED), "le verre ne sert pas de cache");
        //Sauvegarde : la cache relue
        LogisticCableTile tile = (LogisticCableTile) h.getLevel().getBlockEntity(a);
        CompoundTag tag = tile.saveWithoutMetadata();
        LogisticCableTile copy = new LogisticCableTile(a, covered);
        copy.load(tag);
        Gt.equal(Blocks.STONE_BRICKS.defaultBlockState(), copy.cover(), "cache relue de la sauvegarde");
        h.runAfterDelay(2, () -> {
            Gt.check(LogisticNetworks.network(h.getLevel(), a) != null, "le cable cache est toujours dans son reseau");
            //Casser : d'abord la cache (rendue), puis le cadre (rendu), le cable reste
            boolean removed = covered.onDestroyedByPlayer(h.getLevel(), a, p, true, h.getLevel().getFluidState(a));
            Gt.check(!removed, "casser un cable cache ne retire pas le cable");
            BlockState framed = h.getLevel().getBlockState(a);
            Gt.check(!framed.getValue(Covers.COVERED) && framed.getValue(Covers.FRAMED), "apres la cache : le cadre reste");
            Gt.check(!framed.onDestroyedByPlayer(h.getLevel(), a, p, true, h.getLevel().getFluidState(a)), "casser le cadre ne retire pas le cable");
            BlockState after = h.getLevel().getBlockState(a);
            Gt.check(after.is(PiTransport.LOGISTIC_CABLES.get(CableLevel.BASIC).getBlock()) && !Covers.framed(after), "cable nu apres la casse");
            List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(a).inflate(1.5));
            Gt.check(drops.stream().anyMatch(e -> e.getItem().is(Items.STONE_BRICKS)), "la brique de la cache est rendue");
            Gt.check(drops.stream().anyMatch(e -> e.getItem().is(PiCoverFrame.FRAME.asItem())), "le cadre est rendu");
            Gt.check(!after.isCollisionShapeFullBlock(h.getLevel(), a), "forme du cable nu");
            s.done();
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_27_shaft_cover", timeoutTicks = 40)
    public static void p104_27_arbre_cache(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockState shaft = PiKinetics.IRON_SHAFT.getBlock().defaultBlockState().setValue(AxisKineticBlock.AXIS, Direction.Axis.X);
        BlockPos a = h.absolutePos(new BlockPos(3, 2, 3)), b = a.east();
        h.getLevel().setBlock(a, shaft, 3);
        h.getLevel().setBlock(b, shaft, 3);
        Player p = h.makeMockPlayer();
        rightClick(h, p, a, new ItemStack(PiCoverFrame.FRAME.asItem()));
        rightClick(h, p, a, new ItemStack(Items.OAK_PLANKS));
        BlockState covered = h.getLevel().getBlockState(a);
        Gt.check(covered.getValue(Covers.COVERED), "arbre couvert par les planches (dans son cadre)");
        Gt.check(h.getLevel().getBlockEntity(a) instanceof ShaftTile t && t.cover() == Blocks.OAK_PLANKS.defaultBlockState(), "cache dans la tile de l'arbre");
        Gt.equal(Direction.Axis.X, covered.getValue(AxisKineticBlock.AXIS), "l'axe de l'arbre est garde");
        Gt.check(covered.isCollisionShapeFullBlock(h.getLevel(), a), "forme pleine");
        h.runAfterDelay(2, () -> {
            Gt.check(KineticNetworks.network(h.getLevel(), a) != null && KineticNetworks.network(h.getLevel(), a) == KineticNetworks.network(h.getLevel(), b),
                  "l'arbre cache transmet toujours (meme reseau que son voisin)");
            Gt.check(!covered.onDestroyedByPlayer(h.getLevel(), a, p, true, h.getLevel().getFluidState(a)), "casser rend d'abord la cache");
            BlockState framed = h.getLevel().getBlockState(a);
            Gt.check(!framed.onDestroyedByPlayer(h.getLevel(), a, p, true, h.getLevel().getFluidState(a)), "puis le cadre");
            Gt.check(!Covers.framed(h.getLevel().getBlockState(a)) && h.getLevel().getBlockEntity(a) instanceof ShaftTile, "l'arbre reste, nu");
            s.done();
        });
    }

    @GameTest(template = Gt.BORDER, batch = "pioneers_p104_27_ropes", timeoutTicks = 40)
    public static void p104_27_poulies_multicordes(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockState pulley = PiKinetics.PULLEY.getBlock().defaultBlockState().setValue(AxisKineticBlock.AXIS, Direction.Axis.X);
        BlockPos a = h.absolutePos(new BlockPos(4, 3, 4)), b = h.absolutePos(new BlockPos(4, 3, 12)), c = h.absolutePos(new BlockPos(4, 7, 4));
        for (BlockPos p : new BlockPos[]{a, b, c}) {
            h.getLevel().setBlock(p, pulley, 3);
        }
        Gt.check(WarehouseRopes.link(h.getLevel(), a, b, LinkType.LEATHER_BELT), "premiere courroie a -> b : " + WarehouseRopes.check(h.getLevel(), a, b));
        Gt.check(WarehouseRopes.link(h.getLevel(), a, c, LinkType.FIBER_ROPE), "deuxieme corde sur la meme poulie a -> c : " + WarehouseRopes.check(h.getLevel(), a, c));
        PulleyTile ta = (PulleyTile) h.getLevel().getBlockEntity(a);
        Gt.equal(2, (long) ta.ropeCount(), "deux cordes sur la poulie a");
        Gt.check(ta.capacity() >= 4, "4 cordes par poulie");
        h.runAfterDelay(2, () -> {
            //b et c tournent deja dans le meme sens que a (rapport +1) : une corde croisee b -> c les ferait tourner en sens contraire
            Gt.equal("kinetics.pioneers.rope.direction", key(RopeItem.check(h.getLevel(), b, c, true)), "corde croisee entre deux poulies du meme sens refusee");
            Gt.check(RopeItem.check(h.getLevel(), b, c, false) == null, "corde droite (meme sens, meme rapport) acceptee");
            s.done();
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_27_removed", timeoutTicks = 20)
    public static void p104_27_retraits(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        for (String id : new String[]{"steam_injector", "condenser", "pneumatic_caisson", "cavern_valve", "expansion_turbine", "hypertube_anchor",
              "warehouse_pulley", "warehouse_post", "diamond_reflector_module", "fast_core_module"}) {
            ResourceLocation rl = new ResourceLocation("pioneers", id);
            Gt.check(!BuiltInRegistries.BLOCK.containsKey(rl) && !BuiltInRegistries.ITEM.containsKey(rl), id + " retire des registres");
            Gt.check(PiRemaps.P104_LOT27.containsKey(id), id + " remappe (anciens mondes)");
            String to = PiRemaps.P104_LOT27.get(id);
            Gt.check(to.isEmpty() || BuiltInRegistries.BLOCK.containsKey(new ResourceLocation("pioneers", to)), id + " -> " + to + " existe");
        }
        Gt.check(BuiltInRegistries.BLOCK.containsKey(new ResourceLocation("pioneers", "diamond_reflector")), "le reflecteur en diamant reste");
        s.done();
    }
}
