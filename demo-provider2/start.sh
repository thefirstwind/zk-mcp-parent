#!/bin/bash

# Demo Provider 2 启动脚本 (Dubbo 2.5)
# 需要 Java 17 兼容性参数

JAVA_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED \
           --add-opens java.base/java.util=ALL-UNNAMED \
           --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
           --add-opens java.base/java.text=ALL-UNNAMED \
           --add-opens java.desktop/java.awt.font=ALL-UNNAMED"

mvn spring-boot:run -Dspring-boot.run.jvmArguments="$JAVA_OPTS"


