package com.hierarchyhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * The background thread pool that runs {@code POST /bulk/link} jobs: the upload request itself only reads and parses
 * the file, then hands the parsed rows off to this pool so the HTTP caller gets a response (job id, status
 * {@code INITIATED}) without waiting for every row to be attached. Sized small and bounded (org-hierarchy.bulk.*): a
 * full queue runs the job on the caller's own thread instead of rejecting it, so a burst of uploads degrades
 * gracefully rather than losing one.
 */
@Configuration
public class BulkLinkExecutorConfig {

    public static final String BULK_LINK_EXECUTOR = "bulkLinkExecutor";

    @Bean(BULK_LINK_EXECUTOR)
    public Executor bulkLinkExecutor(OrgHierarchyProperties props) {
        OrgHierarchyProperties.Bulk bulk = props.getBulk();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(bulk.getCorePoolSize());
        executor.setMaxPoolSize(bulk.getMaxPoolSize());
        executor.setQueueCapacity(bulk.getQueueCapacity());
        executor.setThreadNamePrefix("bulk-link-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
