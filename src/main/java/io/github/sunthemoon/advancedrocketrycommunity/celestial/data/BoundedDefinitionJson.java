package io.github.sunthemoon.advancedrocketrycommunity.celestial.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Strict, bounded raw input; no lenient Gson tree parser may discard duplicate keys. */
public final class BoundedDefinitionJson {
    public static final int MAX_BODY_BYTES = 32_768;
    public static final int MAX_ROUTE_BYTES = 4_096;
    public static final int MAX_NESTING = 16;

    private BoundedDefinitionJson() {
    }

    public static JsonElement read(InputStream input, int maxBytes) throws IOException {
        if (maxBytes < 1 || maxBytes > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("Definition byte limit is outside its fixed bound");
        }
        byte[] bytes = input.readNBytes(maxBytes + 1);
        if (bytes.length > maxBytes) {
            throw new IOException("definition exceeds " + maxBytes + " UTF-8 bytes");
        }
        String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        checkLexicalStrictness(text);
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            JsonElement value = value(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IOException("definition contains trailing JSON");
            }
            return value;
        }
    }

    /** Gson 2.10's non-lenient reader still permits non-JSON controls, escapes and keyword casing. */
    private static void checkLexicalStrictness(String text) throws IOException {
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (quoted) {
                if (value < 0x20) {
                    throw new IOException("unescaped control character in JSON string");
                }
                if (escaped) {
                    if ("\"\\/bfnrtu".indexOf(value) < 0) {
                        throw new IOException("invalid JSON string escape");
                    }
                    escaped = false;
                } else if (value == '\\') {
                    escaped = true;
                } else if (value == '"') {
                    quoted = false;
                }
            } else if (value == '"') {
                quoted = true;
            } else if (value == 't' || value == 'f' || value == 'n') {
                String keyword = value == 't' ? "true" : value == 'f' ? "false" : "null";
                if (!text.startsWith(keyword, index)) {
                    throw new IOException("invalid JSON keyword");
                }
                index += keyword.length() - 1;
            } else if (value == 'T' || value == 'F' || value == 'N') {
                throw new IOException("invalid JSON keyword");
            }
        }
    }

    private static JsonElement value(JsonReader reader, int depth) throws IOException {
        JsonToken token = reader.peek();
        if ((token == JsonToken.BEGIN_OBJECT || token == JsonToken.BEGIN_ARRAY) && depth >= MAX_NESTING) {
            throw new IOException("JSON nesting exceeds " + MAX_NESTING);
        }
        return switch (token) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                JsonObject object = new JsonObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) {
                        throw new IOException("duplicate JSON key: " + name);
                    }
                    object.add(name, value(reader, depth + 1));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                JsonArray array = new JsonArray();
                while (reader.hasNext()) {
                    array.add(value(reader, depth + 1));
                }
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            // The strict lexer already validated this one token. Preserve its spelling for
            // exact-integer codecs instead of normalizing exponents/fractions via BigDecimal.
            case NUMBER -> JsonParser.parseString(reader.nextString());
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw new IOException("expected a JSON value");
        };
    }
}
