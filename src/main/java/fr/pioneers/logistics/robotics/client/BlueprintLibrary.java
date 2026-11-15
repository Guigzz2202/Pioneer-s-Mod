package fr.pioneers.logistics.robotics.client;

import com.mojang.blaze3d.platform.NativeImage;
import fr.pioneers.Pioneers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

/**
 * Bibliotheque de plans du joueur (comme dans Factorio) : des fichiers {@code .nbt} compresses dans
 * {@code <dossier du jeu>/pioneers/plans}, communs a tous les mondes. Chaque plan a une vignette (vue de dessus) faite a la volee.
 */
public final class BlueprintLibrary {

    /** Plan range : fichier, nom, taille, nombre de blocs, date de modification. */
    public record Entry(String id, String name, int sx, int sy, int sz, int blocks, long modified) {}

    private static List<Entry> entries;
    private static final Map<String, Blueprint> LOADED = new HashMap<>();
    private static final Map<String, ResourceLocation> THUMBS = new HashMap<>();

    private BlueprintLibrary() {
    }

    public static Path folder() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("pioneers").resolve("plans");
    }

    /** Les plans ranges, les plus recents d'abord. */
    public static List<Entry> list() {
        if (entries == null) {
            refresh();
        }
        return entries;
    }

    public static void refresh() {
        List<Entry> list = new ArrayList<>();
        LOADED.clear();
        Path dir = folder();
        if (Files.isDirectory(dir)) {
            try (var stream = Files.list(dir)) {
                for (Path file : stream.toList()) {
                    String name = file.getFileName().toString();
                    if (!name.endsWith(".nbt")) {
                        continue;
                    }
                    String id = name.substring(0, name.length() - 4);
                    Blueprint print = read(id);
                    if (print != null) {
                        list.add(new Entry(id, print.name(), print.sx(), print.sy(), print.sz(), print.solidCount(), Files.getLastModifiedTime(file).toMillis()));
                    }
                }
            } catch (IOException e) {
                Pioneers.LOGGER.warn("Plans : lecture du dossier impossible", e);
            }
        }
        list.sort((a, b) -> Long.compare(b.modified(), a.modified()));
        entries = list;
    }

    @Nullable
    public static Blueprint get(String id) {
        Blueprint print = LOADED.get(id);
        return print != null ? print : read(id);
    }

    @Nullable
    private static Blueprint read(String id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        Path file = folder().resolve(id + ".nbt");
        try {
            CompoundTag tag = NbtIo.readCompressed(file.toFile());
            Blueprint print = Blueprint.load(id, tag, minecraft.level.holderLookup(Registries.BLOCK));
            LOADED.put(id, print);
            return print;
        } catch (IOException | RuntimeException e) {
            Pioneers.LOGGER.warn("Plans : fichier illisible {}", file, e);
            return null;
        }
    }

    /** Range le plan (nouveau fichier si {@code id} est null) ; renvoie le plan avec son identifiant, ou null en cas d'echec. */
    @Nullable
    public static Blueprint save(Blueprint print) {
        String id = print.id() != null ? print.id() : newId(print.name());
        Blueprint saved = print.withName(id, print.name());
        try {
            Files.createDirectories(folder());
            NbtIo.writeCompressed(saved.save(), folder().resolve(id + ".nbt").toFile());
        } catch (IOException e) {
            Pioneers.LOGGER.warn("Plans : enregistrement impossible", e);
            return null;
        }
        dropThumb(id);
        refresh();
        return saved;
    }

    public static void delete(String id) {
        try {
            Files.deleteIfExists(folder().resolve(id + ".nbt"));
        } catch (IOException e) {
            Pioneers.LOGGER.warn("Plans : suppression impossible", e);
        }
        dropThumb(id);
        refresh();
    }

    public static void rename(String id, String name) {
        Blueprint print = get(id);
        if (print != null && !name.isBlank()) {
            save(print.withName(id, name.trim()));
        }
    }

    @Nullable
    public static Blueprint duplicate(String id, String suffix) {
        Blueprint print = get(id);
        return print == null ? null : save(print.withName(null, print.name() + " " + suffix));
    }

    private static String newId(String name) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "plan";
        }
        if (base.length() > 32) {
            base = base.substring(0, 32);
        }
        String id = base;
        for (int i = 2; Files.exists(folder().resolve(id + ".nbt")); i++) {
            id = base + "_" + i;
        }
        return id;
    }

    // ------------------------------------------------------------------
    // Vignettes : vue de dessus, couleur de carte du bloc le plus haut, ombree selon la hauteur
    // ------------------------------------------------------------------

    private static void dropThumb(String id) {
        ResourceLocation old = THUMBS.remove(id);
        if (old != null) {
            Minecraft.getInstance().getTextureManager().release(old);
        }
    }

    /** Vignette d'un plan range (texture de sx x sz pixels). */
    @Nullable
    public static ResourceLocation thumbnail(String id) {
        ResourceLocation known = THUMBS.get(id);
        if (known != null) {
            return known;
        }
        Blueprint print = get(id);
        if (print == null) {
            return null;
        }
        ResourceLocation location = thumbnail(print, "plan_" + id.replaceAll("[^a-z0-9_]", "_"));
        THUMBS.put(id, location);
        return location;
    }

    /** Vignette d'un plan quelconque, enregistree sous ce nom (remplace la precedente). */
    public static ResourceLocation thumbnail(Blueprint print, String key) {
        int sx = Math.max(1, Math.min(256, print.sx())), sz = Math.max(1, Math.min(256, print.sz()));
        NativeImage image = new NativeImage(sx, sz, true);
        int[] top = new int[sx * sz];
        BlockState[] states = new BlockState[sx * sz];
        Arrays.fill(top, Integer.MIN_VALUE);
        for (Map.Entry<BlockPos, BlockState> entry : print.blocks().entrySet()) {
            BlockPos p = entry.getKey();
            if (entry.getValue().isAir() || p.getX() >= sx || p.getZ() >= sz) {
                continue;
            }
            int i = p.getZ() * sx + p.getX();
            if (p.getY() > top[i]) {
                top[i] = p.getY();
                states[i] = entry.getValue();
            }
        }
        for (int z = 0; z < sz; z++) {
            for (int x = 0; x < sx; x++) {
                int i = z * sx + x;
                int argb;
                if (states[i] == null) {
                    argb = 0x00000000;
                } else {
                    MapColor color = states[i].getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                    int rgb = color == MapColor.NONE ? 0x808080 : color.col;
                    //Ombre selon la hauteur, et relief avec la case au nord
                    float shade = 0.65F + 0.35F * (print.sy() <= 1 ? 1 : top[i] / (float) (print.sy() - 1));
                    if (z > 0 && top[i - sx] > top[i]) {
                        shade *= 0.85F;
                    }
                    int r = Math.min(255, (int) ((rgb >> 16 & 255) * shade)), g = Math.min(255, (int) ((rgb >> 8 & 255) * shade)),
                          b = Math.min(255, (int) ((rgb & 255) * shade));
                    argb = 0xFF000000 | r << 16 | g << 8 | b;
                }
                //NativeImage : ABGR
                image.setPixelRGBA(x, z, (argb & 0xFF00FF00) | (argb >> 16 & 0xFF) | (argb & 0xFF) << 16);
            }
        }
        ResourceLocation location = Pioneers.rl("dynamic/" + key);
        Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(image));
        return location;
    }
}
