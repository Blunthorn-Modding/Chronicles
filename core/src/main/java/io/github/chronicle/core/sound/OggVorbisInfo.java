package io.github.chronicle.core.sound;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Bounded container/header validation. Actual audio decoding is a separate client check. */
public record OggVorbisInfo(int channels,int sampleRate,long samples) {
    public double seconds(){return samples/(double)sampleRate;}
    public static OggVorbisInfo inspect(byte[] bytes){
        if(bytes==null||bytes.length<58||bytes.length>8*1024*1024)throw new IllegalArgumentException("Bundled audio must be a bounded Ogg Vorbis file.");
        int offset=0,serial=0,sequence=0,packets=0,channels=0,rate=0;long samples=-1;boolean ended=false,continued=false;
        var packet=new ByteArrayOutputStream();
        while(offset<bytes.length){
            if(ended||bytes.length-offset<27||bytes[offset]!='O'||bytes[offset+1]!='g'||bytes[offset+2]!='g'||bytes[offset+3]!='S'||bytes[offset+4]!=0)throw invalid();
            var view=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);int flags=Byte.toUnsignedInt(bytes[offset+5]);
            int count=Byte.toUnsignedInt(bytes[offset+26]);if((flags&~7)!=0||bytes.length-offset<27+count)throw invalid();
            int payload=0;for(int i=0;i<count;i++)payload+=Byte.toUnsignedInt(bytes[offset+27+i]);int length=27+count+payload;
            if(length>bytes.length-offset||((flags&1)!=0)!=continued||view.getInt(offset+18)!=sequence++)throw invalid();
            int currentSerial=view.getInt(offset+14);if(offset==0){if((flags&2)==0)throw invalid();serial=currentSerial;}else if((flags&2)!=0||currentSerial!=serial)throw invalid();
            int expected=view.getInt(offset+22),crc=0;for(int i=0;i<length;i++){int value=i>=22&&i<26?0:Byte.toUnsignedInt(bytes[offset+i]);crc^=value<<24;for(int bit=0;bit<8;bit++)crc=(crc<<1)^((crc&0x80000000)!=0?0x04c11db7:0);}if(crc!=expected)throw new IllegalArgumentException("Bundled Ogg audio has an invalid page checksum.");
            int cursor=offset+27+count;
            for(int i=0;i<count;i++){
                int size=Byte.toUnsignedInt(bytes[offset+27+i]);if(packets<3)packet.write(bytes,cursor,size);cursor+=size;continued=size==255;
                if(!continued){
                    if(packets<3){byte[] header=packet.toByteArray();int kind=packets==0?1:packets==1?3:5;if(header.length<7||header[0]!=kind||!Arrays.equals(Arrays.copyOfRange(header,1,7),new byte[]{'v','o','r','b','i','s'}))throw new IllegalArgumentException("Bundled audio must use Vorbis, not Opus or another Ogg codec.");
                        if(packets==0){if(header.length!=30)throw invalid();var identification=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);channels=Byte.toUnsignedInt(header[11]);rate=identification.getInt(12);int small=header[28]&15,big=(header[28]>>>4)&15;if(identification.getInt(7)!=0||channels<1||channels>2||rate<8000||rate>192000||small<6||big>13||big<small||header[29]!=1)throw invalid();}packet.reset();}
                    packets++;
                }
            }
            long granule=view.getLong(offset+6);if(granule>=0)samples=granule;
            ended=(flags&4)!=0;if(ended&&(continued||granule<1))throw invalid();offset+=length;
        }
        if(!ended||continued||packets<4||rate==0||samples<1||samples/(double)rate>600)throw invalid();
        return new OggVorbisInfo(channels,rate,samples);
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Bundled Ogg Vorbis audio is malformed, truncated or exceeds ten minutes.");}
}
