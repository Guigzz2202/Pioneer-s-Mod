package fr.pioneers.tech.bronze.steam;

import fr.pioneers.Pioneers;
import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.gas.IGasHandler;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.core.Action;
import fr.pioneers.core.gas.GasAccess;
import fr.pioneers.tech.industry.PiIndustry;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.40 (demande de Guillaume : « unifier le transport de la vapeur avec notre logistique, par les cables, sans nouveaux tubes ») : la vapeur
 * de l'age du bronze voyage dans les cables a gaz (gaz {@code pioneers:steam}), comme partout ailleurs ; les tuyaux de vapeur sont retires.
 * Chaque noeud de vapeur ({@link SteamNode}) recoit ici la capacite gaz du socle sur chaque face qui a un volume :
 * <ul>
 *     <li>les machines (moteur, ateliers, soufflerie, foreuse, injecteur...) acceptent la vapeur jusqu'a remplir leur reserve (1.0.2-beta,
 *     lot 3 : plus de pression de ligne, la vapeur est une quantite) ;</li>
 *     <li>la chaudiere (dome) se laisse puiser et pousse elle-meme sa vapeur dans les cables et ports a gaz voisins ({@link #eject}).</li>
 * </ul>
 * Deux noeuds colles l'un a l'autre echangent toujours directement, par le remplissage de leurs reserves ({@link Steam#exchangeAll}). Les organes (manometre,
 * soupape...) ne prennent pas de cable.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SteamGas implements IGasHandler {

    /** Vapeur poussee au plus par face et par tick (mB). */
    public static final long EJECT = 8_000;
    private static final ResourceLocation KEY = Pioneers.rl("steam_gas");

    private final SteamNode node;
    private final Direction side;

    private SteamGas(SteamNode node, Direction side) {
        this.node = node;
        this.side = side;
    }

    @Nullable
    private SteamVolume volume() {
        return node.steam(side);
    }

    /** Vapeur que les cables peuvent faire entrer (mB) : la place libre de la reserve. */
    private long room() {
        SteamVolume v = volume();
        if (v == null || node.steamSource() || !node.accepts(side, true)) {
            return 0;
        }
        return (long) Math.floor(v.room());
    }

    private static boolean isSteam(GasStack stack) {
        return !stack.isEmpty() && stack.getGas() == PiIndustry.gas("steam");
    }

    @Override
    public int getGasTanks() {
        return 1;
    }

    @NotNull
    @Override
    public GasStack getGasInTank(int tank) {
        SteamVolume v = volume();
        return v == null || v.steam() < 1 ? GasStack.EMPTY : PiIndustry.steam((long) v.steam());
    }

    @Override
    public long getGasTankCapacity(int tank) {
        SteamVolume v = volume();
        return v == null ? 0 : (long) v.capacity();
    }

    @Override
    public boolean isGasValid(int tank, @NotNull GasStack stack) {
        return isSteam(stack);
    }

    @NotNull
    @Override
    public GasStack insertGas(@NotNull GasStack stack, @NotNull Action action) {
        if (!isSteam(stack)) {
            return stack;
        }
        long put = Math.min(stack.getAmount(), room());
        if (put <= 0) {
            return stack;
        }
        if (action.execute()) {
            volume().add(put, 0);
        }
        return stack.copyWithAmount(stack.getAmount() - put);
    }

    @NotNull
    @Override
    public GasStack extractGas(long amount, @NotNull Action action) {
        SteamVolume v = volume();
        if (v == null || !node.steamSource() || !node.accepts(side, false)) {
            return GasStack.EMPTY;
        }
        long got = (long) Math.min(amount, Math.floor(v.steam()));
        if (got <= 0) {
            return GasStack.EMPTY;
        }
        if (action.execute()) {
            v.take(got);
        }
        return PiIndustry.steam(got);
    }

    @NotNull
    @Override
    public GasStack extractGas(@NotNull GasStack stack, @NotNull Action action) {
        return isSteam(stack) ? extractGas(stack.getAmount(), action) : GasStack.EMPTY;
    }

    /**
     * Pousse la vapeur d'un noeud source ({@code volume}, face {@code side}) dans le voisin s'il prend du gaz et n'est pas lui-meme un noeud de
     * vapeur (ceux-la echangent directement) : cable a gaz (fiche neutre), port a gaz d'un multibloc, reservoir... Renvoie la vapeur partie.
     */
    public static double eject(Level level, BlockPos pos, Direction side, SteamVolume volume) {
        if (volume.steam() < 1) {
            return 0;
        }
        BlockPos n = pos.relative(side);
        //1.9 (MAJ RP, bloc 4) : ni a travers une frontiere de claim refusee
        if (!level.isLoaded(n) || !ClanApi.canTransfer(level, pos, n)) {
            return 0;
        }
        BlockEntity be = level.getBlockEntity(n);
        if (be == null || be instanceof SteamNode) {
            return 0;
        }
        IGasHandler handler = GasAccess.find(be, side.getOpposite());
        if (handler == null) {
            return 0;
        }
        long offer = (long) Math.min(EJECT, Math.floor(volume.steam()));
        GasStack rest = handler.insertGas(PiIndustry.steam(offer), Action.EXECUTE);
        long given = offer - rest.getAmount();
        if (given > 0) {
            volume.take(given);
        }
        return given;
    }

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        if (!(event.getObject() instanceof SteamNode node) || event.getObject() instanceof AccessoryTile) {
            return;
        }
        Provider provider = new Provider(node);
        event.addCapability(KEY, provider);
        event.addListener(provider::invalidate);
    }

    /** Capacite gaz d'un noeud, face par face (absente sur les faces sans volume de vapeur). */
    private static final class Provider implements ICapabilityProvider {

        private final SteamNode node;
        private final Map<Direction, LazyOptional<IGasHandler>> sides = new EnumMap<>(Direction.class);

        Provider(SteamNode node) {
            this.node = node;
        }

        @NotNull
        @Override
        public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            if (cap != PioneersAPI.GAS_HANDLER || side == null || node.steam(side) == null) {
                return LazyOptional.empty();
            }
            return sides.computeIfAbsent(side, s -> LazyOptional.of(() -> new SteamGas(node, s))).cast();
        }

        void invalidate() {
            sides.values().forEach(LazyOptional::invalidate);
            sides.clear();
        }
    }
}
