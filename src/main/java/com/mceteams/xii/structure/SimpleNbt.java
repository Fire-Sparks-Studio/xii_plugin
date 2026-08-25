package com.mceteams.xii.structure;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Lecteur NBT MINIMAL (spécification vanilla, big-endian) : juste ce
 * qu'il faut pour décoder un fichier de template de structure (.nbt).
 *
 * Indépendant de toute API serveur : utilisable comme solution de secours
 * quand StructureManager.loadStructure échoue silencieusement selon les
 * versions de Paper.
 */
public final class SimpleNbt {

    private SimpleNbt() {
    }

    /** Types de tags NBT vanilla. */
    private static final int TAG_END = 0;
    private static final int TAG_BYTE = 1;
    private static final int TAG_SHORT = 2;
    private static final int TAG_INT = 3;
    private static final int TAG_LONG = 4;
    private static final int TAG_FLOAT = 5;
    private static final int TAG_DOUBLE = 6;
    private static final int TAG_BYTE_ARRAY = 7;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;

    /** Décompresse si nécessaire puis lit le compound racine nommé. */
    public static Compound read(byte[] fileBytes) throws Exception {
        byte[] data = fileBytes;
        if (data.length > 2 && (data[0] & 0xFF) == 0x1F
                && (data[1] & 0xFF) == 0x8B) {
            try (GZIPInputStream gz =
                         new GZIPInputStream(new ByteArrayInputStream(data))) {
                data = gz.readAllBytes();
            }
        }
        Cursor cursor = new Cursor(ByteBuffer.wrap(data));
        cursor.skipRootHeader();
        return cursor.readCompoundPayload();
    }

    /** Position de lecture sur le buffer. */
    private static final class Cursor {
        private final ByteBuffer buf;

        private Cursor(ByteBuffer buf) {
            this.buf = buf;
        }

        /** En-tête racine : type (0x0A) + nom UTF-8 court. */
        private void skipRootHeader() {
            buf.get();               // type = TAG_COMPOUND
            short nameLength = buf.getShort();
            buf.position(buf.position() + nameLength);
        }

        private int remaining() {
            return buf.remaining();
        }

        private Compound readCompoundPayload() {
            Map<String, Object> values =
                    new LinkedHashMap<>();
            while (remaining() > 0) {
                int type = buf.get() & 0xFF;
                if (type == TAG_END) {
                    break;
                }
                String name = readString();
                values.put(name, readPayload(type));
            }
            return new Compound(values);
        }

        private Object readPayload(int type) {
            return switch (type) {
                case TAG_BYTE -> buf.get();
                case TAG_SHORT -> buf.getShort();
                case TAG_INT -> buf.getInt();
                case TAG_LONG -> buf.getLong();
                case TAG_FLOAT -> buf.getFloat();
                case TAG_DOUBLE -> buf.getDouble();
                case TAG_BYTE_ARRAY -> {
                    int length = buf.getInt();
                    byte[] array = new byte[length];
                    buf.get(array);
                    yield array;
                }
                case TAG_STRING -> readString();
                case TAG_LIST -> {
                    int elementType = buf.get() & 0xFF;
                    int length = buf.getInt();
                    List<Object> items = new ArrayList<>(Math.max(0,
                            Math.min(length, 1_000_000)));
                    for (int i = 0; i < length; i++) {
                        items.add(elementType == TAG_END
                                ? null : readPayload(elementType));
                    }
                    yield items;
                }
                case TAG_COMPOUND -> readCompoundPayload();
                default -> throw new IllegalStateException(
                        "Type NBT non supporté : " + type);
            };
        }

        private String readString() {
            int length = buf.getShort() & 0xFFFF;
            byte[] chars = new byte[length];
            buf.get(chars);
            return new String(chars, StandardCharsets.UTF_8);
        }
    }

    /** Compound NBT immuable avec accès typé tolérant. */
    public record Compound(Map<String, Object> values) {

        public Object get(String key) {
            return values.get(key);
        }

        public boolean has(String key) {
            return values.containsKey(key);
        }

        @SuppressWarnings("unchecked")
        public List<Object> getList(String key) {
            Object value = values.get(key);
            return value instanceof List<?> list ? (List<Object>) list : List.of();
        }

        public Compound getCompound(String key) {
            Object value = values.get(key);
            return value instanceof Compound compound ? compound : new Compound(Map.of());
        }

        public String getString(String key) {
            Object value = values.get(key);
            return value instanceof String string ? string : null;
        }

        public int getInt(String key) {
            Object value = values.get(key);
            return value instanceof Number number ? number.intValue() : 0;
        }

        /** Liste d'entiers (TAG_List de TAG_Int) => int[]. */
        public int[] getIntList(String key) {
            List<Object> list = getList(key);
            int[] result = new int[list.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = list.get(i) instanceof Number number
                        ? number.intValue() : 0;
            }
            return result;
        }
    }
}
