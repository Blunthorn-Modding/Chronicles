package io.github.chronicle.core.client;

import com.google.gson.JsonParser;
import io.github.chronicle.core.ChronicleCore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Append-only defaults for new pack revisions; existing native server settings win. */
public final class ClientServerPresets {
    private ClientServerPresets() { }
    public static void register() { ClientLifecycleEvents.CLIENT_STARTED.register(ClientServerPresets::apply); }

    public static void apply(MinecraftClient client) {
        var config=FabricLoader.getInstance().getConfigDir().resolve("chronicle/client-servers.json");
        if(!Files.isRegularFile(config))return;
        try {
            if(Files.size(config)>65_536)throw new IllegalArgumentException("Preset document is too large");
            var root=JsonParser.parseString(Files.readString(config)).getAsJsonObject();
            if(!root.keySet().equals(java.util.Set.of("version","servers"))||!root.get("version").toString().equals("1"))
                throw new IllegalArgumentException("Unsupported preset document");
            var definitions=root.getAsJsonArray("servers");
            if(definitions.size()>64)throw new IllegalArgumentException("Too many server presets");
            Map<String,ServerInfo> presets=new LinkedHashMap<>();
            for(var element:definitions){
                var entry=element.getAsJsonObject();
                if(!entry.keySet().equals(java.util.Set.of("name","address")))throw new IllegalArgumentException("Invalid server preset");
                if(!entry.get("name").isJsonPrimitive()||!entry.get("name").getAsJsonPrimitive().isString()
                        ||!entry.get("address").isJsonPrimitive()||!entry.get("address").getAsJsonPrimitive().isString())throw new IllegalArgumentException("Preset text must be strings");
                String name=entry.get("name").getAsString(),address=entry.get("address").getAsString();
                if(name.isBlank()||name.length()>80||name.chars().anyMatch(Character::isISOControl)||address.length()>255||address.chars().anyMatch(Character::isWhitespace))
                    throw new IllegalArgumentException("Invalid server preset text");
                if(!address.startsWith("127.0.0.1:")||!address.substring(10).matches("[0-9]{1,5}"))throw new IllegalArgumentException("Presets require explicit loopback review addresses");
                int port=Integer.parseInt(address.substring(10));
                if(port<1||port>65535||port==25575)throw new IllegalArgumentException("Invalid review port");
                String key=key(address);
                if(presets.putIfAbsent(key,new ServerInfo(name,address,ServerInfo.ServerType.OTHER))!=null)
                    throw new IllegalArgumentException("Duplicate server preset");
            }
            if(presets.isEmpty())return;
            // Validate first: native loadFile logs exceptions and falls back to an empty
            // list, which must never be mistaken for permission to overwrite bad data.
            var file=client.runDirectory.toPath().resolve("servers.dat");
            Map<String,String> hidden=new LinkedHashMap<>();
            var existing=new HashSet<String>();var visibleKeys=new HashSet<String>();
            int visible=0;
            if(Files.exists(file)){
                if(Files.size(file)>8_388_608)throw new IllegalArgumentException("Existing server list is too large");
                var raw=NbtIo.read(file);
                if(raw==null||!(raw.get("servers") instanceof NbtList entries))throw new IllegalArgumentException("Invalid existing server list");
                for(var element:entries){
                    if(!(element instanceof NbtCompound entry))throw new IllegalArgumentException("Invalid existing server entry");
                    String address=entry.getString("ip").orElseThrow();
                    entry.getString("name").orElseThrow();
                    String key=key(address);existing.add(key);
                    if(entry.getBoolean("hidden").orElse(false))hidden.putIfAbsent(key,address);else{visible++;visibleKeys.add(key);}
                }
            }
            var servers=new ServerList(client);servers.loadFile();
            if(servers.size()!=visible)throw new IllegalStateException("Existing server list did not load completely");
            boolean changed=false;
            for(var preset:presets.entrySet()){
                if(existing.add(preset.getKey())){servers.add(preset.getValue(),false);changed=true;}
                else if(hidden.containsKey(preset.getKey())&&!visibleKeys.contains(preset.getKey())){
                    // Native promotion keeps its user-supplied name, icon and pack policy.
                    if(servers.tryUnhide(hidden.get(preset.getKey()))==null)throw new IllegalStateException("Hidden entry did not load");
                    changed=true;
                }
            }
            if(changed){servers.saveFile();ChronicleCore.LOGGER.info("Chronicle client server presets merged");}
        }catch(Exception error){ChronicleCore.LOGGER.warn("Client server presets skipped; existing server list preserved",error);}
    }

    private static String key(String address) {
        if(!ServerAddress.isValid(address))throw new IllegalArgumentException("Invalid Minecraft server address");
        var parsed=ServerAddress.parse(address);String host=parsed.getAddress().toLowerCase(Locale.ROOT);
        if(host.equals("localhost")||host.equals("::1")||host.equals("[::1]")||host.equals("127.0.0.1"))host="127.0.0.1";
        return host+":"+parsed.getPort();
    }
}
