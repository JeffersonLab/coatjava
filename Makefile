# this is a simple Makefile meant for developers' convenience
# - it only has the most common build commands we use
# - for any other build command, please use `./build-coatjava.sh`

# convert `make` arg `-j#` -> `mvn` arg `-T#`, if provided
JOBS := $(patsubst -j%,%,$(filter -j%,$(MAKEFLAGS)))
THREADS := $(if $(strip $(JOBS)),-T$(JOBS),)

BUILD := ./build-coatjava.sh

.PHONY: default all clean clara

.DEFAULT_GOAL := default

default:
	$(BUILD) $(THREADS)

clean:
	$(BUILD) --clean $(THREADS)

clara:
	$(BUILD) --clara $(THREADS)

all: clara
