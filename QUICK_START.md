# Quick Start Guide - Metaspace OutOfMemoryError Fix

## Immediate Solution

If you're experiencing `java.lang.OutOfMemoryError: Metaspace` right now, add these JVM parameters:

```bash
-XX:MetaspaceSize=256m
-XX:MaxMetaspaceSize=512m
-XX:+UseG1GC
-XX:+UseStringDeduplication
```

## Build and Run

1. **Build the project:**
   ```bash
   mvn clean package
   ```

2. **Run with Metaspace tuning:**
   ```bash
   ./run-with-metaspace-tuning.sh
   ```

3. **Or run with Docker:**
   ```bash
   docker-compose up --build
   ```

## Monitor Memory Usage

- **Memory Info**: http://localhost:8080/api/memory/info
- **Metaspace Info**: http://localhost:8080/api/memory/metaspace
- **Health Check**: http://localhost:8080/actuator/health
- **Metrics**: http://localhost:8080/actuator/metrics

## Key JVM Parameters Explained

| Parameter | Purpose | Recommended Value |
|-----------|---------|-------------------|
| `-XX:MetaspaceSize` | Initial Metaspace size | 256m |
| `-XX:MaxMetaspaceSize` | Maximum Metaspace size | 512m |
| `-XX:+UseG1GC` | Use G1 garbage collector | - |
| `-XX:+UseStringDeduplication` | Deduplicate strings | - |
| `-XX:+HeapDumpOnOutOfMemoryError` | Create heap dump on OOM | - |

## Emergency Fix for Production

1. **Restart with increased Metaspace:**
   ```bash
   java -XX:MetaspaceSize=512m -XX:MaxMetaspaceSize=1g -jar your-app.jar
   ```

2. **Monitor the fix:**
   ```bash
   curl http://localhost:8080/api/memory/metaspace
   ```

## Next Steps

1. Read the full troubleshooting guide: `METASPACE_TROUBLESHOOTING.md`
2. Set up monitoring and alerting
3. Investigate root cause of excessive class loading
4. Optimize your application code if needed