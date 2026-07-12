package com.wjx871.population.dashboard;

import lombok.Data;

@Data
public class NamedCountView {
    private String code;
    private String label;
    private long value;

    public NamedCountView() {}
    public NamedCountView(String code, String label, long value) {
        this.code = code;
        this.label = label;
        this.value = value;
    }
}
