package com.rag.openai.service;

import com.rag.openai.client.ollama.OllamaClient;
import com.rag.openai.client.qdrant.VectorStoreClient;
import com.rag.openai.client.redis.RedisClient;
import com.rag.openai.config.DocumentsConfig;
import com.rag.openai.config.OllamaConfig;
import com.rag.openai.config.ProcessingConfig;
import com.rag.openai.domain.model.ProcessingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DocumentProcessorTest {
    
    private DocumentProcessor documentProcessor;
    private RedisClient redisClient;
    private OllamaClient ollamaClient;
    private VectorStoreClient vectorStoreClient;
    private ChunkingService chunkingService;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        // Given: Mock dependencies
        redisClient = mock(RedisClient.class);
        ollamaClient = mock(OllamaClient.class);
        vectorStoreClient = mock(VectorStoreClient.class);
        chunkingService = mock(ChunkingService.class);
        
        DocumentsConfig documentsConfig = new DocumentsConfig(
            tempDir,
            List.of("pdf", "jpg", "jpeg", "png", "tiff")
        );
        
        ProcessingConfig processingConfig = new ProcessingConfig(
            "0 */15 * * * *",
            512,
            50,
            100,
            5,
            Duration.ofSeconds(60)
        );
        
        OllamaConfig ollamaConfig = new OllamaConfig(
            "localhost",
            11434,
            "llama3.2",
            "nomic-embed-text",
            "qwen2-vl:8b",
            Duration.ofSeconds(30),
            Duration.ofSeconds(120)
        );
        
        documentProcessor = new DocumentProcessorImpl(
            redisClient,
            ollamaClient,
            vectorStoreClient,
            chunkingService,
            documentsConfig,
            processingConfig,
            ollamaConfig
        );
    }
    
    @Test
    @DisplayName("When computing file hash Then returns SHA-256 hex string")
    void testComputeFileHash() throws Exception {
        // Given: A test file with known content
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Hello World");
        
        // When: Computing the hash
        String hash = documentProcessor.computeFileHash(testFile).join();
        
        // Then: Hash should be a valid SHA-256 hex string (64 characters)
        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }
    
    @Test
    @DisplayName("When file hash matches stored hash Then should not process file")
    void testShouldProcessFile_HashMatches() {
        // Given: A file with matching hash in Redis
        Path testFile = tempDir.resolve("test.pdf");
        String currentHash = "abc123";
        when(redisClient.getFileHash(testFile))
            .thenReturn(CompletableFuture.completedFuture(Optional.of("abc123")));
        
        // When: Checking if file should be processed
        boolean shouldProcess = documentProcessor.shouldProcessFile(testFile, currentHash).join();
        
        // Then: File should not be processed
        assertFalse(shouldProcess);
    }
    
    @Test
    @DisplayName("When file hash differs from stored hash Then should process file")
    void testShouldProcessFile_HashDiffers() {
        // Given: A file with different hash in Redis
        Path testFile = tempDir.resolve("test.pdf");
        String currentHash = "abc123";
        when(redisClient.getFileHash(testFile))
            .thenReturn(CompletableFuture.completedFuture(Optional.of("xyz789")));
        
        // When: Checking if file should be processed
        boolean shouldProcess = documentProcessor.shouldProcessFile(testFile, currentHash).join();
        
        // Then: File should be processed
        assertTrue(shouldProcess);
    }
    
    @Test
    @DisplayName("When no stored hash exists Then should process file")
    void testShouldProcessFile_NoStoredHash() {
        // Given: A file with no stored hash in Redis
        Path testFile = tempDir.resolve("test.pdf");
        String currentHash = "abc123";
        when(redisClient.getFileHash(testFile))
            .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        
        // When: Checking if file should be processed
        boolean shouldProcess = documentProcessor.shouldProcessFile(testFile, currentHash).join();
        
        // Then: File should be processed
        assertTrue(shouldProcess);
    }
    
    @Test
    @DisplayName("When processing empty folder Then returns zero counts")
    void testProcessDocuments_EmptyFolder() {
        // Given: An empty folder
        
        // When: Processing documents
        ProcessingResult result = documentProcessor.processDocuments(tempDir).join();
        
        // Then: All counts should be zero
        assertEquals(0, result.documentsProcessed());
        assertEquals(0, result.documentsSkipped());
        assertEquals(0, result.chunksCreated());
        assertEquals(0, result.embeddingsStored());
        assertTrue(result.errors().isEmpty());
    }
    
    @Test
    @DisplayName("When Redis check fails Then should process file by default")
    void testShouldProcessFile_RedisError() {
        // Given: Redis client throws an error
        Path testFile = tempDir.resolve("test.pdf");
        String currentHash = "abc123";
        when(redisClient.getFileHash(testFile))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Redis error")));
        
        // When: Checking if file should be processed
        boolean shouldProcess = documentProcessor.shouldProcessFile(testFile, currentHash).join();
        
        // Then: File should be processed (fail-safe behavior)
        assertTrue(shouldProcess);
    }
    
    @Test
    @DisplayName("When extracting text from valid PDF Then returns extracted text")
    void testExtractTextFromPdf_ValidFile() throws Exception {
        // Given: A valid PDF file with text content
        Path pdfFile = createSamplePdfFile();
        
        // When: Extracting text from PDF
        Optional<String> result = documentProcessor.extractTextFromPdf(pdfFile).join();
        
        // Then: Text should be extracted successfully
        assertTrue(result.isPresent());
        assertFalse(result.get().isBlank());
        assertTrue(result.get().contains("Sample"));
    }
    
    @Test
    @DisplayName("When extracting text from corrupted PDF Then returns empty")
    void testExtractTextFromPdf_CorruptedFile() throws Exception {
        // Given: A corrupted PDF file (invalid content)
        Path corruptedPdf = tempDir.resolve("corrupted.pdf");
        Files.writeString(corruptedPdf, "This is not a valid PDF file");
        
        // When: Extracting text from corrupted PDF
        Optional<String> result = documentProcessor.extractTextFromPdf(corruptedPdf).join();
        
        // Then: Should return empty due to parsing error
        assertTrue(result.isEmpty());
    }
    
    @Test
    @DisplayName("When extracting text from empty PDF Then returns empty")
    void testExtractTextFromPdf_EmptyFile() throws Exception {
        // Given: An empty PDF file (no text content)
        Path emptyPdf = createEmptyPdfFile();
        
        // When: Extracting text from empty PDF
        Optional<String> result = documentProcessor.extractTextFromPdf(emptyPdf).join();
        
        // Then: Should return empty or blank text
        assertTrue(result.isEmpty() || result.get().isBlank());
    }
    
    @Test
    @DisplayName("When extracting text from image Then uses vision model")
    void testExtractTextFromImage_ValidFile() throws Exception {
        // Given: A valid image file and mocked vision model response
        Path imageFile = tempDir.resolve("test.jpg");
        Files.write(imageFile, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}); // JPEG header
        
        String extractedText = "Text from image";
        when(ollamaClient.analyzeImage(any(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(extractedText));
        
        // When: Extracting text from image
        Optional<String> result = documentProcessor.extractTextFromImage(imageFile).join();
        
        // Then: Should return extracted text from vision model
        assertTrue(result.isPresent());
        assertEquals(extractedText, result.get());
        verify(ollamaClient).analyzeImage(any(), anyString(), eq("qwen2-vl:8b"));
    }
    
    @Test
    @DisplayName("When vision model returns empty text Then returns empty")
    void testExtractTextFromImage_NoTextFound() throws Exception {
        // Given: An image file with no text content
        Path imageFile = tempDir.resolve("blank.png");
        Files.write(imageFile, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}); // PNG header
        
        when(ollamaClient.analyzeImage(any(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(""));
        
        // When: Extracting text from image
        Optional<String> result = documentProcessor.extractTextFromImage(imageFile).join();
        
        // Then: Should return empty
        assertTrue(result.isEmpty());
    }
    
    @Test
    @DisplayName("When vision model fails Then returns empty")
    void testExtractTextFromImage_VisionModelError() throws Exception {
        // Given: An image file and vision model that fails
        Path imageFile = tempDir.resolve("test.jpg");
        Files.write(imageFile, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        
        when(ollamaClient.analyzeImage(any(), anyString(), anyString()))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Vision model error")));
        
        // When: Extracting text from image
        Optional<String> result = documentProcessor.extractTextFromImage(imageFile).join();
        
        // Then: Should return empty due to error
        assertTrue(result.isEmpty());
    }
    
    @Test
    @DisplayName("When image file cannot be read Then returns empty")
    void testExtractTextFromImage_FileReadError() {
        // Given: A non-existent image file
        Path nonExistentFile = tempDir.resolve("nonexistent.jpg");
        
        // When: Extracting text from non-existent image
        Optional<String> result = documentProcessor.extractTextFromImage(nonExistentFile).join();
        
        // Then: Should return empty due to file read error
        assertTrue(result.isEmpty());
    }
    
    @Test
    @DisplayName("When discovering files in folder Then finds all supported types")
    void testFileDiscovery_MultipleFileTypes() throws Exception {
        // Given: A folder with multiple file types
        Path pdf = tempDir.resolve("doc1.pdf");
        Path jpg = tempDir.resolve("image1.jpg");
        Path png = tempDir.resolve("image2.png");
        Path jpeg = tempDir.resolve("image3.jpeg");
        Path tiff = tempDir.resolve("image4.tiff");
        Path txt = tempDir.resolve("ignored.txt");
        
        Files.writeString(pdf, "PDF content");
        Files.writeString(jpg, "JPG content");
        Files.writeString(png, "PNG content");
        Files.writeString(jpeg, "JPEG content");
        Files.writeString(tiff, "TIFF content");
        Files.writeString(txt, "TXT content"); // Should be ignored
        
        // Mock Redis to return matching hashes for all files (so they get skipped)
        // The hash will be computed from the actual file content, so we return the same hash
        when(redisClient.getFileHash(any()))
            .thenAnswer(invocation -> {
                Path file = invocation.getArgument(0);
                // Return a hash that will match what computeFileHash returns
                String hash = documentProcessor.computeFileHash(file).join();
                return CompletableFuture.completedFuture(Optional.of(hash));
            });
        
        // When: Processing documents
        ProcessingResult result = documentProcessor.processDocuments(tempDir).join();
        
        // Then: Should discover 5 supported files (PDF, JPG, PNG, JPEG, TIFF) and skip them
        assertEquals(5, result.documentsSkipped());
        assertEquals(0, result.documentsProcessed());
    }
    
    @Test
    @DisplayName("When discovering files in nested folders Then finds all files recursively")
    void testFileDiscovery_NestedFolders() throws Exception {
        // Given: Nested folder structure with files
        Path subDir1 = tempDir.resolve("subdir1");
        Path subDir2 = subDir1.resolve("subdir2");
        Files.createDirectories(subDir2);
        
        Path root = tempDir.resolve("root.pdf");
        Path sub1 = subDir1.resolve("sub1.pdf");
        Path sub2 = subDir2.resolve("sub2.pdf");
        
        Files.writeString(root, "Root PDF");
        Files.writeString(sub1, "Sub1 PDF");
        Files.writeString(sub2, "Sub2 PDF");
        
        // Mock Redis to return matching hashes for all files (so they get skipped)
        when(redisClient.getFileHash(any()))
            .thenAnswer(invocation -> {
                Path file = invocation.getArgument(0);
                String hash = documentProcessor.computeFileHash(file).join();
                return CompletableFuture.completedFuture(Optional.of(hash));
            });
        
        // When: Processing documents
        ProcessingResult result = documentProcessor.processDocuments(tempDir).join();
        
        // Then: Should discover all 3 PDFs recursively
        assertEquals(3, result.documentsSkipped());
    }
    
    @Test
    @DisplayName("When folder does not exist Then returns zero counts")
    void testFileDiscovery_NonExistentFolder() {
        // Given: A non-existent folder path
        Path nonExistentFolder = tempDir.resolve("nonexistent");
        
        // When: Processing documents
        ProcessingResult result = documentProcessor.processDocuments(nonExistentFolder).join();
        
        // Then: Should return zero counts without errors
        assertEquals(0, result.documentsProcessed());
        assertEquals(0, result.documentsSkipped());
        assertTrue(result.errors().isEmpty());
    }
    
    @Test
    @DisplayName("When hash computation succeeds Then returns consistent hash")
    void testComputeFileHash_Consistency() throws Exception {
        // Given: A test file with specific content
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Consistent content");
        
        // When: Computing hash multiple times
        String hash1 = documentProcessor.computeFileHash(testFile).join();
        String hash2 = documentProcessor.computeFileHash(testFile).join();
        
        // Then: Hashes should be identical
        assertEquals(hash1, hash2);
    }
    
    @Test
    @DisplayName("When file content changes Then hash changes")
    void testComputeFileHash_DetectsChanges() throws Exception {
        // Given: A test file with initial content
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Initial content");
        String hash1 = documentProcessor.computeFileHash(testFile).join();
        
        // When: Modifying file content
        Files.writeString(testFile, "Modified content");
        String hash2 = documentProcessor.computeFileHash(testFile).join();
        
        // Then: Hashes should differ
        assertNotEquals(hash1, hash2);
    }
    
    /**
     * Helper method to create a sample PDF file with text content.
     */
    private Path createSamplePdfFile() throws Exception {
        Path pdfFile = tempDir.resolve("sample.pdf");
        
        // Create a minimal valid PDF with text
        String pdfContent = "%PDF-1.4\n" +
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n" +
            "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n" +
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /Resources 4 0 R /MediaBox [0 0 612 792] /Contents 5 0 R >>\nendobj\n" +
            "4 0 obj\n<< /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> >>\nendobj\n" +
            "5 0 obj\n<< /Length 44 >>\nstream\n" +
            "BT\n/F1 12 Tf\n100 700 Td\n(Sample PDF Text) Tj\nET\n" +
            "endstream\nendobj\n" +
            "xref\n0 6\n" +
            "0000000000 65535 f\n" +
            "0000000009 00000 n\n" +
            "0000000058 00000 n\n" +
            "0000000115 00000 n\n" +
            "0000000214 00000 n\n" +
            "0000000293 00000 n\n" +
            "trailer\n<< /Size 6 /Root 1 0 R >>\n" +
            "startxref\n385\n" +
            "%%EOF";
        
        Files.writeString(pdfFile, pdfContent);
        return pdfFile;
    }
    
    /**
     * Helper method to create an empty PDF file (valid structure but no text).
     */
    private Path createEmptyPdfFile() throws Exception {
        Path pdfFile = tempDir.resolve("empty.pdf");
        
        // Create a minimal valid PDF without text content
        String pdfContent = "%PDF-1.4\n" +
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n" +
            "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n" +
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\nendobj\n" +
            "xref\n0 4\n" +
            "0000000000 65535 f\n" +
            "0000000009 00000 n\n" +
            "0000000058 00000 n\n" +
            "0000000115 00000 n\n" +
            "trailer\n<< /Size 4 /Root 1 0 R >>\n" +
            "startxref\n178\n" +
            "%%EOF";
        
        Files.writeString(pdfFile, pdfContent);
        return pdfFile;
    }
}
