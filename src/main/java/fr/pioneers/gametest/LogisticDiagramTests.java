package fr.pioneers.gametest;

import fr.pioneers.core.RelativeSide;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import fr.pioneers.logistics.transport.PiTransport;
import fr.pioneers.logistics.transport.CableTier;
import fr.pioneers.logistics.transport.buffer.BufferTile;
import fr.pioneers.logistics.transport.buffer.BufferType;
import fr.pioneers.logistics.transport.logistic.CableLevel;
import fr.pioneers.logistics.transport.logistic.ChannelDef;
import fr.pioneers.logistics.transport.logistic.ChannelKind;
import fr.pioneers.logistics.transport.logistic.DiagramEdits;
import fr.pioneers.logistics.transport.logistic.DiagramService;
import fr.pioneers.logistics.transport.logistic.DiagramSnapshot;
import fr.pioneers.logistics.transport.logistic.LogisticApi;
import fr.pioneers.logistics.transport.logistic.LogisticCableBlock;
import fr.pioneers.logistics.transport.logistic.LogisticNetwork;
import fr.pioneers.logistics.transport.logistic.PointConfig;
import fr.pioneers.logistics.transport.logistic.Role;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/**
 * 1.11 (D2b) : une edition complete par le diagramme, comme un joueur : ouverture de la session sur un cable, creation d'un canal d'objets,
 * points (coffre en entree, buffer en sortie), puis changement de la FACE D'ACCES du buffer. Le buffer n'accepte les objets que par le dessus
 * (reglage de ses faces) alors que le cable touche son flanc ouest : par la face touchee rien ne passe ; par la face « Haut », les objets
 * arrivent (spec §3.1 : « injecter en haut meme si le cable est branche en bas »). Le patron du cube de l'instantane dit la meme chose.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class LogisticDiagramTests {

    private static final BlockPos CHEST = new BlockPos(2, 2, 4), CABLE = new BlockPos(3, 2, 4), BUFFER = new BlockPos(4, 2, 4);

    private LogisticDiagramTests() {
    }

    private static int items(IItemHandler handler) {
        int n = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            n += handler.getStackInSlot(i).getCount();
        }
        return n;
    }

    private static int chestItems(GameTestHelper h) {
        Container c = (Container) h.getBlockEntity(CHEST);
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            n += c.getItem(i).getCount();
        }
        return n;
    }

    private static int bufferItems(GameTestHelper h) {
        BufferTile b = (BufferTile) h.getBlockEntity(BUFFER);
        return b.getCapability(ForgeCapabilities.ITEM_HANDLER, null).map(LogisticDiagramTests::items).orElse(-1);
    }

    private static CompoundTag op(String name) {
        CompoundTag t = new CompoundTag();
        t.putString("op", name);
        return t;
    }

    private static CompoundTag point(int uid, PointConfig cfg, long key) {
        CompoundTag t = op("pt");
        t.putInt("u", uid);
        t.put("c", cfg.save());
        t.putLongArray("k", new long[]{key});
        return t;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_cables_diagramme", timeoutTicks = 260)
    public static void diagramme_canal_et_face_d_acces(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        //Coffre (source), buffer d'objets MKI (n'accepte que par le dessus), cable logistique MKI entre les deux
        h.setBlock(CHEST, Blocks.CHEST);
        Container chest = (Container) h.getBlockEntity(CHEST);
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        h.setBlock(BUFFER, PiTransport.buffer(BufferType.ITEM, CableTier.BASIC).getBlock());
        BufferTile buffer = (BufferTile) h.getBlockEntity(BUFFER);
        for (RelativeSide r : RelativeSide.values()) {
            buffer.getSideConfig().set(SideType.ITEMS, r, r == RelativeSide.TOP ? SideMode.INPUT : SideMode.NONE);
        }
        LogisticCableBlock block = PiTransport.logisticCable(CableLevel.BASIC, false).getBlock();
        BlockPos cable = h.absolutePos(CABLE);
        BlockState state = block.refresh(h.getLevel(), cable, block.defaultBlockState());
        h.getLevel().setBlock(cable, state, Block.UPDATE_ALL);
        Gt.check(LogisticCableBlock.link(h.getLevel().getBlockState(cable), Direction.EAST) == LogisticCableBlock.Link.PLUG, "pas de fiche vers le buffer");
        Gt.check(LogisticCableBlock.link(h.getLevel().getBlockState(cable), Direction.WEST) == LogisticCableBlock.Link.PLUG, "pas de fiche vers le coffre");
        ServerPlayer player = s.player("gtdia", "Cabliste", CABLE.north());
        long chestKey = h.absolutePos(CHEST).asLong(), bufferKey = h.absolutePos(BUFFER).asLong();
        h.runAfterDelay(4, () -> {
            LogisticNetwork net = LogisticApi.network(h.getLevel(), cable);
            Gt.check(net != null, "reseau non construit");
            //Ouverture (clic droit) : session et instantane
            DiagramService.open(player, cable, null);
            Gt.check(DiagramService.hasSession(player.getUUID()), "pas de session apres l'ouverture");
            DiagramSnapshot snap = DiagramService.snapshot(player, cable, net, DiagramSnapshot.NO_FOCUS);
            Gt.check(snap.canEdit && snap.machine(chestKey) != null && snap.machine(bufferKey) != null, "instantane : raccords ou droit d'edition");
            int faces = snap.machine(bufferKey).faces[ChannelKind.ITEM.ordinal()];
            Gt.equal(1, faces >> (RelativeSide.TOP.ordinal() * 2) & 3, "patron du cube : dessus du buffer = insertion");
            Gt.equal(0, faces >> (RelativeSide.fromDirections(Direction.NORTH, Direction.WEST).ordinal() * 2) & 3,
                  "patron du cube : flanc touche ferme");
            //Creation d'un canal (materialise le mode auto), puis on retire les canaux materialises
            ChannelDef def = new ChannelDef(0, ChannelKind.ITEM);
            def.name = "Pierres";
            CompoundTag ch = op("ch");
            ch.put("d", def.save());
            Gt.equal(DiagramEdits.Result.OK, DiagramService.edit(player, ch), "creation du canal");
            int uid = 0;
            for (ChannelDef d : net.config().live()) {
                if (d.name.equals("Pierres")) {
                    uid = d.uid;
                } else {
                    CompoundTag del = op("del");
                    del.putInt("u", d.uid);
                    DiagramService.edit(player, del);
                }
            }
            Gt.check(uid != 0 && net.config().live().size() == 1, "canal « Pierres » absent ou canaux auto restants");
            PointConfig in = new PointConfig(Role.INPUT, true);
            PointConfig out = new PointConfig(Role.OUTPUT, true);
            Gt.equal(DiagramEdits.Result.OK, DiagramService.edit(player, point(uid, in, chestKey)), "point du coffre");
            Gt.equal(DiagramEdits.Result.OK, DiagramService.edit(player, point(uid, out, bufferKey)), "point du buffer");
            final int channel = uid;
            Gt.later(h, 60, () -> {
                Gt.equal(0, bufferItems(h), "objets passes par la face touchee (fermee)");
                Gt.equal(16, chestItems(h), "objets sortis du coffre sans destination");
                //Face d'acces du buffer : le dessus (independante de la face touchee)
                PointConfig top = new PointConfig(Role.OUTPUT, true);
                top.face = RelativeSide.TOP.ordinal();
                Gt.equal(DiagramEdits.Result.OK, DiagramService.edit(player, point(channel, top, bufferKey)), "changement de face");
                Gt.equal(RelativeSide.TOP.ordinal(), net.config().point(bufferKey, channel).face, "face d'acces enregistree");
                Gt.later(h, 80, () -> {
                    int got = bufferItems(h);
                    Gt.check(got > 0, "rien n'est arrive par la face du dessus");
                    Gt.equal(16, got + chestItems(h), "objets crees ou perdus");
                    //Session fermee : plus aucune edition
                    DiagramService.close(player);
                    Gt.equal(DiagramEdits.Result.INVALID, DiagramService.edit(player, op("auto")), "edition sans session acceptee");
                    s.done();
                });
            });
        });
    }
}
