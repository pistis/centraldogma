/*
 * Copyright 2017 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.linecorp.centraldogma.internal.jsonpatch;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Equivalence;

import com.linecorp.centraldogma.internal.jsonpatch.utils.JsonNumEquals;

public class SafeReplaceOperation extends JsonPatchOperation {

    private static final Equivalence<JsonNode> EQUIVALENCE
            = JsonNumEquals.getInstance();

    @JsonSerialize
    protected final JsonNode oldValue;
    @JsonSerialize
    protected final JsonNode newValue;

    @JsonCreator
    public SafeReplaceOperation(@JsonProperty("path") final JsonPointer path,
                                @JsonProperty("oldValue") JsonNode oldValue,
                                @JsonProperty("value") JsonNode newValue) {
        super("safeReplace", path);
        this.oldValue = oldValue.deepCopy();
        this.newValue = newValue.deepCopy();
    }

    @Override
    public JsonNode apply(JsonNode node) throws JsonPatchException {
        final JsonNode tested = node.at(path);
        if (tested.isMissingNode()) {
            throw new JsonPatchException("non-existent path: " + path);
        }
        if (!EQUIVALENCE.equivalent(tested, oldValue)) {
            throw new JsonPatchException("mismatching value: " + tested + " (expected: " + oldValue + ')');
        }
        final JsonNode replacement = newValue.deepCopy();
        if (path.toString().isEmpty()) {
            return replacement;
        }
        final JsonNode ret = node.deepCopy();
        final JsonNode parent = ret.at(path.head());
        final String rawToken = path.last().getMatchingProperty();
        if (parent.isObject()) {
            ((ObjectNode) parent).set(rawToken, replacement);
        } else {
            ((ArrayNode) parent).set(Integer.parseInt(rawToken), replacement);
        }
        return ret;
    }

    @Override
    public void serialize(JsonGenerator gen, SerializerProvider serializers) throws IOException {

        gen.writeStartObject();
        gen.writeStringField("op", op);
        gen.writeStringField("path", path.toString());
        gen.writeFieldName("oldValue");
        gen.writeTree(oldValue);
        gen.writeFieldName("value");
        gen.writeTree(newValue);
        gen.writeEndObject();
    }

    @Override
    public void serializeWithType(JsonGenerator gen, SerializerProvider serializers, TypeSerializer typeSer)
            throws IOException {
        serialize(gen, serializers);
    }

    @Override
    public String toString() {
        return "op: " + op + "; path: \"" + path + "\"; oldValue: " + oldValue + "; value: " + newValue;
    }
}
