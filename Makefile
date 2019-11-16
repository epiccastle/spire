GRAALVM = $(HOME)/graalvm-ce-19.2.1
VERSION = 0.1.0-SNAPSHOT

all: build/spire

clean:
	-rm -rf build target
	lein clean
	-rm src/c/*.o libioctl.so src/c/ioctl.h src/java/icotcl.h src/c/ioctl.h src/java/icotcl.class

target/uberjar/spire-$(VERSION)-standalone.jar: $(SRC)
	GRAALVM_HOME=$(GRAALVM) lein uberjar

analyse:
	$(GRAALVM)/bin/java -agentlib:native-image-agent=config-output-dir=config-dir \
		-jar target/uberjar/spire-$(VERSION)-standalone.jar

build/spire: target/uberjar/spire-$(VERSION)-standalone.jar
	-mkdir build
	export
	$(GRAALVM)/bin/native-image \
		-jar target/uberjar/spire-$(VERSION)-standalone.jar \
		-H:Name=build/spire \
		-H:+ReportExceptionStackTraces \
		-J-Dclojure.spec.skip-macros=true \
		-J-Dclojure.compiler.direct-linking=true \
		-H:ConfigurationFileDirectories=graal-configs/ \
		--initialize-at-build-time \
		-H:Log=registerResource: \
		-H:EnableURLProtocols=http,https \
		--verbose \
		--allow-incomplete-classpath \
		--no-fallback \
		--no-server \
		"-J-Xmx6g" \
		-H:+TraceClassInitialization -H:+PrintClassInitialization
	cp build/spire spire

JNI_DIR=target/jni
CLASS_DIR=target/default/classes
CLASS_NAME=SpireUtils
CLASS_FILE=$(CLASS_DIR)/$(CLASS_NAME).class
JAR_FILE=target/uberjar/spire-0.1.0-SNAPSHOT-standalone.jar
LIB_FILE=$(JNI_DIR)/libspire.so
JAVA_FILE=src/c/SpireUtils.java
C_FILE=src/c/SpireUtils.c
C_HEADER=$(JNI_DIR)/SpireUtils.h
INCLUDE_DIRS=$(shell find $(JAVA_HOME)/include -type d)
INCLUDE_ARGS=$(INCLUDE_DIRS:%=-I%) -I$(JNI_DIR)

run: $(LIB_FILE) $(JAR_FILE)
	java -jar $(JAR_FILE)

jar: $(JAR_FILE)

$(JAR_FILE): $(CLASS_FILE) $(C_HEADER)
	lein uberjar

$(CLASS_FILE): $(JAVA_FILE)
	lein javac

header: $(C_HEADER)

$(C_HEADER): $(CLASS_FILE)
	mkdir -p $(JNI_DIR)
	javah -o $(C_HEADER) -cp $(CLASS_DIR) $(CLASS_NAME)
	@touch $(C_HEADER)

lib: $(LIB_FILE)

$(LIB_FILE): $(C_FILE) $(C_HEADER)
	$(CC) $(INCLUDE_ARGS) -shared $(C_FILE) -o $(LIB_FILE) -fPIC
	cp $(LIB_FILE) ./

clean:
	lein clean
	rm -rf $(JNI_DIR)
