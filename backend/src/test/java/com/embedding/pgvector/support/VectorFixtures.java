package com.embedding.pgvector.support;

import com.embedding.pgvector.chunk.domain.entity.Chunk;

/**
 * Testlerde elle hesaplanabilir birim, karışık ve rastgele normalize vektörler üreten yardımcıdır.
 */
public final class VectorFixtures {

    private VectorFixtures() {
    }

    public static float[] unit(int axis) {
        float[] vector = new float[Chunk.EMBEDDING_DIMENSIONS];
        vector[axis] = 1f;
        return vector;
    }

    public static float[] mix(int axisA, double weightA, int axisB, double weightB) {
        double norm = Math.sqrt(weightA * weightA + weightB * weightB);
        float[] vector = new float[Chunk.EMBEDDING_DIMENSIONS];
        vector[axisA] = (float) (weightA / norm);
        vector[axisB] = (float) (weightB / norm);
        return vector;
    }

    public static float[] random(long seed) {
        java.util.Random random = new java.util.Random(seed);
        float[] vector = new float[Chunk.EMBEDDING_DIMENSIONS];
        for (int index = 0; index < vector.length; index++) {
            vector[index] = (float) random.nextGaussian();
        }
        return normalize(vector);
    }

    public static float[] around(int axis, double noise, long seed) {
        java.util.Random random = new java.util.Random(seed);
        float[] vector = new float[Chunk.EMBEDDING_DIMENSIONS];
        for (int index = 0; index < vector.length; index++) {
            vector[index] = (float) (random.nextGaussian() * noise);
        }
        vector[axis] += 1f;
        return normalize(vector);
    }

    private static float[] normalize(float[] vector) {
        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        float scale = (float) (1 / Math.sqrt(norm));
        for (int index = 0; index < vector.length; index++) {
            vector[index] *= scale;
        }
        return vector;
    }
}
