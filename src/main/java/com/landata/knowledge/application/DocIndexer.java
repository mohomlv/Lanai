package com.landata.knowledge.application;

import com.landata.knowledge.infrastructure.persistence.DocEmbeddingRepository;
import com.landata.knowledge.infrastructure.persistence.SchemaDoc;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocIndexer {
  private final DocEmbeddingRepository embeddings;
  private final EntityManager entityManager;

  public DocIndexer(DocEmbeddingRepository embeddings, EntityManager entityManager) {
    this.embeddings = embeddings;
    this.entityManager = entityManager;
  }

  @Transactional
  public void index(SchemaDoc doc, String tableName) {
    if (doc == null || doc.getId() == null || !doc.getCurrent() || doc.getMarkdown() == null) return;
    embeddings.deleteByDocId(doc.getId());
    entityManager.flush();
    List<Chunk> chunks = chunks(doc.getMarkdown(), doc.getKind());
    int index = 0;
    for (Chunk chunk : chunks) {
      float[] vector = TextEmbedding.embed(chunk.text());
      entityManager.createNativeQuery(
              "insert into doc_embedding (id, company_id, datasource_id, doc_id, chunk_index, chunk_kind, chunk_text, token_count, table_name, embedding, created_at) "
                  + "values (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, cast(?10 as vector(1024)), sysutcdatetime())")
          .setParameter(1, UUID.randomUUID())
          .setParameter(2, doc.getCompanyId())
          .setParameter(3, doc.getDatasourceId())
          .setParameter(4, doc.getId())
          .setParameter(5, index++)
          .setParameter(6, chunk.kind())
          .setParameter(7, chunk.text().length() > 4000 ? chunk.text().substring(0, 4000) : chunk.text())
          .setParameter(8, chunk.text().split("\\s+").length)
          .setParameter(9, tableName)
          .setParameter(10, TextEmbedding.literal(vector))
          .executeUpdate();
    }
  }

  public void unindex(UUID docId) {
    if (docId == null) return;
    embeddings.deleteByDocId(docId);
  }

  static List<Chunk> chunks(String markdown, String docKind) {
    List<Chunk> out = new ArrayList<>();
    if (markdown == null || markdown.isBlank()) return out;
    if ("database_overview".equals(docKind) || "query_guide".equals(docKind)) {
      String kind = "query_guide".equals(docKind) ? "query_guide" : "overview";
      String[] parts = markdown.split("(?m)^## ");
      if (parts.length <= 1) {
        out.add(new Chunk(kind, markdown.length() > 4000 ? markdown.substring(0, 4000) : markdown));
        return out;
      }
      if (!parts[0].isBlank()) out.add(new Chunk(kind, parts[0].strip()));
      for (int i = 1; i < parts.length; i++) {
        String body = "## " + parts[i].strip();
        out.add(new Chunk(kind, body.length() > 4000 ? body.substring(0, 4000) : body));
      }
      return out;
    }
    String[] parts = markdown.split("(?m)^## ");
    if (parts.length == 1) {
      out.add(new Chunk("header", markdown.length() > 4000 ? markdown.substring(0, 4000) : markdown));
      return out;
    }
    if (!parts[0].isBlank()) out.add(new Chunk("header", parts[0].strip()));
    for (int i = 1; i < parts.length; i++) {
      String body = "## " + parts[i].strip();
      out.add(new Chunk(kindOf(body), body.length() > 4000 ? body.substring(0, 4000) : body));
    }
    return out;
  }

  private static String kindOf(String section) {
    String head = section.toLowerCase(Locale.ROOT);
    if (head.contains("field") || head.contains("column")) return "columns";
    if (head.contains("related") || head.contains("relation")) return "relationships";
    if (head.contains("sample") || head.contains("example")) return "examples";
    if (head.contains("overview") || head.contains("table")) return "overview";
    return "header";
  }

  record Chunk(String kind, String text) {}
}
