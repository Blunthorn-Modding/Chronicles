package io.github.chronicle.lastcouncil.content;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EditorTransferTest {
    private static final UUID ID=UUID.randomUUID();
    @Test void acceptsOrderedUtf8AndRejectsReplay(){var t=new EditorTransfer(ID,2,4,8);t.append(ID,0,2,new byte[]{'a',(byte)0xc3});t.append(ID,1,2,new byte[]{(byte)0xa9});assertEquals("aé",new String(t.finish(ID),StandardCharsets.UTF_8));assertThrows(IllegalStateException.class,()->t.finish(ID));}
    @Test void invalidOrderAndInterleavingPermanentlyInvalidate(){var t=new EditorTransfer(ID,2,4,8);t.append(ID,0,2,new byte[]{1});assertThrows(IllegalArgumentException.class,()->t.append(ID,0,2,new byte[]{1}));assertThrows(IllegalStateException.class,()->t.append(ID,1,2,new byte[]{1}));var x=new EditorTransfer(ID,2,4,8);assertThrows(IllegalArgumentException.class,()->x.append(UUID.randomUUID(),0,2,new byte[]{1}));}
    @Test void enforcesCountsSizeAndUtf8(){assertThrows(IllegalArgumentException.class,()->new EditorTransfer(ID,3,4,8));var t=new EditorTransfer(ID,2,4,5);t.append(ID,0,2,new byte[]{1,2,3,4});assertThrows(IllegalArgumentException.class,()->t.append(ID,1,2,new byte[]{1,2}));var u=new EditorTransfer(ID,1,4,4);u.append(ID,0,1,new byte[]{(byte)0xff});assertThrows(IllegalArgumentException.class,()->u.finish(ID));var incomplete=new EditorTransfer(ID,2,4,8);incomplete.append(ID,0,2,new byte[]{1});assertThrows(IllegalStateException.class,()->incomplete.finish(ID));}
}
