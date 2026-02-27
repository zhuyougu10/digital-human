package com.medical.knowledge.domain;

import lombok.Data;

@Data
public class VectorData {
    private String id;
    private float[] vector;
    private Long chunkId;
    private Long docId;
    private String content;
    private Double score;
}
