package com.wjx871.population.migration;
public interface MigrationTransactionHook { default void afterArchiveInserted(){} default void afterResidenceDeleted(){} default void afterNewHeadMemberPromoted(){} default void afterResidenceInserted(){} }
