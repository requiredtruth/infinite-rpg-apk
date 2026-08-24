package app.infiniterpg.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public final class ContentRepository extends SQLiteOpenHelper {
    private static final String DB_NAME = "infinite-rpg.db";
    private static final int DB_VERSION = 4;
    private final Object lock = new Object();

    public ContentRepository(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
        db.enableWriteAheadLogging();
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE content(id INTEGER PRIMARY KEY, type TEXT NOT NULL, identity_key TEXT NOT NULL, name TEXT NOT NULL, json TEXT NOT NULL, signature TEXT NOT NULL, source TEXT NOT NULL, judge_score REAL NOT NULL DEFAULT 1, created_at INTEGER NOT NULL, UNIQUE(type,identity_key))");
        db.execSQL("CREATE INDEX content_type_idx ON content(type,created_at)");
        db.execSQL("CREATE TABLE jobs(id INTEGER PRIMARY KEY, type TEXT NOT NULL, prompt TEXT NOT NULL, priority INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'queued', attempts INTEGER NOT NULL DEFAULT 0, last_error TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX jobs_queue_idx ON jobs(status,priority,id)");
        db.execSQL("CREATE TABLE inventory(identity_key TEXT PRIMARY KEY, quantity INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE player(id INTEGER PRIMARY KEY CHECK(id=1), x REAL NOT NULL, y REAL NOT NULL, health INTEGER NOT NULL, energy INTEGER NOT NULL, world_seed INTEGER NOT NULL)");
        db.execSQL("INSERT INTO player(id,x,y,health,energy,world_seed) VALUES(1,0,0,100,100,?)", new Object[]{System.currentTimeMillis()});
        db.execSQL("CREATE TABLE state(key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        db.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY, level TEXT NOT NULL, message TEXT NOT NULL, created_at INTEGER NOT NULL)");
        createWorldTables(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion<2) db.execSQL("INSERT OR REPLACE INTO state(key,value) VALUES('upgrade_visuals','pending')");
        if(oldVersion<3) createWorldTables(db);
        if(oldVersion>=3&&oldVersion<4){db.execSQL("ALTER TABLE structures ADD COLUMN natural_key TEXT");db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS structures_natural_idx ON structures(natural_key)");}
    }

    private void createWorldTables(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS structures(id INTEGER PRIMARY KEY,type TEXT NOT NULL,x REAL NOT NULL,y REAL NOT NULL,state TEXT NOT NULL DEFAULT 'complete',json TEXT NOT NULL DEFAULT '{}',created_at INTEGER NOT NULL,natural_key TEXT UNIQUE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS structures_pos_idx ON structures(x,y)");
        db.execSQL("CREATE TABLE IF NOT EXISTS chest_storage(structure_id INTEGER NOT NULL,identity_key TEXT NOT NULL,quantity INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(structure_id,identity_key),FOREIGN KEY(structure_id) REFERENCES structures(id) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE IF NOT EXISTS mobs(id INTEGER PRIMARY KEY,species TEXT NOT NULL,x REAL NOT NULL,y REAL NOT NULL,health INTEGER NOT NULL,born_at INTEGER NOT NULL,love_until INTEGER NOT NULL DEFAULT 0,cooldown_until INTEGER NOT NULL DEFAULT 0,spawn_key TEXT UNIQUE NOT NULL,created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS mobs_pos_idx ON mobs(x,y)");
        db.execSQL("CREATE TABLE IF NOT EXISTS content_control(type TEXT NOT NULL,identity_key TEXT NOT NULL,disabled INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(type,identity_key))");
    }

    public void bootstrap() {
        synchronized (lock) {
            if (count("content") == 0) seedPlayableCore();
            seedExpandedPresets();
            patchCoreVisuals();
            grantStarterPackIfNeeded();
            ensureRequiredJobs();
            putStateIfAbsent("generator_enabled", "true");
            putStateIfAbsent("generation_interval_seconds", "120");
            putStateIfAbsent("max_tokens", "360");
            putStateIfAbsent("context_tokens", "1280");
            if(!Boolean.parseBoolean(getState("generation_safety_v1","false"))){putState("generation_interval_seconds","120");putState("max_tokens","420");putState("context_tokens","1536");putState("generation_inflight","false");putState("gameplay_active","false");putState("generation_safety_v1","true");}
            if(!"q8_0_quality".equals(getState("generation_model_variant",""))){putState("generation_model_variant","q8_0_quality");putState("model_status","quality Q8 required — press Start download");putState("model_runtime","waiting for quality Q8");putState("last_result","The repaired generator now uses the approximately 609 MiB Q8 model. Press Start download, then Start generations.");putState("download_bytes","0");putState("download_total","-1");putState("max_tokens","420");putState("context_tokens","1280");putState("generator_enabled","false");putState("generation_inflight","false");putState("stream_status","waiting for Q8 download");}
            if(!Boolean.parseBoolean(getState("model_manager_v1","false"))){putState("selected_model_key","qwen25_q8");putState("selected_model_name","Qwen2.5 0.5B Instruct Q8 • recommended");putState("selected_model_file","qwen2.5-0.5b-instruct-q8_0.gguf");putState("model_context","1280");putState("model_max_tokens","420");putState("model_threads","1");putState("model_temperature","0.32");putState("model_template","chatml");putState("active_model_key","");putState("model_status","recommended structured-JSON model selected — press Start download");putState("model_runtime","waiting for selected model");putState("generator_enabled","false");putState("generation_inflight","false");putState("model_manager_v1","true");}
            putStateIfAbsent("music_enabled", "true");
            putStateIfAbsent("music_volume", "62");
            putStateIfAbsent("music_track_offset", "0");
            putStateIfAbsent("sounds_enabled", "true");
            putStateIfAbsent("sounds_volume", "58");
            putStateIfAbsent("world_name", "Infinite RPG World");
            putStateIfAbsent("world_clock_start", Long.toString(System.currentTimeMillis()));
            putStateIfAbsent("catalog_revision", "0");
        }
    }

    private long count(String table) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + table, null)) { c.moveToFirst(); return c.getLong(0); }
    }

    private void seedPlayableCore() {
        try {
            addSeed("BIOME","verdant_reach","Verdant Reach", new JSONObject().put("name","Verdant Reach").put("palette",new JSONArray("[\"#245B3A\",\"#3F8F4F\",\"#78C850\",\"#163326\"]")).put("texture_cell",0).put("noise_min",0.25).put("noise_max",0.62).put("danger",1));
            addSeed("BIOME","sunscorch_expanse","Sunscorch Expanse", new JSONObject().put("name","Sunscorch Expanse").put("palette",new JSONArray("[\"#9C5B28\",\"#D9983D\",\"#F1C75B\",\"#5C321F\"]")).put("texture_cell",4).put("noise_min",0.62).put("noise_max",0.83).put("danger",2));
            addSeed("BIOME","frostglass_wilds","Frostglass Wilds", new JSONObject().put("name","Frostglass Wilds").put("palette",new JSONArray("[\"#8BC6D9\",\"#D8F1F2\",\"#5E89A1\",\"#2E5066\"]")).put("texture_cell",8).put("noise_min",0.83).put("noise_max",1.0).put("danger",3));
            addSeed("BIOME","mire_of_echoes","Mire of Echoes", new JSONObject().put("name","Mire of Echoes").put("palette",new JSONArray("[\"#334B35\",\"#556B2F\",\"#7F8F45\",\"#1F3028\"]")).put("texture_cell",12).put("noise_min",0.0).put("noise_max",0.25).put("danger",2));
            addMaterial("wood","Heartwood", "#9B6B3E", 1, "verdant_reach");
            addMaterial("stone","Fieldstone", "#8A8F98", 1, "verdant_reach");
            addMaterial("fiber","Sungrass Fiber", "#C5B35A", 1, "sunscorch_expanse");
            addMaterial("crystal","Frostglass", "#68D5E8", 3, "frostglass_wilds");
            addItem("wood_axe","Heartwood Axe","tool",4,"#C78B46");
            addItem("stone_pick","Fieldstone Pick","tool",6,"#A5ABB4");
            addItem("trail_ration","Trail Ration","food",2,"#E0B04B");
            addRecipe("wood_axe","Wood Axe", "{\"wood\":5,\"stone\":2}");
            addRecipe("stone_pick","Stone Pick", "{\"wood\":3,\"stone\":6}");
            addRecipe("trail_ration","Trail Ration", "{\"fiber\":3}");
            addSeed("MUSIC","first_horizon","First Horizon", beautifulMusic());
            log("INFO", "Playable core catalog created");
        } catch (JSONException e) { throw new IllegalStateException(e); }
    }

    private JSONObject beautifulMusic() throws JSONException {
        return new JSONObject().put("name","First Horizon").put("description","A warm, evolving exploration theme with breathing strings, harp, flute and low hand drums.").put("tempo",82).put("scale","dorian").put("root",50).put("wave","triangle")
            .put("chords",new JSONArray("[0,3,5,2,0,5,3,4]")).put("melody",new JSONArray("[7,-1,9,10,-1,9,7,5,3,-1,5,7,9,7,5,-1,7,9,12,-1,10,9,7,5,3,5,7,-1,5,3,2,-1]"))
            .put("bass",new JSONArray("[0,-1,0,-1,3,-1,5,-1,0,-1,5,-1,3,-1,4,-1]")).put("rhythm",new JSONArray("[1,0,0,1,0,0,1,0,1,0,0,1,0,1,0,0]"));
    }

    private void seedExpandedPresets() {
        try {
            addSeed("MATERIAL","healing_herb","Silverleaf Herb",new JSONObject().put("name","Silverleaf Herb").put("description","A fragrant restorative herb found near water.").put("color","#72B86C").put("rarity",2).put("biome","verdant_reach").put("gather_min",1).put("gather_max",3).put("atlas_cell",7));
            addSeed("MATERIAL","iron_ingot","Forged Iron",new JSONObject().put("name","Forged Iron").put("description","Dense iron ready for serious equipment.").put("color","#6E747B").put("rarity",2).put("biome","sunscorch_expanse").put("gather_min",1).put("gather_max",2).put("atlas_cell",12));
            addPresetItem("forged_sword","Wayfarer Sword","weapon",12,"#AEB8C4",8);
            addPresetItem("leather_cuirass","Trailwarden Cuirass","armor",10,"#8A5735",9);
            addPresetItem("healing_potion","Frostblue Tonic","food",8,"#39AADD",10);
            addPresetItem("brass_lantern","Brass Trail Lantern","tool",3,"#D99532",11);
            addPresetItem("rope_coil","Climber's Rope","tool",2,"#9B7044",13);
            addPresetItem("jeweled_compass","Infinite RPG Compass","curio",5,"#D5A63B",14);
            addPresetItem("ancient_key","Starward Key","curio",7,"#D89D37",15);
            addRecipe("forged_sword","Wayfarer Sword","{\"wood\":2,\"iron_ingot\":5}");
            addRecipe("leather_cuirass","Trailwarden Cuirass","{\"fiber\":8,\"healing_herb\":2}");
            addRecipe("healing_potion","Frostblue Tonic","{\"healing_herb\":4,\"crystal\":1}");
            addRecipe("brass_lantern","Brass Trail Lantern","{\"iron_ingot\":3,\"crystal\":1}");
            addRecipe("rope_coil","Climber's Rope","{\"fiber\":7}");
            addPixelTexture("meadow_bloom","Meadow Bloom","verdant_reach",new String[]{"#1F4B2B","#2F6D38","#4C9145","#79B94F","#D7D36A","#EDE9CB"},0);
            addPixelTexture("sand_ripple","Sand Ripple","sunscorch_expanse",new String[]{"#6E3E21","#9D642F","#C98A43","#E6B85C","#F2D27B","#FFF0B2"},1);
            addPixelTexture("frost_crackle","Frost Crackle","frostglass_wilds",new String[]{"#27495E","#3F7189","#78AFC0","#B7DCE2","#E7F5F4","#FFFFFF"},2);
            addPixelTexture("mire_lights","Mire Lights","mire_of_echoes",new String[]{"#152A24","#294536","#44623B","#718044","#A9B85D","#D5E879"},3);
            seedSurvivalSystems();
            seedMusicLibrary();
        } catch(JSONException e){log("ERROR","Preset visual catalog: "+e.getMessage());}
    }

    private void seedSurvivalSystems() throws JSONException {
        addSeed("MATERIAL","raw_meat","Raw Game Meat",new JSONObject().put("name","Raw Game Meat").put("description","Fresh meat gathered from hunted animals.").put("color","#A84B45").put("rarity",1).put("gather_min",1).put("gather_max",2).put("atlas_set","buildables").put("atlas_cell",5));
        addSeed("MATERIAL","animal_hide","Animal Hide",new JSONObject().put("name","Animal Hide").put("description","Tough hide used for shelter and equipment.").put("color","#8A4F2A").put("rarity",1).put("gather_min",1).put("gather_max",2).put("atlas_set","buildables").put("atlas_cell",15));
        addSeed("MATERIAL","wool_bundle","Wool Bundle",new JSONObject().put("name","Wool Bundle").put("description","Warm wool gathered from woolhorns.").put("color","#E8D9B5").put("rarity",1).put("gather_min",1).put("gather_max",3).put("atlas_set","buildables").put("atlas_cell",14));
        addSeed("MATERIAL","suncrest_egg","Suncrest Egg",new JSONObject().put("name","Suncrest Egg").put("description","A rich egg laid by suncrest chickens.").put("color","#E9D178").put("rarity",1).put("gather_min",1).put("gather_max",2).put("atlas_set","buildables").put("atlas_cell",4));
        addBuildItem("animal_feed","Animal Feed","food",1,4,"A nourishing feed bundle that places passive animals into breeding mode.");
        addBuildItem("campfire_kit","Campfire Kit","placeable",2,0,"A complete campfire ready to place near the player.");
        addBuildItem("timber_house_kit","Timber House Kit","placeable",8,1,"A complete cottage kit with an enterable interior.");
        addBuildItem("oak_door","Ironbound Oak Door","component",3,2,"A strong working door for a timber house.");
        addBuildItem("storage_chest_kit","Storage Chest","placeable",4,3,"A placeable chest with persistent item storage.");
        addBuildItem("cooked_meat","Roasted Game Meat","food",5,6,"Hot roasted meat prepared for travel.");
        addBuildItem("bedroll","Wool Bedroll","placeable",2,7,"A warm portable bedroll.");
        addBuildItem("resin_torch","Resin Torch","placeable",2,8,"A bright torch for camp and home.");
        addBuildItem("rustic_fence","Rustic Fence","placeable",2,9,"A short timber fence section.");
        addBuildItem("timber_building_kit","Timber Building Kit","component",4,10,"Prepared beams, roof pieces, and stone footings.");
        addBuildItem("wooden_table","Wooden Table","placeable",2,11,"A sturdy table for a house interior.");
        addBuildItem("cooking_pot","Cooking Pot","placeable",3,12,"A heavy pot for campfire cooking.");
        addBuildItem("waterskin","Leather Waterskin","tool",1,13,"A stitched waterskin for long journeys.");
        addSeed("ITEM","sunstone_clock","Sunstone Clock",new JSONObject().put("name","Sunstone Clock").put("description","A compact mechanical clock that reveals exact world time.").put("category","tool").put("power",2).put("color","#D5A63B").put("atlas_cell",14).put("durability",160));
        addBuildItem("small_cabin_kit","Small Cabin Kit","placeable",6,1,"A compact empty one-room cabin with a working door.");
        addBuildItem("grand_lodge_kit","Grand Lodge Kit","placeable",12,1,"A large empty two-story lodge with working double doors.");
        addRecipe("animal_feed","Animal Feed","{\"fiber\":2,\"healing_herb\":1}");
        addRecipe("campfire_kit","Campfire Kit","{\"wood\":5,\"stone\":8}");
        addRecipe("timber_building_kit","Timber Building Kit","{\"wood\":16,\"stone\":8,\"fiber\":4}");
        addRecipe("oak_door","Ironbound Oak Door","{\"wood\":6,\"iron_ingot\":2}");
        addRecipe("timber_house_kit","Timber House Kit","{\"timber_building_kit\":1,\"oak_door\":1,\"wood\":10,\"stone\":6}");
        addRecipe("storage_chest_kit","Storage Chest","{\"wood\":8,\"iron_ingot\":2}");
        addRecipe("cooked_meat","Roasted Game Meat","{\"raw_meat\":1,\"wood\":1}");
        addRecipe("bedroll","Wool Bedroll","{\"wool_bundle\":4,\"fiber\":3,\"animal_hide\":1}");
        addRecipe("resin_torch","Resin Torch","{\"wood\":1,\"fiber\":1}");
        addRecipe("rustic_fence","Rustic Fence","{\"wood\":4,\"fiber\":2}");
        addRecipe("wooden_table","Wooden Table","{\"wood\":6}");
        addRecipe("cooking_pot","Cooking Pot","{\"iron_ingot\":4}");
        addRecipe("waterskin","Leather Waterskin","{\"animal_hide\":2,\"fiber\":1}");
        addRecipe("sunstone_clock","Sunstone Clock","{\"iron_ingot\":2,\"crystal\":1}");
        addRecipe("small_cabin_kit","Small Cabin Kit","{\"wood\":12,\"stone\":5,\"fiber\":3}");
        addRecipe("grand_lodge_kit","Grand Lodge Kit","{\"timber_building_kit\":2,\"oak_door\":2,\"wood\":24,\"stone\":18,\"iron_ingot\":4}");
        addCreature("woolhorn","Woolhorn","fiber",18,0,new JSONObject().put("wool_bundle",2).put("raw_meat",1));
        addCreature("meadow_rabbit","Meadow Rabbit","healing_herb",8,1,new JSONObject().put("raw_meat",1).put("animal_hide",1));
        addCreature("mossback_boar","Mossback Boar","animal_feed",28,2,new JSONObject().put("raw_meat",3).put("animal_hide",2));
        addCreature("suncrest_chicken","Suncrest Chicken","fiber",10,3,new JSONObject().put("suncrest_egg",2).put("raw_meat",1));
        addStructure("campfire","Campfire",0,false,2,2);addStructure("timber_house","Timber Cottage",1,true,5,4);addStructure("storage_chest","Storage Chest",3,false,1,1);
        addStructureVariant("small_cabin","Small Cabin",0,4,3);addStructureVariant("grand_lodge","Grand Lodge",2,7,6);
        addWeather("clear_skies","Clear Skies","#76BCEB",0,1.0);addWeather("summer_rain","Summer Rain","#6F8FA8",55,.78);addWeather("rolling_fog","Rolling Fog","#C7D1CE",30,.70);addWeather("highland_storm","Highland Storm","#394B66",90,.48);addWeather("frost_snow","Frost Snow","#DDECF0",45,.82);
    }

    private void addBuildItem(String key,String name,String category,int power,int atlas,String description) throws JSONException {addSeed("ITEM",key,name,new JSONObject().put("name",name).put("description",description).put("category",category).put("power",power).put("color","#B9783E").put("atlas_set","buildables").put("atlas_cell",atlas).put("durability",120));}
    private void addCreature(String key,String name,String food,int health,int row,JSONObject drops) throws JSONException {addSeed("CREATURE",key,name,new JSONObject().put("name",name).put("description","A passive breedable "+name.toLowerCase(Locale.ROOT)+" of the Infinite RPG wilds.").put("temperament","passive").put("health",health).put("speed",1).put("food",food).put("drops",drops).put("atlas_row",row));}
    private void addStructure(String key,String name,int atlas,boolean enterable,int width,int height) throws JSONException {addSeed("STRUCTURE",key,name,new JSONObject().put("name",name).put("description","A player-built "+name.toLowerCase(Locale.ROOT)+".").put("atlas_set","buildables").put("atlas_cell",atlas).put("enterable",enterable).put("width",width).put("height",height));}
    private void addStructureVariant(String key,String name,int atlas,int width,int height) throws JSONException {addSeed("STRUCTURE",key,name,new JSONObject().put("name",name).put("description","An empty player-built "+name.toLowerCase(Locale.ROOT)+" with a working door.").put("atlas_set","structures_v5").put("atlas_cell",atlas).put("enterable",true).put("width",width).put("height",height));}
    private void addWeather(String key,String name,String color,int particles,double light) throws JSONException {addSeed("WEATHER",key,name,new JSONObject().put("name",name).put("description",name+" moves across the world.").put("color",color).put("particles",particles).put("light",light).put("duration_minutes",4));}
    private void grantStarterPackIfNeeded(){if(Boolean.parseBoolean(getState("starter_pack_v3","false")))return;addInventory("wood",12);addInventory("stone",12);addInventory("fiber",8);addInventory("healing_herb",3);putState("starter_pack_v3","true");log("INFO","Survival starter materials added to the backpack");}

    private void seedMusicLibrary() throws JSONException {
        String m1="[7,-1,9,10,-1,9,7,5,3,-1,5,7,9,7,5,-1,7,9,12,-1,10,9,7,5,3,5,7,-1,5,3,2,-1]";
        String m2="[0,2,3,5,7,-1,5,3,2,-1,3,5,7,9,7,-1,5,7,10,9,7,5,3,-1,2,3,5,-1,3,2,0,-1]";
        String m3="[12,-1,10,9,7,-1,9,10,12,14,12,-1,9,7,5,-1,7,9,10,-1,9,7,5,3,5,-1,7,5,3,2,0,-1]";
        String m4="[3,5,7,-1,10,9,7,-1,5,7,9,12,10,-1,7,5,3,-1,5,7,3,-1,2,0,3,5,7,9,7,5,3,-1]";
        String m5="[0,-1,7,-1,5,3,2,-1,0,2,3,7,5,-1,3,-1,7,9,10,-1,12,10,9,7,5,-1,3,2,0,-1,2,-1]";
        String b1="[0,-1,0,-1,3,-1,5,-1,0,-1,5,-1,3,-1,4,-1]",b2="[0,-1,3,-1,5,-1,3,-1,2,-1,5,-1,4,-1,0,-1]",b3="[0,0,-1,0,5,5,-1,5,3,3,-1,3,4,4,-1,4]";
        String r1="[1,0,0,1,0,0,1,0,1,0,0,1,0,1,0,0]",r2="[1,0,1,0,0,1,0,1,1,0,0,1,0,1,1,0]",r3="[1,0,0,0,1,0,1,0,1,0,0,1,0,0,1,0]";
        addMusicPreset("ember_road","Ember Road","A hopeful road theme carried by warm strings and hand drums.",88,"dorian",48,"triangle","[0,3,5,2,0,5,3,4]",m2,b1,r2);
        addMusicPreset("moonlit_pines","Moonlit Pines","A quiet silver-blue woodland nocturne.",68,"minor_pentatonic",52,"sine","[0,3,4,2,0,5,3,2]",m3,b2,r3);
        addMusicPreset("glasswater","Glasswater","Clear harp-like notes moving over a deep calm current.",74,"major_pentatonic",50,"triangle","[0,2,4,3,0,4,2,1]",m1,b1,r3);
        addMusicPreset("sun_over_ruins","Sun Over Ruins","Ancient stone waking beneath a bright patient melody.",80,"dorian",47,"soft_square","[0,5,3,4,0,2,5,3]",m4,b3,r1);
        addMusicPreset("mire_lanterns","Mire Lanterns","Small lights and low drums crossing the evening marsh.",64,"aeolian",45,"sine","[0,3,5,0,4,3,2,5]",m5,b2,r3);
        addMusicPreset("frostbound_sky","Frostbound Sky","Wide crystalline tones beneath a slow northern sky.",70,"minor_pentatonic",53,"triangle","[0,4,3,5,0,2,4,3]",m3,b1,r3);
        addMusicPreset("wayfarers_rest","Wayfarer's Rest","A gentle fireside theme for inventory and reflection.",62,"major_pentatonic",48,"sine","[0,3,2,4,0,2,3,1]",m2,b2,r3);
        addMusicPreset("ancient_forge","Ancient Forge","Measured iron rhythm with a noble rising line.",96,"dorian",43,"soft_square","[0,3,5,4,0,5,2,3]",m4,b3,r2);
        addMusicPreset("rain_on_copper","Rain on Copper","Soft droplets, warm resonance, and distant rolling bass.",76,"aeolian",49,"triangle","[0,2,5,3,0,4,2,5]",m1,b2,r1);
        addMusicPreset("dawn_caravan","Dawn Caravan","A bright traveling pulse for the first miles of morning.",102,"major_pentatonic",46,"triangle","[0,2,3,5,0,4,3,2]",m2,b3,r2);
        addMusicPreset("starfall_meadow","Starfall Meadow","Open grass, falling stars, and a weightless flute line.",72,"major_pentatonic",55,"sine","[0,4,2,3,0,5,4,2]",m3,b1,r3);
        addMusicPreset("hollow_mountain","Hollow Mountain","Low resonant stone with a cautious ascending echo.",66,"aeolian",41,"soft_square","[0,5,3,2,0,4,5,3]",m5,b3,r1);
        addMusicPreset("river_of_ash","River of Ash","A dark current softened by sparks of hopeful melody.",84,"dorian",44,"triangle","[0,3,2,5,0,4,3,5]",m1,b2,r2);
        addMusicPreset("wildflower_march","Wildflower March","Playful color and an easy adventurer's stride.",108,"major_pentatonic",51,"triangle","[0,2,4,3,0,3,5,2]",m4,b3,r2);
        addMusicPreset("quiet_citadel","Quiet Citadel","Tall halls, old banners, and peaceful watchfires.",69,"dorian",46,"sine","[0,3,5,4,0,2,3,5]",m3,b1,r3);
        addMusicPreset("stormglass","Stormglass","Quick silver melody beneath distant thunder.",112,"aeolian",50,"soft_square","[0,5,4,3,0,3,2,5]",m5,b3,r2);
        addMusicPreset("golden_canopy","Golden Canopy","Warm afternoon light moving through enormous leaves.",78,"major_pentatonic",49,"triangle","[0,2,3,4,0,5,3,2]",m2,b1,r1);
        addMusicPreset("night_hearth","Night Hearth","A low, safe song for the long dark beyond camp.",60,"minor_pentatonic",47,"sine","[0,3,2,5,0,4,3,2]",m1,b2,r3);
        addMusicPreset("beyond_blue_peaks","Beyond Blue Peaks","A spacious closing theme that points toward another horizon.",86,"dorian",52,"triangle","[0,4,5,3,0,5,2,4]",m4,b1,r1);
        seedThirtyNewTracks();
        remixClassicTracks();
    }

    private void addMusicPreset(String key,String name,String description,int tempo,String scale,int root,String wave,String chords,String melody,String bass,String rhythm) throws JSONException {
        addSeed("MUSIC",key,name,new JSONObject().put("name",name).put("description",description).put("tempo",tempo).put("scale",scale).put("root",root).put("wave",wave).put("chords",new JSONArray(chords)).put("melody",new JSONArray(melody)).put("bass",new JSONArray(bass)).put("rhythm",new JSONArray(rhythm)));
    }

    private void seedThirtyNewTracks() throws JSONException {
        String[][] tracks={
            {"verdant_postcard","Verdant Postcard","A breezy field overture with a curious whistle and dancing strings.","94","major_pentatonic","52","whistle","pizzicato","handdrum","ABACDBEC","4101"},
            {"copperwing_village","Copperwing Village","A warm village dance of accordion, fiddle-like lead, and wooden percussion.","106","dorian","48","violin","accordion","festival","ABABCEDE","4102"},
            {"cloudberry_trail","Cloudberry Trail","A skipping trail song with ocarina calls and bright guitar answers.","116","major_pentatonic","55","ocarina","guitar","handdrum","ABACABDE","4103"},
            {"lantern_railway","Lantern Railway","Clockwork motion, glowing windows, and a melody that gathers speed.","122","dorian","46","reed","marimba","clockwork","AABCDBEC","4104"},
            {"sapphire_orchard","Sapphire Orchard","A blue-sky pastoral with flute, harp, and a broad lyrical middle section.","78","major_pentatonic","54","flute","harp","brush","ABACDEBC","4105"},
            {"pocket_meteor","Pocket Meteor","Tiny sparks race across a playful celesta and pizzicato arrangement.","132","major_pentatonic","57","celesta","pizzicato","clockwork","ABBCDACE","4106"},
            {"mosslight_academy","Mosslight Academy","An inquisitive chamber theme with clarinet-like lead and patient organ chords.","84","dorian","50","clarinet","organ","brush","ABACBDCE","4107"},
            {"coralwind_harbor","Coralwind Harbor","Salt air, gull-wing rhythms, and a rolling marimba voyage.","98","major_pentatonic","49","whistle","marimba","festival","ABABCDCE","4108"},
            {"clockwork_kite","Clockwork Kite","A nimble mechanical scherzo that rises into a bright soaring refrain.","126","dorian","53","celesta","marimba","clockwork","AABCDDBE","4109"},
            {"rainbell_market","Rainbell Market","A gentle bustling market theme with bell tones, accordion, and brushed drums.","92","dorian","51","bell","accordion","brush","ABACBDEC","4110"},
            {"sunpetal_parade","Sunpetal Parade","A colorful brass-and-drum procession with a smiling countermelody.","118","major_pentatonic","48","brass","strings","march","ABABCDDE","4111"},
            {"echoes_under_snow","Echoes Under Snow","A spacious winter adagio of glass bells, choir-like pads, and soft timpani.","58","minor_pentatonic","55","chime","choir","orchestral","AACBDEEC","4112"},
            {"little_thunder_plains","Little Thunder Plains","A determined plains run with drums, low strings, and a bold reed melody.","124","dorian","45","reed","strings","march","ABACDBDE","4113"},
            {"starlit_hatchery","Starlit Hatchery","A tender mysterious cradle song with ocarina, celesta, and quiet pulses.","70","major_pentatonic","56","ocarina","celesta","brush","AACBDEBC","4114"},
            {"amber_map_room","Amber Map Room","An exploratory chamber piece whose melody turns like paths on an old map.","82","dorian","47","clarinet","harp","brush","ABCDABEC","4115"},
            {"moonberry_waltz","Moonberry Waltz","A moonlit three-step illusion shaped by violin, harp, and delicate bells.","88","aeolian","52","violin","harp","waltz","ABACDBEC","4116"},
            {"crystal_burrow","Crystal Burrow","A sparkling underground miniature of marimba, chime, and plucked bass.","110","minor_pentatonic","50","chime","marimba","clockwork","ABBCDACE","4117"},
            {"treetop_courier","Treetop Courier","A fast airborne delivery theme with whistle, guitar, and tumbling hand drums.","136","major_pentatonic","54","whistle","guitar","festival","ABACABDE","4118"},
            {"hearthbound_promise","Hearthbound Promise","A sincere homecoming ballad led by warm strings and a simple flute vow.","66","major_pentatonic","49","flute","strings","brush","AACBDEEC","4119"},
            {"silver_finch_flight","Silver Finch Flight","A swift silver melody weaving between flute and pizzicato strings.","128","dorian","57","flute","pizzicato","handdrum","ABABCDDE","4120"},
            {"ancient_seed_vault","Ancient Seed Vault","Low organ, distant choir, and a slowly opening ceremonial theme.","60","aeolian","42","brass","organ","orchestral","AABCDBEE","4121"},
            {"cometgrass_crossing","Cometgrass Crossing","A bright crossing theme with celesta trails and strummed accompaniment.","104","major_pentatonic","53","celesta","guitar","handdrum","ABACBDEC","4122"},
            {"whispering_fossil","Whispering Fossil","A strange ancient dialogue between breathy reed and hollow marimba.","72","aeolian","44","reed","marimba","brush","ACBDAEEC","4123"},
            {"festival_of_small_stars","Festival of Small Stars","A joyful night festival with bells, accordion, drums, and changing refrains.","120","major_pentatonic","51","bell","accordion","festival","ABBCDDEE","4124"},
            {"duskmere_observatory","Duskmere Observatory","A patient astronomical theme of choir, chime, and rising string figures.","64","dorian","50","chime","choir","orchestral","AACBDEBC","4125"},
            {"brave_acorn","Brave Acorn","A tiny heroic march with brass calls and energetic pizzicato replies.","114","major_pentatonic","47","brass","pizzicato","march","ABACDBDE","4126"},
            {"riverstone_arena","Riverstone Arena","A spirited challenge theme driven by taiko-like drums and urgent strings.","138","dorian","43","violin","strings","orchestral","ABBCDACE","4127"},
            {"secret_garden_engine","Secret Garden Engine","Botanical machinery ticks beneath a curious flute and celesta duet.","100","dorian","52","flute","celesta","clockwork","ABCDABEC","4128"},
            {"aurora_caravan","Aurora Caravan","A wide traveling nocturne with ocarina, choir, and a glowing final movement.","76","minor_pentatonic","54","ocarina","choir","handdrum","ABACDEBC","4129"},
            {"home_beyond_the_pass","Home Beyond the Pass","A long-form closing journey from quiet guitar to full strings and brass.","86","dorian","48","violin","guitar","orchestral","AACBDEEE","4130"}
        };
        for(String[] t:tracks){long motif=Long.parseLong(t[10]);JSONObject music=evolvedMusic(t[1],t[2],Integer.parseInt(t[3]),t[4],Integer.parseInt(t[5]),t[6],t[7],t[8],t[9],motif,null,false);addSeed("MUSIC",t[0],t[1],music);}
    }

    private void remixClassicTracks() throws JSONException {
        if(Boolean.parseBoolean(getState("music_expansion_v5","false")))return;
        String[][] remixes={
            {"first_horizon","flute","strings","orchestral","ABACDBEC","5101"},
            {"ember_road","violin","guitar","handdrum","ABACABDE","5102"},
            {"moonlit_pines","ocarina","choir","brush","AACBDEBC","5103"},
            {"glasswater","celesta","harp","brush","ABACBDEC","5104"},
            {"sun_over_ruins","brass","strings","orchestral","AABCDBEE","5105"},
            {"mire_lanterns","reed","marimba","handdrum","ACBDAEEC","5106"},
            {"frostbound_sky","chime","choir","orchestral","AACBDEEC","5107"},
            {"wayfarers_rest","flute","guitar","brush","ABACDEBC","5108"},
            {"ancient_forge","brass","organ","march","ABBCDACE","5109"},
            {"rain_on_copper","bell","marimba","brush","ABCDABEC","5110"},
            {"dawn_caravan","whistle","pizzicato","festival","ABABCDDE","5111"},
            {"starfall_meadow","ocarina","celesta","brush","AACBDEBC","5112"},
            {"hollow_mountain","clarinet","organ","orchestral","ACBDAEEC","5113"},
            {"river_of_ash","violin","strings","handdrum","ABACDBDE","5114"},
            {"wildflower_march","whistle","accordion","festival","ABBCDDEE","5115"}
        };
        for(String[] r:remixes){ContentItem old=byIdentity("MUSIC",r[0]);if(old==null)continue;JSONObject next=evolvedMusic(old.name,old.text("description","An Infinite RPG exploration theme."),old.number("tempo",82),old.text("scale","dorian"),old.number("root",50),r[1],r[2],r[3],r[4],Long.parseLong(r[5]),old.json.optJSONArray("melody"),true);updateJson(old.id,next);}
        putState("music_expansion_v5","true");log("AUDIO","Expanded to 50 original tracks and remixed 15 classic themes");
    }

    private JSONObject evolvedMusic(String name,String description,int tempo,String scale,int root,String lead,String ensemble,String percussion,String form,long motifSeed,JSONArray legacy,boolean remixed) throws JSONException {
        Random rng=new Random(motifSeed);int[][] progressions={{0,3,5,4,0,2,4,5},{0,4,5,3,0,5,2,4},{0,2,3,5,0,4,3,2},{0,5,3,4,2,5,0,4},{0,3,2,5,4,2,5,0},{0,2,5,3,4,0,3,5},{0,5,4,2,3,0,5,4},{0,3,5,2,4,1,5,0},{0,4,2,5,3,1,4,0},{0,1,4,5,2,3,5,0}};int[] pa=progressions[Math.floorMod((int)motifSeed,progressions.length)],pb=progressions[Math.floorMod((int)(motifSeed*7+3),progressions.length)];
        JSONArray chords=new JSONArray(),chordsB=new JSONArray();for(int i=0;i<8;i++){chords.put(pa[i]);chordsB.put(pb[i]);}
        int[] motif=new int[8];int note=2+rng.nextInt(7);for(int i=0;i<motif.length;i++){if(i>0)note=Math.max(0,Math.min(14,note+new int[]{-2,-1,1,2,3}[rng.nextInt(5)]));motif[i]=note;}
        JSONArray melody=new JSONArray(),melodyB=new JSONArray(),counter=new JSONArray();for(int i=0;i<32;i++){int a;if(legacy!=null&&legacy.length()>0)a=legacy.optInt(i%legacy.length(),-1);else{boolean rest=(i%8==3&&rng.nextBoolean())||(i%8==7);a=rest?-1:motif[(i/2+i/8)%motif.length]+(i>=16?(rng.nextBoolean()?1:2):0);}if(a>16)a-=7;melody.put(a);int b=a<0?-1:Math.max(0,Math.min(16,14-a+(i>=16?1:0)));if(i%8==6&&b>=0)b=Math.min(16,b+1);melodyB.put(b);int c=(i%4==0&&a>=0)?Math.max(0,a-2):-1;counter.put(c);}
        JSONArray bass=new JSONArray(),bassB=new JSONArray(),rhythm=new JSONArray(),rhythmB=new JSONArray();for(int i=0;i<16;i++){boolean active=i%2==0||("march".equals(percussion)&&i%4!=3);bass.put(active?pa[(i/2)%8]:-1);bassB.put(active?pb[(i/2)%8]:-1);int hit;if("none".equals(percussion))hit=0;else if("brush".equals(percussion))hit=(i==0||i==6||i==8||i==13)?1:0;else if("waltz".equals(percussion))hit=(i%6==0||i%6==2||i%6==4)?1:0;else if("march".equals(percussion))hit=(i%2==0||i==7||i==15)?1:0;else hit=(i==0||i==3||i==6||i==8||i==11||i==14)?1:0;rhythm.put(hit);rhythmB.put(hit==1||i==4||i==12?1:0);}
        return new JSONObject().put("name",name).put("description",description).put("tempo",tempo).put("scale",scale).put("root",root).put("wave","triangle").put("lead",lead).put("ensemble",ensemble).put("percussion",percussion).put("form",form).put("motif_seed",motifSeed).put("remixed",remixed).put("chords",chords).put("chords_b",chordsB).put("melody",melody).put("melody_b",melodyB).put("counter",counter).put("bass",bass).put("bass_b",bassB).put("rhythm",rhythm).put("rhythm_b",rhythmB).put("swing",.02+rng.nextDouble()*.10).put("echo",.16+rng.nextDouble()*.20);
    }

    private void addPresetItem(String key,String name,String category,int power,String color,int atlas) throws JSONException {
        addSeed("ITEM",key,name,new JSONObject().put("name",name).put("description","A carefully crafted "+name.toLowerCase(Locale.ROOT)+".").put("category",category).put("power",power).put("color",color).put("glyph",name.substring(0,1)).put("atlas_cell",atlas).put("durability",120));
    }

    private void addPixelTexture(String key,String name,String biome,String[] colors,int mode) throws JSONException {
        JSONArray palette=new JSONArray();for(String color:colors)palette.put(color);JSONArray rows=new JSONArray();
        for(int y=0;y<16;y++){StringBuilder row=new StringBuilder();for(int x=0;x<16;x++){int v;if(mode==0)v=(x*3+y*5+(x*y)%7)%6;else if(mode==1)v=((x+y/3)+(y%4==0?2:0))%6;else if(mode==2)v=(Math.abs(x-y)+(x*y)%5)%6;else v=(x*7+y*11+(x^y))%6;row.append((char)('0'+v));}rows.put(row.toString());}
        addSeed("TILE_STYLE",key,name,new JSONObject().put("name",name).put("description","A complete hand-authored 16 by 16 pixel texture.").put("biome",biome).put("palette",palette).put("pixels",rows).put("blend",0.22));
    }

    private void patchCoreVisuals() {
        try {
            patchJson("BIOME","verdant_reach","texture_cell",0);patchJson("BIOME","sunscorch_expanse","texture_cell",4);patchJson("BIOME","frostglass_wilds","texture_cell",8);patchJson("BIOME","mire_of_echoes","texture_cell",12);
            patchJson("MATERIAL","wood","atlas_cell",4);patchJson("MATERIAL","stone","atlas_cell",5);patchJson("MATERIAL","fiber","atlas_cell",6);patchJson("MATERIAL","crystal","atlas_cell",3);
            patchJson("ITEM","wood_axe","atlas_cell",0);patchJson("ITEM","stone_pick","atlas_cell",1);patchJson("ITEM","trail_ration","atlas_cell",2);
            ContentItem music=byIdentity("MUSIC","first_horizon");if(music!=null&&music.json.optJSONArray("chords")==null)updateJson(music.id,beautifulMusic());
            putState("upgrade_visuals","complete");
        }catch(Exception e){log("ERROR","Visual migration: "+e.getMessage());}
    }

    private void patchJson(String type,String identity,String key,int value) throws JSONException {ContentItem item=byIdentity(type,identity);if(item!=null&&item.json.optInt(key,-1)<0){item.json.put(key,value);updateJson(item.id,item.json);}}
    private void updateJson(long id,JSONObject json){ContentValues v=new ContentValues();v.put("json",json.toString());v.put("signature",signature(json.toString()));getWritableDatabase().update("content",v,"id=?",new String[]{Long.toString(id)});}

    private void addMaterial(String key, String name, String color, int rarity, String biome) throws JSONException {
        addSeed("MATERIAL",key,name,new JSONObject().put("name",name).put("color",color).put("rarity",rarity).put("biome",biome).put("gather_min",1).put("gather_max",3));
    }
    private void addItem(String key, String name, String category, int power, String color) throws JSONException {
        addSeed("ITEM",key,name,new JSONObject().put("name",name).put("category",category).put("power",power).put("color",color).put("glyph",name.substring(0,1)));
    }
    private void addRecipe(String key, String name, String ingredients) throws JSONException {
        addSeed("RECIPE",key,name,new JSONObject().put("name",name).put("result",key).put("quantity",1).put("ingredients",new JSONObject(ingredients)));
    }
    private void addSeed(String type, String identity, String name, JSONObject json) {
        ContentValues v = new ContentValues();
        v.put("type", type); v.put("identity_key", identity); v.put("name", name); v.put("json", json.toString());
        v.put("signature", signature(json.toString())); v.put("source", "core"); v.put("judge_score", 1.0); v.put("created_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("content", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private void ensureRequiredJobs() {
        String[] types = {"BIOME","MATERIAL","ITEM","RECIPE","TILE_STYLE","CREATURE","QUEST","MUSIC","STRUCTURE","WEATHER"};
        int[] required = {8,24,35,32,12,18,12,50,10,8};
        SQLiteDatabase db = getWritableDatabase();
        for (int i=0;i<types.length;i++) {
            long have = scalar("SELECT COUNT(*) FROM content WHERE type=?", new String[]{types[i]});
            long queued = scalar("SELECT COUNT(*) FROM jobs WHERE type=? AND status IN ('queued','running')", new String[]{types[i]});
            for (long n=have+queued;n<required[i];n++) enqueue(types[i], "Fill required catalog slot " + (n+1) + " of " + required[i], 1000-i*10);
        }
        if (scalar("SELECT COUNT(*) FROM jobs WHERE status='queued'", null) == 0) enqueueNovelJob();
    }

    public void enqueueNovelJob() {
        long cursor = Long.parseLong(getState("novel_cursor", "0"));
        putState("novel_cursor", Long.toString(cursor+1));
        putState("forge_mode","endless novelty — Qwen chooses the next connected creation");
        enqueue("ANY", "Choose and invent a genuinely new live game addition. Prefer useful connections between existing materials, items, recipes, creatures, biomes, textures, structures, weather, quests, or music. Novelty sequence " + (cursor+1), 10);
    }

    public void enqueue(String type, String prompt, int priority) {
        ContentValues v = new ContentValues(); v.put("type",type); v.put("prompt",prompt); v.put("priority",priority); v.put("created_at",System.currentTimeMillis());
        getWritableDatabase().insert("jobs",null,v);
    }

    public GenerationJob claimNextJob() {
        synchronized (lock) {
            ensureRequiredJobs();
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try (Cursor c = db.rawQuery("SELECT id,type,prompt,priority,attempts FROM jobs WHERE status='queued' ORDER BY priority DESC,id LIMIT 1",null)) {
                if (!c.moveToFirst()) { db.setTransactionSuccessful(); return null; }
                long id=c.getLong(0); ContentValues v=new ContentValues(); v.put("status","running"); v.put("attempts",c.getInt(4)+1); db.update("jobs",v,"id=?",new String[]{Long.toString(id)});
                GenerationJob job=new GenerationJob(id,c.getString(1),c.getString(2),c.getInt(3),c.getInt(4)+1); db.setTransactionSuccessful(); return job;
            } finally { db.endTransaction(); }
        }
    }

    /**
     * A native-process death cannot run GenerationEngine's finally block. Put
     * any job it owned back in the queue so the next explicit Start/Generate
     * request retries the same work instead of leaving a permanent "running"
     * row that can never be claimed again.
     */
    public int recoverRunningJobs() {
        ContentValues v=new ContentValues();
        v.put("status","queued");
        v.put("last_error","Recovered after interrupted native pass; ready to retry");
        int recovered=getWritableDatabase().update("jobs",v,"status='running'",null);
        putState("active_job","idle — recovered "+recovered+" interrupted job"+(recovered==1?"":"s"));
        return recovered;
    }

    public void finishJob(long id, boolean ok, String message) {
        ContentValues v=new ContentValues(); v.put("status",ok?"done":"queued"); v.put("last_error",message==null?"":message);
        if (!ok) {
            long attempts=scalar("SELECT attempts FROM jobs WHERE id=?",new String[]{Long.toString(id)});
            if (attempts>=3) v.put("status","rejected");
        }
        getWritableDatabase().update("jobs",v,"id=?",new String[]{Long.toString(id)});
        if (ok) while (scalar("SELECT COUNT(*) FROM jobs WHERE status='queued'",null)<3) enqueueNovelJob();
    }

    public boolean acceptGenerated(String type, JSONObject candidate, double judgeScore) {
        synchronized (lock) {
            String identity=normalize(candidate.optString("identity_key",candidate.optString("name","")));
            String name=candidate.optString("name",identity.replace('_',' ')).trim();
            if (identity.length()<3 || name.length()<3 || judgeScore<0.72) return false;
            String sig=signature(candidate.toString());
            if (scalar("SELECT COUNT(*) FROM content WHERE type=? AND identity_key=?",new String[]{type,identity})>0) return false;
            for (ContentItem old:listIncludingDisabled(type,200)) if (jaccard(sig,signature(old.json.toString()))>0.82) return false;
            ContentValues v=new ContentValues(); v.put("type",type); v.put("identity_key",identity); v.put("name",name); v.put("json",candidate.toString()); v.put("signature",sig); v.put("source","qwen"); v.put("judge_score",judgeScore); v.put("created_at",System.currentTimeMillis());
            boolean accepted=getWritableDatabase().insertWithOnConflict("content",null,v,SQLiteDatabase.CONFLICT_IGNORE)>0;
            if (accepted) {log("ACCEPT",type+": "+name+" (judge "+String.format(Locale.US,"%.2f",judgeScore)+")");putState("last_creation_type",type);putState("last_creation_key",identity);putState("last_creation_name",name);putState("last_creation_description",candidate.optString("description","A new world creation."));putState("creation_seq",Long.toString(Long.parseLong(getState("creation_seq","0"))+1));bumpCatalog();}
            return accepted;
        }
    }

    public List<ContentItem> list(String type, int limit) {
        ArrayList<ContentItem> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT c.id,c.type,c.identity_key,c.name,c.json FROM content c WHERE c.type=? AND NOT EXISTS(SELECT 1 FROM content_control k WHERE k.type=c.type AND k.identity_key=c.identity_key AND k.disabled=1) ORDER BY c.id DESC LIMIT ?",new String[]{type,Integer.toString(limit)})) {
            while(c.moveToNext()) try { out.add(new ContentItem(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),new JSONObject(c.getString(4)))); } catch(JSONException ignored){}
        }
        return out;
    }

    private List<ContentItem> listIncludingDisabled(String type,int limit){ArrayList<ContentItem> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,type,identity_key,name,json FROM content WHERE type=? ORDER BY id DESC LIMIT ?",new String[]{type,Integer.toString(limit)})){while(c.moveToNext())try{out.add(new ContentItem(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),new JSONObject(c.getString(4))));}catch(JSONException ignored){}}return out;}

    public List<ContentItem> recentAi(int limit) {
        ArrayList<ContentItem> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,type,identity_key,name,json FROM content WHERE source='qwen' ORDER BY id DESC LIMIT ?",new String[]{Integer.toString(limit)})){while(c.moveToNext())try{out.add(new ContentItem(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),new JSONObject(c.getString(4))));}catch(JSONException ignored){}}return out;
    }

    public ContentItem byIdentity(String type,String identity) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT c.id,c.type,c.identity_key,c.name,c.json FROM content c WHERE c.type=? AND c.identity_key=? AND NOT EXISTS(SELECT 1 FROM content_control k WHERE k.type=c.type AND k.identity_key=c.identity_key AND k.disabled=1) LIMIT 1",new String[]{type,identity})) {
            if(c.moveToFirst()) try{return new ContentItem(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),new JSONObject(c.getString(4)));}catch(JSONException ignored){}
        }
        return null;
    }

    public String compactCatalog(String type,int limit) {
        JSONArray a=new JSONArray();
        for(ContentItem item:listIncludingDisabled(type,limit)) { JSONObject o=new JSONObject(); try{o.put("key",item.identity).put("name",item.name);}catch(JSONException ignored){} a.put(o); }
        return a.toString();
    }

    public boolean isDisabled(String type,String identity){return scalar("SELECT disabled FROM content_control WHERE type=? AND identity_key=?",new String[]{type,identity})>0;}
    public void setDisabled(String type,String identity,boolean disabled){getWritableDatabase().execSQL("INSERT INTO content_control(type,identity_key,disabled) VALUES(?,?,?) ON CONFLICT(type,identity_key) DO UPDATE SET disabled=excluded.disabled",new Object[]{type,identity,disabled?1:0});log("INFO",(disabled?"Disabled ":"Enabled ")+type+": "+identity);bumpCatalog();}
    public boolean deleteGenerated(String type,String identity){int n=getWritableDatabase().delete("content","type=? AND identity_key=? AND source='qwen'",new String[]{type,identity});getWritableDatabase().delete("content_control","type=? AND identity_key=?",new String[]{type,identity});if(n>0){log("INFO","Deleted generated "+type+": "+identity);enqueue(type,"Replace a user-cancelled generated entry with a genuinely different one",500);bumpCatalog();}return n>0;}
    private void bumpCatalog(){long value;try{value=Long.parseLong(getState("catalog_revision","0"));}catch(Exception e){value=0;}putState("catalog_revision",Long.toString(value+1));}

    public void addInventory(String key,int quantity) {
        getWritableDatabase().execSQL("INSERT INTO inventory(identity_key,quantity) VALUES(?,?) ON CONFLICT(identity_key) DO UPDATE SET quantity=quantity+excluded.quantity",new Object[]{key,quantity});
    }
    public int inventory(String key) { return (int)scalar("SELECT quantity FROM inventory WHERE identity_key=?",new String[]{key}); }
    public List<String[]> inventoryList() {
        ArrayList<String[]> out=new ArrayList<>(); try(Cursor c=getReadableDatabase().rawQuery("SELECT identity_key,quantity FROM inventory WHERE quantity>0 ORDER BY identity_key",null)){while(c.moveToNext())out.add(new String[]{c.getString(0),Integer.toString(c.getInt(1))});} return out;
    }
    public boolean craft(ContentItem recipe) {
        JSONObject ingredients=recipe.json.optJSONObject("ingredients"); if(ingredients==null)return false;
        JSONArray keys=ingredients.names(); if(keys==null)return false;
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            for(int i=0;i<keys.length();i++){String k=keys.optString(i);if(inventory(k)<ingredients.optInt(k))return false;}
            for(int i=0;i<keys.length();i++){String k=keys.optString(i);db.execSQL("UPDATE inventory SET quantity=quantity-? WHERE identity_key=?",new Object[]{ingredients.optInt(k),k});}
            addInventory(recipe.json.optString("result",recipe.identity),recipe.json.optInt("quantity",1)); db.setTransactionSuccessful(); return true;
        } finally {db.endTransaction();}
    }

    public boolean consumeInventory(String key,int quantity){if(quantity<=0)return true;SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{long have=scalar("SELECT quantity FROM inventory WHERE identity_key=?",new String[]{key});if(have<quantity)return false;db.execSQL("UPDATE inventory SET quantity=quantity-? WHERE identity_key=?",new Object[]{quantity,key});db.setTransactionSuccessful();return true;}finally{db.endTransaction();}}

    public long placeStructure(String structureType,String inventoryKey){double[] p=loadPlayer();return placeStructureAt(structureType,inventoryKey,(float)p[0]+2.2f,(float)p[1]);}
    public long placeStructureAt(String structureType,String inventoryKey,float x,float y){double[] p=loadPlayer();if(Math.hypot(x-p[0],y-p[1])>13)return -3;ContentItem info=byIdentity("STRUCTURE",structureType);double clearance=info==null?1.5:Math.max(1.4,Math.min(3.2,info.number("width",2)*.34));SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{if(inventory(inventoryKey)<1)return -1;if(scalar("SELECT COUNT(*) FROM structures WHERE x BETWEEN ? AND ? AND y BETWEEN ? AND ?",new String[]{Double.toString(x-clearance),Double.toString(x+clearance),Double.toString(y-clearance),Double.toString(y+clearance)})>0)return -2;db.execSQL("UPDATE inventory SET quantity=quantity-1 WHERE identity_key=?",new Object[]{inventoryKey});ContentValues v=new ContentValues();v.put("type",structureType);v.put("x",x);v.put("y",y);v.put("state","complete");v.put("json","{}");v.put("created_at",System.currentTimeMillis());long id=db.insert("structures",null,v);db.setTransactionSuccessful();log("BUILD","Placed "+structureType+" at "+Math.round(x)+", "+Math.round(y));return id;}finally{db.endTransaction();}}
    public long buildGeneratedStructure(ContentItem blueprint){double[] p=loadPlayer();return buildGeneratedStructureAt(blueprint,(float)p[0]+2.2f,(float)p[1]);}
    public long buildGeneratedStructureAt(ContentItem blueprint,float x,float y){JSONObject ingredients=blueprint.json.optJSONObject("required_items");if(ingredients==null)return -1;double[] p=loadPlayer();if(Math.hypot(x-p[0],y-p[1])>13)return -3;double clearance=Math.max(1.4,Math.min(3.2,blueprint.number("width",2)*.34));SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{JSONArray keys=ingredients.names();if(keys==null)return -1;for(int i=0;i<keys.length();i++){String key=keys.optString(i);if(inventory(key)<ingredients.optInt(key))return -1;}if(scalar("SELECT COUNT(*) FROM structures WHERE x BETWEEN ? AND ? AND y BETWEEN ? AND ?",new String[]{Double.toString(x-clearance),Double.toString(x+clearance),Double.toString(y-clearance),Double.toString(y+clearance)})>0)return -2;for(int i=0;i<keys.length();i++){String key=keys.optString(i);db.execSQL("UPDATE inventory SET quantity=quantity-? WHERE identity_key=?",new Object[]{ingredients.optInt(key),key});}ContentValues v=new ContentValues();v.put("type",blueprint.identity);v.put("x",x);v.put("y",y);v.put("state","complete");v.put("json",blueprint.json.toString());v.put("created_at",System.currentTimeMillis());long id=db.insert("structures",null,v);db.setTransactionSuccessful();log("BUILD","Built generated structure "+blueprint.name);return id;}finally{db.endTransaction();}}

    public List<WorldStructure> structuresNear(float x,float y,float radius){ArrayList<WorldStructure> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,type,x,y,state,json FROM structures WHERE x BETWEEN ? AND ? AND y BETWEEN ? AND ? ORDER BY id",new String[]{Float.toString(x-radius),Float.toString(x+radius),Float.toString(y-radius),Float.toString(y+radius)})){while(c.moveToNext())try{out.add(new WorldStructure(c.getLong(0),c.getString(1),c.getFloat(2),c.getFloat(3),c.getString(4),new JSONObject(c.getString(5))));}catch(JSONException ignored){}}return out;}
    public WorldStructure structure(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT id,type,x,y,state,json FROM structures WHERE id=?",new String[]{Long.toString(id)})){if(c.moveToFirst())try{return new WorldStructure(c.getLong(0),c.getString(1),c.getFloat(2),c.getFloat(3),c.getString(4),new JSONObject(c.getString(5)));}catch(JSONException ignored){}}return null;}
    public List<String[]> chestItems(long structureId){ArrayList<String[]> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT identity_key,quantity FROM chest_storage WHERE structure_id=? AND quantity>0 ORDER BY identity_key",new String[]{Long.toString(structureId)})){while(c.moveToNext())out.add(new String[]{c.getString(0),Integer.toString(c.getInt(1))});}return out;}
    public boolean moveToChest(long structureId,String key,int quantity){if(quantity<=0)return false;SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{if(inventory(key)<quantity)return false;db.execSQL("UPDATE inventory SET quantity=quantity-? WHERE identity_key=?",new Object[]{quantity,key});db.execSQL("INSERT INTO chest_storage(structure_id,identity_key,quantity) VALUES(?,?,?) ON CONFLICT(structure_id,identity_key) DO UPDATE SET quantity=quantity+excluded.quantity",new Object[]{structureId,key,quantity});db.setTransactionSuccessful();return true;}finally{db.endTransaction();}}
    public boolean moveFromChest(long structureId,String key,int quantity){if(quantity<=0)return false;SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{long have=scalar("SELECT quantity FROM chest_storage WHERE structure_id=? AND identity_key=?",new String[]{Long.toString(structureId),key});if(have<quantity)return false;db.execSQL("UPDATE chest_storage SET quantity=quantity-? WHERE structure_id=? AND identity_key=?",new Object[]{quantity,structureId,key});db.execSQL("INSERT INTO inventory(identity_key,quantity) VALUES(?,?) ON CONFLICT(identity_key) DO UPDATE SET quantity=quantity+excluded.quantity",new Object[]{key,quantity});db.setTransactionSuccessful();return true;}finally{db.endTransaction();}}

    public void ensureCavesAround(float x,float y,long seed){int size=48,cx=(int)Math.floor(x/size),cy=(int)Math.floor(y/size);SQLiteDatabase db=getWritableDatabase();for(int oy=-1;oy<=1;oy++)for(int ox=-1;ox<=1;ox++){int gx=cx+ox,gy=cy+oy;long h=worldHash(seed^0xCAFE7711L,gx,gy);if((h&3)==0)continue;String natural="cave:"+gx+":"+gy;float px=gx*size+8+Math.floorMod((int)(h>>>8),size-16),py=gy*size+8+Math.floorMod((int)(h>>>24),size-16);boolean chest=(h&8)!=0;int loot=(int)((h>>>5)&3);if(loot>2)loot=2;JSONObject data=new JSONObject();try{data.put("natural",true).put("has_chest",chest).put("loot_count",loot);}catch(JSONException ignored){}ContentValues v=new ContentValues();v.put("type","wild_cave");v.put("x",px);v.put("y",py);v.put("state","complete");v.put("json",data.toString());v.put("created_at",0);v.put("natural_key",natural);long id=db.insertWithOnConflict("structures",null,v,SQLiteDatabase.CONFLICT_IGNORE);boolean created=id>0;if(id<0)id=scalar("SELECT id FROM structures WHERE natural_key=?",new String[]{natural});if(created&&chest&&id>0){String[] lootKeys={"healing_herb","iron_ingot","crystal","trail_ration","ancient_key"};for(int i=0;i<loot;i++){String key=lootKeys[Math.floorMod((int)(h>>>(12+i*7)),lootKeys.length)];db.execSQL("INSERT INTO chest_storage(structure_id,identity_key,quantity) VALUES(?,?,?) ON CONFLICT(structure_id,identity_key) DO UPDATE SET quantity=quantity+excluded.quantity",new Object[]{id,key,1});}}}}

    public void ensureMobsAround(float x,float y,long seed){int size=24,cx=(int)Math.floor(x/size),cy=(int)Math.floor(y/size);ArrayList<ContentItem> pool=new ArrayList<>();for(ContentItem c:list("CREATURE",64))if(!"hostile".equals(c.text("temperament","passive")))pool.add(c);if(pool.isEmpty())return;SQLiteDatabase db=getWritableDatabase();for(int oy=-1;oy<=1;oy++)for(int ox=-1;ox<=1;ox++)for(int i=0;i<2;i++){int gx=cx+ox,gy=cy+oy;long h=worldHash(seed,gx*31+i,gy*37-i);ContentItem info=pool.get(Math.floorMod((int)h,pool.size()));String kind=info.identity,spawn="wild:"+gx+":"+gy+":"+i;int health=info.number("health",12);float mx=gx*size+2+Math.floorMod((int)(h>>>8),size-4),my=gy*size+2+Math.floorMod((int)(h>>>24),size-4);ContentValues v=new ContentValues();v.put("species",kind);v.put("x",mx);v.put("y",my);v.put("health",health);v.put("born_at",0);v.put("love_until",0);v.put("cooldown_until",0);v.put("spawn_key",spawn);v.put("created_at",System.currentTimeMillis());db.insertWithOnConflict("mobs",null,v,SQLiteDatabase.CONFLICT_IGNORE);}}
    public List<MobState> nearbyMobs(float x,float y,float radius){ArrayList<MobState> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,species,x,y,health,born_at,love_until,cooldown_until,spawn_key FROM mobs WHERE health>0 AND x BETWEEN ? AND ? AND y BETWEEN ? AND ? ORDER BY id LIMIT 80",new String[]{Float.toString(x-radius),Float.toString(x+radius),Float.toString(y-radius),Float.toString(y+radius)})){while(c.moveToNext())out.add(new MobState(c.getLong(0),c.getString(1),c.getFloat(2),c.getFloat(3),c.getInt(4),c.getLong(5),c.getLong(6),c.getLong(7),c.getString(8)));}return out;}
    public int damageMob(long id,int amount){getWritableDatabase().execSQL("UPDATE mobs SET health=MAX(0,health-?) WHERE id=?",new Object[]{Math.max(0,amount),id});return (int)scalar("SELECT health FROM mobs WHERE id=?",new String[]{Long.toString(id)});}
    public void updateMob(MobState mob){ContentValues v=new ContentValues();v.put("x",mob.x);v.put("y",mob.y);v.put("love_until",mob.loveUntil);v.put("cooldown_until",mob.cooldownUntil);getWritableDatabase().update("mobs",v,"id=?",new String[]{Long.toString(mob.id)});}
    public MobState spawnBaby(String species,float x,float y,int health,long parentA,long parentB){long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("species",species);v.put("x",x);v.put("y",y);v.put("health",health);v.put("born_at",now);v.put("love_until",0);v.put("cooldown_until",now+5*60*1000L);v.put("spawn_key","baby:"+parentA+":"+parentB+":"+now);v.put("created_at",now);long id=getWritableDatabase().insert("mobs",null,v);return new MobState(id,species,x,y,health,now,0,now+5*60*1000L,"baby");}
    private long worldHash(long seed,int x,int y){long h=seed^(x*0x9E3779B97F4A7C15L)^(y*0xC2B2AE3D27D4EB4FL);h^=h>>>30;h*=0xBF58476D1CE4E5B9L;h^=h>>>27;h*=0x94D049BB133111EBL;return h^(h>>>31);}

    public void savePlayer(float x,float y,int health,int energy){ContentValues v=new ContentValues();v.put("x",x);v.put("y",y);v.put("health",health);v.put("energy",energy);getWritableDatabase().update("player",v,"id=1",null);}
    public double[] loadPlayer(){try(Cursor c=getReadableDatabase().rawQuery("SELECT x,y,health,energy,world_seed FROM player WHERE id=1",null)){c.moveToFirst();return new double[]{c.getDouble(0),c.getDouble(1),c.getInt(2),c.getInt(3),c.getLong(4)};}}

    public JSONObject exportWorld(String name){try{JSONObject root=new JSONObject();root.put("format","infinite-rpg-world-v1").put("name",name).put("saved_at",System.currentTimeMillis());root.put("player",queryJson("SELECT id,x,y,health,energy,world_seed FROM player",null));root.put("inventory",queryJson("SELECT identity_key,quantity FROM inventory WHERE quantity>0",null));root.put("structures",queryJson("SELECT id,type,x,y,state,json,created_at,natural_key FROM structures",null));root.put("chest_storage",queryJson("SELECT structure_id,identity_key,quantity FROM chest_storage WHERE quantity>0",null));root.put("mobs",queryJson("SELECT id,species,x,y,health,born_at,love_until,cooldown_until,spawn_key,created_at FROM mobs",null));root.put("generated_content",queryJson("SELECT id,type,identity_key,name,json,signature,source,judge_score,created_at FROM content WHERE source='qwen'",null));root.put("content_control",queryJson("SELECT type,identity_key,disabled FROM content_control",null));root.put("world_state",queryJson("SELECT key,value FROM state WHERE key IN ('world_clock_start','catalog_revision','creation_seq','last_creation_type','last_creation_key','last_creation_name','last_creation_description','world_name')",null));return root;}catch(JSONException e){throw new IllegalStateException(e);}}
    private JSONArray queryJson(String sql,String[] args)throws JSONException{JSONArray out=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery(sql,args)){String[] cols=c.getColumnNames();while(c.moveToNext()){JSONObject row=new JSONObject();for(int i=0;i<cols.length;i++){int type=c.getType(i);if(type==Cursor.FIELD_TYPE_NULL)row.put(cols[i],JSONObject.NULL);else if(type==Cursor.FIELD_TYPE_INTEGER)row.put(cols[i],c.getLong(i));else if(type==Cursor.FIELD_TYPE_FLOAT)row.put(cols[i],c.getDouble(i));else row.put(cols[i],c.getString(i));}out.put(row);}}return out;}
    public boolean importWorld(JSONObject root){if(root==null||!"infinite-rpg-world-v1".equals(root.optString("format")))return false;SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.delete("chest_storage",null,null);db.delete("structures",null,null);db.delete("mobs",null,null);db.delete("inventory",null,null);db.delete("player",null,null);db.delete("content_control",null,null);db.delete("content","source='qwen'",null);insertRows(db,"player",root.optJSONArray("player"));insertRows(db,"inventory",root.optJSONArray("inventory"));insertRows(db,"structures",root.optJSONArray("structures"));insertRows(db,"chest_storage",root.optJSONArray("chest_storage"));insertRows(db,"mobs",root.optJSONArray("mobs"));insertRows(db,"content",root.optJSONArray("generated_content"));insertRows(db,"content_control",root.optJSONArray("content_control"));JSONArray states=root.optJSONArray("world_state");if(states!=null)for(int i=0;i<states.length();i++){JSONObject row=states.optJSONObject(i);if(row!=null)db.execSQL("INSERT INTO state(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",new Object[]{row.optString("key"),row.optString("value")});}db.execSQL("INSERT INTO state(key,value) VALUES('world_name',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",new Object[]{root.optString("name","Infinite RPG World")});db.setTransactionSuccessful();}catch(Exception e){log("ERROR","World load failed: "+e.getMessage());return false;}finally{db.endTransaction();}bumpCatalog();log("WORLD","Loaded world "+root.optString("name","Infinite RPG World"));return true;}
    private void insertRows(SQLiteDatabase db,String table,JSONArray rows)throws JSONException{if(rows==null)return;for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null)continue;ContentValues v=new ContentValues();JSONArray names=row.names();if(names==null)continue;for(int j=0;j<names.length();j++){String key=names.optString(j);if("content".equals(table)&&"id".equals(key))continue;Object value=row.opt(key);if(value==null||value==JSONObject.NULL)v.putNull(key);else if(value instanceof Integer)v.put(key,(Integer)value);else if(value instanceof Long)v.put(key,(Long)value);else if(value instanceof Number)v.put(key,((Number)value).doubleValue());else v.put(key,String.valueOf(value));}db.insertOrThrow(table,null,v);}}
    public void createNewWorld(String name,long worldSeed){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.delete("chest_storage",null,null);db.delete("structures",null,null);db.delete("mobs",null,null);db.delete("inventory",null,null);db.delete("content_control",null,null);db.delete("content","source='qwen'",null);db.execSQL("UPDATE player SET x=0,y=0,health=100,energy=100,world_seed=? WHERE id=1",new Object[]{worldSeed});db.execSQL("INSERT INTO inventory(identity_key,quantity) VALUES('wood',12),('stone',12),('fiber',8),('healing_herb',3) ON CONFLICT(identity_key) DO UPDATE SET quantity=excluded.quantity");db.execSQL("INSERT INTO state(key,value) VALUES('world_name',?),('world_clock_start',?),('catalog_revision','0') ON CONFLICT(key) DO UPDATE SET value=excluded.value",new Object[]{name,Long.toString(System.currentTimeMillis())});db.setTransactionSuccessful();}finally{db.endTransaction();}log("WORLD","Created world "+name+" with seed "+worldSeed);}

    public String getState(String key,String fallback){try(Cursor c=getReadableDatabase().rawQuery("SELECT value FROM state WHERE key=?",new String[]{key})){return c.moveToFirst()?c.getString(0):fallback;}}
    public void putState(String key,String value){getWritableDatabase().execSQL("INSERT INTO state(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",new Object[]{key,value});}
    private void putStateIfAbsent(String key,String value){getWritableDatabase().execSQL("INSERT OR IGNORE INTO state(key,value) VALUES(?,?)",new Object[]{key,value});}
    public void log(String level,String message){ContentValues v=new ContentValues();v.put("level",level);v.put("message",message);v.put("created_at",System.currentTimeMillis());getWritableDatabase().insert("events",null,v);getWritableDatabase().execSQL("DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY id DESC LIMIT 500)");}
    public List<String> recentEvents(int limit){ArrayList<String> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT level,message FROM events ORDER BY id DESC LIMIT ?",new String[]{Integer.toString(limit)})){while(c.moveToNext())out.add(c.getString(0)+"  "+c.getString(1));}return out;}
    public long scalar(String sql,String[] args){try(Cursor c=getReadableDatabase().rawQuery(sql,args)){return c.moveToFirst()?c.getLong(0):0;}}
    public JSONObject stats(){JSONObject o=new JSONObject();try{o.put("content",scalar("SELECT COUNT(*) FROM content",null));o.put("queued",scalar("SELECT COUNT(*) FROM jobs WHERE status='queued'",null));o.put("done",scalar("SELECT COUNT(*) FROM jobs WHERE status='done'",null));o.put("rejected",scalar("SELECT COUNT(*) FROM jobs WHERE status='rejected'",null));o.put("ai",scalar("SELECT COUNT(*) FROM content WHERE source='qwen'",null));}catch(JSONException ignored){}return o;}

    public static String normalize(String input){return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","_").replaceAll("^_+|_+$","");}
    public static String signature(String text){String clean=text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]"," ");Set<String>s=new HashSet<>(Arrays.asList(clean.split("\\s+")));s.removeAll(Arrays.asList("the","a","an","of","and","to","in","is","with","for","key","name"));ArrayList<String>l=new ArrayList<>(s);Collections.sort(l);return String.join(" ",l);}
    private static double jaccard(String a,String b){Set<String>x=new HashSet<>(Arrays.asList(a.split(" ")));Set<String>y=new HashSet<>(Arrays.asList(b.split(" ")));Set<String>i=new HashSet<>(x);i.retainAll(y);Set<String>u=new HashSet<>(x);u.addAll(y);return u.isEmpty()?0:(double)i.size()/u.size();}
}
