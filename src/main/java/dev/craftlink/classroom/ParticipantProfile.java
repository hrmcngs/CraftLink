package dev.craftlink.classroom;

import java.text.Normalizer;
import java.util.List;

/** Display identity only; participant UUID, permissions and resume token stay independent. */
public record ParticipantProfile(String name,String skin) {
    public static final List<String> SKINS=List.of("steve","alex","ari","efe","kai","makena","noor","sunny","zuri");
    public static final ParticipantProfile DEFAULT=new ParticipantProfile("","auto");
    public ParticipantProfile {
        if(name==null)name="";
        if(name.length()>64)throw new IllegalArgumentException("Name too long");
        name=Normalizer.normalize(name.strip(),Normalizer.Form.NFC);
        if(name.codePointCount(0,name.length())>16 || !name.codePoints().allMatch(c->Character.isLetterOrDigit(c)||c==' '||c=='_'||c=='-'))throw new IllegalArgumentException("Invalid display name");
        if(skin==null)skin="auto";
        if(!skin.equals("auto")&&!SKINS.contains(skin))throw new IllegalArgumentException("Unknown skin");
    }
    public String displayName(int number){return name.isEmpty()?"Bot"+number:name;}
    public int skinIndex(int number){return skin.equals("auto")?Math.floorMod(number-1,SKINS.size()):SKINS.indexOf(skin);}
}
