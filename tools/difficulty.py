#!/usr/bin/python3

from fractions import Fraction

initial_target = 0x00000000FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF
correction_factors = ("1/4", "2/4", "3/4", "1", "2", "3", "4")

# 1st round
for factor in correction_factors:
    target = format(int(initial_target * Fraction(factor)), "064X")
    print("{0:3s}: {1}".format(factor, target))

print("--------------------------------------------------------------------------------")

# 2nd round
for factor in correction_factors:
    # pick the target that was calculated with the factor 3
    target = format(int(initial_target * 3 * Fraction(factor)), "064X")
    print("{0:3s}: {1}".format(factor, target))
