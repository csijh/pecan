# Pecan classes, in dependency order.
Category = pecan/Category.java
Op = pecan/Op.java
Node = pecan/Node.java $(Op)
Test = pecan/Test.java
Parser = pecan/Parser.java $(Test) $(Node)
Binder = pecan/Binder.java $(Parser) $(Category)
Checker = pecan/Checker.java $(Binder)
Stacker = pecan/Stacker.java $(Checker)
Interpreter = pecan/Interpreter.java $(Stacker)
Generator = pecan/Generator.java $(Stacker)
Run = pecan/Run.java $(Interpreter)
# Analyser = pecan/Analyser.java $(Stacker)
#Opcode Generator Interpreter:

%: pecan/%.java
	javac $($@)
	java pecan.$@
