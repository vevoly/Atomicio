package io.github.vevoly.atomicio.server.extension.redis.utils;

import io.github.vevoly.atomicio.common.api.dto.SessionDetails;
import org.springframework.boot.json.JsonParseException;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Json 工具类
 *
 * @since 0.6.10
 * @author vevoly
 */
public class JsonUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String serialize(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T deserialize(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, SessionDetails> convertMapToDetailsMap(Map<String, String> rawMap) {
        if (rawMap == null || rawMap.isEmpty()) return Collections.emptyMap();
        Map<String, SessionDetails> detailsMap = new HashMap<>();
        rawMap.forEach((deviceId, jsonString) -> {
            detailsMap.put(deviceId, deserialize(jsonString, SessionDetails.class));
        });
        return detailsMap;
    }

    public static Map<String, SessionDetails> convertListToDetailsMap(List<String> list) {
        if (list == null || list.isEmpty()) return Collections.emptyMap();
        Map<String, SessionDetails> map = new HashMap<>();
        for (int i = 0; i < list.size(); i += 2) {
            map.put(list.get(i), deserialize(list.get(i+1), SessionDetails.class));
        }
        return map;
    }

    // 重命名，以区分返回类型
    public static Map<String, String> convertListToJsonMap(List<String> list) {
        if (list == null || list.isEmpty()) return Collections.emptyMap();
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < list.size(); i += 2) {
            map.put(list.get(i), list.get(i + 1));
        }
        return map;
    }
}
