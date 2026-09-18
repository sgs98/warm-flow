package org.dromara.warm.flow.core.test;

import org.dromara.warm.flow.core.json.JsonConvert;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单测用反射 JSON 转换器：支持 Map 与简单 bean（如 DefJson/NodeJson）的对称编解码，
 * 覆盖实例变量合并与 defJson 元数据落库/回读路径，供特征测试在纯 JVM 环境运行。
 *
 * @author warm
 */
public class FlatJsonConvert implements JsonConvert {

    @Override
    public Map<String, Object> strToMap(String jsonStr) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (jsonStr == null) {
            return map;
        }
        Object parsed = parse(jsonStr.trim());
        if (parsed instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                map.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return map;
    }

    @Override
    public String objToStr(Object obj) {
        if (obj == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        write(sb, obj);
        return sb.toString();
    }

    @Override
    public <T> T strToBean(String jsonStr, Class<T> clazz) {
        if (jsonStr == null) {
            return null;
        }
        return clazz.cast(bind(parse(jsonStr.trim()), clazz));
    }

    @Override
    public <T> List<T> strToList(String jsonStr) {
        List<T> result = new ArrayList<>();
        if (jsonStr == null) {
            return result;
        }
        Object parsed = parse(jsonStr.trim());
        if (parsed instanceof List<?> list) {
            for (Object o : list) {
                @SuppressWarnings("unchecked")
                T cast = (T) o;
                result.add(cast);
            }
        }
        return result;
    }

    // ---------- 序列化 ----------

    private void write(StringBuilder sb, Object obj) {
        if (obj == null) {
            sb.append("null");
        } else if (obj instanceof String || obj instanceof Enum<?> || obj instanceof Character) {
            writeString(sb, obj.toString());
        } else if (obj instanceof Number || obj instanceof Boolean) {
            sb.append(obj);
        } else if (obj instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (obj instanceof Collection<?> coll) {
            sb.append('[');
            boolean first = true;
            for (Object o : coll) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(sb, o);
            }
            sb.append(']');
        } else {
            // 反射 bean：按 getter 输出（get/is 前缀），与 bind 的 setter 对称
            sb.append('{');
            boolean first = true;
            for (Method m : obj.getClass().getMethods()) {
                if (m.getParameterCount() != 0 || m.isVarArgs()) {
                    continue;
                }
                String key = getterKey(m);
                if (key == null) {
                    continue;
                }
                try {
                    Object v = m.invoke(obj);
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    writeString(sb, key);
                    sb.append(':');
                    write(sb, v);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("序列化 getter 失败: " + m, e);
                }
            }
            sb.append('}');
        }
    }

    private String getterKey(Method m) {
        String name = m.getName();
        if (name.startsWith("get") && name.length() > 3 && !"getClass".equals(name)) {
            return Character.toLowerCase(name.charAt(3)) + name.substring(4);
        }
        if (name.startsWith("is") && name.length() > 2
                && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) {
            return Character.toLowerCase(name.charAt(2)) + name.substring(3);
        }
        return null;
    }

    private void writeString(StringBuilder sb, String s) {
        sb.append('"').append(s.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
    }

    // ---------- 解析 ----------

    private Object parse(String json) {
        if (json == null || json.isEmpty() || "null".equals(json)) {
            return null;
        }
        Parser p = new Parser(json);
        Object v = p.parseValue();
        p.skipSpace();
        if (!p.eof()) {
            throw new IllegalArgumentException("JSON 尾部存在非法字符: " + json);
        }
        return v;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        boolean eof() {
            return i >= s.length();
        }

        void skipSpace() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        Object parseValue() {
            skipSpace();
            if (eof()) {
                return null;
            }
            char c = s.charAt(i);
            if (c == '{') {
                return parseMap();
            }
            if (c == '[') {
                return parseList();
            }
            if (c == '"') {
                return parseString();
            }
            int start = i;
            while (i < s.length() && ",}]:".indexOf(s.charAt(i)) < 0) {
                i++;
            }
            String raw = s.substring(start, i).trim();
            if ("null".equals(raw)) {
                return null;
            }
            if ("true".equals(raw) || "false".equals(raw)) {
                return Boolean.valueOf(raw);
            }
            try {
                long v = Long.parseLong(raw);
                if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) {
                    return (int) v;
                }
                return v;
            } catch (NumberFormatException ignored) {
                return raw;
            }
        }

        private Map<String, Object> parseMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            i++; // {
            skipSpace();
            if (!eof() && s.charAt(i) == '}') {
                i++;
                return map;
            }
            while (!eof()) {
                skipSpace();
                Object key = parseValue();
                skipSpace();
                if (!eof() && s.charAt(i) == ':') {
                    i++;
                }
                Object value = parseValue();
                map.put(String.valueOf(key), value);
                skipSpace();
                if (!eof() && s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                if (!eof() && s.charAt(i) == '}') {
                    i++;
                    break;
                }
            }
            return map;
        }

        private List<Object> parseList() {
            List<Object> list = new ArrayList<>();
            i++; // [
            skipSpace();
            if (!eof() && s.charAt(i) == ']') {
                i++;
                return list;
            }
            while (!eof()) {
                list.add(parseValue());
                skipSpace();
                if (!eof() && s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                if (!eof() && s.charAt(i) == ']') {
                    i++;
                    break;
                }
            }
            return list;
        }

        private String parseString() {
            StringBuilder sb = new StringBuilder();
            i++; // "
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '\\' && i + 1 < s.length()) {
                    char n = s.charAt(i + 1);
                    sb.append(n == '"' || n == '\\' ? n : c == '\\' ? n : n);
                    i += 2;
                    continue;
                }
                if (c == '"') {
                    i++;
                    break;
                }
                sb.append(c);
                i++;
            }
            return sb.toString();
        }
    }

