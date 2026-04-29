package com.medical.ai.service;

import java.util.List;

public interface TtsService {
    String synthesize(String text);

    int cleanupGeneratedAudio(List<String> fileNames);
}
