# Pecan classes, in dependency order.
Category = pecan/Category.java
Source = pecan/Source.java
Op = pecan/Op.java
Node = pecan/Node.java $(Op) $(Source)
Testable = pecan/Testable.java
Test = pecan/Test.java $(Testable)
Parser = pecan/Parser.java $(Test) $(Node) $(Category)
Binder = pecan/Binder.java $(Parser)
Checker = pecan/Checker.java $(Binder)
Stacker = pecan/Stacker.java $(Checker)
Evaluator = pecan/Evaluator.java $(Stacker)
Formats = pecan/Formats.java $(Node)
Pretty = pecan/Pretty.java $(Node)
Transformer = pecan/Transformer.java $(Node)
Compiler = pecan/Compiler.java $(Formats) $(Pretty) $(Transformer) $(Stacker)
Code = pecan/Code.java
Generator = pecan/Generator.java $(Code) $(Stacker)
Run = pecan/Run.java $(Evaluator) $(Compiler) $(Generator)
# Simplifier = pecan/Simplifier.java $(Stacker)
# Analyser = pecan/Analyser.java $(Stacker)

%: pecan/%.java
	javac $($@)
	java -ea pecan.$@

jar: pecan/Run.class
	jar -cef pecan.Run docs/pecan.jar pecan
