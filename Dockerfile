FROM openjdk:11-jre
WORKDIR /opt
ENV PORT 8080
EXPOSE 8080
COPY ./knote-java/target/*.jar /opt/app.jar
ENTRYPOINT exec java $JAVA_OPTS -jar app.jar