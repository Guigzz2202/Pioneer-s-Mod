package fr.pioneers.bronze.autofoundry;

import fr.pioneers.bronze.BronzeBlock;
import fr.pioneers.forge.casting.AnvilMoldBlock;
import fr.pioneers.forge.casting.PlacedMoldBlock;
import fr.pioneers.registries.PiBronze;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.2-beta (lot 4, Guillaume : « le moule est un nouveau bloc dont on parametre la forme et qui accueille du metal en fusion et le fait
 * refroidir automatiquement ») : MOULE AUTOMATIQUE, bloc de fonte garni de terre refractaire dont on choisit l'empreinte dans l'interface
 * ({@link Form} : les formes des moules d'argile, lingot, pepites, plaque, tige, engrenage, et l'enclume). Il recoit le metal par le bec d'un
 * conduit de coulee pose au-dessus, le laisse figer (duree selon la piece ET le metal) et range la piece dans sa sortie, que l'entonnoir ou le
 * cable logistique vident par le dessous ou les cotes ({@link AutoMoldTile}). Il ne s'use pas.
 */
public class AutoMoldBlock extends BronzeBlock {

    /** Empreintes : forme du moule d'argile (recettes {@code pioneers:casting} de son objet), empreinte (px, moule tourne vers le sud). */
    public enum Form implements StringRepresentable {
        INGOT("ingot"), NUGGET("nugget"), PLATE("plate"), ROD("rod"), GEAR("gear"), ANVIL("anvil");

        public final String id;

        Form(String id) {
            this.id = id;
        }

        @NotNull
        @Override
        public String getSerializedName() {
            return id;
        }

        /** Objet du moule d'argile de cette forme (cle des recettes de coulee). */
        public Item moldItem() {
            return this == ANVIL ? PiBronze.ANVIL_MOLD.get() : PiBronze.MOLDS.get(id).get();
        }

        /** Haut du moule (px) : 12, l'enclume un bloc plein. */
        public float top() {
            return this == ANVIL ? AnvilMoldBlock.TOP : TOP;
        }

        /** Fond de l'empreinte (px) : la profondeur du moule d'argile doublee ; l'enclume, celle du moule d'enclume. */
        public float floor() {
            if (this == ANVIL) {
                return AnvilMoldBlock.FLOOR;
            }
            PlacedMoldBlock.Shape s = PlacedMoldBlock.Shape.of(id);
            return TOP - 2 * (PlacedMoldBlock.TOP - (s == null ? 1 : s.floor));
        }

        /** Rectangles de l'empreinte {x0, z0, x1, z1} (px). */
        public float[][] cavities() {
            if (this == ANVIL) {
                return AnvilMoldBlock.Cavity.BOXES;
            }
            PlacedMoldBlock.Shape s = PlacedMoldBlock.Shape.of(id);
            return s == null ? new float[][]{{4, 4, 12, 12}} : s.cavities;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Form of(int n) {
            Form[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, n))];
        }
    }

    public static final EnumProperty<Form> FORM = EnumProperty.create("form", Form.class);
    /** Dessus du moule (px), sauf l'enclume (bloc plein). */
    public static final float TOP = 12;
    private static final VoxelShape LOW = Block.box(0, 0, 0, 16, TOP, 16), FULL = Block.box(0, 0, 0, 16, 16, 16);

    public AutoMoldBlock(Properties properties) {
        super(properties, AutoMoldTile::new, null);
        registerDefaultState(defaultBlockState().setValue(FORM, Form.INGOT));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FORM);
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return state.getValue(FORM) == Form.ANVIL ? FULL : LOW;
    }
}
