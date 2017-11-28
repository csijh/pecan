.PHONY: \
	default unicode Category Op Info Node Test Parser Binder Checker \
	Stacker Analyser Opcode Generator Interpreter pecan jar
default: pecan

unicode:
	cp source/UnicodeData.txt pecan

Category Op Info Node Test Parser Binder Checker Stacker Analyser Opcode \
Generator Interpreter:
	javac -sourcepath source -d . source/$@.java
	java -cp . pecan.$@

# Generator currently not working
# Simplifier left out for now

pecan:
	javac source/Pecan.java -d .
jar:
	jar cfe pecan.jar pecan.Pecan pecan source tests
