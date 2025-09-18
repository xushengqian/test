#!/bin/bash

# Script to run Spring Boot application with Metaspace tuning
# This addresses the OutOfMemoryError: Metaspace issue

echo "Starting Spring Boot application with Metaspace tuning..."

# JVM parameters to fix Metaspace OutOfMemoryError
JVM_OPTS="
-Xms512m
-Xmx2g
-XX:MetaspaceSize=256m
-XX:MaxMetaspaceSize=512m
-XX:MinMetaspaceFreeRatio=40
-XX:MaxMetaspaceFreeRatio=70
-XX:+UseG1GC
-XX:+UseStringDeduplication
-XX:+PrintGCDetails
-XX:+PrintGCTimeStamps
-XX:+PrintGCApplicationStoppedTime
-Xloggc:gc.log
-XX:+UseGCLogFileRotation
-XX:NumberOfGCLogFiles=5
-XX:GCLogFileSize=10M
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=./heapdump.hprof
-XX:+PrintClassHistogram
-XX:+PrintClassHistogramBeforeFullGC
-XX:+PrintClassHistogramAfterFullGC
"

# Additional monitoring options
MONITORING_OPTS="
-Dcom.sun.management.jmxremote
-Dcom.sun.management.jmxremote.port=9999
-Dcom.sun.management.jmxremote.authenticate=false
-Dcom.sun.management.jmxremote.ssl=false
"

# Run the application
java $JVM_OPTS $MONITORING_OPTS -jar target/metaspace-demo-1.0.0.jar

echo "Application started with Metaspace tuning parameters."
echo "Monitor Metaspace usage at: http://localhost:8080/api/memory/metaspace"
echo "View memory info at: http://localhost:8080/api/memory/info"
echo "JMX monitoring available at: localhost:9999"