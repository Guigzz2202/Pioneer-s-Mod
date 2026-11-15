package fr.pioneers.core.block;

import fr.pioneers.Pioneers;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.kinetics.parts.ShaftBlock;
import fr.pioneers.transport.logistic.LogisticCableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 27, Guillaume : « ajouter un moyen de couvrir les cables logistiques et les arbres de rotation avec un bloc pour les
 * camoufler ») : les CACHES. 1.0.4-beta (lot 30, Guillaume : « on implemente un block de frame (en bois) c'est le seul avec lequel on peut
 * clic droit pour ajouter le cover. Ensuite on clic droit avec un bloc pour donner la texture, la lime permet de retirer le cover du frame et
 * si la frame n'a pas de cover, la lime retire la frame, la cle a molette permet de voir et config les cables ») : en DEUX TEMPS.
 * <ol>
 *     <li>clic droit avec un CADRE DE CACHE ({@link PiCoverFrame}) sur un cable logistique ou un arbre de rotation nu : le cadre s'installe
 *     autour ({@link #FRAMED}) ; le cable ou l'arbre reste visible dedans et continue de travailler ;</li>
 *     <li>clic droit sur le cadre avec un bloc PLEIN (cube opaque, sans tuile ni lumiere : pierre, planches, briques, buche orientee...) :
 *     le bloc devient la cache ({@link #COVERED}), dessinee a sa place (vrai modele du bloc, dans le maillage du tronçon), forme et durete
 *     du bloc. C'est le SEUL moyen de poser une cache : un bloc plein sur un cable nu se pose a cote, comme avant le lot 27 ;</li>
 *     <li>LIME (tag {@code pioneers:tools/files}) : sur un cadre avec cache, retire la cache (le bloc est rendu) ; sur un cadre sans cache,
 *     retire le cadre (rendu) ; un point d'usure par retrait ;</li>
 *     <li>CLE A MOLETTE : rien ne change par rapport au cable ou a l'arbre nu (cable : couper / retablir le cote vise, Maj + cle : le
 *     diagramme ; arbre : fiche du reseau) ; tant qu'on tient une cle, les cables et les arbres caches se voient au travers
 *     ({@code client/model/CoverXRay}) ;</li>
 *     <li>CASSER : la cache d'abord (rendue), puis le cadre (rendu), puis le cable ou l'arbre ; explosion ou remplacement : tout tombe.</li>
 * </ol>
 * Mondes du lot 27 : un bloc {@link #COVERED} sans {@link #FRAMED} a un cadre implicite ({@link #framed}) : cache + cadre, rien de perdu.
 * Sauvegarde de la cache dans la tuile ({@link #KEY}) ; le cadre est dans l'etat du bloc.
 */
public final class Covers {

    /** Cle de sauvegarde (etat du bloc de cache) ; propriete du modele (client) : l'etat dessine a la place. */
    public static final String KEY = "cover";
    public static final ModelProperty<BlockState> COVER = new ModelProperty<>();
    /**
     * Propriete d'etat des blocs a cache : forme pleine, lumiere et ombre d'un bloc plein (caches de Minecraft par etat), tuile creee des deux
     * cotes (un cable nu n'en a pas sinon). La cache elle-meme (quel bloc) est dans la tuile.
     */
    public static final BooleanProperty COVERED = BooleanProperty.create("covered");
    /**
     * 1.0.4-beta (lot 30) : cadre de cache installe (forme pleine ; lumiere, ciel et eau passent tant qu'il n'a pas de cache). Une cache
     * suppose un cadre : {@link #framed} vaut aussi pour un etat {@link #COVERED} du lot 27 sans ce booleen.
     */
    public static final BooleanProperty FRAMED = BooleanProperty.create("framed");
    /** 1.0.4-beta (lot 30) : les limes du mod (outils d'atelier) : retirent la cache, puis le cadre. */
    public static final TagKey<Item> FILES = ItemTags.create(Pioneers.rl("tools/files"));

    private Covers() {
    }

    /** Une tuile qui peut porter une cache. */
    public interface Coverable {

        /** Etat du bloc de cache, ou null. */
        @Nullable
        BlockState cover();

        /** Pose ou retire ({@code null}) la cache (sauvegarde et envoi au client compris). */
        void setCover(@Nullable BlockState cover);
    }

    /** Le bloc {@code state} peut-il servir de cache (cube plein et opaque, rendu par son modele, sans tuile, ni cable ni arbre) ? */
    public static boolean allowed(BlockGetter level, BlockPos pos, @Nullable BlockState state) {
        if (state == null || state.isAir() || state.getBlock() instanceof EntityBlock || state.getRenderShape() != RenderShape.MODEL) {
            return false;
        }
        if (state.getBlock() instanceof LogisticCableBlock || state.getBlock() instanceof ShaftBlock || state.hasProperty(BlockStateProperties.WATERLOGGED)
            || state.getLightEmission() > 0) {
            return false;                                                //Un bloc lumineux ne pourrait pas eclairer depuis sa cache
        }
        return state.canOcclude() && state.isCollisionShapeFullBlock(level, pos);
    }

    /** Cache posee sur la tuile en {@code pos}, ou null. */
    @Nullable
    public static BlockState at(@Nullable BlockGetter level, BlockPos pos) {
        return level != null && level.getBlockEntity(pos) instanceof Coverable c ? c.cover() : null;
    }

    /**
     * Clic droit sur un cable ou un arbre ({@code state}, qui porte {@link #COVERED} et {@link #FRAMED}) : cadre de cache en main sur un
     * bloc nu : le cadre s'installe ; lime sur un cadre : retire la cache, sinon le cadre ; bloc plein sur un cadre sans cache : il devient
     * la cache (etat de pose d'apres le joueur : une buche garde son axe ; la tuile est creee au besoin, des deux cotes). Null si ce n'est
     * aucun de ces cas (le clic suit son cours : pose a cote, diagramme, cle).
     */
    @Nullable
    public static InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (!state.hasProperty(COVERED) || !state.hasProperty(FRAMED) || player.isShiftKeyDown() || held.isEmpty()) {
            return null;
        }
        if (held.is(PiCoverFrame.FRAME.asItem())) {
            return framed(state) ? null : installFrame(state, level, pos, player, held);
        }
        if (held.is(FILES)) {
            return framed(state) ? file(state, level, pos, player, hand, held) : null;
        }
        if (!(held.getItem() instanceof BlockItem item) || !framed(state) || covered(state)) {
            return null;
        }
        Block block = item.getBlock();
        if (block instanceof EntityBlock || !block.defaultBlockState().canOcclude()) {
            return null;
        }
        BlockState cover = block.getStateForPlacement(new BlockPlaceContext(player, hand, held, hit));
        if (cover == null || cover.getBlock() != block) {
            cover = block.defaultBlockState();
        }
        if (!allowed(level, pos, cover)) {
            return null;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!ClanApi.canModify(player, level, pos, ClanApi.Action.PLACE)) {
            return InteractionResult.FAIL;
        }
        BlockState next = state.setValue(COVERED, true).setValue(FRAMED, true);
        if (next.hasProperty(BlockStateProperties.WATERLOGGED)) {
            next = next.setValue(BlockStateProperties.WATERLOGGED, false);    //Une cache est un bloc plein : au sec
        }
        level.setBlock(pos, next, Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof Coverable tile)) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
            return null;
        }
        tile.setCover(cover);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        SoundType sound = cover.getSoundType(level, pos, player);
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2F, sound.getPitch() * 0.8F);
        return InteractionResult.CONSUME;
    }

    /**
     * 1.0.5-beta (lot 23, Guillaume : « ajouter la possibilite de placer un frame en bois sur les engrenages (cela deconnecte l'axe au
     * centre, mais permet toujours les connexions entre engrenages) ») : blocs qui portent seulement {@link #FRAMED} (pas de cache : les
     * engrenages). Cadre de cache en main sur un bloc sans cadre : le cadre s'installe ; lime sur un bloc encadre : le cadre est rendu (un
     * point d'usure). Null : rien a faire ici (le bloc garde son usage). Maj ignore (la cle et l'accroupi gardent leur role).
     */
    @Nullable
    public static InteractionResult useFrameOnly(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!state.hasProperty(FRAMED) || state.hasProperty(COVERED) || player.isShiftKeyDown() || held.isEmpty()) {
            return null;
        }
        if (held.is(PiCoverFrame.FRAME.asItem())) {
            return framed(state) ? null : installFrame(state, level, pos, player, held);
        }
        if (held.is(FILES)) {
            return framed(state) ? file(state, level, pos, player, hand, held) : null;
        }
        return null;
    }

    /** 1.0.4-beta (lot 30) : le cadre de cache s'installe autour du cable ou de l'arbre nu (un cadre pris). */
    private static InteractionResult installFrame(BlockState state, Level level, BlockPos pos, Player player, ItemStack held) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!ClanApi.canModify(player, level, pos, ClanApi.Action.PLACE)) {
            return InteractionResult.FAIL;
        }
        level.setBlock(pos, state.setValue(FRAMED, true), Block.UPDATE_ALL);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        SoundType sound = frameState().getSoundType();
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2F, sound.getPitch() * 0.8F);
        return InteractionResult.CONSUME;
    }

    /** 1.0.4-beta (lot 30) : lime sur un cadre : retire la cache (rendue), sinon le cadre (rendu) ; un point d'usure. */
    private static InteractionResult file(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack held) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!ClanApi.canModify(player, level, pos, ClanApi.Action.BREAK)) {
            return InteractionResult.FAIL;
        }
        BlockState cover = covered(state) ? at(level, pos) : null;
        ItemStack back;
        BlockState shown;
        if (covered(state)) {
            if (level.getBlockEntity(pos) instanceof Coverable c) {
                c.setCover(null);
            }
            level.setBlock(pos, state.setValue(COVERED, false).setValue(FRAMED, true), Block.UPDATE_ALL);
            back = cover == null ? ItemStack.EMPTY : new ItemStack(cover.getBlock());
            shown = cover == null ? frameState() : cover;
        } else {
            level.setBlock(pos, state.setValue(FRAMED, false), Block.UPDATE_ALL);
            back = new ItemStack(PiCoverFrame.FRAME.asItem());
            shown = frameState();
        }
        level.levelEvent(null, 2001, pos, Block.getId(shown));             //Particules et son du bloc retire
        if (!back.isEmpty() && !player.getAbilities().instabuild) {
            ItemHandlerHelper.giveItemToPlayer(player, back);
        }
        held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        return InteractionResult.CONSUME;
    }

    /** 1.0.4-beta (lot 30) : etat du bloc cadre de cache (modele, son, durete, particules). */
    public static BlockState frameState() {
        return PiCoverFrame.FRAME.getBlock().defaultBlockState();
    }

    /**
     * Le joueur casse un bloc cache ou encadre : seule la couche du dessus part (rendue hors creatif, particules et son du bloc) : la cache
     * d'abord, puis le cadre ; vrai si c'etait le cas (le cable ou l'arbre reste : {@code onDestroyedByPlayer} rend faux). Cote client :
     * rien ne change (le serveur renvoie l'etat).
     */
    public static boolean breakCover(BlockState state, Level level, BlockPos pos, Player player) {
        if (!framed(state)) {
            return false;
        }
        BlockState cover = covered(state) ? at(level, pos) : null;
        level.levelEvent(player, 2001, pos, Block.getId(cover != null ? cover : frameState()));    //Particules et son (le joueur les a chez lui)
        if (level.isClientSide) {
            return true;
        }
        ItemStack back;
        if (covered(state)) {
            if (level.getBlockEntity(pos) instanceof Coverable c) {
                c.setCover(null);
            }
            level.setBlock(pos, state.setValue(COVERED, false).setValue(FRAMED, true), Block.UPDATE_ALL);
            back = cover == null ? ItemStack.EMPTY : new ItemStack(cover.getBlock());
        } else {
            level.setBlock(pos, state.setValue(FRAMED, false), Block.UPDATE_ALL);
            back = new ItemStack(PiCoverFrame.FRAME.asItem());
        }
        if (!back.isEmpty() && !player.getAbilities().instabuild) {
            Block.popResource(level, pos, back);
        }
        return true;
    }

    /** Le bloc cache disparait (explosion, surcharge, remplacement par un autre bloc) : la cache et le cadre tombent en objets. */
    public static void dropOnRemove(BlockState state, Level level, BlockPos pos, BlockState next) {
        if (level.isClientSide || next.getBlock() == state.getBlock()) {
            return;
        }
        BlockState cover = covered(state) ? at(level, pos) : null;
        if (cover != null) {
            Block.popResource(level, pos, new ItemStack(cover.getBlock()));
        }
        if (framed(state)) {
            Block.popResource(level, pos, new ItemStack(PiCoverFrame.FRAME.asItem()));
        }
    }

    /** Durete de casse : celle de la cache quand il y en a une, sinon celle du cadre. */
    public static float progress(BlockState state, Player player, BlockGetter level, BlockPos pos, float normal) {
        BlockState cover = covered(state) ? at(level, pos) : null;
        if (cover != null) {
            return cover.getDestroyProgress(player, level, pos);
        }
        return framed(state) ? frameState().getDestroyProgress(player, level, pos) : normal;
    }

    /** Etat du bloc a cache : cache posee (forme pleine, lumiere arretee) ? */
    public static boolean covered(BlockState state) {
        return state.hasProperty(COVERED) && state.getValue(COVERED);
    }

    /** 1.0.4-beta (lot 30) : cadre de cache installe (forme pleine) ; une cache du lot 27 sans le booleen compte comme cadre + cache. */
    public static boolean framed(BlockState state) {
        return covered(state) || state.hasProperty(FRAMED) && state.getValue(FRAMED);
    }

    /** Cache dessinee ou rendue (etat couvert ET cache connue de la tuile), ou null. */
    @Nullable
    public static BlockState coverOf(BlockState state, @Nullable BlockGetter level, BlockPos pos) {
        return covered(state) ? at(level, pos) : null;
    }

    /** Son du bloc : celui de la cache quand il y en a une, sinon celui du cadre. */
    public static SoundType sound(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity, SoundType normal) {
        BlockState cover = coverOf(state, level, pos);
        if (cover != null) {
            return cover.getSoundType(level, pos, entity);
        }
        return framed(state) ? frameState().getSoundType() : normal;
    }

    /** Objet du choix du bloc (clic molette) : la cache quand il y en a une, sinon le cadre, sinon null. */
    @Nullable
    public static ItemStack pick(BlockState state, BlockGetter level, BlockPos pos) {
        BlockState cover = coverOf(state, level, pos);
        if (cover != null) {
            return new ItemStack(cover.getBlock());
        }
        return framed(state) ? new ItemStack(PiCoverFrame.FRAME.asItem()) : null;
    }

    public static void save(CompoundTag tag, @Nullable BlockState cover) {
        if (cover != null) {
            tag.put(KEY, NbtUtils.writeBlockState(cover));
        }
    }

    @Nullable
    public static BlockState load(CompoundTag tag) {
        if (!tag.contains(KEY)) {
            return null;
        }
        BlockState s = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound(KEY));
        return s.isAir() ? null : s;
    }

    /** Client : la cache a change : le tronçon est remaille (la cache est dessinee par le modele du bloc). */
    public static void changedOnClient(BlockEntity be) {
        Level level = be.getLevel();
        if (level != null && level.isClientSide) {
            be.requestModelDataUpdate();
            level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), Block.UPDATE_IMMEDIATE);
        }
    }
}
