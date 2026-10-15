package dev.craftlink.classroom;

import com.google.gson.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;

/** Built once on the client thread; immutable templates are copied on the server thread. */
public final class CreativeCatalog {
    public static final int PAGE_SIZE=45;
    private record Entry(String id,String name,ItemStack template) {}
    private record Tab(String id,String name,List<Integer> items) {}
    private final List<Entry> items;
    private final List<Tab> tabs;
    private CreativeCatalog(List<Entry> items,List<Tab> tabs){this.items=List.copyOf(items);this.tabs=List.copyOf(tabs);}
    public static CreativeCatalog build(Minecraft mc){
        CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(),false,mc.level.registryAccess());
        var items=new ArrayList<Entry>();var tabs=new ArrayList<Tab>();
        for(var tab:CreativeModeTabs.tabs()){
            if(tab.getType()!=CreativeModeTab.Type.CATEGORY || tabs.size()>=128)continue;
            var indices=new ArrayList<Integer>();
            for(var stack:tab.getDisplayItems()){
                if(stack.isEmpty() || items.size()>=25000)continue;
                indices.add(items.size());var copy=stack.copy();copy.setCount(1);
                items.add(new Entry(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getHoverName().getString(),copy));
            }
            if(!indices.isEmpty())tabs.add(new Tab(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab).toString(),tab.getDisplayName().getString(),List.copyOf(indices)));
        }
        return new CreativeCatalog(items,tabs);
    }
    public ItemStack stack(int index){
        if(index<0||index>=items.size())throw new IllegalArgumentException("Unknown creative item");
        var copy=items.get(index).template.copy();copy.setCount(copy.getMaxStackSize());return copy;
    }
    public JsonObject page(String tabId,int page){
        Tab tab=tabs.stream().filter(t->t.id.equals(tabId)).findFirst().orElse(tabs.isEmpty()?null:tabs.get(0));
        var result=new JsonObject();result.addProperty("type","creative");var categories=new JsonArray();
        for(var t:tabs){var json=new JsonObject();json.addProperty("id",t.id);json.addProperty("name",t.name);categories.add(json);}result.add("tabs",categories);
        int pages=tab==null?1:Math.max(1,(tab.items.size()+PAGE_SIZE-1)/PAGE_SIZE);int current=Math.max(0,Math.min(page,pages-1));
        result.addProperty("tab",tab==null?"":tab.id);result.addProperty("page",current);result.addProperty("pages",pages);var entries=new JsonArray();
        if(tab!=null)for(int i=current*PAGE_SIZE;i<Math.min(tab.items.size(),(current+1)*PAGE_SIZE);i++){
            int index=tab.items.get(i);var item=items.get(index);var json=new JsonObject();json.addProperty("index",index);json.addProperty("id",item.id);json.addProperty("name",item.name);entries.add(json);
        }
        result.add("items",entries);return result;
    }
}
