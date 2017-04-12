package com.iagd.extendable;

import com.iagd.extendable.maker.EBCentralManagerMaker;
import com.iagd.extendable.maker.EBPeripheralManagerMaker;
import com.iagd.extendable.maker.lambdas.CentralManagerMakerOperation;
import com.iagd.extendable.maker.lambdas.PeripheralManagerMakerOperation;
import com.iagd.extendable.manager.EBCentralManager;
import com.iagd.extendable.manager.EBPeripheralManager;

public class ExtendaBLE {

    public static EBCentralManager newCentralManager(CentralManagerMakerOperation maker) {
        EBCentralManagerMaker manager =  new EBCentralManagerMaker();
        maker.centralMaker(manager);
        return manager.constructedManager();
    }

    public static EBPeripheralManager newPeripheralManager(PeripheralManagerMakerOperation maker) {
        EBPeripheralManagerMaker manager =  new EBPeripheralManagerMaker();
        maker.peripheralMaker(manager);
        return manager.constructedManager();
    }
}
