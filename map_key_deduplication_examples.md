# Map作为Key的判重方法

## 问题描述
当需要使用map/dict/object作为key进行判重时，不同语言有不同的处理方式。

## 解决方案

### 1. Go语言
Go中map不能直接作为key（因为map不可比较），需要转换为可比较的类型。

```go
package main

import (
    "fmt"
    "encoding/json"
)

// 方法1: 将map序列化为字符串作为key
func deduplicateByMapKey(maps []map[string]interface{}) []map[string]interface{} {
    seen := make(map[string]bool)
    result := []map[string]interface{}{}
    
    for _, m := range maps {
        // 将map序列化为JSON字符串
        jsonBytes, _ := json.Marshal(m)
        key := string(jsonBytes)
        
        if !seen[key] {
            seen[key] = true
            result = append(result, m)
        }
    }
    
    return result
}

// 方法2: 使用结构体作为key（如果map结构固定）
type MapKey struct {
    Key1 string
    Key2 int
}

func deduplicateByStructKey(maps []map[string]interface{}) []map[string]interface{} {
    seen := make(map[MapKey]bool)
    result := []map[string]interface{}{}
    
    for _, m := range maps {
        key := MapKey{
            Key1: m["key1"].(string),
            Key2: m["key2"].(int),
        }
        
        if !seen[key] {
            seen[key] = true
            result = append(result, m)
        }
    }
    
    return result
}
```

### 2. Python语言
Python中dict不能直接作为key（因为dict不可哈希），需要转换为可哈希的类型。

```python
# 方法1: 将dict转换为frozenset（适用于简单键值对）
def deduplicate_by_map_key(maps):
    seen = set()
    result = []
    
    for m in maps:
        # 将dict转换为frozenset
        key = frozenset(m.items())
        if key not in seen:
            seen.add(key)
            result.append(m)
    
    return result

# 方法2: 将dict序列化为字符串
import json

def deduplicate_by_json_key(maps):
    seen = set()
    result = []
    
    for m in maps:
        # 将dict序列化为JSON字符串（需要排序键）
        key = json.dumps(m, sort_keys=True)
        if key not in seen:
            seen.add(key)
            result.append(m)
    
    return result

# 方法3: 使用tuple（适用于固定键的情况）
def deduplicate_by_tuple_key(maps, keys):
    seen = set()
    result = []
    
    for m in maps:
        key = tuple(m[k] for k in keys)
        if key not in seen:
            seen.add(key)
            result.append(m)
    
    return result
```

### 3. Java语言
Java中可以使用HashMap作为key，但需要正确实现equals和hashCode。

```java
import java.util.*;

// 方法1: 使用HashMap作为key（需要正确实现equals和hashCode）
public class MapKeyDeduplication {
    public static List<Map<String, Object>> deduplicateByMapKey(
            List<Map<String, Object>> maps) {
        Set<Map<String, Object>> seen = new HashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (Map<String, Object> m : maps) {
            // HashMap已经正确实现了equals和hashCode
            if (!seen.contains(m)) {
                seen.add(new HashMap<>(m)); // 创建副本避免引用问题
                result.add(m);
            }
        }
        
        return result;
    }
    
    // 方法2: 使用序列化字符串作为key
    public static List<Map<String, Object>> deduplicateByJsonKey(
            List<Map<String, Object>> maps) {
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (Map<String, Object> m : maps) {
            // 使用JSON序列化（需要引入JSON库如Jackson或Gson）
            String key = serializeToJson(m);
            if (!seen.contains(key)) {
                seen.add(key);
                result.add(m);
            }
        }
        
        return result;
    }
}
```

### 4. JavaScript/TypeScript
JavaScript中Map和Object不能直接作为Set的key，需要转换为字符串。

```javascript
// 方法1: 将对象序列化为JSON字符串
function deduplicateByMapKey(maps) {
    const seen = new Set();
    const result = [];
    
    for (const m of maps) {
        // 将对象序列化为JSON字符串（需要排序键）
        const key = JSON.stringify(m, Object.keys(m).sort());
        if (!seen.has(key)) {
            seen.add(key);
            result.push(m);
        }
    }
    
    return result;
}

// 方法2: 使用Map存储原始对象引用
function deduplicateByReference(maps) {
    const seen = new Map();
    const result = [];
    
    for (const m of maps) {
        const key = JSON.stringify(m, Object.keys(m).sort());
        if (!seen.has(key)) {
            seen.set(key, m);
            result.push(m);
        }
    }
    
    return result;
}
```

## 推荐方案
1. **序列化方法**：最通用，适用于所有语言和任意map结构
2. **结构体/类方法**：性能最好，但需要map结构固定
3. **frozenset/tuple方法**：Python特有，简单高效

## 注意事项
- 序列化时注意键的顺序（使用sort_keys或排序）
- 处理嵌套map时需要递归序列化
- 注意性能：序列化有开销，大量数据时考虑其他方案
