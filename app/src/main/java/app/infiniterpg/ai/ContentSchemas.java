package app.infiniterpg.ai;

import java.util.HashMap;
import java.util.Map;

public final class ContentSchemas {
    private static final Map<String,String> SCHEMAS=new HashMap<>();
    static {
        SCHEMAS.put("MATERIAL","Required keys and types: identity_key=nonempty unique snake_case string; name=nonempty original string; description=one concrete sentence; color=one #RRGGBB string; rarity=integer 1-5; biome=existing biome key; gather_min=integer 1-4; gather_max=integer 2-8; uses=array of useful strings.");
        SCHEMAS.put("ITEM","Required keys and types: identity_key, name, description are nonempty strings; category is tool, weapon, armor, food, curio, placeable, or component; power integer 1-30; color #RRGGBB; durability integer 10-200; palette=array of exactly six #RRGGBB strings; pixels=array of exactly sixteen strings, each exactly sixteen digits 0-5.");
        SCHEMAS.put("RECIPE","Required keys and types: identity_key, name, description are nonempty strings; result=existing item or material key; quantity integer 1-3; ingredients=JSON object whose keys are existing item/material keys and values are positive integers; station is hand, campfire, forge, or workbench.");
        SCHEMAS.put("BIOME","Required keys and types: identity_key=nonempty unique snake_case string; name=nonempty original string; description=one concrete sentence; palette=array of exactly four #RRGGBB strings; noise_min number 0-0.9; noise_max number larger than noise_min and at most 1; danger integer 1-5; resources=array of existing material-key strings; ambient=nonempty atmosphere string.");
        SCHEMAS.put("TILE_STYLE","Required keys and types: identity_key, name, description, biome are nonempty strings; blend number 0.10-0.35; palette=array of exactly six #RRGGBB strings; pixels=array of exactly sixteen strings, each exactly sixteen digits 0-5. Pixels are mandatory.");
        SCHEMAS.put("CREATURE","Required keys and types: nonempty identity_key, name, description, biome; color #RRGGBB; health integer 5-200; attack integer 0-30; speed integer 1-8; temperament passive, wary, or hostile; food=existing key; drops=object of existing material keys to integers 1-5; breedable boolean; palette=six #RRGGBB strings; pixels=sixteen strings of sixteen digits 0-5.");
        SCHEMAS.put("QUEST","Required keys and types: nonempty identity_key, name, description; objective gather, craft, explore, or defeat; target=existing key; amount integer 1-20; reward=existing item key; reward_quantity integer 1-5.");
        SCHEMAS.put("MUSIC","Required keys and types: nonempty identity_key, name, description; tempo integer 55-140; scale minor_pentatonic, major_pentatonic, dorian, or aeolian; root integer 36-60; wave sine, triangle, or soft_square; chords=8 integers 0-6; melody=32 integers -1 or 0-16; bass=16 integers -1 or 0-8; rhythm=16 integers 0 or 1; lead, ensemble, percussion, and form are optional strings.");
        SCHEMAS.put("STRUCTURE","Required keys and types: nonempty identity_key, name, description; width and height integers 1-8; enterable boolean; storage_slots integer 0-40; required_items=object of existing item/material keys to positive integers; palette=six #RRGGBB strings; pixels=sixteen strings of sixteen digits 0-5.");
        SCHEMAS.put("WEATHER","Required keys and types: nonempty identity_key, name, description; color #RRGGBB; particles integer 0-120; light number 0.35-1; duration_minutes integer 2-8; biomes=array of existing biome-key strings; effect none, rain, snow, fog, storm, or wind.");
    }
    public static String forType(String type){return SCHEMAS.getOrDefault(type,"{identity_key,name,description}");}
}
