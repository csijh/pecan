sum: sum.txt sum.c parse.h parse.c
	java -jar ../pecan.jar sum.txt -c sum.c
	gcc -std=c18 -Wall -O3 -flto sum.c parse.c -o sum
