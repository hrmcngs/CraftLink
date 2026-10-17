package dev.craftlink.classroom;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.craftlink.CraftLink.Client;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Local PC commands have the same authority as the native classroom controls. */
public final class ClassroomCommands {
    @SubscribeEvent public static void register(RegisterClientCommandsEvent event){
        var modes=Commands.literal("gamemode");
        for(var mode:ClassroomRoom.Mode.values()){
            var choice=Commands.literal(mode.name().toLowerCase(java.util.Locale.ROOT))
                .executes(context->changeMode(context,mode,0))
                .then(Commands.literal("all").executes(context->changeMode(context,mode,0)))
                .then(Commands.argument("number",IntegerArgumentType.integer(1,ClassroomRoom.LIMIT))
                    .suggests((context,builder)->{
                        var room=Client.CLASSROOM.room();
                        if(room!=null)for(var peer:room.views())builder.suggest(peer.number());
                        return builder.buildFuture();
                    })
                    .executes(context->changeMode(context,mode,IntegerArgumentType.getInteger(context,"number"))));
            modes.then(choice);
        }
        event.getDispatcher().register(Commands.literal("craftlink").then(modes).then(Commands.literal("tp")
            .then(Commands.argument("number",IntegerArgumentType.integer(1,ClassroomRoom.LIMIT))
                .suggests((context,builder)->{var room=Client.CLASSROOM.room();if(room!=null)for(var peer:room.views())builder.suggest(peer.number());return builder.buildFuture();})
                .executes(context->{
                    int number=IntegerArgumentType.getInteger(context,"number");
                    Client.BOTS.teleport(number,success->Minecraft.getInstance().execute(()->{
                        if(success)context.getSource().sendSuccess(()->Component.literal("Bot"+number+"を近くへ呼び寄せました"),false);
                        else context.getSource().sendFailure(Component.literal("対象のBOTまたは安全な移動先が見つかりません"));
                    }));return 1;
                }))));
    }
    private static int changeMode(CommandContext<CommandSourceStack> context,ClassroomRoom.Mode mode,int number){
        var room=Client.CLASSROOM.room();
        if(Minecraft.getInstance().getSingleplayerServer()==null||room==null||room.expired()){
            context.getSource().sendFailure(Component.literal("先にPCでCraftLinkの参加者ルームを開始してください"));return 0;
        }
        if(number==0)room.mode(mode);
        else if(!room.mode(number,mode)){
            context.getSource().sendFailure(Component.literal(number+"号の参加者が見つかりません"));return 0;
        }
        String target=number==0?"参加者全員":number+"号";
        String name=mode==ClassroomRoom.Mode.CREATIVE?"クリエイティブ":"サバイバル";
        context.getSource().sendSuccess(()->Component.literal(target+"を"+name+"に変更しました"),false);
        return 1;
    }
    private ClassroomCommands(){}
}
