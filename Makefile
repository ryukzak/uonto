all: format lint test


repl:
	clojure -M:test:nrepl:refactor

format:
	clojure -M:format fix

format-check:
	clojure -M:format check

lint:
	clojure -M:lint

test:
	clojure -M:test -d src
