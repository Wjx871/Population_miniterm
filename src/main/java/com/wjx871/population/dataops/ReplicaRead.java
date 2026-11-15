package com.wjx871.population.dataops;

import java.lang.annotation.*;

/** Explicit allow-list for queries whose callers accept replica lag. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ReplicaRead {}
