package com.hierarchyhub.dto;

import javax.validation.Valid;
import javax.validation.constraints.Min;

/** {@code POST /read/{orgId}} body (entirely optional - a missing body behaves like an empty {@link Body}). */
public class ReadChildrenRequest {

    @Valid
    private Body request;

    public ReadChildrenRequest() {
    }

    public Body getRequest() {
        return request;
    }

    public void setRequest(Body request) {
        this.request = request;
    }

    public static class Body {

        private static final int DEFAULT_LIMIT = 50;

        /** Max children to return; defaults to 50. */
        private Integer limit;

        /** Opaque cursor from a previous page's {@code nextCursor}; omit for the first page. Ignored when {@code offset} is given. */
        private String cursor;

        /**
         * Classic {@code LIMIT}/{@code OFFSET} paging, as an alternative to {@code cursor} (e.g. {@code limit=10,
         * offset=0} then {@code limit=10, offset=10} for the next page - any {@code limit}/{@code offset} combination
         * the caller wants). When given (even {@code 0}), this wins over {@code cursor} for that call. Simpler for a
         * caller that just wants page numbers, but {@code OFFSET} gets slower the further it skips into a large
         * result set (this table can hold ~100k+ rows) and can skip/repeat a row if the hierarchy changes between
         * calls - {@code cursor} does not have either problem, so prefer it once a listing gets large or long-lived.
         */
        @Min(value = 0, message = "offset must be at least 0")
        private Integer offset;

        /** Whether to compute each child's descendant count; defaults to true. */
        private Boolean countDescendants;

        /**
         * How many levels below {@code orgId} to include, capped at {@code org-hierarchy.max-depth} either way.
         * Explicit, it always wins; left {@code null}, the effective depth depends on {@code query}: a search reaches
         * the whole subtree (it has to, to find a match anywhere under {@code orgId}), but a plain, filter-less read
         * stays this API's original behaviour - direct children only (depth 1) - so a caller who passes just
         * {@code orgId} never gets the whole hierarchy back without asking for it. Every result is still a flat,
         * paginated list either way - a deeper result does not come back nested, unlike {@code GET /{orgId}/complete}.
         */
        @Min(value = 1, message = "depth must be at least 1")
        private Integer depth;

        /**
         * Optional free-text search, scoped to {@code orgId}'s own subtree (never outside it, and never limited to
         * direct children - see {@link #depth}): matches a descendant whose id exactly equals this value, or whose
         * name contains it (case-insensitive) - both are tried, so the caller does not need to say which one this is.
         */
        private String query;

        public Integer getLimit() {
            return limit;
        }

        public void setLimit(Integer limit) {
            this.limit = limit;
        }

        public String getCursor() {
            return cursor;
        }

        public void setCursor(String cursor) {
            this.cursor = cursor;
        }

        public Integer getOffset() {
            return offset;
        }

        public void setOffset(Integer offset) {
            this.offset = offset;
        }

        public Boolean getCountDescendants() {
            return countDescendants;
        }

        public void setCountDescendants(Boolean countDescendants) {
            this.countDescendants = countDescendants;
        }

        public Integer getDepth() {
            return depth;
        }

        public void setDepth(Integer depth) {
            this.depth = depth;
        }

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public int limitOrDefault() {
            return limit == null ? DEFAULT_LIMIT : limit;
        }

        public boolean countDescendantsOrDefault() {
            return countDescendants == null || countDescendants;
        }
    }
}
