/*
 * Copyright (C) Cybernetica AS
 *
 * All rights are reserved. Reproduction in whole or part is prohibited
 * without the written consent of the copyright owner. The usage of this
 * code is subject to the appropriate license agreement.
 */

module rearrange;

import stdlib;
import shared3p;

template <domain D, type T, type S, dim M, dim N>
D T [[N]] partialRearrange(D T [[M]] a, D T [[N]] b, S [[1]] source, S [[1]] target) {
    assert(size(source) == size(target));
    D T [[1]] temp (size(source));
    __syscall("shared3p::gather_$T\_vec",  __domainid(D), a, temp, __cref (uint)source);
    __syscall("shared3p::scatter_$T\_vec", __domainid(D), temp, b, __cref (uint)target);
    return b;
}

template <domain D, type T, type S, dim N>
D T [[1]] partialRearrange(D T [[N]] a, S [[1]] source) {
    D T [[1]] b (size(source));
    S [[1]] target = (S)iota(size(source));
    b = partialRearrange(a, b, source, target);
    return b;
}

uint [[1]] count_bits (bool [[1]] b, uint [[1]] starts, uint [[1]] ends) {
    assert(size(starts) == size(ends));
    uint [[1]] res (size(starts));
    for (uint j = 0; j < size(starts); j++){
        for (uint i = starts[j]; i < ends[j]; i++){
            res[j] = res[j] + (uint)b[i];
        }
    }
    return res;
}


