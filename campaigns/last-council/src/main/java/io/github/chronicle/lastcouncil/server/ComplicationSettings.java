package io.github.chronicle.lastcouncil.server;

import com.google.gson.*;
import io.github.chronicle.core.books.BookConfig;
import io.github.chronicle.core.persistence.AtomicDataFile;
import io.github.chronicle.lastcouncil.complications.ComplicationConfig;
import java.io.IOException;
import java.nio.charset.*;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;

/** Reusable setup, separate from the world's private allocation and receipts. */
final class ComplicationSettings {
    private final AtomicDataFile file;
    private ComplicationConfig config;
    ComplicationSettings(Path path,ComplicationConfig defaults)throws IOException {
        file=new AtomicDataFile(path,16384);byte[] bytes=file.load();
        config=bytes==null?defaults:decode(bytes);
        if(bytes==null)file.save(encode(config));
    }
    ComplicationConfig config(){return config;}
    void verify()throws IOException{file.verifyUnchanged();}
    void save(ComplicationConfig next)throws IOException{file.save(encode(next));config=next;}
    static byte[] encode(ComplicationConfig c) {
        JsonObject root=new JsonObject();root.addProperty("schema",1);root.addProperty("poolId",c.poolId());root.addProperty("total",c.total());
        JsonArray meetings=new JsonArray();c.meetings().forEach(meetings::add);root.add("meetings",meetings);
        root.addProperty("earliestTicks",c.earliestTicks());root.addProperty("latestTicks",c.latestTicks());root.addProperty("spacingTicks",c.spacingTicks());
        return (root+"\n").getBytes(StandardCharsets.UTF_8);
    }
    static ComplicationConfig decode(byte[] bytes)throws IOException {
        try {
            String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonObject r=BookConfig.strictObject(text);
            if(!r.keySet().equals(Set.of("schema","poolId","total","meetings","earliestTicks","latestTicks","spacingTicks"))||number(r.get("schema"))!=1)
                throw new IllegalArgumentException("Invalid setup schema");
            List<Integer> meetings=new ArrayList<>();for(JsonElement m:r.getAsJsonArray("meetings"))meetings.add(Math.toIntExact(number(m)));
            var id=r.get("poolId");if(!id.isJsonPrimitive()||!id.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Invalid pool ID");
            return new ComplicationConfig(id.getAsString(),Math.toIntExact(number(r.get("total"))),meetings,number(r.get("earliestTicks")),number(r.get("latestTicks")),number(r.get("spacingTicks")));
        }catch(RuntimeException|CharacterCodingException invalid){throw new IOException("Invalid complication settings; file preserved",invalid);}
    }
    private static long number(JsonElement e) {
        if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber()||!e.getAsString().matches("0|[1-9][0-9]*"))throw new IllegalArgumentException("Expected integer");
        return Long.parseLong(e.getAsString());
    }
}
