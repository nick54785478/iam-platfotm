package com.example.demo.application.domain.tenant.aggregate.vo;


public enum PlanType {
    FREE(1),
    PRO(2),
    ENTERPRISE(3);

    private final int level;

    PlanType(int level) {
        this.level = level;
    }
    public int getLevel() {
        return level;
    }
}