package io.github.chronicle.core.areas;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Strict bounded atomic area-definition catalog. */
public final class AreaCatalogStore implements AreaCatalogRepository {
    private static final int MAGIC=0x43484143, SCHEMA=1; public static final long MAX_BYTES=4L*1024*1024;
    private final AtomicAreaFile file; private boolean loadedValid;
    public AreaCatalogStore(Path file){this.file=new AtomicAreaFile(file,MAX_BYTES);}
    @Override public synchronized AreaCatalog load()throws IOException{byte[] b=file.load();if(b==null){loadedValid=true;return AreaCatalog.empty();}try{AreaCatalog result=decode(b);loadedValid=true;return result;}catch(RuntimeException e){loadedValid=false;throw new IOException("area catalog is invalid and was preserved",e);}}
    @Override public synchronized void save(AreaCatalog catalog)throws IOException{if(!loadedValid)throw new IOException("load a valid area catalog before saving");file.save(encode(catalog));}
    static byte[] encode(AreaCatalog catalog)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(bytes)){out.writeInt(MAGIC);out.writeInt(SCHEMA);out.writeLong(catalog.revision());out.writeInt(catalog.areas().size());for(AreaChange a:catalog.areas().values()){AreaBinary.text(out,a.id());AreaBinary.text(out,a.name());AreaBinary.bounds(out,a.bounds());out.writeLong(a.revision());AreaBinary.optionalRef(out,a.start());AreaBinary.optionalRef(out,a.end());}out.writeInt(catalog.tombstones().size());for(String id:catalog.tombstones())AreaBinary.text(out,id);}return bytes.toByteArray();}
    static AreaCatalog decode(byte[] bytes){try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes))){if(in.readInt()!=MAGIC||in.readInt()!=SCHEMA)throw new IllegalArgumentException("area catalog magic/schema is invalid");long revision=in.readLong();int n=AreaBinary.count(in,AreaCatalog.MAX_AREAS,"area");Map<String,AreaChange> areas=new LinkedHashMap<>();for(int i=0;i<n;i++){String id=AreaBinary.text(in,129,"area ID");AreaChange a=new AreaChange(id,AreaBinary.text(in,AreaChange.MAX_NAME_BYTES,"area name"),AreaBinary.bounds(in),in.readLong(),AreaBinary.optionalRef(in),AreaBinary.optionalRef(in));if(areas.putIfAbsent(id,a)!=null)throw new IllegalArgumentException("duplicate area ID");}int t=AreaBinary.count(in,AreaCatalog.MAX_TOMBSTONES,"tombstone");Set<String> tombstones=new LinkedHashSet<>();for(int i=0;i<t;i++)if(!tombstones.add(AreaBinary.text(in,129,"tombstone")))throw new IllegalArgumentException("duplicate tombstone");if(in.read()!=-1)throw new IllegalArgumentException("area catalog has trailing bytes");return new AreaCatalog(revision,areas,tombstones);}catch(EOFException e){throw new IllegalArgumentException("area catalog is truncated",e);}catch(IOException e){throw new IllegalArgumentException("area catalog cannot be decoded",e);}}
}
