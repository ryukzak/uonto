all: format lint test


repl:
	clj -M:test:nrepl:refactor

format:
	clj -M:format fix

format-check:
	clj -M:format check

lint:
	clj -M:lint

test:
	clj -M:test -d src
