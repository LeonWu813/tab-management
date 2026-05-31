package com.tabvault.backend.contentextraction;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;

/**
 * Extracts text from a PDF URL using Apache PDFBox 3.
 *
 * Downloads the PDF from the given URL and uses PDFTextStripper to extract
 * all text content from every page.
 *
 * AC (PDF path): extracted text is returned to the content analysis pipeline
 * as pageText input for MOD-003 LLM summarization (same 3,000-token limit applies).
 */
@Component
public class PdfExtractor {

    private static final Logger logger = LoggerFactory.getLogger(PdfExtractor.class);

    /**
     * Connection timeout in milliseconds for downloading the PDF.
     * Configurable via {@code app.content-extraction.fetch-timeout-ms}.
     */
    private final int fetchTimeoutMs;

    public PdfExtractor(
            @Value("${app.content-extraction.fetch-timeout-ms:8000}") int fetchTimeoutMs) {
        this.fetchTimeoutMs = fetchTimeoutMs;
    }

    /**
     * Downloads the PDF at the given URL and extracts all text content.
     *
     * @param url the URL of the PDF file to download and extract
     * @return extracted text content, or an empty string if extraction fails
     */
    public String extract(String url) {
        try {
            logger.debug("Downloading PDF url={}", url);
            URL pdfUrl = URI.create(url).toURL();
            URLConnection connection = pdfUrl.openConnection();
            connection.setConnectTimeout(fetchTimeoutMs);
            connection.setReadTimeout(fetchTimeoutMs);
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (compatible; TabVault/1.0; +https://tabvault.app)");

            try (InputStream inputStream = connection.getInputStream();
                 PDDocument pdfDocument = Loader.loadPDF(inputStream.readAllBytes())) {

                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(pdfDocument);
                logger.info("PDF text extracted url={} chars={}", url, text.length());
                return text.trim();
            }
        } catch (Exception exception) {
            logger.error("PDF extraction failed url={} error={}", url, exception.getMessage());
            return "";
        }
    }
}
