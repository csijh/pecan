# Pecan classes, in dependency order.
Category = pecan/Category.java
Source = pecan/Source.java
Op = pecan/Op.java
Node = pecan/Node.java $(Op)
Testable = pecan/Testable.java
Test = pecan/Test.java
Parser = pecan/Parser.java $(Test) $(Node) $(Category)
Binder = pecan/Binder.java $(Parser)
Checker = pecan/Checker.java $(Binder)
Stacker = pecan/Stacker.java $(Checker)
Evaluator = pecan/Evaluator.java $(Stacker)
Simplifier = pecan/Simplifier.java $(Stacker)
Code = pecan/Code.java
Pretty = pecan/Pretty.java
Transformer = pecan/Transformer.java
Compiler = pecan/Compiler.java $(Stacker) $(Transformer)
Generator = pecan/Generator.java $(Stacker)
Run = pecan/Run.java $(Evaluator) $(Compiler)
# Analyser = pecan/Analyser.java $(Stacker)
#Opcode Generator Evaluator:

%: pecan/%.java
	javac $($@)
	java -ea pecan.$@

Run: pecan/Run.java $(Evaluator)
	javac pecan/Run.java $(Evaluator)

jar: pecan/Run.class
	jar -cef pecan.Run docs/pecan.jar pecan
