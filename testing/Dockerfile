FROM maven:3.9.9-eclipse-temurin-21

WORKDIR /workspace

COPY database ./database
COPY testing ./testing

WORKDIR /workspace/testing

CMD ["mvn", "-B", "test"]