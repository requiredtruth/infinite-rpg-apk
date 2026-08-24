package app.infiniterpg.data;

import org.json.JSONObject;

public final class ContentItem {
    public final long id;
    public final String type;
    public final String identity;
    public final String name;
    public final JSONObject json;

    public ContentItem(long id, String type, String identity, String name, JSONObject json) {
        this.id = id; this.type = type; this.identity = identity; this.name = name; this.json = json;
    }

    public String text(String key, String fallback) { return json.optString(key, fallback); }
    public int number(String key, int fallback) { return json.optInt(key, fallback); }
    public double decimal(String key, double fallback) { return json.optDouble(key, fallback); }
}
