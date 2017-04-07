package com.iagd.extendable;

import com.iagd.extendable.maker.EBCentralManagerMaker;
import com.iagd.extendable.maker.lambdas.ManagerMakerOperation;
import com.iagd.extendable.manager.EBCentralManager;

public class ExtendaBLE {

    public static EBCentralManager newCentralManager(ManagerMakerOperation maker) {
        EBCentralManagerMaker manager =  new EBCentralManagerMaker();
        maker.centralMaker(manager);
        return manager.constructedManager();
    }
}
