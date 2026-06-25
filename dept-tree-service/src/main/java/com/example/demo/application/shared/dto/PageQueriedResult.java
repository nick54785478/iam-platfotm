package com.example.demo.application.shared.dto;

import java.util.List;

/**
 * PageQueriedResult - 通用分頁結果封裝
 */
public record PageQueriedResult<T>(List<T> content, long totalElements, int totalPages, int currentPage) {
}