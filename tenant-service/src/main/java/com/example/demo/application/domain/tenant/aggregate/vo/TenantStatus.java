package com.example.demo.application.domain.tenant.aggregate.vo;

public enum TenantStatus {
    PENDING,   // 尚未完成初始化
    ACTIVE,    // 正常營運中
    SUSPENDED, // 停權 (欠費或違規)
    EXPIRED    // 合約到期
}