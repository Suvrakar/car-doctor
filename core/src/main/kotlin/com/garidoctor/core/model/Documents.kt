package com.garidoctor.core.model

import kotlinx.serialization.Serializable

/** Paper types that Bangladeshi vehicles need. */
@Serializable
enum class DocumentType(val label: L10n) {
    REGISTRATION(L10n("Registration (Blue book / Smart card)", "রেজিস্ট্রেশন (ব্লু বুক / স্মার্ট কার্ড)")),
    FITNESS(L10n("Fitness certificate", "ফিটনেস সার্টিফিকেট")),
    TAX_TOKEN(L10n("Tax token", "ট্যাক্স টোকেন")),
    INSURANCE(L10n("Insurance", "ইন্স্যুরেন্স")),
    DRIVING_LICENCE(L10n("Driving licence", "ড্রাইভিং লাইসেন্স")),
    ROUTE_PERMIT(L10n("Route permit", "রুট পারমিট")),
    CNG_CYLINDER_RETEST(L10n("CNG cylinder retest", "সিএনজি সিলিন্ডার রিটেস্ট")),
}

data class VehicleDocument(
    val type: DocumentType,
    val expiryEpochDay: Long,
    val number: String? = null,
)
