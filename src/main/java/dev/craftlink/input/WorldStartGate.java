package dev.craftlink.input;

/** Loading screens and world replacements must settle before starting a remote room. */
public final class WorldStartGate {
    private Object world;
    private int readyTicks;
    public boolean tick(Object nextWorld,boolean loaded){
        if(nextWorld!=world){world=nextWorld;readyTicks=0;}
        if(nextWorld==null || !loaded){readyTicks=0;return false;}
        readyTicks=Math.min(20,readyTicks+1);return readyTicks==20;
    }
}
