.PHONY: \
	default Category op node pecanexception test reader binder checker \
	stacker simplifier generator interpreter pecan jar
default: pecan

category: Category

Category:
	javac -sourcepath source -d . source/$@.java
	cp source/UnicodeData.txt pecan
	java -cp . pecan.$@

TEST = source/Test.java
test:
	javac $(TEST) -d .

OP = source/Op.java
op:
	javac $(OP) -d .

INFO = $(OP) source/Info.java
info:
	javac $(INFO) -d .

NODE = $(INFO) source/Node.java
node:
	javac $(NODE) -d .

PARSER = $(TEST) $(NODE) source/Parser.java
parser:
	javac $(PARSER) -d .
	java pecan.Parser

BINDER = $(PARSER) source/Binder.java
binder:
	javac $(BINDER) -d .
	java pecan.Binder

CHECKER = $(BINDER) source/Checker.java
checker:
	javac $(CHECKER) -d .
	java pecan.Checker

STACKER = $(CHECKER) source/Stacker.java
stacker:
	javac $(STACKER) -d .
	java pecan.Stacker

ANALYSER = $(STACKER) source/Analyser.java
analyser:
	javac $(ANALYSER) -d .
	java pecan.Analyser

OPCODE = source/Opcode.java
opcode:
	javac $(OPCODE) -d .

GENERATOR = $(ANALYSER) $(OPCODE) source/Generator.java
generator:
	javac $(GENERATOR) -d .
	java pecan.Generator

INTERPRETER = $(GENERATOR) source/Interpreter.java
interpreter:
	javac $(INTERPRETER) -d .
	java pecan.Interpreter

pecan:
	javac source/Pecan.java -d .
jar:
	jar cfe pecan.jar pecan.Pecan pecan source tests
