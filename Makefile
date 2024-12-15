all: format lint test


repl:
	clj -M:test:nrepl:refactor

format:
	clj -M:format

lint:
	clj -M:lint

test:
	clj -M:test -d src