    // ---------- 绑定 ----------

    private Object bind(Object parsed, Type type) {
        if (parsed == null) {
            return null;
        }
        Class<?> clazz = type instanceof Class<?> c ? c : (Class<?>) ((ParameterizedType) type).getRawType();
        if (clazz == String.class || clazz == Object.class && parsed instanceof String) {
            return parsed;
        }
        if (clazz == String.class) {
            return String.valueOf(parsed);
        }
        if (clazz == Boolean.class || clazz == boolean.class) {
            return parsed instanceof Boolean b ? b : Boolean.valueOf(parsed.toString());
        }
        if (Number.class.isAssignableFrom(clazz) || clazz.isPrimitive()) {
            Number n = parsed instanceof Number number ? number : null;
            if (n == null) {
                throw new IllegalArgumentException("无法绑定数字: " + parsed);
            }
            if (clazz == Integer.class || clazz == int.class) {
                return n.intValue();
            }
            if (clazz == Long.class || clazz == long.class) {
                return n.longValue();
            }
            if (clazz == Double.class || clazz == double.class) {
                return n.doubleValue();
            }
            if (clazz == Float.class || clazz == float.class) {
                return n.floatValue();
            }
            if (clazz == Short.class || clazz == short.class) {
                return n.shortValue();
            }
            if (clazz == Byte.class || clazz == byte.class) {
                return n.byteValue();
            }
        }
        if (clazz.isEnum()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object e = Enum.valueOf((Class<? extends Enum>) clazz, parsed.toString());
            return e;
        }
        if (List.class.isAssignableFrom(clazz)) {
            List<Object> result = new ArrayList<>();
            if (parsed instanceof List<?> list) {
                Type elementType = Object.class;
                if (type instanceof ParameterizedType pt && pt.getActualTypeArguments().length == 1) {
                    elementType = pt.getActualTypeArguments()[0];
                }
                for (Object o : list) {
                    result.add(bind(o, elementType));
                }
            }
            return result;
        }
        if (Map.class.isAssignableFrom(clazz)) {
            Map<Object, Object> result = new LinkedHashMap<>();
            if (parsed instanceof Map<?, ?> map) {
                Type valType = Object.class;
                if (type instanceof ParameterizedType pt && pt.getActualTypeArguments().length == 2) {
                    valType = pt.getActualTypeArguments()[1];
                }
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    result.put(e.getKey(), bind(e.getValue(), valType));
                }
            }
            return result;
        }
        // bean 绑定
        if (parsed instanceof Map<?, ?> map) {
            try {
                Object bean = clazz.getDeclaredConstructor().newInstance();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    String key = String.valueOf(e.getKey());
                    String setter = "set" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
                    for (Method m : clazz.getMethods()) {
                        if (m.getName().equals(setter) && m.getParameterCount() == 1) {
                            m.invoke(bean, bind(e.getValue(), m.getGenericParameterTypes()[0]));
                            break;
                        }
                    }
                }
                return bean;
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("绑定 bean 失败: " + clazz, ex);
            }
        }
        return parsed;
    }
}
