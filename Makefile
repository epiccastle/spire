GRAALVM = $(HOME)/graalvm-ce-java11-20.1.0
ifneq (,$(findstring java11,$(GRAALVM)))
	JAVA_VERSION = 11
else
	JAVA_VERSION = 8
endif
VERSION = $(shell cat .meta/VERSION)
UNAME = $(shell uname)

all: sci-reflector-install build/spire

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
		--initialize-at-run-time=com.jcraft.jsch.PortWatcher \
		-H:Log=registerResource: \
		-H:EnableURLProtocols=http,https \
		--enable-all-security-services \
		-H:+JNI \
		--report-unsupported-elements-at-runtime \
		--verbose \
		--allow-incomplete-classpath \
		--no-fallback \
		--no-server \
		"-J-Xmx6500m"
	cp build/spire spire

JNI_DIR=target/jni
CLASS_DIR=target/default/classes
CLASS_NAME=SpireUtils
CLASS_FILE=$(CLASS_DIR)/$(CLASS_NAME).class
JAR_FILE=target/uberjar/spire-$(VERSION)-standalone.jar
SOLIB_FILE=$(JNI_DIR)/libspire.so
DYLIB_FILE=$(JNI_DIR)/libspire.dylib
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

sci-reflector-install:
	cd sci-reflector && lein install

run: $(LIB_FILE) $(JAR_FILE)
	java -jar $(JAR_FILE)

jar: $(JAR_FILE)

$(JAR_FILE): $(CLASS_FILE) $(C_HEADER) $(LIB_FILE)
	GRAALVM_HOME=$(GRAALVM) lein with-profiles +native-image uberjar

$(CLASS_FILE): $(JAVA_FILE)
	lein javac

header: $(C_HEADER)

$(C_HEADER): $(CLASS_FILE)
	mkdir -p $(JNI_DIR)
ifeq ($(JAVA_VERSION),8)
	javah -o $(C_HEADER) -cp $(CLASS_DIR) $(CLASS_NAME)
else
	javac -h $(JNI_DIR) $(JAVA_FILE)
endif
	@touch $(C_HEADER)

lib: $(LIB_FILE)

$(SOLIB_FILE): $(C_FILE) $(C_HEADER)
	$(CC) $(INCLUDE_ARGS) -shared $(C_FILE) -o $(SOLIB_FILE) -fPIC
	cp $(SOLIB_FILE) ./
	mkdir -p resources
	cp $(SOLIB_FILE) ./resources/
	make copy-libs-to-resource

$(DYLIB_FILE):  $(C_FILE) $(C_HEADER)
	$(CC) $(INCLUDE_ARGS) -dynamiclib -undefined suppress -flat_namespace $(C_FILE) -o $(DYLIB_FILE) -fPIC
	cp $(DYLIB_FILE) ./
	mkdir -p resources
	cp $(DYLIB_FILE) ./resources/
	make copy-libs-to-resource

clean:
	-rm -rf build target
	lein clean
	-rm src/c/*.o libspire.so src/c/SpireUtils.h resources/libspire.so libspire.dynlib resources/libspire.dynlib
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
	cp target/uberjar/spire-$(VERSION)-standalone.jar build/spire-$(VERSION)-linux-standalone.jar
	du -sh spire build/spire-$(VERSION)-linux-amd64.tgz build/spire-$(VERSION)-linux-standalone.jar

macos-package:
	-rm -rf build/macos-package
	-mkdir -p build/macos-package
	cp spire build/macos-package
	cd build/macos-package && zip ../spire-$(VERSION)-macos-amd64.zip spire
	cp target/uberjar/spire-$(VERSION)-standalone.jar build/spire-$(VERSION)-macos-standalone.jar
	du -sh spire build/spire-$(VERSION)-macos-amd64.zip build/spire-$(VERSION)-macos-standalone.jar

jar-package:
	-rm -rf build/jar-package
	-mkdir -p build/jar-package
	cp target/uberjar/spire-$(VERSION)-standalone.jar build/jar-package/spire-$(VERSION).jar
	cd build/jar-package && zip ../spire-$(VERSION)-jar.zip spire-$(VERSION).jar
	du -sh build/jar-package/spire-$(VERSION).jar build/spire-$(VERSION)-jar.zip

#
# CircleCI
#
ssh_test_key_rsa:
	ssh-keygen -t rsa -f ssh_test_key_rsa -b 2048 -q -N ""
	cat ssh_test_key_rsa.pub >> ~/.ssh/authorized_keys
	sudo mkdir ~root/.ssh
	sudo bash -c 'cat ssh_test_key_rsa.pub >> ~root/.ssh/authorized_keys'

circle-ci: ssh_test_key_rsa
	-lsb_release -a
	-sw_vers
	-system_profiler SPSoftwareDataType
	sudo /usr/sbin/sshd -f test/config/sshd_config -D & echo "$$!" > sshd.pid
	eval `ssh-agent` && \
		ssh-add ssh_test_key_rsa && \
		export SSH_TEST_PORT=2200 && \
		umask 0022 && \
		lein trampoline test && \
		lein trampoline run -e '(ssh "localhost:2200" (get-fact))'; \
		EXIT=$$?; \
		sudo kill `cat sshd.pid`; \
		exit $$EXIT

macos-client-test: ssh_test_key_rsa
	sudo /usr/sbin/sshd -f test/config/sshd_config -D & echo "$$!" > sshd.pid
	eval `ssh-agent` && \
		ssh-add ssh_test_key_rsa && \
		export SSH_TEST_PORT=2200 && \
		umask 0022 && \
		./spire -e '(ssh "localhost:2200" (get-fact))' && \
		./spire -e '(ssh "localhost:2200" (System/getenv "HOME"))'

macos-client-test-jvm: ssh_test_key_rsa
	sudo /usr/sbin/sshd -f test/config/sshd_config -D & echo "$$!" > sshd.pid
	eval `ssh-agent` && \
		ssh-add ssh_test_key_rsa && \
		export SSH_TEST_PORT=2200 && \
		umask 0022 && \
		lein trampoline run -- -e '(ssh "localhost:2200" (get-fact))'

jar-help:
	java -Djava.library.path="." -jar target/uberjar/spire-$(VERSION)-standalone.jar -h

tests-pod:
	bb test/test-pod/test-remote.clj
	bb test/test-pod/test-local.clj
	bb test/test-pod/test-facts.clj
	bb test/test-pod/test-nio.clj
	bb test/test-pod/test-transport.clj
	bb test/test-pod/test-shlex.clj
	bb test/test-pod/test-sh.clj
	bb test/test-pod/test-ssh.clj
