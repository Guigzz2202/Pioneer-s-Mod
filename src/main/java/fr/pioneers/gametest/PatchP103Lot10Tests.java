package fr.pioneers.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.pioneers.Pioneers;
import fr.pioneers.api.PioneersAPI;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Patch 1.0.3-beta, lot 10 (GameTests) : les pages « Procede » (assets/pioneers/jei/process_pages.json, ecrit par
 * tools/gc/industry/process_pages.py) relues dans le jeu : chaque etape de chaque voie est une recette CHARGEE par le serveur, chaque
 * poste, objet, fluide et gaz cite existe ; quelques materiaux attendus (aluminium a 3 voies, titane, acier, silicium, circuit MKII).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP103Lot10Tests {

    private PatchP103Lot10Tests() {
    }

    private static JsonObject pages() {
        try (InputStream in = Pioneers.class.getResourceAsStream("/assets/pioneers/jei/process_pages.json")) {
            Gt.check(in != null, "process_pages.json absent du mod");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("lecture de process_pages.json : " + e);
        }
    }

    private static void exists(JsonElement node, String where) {
        if (node == null || node.isJsonNull()) {
            return;
        }
        String id = node.getAsJsonObject().get("id").getAsString();
        if (id.startsWith("fluid:")) {
            var f = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(id.substring(6)));
            Gt.check(f != null && f != Fluids.EMPTY, where + " : fluide inconnu " + id);
        } else if (id.startsWith("gas:")) {
            var g = PioneersAPI.gas(new ResourceLocation(id.substring(4)));
            Gt.check(g != null && !g.isEmptyType(), where + " : gaz inconnu " + id);
        } else {
            var it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            Gt.check(it != null && it != Items.AIR, where + " : objet inconnu " + id);
        }
    }

    // ================================================================== chaque etape = une recette chargee
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_10_processes", timeoutTicks = 20)
    public static void p103_10_etapes_des_procedes(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        MinecraftServer server = h.getLevel().getServer();
        JsonObject root = pages();
        int pages = 0, steps = 0;
        Set<String> ids = new HashSet<>();
        for (JsonElement pe : root.getAsJsonArray("pages")) {
            JsonObject p = pe.getAsJsonObject();
            String id = p.get("id").getAsString();
            ids.add(id);
            pages++;
            exists(p.get("product"), id);
            for (JsonElement re : p.getAsJsonArray("routes")) {
                JsonObject r = re.getAsJsonObject();
                exists(r.get("start"), id);
                for (String key : new String[]{"steps", "side"}) {
                    if (!r.has(key)) {
                        continue;
                    }
                    for (JsonElement se : r.getAsJsonArray(key)) {
                        JsonObject st = se.getAsJsonObject();
                        String recipe = st.get("recipe").getAsString();
                        Gt.check(server.getRecipeManager().byKey(new ResourceLocation(recipe)).isPresent(), id + " : recette non chargee " + recipe);
                        var station = ForgeRegistries.ITEMS.getValue(new ResourceLocation(st.get("station").getAsString()));
                        Gt.check(station != null && station != Items.AIR, id + " : poste inconnu " + st.get("station").getAsString());
                        exists(st.get("out"), id);
                        st.getAsJsonArray("reagents").forEach(x -> exists(x, id));
                        st.getAsJsonArray("byproducts").forEach(x -> exists(x, id));
                        steps++;
                    }
                }
            }
            if (id.equals("aluminium")) {
                Gt.check(p.getAsJsonArray("routes").size() >= 3, "aluminium : au moins 3 voies");
            }
        }
        for (String need : new String[]{"aluminium", "titanium", "steel", "stainless_steel", "silicon", "circuit_mk2", "fuel_assembly"}) {
            Gt.check(ids.contains(need), "page Procede absente : " + need);
        }
        Gt.check(pages >= 50 && steps >= 500, "pages Procede : " + pages + " pages, " + steps + " etapes");
        s.done();
    }
}
