# 构建阶段：在容器里打包，宿主机不需要装 JDK 和 Maven
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MAVEN_MIRROR=https://repo.maven.apache.org/maven2
WORKDIR /src
COPY . .
RUN printf '<settings><mirrors><mirror><id>mirror</id><mirrorOf>*</mirrorOf><url>%s</url></mirror></mirrors></settings>' \
        "$MAVEN_MIRROR" > /tmp/settings.xml
# 缓存本地仓库，改代码后重新构建不用再下载依赖
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -s /tmp/settings.xml -DskipTests -pl tickflow-web -am package

# 运行阶段：只带 JRE 和 jar，镜像更小
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/tickflow-web/target/tickflow-web-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
