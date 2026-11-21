# syntax=docker/dockerfile:1
# 多阶段构建：第一阶段 Maven 编译，第二阶段 JRE 运行

# ====== 第一阶段：Maven 编译 ======
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
# 复制整个后端工程（父 pom + 各 module）
COPY . .
# 仅构建 nway-system-server 及其依赖模块，跳过测试
RUN mvn -pl nway-system-server -am clean package -DskipTests -B

# ====== 第二阶段：JRE 运行 ======
FROM eclipse-temurin:21-jre-noble
LABEL maintainer="nway-system"
# 时区 Asia/Shanghai + 安装 wget 用于健康检查
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone \
    && apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*
# JVM 参数：限制容器内存占用百分比并使用 G1GC
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
WORKDIR /app
# nway-system-server 的 finalName 默认为 artifactId-version：nway-system-server-1.0.0.jar
COPY --from=builder /build/nway-system-server/target/nway-system-server-1.0.0.jar /app/nway-system-server.jar
EXPOSE 8081
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/nway-system-server.jar"]
