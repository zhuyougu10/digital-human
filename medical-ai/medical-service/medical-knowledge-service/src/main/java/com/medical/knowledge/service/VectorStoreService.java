package com.medical.knowledge.service;

import com.medical.knowledge.domain.VectorData;
import java.util.List;

public interface VectorStoreService {

    void createCollection(String collectionName);

    void dropCollection(String collectionName);

    void insertVectors(String collectionName, List<VectorData> vectors);

    void deleteVectors(String collectionName, List<String> ids);

    List<VectorData> search(String collectionName, float[] queryVector, int topK);
}
