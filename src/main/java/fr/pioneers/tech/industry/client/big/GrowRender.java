package fr.pioneers.tech.industry.client.big;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.core.RenderClock;
import fr.pioneers.farm.tree.FruitLeavesBlock;
import fr.pioneers.farm.tree.FruitSaplingBlock;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.GeneratedNbtShapes;
import fr.pioneers.tech.industry.multi.GrowView;
import fr.pioneers.tech.industry.multi.NbtAnims;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 26, Guillaume : « l'animation de la ferme hydroponique doit representer les crops qui sont plantes et qui poussent. Pareil
 * pour les arbres ») : les VRAIES plantes de la recette en cours ({@link GrowView} : id de la recette du lot et progression), une par case de
 * substrat de la structure (lettre {@code U} des structures de Guillaume), dessinees par le controleur avec leurs propres modeles :
 * <ul>
 *     <li>ferme hydroponique : la culture de la graine de la recette (bloc de culture de l'objet), au stade de croissance qui suit la
 *     progression du lot (age 0 au semis, age maximal a la recolte ; une plante sans age grandit de 30 a 100 %) ;</li>
 *     <li>plantation forestiere : la pousse de l'essence (bloc de la pousse), puis un arbre de l'essence (bois du tronc et feuilles de
 *     l'essence : buches de la recette, feuilles du meme nom) dont le tronc et le houppier grandissent, fruits qui murissent sur les
 *     feuilles fruitieres ; bambou et autres tiges : la tige monte.</li>
 * </ul>
 * Tout reste dans l'interieur de la structure (case au-dessus du substrat ; arbres : 4,8 blocs de haut au plus, houppier en losange de
 * 1 bloc de demi-diagonale au plus : dans l'interieur vitre, hors des vitres des angles ; controle : tools/check_p104_26.py). Rien n'est envoye en plus : la recette et la progression viennent de l'etat client du controleur.
 */
public final class GrowRender {

    /** Sorte de pousse. */
    enum Kind { CROP, TREE, COLUMN }

    /** Ce que la recette fait pousser (calcule une fois par recette). */
    record Spec(Kind kind, BlockState plant, @Nullable BlockState log, @Nullable BlockState leaves, boolean conifer) {
    }

    private static final Map<ResourceLocation, Optional<Spec>> SPECS = new HashMap<>();
    /**
     * Hauteur maximale d'un arbre (blocs, depuis le dessus du substrat) et demi-cote maximal du houppier (blocs) ; le houppier est tourne de
     * 45 degres (losange de demi-diagonale {@code CROWN_MAX * sqrt(2)} <= 1 bloc) : il reste hors des vitres des angles de la serre.
     */
    static final float TREE_MAX = 4.8F, CROWN_MAX = 0.70F;

    private GrowRender() {
    }

    /** Plantes du controleur (rien si la machine n'en a pas ou si rien ne pousse). */
    public static void draw(ControllerTile tile, Level level, BlockPos c, Direction front, GeneratedNbtShapes.Shape s, boolean active, float partialTick,
          PoseStack pose, MultiBufferSource buffers) {
        GrowView.State st = GrowView.of(tile);
        if (st == null || st.recipe() == null) {
            return;
        }
        Spec spec = spec(level, st.recipe());
        if (spec == null) {
            return;
        }
        float p = st.progress(RenderClock.time(partialTick), active);   //1.0.5-beta (lot 29) : horloge de rendu continue
        List<BlockPos> soil = substrate(c, front, s);
        BlockRenderDispatcher br = Minecraft.getInstance().getBlockRenderer();
        for (int i = 0; i < soil.size(); i++) {
            BlockPos at = soil.get(i).above();
            int light = LevelRenderer.getLightColor(level, at);
            pose.pushPose();
            pose.translate(at.getX() - c.getX(), at.getY() - c.getY(), at.getZ() - c.getZ());
            switch (spec.kind) {
                case CROP -> crop(br, spec, p, i, pose, buffers, light);
                case TREE -> tree(br, spec, p, i, pose, buffers, light);
                case COLUMN -> column(br, spec, p, pose, buffers, light);
            }
            pose.popPose();
        }
    }

    /** Cases de substrat de la structure (lettre U), dans le monde. */
    static List<BlockPos> substrate(BlockPos c, Direction front, GeneratedNbtShapes.Shape s) {
        List<BlockPos> out = new ArrayList<>();
        for (int y = 0; y < s.sy(); y++) {
            for (int z = 0; z < s.sz(); z++) {
                for (int x = 0; x < s.sx(); x++) {
                    if (s.at(x, y, z) == 'U') {
                        out.add(NbtAnims.cell(c, front, s.cx(), s.cy(), s.cz(), x, y, z));
                    }
                }
            }
        }
        return out;
    }

    /** Spec de la recette (null si elle ne fait rien pousser de dessinable). */
    @Nullable
    static Spec spec(Level level, ResourceLocation id) {
        return SPECS.computeIfAbsent(id, k -> {
            Optional<? extends Recipe<?>> r = level.getRecipeManager().byKey(k);
            if (r.isEmpty() || !(r.get() instanceof IndustrialRecipe ir) || ir.items().isEmpty()) {
                return Optional.empty();
            }
            ItemStack[] in = ir.items().get(0).ingredient().getItems();
            if (in.length == 0 || !(in[0].getItem() instanceof BlockItem bi)) {
                return Optional.empty();
            }
            List<ItemStack> out = new ArrayList<>();
            ir.results().forEach(res -> out.add(res.stack()));
            return Optional.of(of(bi.getBlock(), out));
        }).orElse(null);
    }

    /** Ce que fait pousser le bloc semé (graine, pousse, tige), d'apres les sorties de la recette (buches). */
    static Spec of(Block seed, List<ItemStack> results) {
        BlockState plant = seed.defaultBlockState();
        Block log = null;
        for (ItemStack out : results) {
            if (out.getItem() instanceof BlockItem b && b.getBlock().defaultBlockState().is(BlockTags.LOGS)) {
                log = b.getBlock();
                break;
            }
        }
        boolean sapling = seed instanceof SaplingBlock || seed instanceof FruitSaplingBlock || seed == Blocks.MANGROVE_PROPAGULE;
        if (sapling || log != null) {
            ResourceLocation sid = BuiltInRegistries.BLOCK.getKey(seed);
            if (log == null) {
                log = byName(sid, "_sapling", "_log", Blocks.OAK_LOG);
            }
            Block leaves = byName(sid, sid.getPath().endsWith("_propagule") ? "_propagule" : "_sapling", "_leaves", null);
            if (leaves == null) {
                leaves = byName(BuiltInRegistries.BLOCK.getKey(log), "_log", "_leaves", Blocks.OAK_LEAVES);
            }
            BlockState ls = leaves.defaultBlockState();
            if (ls.hasProperty(LeavesBlock.PERSISTENT)) {
                ls = ls.setValue(LeavesBlock.PERSISTENT, true);
            }
            BlockState ws = log.defaultBlockState();
            if (ws.hasProperty(RotatedPillarBlock.AXIS)) {
                ws = ws.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
            }
            String path = BuiltInRegistries.BLOCK.getKey(leaves).getPath();
            return new Spec(Kind.TREE, plant, ws, ls, path.contains("spruce") || path.contains("pine") || path.contains("fir"));
        }
        if (seed == Blocks.BAMBOO || seed == Blocks.SUGAR_CANE || seed == Blocks.CACTUS || seed == Blocks.KELP) {
            return new Spec(Kind.COLUMN, plant, null, null, false);
        }
        return new Spec(Kind.CROP, plant, null, null, false);
    }

    /** Bloc de meme espace de noms dont le nom remplace {@code from} par {@code to}, sinon le repli. */
    @Nullable
    private static Block byName(ResourceLocation id, String from, String to, @Nullable Block fallback) {
        if (id.getPath().endsWith(from)) {
            ResourceLocation other = new ResourceLocation(id.getNamespace(), id.getPath().substring(0, id.getPath().length() - from.length()) + to);
            if (BuiltInRegistries.BLOCK.containsKey(other)) {
                return BuiltInRegistries.BLOCK.get(other);
            }
        }
        return fallback;
    }

    /** Etat de la plante au stade {@code p} (0..1) : age de la culture (ou premiere propriete entiere de croissance). */
    static BlockState staged(BlockState plant, float p) {
        if (plant.getBlock() instanceof CropBlock crop) {
            return crop.getStateForAge(Math.round(p * crop.getMaxAge()));
        }
        IntegerProperty age = ageOf(plant);
        if (age != null) {
            int lo = age.getPossibleValues().stream().min(Integer::compare).orElse(0), hi = age.getPossibleValues().stream().max(Integer::compare).orElse(0);
            return plant.setValue(age, lo + Math.round(p * (hi - lo)));
        }
        return plant;
    }

    @Nullable
    private static IntegerProperty ageOf(BlockState state) {
        for (Property<?> prop : state.getProperties()) {
            if (prop instanceof IntegerProperty ip && (prop.getName().equals("age") || prop.getName().equals("stage") || prop.getName().equals("fruit"))) {
                return ip;
            }
        }
        return null;
    }

    private static void block(BlockRenderDispatcher br, BlockState st, PoseStack pose, MultiBufferSource buffers, int light) {
        br.renderSingleBlock(st, pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
    }

    /** Culture : son modele au stade du lot (leger decalage de stade d'une case a l'autre : rangees vivantes). */
    static void crop(BlockRenderDispatcher br, Spec spec, float p, int i, PoseStack pose, MultiBufferSource buffers, int light) {
        float q = Math.max(0, Math.min(1, p + ((i * 7) % 5 - 2) * 0.02F));
        BlockState st = staged(spec.plant, q);
        boolean grows = st != spec.plant || spec.plant.getBlock() instanceof CropBlock || ageOf(spec.plant) != null;
        if (!grows) {
            //plante sans stade : elle grandit (30 a 100 %), posee au centre de la case
            float k = 0.3F + 0.7F * q;
            pose.translate(0.5, 0, 0.5);
            pose.scale(k, k, k);
            pose.translate(-0.5, 0, -0.5);
        }
        if (st.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            st = st.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER);
        }
        block(br, st, pose, buffers, light);
        if (st.getBlock() instanceof DoublePlantBlock && q > 0.5F) {
            pose.translate(0, 1, 0);
            block(br, st.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), pose,
                  buffers, light);
        }
    }

    /**
     * Arbre : pousse (jusqu'a 20 % du lot), puis tronc (bois de l'essence, debout) et houppier (feuilles de l'essence) qui grandissent ;
     * coniferes en trois etages ; fruits des feuilles fruitieres qui murissent a la fin. Tailles legerement differentes d'un arbre a
     * l'autre (pas de faces confondues entre houppiers voisins).
     */
    static void tree(BlockRenderDispatcher br, Spec spec, float p, int i, PoseStack pose, MultiBufferSource buffers, int light) {
        float jitter = 1 + ((i * 5) % 7 - 3) * 0.022F;
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees((i * 37) % 4 * 90F));
        if (p < 0.2F) {
            float k = 0.55F + 0.45F * p / 0.2F;
            pose.scale(k, k, k);
            pose.translate(-0.5, 0, -0.5);
            block(br, staged(spec.plant, 0), pose, buffers, light);
            return;
        }
        float q = (p - 0.2F) / 0.8F;
        pose.mulPose(Axis.YP.rotationDegrees(45));
        float trunk = (1.0F + 2.4F * q) * jitter, tw = 0.16F + 0.16F * q, r = (0.38F + (CROWN_MAX - 0.38F) * q) * Math.min(1, jitter);
        //tronc
        cube(br, spec.log, -tw / 2, 0, -tw / 2, tw, trunk, tw, pose, buffers, light);
        BlockState leaves = spec.leaves;
        if (leaves.getBlock() instanceof FruitLeavesBlock) {
            leaves = leaves.setValue(FruitLeavesBlock.FRUIT, Math.max(0, Math.min(4, Math.round((q - 0.55F) / 0.45F * 4))));
        }
        if (spec.conifer) {
            float base = trunk * 0.35F, hgt = Math.min(TREE_MAX - base, trunk * 0.95F + 0.5F);
            for (int k = 0; k < 3; k++) {
                float rr = r * (1 - k * 0.3F), y0 = base + hgt * k / 3F;
                cube(br, leaves, -rr, y0, -rr, 2 * rr, hgt / 3F + 0.15F, 2 * rr, pose, buffers, light);
            }
        } else {
            float y0 = Math.max(0.6F, trunk - r * 1.1F), hc = Math.min(TREE_MAX - y0, r * 1.5F);
            cube(br, leaves, -r, y0, -r, 2 * r, hc, 2 * r, pose, buffers, light);
            float rt = r * 0.62F, yt = y0 + hc;
            cube(br, leaves, -rt, yt, -rt, 2 * rt, Math.min(TREE_MAX - yt, rt * 0.9F), 2 * rt, pose, buffers, light);
        }
    }

    /** Tige (bambou, canne a sucre, cactus, varech) : 1 a 3 blocs, le dernier qui monte. */
    static void column(BlockRenderDispatcher br, Spec spec, float p, PoseStack pose, MultiBufferSource buffers, int light) {
        float hgt = 0.25F + 2.75F * p;
        for (int k = 0; k < 3 && k < hgt; k++) {
            float part = Math.min(1, hgt - k);
            pose.pushPose();
            pose.translate(0, k, 0);
            pose.scale(1, part, 1);
            block(br, spec.plant, pose, buffers, light);
            pose.popPose();
        }
    }

    /** Bloc etire dans la boite (x, y, z) + (w, h, d) en blocs (repere du pied de l'arbre). */
    private static void cube(BlockRenderDispatcher br, @Nullable BlockState st, float x, float y, float z, float w, float h, float d, PoseStack pose,
          MultiBufferSource buffers, int light) {
        if (st == null || w <= 0 || h <= 0 || d <= 0) {
            return;
        }
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(w, h, d);
        block(br, st, pose, buffers, light);
        pose.popPose();
    }
}
