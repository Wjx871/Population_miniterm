package com.wjx871.population.s2.aggregation;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface PopulationMetricsRemote extends Remote {
    PopulationMetricsResult aggregate(PopulationMetricsRequest request)
            throws RemoteException, InvalidPopulationMetricsException;
}
