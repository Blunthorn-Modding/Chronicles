package io.github.chronicle.lastcouncil.content;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.UUID;

/** One bounded, contiguous upload; any protocol error permanently invalidates it. */
public final class EditorTransfer {
    private final UUID id; private final int count,chunkLimit,totalLimit; private int next; private boolean invalid,consumed;
    private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
    public EditorTransfer(UUID id,int count,int chunkLimit,int totalLimit){if(id==null||count<1||chunkLimit<1||totalLimit<1||count>(totalLimit+chunkLimit-1)/chunkLimit)throw new IllegalArgumentException("Invalid transfer count.");this.id=id;this.count=count;this.chunkLimit=chunkLimit;this.totalLimit=totalLimit;}
    public void append(UUID id,int index,int count,byte[] data){if(invalid||consumed)throw new IllegalStateException("Transfer is closed.");if(!this.id.equals(id)||index!=next||count!=this.count||data==null||data.length==0||data.length>chunkLimit||bytes.size()+data.length>totalLimit){invalid=true;throw new IllegalArgumentException("Invalid transfer order or size.");}bytes.writeBytes(data);next++;}
    public byte[] finish(UUID id){if(invalid||consumed||!this.id.equals(id)||next!=count){invalid=true;throw new IllegalStateException("Incomplete or stale upload.");}consumed=true;byte[] result=bytes.toByteArray();try{StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(result));}catch(CharacterCodingException e){throw new IllegalArgumentException("Invalid UTF-8.",e);}return result;}
}
