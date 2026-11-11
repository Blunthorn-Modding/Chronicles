package io.github.chronicle.core.sound;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BundledSoundCatalogTest {
    private static final String ENTRY="{\"id\":\"test:signal\",\"label\":\"Signal\",\"subtitleKey\":\"subtitles.test.signal\",\"file\":\"events/signal\",\"sha256\":\""+"a".repeat(64)+"\",\"source\":\"https://example.org/sound\",\"creator\":\"Creator\",\"title\":\"Recording\",\"license\":\"CC0-1.0\",\"licenseUrl\":\"https://creativecommons.org/publicdomain/zero/1.0/\",\"changes\":\"No edits\",\"downloadUrl\":\"https://example.org/audio.ogg\",\"stream\":false}";
    private static String manifest(String entries){return "{\"schemaVersion\":1,\"namespace\":\"test\",\"sounds\":["+entries+"]}";}
    private static BundledSoundCatalog read(String text)throws IOException{return BundledSoundCatalog.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));}
    @Test void readsSourceControlledMetadataAndStableFingerprint()throws Exception{var catalog=read(manifest(ENTRY));assertEquals("test:signal",catalog.sounds().getFirst().id());assertEquals(64,catalog.fingerprint().length());assertEquals(catalog.fingerprint(),read(manifest(ENTRY)).fingerprint());}
    @Test void rejectsDuplicateUnknownAndWrongTypedMetadata(){assertThrows(IllegalArgumentException.class,()->read(manifest(ENTRY+","+ENTRY)));assertThrows(IllegalArgumentException.class,()->read(manifest(ENTRY).replace("\"stream\":false","\"stream\":\"false\"")));assertThrows(IllegalArgumentException.class,()->read(manifest(ENTRY).replace("\"namespace\":\"test\"","\"namespace\":\"test\",\"namespace\":\"other\"")));assertThrows(IllegalArgumentException.class,()->read(manifest(ENTRY).replace("\"stream\":false","\"stream\":false,\"extra\":1")));}
    @Test void rejectsPathEscapeCredentialsWrongNamespaceAndMissingAudio(){for(String replacement:List.of("../signal","/signal","events//signal","events\\\\signal"))assertThrows(IllegalArgumentException.class,()->read(manifest(ENTRY.replace("events/signal",replacement))));assertThrows(IllegalArgumentException.class,()->read(manifest(ENTRY.replace("test:signal","other:signal"))));assertThrows(IllegalArgumentException.class,()->read(manifest(ENTRY.replace("https://example.org/sound","https://secret@example.org/sound"))));assertThrows(IOException.class,()->read(manifest(ENTRY)).verify(path->null));}
    @Test void metadataIsBoundedAndStrictUtf8(){assertThrows(IOException.class,()->read(" ".repeat(BundledSoundCatalog.MAX_MANIFEST_BYTES+1)));assertThrows(IOException.class,()->BundledSoundCatalog.read(new ByteArrayInputStream(new byte[]{(byte)0xc3,0x28})));}
    @Test void inspectsVorbisPagesAndRejectsCorruptionTruncationAndChainedStreams(){byte[] audio=container(false);var info=OggVorbisInfo.inspect(audio);assertEquals(1,info.channels());assertEquals(22050,info.sampleRate());assertEquals(1,info.seconds());byte[] corrupted=audio.clone();corrupted[corrupted.length-1]^=1;assertThrows(IllegalArgumentException.class,()->OggVorbisInfo.inspect(corrupted));assertThrows(IllegalArgumentException.class,()->OggVorbisInfo.inspect(Arrays.copyOf(audio,audio.length-1)));byte[] chained=Arrays.copyOf(audio,audio.length*2);System.arraycopy(audio,0,chained,audio.length,audio.length);assertThrows(IllegalArgumentException.class,()->OggVorbisInfo.inspect(chained));}
    @Test void rejectsOpusEvenWhenContainerChecksumMatches(){assertTrue(assertThrows(IllegalArgumentException.class,()->OggVorbisInfo.inspect(container(true))).getMessage().contains("Vorbis"));}
    private static byte[] container(boolean opus){
        byte[] identification=new byte[30];identification[0]=1;System.arraycopy("vorbis".getBytes(StandardCharsets.US_ASCII),0,identification,1,6);identification[11]=1;ByteBuffer.wrap(identification).order(ByteOrder.LITTLE_ENDIAN).putInt(12,22050);identification[28]=0x76;identification[29]=1;if(opus)System.arraycopy("OpusHead".getBytes(StandardCharsets.US_ASCII),0,identification,0,8);
        byte[][] packets={identification,new byte[]{3,'v','o','r','b','i','s'},new byte[]{5,'v','o','r','b','i','s'},new byte[]{0}};byte[] bytes=new byte[27+4+45];ByteBuffer buffer=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);buffer.put(new byte[]{'O','g','g','S',0,6}).putLong(22050).putInt(7).putInt(0).putInt(0).put((byte)4);for(byte[] packet:packets)buffer.put((byte)packet.length);for(byte[] packet:packets)buffer.put(packet);
        int crc=0;for(byte value:bytes){crc^=Byte.toUnsignedInt(value)<<24;for(int bit=0;bit<8;bit++)crc=(crc<<1)^((crc&0x80000000)!=0?0x04c11db7:0);}buffer.putInt(22,crc);return bytes;
    }
}
