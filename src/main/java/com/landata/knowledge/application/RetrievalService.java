package com.landata.knowledge.application;

import com.landata.knowledge.infrastructure.persistence.DocEmbeddingRepository;
import com.landata.knowledge.infrastructure.persistence.SchemaDoc;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RetrievalService {
  private final EntityManager entityManager;
  private final DocEmbeddingRepository embeddings;

  public RetrievalService(EntityManager entityManager, DocEmbeddingRepository embeddings) {
    this.entityManager = entityManager;
    this.embeddings = embeddings;
  }

  public List<SchemaDoc> forQuestion(UUID companyId, UUID datasourceId, String question, List<SchemaDoc> current) {
    List<SchemaDoc> picked = new ArrayList<>();
    current.stream().filter(doc -> "database_overview".equals(doc.getKind())).findFirst().ifPresent(picked::add);
    List<SchemaDoc> guides = current.stream().filter(doc -> "query_guide".equals(doc.getKind())).toList();
    List<UUID> nearest = nearest(companyId, datasourceId, question);
    addGuides(picked, guides, nearest, question);
    for (UUID id : nearest) {
      current.stream().filter(doc -> id.equals(doc.getId()) && picked.stream().noneMatch(have -> have.getId().equals(id)))
          .findFirst()
          .ifPresent(picked::add);
    }
    if (picked.size() <= 1 + Math.min(4, guides.size())) {
      for (SchemaDoc doc : keywordHits(question, current)) {
        if (picked.stream().noneMatch(have -> have.getId().equals(doc.getId()))) picked.add(doc);
        if (picked.size() >= 8) break;
      }
    }
    if (picked.size() <= 1) {
      current.stream()
          .filter(doc -> doc.getMarkdown() != null && picked.stream().noneMatch(have -> have.getId().equals(doc.getId())))
          .limit(4)
          .forEach(picked::add);
    }
    return picked;
  }

  private static void addGuides(List<SchemaDoc> picked, List<SchemaDoc> guides, List<UUID> nearest, String question) {
    if (guides.isEmpty()) return;
    for (UUID id : nearest) {
      guides.stream()
          .filter(doc -> id.equals(doc.getId()) && picked.stream().noneMatch(have -> have.getId().equals(id)))
          .findFirst()
          .ifPresent(picked::add);
    }
    for (SchemaDoc guide : keywordHits(question, guides)) {
      if (picked.stream().noneMatch(have -> have.getId().equals(guide.getId()))) picked.add(guide);
      if (guideCount(picked) >= 4) return;
    }
    if (guideCount(picked) == 0 || guides.size() <= 4) {
      for (SchemaDoc guide : guides) {
        if (picked.stream().noneMatch(have -> have.getId().equals(guide.getId()))) picked.add(guide);
        if (guideCount(picked) >= 4) return;
      }
    }
  }

  private static long guideCount(List<SchemaDoc> picked) {
    return picked.stream().filter(doc -> "query_guide".equals(doc.getKind())).count();
  }

  public boolean hasIndex(UUID companyId, UUID datasourceId) {
    return embeddings.countByDatasourceIdAndCompanyId(datasourceId, companyId) > 0;
  }

  @SuppressWarnings("unchecked")
  private List<UUID> nearest(UUID companyId, UUID datasourceId, String question) {
    if (question == null || question.isBlank()) return List.of();
    if (embeddings.countByDatasourceIdAndCompanyId(datasourceId, companyId) == 0) return List.of();
    try {
      String literal = TextEmbedding.literal(TextEmbedding.embed(question));
      List<Object> rows = entityManager.createNativeQuery(
              "select top 6 d.doc_id from doc_embedding d "
                  + "join schema_doc s on s.id = d.doc_id and s.is_current = 1 "
                  + "where d.company_id = ?1 and d.datasource_id = ?2 "
                  + "group by d.doc_id "
                  + "order by min(vector_distance('cosine', d.embedding, cast(?3 as vector(1024))))")
          .setParameter(1, companyId)
          .setParameter(2, datasourceId)
          .setParameter(3, literal)
          .getResultList();
      List<UUID> ids = new ArrayList<>();
      for (Object row : rows) {
        if (row instanceof UUID id) ids.add(id);
        else if (row != null) ids.add(UUID.fromString(String.valueOf(row)));
      }
      return ids;
    } catch (RuntimeException ex) {
      return List.of();
    }
  }

  private static List<SchemaDoc> keywordHits(String question, List<SchemaDoc> current) {
    Set<String> words = new LinkedHashSet<>();
    if (question != null) {
      for (String raw : question.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
        if (raw.length() < 2) continue;
        if (raw.matches("[a-z0-9]+") && raw.length() < 3) continue;
        words.add(raw);
      }
    }
    List<SchemaDoc> hits = new ArrayList<>();
    for (SchemaDoc doc : current) {
      if (doc.getMarkdown() == null || "database_overview".equals(doc.getKind())) continue;
      String title = doc.getTitle() == null ? "" : doc.getTitle();
      String text = (title + "\n" + doc.getMarkdown()).toLowerCase(Locale.ROOT);
      if (words.stream().anyMatch(text::contains)) hits.add(doc);
    }
    return hits;
  }
}
