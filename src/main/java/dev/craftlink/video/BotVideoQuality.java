package dev.craftlink.video;

/** Bounded presets; clients cannot request arbitrary render sizes or compression settings. */
public enum BotVideoQuality {
    SMOOTH("smooth",640,360,.70f), BALANCED("balanced",854,480,.82f), SHARP("sharp",960,540,.88f);
    public final String id;
    public final int width, height;
    public final float jpegQuality;
    BotVideoQuality(String id,int width,int height,float quality){this.id=id;this.width=width;this.height=height;this.jpegQuality=quality;}
    public static BotVideoQuality parse(String id){
        for(var value:values())if(value.id.equals(id))return value;
        throw new IllegalArgumentException("Unknown video quality");
    }
}
