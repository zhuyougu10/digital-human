package com.medical.knowledge.service;

import java.util.List;

public interface DocumentParseService {

    String parseDocument(String filePath, String fileType);

    List<String> splitText(String text, int chunkSize, int overlap);
}
