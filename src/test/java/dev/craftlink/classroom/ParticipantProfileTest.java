package dev.craftlink.classroom;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParticipantProfileTest {
    @Test void unicodeNamesAreNormalizedAndBounded(){
        assertEquals("はると_7",new ParticipantProfile(" はると_7 ","alex").name());
        assertEquals("が",new ParticipantProfile("か\u3099","steve").name());
        assertDoesNotThrow(()->new ParticipantProfile("あ".repeat(16),"zuri"));
        for(String name:new String[]{"あ".repeat(17),"x\ny","§c名前","<script>","a\u202Eb","😀","x".repeat(65)})assertThrows(IllegalArgumentException.class,()->new ParticipantProfile(name,"steve"));
    }
    @Test void onlyBundledSkinIdsAreAcceptedAndDefaultsStayPerParticipant(){
        assertEquals(9,ParticipantProfile.SKINS.size());
        for(int i=0;i<9;i++)assertEquals(i,new ParticipantProfile("名前",ParticipantProfile.SKINS.get(i)).skinIndex(1));
        assertEquals(0,ParticipantProfile.DEFAULT.skinIndex(1));assertEquals(7,ParticipantProfile.DEFAULT.skinIndex(8));
        assertEquals("あか",ParticipantProfile.DEFAULT.displayName(1));
        for(String value:new String[]{"https://example.com/skin.png","../steve","STEVE","", "unknown"})assertThrows(IllegalArgumentException.class,()->new ParticipantProfile("",value));
    }
}
