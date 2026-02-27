package com.medical.knowledge.service.impl;

import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import com.medical.knowledge.domain.VectorData;
import com.medical.knowledge.service.VectorStoreService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VectorStoreServiceImpl implements VectorStoreService {

    private static final String FIELD_ID = "id";
    private static final String FIELD_VECTOR = "vector";
    private static final String FIELD_CHUNK_ID = "chunk_id";
    private static final String FIELD_DOC_ID = "doc_id";
    private static final String FIELD_CONTENT = "content";
    private static final int VECTOR_DIM = 1024;
    private static final Gson GSON = new Gson();

    private final MilvusClientV2 milvusClient;

    @Override
    public void createCollection(String collectionName) {
        try {
            CreateCollectionReq.CollectionSchema schema = milvusClient.createSchema();
            schema.addField(AddFieldReq.builder()
                    .fieldName(FIELD_ID)
                    .dataType(DataType.VarChar)
                    .isPrimaryKey(true)
                    .maxLength(64)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName(FIELD_VECTOR)
                    .dataType(DataType.FloatVector)
                    .dimension(VECTOR_DIM)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName(FIELD_CHUNK_ID)
                    .dataType(DataType.Int64)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName(FIELD_DOC_ID)
                    .dataType(DataType.Int64)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName(FIELD_CONTENT)
                    .dataType(DataType.VarChar)
                    .maxLength(2000)
                    .build());

            IndexParam indexParam = IndexParam.builder()
                    .fieldName(FIELD_VECTOR)
                    .indexType(IndexParam.IndexType.AUTOINDEX)
                    .metricType(IndexParam.MetricType.COSINE)
                    .build();

            CreateCollectionReq req = CreateCollectionReq.builder()
                    .collectionName(collectionName)
                    .collectionSchema(schema)
                    .indexParams(Collections.singletonList(indexParam))
                    .build();
            milvusClient.createCollection(req);
        } catch (Exception e) {
            if (!isCollectionAlreadyExistsError(e)) {
                throw new BusinessException(ErrorCode.FAIL, "Create collection failed: " + e.getMessage());
            }
        }
    }

    @Override
    public void dropCollection(String collectionName) {
        try {
            milvusClient.dropCollection(DropCollectionReq.builder()
                    .collectionName(collectionName)
                    .build());
        } catch (Exception e) {
            if (!isCollectionNotExistsError(e)) {
                throw new BusinessException(ErrorCode.FAIL, "Drop collection failed: " + e.getMessage());
            }
        }
    }

    @Override
    public void insertVectors(String collectionName, List<VectorData> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            return;
        }
        try {
            List<JsonObject> rows = vectors.stream()
                    .filter(Objects::nonNull)
                    .map(this::toRow)
                    .collect(Collectors.toList());
            if (rows.isEmpty()) {
                return;
            }
            InsertReq req = InsertReq.builder()
                    .collectionName(collectionName)
                    .data(rows)
                    .build();
            milvusClient.insert(req);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.FAIL, "Insert vectors failed: " + e.getMessage());
        }
    }

    @Override
    public void deleteVectors(String collectionName, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        try {
            String filter = ids.stream()
                    .filter(Objects::nonNull)
                    .map(id -> "'" + id.replace("'", "\\'") + "'")
                    .collect(Collectors.joining(",", FIELD_ID + " in [", "]"));
            milvusClient.delete(DeleteReq.builder()
                    .collectionName(collectionName)
                    .filter(filter)
                    .build());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.FAIL, "Delete vectors failed: " + e.getMessage());
        }
    }

    @Override
    public List<VectorData> search(String collectionName, float[] queryVector, int topK) {
        if (queryVector == null || queryVector.length == 0) {
            return Collections.emptyList();
        }
        try {
            SearchReq req = SearchReq.builder()
                    .collectionName(collectionName)
                    .data(Collections.singletonList(new FloatVec(queryVector)))
                    .topK(Math.max(topK, 1))
                    .outputFields(List.of(FIELD_ID, FIELD_CHUNK_ID, FIELD_DOC_ID, FIELD_CONTENT))
                    .build();

            SearchResp resp = milvusClient.search(req);
            List<List<SearchResp.SearchResult>> allResults = resp.getSearchResults();
            if (allResults == null || allResults.isEmpty()) {
                return Collections.emptyList();
            }
            List<VectorData> result = new ArrayList<>();
            for (SearchResp.SearchResult item : allResults.get(0)) {
                result.add(toVectorData(item));
            }
            return result;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.FAIL, "Search vectors failed: " + e.getMessage());
        }
    }

    private JsonObject toRow(VectorData data) {
        JsonObject row = new JsonObject();
        row.addProperty(FIELD_ID, data.getId());
        row.add(FIELD_VECTOR, GSON.toJsonTree(data.getVector()));
        if (data.getChunkId() != null) {
            row.addProperty(FIELD_CHUNK_ID, data.getChunkId());
        }
        if (data.getDocId() != null) {
            row.addProperty(FIELD_DOC_ID, data.getDocId());
        }
        row.addProperty(FIELD_CONTENT, safeContent(data.getContent()));
        return row;
    }

    private VectorData toVectorData(SearchResp.SearchResult item) {
        VectorData data = new VectorData();
        data.setScore(item.getScore() != null ? item.getScore().doubleValue() : null);
        Map<String, Object> entity = item.getEntity();
        if (entity == null) {
            return data;
        }
        data.setId(asString(entity.get(FIELD_ID)));
        data.setChunkId(asLong(entity.get(FIELD_CHUNK_ID)));
        data.setDocId(asLong(entity.get(FIELD_DOC_ID)));
        data.setContent(asString(entity.get(FIELD_CONTENT)));
        return data;
    }

    private String safeContent(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= 2000 ? content : content.substring(0, 2000);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isCollectionAlreadyExistsError(Exception e) {
        String msg = e.getMessage();
        return msg != null && msg.toLowerCase(Locale.ROOT).contains("already");
    }

    private boolean isCollectionNotExistsError(Exception e) {
        String msg = e.getMessage();
        return msg != null
                && (msg.toLowerCase(Locale.ROOT).contains("not exist")
                || msg.toLowerCase(Locale.ROOT).contains("can't find"));
    }
}
