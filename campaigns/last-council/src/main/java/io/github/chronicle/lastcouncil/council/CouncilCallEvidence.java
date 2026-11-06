package io.github.chronicle.lastcouncil.council;

import io.github.chronicle.core.progression.SourceLedger;
import java.util.*;

/** Only call after the entire producer journal was loaded and verified unchanged. */
public final class CouncilCallEvidence {
    private CouncilCallEvidence() { }
    public static boolean accepted(SourceLedger.Receipt receipt,Map<UUID,ProceedingsState.Call> calls,String expectedSourceId) {
        String prefix="council-call:";
        if(!receipt.authority().startsWith(prefix))throw new IllegalArgumentException("Not Council call evidence.");
        String suffix=receipt.authority().substring(prefix.length());UUID id=UUID.fromString(suffix);
        if(!id.toString().equals(suffix)||!receipt.type().equals("last_council:council_called")||!receipt.target().equals("council")
                ||!receipt.activity().isEmpty()||receipt.actor()==null||!receipt.activeOrigin()||!receipt.id().equals(expectedSourceId))
            throw new IllegalStateException("Council call evidence differs from its producer.");
        ProceedingsState.Call call=calls.get(id);
        if(call!=null&&!call.actor().equals(receipt.actor()))throw new IllegalStateException("Council call actor changed.");
        return call!=null;
    }
}
