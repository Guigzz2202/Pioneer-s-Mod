package fr.pioneers.farm.aquarium;

import fr.pioneers.farm.PiFishing;
import fr.pioneers.farm.crustacean.Crustacean;
import fr.pioneers.farm.crustacean.GeneratedCrustaceans;
import fr.pioneers.farm.crustacean.PiCrustaceans;
import fr.pioneers.farm.fish.GeneratedFish;
import fr.pioneers.farm.fish.PiFish;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.4-beta (lot 28) : une case d'aquarium et ses HABITANTS (aucune entite : un poisson ou un crustace = espece, taille, graine du trajet).
 * Poissons : autant que les PLACES du bassin le permettent (1.0.5-beta lot 14, {@link AquariumRules}), {@link #FISH_MAX} au plus par case ;
 * {@link #CRUSTACEAN_MAX} crustaces par case ; pas d'elevage (ni faim, ni ponte, ni croissance).
 * Synchronisation complete (habitants) au client, qui les fait nager ({@code J:client/farm/aquarium/AquariumRenderer}). Case cassee : les
 * habitants sont relaches (entites) a sa place.
 * <p>
 * 1.0.5-beta (lot 25) : la COMPOSITION du fond ({@link AquariumDecor}) : {@link #ground} et {@link #piece} par emplacement (4 par case,
 * seulement dessinee sur une case du fond) ; un aquarium pose est VIDE (eau seule) ; case cassee : chaque element rend son objet.
 * Aquariums d'avant le lot 25 (NBT sans {@code decor_v}) : leur decor tire au hasard devient une composition equivalente
 * ({@link AquariumDecor#legacy}) au premier tick apres leur chargement ({@link #convertLegacy}), ses objets rendus comme les autres.
 */
public class AquariumTile extends BlockEntity {

    /**
     * 1.0.5-beta (lot 14) : le nombre de poissons se regle par les PLACES du bassin ({@link AquariumRules}) ; {@code FISH_MAX} ne borne plus
     * qu'une case (dessin : au plus 16 poissons, avant 4) ; crustaces : 2 par case.
     */
    public static final int FISH_MAX = 16, CRUSTACEAN_MAX = 2;

    /** Habitant : crustace ou poisson, espece (index de {@link GeneratedFish#SPECIES} / {@link GeneratedCrustaceans#SPECIES}), taille, graine. */
    public record Dweller(boolean crustacean, int species, float size, int seed) {
    }

    private final List<Dweller> dwellers = new ArrayList<>();
    /** 1.0.5-beta (lot 25) : sol et element de chaque emplacement (null = vide). */
    private final AquariumDecor.Kind[] ground = new AquariumDecor.Kind[AquariumDecor.SLOTS], piece = new AquariumDecor.Kind[AquariumDecor.SLOTS];
    /** NBT d'avant le lot 25 : decor a convertir. */
    private boolean legacy;

    public AquariumTile(BlockPos pos, BlockState state) {
        super(PiAquarium.TILE.get(), pos, state);
    }

    public List<Dweller> dwellers() {
        return dwellers;
    }

    public int count(boolean crustacean) {
        int n = 0;
        for (Dweller d : dwellers) {
            if (d.crustacean() == crustacean) {
                n++;
            }
        }
        return n;
    }

    /** 1.0.5-beta (lot 14) : places prises par les poissons de la case ({@link AquariumRules#places}). */
    public int places() {
        int n = 0;
        for (Dweller d : dwellers) {
            if (!d.crustacean()) {
                n += AquariumRules.places(GeneratedFish.SPECIES[Mth.clamp(d.species(), 0, GeneratedFish.SPECIES.length - 1)], d.size());
            }
        }
        return n;
    }

    public boolean hasRoom(boolean crustacean) {
        return count(crustacean) < (crustacean ? CRUSTACEAN_MAX : FISH_MAX);
    }

    /** Ajoute un habitant (faux si la case est pleine pour cette sorte). */
    public boolean add(boolean crustacean, int species, float size) {
        if (!hasRoom(crustacean)) {
            return false;
        }
        int seed = level == null ? dwellers.size() * 977 : level.random.nextInt();
        dwellers.add(new Dweller(crustacean, species, size, seed));
        changed();
        return true;
    }

    /** Reprend le dernier habitant mis (null s'il n'y en a pas). */
    public Dweller take() {
        if (dwellers.isEmpty()) {
            return null;
        }
        Dweller d = dwellers.remove(dwellers.size() - 1);
        changed();
        return d;
    }

    // ------------------------------------------------------------------ composition du fond (1.0.5-beta lot 25)

    public AquariumDecor.Kind ground(int slot) {
        return ground[slot];
    }

    public AquariumDecor.Kind piece(int slot) {
        return piece[slot];
    }

    /** Elements poses (sols et elements). */
    public int decorCount() {
        int n = 0;
        for (int i = 0; i < AquariumDecor.SLOTS; i++) {
            n += (ground[i] != null ? 1 : 0) + (piece[i] != null ? 1 : 0);
        }
        return n;
    }

    /** Pose (ou retire : null) un sol ({@code layer} 0) ou un element (1) ; rend la sorte d'avant (son objet est a rendre par l'appelant). */
    public AquariumDecor.Kind setDecor(int slot, int layer, AquariumDecor.Kind kind) {
        AquariumDecor.Kind[] arr = layer == 0 ? ground : piece;
        AquariumDecor.Kind old = arr[slot];
        arr[slot] = kind;
        changed();
        return old;
    }

    /** Objet d'une sorte (vide si l'objet n'existe pas). */
    public static ItemStack itemOf(AquariumDecor.Kind kind) {
        if (kind == null) {
            return ItemStack.EMPTY;
        }
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(kind.item()));
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** Objets de toute la composition (restitution a la casse). */
    public List<ItemStack> decorItems() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < AquariumDecor.SLOTS; i++) {
            for (AquariumDecor.Kind k : new AquariumDecor.Kind[]{ground[i], piece[i]}) {
                ItemStack st = itemOf(k);
                if (!st.isEmpty()) {
                    out.add(st);
                }
            }
        }
        return out;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (legacy && level instanceof ServerLevel server) {
            server.getServer().tell(new TickTask(server.getServer().getTickCount(), this::convertLegacy));
        }
    }

    /**
     * 1.0.5-beta (lot 25) : decor d'avant (tire au hasard par le rendu, sur toute case du fond) -> composition equivalente, gardee (choix :
     * l'aquarium du joueur ne change pas d'aspect ; ses elements se retirent et rendent leur objet comme les autres).
     */
    public void convertLegacy() {
        if (!legacy || isRemoved() || level == null || !level.isLoaded(worldPosition)) {
            return;
        }
        legacy = false;
        BlockState state = level.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof AquariumBlock) || level.getBlockState(worldPosition.below()).getBlock() instanceof AquariumBlock) {
            setChanged();
            return;
        }
        Direction[] sides = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        boolean[] glass = new boolean[4];
        for (int i = 0; i < 4; i++) {
            glass[i] = !(level.getBlockState(worldPosition.relative(sides[i])).getBlock() instanceof AquariumBlock);
        }
        String[][] c = AquariumDecor.legacy(legacySeed(worldPosition), glass);
        for (int i = 0; i < AquariumDecor.SLOTS; i++) {
            if (ground[i] == null) {
                ground[i] = AquariumDecor.of(c[0][i]);
            }
            if (piece[i] == null) {
                piece[i] = AquariumDecor.of(c[1][i]);
            }
        }
        changed();
    }

    /** Graine du decor d'une case d'avant le lot 25 (= l'ancien {@code AquariumRenderer.render}). */
    public static int legacySeed(BlockPos pos) {
        return (int) (pos.asLong() ^ pos.asLong() >>> 32) * 0x2F6B;
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    /** Case cassee : chaque habitant revient au monde (entite a sa place, avec son espece et sa taille). */
    public void release() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        for (Dweller d : dwellers) {
            if (d.crustacean()) {
                int sp = Mth.clamp(d.species(), 0, GeneratedCrustaceans.SPECIES.length - 1);
                var type = GeneratedCrustaceans.SPECIES[sp].aquatic() ? PiCrustaceans.AQUATIC.get() : PiCrustaceans.SHORE.get();
                Crustacean c = type.create(server);
                if (c != null) {
                    c.moveTo(worldPosition.getX() + 0.5, worldPosition.getY() + 0.1, worldPosition.getZ() + 0.5, server.random.nextFloat() * 360F, 0F);
                    c.set(sp, d.size());
                    c.setPersistenceRequired();
                    server.addFreshEntity(c);
                }
            } else if (fr.pioneers.farm.fish.FishSpawns.catchOnly(GeneratedFish.SPECIES[Mth.clamp(d.species(), 0, GeneratedFish.SPECIES.length - 1)])) {
                //1.0.5-beta (lot 25) : le requin gris redevient la vraie bete
                net.minecraft.world.entity.Mob shark = fr.pioneers.farm.fish.LiveRelease.liveFish(server, d.species(), d.size());
                if (shark != null) {
                    shark.moveTo(worldPosition.getX() + 0.5, worldPosition.getY() + 0.3, worldPosition.getZ() + 0.5, server.random.nextFloat() * 360F, 0F);
                    server.addFreshEntity(shark);
                }
            } else {
                PiFish f = PiFishing.speciesType(d.species()).create(server);
                if (f != null) {
                    f.moveTo(worldPosition.getX() + 0.5, worldPosition.getY() + 0.3, worldPosition.getZ() + 0.5, server.random.nextFloat() * 360F, 0F);
                    f.set(d.species(), d.size());
                    f.setFromBucket(true);
                    f.finalizeSpawn(server, server.getCurrentDifficultyAt(worldPosition), MobSpawnType.BUCKET, null, null);
                    f.set(d.species(), d.size());
                    server.addFreshEntity(f);
                }
            }
        }
        dwellers.clear();
        //1.0.5-beta (lot 25) : la composition rend ses objets
        for (ItemStack st : decorItems()) {
            Containers.dropItemStack(server, worldPosition.getX() + 0.5, worldPosition.getY() + 0.3, worldPosition.getZ() + 0.5, st);
        }
        java.util.Arrays.fill(ground, null);
        java.util.Arrays.fill(piece, null);
    }

    // ------------------------------------------------------------------ sauvegarde et synchro

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (Dweller d : dwellers) {
            CompoundTag t = new CompoundTag();
            t.putBoolean("crustacean", d.crustacean());
            t.putInt("species", d.species());
            t.putFloat("size", d.size());
            t.putInt("seed", d.seed());
            list.add(t);
        }
        tag.put("dwellers", list);
        //1.0.5-beta (lot 25) : composition (cles des sortes) ; decor_v : plus de decor tire au hasard
        CompoundTag decor = new CompoundTag();
        for (int i = 0; i < AquariumDecor.SLOTS; i++) {
            if (ground[i] != null) {
                decor.putString("g" + i, ground[i].key());
            }
            if (piece[i] != null) {
                decor.putString("p" + i, piece[i].key());
            }
        }
        tag.put("decor", decor);
        tag.putInt("decor_v", legacy ? 0 : 1);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        dwellers.clear();
        for (Tag x : tag.getList("dwellers", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) x;
            dwellers.add(new Dweller(t.getBoolean("crustacean"), t.getInt("species"), t.getFloat("size"), t.getInt("seed")));
        }
        CompoundTag decor = tag.getCompound("decor");
        for (int i = 0; i < AquariumDecor.SLOTS; i++) {
            ground[i] = decor.contains("g" + i) ? AquariumDecor.of(decor.getString("g" + i)) : null;
            piece[i] = decor.contains("p" + i) ? AquariumDecor.of(decor.getString("p" + i)) : null;
        }
        legacy = tag.contains("dwellers") && tag.getInt("decor_v") < 1;
    }

    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Les poissons nagent jusque dans les cases voisines du bassin : boite de rendu elargie d'un bloc ; 1.0.5-beta (lot 25) : et jusqu'a 8 blocs
     * au-dessus (varech d'une haute colonne, nenuphars a la surface, dessines par la case du fond).
     */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.0).expandTowards(0, 8, 0);
    }
}
