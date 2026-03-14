package com.rag.openai.service;

import com.rag.openai.client.ollama.OllamaClient;
import com.rag.openai.client.qdrant.VectorStoreClient;
import com.rag.openai.client.redis.RedisClient;
import com.rag.openai.config.DocumentsConfig;
import com.rag.openai.config.ProcessingConfig;
import com.rag.openai.domain.model.DocumentMetadata;
import com.rag.openai.domain.model.EmbeddingRecord;
import com.rag.openai.domain.model.ProcessingResult;
import com.rag.openai.domain.model.TextChunk;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Implementation of DocumentProcessor that handles PDF and image file processing.
 * Uses functional programming patterns with CompletableFuture and Stream API.
 */
@Service
public class DocumentProcessorImpl implements DocumentProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentProcessorImpl.class);
    
    private final RedisClient redisClient;
    private final OllamaClient ollamaClient;
    private final VectorStoreClient vectorStoreClient;
    private final ChunkingService chunkingService;
    private final DocumentsConfig documentsConfig;
    private final ProcessingConfig processingConfig;
    
    public DocumentProcessorImpl(
        RedisClient redisClient,
        OllamaClient ollamaClient,
        VectorStoreClient vectorStoreClient,
        ChunkingService chunkingService,
        DocumentsConfig documentsConfig,
        ProcessingConfig processingConfig
    ) {
        this.redisClient = redisClient;
        this.ollamaClient = ollamaClient;
        this.vectorStoreClient = vectorStoreClient;
        this.chunkingService = chunkingService;
        this.documentsConfig = documentsConfig;
        this.processingConfig = processingConfig;
        
        logger.info("DocumentProcessor initialized with input folder: {}", 
            documentsConfig.inputFolder());
    }
    
    @Override
    public CompletableFuture<ProcessingResult> processDocuments(Path inputFolder) {
        logger.info("Starting document processing for folder: {}", inputFolder);
        long startTime = System.currentTimeMillis();
        
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger skippedCount = new AtomicInteger(0);
        AtomicInteger chunksCount = new AtomicInteger(0);
        AtomicInteger embeddingsCount = new AtomicInteger(0);
        List<String> errors = new ArrayList<>();
        
        try {
            // Scan folder for supported files
            List<Path> files = scanFolder(inputFolder);
            logger.info("Found {} files to process", files.size());
            
            // Process files in parallel using CompletableFuture.allOf
            List<CompletableFuture<Void>> futures = files.stream()
                .map(file -> processFile(file, processedCount, skippedCount, 
                    chunksCount, embeddingsCount, errors))
                .toList();
            
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    long processingTime = System.currentTimeMillis() - startTime;
                    ProcessingResult result = new ProcessingResult(
                        processedCount.get(),
                        skippedCount.get(),
                        chunksCount.get(),
                        embeddingsCount.get(),
                        processingTime,
                        List.copyOf(errors)
                    );
                    logger.info("Document processing completed: {}", result);
                    return result;
                });
            
        } catch (IOException e) {
            logger.error("Error scanning folder: {}", inputFolder, e);
            errors.add("Failed to scan folder: " + e.getMessage());
            long processingTime = System.currentTimeMillis() - startTime;
            return CompletableFuture.completedFuture(new ProcessingResult(
                0, 0, 0, 0, processingTime, List.copyOf(errors)
            ));
        }
    }
    
    /**
     * Scan folder for files with supported extensions.
     */
    private List<Path> scanFolder(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            logger.warn("Input folder does not exist: {}", folder);
            return List.of();
        }
        
        try (Stream<Path> paths = Files.walk(folder)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(this::isSupportedFile)
                .toList();
        }
    }
    
    /**
     * Check if file has a supported extension.
     */
    private boolean isSupportedFile(Path file) {
        String filename = file.getFileName().toString().toLowerCase();
        return documentsConfig.supportedExtensions().stream()
            .anyMatch(ext -> filename.endsWith("." + ext));
    }
    
    /**
     * Process a single file.
     */
    private CompletableFuture<Void> processFile(
        Path file,
        AtomicInteger processedCount,
        AtomicInteger skippedCount,
        AtomicInteger chunksCount,
        AtomicInteger embeddingsCount,
        List<String> errors
    ) {
        logger.debug("Processing file: {}", file);
        
        return computeFileHash(file)
            .thenCompose(currentHash -> 
                shouldProcessFile(file, currentHash)
                    .thenCompose(shouldProcess -> {
                        if (!shouldProcess) {
                            logger.debug("Skipping unchanged file: {}", file);
                            skippedCount.incrementAndGet();
                            return CompletableFuture.completedFuture(null);
                        }
                        
                        return processFileContent(file, currentHash, 
                            processedCount, chunksCount, embeddingsCount, errors);
                    })
            )
            .exceptionally(error -> {
                logger.error("Error processing file: {}", file, error);
                synchronized (errors) {
                    errors.add("Failed to process " + file.getFileName() + ": " + error.getMessage());
                }
                return null;
            });
    }
    
    /**
     * Process file content: extract text, delete old embeddings, chunk, and store.
     */
    private CompletableFuture<Void> processFileContent(
        Path file,
        String currentHash,
        AtomicInteger processedCount,
        AtomicInteger chunksCount,
        AtomicInteger embeddingsCount,
        List<String> errors
    ) {
        String filename = file.getFileName().toString();
        
        // Extract text based on file type
        CompletableFuture<Optional<String>> textFuture = isImageFile(file) 
            ? extractTextFromImage(file)
            : extractTextFromPdf(file);
        
        return textFuture
            .thenCompose(textOpt -> {
                if (textOpt.isEmpty()) {
                    logger.warn("No text extracted from file: {}", file);
                    return CompletableFuture.completedFuture(null);
                }
                
                String text = textOpt.get();
                logger.debug("Extracted {} characters from {}", text.length(), filename);
                
                // Delete old embeddings for this file
                return vectorStoreClient.deleteEmbeddingsByFilename(filename)
                    .thenCompose(v -> {
                        // Create metadata
                        DocumentMetadata metadata = createMetadata(file);
                        
                        // Chunk the text
                        List<TextChunk> chunks = chunkingService.chunkText(
                            text,
                            metadata,
                            processingConfig.chunkSize(),
                            processingConfig.chunkOverlap()
                        );
                        
                        logger.debug("Created {} chunks from {}", chunks.size(), filename);
                        chunksCount.addAndGet(chunks.size());
                        
                        // Generate embeddings and store
                        return storeChunks(chunks, embeddingsCount);
                    })
                    .thenCompose(v -> 
                        // Update hash in Redis
                        redisClient.storeFileHash(file, currentHash)
                    )
                    .thenRun(() -> {
                        processedCount.incrementAndGet();
                        logger.info("Successfully processed file: {}", filename);
                    });
            });
    }
    
    /**
     * Check if file is an image based on extension.
     */
    private boolean isImageFile(Path file) {
        String filename = file.getFileName().toString().toLowerCase();
        return filename.endsWith(".jpg") || filename.endsWith(".jpeg") 
            || filename.endsWith(".png") || filename.endsWith(".tiff");
    }
    
    /**
     * Create metadata for a document.
     */
    private DocumentMetadata createMetadata(Path file) {
        try {
            String filename = file.getFileName().toString();
            long lastModified = Files.getLastModifiedTime(file).toMillis();
            String fileType = getFileExtension(filename);
            
            return new DocumentMetadata(filename, file, lastModified, fileType);
        } catch (IOException e) {
            logger.warn("Error reading file metadata for {}: {}", file, e.getMessage());
            String filename = file.getFileName().toString();
            return new DocumentMetadata(filename, file, 0L, getFileExtension(filename));
        }
    }
    
    /**
     * Get file extension from filename.
     */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1).toLowerCase() : "";
    }
    
    /**
     * Store chunks by generating embeddings and storing in vector database.
     */
    private CompletableFuture<Void> storeChunks(
        List<TextChunk> chunks,
        AtomicInteger embeddingsCount
    ) {
        // Generate embeddings for all chunks
        List<CompletableFuture<EmbeddingRecord>> embeddingFutures = chunks.stream()
            .map(this::generateEmbeddingForChunk)
            .toList();
        
        return CompletableFuture.allOf(embeddingFutures.toArray(new CompletableFuture[0]))
            .thenCompose(v -> {
                List<EmbeddingRecord> records = embeddingFutures.stream()
                    .map(CompletableFuture::join)
                    .toList();
                
                embeddingsCount.addAndGet(records.size());
                
                // Store embeddings in batches
                return vectorStoreClient.storeEmbeddings(records);
            });
    }
    
    /**
     * Generate embedding for a single chunk.
     */
    private CompletableFuture<EmbeddingRecord> generateEmbeddingForChunk(TextChunk chunk) {
        return ollamaClient.generateEmbedding(chunk.text())
            .thenApply(embedding -> new EmbeddingRecord(
                UUID.randomUUID().toString(),
                embedding,
                chunk
            ));
    }
    
    @Override
    public CompletableFuture<Optional<String>> extractTextFromPdf(Path pdfFile) {
        logger.debug("Extracting text from PDF: {}", pdfFile);
        
        return CompletableFuture.supplyAsync(() -> {
            try (PDDocument document = Loader.loadPDF(pdfFile.toFile())) {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);
                
                if (text == null || text.isBlank()) {
                    logger.warn("No text extracted from PDF: {}", pdfFile);
                    return Optional.empty();
                }
                
                logger.debug("Extracted {} characters from PDF: {}", text.length(), pdfFile);
                return Optional.of(text);
                
            } catch (IOException e) {
                logger.error("Error extracting text from PDF: {}", pdfFile, e);
                return Optional.empty();
            }
        });
    }
    
    @Override
    public CompletableFuture<Optional<String>> extractTextFromImage(Path imageFile) {
        logger.debug("Extracting text from image: {}", imageFile);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] imageData = Files.readAllBytes(imageFile);
                String prompt = "Extract all visible text from this image. " +
                    "Return only the text content without any additional commentary.";
                
                return ollamaClient.analyzeImage(imageData, prompt)
                    .thenApply(text -> {
                        if (text == null || text.isBlank()) {
                            logger.warn("No text extracted from image: {}", imageFile);
                            return Optional.<String>empty();
                        }
                        
                        logger.debug("Extracted {} characters from image: {}", 
                            text.length(), imageFile);
                        return Optional.of(text);
                    })
                    .exceptionally(error -> {
                        logger.error("Error extracting text from image: {}", imageFile, error);
                        return Optional.empty();
                    })
                    .join();
                
            } catch (IOException e) {
                logger.error("Error reading image file: {}", imageFile, e);
                return Optional.empty();
            }
        });
    }
    
    @Override
    public CompletableFuture<String> computeFileHash(Path file) {
        logger.debug("Computing hash for file: {}", file);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] fileBytes = Files.readAllBytes(file);
                byte[] hashBytes = digest.digest(fileBytes);
                
                // Convert to hex string
                StringBuilder hexString = new StringBuilder();
                for (byte b : hashBytes) {
                    String hex = Integer.toHexString(0xff & b);
                    if (hex.length() == 1) {
                        hexString.append('0');
                    }
                    hexString.append(hex);
                }
                
                String hash = hexString.toString();
                logger.debug("Computed hash for {}: {}", file.getFileName(), hash);
                return hash;
                
            } catch (NoSuchAlgorithmException | IOException e) {
                logger.error("Error computing hash for file: {}", file, e);
                throw new RuntimeException("Failed to compute file hash", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> shouldProcessFile(Path file, String currentHash) {
        return redisClient.getFileHash(file)
            .thenApply(storedHashOpt -> {
                if (storedHashOpt.isEmpty()) {
                    logger.debug("No stored hash for {}, will process", file.getFileName());
                    return true;
                }
                
                String storedHash = storedHashOpt.get();
                boolean hashesMatch = storedHash.equals(currentHash);
                
                if (hashesMatch) {
                    logger.debug("Hash matches for {}, will skip", file.getFileName());
                } else {
                    logger.debug("Hash differs for {}, will process", file.getFileName());
                }
                
                return !hashesMatch;
            })
            .exceptionally(error -> {
                logger.warn("Error checking stored hash for {}, will process: {}", 
                    file.getFileName(), error.getMessage());
                return true;
            });
    }
}
