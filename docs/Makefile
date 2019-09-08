parser: parser.h parser.c
	gcc -std=c11 -Wall -O2 -DTEST parser.c -o parser
	./parser

sum: sum.c parser.h parser.c
	cp sum.c sum2.c
	java -jar pecan.jar -o sum2.c sum.txt
	gcc -std=c11 -Wall -O2 sum2.c parser.c -o sum

scan: scan.txt scan.c parser.h parser.c
	cp scan.c scan2.c
	java -jar pecan.jar -o scan2.c scan.txt
	gcc -std=c11 -Wall -O2 scan2.c parser.c -o scan

clean:
	rm -f sum2.c scan2.c sum scan parser
