package com.wjx871.population.dashboard;

import lombok.Data;

@Data
public class RegionCountView {
    private String regionCode;
    private String regionName;
    private long value;
}
