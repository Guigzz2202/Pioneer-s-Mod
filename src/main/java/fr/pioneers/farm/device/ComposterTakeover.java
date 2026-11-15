package fr.pioneers.farm.device;

import fr.pioneers.Pioneers;
import fr.pioneers.farm.PiKitchen;
import fr.pioneers.farm.kitchen.BigStationBlock;
import fr.pioneers.farm.kitchen.StationBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.GameData;

/**
 * 1.6 (unification vanilla) : le composteur de Minecraft est remplace par le notre ({@link CompostBinTile}). Proposition retenue pour les mondes
 * existants :
 * <ul>
 * <li>au CHARGEMENT d'un chunk (serveur), chaque composteur vanilla est mis en file puis converti au tick suivant (au plus {@link #PER_TICK} par
 * tick et par dimension) en composteur Pioneer's, son niveau garde (couches, fermentation, compost pret) : les villages generes ensuite aussi ;</li>
 * <li>au CLIC droit sur un composteur vanilla qui aurait echappe a la file : converti tout de suite, puis le clic agit sur le notre ;</li>
 * <li>les fermiers des villages gardent leur lieu de travail : les etats de notre composteur sont enregistres comme lieu de travail des
 * fermiers ({@link #registerJobSite}, table des points d'interet de Forge), la conversion ne change donc pas le point d'interet.</li>
 * </ul>
 * L'objet vanilla est converti dans les inventaires (table {@code vanilla_replacements.json}, groupe farm), sans recette ni onglet.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class ComposterTakeover {

    private ComposterTakeover() {
    }

    static final int PER_TICK = 32;
    private static final Map<ResourceKey<Level>, Queue<BlockPos>> PENDING = new ConcurrentHashMap<>();

    private static BlockState bin(Direction facing) {
        return PiFarmDevices.DEVICES.get(DeviceBlock.Kind.COMPOST_BIN).get().defaultBlockState().setValue(DeviceBlock.FACING, facing);
    }

    // ------------------------------------------------------------------ lieu de travail des fermiers

    /**
     * Etats du composteur Pioneer's = lieu de travail du fermier (comme le composteur vanilla) ; 1.6 : fumoir du mod = celui du boucher. Appele au setup commun et avant chaque demarrage
     * de serveur (la table de Forge est reconstruite quand les registres reviennent a leur etat fige).
     */
    public static void registerJobSite() {
        PoiType farmer = ForgeRegistries.POI_TYPES.getValue(PoiTypes.FARMER.location());
        if (farmer == null) {
            return;
        }
        Map<BlockState, PoiType> map = GameData.getBlockStatePointOfInterestTypeMap();
        Block block = PiFarmDevices.DEVICES.get(DeviceBlock.Kind.COMPOST_BIN).get();
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            map.putIfAbsent(state, farmer);
        }
        //1.6 : le fumoir du mod remplace celui de Minecraft : lieu de travail du boucher (case principale seulement)
        PoiType butcher = ForgeRegistries.POI_TYPES.getValue(PoiTypes.BUTCHER.location());
        if (butcher != null) {
            for (BlockState state : PiKitchen.STATIONS.get(StationBlock.Kind.SMOKEHOUSE).get().getStateDefinition().getPossibleStates()) {
                if (!state.hasProperty(BigStationBlock.PART) || state.getValue(BigStationBlock.PART) == 0) {
                    map.putIfAbsent(state, butcher);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        registerJobSite();
    }

    // ------------------------------------------------------------------ conversion

    /** Remplace un composteur vanilla par le notre (niveau garde) ; renvoie le nouvel etat, ou null si ce n'en etait pas un. */
    static BlockState convert(ServerLevel level, BlockPos pos, Direction facing) {
        BlockState old = level.getBlockState(pos);
        if (!old.is(Blocks.COMPOSTER)) {
            return null;
        }
        int vanillaLevel = old.getValue(ComposterBlock.LEVEL);
        BlockState now = bin(facing);
        level.setBlock(pos, now, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof CompostBinTile tile) {
            tile.takeOver(vanillaLevel);
        }
        return now;
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (!level.getBlockState(event.getPos()).is(Blocks.COMPOSTER)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (level instanceof ServerLevel server) {
            Player player = event.getEntity();
            BlockState now = convert(server, event.getPos(), player.getDirection().getOpposite());
            if (now != null) {
                player.displayClientMessage(Component.translatable("farm.pioneers.device.compost.converted").withStyle(ChatFormatting.GOLD), true);
                now.use(level, player, event.getHand(), event.getHitVec());
            }
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        List<BlockPos> found = null;
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(s -> s.is(Blocks.COMPOSTER))) {
                continue;
            }
            int y0 = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (section.getBlockState(x, y, z).is(Blocks.COMPOSTER)) {
                            if (found == null) {
                                found = new ArrayList<>();
                            }
                            found.add(chunk.getPos().getWorldPosition().offset(x, y0 + y, z));
                        }
                    }
                }
            }
        }
        if (found != null) {
            PENDING.computeIfAbsent(level.dimension(), k -> new ConcurrentLinkedQueue<>()).addAll(found);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        Queue<BlockPos> queue = PENDING.get(level.dimension());
        if (queue == null || queue.isEmpty()) {
            return;
        }
        for (int n = 0; n < PER_TICK && !queue.isEmpty(); n++) {
            BlockPos pos = queue.poll();
            if (pos != null && level.isLoaded(pos)) {
                Direction facing = Direction.from2DDataValue(Math.floorMod(pos.getX() * 31 + pos.getZ(), 4));
                convert(level, pos, facing);
            }
        }
    }
}
