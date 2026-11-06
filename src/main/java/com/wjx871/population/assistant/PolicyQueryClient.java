package com.wjx871.population.assistant;

public interface PolicyQueryClient {
    PolicyPythonContracts.Response query(PolicyPythonContracts.Request request);
}
