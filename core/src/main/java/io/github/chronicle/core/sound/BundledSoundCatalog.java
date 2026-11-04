package io.github.chronicle.core.sound;

import com.google.gson.*;
import io.github.chronicle.core.books.BookConfig;
import java.io.*;
import java.net.URI;
import java.nio.charset.*;
import java.security.*;
import java.util.*;

/** Portable, source-controlled audio declarations. URLs are attribution only, never playback sources. */
public record BundledSoundCatalog(String namespace,List<Sound> sounds) {
    public static final int MAX_MANIFEST_BYTES=256*1024,MAX_FILE_BYTES=8*1024*1024,MAX_TOTAL_BYTES=64*1024*1024;
    private static final Set<String> FIELDS=Set.of("id","label","subtitleKey","file","sha256","source","creator","title","license","licenseUrl","changes","downloadUrl","stream");
    public record Sound(String id,String label,String subtitleKey,String file,String sha256,String source,String creator,String title,String license,String licenseUrl,String changes,String downloadUrl,boolean stream){
        public Sound{
            if(id==null||!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||id.length()>160)throw new IllegalArgumentException("Invalid bundled sound ID.");
            plain(label,80);plain(subtitleKey,160);plain(creator,256);plain(title,256);plain(license,80);plain(changes,1024);
            if(file==null||file.length()>160||!file.matches("[a-z0-9_./-]+")||file.endsWith(".ogg")||Arrays.stream(file.split("/",-1)).anyMatch(s->s.isEmpty()||s.equals(".")||s.equals("..")))throw new IllegalArgumentException("Bundled sound file must be a safe resource path without its extension.");
            if(sha256==null||!sha256.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Bundled sound SHA256 is required.");
            url(source);url(licenseUrl);url(downloadUrl);
        }
    }
    public BundledSoundCatalog{
        if(namespace==null||!namespace.matches("[a-z0-9_.-]+")||namespace.length()>80||sounds==null||sounds.isEmpty()||sounds.size()>128)throw new IllegalArgumentException("A bounded namespaced bundled sound catalog is required.");
        sounds=List.copyOf(sounds);Set<String> ids=new HashSet<>(),files=new HashSet<>();for(var sound:sounds)if(!sound.id().startsWith(namespace+":")||!ids.add(sound.id())||!files.add(sound.file()))throw new IllegalArgumentException("Bundled sound IDs/files must be unique and owned by their namespace.");
    }
    @FunctionalInterface public interface Resources {InputStream open(String absolutePath)throws IOException;}
    public static BundledSoundCatalog read(InputStream input)throws IOException{
        if(input==null)throw new IOException("Bundled event sound manifest is missing.");String source=text(input,MAX_MANIFEST_BYTES);JsonObject root=BookConfig.strictObject(source);
        exact(root,Set.of("schemaVersion","namespace","sounds"));if(!root.get("schemaVersion").toString().equals("1")||!root.get("sounds").isJsonArray()||root.getAsJsonArray("sounds").size()>128)throw new IllegalArgumentException("Unsupported bundled sound manifest.");
        List<Sound> entries=new ArrayList<>();for(var value:root.getAsJsonArray("sounds")){if(!value.isJsonObject())throw new IllegalArgumentException("Invalid sound entry.");var o=value.getAsJsonObject();exact(o,FIELDS);if(!o.get("stream").isJsonPrimitive()||!o.getAsJsonPrimitive("stream").isBoolean())throw new IllegalArgumentException("Sound stream must be boolean.");entries.add(new Sound(string(o,"id"),string(o,"label"),string(o,"subtitleKey"),string(o,"file"),string(o,"sha256"),string(o,"source"),string(o,"creator"),string(o,"title"),string(o,"license"),string(o,"licenseUrl"),string(o,"changes"),string(o,"downloadUrl"),o.get("stream").getAsBoolean()));}
        return new BundledSoundCatalog(string(root,"namespace"),entries);
    }
    public void verify(Resources resources)throws IOException{
        var registry=BookConfig.strictObject(resourceText(resources,"/assets/"+namespace+"/sounds.json",MAX_MANIFEST_BYTES));
        var language=BookConfig.strictObject(resourceText(resources,"/assets/"+namespace+"/lang/en_us.json",512*1024));
        if(resourceText(resources,"/"+namespace+"-sound-credits.txt",MAX_MANIFEST_BYTES).isBlank())throw new IOException("Bundled audio attribution is missing.");
        long total=0;
        for(var sound:sounds){
            String key=sound.id().substring(namespace.length()+1);if(!registry.has(key)||!registry.get(key).isJsonObject())throw new IOException("Bundled sound definition is missing: "+sound.id());var definition=registry.getAsJsonObject(key);exact(definition,Set.of("subtitle","sounds"));
            if(!string(definition,"subtitle").equals(sound.subtitleKey())||!language.has(sound.subtitleKey())||!language.get(sound.subtitleKey()).isJsonPrimitive()||!language.getAsJsonPrimitive(sound.subtitleKey()).isString()||language.get(sound.subtitleKey()).getAsString().isBlank())throw new IOException("Bundled subtitle is missing: "+sound.id());
            if(!definition.get("sounds").isJsonArray()||definition.getAsJsonArray("sounds").size()!=1||!definition.getAsJsonArray("sounds").get(0).isJsonObject())throw new IOException("Bundled sound must reference its declared audio: "+sound.id());var reference=definition.getAsJsonArray("sounds").get(0).getAsJsonObject();exact(reference,Set.of("name","stream"));
            if(!string(reference,"name").equals(namespace+":"+sound.file())||!reference.get("stream").isJsonPrimitive()||!reference.getAsJsonPrimitive("stream").isBoolean()||reference.get("stream").getAsBoolean()!=sound.stream())throw new IOException("Bundled audio reference differs from its manifest: "+sound.id());
            byte[] bytes=resource(resources,"/assets/"+namespace+"/sounds/"+sound.file()+".ogg",MAX_FILE_BYTES);total+=bytes.length;if(total>MAX_TOTAL_BYTES)throw new IOException("Bundled sound catalog exceeds 64 MiB.");
            if(!sha256(bytes).equals(sound.sha256()))throw new IOException("Bundled audio hash differs: "+sound.id()+". Install the matching mod/pack.");OggVorbisInfo.inspect(bytes);
        }
    }
    public String fingerprint(){StringBuilder bytes=new StringBuilder(namespace).append('\n');sounds.stream().sorted(Comparator.comparing(Sound::id)).forEach(s->bytes.append(s.id()).append('|').append(s.sha256()).append('|').append(s.file()).append('|').append(s.stream()).append('|').append(s.subtitleKey()).append('\n'));return sha256(bytes.toString().getBytes(StandardCharsets.UTF_8));}
    public static String sha256(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private static void plain(String value,int max){if(value==null||value.isBlank()||value.length()>max||value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Bundled audio metadata must be bounded plain text.");}
    private static void url(String value){plain(value,2048);try{var uri=URI.create(value);if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null)throw new IllegalArgumentException("Audio metadata requires a public source/license URL.");}catch(IllegalArgumentException error){throw new IllegalArgumentException("Invalid audio attribution URL.",error);}}
    private static String string(JsonObject object,String key){var v=object.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Missing sound text: "+key);return v.getAsString();}
    private static void exact(JsonObject object,Set<String> fields){if(!object.keySet().equals(fields))throw new IllegalArgumentException("Unexpected or missing bundled sound fields.");}
    private static String text(InputStream input,int max)throws IOException{byte[] bytes=input.readNBytes(max+1);if(bytes.length>max)throw new IOException("Bundled audio resource exceeds its size limit.");try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException error){throw new IOException("Bundled audio metadata must be UTF-8.",error);}}
    private static byte[] resource(Resources resources,String path,int max)throws IOException{try(var stream=resources.open(path)){if(stream==null)throw new IOException("Bundled audio resource is missing: "+path);byte[] bytes=stream.readNBytes(max+1);if(bytes.length>max)throw new IOException("Bundled audio resource exceeds its size limit: "+path);return bytes;}}
    private static String resourceText(Resources resources,String path,int max)throws IOException{try(var stream=resources.open(path)){if(stream==null)throw new IOException("Bundled audio resource is missing: "+path);return text(stream,max);}}
}
