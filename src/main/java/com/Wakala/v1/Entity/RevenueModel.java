package com.Wakala.v1.Entity;

/**
 * Inaeleza faida inakuja vipi kwa muamala huu.
 * - INSTANT: Lipa Namba — faida papo hapo
 * - MONTHLY: Till, Bank — faida mwisho wa mwezi
 * - NONE: Transfers, top-ups — hakuna faida
 */
public enum RevenueModel {
    INSTANT,
    MONTHLY,
    NONE
}