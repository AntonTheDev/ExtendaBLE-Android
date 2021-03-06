package com.iagd.extendable.result;

import com.iagd.extendable.transaction.EBData;
import java.util.concurrent.Callable;

public abstract class ExtendaBLECallback<T> implements Callable<Void> {

    public EBData result;

    public void setResult (byte[] result) {
        this.result = new EBData(result);
    }

    public abstract Void call();
}
