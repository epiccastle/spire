GRAALVM = $(HOME)/graalvm-ce-java8-19.3.0
VERSION = 0.1.0-SNAPSHOT
UNAME = $(shell uname)

all: build/spire

analyse:
	$(GRAALVM)/bin/java -agentlib:native-image-agent=config-output-dir=config-dir \
		-jar target/uberjar/spire-$(VERSION)-standalone.jar

build/spire: target/uberjar/spire-$(VERSION)-standalone.jar
	-mkdir build
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
SOLIB_FILE=$(JNI_DIR)/libspire.so
DYLIB_FILE=$(JNI_DIR)/spire.dylib
JAVA_FILE=src/c/SpireUtils.java
C_FILE=src/c/SpireUtils.c
C_HEADER=$(JNI_DIR)/SpireUtils.h
ifndef JAVA_HOME
	JAVA_HOME=$(GRAALVM)
endif
INCLUDE_DIRS=$(shell find $(JAVA_HOME)/include -type d)
INCLUDE_ARGS=$(INCLUDE_DIRS:%=-I%) -I$(JNI_DIR)

ifeq ($(UNAME),Linux)
	LIB_FILE=$(SOLIB_FILE)
else ifeq ($(UNAME),FreeBSD)
	LIB_FILE=$(SOLIB_FILE)
else ifeq ($(UNAME),Darwin)
	LIB_FILE=$(DYLIB_FILE)
endif

run: $(LIB_FILE) $(JAR_FILE)
	java -jar $(JAR_FILE)

jar: $(JAR_FILE)

$(JAR_FILE): $(CLASS_FILE) $(C_HEADER) $(LIB_FILE)
	GRAALVM_HOME=$(GRAALVM) lein uberjar

$(CLASS_FILE): $(JAVA_FILE)
	lein javac

header: $(C_HEADER)

$(C_HEADER): $(CLASS_FILE)
	mkdir -p $(JNI_DIR)
	javah -o $(C_HEADER) -cp $(CLASS_DIR) $(CLASS_NAME)
	@touch $(C_HEADER)

lib: $(LIB_FILE)

$(SOLIB_FILE): $(C_FILE) $(C_HEADER)
	$(CC) $(INCLUDE_ARGS) -shared $(C_FILE) -o $(SOLIB_FILE) -fPIC
	cp $(SOLIB_FILE) ./
	mkdir -p resources
	cp $(SOLIB_FILE) ./resources/

$(DYLIB_FILE):  $(C_FILE) $(C_HEADER)
	$(CC) $(INCLUDE_ARGS) -dynamiclib -undefined suppress -flat_namespace $(C_FILE) -o $(DYLIB_FILE) -fPIC
	cp $(DYLIB_FILE) ./
	mkdir -p resources
	cp $(DYLIB_FILE) ./resources/

clean:
	-rm -rf build target
	lein clean
	-rm src/c/*.o libspire.so src/c/SpireUtils.h resources/libspire.so
	-rm -rf $(JNI_DIR)

copy-libs-to-resource:
	-cp $(GRAALVM)/jre/lib/sunec.lib resources
	-cp $(GRAALVM)/jre/bin/sunec.dll resources
	-cp $(GRAALVM)/jre/lib/libsunec.dylib resources
	-cp $(GRAALVM)/jre/lib/amd64/libsunec.so resources

linux-package:
	-rm -rf build/linux-package
	-mkdir -p build/linux-package
	cp spire build/linux-package
	cd build/linux-package && GZIP=-9 tar cvzf ../spire-$(VERSION)-linux-amd64.tgz spire
	du -sh spire build/spire-$(VERSION)-linux-amd64.tgz

#
# CircleCI
#
ssh_test_key_rsa:
	ssh-keygen -t rsa -f ssh_test_key_rsa -b 2048 -q -N ""
	grep -qxF "$(cat ssh_test_key_rsa.pub)" ~/.ssh/authorized_keys || cat ssh_test_key_rsa.pub >> ~/.ssh/authorized_keys

circle-setup: ssh_test_key_rsa
	-lsb-release -a
	sudo /usr/sbin/sshd -f test/config/sshd_config -D & echo "$$!" > sshd.pid
	eval `ssh-agent` && ssh-add ssh_test_key_rsa && lein trampoline test; EXIT=$$?; sudo kill `cat sshd.pid`; exit $$EXIT
