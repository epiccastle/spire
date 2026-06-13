.PHONY: help test test-bb test-clojure jar install deploy clean repl run codox codox-upload

VERSION = $(shell clojure -T:build version)

help:
	@echo "Available targets:"
	@echo "  make test-clojure - Run the test suite under clojure"
	@echo "  make test-bb      - Run the test suite under babashka"
	@echo "  make jar          - Build a jar file"
	@echo "  make install      - Install jar to local Maven repo (~/.m2)"
	@echo "  make deploy       - Deploy jar to Clojars (requires CLOJARS_USERNAME/CLOJARS_PASSWORD)"
	@echo "  make clean        - Remove build artifacts"
	@echo "  make version      - print the version string derived from the git tags"
	@echo "  make repl         - Start a Clojure REPL"
	@echo "  make codox        - Build codox API documentation into target/docs"
	@echo "  make codox-upload - Upload generated docs to epiccastle.io"

test-clojure:
	-mkdir test/files/dir1/dir3
	umask 0000; clojure -M:test

test-bb:
	-mkdir test/files/dir1/dir3
	umask 0000; bb -cp `clojure -Spath -M:test` -m cognitect.test-runner

test: test-clojure test-bb

jar:
	clojure -T:build jar

install:
	clojure -T:build install

deploy:
	clojure -T:build deploy

clean:
	clojure -T:build clean

version:
	clojure -T:build version

repl:
	rlwrap clojure

codox:
	clojure -X:codox :version '"${VERSION}"'

codox-upload:
	rsync -av --delete target/docs/ www-data@epiccastle.io:~/epiccastle.io/public/clojuressh/${VERSION}
