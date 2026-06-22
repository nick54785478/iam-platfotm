package com.example.demo.application.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**

 * PagedResult

 *

 * <p>

 * 分頁聚合結果封裝，用於回傳 content 與分頁資訊。

 * </p>

 *

 * @param <T> 文件類型

 */

@Data
@Builder
@AllArgsConstructor
public class PagedQueriedView<T> {

    /**
     * 聚合查詢結果
     */
    private List<T> content;

    /**
     * 總筆數
     */
    private long totalElements;

    /**
     * 當前頁碼
     */
    private int page;

    /**
     * 每頁筆數
     */
    private int size;

    /**
     * 總頁數
     */
    public int getTotalPages() {

        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);

    }

}

