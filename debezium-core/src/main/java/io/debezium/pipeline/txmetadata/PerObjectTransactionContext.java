/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.pipeline.txmetadata;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import io.debezium.annotation.NotThreadSafe;
import io.debezium.schema.DataCollectionId;

/**
 * The context holds internal state necessary for book-keeping of events in active transaction.
 * The main data tracked are
 * <ul>
 * <li>active transaction id</li>
 * <li>the total event number seen from the transaction</li>
 * <li>the number of events per table/collection seen in the transaction</li>
 * </ul>
 *
 * The state of this context is stored in offsets and is recovered upon restart.
 *
 * @author Jiri Pechanec
 */
@NotThreadSafe
public class PerObjectTransactionContext {

    private static final String OFFSET_TRANSACTION_ID = TransactionMonitor.DEBEZIUM_TRANSACTION_KEY + "_" + TransactionMonitor.DEBEZIUM_TRANSACTION_ID_KEY;
    private static final String OFFSET_TABLE_COUNT_PREFIX = TransactionMonitor.DEBEZIUM_TRANSACTION_KEY + "_"
            + TransactionMonitor.DEBEZIUM_TRANSACTION_DATA_COLLECTION_ORDER_KEY + "_";
    private static final int OFFSET_TABLE_COUNT_PREFIX_LENGTH = OFFSET_TABLE_COUNT_PREFIX.length();
    public static final String IDENTITY_ID_SEPARATOR = "---";

    protected String transactionId = null;
    protected final Map<String, Long> perTableEventCount = new HashMap<>();
    private final Map<String, Long> viewPerTableEventCount = Collections.unmodifiableMap(perTableEventCount);
    protected long totalEventCount = 0;
    protected Map<String, Object> localOffset = new HashMap<>();

    protected void reset() {
        transactionId = null;
        totalEventCount = 0;
        perTableEventCount.clear();
        localOffset.clear();
    }

    public Map<String, Object> store(Map<String, Object> offset) {
        offset.putAll(localOffset);
        return offset;
    }

    public Map<String, Object> getLocalOffset() {
        return localOffset;
    }

    @SuppressWarnings("unchecked")
    public static PerObjectTransactionContext load(Map<String, ?> offsets) {
        final Map<String, Object> o = (Map<String, Object>) offsets;
        final PerObjectTransactionContext context = new PerObjectTransactionContext();

        context.transactionId = (String) o.get(OFFSET_TRANSACTION_ID);

        for (final Entry<String, Object> offset : o.entrySet()) {
            if (offset.getKey().startsWith(OFFSET_TABLE_COUNT_PREFIX)) {
                final String dataCollectionId = offset.getKey().substring(OFFSET_TABLE_COUNT_PREFIX_LENGTH);
                final Long count = (Long) offset.getValue();
                context.perTableEventCount.put(dataCollectionId, count);
            }
        }

        context.totalEventCount = context.perTableEventCount.values().stream().mapToLong(x -> x).sum();

        return context;
    }

    public boolean isTransactionInProgress() {
        return transactionId != null;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public long getTotalEventCount() {
        return totalEventCount;
    }

    public void beginTransaction(String txId) {
        reset();
        transactionId = txId;
        localOffset.put(OFFSET_TRANSACTION_ID, transactionId);
    }

    public void endTransaction() {
        reset();
    }

    public long event(DataCollectionId source, String objectId) {
        totalEventCount++;
        final String sourceName = source.toString();
        final long dataCollectionEventOrder = perTableEventCount.getOrDefault(sourceName, 0L).longValue() + 1;
        perTableEventCount.put(sourceName, dataCollectionEventOrder);
        localOffset.put(OFFSET_TABLE_COUNT_PREFIX + objectId + IDENTITY_ID_SEPARATOR + sourceName, dataCollectionEventOrder);
        return dataCollectionEventOrder;
    }

    public Map<String, Long> getPerTableEventCount() {
        return viewPerTableEventCount;
    }

    @Override
    public String toString() {
        return "TransactionContext [currentTransactionId=" + transactionId + ", perTableEventCount="
                + perTableEventCount + ", totalEventCount=" + totalEventCount + "]";
    }
}
