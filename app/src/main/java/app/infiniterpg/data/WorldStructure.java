package app.infiniterpg.data;

import org.json.JSONObject;

public final class WorldStructure {
    public final long id;public final String type;public final float x,y;public final String state;public final JSONObject json;
    public WorldStructure(long id,String type,float x,float y,String state,JSONObject json){this.id=id;this.type=type;this.x=x;this.y=y;this.state=state;this.json=json;}
}
