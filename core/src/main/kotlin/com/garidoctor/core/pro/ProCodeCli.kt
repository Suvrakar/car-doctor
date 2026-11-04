package com.garidoctor.core.pro

/** Generate promo codes for Facebook-group admins, dongle bundles, mechanics:
 *  ./gradlew :core:proCodes -PcoreOnly -Pfrom=1 -Pcount=50 -Psalt=YOUR_RELEASE_SALT
 */
fun main(args: Array<String>) {
    val from = args.getOrNull(0)?.toIntOrNull() ?: 1
    val count = args.getOrNull(1)?.toIntOrNull() ?: 10
    val salt = args.getOrNull(2) ?: "garidoctor-dev-salt"
    (from until from + count).forEach { println(ProCodes.generate(it, salt)) }
}
