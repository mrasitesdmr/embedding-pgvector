package com.embedding.pgvector.search.service;

/**
 * Bir embedding vektörünü native sorgulara verilecek pgvector metin biçimine çevirir.
 */
public final class VectorLiteral {

    private VectorLiteral() {
    }

    public static String format(float[] embedding) {
        StringBuilder literal = new StringBuilder(embedding.length * 12 + 2).append('[');
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                literal.append(',');
            }
            literal.append(Float.toString(embedding[index]));
        }
        return literal.append(']').toString();
    }
}
