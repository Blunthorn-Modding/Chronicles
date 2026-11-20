package io.github.chronicle.lastcouncil.client;
import io.github.chronicle.lastcouncil.network.ElectionPayloads.Action;
import java.util.*;
final class ElectionScreen {
    static void panel(CouncilScreen screen,int x,int y,int w,int h){
        var v=ElectionClient.view();if(v==null){screen.body("Reading election...",x,y,w,h);return;}
        boolean chancellor=screen instanceof CouncilBusinessScreen&&v.chancellor();
        List<CouncilScreen.SectionCard> cards=new ArrayList<>();
        cards.add(new CouncilScreen.SectionCard("Election",v.stage()));
        cards.add(new CouncilScreen.SectionCard("Candidates",v.candidates().isEmpty()?(v.stage().equals("Not started")?"The slate locks when the first Regency ballot opens.":v.stage().equals("Unavailable")?"Election information is unavailable.":"No ruler was elected."):String.join("\n",v.candidates())));
        if(chancellor&&!v.warning().isBlank())cards.add(new CouncilScreen.SectionCard("Planning",v.warning()));
        cards.add(new CouncilScreen.SectionCard("Public history",v.history().isEmpty()?"No history yet.":String.join("\n\n",v.history())));
        String refusal=chancellor?v.endRefusal():v.chancellor()?"":v.withdrawRefusal();if((chancellor||!v.ownCandidate().isBlank())&&!refusal.isBlank())cards.add(new CouncilScreen.SectionCard("Availability",refusal));
        boolean paging=v.pages()>1;int actionY=y+h-20;
        screen.sections("election-page-"+v.page(),cards,x,y,w,Math.max(20,h-24-(paging?22:0)));
        if(paging){int half=(w-4)/2;if(v.page()>0)screen.button("Previous",x,actionY-22,half,()->ElectionClient.send(Action.STATE,"",v.page()-1)).active=!ElectionClient.busy();if(v.page()+1<v.pages())screen.button("Next",x+half+4,actionY-22,half,()->ElectionClient.send(Action.STATE,"",v.page()+1)).active=!ElectionClient.busy();}
        if(chancellor||(!v.chancellor()&&!v.ownCandidate().isBlank()))screen.button(chancellor?"End unresolved election...":"Withdraw candidacy...",x,actionY,w,()->ElectionClient.reason(chancellor?Action.END:Action.WITHDRAW)).active=(chancellor?v.canEnd():v.canWithdraw())&&!ElectionClient.busy();
    }
}
