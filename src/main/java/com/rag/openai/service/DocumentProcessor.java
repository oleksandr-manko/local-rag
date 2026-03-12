package com.rag.openai.service;

import com.rag.openai.domain.model.ProcessingResult;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service for processing documents from a folder.
 * Handles PDF and image files, extracts text, computes hashes for change detection,
 * and coordinates the document processing pipeline.
 */
public interface DocumentProcessor {
    
    /**
     * Process all documents in the specified folder.
     * Scans for supported file types, checks for changes using hashes,
     * extracts text, and coordinates chunking and embedding storage.
     * 
     * @param inputFolder The folder containing documents to process
     * @return CompletableFuture containing processing results with metrics
     */
    CompletableFuture<ProcessingResult> processDocuments(Path inputFolder);
    
    /**
     * Extract text content from a PDF file using Apache PDFBox.
     * 
     * @param pdfFile The path to the PDF file
     * @return CompletableFuture containing Optional with extracted text, or empty if extraction fails
     */
    CompletableFuture<Optional<String>> extractTextFromPdf(Path pdfFile);
    
    /**
     * Extract text content from an image file using Ollama vision models.
     * 
     * @param imageFile The path to the image file
     * @return CompletableFuture containing Optional with extracted text, or empty if extraction fails
     */
    CompletableFuture<Optional<String>> extractTextFromImage(Path imageFile);
    
    /**
     * Compute SHA-256 hash of a file for change detection.
     * 
     * @param file The file to hash
     * @return CompletableFuture containing the hex-encoded hash string
     */
    CompletableFuture<String> computeFileHash(Path file);
    
    /**
     * Determine if a file should be processed based on hash comparison.
     * Checks the current hash against the stored hash in Redis.
     * 
     * @param file The file to check
     * @param currentHash The current hash of the file
     * @return CompletableFuture containing true if file should be processed, false if it should be skipped
     */
    CompletableFuture<Boolean> shouldProcessFile(Path file, String currentHash);
}
