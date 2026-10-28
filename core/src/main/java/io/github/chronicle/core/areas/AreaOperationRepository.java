package io.github.chronicle.core.areas;
import java.io.IOException;public interface AreaOperationRepository{AreaOperationJournal load()throws IOException;void save(AreaOperationJournal journal)throws IOException;default void verifyUnchanged()throws IOException{throw new IOException("this Area authority cannot verify observed evidence");}}
