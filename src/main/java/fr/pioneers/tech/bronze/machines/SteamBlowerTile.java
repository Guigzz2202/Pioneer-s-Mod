package fr.pioneers.tech.bronze.machines;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.unit.PowerSource;
import fr.pioneers.tech.bronze.BronzeBlock;
import fr.pioneers.tech.bronze.BronzeTile;
import fr.pioneers.tech.bronze.steam.Steam;
import fr.pioneers.tech.bronze.steam.SteamNode;
import fr.pioneers.tech.bronze.steam.SteamRules;
import fr.pioneers.tech.bronze.steam.SteamVolume;
import fr.pioneers.core.registries.PiBronze;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Soufflerie a vapeur : un cylindre a vapeur entraine un cylindre soufflant ; l'air sort a l'avant, dans le four ou la tuyere qu'elle regarde.
 * 1.0.2-beta (lot 3, plus de pression) : tant qu'elle a de la vapeur, elle souffle comme 3 soufflets mecaniques pour 17 mB de vapeur par
 * tick ({@link SteamRules#BLOWER_AIR}, {@link SteamRules#BLOWER_USE}). Un signal
 * redstone l'arrete. API Machine (lot 7) : la tile ne fait plus que PORTER son unite ({@link SteamBlowerUnit}) et reste le noeud de
 * vapeur ; type de tile {@code steam_blower} et cles inchanges.
 */
public class SteamBlowerTile extends BronzeTile implements SteamNode {

    public static final double MAX_AIR = SteamBlowerUnit.MAX_AIR;
    private final SteamVolume chest = new SteamVolume(SteamRules.BLOWER_RESERVE);
    private final PowerSource.Steam power = new PowerSource.Steam(chest);
    private final SteamBlowerUnit unit;

    public SteamBlowerTile(BlockPos pos, BlockState state) {
        super(PiBronze.STEAM_BLOWER_TILE.get(), pos, state);
        unit = carry(new SteamBlowerUnit(host(), this));
    }

    @Override
    protected PowerSource power() {
        return power;
    }

    public Direction facing() {
        return getBlockState().getValue(BronzeBlock.FACING);
    }

    @Override
    public SteamVolume steam(Direction side) {
        return side != facing() ? chest : null;
    }

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    public double air() {
        return unit.air();
    }

    @Override
    public void serverTick(ServerLevel level) {
        Steam.exchangeAll(level, this);
        unit.tick();
    }

    // ------------------------------------------------------------------
    // Interface standard v2 (valeurs nommees de l'unite)
    // ------------------------------------------------------------------

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public String guiTier() {
        return "bronze";
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        return SteamBlowerUnit.parts();
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        chest.save(tag);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        chest.load(tag);
    }
}
