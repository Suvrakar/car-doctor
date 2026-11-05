package com.garidoctor.core.history

import com.garidoctor.core.model.L10n

/** Money spent on a vehicle that isn't a part service or fuel. */
enum class ExpenseCategory(val label: L10n) {
    PAPER_FEE(L10n("Paper renewal fee", "কাগজ নবায়ন ফি")),
    INSURANCE(L10n("Insurance premium", "ইন্স্যুরেন্স প্রিমিয়াম")),
    REPAIR(L10n("Repair / body work", "মেরামত / বডির কাজ")),
    TOLL(L10n("Toll / bridge", "টোল / ব্রিজ")),
    PARKING(L10n("Parking", "পার্কিং")),
    FINE(L10n("Fine / case", "জরিমানা / মামলা")),
    WASH(L10n("Wash & cleaning", "ধোয়া ও পরিষ্কার")),
    ACCESSORY(L10n("Accessories", "এক্সেসরিজ")),
    DRIVER(L10n("Driver salary", "চালকের বেতন")),
    OTHER(L10n("Other", "অন্যান্য"));

    companion object { fun of(name: String) = entries.firstOrNull { it.name == name } ?: OTHER }
}
