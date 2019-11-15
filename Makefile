GRAALVM = $(HOME)/graalvm-ce-19.2.1
VERSION = 0.1.0-SNAPSHOT

all: build/spire

clean:
	-rm -rf build target
	lein clean
	rm src/c/*.o

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

%.o: %.c
	gcc -c -Wall -o $@ $<

ioctl: src/c/ioctl.o
	gcc -o $@ $<
