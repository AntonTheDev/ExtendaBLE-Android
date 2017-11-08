package com.iagd.extendable;

import android.content.Context;

import com.iagd.extendable.maker.EBCentralManagerMaker;
import com.iagd.extendable.maker.EBPeripheralManagerMaker;
import com.iagd.extendable.maker.lambdas.CentralManagerMakerOperation;
import com.iagd.extendable.maker.lambdas.PeripheralManagerMakerOperation;
import com.iagd.extendable.manager.EBCentralManager;
import com.iagd.extendable.manager.EBPeripheralManager;

public class ExtendaBLE {

    public static EBCentralManager newCentralManager(Context context, CentralManagerMakerOperation maker) {
        EBCentralManagerMaker manager =  new EBCentralManagerMaker();
        maker.centralMaker(manager);
        EBCentralManager newManager = manager.constructedManager();
        newManager.setApplicationContext(context);
        return newManager;
    }

    public static EBPeripheralManager newPeripheralManager(Context context, PeripheralManagerMakerOperation maker) {
        EBPeripheralManagerMaker manager =  new EBPeripheralManagerMaker();
        maker.peripheralMaker(manager);
        EBPeripheralManager newManager = manager.constructedManager();
        newManager.setApplicationContext(context);
        return newManager;
    }
}
