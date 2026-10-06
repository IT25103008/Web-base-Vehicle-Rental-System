package com.vehiclerental.dto.response;

import java.util.List;
import java.util.Map;

/**
 * One page of a long list (C1). `counts` carries per-status totals for the
 * tabs above the list, so the console never has to download everything just
 * to count it.
 */
public class PageResponse<T> {

    private final List<T> items;
    private final int page;
    private final int size;
    private final long total;
    private final Map<String, Long> counts;

    public PageResponse(List<T> items, int page, int size, long total, Map<String, Long> counts) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.total = total;
        this.counts = counts;
    }

    public List<T> getItems() { return items; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotal() { return total; }
    public int getPages() { return size <= 0 ? 1 : (int) Math.max(1, (total + size - 1) / size); }
    public Map<String, Long> getCounts() { return counts; }

    /** Clamps a requested page size to something sensible. */
    public static int size(Integer requested) {
        if (requested == null || requested <= 0) return 20;
        return Math.min(requested, 200);
    }

    public static int page(Integer requested) {
        return requested == null || requested < 1 ? 1 : requested;
    }
}
