package fr.pioneers.tech.industry.multi;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.heat.IHeatHandler;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.core.Directions;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.core.heat.HeatCapacitor;
import fr.pioneers.core.heat.HeatExchange;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.logistics.transport.CableTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Radiateur : corps thermique, echanges avec les voisins qui ne conduisent pas d'eux-memes (ports), pertes vers l'air x4 par tier.
 * <p>
 * MAJ simplification (03/11/2026, Guillaume : « buffer les radiateurs et mieux les documenter dans JEI avec les chiffres ») : SOURCE UNIQUE
 * des chiffres ({@link #DISSIPATION}, {@link #CONDUCTANCE}, {@link #CAPACITY}, {@link #rated}), lue aussi par JEI et l'infobulle. Pertes x3
 * (8 -> 24 J/t/K au MKI), conductance x2 (60 -> 120 : le radiateur n'est jamais plus lent que le corps du multibloc de son tier), capacite x4
 * (2 000 -> 8 000 J/K : l'echange borne a l'equilibre ne bride plus le MKIV). Toujours x4 par tier, comme la courbe d'energie du mod. Un MKI
 * evacue ~8,6 kJ/t a 800 K : de quoi tenir seul le multibloc MKI le plus chaud a pleine charge (four a arc : ~5,8 kJ/t ; avant : 3,5 kJ/t).
 */
public class RadiatorTile extends PiBlockEntity {

    /** Pertes vers l'air par tier (J par tick et par kelvin au-dessus de 300 K). */
    public static final double[] DISSIPATION = {24, 96, 384, 1_536};
    /** Conductance du radiateur vers le port de chaleur voisin, par tier (J/t/K). */
    public static final double[] CONDUCTANCE = {120, 480, 1_920, 7_680};
    /** Capacite thermique (J/K). */
    public static final double CAPACITY = 8_000;

    private final CableTier tier;
    private final HeatCapacitor body;
    private final LazyOptional<IHeatHandler> cap;

    public RadiatorTile(BlockPos pos, BlockState state) {
        super(PiIndustry.RADIATOR_TILE.get(), pos, state);
        tier = state.getBlock() instanceof RadiatorBlock r ? r.tier : CableTier.BASIC;
        body = new HeatCapacitor(CAPACITY, CONDUCTANCE[tier.ordinal()], this::setChanged);
        cap = LazyOptional.of(() -> body);
    }

    /** Pertes vers l'air (J par tick et par kelvin d'ecart). */
    public double dissipation() {
        return DISSIPATION[tier.ordinal()];
    }

    /**
     * Chaleur evacuee en regime etabli (J/t) par un radiateur du tier {@code tier} contre le port d'un multibloc du tier {@code machineTier}
     * a la temperature {@code temperature} (K) : la conductance la plus faible (corps du multibloc 60 x 4^tier, radiateur) en serie avec les
     * pertes a l'air (meme loi que {@link HeatExchange}).
     */
    public static double rated(int tier, int machineTier, double temperature) {
        int t = Math.max(0, Math.min(3, tier));
        double k = Math.min(60.0 * (1L << (2 * Math.max(0, Math.min(3, machineTier)))), CONDUCTANCE[t]);
        double d = DISSIPATION[t];
        return Math.max(0, temperature - HeatExchange.AMBIENT) * k * d / (k + d);
    }

    void tick() {
        for (Direction d : Directions.ALL) {
            //1.9 (MAJ RP, bloc 4) : la chaleur ne passe pas une frontiere de claim refusee
            BlockEntity other = ClanApi.canTransfer(level, worldPosition, worldPosition.relative(d)) ? level.getBlockEntity(worldPosition.relative(d)) : null;
            if (other instanceof PortTile) {
                other.getCapability(PioneersAPI.HEAT_HANDLER, d.getOpposite()).ifPresent(h -> HeatExchange.exchange(h, body));
            }
        }
        HeatExchange.ambient(body, dissipation());
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> wanted, @Nullable Direction side) {
        return wanted == PioneersAPI.HEAT_HANDLER ? cap.cast() : super.getCapability(wanted, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        cap.invalidate();
    }

    public double temperature() {
        return body.getTemperature();
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("heat", body.serializeNBT());
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        body.deserializeNBT(tag.getCompound("heat"));
    }
}
