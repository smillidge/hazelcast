/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.map.tx;

import com.hazelcast.concurrent.lock.LockBackupOperation;
import com.hazelcast.concurrent.lock.LockNamespace;
import com.hazelcast.map.LockAwareOperation;
import com.hazelcast.map.MapService;
import com.hazelcast.map.Record;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.spi.BackupAwareOperation;
import com.hazelcast.spi.Operation;
import com.hazelcast.spi.ResponseHandler;
import com.hazelcast.transaction.TransactionException;

import java.io.IOException;

public class TxnLockAndGetOperation extends LockAwareOperation {

    private long timeout;
    private VersionedValue response;

    public TxnLockAndGetOperation() {
    }

    public TxnLockAndGetOperation(String name, Data dataKey, long timeout) {
        super(name, dataKey, -1);
        this.timeout = timeout;
    }

    @Override
    public void run() throws Exception {
        if (!recordStore.lock(getKey(), getCallerUuid(), getThreadId(), ttl)) {
            throw new TransactionException("Lock failed.");
        }
        Record record = recordStore.getRecords().get(dataKey);
        Data value = record == null ? null : mapService.toData(record.getValue());
        response = new VersionedValue(value, record == null ? 0 : record.getVersion());
    }

    @Override
    public long getWaitTimeoutMillis() {
        return timeout;
    }

    @Override
    public void onWaitExpire() {
        final ResponseHandler responseHandler = getResponseHandler();
        responseHandler.sendResponse(null);
    }

    @Override
    public Object getResponse() {
        return response;
    }

    @Override
    protected void writeInternal(ObjectDataOutput out) throws IOException {
        super.writeInternal(out);
        out.writeLong(timeout);
    }

    @Override
    protected void readInternal(ObjectDataInput in) throws IOException {
        super.readInternal(in);
        timeout = in.readLong();
    }


    @Override
    public String toString() {
        return "TxnLockAndGetOperation{" +
                "timeout=" + timeout +
                ", thread=" + getThreadId() +
                '}';
    }

}
