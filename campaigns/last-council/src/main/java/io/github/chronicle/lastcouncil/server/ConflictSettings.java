package io.github.chronicle.lastcouncil.server;

import com.google.gson.JsonObject;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.persistence.AtomicDataFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Path;
import java.util.*;

/** Portable setup only; world conflict history never enters scenario defaults. */
public final class ConflictSettings {
    private final AtomicDataFile file;
    private int threshold;
    public ConflictSettings(Path path,int defaults)throws IOException{file=new AtomicDataFile(path,1024);byte[] bytes=file.load();threshold=bytes==null?valid(defaults):decode(bytes);if(bytes==null)file.save(encode(threshold));}
    public int threshold(){return threshold;}
    public void verify()throws IOException{file.verifyUnchanged();}
    public void save(int next)throws IOException{file.save(encode(next));threshold=next;}
    public static byte[] encode(int value){JsonObject o=new JsonObject();o.addProperty("schema",1);o.addProperty("civilWarThreshold",valid(value));return(o+"\n").getBytes(StandardCharsets.UTF_8);}
    public static int decode(byte[] bytes)throws IOException{try{if(bytes.length>1024)throw new IllegalArgumentException();String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();var o=BookConfig.strictObject(text);if(!o.keySet().equals(Set.of("schema","civilWarThreshold"))||!o.get("schema").toString().equals("1")||!o.get("civilWarThreshold").toString().matches("[1-9][0-9]*"))throw new IllegalArgumentException();return valid(Integer.parseInt(o.get("civilWarThreshold").getAsString()));}catch(Exception e){throw new IOException("Invalid conflict settings; preserved",e);}}
    private static int valid(int x){if(x<2||x>16)throw new IllegalArgumentException("Use a whole number from 2 to 16.");return x;}
    static void initialize(Path directory,String initial,boolean legacy)throws IOException{
        var marker=new AtomicDataFile(directory.resolve("last-council-conflicts.initialized"),32);byte[] bytes=marker.load();
        if(bytes!=null){if(!Arrays.equals(bytes,new byte[]{'1','\n'}))throw new IOException("Invalid conflict profile marker");var target=new AtomicDataFile(directory.resolve("last-council-conflicts.json"),1024);byte[] config=target.load();if(config==null)throw new IOException("Initialized conflict settings missing");decode(config);return;}
        var target=new AtomicDataFile(directory.resolve("last-council-conflicts.json"),1024);byte[] existing=target.load();int desired=!legacy&&initial!=null?decode(initial.getBytes(StandardCharsets.UTF_8)):4;
        if(existing!=null){decode(existing);if(!legacy&&initial!=null&&!Arrays.equals(existing,encode(desired)))throw new IOException("Conflict profile differs from import; preserved");}else target.save(encode(desired));
        marker.save(new byte[]{'1','\n'});
    }
}
