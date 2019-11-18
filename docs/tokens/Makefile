sum: scan.txt scan.h scan.c sum.c parser.h parser.c
	java -jar ../pecan.jar scan.txt -c scan.c
	java -jar ../pecan.jar sum.txt -c sum.c
	gcc -g -std=c11 -Wall -fsanitize=undefined -fsanitize=address -fsanitize=leak scan.c sum.c parser.c -o sum
