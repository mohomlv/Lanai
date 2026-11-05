package com.landata.knowledge.application;

import java.util.Locale;

/**
 * Local 1024-d embedding. Same function is used when a doc is saved and when
 * a question is asked, so scrape-without-AI still indexes. A later ONNX model
 * can replace this if the column stays 1024.
 */
public final class TextEmbedding {
  public static final int DIM = 1024;

  private TextEmbedding() {}

  public static float[] embed(String text) {
    float[] vector = new float[DIM];
    if (text == null || text.isBlank()) return vector;
    String[] tokens = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._]+", " ").trim().split("\\s+");
    for (int i = 0; i < tokens.length; i++) {
      String token = tokens[i];
      if (token.isBlank()) continue;
      add(vector, token, 1f);
      if (token.contains(".")) {
        for (String part : token.split("\\.")) {
          if (!part.isBlank()) add(vector, part, 0.8f);
        }
      }
      if (i + 1 < tokens.length && !tokens[i + 1].isBlank()) {
        add(vector, token + "_" + tokens[i + 1], 0.55f);
      }
    }
    normalize(vector);
    return vector;
  }

  public static double cosine(float[] left, float[] right) {
    double sum = 0;
    for (int i = 0; i < DIM; i++) sum += left[i] * right[i];
    return sum;
  }

  public static String literal(float[] vector) {
    StringBuilder out = new StringBuilder(vector.length * 8);
    out.append('[');
    for (int i = 0; i < vector.length; i++) {
      if (i > 0) out.append(',');
      out.append(vector[i]);
    }
    return out.append(']').toString();
  }

  private static void add(float[] vector, String token, float weight) {
    int hash = token.hashCode();
    vector[Math.floorMod(hash, DIM)] += weight;
    vector[Math.floorMod(Integer.rotateLeft(hash, 13) ^ 0x9e3779b9, DIM)] += weight * 0.5f;
  }

  private static void normalize(float[] vector) {
    double sum = 0;
    for (float value : vector) sum += value * value;
    if (sum == 0) return;
    float scale = (float) (1 / Math.sqrt(sum));
    for (int i = 0; i < vector.length; i++) vector[i] *= scale;
  }
}
