# =============================================================================
# BACKEND IMAGE (Spring Boot API)
# -----------------------------------------------------------------------------
# A Dockerfile is a recipe for building an IMAGE (a read-only snapshot of a
# filesystem plus the command to run). Each instruction (FROM, RUN, COPY, ...)
# creates one LAYER. Docker caches layers: if an instruction and everything it
# depends on is unchanged since the last build, Docker reuses the cached layer
# instead of running it again. Order matters, because once one layer changes,
# every layer after it is rebuilt.
#
# This file is a MULTI-STAGE build: two "FROM" lines = two separate stages.
#   Stage 1 ("build"):  has Maven + a full JDK. Compiles the code into a .jar.
#   Stage 2 (final):    has only a small Java runtime. Receives just the .jar.
# Only the LAST stage becomes the image you run. Stage 1 is thrown away, so
# Maven, the source code and all downloaded dependencies never ship.
#
# You normally do not run this file by hand. docker-compose.yml (in the project
# root) builds it and passes in the configuration. To build it alone:
#   docker build -t takemybible-backend .
# =============================================================================


# ----------------------------- STAGE 1: BUILD --------------------------------

# FROM picks the BASE IMAGE this stage starts from.
#   maven:3.9-eclipse-temurin-25 = Maven 3.9 + Eclipse Temurin JDK 25 on Linux.
#   "AS build" gives the stage a name so stage 2 can copy files out of it.
# The Java version must match <java.version> in pom.xml (25). A JDK 21 compiler
# cannot compile for Java 25.
FROM maven:3.9-eclipse-temurin-25 AS build

# WORKDIR sets the current directory for every instruction after it, and
# creates the directory if it does not exist. Like "mkdir -p /app && cd /app".
# Paths below such as "." and "./src" are relative to /app inside the image.
WORKDIR /app

# COPY <from build context> <to image>. The "build context" is the folder you
# pass to "docker build" (here backend/backend, set in docker-compose.yml).
# Only pom.xml is copied at this point, on purpose: see the next RUN.
COPY pom.xml .

# Download every dependency and Maven plugin the project needs, using only
# pom.xml. "-q" = quiet output.
# WHY THIS IS SPLIT FROM THE SOURCE COPY: this layer is cached as long as
# pom.xml is unchanged. When you edit a .java file, Docker reuses this layer and
# skips the slow download, so rebuilds take seconds instead of minutes.
# (A few plugins are only needed at package time and may still download then.
# That is harmless.)
RUN mvn -q dependency:go-offline

# Now copy the source code. This layer changes on almost every code edit,
# which is why it comes AFTER the dependency download above.
COPY src ./src

# Compile and package the app into an executable "fat" jar in /app/target.
#   clean         = delete old build output first
#   package       = compile and build the jar (Spring Boot's Maven plugin turns
#                   it into one runnable jar containing all its dependencies)
#   -DskipTests   = do not run tests. A Spring Boot test typically starts the
#                   whole app, which needs a database and env vars that do not
#                   exist while building an image.
RUN mvn -q clean package -DskipTests


# ----------------------------- STAGE 2: RUN ----------------------------------

# A fresh, separate stage starting from a different base image.
# "-jre" = Java Runtime Environment only: no compiler and no Maven. Smaller to
# download and less software that could contain vulnerabilities. Nothing from
# stage 1 exists here unless we explicitly copy it in (see COPY --from below).
FROM eclipse-temurin:25-jre

# Working directory for the following instructions and for the running
# container. The app also looks here for an optional ".env" file
# (spring.config.import in application.properties). No .env is copied into the
# image (see .dockerignore), so all configuration arrives as environment
# variables when the container starts.
WORKDIR /app

# Create a system user (-r) named "spring". By default a container's processes
# run as root, so a bug in the app would have root inside the container.
# Running as a normal user limits the damage. (Used by the USER line below.)
RUN useradd -r spring

# Copy the finished jar OUT of the "build" stage into this image and rename it
# to app.jar. "--from=build" refers to the stage named above.
# The * matches the jar's version-specific name (backend-0.0.1-SNAPSHOT.jar).
# It must match exactly ONE file or COPY errors. Spring Boot also leaves a
# "*.jar.original" file, which does not match "*.jar", so that is fine.
COPY --from=build /app/target/*.jar app.jar

# From here on, and when the container runs, act as the "spring" user, not root.
USER spring

# EXPOSE only DOCUMENTS which port the app listens on. It does not open or
# publish anything. The real port is whatever the SERVER_PORT variable says
# (application.properties: server.port=${SERVER_PORT}); docker-compose.yml sets
# it to 8080 so this line is accurate. In compose the backend is deliberately
# NOT published to the host: only nginx can reach it, over a private network.
EXPOSE 8080

# The command the container runs on start. Written in "exec form" (a JSON
# array) so java itself is the main process (PID 1) and receives stop signals
# (docker stop) directly, which allows a clean shutdown. A plain string would
# be wrapped in a shell, which often swallows those signals.
#   -XX:MaxRAMPercentage=60  limit the JVM heap to 60% of the memory available
#                            to the container, leaving room for non-heap memory.
#                            Without a container memory limit, "available"
#                            means the whole machine's RAM. docker-compose.yml
#                            sets a limit (mem_limit) so this has a real base.
#   -jar app.jar             run the executable jar built in stage 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "app.jar"]
