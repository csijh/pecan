#include <stdio.h>
#include <stdbool.h>

struct state { int in; };
typedef struct state state;
typedef bool parser(state *p);

parser sum, expression, term, atom, end;
int $add, $subtract, $multiply, $divide, $number;

bool check(int in, state *p, bool result) { return result; }
bool s(state *p, char *txt);
bool s$(state *p, char *txt);
bool at(state *p);
bool act2(state *p, int n);
bool act0(state *p, int n);
bool some(state *p, parser *f); // NO: must use loop
bool many(state *p, parser *f); // ditto

// sum = expression end
bool sum(state *p) {
    int in = p->in;
    bool b = expression(p) && end(p);
    return check(in, p, b);
}

// expression = term ("+" @ term @2add / "-" @ term @2subtract)*
bool expression(state *p) {
  int in = p->in;
  bool b = term(p);
  while (b && p->in == in) {
      b =
      (s$(p,"+") && term(p) && act2(p, $add)) ||
      (s$(p,"-") && term(p) && act2(p, $subtract))
  }
  return check(p->in, p, b);
}

// term = atom ("*" @ atom @2multiply / "/" @ atom @2divide)*
bool term(state *p) {
  return check(p->in, p,
    atom(p) && many(p,
      (s$(p,"*") && atom(p) && act2(p, $multiply)) ||
      (s$(p,"/") && atom(p) && act2(p, $divide))
    )
  );
}

// atom = number / "(" @ expression ")" @
bool atom(state *p) {
  return check(p->in, p,
    number(p) || (s$(p, "(") && expression(p) && s$(p, ")"))
  );
}

// number = ("0".."9")+ @number
bool number(state *p) {
    return check(p->in, p,
        some(p, range('0','9')) && act0(p, $number)
    );
}

// end = 13? 10 @
bool end(state *p) {
    return check(p->in, p,
        optional(code(13)) && code(10) && at(p)
    );
}

int main() {
    return 0;
}
