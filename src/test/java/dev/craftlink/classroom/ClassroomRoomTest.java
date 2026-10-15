package dev.craftlink.classroom;
import com.google.gson.JsonParser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ClassroomRoomTest {
    static class Socket implements ClassroomRoom.Socket {
        final List<String> messages = new ArrayList<>(); boolean closed;
        public void send(String json){messages.add(json);} public void close(){closed=true;}
        String resume(){return JsonParser.parseString(messages.get(0)).getAsJsonObject().get("resume").getAsString();}
    }
    @Test void qrJoinImmediatelyEnablesIndependentInputsAndManualPauseStillBlocks(){
        var room=new ClassroomRoom();var one=new Socket();var two=new Socket();
        UUID a=room.join(room.token(),"",one),b=room.join(room.token(),"",two);
        assertFalse(room.paused());assertTrue(room.views().stream().allMatch(ClassroomRoom.View::approved));
        room.pause(true);
        room.approve(a);room.approve(b);room.input(a,one,Set.of("forward"),20);assertTrue(room.takeInputs().get(a).input().held().isEmpty());
        room.pause(false);room.input(a,one,Set.of("forward"),20);room.input(b,two,Set.of("left"),-10);
        var inputs=room.takeInputs();assertEquals(Set.of("forward"),inputs.get(a).input().held());assertEquals(Set.of("left"),inputs.get(b).input().held());assertEquals(20,inputs.get(a).input().yaw());
        room.pause(true);assertTrue(room.takeInputs().values().stream().allMatch(input->input.input().held().isEmpty()));
        room.pause(false);assertTrue(room.takeInputs().get(a).input().held().isEmpty());
    }
    @Test void joiningWhileManuallyPausedDoesNotResumeAnyone(){
        var room=new ClassroomRoom();room.pause(true);var socket=new Socket();var id=room.join(room.token(),"",socket);
        assertTrue(room.paused());assertTrue(room.views().get(0).approved());assertTrue(room.takeInputs().containsKey(id));
        room.input(id,socket,Set.of("forward"),0);assertTrue(room.takeInputs().get(id).input().held().isEmpty());
    }
    @Test void eightPendingParticipantsFillTheRoomAndRemovalFreesOneSlot(){
        var room=new ClassroomRoom();for(int i=0;i<8;i++)room.join(room.token(),"",new Socket());
        assertThrows(IllegalArgumentException.class,()->room.join(room.token(),"",new Socket()));
        room.remove(room.views().get(0).id());room.join(room.token(),"",new Socket());assertEquals(8,room.views().size());
    }
    @Test void privateResumeRetainsOnlyItsOwnNpcAndOldSocketCannotDriveIt(){
        var room=new ClassroomRoom();var old=new Socket();UUID id=room.join(room.token(),"",old);room.approve(id);room.pause(false);
        room.input(id,old,Set.of("forward"),0);room.disconnect(id,old);assertTrue(room.takeInputs().get(id).input().held().isEmpty());
        var next=new Socket();assertEquals(id,room.join(room.token(),old.resume(),next));
        room.input(id,old,Set.of("forward"),0);room.disconnect(id,old);assertTrue(room.views().get(0).connected());assertTrue(room.takeInputs().get(id).input().held().isEmpty());
        room.input(id,next,Set.of("jump"),0);assertEquals(Set.of("jump"),room.takeInputs().get(id).input().held());
        assertThrows(IllegalArgumentException.class,()->room.join(room.token(),next.resume(),new Socket()));
    }
    @Test void expiredReconnectBecomesANewAutomaticallyApprovedParticipant(){
        long[] clock={1};var room=new ClassroomRoom(()->clock[0]);var socket=new Socket();UUID id=room.join(room.token(),"",socket);room.approve(id);room.disconnect(id,socket);
        clock[0]+=30_000_000_000L;assertTrue(room.takeInputs().isEmpty());
        UUID next=room.join(room.token(),socket.resume(),new Socket());assertNotEquals(id,next);assertTrue(room.views().get(0).approved());
    }
    @Test void invalidTokenActionsAndNonfiniteAnglesAreRejected(){
        var room=new ClassroomRoom();var socket=new Socket();assertThrows(IllegalArgumentException.class,()->room.join("wrong","",socket));
        UUID id=room.join(room.token(),"",socket);
        assertThrows(IllegalArgumentException.class,()->room.input(id,socket,Set.of("drop"),0));
        assertThrows(IllegalArgumentException.class,()->room.input(id,socket,Set.of(),Double.NaN));
        assertThrows(IllegalArgumentException.class,()->room.input(id,socket,Set.of(),181));
        room.close();assertTrue(socket.closed);assertTrue(room.takeInputs().isEmpty());assertThrows(IllegalArgumentException.class,()->room.join(room.token(),"",new Socket()));
    }
    @Test void missingInputReleasesEvenIfConnectionRemainsOpen() throws Exception {
        var room=new ClassroomRoom();var socket=new Socket();UUID id=room.join(room.token(),"",socket);room.approve(id);room.pause(false);room.input(id,socket,Set.of("forward"),0);
        Thread.sleep(800);assertTrue(room.takeInputs().get(id).input().held().isEmpty());assertTrue(room.views().get(0).connected());
    }@Test void botVideoIsAvailableOnJoinAndKeepsEachPeersFramesSeparate(){
    var room=new ClassroomRoom();var one=new VideoSocket();var two=new VideoSocket();
    var a=room.join(room.token(),"",one);var b=room.join(room.token(),"",two);
    room.video(a,one,true);assertEquals(1,room.videoTargets().size());
    room.approve(a);room.approve(b);room.video(a,one,true);room.video(b,two,true);
    var targets=room.videoTargets();assertEquals(2,targets.size());
    var targetA=targets.stream().filter(t->t.id().equals(a)).findFirst().orElseThrow();
    room.publishVideo(targetA,1,new byte[]{1});assertEquals(2,room.videoTargets().size());
    room.publishVideo(targetA,2,new byte[]{2});assertEquals(2,one.frames.size());assertTrue(two.frames.isEmpty());
    assertEquals(1,room.videoTargets().size());
    room.frameAck(a,two,1);assertEquals(1,room.videoTargets().size());
    room.frameAck(a,one,1);assertEquals(2,room.videoTargets().size());
    room.video(a,one,false);room.publishVideo(targetA,3,new byte[]{3});assertEquals(2,one.frames.size());
    room.close();assertTrue(room.videoTargets().isEmpty());
}
@Test void oldEncodedFramesCannotCrossReconnectAndAckTimeoutRecovers(){
    long[] clock={1};var room=new ClassroomRoom(()->clock[0]);var old=new VideoSocket();
    var id=room.join(room.token(),"",old);room.approve(id);room.video(id,old,true);
    var target=room.videoTargets().get(0);room.publishVideo(target,1,new byte[]{1});room.publishVideo(target,2,new byte[]{2});assertTrue(room.videoTargets().isEmpty());
    clock[0]+=2_000_000_001L;assertEquals(1,room.videoTargets().size());
    room.disconnect(id,old);var next=new VideoSocket();room.join(room.token(),old.resume(),next);room.video(id,next,true);
    room.publishVideo(target,2,new byte[]{2});assertTrue(next.frames.isEmpty());
}
@Test void botLookPitchIsValidatedAndDeliveredIndependently(){
    var room=new ClassroomRoom();var socket=new Socket();var id=room.join(room.token(),"",socket);room.approve(id);room.pause(false);
    room.input(id,socket,Set.of(),20,-15);var input=room.takeInputs().get(id).input();assertEquals(20,input.yaw());assertEquals(-15,input.pitch());
    assertThrows(IllegalArgumentException.class,()->room.input(id,socket,Set.of(),0,Double.NaN));
}
static class VideoSocket extends Socket {
    final List<byte[]> frames=new ArrayList<>();
    public boolean sendVideo(byte[] packet){frames.add(packet);return true;}
}
@Test void explicitHostApprovalStartsInputAndEmergencyPauseStillBlocksIt(){
    var room=new ClassroomRoom();var socket=new Socket();var id=room.join(room.token(),"",socket);
    room.pause(true);room.input(id,socket,Set.of("forward"),0);assertTrue(room.takeInputs().get(id).input().held().isEmpty());
    assertEquals(1,room.approveConnectedAndStart());assertFalse(room.paused());
    room.input(id,socket,Set.of("forward"),0);assertEquals(Set.of("forward"),room.takeInputs().get(id).input().held());
    room.pause(true);room.input(id,socket,Set.of("forward"),0);assertTrue(room.takeInputs().get(id).input().held().isEmpty());
    assertEquals(1,room.approveConnectedAndStart());assertTrue(room.takeInputs().get(id).input().held().isEmpty());
}
@Test void emptyOrClosedRoomCannotBeStartedByCombinedApproval(){
    var room=new ClassroomRoom();room.pause(true);assertEquals(0,room.approveConnectedAndStart());assertTrue(room.paused());
    var socket=new Socket();var id=room.join(room.token(),"",socket);room.disconnect(id,socket);
    assertEquals(0,room.approveConnectedAndStart());assertTrue(room.paused());
    room.close();assertEquals(0,room.approveConnectedAndStart());
}

@Test void qualitySwitchRejectsOldEncodedFramesAndWindowNeverExceedsTwo() {
    var room=new ClassroomRoom();var socket=new VideoSocket();var id=room.join(room.token(),"",socket);
    room.video(id,socket,true);var old=room.videoTargets().get(0);
    room.publishVideo(old,1,new byte[]{1});room.publishVideo(old,2,new byte[]{2});room.publishVideo(old,3,new byte[]{3});
    assertEquals(2,socket.frames.size());assertTrue(room.videoTargets().isEmpty());
    room.frameAck(id,socket,999);assertTrue(room.videoTargets().isEmpty());
    room.video(id,socket,true,dev.craftlink.video.BotVideoQuality.SMOOTH);
    room.publishVideo(old,4,new byte[]{4});assertEquals(2,socket.frames.size());
    var current=room.videoTargets().get(0);assertEquals(dev.craftlink.video.BotVideoQuality.SMOOTH,current.quality());
    room.publishVideo(current,5,new byte[]{5});assertEquals(3,socket.frames.size());
}
@Test void inventoryCommandsAreIsolatedAndPauseDropsQueuedSwaps(){
    var room=new ClassroomRoom();var one=new Socket();var two=new Socket();var a=room.join(room.token(),"",one);var b=room.join(room.token(),"",two);
    room.takeInputs();room.inventory(a,two,5);assertEquals(0,room.takeInputs().get(a).selectedSlot());
    room.inventory(a,one,5);var states=room.takeInputs();assertEquals(5,states.get(a).selectedSlot());assertEquals(0,states.get(b).selectedSlot());assertTrue(states.get(a).inventoryRequested());
    room.inventory(a,one,12);assertEquals(12,room.takeInputs().get(a).swapSlot());assertEquals(-1,room.takeInputs().get(a).swapSlot());
    room.inventory(a,one,13);room.pause(true);room.pause(false);assertEquals(-1,room.takeInputs().get(a).swapSlot());
    room.pause(true);room.inventory(a,one,7);room.inventory(a,one,14);states=room.takeInputs();assertEquals(5,states.get(a).selectedSlot());assertEquals(-1,states.get(a).swapSlot());
    assertThrows(IllegalArgumentException.class,()->room.inventory(a,one,36));assertThrows(IllegalArgumentException.class,()->room.inventory(a,one,-2));
}
@Test void creativeRequestsRemainBoundedIsolatedAndStopWithPause(){
    var room=new ClassroomRoom();var one=new Socket();var two=new Socket();var a=room.join(room.token(),"",one);var b=room.join(room.token(),"",two);
    room.creative(a,two,"",0,2);assertNull(room.takeInputs().get(a).creative());
    room.creative(a,one,"minecraft:building_blocks",1,-1);var state=room.takeInputs();assertEquals(1,state.get(a).creative().page());assertNull(state.get(b).creative());assertNull(room.takeInputs().get(a).creative());
    room.creative(a,one,"",0,3);room.pause(true);room.pause(false);assertNull(room.takeInputs().get(a).creative());
    room.pause(true);room.creative(a,one,"",0,4);assertNull(room.takeInputs().get(a).creative());
    assertThrows(IllegalArgumentException.class,()->room.creative(a,one,"",0,25000));assertThrows(IllegalArgumentException.class,()->room.creative(a,one,"",601,-1));assertThrows(IllegalArgumentException.class,()->room.creative(a,one,"../?",0,-1));
}

@Test void hostModeChangeClearsQueuedActionsAndNotifiesAllPhones(){
 var room=new ClassroomRoom();var one=new Socket();var two=new Socket();var a=room.join(room.token(),"",one);room.join(room.token(),"",two);
 assertEquals(ClassroomRoom.Mode.SURVIVAL,room.mode());room.input(a,one,Set.of("attack","use"),0);room.creative(a,one,"",0,1);
 room.mode(ClassroomRoom.Mode.CREATIVE);var input=room.takeInputs().get(a);assertTrue(input.input().held().isEmpty());assertTrue(input.input().presses().isEmpty());assertNull(input.creative());assertTrue(input.inventoryRequested());
 assertTrue(one.messages.stream().anyMatch(m->m.contains("creative")));assertTrue(two.messages.stream().anyMatch(m->m.contains("creative")));
}
@Test void directTouchUsesOnlyFreshFramesSentToThatPeerAndSurvivesShortTapRelease(){
 long[] now={1};var room=new ClassroomRoom(()->now[0]);var one=new VideoSocket();var two=new VideoSocket();var a=room.join(room.token(),"",one);var b=room.join(room.token(),"",two);
 room.video(a,one,true);var target=room.videoTargets().get(0);var view=new ClassroomRoom.ViewSample(1,2,3,90,15,16.0/9);
 room.publishVideo(target,7,new byte[]{1},view);
 room.input(a,one,Set.of("use"),0,0,new ClassroomRoom.Touch(7,.25,.75));room.input(a,one,Set.of(),0);
 var snapshot=room.takeInputs().get(a);assertTrue(snapshot.input().presses().contains("use"));assertEquals(.25,snapshot.aim().x());assertEquals(view,snapshot.aim().view());assertNull(room.takeInputs().get(a).aim());
 room.input(b,two,Set.of("attack"),0,0,new ClassroomRoom.Touch(7,.5,.5));assertTrue(room.takeInputs().get(b).input().held().isEmpty());
 now[0]+=2_000_000_001L;room.input(a,one,Set.of("attack"),0,0,new ClassroomRoom.Touch(7,.5,.5));assertTrue(room.takeInputs().get(a).input().held().isEmpty());
 assertThrows(IllegalArgumentException.class,()->new ClassroomRoom.Touch(7,Double.NaN,.5));assertThrows(IllegalArgumentException.class,()->new ClassroomRoom.Touch(7,.5,1.01));
}
@Test void pauseAndDisconnectCancelTouchMining(){
 var room=new ClassroomRoom();var socket=new VideoSocket();var id=room.join(room.token(),"",socket);room.video(id,socket,true);room.publishVideo(room.videoTargets().get(0),1,new byte[]{1},new ClassroomRoom.ViewSample(0,0,0,0,0,1));
 room.input(id,socket,Set.of("attack"),0,0,new ClassroomRoom.Touch(1,.5,.5));room.pause(true);var input=room.takeInputs().get(id);assertNull(input.aim());assertTrue(input.input().held().isEmpty());
 room.pause(false);room.input(id,socket,Set.of("attack"),0,0,new ClassroomRoom.Touch(1,.5,.5));room.disconnect(id,socket);input=room.takeInputs().get(id);assertNull(input.aim());assertTrue(input.input().held().isEmpty());
}
}
