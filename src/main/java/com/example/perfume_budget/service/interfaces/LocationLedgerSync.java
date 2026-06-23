package com.example.perfume_budget.service.interfaces;

import com.example.perfume_budget.enums.StockTransferType;
import com.example.perfume_budget.model.Product;
import com.example.perfume_budget.model.StockTransfer;
import com.example.perfume_budget.model.StorageLocation;
import com.example.perfume_budget.model.User;

/**
 * Single writer of the physical location ledger ({@code LocationStock} + {@code StockTransfer}).
 * The FIFO cost engine remains the source of truth for global stock; this ledger only records
 * where units physically sit. Missing location configuration must never fail the calling
 * inventory operation — the convenience methods log and skip instead.
 */
public interface LocationLedgerSync {

    void applyDelta(Product product, StorageLocation location, int delta, StockTransferType type, String note, User movedBy);

    void increaseAtDefaultReceiving(Product product, int quantity, StockTransferType type, String note);

    void deductForWalkInSale(Product product, int quantity, String note);

    void deductForEcommerceSale(Product product, int quantity, String note);

    void deductAtDefaultReceiving(Product product, int quantity, String note);

    StockTransfer transfer(Product product, StorageLocation from, StorageLocation to, int quantity, String note, User movedBy);
}
