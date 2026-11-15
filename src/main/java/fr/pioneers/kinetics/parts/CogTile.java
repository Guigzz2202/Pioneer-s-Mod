package fr.pioneers.kinetics.parts;

import fr.pioneers.core.Directions;
import fr.pioneers.core.vanilla.PiVanillaMaterials;
import fr.pioneers.kinetics.AxisKineticBlock;
import fr.pioneers.kinetics.KineticMaterial;
import fr.pioneers.kinetics.KineticTile;
import fr.pioneers.registries.PiKinetics;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

public class CogTile extends KineticTile {

    public CogTile(BlockPos pos, BlockState state) {
        super(PiKinetics.COG_TILE.get(), pos, state);
    }

    public boolean isLarge() {
        return getBlockState().getBlock() instanceof CogBlock cog && cog.large;
    }

    public Direction.Axis axis() {
        return AxisKineticBlock.axis(getBlockState());
    }

    @Override
    public KineticMaterial material() {
        return getBlockState().getBlock() instanceof CogBlock cog ? cog.material : KineticMaterial.WOOD;
    }

    /**
     * 1.0.5-beta (lot 23) : un engrenage ENCADRE ({@link fr.pioneers.core.block.Covers#FRAMED}) tourne sur le pivot de son cadre : aucun bout
     * d'arbre (ni lien par l'axe, ni axe dessine) ; ses engrenements dans le plan restent ({@link #collectLinks}).
     */
    public boolean framed() {
        return fr.pioneers.core.block.Covers.framed(getBlockState());
    }

    @Override
    public double shaftFactor(Direction face) {
        return face.getAxis() == axis() && !framed() ? 1 : 0;
    }

    /**
     * 1.0.5-beta (lot 23, Guillaume : « par defaut les engrenages n'ont plus d'axe au centre ; les textures se connectent si besoin ») : un
     * bout d'arbre se dessine vers la face {@code face} seulement si un VRAI lien par l'axe passe par elle (meme regle que
     * {@link fr.pioneers.kinetics.KineticTile#shaftLinks} : facteurs de face non nuls des deux cotes) ; sinon le moyeu reste plein. Client
     * (rendu) et serveur (GameTests).
     */
    public boolean axleTowards(Direction face) {
        if (level == null || shaftFactor(face) == 0) {
            return false;
        }
        BlockPos other = worldPosition.relative(face);
        return level.isLoaded(other) && level.getBlockEntity(other) instanceof fr.pioneers.kinetics.KineticNode node
               && node.shaftFactor(face.getOpposite()) != 0;
    }

    @Override
    public void collectLinks(LinkSink sink) {
        shaftLinks(sink);
        if (level == null) {
            return;
        }
        Direction.Axis axis = axis();
        boolean large = isLarge();
        for (Direction d : Directions.ALL) {
            if (d.getAxis() == axis) {
                continue;
            }
            //Pignon contre pignon : voisins directs dans le plan
            CogTile near = cog(worldPosition.relative(d), axis);
            if (!large && near != null && !near.isLarge()) {
                sink.link(near.worldPosition, -1);
            }
            //Rouet et pignon : voisins en diagonale dans le plan
            for (Direction e : Directions.ALL) {
                if (e.getAxis() == axis || e.getAxis() == d.getAxis() || e.ordinal() < d.ordinal()) {
                    continue;
                }
                CogTile diagonal = cog(worldPosition.relative(d).relative(e), axis);
                if (diagonal != null && diagonal.isLarge() != large) {
                    sink.link(diagonal.worldPosition, large ? -2 : -0.5);
                }
            }
        }
    }

    private CogTile cog(BlockPos pos, Direction.Axis axis) {
        if (level != null && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof CogTile tile && tile.axis() == axis) {
            return tile;
        }
        return null;
    }

    @Override
    public double inertia() {
        return (isLarge() ? 2.0 : 0.4) * (material() == KineticMaterial.WOOD ? 1 : 2.5);
    }

    @Override
    public double efficiency() {
        return 0.97;
    }

    @Override
    protected double renderRadius() {
        return isLarge() ? 1 : 0;
    }

    @Override
    protected List<ItemStack> brokenDrops() {
        return material() == KineticMaterial.WOOD ? List.of(new ItemStack(Items.STICK, 3)) : List.of(new ItemStack(PiVanillaMaterials.item("iron_nugget"), 6));
    }
}
