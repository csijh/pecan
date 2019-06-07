-- Two versions of Pecan parsing expressions.

-- In the first version, each parser returns whether it succeeds, whether it
-- progresses in the input, and the remaining input.

type Pe1 = String -> (Bool, Bool, String)

and1 :: Pe1 -> Pe1 -> Pe1
and1 x y i =
    let (sx, px, ix) = x i in
    if not sx then (sx, px, ix) else
    let (sy, py, ixy) = y ix in
    (sy, px || py, ixy)

or1 :: Pe1 -> Pe1 -> Pe1
or1 x y i =
    let (sx, px, ix) = x i in
    if sx || px then (sx, px, ix) else y i

-- In the second version, a parser doesn't return whether it progresses.
-- Instead, that is implicit in whether the remaining input changes.

type Pe2 = String -> (Bool, String)

and2 x y i =
    let (sx, ix) = x i in
    if not sx then (sx, ix) else y ix

or2 x y i =
    let (sx, ix) = x i in
    if sx || ix /= i then (sx, ix) else y ix

-- In other languages, with side effects, x y can be implemented as:
--     x() && y()
-- and x / y can be implemented by:
--     int in0 = in;
--     x() || in == in0 && y()
-- If it is known that x cannot progress and fail, x / y is
--     x() || y()
