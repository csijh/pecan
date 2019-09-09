interpret: interpret.h interpret.c
	gcc -std=c11 -Wall -O2 -DTEST interpret.c -o interpret
	./interpret

sum: sum.c interpret.h interpret.c
	cp sum.c sum2.c
	java -jar pecan.jar sum.txt -o sum2.c
	gcc -std=c11 -Wall -O2 sum2.c interpret.c -o sum

scan: scan.txt scan.c interpret.h interpret.c
	cp scan.c scan2.c
	java -jar pecan.jar scan.txt -o scan2.c
	gcc -std=c11 -Wall -O2 scan2.c interpret.c -o scan

clean:
	rm -f sum2.c scan2.c sum scan interpret
