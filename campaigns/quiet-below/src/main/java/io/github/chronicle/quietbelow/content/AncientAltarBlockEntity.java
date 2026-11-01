package io.github.chronicle.quietbelow.content;

import io.github.chronicle.quietbelow.QuietBelow;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/** Only the placed station identity; no inventory, ticking work or per-sculk entity. */
public final class AncientAltarBlockEntity extends BlockEntity {
    private static final String STATION_ID="chronicle_station_id";
    private UUID stationId=UUID.randomUUID();
    public AncientAltarBlockEntity(BlockPos pos, BlockState state) { super(QuietBelow.ALTAR_BLOCK_ENTITY, pos, state); }
    public UUID stationId(){return stationId;}
    @Override protected void writeData(WriteView view){super.writeData(view);view.putString(STATION_ID,stationId.toString());}
    @Override protected void readData(ReadView view){super.readData(view);String value=view.getString(STATION_ID,"");try{stationId=UUID.fromString(value);}catch(IllegalArgumentException ignored){stationId=UUID.randomUUID();}}
}
