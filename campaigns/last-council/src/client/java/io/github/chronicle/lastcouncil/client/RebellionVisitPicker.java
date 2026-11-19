package io.github.chronicle.lastcouncil.client;
import io.github.chronicle.lastcouncil.network.RebellionPayloads;
import io.github.chronicle.lastcouncil.network.RebellionPayloads.Action;
import java.util.List;
final class RebellionVisitPicker extends CouncilScreen {
    private final CouncilScreen owner;
    RebellionVisitPicker(CouncilScreen owner){super("Choose rebel House",owner);this.owner=owner;}
    @Override protected void init(){frame();var v=RebellionClient.view();if(v==null)return;rows("visits",v.visits().stream().map(h->new Choice(h.house(),h.title()+" / "+h.status())).toList(),"",left+12,top+36,panelWidth-24,panelHeight-74,id->{var h=v.visits().stream().filter(a->a.house().equals(id)).findFirst().orElseThrow();RebellionClient.child(new RebellionConfirm(owner,"Allow one palace visit?",List.of(new SectionCard(h.title(),"One supervised visit ends on exit. It does not restore a Council seat or vote.")),()->RebellionClient.send(Action.ALLOW_VISIT,RebellionPayloads.NONE,"",id)));});}
    @Override public void close(){RebellionClient.returnToOwner();}
}
