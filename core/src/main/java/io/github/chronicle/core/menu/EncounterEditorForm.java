package io.github.chronicle.core.menu;

import java.util.HashSet;
import java.util.List;

/** Bounded local draft for one portable group plus its separate world-owned target binding. */
public record EncounterEditorForm(long groupRegistryRevision,long bindingRegistryRevision,String id,String label,
                                  String description,List<Member> members,String targetKind,String siteId,String siteLabel,
                                  long siteRevision,boolean current,String problem,boolean creating) {
    public EncounterEditorForm {if(groupRegistryRevision<0||bindingRegistryRevision<0||siteRevision<0)throw new IllegalArgumentException("Invalid encounter editor revision");text(id,128);text(label,80);text(description,512);text(targetKind,16);text(siteId,128);text(siteLabel,160);text(problem,512);members=List.copyOf(members);if(members.size()>16)throw new IllegalArgumentException("Too many encounter member types");HashSet<String>types=new HashSet<>();int total=0;for(Member member:members){if(!types.add(member.entityType()))throw new IllegalArgumentException("Duplicate encounter entity type");total=Math.addExact(total,member.count());}if(total>64)throw new IllegalArgumentException("Encounter group exceeds 64 entities");if(!targetKind.isEmpty()&&!targetKind.equals("POINT")&&!targetKind.equals("REGION"))throw new IllegalArgumentException("Invalid encounter target kind");}
    public record Member(String entityType,String label,int count,boolean current,String problem){public Member{text(entityType,128);text(label,160);text(problem,512);if(count<1||count>32)throw new IllegalArgumentException("Encounter member count must be 1 to 32");}}
    private static void text(String value,int maximum){if(value==null||value.length()>maximum||value.indexOf('\0')>=0)throw new IllegalArgumentException("Encounter editor text exceeds bounds");}
}
