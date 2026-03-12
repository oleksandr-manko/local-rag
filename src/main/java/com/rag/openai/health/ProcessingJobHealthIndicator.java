package com.rag.openai.health;

import com.rag.openai.service.ProcessingJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

/**
 * Health indicator for the document processing job.
 * Checks if the processing job is functioning correctly.
 */
@Component
public class ProcessingJobHealthIndicator extends AbstractHealthIndicator {
    
    private static final Logger logger = LoggerFactory.getLogger(ProcessingJobHealthIndicator.class);
    
    private final ProcessingJob processingJob;
    
    public ProcessingJobHealthIndicator(ProcessingJob processingJob) {
        this.processingJob = processingJob;
    }
    
    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        logger.debug("Checking ProcessingJob health");
        
        try {
            boolean isProcessing = processingJob.isProcessingInProgress();
            
            // The processing job is healthy if it's operational
            // Being in progress or idle are both healthy states
            builder.up()
                .withDetail("service", "ProcessingJob")
                .withDetail("status", isProcessing ? "Processing" : "Idle");
            
            logger.debug("ProcessingJob health check passed: {}", 
                isProcessing ? "processing" : "idle");
        } catch (Exception e) {
            logger.error("ProcessingJob health check encountered an error", e);
            builder.down()
                .withDetail("service", "ProcessingJob")
                .withDetail("error", e.getMessage());
        }
    }
}
