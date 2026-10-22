package io.github.chronicle.core.server;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class ServerDisconnectsTest {
    @Test void nettyNotificationCannotRunThreadConfinedCleanupOrAbortLaterSubscribers(){
        var tasks=new ArrayList<Runnable>();var completed=new ArrayList<Integer>();
        ServerDisconnects.dispatch(()->false,tasks::add,()->completed.add(1));
        ServerDisconnects.dispatch(()->false,tasks::add,()->completed.add(2));
        assertTrue(completed.isEmpty());assertEquals(2,tasks.size());tasks.forEach(Runnable::run);
        assertEquals(java.util.List.of(1,2),completed);
    }
    @Test void serverThreadNotificationRunsOnceWithoutEnqueue(){
        var completed=new ArrayList<Integer>();
        ServerDisconnects.dispatch(()->true,task->fail("Unexpected enqueue"),()->completed.add(1));
        assertEquals(java.util.List.of(1),completed);
    }
}
