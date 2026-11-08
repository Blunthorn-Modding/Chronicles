package io.github.chronicle.core.eventpool;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Reusable weighted references; no live selection or secret state. */
public record EventPoolDefinition(String id,String label,List<Entry> entries) {
    public record Entry(String eventId,int weight) {
        public Entry { identifier(eventId);if(weight<1||weight>10_000)throw new IllegalArgumentException("Invalid weight"); }
    }
    public EventPoolDefinition {
        identifier(id);if(label==null||label.isBlank()||label.codePointCount(0,label.length())>80||label.codePoints().anyMatch(c->c==0xa7||Character.isISOControl(c)))throw new IllegalArgumentException("Invalid pool label");
        entries=List.copyOf(entries);if(entries.size()>64||entries.stream().map(Entry::eventId).distinct().count()!=entries.size())throw new IllegalArgumentException("Invalid pool entries");
    }
    static void identifier(String value){if(value==null||!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||value.length()>128)throw new IllegalArgumentException("Invalid identifier");}
    public String fingerprint(){
        try {var digest=MessageDigest.getInstance("SHA-256");
            update(digest,id);update(digest,label);entries.stream().sorted(Comparator.comparing(Entry::eventId)).forEach(e->{update(digest,e.eventId());update(digest,Integer.toString(e.weight()));});
            return HexFormat.of().formatHex(digest.digest());
        }catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static void update(MessageDigest digest,String text){byte[] bytes=text.getBytes(StandardCharsets.UTF_8);digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);}
}
