package fr.pioneers.ruins.gen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.9 (MAJ RP, bloc 1) : gabarit d'une annexe en briques et en pierre ({@code data/pioneers/ruins/annexes/<type>_<n>.json}, ecrit par
 * {@code tools/gc/ruins/annex_gen.py}) : blocs vanilla seulement. Repere du gabarit : x en largeur, y vers le haut (0 = plancher pose au ras du
 * sol), z en profondeur ; la facade (porte) est au nord (z = 0). Couches : {@code layers[y][z]} = une ligne de {@code w} caracteres (indice
 * de palette dans {@link #ALPHABET}, {@code .} = air, espace = terrain laisse tel quel). Coffres : positions (table de butin posee par la
 * generation) ; cadres : cadres d'objet (photo, papier colle sur le tableau).
 */
public record AnnexTemplate(String type, int variant, int minTier, int w, int h, int d, BlockState[] palette, short[] cells, List<int[]> chests,
                            List<Frame> frames) {

    public static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    public static final short AIR = -1, KEEP = -2;

    /** Cadre d'objet : case (air), facade (vers l'exterieur du mur), objet. */
    public record Frame(int x, int y, int z, Direction facing, ResourceLocation item) {
    }

    public short cell(int x, int y, int z) {
        return cells[(y * d + z) * w + x];
    }

    public static AnnexTemplate parse(JsonObject o) throws Exception {
        JsonArray size = o.getAsJsonArray("size");
        int w = size.get(0).getAsInt(), h = size.get(1).getAsInt(), d = size.get(2).getAsInt();
        JsonArray pal = o.getAsJsonArray("palette");
        BlockState[] palette = new BlockState[pal.size()];
        for (int k = 0; k < palette.length; k++) {
            palette[k] = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), pal.get(k).getAsString(), false).blockState();
        }
        short[] cells = new short[w * h * d];
        JsonArray layers = o.getAsJsonArray("layers");
        for (int y = 0; y < h; y++) {
            JsonArray rows = layers.get(y).getAsJsonArray();
            for (int z = 0; z < d; z++) {
                String row = rows.get(z).getAsString();
                for (int x = 0; x < w; x++) {
                    char c = x < row.length() ? row.charAt(x) : ' ';
                    short v;
                    if (c == '.') {
                        v = AIR;
                    } else if (c == ' ') {
                        v = KEEP;
                    } else {
                        v = (short) ALPHABET.indexOf(c);
                        if (v < 0 || v >= palette.length) {
                            throw new IllegalArgumentException("caractere inconnu '" + c + "'");
                        }
                    }
                    cells[(y * d + z) * w + x] = v;
                }
            }
        }
        List<int[]> chests = new ArrayList<>();
        if (o.has("chests")) {
            for (JsonElement e : o.getAsJsonArray("chests")) {
                JsonArray p = e.getAsJsonArray();
                chests.add(new int[]{p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt()});
            }
        }
        List<Frame> frames = new ArrayList<>();
        if (o.has("frames")) {
            for (JsonElement e : o.getAsJsonArray("frames")) {
                JsonObject f = e.getAsJsonObject();
                JsonArray p = f.getAsJsonArray("pos");
                Direction dir = Direction.byName(f.get("facing").getAsString());
                frames.add(new Frame(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt(), dir == null ? Direction.SOUTH : dir,
                      new ResourceLocation(f.get("item").getAsString())));
            }
        }
        return new AnnexTemplate(o.get("type").getAsString(), o.has("variant") ? o.get("variant").getAsInt() : 0,
              o.has("min_tier") ? o.get("min_tier").getAsInt() : 1, w, h, d, palette, cells, List.copyOf(chests), List.copyOf(frames));
    }
}
