package fr.pioneers.gametest;

import fr.pioneers.logistics.transport.PiTransport;
import fr.pioneers.logistics.transport.legacy.LegacyCableBlock;
import fr.pioneers.logistics.transport.legacy.LegacyCableTile;
import fr.pioneers.logistics.transport.legacy.LegacyMigration;
import fr.pioneers.logistics.transport.logistic.CableLevel;
import fr.pioneers.logistics.transport.logistic.ChannelDef;
import fr.pioneers.logistics.transport.logistic.ChannelKind;
import fr.pioneers.logistics.transport.logistic.LogisticCableBlock;
import fr.pioneers.logistics.transport.logistic.LogisticNetwork;
import fr.pioneers.logistics.transport.logistic.LogisticNetworks;
import fr.pioneers.logistics.transport.logistic.PointConfig;
import fr.pioneers.logistics.transport.logistic.Role;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.11 (D2c) : un « ancien monde » miniature (1.10) converti au chargement. Rangee d'objets : coffre A → 4 anciens cables d'objets MKI (fiche
 * d'entree sur A, fiche de sortie sur B, 5 diamants en transit dans un cable) → coffre B ; rangee voisine : 4 anciens cables d'energie MKIV
 * collés a la premiere. Attendu apres conversion : 8 cables logistiques du meme niveau, les deux rangees separees (faces coupees), un canal
 * d'objets reel (pas auto) avec A en entree et B en sortie, les diamants au sol, puis les 16 pierres de A arrivees dans B.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class CableMigrationTests {

    private static final BlockPos A = new BlockPos(1, 2, 4), B = new BlockPos(6, 2, 4);

    private CableMigrationTests() {
    }

    private static int count(GameTestHelper h, BlockPos rel) {
        Container c = (Container) h.getBlockEntity(rel);
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            n += c.getItem(i).getCount();
        }
        return n;
    }

    /** Pose un ancien cable (etat sauve : seulement l'eau depuis la 1.0.6) et son NBT d'avant. */
    private static void old(GameTestHelper h, BlockPos rel, String id, Direction plug, int pulling, int pushing, ListTag share) {
        LegacyCableBlock block = PiTransport.legacy(id);
        Gt.check(block != null, "bloc herite absent : " + id);
        //1.0.6 : l'etat ne garde plus les faces (memoire du client) ; la face branchee se relit sur le voisin a tile a la conversion
        h.setBlock(rel, block.defaultBlockState());
        int want = plug == null ? 0 : 1 << plug.ordinal();
        Gt.equal(want, block.plugMask(h.getLevel(), h.absolutePos(rel)), "faces branchees relues sur les voisins : " + id + " " + rel);
        LegacyCableTile tile = (LegacyCableTile) h.getBlockEntity(rel);
        CompoundTag tag = new CompoundTag();
        tag.putByte("disabled", (byte) 0);
        tag.putByte("pulling", (byte) pulling);
        tag.putByte("pushing", (byte) pushing);
        tag.putByte("v", (byte) 1);
        if (!share.isEmpty()) {
            tag.put("share", share);
        }
        tile.load(tag);
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_cables_migration", timeoutTicks = 300)
    public static void ancien_monde_converti(GameTestHelper h) {
        Gt.begin(h);
        h.setBlock(A, Blocks.CHEST);
        h.setBlock(B, Blocks.CHEST);
        ((Container) h.getBlockEntity(A)).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        ListTag share = new ListTag();
        CompoundTag diamonds = new ItemStack(Items.DIAMOND).save(new CompoundTag());
        diamonds.putInt("n", 5);
        share.add(diamonds);
        for (int x = 2; x <= 5; x++) {
            Direction plug = x == 2 ? Direction.WEST : x == 5 ? Direction.EAST : null;
            old(h, new BlockPos(x, 2, 4), "basic_item_cable", plug, x == 2 ? 1 << Direction.WEST.ordinal() : 0,
                  x == 5 ? 1 << Direction.EAST.ordinal() : 0, x == 3 ? share : new ListTag());
            old(h, new BlockPos(x, 2, 5), "ultimate_energy_cable", null, 0, 0, new ListTag());
        }
        ServerLevel level = h.getLevel();
        for (int x = 2; x <= 5; x++) {
            LegacyMigration.queue(level, h.absolutePos(new BlockPos(x, 2, 4)));
            LegacyMigration.queue(level, h.absolutePos(new BlockPos(x, 2, 5)));
        }
        LegacyMigration.run(level);
        for (int x = 2; x <= 5; x++) {
            BlockState item = h.getBlockState(new BlockPos(x, 2, 4)), energy = h.getBlockState(new BlockPos(x, 2, 5));
            Gt.check(item.getBlock() instanceof LogisticCableBlock c && c.level == CableLevel.BASIC && !c.clear, "objets x=" + x + " : pas un cable MKI");
            Gt.check(energy.getBlock() instanceof LogisticCableBlock c && c.level == CableLevel.ULTIMATE, "energie x=" + x + " : pas un cable MKIV");
            Gt.check(LogisticCableBlock.link(item, Direction.SOUTH) == LogisticCableBlock.Link.NONE, "x=" + x + " : les deux rangees se touchent");
        }
        Gt.check(LogisticCableBlock.link(h.getBlockState(new BlockPos(2, 2, 4)), Direction.WEST) == LogisticCableBlock.Link.PLUG, "pas de fiche vers A");
        Gt.check(LogisticCableBlock.link(h.getBlockState(new BlockPos(5, 2, 4)), Direction.EAST) == LogisticCableBlock.Link.PLUG, "pas de fiche vers B");
        //1.0.6 : boite elargie de 3 blocs : un objet jete part avec une petite vitesse au hasard et sortait de la boite serree (diamants trouves
        //un bloc plus loin, run local du 16/11)
        AABB around = new AABB(h.absolutePos(new BlockPos(2, 1, 3)), h.absolutePos(new BlockPos(5, 4, 6))).inflate(3);
        //1.0.6 : un diamant vanilla jete au sol devient pioneers:diamond (vanilla_replacements.json, VanillaEvents.onJoin)
        net.minecraft.world.item.Item diamond = fr.pioneers.core.vanilla.VanillaReplacements.convert(new net.minecraft.world.item.ItemStack(Items.DIAMOND)).getItem();
        int dropped = level.getEntitiesOfClass(ItemEntity.class, around, e -> e.getItem().is(diamond) || e.getItem().is(Items.DIAMOND)).stream()
              .mapToInt(e -> e.getItem().getCount()).sum();
        Gt.equal(5, dropped, "diamants en transit deposes au sol (objets autour : " + level.getEntitiesOfClass(ItemEntity.class, around.inflate(6)).stream()
              .map(e -> e.getItem() + " en " + e.blockPosition()).toList() + ")");
        long keyA = h.absolutePos(A).asLong(), keyB = h.absolutePos(B).asLong();
        BlockPos cable = h.absolutePos(new BlockPos(3, 2, 4));
        h.succeedWhen(() -> {
            LogisticNetwork net = LogisticNetworks.network(level, cable);
            Gt.check(net != null, "reseau des objets non construit");
            Gt.check(!net.isAuto(), "reseau migre en mode auto");
            ChannelDef items = null;
            for (ChannelDef d : net.config().live()) {
                if (d.kind == ChannelKind.ITEM) {
                    items = d;
                }
            }
            Gt.check(items != null && net.config().live().size() == 1, "un seul canal d'objets attendu");
            PointConfig pa = net.config().point(keyA, items.uid), pb = net.config().point(keyB, items.uid);
            Gt.check(pa != null && pa.role == Role.INPUT && pa.pull, "point de A : entree (tirer)");
            Gt.check(pb != null && pb.role == Role.OUTPUT, "point de B : sortie");
            Gt.check(LogisticNetworks.network(level, h.absolutePos(new BlockPos(3, 2, 5))) != net, "la rangee d'energie a rejoint le reseau des objets");
            Gt.equal(16, count(h, B), "pierres arrivees dans B");
            Gt.equal(0, count(h, A), "A doit etre vide");
        });
    }
}
