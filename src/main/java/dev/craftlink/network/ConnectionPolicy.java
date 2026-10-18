package dev.craftlink.network;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Properties;

/** Release kits accept local-network peers and have no built-in external relay. */
public final class ConnectionPolicy {
    private static final boolean REMOTE=readRemote();
    private static boolean readRemote(){
        try(var stream=ConnectionPolicy.class.getResourceAsStream("/craftlink-network.properties")){
            if(stream==null)return false;
            var values=new Properties();values.load(stream);
            return Boolean.parseBoolean(values.getProperty("remoteAccess","false"));
        }catch(java.io.IOException e){return false;}
    }
    public static boolean remoteEnabled(){return REMOTE;}
    public static boolean allows(SocketAddress peer){
        if(REMOTE)return true;
        if(!(peer instanceof InetSocketAddress socket)||socket.getAddress()==null)return false;
        var address=socket.getAddress();
        if(address.isLoopbackAddress()||address.isSiteLocalAddress()||address.isLinkLocalAddress())return true;
        byte[] bytes=address.getAddress();
        return bytes.length==16&&(bytes[0]&0xfe)==0xfc;
    }
    private ConnectionPolicy(){}
}
