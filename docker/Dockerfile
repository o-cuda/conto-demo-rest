FROM amazoncorretto:21.0.9-alpine3.22
WORKDIR /tmp
ADD conto-demo.jar /tmp/conto-demo.jar

USER root
RUN chown jboss:jboss /tmp/conto-demo.jar
RUN chmod 755 /tmp/conto-demo.jar
USER jboss

EXPOSE 8000
ENTRYPOINT ["java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000", "-jar", "conto-demo.jar"]

