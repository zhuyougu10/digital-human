package com.medical.knowledge.service.impl;

import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import com.medical.knowledge.service.DocumentParseService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DocumentParseServiceImpl implements DocumentParseService {

    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_OVERLAP = 50;

    private final Tika tika = new Tika();

    @Override
    public String parseDocument(String filePath, String fileType) {
        try {
            String type = fileType == null ? "" : fileType.trim().toLowerCase();
            if ("txt".equals(type)) {
                return Files.readString(Path.of(filePath));
            }
            return tika.parseToString(Path.of(filePath));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.DOCUMENT_PARSE_ERROR, e.getMessage());
        }
    }

    @Override
    public List<String> splitText(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        int actualChunkSize = chunkSize > 0 ? chunkSize : DEFAULT_CHUNK_SIZE;
        int actualOverlap = overlap >= 0 ? Math.min(overlap, actualChunkSize - 1) : DEFAULT_OVERLAP;

        String[] rawParagraphs = text.split("\\r?\\n+");
        List<String> paragraphs = new ArrayList<>();
        for (String paragraph : rawParagraphs) {
            String trimmed = paragraph == null ? "" : paragraph.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }
        if (paragraphs.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (current.length() == 0) {
                appendWithLimit(current, paragraph, actualChunkSize, chunks);
                continue;
            }

            if (current.length() + 1 + paragraph.length() <= actualChunkSize) {
                current.append('\n').append(paragraph);
            } else {
                chunks.add(current.toString());
                current = new StringBuilder(withOverlap(current.toString(), actualOverlap));
                if (!current.isEmpty()) {
                    current.append('\n');
                }
                appendWithLimit(current, paragraph, actualChunkSize, chunks);
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    private void appendWithLimit(StringBuilder current, String paragraph, int chunkSize, List<String> chunks) {
        if (paragraph.length() <= chunkSize) {
            current.append(paragraph);
            return;
        }
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(start + chunkSize, paragraph.length());
            String part = paragraph.substring(start, end);
            if (!current.isEmpty()) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            current.append(part);
            if (end < paragraph.length()) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            start = end;
        }
    }

    private String withOverlap(String source, int overlap) {
        if (source == null || source.isEmpty() || overlap <= 0) {
            return "";
        }
        int start = Math.max(0, source.length() - overlap);
        return source.substring(start);
    }
}
