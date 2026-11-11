package io.github.chronicle.core.progression;

import io.github.chronicle.core.books.BookDelivery;

/** Pure evidence rule: recovery cannot publish Book found without the real discovery authority. */
public final class BookSourceRecovery {
    private BookSourceRecovery() { }
    public enum Decision { ALREADY_RECORDED, RECORD_AUTHENTIC_INVENTORY }
    public static Decision inspect(SourceLedger.Receipt receipt,BookDelivery delivery,boolean authenticInventory) {
        if(receipt==null||receipt.stage()!=SourceLedger.Stage.PREPARED||!receipt.type().equals("chronicle_core:book_found")||!receipt.authority().startsWith("book:")||receipt.actor()==null||!receipt.activeOrigin())
            throw new IllegalStateException("Choose an accepted Book found source preparation");
        if(delivery==null||!delivery.issued()||!delivery.bookId().equals(receipt.target())||!receipt.authority().equals("book:"+delivery.deliveryToken()))
            throw new IllegalStateException("The pinned book issuance or delivery token changed");
        if(delivery.firstDiscovery()!=null) {
            if(!delivery.firstDiscovery().playerUuid().equals(receipt.actor().toString()))throw new IllegalStateException("Another player owns the durable first discovery; it cannot be overwritten");
            return Decision.ALREADY_RECORDED;
        }
        if(!authenticInventory)throw new IllegalStateException("The original player must be online with the authentic managed book in their inventory before recording discovery");
        return Decision.RECORD_AUTHENTIC_INVENTORY;
    }
}
