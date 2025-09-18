# Metaspace OutOfMemoryError Troubleshooting Guide

## Problem Description

The `java.lang.OutOfMemoryError: Metaspace` error occurs when the JVM's Metaspace area runs out of memory. The Metaspace stores class metadata including:

- Class definitions
- Method metadata
- Constant pool information
- Annotations
- Field information

## Common Causes

1. **Insufficient Metaspace Size**: Default Metaspace size is too small for the application
2. **Class Loading Issues**: Too many classes being loaded dynamically
3. **Memory Leaks**: Classes not being garbage collected properly
4. **Dynamic Class Generation**: Libraries creating classes at runtime
5. **Spring Boot Auto-configuration**: Large number of auto-configured beans

## Solutions

### 1. JVM Tuning Parameters

Add these JVM parameters to increase Metaspace size:

```bash
# Basic Metaspace tuning
-XX:MetaspaceSize=256m          # Initial Metaspace size
-XX:MaxMetaspaceSize=512m       # Maximum Metaspace size

# Advanced tuning
-XX:MinMetaspaceFreeRatio=40    # Minimum free space ratio
-XX:MaxMetaspaceFreeRatio=70    # Maximum free space ratio
-XX:+UseG1GC                    # Use G1 garbage collector
-XX:+UseStringDeduplication     # Enable string deduplication
```

### 2. Memory Monitoring

The application includes several endpoints for monitoring:

- `GET /api/memory/info` - General memory information
- `GET /api/memory/metaspace` - Metaspace-specific information
- `GET /actuator/metrics` - Spring Boot Actuator metrics
- `GET /actuator/prometheus` - Prometheus metrics

### 3. Application Configuration

In `application.yml`, configure:

```yaml
# Enable memory monitoring
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,memory
  endpoint:
    health:
      show-details: always
```

### 4. Code-Level Solutions

#### Enable Class Unloading
```java
// Ensure classes can be unloaded
-XX:+UnlockExperimentalVMOptions
-XX:+UseCGroupMemoryLimitForHeap
```

#### Monitor Class Loading
```java
// Add JVM parameters for class loading monitoring
-XX:+TraceClassLoading
-XX:+TraceClassUnloading
-verbose:class
```

### 5. Spring Boot Specific Solutions

#### Reduce Auto-configuration
```java
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    JpaRepositoriesAutoConfiguration.class
})
```

#### Use Lazy Initialization
```yaml
spring:
  main:
    lazy-initialization: true
```

## Monitoring and Diagnostics

### 1. Real-time Monitoring
```bash
# Monitor Metaspace usage
curl http://localhost:8080/api/memory/metaspace

# Check general memory info
curl http://localhost:8080/api/memory/info
```

### 2. JVM Monitoring
```bash
# Use jstat to monitor Metaspace
jstat -gc <pid> 5s

# Use jmap to analyze memory
jmap -histo <pid>
```

### 3. Heap Dump Analysis
```bash
# Generate heap dump on OOM
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=./heapdump.hprof

# Analyze with Eclipse MAT or VisualVM
```

## Prevention Strategies

1. **Regular Monitoring**: Set up alerts for Metaspace usage > 80%
2. **Code Reviews**: Review dynamic class generation code
3. **Testing**: Load test with production-like data
4. **JVM Tuning**: Start with conservative values and tune based on monitoring

## Emergency Fixes

If you encounter the error in production:

1. **Immediate**: Restart the application with increased Metaspace size
2. **Short-term**: Add monitoring and alerting
3. **Long-term**: Investigate root cause and optimize code

## Example JVM Parameters for Different Scenarios

### Small Application
```bash
-XX:MetaspaceSize=128m
-XX:MaxMetaspaceSize=256m
```

### Medium Application
```bash
-XX:MetaspaceSize=256m
-XX:MaxMetaspaceSize=512m
```

### Large Application
```bash
-XX:MetaspaceSize=512m
-XX:MaxMetaspaceSize=1g
```

### Enterprise Application
```bash
-XX:MetaspaceSize=1g
-XX:MaxMetaspaceSize=2g
-XX:+UseG1GC
-XX:+UseStringDeduplication
```

## Troubleshooting Checklist

- [ ] Check current Metaspace usage
- [ ] Review JVM parameters
- [ ] Analyze heap dump
- [ ] Check for memory leaks
- [ ] Review class loading patterns
- [ ] Test with increased Metaspace size
- [ ] Implement monitoring
- [ ] Document findings

## Additional Resources

- [Oracle JVM Tuning Guide](https://docs.oracle.com/en/java/javase/11/gctuning/)
- [Spring Boot Memory Tuning](https://spring.io/guides/gs/spring-boot-docker/)
- [Eclipse MAT for Heap Analysis](https://www.eclipse.org/mat/)