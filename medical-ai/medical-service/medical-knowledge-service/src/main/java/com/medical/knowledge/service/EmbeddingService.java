package com.medical.knowledge.service;

import java.util.List;

public interface EmbeddingService {

    float[] embed(String text);

    List<float[]> embedBatch(List<String> texts);
}
