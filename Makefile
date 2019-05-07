# Pecan classes, in dependency order.
Category = pecan/Category.java
Op = pecan/Op.java
Info = pecan/Info.java
Node = pecan/Node.java $(Info) $(Op)
Test = pecan/Test.java
Parser = pecan/Parser.java $(Test) $(Node)
Binder = pecan/Binder.java $(Parser) $(Category)
Checker = pecan/Checker.java $(Binder)
Stacker = pecan/Stacker.java $(Checker)
Analyser = pecan/Analyser.java $(Stacker)
Interpreter = pecan/Interpreter.java $(Stacker)
#Opcode Generator Interpreter:

%: pecan/%.java
	javac $($@)
	java pecan.$@
