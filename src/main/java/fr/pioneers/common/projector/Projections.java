package fr.pioneers.common.projector;

import fr.pioneers.Pioneers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Projection du plan d'un multibloc depuis son controleur : Maj + clic droit (main vide) sur le controleur montre la structure en blocs
 * fantomes, recommence pour la variante suivante (taille, regime), puis la masque. En creatif, Maj + clic droit en tenant le controleur
 * lui-meme construit la structure d'un coup. L'etat est tenu des deux cotes (le meme evenement arrive au client et au serveur) : le client
 * le dessine, le serveur s'en sert pour la construction creative.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class Projections {

    /** Plan affiche : structure, variante, ancre et orientation du repere local, dimension, controleur. */
    public record Active(Blueprint structure, int variant, BlockPos anchor, Direction forward, ResourceKey<Level> dimension, BlockPos controller) {

        public Map<BlockPos, ProjectedStructure.Ghost> layout() {
            return structure.layout(variant, anchor, forward);
        }
    }

    private static final Map<UUID, Active> SERVER = new ConcurrentHashMap<>();
    @Nullable
    private static volatile Active client;

    private Projections() {
    }

    /** Plan affiche pour le joueur local (client). */
    @Nullable
    /** Plans fournis par d'autres registres (multiblocs industriels) : pour un bloc, ses plans. */
    private static final List<Function<Block, List<? extends Blueprint>>> PROVIDERS = new CopyOnWriteArrayList<>();

    public static void addProvider(Function<Block, List<? extends Blueprint>> provider) {
        PROVIDERS.add(provider);
    }

    public static Active client() {
        return client;
    }

    public static void clearClient() {
        client = null;
    }

    /** Les plans que ce bloc sait projeter, dans l'ordre (structure, variante). */
    public static List<Active> options(Block block) {
        List<Active> out = new ArrayList<>();
        //Lot 6 : plans maison tires de leur forme (HouseShapes) d'abord, a la place des anciens plans ecrits a la main
        List<Blueprint> all = new ArrayList<>(fr.pioneers.api.multiblock.HouseShapes.forBlock(block));
        for (Function<Block, List<? extends Blueprint>> provider : PROVIDERS) {
            all.addAll(provider.apply(block));
        }
        for (Blueprint s : all) {
            if (s.controller() == block) {
                for (int v = 0; v < s.variants(); v++) {
                    out.add(new Active(s, v, BlockPos.ZERO, Direction.NORTH, Level.OVERWORLD, BlockPos.ZERO));
                }
            }
        }
        return out;
    }

    /**
     * 1.0.5-beta (lot 3) : bloc pose dont la tile propose ses PROPRES plans en tete (regulateur de serre : le plan des fautes de la serre
     * relevee), avant ceux de son bloc.
     */
    public interface Dynamic {

        List<Blueprint> projectionPlans();
    }

    /** Plans que le bloc pose en {@code pos} sait projeter : ceux de sa tile ({@link Dynamic}) d'abord, puis ceux de son bloc. */
    public static List<Active> optionsAt(Level level, BlockPos pos, BlockState state) {
        List<Active> out = new ArrayList<>();
        if (level.getBlockEntity(pos) instanceof Dynamic dynamic) {
            for (Blueprint s : dynamic.projectionPlans()) {
                for (int v = 0; v < s.variants(); v++) {
                    out.add(new Active(s, v, BlockPos.ZERO, Direction.NORTH, Level.OVERWORLD, BlockPos.ZERO));
                }
            }
        }
        out.addAll(options(state.getBlock()));
        return out;
    }

    /** 1.11.3 (lot E3) : un plan du catalogue (page « Multiblocs » de JEI, banc hors jeu) : structure et variante. */
    public record Plan(Blueprint structure, int variant) {
    }

    /**
     * 1.11.3 (lot E3) : TOUS les plans que les blocs donnes savent projeter (une entree par structure et par variante), sans doublon, dans
     * l'ordre des blocs. Meme source que la projection (Maj + clic droit) : {@link ProjectedStructure} et les fournisseurs enregistres.
     */
    public static List<Plan> catalog(Iterable<Block> blocks) {
        Map<String, Plan> out = new java.util.LinkedHashMap<>();
        for (Block block : blocks) {
            if (block == Blocks.AIR) {
                continue;
            }
            for (Active a : options(block)) {
                Blueprint s = a.structure();
                String key = s.getTranslationKey() + "|" + s.getVariantKey(a.variant()) + "|" + a.variant() + "|" + System.identityHashCode(s.controller());
                out.putIfAbsent(key, new Plan(s, a.variant()));
            }
        }
        return new ArrayList<>(out.values());
    }

    /**
     * Place le plan pour que sa case « controleur » tombe sur le controleur pose ; l'orientation est celle ou la facade du controleur regarde
     * vers l'exterieur de la structure (sinon celle du joueur).
     */
    public static Active place(Blueprint structure, int variant, Level level, BlockPos controller, BlockState state, Direction playerFacing) {
        Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.getValue(BlockStateProperties.HORIZONTAL_FACING)
              : state.hasProperty(BlockStateProperties.FACING) && state.getValue(BlockStateProperties.FACING).getAxis().isHorizontal()
                ? state.getValue(BlockStateProperties.FACING) : null;
        Direction best = playerFacing;
        BlockPos bestRel = null;
        double bestScore = -1e9;
        for (Direction forward : new Direction[]{playerFacing, playerFacing.getClockWise(), playerFacing.getOpposite(), playerFacing.getCounterClockWise()}) {
            Map<BlockPos, ProjectedStructure.Ghost> layout = structure.layout(variant, BlockPos.ZERO, forward);
            BlockPos rel = null;
            double cx = 0, cz = 0;
            for (Map.Entry<BlockPos, ProjectedStructure.Ghost> e : layout.entrySet()) {
                cx += e.getKey().getX();
                cz += e.getKey().getZ();
                if (rel == null && e.getValue().state() != null && e.getValue().state().getBlock() == state.getBlock()) {
                    rel = e.getKey();
                }
            }
            if (rel == null) {
                continue;
            }
            cx /= Math.max(1, layout.size());
            cz /= Math.max(1, layout.size());
            double score = facing == null ? 0 : facing.getStepX() * (rel.getX() - cx) + facing.getStepZ() * (rel.getZ() - cz);
            if (score > bestScore + 1e-6) {
                bestScore = score;
                best = forward;
                bestRel = rel;
            }
        }
        BlockPos anchor = bestRel == null ? controller : controller.subtract(bestRel);
        return new Active(structure, variant, anchor, best, level.dimension(), controller);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SERVER.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SERVER.clear();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        List<Active> options = optionsAt(level, pos, state);
        if (options.isEmpty()) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        boolean build = player.getAbilities().instabuild && held.getItem() instanceof BlockItem item && item.getBlock() == state.getBlock();
        if (!held.isEmpty() && !build) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        Active current = level.isClientSide ? client : SERVER.get(player.getUUID());
        boolean here = current != null && current.controller().equals(pos) && current.dimension() == level.dimension();
        Active next;
        if (build) {
            Active base = here ? current : options.get(0);
            next = place(base.structure(), base.variant(), level, pos, state, player.getDirection());
            if (!level.isClientSide) {
                int placed = build(level, next.layout());
                player.displayClientMessage(Component.translatable("projection.pioneers.built", describe(next), placed).withStyle(ChatFormatting.GREEN), true);
            }
            next = null;
        } else {
            int index = -1;
            if (here) {
                for (int i = 0; i < options.size(); i++) {
                    if (options.get(i).structure() == current.structure() && options.get(i).variant() == current.variant()) {
                        index = i;
                    }
                }
            }
            Active option = index + 1 < options.size() ? options.get(index + 1) : null;
            next = option == null ? null : place(option.structure(), option.variant(), level, pos, state, player.getDirection());
            if (!level.isClientSide) {
                player.displayClientMessage(next == null ? Component.translatable("projection.pioneers.hidden").withStyle(ChatFormatting.GRAY)
                      : Component.translatable("projection.pioneers.shown", describe(next)).withStyle(ChatFormatting.AQUA), true);
                //1.9 (MAJ RP) : « Chutes reemployees » du Batisseur : la liste de ce qui manque au chantier
                if (next != null && player instanceof net.minecraft.server.level.ServerPlayer sp
                    && fr.pioneers.core.profile.PioneerApi.modifier(sp, "builder.missing_list") > 0) {
                    java.util.Map<Block, Integer> missing = new java.util.LinkedHashMap<>();
                    for (Map.Entry<BlockPos, ProjectedStructure.Ghost> e : next.layout().entrySet()) {
                        ProjectedStructure.Ghost ghost = e.getValue();
                        if (ghost.state() != null && !ghost.isSatisfiedBy(level.getBlockState(e.getKey()))) {
                            missing.merge(ghost.state().getBlock(), 1, Integer::sum);
                        }
                    }
                    fr.pioneers.core.profile.SkillHooks.missingList(sp, missing);
                }
            }
        }
        if (level.isClientSide) {
            client = next;
        } else if (next == null) {
            SERVER.remove(player.getUUID());
        } else {
            SERVER.put(player.getUUID(), next);
        }
    }

    /** Pose les blocs du plan (et vide les cases qui doivent l'etre). Retourne le nombre de blocs changes. */
    private static int build(Level level, Map<BlockPos, ProjectedStructure.Ghost> layout) {
        int changed = 0;
        for (Map.Entry<BlockPos, ProjectedStructure.Ghost> entry : layout.entrySet()) {
            if (entry.getValue().state() == null && !level.getBlockState(entry.getKey()).isAir()) {
                level.setBlock(entry.getKey(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                changed++;
            }
        }
        for (Map.Entry<BlockPos, ProjectedStructure.Ghost> entry : layout.entrySet()) {
            ProjectedStructure.Ghost ghost = entry.getValue();
            if (ghost.state() != null && !ghost.isSatisfiedBy(level.getBlockState(entry.getKey()))) {
                //1.0.4-beta (lot 23) : etat calcule d'apres les voisins deja poses (textures connectees, blocs empilables), comme une pose a la main
                level.setBlock(entry.getKey(), Block.updateFromNeighbourShapes(ghost.state(), level, entry.getKey()), Block.UPDATE_ALL);
                changed++;
            }
        }
        return changed;
    }

    /**
     * API Multibloc (lot 5) : bouton « Afficher le plan » de l'onglet Structure (client) : projette le premier plan du controleur en
     * {@code pos} (comme Maj + clic droit), ou le retire s'il est deja projete sur lui.
     */
    public static void showClient(Level level, BlockPos pos, BlockState state, Direction playerFacing) {
        if (client != null && client.controller().equals(pos) && client.dimension() == level.dimension()) {
            client = null;
            return;
        }
        List<Active> options = optionsAt(level, pos, state);
        client = options.isEmpty() ? null : place(options.get(0).structure(), options.get(0).variant(), level, pos, state, playerFacing);
    }

    public static Component describe(Active active) {
        MutableComponent text = Component.translatable(active.structure().getTranslationKey()).withStyle(ChatFormatting.AQUA);
        String variant = active.structure().getVariantKey(active.variant());
        if (variant != null) {
            text.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(Component.translatable(variant).withStyle(ChatFormatting.YELLOW));
        }
        return text;
    }
}
