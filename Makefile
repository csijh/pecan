# Pecan classes, in dependency order.
Category = pecan/Category.java
Op = pecan/Op.java
Source = pecan/Source.java
Node = pecan/Node.java $(Op)
Test = pecan/Test.java
Parser = pecan/Parser.java $(Test) $(Node)
Binder = pecan/Binder.java $(Parser) $(Category)
Checker = pecan/Checker.java $(Binder)
Stacker = pecan/Stacker.java $(Checker)
Evaluator = pecan/Evaluator.java $(Stacker)
Code = pecan/Code.java
Generator = pecan/Generator.java $(Stacker)
Run = pecan/Run.java $(Evaluator)
# Analyser = pecan/Analyser.java $(Stacker)
#Opcode Generator Evaluator:

%: pecan/%.java
	javac $($@)
	java -ea pecan.$@

Run: pecan/Run.java
	javac pecan/Run.java

jar: pecan/Run.class
	jar -cef pecan.Run pecan.jar pecan
